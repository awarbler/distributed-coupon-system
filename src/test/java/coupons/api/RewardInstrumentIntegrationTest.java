package coupons.api;

import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real API/persistence checks for individually identified accounts and gift cards. */
class RewardInstrumentIntegrationTest {
    final PostgresApiIntegrationTest fixture=new PostgresApiIntegrationTest();
    @BeforeEach void setup()throws Exception{fixture.setup();}
    @AfterEach void cleanup()throws Exception{fixture.cleanup();}
    Map<String,Object> create(String kind,String retailer,String nickname,String unit,String opening)throws Exception {
        var fields=new LinkedHashMap<String,Object>(Map.of("id",UUID.randomUUID().toString(),"kind",kind,"provider",retailer,"nickname",nickname,"unit",unit,"historyComplete",true,"reason","Synthetic observed opening","date","2026-10-30"));
        if(opening!=null)fields.put("opening",opening);
        var response=fixture.call("POST","/reward-instruments",Json.object(fields),UUID.randomUUID().toString(),null);
        assertEquals(201,response.statusCode(),response.body());return Json.parseObject(response.body());
    }
    Map<String,Object> event(Map<String,Object> account,String kind,String amount)throws Exception {
        var response=fixture.call("POST","/reward-instruments/"+account.get("id")+"/events",Json.object(Map.of("id",UUID.randomUUID().toString(),"kind",kind,"amount",amount,"date","2026-10-30","reason","Observed synthetic activity")),UUID.randomUUID().toString(),account.get("revision").toString());
        assertEquals(201,response.statusCode(),response.body());return Json.parseObject(response.body());
    }
    @Test void requestedGiftCardAppearsOnlyWhenMarkedReceived()throws Exception{
        UUID payout=UUID.randomUUID();var request=Json.object(Map.of("payoutId",payout,"provider","Fetch","date","2026-11-05","points","55000","amount","50","destination","GIFT_CARD","brand","Walmart Gift Card","number","3438","status","REQUESTED"));assertEquals(201,fixture.call("POST","/accounts/payouts",request,UUID.randomUUID().toString(),null).statusCode());assertTrue(((List<?>)Json.parseObject(fixture.call("GET","/reward-instruments",null,null,null).body()).get("instruments")).isEmpty());
        var response=fixture.call("PUT","/accounts/payouts/"+payout,Json.object(Map.of("status","RECEIVED","date","2026-11-06")),UUID.randomUUID().toString(),"1");assertEquals(200,response.statusCode(),response.body());var cards=(List<?>)Json.parseObject(fixture.call("GET","/reward-instruments",null,null,null).body()).get("instruments");assertEquals(1,cards.size());var card=(Map<?,?>)cards.getFirst();assertEquals("Walmart Gift Card",card.get("provider"));assertEquals("3438",card.get("lastFour"));assertEquals("50",card.get("opening").toString());assertNull(card.get("calculated"));
    }
    @Test void payoutFinancialCorrectionsAreBlockedWhileLinkedSpendingExists()throws Exception{
        var id=UUID.randomUUID();new coupons.repository.PostgresProviderAccountRepository(fixture.db).saveRedemption(new coupons.repository.ProviderRedemption(id,"Fetch",java.time.LocalDate.of(2026,11,5),null,new java.math.BigDecimal("63500"),new java.math.BigDecimal("50"),coupons.repository.ProviderRedemption.Destination.GIFT_CARD,"Gift Cards",new java.math.BigDecimal("50"),coupons.repository.ProviderRedemption.Status.RECEIVED,java.time.LocalDate.of(2026,11,5),null,List.of(),"Visa synthetic","Verified payout"));
        var instruments=Json.parseObject(fixture.call("GET","/reward-instruments",null,null,null).body());@SuppressWarnings("unchecked") var card=(Map<String,Object>)((List<?>)instruments.get("instruments")).getFirst();event(card,"REDEMPTION","5");
        var response=fixture.call("POST","/accounts/payouts/"+id+"/correction",Json.object(Map.of("void",true,"confirmed",true,"reason","Request correction")),UUID.randomUUID().toString(),"1");assertEquals(400,response.statusCode());assertTrue(response.body().contains("linked card activity"));
        var original=Json.parseObject(fixture.call("GET","/accounts/payouts/"+id,null,null,null).body());assertEquals("1",original.get("revision").toString());assertTrue(original.toString().contains("RECEIVED"));
    }
    @Test void receivedPayoutAutomaticallyCreatesMissingInstrumentAndRejectsDuplicateLinks()throws Exception{
        var payout=UUID.randomUUID();new coupons.repository.PostgresProviderAccountRepository(fixture.db).saveRedemption(new coupons.repository.ProviderRedemption(payout,"Fetch",java.time.LocalDate.of(2026,11,5),null,new java.math.BigDecimal("63500"),new java.math.BigDecimal("50"),coupons.repository.ProviderRedemption.Destination.GIFT_CARD,"Gift Cards",new java.math.BigDecimal("50"),coupons.repository.ProviderRedemption.Status.RECEIVED,java.time.LocalDate.of(2026,11,5),null,List.of(),"Visa issuance","Brand: Visa Prepaid Card | Number: 4204**** **** 4668"));
        var body=Json.object(Map.of("id",UUID.randomUUID(),"provider","Visa Prepaid Card","lastFour","4668","confirmed",true,"reason","Verified original payout"));String path="/accounts/payouts/"+payout+"/gift-card";
        var response=fixture.call("GET","/reward-instruments",null,null,null);assertEquals(200,response.statusCode(),response.body());@SuppressWarnings("unchecked") var instrument=(Map<String,Object>)((List<?>)Json.parseObject(response.body()).get("instruments")).getFirst();assertNull(instrument.get("calculated"));assertEquals("50",instrument.get("opening").toString());assertEquals("PAYOUT:"+payout,instrument.get("evidenceReference"));assertTrue(((List<?>)instrument.get("events")).isEmpty());
        assertEquals(400,fixture.call("POST",path,body,UUID.randomUUID().toString(),"1").statusCode());
        assertEquals(200,fixture.call("POST","/accounts/payouts/reconcile-gift-cards","{}",UUID.randomUUID().toString(),null).statusCode());assertEquals(200,fixture.call("POST","/accounts/payouts/reconcile-gift-cards","{}",UUID.randomUUID().toString(),null).statusCode());assertEquals(1,((List<?>)Json.parseObject(fixture.call("GET","/reward-instruments",null,null,null).body()).get("instruments")).size());
        var original=Json.parseObject(fixture.call("GET","/accounts/payouts/"+payout,null,null,null).body());assertEquals("1",original.get("revision").toString());
    }
    @Test @SuppressWarnings("unchecked") void plannedRRUsesConsumeSelectedSourcesOnlyOnPurchaseAndCannotBeSpentTwice()throws Exception {
        var imported=ShoppingNoteInput.parse(ShoppingNotebookTest.ADIDAS,new coupons.shopping.ShoppingPlan.PlanningSource(ShoppingNotebookTest.ADIDAS,null,null));
        var original=(List<coupons.shopping.ShoppingPlan.ScenarioPayment>)imported.get("payments");
        var payments=new ArrayList<coupons.shopping.ShoppingPlan.ScenarioPayment>();
        var supplement=new coupons.repository.PostgresReceiptSupplementRepository(fixture.db);
        for(var payment:original) {
            var source=UUID.randomUUID();supplement.addStandaloneReward(new coupons.rewards.RegisterReward(source,payment.notes(),payment.amount(),java.time.LocalDate.of(2026,10,1),java.time.LocalDate.of(2026,12,1),null,"MANUFACTURER"));
            payments.add(new coupons.shopping.ShoppingPlan.ScenarioPayment(payment.id(),payment.type(),payment.amount(),payment.notes(),"MANUFACTURER",source));
        }
        var scenario=new coupons.shopping.ShoppingPlan.Scenario((List<coupons.shopping.ShoppingPlan.Line>)imported.get("items"),(List<coupons.shopping.ShoppingPlan.ScenarioCoupon>)imported.get("coupons"),(List<coupons.shopping.ShoppingPlan.ScenarioPromotion>)imported.get("promotions"),java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,null,"CASH","",null,payments,(coupons.shopping.ShoppingPlan.PlanningSource)imported.get("source"));
        UUID plan=UUID.randomUUID();fixture.plans.save(new coupons.shopping.ShoppingPlan(plan,"Addidas","Walgreens",java.time.LocalDate.of(2026,10,19),List.of(),1,scenario),"test");
        assertTrue(supplement.rrUsageLinks().isEmpty());
        var reopened=new coupons.repository.PostgresShoppingPlanRepository(fixture.db).findById(plan).orElseThrow();assertEquals(payments,reopened.scenario().payments());assertEquals(ShoppingNotebookTest.ADIDAS,reopened.scenario().source().text());
        UUID receipt=UUID.randomUUID(),line=UUID.randomUUID();var fields=ReceiptInputTest.draft(receipt);
        fields.remove("rrUsed");fields.remove("rrUsageId");fields.put("receiptNumber","ADIDAS-PLAN");fields.put("purchaseDate","2026-10-19");fields.put("tax","0");fields.put("reportedTotal","5");
        fields.put("items",List.of(Map.of("lineItemId",line,"description","Addidas Gift Package","regularUnitPrice","10","unitPrice","10","quantity",3)));
        fields.put("discounts",List.of(Map.of("applicationId",UUID.randomUUID(),"label","BOGO 50%","amount","5","kind","SALE_PROMOTION","lineItemIds",List.of(line)),Map.of("applicationId",UUID.randomUUID(),"label","$4 off 2","amount","4","kind","MANUFACTURER","lineItemIds",List.of(line),"requiredQuantity",2)));
        fields.put("rrUses",payments.stream().map(p->Map.of("eventId",p.id(),"amount",p.amount().toPlainString(),"recordedDescription",p.notes())).toList());fields.put("earnings",List.of());fields.put("payments",List.of(Map.of("paymentId",UUID.randomUUID(),"type","CASH","amount","5")));
        var input=ReceiptInput.read(Json.parseObject(Json.object(fields)));var request=new coupons.repository.ReceiptWriteRequest(UUID.randomUUID(),java.time.Instant.now());var context=new coupons.repository.ReceiptCreateCtx("test");
        var saved=fixture.receipts.saveWithPlan(receipt,input.transaction(),input.metadata(),List.of(),request,context,List.of(),plan,1);
        assertEquals(2,supplement.rrUsageLinks().size());assertEquals(saved,fixture.receipts.saveWithPlan(receipt,input.transaction(),input.metadata(),List.of(),request,context,List.of(),plan,1));assertEquals(2,supplement.rrUsageLinks().size());
        UUID second=UUID.randomUUID(),secondPlan=UUID.randomUUID();fixture.plans.save(new coupons.shopping.ShoppingPlan(secondPlan,"Duplicate attempt","Walgreens",java.time.LocalDate.of(2026,10,19),List.of(),1,scenario),"test");
        assertThrows(IllegalArgumentException.class,()->fixture.receipts.saveWithPlan(second,input.transaction(),new coupons.repository.ReceiptMeta("Walgreens","ADIDAS-DUPLICATE",null,null),List.of(),new coupons.repository.ReceiptWriteRequest(UUID.randomUUID(),java.time.Instant.now()),context,List.of(),secondPlan,1));
        assertTrue(fixture.receipts.findById(second).isEmpty());assertNull(fixture.plans.findById(secondPlan).orElseThrow().purchasedReceiptId());
    }

