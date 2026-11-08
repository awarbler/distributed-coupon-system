package coupons.api;

import coupons.promotions.ClippedCoupon;
import coupons.promotions.Coupon;
import coupons.promotions.CouponStatus;
import coupons.receipts.CouponTransaction;
import coupons.promotions.CouponType;
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



    @Test void weeklyPreviewPreservesRequiredQuantityAndReturnsBudgetShortfall()throws Exception {
        var candidate=WeeklyDealPlannerTest.deal("required","10","1");var line=candidate.scenario().items().getFirst();
        var clientScenario=Map.of("items",List.of(Map.of("id",line.id(),"name",line.name(),"sale","10","quantity",1)),"coupons",List.of(),"promotions",List.of(),"tax","0","walgreensCashUsed","0","registerRewardsUsed","0","giftCardUsed","0","paymentMethod","CASH");
        var body=Json.object(Map.of("candidates",List.of(Map.of("id",candidate.id(),"name",candidate.name(),"store",candidate.store(),"scenario",clientScenario)),"rewards",List.of(),"walgreensCash","0","budget","5","mode","LOWEST_OOP","day","2026-11-06","requiredItems",List.of(Map.of("candidateId",candidate.id(),"lineId",line.id(),"quantity",2)),"includeOptional",false));
        var response=call("POST","/shopping-plans/weekly-preview","owner",body,null,null);assertEquals(200,response.statusCode(),response.body());
        var result=Json.parseObject(response.body());assertEquals(true,result.get("requirementsMet"));assertEquals(false,result.get("affordable"));assertEquals(0,new BigDecimal("15").compareTo((BigDecimal)result.get("additionalCashNeeded")));
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
            var payout=UUID.randomUUID();var payoutAdded=change.apply("/accounts/payouts",Json.object(Map.of("payoutId",payout.toString(),"provider","P&G Brand","date","2026-10-26","points","75","destination","GIFT_CARD","brand","Target","status","REQUESTED")));assertEquals(201,payoutAdded.statusCode(),payoutAdded.body());
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
        public ClippedCoupon update(ClippedCoupon c,long expected,String actor,String reason){throw new UnsupportedOperationException();}
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
