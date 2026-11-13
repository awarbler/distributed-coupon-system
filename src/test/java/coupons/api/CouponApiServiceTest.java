package coupons.api;

import coupons.promotions.ClippedCoupon;
import coupons.promotions.Coupon;
import coupons.promotions.DealTerms;
import coupons.promotions.CouponStatus;
import coupons.receipts.CouponTransaction;
import coupons.promotions.CouponType;
import coupons.promotions.CouponTerms;
import coupons.receipts.PurchaseItem;
import coupons.promotions.RebateStatus;
import coupons.receipts.ReceiptObservations;
import coupons.receipts.ReceiptTotals;
import coupons.shopping.ShoppingPlan;

import com.sun.net.httpserver.HttpServer;
import coupons.repository.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP requests over loopback; isolated repositories, never local receipt files. */
class CouponApiServiceTest {
    final InMemoryTransactionRepository receipts = new InMemoryTransactionRepository();
    final Plans plans = new Plans();
    final Coupons coupons = new Coupons();
    HttpServer server;
    PostgresTestDatabase testDatabase;
    final HttpClient client = HttpClient.newHttpClient();
    String base;
    @BeforeEach void start() throws Exception {
        server = new CouponApiService(receipts,plans,coupons,"reader","owner").start(0);
        base="http://127.0.0.1:"+server.getAddress().getPort();
    }
    @AfterEach void stop() throws Exception { server.stop(0); client.close(); receipts.close(); if(testDatabase!=null)testDatabase.close(); }
    HttpResponse<String> call(String method,String path,String token,String body,String key,String revision) throws Exception {
        var b=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(10));
        if(token!=null)b.header("Authorization","Bearer "+token);
        if(key!=null)b.header("Idempotency-Key",key);
        if(revision!=null)b.header("If-Match-Revision",revision);
        b.header("Content-Type","application/json");
        b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
        return client.send(b.build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void weeklyPurchaseAppearsInScenarioWithoutInventingPricesAndEditsPersist()throws Exception {
        UUID planId=UUID.randomUUID(),originalId=UUID.randomUUID(),purchaseId=UUID.randomUUID(),dealId=UUID.randomUUID(),sourceId=UUID.randomUUID();
        var zero=BigDecimal.ZERO;var original=new ShoppingPlan.Line(originalId,"Test Product",null,BigDecimal.ONE,null,1,0);
        var scenario=new ShoppingPlan.Scenario(List.of(original),List.of(),List.of(),zero,zero,zero,zero,null,"CASH","");
        plans.save(new ShoppingPlan(planId,"Testin Add Weekly Deal","Walgreens",LocalDate.of(2026,11,9),List.of(),1,scenario),"test");
        var purchase=new coupons.shopping.PlannedPurchase(purchaseId,planId,"CoverGirl Cosmetics",null,1,null,dealId,List.of(),1,null,null,null);
        var product=new LinkedHashMap<String,Object>();product.put("id",sourceId);product.put("name","CoverGirl Cosmetics");product.put("quantity",1);product.put("regular",null);product.put("sale",null);product.put("sku",null);
        var ad=new coupons.promotions.DealTerms.WeeklyAdvertisement(Json.encode(Map.of("items",List.of(product),"coupons",List.of(),"promotions",List.of())),Map.of("originalText","CoverGirl Cosmetics"),List.of());
        var terms=new coupons.promotions.DealTerms(DealTerms.Type.ADVERTISEMENT,"CoverGirl",null,null,1,null,0,null,false,null,List.of(),null,null,null,false,false,ad);
        var deal=new coupons.promotions.AdvertisedDeal(dealId,"CoverGirl","CoverGirl Cosmetics","Walgreens",LocalDate.of(2026,11,8),LocalDate.of(2026,11,14),List.of(UUID.randomUUID()),1,terms);
        PlatformRepository substitute=(PlatformRepository)java.lang.reflect.Proxy.newProxyInstance(PlatformRepository.class.getClassLoader(),new Class<?>[]{PlatformRepository.class},(proxy,method,args)->switch(method.getName()){
            case "workspace"->{var current=plans.findById(planId).orElseThrow();yield Map.of("plans",List.of(Map.of("plan",current,"items",List.of(purchase),"scenarioEstimate",current.scenario().estimate("Walgreens"),"estimateComplete",true)),"deals",List.of(deal));}
            case "planPurchases"->List.of(purchase);
            case "deal"->deal;
            case "receiptDrafts","deletedWeeklyDealIds"->List.of();
            default->throw new AssertionError(method.getName());
        });
        server.stop(0);server=new CouponApiService(receipts,plans,coupons,"reader","owner",null,substitute).start(0);base="http://127.0.0.1:"+server.getAddress().getPort();
        var workspace=Json.parseObject(call("GET","/workspace","owner",null,null,null).body());var row=ReceiptInput.rows(workspace,"plans",10).getFirst();
        assertNull(row.get("scenarioEstimate"));assertEquals(false,row.get("estimateComplete"));assertEquals(1,plans.writes);
        var stale=call("PUT","/shopping-plans/"+planId,"owner",Json.encode(Map.of("name","Stale editor","scenario",ShoppingPlanViews.input(ShoppingPlanViews.object(scenario)))),"stale-weekly-view","1");assertEquals(409,stale.statusCode());assertEquals(1,plans.writes);
        var viewed=Json.parseObject(call("GET","/shopping-plans/"+planId,"owner",null,null,null).body());var edited=Json.parseObject(Json.encode(viewed.get("scenario")));
        var lines=ReceiptInput.rows(edited,"items",10);assertEquals(2,lines.size());assertEquals("Test Product",lines.getFirst().get("name"));var added=lines.get(1);assertEquals("CoverGirl Cosmetics",added.get("name"));assertNull(added.get("regular"));assertNull(added.get("sale"));
        added.put("name","CoverGirl corrected");added.put("regular","7.00");added.put("quantity",new BigDecimal("2"));added.put("memberRewardRate",BigDecimal.ZERO);
        var saved=call("PUT","/shopping-plans/"+planId,"owner",Json.encode(Map.of("name","Testin Add Weekly Deal","scenario",ShoppingPlanViews.input(edited))),"save-weekly-edit","1");assertEquals(200,saved.statusCode(),saved.body());
        assertEquals(List.of(purchaseId),plans.findById(planId).orElseThrow().plannedPurchaseIds());
        var refreshed=Json.parseObject(call("GET","/workspace","owner",null,null,null).body());var result=ReceiptInput.rows(refreshed,"plans",10).getFirst();var estimate=Json.parseObject(Json.encode(result.get("scenarioEstimate")));
        assertEquals(0,new BigDecimal("15").compareTo((BigDecimal)estimate.get("saleTotal")));var displayed=ReceiptInput.rows(result,"items",10);assertEquals(2,displayed.size());assertEquals("CoverGirl corrected",displayed.get(1).get("productName"));
        var reopened=Json.parseObject(call("GET","/shopping-plans/"+planId,"owner",null,null,null).body());assertEquals(2,ReceiptInput.rows(Json.parseObject(Json.encode(reopened.get("scenario"))),"items",10).size());
        // Explicit removal stays removed on later reads; the original purchase record is retained.
        edited.put("items",List.of(lines.getFirst()));var removed=call("PUT","/shopping-plans/"+planId,"owner",Json.encode(Map.of("name","Testin Add Weekly Deal","scenario",ShoppingPlanViews.input(edited))),"remove-weekly-edit","2");assertEquals(200,removed.statusCode(),removed.body());
        var finalView=Json.parseObject(call("GET","/shopping-plans/"+planId,"owner",null,null,null).body());assertEquals(1,ReceiptInput.rows(Json.parseObject(Json.encode(finalView.get("scenario"))),"items",10).size());assertNull(purchase.regularUnitPrice());
    }
    @Test void weeklyAdvertisementPreviewIsReadOnlyAndSelectedDealsShareOneOriginal()throws Exception {
        server.stop(0);var writes=new ArrayList<Object[]>();
        PlatformRepository substitute=(PlatformRepository)java.lang.reflect.Proxy.newProxyInstance(PlatformRepository.class.getClassLoader(),new Class<?>[]{PlatformRepository.class},(proxy,method,args)->{if(method.getName().equals("saveDeal")){writes.add(args);return null;}throw new AssertionError(method.getName());});
        server=new CouponApiService(receipts,plans,coupons,"reader","owner",null,substitute).start(0);base="http://127.0.0.1:"+server.getAddress().getPort();
        byte[] original;
        try(var pdf=new org.apache.pdfbox.pdmodel.PDDocument();var out=new java.io.ByteArrayOutputStream()){
            var page=new org.apache.pdfbox.pdmodel.PDPage();pdf.addPage(page);
            try(var stream=new org.apache.pdfbox.pdmodel.PDPageContentStream(pdf,page)){
                stream.beginText();stream.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),12);stream.newLineAtOffset(40,700);stream.showText("Tide $5.00");stream.endText();
            }pdf.save(out);original=out.toByteArray();
        }
        var request=HttpRequest.newBuilder(URI.create(base+"/deals/advertisement-preview?name=weekly.pdf&retailer=Walgreens&validFrom=2026-11-08&validThrough=2026-11-14")).header("Authorization","Bearer owner").header("Content-Type","application/pdf").POST(HttpRequest.BodyPublishers.ofByteArray(original)).build();
        var response=client.send(request,HttpResponse.BodyHandlers.ofString());assertEquals(200,response.statusCode(),response.body());assertTrue(writes.isEmpty());
        var preview=Json.parseObject(response.body());String token=preview.get("sourcePreviewToken").toString();
        var rows=ReceiptInput.rows(preview,"offers",100);assertEquals(1,rows.size());var offer=rows.getFirst();
        var pricing=new LinkedHashMap<String,Object>();pricing.put("type","ADVERTISEMENT");pricing.put("productDescription",offer.get("title"));pricing.put("minimumQuantity",1);pricing.put("advertisement",Map.of("draftJson",Json.encode(offer.get("draft")),"fields",offer.get("fields"),"warnings",offer.get("warnings")));
        var body=new LinkedHashMap<String,Object>();for(String field:List.of("dealId","title","interpretedTerms","retailer","validFrom","validThrough"))body.put(field,offer.get(field));body.put("pricing",pricing);body.put("sourcePreviewToken",token);
        String first=Json.encode(body);var saved=call("POST","/deals","owner",first,"weekly-first",null);assertEquals(201,saved.statusCode(),saved.body());
        assertEquals(1,writes.size());assertArrayEquals(original,((coupons.promotions.SourceArtifact)writes.getFirst()[2]).getContent());
        assertEquals(201,call("POST","/deals","owner",first,"weekly-first",null).statusCode());assertEquals(1,writes.size());
        body.put("dealId",UUID.randomUUID());body.put("title","Another selected offer");assertEquals(201,call("POST","/deals","owner",Json.encode(body),"weekly-second",null).statusCode());assertEquals(2,writes.size());assertNull(writes.get(1)[2]);
        assertEquals(((coupons.promotions.AdvertisedDeal)writes.getFirst()[0]).sourceArtifactIds(),((coupons.promotions.AdvertisedDeal)writes.get(1)[0]).sourceArtifactIds());
        assertThrows(IllegalArgumentException.class,()->WeeklyAdvertisementImport.source(token,"another-owner"));
    }
    @Test void giftCardPayoutRequiresItsRecordedFaceValueRatherThanInferringItFromPoints()throws Exception{
        server.stop(0);var saved=new ArrayList<Object>();PlatformRepository substitute=(PlatformRepository)java.lang.reflect.Proxy.newProxyInstance(PlatformRepository.class.getClassLoader(),new Class<?>[]{PlatformRepository.class},(proxy,method,args)->{if(method.getName().equals("savePayout")){saved.add(args[0]);return null;}throw new AssertionError(method.getName());});server=new CouponApiService(receipts,plans,coupons,"reader","owner",null,substitute).start(0);base="http://127.0.0.1:"+server.getAddress().getPort();
        var input=new LinkedHashMap<String,Object>(Map.of("payoutId",UUID.randomUUID(),"provider","Fetch","date","2026-11-05","points","55000","destination","GIFT_CARD","brand","Walmart Gift Card","status","RECEIVED"));var response=call("POST","/accounts/payouts","owner",Json.object(input),UUID.randomUUID().toString(),null);assertEquals(400,response.statusCode());assertTrue(saved.isEmpty());input.put("amount","50");response=call("POST","/accounts/payouts","owner",Json.object(input),UUID.randomUUID().toString(),null);assertEquals(201,response.statusCode(),response.body());var payout=(ProviderRedemption)saved.getFirst();assertEquals(new BigDecimal("50"),payout.destinationUsdValue());assertEquals(new BigDecimal("55000"),payout.originalUnitsUsed());
    }
    @Test void receivedGiftCardIdentityUsesRecordedBrandAndLastFourWithoutInventingRetailer(){
        var day=LocalDate.of(2026,11,5);var id=UUID.randomUUID();var payout=new ProviderRedemption(id,"Fetch",day,null,new BigDecimal("63500"),new BigDecimal("50"),ProviderRedemption.Destination.GIFT_CARD,"Gift Cards",new BigDecimal("50"),ProviderRedemption.Status.RECEIVED,day,null,List.of(),"reference","Brand: Visa Prepaid Card | Number: 4204**** **** 4668");
        var card=RewardInstrumentRepository.issuedCard(payout);assertEquals("Visa Prepaid Card",card.retailer());assertEquals("4668",card.lastFour());
        var unknown=new ProviderRedemption(id,"Fetch",day,null,null,new BigDecimal("50"),ProviderRedemption.Destination.GIFT_CARD,"Gift Cards",new BigDecimal("50"),ProviderRedemption.Status.RECEIVED,day,null,List.of(),"reference",null);assertEquals("Gift card from Fetch",RewardInstrumentRepository.issuedCard(unknown).retailer());assertNull(RewardInstrumentRepository.issuedCard(unknown).lastFour());
    }
    @Test void manualRewardAccountCorrectionUsesTheSelectedProgramsExistingPointsValuation()throws Exception{
        server.stop(0);var calls=new ArrayList<Object[]>();var id=UUID.randomUUID();PlatformRepository substitute=(PlatformRepository)java.lang.reflect.Proxy.newProxyInstance(PlatformRepository.class.getClassLoader(),new Class<?>[]{PlatformRepository.class},(proxy,method,args)->switch(method.getName()){case "memberReward"->Map.of("amount",BigDecimal.ONE,"points",new BigDecimal("1000"),"provider","Fetch","token","original");case "correctMemberDetails"->{calls.add(args);yield null;}default->throw new AssertionError(method.getName());});
        server=new CouponApiService(receipts,plans,coupons,"reader","owner",null,substitute).start(0);base="http://127.0.0.1:"+server.getAddress().getPort();
        var response=call("PUT","/accounts/member-rewards/"+id,"owner",Json.object(Map.of("amount","10","expectedAmount","1","points","1500","expectedPoints","1000","correctionId",UUID.randomUUID(),"expectedToken","original","details",Map.of("provider","P&G Brand","description","Verified account correction"))),UUID.randomUUID().toString(),null);
        assertEquals(200,response.statusCode(),response.body());assertEquals(0,new BigDecimal("10").compareTo((BigDecimal)calls.getFirst()[3]));assertEquals(id,calls.getFirst()[0]);
    }
    @Test void cardSummarySeparatesObservedOpeningsSpendingAndUnknownCurrentBalances(){
        var cards=List.of(Map.<String,Object>of("kind","GIFT_CARD","status","ACTIVE","opening",new BigDecimal("50"),"calculated",new BigDecimal("43.50"),"events",List.of(Map.of("active",true,"kind","REDEMPTION","amount",new BigDecimal("6.50")))),Map.<String,Object>of("kind","GIFT_CARD","status","ACTIVE","opening",new BigDecimal("50"),"calculated",new BigDecimal("29.77"),"events",List.of(Map.of("active",true,"kind","REDEMPTION","amount",new BigDecimal("20.23")))),Map.<String,Object>of("kind","GIFT_CARD","status","ACTIVE","opening",new BigDecimal("50"),"events",List.of()),Map.<String,Object>of("kind","GIFT_CARD","status","ARCHIVED","opening",new BigDecimal("50"),"events",List.of()));
        var summary=RewardInstrumentRepository.cardSummary(cards);assertEquals(new BigDecimal("150"),summary.get("recordedOpenings"));assertEquals(new BigDecimal("26.73"),summary.get("recordedRedemptions"));assertEquals(new BigDecimal("73.27"),summary.get("knownRemaining"));assertEquals(1,summary.get("unknownBalanceCount"));
    }
    @Test void explicitPayoutLinkUsesTheOriginalPayoutAndRequiresWriteAuthorization()throws Exception {
        server.stop(0);var calls=new ArrayList<Object[]>();var payout=UUID.randomUUID();PlatformRepository substitute=(PlatformRepository)java.lang.reflect.Proxy.newProxyInstance(PlatformRepository.class.getClassLoader(),new Class<?>[]{PlatformRepository.class},(proxy,method,args)->{if(method.getName().equals("linkPayoutCard")){calls.add(args);return Map.of("id",UUID.randomUUID());}throw new AssertionError(method.getName());});
        server=new CouponApiService(receipts,plans,coupons,"reader","owner",null,substitute).start(0);base="http://127.0.0.1:"+server.getAddress().getPort();var body=Json.object(Map.of("id",UUID.randomUUID(),"provider","Visa Prepaid Card","lastFour","4668","confirmed",true,"reason","Verified received payout"));
        assertEquals(403,call("POST","/accounts/payouts/"+payout+"/gift-card","reader",body,UUID.randomUUID().toString(),"2").statusCode());assertTrue(calls.isEmpty());assertEquals(201,call("POST","/accounts/payouts/"+payout+"/gift-card","owner",body,UUID.randomUUID().toString(),"2").statusCode());assertEquals(payout,calls.getFirst()[0]);assertEquals(2L,calls.getFirst()[1]);assertEquals(1,calls.size());
    }
    @Test void unknownRequestFieldsIdentifyTheRejectedFieldWithoutEchoingValues() {
        var error=org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,()->Json.known(Map.of("obsoleteFlag","private value"),"accepted"));
        assertTrue(error.getMessage().contains("obsoleteFlag"));
        assertFalse(error.getMessage().contains("private value"));
    }
    @Test @SuppressWarnings("unchecked") void pastedDealsUseTextParserAndSaveNotesFundingAndChannel()throws Exception {
        for(String name:List.of("Addidas","Qtips","Pampers")) {
            boolean online=name.equals("Qtips"),pampers=name.equals("Pampers");
            String text=pampers?ShoppingNotebookTest.PAMPERS:online?ShoppingNotebookTest.QTIPS:ShoppingNotebookTest.ADIDAS;
            var imported=call("POST","/shopping-plans/import-preview","owner",Json.object(Map.of("text",text)),null,null);
            assertEquals(200,imported.statusCode(),imported.body());var draft=Json.parseObject(imported.body());
            assertEquals("local",draft.get("reader"));assertEquals(text,((Map<String,Object>)draft.get("source")).get("text"));
            // The browser keeps monetary response lexemes as strings, then sends quantities as integers.
            for(String group:List.of("items","coupons","promotions","payments"))for(var row:(List<Map<String,Object>>)draft.get(group))
                for(String field:List.of("regular","sale","amount","rewardAmount","requiredSpend"))if(row.get(field) instanceof BigDecimal amount)row.put(field,amount.toPlainString());
            var scenario=new LinkedHashMap<String,Object>();for(String field:List.of("items","coupons","promotions","payments","source"))scenario.put(field,draft.get(field));
            for(String field:List.of("tax","walgreensCashUsed","registerRewardsUsed","giftCardUsed"))scenario.put(field,"0");
            scenario.put("paymentMethod","CASH");scenario.put("fundingNotes","");
            var estimate=call("POST","/shopping-plans/scenario/preview","owner",Json.object(Map.of("scenario",scenario,"retailer","Walgreens")),null,null);
            assertEquals(200,estimate.statusCode(),estimate.body());assertEquals(0,new BigDecimal(pampers?"15":online?"3.60":"5").compareTo(new BigDecimal(Json.parseObject(estimate.body()).get("calculatedOop").toString())));
            UUID id=UUID.randomUUID();var saved=call("POST","/shopping-plans","owner",Json.object(Map.of("planId",id,"name",name,"retailer","Walgreens","scenario",scenario)),name+"-plan",null);
            assertEquals(201,saved.statusCode(),saved.body());var loaded=plans.findById(id).orElseThrow();
            assertEquals(text,loaded.scenario().source().text());assertEquals(name,loaded.name());if(online){assertEquals(1,loaded.scenario().items().size());assertEquals("ONLINE",loaded.scenario().source().purchaseChannel());}else if(pampers){
                assertEquals(1,loaded.scenario().items().size());assertEquals(3,loaded.scenario().payments().size());assertEquals(2,loaded.scenario().promotions().size());assertEquals("UNKNOWN",loaded.scenario().payments().getFirst().registerRewardType());
                var report=loaded.scenario().estimate();assertEquals(0,new BigDecimal("15").compareTo((BigDecimal)report.get("calculatedOop")));assertEquals(0,new BigDecimal("20").compareTo((BigDecimal)report.get("potentialRewards")));assertEquals(0,new BigDecimal("1.50").compareTo((BigDecimal)report.get("storePromotionTotal")));
            }else assertEquals("MANUFACTURER",loaded.scenario().payments().getFirst().registerRewardType());
            assertTrue(receipts.findById(id).isEmpty());
        }
    }

    @Test void splitManagerCreditsLinkToRejectedRewardWithoutDuplicatingItAndKeepFetch()throws Exception {
        UUID originalId=UUID.randomUUID(),originalEarning=UUID.randomUUID();var originalFields=ReceiptInputTest.draft(originalId);
        originalFields.put("receiptNumber","original-manager-test");originalFields.put("earnings",List.of(Map.of("earningId",originalEarning,"kind","WALGREENS_CASH","provider","Walgreens","form","STORE_REWARD","amount","15","status","REJECTED","rejectionReason","Manager override")));
        var original=ReceiptInput.read(Json.parseObject(Json.object(originalFields)));receipts.save(originalId,original.transaction(),original.metadata(),List.of(),new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptCreateCtx("test"));
        var children=new ArrayList<UUID>();
        for(String amount:List.of("10","5","1")){
            UUID child=UUID.randomUUID();var f=new LinkedHashMap<String,Object>(ReceiptInputTest.draft(child));f.remove("rrUsed");f.remove("rrUsageId");f.put("receiptKind","REWARD_ADJUSTMENT");f.put("receiptNumber","manager-"+amount);f.put("printedTime","09:00");f.put("items",List.of());f.put("discounts",List.of());f.put("payments",List.of());f.put("tax","0");f.put("reportedTotal","0");f.put("printedTotalBasis","BEFORE_PAYMENT_FUNDING");f.put("paymentEntryStatus","COMPLETE");f.put("earnings",List.of(Map.ofEntries(Map.entry("earningId",UUID.randomUUID()),Map.entry("kind","WALGREENS_CASH"),Map.entry("provider","Walgreens"),Map.entry("form","STORE_REWARD"),Map.entry("amount",amount),Map.entry("status","CREDITED"),Map.entry("confirmedAmount",amount),Map.entry("unit","USD"),Map.entry("confirmAmountAndUnit",true),Map.entry("originalReceiptId",originalId),Map.entry("originalEarningId",originalEarning))));
            var preview=call("POST","/receipts/preview","owner",Json.object(f),null,null);
            if(amount.equals("1")){assertEquals(400,preview.statusCode(),preview.body());continue;}
            assertEquals(200,preview.statusCode(),preview.body());f.put("reviewToken",Json.parseObject(preview.body()).get("reviewToken"));f.put("acknowledgeDifference",true);
            var save=call("POST","/receipts","owner",Json.object(f),"manager-"+amount,null);assertEquals(201,save.statusCode(),save.body());children.add(child);
            var record=receipts.findById(child).orElseThrow();assertTrue(record.transaction().getPurchaseItems().isEmpty());assertEquals(ReceiptMeta.ReceiptKind.REWARD_ADJUSTMENT,record.metadata().receiptKind());assertEquals(1,record.transaction().getObservations().earnings().stream().filter(e->e.provider().equals("Fetch")).count());
        }
        var source=receipts.findById(originalId).orElseThrow();assertEquals(RebateStatus.REJECTED,source.transaction().getObservations().earnings().getFirst().status());assertEquals(2,receipts.findRewardCredits(originalId,originalEarning).size());
        var detail=call("GET","/receipts/"+originalId,"owner",null,null,null);assertEquals(200,detail.statusCode());assertTrue(detail.body().contains("creditSettlements"));assertTrue(detail.body().contains("received\":15"));
        var doubleCredit=call("PUT","/receipts/"+originalId+"/earnings/"+originalEarning+"/status","owner",Json.object(Map.of("status","CREDITED","amount","15","unit","USD","confirmAmountAndUnit",true)),"do-not-double-credit","1");assertEquals(400,doubleCredit.statusCode(),doubleCredit.body());
    }

    @Test void receiptPdfImportIsReadOnly()throws Exception {
        var bytes=new java.io.ByteArrayOutputStream();
        try(var pdf=new org.apache.pdfbox.pdmodel.PDDocument()) {
            var page=new org.apache.pdfbox.pdmodel.PDPage();pdf.addPage(page);
            try(var content=new org.apache.pdfbox.pdmodel.PDPageContentStream(pdf,page)){content.beginText();content.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),12);content.setLeading(16);content.newLineAtOffset(40,740);for(var line:List.of("Walgreens","10/08/2026 9:34 AM","TEST LOTION","000123456789 A 4.00","REGULAR PRICE 5.00","MYWALGREENS SAVINGS 1.00","SUBTOTAL 4.00","SALES TAX 0.33","TOTAL 4.33","VISA 4.33","RFN# 0476-1231-0521-2610-0803")){content.showText(line);content.newLine();}content.endText();}pdf.save(bytes);
        }
        String body=Json.object(Map.of("files",List.of(Map.of("name","synthetic.pdf","mediaType","application/pdf","contentBase64",Base64.getEncoder().encodeToString(bytes.toByteArray())))));
        assertEquals(403,call("POST","/receipts/import-preview","reader",body,null,null).statusCode());
        var result=call("POST","/receipts/import-preview","owner",body,null,null);assertEquals(200,result.statusCode(),result.body());assertTrue(result.body().contains("4.33"));assertTrue(receipts.findPossibleDuplicates(new ReceiptMeta("Walgreens","0476-1231-0521-2610-0803",null,null)).isEmpty());
        UUID existing=UUID.randomUUID();var tx=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,BigDecimal.ONE));tx.addPurchaseItem(new PurchaseItem("Synthetic",null,BigDecimal.ONE,BigDecimal.ONE,1));
        receipts.save(existing,tx,new ReceiptMeta("walgreens","04761231052126100803",null,null),List.of(),new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptCreateCtx("test"));
        var duplicate=call("POST","/receipts/import-preview","owner",body,null,null);assertEquals(200,duplicate.statusCode());assertTrue(duplicate.body().contains(existing.toString()));assertEquals(1,receipts.history().findByReceiptId(existing).size());

    }

    @Test void editsEcbExpirationWithoutChangingMoneyOrStatusAndCanClearIt() throws Exception {
        UUID id=UUID.randomUUID(),earningId=UUID.randomUUID();
        var earning=EarningInput.read(Map.of("earningId",earningId.toString(),"kind","THIRD_PARTY_REBATE","provider","ECB","form","STORE_REWARD","amount","10","status","CREDITED","confirmedAmount","10","unit","USD","confirmAmountAndUnit",true));
        var tx=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,BigDecimal.ONE));tx.addPurchaseItem(new PurchaseItem("Synthetic",null,BigDecimal.ONE,BigDecimal.ONE,1));
        tx.setObservations(new ReceiptObservations(LocalDate.of(2026,10,4),null,List.of(earning),List.of(),null,List.of(),Map.of()));
        receipts.save(id,tx,new ReceiptMeta("CVS","Synthetic-expiry",null,null),List.of(),new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptCreateCtx("test"));
        String path="/receipts/"+id+"/earnings/"+earningId+"/associations";
        var result=call("PUT",path,"owner","{\"expirationDate\":\"2026-11-04\"}","expiry-save","1");assertEquals(200,result.statusCode(),result.body());
        var changed=receipts.findById(id).orElseThrow().transaction().getObservations().earnings().getFirst();
        assertEquals(LocalDate.of(2026,11,4),changed.expirationDate());assertEquals(earning.confirmed(),changed.confirmed());assertEquals(earning.status(),changed.status());assertEquals(earning.earningId(),changed.earningId());
        assertEquals(400,call("PUT",path,"owner","{\"expirationDate\":\"2026-10-03\"}","expiry-invalid","2").statusCode());
        assertEquals(200,call("PUT",path,"owner","{\"expirationDate\":null}","expiry-clear","2").statusCode());
        assertNull(receipts.findById(id).orElseThrow().transaction().getObservations().earnings().getFirst().expirationDate());
    }

    @Test void savesRebateFollowUpWithRevisionAndWithoutCreditingMoney()throws Exception {
        UUID id=UUID.randomUUID(),earningId=UUID.randomUUID();
        var earning=EarningInput.read(Map.of("earningId",earningId.toString(),"kind","THIRD_PARTY_REBATE","provider","Ibotta","form","CASH","amount","4","status","EXPECTED"));
        var tx=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,BigDecimal.ONE));tx.addPurchaseItem(new PurchaseItem("Synthetic",null,BigDecimal.ONE,BigDecimal.ONE,1));tx.setObservations(new ReceiptObservations(LocalDate.of(2026,10,4),null,List.of(earning),List.of(),null,List.of(),Map.of()));
        receipts.save(id,tx,new ReceiptMeta("Synthetic","Follow-up",null,null),List.of(),new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptCreateCtx("test"));
        String path="/receipts/"+id+"/earnings/"+earningId+"/follow-up",body=Json.object(Map.of("submissionStatus","FOLLOW_UP","submittedOn","2026-10-25","followUpOn","2026-11-01","followUpNote","Waiting"));
        assertEquals(403,call("PUT",path,"reader",body,"reader-followup","1").statusCode());
        var result=call("PUT",path,"owner",body,"followup-save","1");assertEquals(200,result.statusCode(),result.body());assertEquals(result.body(),call("PUT",path,"owner",body,"followup-save","1").body());
        var updated=receipts.findById(id).orElseThrow().transaction().getObservations().earnings().getFirst();
        assertEquals(earning.expected(),updated.expected());assertNull(updated.confirmed());assertEquals(RebateStatus.EXPECTED,updated.status());assertEquals(LocalDate.of(2026,11,1),updated.details().followUpOn());
        assertEquals(409,call("PUT",path,"owner",body,"stale-followup","1").statusCode());
        assertEquals(2,receipts.history().findByReceiptId(id).size());
    }



    @Test void rewardModeAutomaticallyComparesVerifiedSpendBoostersWithoutRecordingUses()throws Exception {
        server.stop(0);UUID boosterId=UUID.randomUUID();var rules=Map.of("kind","SPEND_BOOSTER","verified","true","scope","RECEIPT","wcAllowed","NO","rrAllowed","NO","usageLimit","ONCE","spendBasis","BEFORE_COUPONS","giftCardRule","PROHIBITED");
        var booster=Map.of("id",boosterId,"name","Verified booster","planningRules",rules,"start","2026-11-08","end","2026-11-14","status","AVAILABLE","giftCardValue","5","threshold","4");
        PlatformRepository substitute=(PlatformRepository)java.lang.reflect.Proxy.newProxyInstance(PlatformRepository.class.getClassLoader(),new Class<?>[]{PlatformRepository.class},(proxy,method,args)->{if(method.getName().equals("workspace"))return Map.of("promotions",List.of(booster),"plans",List.of());throw new AssertionError("Planning must not mutate: "+method.getName());});
        server=new CouponApiService(receipts,plans,coupons,"reader","owner",null,substitute).start(0);base="http://127.0.0.1:"+server.getAddress().getPort();
        var scenario=Map.of("items",List.of(Map.of("id",UUID.randomUUID(),"name","Product","sale","4","quantity",1)),"coupons",List.of(),"promotions",List.of(),"tax","0","walgreensCashUsed","0","registerRewardsUsed","0","giftCardUsed","0","paymentMethod","CASH");
        var input=Map.of("candidates",List.of(Map.of("id","wg","name","Walgreens offer","store","Walgreens","scenario",scenario)),"rewards",List.of(),"walgreensCash","0","budget","20","mode","MAX_REWARDS","retailer","Walgreens","day","2026-11-09");
        var response=call("POST","/shopping-plans/weekly-preview","owner",Json.encode(input),null,null);assertEquals(200,response.statusCode(),response.body());var result=Json.parseObject(response.body());assertTrue(((BigDecimal)result.get("earnings")).compareTo(new BigDecimal("5"))>=0);assertTrue(response.body().contains(boosterId.toString()));assertEquals(0,new BigDecimal("4").compareTo((BigDecimal)result.get("cash")));
    }
    @Test void rewardModeEnforcesRetailerAndAcceptsBlankPreference()throws Exception {
        var scenario=Map.of("items",List.of(Map.of("id",UUID.randomUUID(),"name","Product","sale","4","quantity",1)),"coupons",List.of(),"promotions",List.of(),"tax","0","walgreensCashUsed","0","registerRewardsUsed","0","giftCardUsed","0","paymentMethod","CASH");
        var input=new LinkedHashMap<String,Object>();input.put("candidates",List.of(Map.of("id","wg","name","Walgreens offer","store","Walgreens","scenario",scenario),Map.of("id","cvs","name","CVS offer","store","CVS","scenario",scenario)));input.put("rewards",List.of());input.put("walgreensCash","0");input.put("budget","20");input.put("mode","MAX_REWARDS");input.put("day","2026-11-09");
        assertEquals(400,call("POST","/shopping-plans/weekly-preview","owner",Json.encode(input),null,null).statusCode());
        input.put("retailer","Walgreens");var response=call("POST","/shopping-plans/weekly-preview","owner",Json.encode(input),null,null);assertEquals(200,response.statusCode(),response.body());var steps=ReceiptInput.rows(Json.parseObject(response.body()),"steps",10);assertFalse(steps.isEmpty());assertTrue(steps.stream().allMatch(step->step.get("store").equals("Walgreens")));
        input.put("mode","");response=call("POST","/shopping-plans/weekly-preview","owner",Json.encode(input),null,null);assertEquals(200,response.statusCode(),response.body());assertEquals(6,ReceiptInput.rows(Json.parseObject(response.body()),"alternatives",10).size());
    }
    @Test void weeklyDealCorrectionPreservesSourceAndReloadsThroughWorkspace()throws Exception {
        server.stop(0);String source="Buy 2 get ∞ 2 FREE 12-Pack Pepsi Products with myWalgreens Shop now";
        var offer=ReceiptInput.rows(WeeklyAdvertisementImport.interpret(List.of(new WeeklyAdvertisementImport.Page(1,source)),Map.of("retailer","Walgreens"),"Availables.pdf"),"offers",10).getFirst();
        var pricing=new LinkedHashMap<String,Object>();pricing.put("type","ADVERTISEMENT");pricing.put("productDescription",offer.get("title"));pricing.put("minimumQuantity",1);pricing.put("advertisement",Map.of("draftJson",Json.encode(offer.get("draft")),"fields",offer.get("fields"),"warnings",List.of()));
        var body=new LinkedHashMap<String,Object>();for(String key:List.of("dealId","title","interpretedTerms","retailer","validFrom","validThrough"))body.put(key,offer.get(key));body.put("pricing",pricing);
        var initial=DealInput.read(Json.parseObject(Json.encode(body))).deal();var stored=new java.util.concurrent.atomic.AtomicReference<>(initial);
        PlatformRepository substitute=(PlatformRepository)java.lang.reflect.Proxy.newProxyInstance(PlatformRepository.class.getClassLoader(),new Class<?>[]{PlatformRepository.class},(proxy,method,args)->switch(method.getName()){
            case "deal"->stored.get();case "updateDeal"->{stored.set((coupons.promotions.AdvertisedDeal)args[0]);yield stored.get();}
            case "workspace"->Map.of("deals",List.of(stored.get()),"plans",List.of());case "receiptDrafts","deletedWeeklyDealIds"->List.of();default->throw new AssertionError(method.getName());
        });
        server=new CouponApiService(receipts,plans,coupons,"reader","owner",null,substitute).start(0);base="http://127.0.0.1:"+server.getAddress().getPort();
        body.put("title","Pepsi Products — 12-pack");body.put("interpretedTerms","Attempt to overwrite source");
        var saved=call("PUT","/deals/"+initial.dealId(),"owner",Json.encode(body),"correct-incomplete","1");assertEquals(200,saved.statusCode(),saved.body());assertEquals(source,stored.get().interpretedTerms());assertEquals(initial.sourceArtifactIds(),stored.get().sourceArtifactIds());
        var draft=Json.parseObject(stored.get().pricing().advertisement().draftJson());var item=ReceiptInput.rows(draft,"items",10).getFirst();item.put("regular","8.99");item.put("name","Pepsi Products — 12-pack");pricing.put("advertisement",Map.of("draftJson",Json.encode(draft),"fields",offer.get("fields"),"warnings",List.of()));body.put("validFrom","2026-11-08");body.put("validThrough","2026-11-14");
        saved=call("PUT","/deals/"+initial.dealId(),"owner",Json.encode(body),"correct-price","2");assertEquals(200,saved.statusCode(),saved.body());
        var loaded=call("GET","/workspace?day=2026-11-09","owner",null,null,null);assertEquals(200,loaded.statusCode(),loaded.body());assertTrue(loaded.body().contains("8.99"));assertEquals(source,stored.get().pricing().advertisement().fields().get("originalText"));
        var scenario=new LinkedHashMap<>(draft);scenario.put("tax","0");scenario.put("walgreensCashUsed","0");scenario.put("registerRewardsUsed","0");scenario.put("giftCardUsed","0");scenario.put("paymentMethod","CASH");
        var request=Map.of("candidates",List.of(Map.of("id",initial.dealId(),"name",stored.get().title(),"store","Walgreens","scenario",scenario)),"rewards",List.of(),"walgreensCash","0","budget","20","mode","MAX_REWARDS","retailer","Walgreens","day","2026-11-09");
        var planned=call("POST","/shopping-plans/weekly-preview","owner",Json.encode(request),null,null);assertEquals(200,planned.statusCode(),planned.body());assertEquals(1,ReceiptInput.rows(Json.parseObject(planned.body()),"steps",10).size());assertEquals(0,new BigDecimal("17.98").compareTo((BigDecimal)Json.parseObject(planned.body()).get("cash")));
    }
    @Test void weeklyPreviewPreservesRequiredQuantityAndReturnsBudgetShortfall()throws Exception {
        var candidate=WeeklyDealPlannerTest.deal("required","10","1");var line=candidate.scenario().items().getFirst();
        var clientScenario=Map.of("items",List.of(Map.of("id",line.id(),"name",line.name(),"sale","10","quantity",1)),"coupons",List.of(),"promotions",List.of(),"tax","0","walgreensCashUsed","0","registerRewardsUsed","0","giftCardUsed","0","paymentMethod","CASH");
        var body=Json.object(Map.of("candidates",List.of(Map.of("id",candidate.id(),"name",candidate.name(),"store",candidate.store(),"scenario",clientScenario)),"rewards",List.of(),"walgreensCash","0","budget","5","mode","LOWEST_OOP","day","2026-11-06","requiredItems",List.of(Map.of("candidateId",candidate.id(),"lineId",line.id(),"quantity",2)),"includeOptional",false));
        var response=call("POST","/shopping-plans/weekly-preview","owner",body,null,null);assertEquals(200,response.statusCode(),response.body());
        var result=Json.parseObject(response.body());assertEquals(true,result.get("requirementsMet"));assertEquals(false,result.get("affordable"));assertEquals(0,new BigDecimal("15").compareTo((BigDecimal)result.get("additionalCashNeeded")));
    }
    @Test void weeklyPreviewSkipsInvalidOptionalCandidateWithoutRejectingValidRequiredItem()throws Exception {
        var requiredDeal=WeeklyDealPlannerTest.deal("Walgreens Trash Bag","4","1");var line=requiredDeal.scenario().items().getFirst();
        var validScenario=Map.of("items",List.of(Map.of("id",line.id(),"name","Walgreens Trash Bag","sale","4","quantity",1)),"coupons",List.of(),"promotions",List.of(),"tax","0","walgreensCashUsed","0","registerRewardsUsed","0","giftCardUsed","0","paymentMethod","CASH");
        var valid=new LinkedHashMap<String,Object>();valid.put("id",requiredDeal.id());valid.put("name","Walgreens Trash Bag");valid.put("store","Walgreens");valid.put("scenario",validScenario);
        var invalidOptional=new LinkedHashMap<String,Object>();invalidOptional.put("id","advertisement:incomplete-walmart");invalidOptional.put("name","Incomplete Walmart advertisement");invalidOptional.put("store","Walmart");invalidOptional.put("scenario",null);
        var input=new LinkedHashMap<String,Object>();input.put("candidates",List.of(valid,invalidOptional));input.put("rewards",List.of());input.put("walgreensCash","0");input.put("walmartCash","0");input.put("budget","1000");input.put("mode","EXPIRING_FIRST");input.put("day","2026-11-12");input.put("requiredItems",List.of(Map.of("candidateId",requiredDeal.id(),"lineId",line.id(),"quantity",3,"name","Walgreens Trash Bag")));input.put("includeOptional",true);input.put("allowRewardsFromEarlierTransactions",true);input.put("useGiftCards",false);input.put("giftCardFirst",false);input.put("boosterId",null);
        var response=call("POST","/shopping-plans/weekly-preview","owner",Json.object(input),null,null);assertEquals(200,response.statusCode(),response.body());
        var result=Json.parseObject(response.body());assertEquals(true,result.get("requirementsMet"));assertEquals(1,((List<?>)result.get("steps")).size());assertEquals(0,new BigDecimal("12").compareTo((BigDecimal)result.get("cash")));var required=(List<?>)result.get("requiredItems");assertEquals(3,((BigDecimal)((Map<?,?>)required.getFirst()).get("quantity")).intValueExact());
    }
    @Test void weeklyPreviewNamesMissingPriceForRequiredProductInsteadOfGenericScenarioError()throws Exception {
        UUID line=UUID.randomUUID();var scenario=Map.of("items",List.of(Map.of("id",line,"name","Walgreens Trash Bag","regular","","sale","","quantity",1)),"coupons",List.of(),"promotions",List.of(),"tax","0","walgreensCashUsed","0","registerRewardsUsed","0","giftCardUsed","0","paymentMethod","CASH");
        var input=Map.of("candidates",List.of(Map.of("id","trash","name","Trash bags","store","Walgreens","scenario",scenario)),"rewards",List.of(),"walgreensCash","0","budget","1000","mode","EXPIRING_FIRST","day","2026-11-12","requiredItems",List.of(Map.of("candidateId","trash","lineId",line,"quantity",3,"name","Walgreens Trash Bag")),"includeOptional",true);
        var response=call("POST","/shopping-plans/weekly-preview","owner",Json.object(input),null,null);assertEquals(200,response.statusCode(),response.body());assertTrue(response.body().contains("purchase price is missing for Walgreens Trash Bag"),response.body());assertFalse(response.body().contains("Complete the planning scenario"),response.body());
    }
    @Test void correctsOriginalRrMetadataWithoutDuplicatingOrChangingEarnedAmount()throws Exception {
        UUID receipt=UUID.randomUUID(),earningId=UUID.randomUUID();
        var earning=EarningInput.read(Map.of("earningId",earningId.toString(),"kind","REGISTER_REWARD","provider","Walgreens","form","STORE_COUPON","amount","6","status","PAID","confirmedAmount","6","unit","USD","confirmAmountAndUnit",true));
        var tx=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,BigDecimal.ONE));tx.addPurchaseItem(new PurchaseItem("Synthetic",null,BigDecimal.ONE,BigDecimal.ONE,1));tx.setObservations(new ReceiptObservations(LocalDate.of(2026,11,1),"10:00",List.of(earning),List.of(),null,List.of(),Map.of()));
        receipts.save(receipt,tx,new ReceiptMeta("Walgreens","Metadata test",null,null),List.of(),new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptCreateCtx("test"));
        String path="/receipts/"+receipt+"/earnings/"+earningId+"/associations";
        String body=Json.object(Map.of("offer","Verified source","registerRewardType","MANUFACTURER","originProgram","Recorded program","originPromotion","Recorded offer","expirationDate","2026-11-19"));
        var result=call("PUT",path,"owner",body,"metadata-save","1");assertEquals(200,result.statusCode(),result.body());assertEquals(result.body(),call("PUT",path,"owner",body,"metadata-save","1").body());
        var changed=receipts.findById(receipt).orElseThrow();assertEquals(2,changed.revision());assertEquals(1,changed.transaction().getObservations().earnings().size());
        var restored=changed.transaction().getObservations().earnings().getFirst();assertEquals(earningId,restored.earningId());assertEquals(earning.confirmed(),restored.confirmed());assertEquals("Recorded program",restored.details().originProgram());assertEquals("MANUFACTURER",restored.details().registerRewardType());assertEquals("Verified source",restored.offerReference());assertEquals(LocalDate.of(2026,11,19),restored.expirationDate());
    }
    @Test void correctsRewardAmountWithRevisionAuditAndIdempotentRetry()throws Exception {
        UUID receipt=UUID.randomUUID(),earningId=UUID.randomUUID();
        var earning=EarningInput.read(Map.of("earningId",earningId.toString(),"kind","THIRD_PARTY_REBATE","provider","Fetch","form","POINTS","amount","25","status","CREDITED","confirmedAmount","25","unit","points","confirmAmountAndUnit",true));
        var tx=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,BigDecimal.ONE));tx.addPurchaseItem(new PurchaseItem("Synthetic",null,BigDecimal.ONE,BigDecimal.ONE,1));tx.setObservations(new ReceiptObservations(LocalDate.of(2026,10,4),null,List.of(earning),List.of(),null,List.of(),Map.of()));
        receipts.save(receipt,tx,new ReceiptMeta("Synthetic","Amount test",null,null),List.of(),new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptCreateCtx("test"));
        String path="/receipts/"+receipt+"/earnings/"+earningId+"/amount",body="{\"amount\":\"50\",\"unit\":\"points\"}";
        assertEquals(403,call("PUT",path,"reader",body,"denied-amount","1").statusCode());
        var result=call("PUT",path,"owner",body,"correct-amount","1");assertEquals(200,result.statusCode(),result.body());assertEquals(result.body(),call("PUT",path,"owner",body,"correct-amount","1").body());
        var changed=receipts.findById(receipt).orElseThrow();assertEquals(2,changed.revision());assertEquals(1,changed.transaction().getObservations().earnings().size());var value=changed.transaction().getObservations().earnings().getFirst();assertEquals(earningId,value.earningId());assertEquals(earning.status(),value.status());assertEquals(new BigDecimal("50"),value.confirmed().amount());
        assertEquals(409,call("PUT",path,"owner",body,"stale-amount","1").statusCode());assertEquals(400,call("PUT",path,"owner","{\"amount\":\"0\",\"unit\":\"points\"}","zero-amount","2").statusCode());
        assertEquals(2,receipts.history().findByReceiptId(receipt).size());
        var deletion=call("PUT","/receipts/"+receipt+"/earnings/"+earningId+"/delete","owner","{}","delete-reward","2");assertEquals(200,deletion.statusCode(),deletion.body());var removed=receipts.findById(receipt).orElseThrow().transaction().getObservations().earnings().getFirst();assertEquals(RebateStatus.REJECTED,removed.status());assertEquals(new BigDecimal("50"),removed.confirmed().amount());assertEquals(3,receipts.history().findByReceiptId(receipt).size());
    }

    @Test void automaticCouponEligibilityRequiresVerifiedStatusSkuQuantityStoreDatesAndRestrictions() {
        var id=UUID.randomUUID();var line=UUID.randomUUID();var day=LocalDate.of(2026,11,9);
        for(String restriction:List.of("NONE","exclusions","channelRestriction","usageRestriction","qualificationStatus")) {
            var details=restriction.equals("NONE")?Map.<String,String>of():Map.of(restriction,restriction.equals("qualificationStatus")?"NEEDS_REVIEW":"Recorded restriction");
            var terms=new CouponTerms(id,CouponTerms.Source.DIGITAL_MANUFACTURER,CouponTerms.Discount.DOLLARS,"Walgreens","Walgreens",null,"Gillette or Venus",List.of("123"),null,null,null,1,null,null,null,null,null,null,details);
            var coupon=new Coupon("Gillette or Venus",new BigDecimal("4"),2,CouponType.MANUFACTURER,LocalDate.of(2026,11,30),terms);var clipped=new ClippedCoupon(id,"Walgreens",null,coupon,CouponStatus.CLIPPED,LocalDate.of(2026,11,1),1);var available=new ClippedCoupon(id,"Walgreens",null,coupon,CouponStatus.AVAILABLE,null,1);
            var scenario=new ShoppingPlan.Scenario(List.of(new ShoppingPlan.Line(line,"Venus","123",new BigDecimal("12"),null,2)),List.of(),List.of(),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,null,"CASH","");
            assertEquals(restriction.equals("NONE")?1:0,ShoppingOfferMatch.match(scenario,"Walgreens",day,List.of(clipped)).size());assertTrue(ShoppingOfferMatch.match(scenario,"Walgreens",day,List.of(available)).isEmpty());assertTrue(ShoppingOfferMatch.match(scenario,"CVS",day,List.of(clipped)).isEmpty());assertTrue(ShoppingOfferMatch.match(scenario,"Walgreens",LocalDate.of(2026,12,1),List.of(clipped)).isEmpty());
            var quantity=new ShoppingPlan.Scenario(List.of(new ShoppingPlan.Line(line,"Venus","123",new BigDecimal("12"),null,1)),List.of(),List.of(),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,null,"CASH","");assertTrue(ShoppingOfferMatch.match(quantity,"Walgreens",day,List.of(clipped)).isEmpty());
        }
    }
    @Test void availableCouponSelectionCountsAsPlannedDiscountWithoutChangingLibraryStatus() throws Exception {
        var line=UUID.randomUUID();var coupon=couponEntry(UUID.randomUUID());coupon.put("status","AVAILABLE");coupon.put("clippedOn",null);var available=CouponInput.read(coupon).getFirst();coupons.data.put(available.couponId(),available);
        var selected=Map.of("id",UUID.randomUUID(),"description","Venus planned offer","amount","4","requiredQuantity",1,"lineIds",List.of(line),"savedCouponId",available.couponId(),"kind","MANUFACTURER","pendingVerification",true);
        var scenario=Map.of("items",List.of(Map.of("id",line,"name","Venus","regular","12","quantity",1)),"coupons",List.of(selected),"promotions",List.of(),"tax","0","walgreensCashUsed","0","registerRewardsUsed","0","giftCardUsed","0","paymentMethod","CASH");var planId=UUID.randomUUID();
        var response=call("POST","/shopping-plans","owner",Json.object(Map.of("planId",planId,"name","Venus idea","retailer","Walgreens","scenario",scenario)),"plan-available-coupon",null);assertEquals(201,response.statusCode(),response.body());var saved=plans.findById(planId).orElseThrow();assertTrue(saved.scenario().coupons().getFirst().pendingVerification());assertEquals(available.couponId(),saved.scenario().coupons().getFirst().savedCouponId());assertEquals(new BigDecimal("8"),saved.scenario().estimate("Walgreens").get("calculatedOop"));assertEquals(CouponStatus.AVAILABLE,coupons.data.get(available.couponId()).status());assertEquals(0,coupons.redemptions);
        assertEquals(200,call("GET","/shopping-plans/"+planId,"reader",null,null,null).statusCode());
    }
    @Test void scenarioPreviewAndPlanSaveAreSeparateFromReceiptsAndBalances()throws Exception{
        var line=Map.of("id",UUID.randomUUID().toString(),"name","Scott","regular","5.00","quantity",2);
        var scenario=Map.of("items",List.of(line),"coupons",List.of(),"promotions",List.of(),"tax","0","walgreensCashUsed","0","registerRewardsUsed","0","giftCardUsed","0","paymentMethod","CASH");
        var preview=call("POST","/shopping-plans/scenario/preview","owner",Json.object(Map.of("scenario",scenario)),null,null);assertEquals(200,preview.statusCode(),preview.body());assertEquals(new BigDecimal("10.00"),Json.parseObject(preview.body()).get("calculatedOop"));
        var memberLine=new LinkedHashMap<String,Object>(line);memberLine.put("memberRewardRate",5);
        var memberScenario=new LinkedHashMap<String,Object>(scenario);memberScenario.put("items",List.of(memberLine));
        var memberPreview=call("POST","/shopping-plans/scenario/preview","owner",Json.object(Map.of("scenario",memberScenario,"retailer","Walgreens")),null,null);
        assertEquals(200,memberPreview.statusCode(),memberPreview.body());assertEquals(new BigDecimal("0.50"),Json.parseObject(memberPreview.body()).get("memberRewardEstimate"));
        var paid=new java.util.LinkedHashMap<String,Object>(scenario);paid.put("payments",List.of(Map.of("id",UUID.randomUUID().toString(),"type","ECB","amount","2","notes","Scott")));
        var paidPreview=call("POST","/shopping-plans/scenario/preview","owner",Json.object(Map.of("scenario",paid)),null,null);assertEquals(200,paidPreview.statusCode(),paidPreview.body());assertEquals(new BigDecimal("8.00"),Json.parseObject(paidPreview.body()).get("calculatedOop"));
        var whole=new java.util.LinkedHashMap<String,Object>(scenario);whole.put("coupons",List.of(Map.of("id",UUID.randomUUID().toString(),"description","ONLINE2","amount","2","requiredQuantity",1,"lineIds",List.of(),"kind","SALE_PROMOTION","wholeReceipt",true)));
        var wholePreview=call("POST","/shopping-plans/scenario/preview","owner",Json.object(Map.of("scenario",whole)),null,null);assertEquals(200,wholePreview.statusCode(),wholePreview.body());assertEquals(new BigDecimal("8.00"),Json.parseObject(wholePreview.body()).get("calculatedOop"));
        var id=UUID.randomUUID();var body=Map.of("planId",id.toString(),"name","Synthetic scenario","retailer","Walgreens","scenario",scenario);
        var created=call("POST","/shopping-plans","owner",Json.object(body),"scenario-create",null);assertEquals(201,created.statusCode(),created.body());assertEquals(2,plans.findById(id).orElseThrow().scenario().items().getFirst().quantity());
        var edited=call("PUT","/shopping-plans/"+id,"owner",Json.object(Map.of("name","Renamed")),"scenario-metadata","1");assertEquals(200,edited.statusCode(),edited.body());assertNotNull(plans.findById(id).orElseThrow().scenario());
        assertTrue(receipts.findById(id).isEmpty());
    }

    @Test void existingReceiptConnectionChangesOnlyThePlanInPostgres() throws Exception {
        testDatabase=new PostgresTestDatabase();var db=testDatabase.connections;
        var repository=new PostgresTransactionRepository(db);var planRepository=new PostgresShoppingPlanRepository(db);var platform=new PostgresPlatformRepository(db);
        UUID id=UUID.randomUUID(),receiptId=UUID.randomUUID();
        planRepository.save(new ShoppingPlan(id,"PG completed deal","Walgreens",LocalDate.of(2026,11,1),List.of(),1),"test");
        var input=ReceiptInputTest.read(ReceiptInputTest.draft(receiptId));
        repository.save(receiptId,input.transaction(),input.metadata(),List.of(),new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptCreateCtx("test"));
        var before=repository.findById(receiptId).orElseThrow();var snapshot=ReceiptSnapshot.from(before.transaction());
        assertThrows(ConcurrentModificationException.class,()->platform.linkShoppingPlanReceipt(id,1,receiptId,before.revision()+1,"test"));
        assertNull(planRepository.findById(id).orElseThrow().purchasedReceiptId());
        platform.linkShoppingPlanReceipt(id,1,receiptId,before.revision(),"test");
        var linked=planRepository.findById(id).orElseThrow();assertEquals(receiptId,linked.purchasedReceiptId());assertEquals(2,linked.revision());
        assertEquals(receiptId,planRepository.findByRevision(id,2).orElseThrow().purchasedReceiptId());
        var after=repository.findById(receiptId).orElseThrow();assertEquals(before.revision(),after.revision());assertEquals(snapshot,ReceiptSnapshot.from(after.transaction()));assertEquals(before.metadata(),after.metadata());
        assertThrows(ConcurrentModificationException.class,()->platform.linkShoppingPlanReceipt(id,1,receiptId,before.revision(),"test"));
    }
    @Test void existingReceiptConnectionIsOwnerOnlyRevisionedAndIdempotent() throws Exception {
        server.stop(0);UUID planId=UUID.randomUUID(),receiptId=UUID.randomUUID();var calls=new java.util.concurrent.atomic.AtomicInteger();
        PlatformRepository substitute=(PlatformRepository)java.lang.reflect.Proxy.newProxyInstance(PlatformRepository.class.getClassLoader(),new Class<?>[]{PlatformRepository.class},(proxy,method,args)->{
            if(!method.getName().equals("linkShoppingPlanReceipt"))throw new AssertionError(method.getName());
            assertEquals(planId,args[0]);assertEquals(3L,args[1]);assertEquals(receiptId,args[2]);assertEquals(2L,args[3]);assertEquals("api-owner",args[4]);calls.incrementAndGet();return null;
        });
        server=new CouponApiService(receipts,plans,coupons,"reader","owner",null,substitute).start(0);base="http://127.0.0.1:"+server.getAddress().getPort();
        String path="/shopping-plans/"+planId+"/receipt-link",body=Json.object(Map.of("receiptId",receiptId,"receiptRevision","2")),key=UUID.randomUUID().toString();
        assertEquals(403,call("POST",path,"reader",body,key,"3").statusCode());assertEquals(0,calls.get());
        assertEquals(200,call("POST",path,"owner",body,key,"3").statusCode());assertEquals(200,call("POST",path,"owner",body,key,"3").statusCode());assertEquals(1,calls.get());
    }

    @Test @SuppressWarnings("unchecked") void weeklyPreviewUsesVerifiedCardSnapshotAndDoesNotDoubleSpendConvertedAccountFunds()throws Exception{
        server.stop(0);UUID cardId=UUID.randomUUID();
        var card=new HashMap<String,Object>(Map.of("id",cardId,"kind","GIFT_CARD","provider","Walgreens","nickname","Safe card","lastFour","1234","unit","USD","historyComplete",true,"calculated",new BigDecimal("5"),"status","ACTIVE"));
        var unknown=new HashMap<>(card);unknown.put("id",UUID.randomUUID());unknown.put("calculated",null);
        var archived=new HashMap<>(card);archived.put("id",UUID.randomUUID());archived.put("status","ARCHIVED");
        var unrelated=new HashMap<>(card);unrelated.put("id",UUID.randomUUID());unrelated.put("provider","CVS");
        var account=Map.<String,Object>of("id",UUID.randomUUID(),"kind","ACCOUNT_REWARD","provider","Walgreens Cash","unit","USD","historyComplete",true,"calculated",BigDecimal.ZERO,"status","ACTIVE","events",List.of(Map.of("kind","EXCHANGE")));
        var snapshot=new HashMap<String,Object>(Map.<String,Object>of("rewardInstruments",List.of(card,unknown,archived,unrelated,account),"balances",List.of(Map.of("provider","Walgreens Cash","balance",Map.of("endUsd",new BigDecimal("10"))))));
        PlatformRepository substitute=(PlatformRepository)java.lang.reflect.Proxy.newProxyInstance(PlatformRepository.class.getClassLoader(),new Class<?>[]{PlatformRepository.class},(proxy,method,args)->{if(method.getName().equals("workspace"))return snapshot;throw new AssertionError(method.getName());});
        server=new CouponApiService(receipts,plans,coupons,"reader","owner",null,substitute).start(0);base="http://127.0.0.1:"+server.getAddress().getPort();
        var candidates=new ArrayList<Object>();var required=new ArrayList<Object>();for(String name:List.of("a","b")){
            UUID line=UUID.randomUUID();var scenario=Map.of("items",List.of(Map.of("id",line,"name",name,"sale","5","quantity",1)),"coupons",List.of(),"promotions",List.of(),"tax","0","walgreensCashUsed","0","registerRewardsUsed","0","giftCardUsed","0","paymentMethod","CASH");
            candidates.add(Map.of("id",name,"name",name,"store","Walgreens","scenario",scenario));required.add(Map.of("candidateId",name,"lineId",line,"quantity",1));
        }
        var input=new LinkedHashMap<String,Object>(Map.of("candidates",candidates,"rewards",List.of(),"walgreensCash","10","budget","10","mode","WC_FIRST","day","2026-11-07","requiredItems",required,"includeOptional",false));input.put("useGiftCards",true);
        var response=call("POST","/shopping-plans/weekly-preview","owner",Json.object(input),null,null);assertEquals(200,response.statusCode(),response.body());var result=Json.parseObject(response.body());assertEquals(0,new BigDecimal("5").compareTo((BigDecimal)result.get("cash")));
        for(var step:(List<Map<String,Object>>)result.get("steps"))for(var payment:(List<Map<String,Object>>)((Map<String,Object>)step.get("scenario")).get("payments"))assertEquals(cardId.toString(),payment.get("rewardSourceId"));
        input.put("useGiftCards",false);response=call("POST","/shopping-plans/weekly-preview","owner",Json.object(input),null,null);assertEquals(0,new BigDecimal("10").compareTo((BigDecimal)Json.parseObject(response.body()).get("cash")));
        snapshot.put("rewardInstruments",List.of());snapshot.put("balances",List.of(Map.of("provider","Walgreens Cash","balance",new coupons.rewards.ProviderBalanceSummary.Balance(BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("10"),null,null,null))));
        response=call("POST","/shopping-plans/weekly-preview","owner",Json.object(input),null,null);assertEquals(200,response.statusCode(),response.body());assertEquals(0,((BigDecimal)Json.parseObject(response.body()).get("cash")).signum());

    }
    @Test void targetedSpendBoosterDoesNotUseWalgreensCash()throws Exception {
        server.stop(0);var id=UUID.randomUUID();
        var rules=Map.of("kind","SPEND_BOOSTER","verified","true","scope","RECEIPT","wcAllowed","YES","rrAllowed","YES","usageLimit","ONCE","spendBasis","BEFORE_COUPONS");
        var booster=Map.<String,Object>of("id",id,"name","Spend 35 Get 7","threshold",new BigDecimal("35"),"giftCardValue",new BigDecimal("7"),"start",LocalDate.of(2026,11,1),"end",LocalDate.of(2026,11,30),"status","ACTIVE","planningRules",rules);
        var snapshot=Map.<String,Object>of("promotions",List.of(booster),"balances",List.of(Map.of("provider","Walgreens Cash","balance",new coupons.rewards.ProviderBalanceSummary.Balance(BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("35"),null,null,null))));
        PlatformRepository substitute=(PlatformRepository)java.lang.reflect.Proxy.newProxyInstance(PlatformRepository.class.getClassLoader(),new Class<?>[]{PlatformRepository.class},(proxy,method,args)->{if(method.getName().equals("workspace"))return snapshot;throw new AssertionError(method.getName());});
        server=new CouponApiService(receipts,plans,coupons,"reader","owner",null,substitute).start(0);base="http://127.0.0.1:"+server.getAddress().getPort();
        var scenario=Map.of("items",List.of(Map.of("id",UUID.randomUUID(),"name","Required goods","sale","35","quantity",1)),"coupons",List.of(),"promotions",List.of(),"tax","0","walgreensCashUsed","0","registerRewardsUsed","0","giftCardUsed","0","paymentMethod","CASH");
        var input=Map.of("candidates",List.of(Map.of("id","goods","name","Goods","store","Walgreens","scenario",scenario)),"rewards",List.of(),"walgreensCash","35","budget","40","mode","WC_FIRST","day","2026-11-07","boosterId",id);
        var response=call("POST","/shopping-plans/weekly-preview","owner",Json.object(input),null,null);assertEquals(200,response.statusCode(),response.body());
        assertEquals(0,new BigDecimal("35").compareTo((BigDecimal)Json.parseObject(response.body()).get("cash")));
    }

    @Test void boosterCrudUsesExistingPromotionApiAndUnverifiedTargetsAreRejected()throws Exception{
        server.stop(0);UUID id=UUID.randomUUID();var calls=new ArrayList<Object[]>();var rules=Map.of("kind","SPEND_BOOSTER","verified","false");
        var booster=Map.<String,Object>of("id",id,"name","Spend 35 Get 7","threshold",new BigDecimal("35"),"giftCardValue",new BigDecimal("7"),"start",LocalDate.of(2026,11,1),"end",LocalDate.of(2026,11,30),"status","ACTIVE","planningRules",rules);
        PlatformRepository substitute=(PlatformRepository)java.lang.reflect.Proxy.newProxyInstance(PlatformRepository.class.getClassLoader(),new Class<?>[]{PlatformRepository.class},(proxy,method,args)->switch(method.getName()){case "savePromotion","deletePromotion"->{calls.add(args);yield null;}case "workspace"->Map.of("promotions",List.of(booster));default->throw new AssertionError(method.getName());});
        server=new CouponApiService(receipts,plans,coupons,"reader","owner",null,substitute).start(0);base="http://127.0.0.1:"+server.getAddress().getPort();
        var body=Json.object(Map.of("name","Spend 35 Get 7","threshold","35","giftCardValue","7","start","2026-11-01","end","2026-11-30","terms","User-recorded terms","status","ACTIVE","expectedToken","new","planningRules",rules));
        var response=call("PUT","/rebate-programs/"+id,"owner",body,UUID.randomUUID().toString(),null);assertEquals(200,response.statusCode(),response.body());assertEquals(rules,calls.getFirst()[9]);
        response=call("DELETE","/rebate-programs/"+id,"owner",Json.object(Map.of("expectedToken","token")),UUID.randomUUID().toString(),null);assertEquals(200,response.statusCode());
        var target=Json.object(Map.of("candidates",List.of(),"rewards",List.of(),"walgreensCash","0","budget","20","mode","LOWEST_OOP","day","2026-11-07","boosterId",id));
        response=call("POST","/shopping-plans/weekly-preview","owner",target,null,null);assertEquals(400,response.statusCode());assertTrue(response.body().contains("Needs Review"));
    }

    @Test void inventoryUpdateAcceptsCategoryAndPackageFields() throws Exception {
        server.stop(0);
        var calls=new ArrayList<Object[]>();
        PlatformRepository substitute=(PlatformRepository)java.lang.reflect.Proxy.newProxyInstance(PlatformRepository.class.getClassLoader(),new Class<?>[]{PlatformRepository.class},(proxy,method,args)->{
            if(method.getName().equals("adjustReceiptInventory")){calls.add(args);return null;}
            throw new AssertionError("Unexpected operation: "+method.getName());
        });
        server=new CouponApiService(receipts,plans,coupons,"reader","owner",null,substitute).start(0);
        base="http://127.0.0.1:"+server.getAddress().getPort();
        UUID id=UUID.randomUUID();
        String body="{\"action\":\"DETAILS\",\"quantity\":0,\"location\":null,\"note\":null,\"lowStockLevel\":null,\"unitsPerPackage\":80,\"unitName\":\"Loads\",\"countUnit\":\"PACKAGE\",\"category\":\"Household & Pet Essentials > Laundry Care\"}";
        var response=call("PUT","/inventory/"+id,"owner",body,UUID.randomUUID().toString(),"2");
        assertEquals(200,response.statusCode(),response.body());assertEquals(1,calls.size());
        assertEquals(id,calls.getFirst()[0]);assertEquals(80,calls.getFirst()[8]);assertEquals("Loads",calls.getFirst()[9]);assertEquals("Household & Pet Essentials > Laundry Care",calls.getFirst()[11]);
        var clear=call("PUT","/inventory/"+id,"owner",body.replace("\"Household & Pet Essentials > Laundry Care\"","null"),UUID.randomUUID().toString(),"3");
        assertEquals(200,clear.statusCode(),clear.body());assertEquals("",calls.getLast()[11]);
        var ounces=call("PUT","/inventory/"+id,"owner",body.replace("\"category\":", "\"comparisonSize\":\"7.5\",\"comparisonUnit\":\"FL_OZ\",\"category\":"),UUID.randomUUID().toString(),"4");
        assertEquals(200,ounces.statusCode(),ounces.body());assertEquals(new BigDecimal("7.5"),calls.getLast()[12]);assertEquals("FL_OZ",calls.getLast()[13]);

    }

    @Test void platformWorkspaceAccountsAndPayoutsUseInjectedInterfaceWithoutPostgres() throws Exception {
        server.stop(0);
        var payouts = new HashMap<UUID, ProviderRedemption>();
        var account = Map.of("provider", "Fetch", "availablePoints", new BigDecimal("10000"),
                "availableUsd", new BigDecimal("10.00"));
        PlatformRepository substitute = (PlatformRepository) java.lang.reflect.Proxy.newProxyInstance(
                PlatformRepository.class.getClassLoader(), new Class<?>[]{PlatformRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "workspace" -> Map.of("accounts", List.of(account));
                    case "receiptDrafts" -> List.of();
                    case "account" -> {
                        assertEquals("Fetch", args[0]);
                        assertEquals(LocalDate.of(2026, 10, 1), args[1]);
                        assertEquals(LocalDate.of(2026, 10, 27), args[2]);
                        yield account;
                    }
                    case "savePayout" -> {
                        var payout = (ProviderRedemption) args[0];
                        payouts.put(payout.redemptionId(), payout);
                        yield null;
                    }
                    case "payout" -> Map.of("revision", 1, "payout", payouts.get(args[0]));
                    case "finishPayout" -> {
                        assertEquals(1L, args[1]);
                        var old = payouts.get(args[0]);
                        payouts.put(old.redemptionId(), old.complete((ProviderRedemption.Status) args[2],
                                (LocalDate) args[3], null));
                        yield null;
                    }
                    default -> throw new AssertionError("Unexpected platform operation: " + method.getName());
                });
        server = new CouponApiService(receipts, plans, coupons, "reader", "owner", null, substitute).start(0);
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        var workspace = call("GET", "/workspace", "owner", null, null, null);
        assertEquals(200, workspace.statusCode(), workspace.body());
        assertTrue(workspace.body().contains("10000"));
        var activity = call("GET", "/accounts/activity?provider=Fetch&from=2026-10-01&to=2026-10-27",
                "reader", null, null, null);
        assertEquals(200, activity.statusCode(), activity.body());
        assertEquals(new BigDecimal("10.00"), Json.parseObject(activity.body()).get("availableUsd"));
        UUID id = UUID.randomUUID();
        var created = call("POST", "/accounts/payouts", "owner", Json.object(Map.of(
                "payoutId", id, "provider", "Fetch", "date", "2026-10-27", "points", "3000", "amount", "3.000",
                "description", "Synthetic payout", "destination", "GIFT_CARD", "status", "REQUESTED")),
                "interface-payout", null);
        assertEquals(201, created.statusCode(), created.body());
        var payout = payouts.get(id);
        assertEquals(new BigDecimal("3000"), payout.originalUnitsUsed());
        assertEquals(new BigDecimal("3.000"), payout.sourceUsdValue());
        assertEquals(ProviderRedemption.Status.REQUESTED, payout.status());
        assertEquals("Synthetic payout", payout.notes());
        var loaded = call("GET", "/accounts/payouts/" + id, "reader", null, null, null);
        assertEquals(200, loaded.statusCode(), loaded.body());
        assertTrue(loaded.body().contains("REQUESTED"));
        var cancelled = call("PUT", "/accounts/payouts/" + id, "owner",
                Json.object(Map.of("status", "CANCELLED", "date", "2026-10-27")), "interface-cancel", "1");
        assertEquals(200, cancelled.statusCode(), cancelled.body());
        assertEquals(ProviderRedemption.Status.CANCELLED, payouts.get(id).status());
        assertEquals(payout.originalUnitsUsed(), payouts.get(id).originalUnitsUsed());
    }


    @Test void printedCashIsPreviewedAndSavedOnceOnIdempotentReceiptCreation()throws Exception {
        UUID id=UUID.randomUUID();var fields=ReceiptInputTest.draft(id);fields.put("retailer","Walgreens");fields.put("earnings",List.of());fields.put("printedFooter",Map.of("status","PRINTED","wcEarned","0.69"));
        var preview=call("POST","/receipts/preview","owner",Json.object(fields),"printed-cash-preview",null);
        assertEquals(200,preview.statusCode(),preview.body());assertTrue(receipts.findById(id).isEmpty());
        fields.put("reviewToken",Json.parseObject(preview.body()).get("reviewToken"));fields.put("acknowledgeDifference",true);
        var first=call("POST","/receipts","owner",Json.object(fields),"printed-cash-save",null);assertEquals(201,first.statusCode(),first.body());
        var retry=call("POST","/receipts","owner",Json.object(fields),"printed-cash-save",null);assertEquals(first.body(),retry.body());
        var saved=receipts.findById(id).orElseThrow();assertEquals(1,saved.revision());
        var earned=saved.transaction().getObservations().earnings().stream().filter(e->e.kind()==ReceiptObservations.EarningKind.WALGREENS_CASH).toList();assertEquals(1,earned.size());assertEquals(new BigDecimal("0.69"),earned.getFirst().confirmed().amount());
    }

    @Test void receiptViewShowsSaleSubtotalBeforeCouponsWithoutChangingCheckout(){
        var fields=ReceiptInputTest.draft(UUID.randomUUID());UUID line=UUID.randomUUID();
        fields.put("items",List.of(Map.of("lineItemId",line,"description","Receipt merchandise","regularUnitPrice","30.53","unitPrice","30.53","quantity",1)));
        fields.put("discounts",List.of(Map.of("applicationId",UUID.randomUUID(),"label","Sale reductions","amount","10.88","kind","SALE_PROMOTION","lineItemIds",List.of(line),"wholeReceipt",false)));
        fields.put("rrUsed","10.00");fields.put("tax","0.47");fields.put("reportedTotal","9.62");
        var record=ReceiptInput.read(Json.parseObject(Json.object(fields))).record();
        var totals=ReceiptViews.totals(record);
        assertEquals(new BigDecimal("19.65"),totals.get("merchandiseBeforeCoupons"));
        assertEquals(new BigDecimal("30.53"),totals.get("saleMerchandise"));
        assertEquals(new BigDecimal("10.12"),totals.get("calculatedDue"));
        assertEquals(new BigDecimal("9.62"),totals.get("reportedDue"));
    }

    @Test void receiptViewsCalculateWithoutStorageAndPreserveKnownDiscrepancy() {
        var input = ReceiptInput.read(Json.parseObject(Json.object(ReceiptInputTest.draft(UUID.randomUUID()))));
        var totals = ReceiptViews.totals(input.record());
        assertEquals(0, new BigDecimal("7.27").compareTo((BigDecimal) totals.get("calculatedDue")));
        assertEquals(0, new BigDecimal("7.28").compareTo((BigDecimal) totals.get("reportedDue")));
        assertEquals(0, new BigDecimal("0.01").compareTo((BigDecimal) totals.get("difference")));
    }

    @Test void shoppingNotebookPreviewAndSourceSaveAreIsolatedFromReceipts() throws Exception {
        String text="Colgate 6 x2\n5 off 2\nearn 5 RR\nCheck my offer";
        assertEquals(403,call("POST","/shopping-plans/import-preview","reader",Json.object(Map.of("text",text)),null,null).statusCode());
        var imported=call("POST","/shopping-plans/import-preview","owner",Json.object(Map.of("text",text,"reader","ocr")),null,null);
        assertEquals(200,imported.statusCode(),imported.body());
        var fields=Json.parseObject(imported.body());
        var scenario=new LinkedHashMap<String,Object>();
        for(String key:List.of("items","coupons","promotions","source"))scenario.put(key,fields.get(key));
        scenario.putAll(Map.of("tax","0","walgreensCashUsed","0","registerRewardsUsed","0","giftCardUsed","0","paymentMethod","CASH","fundingNotes",""));
        for(String collection:List.of("items","coupons","promotions"))for(var row:ReceiptInput.rows(scenario,collection,100))for(String amount:List.of("regular","sale","amount","rewardAmount","requiredSpend"))if(row.get(amount) instanceof BigDecimal decimal)row.put(amount,decimal.toPlainString());
        var preview=call("POST","/shopping-plans/scenario/preview","owner",Json.object(Map.of("scenario",scenario)),null,null);
        assertEquals(200,preview.statusCode(),preview.body());
        assertEquals(new BigDecimal("7"),Json.parseObject(preview.body()).get("calculatedOop"));
        assertNull(Json.parseObject(preview.body()).get("regularTotal"));
        UUID id=UUID.randomUUID();var body=Json.object(Map.of("planId",id,"name","Synthetic notebook","shoppingDate","2026-10-27","scenario",scenario,"organization",Map.of("stage","IDEA","rewardSources",List.of())));
        var created=call("POST","/shopping-plans","owner",body,"notebook-create",null);
        assertEquals(201,created.statusCode(),created.body());assertEquals(created.body(),call("POST","/shopping-plans","owner",body,"notebook-create",null).body());
        assertEquals(text,plans.findById(id).orElseThrow().scenario().source().text());
        assertEquals("IDEA",plans.findById(id).orElseThrow().organization().stage());
        assertTrue(receipts.findById(id).isEmpty());
        var comparison=call("POST","/shopping-plans/compare","reader",Json.object(Map.of("a",scenario,"b",scenario)),null,null);
        assertEquals(200,comparison.statusCode(),comparison.body());assertEquals(BigDecimal.ZERO,Json.parseObject(comparison.body()).get("cashDifference"));
        var cycle=call("PUT","/shopping-plans/"+id,"owner",Json.object(Map.of("name","Synthetic notebook","organization",Map.of("stage","TRIP","checkoutOrder",0,"rewardSources",List.of(id)))),"notebook-self","1");
        assertEquals(400,cycle.statusCode(),cycle.body());assertEquals(1,plans.findById(id).orElseThrow().revision());
        ReceiptInput.rows(scenario,"items",100).getFirst().put("priceRule",Map.of("type","BOGO_FREE","lineIds",List.of(UUID.randomUUID())));
        var badGroup=call("PUT","/shopping-plans/"+id,"owner",Json.object(Map.of("name","Synthetic notebook","scenario",scenario)),"notebook-invalid-group","1");
        assertEquals(400,badGroup.statusCode(),badGroup.body());assertEquals(1,plans.findById(id).orElseThrow().revision());
    }

    @Test void linkedReceiptCreationDispatchesThroughInjectedInterface() throws Exception {
        server.stop(0);
        var dispatched = new java.util.concurrent.atomic.AtomicBoolean();
        TransactionRepository substitute = (TransactionRepository) java.lang.reflect.Proxy.newProxyInstance(
                TransactionRepository.class.getClassLoader(), new Class<?>[]{TransactionRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("saveWithDraft")) {
                        dispatched.set(true);
                        throw new IllegalArgumentException("Injected linked repository reached");
                    }
                    try { return method.invoke(receipts, args); }
                    catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
                });
        server = new CouponApiService(substitute, plans, coupons, "reader", "owner").start(0);
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        UUID id = UUID.randomUUID();
        var fields = ReceiptInputTest.draft(id);
        fields.put("shoppingPlanId", UUID.randomUUID()); fields.put("shoppingPlanRevision", "1");
        var preview = call("POST", "/receipts/preview", "owner", Json.object(fields), null, null);
        fields.put("reviewToken", Json.parseObject(preview.body()).get("reviewToken"));
        fields.put("acknowledgeDifference", true);
        var result = call("POST", "/receipts", "owner", Json.object(fields), "interface-dispatch", null);
        assertEquals(400, result.statusCode(), result.body());
        assertTrue(dispatched.get(), result.body());
        assertTrue(receipts.findById(id).isEmpty());
    }

    @Test void clearedPurchaseDateRemainsUnknownWhilePrintedTimeIsPreserved() {
        var fields=new java.util.LinkedHashMap<String,Object>();fields.put("transactionId",UUID.randomUUID().toString());fields.put("retailer","Store");fields.put("purchaseDate",null);fields.put("printedTime","14:07");fields.put("purchaseChannel","IN_STORE");fields.put("tax","0");fields.put("reportedTotal","1");fields.put("items",List.of(Map.of("lineItemId",UUID.randomUUID().toString(),"description","Product","regularUnitPrice","1","unitPrice","1","quantity",1)));
        var input=ReceiptInput.read(Json.parseObject(Json.object(fields)));assertNull(input.metadata().purchasedAt());assertNull(input.transaction().getObservations().purchaseDate());assertEquals("14:07",input.transaction().getObservations().printedTime());
    }

    @Test void draftHttpEndpointsAreOwnerOnlyAndIdempotent() throws Exception {
        testDatabase = new PostgresTestDatabase();
        var connections = testDatabase.connections;
        var repository=new PostgresTransactionRepository(connections);
        var platform=new PostgresPlatformRepository(connections);
        var service=new coupons.api.CouponApiService(repository,new PostgresShoppingPlanRepository(connections),new PostgresClippedCouponRepository(connections),"reader","owner",new PostgresApiRequestStore(connections),platform);
        var server=service.start(0);try{
            var bonus=UUID.randomUUID();var root="http://127.0.0.1:"+server.getAddress().getPort();var http=java.net.http.HttpClient.newHttpClient();
            java.util.function.BiFunction<String,String,java.net.http.HttpResponse<String>> change=(path,payload)->{try{return http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(root+path)).header("Authorization","Bearer owner").header("Content-Type","application/json").header("Idempotency-Key",UUID.randomUUID().toString()).method((path.endsWith("/member-rewards")||path.endsWith("/payouts"))?"POST":"PUT",java.net.http.HttpRequest.BodyPublishers.ofString(payload)).build(),java.net.http.HttpResponse.BodyHandlers.ofString());}catch(Exception ex){throw new RuntimeException(ex);}};
            var added=change.apply("/accounts/member-rewards",Json.object(Map.of("eventId",bonus.toString(),"provider","P&G Brand","date","2026-10-26","points","75","time","08:17","description","Survey bonus")));assertEquals(201,added.statusCode(),added.body());assertEquals(0,new BigDecimal("0.5").compareTo(platform.memberAmount(bonus)));assertEquals(java.time.LocalTime.of(8,17),new PostgresProviderAccountRepository(connections).findAdjustment(bonus).orElseThrow().time());assertEquals(java.time.LocalTime.of(8,17),platform.memberReward(bonus).get("time"));
            var edited=change.apply("/accounts/member-rewards/"+bonus,Json.object(Map.of("correctionId",UUID.randomUUID().toString(),"amount","1","expectedAmount","0.5","points","150","expectedPoints","75")));assertEquals(200,edited.statusCode(),edited.body());assertEquals(0,BigDecimal.ONE.compareTo(platform.memberAmount(bonus)));
            var deletion=java.net.http.HttpRequest.newBuilder(java.net.URI.create(root+"/accounts/member-rewards/"+bonus)).header("Authorization","Bearer owner").header("Content-Type","application/json").header("Idempotency-Key",UUID.randomUUID().toString()).method("DELETE",java.net.http.HttpRequest.BodyPublishers.ofString(Json.object(Map.of("expectedAmount","1","expectedPoints","150","correctionId",UUID.randomUUID().toString())))).build();var deleted=http.send(deletion,java.net.http.HttpResponse.BodyHandlers.ofString());assertEquals(200,deleted.statusCode(),deleted.body());assertEquals(0,platform.memberAmount(bonus).signum());assertEquals(0,((BigDecimal)platform.memberReward(bonus).get("points")).signum());
            var payout=UUID.randomUUID();var payoutAdded=change.apply("/accounts/payouts",Json.object(Map.of("payoutId",payout.toString(),"provider","P&G Brand","date","2026-10-26","points","75","destination","GIFT_CARD","brand","Target","amount","0.5","status","REQUESTED")));assertEquals(201,payoutAdded.statusCode(),payoutAdded.body());
            var payoutRecord=new PostgresProviderAccountRepository(connections).findRedemption(payout).orElseThrow();assertEquals(0,new BigDecimal("0.5").compareTo(payoutRecord.sourceUsdValue()));assertEquals(new BigDecimal("75"),payoutRecord.originalUnitsUsed());assertEquals(ProviderRedemption.Status.REQUESTED,payoutRecord.status());
            var completedRequest=java.net.http.HttpRequest.newBuilder(java.net.URI.create(root+"/accounts/payouts/"+payout)).header("Authorization","Bearer owner").header("Content-Type","application/json").header("Idempotency-Key","payout-complete").header("If-Match-Revision","1").PUT(java.net.http.HttpRequest.BodyPublishers.ofString("{\"status\":\"RECEIVED\",\"date\":\"2026-10-26\"}")).build();var completedResponse=http.send(completedRequest,java.net.http.HttpResponse.BodyHandlers.ofString());assertEquals(200,completedResponse.statusCode(),completedResponse.body());assertEquals(completedResponse.body(),http.send(completedRequest,java.net.http.HttpResponse.BodyHandlers.ofString()).body());assertEquals(ProviderRedemption.Status.RECEIVED,new PostgresProviderAccountRepository(connections).findRedemption(payout).orElseThrow().status());
            var invalidCard=change.apply("/accounts/payouts",Json.object(Map.of("payoutId",UUID.randomUUID().toString(),"provider","Ibotta","amount","5","destination","CREDIT_CARD","number","123456789")));assertEquals(400,invalidCard.statusCode());
            var id=UUID.randomUUID();var uri=java.net.URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/receipt-drafts/"+id);
            var client=java.net.http.HttpClient.newHttpClient();String body="{\"state\":{\"id\":\""+id+"\",\"store\":\"Walmart\",\"lines\":[]}}";
            var put=java.net.http.HttpRequest.newBuilder(uri).header("Authorization","Bearer owner").header("Content-Type","application/json").header("Idempotency-Key","draft-http").header("If-Match-Revision","0").PUT(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build();
            var first=client.send(put,java.net.http.HttpResponse.BodyHandlers.ofString());assertEquals(200,first.statusCode(),first.body());assertEquals(first.body(),client.send(put,java.net.http.HttpResponse.BodyHandlers.ofString()).body());
            var reader=java.net.http.HttpRequest.newBuilder(uri).header("Authorization","Bearer reader").GET().build();assertEquals(403,client.send(reader,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
            var get=java.net.http.HttpRequest.newBuilder(uri).header("Authorization","Bearer owner").GET().build();assertEquals(200,client.send(get,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());assertTrue(repository.findById(id).isEmpty());
            var del=java.net.http.HttpRequest.newBuilder(uri).header("Authorization","Bearer owner").header("Content-Type","application/json").header("Idempotency-Key","draft-delete").header("If-Match-Revision","1").method("DELETE",java.net.http.HttpRequest.BodyPublishers.ofString("{}")).build();assertEquals(200,client.send(del,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());assertEquals(404,client.send(get,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
        }finally{server.stop(0);}
    }

    String planBody(UUID id,String name) { return Json.object(Map.of("planId",id,"name",name)); }
    UUID receipt() {
        UUID id=UUID.randomUUID();
        CouponTransaction tx=new CouponTransaction(new ReceiptTotals(new BigDecimal("0.01"),new BigDecimal("1.02")));
        tx.addPurchaseItem(new PurchaseItem("Soap",null,new BigDecimal("1.00"),new BigDecimal("1.00"),1));
        receipts.save(id,tx,new ReceiptMeta("Store","000123",LocalDateTime.of(2026,10,4,9,0),null),List.of(),
                new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptCreateCtx("test"));
        return id;
    }
    @Test void healthIsPublicAndOtherEndpointsRequireAuthentication() throws Exception {
        assertEquals(200,call("GET","/health",null,null,null,null).statusCode());
        assertEquals(405,call("POST","/health",null,null,null,null).statusCode());
        assertEquals(401,call("GET","/receipts/"+UUID.randomUUID(),null,null,null,null).statusCode());
        assertEquals(401,call("GET","/shopping-plans/"+UUID.randomUUID(),"wrong",null,null,null).statusCode());
        assertEquals(403,call("POST","/shopping-plans","reader",planBody(UUID.randomUUID(),"x"),"key",null).statusCode());
        assertEquals(0,plans.writes);
    }
    @Test void receiptCorrectionExplainsZeroPaymentAndRRMatchingRequiresOwner() throws Exception {
        UUID id=receipt();
        var body=Json.object(Map.of("reason","Check payment","payments",List.of(Map.of("paymentId",UUID.randomUUID(),"paymentType","CASH","amount","0"))));
        var result=call("POST","/receipts/"+id+"/edit-preview","owner",body,null,"1");
        assertEquals(400,result.statusCode());assertTrue(result.body().contains("greater than zero"));
        assertEquals(403,call("POST","/receipts/"+id+"/rr-matches","reader","{}","match","1").statusCode());
        assertEquals(403,call("POST","/receipts/"+id+"/rr-matches/unknown-source","reader","{}","unknown","1").statusCode());
        assertEquals(403,call("POST","/rewards/register-rewards","reader","{}","standalone",null).statusCode());
    }
    @Test void configurationRejectsMissingOrAmbiguousCredentials() {
        assertThrows(IllegalArgumentException.class,()->new CouponApiService(receipts,plans,coupons," ",null));
        assertThrows(IllegalArgumentException.class,()->new CouponApiService(receipts,plans,coupons,"same","same"));
    }
    @Test void receiptReturnsStructuredValuesWithoutRecalculatingReportedTotal() throws Exception {
        UUID id=receipt();
        var r=call("GET","/receipts/"+id,"reader",null,null,null);
        assertEquals(200,r.statusCode());
        Map<String,Object> f=Json.parseObject(r.body());
        assertEquals("000123",((Map<?,?>)f.get("metadata")).get("receiptNumber"));
        assertTrue(r.body().contains("\"finalTotal\":1.02"));
        assertTrue(r.body().contains("Soap"));
        assertEquals("\"1\"",r.headers().firstValue("ETag").orElseThrow());
        assertEquals(404,call("GET","/receipts/"+UUID.randomUUID(),"reader",null,null,null).statusCode());
    }
    @Test void plansHandleEscapesNullsAndRepeatedUpdatesWithoutLosingLinks() throws Exception {
        UUID id=UUID.randomUUID(); String name="Trip, \"weekly\"\nfood";
        var created=call("POST","/shopping-plans","owner",planBody(id,name),"create",null);
        assertEquals(201,created.statusCode(),created.body());
        assertEquals(name,Json.parseObject(created.body()).get("name"));
        assertNull(Json.parseObject(created.body()).get("retailer"));
        UUID line=UUID.randomUUID(); ShoppingPlan p=plans.data.get(id);
        plans.data.put(id,new ShoppingPlan(id,p.name(),"Store",null,List.of(line),1));
        var updated=call("PUT","/shopping-plans/"+id,"owner","{\"name\":\"changed\"}","update","1");
        assertEquals(200,updated.statusCode(),updated.body());
        assertEquals(List.of(line),plans.data.get(id).plannedPurchaseIds());
        assertEquals("Store",plans.data.get(id).retailer());
        assertEquals(200,call("PUT","/shopping-plans/"+id,"owner","{\"name\":\"again\"}","update2","2").statusCode());
        assertEquals(3,plans.data.get(id).revision());
    }
    @Test void updateRequiresRevisionAndRejectsStaleOrMismatchedInputs() throws Exception {
        UUID id=UUID.randomUUID();call("POST","/shopping-plans","owner",planBody(id,"trip"),"c",null);
        assertEquals(428,call("PUT","/shopping-plans/"+id,"owner","{\"name\":\"x\"}","a",null).statusCode());
        assertEquals(409,call("PUT","/shopping-plans/"+id,"owner","{\"name\":\"x\"}","b","2").statusCode());
        assertEquals(400,call("PUT","/shopping-plans/"+id,"owner",planBody(UUID.randomUUID(),"x"),"d","1").statusCode());
        assertEquals(400,call("PUT","/shopping-plans/"+id,"owner","{\"name\":\"x\",\"plannedPurchaseIds\":[\""+UUID.randomUUID()+"\"]}","e","1").statusCode());
        assertEquals(1,plans.data.get(id).revision());
    }
    @Test void retryUsesExactRequestRouteAndRevisionAndReturnsOriginalResult() throws Exception {
        UUID id=UUID.randomUUID();String body=planBody(id,"trip");
        var first=call("POST","/shopping-plans","owner",body,"key",null);
        var again=call("POST","/shopping-plans","owner",body,"key",null);
        assertEquals(201,again.statusCode());assertEquals(first.body(),again.body());assertEquals(1,plans.writes);
        assertEquals(409,call("POST","/shopping-plans","owner",planBody(id,"other"),"key",null).statusCode());
        assertEquals(409,call("PUT","/shopping-plans/"+id,"owner",body,"key","1").statusCode());
        String change="{\"name\":\"v2\"}";
        var v2=call("PUT","/shopping-plans/"+id,"owner",change,"v2","1");
        call("PUT","/shopping-plans/"+id,"owner","{\"name\":\"v3\"}","v3","2");
        assertEquals(v2.body(),call("PUT","/shopping-plans/"+id,"owner",change,"v2","1").body());
        assertEquals(3,plans.data.get(id).revision());
    }
    @Test void simultaneousRetriesExecuteOnlyOneWrite() throws Exception {
        UUID id=UUID.randomUUID();String body=planBody(id,"trip");
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Callable<HttpResponse<String>> work=()->{start.await();return call("POST","/shopping-plans","owner",body,"same",null);};
            var a=pool.submit(work);var b=pool.submit(work);start.countDown();
            assertEquals(201,a.get(10,TimeUnit.SECONDS).statusCode());assertEquals(201,b.get(10,TimeUnit.SECONDS).statusCode());
            assertEquals(1,plans.writes);
        }
    }
    @Test void couponsExposeNullsAndRedemptionIsValidatedAndReplayable() throws Exception {
        UUID couponId=UUID.randomUUID(), receiptId=receipt();
        coupons.data.put(couponId,new ClippedCoupon(couponId,"Store",null,
                new Coupon("Save",BigDecimal.ONE,1,CouponType.MANUFACTURER,null),CouponStatus.CLIPPED,LocalDate.of(2026,10,1),1));
        var listing=call("GET","/clipped-coupons?provider=Store","reader",null,null,null);
        assertEquals(200,listing.statusCode());assertTrue(listing.body().startsWith("[{"));assertTrue(listing.body().contains("\"externalId\":null"));
        String path="/clipped-coupons/"+couponId+"/redeem";
        String body=Json.object(Map.of("transactionId",receiptId,"redeemedOn","2026-10-04"));
        var first=call("POST",path,"owner",body,"redeem","1");
        assertEquals(200,first.statusCode(),first.body());
        assertEquals(first.body(),call("POST",path,"owner",body,"redeem","1").body());
        assertEquals(1,coupons.redemptions);
        assertEquals(409,call("POST",path,"owner",body,"new-key","2").statusCode());
        assertEquals(404,call("POST",path,"owner",Json.object(Map.of("transactionId",UUID.randomUUID(),"redeemedOn","2026-10-04")),"missing","2").statusCode());
    }
    private Map<String,Object> couponEntry(UUID id) {
        var f=new LinkedHashMap<String,Object>();
        f.put("couponId",id.toString());f.put("provider","Walgreens");f.put("description","CREST $2 off 1");f.put("amount","2");
        f.put("requiredQuantity",BigDecimal.ONE);f.put("couponType","MANUFACTURER");f.put("source","DIGITAL_MANUFACTURER");f.put("discount","DOLLARS");
        f.put("copies",BigDecimal.ONE);f.put("usageLimit",BigDecimal.ONE);f.put("status","CLIPPED");f.put("clippedOn","2026-11-08");
        f.put("expirationDate","2026-11-30");f.put("storeRestriction","Walgreens");f.put("originalOfferText","Verified original offer wording");return f;
    }
    @Test void additionalPhysicalCopyKeepsOfferTermsButNoRedemptionOrAssignmentHistory() {
        var fields=couponEntry(UUID.randomUUID());fields.put("source","PAPER_MANUFACTURER");var original=CouponInput.read(fields).getFirst();
        var receiptId=UUID.randomUUID();var used=new ClippedCoupon(original.couponId(),original.provider(),original.externalId(),original.coupon(),CouponStatus.APPLIED,original.clippedOn(),original.clippedOn(),receiptId,2);
        var added=CouponInput.addCopy(used,UUID.randomUUID(),LocalDate.of(2026,11,9));assertNotEquals(used.couponId(),added.couponId());assertEquals(used.coupon().getTerms().offerId(),added.coupon().getTerms().offerId());assertEquals(used.coupon(),added.coupon());assertEquals(CouponStatus.CLIPPED,added.status());assertNull(added.redeemedOn());assertNull(added.redeemedTransactionId());assertEquals(1,added.revision());assertEquals(receiptId,used.redeemedTransactionId());
        assertThrows(IllegalArgumentException.class,()->CouponInput.addCopy(used,used.couponId(),LocalDate.of(2026,11,9)));
        var digital=CouponInput.read(couponEntry(UUID.randomUUID())).getFirst();assertThrows(IllegalArgumentException.class,()->CouponInput.addCopy(digital,UUID.randomUUID(),LocalDate.of(2026,11,9)));
    }
    @Test void addPaperCopyIsAuthenticatedRevisionCheckedIdempotentAndPreservesSource() throws Exception {
        var fields=couponEntry(UUID.randomUUID());fields.put("source","PAPER_MANUFACTURER");var source=CouponInput.read(fields).getFirst();coupons.data.put(source.couponId(),source);
        var saves=new java.util.concurrent.atomic.AtomicInteger();
        PlatformRepository substitute=(PlatformRepository)java.lang.reflect.Proxy.newProxyInstance(PlatformRepository.class.getClassLoader(),new Class<?>[]{PlatformRepository.class},(proxy,method,args)->{
            if(method.getName().equals("saveCoupons")){@SuppressWarnings("unchecked")var copies=(List<ClippedCoupon>)args[0];saves.incrementAndGet();copies.forEach(c->coupons.save(c,"test"));return null;}throw new AssertionError(method.getName());
        });
        server.stop(0);server=new CouponApiService(receipts,plans,coupons,"reader","owner",null,substitute).start(0);base="http://127.0.0.1:"+server.getAddress().getPort();
        var plan=new ShoppingPlan(UUID.randomUUID(),"Existing plan","Walgreens",LocalDate.of(2026,11,9),List.of(UUID.randomUUID()),1);plans.data.put(plan.planId(),plan);
        UUID copyId=UUID.randomUUID();String path="/clipped-coupons/"+source.couponId()+"/copies",body=Json.object(Map.of("couponId",copyId,"clippedOn","2026-11-09"));
        assertEquals(403,call("POST",path,"reader",body,"copy-reader","1").statusCode());assertEquals(409,call("POST",path,"owner",body,"copy-stale","2").statusCode());assertEquals(0,saves.get());
        var response=call("POST",path,"owner",body,"copy-created","1");assertEquals(201,response.statusCode(),response.body());assertEquals(response.body(),call("POST",path,"owner",body,"copy-created","1").body());assertEquals(1,saves.get());assertEquals(2,coupons.data.size());assertEquals(source,coupons.data.get(source.couponId()));assertEquals(plan,plans.data.get(plan.planId()));assertEquals(0,plans.writes);
        var copy=coupons.data.get(copyId);assertNotNull(copy);assertEquals(source.coupon().getTerms().offerId(),copy.coupon().getTerms().offerId());assertNull(copy.redeemedTransactionId());
        var redeemed=call("POST","/clipped-coupons/"+copyId+"/redeem","owner",Json.object(Map.of("transactionId",receipt(),"redeemedOn","2026-11-09")),"use-added-copy","1");assertEquals(200,redeemed.statusCode(),redeemed.body());assertEquals(CouponStatus.CLIPPED,coupons.data.get(source.couponId()).status());assertEquals(CouponStatus.APPLIED,coupons.data.get(copyId).status());
        var digital=CouponInput.read(couponEntry(UUID.randomUUID())).getFirst();coupons.data.put(digital.couponId(),digital);assertEquals(400,call("POST","/clipped-coupons/"+digital.couponId()+"/copies","owner",Json.object(Map.of("couponId",UUID.randomUUID(),"clippedOn","2026-11-09")),"copy-digital","1").statusCode());assertEquals(1,saves.get());
    }
    @Test void couponEditingPreservesIdentityAndOfferGroupAndOriginalEvidence() {
        var f=couponEntry(UUID.randomUUID());f.put("copies",new BigDecimal("2"));f.put("source","PAPER_MANUFACTURER");
        var copies=CouponInput.read(f);var old=copies.get(1);var edit=new LinkedHashMap<>(f);edit.put("couponId",old.couponId().toString());edit.put("copies",BigDecimal.ONE);edit.put("description","Corrected description");
        var changed=CouponInput.edit(old,edit);
        assertEquals(old.couponId(),changed.couponId());assertEquals(old.coupon().getTerms().offerId(),changed.coupon().getTerms().offerId());assertEquals(2,changed.revision());assertEquals("Verified original offer wording",changed.coupon().getTerms().originalOfferText());
        edit.put("couponId",UUID.randomUUID().toString());assertThrows(IllegalArgumentException.class,()->CouponInput.edit(old,edit));
    }
    @Test void redeemedCouponCannotBeEditedOrRemovedOrReimported() throws Exception {
        var f=couponEntry(UUID.randomUUID());var old=CouponInput.read(f).get(0);var used=new ClippedCoupon(old.couponId(),old.provider(),old.externalId(),old.coupon(),CouponStatus.APPLIED,old.clippedOn(),old.clippedOn(),UUID.randomUUID(),2);coupons.data.put(used.couponId(),used);
        assertThrows(IllegalArgumentException.class,()->CouponInput.edit(used,f));
        assertEquals(400,call("PUT","/clipped-coupons/"+used.couponId(),"owner",Json.object(f),"edit-used","2").statusCode());
        assertEquals(400,call("POST","/clipped-coupons/"+used.couponId()+"/state","owner",Json.object(Map.of("status","NO_LONGER_AVAILABLE")),"remove-used","2").statusCode());
        assertEquals(used,coupons.data.get(used.couponId()));
    }
    @Test void removeFromClippedKeepsUnknownAvailabilityAndNeverRedeems() throws Exception {
        var old=CouponInput.read(couponEntry(UUID.randomUUID())).get(0);coupons.data.put(old.couponId(),old);
        String path="/clipped-coupons/"+old.couponId()+"/state",body=Json.object(Map.of("status","NO_LONGER_AVAILABLE"));
        var response=call("POST",path,"owner",body,"remove-coupon","1");assertEquals(200,response.statusCode(),response.body());
        var changed=coupons.data.get(old.couponId());assertEquals(CouponStatus.NO_LONGER_AVAILABLE,changed.status());assertNull(changed.redeemedOn());assertNull(changed.redeemedTransactionId());assertEquals(0,coupons.redemptions);
        assertEquals(response.body(),call("POST",path,"owner",body,"remove-coupon","1").body());
        var clipped=call("POST",path,"owner",Json.object(Map.of("status","CLIPPED","clippedOn","2026-11-09")),"reclip","2");assertEquals(200,clipped.statusCode(),clipped.body());assertEquals(CouponStatus.CLIPPED,coupons.data.get(old.couponId()).status());
    }
    @Test void manualEditIsAuthenticatedRevisionCheckedAndReplayable() throws Exception {
        var f=couponEntry(UUID.randomUUID());var old=CouponInput.read(f).get(0);coupons.data.put(old.couponId(),old);f.put("description","CREST corrected");String path="/clipped-coupons/"+old.couponId(),body=Json.object(f);
        assertEquals(403,call("PUT",path,"reader",body,"reader-edit","1").statusCode());
        var response=call("PUT",path,"owner",body,"edit-coupon","1");assertEquals(200,response.statusCode(),response.body());assertEquals(response.body(),call("PUT",path,"owner",body,"edit-coupon","1").body());assertEquals(2,coupons.data.get(old.couponId()).revision());
    }
    @Test void pdfParserPreservesUnrecognizedEntriesAndDoesNotInventMissingTerms() {
        var parsed=WalgreensCouponImport.parse("ROC $8 off 3\nManufacturer coupon\nExpires: 11/30/2026\nOffer ID: abc123\n\nUnrecognized complicated offer with no quantity", "sunday.pdf",LocalDate.of(2026,11,8));
        @SuppressWarnings("unchecked") var entries=(List<Map<String,Object>>)parsed.get("entries");
        assertTrue(entries.size()>=2);var supported=entries.get(0);assertEquals("8",supported.get("amount"));assertEquals(3,supported.get("requiredQuantity"));assertEquals(LocalDate.of(2026,11,30),supported.get("expirationDate"));
        var unknown=entries.get(entries.size()-1);assertNull(unknown.get("amount"));assertNull(unknown.get("requiredQuantity"));assertNull(unknown.get("expirationDate"));assertEquals("OTHER",unknown.get("couponType"));assertTrue(unknown.get("originalOfferText").toString().contains("Unrecognized"));assertEquals("CLIPPED",unknown.get("status"));
    }
    @Test void invalidPdfExpirationIsKeptUnknownRatherThanAdjusted() {
        var result=WalgreensCouponImport.parse("CREST $2 off 1\nExpires: 11/31/2026", "weekly.pdf",LocalDate.of(2026,11,8));
        @SuppressWarnings("unchecked") var entries=(List<Map<String,Object>>)result.get("entries");
        assertNull(entries.get(0).get("expirationDate"));assertTrue(entries.get(0).get("originalOfferText").toString().contains("11/31/2026"));
    }
    @Test void pdfParserKeepsDistinctOfferIdsAndOriginalTerms() {
        var result=WalgreensCouponImport.parse("Offer ID: a\nCREST $2 off 1\nManufacturer coupon\n\nOffer ID: b\nCREST $2 off 1\nStore coupon", "sunday.pdf",LocalDate.of(2026,11,8));
        @SuppressWarnings("unchecked") var entries=(List<Map<String,Object>>)result.get("entries");
        assertEquals(2,entries.size());assertEquals("a",entries.get(0).get("externalId"));assertEquals("b",entries.get(1).get("externalId"));assertNotEquals(entries.get(0).get("couponId"),entries.get(1).get("couponId"));assertEquals("STORE",entries.get(1).get("couponType"));
    }
    private byte[] walgreensPdf(boolean printable,String control,String retailer) throws Exception {
        try(var doc=new org.apache.pdfbox.pdmodel.PDDocument()) {
            var page=new org.apache.pdfbox.pdmodel.PDPage();doc.addPage(page);
            try(var content=new org.apache.pdfbox.pdmodel.PDPageContentStream(doc,page)) {
                content.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),12);
                java.util.List<String[]> text=printable?List.of(new String[]{"40","750",retailer},new String[]{"40","725","1 clipped coupons"},new String[]{"40","700","These digital coupons are automatically applied"},new String[]{"123","520","$2 off 1"},new String[]{"236","520","CREST"},new String[]{"236","502","ONE Crest toothpaste"},new String[]{"438","520","Expires"},new String[]{"438","502","11/30/26"},new String[]{"438","484","Manufacturer's Coupon"}):List.of(new String[]{"30","750",retailer},new String[]{"30","725","Print List"},new String[]{"83","520","Expires 11/30/26"},new String[]{"83","502","$2 off 1"},new String[]{"83","484","CREST"},new String[]{"83","466","Remove odors with Crest toothpaste"},new String[]{"83","448","View details"},new String[]{"83","430",control==null?"":control});
                for(var line:text){content.beginText();content.newLineAtOffset(Float.parseFloat(line[0]),Float.parseFloat(line[1]));content.showText(line[2]);content.endText();}
            }
            var out=new java.io.ByteArrayOutputStream();doc.save(out);return out.toByteArray();
        }
    }
    @Test @SuppressWarnings("unchecked") void websiteStatusComesFromExplicitControlsAndNotTheFilenameOrSelectedFormat() throws Exception {
        for(String control:List.of("Clip","Remove","NO_CONTROL")) {
            var result=WalgreensCouponImport.preview(walgreensPdf(false,control.equals("NO_CONTROL")?null:control,"Walgreens"),"available.pdf",LocalDate.of(2026,11,8));var row=((List<Map<String,Object>>)result.get("entries")).getFirst();
            assertEquals(control.equals("Clip")?"AVAILABLE":control.equals("Remove")?"CLIPPED":null,row.get("status"));
            if(!control.equals("Remove"))assertNull(row.get("clippedOn"));
            if(control.equals("NO_CONTROL"))assertEquals("UNVERIFIED",((Map<?,?>)row.get("offerDetails")).get("statusEvidence"));
        }
        var print=WalgreensCouponImport.preview(walgreensPdf(true,null,"Walgreens"),"available.pdf",LocalDate.of(2026,11,8));assertEquals("PRINTABLE",print.get("layout"));assertEquals("CLIPPED",((List<Map<String,Object>>)print.get("entries")).getFirst().get("status"));
    }
    @Test void unsupportedRetailerDocumentsAndMismatchedFormatsAreRejectedWithoutSaving() throws Exception {
        for(String retailer:List.of("CVS","Target","Costco","Paper coupons"))assertThrows(IllegalArgumentException.class,()->WalgreensCouponImport.preview(walgreensPdf(false,"Clip",retailer),"Walgreens.pdf",LocalDate.of(2026,11,8)));
        var web=walgreensPdf(false,"Clip","Walgreens");assertThrows(IllegalArgumentException.class,()->WalgreensCouponImport.preview(web,"offers.pdf",LocalDate.of(2026,11,8),"PRINTABLE",image->{throw new AssertionError("Text PDF must not invoke OCR");}));
        var response=call("POST","/clipped-coupons/walgreens/preview","owner",Json.object(Map.of("source","cvs.pdf","contentBase64",Base64.getEncoder().encodeToString(walgreensPdf(false,"Clip","CVS")),"documentFormat","AUTO")),"unsupported-pdf",null);assertEquals(400,response.statusCode());assertTrue(coupons.data.isEmpty());
    }
    @Test void availableImportsRepeatWithoutDuplicatesAndClippingNeedsPositiveObservation() throws Exception {
        UUID id=UUID.randomUUID();var fields=couponEntry(id);fields.put("status","AVAILABLE");fields.put("clippedOn",null);fields.put("offerDetails",Map.of("importKey","verified-web-offer","statusEvidence","WEBSITE_CLIP_CONTROL"));fields.put("importedOn","2026-11-08");
        String body=Json.object(Map.of("entries",List.of(fields)));
        for(String view:List.of("All Coupons","Available","Clipped")){var response=call("POST","/clipped-coupons/walgreens/import","owner",body,"same-pdf-from-"+view,null);assertEquals(200,response.statusCode(),response.body());assertEquals(1,coupons.data.size());assertEquals(CouponStatus.AVAILABLE,coupons.data.get(id).status());assertNull(coupons.data.get(id).clippedOn());}
        var clipped=new LinkedHashMap<>(fields);clipped.put("status","CLIPPED");clipped.put("clippedOn","2026-11-08");clipped.put("offerDetails",Map.of("importKey","verified-web-offer","statusEvidence","WEBSITE_REMOVE_CONTROL"));var response=call("POST","/clipped-coupons/walgreens/import","owner",Json.object(Map.of("entries",List.of(clipped))),"verified-clipped-observation",null);assertEquals(200,response.statusCode(),response.body());assertEquals(CouponStatus.CLIPPED,coupons.data.get(id).status());assertEquals(1,coupons.data.size());
        var availableAgain=call("POST","/clipped-coupons/walgreens/import","owner",body,"available-list-does-not-unclip",null);assertEquals(200,availableAgain.statusCode(),availableAgain.body());assertEquals(CouponStatus.CLIPPED,coupons.data.get(id).status());assertEquals(0,coupons.redemptions);
    }
    @Test @SuppressWarnings("unchecked") void websiteValidityRangesAndPageBoundariesKeepOfferFieldsSeparate() {
        var pages=new ArrayList<WalgreensCouponImport.PageText>();
        for(int page=1;page<=2;page++) {
            var glyphs=new ArrayList<WalgreensCouponImport.Glyph>();
            String[] text={page==1?"Valid 11/8/2026 - 11/21/2026":"Valid 11/8 - 11/21","$2 off 1",page==1?"Purex":"Colgate","Qualifying products...","View Details","Clip"};
            for(int i=0;i<text.length;i++)glyphs.add(new WalgreensCouponImport.Glyph(30,100+i*20,120,8,false,text[i]));
            pages.add(new WalgreensCouponImport.PageText(page,glyphs,"Walgreens Available",false));
        }
        var result=WalgreensCouponImport.structured(pages,"synthetic-ranges.pdf",LocalDate.of(2026,11,8),"WEB_CARDS",null);var rows=(List<Map<String,Object>>)result.get("entries");assertEquals(2,rows.size());
        assertEquals(LocalDate.of(2026,11,8),rows.getFirst().get("startsOn"));assertEquals(LocalDate.of(2026,11,21),rows.getFirst().get("expirationDate"));assertEquals(LocalDate.of(2026,11,21),rows.getLast().get("expirationDate"));assertEquals(0L,result.get("needsReview"));assertTrue(rows.getFirst().get("description").toString().startsWith("Purex"));assertTrue(rows.getLast().get("description").toString().startsWith("Colgate"));
        assertEquals("1",((Map<String,String>)rows.getFirst().get("offerDetails")).get("sourcePage"));assertEquals("2",((Map<String,String>)rows.getLast().get("offerDetails")).get("sourcePage"));
    }
    @Test void legacySourceKeyRetainsIdentityAndSummaryReportsVerifiedUpdates() throws Exception {
        UUID oldId=UUID.randomUUID();var old=couponEntry(oldId);old.put("status","AVAILABLE");old.put("clippedOn",null);old.put("offerDetails",Map.of("importKey","old-control-dependent-key"));
        assertEquals(200,call("POST","/clipped-coupons/walgreens/import","owner",Json.object(Map.of("entries",List.of(old))),"legacy-key-first",null).statusCode());
        var incoming=new LinkedHashMap<>(old);incoming.put("couponId",UUID.randomUUID().toString());incoming.put("offerDetails",Map.of("importKey","canonical-control-independent-key","previousImportKey","old-control-dependent-key"));
        var same=call("POST","/clipped-coupons/walgreens/import","owner",Json.object(Map.of("entries",List.of(incoming),"summary",true)),"legacy-key-again",null);assertEquals(200,same.statusCode(),same.body());assertEquals(1,coupons.data.size());assertTrue(coupons.data.containsKey(oldId));assertEquals(new BigDecimal("1"),((Map<?,?>)Json.parseObject(same.body()).get("summary")).get("alreadySaved"));
        old.put("expectedRevision",coupons.data.get(oldId).revision());old.put("description","Verified corrected offer");var update=call("POST","/clipped-coupons/walgreens/import","owner",Json.object(Map.of("entries",List.of(old),"summary",true)),"verified-update-summary",null);assertEquals(200,update.statusCode(),update.body());assertEquals(new BigDecimal("1"),((Map<?,?>)Json.parseObject(update.body()).get("summary")).get("updated"));assertEquals(1,coupons.data.size());
    }
    @Test @SuppressWarnings("unchecked") void availableWebsiteWithoutPrintListSavesCatalogTermsAndIgnoresClippedStatistics() throws Exception {
        byte[] bytes;
        try(var doc=new org.apache.pdfbox.pdmodel.PDDocument()) {
            var page=new org.apache.pdfbox.pdmodel.PDPage();doc.addPage(page);
            try(var c=new org.apache.pdfbox.pdmodel.PDPageContentStream(doc,page)) {
                c.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),8);
                String[][] lines={{"30","750","Walgreens Available 197 clipped 3 more coupons"},{"30","520","Expires 11/14/26"},{"30","502","$1.50 off 3"},{"30","484","Angel Soft"},{"30","466","Selected varieties..."},{"30","448","View Details"},{"30","430","Clip Shop"},{"195","520","Expires 11/14/2026"},{"195","502","$1 off 1"},{"195","484","Kotex"},{"195","448","View Details"},{"195","430","Clip"},{"360","520","Expires 11/21/26"},{"360","502","$3 off 2"},{"360","484","Loreal"},{"360","448","View Details"},{"360","430","Clip"}};
                for(var l:lines){c.beginText();c.newLineAtOffset(Float.parseFloat(l[0]),Float.parseFloat(l[1]));c.showText(l[2]);c.endText();}
            }
            var out=new java.io.ByteArrayOutputStream();doc.save(out);bytes=out.toByteArray();
        }
        var result=WalgreensCouponImport.preview(bytes,"synthetic-available.pdf",LocalDate.of(2026,11,8),"AVAILABLE",image->{throw new AssertionError("Selectable text must not use OCR");});
        var rows=(List<Map<String,Object>>)result.get("entries");assertEquals(3,rows.size());assertNull(result.get("declaredCount"));assertEquals("AVAILABLE",result.get("documentKind"));assertEquals(0L,result.get("needsReview"));
        assertEquals(List.of("1.50","1","3"),rows.stream().map(r->r.get("amount")).toList());assertEquals(List.of(3,1,2),rows.stream().map(r->r.get("requiredQuantity")).toList());
        assertTrue(rows.stream().allMatch(r->r.get("status").equals("AVAILABLE")&&r.get("clippedOn")==null));
        for(var r:rows){var d=(Map<String,String>)r.get("offerDetails");assertEquals("NEEDS_REVIEW",d.get("qualificationStatus"));assertEquals("OTHER",r.get("couponType"));assertFalse(r.get("description").toString().contains("Clip"));}
        String body=Json.object(Map.of("entries",rows,"summary",true));
        var first=call("POST","/clipped-coupons/walgreens/import","owner",body,"available-auto-first",null);assertEquals(200,first.statusCode(),first.body());assertEquals(new BigDecimal("3"),((Map<?,?>)Json.parseObject(first.body()).get("summary")).get("imported"));
        var again=call("POST","/clipped-coupons/walgreens/import","owner",body,"available-auto-again",null);assertEquals(200,again.statusCode(),again.body());assertEquals(new BigDecimal("3"),((Map<?,?>)Json.parseObject(again.body()).get("summary")).get("alreadySaved"));assertEquals(3,coupons.data.size());
    }
    @Test void textPdfImportUsesPdfboxAndRejectsImageOnlyPdfWithoutOcr() throws Exception {
        byte[] readable,empty;
        try(var document=new org.apache.pdfbox.pdmodel.PDDocument()) {
            var page=new org.apache.pdfbox.pdmodel.PDPage();document.addPage(page);
            var bytes=new java.io.ByteArrayOutputStream();document.save(bytes);empty=bytes.toByteArray();
            try(var stream=new org.apache.pdfbox.pdmodel.PDPageContentStream(document,page)) {
                stream.beginText();stream.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),12);stream.newLineAtOffset(40,700);stream.showText("CREST $2 off 1");stream.endText();
            }
            bytes.reset();document.save(bytes);readable=bytes.toByteArray();
        }
        readable=walgreensPdf(true,null,"Walgreens");
        assertThrows(IllegalArgumentException.class,()->WalgreensCouponImport.preview(empty,"scan.pdf",LocalDate.now(),image->{throw new java.io.IOException("Local OCR unavailable");}));
        assertFalse(((List<?>)WalgreensCouponImport.preview(readable,"text.pdf",LocalDate.now()).get("entries")).isEmpty());
        var response=call("POST","/clipped-coupons/walgreens/preview","owner",Json.object(Map.of("source","text.pdf","contentBase64",Base64.getEncoder().encodeToString(readable))),"preview-pdf",null);assertEquals(200,response.statusCode(),response.body());assertEquals(0,coupons.data.size());assertEquals(0,coupons.redemptions);
    }
    @Test void reviewedImportIsReplayableAndNeverChangesAbsentCouponState() throws Exception {
        var absent=CouponInput.read(couponEntry(UUID.randomUUID())).get(0);coupons.data.put(absent.couponId(),absent);
        var f=couponEntry(UUID.randomUUID());f.put("externalId","verified-offer");f.put("expectedRevision",BigDecimal.ZERO);f.put("importedOn","2026-11-08");f.put("importSource","weekly.pdf");
        String body=Json.object(Map.of("entries",List.of(f)));var response=call("POST","/clipped-coupons/walgreens/import","owner",body,"weekly-import",null);assertEquals(200,response.statusCode(),response.body());assertEquals(response.body(),call("POST","/clipped-coupons/walgreens/import","owner",body,"weekly-import",null).body());assertEquals(2,coupons.data.size());assertEquals(absent,coupons.data.get(absent.couponId()));assertEquals(0,coupons.redemptions);
    }
    @SuppressWarnings("unchecked") private Map<String,Object> realCouponFixture(String kind) throws Exception {
        String file="/coupons/walgreens-"+kind+"-redacted.json.gz";String text;
        try(var resource=getClass().getResourceAsStream(file)){assertNotNull(resource);try(var in=new java.util.zip.GZIPInputStream(resource)){text=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}}
        assertFalse(text.contains("Member ID"));assertFalse(text.contains("Slaughter"));assertFalse(text.contains("Hi, Anita"));
        var f=Json.parseObject(text);var pages=new ArrayList<WalgreensCouponImport.PageText>();
        for(var p:ReceiptInput.rows(f,"pages",60)) {
            var glyphs=new ArrayList<WalgreensCouponImport.Glyph>();for(var g:ReceiptInput.rows(p,"glyphs",20000))glyphs.add(new WalgreensCouponImport.Glyph(((BigDecimal)g.get("x")).floatValue(),((BigDecimal)g.get("y")).floatValue(),((BigDecimal)g.get("width")).floatValue(),((BigDecimal)g.get("height")).floatValue(),(Boolean)g.get("bold"),(String)g.get("text")));
            pages.add(new WalgreensCouponImport.PageText((int)Json.number(p.get("page")),glyphs,"",false));
        }
        return WalgreensCouponImport.structured(pages,"real-"+kind+".pdf",LocalDate.of(2026,11,8),String.valueOf(f.get("layout")),kind.equals("available")?null:197);
    }
    @Test void newAvailableExpirationDoesNotOverwriteClippedOfferAndRepeatsDoNotDuplicate() throws Exception {
        var first=couponEntry(UUID.randomUUID());first.put("description","Colgate $1 off 1");first.put("expirationDate","2026-11-14");first.put("offerDetails",Map.of("importKey","colgate-1114"));
        assertEquals(200,call("POST","/clipped-coupons/walgreens/import","owner",Json.object(Map.of("entries",List.of(first))),"old-colgate",null).statusCode());
        var newer=new LinkedHashMap<>(first);newer.put("couponId",UUID.randomUUID().toString());newer.put("expirationDate","2026-11-21");newer.put("status","AVAILABLE");newer.put("clippedOn",null);newer.put("offerDetails",Map.of("importKey","colgate-1121"));
        for(String key:List.of("new-colgate","new-colgate-repeat")){var response=call("POST","/clipped-coupons/walgreens/import","owner",Json.object(Map.of("entries",List.of(newer))),key,null);assertEquals(200,response.statusCode(),response.body());}
        assertEquals(2,coupons.data.size());assertEquals(1,coupons.data.values().stream().filter(c->c.status()==CouponStatus.CLIPPED&&c.coupon().getExpirationDate().equals(LocalDate.of(2026,11,14))).count());assertEquals(1,coupons.data.values().stream().filter(c->c.status()==CouponStatus.AVAILABLE&&c.coupon().getExpirationDate().equals(LocalDate.of(2026,11,21))).count());
    }
    @Test @SuppressWarnings("unchecked") void currentYearResolutionUpdatesPriorYearlessIdentityAndKeepsNextYearsOfferSeparate() throws Exception {
        var rows=(List<Map<String,Object>>)realCouponFixture("available").get("entries");var observed=rows.stream().filter(r->"CURRENT_IMPORT_YEAR".equals(((Map<?,?>)r.get("offerDetails")).get("dateYearBasis"))).findFirst().orElseThrow();
        var incoming=Json.parseObject(Json.object(observed));var metadata=(Map<String,Object>)incoming.get("offerDetails");var oldId=UUID.randomUUID();var old=new LinkedHashMap<>(incoming);old.put("couponId",oldId.toString());old.put("expirationDate",null);old.put("startsOn",null);old.put("description","My verified correction");old.put("offerDetails",Map.of("importKey",metadata.get("previousImportKey"),"datePrecision","MONTH_DAY_RANGE"));
        assertEquals(200,call("POST","/clipped-coupons/walgreens/import","owner",Json.object(Map.of("entries",List.of(old))),"yearless-original",null).statusCode());
        var response=call("POST","/clipped-coupons/walgreens/import","owner",Json.object(Map.of("entries",List.of(incoming),"summary",true)),"resolve-current-year",null);assertEquals(200,response.statusCode(),response.body());assertEquals(1,coupons.data.size());assertEquals(observed.get("expirationDate"),coupons.data.get(oldId).coupon().getExpirationDate());assertEquals("My verified correction",coupons.data.get(oldId).coupon().getCouponDescription());
        var next=new LinkedHashMap<>(incoming);next.put("couponId",UUID.randomUUID().toString());next.put("expirationDate",observed.get("expirationDate").toString().replace("2026","2027"));next.put("startsOn",observed.get("startsOn").toString().replace("2026","2027"));next.put("importedOn","2027-11-08");var nextMetadata=new LinkedHashMap<>(metadata);nextMetadata.put("importKey","new-year-2027-key");next.put("offerDetails",nextMetadata);
        response=call("POST","/clipped-coupons/walgreens/import","owner",Json.object(Map.of("entries",List.of(next))),"next-year-new-offer",null);assertEquals(200,response.statusCode(),response.body());assertEquals(2,coupons.data.size());
    }
    @Test @SuppressWarnings("unchecked") void actualAvailableElevenPageFixtureImportsAll141OffersWith37RecordedYearlessRanges() throws Exception {
        var result=realCouponFixture("available");var rows=(List<Map<String,Object>>)result.get("entries");assertEquals(141,rows.size());assertEquals(141,rows.stream().map(r->r.get("couponId")).distinct().count());assertEquals("AVAILABLE",result.get("documentKind"));assertNull(result.get("declaredCount"));assertEquals(0L,result.get("needsReview"));assertEquals(0L,result.get("missingExpiration"));assertEquals(Map.of("DOLLARS",141L),result.get("offerTypes"));
        String[][] examples={{"Angel Soft","1.50","3","2026-11-14"},{"Kotex","1","1","2026-11-21"},{"Windex","0.50","1","2026-11-21"},{"Colgate","1","1","2026-11-21"},{"Purex","2","1","2026-11-14"},{"L'ORÉAL PARIS","3","2","2026-11-21"}};
        for(var e:examples){var r=rows.stream().filter(x->x.get("description").toString().startsWith(e[0])&&"1".equals(((Map<?,?>)x.get("offerDetails")).get("sourcePage"))).findFirst().orElseThrow();assertEquals(e[1],r.get("amount"));assertEquals(Integer.parseInt(e[2]),r.get("requiredQuantity"));assertEquals(LocalDate.parse(e[3]),r.get("expirationDate"));}
        assertTrue(rows.stream().allMatch(r->r.get("status").equals("AVAILABLE")&&r.get("clippedOn")==null));assertEquals(37,rows.stream().filter(r->"CURRENT_IMPORT_YEAR".equals(((Map<?,?>)r.get("offerDetails")).get("dateYearBasis"))).count());
        var recticare=rows.stream().filter(r->r.get("description").toString().startsWith("RectiCare")).findFirst().orElseThrow();assertEquals(LocalDate.of(2026,10,24),recticare.get("startsOn"));assertEquals(LocalDate.of(2026,11,28),recticare.get("expirationDate"));
        var valid=rows.stream().filter(r->"PARSED".equals(((Map<?,?>)r.get("offerDetails")).get("reviewStatus"))).toList();assertEquals(141,valid.size());String body=Json.object(Map.of("entries",valid,"summary",true));
        var first=call("POST","/clipped-coupons/walgreens/import","owner",body,"actual-available-first",null);assertEquals(200,first.statusCode(),first.body());var summary=(Map<?,?>)Json.parseObject(first.body()).get("summary");assertEquals(new BigDecimal("141"),summary.get("imported"));assertEquals(BigDecimal.ZERO,summary.get("updated"));assertEquals(BigDecimal.ZERO,summary.get("failed"));
        var again=call("POST","/clipped-coupons/walgreens/import","owner",body,"actual-available-again",null);assertEquals(200,again.statusCode(),again.body());assertEquals(new BigDecimal("141"),((Map<?,?>)Json.parseObject(again.body()).get("summary")).get("alreadySaved"));assertEquals(141,coupons.data.size());assertEquals(0,coupons.redemptions);
    }
    @Test @SuppressWarnings("unchecked") void actualPrintableFixtureHasAll197OffersAndPreservesStructuredMechanisms() throws Exception {
        var result=realCouponFixture("printable");var rows=(List<Map<String,Object>>)result.get("entries");assertEquals(197,rows.size());assertEquals(197,rows.stream().map(r->r.get("couponId")).distinct().count());
        assertEquals(Map.of("DOLLARS",181L,"REBATE",9L,"BOGO",2L,"REWARD",5L),result.get("offerTypes"));assertEquals(0L,result.get("missingExpiration"));assertEquals(2L,result.get("missingQuantity"));
        assertTrue(rows.stream().allMatch(r->r.get("externalId")==null&&((List<?>)r.get("qualifyingSkus")).isEmpty()));
        assertTrue(rows.stream().allMatch(r->!((Map<String,String>)r.get("offerDetails")).get("category").isBlank()));
        var roc=rows.getFirst();assertEquals("RoC® Skincare — $8 off 2",roc.get("description"));assertEquals("8",roc.get("amount"));assertEquals(2,roc.get("requiredQuantity"));
        var qtip=rows.stream().filter(r->r.get("description").toString().startsWith("Q-Tips")).findFirst().orElseThrow();assertEquals("STORE",qtip.get("couponType"));assertEquals("MONTHLY_SAVINGS_BOOK",((Map<String,String>)qtip.get("offerDetails")).get("issuerType"));
        for(var r:rows) {
            var d=(Map<String,String>)r.get("offerDetails");
            if(r.get("discount").equals("REBATE")){assertNull(r.get("amount"));assertNotNull(d.get("rebateAmount"));}
            if(r.get("discount").equals("BOGO")){assertNull(r.get("amount"));assertEquals("7.00",d.get("maximumSavings"));assertEquals("1",d.get("freeQuantity"));}
            if("15".equals(d.get("rewardAmount"))){assertNull(r.get("amount"));assertEquals("45",r.get("minimumPurchase"));assertEquals("ONLINE_ONLY",d.get("channelRestriction"));}
        }
        var again=(List<Map<String,Object>>)realCouponFixture("printable").get("entries");assertEquals(rows.stream().map(r->r.get("couponId")).toList(),again.stream().map(r->r.get("couponId")).toList());
    }
    @Test @SuppressWarnings("unchecked") void actualWebsiteFixtureHas197CardsAndNeverInventsMissingDatesOrClassifications() throws Exception {
        var result=realCouponFixture("cards");var rows=(List<Map<String,Object>>)result.get("entries");assertEquals(197,rows.size());assertEquals(197,rows.stream().map(r->r.get("couponId")).distinct().count());assertTrue(rows.stream().allMatch(r->r.get("couponType").equals("OTHER")));assertEquals(0L,result.get("missingExpiration"));
        assertEquals(Map.of("DOLLARS",181L,"REBATE",9L,"BOGO",2L,"REWARD",5L),result.get("offerTypes"));
        assertTrue(rows.stream().filter(r->((Map<?,?>)r.get("offerDetails")).containsKey("validityText")).allMatch(r->r.get("expirationDate")!=null));
        assertEquals(7L,result.get("needsReview"));
        assertTrue(rows.stream().allMatch(r->"NEEDS_REVIEW".equals(((Map<?,?>)r.get("offerDetails")).get("qualificationStatus"))));
    }
    @Test @SuppressWarnings("unchecked") void importingActualPrintableTwiceAddsNoCouponsAndProtectsManualCorrectionsAndRedemption() throws Exception {
        var entries=(List<Map<String,Object>>)realCouponFixture("printable").get("entries");String body=Json.object(Map.of("entries",entries));
        var response=call("POST","/clipped-coupons/walgreens/import","owner",body,"actual-import-1",null);assertEquals(200,response.statusCode(),response.body());assertEquals(197,coupons.data.size());
        var old=coupons.data.values().iterator().next();var fields=new LinkedHashMap<>(entries.stream().filter(r->r.get("couponId").equals(old.couponId())).findFirst().orElseThrow());fields.put("description","Manual correction preserved");var normalizedFields=Json.parseObject(Json.object(fields));var corrected=CouponInput.edit(old,normalizedFields);coupons.data.put(corrected.couponId(),corrected);
        var other=coupons.data.values().stream().filter(c->!c.couponId().equals(corrected.couponId())).findFirst().orElseThrow();var used=new ClippedCoupon(other.couponId(),other.provider(),other.externalId(),other.coupon(),CouponStatus.APPLIED,other.clippedOn(),other.clippedOn(),UUID.randomUUID(),2);coupons.data.put(used.couponId(),used);
        var again=call("POST","/clipped-coupons/walgreens/import","owner",body,"actual-import-2",null);assertEquals(200,again.statusCode(),again.body());assertEquals(197,coupons.data.size());assertEquals(corrected,coupons.data.get(corrected.couponId()));assertEquals(used,coupons.data.get(used.couponId()));assertEquals(0,coupons.redemptions);
    }
    @Test @SuppressWarnings("unchecked") void nextSundayRefreshUpdatesOnlyImportProvenanceAndLeavesMissingOffersUntouched() throws Exception {
        var entries=(List<Map<String,Object>>)realCouponFixture("printable").get("entries");var first=entries.getFirst();var absent=entries.get(1);var initial=Json.object(Map.of("entries",List.of(first,absent)));assertEquals(200,call("POST","/clipped-coupons/walgreens/import","owner",initial,"sunday-initial",null).statusCode());
        var id=(UUID)first.get("couponId");var old=coupons.data.get(id);var fields=Json.parseObject(Json.object(first));fields.put("description","Verified correction");var corrected=CouponInput.edit(old,fields);coupons.data.put(id,corrected);var missing=coupons.data.get(absent.get("couponId"));
        var next=new LinkedHashMap<>(first);next.put("importedOn","2026-11-15");next.put("importSource","next-sunday.pdf");var response=call("POST","/clipped-coupons/walgreens/import","owner",Json.object(Map.of("entries",List.of(next))),"sunday-next",null);assertEquals(200,response.statusCode(),response.body());
        var refreshed=coupons.data.get(id);assertEquals(id,refreshed.couponId());assertEquals("Verified correction",refreshed.coupon().getCouponDescription());assertEquals(LocalDate.of(2026,11,15),refreshed.coupon().getTerms().importedOn());assertEquals("2026-11-15",refreshed.coupon().getTerms().offerDetails().get("lastSeenInImport"));assertEquals(CouponStatus.CLIPPED,refreshed.status());assertEquals(missing,coupons.data.get(absent.get("couponId")));assertEquals(0,coupons.redemptions);
    }
    @Test void scannedPdfUsesExistingLocalOcrOnlyWhenTextIsUnreadable() throws Exception {
        byte[] bytes;try(var doc=new org.apache.pdfbox.pdmodel.PDDocument()){doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());var out=new java.io.ByteArrayOutputStream();doc.save(out);bytes=out.toByteArray();}
        var calls=new java.util.concurrent.atomic.AtomicInteger();var result=WalgreensCouponImport.preview(bytes,"scan.pdf",LocalDate.of(2026,11,8),image->{calls.incrementAndGet();return "Walgreens\n1 clipped coupons\nThese digital coupons are automatically applied\nCREST $2 off 1\nManufacturer coupon\nExpires: 11/30/2026";});assertEquals(1,calls.get());assertEquals(1L,result.get("ocrPages"));assertEquals(1L,result.get("needsReview"));
    }
    @Test void malformedOversizedUnknownAndMissingKeyRequestsDoNotWrite() throws Exception {
        for(String body:List.of("{", "{\"name\":\"a\",\"name\":\"b\"}", "{\"name\":\"a\",}", "{} trailing"))
            assertEquals(400,call("POST","/shopping-plans","owner",body,UUID.randomUUID().toString(),null).statusCode());
        assertEquals(400,call("POST","/shopping-plans","owner",planBody(UUID.randomUUID(),"x"),null,null).statusCode());
        assertEquals(413,call("POST","/shopping-plans","owner"," ".repeat(8*1024*1024+1),"large",null).statusCode());
        assertEquals(0,plans.writes);
    }
    @Test void internalErrorsDoNotExposeStorageDetails() throws Exception {
        plans.fail=true;
        var r=call("GET","/shopping-plans/"+UUID.randomUUID(),"reader",null,null,null);
        assertEquals(500,r.statusCode());assertFalse(r.body().contains("secret"));
    }
    static class Plans implements ShoppingPlanRepository {
        final Map<UUID,ShoppingPlan> data=new ConcurrentHashMap<>();int writes;boolean fail;
        public synchronized void save(ShoppingPlan p,String actor){if(data.putIfAbsent(p.planId(),p)!=null)throw new IllegalArgumentException();writes++;}
        public Optional<ShoppingPlan> findById(UUID id){if(fail)throw new IllegalStateException("secret DB password and path");return Optional.ofNullable(data.get(id));}
        public Optional<ShoppingPlan> findByRevision(UUID id,long rev){return findById(id).filter(p->p.revision()==rev);}
        public synchronized ShoppingPlan update(ShoppingPlan p,long expected,String actor,String reason){if(data.get(p.planId()).revision()!=expected)throw new ConcurrentModificationException();data.put(p.planId(),p);writes++;return p;}
    }
    static class Coupons implements ClippedCouponRepository {
        final Map<UUID,ClippedCoupon> data=new ConcurrentHashMap<>();int redemptions;
        public void save(ClippedCoupon c,String actor){data.put(c.couponId(),c);}
        public Optional<ClippedCoupon> findById(UUID id){return Optional.ofNullable(data.get(id));}
        public List<ClippedCoupon> findByProvider(String p){return data.values().stream().filter(c->c.provider().equals(p)).toList();}
        public synchronized ClippedCoupon update(ClippedCoupon c,long expected,String actor,String reason){if(data.get(c.couponId()).revision()!=expected)throw new ConcurrentModificationException();data.put(c.couponId(),c);return c;}
        public synchronized void importReviewed(List<ClippedCoupon> changes,Map<UUID,Long> expected,String actor){for(var c:changes){long stored=data.containsKey(c.couponId())?data.get(c.couponId()).revision():0;if(expected.get(c.couponId())!=stored)throw new ConcurrentModificationException();}changes.forEach(c->data.put(c.couponId(),c));}
        public synchronized ClippedCoupon redeem(UUID id,UUID receipt,LocalDate date,long expected,String actor){var old=data.get(id);if(old.revision()!=expected)throw new ConcurrentModificationException();if(old.status()==CouponStatus.APPLIED)throw new IllegalStateException();var next=new ClippedCoupon(id,old.provider(),old.externalId(),old.coupon(),CouponStatus.APPLIED,old.clippedOn(),date,receipt,expected+1);data.put(id,next);redemptions++;return next;}
    }

    @Test void beforeFundingReceiptSavesWithoutFalseWalgreensCashDiscrepancy() throws Exception {
        var fields = ReceiptInputTest.draft(UUID.randomUUID());
        fields.put("retailer", "Walgreens"); fields.put("reportedTotal", "25.96"); fields.put("tax", "1.98");
        fields.put("printedTotalBasis", "BEFORE_PAYMENT_FUNDING"); fields.put("paymentEntryStatus", "COMPLETE");
        fields.put("items", List.of(Map.of("lineItemId", UUID.randomUUID(), "description", "Basket", "unitPrice", "31.98", "quantity", 1)));
        fields.put("discounts", List.of(Map.of("applicationId", UUID.randomUUID(), "label", "Recorded discounts", "amount", "8", "kind", "STORE_COUPON", "wholeReceipt", true)));
        fields.remove("rrUsed"); fields.put("payments", List.of(
                Map.of("paymentId", UUID.randomUUID(), "type", "WALGREENS_CASH", "amount", "20"),
                Map.of("paymentId", UUID.randomUUID(), "type", "CARD", "amount", "5.96")));
        fields.put("earnings", List.of()); fields.put("evidence", List.of());
        var preview = call("POST", "/receipts/preview", "owner", Json.object(fields), null, null);
        assertEquals(200, preview.statusCode(), preview.body());
        var response = Json.parseObject(preview.body());
        var totals = (Map<?, ?>) response.get("totals");
        var review = (Map<?, ?>) totals.get("paymentReview");
        assertEquals("BALANCED", review.get("status"));
        assertEquals(false, review.get("acknowledgementRequired"));
        assertEquals(0, ((BigDecimal) totals.get("difference")).compareTo(new BigDecimal("20")));
        fields.put("reviewToken", response.get("reviewToken"));
        var created = call("POST", "/receipts", "owner", Json.object(fields), "split-funding", null);
        assertEquals(201, created.statusCode(), created.body());
        var saved = receipts.findById(UUID.fromString(fields.get("transactionId").toString())).orElseThrow();
        assertEquals(ReceiptMeta.PrintedTotalBasis.BEFORE_PAYMENT_FUNDING, saved.metadata().printedTotalBasis());
        assertEquals(0, saved.transaction().getReceiptTotals().getFinalTotal().compareTo(new BigDecimal("25.96")));
    }

    @Test void receiptCorrectionsRequirePreviewRevisionAndOwnerAndRetainHistory() throws Exception {
        UUID id = receipt();
        var body = new LinkedHashMap<String,Object>();
        body.put("retailer","Corrected Store"); body.put("reason","Correct store name");
        String payload = Json.object(body);
        assertEquals(403,call("POST","/receipts/"+id+"/edit-preview","reader",payload,"preview","1").statusCode());
        assertEquals(409,call("PUT","/receipts/"+id,"owner",payload,"no-preview","1").statusCode());
        var preview = call("POST","/receipts/"+id+"/edit-preview","owner",payload,"preview","1");
        assertEquals(200,preview.statusCode(),preview.body());
        body.put("reviewToken",Json.parseObject(preview.body()).get("reviewToken"));
        body.put("acknowledgeDifference",true);
        var before = ReceiptSnapshot.from(receipts.findById(id).orElseThrow().transaction());
        var saved = call("PUT","/receipts/"+id,"owner",Json.object(body),"correct","1");
        assertEquals(200,saved.statusCode(),saved.body());
        assertEquals(before,ReceiptSnapshot.from(receipts.findById(id).orElseThrow().transaction()));
        assertEquals("Corrected Store",receipts.findById(id).orElseThrow().metadata().retailer());
        assertEquals(2,receipts.findById(id).orElseThrow().revision());
        assertEquals(saved.body(),call("PUT","/receipts/"+id,"owner",Json.object(body),"correct","1").body());
        assertEquals(409,call("PUT","/receipts/"+id,"owner",Json.object(body),"stale","1").statusCode());
        assertEquals("Store",receipts.creationHistory().getFirst().after().metadata().retailer());
    }
    @Test void receiptDeletionAndRestorationRequireConfirmationAndPreserveContents() throws Exception {
        UUID id = receipt(); var before = ReceiptSnapshot.from(receipts.findById(id).orElseThrow().transaction());
        String payload = Json.object(Map.of("reason","Duplicate entry","confirmed",true));
        assertEquals(403,call("DELETE","/receipts/"+id,"reader",payload,"delete-reader","1").statusCode());
        assertEquals(400,call("DELETE","/receipts/"+id,"owner","{\"reason\":\"Duplicate\"}","unconfirmed","1").statusCode());
        var deleted = call("DELETE","/receipts/"+id,"owner",payload,"delete","1");
        assertEquals(200,deleted.statusCode(),deleted.body());
        assertTrue(receipts.findById(id).isEmpty());
        assertEquals(before,ReceiptSnapshot.from(receipts.findDeletedById(id).orElseThrow().transaction()));
        assertEquals(deleted.body(),call("DELETE","/receipts/"+id,"owner",payload,"delete","1").body());
        assertEquals(200,call("POST","/receipts/"+id+"/restore","owner",payload,"restore","2").statusCode());
        assertEquals(3,receipts.findById(id).orElseThrow().revision());
        assertEquals(before,ReceiptSnapshot.from(receipts.findById(id).orElseThrow().transaction()));
    }

    @Test void correctedProductsAndPaymentsRetainStableIdsAndRejectChangedPreview() throws Exception {
        UUID id = receipt(); var old = receipts.findById(id).orElseThrow();
        UUID line = old.transaction().getPurchaseItems().getFirst().getLineItemId(), payment = UUID.randomUUID();
        var f = new LinkedHashMap<String,Object>();
        f.put("reason","Correct product price and record card payment"); f.put("reportedTotal","0.81");
        f.put("items",List.of(Map.of("lineItemId",line.toString(),"productName","Soap","regularUnitPrice","1.00","saleUnitPrice","0.80","quantity",1)));
        f.put("payments",List.of(Map.of("paymentId",payment.toString(),"paymentType","CARD","amount","0.81")));
        var preview = call("POST","/receipts/"+id+"/edit-preview","owner",Json.object(f),"price-preview","1");
        assertEquals(200,preview.statusCode(),preview.body());
        f.put("reviewToken",Json.parseObject(preview.body()).get("reviewToken")); f.put("acknowledgeDifference",true);
        f.put("reportedTotal","9.99");
        assertEquals(409,call("PUT","/receipts/"+id,"owner",Json.object(f),"altered-preview","1").statusCode());
        assertEquals(1,receipts.findById(id).orElseThrow().revision());
        f.put("reportedTotal","0.81");
        var saved = call("PUT","/receipts/"+id,"owner",Json.object(f),"price-save","1");
        assertEquals(200,saved.statusCode(),saved.body());
        var tx = receipts.findById(id).orElseThrow().transaction();
        assertEquals(line,tx.getPurchaseItems().getFirst().getLineItemId());
        assertEquals(new BigDecimal("0.80"),tx.getPurchaseItems().getFirst().getSaleUnitPrice());
        assertEquals(payment,tx.getPayments().getFirst().getPaymentId());
        assertEquals(old.transaction().getObservations(),tx.getObservations());
    }

}
