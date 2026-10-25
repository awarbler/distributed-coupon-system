package coupons.api;

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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import coupons.ClippedCoupon;
import coupons.ProviderBalanceSummary;
import coupons.PurchaseItem;
import coupons.EarningDetails;
import coupons.PromotionQualification;
import coupons.RebateStatus;
import coupons.ReceiptObservations;
import coupons.ShoppingPlan;
import coupons.TransactionCalculator;
import static coupons.api.Json.asText;
import static coupons.api.Json.known;
import static coupons.api.Json.number;
import static coupons.api.Json.optional;
import static coupons.api.Json.required;
import coupons.repository.ClippedCouponRepository;
import coupons.repository.PostgresPlatformRepository;
import coupons.repository.ReceiptChangeContext;
import coupons.repository.ReceiptCreateCtx;
import coupons.repository.ReceiptRecord;
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
    private final PostgresPlatformRepository platform;
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
            String token, String adminToken, ApiRequestStore requestStore, PostgresPlatformRepository platform) {
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
            send(exchange, new Response(400, Json.object(Map.of("error", "Invalid request fields or resource state."))));
        } catch (Exception error) {
            error.printStackTrace(System.err);
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
        boolean write = !method.equals("GET");
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
            requireMethod(method,"PUT");requirePlatform();return write(e,principal,request->{var f=Json.parseObject(request);known(f,"action","quantity","location","note","lowStockLevel","unitsPerPackage","unitName","countUnit");platform.adjustReceiptInventory(UUID.fromString(parts[2]),revision(e),required(f,"action"),Math.toIntExact(number(f.get("quantity"))),optional(f,"location"),optional(f,"note"),principal,f.get("lowStockLevel")==null?null:Math.toIntExact(number(f.get("lowStockLevel"))),f.get("unitsPerPackage")==null?null:Math.toIntExact(number(f.get("unitsPerPackage"))),optional(f,"unitName"),optional(f,"countUnit"));return json(200,fields("saved",true));});
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
        if (path.equals("/receipts/preview")) {
            requireMethod(method, "POST");
            var input = receiptInput(body(e, 16 * 1024 * 1024));
            var duplicate = possibleDuplicates(input);
            return json(200, fields("transactionId", input.id(), "reviewToken", input.reviewToken(), "totals", PostgresPlatformRepository.totals(input.record()),
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
                    receipts instanceof coupons.repository.PostgresTransactionRepository postgres?postgres.saveWithDraft(input.id(),input.transaction(),input.metadata(),input.evidence(),writeRequest,new ReceiptCreateCtx(principal),selected,planId==null?null:UUID.fromString(planId),planId==null?0:Long.parseLong(required(source,"shoppingPlanRevision")),draftId==null?null:UUID.fromString(draftId),draftId==null?0:Long.parseLong(required(source,"receiptDraftRevision"))):null:
                    receipts.save(input.id(),input.transaction(),input.metadata(),input.evidence(),writeRequest,new ReceiptCreateCtx(principal));
                if(result==null)throw new ApiError(400,"Linked shopping plans and coupons require PostgreSQL.");
                e.getResponseHeaders().set("Location", "/receipts/" + input.id());
                return json(201, result);
            });
        }
        if(path.equals("/accounts/activity")) {
            requireMethod(method,"GET"); requirePlatform();
            var provider=query(e.getRequestURI(),"provider");var from=query(e.getRequestURI(),"from");var to=query(e.getRequestURI(),"to");
            if(provider==null || provider.isBlank() || from==null || to==null) throw new ApiError(400,"Select an account and date range.");
            return json(200,platform.account(provider,LocalDate.parse(from),LocalDate.parse(to)));
        }
        if(path.equals("/accounts/member-rewards")) {
            requireMethod(method,"POST");requirePlatform();
            return write(e,principal,request->{
                var f=Json.parseObject(request);known(f,"eventId","provider","date","amount","description","expirationDate");
                String provider=ProviderBalanceSummary.accountName(required(f,"provider"));
                if(!Set.of("Walgreens Cash","Register Rewards","ECB","Walmart Cash").contains(provider))throw new ApiError(400,"Select a supported member reward account.");
                var amount=ReceiptInput.decimal(f,"amount",false);if(amount.signum()<=0)throw new ApiError(400,"Enter an amount greater than zero.");
                var value=new coupons.repository.AccountAdjustment(ReceiptInput.uuid(f,"eventId"),LocalDate.parse(required(f,"date")),provider,null,amount,"Member reward: "+required(f,"description"),principal,java.time.Instant.now(),optional(f,"expirationDate")==null?null:LocalDate.parse(optional(f,"expirationDate")));
                platform.saveMemberReward(value);return json(201,value);
            });
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
        if(path.equals("/rewards/register-rewards")) {
            requirePlatform();if(method.equals("GET"))return json(200,platform.standaloneRewards());
            requireMethod(method,"POST");return write(e,principal,request->{
                var f=Json.parseObject(request);known(f,"rewardId","description","amount","earnedDate","expirationDate","confirmAvailable");
                if(!ReceiptInput.flag(f,"confirmAvailable"))throw new ApiError(400,"Confirm this is an unused RR coupon available to use.");
                String earned=optional(f,"earnedDate"),expires=optional(f,"expirationDate");
                var amount=ReceiptInput.decimal(f,"amount",false);if(amount.signum()<=0)throw new ApiError(400,"Enter a reward amount greater than zero.");
                var reward=new coupons.RegisterReward(ReceiptInput.uuid(f,"rewardId"),required(f,"description"),amount,earned==null?null:LocalDate.parse(earned),expires==null?null:LocalDate.parse(expires),null);
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
                var f=Json.parseObject(request);known(f,"usageEventId","sourceId","receiptEarning","reason","confirmUnknownHistory");
                try {return json(201,platform.matchRR(id,revision(e),ReceiptInput.uuid(f,"usageEventId"),ReceiptInput.uuid(f,"sourceId"),ReceiptInput.flag(f,"receiptEarning"),optional(f,"reason")==null || optional(f,"reason").isBlank() ? "Matched recorded RR use to selected reward" : optional(f,"reason").trim(),principal,ReceiptInput.flag(f,"confirmUnknownHistory")));}
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
                return json(200, fields("earning", addition.earning(), "qualification", PromotionQualification.evaluate(addition.earning(), addition.transaction()), "totals", PostgresPlatformRepository.totals(new ReceiptRecord(old.transactionId(), old.status(), old.revision(), addition.transaction(), old.metadata(), old.evidenceIds()))));
            }
            return write(e, principal, 8 * 1024 * 1024, request -> {
                var old = receipts.findById(receiptId).orElseThrow();
                if (old.revision() != revision(e)) {
                    throw new ConcurrentModificationException();

                }
                var addition = ReceiptEarningAddition.read(old, Json.parseObject(request));
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
                known(f, "lineItemIds", "qualifyingQuantities", "expirationDate");
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
                var nextDetails = new EarningDetails(f.containsKey("qualifyingQuantities")?ReceiptInput.quantities(f):detail==null?Map.of():detail.qualifyingQuantities(), detail == null ? null : detail.payoutDestination(), detail == null ? null : detail.submittedOn(), detail == null ? null : detail.submissionReference(), detail == null ? null : detail.confirmationReference());
                earnings.set(index, new ReceiptObservations.Earning(previous.earningId(), previous.kind(), previous.provider(), previous.offerReference(), lines.isEmpty() ? ReceiptObservations.Scope.RECEIPT : lines.size() == 1 ? ReceiptObservations.Scope.ITEM : ReceiptObservations.Scope.MULTI_ITEM, lines, previous.expected(), previous.confirmed(), previous.status(), expiry, previous.instrumentId(), previous.claimId(), previous.evidenceIds(), previous.recordedLifecycle(), previous.requirement(), previous.valuation(), nextDetails));
                tx.setObservations(new ReceiptObservations(obs.purchaseDate(), obs.printedTime(), earnings, obs.unidentifiedRR(), obs.walgreensCash(), obs.savings(), obs.discountKinds(), obs.receiptSavingValuePercent(), obs.promotionLinks(), obs.worksheet(), obs.footerStatus()));
                var now = java.time.Instant.now();
                return json(200, receipts.update(receiptId, old.revision(), tx, old.metadata(), List.of(), new ReceiptWriteRequest(UUID.randomUUID(), now), new ReceiptChangeContext(principal, "Updated earning product associations / expiration: " + earningId, principal, now)));
            });
        }
        if (parts.length == 6 && parts[1].equals("receipts") && parts[3].equals("earnings") && parts[5].equals("status")) {
            requireMethod(method, "PUT");
            UUID receiptId = UUID.fromString(parts[2]), earningId = UUID.fromString(parts[4]);
            return write(e, principal, request -> {
                long expected = revision(e);
                var f = Json.parseObject(request);
                known(f, "status", "amount", "unit", "confirmAmountAndUnit", "rejectionReason");
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
                var status = RebateStatus.valueOf(required(f, "status"));
                var updated = ReceiptEarningStatusUpdate.apply(earnings.get(index), status, optional(f, "amount"), optional(f, "unit"), Boolean.TRUE.equals(f.get("confirmAmountAndUnit")), optional(f, "rejectionReason"));
                earnings.set(index, updated);
                tx.setObservations(new ReceiptObservations(obs.purchaseDate(), obs.printedTime(), earnings, obs.unidentifiedRR(), obs.walgreensCash(), obs.savings(), obs.discountKinds(), obs.receiptSavingValuePercent(), obs.promotionLinks(), obs.worksheet(), obs.footerStatus()));
                var now = java.time.Instant.now();
                var result = receipts.update(receiptId, expected, tx, receipt.metadata(), List.of(), new ReceiptWriteRequest(UUID.randomUUID(), now),
                        new ReceiptChangeContext(principal, "Owner-confirmed reward status: " + earningId + " -> " + status, principal, now));
                return json(200, result);
            });
        }

        if (parts.length == 4 && parts[1].equals("receipts") && parts[3].equals("edit-preview")) {
            requireMethod(method, "POST");
            var old = receipts.findById(UUID.fromString(parts[2])).orElseThrow();
            if (old.revision() != revision(e)) throw new ApiError(409, "Receipt changed; refresh before editing.");
            var correction = ReceiptCorrection.read(old, Json.parseObject(body(e, MAX_BODY)));
            var candidate = new ReceiptRecord(old.transactionId(), old.status(), old.revision(), correction.transaction(), correction.metadata(), old.evidenceIds());
            return json(200, fields("reviewToken", correction.token(), "totals", PostgresPlatformRepository.totals(candidate)));
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
                    "paymentRoles", r.transaction().getPayments().stream().collect(java.util.stream.Collectors.toMap(p->p.getPaymentId().toString(),p->new TransactionCalculator().paymentRole(r.transaction(),p))), "rrUses", platform == null ? List.of() : platform.rewardUses(r.transactionId()), "contents", ReceiptSnapshot.from(r.transaction()), "editDiscounts", ReceiptCorrection.editDiscounts(r), "preservePriceLineIds", r.transaction().getPurchaseItems().stream().filter(item -> item.getRegularUnitPrice() != null && item.getRegularUnitPrice().compareTo(item.getSaleUnitPrice()) < 0).map(PurchaseItem::getLineItemId).toList()));
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
        if(path.equals("/shopping-plans/scenario/preview")) {
            requireMethod(method,"POST");var fields=Json.parseObject(body(e,1024*1024));known(fields,"scenario");return json(200,parseScenario(fields.get("scenario")).estimate());
        }
        if (path.equals("/shopping-plans")) {
            requireMethod(method, "POST");
            return write(e, principal, request -> {
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
            return write(e, principal, request -> {
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
        known(f, "planId", "name", "retailer", "shoppingDate", "revision", "plannedPurchaseIds", "scenario");
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
        return new ShoppingPlan(id, required(f, "name"), f.containsKey("retailer") ? optional(f, "retailer") : previous == null ? null : previous.retailer(),
                date == null ? null : LocalDate.parse(date), purchases, next,f.containsKey("scenario")?(f.get("scenario")==null?null:parseScenario(f.get("scenario"))):previous==null?null:previous.scenario(),previous==null?null:previous.purchasedReceiptId());
    }

    static ShoppingPlan.Scenario parseScenario(Object raw) {
        try{return parseScenarioFields(raw);}catch(IllegalArgumentException error){throw new ApiError(400,"Shopping deal: "+error.getMessage());}
    }
    private static ShoppingPlan.Scenario parseScenarioFields(Object raw) {
        if(!(raw instanceof Map<?,?>))throw new IllegalArgumentException("Complete the planning scenario.");
        @SuppressWarnings("unchecked") var f=(Map<String,Object>)raw;
        known(f,"items","coupons","promotions","tax","walgreensCashUsed","registerRewardsUsed","giftCardUsed","manualOop","paymentMethod","fundingNotes","taxRate","payments");
        var items=ReceiptInput.rows(f,"items",100).stream().map(r->{known(r,"id","name","sku","regular","sale","quantity");return new ShoppingPlan.Line(ReceiptInput.uuid(r,"id"),required(r,"name"),optional(r,"sku"),ReceiptInput.decimal(r,"regular",false),ReceiptInput.decimal(r,"sale",true),Math.toIntExact(number(r.get("quantity"))));}).toList();
        var coupons=ReceiptInput.rows(f,"coupons",100).stream().map(r->{known(r,"id","description","amount","requiredQuantity","lineIds","savedCouponId","kind","wholeReceipt");return new ShoppingPlan.ScenarioCoupon(ReceiptInput.uuid(r,"id"),required(r,"description"),ReceiptInput.decimal(r,"amount",false),Math.toIntExact(number(r.get("requiredQuantity"))),ReceiptInput.ids(r,"lineIds"),optional(r,"savedCouponId")==null?null:ReceiptInput.uuid(r,"savedCouponId"),optional(r,"kind"),r.containsKey("wholeReceipt")&&ReceiptInput.flag(r,"wholeReceipt"));}).toList();
        var promotions=ReceiptInput.rows(f,"promotions",100).stream().map(r->{known(r,"id","description","rewardType","rewardAmount","requiredQuantity","requiredSpend","spendBasis","allowsWalgreensCash","allowsWalmartCash","lineIds","wholeReceipt");return new ShoppingPlan.ScenarioPromotion(ReceiptInput.uuid(r,"id"),required(r,"description"),required(r,"rewardType"),ReceiptInput.decimal(r,"rewardAmount",false),Math.toIntExact(number(r.get("requiredQuantity"))),ReceiptInput.decimal(r,"requiredSpend",false),required(r,"spendBasis"),ReceiptInput.flag(r,"allowsWalgreensCash"),ReceiptInput.ids(r,"lineIds"),r.containsKey("wholeReceipt")&&ReceiptInput.flag(r,"wholeReceipt"),!r.containsKey("allowsWalmartCash")||ReceiptInput.flag(r,"allowsWalmartCash"));}).toList();
        return new ShoppingPlan.Scenario(items,coupons,promotions,ReceiptInput.decimal(f,"tax",false),ReceiptInput.decimal(f,"walgreensCashUsed",false),ReceiptInput.decimal(f,"registerRewardsUsed",false),ReceiptInput.decimal(f,"giftCardUsed",false),ReceiptInput.decimal(f,"manualOop",true),required(f,"paymentMethod"),optional(f,"fundingNotes"),ReceiptInput.decimal(f,"taxRate",true),!f.containsKey("payments")||f.get("payments")==null?null:ReceiptInput.rows(f,"payments",100).stream().map(r->{known(r,"id","type","amount","notes");return new ShoppingPlan.ScenarioPayment(ReceiptInput.uuid(r,"id"),required(r,"type"),ReceiptInput.decimal(r,"amount",false),optional(r,"notes"));}).toList());
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

    private static ReceiptInput receiptInput(String json) {
        try {
            return ReceiptInput.read(Json.parseObject(json));
        } catch (IllegalArgumentException ex) {
            throw new ApiError(400, ex.getMessage() == null ? "Invalid receipt fields." : ex.getMessage());
        }
    }

    private List<ReceiptRecord> possibleDuplicates(ReceiptInput input) {
        var m = input.metadata();
        return m.receiptNumber() == null && m.purchasedAt() == null ? List.of() : receipts.findPossibleDuplicates(m);
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
        return PostgresPlatformRepository.couponFields(c);
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

    private static final class ApiError extends RuntimeException {

        final int status;

        ApiError(int status, String message) {
            super(message);
            this.status = status;
        }
    }
}
