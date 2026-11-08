package coupons.api;

import coupons.promotions.Coupon;
import coupons.promotions.Promotion;
import coupons.rewards.Reward;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import coupons.promotions.ClippedCoupon;
import coupons.rewards.ProviderBalanceSummary;
import coupons.receipts.PurchaseItem;
import coupons.rewards.EarningDetails;
import coupons.promotions.PromotionQualification;
import coupons.promotions.RebateStatus;
import coupons.receipts.ReceiptObservations;
import coupons.shopping.ShoppingPlan;
import coupons.receipts.TransactionCalculator;
import static coupons.api.Json.asText;
import static coupons.api.Json.known;
import static coupons.api.Json.number;
import static coupons.api.Json.optional;
import static coupons.api.Json.required;
import coupons.repository.ClippedCouponRepository;
import coupons.repository.PlatformRepository;
import coupons.repository.RewardInstrumentRepository;
import coupons.repository.ReceiptViews;
import coupons.repository.ProviderRedemption;
import coupons.repository.ReceiptChangeContext;
import coupons.repository.ReceiptCreateCtx;
import coupons.repository.ReceiptRecord;
import coupons.repository.ReceiptStatus;
import coupons.repository.ReceiptSnapshot;
import coupons.repository.ReceiptWriteRequest;
import coupons.repository.ShoppingPlanRepository;
import coupons.repository.TransactionRepository;

/**
 * Single-owner HTTP boundary. Repository instances must belong to the
 * configured owner.
 */
public final class CouponApiService implements HttpHandler {

    private static final int MAX_BODY = 64 * 1024;
    private static final int MAX_KEYS = 10_000;
    private final TransactionRepository receipts;
    private final ShoppingPlanRepository plans;
    private final ClippedCouponRepository coupons;
    private final ApiRequestStore requestStore;
    private final PlatformRepository platform;
    private final String token;
    private final String adminToken;
    private final Map<String, Cached> idempotency = new HashMap<>();

    public CouponApiService(TransactionRepository receipts, ShoppingPlanRepository plans,
            ClippedCouponRepository coupons, String token, String adminToken) {
        this(receipts, plans, coupons, token, adminToken, null);
    }

    CouponApiService(TransactionRepository receipts, ShoppingPlanRepository plans,
            ClippedCouponRepository coupons, String token, String adminToken, ApiRequestStore requestStore) {
        this(receipts, plans, coupons, token, adminToken, requestStore, null);
    }

