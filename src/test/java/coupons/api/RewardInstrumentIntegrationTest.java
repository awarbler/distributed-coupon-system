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
}