    @Test void receiptAssignmentsSynchronizeEditsReassignmentRemovalAndRetries()throws Exception {
        var first=create("GIFT_CARD","Walmart","First card","USD","50.00");
        var second=create("GIFT_CARD","Walmart","Second card","USD","25.00");
        UUID receipt=UUID.randomUUID(),payment=UUID.randomUUID();
        var fields=ReceiptInputTest.draft(receipt);
        fields.put("payments",List.of(Map.of("paymentId",payment,"type","GIFT_CARD","amount","8.75","giftCardId",first.get("id"))));
        var preview=fixture.call("POST","/receipts/preview",Json.object(fields),null,null);assertEquals(200,preview.statusCode(),preview.body());
        fields.put("reviewToken",Json.parseObject(preview.body()).get("reviewToken"));fields.put("acknowledgeDifference",true);
        var created=fixture.call("POST","/receipts",Json.object(fields),"assigned-card",null);assertEquals(201,created.statusCode(),created.body());
        assertEquals(created.body(),fixture.call("POST","/receipts",Json.object(fields),"assigned-card",null).body());
        assertCardBalance(first,"41.25");
        assertEquals(UUID.fromString(first.get("id").toString()),fixture.receipts.findById(receipt).orElseThrow().transaction().getPayments().getFirst().getGiftCardId());
        editCardPayment(receipt,1,payment,first.get("id"),"11.48");assertCardBalance(first,"38.52");
        editCardPayment(receipt,2,payment,second.get("id"),"11.48");assertCardBalance(first,"50.00");assertCardBalance(second,"13.52");
        var request=new coupons.repository.ReceiptWriteRequest(UUID.randomUUID(),java.time.Instant.now());
        var context=new coupons.repository.ReceiptChangeContext("test","Delete receipt","test",java.time.Instant.now());
        fixture.receipts.softDelete(receipt,3,request,context);assertCardBalance(second,"25.00");
        fixture.receipts.restore(receipt,4,new coupons.repository.ReceiptWriteRequest(UUID.randomUUID(),java.time.Instant.now()),context);assertCardBalance(second,"13.52");
        editCardPayment(receipt,5,payment,null,"11.48");assertCardBalance(second,"25.00");
        editCardPayment(receipt,6,payment,null,"11.48");assertCardBalance(second,"25.00");
    }
    void assertCardBalance(Map<String,Object> card,String expected)throws Exception {
        var response=fixture.call("GET","/reward-instruments/"+card.get("id"),null,null,null);assertEquals(200,response.statusCode(),response.body());
        assertEquals(0,new java.math.BigDecimal(expected).compareTo(new java.math.BigDecimal(Json.parseObject(response.body()).get("calculated").toString())));
    }
    void editCardPayment(UUID receipt,long revision,UUID payment,Object card,String amount)throws Exception {
        var tender=new LinkedHashMap<String,Object>(Map.of("paymentId",payment,"paymentType","GIFT_CARD","amount",amount));tender.put("giftCardId",card);
        var body=new LinkedHashMap<String,Object>(Map.of("reason","Correct gift card payment","payments",List.of(tender)));
        var preview=fixture.call("POST","/receipts/"+receipt+"/edit-preview",Json.object(body),null,""+revision);assertEquals(200,preview.statusCode(),preview.body());
        body.put("reviewToken",Json.parseObject(preview.body()).get("reviewToken"));body.put("acknowledgeDifference",true);
        var response=fixture.call("PUT","/receipts/"+receipt,Json.object(body),"edit-card-"+revision,""+revision);assertEquals(200,response.statusCode(),response.body());
    }

