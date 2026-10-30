package coupons.api;

import java.math.BigDecimal;
import java.math.MathContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import coupons.promotions.AdvertisedDeal;
import coupons.promotions.BenefitKind;
import coupons.promotions.ClippedCoupon;
import coupons.promotions.Coupon;
import coupons.promotions.CouponApplied;
import coupons.promotions.CouponStatus;
import coupons.receipts.CouponTransaction;
import coupons.promotions.CouponType;
import coupons.promotions.DealTerms;
import coupons.inventory.InventoryBalance;
import coupons.rewards.ProviderBalanceSummary;
import coupons.receipts.PurchaseItem;
import coupons.promotions.RebateBenefit;
import coupons.promotions.RebateStatus;
import coupons.receipts.ReceiptObservations;
import coupons.receipts.ReceiptTotals;
import coupons.receipts.SavingsReport;
import coupons.shopping.ShoppingPlan;
import coupons.promotions.SourceArtifact;
import coupons.receipts.TransactionCalculator;
import coupons.repository.GiftCardPromotion;
import coupons.repository.PostgresClippedCouponRepository;
import coupons.repository.PostgresConnectionFactory;
import coupons.repository.PostgresDealRepository;
import coupons.repository.PostgresInventoryRepository;
import coupons.repository.PostgresPlannedPurchaseRepository;
import coupons.repository.PostgresPlatformRepository;
import coupons.repository.PostgresSchema;
import coupons.repository.PostgresShoppingPlanRepository;
import coupons.repository.PostgresTransactionRepository;
import coupons.repository.ReceiptFinancialProjection;
import coupons.repository.PostgresTestDatabase;
import coupons.repository.ReceiptChangeContext;
import coupons.repository.ReceiptCreateCtx;
import coupons.repository.ReceiptEvidence;
import coupons.repository.ReceiptMeta;
import coupons.repository.ReceiptWriteRequest;

/**
 * A private schema in an explicitly configured disposable test database.
 */
class PostgresApiIntegrationTest {

    PostgresConnectionFactory admin, db;
    String schema, base;
    HttpServer server;
    HttpClient client;
    PostgresTransactionRepository receipts;
    PostgresShoppingPlanRepository plans;
    PostgresClippedCouponRepository coupons;

    boolean financialsEnabled() { return false; }