    CouponApiService(TransactionRepository receipts, ShoppingPlanRepository plans, ClippedCouponRepository coupons,
            String token, String adminToken, ApiRequestStore requestStore, PlatformRepository platform) {
        this.platform = platform;
        this.requestStore = requestStore;
        this.receipts = Objects.requireNonNull(receipts);
        this.plans = Objects.requireNonNull(plans);
        this.coupons = Objects.requireNonNull(coupons);
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("API token must be configured.");
        }
        this.token = token;
        this.adminToken = adminToken == null || adminToken.isBlank() ? null : adminToken;
        if (token.equals(this.adminToken)) {
            throw new IllegalArgumentException("Read and write tokens must differ.");
        }
    }

    /**
     * Loopback by default; remote deployment must use a trusted TLS proxy.
     */
    public HttpServer start(int port) throws IOException {
        return start("127.0.0.1", port);
    }

    public HttpServer start(String host, int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 32);
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        server.createContext("/", this);
        server.setExecutor(executor);
        // HttpServer does not own the supplied executor. Virtual workers exit with each request.
        server.start();
        return server;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            Response response = route(exchange);
            send(exchange, response);
        } catch (ApiError error) {
            if (error.status == 401) {
                exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
            }
            send(exchange, new Response(error.status, Json.object(Map.of("error", error.getMessage()))));
        } catch (ApiRequestStore.Conflict error) {
            send(exchange, new Response(409, Json.object(Map.of("error", error.getMessage()))));
        } catch (NoSuchElementException error) {
            send(exchange, new Response(404, Json.object(Map.of("error", "Resource not found."))));
        } catch (ConcurrentModificationException error) {
            send(exchange, new Response(409, Json.object(Map.of("error", "Revision changed; retrieve the current resource."))));
        } catch (ReceiptCorrection.InvalidFields error) {
            send(exchange,new Response(400,Json.object(Map.of("error",error.getMessage()))));
        } catch (IllegalArgumentException | DateTimeParseException error) {
            String message = error.getMessage();
            send(exchange, new Response(400, Json.object(Map.of(
                    "error", message == null || message.isBlank()
                            ? "Invalid request fields or resource state."
                            : message))));
        } catch (Exception error) {
            System.err.println("API request failed (" + error.getClass().getSimpleName() + ").");
            // Never expose SQL, credentials, paths, or repository exception messages to clients.
            send(exchange, new Response(500, Json.object(Map.of("error", "Request failed."))));
        } finally {
            exchange.close();
        }
    }

    private Response route(HttpExchange e) throws IOException {
        String path = e.getRequestURI().getPath();
        String method = e.getRequestMethod();
        if (path.equals("/health")) {
            if (!method.equals("GET")) {
                throw new ApiError(405, "Method not allowed.");
            }
            return json(200, Map.of("status", "ok"));
        }
        boolean write = !method.equals("GET") && !Set.of("/shopping-plans/compare","/shopping-plans/reconcile","/shopping-plans/match-offers").contains(path);
        String principal = authorize(e, write);
        String[] parts = path.split("/", -1);
        if(parts.length==3 && (parts[1].equals("deals") || parts[1].equals("clipped-coupons")) && method.equals("DELETE")) {
            requirePlatform();return write(e,principal,request->{known(Json.parseObject(request));platform.deleteCatalogRecord(parts[1].equals("deals"),UUID.fromString(parts[2]),revision(e),principal);return json(200,fields("deleted",true));});
        }
        if(parts.length==3&&parts[1].equals("receipt-drafts")) {
            authorize(e,true);requirePlatform();UUID id=UUID.fromString(parts[2]);
            if(method.equals("GET"))return json(200,platform.receiptDraft(id,principal));
            if(method.equals("DELETE"))return write(e,principal,request->{known(Json.parseObject(request));platform.deleteReceiptDraft(id,revision(e),principal);return json(200,fields("deleted",true));});
            requireMethod(method,"PUT");return write(e,principal,16*1024*1024,request->{var f=Json.parseObject(request);known(f,"state");if(!(f.get("state") instanceof Map<?,?>))throw new ApiError(400,"Receipt draft state is required.");@SuppressWarnings("unchecked") var state=(Map<String,Object>)f.get("state");if(!id.toString().equals(required(state,"id")))throw new ApiError(400,"Draft receipt identifier does not match.");return json(200,platform.saveReceiptDraft(id,checkoutRevision(e),Json.object(state),optional(state,"store"),optional(state,"number"),principal));});
        }
        if(path.equals("/inventory/import-receipts")) {
            requireMethod(method,"POST");requirePlatform();return write(e,principal,request->{known(Json.parseObject(request));return json(200,platform.importReceiptInventory(principal));});
        }
        if(parts.length==3&&parts[1].equals("inventory")) {
            requireMethod(method,"PUT");requirePlatform();return write(e,principal,request->{var f=Json.parseObject(request);known(f,"action","quantity","location","note","lowStockLevel","unitsPerPackage","unitName","countUnit","category","comparisonSize","comparisonUnit");platform.adjustReceiptInventory(UUID.fromString(parts[2]),revision(e),required(f,"action"),Math.toIntExact(number(f.get("quantity"))),optional(f,"location"),optional(f,"note"),principal,f.get("lowStockLevel")==null?null:Math.toIntExact(number(f.get("lowStockLevel"))),f.get("unitsPerPackage")==null?null:Math.toIntExact(number(f.get("unitsPerPackage"))),optional(f,"unitName"),optional(f,"countUnit"),f.containsKey("category")?Objects.toString(f.get("category"),""):null,f.get("comparisonSize")==null?null:ReceiptInput.decimal(f,"comparisonSize",true),f.containsKey("comparisonUnit")?Objects.toString(f.get("comparisonUnit"),""):null);return json(200,fields("saved",true));});
        }
        if (path.equals("/deals")) {
            requireMethod(method, "POST");
            requirePlatform();
            return write(e, principal, 8 * 1024 * 1024, request -> {
                var input = DealInput.read(Json.parseObject(request));
                platform.saveDeal(input.deal(), principal, input.source());
                return json(201, input.deal());
            });
        }
        if(path.equals("/receipts/import-preview")) {
            requireMethod(method,"POST");var input=Json.parseObject(body(e,15*1024*1024));known(input,"files");
            var files=ReceiptInput.rows(input,"files",4);if(files.isEmpty())throw new ApiError(400,"Choose receipt files to import.");
            var sources=new ArrayList<Map<String,Object>>();int total=0;
            for(var file:files){known(file,"name","mediaType","contentBase64");var encoded=required(file,"contentBase64");if(encoded.length()>7*1024*1024)throw new ApiError(400,"Each import file must be at most 5 MiB.");var bytes=java.util.Base64.getDecoder().decode(encoded);total+=bytes.length;if(total>10*1024*1024)throw new ApiError(400,"Import at most 10 MiB total.");try{sources.add(ReceiptImport.extract(required(file,"name"),required(file,"mediaType"),bytes));}catch(ApiError error){throw error;}catch(Exception error){throw new ApiError(400,"Receipt extraction failed. Try a clearer PDF or image.");}}
            var numbers=sources.stream().map(v->v.get("number").toString().replaceAll("[^0-9]","")).filter(v->!v.isBlank()).distinct().toList();
            if(numbers.size()>1)throw new ApiError(400,"These files contain different RFNs. Import one purchase at a time.");
            sources=new ArrayList<>(ReceiptPhotoMerge.combine(sources));
            var duplicates=new ArrayList<Map<String,Object>>();
            for(var source:sources){String number=source.get("number").toString();if(!number.isBlank())for(var receipt:receipts.findImportDuplicates(new coupons.repository.ReceiptMeta(source.get("store").toString(),number,null,null)))if(duplicates.stream().noneMatch(v->v.get("transactionId").equals(receipt.transactionId())))duplicates.add(fields("transactionId",receipt.transactionId(),"receiptNumber",receipt.metadata().receiptNumber(),"status",receipt.status()));}
            return json(200,fields("sources",sources,"duplicates",duplicates));
        }
        if (path.equals("/receipts/preview")) {
            requireMethod(method, "POST");
            var input = receiptInput(body(e, 16 * 1024 * 1024));
            var duplicate = possibleDuplicates(input);
            return json(200, fields("transactionId", input.id(), "reviewToken", input.reviewToken(), "totals", ReceiptViews.totals(input.record()),
                    "possibleDuplicates", duplicate.stream().map(r -> fields("transactionId", r.transactionId(), "status", r.status(), "revision", r.revision())).toList(),
                    "evidence", input.evidence().stream().map(v -> fields("evidenceId", v.getEvidenceId(), "mediaType", v.getMediaType(), "bytes", v.getContent().length)).toList()));
        }
        if (path.equals("/receipts")) {
            requireMethod(method, "POST");
            return write(e, principal, 16 * 1024 * 1024, request -> {
                var input = receiptInput(request);
                if (!input.reviewToken().equals(input.suppliedReviewToken())) {
                    throw new ApiError(409, "Receipt changed since preview. Review the current values before saving.");
                }
                if (new TransactionCalculator().reconcilePayments(input.transaction(), input.metadata()).acknowledgementRequired() && !input.acknowledgeDifference()) {
                    throw new ApiError(400, "Confirm the recorded purchase/payment difference before saving.");
                }
                if (receipts.findById(input.id()).or(() -> receipts.findDeletedById(input.id())).isPresent()) {
                    throw new ApiError(409, "Receipt UUID already exists; creation cannot overwrite it.");
                }
                if (!possibleDuplicates(input).isEmpty()) {
                    throw new ApiError(409, "Possible existing receipt found. Review it before creating another receipt.");
                }
                var selected=ReceiptInput.ids(Json.parseObject(request),"clippedCouponIds");
                if(selected.size()>100 || new java.util.HashSet<>(selected).size()!=selected.size())throw new ApiError(400,"Select each clipped coupon once.");
                var writeRequest=new ReceiptWriteRequest(UUID.randomUUID(),java.time.Instant.now());
                var source=Json.parseObject(request);var planId=optional(source,"shoppingPlanId");var draftId=optional(source,"receiptDraftId");if(draftId!=null&&!input.id().toString().equals(draftId))throw new ApiError(400,"Saved draft must match this receipt.");
                var result = draftId!=null || planId!=null || !selected.isEmpty() ?
                    receipts.saveWithDraft(input.id(),input.transaction(),input.metadata(),input.evidence(),writeRequest,new ReceiptCreateCtx(principal),selected,planId==null?null:UUID.fromString(planId),planId==null?0:Long.parseLong(required(source,"shoppingPlanRevision")),draftId==null?null:UUID.fromString(draftId),draftId==null?0:Long.parseLong(required(source,"receiptDraftRevision"))):
                    receipts.save(input.id(),input.transaction(),input.metadata(),input.evidence(),writeRequest,new ReceiptCreateCtx(principal));
                e.getResponseHeaders().set("Location", "/receipts/" + input.id());
                return json(201, result);
            });
        }
        if(path.equals("/reward-instruments")) {
            requirePlatform();if(method.equals("GET")){var instruments=platform.rewardInstruments();return json(200,fields("instruments",instruments,"cardSummary",RewardInstrumentRepository.cardSummary(instruments)));}
            requireMethod(method,"POST");return write(e,principal,request->{var f=Json.parseObject(request);known(f,"id","kind","provider","nickname","lastFour","unit","opening","date","historyComplete","replacementId","evidenceReference","reason");return json(201,platform.createRewardInstrument(f,principal));});
        }
        if(parts.length>=3&&parts[1].equals("reward-instruments")) {
            requirePlatform();UUID id=UUID.fromString(parts[2]);
            if(parts.length==3){if(method.equals("GET"))return json(200,platform.rewardInstrument(id));requireMethod(method,"PUT");return write(e,principal,request->{var f=Json.parseObject(request);known(f,"provider","nickname","lastFour","opening","date","historyComplete","reason","confirmed","kind","status");return json(200,platform.correctRewardInstrument(id,revision(e),f,principal));});}
            if(parts.length==4&&parts[3].equals("events")){requireMethod(method,"POST");return write(e,principal,request->{var f=Json.parseObject(request);known(f,"id","kind","amount","date","reason","supersedes","confirmed","sourceRevision","sourceReceiptId","sourceEventId");return json(201,platform.rewardInstrumentEvent(id,revision(e),f,principal));});}
            if(parts.length==4&&parts[3].equals("exchange")){requireMethod(method,"POST");return write(e,principal,request->{var f=Json.parseObject(request);known(f,"id","cardId","provider","nickname","lastFour","sourceAmount","cardValue","date","reason","evidenceReference");return json(201,platform.exchangeRewardInstrument(id,revision(e),f,principal));});}
        }
        if(path.equals("/accounts/activity")) {
            requireMethod(method,"GET"); requirePlatform();
            var provider=query(e.getRequestURI(),"provider");var from=query(e.getRequestURI(),"from");var to=query(e.getRequestURI(),"to");
            if(provider==null || provider.isBlank() || from==null || to==null) throw new ApiError(400,"Select an account and date range.");
            return json(200,platform.account(provider,LocalDate.parse(from),LocalDate.parse(to)));
        }
        if(path.equals("/accounts/payouts/reconcile-gift-cards")){requireMethod(method,"POST");requirePlatform();return write(e,principal,request->{known(Json.parseObject(request));return json(200,platform.reconcilePayoutCards(principal));});}
        if(path.equals("/accounts/payouts")) {
            requireMethod(method,"POST");requirePlatform();return write(e,principal,request->{var f=Json.parseObject(request);known(f,"payoutId","provider","date","description","points","amount","destination","brand","number","status","sourceRewardId");
                String provider=ProviderBalanceSummary.accountName(required(f,"provider"));if(!ProviderBalanceSummary.supportsAccount(provider))throw new ApiError(400,"Choose a supported reward account.");
                var points=ReceiptInput.decimal(f,"points",true);var amount=ReceiptInput.decimal(f,"amount",true);
                if(amount==null&&"GIFT_CARD".equals(optional(f,"destination")))throw new ApiError(400,"Enter the actual gift-card face value. Redeemed points do not establish the issued card amount.");
                if(amount==null&&points!=null){if(provider.equals("Fetch"))amount=points.movePointLeft(3);else if(provider.equals("P&G Brand"))amount=points.divide(new BigDecimal("150"),java.math.MathContext.DECIMAL128);}
                if(amount==null||amount.signum()<=0||points!=null&&points.signum()<=0)throw new ApiError(400,"Enter a positive payout amount, or points with a known dollar conversion.");
                if(points!=null&&!Set.of("Fetch","P&G Brand","Swagbucks","MyPoints","Merryfield").contains(provider))throw new ApiError(400,"This account does not use points.");
                String destination=optional(f,"destination");if(destination==null)destination="CASH";if(!Set.of("CASH","GIFT_CARD","CREDIT_CARD").contains(destination))throw new ApiError(400,"Choose Cash, Gift Card or Credit Card.");
                String brand=optional(f,"brand"),number=optional(f,"number"),description=optional(f,"description");
                if(brand!=null&&brand.length()>120||number!=null&&number.length()>100||description!=null&&description.length()>1000)throw new ApiError(400,"Payout details are too long.");
                if(destination.equals("CREDIT_CARD")&&number!=null&&!number.matches("[0-9]{1,4}"))throw new ApiError(400,"Record only the last four credit-card digits.");
                var day=optional(f,"date")==null?LocalDate.now(java.time.ZoneId.of("America/Chicago")):LocalDate.parse(optional(f,"date"));var status=optional(f,"status")==null?ProviderRedemption.Status.REQUESTED:ProviderRedemption.Status.valueOf(optional(f,"status"));if(status==ProviderRedemption.Status.CANCELLED)throw new ApiError(400,"Record a request before cancelling it.");
                var sources=new java.util.ArrayList<UUID>();if(optional(f,"sourceRewardId")!=null){var source=ReceiptInput.uuid(f,"sourceRewardId");var reward=platform.memberReward(source);if(!provider.equals(ProviderBalanceSummary.accountName(reward.get("provider").toString())))throw new ApiError(400,"The payout and source reward must belong to the same account.");sources.add(source);}
                UUID id=ReceiptInput.uuid(f,"payoutId");String details=java.util.Objects.toString(description,"")+(brand==null?"":" | Brand: "+brand)+(number==null?"":" | Number: "+number);
                var value=new ProviderRedemption(id,provider,day,null,points,amount,destination.equals("CASH")?ProviderRedemption.Destination.CASH:destination.equals("GIFT_CARD")?ProviderRedemption.Destination.GIFT_CARD:ProviderRedemption.Destination.OTHER,destination.equals("CASH")?"Cash":destination.equals("GIFT_CARD")?"Gift Cards":"Credit Card",amount,status,status==ProviderRedemption.Status.RECEIVED?day:null,null,sources,id.toString(),details);
                platform.savePayout(value);return json(201,fields("saved",true,"payoutId",id));});
        }
        if(parts.length==5&&parts[1].equals("accounts")&&parts[2].equals("payouts")&&parts[4].equals("correction")){
            requirePlatform();requireMethod(method,"POST");return write(e,principal,request->{var f=Json.parseObject(request);known(f,"date","completedDate","sourceUsdValue","destinationUsdValue","originalUnitsUsed","reference","reason","confirmed","void");platform.correctPayout(UUID.fromString(parts[3]),revision(e),f,principal);return json(200,fields("saved",true));});
        }
        if(parts.length==5&&parts[1].equals("accounts")&&parts[2].equals("payouts")&&parts[4].equals("gift-card")){
            requirePlatform();requireMethod(method,"POST");return write(e,principal,request->{var f=Json.parseObject(request);known(f,"id","existingInstrumentId","instrumentRevision","provider","nickname","lastFour","confirmed","reason");return json(201,platform.linkPayoutCard(UUID.fromString(parts[3]),revision(e),f,principal));});
        }
        if(parts.length==4&&parts[1].equals("accounts")&&parts[2].equals("payouts")) {
            requirePlatform();UUID id=UUID.fromString(parts[3]);if(method.equals("GET"))return json(200,platform.payout(id));requireMethod(method,"PUT");return write(e,principal,request->{var f=Json.parseObject(request);known(f,"status","date");platform.finishPayout(id,revision(e),ProviderRedemption.Status.valueOf(required(f,"status")),LocalDate.parse(required(f,"date")));return json(200,fields("saved",true));});
        }
        if(parts.length==4&&parts[1].equals("accounts")&&parts[2].equals("member-rewards")) {
            requirePlatform();UUID id=UUID.fromString(parts[3]);if(method.equals("GET"))return json(200,platform.memberReward(id));
            if(!method.equals("PUT")&&!method.equals("DELETE"))throw new ApiError(405,"Use PUT or DELETE.");
            return write(e,principal,request->{var f=Json.parseObject(request);known(f,"amount","expectedAmount","points","expectedPoints","correctionId","details","expectedToken");var state=platform.memberReward(id);var pts=ReceiptInput.decimal(f,"points",true);var amount=ReceiptInput.decimal(f,"amount",method.equals("DELETE"));
                if(method.equals("DELETE")){amount=BigDecimal.ZERO;pts=state.get("points")==null?null:BigDecimal.ZERO;}
                else if(pts!=null){String name=f.get("details") instanceof Map<?,?> details&&details.get("provider") instanceof String target?ProviderBalanceSummary.accountName(target):(String)state.get("provider");if(name.equals("Fetch"))amount=pts.movePointLeft(3);else if(name.equals("P&G Brand"))amount=pts.divide(new BigDecimal("150"),java.math.MathContext.DECIMAL128);}
                if(f.get("details")!=null||f.get("expectedToken")!=null){var details=new java.util.HashMap<String,String>(f.get("details")==null?Map.of():promotionPlanningRules(f.get("details")));known(new java.util.HashMap<String,Object>(details),"description","provider","date","time","expirationDate","source","status","receiptId","rewardType");if(details.containsKey("provider")){var provider=ProviderBalanceSummary.accountName(details.get("provider"));if(!ProviderBalanceSummary.supportsAccount(provider))throw new ApiError(400,"Choose a supported account.");details.put("provider",provider);}if(details.containsKey("status")&&!Set.of("ACTIVE","VOID").contains(details.get("status")))throw new ApiError(400,"Choose Active or void this reward.");if("VOID".equals(details.get("status"))&&amount.signum()!=0)throw new ApiError(400,"Use Void reward to reverse its recorded value.");if(details.containsKey("description")&&details.get("description").isBlank())throw new ApiError(400,"Enter a reward description.");if(details.containsKey("date"))LocalDate.parse(details.get("date"));if(details.containsKey("expirationDate")&&!details.get("expirationDate").isBlank())LocalDate.parse(details.get("expirationDate"));if(details.containsKey("receiptId")&&!details.get("receiptId").isBlank()){var source=receipts.findById(UUID.fromString(details.get("receiptId"))).orElseThrow(()->new ApiError(400,"Choose an existing receipt."));if(source.status()!=ReceiptStatus.ACTIVE)throw new ApiError(400,"Choose an active receipt.");}details.put("__expectedToken",required(f,"expectedToken"));platform.correctMemberDetails(id,ReceiptInput.uuid(f,"correctionId"),ReceiptInput.decimal(f,"expectedAmount",false),amount,ReceiptInput.decimal(f,"expectedPoints",true),pts,details,principal);}else platform.correctMemberAmount(id,ReceiptInput.uuid(f,"correctionId"),ReceiptInput.decimal(f,"expectedAmount",false),amount,ReceiptInput.decimal(f,"expectedPoints",true),pts,principal);return json(200,fields("saved",true));});
        }
        if(parts.length==4&&parts[1].equals("rewards")&&parts[2].equals("register-rewards")) {
            requirePlatform();if(method.equals("DELETE"))return write(e,principal,request->{known(Json.parseObject(request));platform.deleteStandaloneReward(UUID.fromString(parts[3]),principal);return json(200,fields("deleted",true));});
            requireMethod(method,"PUT");return write(e,principal,request->{var f=Json.parseObject(request);known(f,"amount","expectedAmount","description","earnedDate","expirationDate","registerRewardType","originProduct","originBrand","originProgram","originPromotion");f.put("__actor",principal);platform.correctStandaloneAmount(UUID.fromString(parts[3]),ReceiptInput.decimal(f,"expectedAmount",false),ReceiptInput.decimal(f,"amount",false),f);return json(200,fields("saved",true));});
        }
        if(path.equals("/accounts/member-rewards")) {
            requireMethod(method,"POST");requirePlatform();return write(e,principal,request->{
                var f=Json.parseObject(request);known(f,"eventId","provider","date","time","amount","points","description","expirationDate");String provider=ProviderBalanceSummary.accountName(required(f,"provider"));
                if(!ProviderBalanceSummary.supportsAccount(provider))throw new ApiError(400,"Select a supported reward account.");
                var amount=ReceiptInput.decimal(f,"amount",true);var pts=ReceiptInput.decimal(f,"points",true);
                boolean pointProgram=Set.of("Fetch","P&G Brand","Swagbucks","MyPoints","Merryfield").contains(provider);
                if(pts!=null&&!pointProgram)throw new ApiError(400,"This account does not use points.");
                if(pts!=null){if(pts.signum()<=0)throw new ApiError(400,"Enter positive points.");if(provider.equals("Fetch"))amount=pts.movePointLeft(3);else if(provider.equals("P&G Brand"))amount=pts.divide(new BigDecimal("150"),java.math.MathContext.DECIMAL128);}
                if(amount==null||amount.signum()<=0)throw new ApiError(400,"Enter a positive reward amount or a dollar valuation for these points.");
                var value=new coupons.repository.AccountAdjustment(ReceiptInput.uuid(f,"eventId"),LocalDate.parse(required(f,"date")),provider,pts,amount,"Member reward: "+required(f,"description"),principal,java.time.Instant.now(),optional(f,"expirationDate")==null?null:LocalDate.parse(optional(f,"expirationDate")),optional(f,"time")==null?null:java.time.LocalTime.parse(optional(f,"time")));
                platform.saveMemberReward(value);return json(201,value);});
        }
        if(path.equals("/accounts/openings")) {
            requireMethod(method,"POST"); requirePlatform();
            return write(e,principal,request -> {
                var f=Json.parseObject(request); known(f,"observationId","provider","date","points","usd","valuationBasis","sourceNote","confirmedBeginningOfDay");
                if(!ReceiptInput.flag(f,"confirmedBeginningOfDay")) throw new ApiError(400,"Confirm this is a beginning-of-day balance.");
                var opening=new coupons.repository.AccountOpening(ReceiptInput.uuid(f,"observationId"),LocalDate.parse(required(f,"date")),ProviderBalanceSummary.accountName(required(f,"provider")),ReceiptInput.decimal(f,"points",true),ReceiptInput.decimal(f,"usd",true),false,required(f,"valuationBasis"),required(f,"sourceNote")+" | "+principal,java.time.Instant.now());
                platform.saveOpening(opening); return json(201,opening);
            });
        }
        if(path.equals("/accounts/unavailable-rewards")){
            requireMethod(method,"POST");requirePlatform();return write(e,principal,request->{var f=Json.parseObject(request);known(f,"eventId","provider","date","amount","points","sourceId","category","reason");
                platform.recordUnavailableReward(ReceiptInput.uuid(f,"eventId"),required(f,"provider"),LocalDate.parse(required(f,"date")),ReceiptInput.decimal(f,"amount",true),ReceiptInput.decimal(f,"points",true),optional(f,"sourceId")==null?null:UUID.fromString(optional(f,"sourceId")),required(f,"category"),required(f,"reason"),principal);return json(201,Map.of("recorded",true));});
        }
        if(path.equals("/rewards/register-rewards")) {
            requirePlatform();if(method.equals("GET"))return json(200,platform.standaloneRewards());
            requireMethod(method,"POST");return write(e,principal,request->{
                var f=Json.parseObject(request);known(f,"rewardId","description","amount","earnedDate","expirationDate","confirmAvailable","registerRewardType");
                if(!ReceiptInput.flag(f,"confirmAvailable"))throw new ApiError(400,"Confirm this is an unused RR coupon available to use.");
                String earned=optional(f,"earnedDate"),expires=optional(f,"expirationDate");
                var amount=ReceiptInput.decimal(f,"amount",false);if(amount.signum()<=0)throw new ApiError(400,"Enter a reward amount greater than zero.");
                var reward=new coupons.rewards.RegisterReward(ReceiptInput.uuid(f,"rewardId"),required(f,"description"),amount,earned==null?null:LocalDate.parse(earned),expires==null?null:LocalDate.parse(expires),null,optional(f,"registerRewardType"));
                platform.addStandaloneReward(reward);return json(201,fields("rewardId",reward.getRewardId(),"status","UNUSED"));
            });
        }
        if(parts.length==5 && parts[1].equals("receipts") && parts[3].equals("rr-matches") && parts[4].equals("unknown-source")) {
            requireMethod(method,"POST");requirePlatform();
            return write(e,principal,request->{
                var f=Json.parseObject(request);known(f,"usageEventId","rewardId","description","earnedDate","expirationDate","reason","confirmRecordedUse");
                if(!ReceiptInput.flag(f,"confirmRecordedUse"))throw new ApiError(400,"Confirm this RR was used on the receipt; an earning receipt is optional.");
                String earned=optional(f,"earnedDate"),expires=optional(f,"expirationDate");
                try {return json(201,platform.addStandaloneMatch(UUID.fromString(parts[2]),revision(e),ReceiptInput.uuid(f,"usageEventId"),ReceiptInput.uuid(f,"rewardId"),required(f,"description"),earned==null?null:LocalDate.parse(earned),expires==null?null:LocalDate.parse(expires),required(f,"reason"),principal));}
                catch(IllegalArgumentException error){throw new ApiError(400,"Check the source description and known dates. The RR use must be unmatched; known dates must fit this purchase.");}
            });
        }
        if(parts.length==4 && parts[1].equals("receipts") && parts[3].equals("rr-matches")) {
            requirePlatform();var id=UUID.fromString(parts[2]);
            if(method.equals("GET"))return json(200,platform.rrOptions(id));
            requireMethod(method,"POST");return write(e,principal,request->{
                var f=Json.parseObject(request);known(f,"usageEventId","sourceId","receiptEarning","reason","confirmUnknownHistory","expectedSourceId");
                try {if(f.get("expectedSourceId")!=null)return json(200,platform.correctRRMatch(id,revision(e),ReceiptInput.uuid(f,"usageEventId"),ReceiptInput.uuid(f,"expectedSourceId"),ReceiptInput.uuid(f,"sourceId"),ReceiptInput.flag(f,"receiptEarning"),required(f,"reason"),principal,ReceiptInput.flag(f,"confirmUnknownHistory")));return json(201,platform.matchRR(id,revision(e),ReceiptInput.uuid(f,"usageEventId"),ReceiptInput.uuid(f,"sourceId"),ReceiptInput.flag(f,"receiptEarning"),optional(f,"reason")==null || optional(f,"reason").isBlank() ? "Matched recorded RR use to selected reward" : optional(f,"reason").trim(),principal,ReceiptInput.flag(f,"confirmUnknownHistory")));}
                catch(IllegalArgumentException error) {throw new ApiError(400,"The RR cannot be matched. Refresh available rewards and check the receipt date, amount and source.");}
            });
        }
        if(path.equals("/receipts/report")) {
            requireMethod(method,"GET");requirePlatform();
            var from=query(e.getRequestURI(),"from");var to=query(e.getRequestURI(),"to");
            return json(200,platform.receiptReport(Objects.toString(query(e.getRequestURI(),"metric"),"regular"),from==null||from.isBlank()?null:LocalDate.parse(from),to==null||to.isBlank()?null:LocalDate.parse(to),query(e.getRequestURI(),"search"),query(e.getRequestURI(),"sort")));
        }
        if (path.equals("/receipts/summary")) {
            requireMethod(method, "GET");
            requirePlatform();
            if (query(e.getRequestURI(), "from") == null || query(e.getRequestURI(), "to") == null) throw new ApiError(400, "Choose a start and end date.");
            return json(200, platform.receiptRange(LocalDate.parse(query(e.getRequestURI(), "from")), LocalDate.parse(query(e.getRequestURI(), "to"))));
        }
        if (path.equals("/workspace")) {
            requireMethod(method, "GET");
            requirePlatform();
            var data = new LinkedHashMap<>(platform.workspace(query(e.getRequestURI(),"asOf")==null?null:LocalDate.parse(query(e.getRequestURI(),"asOf"))));
            data.put("canWrite", principal.equals("api-owner"));
            data.put("receiptDrafts",principal.equals("api-owner")?platform.receiptDrafts(principal):List.of());
            return json(200, data);
        }
        if (parts.length == 4 && parts[1].equals("receipts") && Set.of("totals", "history").contains(parts[3])) {
            requireMethod(method, "GET");
            requirePlatform();
            UUID id = UUID.fromString(parts[2]);
            return json(200, parts[3].equals("history") ? platform.history(id) : platform.receiptTotals(id));
        }
        if (parts.length == 5 && parts[1].equals("receipts") && parts[3].equals("evidence")) {
            requireMethod(method, "GET");
            requirePlatform();
            var evidence = platform.evidence(UUID.fromString(parts[2]), UUID.fromString(parts[4]));
            e.getResponseHeaders().set("Content-Disposition", "attachment; filename=receipt-evidence");
            String type = Set.of("image/jpeg", "image/png", "image/webp", "application/pdf").contains(evidence.getMediaType()) ? evidence.getMediaType() : "application/octet-stream";
            return new Response(200, "", evidence.getContent(), type);
        }
        if ((parts.length == 4 || parts.length == 5) && parts[1].equals("shopping-plans") && parts[3].equals("purchases")) {
            requirePlatform();
            UUID planId = UUID.fromString(parts[2]);
            requireMethod(method, parts.length == 4 ? "POST" : "PUT");
            return write(e, principal, request -> {
                var f = Json.parseObject(request);
                known(f, "plannedPurchaseId", "productName", "productCode", "quantity", "targetUnitPrice", "advertisedDealId", "clippedCouponIds", "regularUnitPrice", "saleUnitPrice", "priceSource");
                UUID id = parts.length == 4 ? UUID.fromString(required(f, "plannedPurchaseId")) : UUID.fromString(parts[4]);
                if (f.containsKey("plannedPurchaseId") && !id.toString().equals(required(f, "plannedPurchaseId"))) {
                    throw new ApiError(400, "Purchase ID must match URL.");
                }
                long quantity = number(f.get("quantity"));
                if (quantity < 1 || quantity > Integer.MAX_VALUE) {
                    throw new ApiError(400, "Quantity must be a positive whole number.");
                }
                String target = optional(f, "targetUnitPrice"), deal = optional(f, "advertisedDealId");
                var couponIds = new ArrayList<UUID>();
                if (f.containsKey("clippedCouponIds")) {
                    if (!(f.get("clippedCouponIds") instanceof List<?> ids)) {
                        throw new ApiError(400, "Coupon IDs must be an array.");
                    }
                    for (var coupon : ids) {
                        couponIds.add(UUID.fromString(asText(coupon)));
                    }
                }
                var item = f.containsKey("regularUnitPrice") ? platform.savePurchase(planId, id, required(f, "productName"), optional(f, "productCode"), (int) quantity,
                        ReceiptInput.decimal(f, "regularUnitPrice", true), ReceiptInput.decimal(f, "saleUnitPrice", true), optional(f, "priceSource"), deal == null ? null : UUID.fromString(deal), couponIds, parts.length == 4 ? 0 : revision(e), principal)
                        : platform.savePurchase(planId, id, required(f, "productName"), optional(f, "productCode"), (int) quantity, target == null ? null : new BigDecimal(target), deal == null ? null : UUID.fromString(deal), couponIds, parts.length == 4 ? 0 : revision(e), principal);
                return json(parts.length == 4 ? 201 : 200, item);
            });
        }

        if ((parts.length == 4 || parts.length == 5 && parts[4].equals("preview")) && parts[1].equals("receipts") && parts[3].equals("earnings")) {
            requireMethod(method, "POST");
            UUID receiptId = UUID.fromString(parts[2]);
            if (parts.length == 5) {
                var old = receipts.findById(receiptId).orElseThrow();
                if (old.revision() != revision(e)) {
                    throw new ConcurrentModificationException();
                }
                var addition = ReceiptEarningAddition.read(old, Json.parseObject(body(e, 8 * 1024 * 1024)));
                return json(200, fields("earning", addition.earning(), "qualification", PromotionQualification.evaluate(addition.earning(), addition.transaction()), "totals", ReceiptViews.totals(new ReceiptRecord(old.transactionId(), old.status(), old.revision(), addition.transaction(), old.metadata(), old.evidenceIds()))));
            }
            return write(e, principal, 8 * 1024 * 1024, request -> {
                var old = receipts.findById(receiptId).orElseThrow();
                if (old.revision() != revision(e)) {
                    throw new ConcurrentModificationException();

                }
                var addition = ReceiptEarningAddition.read(old, Json.parseObject(request));
                validateCreditSources(receiptId,addition.transaction());
                return json(201, addition.save(receipts, old, principal));
            });
        }

        if (parts.length == 6 && parts[1].equals("receipts") && parts[3].equals("earnings") && parts[5].equals("associations")) {
            requireMethod(method, "PUT");
            UUID receiptId = UUID.fromString(parts[2]), earningId = UUID.fromString(parts[4]);
            return write(e, principal, request -> {
                var old = receipts.findById(receiptId).orElseThrow();
                if (old.revision() != revision(e)) {
                    throw new ConcurrentModificationException();
                }
                var f = Json.parseObject(request);
                known(f, "lineItemIds", "qualifyingQuantities", "expirationDate","offer","registerRewardType","originProduct","originBrand","originProgram","originPromotion");
                var tx = old.transaction();
                var obs = tx.getObservations();
                var earnings = new ArrayList<>(obs.earnings());
                int index = -1;
                for (int i = 0; i < earnings.size(); i++) {
                    if (earnings.get(i).earningId().equals(earningId)) {
                        index = i;

                    }
                }
                if (index < 0) {
                    throw new NoSuchElementException();
                }
                var previous = earnings.get(index);
                var lines=f.containsKey("lineItemIds")?ReceiptInput.ids(f,"lineItemIds"):previous.lineItemIds();
                var expiry=f.containsKey("expirationDate")?(optional(f,"expirationDate")==null?null:LocalDate.parse(optional(f,"expirationDate"))):previous.expirationDate();
                if(expiry!=null && obs.purchaseDate()!=null && expiry.isBefore(obs.purchaseDate()))throw new ApiError(400,"Expiration must be on or after the purchase date.");
                var detail = previous.details();
                var nextDetails = new EarningDetails(f.containsKey("qualifyingQuantities")?ReceiptInput.quantities(f):detail==null?Map.of():detail.qualifyingQuantities(), detail == null ? null : detail.payoutDestination(), detail == null ? null : detail.submittedOn(), detail == null ? null : detail.submissionReference(), detail == null ? null : detail.confirmationReference(),detail==null?null:detail.submissionStatus(),detail==null?null:detail.followUpOn(),detail==null?null:detail.followUpNote(),detail==null?null:detail.originalReceiptId(),detail==null?null:detail.originalEarningId(),f.containsKey("registerRewardType")?optional(f,"registerRewardType"):detail==null?null:detail.registerRewardType(),f.containsKey("originProduct")?optional(f,"originProduct"):detail==null?null:detail.originProduct(),f.containsKey("originBrand")?optional(f,"originBrand"):detail==null?null:detail.originBrand(),f.containsKey("originProgram")?optional(f,"originProgram"):detail==null?null:detail.originProgram(),f.containsKey("originPromotion")?optional(f,"originPromotion"):detail==null?null:detail.originPromotion());
                earnings.set(index, new ReceiptObservations.Earning(previous.earningId(), previous.kind(), previous.provider(), f.containsKey("offer")?optional(f,"offer"):previous.offerReference(), lines.isEmpty() ? ReceiptObservations.Scope.RECEIPT : lines.size() == 1 ? ReceiptObservations.Scope.ITEM : ReceiptObservations.Scope.MULTI_ITEM, lines, previous.expected(), previous.confirmed(), previous.status(), expiry, previous.instrumentId(), previous.claimId(), previous.evidenceIds(), previous.recordedLifecycle(), previous.requirement(), previous.valuation(), nextDetails));
                tx.setObservations(new ReceiptObservations(obs.purchaseDate(), obs.printedTime(), earnings, obs.unidentifiedRR(), obs.walgreensCash(), obs.savings(), obs.discountKinds(), obs.receiptSavingValuePercent(), obs.promotionLinks(), obs.worksheet(), obs.footerStatus()));
                var now = java.time.Instant.now();
                return json(200, receipts.update(receiptId, old.revision(), tx, old.metadata(), List.of(), new ReceiptWriteRequest(UUID.randomUUID(), now), new ReceiptChangeContext(principal, "Updated earning product associations / expiration: " + earningId, principal, now)));
            });
        }
        if (parts.length == 6 && parts[1].equals("receipts") && parts[3].equals("earnings") && (parts[5].equals("status")||parts[5].equals("amount")||parts[5].equals("delete")||parts[5].equals("follow-up")||parts[5].equals("source"))) {
            requireMethod(method, "PUT");
            UUID receiptId = UUID.fromString(parts[2]), earningId = UUID.fromString(parts[4]);
            return write(e, principal, request -> {
                long expected = revision(e);
                var f = Json.parseObject(request);
                if(parts[5].equals("source"))known(f,"originalReceiptId","originalEarningId");else if(parts[5].equals("follow-up"))known(f,"submissionStatus","submittedOn","submissionReference","followUpOn","followUpNote");else if(parts[5].equals("delete"))known(f);else if(parts[5].equals("amount"))known(f,"amount","unit");else known(f, "status", "amount", "unit", "confirmAmountAndUnit", "rejectionReason");
                var receipt = receipts.findById(receiptId).orElseThrow();
                if (receipt.revision() != expected) {
                    throw new ConcurrentModificationException("Receipt revision changed; refresh before editing.");
                }
                var tx = receipt.transaction();
                var obs = tx.getObservations();
                var earnings = new ArrayList<>(obs.earnings());
                int index = -1;
                for (int i = 0; i < earnings.size(); i++) {
                    if (earnings.get(i).earningId().equals(earningId)) {
                        index = i;
                    }
                }
                if (index < 0) {
                    throw new NoSuchElementException();
                }
                if (f.containsKey("confirmAmountAndUnit") && !(f.get("confirmAmountAndUnit") instanceof Boolean)) {
                    throw new ApiError(400, "Amount confirmation must be a boolean.");
                }
                boolean deleting=parts[5].equals("delete"),correcting=parts[5].equals("amount");
                boolean linking=parts[5].equals("source");
                boolean tracking=parts[5].equals("follow-up");
                var status = linking||tracking||correcting||deleting?earnings.get(index).status():RebateStatus.valueOf(required(f, "status"));
                if((correcting||deleting)&&earnings.get(index).kind()==ReceiptObservations.EarningKind.REGISTER_REWARD&&platform!=null&&platform.earningMatched(earningId))throw new ApiError(409,"Unmatch this Register Reward before changing its amount; the used coupon must keep the same denomination.");
                var updated = linking?ReceiptEarningStatusUpdate.source(earnings.get(index),optional(f,"originalReceiptId")==null?null:UUID.fromString(required(f,"originalReceiptId")),optional(f,"originalEarningId")==null?null:UUID.fromString(required(f,"originalEarningId"))):tracking?ReceiptEarningStatusUpdate.followUp(earnings.get(index),required(f,"submissionStatus"),optional(f,"submittedOn"),optional(f,"submissionReference"),optional(f,"followUpOn"),optional(f,"followUpNote")):deleting?ReceiptEarningStatusUpdate.remove(earnings.get(index)):correcting?ReceiptEarningStatusUpdate.correctAmount(earnings.get(index),required(f,"amount"),required(f,"unit")):ReceiptEarningStatusUpdate.apply(earnings.get(index), status, optional(f, "amount"), optional(f, "unit"), Boolean.TRUE.equals(f.get("confirmAmountAndUnit")), optional(f, "rejectionReason"));
                earnings.set(index, updated);
                tx.setObservations(new ReceiptObservations(obs.purchaseDate(), obs.printedTime(), earnings, obs.unidentifiedRR(), obs.walgreensCash(), obs.savings(), obs.discountKinds(), obs.receiptSavingValuePercent(), obs.promotionLinks(), obs.worksheet(), obs.footerStatus()));
                validateCreditSources(receiptId,tx);
                var now = java.time.Instant.now();
                var result = receipts.update(receiptId, expected, tx, receipt.metadata(), List.of(), new ReceiptWriteRequest(UUID.randomUUID(), now),
                        new ReceiptChangeContext(principal, (linking?"Linked manager reward credit source: ":tracking?"Updated rebate follow-up: ":deleting?"Owner-deleted reward: ":correcting?"Owner-corrected reward amount: ":"Owner-confirmed reward status: ") + earningId + " -> " + status, principal, now));
                return json(200, result);
            });
        }

        if (parts.length == 4 && parts[1].equals("receipts") && parts[3].equals("edit-preview")) {
            requireMethod(method, "POST");
            var old = receipts.findById(UUID.fromString(parts[2])).orElseThrow();
            if (old.revision() != revision(e)) throw new ApiError(409, "Receipt changed; refresh before editing.");
            var correction = ReceiptCorrection.read(old, Json.parseObject(body(e, MAX_BODY)));
            validateCreditSources(old.transactionId(),correction.transaction());
            var candidate = new ReceiptRecord(old.transactionId(), old.status(), old.revision(), correction.transaction(), correction.metadata(), old.evidenceIds());
            return json(200, fields("reviewToken", correction.token(), "totals", ReceiptViews.totals(candidate)));
        }
        if (parts.length == 4 && parts[1].equals("receipts") && parts[3].equals("restore")) {
            requireMethod(method, "POST");
            return write(e, principal, request -> {
                var f = Json.parseObject(request); known(f,"reason","confirmed");
                if (!Boolean.TRUE.equals(f.get("confirmed"))) throw new ApiError(400,"Confirm restoration.");
                var now = java.time.Instant.now();
                return json(200, receipts.restore(UUID.fromString(parts[2]), revision(e), new ReceiptWriteRequest(UUID.randomUUID(),now),
                        new ReceiptChangeContext(principal,required(f,"reason"),principal,now)));
            });
        }
        if (parts.length == 3 && parts[1].equals("receipts")) {
            UUID mutationId = UUID.fromString(parts[2]);
            if (method.equals("PUT")) return write(e, principal, request -> {
                var old = receipts.findById(mutationId).orElseThrow();
                if (old.revision() != revision(e)) throw new ApiError(409,"Receipt changed; refresh before editing.");
                var f = Json.parseObject(request);
                var correction = ReceiptCorrection.read(old,f);
                validateCreditSources(old.transactionId(),correction.transaction());
                if (!correction.token().equals(optional(f,"reviewToken"))) throw new ApiError(409,"Review the updated totals before saving.");
                if (new TransactionCalculator().reconcilePayments(correction.transaction(),correction.metadata()).acknowledgementRequired() && !Boolean.TRUE.equals(f.get("acknowledgeDifference")))
                    throw new ApiError(400,"Acknowledge the recorded difference before saving.");
                var now = java.time.Instant.now();
                return json(200,receipts.update(mutationId,old.revision(),correction.transaction(),correction.metadata(),List.of(),new ReceiptWriteRequest(UUID.randomUUID(),now),
                        new ReceiptChangeContext(principal,correction.reason(),principal,now)));
            });
            if (method.equals("DELETE")) return write(e, principal, request -> {
                var f = Json.parseObject(request); known(f,"reason","confirmed");
                if (!Boolean.TRUE.equals(f.get("confirmed"))) throw new ApiError(400,"Confirm deletion.");
                var now = java.time.Instant.now();
                return json(200,receipts.softDelete(mutationId,revision(e),new ReceiptWriteRequest(UUID.randomUUID(),now),new ReceiptChangeContext(principal,required(f,"reason"),principal,now)));
            });
            requireMethod(method, "GET");
            UUID receiptId = UUID.fromString(parts[2]);
            ReceiptRecord r = receipts.findById(receiptId).or(() -> receipts.findDeletedById(receiptId)).orElseThrow();
            e.getResponseHeaders().set("ETag", "\"" + r.revision() + "\"");
            return json(200, fields("transactionId", r.transactionId(), "revision", r.revision(),
                    "status", r.status(), "metadata", r.metadata(), "evidenceIds", r.evidenceIds(),
                    "creditSettlements", creditSettlements(r), "paymentRoles", r.transaction().getPayments().stream().collect(java.util.stream.Collectors.toMap(p->p.getPaymentId().toString(),p->new TransactionCalculator().paymentRole(r.transaction(),p))), "rrUses", platform == null ? List.of() : platform.rewardUses(r.transactionId()), "contents", ReceiptSnapshot.from(r.transaction()), "editDiscounts", ReceiptCorrection.editDiscounts(r), "preservePriceLineIds", r.transaction().getPurchaseItems().stream().filter(item -> item.getRegularUnitPrice() != null && item.getRegularUnitPrice().compareTo(item.getSaleUnitPrice()) < 0).map(PurchaseItem::getLineItemId).toList()));
        }
        if (parts.length == 4
                && parts[1].equals("shopping-plans")
                && parts[3].equals("checkout")) {

            requirePlatform();

            UUID planId = UUID.fromString(parts[2]);

            if (method.equals("GET")) {
                return json(200, platform.checkout(planId));
            }

            requireMethod(method, "PUT");

            return write(e, principal, request -> {
                long expected = checkoutRevision(e);
                Map<String, Object> values
                        = Json.parseObject(request);

                return json(200, platform.saveCheckout(
                        planId,
                        expected,
                        values,
                        principal
                ));
            });
        }
        if(path.equals("/shopping-plans/match-offers")) {
            requireMethod(method,"POST");var f=Json.parseObject(body(e,1024*1024));known(f,"scenario","retailer","day","couponIds");
            var available=ReceiptInput.ids(f,"couponIds").stream().distinct().limit(100).map(coupons::findById).flatMap(Optional::stream).toList();
            return json(200,ShoppingOfferMatch.match(parseScenario(f.get("scenario")),optional(f,"retailer"),optional(f,"day")==null?null:LocalDate.parse(optional(f,"day")),available));
        }
        if(path.equals("/shopping-plans/compare")) {
            requireMethod(method,"POST");var f=Json.parseObject(body(e,1024*1024));known(f,"a","b","storeA","storeB");
            return json(200,ShoppingReview.compare(parseScenario(f.get("a")),optional(f,"storeA"),parseScenario(f.get("b")),optional(f,"storeB")));
        }
        if(parts.length==3&&parts[1].equals("rebate-programs")) {
            requirePlatform();
            if(method.equals("DELETE"))return write(e,principal,request->{var f=Json.parseObject(request);known(f,"expectedToken");platform.deletePromotion(UUID.fromString(parts[2]),required(f,"expectedToken"));return json(200,fields("deleted",true));});
            requireMethod(method,"PUT");
            return write(e,principal,request->{var f=Json.parseObject(request);known(f,"name","threshold","giftCardValue","start","end","terms","status","expectedToken","planningRules");
                if(required(f,"name").length()>200||java.util.Objects.toString(f.get("terms"),"").length()>4000)throw new ApiError(400,"Promotion name or terms are too long.");
                platform.savePromotion(UUID.fromString(parts[2]),required(f,"name"),ReceiptInput.decimal(f,"threshold",false),ReceiptInput.decimal(f,"giftCardValue",false),LocalDate.parse(required(f,"start")),LocalDate.parse(required(f,"end")),java.util.Objects.toString(f.get("terms"),""),required(f,"status"),required(f,"expectedToken"),promotionPlanningRules(f.get("planningRules")));return json(200,fields("saved",true));
            });
        }
        if(parts.length==5&&parts[1].equals("rebate-programs")&&parts[3].equals("receipts")){
            requirePlatform();
            if(method.equals("DELETE"))return write(e,principal,request->{var f=Json.parseObject(request);known(f,"expectedContribution");platform.unlinkRebateReceipt(UUID.fromString(parts[2]),UUID.fromString(parts[4]),required(f,"expectedContribution"));return json(200,Map.of("unlinked",true));});
            requireMethod(method,"PUT");
            return write(e,principal,request->{
                var f=Json.parseObject(request);known(f,"lineIds","netAmount","note","submitted","submittedOn","submissionReference","expectedContribution");
                String date=optional(f,"submittedOn"),reference=optional(f,"submissionReference");
                if(reference!=null&&reference.length()>500||optional(f,"note")!=null&&required(f,"note").length()>2000)throw new ApiError(400,"Submission reference or note is too long.");
                return json(200,platform.saveRebateReceipt(UUID.fromString(parts[2]),UUID.fromString(parts[4]),revision(e),ReceiptInput.ids(f,"lineIds"),ReceiptInput.decimal(f,"netAmount",false),optional(f,"note"),ReceiptInput.flag(f,"submitted"),date==null?null:LocalDate.parse(date),reference,required(f,"expectedContribution")));
            });
        }
        if(path.equals("/shopping-plans/reconcile")) {
            requireMethod(method,"POST");var f=Json.parseObject(body(e));known(f,"beginning","earned","used","reported");
            return json(200,ShoppingReview.reconcile(ReceiptInput.decimal(f,"beginning",true),ReceiptInput.decimal(f,"earned",true),ReceiptInput.decimal(f,"used",true),ReceiptInput.decimal(f,"reported",true)));
        }
        if(path.equals("/shopping-plans/weekly-preview")) {
            requireMethod(method,"POST");var f=Json.parseObject(body(e,1024*1024));known(f,"candidates","rewards","walgreensCash","budget","mode","day","requiredItems","includeOptional","walmartCash","allowRewardsFromEarlierTransactions","useGiftCards","giftCardFirst","boosterId");
            var requiredItems=ReceiptInput.rows(f,"requiredItems",100).stream().map(r->{known(r,"candidateId","lineId","quantity");return new WeeklyDealPlanner.RequiredItem(required(r,"candidateId"),ReceiptInput.uuid(r,"lineId"),Math.toIntExact(number(r.get("quantity"))));}).toList();
            var candidates=ReceiptInput.rows(f,"candidates",50).stream().map(r->{
                var raw=r.get("scenario");boolean requiredCandidate=requiredItems.stream().anyMatch(item->item.candidateId().equals(required(r,"id")));
                boolean missingPrice=raw instanceof Map<?,?> scenario&&scenario.get("items") instanceof List<?> lines&&lines.stream().anyMatch(line->line instanceof Map<?,?> item&&(item.get("regular")==null||item.get("regular").toString().isBlank())&&(item.get("sale")==null||item.get("sale").toString().isBlank()));
                return new WeeklyDealPlanner.Candidate(required(r,"id"),required(r,"name"),optional(r,"store"),requiredCandidate&&(raw==null||missingPrice)?null:parseScenario(raw),optional(r,"group"));}).toList();
            var rewards=ReceiptInput.rows(f,"rewards",30).stream().map(r->{String type=required(r,"type");if(!Set.of("REGISTER_REWARD","ECB").contains(type))throw new ApiError(400,"Unsupported reward type.");return new WeeklyDealPlanner.Reward(required(r,"id"),required(r,"description"),type,required(r,"store"),ReceiptInput.decimal(r,"amount",false),optional(r,"expires")==null?null:LocalDate.parse(required(r,"expires")),optional(r,"registerRewardType"),optional(r,"originProduct"),optional(r,"originBrand"),optional(r,"originProgram"),optional(r,"originPromotion"));}).toList();
            var day=LocalDate.parse(required(f,"day"));boolean cards=ReceiptInput.flag(f,"useGiftCards");
            var planningWorkspace=platform==null?Map.<String,Object>of():platform.workspace(day);
            @SuppressWarnings("unchecked") var instruments=(List<Map<String,Object>>)planningWorkspace.getOrDefault("rewardInstruments",List.of());
            var funding=new java.util.ArrayList<WeeklyDealPlanner.Reward>();
            var ecbBalance=planningBalance("ECB",rewards.stream().filter(r->r.type().equals("ECB")).map(WeeklyDealPlanner.Reward::amount).reduce(BigDecimal.ZERO,BigDecimal::add),day,instruments,planningWorkspace);
            for(var reward:rewards.stream().sorted(java.util.Comparator.comparing(WeeklyDealPlanner.Reward::expires,java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())).thenComparing(WeeklyDealPlanner.Reward::id)).toList()){
                if(reward.type().equals("ECB")){if(!WeeklyDealPlanner.retailer(reward.store()).equals("CVS"))continue;if(reward.expires()!=null&&reward.expires().isBefore(day)||reward.amount().compareTo(ecbBalance)>0)continue;ecbBalance=ecbBalance.subtract(reward.amount());}funding.add(reward);
            }
            var walmart=ReceiptInput.decimal(f,"walmartCash",true);
            walmart=planningBalance("Walmart Cash",walmart==null?BigDecimal.ZERO:walmart,day,instruments,planningWorkspace);
            var wcBalance=planningBalance("Walgreens Cash",ReceiptInput.decimal(f,"walgreensCash",false),day,instruments,planningWorkspace);
            if(walmart!=null&&walmart.signum()>0)funding.add(new WeeklyDealPlanner.Reward("balance:WALMART_CASH","Recorded Walmart Cash balance","WALMART_CASH","Walmart",walmart,null));
            if(cards){requirePlatform();for(var card:instruments){
                if(!"GIFT_CARD".equals(card.get("kind"))||"ARCHIVED".equals(card.get("status"))||!Boolean.TRUE.equals(card.get("historyComplete"))||!(card.get("calculated") instanceof BigDecimal balance)||balance.signum()<=0)continue;
                if(card.get("balanceDate") instanceof LocalDate date&&date.isAfter(day)||card.get("openingDate") instanceof LocalDate openingDate&&openingDate.isAfter(day))continue;
                String store=card.get("provider").toString();var brand=java.util.regex.Pattern.compile("(?i)\\bBrand:\\s*([^|]+)").matcher(store);if(brand.find())store=brand.group(1).trim();if(!Set.of("WALGREENS","WALMART","CVS").contains(WeeklyDealPlanner.retailer(store)))continue;
                String last=Objects.toString(card.get("lastFour"),"");if(last.isEmpty()){var number=java.util.regex.Pattern.compile("(?i)\\bNumber:\\s*([^|]+)").matcher(card.get("provider").toString());if(number.find()){String digits=number.group(1).replaceAll("[^0-9]","");last=digits.substring(Math.max(0,digits.length()-4));}}
                String description=Objects.toString(card.get("nickname"),store)+(last.isEmpty()?"":" · ending "+last);
                funding.add(new WeeklyDealPlanner.Reward(card.get("id").toString(),description,"GIFT_CARD",store,balance,null));
            }}
            WeeklyDealPlanner.Target target=null;
            if(optional(f,"boosterId")!=null){requirePlatform();target=planningBooster(required(f,"boosterId"),day,planningWorkspace);}
            var catalog=new java.util.ArrayList<ClippedCoupon>();var couponIds=new java.util.HashSet<UUID>();
            if(planningWorkspace.get("coupons") instanceof List<?> storedCoupons)for(var raw:storedCoupons)if(raw instanceof Map<?,?> coupon&&coupon.get("couponId")!=null&&"CLIPPED".equals(Objects.toString(coupon.get("status"),"")))couponIds.add(UUID.fromString(coupon.get("couponId").toString()));
            for(var candidate:candidates)if(candidate.scenario()!=null)for(var coupon:candidate.scenario().coupons())if(coupon.savedCouponId()!=null)couponIds.add(coupon.savedCouponId());
            for(var couponId:couponIds)coupons.findById(couponId).ifPresent(catalog::add);
            var options=new WeeklyDealPlanner.Options(ReceiptInput.flag(f,"allowRewardsFromEarlierTransactions"),cards,ReceiptInput.flag(f,"giftCardFirst"),target,catalog);
            return json(200,WeeklyDealPlanner.plan(candidates,funding,wcBalance,ReceiptInput.decimal(f,"budget",false),required(f,"mode"),day,requiredItems,!f.containsKey("includeOptional")||ReceiptInput.flag(f,"includeOptional"),options));
        }
        if(path.equals("/shopping-plans/convert-image")) {
            requireMethod(method,"POST");authorize(e,true);var f=Json.parseObject(body(e,8*1024*1024));known(f,"mediaType","contentBase64");
            try{return json(200,DealImageConversion.convert(required(f,"mediaType"),required(f,"contentBase64")));}
            catch(Exception failure){throw new ApiError(400,"Photo conversion failed. Export JPEG from Photos and retry.");}
        }
        if(path.equals("/shopping-plans/import-preview")) {
            requireMethod(method,"POST");authorize(e,true);
            var f=Json.parseObject(body(e,15*1024*1024));known(f,"text","mediaType","contentBase64","files","reader");
            String text=optional(f,"text");var attachments=new ArrayList<ShoppingPlan.PlanningSource>();int total=0;
            if(optional(f,"contentBase64")!=null)attachments.add(new ShoppingPlan.PlanningSource("",optional(f,"mediaType"),optional(f,"contentBase64")));
            for(var file:ReceiptInput.rows(f,"files",10)){known(file,"mediaType","contentBase64");attachments.add(new ShoppingPlan.PlanningSource("",required(file,"mediaType"),required(file,"contentBase64")));}
            for(var file:attachments)total+=java.util.Base64.getDecoder().decode(file.contentBase64()).length;
            if(total>5*1024*1024)throw new ApiError(400,"Use at most 5 MiB of images per deal so the draft can be saved.");
            var source=attachments.isEmpty()?new ShoppingPlan.PlanningSource(text,null,null):new ShoppingPlan.PlanningSource(text,attachments.getFirst().mediaType(),attachments.getFirst().contentBase64(),attachments.subList(1,attachments.size()));
            if(!attachments.isEmpty()&&!"ocr".equals(optional(f,"reader"))) {
                try{return json(200,LocalDealReader.read(text,source));}
                catch(IllegalArgumentException failure){throw new ApiError(400,failure.getMessage());}
                catch(Exception failure){throw new ApiError(400,"Local deal reading could not finish. Your image stays attached; retry or enter details manually.");}
            }
            for(var file:attachments) {
                try {String recognized=ReceiptImport.ocr(java.util.Base64.getDecoder().decode(file.contentBase64()),true);text=text==null?recognized:text+"\n"+recognized;}
                catch(ApiError failure){throw failure;}
                catch(Exception failure){throw new ApiError(400,"Screenshot could not be read. Try a clearer JPEG or PNG.");}
            }
            source=new ShoppingPlan.PlanningSource(text,source.mediaType(),source.contentBase64(),source.earlier());
            var imported=ShoppingNoteInput.parse(text,source);imported.put("reader","local");
            return json(200,imported);
        }

        if(path.equals("/shopping-plans/scenario/preview")) {
            requireMethod(method,"POST");var fields=Json.parseObject(body(e,1024*1024));known(fields,"scenario","retailer");return json(200,parseScenario(fields.get("scenario")).estimate(optional(fields,"retailer")));
        }
        if(parts.length==4&&parts[1].equals("shopping-plans")&&parts[3].equals("receipt-link")) {
            requireMethod(method,"POST");requirePlatform();
            return write(e,principal,request->{var f=Json.parseObject(request);known(f,"receiptId","receiptRevision");
                UUID receiptId=ReceiptInput.uuid(f,"receiptId");long receiptRevision=Long.parseLong(required(f,"receiptRevision"));
                platform.linkShoppingPlanReceipt(UUID.fromString(parts[2]),revision(e),receiptId,receiptRevision,principal);
                return json(200,fields("linked",true,"receiptId",receiptId));
            });
        }
        if (path.equals("/shopping-plans")) {
            requireMethod(method, "POST");
            return write(e, principal, 8*1024*1024, request -> {
                ShoppingPlan plan = parsePlan(null, Json.parseObject(request), 1, null);
                plans.save(plan, principal);
                return json(201, plan);
            });
        }
        if (parts.length == 3 && parts[1].equals("shopping-plans")) {
            UUID id = UUID.fromString(parts[2]);
            if (method.equals("GET")) {
                ShoppingPlan plan = plans.findById(id).orElseThrow();
                e.getResponseHeaders().set("ETag", "\"" + plan.revision() + "\"");
                return json(200, plan);
            }
            if (method.equals("DELETE")) {
                requirePlatform();
                return write(e, principal, request -> {
                    known(Json.parseObject(request));
                    platform.deleteShoppingPlan(id, revision(e), principal);
                    return json(200, fields("deleted", true));
                });
            }
            requireMethod(method, "PUT");
            return write(e, principal, 8*1024*1024, request -> {
                long expected = revision(e);
                ShoppingPlan previous = plans.findById(id).orElseThrow();
                if (previous.revision() != expected) {
                    throw new ConcurrentModificationException();
                }
                ShoppingPlan plan = parsePlan(id, Json.parseObject(request), Math.addExact(expected, 1), previous);
                return json(200, plans.update(plan, expected, principal, "API update"));
            });
        }
        if (path.equals("/clipped-coupons")) {
            if (method.equals("POST")) {
                requirePlatform();
                return write(e, principal, 8 * 1024 * 1024, request -> {
                    var copies = CouponInput.read(Json.parseObject(request));
                    platform.saveCoupons(copies, principal);
                    return json(201, copies.stream().map(CouponApiService::couponFields).toList());
                });
            }
            requireMethod(method, "GET");
            String provider = query(e.getRequestURI(), "provider");
            if (provider == null || provider.isBlank()) {
                throw new ApiError(400, "provider is required.");
            }
            return json(200, coupons.findByProvider(provider).stream().map(CouponApiService::couponFields).toList());
        }
        if (parts.length == 3 && parts[1].equals("clipped-coupons")) {
            requireMethod(method, "GET");
            ClippedCoupon coupon = coupons.findById(UUID.fromString(parts[2])).orElseThrow();
            return json(200, couponFields(coupon));
        }
        if (parts.length == 4 && parts[1].equals("clipped-coupons") && parts[3].equals("redeem")) {
            requireMethod(method, "POST");
            UUID id = UUID.fromString(parts[2]);
            return write(e, principal, request -> {
                long expected = revision(e);
                Map<String, Object> f = Json.parseObject(request);
                known(f, "transactionId", "redeemedOn");
                UUID receiptId = UUID.fromString(required(f, "transactionId"));
                receipts.findById(receiptId).orElseThrow();
                coupons.findById(id).orElseThrow();
                try {
                    return json(200, couponFields(coupons.redeem(id, receiptId,
                            LocalDate.parse(required(f, "redeemedOn")), expected, principal)));
                } catch (IllegalStateException x) {
                    throw new ApiError(409, "Coupon cannot be redeemed in its current state.");
                }
            });
        }
        throw new ApiError(404, "Unknown endpoint.");
    }

    /**
     * Serialize retries and writes: cache key includes authenticated role,
     * route and revision.
     */
    private Response write(HttpExchange e, String principal, Work operation) throws IOException {
        return write(e, principal, MAX_BODY, operation);
    }

    private synchronized Response write(HttpExchange e, String principal, int limit, Work operation) throws IOException {
        String key = e.getRequestHeaders().getFirst("Idempotency-Key");
        if (key == null || key.isBlank() || key.length() > 200) {
            throw new ApiError(400, "Idempotency-Key is required (maximum 200 characters).");
        }
        String requestBody = body(e, limit);
        String signature = e.getRequestMethod() + "\n" + e.getRequestURI() + "\n"
                + e.getRequestHeaders().getFirst("If-Match-Revision") + "\n" + requestBody;
        if (requestStore != null) {
            var result = requestStore.execute(principal, key, signature, () -> {
                Response response = operation.run(requestBody);
                return new ApiRequestStore.Result(response.status, response.body);
            });
            return new Response(result.status(), result.body());
        }
        String scopedKey = principal + ":" + key;
        Cached old = idempotency.get(scopedKey);
        if (old != null) {
            if (!old.signature.equals(signature)) {
                throw new ApiError(409, "Idempotency key was used for a different request.");
            }
            return old.response;
        }
        if (idempotency.size() >= MAX_KEYS) {
            throw new ApiError(503, "API retry cache is full; restart during a maintenance window.");
        }
        Response result = operation.run(requestBody);
        idempotency.put(scopedKey, new Cached(signature, result));
        return result;
    }

    private ShoppingPlan parsePlan(UUID pathId, Map<String, Object> f, long next, ShoppingPlan previous) {
        known(f, "planId", "name", "retailer", "shoppingDate", "revision", "plannedPurchaseIds", "scenario","organization");
        UUID id = pathId == null ? UUID.fromString(required(f, "planId")) : pathId;
        if (f.containsKey("planId") && !id.toString().equals(required(f, "planId"))) {
            throw new ApiError(400, "Plan ID must match the URL.");
        }
        if (f.containsKey("revision") && number(f.get("revision")) != next) {
            throw new ApiError(400, "Body revision must be the next revision.");
        }
        List<UUID> purchases = previous == null ? List.of() : previous.plannedPurchaseIds();
        if (f.containsKey("plannedPurchaseIds")) {
            if (!(f.get("plannedPurchaseIds") instanceof List<?> list)) {
                throw new ApiError(400, "plannedPurchaseIds must be an array.");
            }
            purchases = list.stream().map(v -> UUID.fromString(asText(v))).toList();
            // These repositories do not atomically validate or synchronize purchase ownership.
            if (previous == null ? !purchases.isEmpty() : !purchases.equals(previous.plannedPurchaseIds())) {
                throw new ApiError(400, "Purchase links must be managed through the planned-purchase workflow; this endpoint preserves existing links.");
            }
        }
        String date = f.containsKey("shoppingDate") ? optional(f, "shoppingDate") : previous == null || previous.shoppingDate() == null ? null : previous.shoppingDate().toString();
        var organization=f.containsKey("organization")?planningOrganization(f.get("organization")):previous==null?null:previous.organization();
        if(organization!=null) {
            if(id.equals(organization.alternativeOf())||organization.rewardSources().contains(id))throw new ApiError(400,"A plan cannot depend on or be an alternative to itself.");
            for(var sourceId:organization.rewardSources()) {
                var source=plans.findById(sourceId).orElseThrow();
                if(organization.stage().equals("TRIP")&&(source.organization()==null||source.organization().checkoutOrder()==null||organization.checkoutOrder()==null||source.organization().checkoutOrder()>=organization.checkoutOrder()))throw new ApiError(400,"Reward sources must come earlier in checkout order.");
            }
        }
        return new ShoppingPlan(id, required(f, "name"), f.containsKey("retailer") ? optional(f, "retailer") : previous == null ? null : previous.retailer(),
                date == null ? null : LocalDate.parse(date), purchases, next,f.containsKey("scenario")?(f.get("scenario")==null?null:parseScenario(f.get("scenario"))):previous==null?null:previous.scenario(),previous==null?null:previous.purchasedReceiptId(),organization);
    }

    @SuppressWarnings("unchecked") private BigDecimal planningBalance(String provider,BigDecimal requested,LocalDate day,List<Map<String,Object>> instruments,Map<String,Object> workspace){
        if(requested.signum()<0)throw new ApiError(400,"Reward balances must be nonnegative.");
        if(platform==null)return requested;
        var individual=instruments.stream().filter(row->"ACCOUNT_REWARD".equals(row.get("kind"))&&provider.equalsIgnoreCase(ProviderBalanceSummary.accountName(row.get("provider").toString()))&&"USD".equals(row.get("unit"))&&((List<Map<String,Object>>)row.getOrDefault("events",List.of())).stream().anyMatch(event->"EXCHANGE".equals(event.get("kind")))).toList();
        if(!individual.isEmpty())return individual.stream().filter(row->!"ARCHIVED".equals(row.get("status"))&&Boolean.TRUE.equals(row.get("historyComplete"))&&row.get("calculated") instanceof BigDecimal&&(!(row.get("balanceDate") instanceof LocalDate date)||!date.isAfter(day))).map(row->((BigDecimal)row.get("calculated")).max(BigDecimal.ZERO)).reduce(BigDecimal.ZERO,BigDecimal::add);
        var balances=(List<Map<String,Object>>)workspace.getOrDefault("balances",List.of());
        for(var row:balances)if(provider.equals(ProviderBalanceSummary.accountName(row.get("provider").toString()))){
            Object raw=row.get("balance");BigDecimal amount=raw instanceof ProviderBalanceSummary.Balance balance?balance.endUsd():raw instanceof Map<?,?> balance&&balance.get("endUsd") instanceof BigDecimal value?value:null;
            return amount==null?BigDecimal.ZERO:requested.min(amount.max(BigDecimal.ZERO));
        }
        return BigDecimal.ZERO;
    }
    @SuppressWarnings("unchecked") private WeeklyDealPlanner.Target planningBooster(String id,LocalDate day,Map<String,Object> workspace){
        var rows=(List<Map<String,Object>>)workspace.getOrDefault("promotions",List.of());
        var row=rows.stream().filter(p->id.equals(p.get("id").toString())).findFirst().orElseThrow(()->new ApiError(400,"Selected Spend Booster is no longer available."));
        var rules=(Map<String,String>)row.getOrDefault("planningRules",Map.of());
        if(!"SPEND_BOOSTER".equals(rules.get("kind"))||!"true".equals(rules.get("verified"))||!Set.of("RECEIPT","SKUS","CATEGORIES").contains(rules.getOrDefault("scope",""))||!Set.of("YES","NO").contains(rules.getOrDefault("wcAllowed",""))||!Set.of("YES","NO").contains(rules.getOrDefault("rrAllowed",""))||!Set.of("ONCE","REPEAT").contains(rules.getOrDefault("usageLimit","")))throw new ApiError(400,"Spend Booster Needs Review: record and verify scope, spend basis, payment terms and use limits before targeting it.");
        var start=LocalDate.parse(row.get("start").toString());var end=LocalDate.parse(row.get("end").toString());
        if(day.isBefore(start)||day.isAfter(end)||Set.of("USED","EXPIRED","ARCHIVED").contains(row.get("status").toString()))throw new ApiError(400,"This Spend Booster is outside its dates, already used, or archived.");
        var values=new java.util.HashSet<String>();for(var value:rules.getOrDefault("scopeValues","").split("\\R"))if(!value.isBlank())values.add(rules.get("scope").equals("SKUS")?WeeklyDealPlanner.skuKey(value):value.trim());
        if(!rules.get("scope").equals("RECEIPT")&&values.isEmpty())throw new ApiError(400,"Spend Booster Needs Review: record qualifying products or categories.");
        var categories=new java.util.HashMap<String,String>();for(var inventory:(List<Map<String,Object>>)workspace.getOrDefault("inventory",List.of()))if(inventory.get("sku")!=null&&inventory.get("category")!=null)categories.put(WeeklyDealPlanner.skuKey(inventory.get("sku").toString()),inventory.get("category").toString());
        int used=rules.getOrDefault("usedCount","").isBlank()?0:Integer.parseInt(rules.get("usedCount"));
        int limit=rules.get("usageLimit").equals("ONCE")?1:Integer.MAX_VALUE;
        if(!rules.getOrDefault("maxUses","").isBlank()){
            if(rules.getOrDefault("usedCount","").isBlank())throw new ApiError(400,"Spend Booster Needs Review: record the number of uses already completed before applying a recorded use limit.");
            limit=Math.min(limit,Integer.parseInt(rules.get("maxUses")));
        }
        if(used<0||limit<1||used>=limit)throw new ApiError(400,"The recorded Spend Booster use limit has been reached or is invalid.");
        var promotion=new ShoppingPlan.ScenarioPromotion(UUID.fromString(id),row.get("name").toString(),rules.getOrDefault("rewardType","WALGREENS_CASH"),new BigDecimal(row.get("giftCardValue").toString()),0,new BigDecimal(row.get("threshold").toString()),rules.getOrDefault("spendBasis","UNKNOWN"),false,List.of(),true,true,false,rules.get("prohibitedRegisterRewardProgram"),rules.get("originProduct"),rules.get("originBrand"),rules.get("originProgram"),id,true,rules.getOrDefault("giftCardRule","UNKNOWN"),"true".equals(rules.get("rollingAllowed")),rules.get("earnedRegisterRewardType"),rules.get("rrAllowed").equals("YES"),null);
        return new WeeklyDealPlanner.Target(promotion,rules.get("scope"),values,categories,ruleLines(rules,"excludedSkus",true),ruleLines(rules,"excludedCategories",false),limit-used);
    }
    private static Set<String> ruleLines(Map<String,String> rules,String field,boolean sku){var values=new java.util.HashSet<String>();for(var value:rules.getOrDefault(field,"").split("\\R"))if(!value.isBlank())values.add(sku?WeeklyDealPlanner.skuKey(value):value.trim());return values;}
    private static Map<String,String> promotionPlanningRules(Object raw){
        if(raw==null)return null;if(!(raw instanceof Map<?,?> map)||map.size()>25)throw new IllegalArgumentException("Invalid booster rules.");
        var result=new java.util.TreeMap<String,String>();for(var e:map.entrySet()){if(!(e.getKey() instanceof String key)||!(e.getValue() instanceof String value)||value.length()>4000)throw new IllegalArgumentException("Enter recorded booster terms as text.");result.put(key,value);}
        return result;
    }
    static ShoppingPlan.Scenario parseScenario(Object raw) {
        try{return parseScenarioFields(raw);}catch(IllegalArgumentException error){throw new ApiError(400,"Shopping deal: "+error.getMessage());}
    }
    private static ShoppingPlan.Scenario parseScenarioFields(Object raw) {
        if(!(raw instanceof Map<?,?>))throw new IllegalArgumentException("Complete the planning scenario.");
        @SuppressWarnings("unchecked") var f=(Map<String,Object>)raw;
        known(f,"items","coupons","promotions","tax","walgreensCashUsed","registerRewardsUsed","giftCardUsed","manualOop","paymentMethod","fundingNotes","taxRate","payments","source","walgreensCashIncrement","walgreensCashLimit");
        var items=ReceiptInput.rows(f,"items",100).stream().map(r->{known(r,"id","name","sku","regular","sale","quantity","memberRewardRate","priceRule");return new ShoppingPlan.Line(ReceiptInput.uuid(r,"id"),required(r,"name"),optional(r,"sku"),ReceiptInput.decimal(r,"regular",true),ReceiptInput.decimal(r,"sale",true),Math.toIntExact(number(r.get("quantity"))),r.get("memberRewardRate")==null?null:Math.toIntExact(number(r.get("memberRewardRate"))),planningPriceRule(r.get("priceRule")));}).toList();
        var coupons=ReceiptInput.rows(f,"coupons",100).stream().map(r->{known(r,"id","description","amount","requiredQuantity","lineIds","savedCouponId","kind","wholeReceipt");return new ShoppingPlan.ScenarioCoupon(ReceiptInput.uuid(r,"id"),required(r,"description"),ReceiptInput.decimal(r,"amount",false),Math.toIntExact(number(r.get("requiredQuantity"))),ReceiptInput.ids(r,"lineIds"),optional(r,"savedCouponId")==null?null:ReceiptInput.uuid(r,"savedCouponId"),optional(r,"kind"),r.containsKey("wholeReceipt")&&ReceiptInput.flag(r,"wholeReceipt"));}).toList();
        var promotions=ReceiptInput.rows(f,"promotions",100).stream().map(r->{known(r,"id","description","rewardType","rewardAmount","requiredQuantity","requiredSpend","spendBasis","allowsWalgreensCash","allowsWalmartCash","lineIds","wholeReceipt","paymentRulesReviewed","restrictSameRegisterReward","prohibitedRegisterRewardProgram","originProduct","originBrand","originProgram","originPromotion","giftCardRule","rollingAllowed","earnedRegisterRewardType","allowsRegisterRewards","allowsEcb");return new ShoppingPlan.ScenarioPromotion(ReceiptInput.uuid(r,"id"),required(r,"description"),required(r,"rewardType"),ReceiptInput.decimal(r,"rewardAmount",false),Math.toIntExact(number(r.get("requiredQuantity"))),ReceiptInput.decimal(r,"requiredSpend",false),required(r,"spendBasis"),ReceiptInput.flag(r,"allowsWalgreensCash"),ReceiptInput.ids(r,"lineIds"),r.containsKey("wholeReceipt")&&ReceiptInput.flag(r,"wholeReceipt"),!r.containsKey("allowsWalmartCash")||ReceiptInput.flag(r,"allowsWalmartCash"),r.containsKey("restrictSameRegisterReward")&&ReceiptInput.flag(r,"restrictSameRegisterReward"),optional(r,"prohibitedRegisterRewardProgram"),optional(r,"originProduct"),optional(r,"originBrand"),optional(r,"originProgram"),optional(r,"originPromotion"),r.containsKey("paymentRulesReviewed")&&r.get("paymentRulesReviewed")!=null?ReceiptInput.flag(r,"paymentRulesReviewed"):null,optional(r,"giftCardRule"),r.get("rollingAllowed")==null?null:ReceiptInput.flag(r,"rollingAllowed"),optional(r,"earnedRegisterRewardType"),r.get("allowsRegisterRewards")==null?null:ReceiptInput.flag(r,"allowsRegisterRewards"),r.get("allowsEcb")==null?null:ReceiptInput.flag(r,"allowsEcb"));}).toList();
        return new ShoppingPlan.Scenario(items,coupons,promotions,ReceiptInput.decimal(f,"tax",false),ReceiptInput.decimal(f,"walgreensCashUsed",false),ReceiptInput.decimal(f,"registerRewardsUsed",false),ReceiptInput.decimal(f,"giftCardUsed",false),ReceiptInput.decimal(f,"manualOop",true),required(f,"paymentMethod"),optional(f,"fundingNotes"),ReceiptInput.decimal(f,"taxRate",true),!f.containsKey("payments")||f.get("payments")==null?null:ReceiptInput.rows(f,"payments",100).stream().map(r->{known(r,"id","type","amount","notes","registerRewardType","rewardSourceId","originProduct","originBrand","originProgram","originPromotion");return new ShoppingPlan.ScenarioPayment(ReceiptInput.uuid(r,"id"),required(r,"type"),ReceiptInput.decimal(r,"amount",false),optional(r,"notes"),optional(r,"registerRewardType"),optional(r,"rewardSourceId")==null?null:ReceiptInput.uuid(r,"rewardSourceId"),optional(r,"originProduct"),optional(r,"originBrand"),optional(r,"originProgram"),optional(r,"originPromotion"));}).toList(),planningSource(f.get("source")),ReceiptInput.decimal(f,"walgreensCashIncrement",true),ReceiptInput.decimal(f,"walgreensCashLimit",true));
    }

    private ShoppingPlan.Organization planningOrganization(Object raw) {
        if(raw==null)return null;
        if(!(raw instanceof Map<?,?>))throw new IllegalArgumentException("Invalid trip details.");
        @SuppressWarnings("unchecked") var f=(Map<String,Object>)raw;
        known(f,"stage","alternativeOf","checkoutOrder","rewardSources");
        var sources=ReceiptInput.ids(f,"rewardSources");
        for(var source:sources)plans.findById(source).orElseThrow(()->new IllegalArgumentException("Reward source plan no longer exists."));
        return new ShoppingPlan.Organization(required(f,"stage"),optional(f,"alternativeOf")==null?null:UUID.fromString(optional(f,"alternativeOf")),f.get("checkoutOrder")==null?null:Math.toIntExact(number(f.get("checkoutOrder"))),sources);
    }
    private static ShoppingPlan.PriceRule planningPriceRule(Object raw) {
        if(raw==null)return null;
        if(!(raw instanceof Map<?,?>))throw new IllegalArgumentException("Invalid sale rule.");
        @SuppressWarnings("unchecked") var f=(Map<String,Object>)raw;
        known(f,"type","bundlePrice","lineIds");return new ShoppingPlan.PriceRule(required(f,"type"),ReceiptInput.decimal(f,"bundlePrice",true),ReceiptInput.ids(f,"lineIds"));
    }
    private static ShoppingPlan.PlanningSource planningSource(Object raw) { return planningSource(raw,0); }
    private static ShoppingPlan.PlanningSource planningSource(Object raw,int depth) {
        if(depth>1)throw new IllegalArgumentException("Invalid nested planning source.");
        if(raw==null)return null;
        if(!(raw instanceof Map<?,?>))throw new IllegalArgumentException("Invalid planning source.");
        @SuppressWarnings("unchecked") var f=(Map<String,Object>)raw;
        known(f,"text","mediaType","contentBase64","earlier","purchaseChannel");return new ShoppingPlan.PlanningSource(optional(f,"text"),optional(f,"mediaType"),optional(f,"contentBase64"),ReceiptInput.rows(f,"earlier",20).stream().map(row->planningSource(row,depth+1)).toList(),optional(f,"purchaseChannel"));
    }

    private String authorize(HttpExchange e, boolean write) {
        String header = e.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new ApiError(401, "Bearer token required.");
        }
        String supplied = header.substring(7);
        boolean owner = equal(token, supplied);
        boolean admin = adminToken != null && equal(adminToken, supplied);
        if (!owner && !admin) {
            throw new ApiError(401, "Invalid bearer token.");
        }
        if (write && adminToken != null && !admin) {
            throw new ApiError(403, "Write authorization required.");
        }
        // Audit identity comes from server configuration, never the caller's X-Actor header.
        return admin ? "api-owner" : adminToken == null ? "api-owner" : "api-reader";
    }

    private ReceiptInput receiptInput(String json) {
        try {
            var input=ReceiptInput.read(Json.parseObject(json));validateCreditSources(input.id(),input.transaction());return input;
        } catch (IllegalArgumentException ex) {
            throw new ApiError(400, ex.getMessage() == null ? "Invalid receipt fields." : ex.getMessage());
        }
    }

    private List<Object> creditSettlements(ReceiptRecord original){var result=new ArrayList<Object>();for(var earning:original.transaction().getObservations().earnings()){var credits=new ArrayList<Object>();var total=BigDecimal.ZERO;for(var receipt:receipts.findRewardCredits(original.transactionId(),earning.earningId()))if(receipt.status()==ReceiptStatus.ACTIVE)for(var credit:receipt.transaction().getObservations().earnings()){var d=credit.details();if(d!=null&&original.transactionId().equals(d.originalReceiptId())&&earning.earningId().equals(d.originalEarningId())&&credit.status()==RebateStatus.CREDITED&&credit.confirmed()!=null){total=total.add(credit.confirmed().amount());credits.add(fields("receiptId",receipt.transactionId(),"receiptNumber",receipt.metadata()==null?null:receipt.metadata().receiptNumber(),"earningId",credit.earningId(),"amount",credit.confirmed().amount()));}}if(!credits.isEmpty())result.add(fields("earningId",earning.earningId(),"received",total,"expected",earning.expected()==null?null:earning.expected().amount(),"credits",credits));}return result;}

    private void validateCreditSources(UUID receiptId,coupons.receipts.CouponTransaction tx){
        for(var earning:tx.getObservations().earnings())if(earning.status()==RebateStatus.CREDITED&&receipts.findRewardCredits(receiptId,earning.earningId()).stream().anyMatch(r->r.status()==ReceiptStatus.ACTIVE&&r.transaction().getObservations().earnings().stream().anyMatch(e->e.status()==RebateStatus.CREDITED&&e.confirmed()!=null&&e.details()!=null&&receiptId.equals(e.details().originalReceiptId())&&earning.earningId().equals(e.details().originalEarningId()))))throw new ApiError(400,"This original reward already has received manager credits. Keep its original rejection/unpaid status to avoid counting the reward twice.");
        var totals=new LinkedHashMap<String,BigDecimal>();
        for(var credit:tx.getObservations().earnings()){var d=credit.details();if(d==null||d.originalReceiptId()==null||credit.status()==RebateStatus.REJECTED)continue;
            if(receiptId.equals(d.originalReceiptId()))throw new ApiError(400,"A credit cannot point to its own receipt.");
            var source=receipts.findById(d.originalReceiptId()).orElseThrow();if(source.status()!=ReceiptStatus.ACTIVE)throw new ApiError(400,"Choose an active original receipt.");var earning=source.transaction().getObservations().earnings().stream().filter(v->v.earningId().equals(d.originalEarningId())).findFirst().orElseThrow();
            if(credit.kind()!=ReceiptObservations.EarningKind.WALGREENS_CASH||earning.kind()!=ReceiptObservations.EarningKind.WALGREENS_CASH||earning.status()==RebateStatus.CREDITED||earning.status()==RebateStatus.PAID||earning.expected()==null)throw new ApiError(400,"Choose an original unpaid or rejected Walgreens Cash reward. An already-credited reward must not be credited again.");
            if(credit.status()!=RebateStatus.CREDITED||credit.confirmed()==null)throw new ApiError(400,"Link only a received manager credit; enter the amount actually received.");
            String key=d.originalReceiptId()+":"+d.originalEarningId();var amount=totals.merge(key,credit.confirmed().amount(),BigDecimal::add);
            var prior=receipts.findRewardCredits(d.originalReceiptId(),d.originalEarningId()).stream().filter(r->!r.transactionId().equals(receiptId)&&r.status()==ReceiptStatus.ACTIVE).flatMap(r->r.transaction().getObservations().earnings().stream()).filter(v->v.details()!=null&&d.originalReceiptId().equals(v.details().originalReceiptId())&&d.originalEarningId().equals(v.details().originalEarningId())&&v.status()==RebateStatus.CREDITED&&v.confirmed()!=null).map(v->v.confirmed().amount()).reduce(BigDecimal.ZERO,BigDecimal::add);
            if(prior.add(amount).compareTo(earning.expected().amount())>0)throw new ApiError(400,"Linked credits exceed the original expected reward. Review existing credits instead of entering it twice.");
        }
    }
    private List<ReceiptRecord> possibleDuplicates(ReceiptInput input) {
        var m = input.metadata();
        if(m.receiptNumber()==null&&m.purchasedAt()==null)return List.of();
        var matches=new ArrayList<>(receipts.findPossibleDuplicates(m));for(var receipt:receipts.findImportDuplicates(m))if(matches.stream().noneMatch(v->v.transactionId().equals(receipt.transactionId())))matches.add(receipt);return List.copyOf(matches);
    }

    private void requirePlatform() {
        if (platform == null) {
            throw new ApiError(501, "Platform operations are not configured on this server.");

        }
    }

    private static boolean equal(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static long revision(HttpExchange e) {
        String value = e.getRequestHeaders().getFirst("If-Match-Revision");
        if (value == null) {
            throw new ApiError(428, "If-Match-Revision is required.");
        }
        try {
            long n = Long.parseLong(value);
            if (n < 1 || n == Long.MAX_VALUE) {
                throw new NumberFormatException();

            }
            return n;
        } catch (NumberFormatException x) {
            throw new ApiError(400, "Revision must be a positive integer.");
        }
    }

    private static long checkoutRevision(HttpExchange e) {
        String value = e.getRequestHeaders().getFirst("If-Match-Revision");
        if (value == null) {
            throw new ApiError(428, "If-Match-Revision is required.");
        }

        try {
            long revision = Long.parseLong(value);
            if (revision < 0 || revision == Long.MAX_VALUE) {
                throw new NumberFormatException();
            }
            return revision;
        } catch (NumberFormatException ex) {
            throw new ApiError(400,
                    "Checkout revision must be a nonnegative integer.");
        }
    }

    private static String body(HttpExchange e) throws IOException {
        return body(e, MAX_BODY);
    }

    private static String body(HttpExchange e, int limit) throws IOException {
        String type = e.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.split(";", 2)[0].trim().equalsIgnoreCase("application/json")) {
            throw new ApiError(415, "Content-Type must be application/json.");
        }
        byte[] bytes = e.getRequestBody().readNBytes(limit + 1);
        if (bytes.length > limit) {
            throw new ApiError(413, "Request body exceeds the allowed size.");
        }
        String value;
        try {
            value = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException x) {
            throw new ApiError(400, "Body must be UTF-8.");
        }
        return value;
    }

    private static String query(URI uri, String key) {
        if (uri.getRawQuery() == null) {
            return null;
        }
        for (String p : uri.getRawQuery().split("&")) {
            String[] a = p.split("=", 2);
            if (URLDecoder.decode(a[0], StandardCharsets.UTF_8).equals(key)) {
                return a.length == 2 ? URLDecoder.decode(a[1], StandardCharsets.UTF_8) : "";

            }
        }
        return null;
    }

    private static void requireMethod(String actual, String expected) {
        if (!actual.equals(expected)) {
            throw new ApiError(405, "Method not allowed.");

        }
    }

    private static Map<String, Object> fields(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);

        }
        return map;
    }

    private static Map<String, Object> couponFields(ClippedCoupon c) {
        return ReceiptViews.couponFields(c);
    }

    private static Response json(int status, Object value) {
        return new Response(status, Json.encode(value));
    }

    private static void send(HttpExchange e, Response response) throws IOException {
        e.getResponseHeaders().set("Content-Type", response.mediaType);
        e.getResponseHeaders().set("Cache-Control", "no-store");
        e.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        byte[] bytes = response.binary == null ? response.body.getBytes(StandardCharsets.UTF_8) : response.binary;
        e.sendResponseHeaders(response.status, bytes.length);
        try (OutputStream out = e.getResponseBody()) {
            out.write(bytes);
        }
    }

    private record Response(int status, String body, byte[] binary, String mediaType) {

        Response(int status, String body) {
            this(status, body, null, "application/json; charset=utf-8");
        }
    }

    private record Cached(String signature, Response response) {}

    @FunctionalInterface
    private interface Work {

        Response run(String request) throws IOException;
    }

    static final class ApiError extends RuntimeException {

        final int status;

        ApiError(int status, String message) {
            super(message);
            this.status = status;
        }
    }
}