    @Test void separatesAccountsAndCardInstrumentsAndRetainsReportedDiscrepancies()throws Exception {
        var a=create("ACCOUNT_REWARD","Target","My Target","USD","12.00");
        var b=create("ACCOUNT_REWARD","Target","Son's Target","USD","7.00");
        var card=create("GIFT_CARD","Target","Promotional card","USD","10.00");
        a=event(a,"REPORTED_BALANCE","11.00");
        assertEquals("12.00",a.get("calculated").toString());assertEquals("11.00",a.get("reported").toString());assertEquals("1.00",a.get("difference").toString());
        assertEquals("7.00",b.get("calculated").toString());assertEquals("10.00",card.get("calculated").toString());
        assertNotEquals(a.get("id"),b.get("id"));assertNull(a.get("lastFour"));
        var workspace=Json.parseObject(fixture.call("GET","/workspace",null,null,null).body());assertEquals(3,((List<?>)workspace.get("rewardInstruments")).size());
    }
    @Test void unknownHistoryStaysUnknownAndCorrectionsPreserveOriginalEvents()throws Exception {
        var unknown=create("ACCOUNT_REWARD","Target","Unknown opening","USD",null);
        unknown=event(unknown,"CREDIT","5.00");assertNull(unknown.get("calculated"));
        @SuppressWarnings("unchecked") var previous=(Map<String,Object>)((List<?>)unknown.get("events")).getFirst();
        var response=fixture.call("POST","/reward-instruments/"+unknown.get("id")+"/events",Json.object(Map.of("id",UUID.randomUUID().toString(),"kind","CREDIT","amount","4.00","date","2026-10-30","reason","Correct wrong amount","supersedes",previous.get("id"),"confirmed",true)),"correct-event",unknown.get("revision").toString());
        assertEquals(201,response.statusCode(),response.body());var corrected=Json.parseObject(response.body());assertEquals(2,((List<?>)corrected.get("events")).size());assertNull(corrected.get("calculated"));
        assertTrue(response.body().contains("5.00"));assertTrue(response.body().contains("4.00"));
        assertEquals(409,fixture.call("POST","/reward-instruments/"+unknown.get("id")+"/events",Json.object(Map.of("id",UUID.randomUUID().toString(),"kind","CREDIT","amount","9","reason","Stale")),"stale-event","1").statusCode());
    }
    @Test void observedExchangeCreatesOneLinkedCardWithoutNewEarningsAndRetriesSafely()throws Exception {
        var fetch=create("ACCOUNT_REWARD","Fetch","My Fetch","POINTS","20000");
        var body=Json.object(Map.of("id",UUID.randomUUID().toString(),"cardId",UUID.randomUUID().toString(),"provider","Target","nickname","Fetch exchange","sourceAmount","10000","cardValue","10.00","date","2026-10-30","reason","Observed redemption and issued card"));
        String path="/reward-instruments/"+fetch.get("id")+"/exchange";
        var response=fixture.call("POST",path,body,"exchange-once","1");assertEquals(201,response.statusCode(),response.body());
        assertEquals(response.body(),fixture.call("POST",path,body,"exchange-once","1").body());
        var card=Json.parseObject(response.body());card=event(card,"REDEMPTION","4.00");assertEquals("6.00",card.get("calculated").toString());assertEquals("0",card.get("newEarnings").toString());
        try(var c=fixture.db.open();var rows=c.createStatement().executeQuery("SELECT count(*) FROM receipt_current")){rows.next();assertEquals(0,rows.getInt(1));}
        var source=fixture.call("GET","/reward-instruments/"+fetch.get("id"),null,null,null);assertEquals(200,source.statusCode());assertTrue(source.body().contains("10000"));
    }

