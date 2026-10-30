package coupons.repository;

import coupons.promotions.BenefitKind;
import coupons.promotions.ClippedCoupon;
import coupons.promotions.Coupon;
import coupons.promotions.CouponApplied;
import coupons.promotions.CouponStatus;
import coupons.receipts.CouponTransaction;
import coupons.promotions.CouponType;
import coupons.receipts.Payment;
import coupons.receipts.PaymentType;
import coupons.rewards.ProviderBalanceSummary;
import coupons.receipts.PurchaseItem;
import coupons.promotions.RebateBenefit;
import coupons.promotions.RebateStatus;
import coupons.receipts.ReceiptObservations;
import coupons.receipts.ReceiptTotals;
import coupons.shopping.ShoppingPlan;

import coupons.receipts.ReceiptObservations.*;
import java.math.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ReceiptFinancialProjectionTest {
    PostgresTestDatabase database;
    PostgresTransactionRepository repository;
    static final LocalDate DAY = LocalDate.of(2026,10,27);
    @BeforeEach void setup() throws Exception {
        database = new PostgresTestDatabase();
        ReceiptFinancialProjection.install(database.connections,database.target());
        repository = new PostgresTransactionRepository(database.connections);
    }
    @AfterEach void cleanup() throws Exception { if(database!=null)database.close(); }
    static BigDecimal n(String value) { return new BigDecimal(value); }
    static ReceiptWriteRequest request() { return new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()); }
    static ReceiptChangeContext change() { return new ReceiptChangeContext("test","Correct accidental entry","test",Instant.now()); }
    static CouponTransaction transaction() {
        var tx=new CouponTransaction(new ReceiptTotals(n("0.00125"),n("0.00126")));
        tx.addPurchaseItem(new PurchaseItem("Free product","000123",null,n("0.0000"),1));
        return tx;
    }
    void save(UUID id,CouponTransaction tx) { repository.save(id,tx,null,List.of(),request(),new ReceiptCreateCtx("test")); }
    Object value(String sql) throws Exception {
        try(var c=database.connections.open();var s=c.createStatement();var r=s.executeQuery(sql)){assertTrue(r.next());return r.getObject(1);}
    }
    long count(String table) throws Exception { return ((Number)value("SELECT count(*) FROM "+table)).longValue(); }
    byte[] source(UUID id) throws Exception {
        try(var c=database.connections.open();var s=c.prepareStatement("SELECT payload FROM receipt_history WHERE transaction_id=? ORDER BY revision")) {
            s.setObject(1,id);try(var r=s.executeQuery()){assertTrue(r.next());return r.getBytes(1);}
        }
    }

    /** Model a pre-V12 audit revision without a production bypass switch. */
    void seedLegacy(UUID id, CouponTransaction tx) throws Exception {
        try (var memory = new InMemoryTransactionRepository(); var c = database.connections.open()) {
            memory.save(id,tx,null,List.of(),request(),new ReceiptCreateCtx("test"));
            var record=memory.findById(id).orElseThrow();
            var history=memory.history().findByReceiptId(id).getFirst();
            try (var insert=c.prepareStatement("INSERT INTO receipt_current VALUES(?,'ACTIVE',1,?,NULL,?)")) {
                insert.setObject(1,id);insert.setBytes(2,PostgresRepositorySupport.serialize(ReceiptSnapshot.from(tx)));
                insert.setBytes(3,PostgresRepositorySupport.serialize(List.of()));insert.executeUpdate();
            }
            try (var insert=c.prepareStatement("INSERT INTO receipt_history VALUES(?,?,?,?,?)")) {
                insert.setObject(1,history.historyId());insert.setObject(2,id);insert.setLong(3,record.revision());
                insert.setObject(4,history.request().requestId());insert.setBytes(5,PostgresRepositorySupport.serialize(history));insert.executeUpdate();
            }
        }
    }

    @Test void exactDecimalsNullZeroUnitsAndNoOptionalOffers() throws Exception {
        save(UUID.randomUUID(),transaction());
        assertNull(value("SELECT regular_unit_price FROM receipt_purchase_lines"));
        assertEquals(n("0.0000"),((BigDecimal)value("SELECT purchase_unit_price FROM receipt_purchase_lines")).setScale(4,RoundingMode.UNNECESSARY));
        assertEquals(4,value("SELECT purchase_unit_price_scale FROM receipt_purchase_lines"));
        assertEquals(n("0.00125"),value("SELECT tax_amount FROM receipt_financial_revisions"));
        assertEquals(n("0.00001"),value("SELECT difference FROM receipt_financial_revisions"));
        assertEquals(0,count("receipt_coupon_applications"));assertEquals(0,count("receipt_coupon_offers"));
        assertEquals(0,count("receipt_promotion_terms"));assertEquals(0,count("receipt_payments"));
        assertEquals(0L,((Number)value("SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema() AND table_name LIKE 'receipt_%' AND data_type='numeric' AND numeric_scale IS NOT NULL AND table_name IN ('receipt_financial_revisions','receipt_purchase_lines','receipt_financial_amounts')")).longValue());
        try(var c=database.connections.open();var s=c.createStatement()) {
            assertThrows(SQLException.class,()->s.executeUpdate("UPDATE receipt_purchase_lines SET purchase_unit_price=9"));
            assertThrows(SQLException.class,()->s.executeUpdate("DELETE FROM receipt_financial_revisions"));
        }
    }

    @Test void preservesLargeValuesNegativeScaleAndExistingRegularPriceWithoutSale() throws Exception {
        var tx=new CouponTransaction(new ReceiptTotals(n("0.00"),n("12345678901234567890.123456789")));
        tx.addPurchaseItem(new PurchaseItem("Regular purchase",null,n("1E+3"),n("12345678901234567890.123456789"),1));
        save(UUID.randomUUID(),tx);
        assertEquals(-3,value("SELECT regular_unit_price_scale FROM receipt_purchase_lines"));
        assertEquals(n("1E+3"),((BigDecimal)value("SELECT regular_unit_price FROM receipt_purchase_lines")).setScale(-3,RoundingMode.UNNECESSARY));
        assertEquals(n("12345678901234567890.123456789"),value("SELECT purchase_unit_price FROM receipt_purchase_lines"));
        var ordinary=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,n("2.00")));
        ordinary.addPurchaseItem(new PurchaseItem("No sale",null,n("2.00"),n("2.00"),1));save(UUID.randomUUID(),ordinary);
    }

    @Test void correctionsAppendHistoryAndRemoveAccidentalCouponOnlyFromCurrentRevision() throws Exception {
        UUID id=UUID.randomUUID();var tx=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,n("1.00")));
        tx.addPurchaseItem(new PurchaseItem("Item",null,n("2.00"),n("2.00"),1));
        tx.addAppliedCoupon(new CouponApplied("Accidental",n("1.00"),List.of()));save(id,tx);
        byte[] original=source(id);
        var corrected=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,n("2.00")));
        corrected.addPurchaseItem(tx.getPurchaseItems().getFirst());
        repository.update(id,1,corrected,null,List.of(),request(),change());
        assertEquals(1,count("receipt_coupon_applications"));
        assertEquals(1L,((Number)value("SELECT count(*) FROM receipt_coupon_applications WHERE revision=1")).longValue());
        assertEquals(0L,((Number)value("SELECT count(*) FROM receipt_coupon_applications WHERE revision=2")).longValue());
        assertArrayEquals(original,source(id));assertTrue(repository.findById(id).orElseThrow().transaction().getAppliedCoupons().isEmpty());
        repository.softDelete(id,2,request(),change());repository.restore(id,3,request(),change());
        assertEquals(4,count("receipt_financial_revisions"));assertArrayEquals(original,source(id));
    }

    @Test void restartableBackfillPreservesBytesAndNeverDuplicatesEvents() throws Exception {
        var plain=new PostgresTransactionRepository(database.connections);
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),run=UUID.randomUUID();
        seedLegacy(a,transaction());
        seedLegacy(b,transaction());
        byte[] original=source(a);
        var first=ReceiptFinancialProjection.backfill(database.connections,database.target(),run,1);
        assertEquals(1,first.projectedRevisions());assertFalse(first.complete());
        var resumed=ReceiptFinancialProjection.backfill(database.connections,database.target(),run,1);
        assertTrue(resumed.complete());assertEquals(2,resumed.projectedRevisions());
        assertTrue(ReceiptFinancialProjection.backfill(database.connections,database.target(),run,10).complete());
        assertEquals(2,count("receipt_financial_revisions"));assertEquals(2,count("receipt_purchase_lines"));
        assertArrayEquals(original,source(a));
        var restarted=PostgresTransactionRepository.withFinancialProjections(database.connections,database.target());
        assertEquals(n("0.00126"),restarted.findById(a).orElseThrow().transaction().getReceiptTotals().getFinalTotal());
        seedLegacy(UUID.randomUUID(),transaction());
        assertThrows(IllegalStateException.class,()->ReceiptFinancialProjection.backfill(database.connections,database.target(),run,10));
    }

    @Test void projectionFailureRollsBackReceiptEvidenceAuditAndRequest() throws Exception {
        try(var c=database.connections.open();var s=c.createStatement()) {
            s.execute("ALTER TABLE receipt_purchase_lines ADD CONSTRAINT inject_failure CHECK(product_name <> 'Free product')");
        }
        UUID id=UUID.randomUUID(),planId=UUID.randomUUID(),couponId=UUID.randomUUID();var write=request();
        var tx=new CouponTransaction(new ReceiptTotals(n("0.00125"),n("1.00126")));
        tx.addPurchaseItem(new PurchaseItem("Free product",null,null,n("2.0000"),1));
        tx.addAppliedCoupon(new CouponApplied(couponId,"Coupon",BigDecimal.ONE,List.of(),1,true));
        tx.setObservations(new ReceiptObservations(DAY,null,List.of(),List.of(),null,List.of(),Map.of()));
        var clipped=new PostgresClippedCouponRepository(database.connections);
        clipped.save(new ClippedCoupon(couponId,"Synthetic",null,new Coupon("Coupon",BigDecimal.ONE,1,CouponType.MANUFACTURER,null),CouponStatus.CLIPPED,DAY,1),"test");
        var plans=new PostgresShoppingPlanRepository(database.connections);
        plans.save(new ShoppingPlan(planId,"Projection failure",null,DAY,List.of(),1),"test");
        var platform=new PostgresPlatformRepository(database.connections);
        platform.saveReceiptDraft(id,0,"{}","Synthetic",null,"test");
        var metadata=new ReceiptMeta("Synthetic",id.toString(),null,null);
        var evidence=new ReceiptEvidence(UUID.randomUUID(),id,"text/plain",new byte[]{1});
        assertThrows(IllegalStateException.class,()->repository.saveWithDraft(id,tx,metadata,List.of(evidence),write,new ReceiptCreateCtx("test"),List.of(couponId),planId,1,id,1));
        assertTrue(repository.findById(id).isEmpty());assertEquals(0,count("receipt_history"));
        assertEquals(0,count("receipt_evidence"));assertEquals(0,count("receipt_write_requests"));assertEquals(0,count("receipt_financial_revisions"));
        assertEquals(CouponStatus.CLIPPED,clipped.findById(couponId).orElseThrow().status());
        assertEquals(0,count("clipped_coupon_redemptions"));
        assertNotNull(platform.receiptDraft(id,"test"));assertNull(plans.findById(planId).orElseThrow().purchasedReceiptId());
        try(var c=database.connections.open();var s=c.createStatement()){s.execute("ALTER TABLE receipt_purchase_lines DROP CONSTRAINT inject_failure");}
        repository.saveWithDraft(id,tx,metadata,List.of(evidence),write,new ReceiptCreateCtx("test"),List.of(couponId),planId,1,id,1);
        assertEquals(1,count("receipt_financial_revisions"));assertEquals(CouponStatus.APPLIED,clipped.findById(couponId).orElseThrow().status());
        assertEquals(id,plans.findById(planId).orElseThrow().purchasedReceiptId());
        assertThrows(NoSuchElementException.class,()->platform.receiptDraft(id,"test"));
    }

    @Test void recordedPointsValuationAndPayoutReservationsRemainUnchanged() throws Exception {
        var benefit=new RebateBenefit(BenefitKind.POINTS,n("3000"),null,"Fetch");
        var earning=new Earning(UUID.randomUUID(),EarningKind.THIRD_PARTY_REBATE,"Fetch",null,Scope.RECEIPT,List.of(),benefit,benefit,RebateStatus.CREDITED,null,null,null,List.of(),null,null,new RecordedValuation(n("3.0000"),Currency.getInstance("USD"),"1000 points = 1 USD"));
        var tx=transaction();tx.setObservations(new ReceiptObservations(DAY,null,List.of(earning),List.of(),null,List.of(),Map.of()));
        UUID id=UUID.randomUUID();save(id,tx);
        assertEquals("POINTS",value("SELECT unit FROM receipt_earning_benefits LIMIT 1"));
        assertEquals(n("3000"),value("SELECT amount FROM receipt_earning_benefits WHERE benefit_role='CONFIRMED'"));
        assertEquals(n("3.0000"),value("SELECT amount FROM receipt_financial_amounts WHERE field_name='valuation_amount'"));
        var ledger=new PostgresProviderAccountRepository(database.connections);
        ledger.saveOpening(new AccountOpening(UUID.randomUUID(),DAY,"Fetch",n("10000"),n("10.00"),false,"recorded conversion","synthetic statement",Instant.now()));
        var platform=new PostgresPlatformRepository(database.connections);
        var baseline=platform.account("Fetch",DAY,DAY);
        var payout=new ProviderRedemption(UUID.randomUUID(),"Fetch",DAY,LocalTime.of(10,0),n("1000"),n("1.00"),ProviderRedemption.Destination.CASH,"Bank",n("1.00"),ProviderRedemption.Status.REQUESTED,null,null,List.of(),"Synthetic payout","Description retained");
        ledger.saveRedemption(payout);
        var pending=platform.account("Fetch",DAY,DAY);
        assertEquals(n("1.00"),pending.get("spent"));assertEquals(n("1000"),pending.get("spentPoints"));
        var pendingBalance=(ProviderBalanceSummary.Balance)pending.get("ending");
        ReceiptFinancialProjection.backfill(database.connections,database.target(),UUID.randomUUID(),100);
        assertEquals(pending,platform.account("Fetch",DAY,DAY));
        ledger.updateRedemption(payout.redemptionId(),1,payout.complete(ProviderRedemption.Status.RECEIVED,DAY,LocalTime.NOON));
        var received=platform.account("Fetch",DAY,DAY);
        assertEquals(n("1.00"),received.get("spent"));assertEquals(pendingBalance,received.get("ending"));
        assertEquals(ProviderRedemption.Status.RECEIVED,ledger.findRedemption(payout.redemptionId()).orElseThrow().status());
        var cancelled=new ProviderRedemption(UUID.randomUUID(),"Fetch",DAY,LocalTime.of(13,0),n("1000"),n("1.00"),ProviderRedemption.Destination.CASH,"Bank",n("1.00"),ProviderRedemption.Status.REQUESTED,null,null,List.of(),"Synthetic cancel","Cancel test");
        ledger.saveRedemption(cancelled);ledger.updateRedemption(cancelled.redemptionId(),1,cancelled.complete(ProviderRedemption.Status.CANCELLED,DAY,LocalTime.of(14,0)));
        assertEquals(pendingBalance,platform.account("Fetch",DAY,DAY).get("ending"));
        assertNotEquals(baseline.get("ending"),pendingBalance);
        assertEquals("Description retained",ledger.findRedemption(payout.redemptionId()).orElseThrow().notes());
    }

    @Test void numericConstraintsRejectNonfiniteAndInvalidUnitsAndTruncate() throws Exception {
        UUID id=UUID.randomUUID();save(id,transaction());
        try(var c=database.connections.open();var s=c.prepareStatement("""
                INSERT INTO receipt_financial_amounts(transaction_id,revision,source_path,field_name,unit,
                  source_representation,source_text,amount,amount_scale,source_context)
                VALUES(?,1,'invalid','wc_used',?,'BIG_DECIMAL','synthetic',?::numeric,0,?)
                """)) {
            for(String invalid:List.of("NaN","Infinity","-Infinity")) {
                s.setObject(1,id);s.setString(2,"MONEY");s.setString(3,invalid);s.setBytes(4,new byte[]{1});
                assertEquals("23514",assertThrows(SQLException.class,()->s.executeUpdate()).getSQLState());
            }
            s.setObject(1,id);s.setString(2,"POINTS");s.setString(3,"1");s.setBytes(4,new byte[]{1});
            assertEquals("23514",assertThrows(SQLException.class,()->s.executeUpdate()).getSQLState());
            assertThrows(SQLException.class,()->c.createStatement().executeUpdate("TRUNCATE receipt_financial_amounts"));
        }
        assertThrows(IllegalArgumentException.class,()->new Payment(PaymentType.CASH,BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,()->new Payment(PaymentType.CASH,n("-1")));
    }

    @Test void failedBackfillLeavesNoPartialRevisionAndCanResume() throws Exception {
        UUID id=UUID.randomUUID(),run=UUID.randomUUID();
        seedLegacy(id,transaction());
        byte[] original=source(id);
        try(var c=database.connections.open();var s=c.createStatement()) {
            s.execute("ALTER TABLE receipt_purchase_lines ADD CONSTRAINT backfill_failure CHECK(product_name <> 'Free product')");
        }
        assertThrows(SQLException.class,()->ReceiptFinancialProjection.backfill(database.connections,database.target(),run,1));
        assertEquals(0,count("receipt_financial_revisions"));assertArrayEquals(original,source(id));
        assertEquals(false,value("SELECT complete FROM receipt_financial_backfill_runs"));
        try(var c=database.connections.open();var s=c.createStatement()){s.execute("ALTER TABLE receipt_purchase_lines DROP CONSTRAINT backfill_failure");}
        assertTrue(ReceiptFinancialProjection.backfill(database.connections,database.target(),run,1).complete());
        assertEquals(1,count("receipt_purchase_lines"));assertArrayEquals(original,source(id));
    }

    @Test void concurrentBackfillsCreateOnlyOneProjectionPerSourceRevision() throws Exception {
        UUID id=UUID.randomUUID();
        seedLegacy(id,transaction());
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var tasks=new ArrayList<java.util.concurrent.Future<ReceiptFinancialProjection.BackfillResult>>();
            for(int i=0;i<2;i++)tasks.add(executor.submit(()->ReceiptFinancialProjection.backfill(database.connections,database.target(),UUID.randomUUID(),10)));
            for(var task:tasks)assertTrue(task.get(20,java.util.concurrent.TimeUnit.SECONDS).complete());
        }
        assertEquals(1,count("receipt_financial_revisions"));assertEquals(1,count("receipt_purchase_lines"));
    }

    @Test void outOfRangeDecimalFailsInsteadOfBeingRoundedOrPartiallySaved() throws Exception {
        var tooPrecise = new CouponTransaction(new ReceiptTotals(new BigDecimal(java.math.BigInteger.ONE,16384),BigDecimal.ONE));
        tooPrecise.addPurchaseItem(new PurchaseItem("Item",null,null,BigDecimal.ONE,1));
        UUID id=UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,()->save(id,tooPrecise));
        assertTrue(repository.findById(id).isEmpty());
        assertEquals(0,count("receipt_financial_revisions"));assertEquals(0,count("receipt_history"));
    }

    @Test void receiptAndProjectionSurviveSeparateJvmRestart() throws Exception {
        UUID id=UUID.randomUUID();
        String schema=database.target().schema();
        String classpath=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        String javaExecutable=java.nio.file.Path.of(System.getProperty("java.home"),"bin","java").toString();
        for(String operation:List.of("install","write","verify")) {
            var command=new ArrayList<String>(List.of(javaExecutable,"-cp",classpath));
            if(operation.equals("install")) command.addAll(List.of(ReceiptFinancialProjection.class.getName(),"install",schema));
            else command.addAll(List.of(ReceiptFinancialRestartProbe.class.getName(),schema,id.toString(),operation));
            var builder=new ProcessBuilder(command).redirectErrorStream(true);
            builder.environment().put("COUPON_POSTGRES_TEST_URL",System.getenv("COUPON_POSTGRES_TEST_URL")+"?currentSchema="+schema);
            var process=builder.start();
            assertTrue(process.waitFor(20,java.util.concurrent.TimeUnit.SECONDS),"Child JVM timed out");
            String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0,process.exitValue(),output);
        }
        assertEquals(1,count("receipt_financial_revisions"));assertEquals(1,count("receipt_payments"));
        assertEquals(0,new BigDecimal("0.01").compareTo((BigDecimal)value("SELECT difference FROM receipt_financial_revisions")));
    }

    @Test void normalSchemaInstallAutomaticallyCreatesAndBackfillsProjections() throws Exception {
        UUID id=UUID.randomUUID();seedLegacy(id,transaction());byte[] original=source(id);
        try(var c=database.connections.open()){PostgresSchema.apply(c);PostgresSchema.apply(c);}
        assertEquals(1,count("receipt_financial_revisions"));assertArrayEquals(original,source(id));
        new PostgresTransactionRepository(database.connections).save(UUID.randomUUID(),transaction(),null,List.of(),request(),new ReceiptCreateCtx("test"));
        assertEquals(2,count("receipt_financial_revisions"));
        assertEquals(1L,((Number)value("SELECT count(*) FROM schema_version WHERE version=12")).longValue());
    }
}