    @BeforeEach
    void setup() throws Exception {
        String url = System.getenv("COUPON_POSTGRES_TEST_URL");
        Assumptions.assumeTrue(url != null && System.getenv("COUPON_POSTGRES_TEST_USER") != null && System.getenv("COUPON_POSTGRES_TEST_PASSWORD") != null);
        admin = new PostgresConnectionFactory(url, System.getenv("COUPON_POSTGRES_TEST_USER"), System.getenv("COUPON_POSTGRES_TEST_PASSWORD"));
        String candidate = "api_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection c = PostgresTestDatabase.targetForSchema(candidate).open(admin)) {
            try (var rows = c.createStatement().executeQuery("SELECT current_database()")) {
                rows.next();
                assertEquals("distributed_coupons_test", rows.getString(1), "Refusing a non-test database before schema changes");
            }
            schema = candidate;
            c.createStatement().execute("CREATE SCHEMA " + schema);
        }
        db = new PostgresConnectionFactory(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema, System.getenv("COUPON_POSTGRES_TEST_USER"), System.getenv("COUPON_POSTGRES_TEST_PASSWORD"));
        try (Connection c = PostgresTestDatabase.targetForSchema(schema).open(db)) {
            PostgresTestDatabase.targetForSchema(schema).verifySchema(c);
            PostgresSchema.apply(c);
        }
        receipts = new PostgresTransactionRepository(db);
        if (financialsEnabled()) {
            var target = new coupons.repository.DisposablePostgresTarget(System.getenv("COUPON_POSTGRES_TEST_HOST"),
                    Integer.parseInt(System.getenv("COUPON_POSTGRES_TEST_PORT")),
                    System.getenv("COUPON_POSTGRES_TEST_MARKER"), System.getenv("COUPON_POSTGRES_TEST_USER"), schema);
            ReceiptFinancialProjection.install(db, target);
            receipts = PostgresTransactionRepository.withFinancialProjections(db, target);
        }
        plans = new PostgresShoppingPlanRepository(db);
        coupons = new PostgresClippedCouponRepository(db);
        client = HttpClient.newHttpClient();
        start();
    }

    void start() throws Exception {
        server = new CouponApiService(receipts, plans, coupons, "reader", "owner", new PostgresApiRequestStore(db), new PostgresPlatformRepository(db)).start(0);
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void cleanup() throws Exception {
        if (server != null) {
            server.stop(0);

        }if (client != null) {
            client.close();
        }
        if (admin != null && schema != null)try (Connection c = PostgresTestDatabase.targetForSchema(schema).open(admin)) {
            c.createStatement().execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }


    @Test void unavailableRewardsReduceBalancesWithoutCountingAsSpentAndRetainNativePoints()throws Exception{
        for(String provider:List.of("ECB","Fetch","Target Gift Card")){
            boolean points=provider.equals("Fetch");var accounts=new coupons.repository.PostgresProviderAccountRepository(db);
            accounts.saveOpening(new coupons.repository.AccountOpening(UUID.randomUUID(),LocalDate.of(2026,10,1),provider,points?BigDecimal.ZERO:null,BigDecimal.ZERO,true,"Recorded test value","test opening",Instant.now()));
            UUID source=UUID.randomUUID();accounts.saveAdjustment(new coupons.repository.AccountAdjustment(source,LocalDate.of(2026,10,5),provider,points?new BigDecimal("1000"):null,new BigDecimal("10.0000"),"Member reward: synthetic earning","test",Instant.now()));
            var body=new LinkedHashMap<String,Object>();body.putAll(Map.of("eventId",UUID.randomUUID(),"provider",provider,"date","2026-10-10","amount","3.0000","category","EXPIRED","reason","Expired without purchase","sourceId",source));if(points)body.put("points","300");
            var result=call("POST","/accounts/unavailable-rewards",Json.object(body),"loss-"+provider,null);assertEquals(201,result.statusCode(),result.body());
            assertEquals(result.body(),call("POST","/accounts/unavailable-rewards",Json.object(body),"loss-"+provider,null).body());
            var report=new PostgresPlatformRepository(db).account(provider,LocalDate.of(2026,10,1),LocalDate.of(2026,10,31));
            assertEquals(new BigDecimal("10.0000"),report.get("earned"));assertEquals(0,((BigDecimal)report.get("spent")).signum());assertEquals(new BigDecimal("3.0000"),report.get("unavailable"));
            var end=(coupons.rewards.ProviderBalanceSummary.Balance)report.get("ending");assertEquals(0,end.endUsd().compareTo(new BigDecimal("7")));
            if(points){assertEquals(0,end.endPoints().compareTo(new BigDecimal("700")));assertEquals(new BigDecimal("300"),report.get("unavailablePoints"));assertEquals(0,((BigDecimal)report.get("spentPoints")).signum());}
            assertEquals(new BigDecimal("10.0000"),accounts.findAdjustment(source).orElseThrow().usdChange());
            body.put("eventId",UUID.randomUUID());assertEquals(400,call("POST","/accounts/unavailable-rewards",Json.object(body),"loss-duplicate-"+provider,null).statusCode());
            var removal=accounts.findAdjustments(provider).stream().filter(a->a.unavailable()).findFirst().orElseThrow();assertEquals(source,removal.unavailableSource());assertEquals("Expired without purchase",removal.reason().split(" \\| ",3)[2]);
        }
    }

    @Test void unavailablePrintedRRCannotBeMatchedAndItsOriginalReceiptIsUnchanged()throws Exception{
        UUID origin=UUID.randomUUID(),earning=UUID.randomUUID(),spent=UUID.randomUUID(),use=UUID.randomUUID();
        var benefit=new RebateBenefit(BenefitKind.STORE_COUPON,new BigDecimal("5"),Currency.getInstance("USD"),null);
        var tx=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,BigDecimal.ZERO));
        tx.setObservations(new ReceiptObservations(LocalDate.of(2026,10,5),"10:00",List.of(new ReceiptObservations.Earning(earning,ReceiptObservations.EarningKind.REGISTER_REWARD,"Walgreens","Printed RR",ReceiptObservations.Scope.RECEIPT,List.of(),benefit,benefit,RebateStatus.PAID,null,null,null,List.of())),List.of(),null,List.of(),Map.of()));
        receipts.save(origin,tx,new ReceiptMeta("Walgreens","RR-ORIGIN",null,null),List.of(),new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptCreateCtx("test"));
        var purchase=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,BigDecimal.ZERO));purchase.setObservations(new ReceiptObservations(LocalDate.of(2026,10,11),"12:00",List.of(),List.of(new ReceiptObservations.UnidentifiedRR(use,new BigDecimal("5"),"RR used")),null,List.of(),Map.of()));
        receipts.save(spent,purchase,new ReceiptMeta("Walgreens","RR-SPEND",null,null),List.of(),new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptCreateCtx("test"));
        var platform=new PostgresPlatformRepository(db);assertTrue(Json.encode(platform.rrOptions(spent)).contains(earning.toString()));
        platform.recordUnavailableReward(UUID.randomUUID(),"Register Rewards",LocalDate.of(2026,10,10),new BigDecimal("5"),null,earning,"EXPIRED","Printed coupon expired","test");
        assertFalse(Json.encode(platform.rrOptions(spent)).contains(earning.toString()));
        assertThrows(IllegalArgumentException.class,()->platform.matchRR(spent,1,use,earning,true,"Cannot use an expired coupon","test",false));
        assertEquals(1,receipts.findById(origin).orElseThrow().revision());assertEquals(tx.getObservations(),receipts.findById(origin).orElseThrow().transaction().getObservations());
        var report=platform.account("Register Rewards",LocalDate.of(2026,10,1),LocalDate.of(2026,10,31));assertEquals(new BigDecimal("5"),report.get("unavailable"));
        assertEquals(new BigDecimal("5"),report.get("spent")); // Existing unmatched receipt deduction is retained, not manufactured by the loss.
    }

    @Test void standaloneUnavailableRRAndFailedAdjustmentKeepAuditableOriginals()throws Exception{
        var accounts=new coupons.repository.PostgresProviderAccountRepository(db);var platform=new PostgresPlatformRepository(db);
        accounts.saveOpening(new coupons.repository.AccountOpening(UUID.randomUUID(),LocalDate.of(2026,10,1),"Register Rewards",null,new BigDecimal("5"),true,"USD","Test RR opening",Instant.now()));
        UUID coupon=UUID.randomUUID(),event=UUID.randomUUID();platform.addStandaloneReward(new coupons.rewards.RegisterReward(coupon,"Existing paper RR",new BigDecimal("5"),LocalDate.of(2026,10,1),null,null));
        try(var c=db.open();var statement=c.createStatement()){statement.execute("CREATE FUNCTION reject_loss() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic rollback'; END $$");statement.execute("CREATE TRIGGER reject_loss BEFORE INSERT ON provider_account_adjustments FOR EACH ROW EXECUTE FUNCTION reject_loss()");}
        assertThrows(IllegalStateException.class,()->platform.recordUnavailableReward(event,"Register Rewards",LocalDate.of(2026,10,10),new BigDecimal("5"),null,coupon,"LOST","Paper coupon lost","test"));
        assertTrue(accounts.findAdjustments("Register Rewards").isEmpty());assertEquals("UNUSED",platform.standaloneRewards().getFirst().get("status"));
        try(var c=db.open();var statement=c.createStatement()){statement.execute("DROP TRIGGER reject_loss ON provider_account_adjustments");statement.execute("DROP FUNCTION reject_loss()");}
        platform.recordUnavailableReward(event,"Register Rewards",LocalDate.of(2026,10,10),new BigDecimal("5"),null,coupon,"LOST","Paper coupon lost","test");
        assertEquals("UNAVAILABLE",platform.standaloneRewards().getFirst().get("status"));assertEquals(1,accounts.findAdjustments("Register Rewards").size());
        assertThrows(java.util.ConcurrentModificationException.class,()->platform.recordUnavailableReward(event,"Register Rewards",LocalDate.of(2026,10,10),new BigDecimal("4"),null,coupon,"LOST","Different amount","test"));
    }

    @Test void unavailableRewardValidationDoesNotWriteInvalidOrUnauthorizedDeductions()throws Exception{
        var body=new LinkedHashMap<String,Object>();body.putAll(Map.of("eventId",UUID.randomUUID(),"provider","Fetch","date","2026-10-10","amount","1","category","LOST","reason","Lost points"));
        assertEquals(400,call("POST","/accounts/unavailable-rewards",Json.object(body),"loss-missing-points",null).statusCode());
        body.put("points","-1");assertEquals(400,call("POST","/accounts/unavailable-rewards",Json.object(body),"loss-negative",null).statusCode());
        body.put("points","100");body.put("category","SPENT");assertEquals(400,call("POST","/accounts/unavailable-rewards",Json.object(body),"loss-invalid-category",null).statusCode());
        var request=HttpRequest.newBuilder(URI.create(base+"/accounts/unavailable-rewards")).header("Authorization","Bearer reader").header("Content-Type","application/json").header("Idempotency-Key","read-only-loss").POST(HttpRequest.BodyPublishers.ofString(Json.object(body))).build();assertEquals(403,client.send(request,HttpResponse.BodyHandlers.ofString()).statusCode());
        assertTrue(new coupons.repository.PostgresProviderAccountRepository(db).findAdjustments("Fetch").isEmpty());
    }

    @Test void rebateReceiptLinksPersistNetAmountsAndIndividualSubmissionWithoutChangingReceipts()throws Exception {
        UUID id=UUID.randomUUID(),line=UUID.randomUUID(),program=UUID.randomUUID();
        var tx=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,new BigDecimal("15.00")));
        tx.addPurchaseItem(new PurchaseItem(line,"P&G eligible item",null,new BigDecimal("20.00"),new BigDecimal("20.00"),1));
        tx.addAppliedCoupon(new CouponApplied(UUID.randomUUID(),"Product coupon",new BigDecimal("5.00"),List.of(line),null,false));
        tx.setObservations(new ReceiptObservations(LocalDate.of(2026,10,25),"12:00",List.of(),List.of(),null,List.of(),Map.of()));
        receipts.save(id,tx,new ReceiptMeta("Walgreens","REBATE-TEST",null,null),List.of(),new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptCreateCtx("test"));
        var supplement=new coupons.repository.PostgresReceiptSupplementRepository(db);
        supplement.saveGiftCardPromotion(new coupons.repository.GiftCardPromotion(program,"P&G rebate",new BigDecimal("75"),new BigDecimal("15"),LocalDate.of(2026,10,1),LocalDate.of(2026,10,31),GiftCardPromotion.Basis.SALE_BEFORE_COUPONS,"Selected products after coupons",GiftCardPromotion.Status.TRACKING,Map.of()));
        UUID other=UUID.randomUUID(),otherLine=UUID.randomUUID();
        var otherTx=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,BigDecimal.TEN));
        otherTx.addPurchaseItem(new PurchaseItem(otherLine,"Another qualifying item",null,BigDecimal.TEN,BigDecimal.TEN,1));
        otherTx.setObservations(new ReceiptObservations(LocalDate.of(2026,10,25),null,List.of(),List.of(),null,List.of(),Map.of()));
        receipts.save(other,otherTx,new ReceiptMeta("Walgreens","OTHER-REBATE-TEST",null,null),List.of(),new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptCreateCtx("test"));
        supplement.saveRebateReceipt(program,other,1,List.of(otherLine),BigDecimal.TEN,null,false,null,null,"new");
        byte[] snapshot;try(var c=db.open();var p=c.prepareStatement("SELECT contents FROM receipt_current WHERE transaction_id=?")){p.setObject(1,id);try(var r=p.executeQuery()){r.next();snapshot=r.getBytes(1);}}
        var body=new LinkedHashMap<String,Object>();body.putAll(Map.of("lineIds",List.of(line),"netAmount","15.0000","note","Coupon deducted once","submitted",true,"submittedOn","2026-10-26","submissionReference","PG-123","expectedContribution","new"));
        String path="/rebate-programs/"+program+"/receipts/"+id;
        var saved=call("PUT",path,Json.object(body),"rebate-link","1");assertEquals(200,saved.statusCode(),saved.body());
        assertEquals(saved.body(),call("PUT",path,Json.object(body),"rebate-link","1").body());
        var linked=new coupons.repository.PostgresReceiptSupplementRepository(db).giftCardPromotions().getFirst();assertEquals(2,linked.contributions().size());assertFalse(linked.contributions().get(other).submitted());
        var contribution=linked.contributions().get(id);assertEquals(new BigDecimal("15.0000"),contribution.reviewedAmount());assertTrue(contribution.afterCoupons());assertTrue(contribution.submitted());assertEquals("PG-123",contribution.submissionReference());
        assertEquals(409,call("PUT",path,Json.object(body),"stale-rebate-link","1").statusCode());
        body.put("expectedContribution",GiftCardPromotion.contributionToken(contribution));body.put("netAmount","14.00125");body.put("submitted",false);body.put("submittedOn",null);body.put("submissionReference",null);
        var updated=call("PUT",path,Json.object(body),"rebate-adjust","1");assertEquals(200,updated.statusCode(),updated.body());
        var latest=supplement.giftCardPromotions().getFirst().contributions().get(id);assertEquals(new BigDecimal("14.00125"),latest.reviewedAmount());assertFalse(latest.submitted());
        body.put("expectedContribution",GiftCardPromotion.contributionToken(latest));body.put("lineIds",List.of(UUID.randomUUID()));
        assertEquals(400,call("PUT",path,Json.object(body),"invalid-rebate-lines","1").statusCode());assertEquals(latest,supplement.giftCardPromotions().getFirst().contributions().get(id));
        assertEquals(409,call("PUT",path,Json.object(body),"stale-rebate-receipt","2").statusCode());
        body.put("lineIds",List.of(line));body.put("netAmount","21");
        assertEquals(400,call("PUT",path,Json.object(body),"rebate-too-large","1").statusCode());
        body.put("netAmount","14.00125");body.put("submitted",true);body.put("submittedOn","2026-10-24");
        assertEquals(400,call("PUT",path,Json.object(body),"rebate-before-purchase","1").statusCode());
        body.put("submitted",false);body.put("submittedOn",null);
        var reader=HttpRequest.newBuilder(URI.create(base+path)).header("Authorization","Bearer reader").header("Content-Type","application/json").header("Idempotency-Key","reader-rebate").header("If-Match-Revision","1").PUT(HttpRequest.BodyPublishers.ofString(Json.object(body))).build();
        assertEquals(403,client.send(reader,HttpResponse.BodyHandlers.ofString()).statusCode());
        try(var c=db.open();var statement=c.createStatement()){statement.execute("CREATE FUNCTION reject_rebate_update() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic failure'; END $$");statement.execute("CREATE TRIGGER reject_rebate_update BEFORE UPDATE ON gift_card_promotions FOR EACH ROW EXECUTE FUNCTION reject_rebate_update()");}
        assertThrows(IllegalStateException.class,()->supplement.unlinkRebateReceipt(program,id,GiftCardPromotion.contributionToken(latest)));
        assertThrows(IllegalStateException.class,()->supplement.saveRebateReceipt(program,id,1,List.of(line),BigDecimal.ONE,null,false,null,null,GiftCardPromotion.contributionToken(latest)));
        try(var c=db.open();var statement=c.createStatement()){statement.execute("DROP TRIGGER reject_rebate_update ON gift_card_promotions");statement.execute("DROP FUNCTION reject_rebate_update()");}
        assertEquals(latest,supplement.giftCardPromotions().getFirst().contributions().get(id));
        var workspaceResponse=call("GET","/workspace",null,null,null);assertEquals(200,workspaceResponse.statusCode());assertTrue(workspaceResponse.body().contains("14.00125"));assertTrue(workspaceResponse.body().contains("afterCoupons"));
        assertEquals(409,call("DELETE",path,Json.object(Map.of("expectedContribution","stale")),"unlink-stale",null).statusCode());
        String unlinkBody=Json.object(Map.of("expectedContribution",GiftCardPromotion.contributionToken(latest)));
        var unlinked=call("DELETE",path,unlinkBody,"unlink-rebate",null);assertEquals(200,unlinked.statusCode(),unlinked.body());
        assertEquals(unlinked.body(),call("DELETE",path,unlinkBody,"unlink-rebate",null).body());
        var remaining=supplement.giftCardPromotions().getFirst().contributions();assertFalse(remaining.containsKey(id));assertTrue(remaining.containsKey(other));
        body.put("expectedContribution","new");var relinked=call("PUT",path,Json.object(body),"relink-rebate","1");assertEquals(200,relinked.statusCode(),relinked.body());
        try(var c=db.open();var p=c.prepareStatement("SELECT contents FROM receipt_current WHERE transaction_id=?")){p.setObject(1,id);try(var r=p.executeQuery()){r.next();assertArrayEquals(snapshot,r.getBytes(1));}}
        assertEquals(1,receipts.findById(id).orElseThrow().revision());assertEquals(1,receipts.findHistoryByReceiptId(id).size());
        assertTrue(new ProviderBalanceSummary().movements(List.of(receipts.findById(id).orElseThrow())).isEmpty());
        var corrected=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,new BigDecimal("40")));
        corrected.addPurchaseItem(new PurchaseItem(line,"Corrected product",null,new BigDecimal("40"),new BigDecimal("40"),1));
        corrected.setObservations(tx.getObservations());
        receipts.update(id,1,corrected,new ReceiptMeta("Walgreens","REBATE-TEST",null,null),List.of(),new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptChangeContext("test","synthetic correction","test",Instant.now()));
        var progress=(java.util.List<?>)new PostgresPlatformRepository(db).workspace(LocalDate.of(2026,10,31)).get("promotions");
        var projected=(Map<?,?>)progress.getFirst();var links=(Map<?,?>)projected.get("contributions");var original=(Map<?,?>)links.get(id);
        assertEquals(false,original.get("needsReview"));assertEquals(new BigDecimal("20.00"),original.get("saleTotal"));
        assertEquals(new BigDecimal("24.00125"),projected.get("eligibleTotal"));assertEquals(true,projected.get("progressComplete"));
        var retained=supplement.giftCardPromotions().getFirst().contributions().get(id);
        supplement.saveRebateReceipt(program,id,1,List.of(line),new BigDecimal("13.00125"),null,true,LocalDate.of(2026,10,26),"same submitted receipt",GiftCardPromotion.contributionToken(retained));
        assertEquals(1,supplement.giftCardPromotions().getFirst().contributions().get(id).reviewedRevision());assertEquals(2,receipts.findById(id).orElseThrow().revision());
    }

    @Test void notebookSourcesSaleRulesAndTripOrganizationSurviveRepositoryRestart() throws Exception {
        UUID line=UUID.randomUUID(),id=UUID.randomUUID();
        var item=new LinkedHashMap<String,Object>();item.putAll(Map.of("id",line,"name","Synthetic Colgate","sale","6.0000","quantity",2,"priceRule",Map.of("type","BOGO_FREE")));item.put("regular",null);
        var scenario=new LinkedHashMap<String,Object>();scenario.putAll(Map.of("items",List.of(item),"coupons",List.of(),"promotions",List.of(),"tax","0","walgreensCashUsed","0","registerRewardsUsed","0","giftCardUsed","0","paymentMethod","CASH","fundingNotes",""));
        scenario.put("source",Map.of("text","Original synthetic note","mediaType","image/png","contentBase64","AQID","earlier",List.of(Map.of("text","Earlier note"))));
        var organization=Map.of("stage","TRIP","checkoutOrder",0,"rewardSources",List.of());
        var created=call("POST","/shopping-plans",Json.object(Map.of("planId",id,"name","Notebook persistence","scenario",scenario,"organization",organization)),"notebook-persistence",null);
        assertEquals(201,created.statusCode(),created.body());
        var loaded=new PostgresShoppingPlanRepository(db).findById(id).orElseThrow();
        assertEquals("TRIP",loaded.organization().stage());assertEquals("Original synthetic note",loaded.scenario().source().text());
        assertEquals("Earlier note",loaded.scenario().source().earlier().getFirst().text());
        assertEquals("BOGO_FREE",loaded.scenario().items().getFirst().priceRule().type());
        assertNull(loaded.scenario().items().getFirst().regular());assertEquals(new BigDecimal("6.0000"),loaded.scenario().estimate().get("calculatedOop"));
        var metadata=call("PUT","/shopping-plans/"+id,Json.object(Map.of("name","Renamed notebook")),"notebook-rename","1");
        assertEquals(200,metadata.statusCode(),metadata.body());assertEquals(loaded.organization(),plans.findById(id).orElseThrow().organization());
        assertTrue(receipts.findById(id).isEmpty());
    }

    @Test void promotionManagementRetainsReceiptLinksAndRejectsStaleEdits() throws Exception {
        UUID id=UUID.randomUUID();String path="/rebate-programs/"+id;
        var body=new LinkedHashMap<String,Object>(Map.of("name","New promotion","threshold","75.00","giftCardValue","15.00","start","2026-10-01","end","2026-10-31","terms","Eligible products after coupons","status","TRACKING","expectedToken","new"));
        var created=call("PUT",path,Json.object(body),"create-promotion",null);assertEquals(200,created.statusCode(),created.body());
        assertEquals(created.body(),call("PUT",path,Json.object(body),"create-promotion",null).body());
        var supplement=new coupons.repository.PostgresReceiptSupplementRepository(db);
        UUID receiptId=UUID.randomUUID(),lineId=UUID.randomUUID();
        var tx=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,BigDecimal.TEN));
        tx.addPurchaseItem(new PurchaseItem(lineId,"Qualifying product",null,BigDecimal.TEN,BigDecimal.TEN,1));
        tx.setObservations(new ReceiptObservations(LocalDate.of(2026,10,25),null,List.of(),List.of(),null,List.of(),Map.of()));
        receipts.save(receiptId,tx,new ReceiptMeta("Walgreens","PROMO-TEST",null,null),List.of(),new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptCreateCtx("test"));
        supplement.saveRebateReceipt(id,receiptId,1,List.of(lineId),BigDecimal.TEN,null,false,null,null,"new");
        var original=supplement.giftCardPromotions().getFirst();
        assertEquals(1,original.contributions().size());
        body.put("name","Changed promotion");body.put("expectedToken",original.token());
        var changed=call("PUT",path,Json.object(body),"edit-promotion",null);assertEquals(200,changed.statusCode(),changed.body());
        var updated=supplement.giftCardPromotions().getFirst();assertEquals(original.contributions(),updated.contributions());assertEquals("Changed promotion",updated.name());
        assertEquals(409,call("PUT",path,Json.object(body),"stale-promotion",null).statusCode());
        assertEquals(409,call("DELETE",path,Json.object(Map.of("expectedToken",original.token())),"stale-delete-promotion",null).statusCode());
        var removed=call("DELETE",path,Json.object(Map.of("expectedToken",updated.token())),"delete-promotion",null);assertEquals(200,removed.statusCode(),removed.body());
        assertTrue(supplement.giftCardPromotions().isEmpty());
    }

    HttpResponse<String> call(String method, String path, String body, String key, String revision) throws Exception {
        var b = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer owner").header("Content-Type", "application/json");
        if (key != null) {
            b.header("Idempotency-Key", key);

        }if (revision != null) {
            b.header("If-Match-Revision", revision);
        }
        return client.send(b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    UUID receipt() {
        UUID id = UUID.randomUUID();
        var tx = new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO, BigDecimal.ONE));
        tx.addPurchaseItem(new PurchaseItem("Soap", null, BigDecimal.ONE, BigDecimal.ONE, 1));
        receipts.save(id, tx, new ReceiptMeta("Test store", "001", null, null), List.of(), new ReceiptWriteRequest(UUID.randomUUID(), Instant.now()), new ReceiptCreateCtx("test"));
        return id;
    }

    UUID coupon() {
        UUID id = UUID.randomUUID();
        coupons.save(new ClippedCoupon(id, "Test provider", null, new Coupon("Test", BigDecimal.ONE, 1, CouponType.MANUFACTURER, LocalDate.of(2026, 12, 31)), CouponStatus.CLIPPED, LocalDate.of(2026, 10, 1), 1), "test");
        return id;
    }

    @Test
    void newOperationalWritesRequireOwnerAuthorization() throws Exception {
        for (String path : List.of("/deals", "/clipped-coupons", "/receipts/" + UUID.randomUUID() + "/earnings")) {
            var request = HttpRequest.newBuilder(URI.create(base + path)).header("Authorization", "Bearer reader").header("Content-Type", "application/json").header("Idempotency-Key", UUID.randomUUID().toString()).POST(HttpRequest.BodyPublishers.ofString("{}")).build();
            assertEquals(403, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
        }
    }

    @Test
    void postSaveRebatesRetainIssuerUnitsDestinationsScopeAndRegisterTotals() throws Exception {
        UUID receipt = receipt();
        var original = receipts.findById(receipt).orElseThrow();
        UUID line = original.transaction().getPurchaseItems().getFirst().getLineItemId();
        long revision = 1;
        for (String provider : List.of("Fetch", "P&G Good Everyday", "Ibotta", "Custom provider")) {
            UUID earning = UUID.randomUUID();
            boolean points = provider.equals("Fetch") || provider.startsWith("P&G");
            var body = new LinkedHashMap<String, Object>();
            var e = new LinkedHashMap<String, Object>();
            e.put("earningId", earning);
            e.put("kind", "THIRD_PARTY_REBATE");
            e.put("provider", provider);
            e.put("offer", provider + " distinct offer");
            e.put("form", points ? "POINTS" : "CASH");
            e.put("amount", points ? "25.0000" : "2.50000000000000000001");
            e.put("status", "EXPECTED");
            e.put("lineItemIds", List.of(line));
            e.put("qualifyingQuantities", Map.of(line.toString(), 1));
            e.put("payoutDestination", provider.equals("Ibotta") ? "VENMO" : "PAYPAL");
            e.put("submissionReference", "reference-" + provider);
            body.put("earning", e);
            if (provider.equals("Ibotta")) {
                var evidenceId = UUID.randomUUID();
                e.put("evidenceIds", List.of(evidenceId));
                body.put("evidence", List.of(Map.of("evidenceId", evidenceId, "mediaType", "image/png", "contentBase64", "AQID")));
            }
            var preview = call("POST", "/receipts/" + receipt + "/earnings/preview", Json.encode(body), null, "" + revision);
            assertEquals(200, preview.statusCode(), preview.body());
            assertEquals(revision, receipts.findById(receipt).orElseThrow().revision());
            var result = call("POST", "/receipts/" + receipt + "/earnings", Json.encode(body), "rebate-" + provider, "" + revision);
            assertEquals(201, result.statusCode(), result.body());
            assertEquals(result.body(), call("POST", "/receipts/" + receipt + "/earnings", Json.encode(body), "rebate-" + provider, "" + revision).body());
            assertEquals(409, call("POST", "/receipts/" + receipt + "/earnings", Json.encode(body), "stale-" + provider, "" + revision).statusCode());
            revision++;
            e.put("earningId", UUID.randomUUID());
            assertEquals(400, call("POST", "/receipts/" + receipt + "/earnings", Json.encode(body), "duplicate-" + provider, "" + revision).statusCode());
            assertEquals(revision, receipts.findById(receipt).orElseThrow().revision());
        }
        var saved = receipts.findById(receipt).orElseThrow();
        assertEquals(4, saved.transaction().getObservations().earnings().size());
        assertEquals(1, saved.evidenceIds().size());
        assertArrayEquals(new byte[]{1, 2, 3}, receipts.findEvidenceById(saved.evidenceIds().getFirst()).orElseThrow().getContent());
        assertEquals(5, receipts.findHistoryByReceiptId(receipt).size());
        assertEquals(original.transaction().getReceiptTotals().getFinalTotal(), saved.transaction().getReceiptTotals().getFinalTotal());
        assertEquals(BigDecimal.ZERO, SavingsReport.calculate(List.of(saved)).newlyEarned());
        var p = saved.transaction().getObservations().earnings().stream().filter(x -> x.provider().equals("P&G Good Everyday")).findFirst().orElseThrow();
        assertEquals(new BigDecimal("25.0000"), p.expected().amount());
        assertEquals("PAYPAL", p.details().payoutDestination());
        assertNotNull(p.valuation());
        assertEquals(
                0,
                new BigDecimal("25").divide(
                        new BigDecimal("150"),
                        MathContext.DECIMAL128
                ).compareTo(p.valuation().amount())
        );
        assertEquals("USD", p.valuation().currency().getCurrencyCode());
        var associations = Json.encode(Map.of("lineItemIds", List.of(), "qualifyingQuantities", Map.of()));
        assertEquals(200, call("PUT", "/receipts/" + receipt + "/earnings/" + p.earningId() + "/associations", associations, "scope", "" + revision).statusCode());
        var updated = receipts.findById(receipt).orElseThrow().transaction().getObservations().earnings().stream().filter(x -> x.earningId().equals(p.earningId())).findFirst().orElseThrow();
        assertEquals(ReceiptObservations.Scope.RECEIPT, updated.scope());
        assertEquals(p.expected(), updated.expected());
        assertEquals(p.details().payoutDestination(), updated.details().payoutDestination());
    }

    @Test
    void rewardAssociationsRejectExcessQuantitiesAndForeignEvidenceAtomically() throws Exception {
        UUID receipt = receipt();
        UUID line = receipts.findById(receipt).orElseThrow().transaction().getPurchaseItems().getFirst().getLineItemId();
        var e = new LinkedHashMap<String, Object>();
        e.put("earningId", UUID.randomUUID());
        e.put("kind", "WALGREENS_CASH");
        e.put("form", "STORE_REWARD");
        e.put("amount", "5");
        e.put("status", "CREDITED");
        e.put("confirmedAmount", "5");
        e.put("unit", "USD");
        e.put("confirmAmountAndUnit", true);
        e.put("lineItemIds", List.of(line));
        e.put("qualifyingQuantities", Map.of(line.toString(), 2));
        assertEquals(400, call("POST", "/receipts/" + receipt + "/earnings", Json.encode(Map.of("earning", e)), "overquantity", "1").statusCode());
        assertEquals(1, receipts.findHistoryByReceiptId(receipt).size());
        e.put("qualifyingQuantities", Map.of(line.toString(), 1));
        e.put("evidenceIds", List.of(UUID.randomUUID()));
        assertEquals(400, call("POST", "/receipts/" + receipt + "/earnings", Json.encode(Map.of("earning", e)), "foreignEvidence", "1").statusCode());
        assertEquals(1, receipts.findById(receipt).orElseThrow().revision());
    }

    @Test
    void paperCouponsHaveIndependentCopiesWithoutAccountAndAtomicRollback() throws Exception {
        var id = UUID.randomUUID();
        var body = new LinkedHashMap<String, Object>();
        body.put("couponId", id);
        body.put("provider", "Manufacturer");
        body.put("description", "Two dollars off soap");
        body.put("amount", "2.0000");
        body.put("requiredQuantity", 1);
        body.put("couponType", "MANUFACTURER");
        body.put("source", "PAPER_MANUFACTURER");
        body.put("discount", "DOLLARS");
        body.put("copies", 3);
        body.put("usageLimit", 2);
        body.put("status", "CLIPPED");
        body.put("clippedOn", "2026-10-01");
        body.put("expirationDate", "2026-10-31");
        var created = call("POST", "/clipped-coupons", Json.encode(body), "paper", null);
        assertEquals(201, created.statusCode(), created.body());
        assertEquals(3, coupons.findByProvider("Manufacturer").size());
        assertNull(coupons.findById(id).orElseThrow().coupon().getTerms().accountRestriction());
        assertEquals(created.body(), call("POST", "/clipped-coupons", Json.encode(body), "paper", null).body());
        assertEquals(3, coupons.findByProvider("Manufacturer").size());
        var receipt = receipt();
        assertEquals(200, call("POST", "/clipped-coupons/" + id + "/redeem", Json.encode(Map.of("transactionId", receipt, "redeemedOn", "2026-10-05")), "paperUsed", "1").statusCode());
        var second = coupons.findByProvider("Manufacturer").stream().filter(c -> !c.couponId().equals(id)).findFirst().orElseThrow();
        assertEquals(CouponStatus.CLIPPED, second.status());
        assertEquals(400, call("POST", "/clipped-coupons/" + second.couponId() + "/redeem", Json.encode(Map.of("transactionId", receipt, "redeemedOn", "2026-11-05")), "expired", "1").statusCode());
        assertEquals(1, coupons.findById(second.couponId()).orElseThrow().revision());
    }

    @Test
    void structuredDealAndPlanPricingPersistWithoutReceiptWrites() throws Exception {
        var plan = UUID.randomUUID();
        assertEquals(201, call("POST", "/shopping-plans", Json.encode(Map.of("planId", plan, "name", "Shop", "retailer", "Store", "shoppingDate", "2026-10-19")), "plan9", null).statusCode());
        var body = new LinkedHashMap<String, Object>();
        body.put("dealId", UUID.randomUUID());
        body.put("title", "Two soaps");
        body.put("retailer", "Store");
        body.put("interpretedTerms", "Buy 2 for $5");
        body.put("pricing", Map.of("type", "MULTIBUY", "productDescription", "Soap", "minimumQuantity", 2, "promotionalPrice", "5.00", "repeatable", true));
        var result = call("POST", "/deals", Json.encode(body), "deal9", null);
        assertEquals(201, result.statusCode(), result.body());
        assertEquals(result.body(), call("POST", "/deals", Json.encode(body), "deal9", null).body());
        var d = new PostgresDealRepository(db).findById((UUID) body.get("dealId")).orElseThrow();
        assertEquals(DealTerms.Type.MULTIBUY, d.pricing().type());
        assertEquals(d, new PostgresDealRepository(db).findByRevision(d.dealId(), 1).orElseThrow());
        body.put("dealId", UUID.randomUUID());
        assertEquals(400, call("POST", "/deals", Json.encode(body), "duplicate9", null).statusCode());
        var purchase = Map.of("plannedPurchaseId", UUID.randomUUID(), "productName", "Soap", "quantity", 3, "regularUnitPrice", "3.00", "priceSource", "DEAL", "advertisedDealId", d.dealId());
        assertEquals(201, call("POST", "/shopping-plans/" + plan + "/purchases", Json.encode(purchase), "item9", null).statusCode());
        var workspace = call("GET", "/workspace", null, null, null);
        assertEquals(200, workspace.statusCode(), workspace.body());
        assertTrue(workspace.body().contains("\"registerCost\":8.00"), workspace.body());
    }

    @Test
    void postgresHttpPlansPersistAndRetryAcrossServerRestart() throws Exception {
        UUID id = UUID.randomUUID();
        String body = Json.object(Map.of("planId", id, "name", "Weekly, food"));
        var created = call("POST", "/shopping-plans", body, "create", null);
        assertEquals(201, created.statusCode(), created.body());
        server.stop(0);
        start();
        assertEquals(created.body(), call("POST", "/shopping-plans", body, "create", null).body());
        assertEquals(200, call("GET", "/shopping-plans/" + id, null, null, null).statusCode());
        assertEquals(200, call("PUT", "/shopping-plans/" + id, "{\"name\":\"Updated\"}", "update", "1").statusCode());
        assertEquals(409, call("PUT", "/shopping-plans/" + id, "{\"name\":\"stale\"}", "stale", "1").statusCode());
        assertEquals("Updated", plans.findById(id).orElseThrow().name());
        assertEquals("Weekly, food", plans.findByRevision(id, 1).orElseThrow().name());
    }

    @Test
    void multipleCouponsCanRedeemOnOneReceiptAndRetryWithoutAnotherRedemption() throws Exception {
        UUID receipt = receipt(), a = coupon(), b = coupon();
        String body = Json.object(Map.of("transactionId", receipt, "redeemedOn", "2026-10-05"));
        var first = call("POST", "/clipped-coupons/" + a + "/redeem", body, "a", "1");
        assertEquals(200, first.statusCode(), first.body());
        assertEquals(200, call("POST", "/clipped-coupons/" + b + "/redeem", body, "b", "1").statusCode());
        server.stop(0);
        start();
        assertEquals(first.body(), call("POST", "/clipped-coupons/" + a + "/redeem", body, "a", "1").body());
        assertEquals(409, call("POST", "/clipped-coupons/" + a + "/redeem", body, "again", "2").statusCode());
        assertEquals(2, coupons.findById(a).orElseThrow().revision());
        var found = call("GET", "/receipts/" + receipt, null, null, null);
        assertEquals(200, found.statusCode(), found.body());
        assertTrue(found.body().contains("Soap"));
        assertEquals(200, call("GET", "/clipped-coupons?provider=Test%20provider", null, null, null).statusCode());
    }

    @Test
    void journalCoordinatesInstancesAndKeepsUncertainOutcomesReserved() throws Exception {
        var a = new PostgresApiRequestStore(db);
        var b = new PostgresApiRequestStore(db);
        var writes = new java.util.concurrent.atomic.AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var x = pool.submit(() -> {
                start.await();
                return a.execute("owner", "race", "same", () -> {
                    writes.incrementAndGet();
                    return new ApiRequestStore.Result(201, "{}");
                });
            });
            var y = pool.submit(() -> {
                start.await();
                return b.execute("owner", "race", "same", () -> {
                    writes.incrementAndGet();
                    return new ApiRequestStore.Result(201, "{}");
                });
            });
            start.countDown();
            assertEquals(x.get(10, TimeUnit.SECONDS), y.get(10, TimeUnit.SECONDS));
            assertEquals(1, writes.get());
        }
        assertThrows(ApiRequestStore.Conflict.class, () -> b.execute("owner", "race", "changed", () -> null));
        assertThrows(IllegalStateException.class, () -> a.execute("owner", "interrupted", "same", () -> {
            throw new IllegalStateException("simulated interruption");
        }));
        assertThrows(ApiRequestStore.Conflict.class, () -> b.execute("owner", "interrupted", "same", () -> {
            fail("Uncertain write must not execute again");
            return null;
        }));
    }

    @Test
    void workspaceAndPlannedItemsUseExistingJavaModelsAndExactEstimates() throws Exception {
        UUID plan = UUID.randomUUID(), item = UUID.randomUUID();
        assertEquals(201, call("POST", "/shopping-plans", Json.object(Map.of("planId", plan, "name", "Weekly")), "create-plan", null).statusCode());
        String body = Json.object(Map.of("plannedPurchaseId", item, "productName", "Soap", "quantity", 3, "targetUnitPrice", "0.10000000000000000001", "clippedCouponIds", List.of()));
        assertEquals(201, call("POST", "/shopping-plans/" + plan + "/purchases", body, "add-item", null).statusCode());
        var workspace = call("GET", "/workspace", null, null, null);
        assertEquals(200, workspace.statusCode(), workspace.body());
        assertTrue(workspace.body().contains("0.30000000000000000003"));
        assertTrue(workspace.body().contains("\"canWrite\":true"));
        assertEquals(200, call("PUT", "/shopping-plans/" + plan + "/purchases/" + item, body, "update-item", "1").statusCode());
        assertEquals(409, call("PUT", "/shopping-plans/" + plan + "/purchases/" + item, body, "stale-item", "1").statusCode());
        assertEquals(2, new PostgresPlannedPurchaseRepository(db).findById(item).orElseThrow().revision());
        var reader = HttpRequest.newBuilder(URI.create(base + "/workspace")).header("Authorization", "Bearer reader").GET().build();
        var read = client.send(reader, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, read.statusCode());
        assertTrue(read.body().contains("\"canWrite\":false"));
    }

    @Test
    void inventoryRemainsReadOnlyWithoutAnAuthenticatedApprovalWorkflow() throws Exception {
        UUID id = UUID.randomUUID();
        var repo = new PostgresInventoryRepository(db);
        repo.save(new InventoryBalance(id, UUID.randomUUID(), 5, 1), "seed-owner");
        assertEquals(400, call("PUT", "/inventory/" + id, "{\"quantity\":0,\"approvedBy\":\"owner\"}", "unsafe-approval", "1").statusCode());
        assertEquals(5, repo.findById(id).orElseThrow().quantityOnHand());
        try (var c = db.open(); var rows = c.createStatement().executeQuery("SELECT count(*) FROM inventory_history")) {
            rows.next();
            assertEquals(1, rows.getInt(1));
        }
    }

    @Test
    void receiptHistoryTotalsEvidenceAndCouponsAreStructuredAndOwnerScoped() throws Exception {
        UUID id = UUID.randomUUID(), evidenceId = UUID.randomUUID();
        var tx = new CouponTransaction(new ReceiptTotals(new BigDecimal("0.27"), new BigDecimal("4.28")));
        var item = new PurchaseItem("Soap", null, new BigDecimal("5.00"), new BigDecimal("5.00"), 1);
        tx.addPurchaseItem(item);
        tx.addAppliedCoupon(new CouponApplied("Digital coupon", BigDecimal.ONE, List.of(item.getLineItemId())));
        receipts.save(id, tx, null, List.of(new ReceiptEvidence(evidenceId, id, "image/png", new byte[]{1, 2, 3})), new ReceiptWriteRequest(UUID.randomUUID(), Instant.now()), new ReceiptCreateCtx("owner"));
        var detail = call("GET", "/receipts/" + id, null, null, null);
        assertTrue(detail.body().contains("\"printedLabel\":\"Digital coupon\""));
        assertEquals(200, call("GET", "/receipts/" + id + "/history", null, null, null).statusCode());
        var totals = call("GET", "/receipts/" + id + "/totals", null, null, null);
        assertEquals(200, totals.statusCode());
        assertTrue(totals.body().contains("0.01"));
        var artifact = call("GET", "/receipts/" + id + "/evidence/" + evidenceId, null, null, null);
        assertEquals(200, artifact.statusCode());
        assertEquals("no-store", artifact.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals(404, call("GET", "/receipts/" + UUID.randomUUID() + "/evidence/" + evidenceId, null, null, null).statusCode());
        receipts.softDelete(id, 1, new ReceiptWriteRequest(UUID.randomUUID(), Instant.now()), new ReceiptChangeContext("owner", "delete", "owner", Instant.now()));
        assertEquals(200, call("GET", "/receipts/" + id, null, null, null).statusCode());
    }

    @Test
    void rewardStatusUpdatesPreserveHistoryAndRetryWithoutDuplicateFinancialActivity() throws Exception {
        UUID id = UUID.randomUUID(), earningId = UUID.randomUUID();
        var tx = new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO, BigDecimal.ONE));
        tx.addPurchaseItem(new PurchaseItem("Test soap", null, BigDecimal.ONE, BigDecimal.ONE, 1));
        var expected = new RebateBenefit(BenefitKind.POINTS, new BigDecimal("25.000"), null, "Fetch");
        var earning = new ReceiptObservations.Earning(earningId, ReceiptObservations.EarningKind.THIRD_PARTY_REBATE, "Fetch", null, ReceiptObservations.Scope.RECEIPT, List.of(), expected, null, RebateStatus.EXPECTED, null, null, null, List.of(), "Original note", null, new ReceiptObservations.RecordedValuation(new BigDecimal("0.025000"), Currency.getInstance("USD"), "Original valuation"));
        tx.setObservations(new ReceiptObservations(LocalDate.of(2026, 10, 19), "9:00 AM", List.of(earning), List.of(), null, List.of(), Map.of()));
        receipts.save(id, tx, new ReceiptMeta("Test", "status-test", null, null), List.of(), new ReceiptWriteRequest(UUID.randomUUID(), Instant.now()), new ReceiptCreateCtx("test"));
        String path = "/receipts/" + id + "/earnings/" + earningId + "/status";
        assertEquals(400, call("PUT", path, "{\"status\":\"CREDITED\"}", "missing-confirmation", "1").statusCode());
        assertEquals(400, call("PUT", path, "{\"status\":\"APPROVED\"}", "legacy-assignment", "1").statusCode());
        String body = "{\"status\":\"CREDITED\",\"amount\":\"25\",\"unit\":\"points\",\"confirmAmountAndUnit\":true}";
        var saved = call("PUT", path, body, "confirm-credit", "1");
        assertEquals(200, saved.statusCode(), saved.body());
        assertEquals(409, call("PUT", path, body, "stale-credit", "1").statusCode());
        server.stop(0);
        start();
        assertEquals(saved.body(), call("PUT", path, body, "confirm-credit", "1").body());
        var after = receipts.findById(id).orElseThrow();
        var updated = after.transaction().getObservations().earnings().getFirst();
        assertEquals(2, after.revision());
        assertEquals(earningId, updated.earningId());
        assertEquals(expected, updated.confirmed());
        assertEquals(earning.valuation(), updated.valuation());
        assertEquals(expected, updated.expected());
        assertEquals(2, receipts.findHistoryByReceiptId(id).size());
        assertEquals(earning, receipts.findHistoryByReceiptId(id).getFirst().after().contents().observations().earnings().getFirst());
        var moves = new ProviderBalanceSummary().movements(List.of(after));
        assertEquals(1, moves.stream().filter(m -> m.source().contains("earning ")).count());
        var readonly = HttpRequest.newBuilder(URI.create(base + path)).header("Authorization", "Bearer reader").header("Content-Type", "application/json").header("Idempotency-Key", "readonly").header("If-Match-Revision", "2").PUT(HttpRequest.BodyPublishers.ofString(body)).build();
        assertEquals(403, client.send(readonly, HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(200, call("PUT", path, "{\"status\":\"REJECTED\",\"rejectionReason\":\"Provider correction\"}", "reject", "2").statusCode());
        var rejected = receipts.findById(id).orElseThrow();
        var rejectedEarning = rejected.transaction().getObservations().earnings().getFirst();
        assertEquals(expected, rejectedEarning.expected());
        assertEquals(expected, rejectedEarning.confirmed());
        assertTrue(rejectedEarning.recordedLifecycle().contains("Original note"));
        assertTrue(rejectedEarning.recordedLifecycle().contains("Provider correction"));
        assertEquals(0, new ProviderBalanceSummary().movements(List.of(rejected)).stream().filter(m -> m.source().contains("earning ")).count());
        assertEquals(0, BigDecimal.ZERO.compareTo(SavingsReport.calculate(List.of(rejected)).newlyEarned()));
    }

    @Test
    void webReceiptPreviewAndCreationAreExactAtomicAndIdempotent() throws Exception {
        UUID id = UUID.randomUUID();
        var fields = ReceiptInputTest.draft(id);
        String body = Json.object(fields);
        var preview = call("POST", "/receipts/preview", body, null, null);
        assertEquals(200, preview.statusCode(), preview.body());
        assertTrue(receipts.findById(id).isEmpty());
        var response = Json.parseObject(preview.body());
        assertTrue(preview.body().contains("7.270"));
        assertEquals(409, call("POST", "/receipts", body, "unreviewed", null).statusCode());
        fields.put("reviewToken", response.get("reviewToken"));
        assertEquals(400, call("POST", "/receipts", Json.object(fields), "unacknowledged-difference", null).statusCode());
        fields.put("acknowledgeDifference", true);
        String save = Json.object(fields);
        var created = call("POST", "/receipts", save, "create-receipt", null);
        assertEquals(201, created.statusCode(), created.body());
        server.stop(0);
        start();
        assertEquals(created.body(), call("POST", "/receipts", save, "create-receipt", null).body());
        assertEquals(409, call("POST", "/receipts", save, "same-uuid-new-request", null).statusCode());
        var record = receipts.findById(id).orElseThrow();
        assertEquals(1, record.revision());
        assertEquals(1, receipts.findHistoryByReceiptId(id).size());
        assertEquals(1, receipts.findEvidenceByReceiptId(id).size());
        assertEquals(new BigDecimal("10.0000"), record.transaction().getPurchaseItems().getFirst().getRegularUnitPrice());
        assertEquals(0, new BigDecimal("0.01").compareTo(new TransactionCalculator().calculateReceiptDifference(record.transaction())));
        assertEquals(ReceiptInputTest.read(fields).transaction().getObservations(), record.transaction().getObservations());
        assertArrayEquals(new byte[]{1, 2, 3}, receipts.findEvidenceByReceiptId(id).getFirst().getContent());
        assertEquals("api-owner", receipts.findHistoryByReceiptId(id).getFirst().createCtx().requestedBy());
        var changed = new LinkedHashMap<>(fields);
        changed.put("tax", "1.00");
        changed.put("transactionId", UUID.randomUUID());
        assertEquals(409, call("POST", "/receipts", Json.object(changed), "stale-preview", null).statusCode());
        assertEquals(1, receipts.findHistoryByReceiptId(id).size());
        // Ordinary application construction must project both creation and correction automatically.
        var edit = new LinkedHashMap<String,Object>(Map.of("reason","Correct entered tax", "tax","0.28", "reportedTotal","7.29"));
        var editPreview = call("POST", "/receipts/"+id+"/edit-preview", Json.object(edit), null, "1");
        assertEquals(200,editPreview.statusCode(),editPreview.body());
        edit.put("reviewToken",Json.parseObject(editPreview.body()).get("reviewToken"));
        edit.put("acknowledgeDifference",true);
        var corrected=call("PUT","/receipts/"+id,Json.object(edit),"normal-correction","1");
        assertEquals(200,corrected.statusCode(),corrected.body());
        assertEquals(2,receipts.findById(id).orElseThrow().revision());
        try(var connection=db.open();var query=connection.prepareStatement("SELECT revision,tax_amount,difference FROM receipt_financial_revisions WHERE transaction_id=? ORDER BY revision")) {
            query.setObject(1,id);
            try(var rows=query.executeQuery()) {
                assertTrue(rows.next());assertEquals(1,rows.getLong(1));assertEquals(0,new BigDecimal("0.27").compareTo(rows.getBigDecimal(2)));
                assertTrue(rows.next());assertEquals(2,rows.getLong(1));assertEquals(0,new BigDecimal("0.28").compareTo(rows.getBigDecimal(2)));
                assertEquals(0,new BigDecimal("0.01").compareTo(rows.getBigDecimal(3)));assertFalse(rows.next());
            }
        }
    }

    @Test
    void webReceiptFailuresLeaveNoPartialEvidenceOrAuditAndReadOnlyCannotWrite() throws Exception {
        UUID first = UUID.randomUUID();
        var fields = ReceiptInputTest.draft(first);
        var preview = Json.parseObject(call("POST", "/receipts/preview", Json.object(fields), null, null).body());
        fields.put("reviewToken", preview.get("reviewToken"));
        fields.put("acknowledgeDifference", true);
        assertEquals(201, call("POST", "/receipts", Json.object(fields), "first-create", null).statusCode());
        UUID second = UUID.randomUUID();
        fields.put("transactionId", second);
        fields.put("receiptNumber", "another-receipt");
        fields.put("printedTime", "10:00");
        var secondPreview = call("POST", "/receipts/preview", Json.object(fields), null, null);
        assertEquals(200, secondPreview.statusCode());
        fields.put("reviewToken", Json.parseObject(secondPreview.body()).get("reviewToken"));
        // Same evidence UUID belongs to the first receipt: existing repository validation must reject the complete second write.
        assertEquals(400, call("POST", "/receipts", Json.object(fields), "foreign-evidence", null).statusCode());
        assertTrue(receipts.findById(second).isEmpty());
        assertTrue(receipts.findHistoryByReceiptId(second).isEmpty());
        assertTrue(receipts.findEvidenceByReceiptId(second).isEmpty());
        assertEquals(1, receipts.findHistoryByReceiptId(first).size());
        assertEquals(1, receipts.findEvidenceByReceiptId(first).size());
        var denied = HttpRequest.newBuilder(URI.create(base + "/receipts")).header("Authorization", "Bearer reader").header("Content-Type", "application/json").header("Idempotency-Key", "denied-create").POST(HttpRequest.BodyPublishers.ofString(Json.object(fields))).build();
        assertEquals(403, client.send(denied, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void webZeroDueReceiptNeedsNoInventedPaymentAndDuplicateMetadataIsFlagged() throws Exception {
        UUID id = UUID.randomUUID();
        var f = ReceiptInputTest.draft(id);
        f.put("tax", "0.00");
        f.put("reportedTotal", "0.00");
        f.put("rrUsed", null);
        f.put("discounts", List.of());
        f.put("payments", List.of());
        f.put("earnings", List.of());
        f.put("evidence", List.of());
        f.put("items", List.of(Map.of("lineItemId", UUID.randomUUID(), "description", "Free item", "regularUnitPrice", "0.00", "unitPrice", "0.00", "quantity", 1)));
        var preview = call("POST", "/receipts/preview", Json.object(f), null, null);
        assertEquals(200, preview.statusCode(), preview.body());
        f.put("reviewToken", Json.parseObject(preview.body()).get("reviewToken"));
        assertEquals(201, call("POST", "/receipts", Json.object(f), "zero-due-create", null).statusCode());
        assertTrue(receipts.findById(id).orElseThrow().transaction().getPayments().isEmpty());
        UUID another = UUID.randomUUID();
        f.put("transactionId", another);
        var duplicate = call("POST", "/receipts/preview", Json.object(f), null, null);
        assertEquals(200, duplicate.statusCode());
        assertTrue(duplicate.body().contains(id.toString()));
        f.put("reviewToken", Json.parseObject(duplicate.body()).get("reviewToken"));
        assertEquals(409, call("POST", "/receipts", Json.object(f), "duplicate-metadata", null).statusCode());
        assertTrue(receipts.findById(another).isEmpty());
    }

    @Test
    void planningProgressRoundTripsThroughWorkspaceAndCheckoutWithoutReceiptCredit() throws Exception {
        UUID planId = UUID.randomUUID(), dealId = UUID.randomUUID();
        plans.save(new ShoppingPlan(planId, "Potential", null, null, List.of(), 1), "test");
        var terms = new DealTerms(DealTerms.Type.QUANTITY_REWARD, "Dove", null, null, 2, null,
                0, new BigDecimal("5"), false, null, List.of(), null, null, null);
        var source = new SourceArtifact(UUID.randomUUID(), "Test terms", "application/json", null,
                Instant.now(), "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        new PostgresDealRepository(db).save(new AdvertisedDeal(dealId, "Dove", "Buy two", "Walgreens",
                LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 24), List.of(source.getArtifactId()), 1, terms), "test", source);
        new PostgresPlatformRepository(db).savePurchase(planId, UUID.randomUUID(), "Dove", null, 2,
                new BigDecimal("6"), new BigDecimal("5"), "MANUAL", dealId, List.of(), 0, "test");
        var response = call("GET", "/workspace", null, null, null);
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"rewardStatus\":\"KNOWN\""));
        assertTrue(response.body().contains("\"storeStatus\":\"NOT_RECORDED\""));
        assertTrue(response.body().contains("\"potentialReward\":5"));
        var checkoutResponse = call("GET", "/shopping-plans/" + planId + "/checkout", null, null, null);
        assertEquals(200, checkoutResponse.statusCode(), checkoutResponse.body());
        assertTrue(checkoutResponse.body().contains("\"reviewRequired\":true"));
        assertTrue(checkoutResponse.body().contains("\"rewardsComplete\":true"));
        var workspace = Json.parseObject(response.body());
        assertTrue(((List<?>) workspace.get("receiptEarnings")).isEmpty());
    }
}