    @Test void rejectsCredentialFieldsAndReaderWritesAndProtectsHistory()throws Exception {
        var body=Json.object(Map.of("id",UUID.randomUUID().toString(),"kind","ACCOUNT_REWARD","provider","Target","unit","USD","reason","Test","password","secret"));
        assertEquals(400,fixture.call("POST","/reward-instruments",body,"no-credentials",null).statusCode());
        var reader=java.net.http.HttpRequest.newBuilder(java.net.URI.create(fixture.base+"/reward-instruments")).header("Authorization","Bearer reader").header("Content-Type","application/json").header("Idempotency-Key","reader-write").POST(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build();
        assertEquals(403,fixture.client.send(reader,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
        var account=create("ACCOUNT_REWARD","Target","Protected","USD","5");account=event(account,"CREDIT","1");
        try(var c=fixture.db.open()){
            assertThrows(java.sql.SQLException.class,()->c.createStatement().executeUpdate("UPDATE reward_instrument_events SET amount=999"));
            assertThrows(java.sql.SQLException.class,()->c.createStatement().executeUpdate("DELETE FROM reward_instrument_history"));
            assertThrows(java.sql.SQLException.class,()->c.createStatement().executeUpdate("DELETE FROM reward_instruments"));
        }
    }
    @Test void unknownAndDifferentObservationDatesDoNotInventComparableDifference()throws Exception {
        var a=create("ACCOUNT_REWARD","Target","Dated","USD","12");
        var response=fixture.call("POST","/reward-instruments/"+a.get("id")+"/events",Json.object(Map.of("id",UUID.randomUUID().toString(),"kind","REPORTED_BALANCE","amount","11","date","2026-10-29","reason","Actual earlier observation")),"earlier-report",a.get("revision").toString());
        assertEquals(201,response.statusCode(),response.body());var row=Json.parseObject(response.body());assertNull(row.get("difference"));assertEquals("12",row.get("calculated").toString());assertEquals("11",row.get("reported").toString());
    }
    @Test void explicitReassociationPreservesBothHistoriesAndMovesOnlyTheCorrectedActivity()throws Exception {
        var a=create("ACCOUNT_REWARD","Target","First account","USD","0");var b=create("ACCOUNT_REWARD","Target","Second account","USD","0");a=event(a,"CREDIT","5");
        @SuppressWarnings("unchecked") var previous=(Map<String,Object>)((List<?>)a.get("events")).getFirst();
        var body=Json.object(Map.of("id",UUID.randomUUID().toString(),"kind","CREDIT","amount","5","date","2026-10-30","reason","Correct account association","supersedes",previous.get("id"),"sourceRevision",a.get("revision").toString(),"confirmed",true));
        var moved=fixture.call("POST","/reward-instruments/"+b.get("id")+"/events",body,"move-observation",b.get("revision").toString());assertEquals(201,moved.statusCode(),moved.body());
        assertEquals("5",Json.parseObject(moved.body()).get("calculated").toString());
        var original=Json.parseObject(fixture.call("GET","/reward-instruments/"+a.get("id"),null,null,null).body());assertEquals("0",original.get("calculated").toString());assertEquals(1,((List<?>)original.get("events")).size());
    }
    @Test void failedExchangeRollsBackCardAndSourceAndUnknownRedemptionStaysUnknown()throws Exception {
        var a=create("ACCOUNT_REWARD","Fetch","Synthetic","POINTS","20000");
        try(var c=fixture.db.open()){c.createStatement().execute("ALTER TABLE reward_instrument_events ADD CONSTRAINT synthetic_exchange_failure CHECK(kind<>'EXCHANGE')");}
        var body=new LinkedHashMap<String,Object>(Map.of("id",UUID.randomUUID().toString(),"cardId",UUID.randomUUID().toString(),"provider","Target","cardValue","10","reason","private synthetic source note","date","2026-10-30"));
        var errorLog=new java.io.ByteArrayOutputStream();var originalError=System.err;
        java.net.http.HttpResponse<String> failure;
        try{System.setErr(new java.io.PrintStream(errorLog,true,java.nio.charset.StandardCharsets.UTF_8));failure=fixture.call("POST","/reward-instruments/"+a.get("id")+"/exchange",Json.object(body),"injected-failure","1");}finally{System.setErr(originalError);}
        assertEquals(500,failure.statusCode());assertFalse(failure.body().contains("synthetic_exchange_failure"));
        assertFalse(errorLog.toString(java.nio.charset.StandardCharsets.UTF_8).contains("private synthetic source note"),"Failure logs must not disclose private financial source notes");
        try(var c=fixture.db.open();var rows=c.createStatement().executeQuery("SELECT count(*) FROM reward_instruments")){rows.next();assertEquals(1,rows.getInt(1));}
        try(var c=fixture.db.open()){c.createStatement().execute("ALTER TABLE reward_instrument_events DROP CONSTRAINT synthetic_exchange_failure");}
        assertEquals(201,fixture.call("POST","/reward-instruments/"+a.get("id")+"/exchange",Json.object(body),"safe-new-request-after-proven-rollback","1").statusCode());
        var source=Json.parseObject(fixture.call("GET","/reward-instruments/"+a.get("id"),null,null,null).body());assertNull(source.get("calculated"));assertEquals("0",source.get("newEarnings").toString());
    }

    @Test void linksActualReceiptCreditOnceWithoutChangingReceiptOrCountingAnotherEarning()throws Exception {
        UUID receiptId=UUID.randomUUID();var fields=ReceiptInputTest.draft(receiptId);
        var preview=fixture.call("POST","/receipts/preview",Json.object(fields),null,null);assertEquals(200,preview.statusCode(),preview.body());fields.put("reviewToken",Json.parseObject(preview.body()).get("reviewToken"));fields.put("acknowledgeDifference",true);
        assertEquals(201,fixture.call("POST","/receipts",Json.object(fields),"receipt-for-association",null).statusCode());
        String originalResponse=fixture.call("GET","/receipts/"+receiptId,null,null,null).body();var original=fixture.receipts.findById(receiptId).orElseThrow();var fetch=original.transaction().getObservations().earnings().stream().filter(e->e.provider().equals("Fetch")).findFirst().orElseThrow();
        var account=create("ACCOUNT_REWARD","Fetch","Recorded account","POINTS","0");var second=create("ACCOUNT_REWARD","Fetch","Other account","POINTS","0");
        account=event(account,"CREDIT","25");
        @SuppressWarnings("unchecked") var observed=(Map<String,Object>)((List<?>)account.get("events")).getFirst();
        var sourceFields=new LinkedHashMap<String,Object>(Map.of("id",UUID.randomUUID().toString(),"kind","CREDIT","amount","25","sourceReceiptId",receiptId.toString(),"sourceEventId",fetch.earningId().toString(),"date","2026-10-30","reason","Explicitly link the existing observation to its actual receipt","supersedes",observed.get("id"),"confirmed",true));
        var link=Json.object(sourceFields);
        var result=fixture.call("POST","/reward-instruments/"+account.get("id")+"/events",link,"associate-receipt",account.get("revision").toString());assertEquals(201,result.statusCode(),result.body());var data=Json.parseObject(result.body());assertEquals("25",data.get("calculated").toString());assertEquals("0",data.get("newEarnings").toString());
        sourceFields.remove("supersedes");sourceFields.remove("confirmed");sourceFields.put("id",UUID.randomUUID().toString());assertEquals(409,fixture.call("POST","/reward-instruments/"+second.get("id")+"/events",Json.object(sourceFields),"duplicate-association","1").statusCode());
        assertEquals(originalResponse,fixture.call("GET","/receipts/"+receiptId,null,null,null).body(),"Complete recorded receipt response must remain byte-for-byte identical");assertEquals(1,fixture.receipts.findHistoryByReceiptId(receiptId).size());
    }
    @Test void openingCorrectionsRetainUnknownOriginalAndRequireConfirmation()throws Exception {
        var a=create("ACCOUNT_REWARD","Target","Opening missing","USD",null);a=event(a,"CREDIT","5");
        var correction=Json.object(Map.of("opening","0","date","2026-10-30","historyComplete",true,"reason","Observed original opening now available","confirmed",true));
        var result=fixture.call("PUT","/reward-instruments/"+a.get("id"),correction,"opening-correction",a.get("revision").toString());assertEquals(200,result.statusCode(),result.body());var row=Json.parseObject(result.body());assertEquals("5",row.get("calculated").toString());
        @SuppressWarnings("unchecked") var first=(Map<String,Object>)((List<?>)row.get("history")).getFirst();assertNull(first.get("opening"));
        assertEquals(400,fixture.call("PUT","/reward-instruments/"+a.get("id"),Json.object(Map.of("nickname","Unconfirmed","reason","Test")),"no-confirmation",row.get("revision").toString()).statusCode());
    }
    @Test void concurrentExchangesWithOneSourceRevisionCannotSpendTwice()throws Exception {
        var a=create("ACCOUNT_REWARD","Fetch","Concurrent source","POINTS","20000");
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var start=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> attempt=()->{start.await();var body=Json.object(Map.of("id",UUID.randomUUID().toString(),"cardId",UUID.randomUUID().toString(),"provider","Target","sourceAmount","10000","cardValue","10","reason","Observed exchange"));return fixture.call("POST","/reward-instruments/"+a.get("id")+"/exchange",body,UUID.randomUUID().toString(),"1").statusCode();};
            var left=pool.submit(attempt);var right=pool.submit(attempt);start.countDown();assertEquals(Set.of(201,409),Set.of(left.get(),right.get()));
        }
        try(var c=fixture.db.open();var rows=c.createStatement().executeQuery("SELECT count(*) FROM reward_instruments WHERE kind='GIFT_CARD'")){rows.next();assertEquals(1,rows.getInt(1));}
    }

    @Test void observedWithdrawalsReturnsReversalsAndAdjustmentsRemainDistinctFromEarnings()throws Exception {
        var a=create("ACCOUNT_REWARD","Target","Observed activity","USD","20");
        for(var entry:List.of(Map.entry("CREDIT","5"),Map.entry("REDEMPTION","4"),Map.entry("WITHDRAWAL","3"),Map.entry("ADJUSTMENT","-2"),Map.entry("REFUND","1"),Map.entry("REVERSAL","1"),Map.entry("RESTORATION","2")))a=event(a,entry.getKey(),entry.getValue());
        assertEquals("18",a.get("calculated").toString());assertEquals("5",a.get("newEarnings").toString());assertEquals(7,((List<?>)a.get("events")).size());
        a=event(a,"REPORTED_BALANCE","-1");assertEquals("18",a.get("calculated").toString());assertEquals("-1",a.get("reported").toString());assertEquals(8,((List<?>)a.get("events")).size());
    }

    @Test void unknownCardHistoryAndEvidenceBackedReplacementDoNotInventOrTransferValue()throws Exception {
        var unknown=create("GIFT_CARD","Target","Unknown card opening","USD",null);unknown=event(unknown,"REPORTED_BALANCE","5");unknown=event(unknown,"REDEMPTION","2");assertNull(unknown.get("calculated"));assertEquals("5",unknown.get("reported").toString());
        var original=create("GIFT_CARD","Target","Original issued card","USD","10");
        var replacement=new LinkedHashMap<String,Object>(Map.of("id",UUID.randomUUID().toString(),"kind","GIFT_CARD","provider","Target","unit","USD","replacementId",original.get("id").toString(),"lastFour","4321","reason","Replacement observed"));
        assertEquals(400,fixture.call("POST","/reward-instruments",Json.object(replacement),"replacement-without-evidence",null).statusCode());
        replacement.put("evidenceReference","Owner-recorded replacement notice reference");
        var saved=fixture.call("POST","/reward-instruments",Json.object(replacement),"evidenced-replacement",null);assertEquals(201,saved.statusCode(),saved.body());var row=Json.parseObject(saved.body());assertNull(row.get("calculated"));assertEquals("4321",row.get("lastFour"));assertEquals(original.get("id"),row.get("replacementId"));
        var old=Json.parseObject(fixture.call("GET","/reward-instruments/"+original.get("id"),null,null,null).body());assertEquals("10",old.get("calculated").toString());
        var fullyUsed=event(original,"REDEMPTION","10");assertEquals("0",fullyUsed.get("calculated").toString());
    }

    @Test void sourceAssociationCannotRelabelForeignCurrencyAsDollars()throws Exception {
        UUID receipt=UUID.randomUUID(),earning=UUID.randomUUID();
        var benefit=new coupons.promotions.RebateBenefit(coupons.promotions.BenefitKind.CASH,new java.math.BigDecimal("5"),java.util.Currency.getInstance("EUR"),null);
        var tx=new coupons.receipts.CouponTransaction(new coupons.receipts.ReceiptTotals(java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO));
        tx.setObservations(new coupons.receipts.ReceiptObservations(java.time.LocalDate.of(2026,10,30),null,List.of(new coupons.receipts.ReceiptObservations.Earning(earning,coupons.receipts.ReceiptObservations.EarningKind.THIRD_PARTY_REBATE,"Synthetic provider","Observed foreign currency",coupons.receipts.ReceiptObservations.Scope.RECEIPT,List.of(),benefit,benefit,coupons.promotions.RebateStatus.CREDITED,null,null,null,List.of())),List.of(),null,List.of(),Map.of()));
        fixture.receipts.save(receipt,tx,null,List.of(),new coupons.repository.ReceiptWriteRequest(UUID.randomUUID(),java.time.Instant.now()),new coupons.repository.ReceiptCreateCtx("test"));
        var account=create("ACCOUNT_REWARD","Synthetic provider","USD account","USD","0");
        var link=Json.object(Map.of("id",UUID.randomUUID().toString(),"kind","CREDIT","amount","5","sourceReceiptId",receipt.toString(),"sourceEventId",earning.toString(),"reason","Attempt unsupported currency relabeling"));
        assertEquals(400,fixture.call("POST","/reward-instruments/"+account.get("id")+"/events",link,"foreign-currency-source","1").statusCode());
        var unchanged=Json.parseObject(fixture.call("GET","/reward-instruments/"+account.get("id"),null,null,null).body());assertEquals("0",unchanged.get("calculated").toString());assertTrue(((List<?>)unchanged.get("events")).isEmpty());
    }

    @Test @SuppressWarnings("unchecked") void cardArchivalPreservesFinancialEventsAndRevisions()throws Exception{
        var card=create("GIFT_CARD","Walgreens","Synthetic card","USD","50");card=event(card,"REDEMPTION","10");
        String id=card.get("id").toString();var rejectedKind=fixture.call("PUT","/reward-instruments/"+id,Json.object(Map.of("kind","ACCOUNT_REWARD","confirmed",true,"reason","Cannot reclassify financial history")),UUID.randomUUID().toString(),card.get("revision").toString());assertEquals(400,rejectedKind.statusCode());
        var response=fixture.call("PUT","/reward-instruments/"+id,Json.object(Map.of("status","ARCHIVED","confirmed",true,"reason","User confirmed duplicate/archive")),UUID.randomUUID().toString(),card.get("revision").toString());
        assertEquals(200,response.statusCode(),response.body());var archived=Json.parseObject(response.body());assertEquals("ARCHIVED",archived.get("status"));assertEquals(new java.math.BigDecimal("40"),archived.get("calculated"));assertEquals(1,((List<?>)archived.get("events")).size());assertTrue(((List<?>)archived.get("history")).size()>=3);
        var reopened=fixture.call("GET","/reward-instruments/"+id,null,null,null);assertEquals("ARCHIVED",Json.parseObject(reopened.body()).get("status"));
        var rejected=fixture.call("POST","/reward-instruments/"+id+"/events",Json.object(Map.of("id",UUID.randomUUID().toString(),"kind","REDEMPTION","amount","5","date","2026-11-07","reason","Not permitted")),UUID.randomUUID().toString(),archived.get("revision").toString());assertEquals(400,rejected.statusCode());
    }
}
