package coupons.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import coupons.*;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import coupons.PurchaseItem;
import coupons.ReceiptTotals;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Runs only against an explicitly configured, disposable PostgreSQL test database.
 *
 * <p>Set COUPON_POSTGRES_TEST_URL, COUPON_POSTGRES_TEST_USER, and
 * COUPON_POSTGRES_TEST_PASSWORD to enable these tests. No local receipt file
 * or production database configuration is used.
 */
class PostgresTransactionRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");
    private PostgresConnectionFactory connections;
    private PostgresTransactionRepository repository;

    private PostgresTestDatabase testDatabase;

    @org.junit.jupiter.api.AfterEach void releaseTestDatabase() throws Exception {
        if (testDatabase != null) testDatabase.close();
    }

    boolean financialsEnabled() { return false; }

    @BeforeEach
    void setUp() throws Exception {
        testDatabase = new PostgresTestDatabase();
        connections = testDatabase.connections;
        repository = new PostgresTransactionRepository(connections,
                Clock.fixed(NOW, ZoneOffset.UTC));
        if (financialsEnabled()) {
            ReceiptFinancialProjection.install(connections, testDatabase.target());
            repository = PostgresTransactionRepository.withFinancialProjections(connections, testDatabase.target());
        }
    }

    @Test void receiptNumbersNotTimesIdentifyConcurrentPurchases() throws Exception {
        var time=java.time.LocalDateTime.of(2026,10,25,16,35);
        repository.save(UUID.randomUUID(),transaction(),new ReceiptMeta("Walgreens","100-001",time,null),List.of(),new ReceiptWriteRequest(UUID.randomUUID(),NOW),new ReceiptCreateCtx("test"));
        repository.save(UUID.randomUUID(),transaction(),new ReceiptMeta("Walgreens","100-002",time,null),List.of(),new ReceiptWriteRequest(UUID.randomUUID(),NOW),new ReceiptCreateCtx("test"));
        assertTrue(repository.findPossibleDuplicates(new ReceiptMeta("Walgreens","100-003",time,null)).isEmpty());
        var start=new java.util.concurrent.CountDownLatch(1);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var attempts=new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for(String number:List.of("200-001","200001"))attempts.add(executor.submit(()->{
                start.await();
                try{new PostgresTransactionRepository(connections,Clock.fixed(NOW,ZoneOffset.UTC)).save(UUID.randomUUID(),transaction(),new ReceiptMeta("Walgreens",number,time,null),List.of(),new ReceiptWriteRequest(UUID.randomUUID(),NOW),new ReceiptCreateCtx("test"));return true;}
                catch(IllegalArgumentException duplicate){assertTrue(duplicate.getMessage().contains("receipt number already exist"));return false;}
            }));
            start.countDown();int saved=0;for(var attempt:attempts)if(attempt.get(15,java.util.concurrent.TimeUnit.SECONDS))saved++;
            assertEquals(1,saved);
        }
    }

    @Test
    void persistsReceiptEvidenceHistoryAndIdempotentRetry() {
        UUID receiptId = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();
        var request = new ReceiptWriteRequest(UUID.randomUUID(), NOW);
        var transaction = transaction();
        var evidence = new ReceiptEvidence(evidenceId, receiptId, "text/plain", new byte[]{1, 2, 3});
        var result = repository.save(receiptId, transaction,
                new ReceiptMeta("Walgreens", "R-1", null, null),
                List.of(evidence), request, new ReceiptCreateCtx("test-user"));

        assertEquals(result, repository.save(receiptId, ReceiptSnapshot.from(transaction).toTransaction(),
                new ReceiptMeta("Walgreens", "R-1", null, null), List.of(evidence),
                new ReceiptWriteRequest(request.requestId(), request.requestedAt()),
                new ReceiptCreateCtx("test-user")));
        assertEquals(1, repository.findById(receiptId).orElseThrow().revision());
        assertEquals(1, repository.findHistoryByReceiptId(receiptId).size());
        assertEquals(evidence, repository.findEvidenceById(evidenceId).orElseThrow());
    }

    @Test
    void supportsRevisionCheckedDeleteAndRestore() {
        UUID receiptId = UUID.randomUUID();
        repository.save(receiptId, transaction(), null, List.of(),
                new ReceiptWriteRequest(UUID.randomUUID(), NOW), new ReceiptCreateCtx("test-user"));

        var deleted = repository.softDelete(receiptId, 1,
                new ReceiptWriteRequest(UUID.randomUUID(), NOW), changeContext());
        assertTrue(repository.findById(receiptId).isEmpty());
        assertEquals(ReceiptStatus.DELETED, repository.findDeletedById(receiptId).orElseThrow().status());

        var restored = repository.restore(receiptId, deleted.revision(),
                new ReceiptWriteRequest(UUID.randomUUID(), NOW), changeContext());
        assertEquals(ReceiptStatus.ACTIVE, restored.status());
        assertEquals(3, repository.findHistoryByReceiptId(receiptId).size());
    }

    @Test
    void rejectsPromotionLinksReferencingMissingPrograms() {
        var transaction = transaction();
        var observations = transaction.getObservations();
        transaction.setObservations(new coupons.ReceiptObservations(
                observations.purchaseDate(), observations.printedTime(), observations.earnings(),
                observations.unidentifiedRR(), observations.walgreensCash(), observations.savings(),
                observations.discountKinds(), observations.receiptSavingValuePercent(),
                List.of(new coupons.ReceiptPromotionLink(UUID.randomUUID(),
                        List.of(transaction.getPurchaseItems().get(0).getLineItemId()),
                        BigDecimal.ONE, "reviewed", NOW)), observations.worksheet(),
                observations.footerStatus()));

        assertThrows(IllegalArgumentException.class, () -> repository.save(UUID.randomUUID(),
                transaction, null, List.of(),
                new ReceiptWriteRequest(UUID.randomUUID(), NOW),
                new ReceiptCreateCtx("test-user")));
    }

    @Test void linkedReceiptCouponPlanAndDraftCommitOrRollBackTogether() {
        UUID id = UUID.randomUUID(), planId = UUID.randomUUID(), couponId = UUID.randomUUID();
        var plans = new PostgresShoppingPlanRepository(connections);
        var clipped = new PostgresClippedCouponRepository(connections);
        var platform = new PostgresPlatformRepository(connections);
        var day = java.time.LocalDate.of(2026, 10, 18);
        plans.save(new ShoppingPlan(planId, "Atomic links", null, day, List.of(), 1), "test");
        clipped.save(new ClippedCoupon(couponId, "Synthetic", null,
                new Coupon("Coupon", BigDecimal.ONE, 1, CouponType.MANUFACTURER, null),
                CouponStatus.CLIPPED, day, 1), "test");
        platform.saveReceiptDraft(id, 0, "{}", "Synthetic", null, "test");
        var tx = transaction();
        tx.addAppliedCoupon(new CouponApplied(couponId, "Coupon", BigDecimal.ONE, List.of(), 1, true));
        tx.setObservations(new ReceiptObservations(day, null, List.of(), List.of(), null, List.of(), java.util.Map.of()));
        var meta = new ReceiptMeta("Synthetic", id.toString(), null, null);
        var evidence = new ReceiptEvidence(UUID.randomUUID(), id, "text/plain", new byte[]{1});
        var request = new ReceiptWriteRequest(UUID.randomUUID(), NOW);
        TransactionRepository storage = repository;
        assertThrows(java.util.ConcurrentModificationException.class, () -> storage.saveWithDraft(
                id, tx, meta, List.of(evidence), request, new ReceiptCreateCtx("test"), List.of(couponId), planId, 2, id, 1));
        assertTrue(repository.findById(id).isEmpty());
        assertTrue(repository.findHistoryByReceiptId(id).isEmpty());
        assertTrue(repository.findEvidenceById(evidence.getEvidenceId()).isEmpty());
        assertEquals(CouponStatus.CLIPPED, clipped.findById(couponId).orElseThrow().status());
        assertNotNull(platform.receiptDraft(id, "test"));
        assertNull(plans.findById(planId).orElseThrow().purchasedReceiptId());
        var result = storage.saveWithDraft(id, tx, meta, List.of(evidence), request,
                new ReceiptCreateCtx("test"), List.of(couponId), planId, 1, id, 1);
        assertEquals(result, storage.saveWithDraft(id, tx, meta, List.of(evidence), request,
                new ReceiptCreateCtx("test"), List.of(couponId), planId, 1, id, 1));
        assertEquals(CouponStatus.APPLIED, clipped.findById(couponId).orElseThrow().status());
        assertEquals(id, plans.findById(planId).orElseThrow().purchasedReceiptId());
        assertThrows(java.util.NoSuchElementException.class, () -> platform.receiptDraft(id, "test"));
        assertEquals(1, repository.findHistoryByReceiptId(id).size());
    }

    @Test void retryIdentityIncludesCouponSelectionAndRejectsDuplicatesBeforeWriting() {
        UUID id = UUID.randomUUID(), coupon = UUID.randomUUID();
        var tx = transaction();
        var request = new ReceiptWriteRequest(UUID.randomUUID(), NOW);
        TransactionRepository storage = repository;
        var result = storage.saveWithDraft(id, tx, null, List.of(), request,
                new ReceiptCreateCtx("test"), List.of(), null, 0, null, 0);
        assertEquals(result, storage.saveWithDraft(id, tx, null, List.of(), request,
                new ReceiptCreateCtx("test"), List.of(), null, 0, null, 0));
        var conflict = assertThrows(IllegalArgumentException.class, () -> storage.saveWithDraft(
                id, tx, null, List.of(), request, new ReceiptCreateCtx("test"),
                List.of(coupon), null, 0, null, 0));
        assertTrue(conflict.getMessage().contains("different contents"));
        UUID rejected = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> storage.saveWithDraft(rejected, tx,
                null, List.of(), new ReceiptWriteRequest(UUID.randomUUID(), NOW),
                new ReceiptCreateCtx("test"), List.of(coupon, coupon), null, 0, null, 0));
        assertTrue(repository.findById(rejected).isEmpty());
        assertEquals(1, repository.findHistoryByReceiptId(id).size());
    }

    @Test void selectedCouponsCommitWithReceiptAndFailureRollsEverythingBack() throws Exception {
        var couponRepo=new PostgresClippedCouponRepository(connections);
        var coupon=new coupons.ClippedCoupon(UUID.randomUUID(),"Synthetic",null,new coupons.Coupon("Synthetic coupon",BigDecimal.ONE,1,coupons.CouponType.MANUFACTURER,java.time.LocalDate.of(2026,10,31)),coupons.CouponStatus.CLIPPED,java.time.LocalDate.of(2026,10,1),null,null,1);
        couponRepo.save(coupon,"test");
        var id=UUID.randomUUID();var tx=transaction();
        tx.addAppliedCoupon(new coupons.CouponApplied(coupon.couponId(),"Synthetic coupon",BigDecimal.ONE,List.of(),1,true));
        tx.setObservations(new coupons.ReceiptObservations(java.time.LocalDate.of(2026,10,18),null,List.of(),List.of(),null,List.of(),java.util.Map.of()));
        var metadata=new ReceiptMeta("Synthetic",id.toString(),null,null);
        var request = new ReceiptWriteRequest(UUID.randomUUID(),NOW);
        var result = repository.saveWithCoupons(id,tx,metadata,List.of(),request,new ReceiptCreateCtx("test"),List.of(coupon.couponId()));
        assertEquals(result, new PostgresTransactionRepository(connections).saveWithCoupons(id,tx,metadata,List.of(),request,new ReceiptCreateCtx("test"),List.of(coupon.couponId())));
        assertThrows(IllegalArgumentException.class, () -> repository.saveWithCoupons(id,tx,metadata,List.of(),request,new ReceiptCreateCtx("test"),List.of()));
        assertEquals(1, repository.findHistoryByReceiptId(id).size());
        // Simulate a pre-extension serialized request, whose new couponIds field is absent/null.
        try (var connection = connections.open()) {
            var type = Class.forName("coupons.repository.PostgresTransactionRepository$CreatePayload");
            var constructor = java.util.Arrays.stream(type.getDeclaredConstructors())
                    .filter(c -> c.getParameterCount() == 11).findFirst().orElseThrow();
            constructor.setAccessible(true);
            var legacy = constructor.newInstance(id, ReceiptSnapshot.from(tx), metadata, List.of(),
                    request, new ReceiptCreateCtx("test"), null, 0L, null, 0L, null);
            try (var update = connection.prepareStatement("UPDATE receipt_write_requests SET payload=? WHERE request_id=?")) {
                update.setBytes(1, PostgresRepositorySupport.serialize(legacy));
                update.setObject(2, request.requestId()); update.executeUpdate();
            }
        }
        assertEquals(result, repository.saveWithCoupons(id,tx,metadata,List.of(),request,new ReceiptCreateCtx("test"),List.of(coupon.couponId())));
        assertThrows(IllegalArgumentException.class, () -> repository.saveWithCoupons(id,tx,metadata,List.of(),request,new ReceiptCreateCtx("test"),List.of()));
        assertEquals(coupons.CouponStatus.APPLIED,couponRepo.findById(coupon.couponId()).orElseThrow().status());
        var failed=UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,()->repository.saveWithCoupons(failed,tx,metadata,List.of(),new ReceiptWriteRequest(UUID.randomUUID(),NOW),new ReceiptCreateCtx("test"),List.of(coupon.couponId())));
        assertTrue(repository.findById(failed).isEmpty());
        var expired=new coupons.ClippedCoupon(UUID.randomUUID(),"Synthetic",null,new coupons.Coupon("Expired",BigDecimal.ONE,1,coupons.CouponType.MANUFACTURER,java.time.LocalDate.of(2026,10,2)),coupons.CouponStatus.CLIPPED,java.time.LocalDate.of(2026,10,1),null,null,1);
        couponRepo.save(expired,"test");var rolledBack=UUID.randomUUID();var other=transaction();
        other.addAppliedCoupon(new coupons.CouponApplied(expired.couponId(),"Expired",BigDecimal.ONE,List.of(),1,true));
        other.setObservations(new coupons.ReceiptObservations(java.time.LocalDate.of(2026,10,18),null,List.of(),List.of(),null,List.of(),java.util.Map.of()));
        assertThrows(IllegalArgumentException.class,()->repository.saveWithCoupons(rolledBack,other,new ReceiptMeta("Synthetic",rolledBack.toString(),null,null),List.of(),new ReceiptWriteRequest(UUID.randomUUID(),NOW),new ReceiptCreateCtx("test"),List.of(expired.couponId())));
        assertTrue(repository.findById(rolledBack).isEmpty());assertEquals(coupons.CouponStatus.CLIPPED,couponRepo.findById(expired.couponId()).orElseThrow().status());

        assertThrows(IllegalArgumentException.class,()->new PostgresPlatformRepository(connections).deleteCatalogRecord(false,coupon.couponId(),2,"test"));
    }

    @Test void permanentCatalogDeletionDetachesPlansAndRemovesUnsharedSource() {
        var platform=new PostgresPlatformRepository(connections);
        var coupon=new ClippedCoupon(UUID.randomUUID(),"Synthetic",null,new Coupon("Synthetic",BigDecimal.ONE,1,CouponType.MANUFACTURER,null),CouponStatus.CLIPPED,java.time.LocalDate.of(2026,10,1),null,null,1);
        new PostgresClippedCouponRepository(connections).save(coupon,"test");
        var source=new SourceArtifact(UUID.randomUUID(),"Synthetic","text/plain",null,NOW,new byte[]{1});
        var deal=new AdvertisedDeal(UUID.randomUUID(),"Synthetic","Synthetic",null,null,null,List.of(source.getArtifactId()),1);
        platform.saveDeal(deal,"test",source);
        var plan=new ShoppingPlan(UUID.randomUUID(),"Synthetic",null,null,List.of(),1);
        new PostgresShoppingPlanRepository(connections).save(plan,"test");
        var item=new PlannedPurchase(UUID.randomUUID(),plan.planId(),"Synthetic",null,1,BigDecimal.TEN,deal.dealId(),List.of(coupon.couponId()),1,BigDecimal.TEN,BigDecimal.TEN,"DEAL");
        var items=new PostgresPlannedPurchaseRepository(connections);items.save(item,"test");
        platform.deleteCatalogRecord(true,deal.dealId(),1,"test");platform.deleteCatalogRecord(false,coupon.couponId(),1,"test");
        var updated=items.findById(item.plannedPurchaseId()).orElseThrow();assertNull(updated.advertisedDealId());assertTrue(updated.clippedCouponIds().isEmpty());assertEquals(BigDecimal.TEN,updated.saleUnitPrice());
        assertTrue(new PostgresDealRepository(connections).findById(deal.dealId()).isEmpty());assertTrue(new PostgresClippedCouponRepository(connections).findById(coupon.couponId()).isEmpty());
        assertTrue(new PostgresSourceArtifactRepository(connections).findById(source.getArtifactId()).isEmpty());
    }

    @Test void savedPlanningScenarioRoundTripsWithoutPostingReceiptOrAccountActivity(){
        var id=UUID.randomUUID();var line=new ShoppingPlan.Line(UUID.randomUUID(),"Synthetic Scott",null,new BigDecimal("5"),null,2);
        var scenario=new ShoppingPlan.Scenario(List.of(line),List.of(),List.of(),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,null,"CASH","",new BigDecimal("8.25"),List.of(new ShoppingPlan.ScenarioPayment(UUID.randomUUID(),"ECB",new BigDecimal("2"),"Scott")));
        var plan=new ShoppingPlan(id,"Synthetic scenario","Store",null,List.of(),1,scenario);var repo=new PostgresShoppingPlanRepository(connections);repo.save(plan,"test");
        assertEquals(scenario,repo.findById(id).orElseThrow().scenario());assertTrue(repository.findById(id).isEmpty());
        assertTrue(new PostgresProviderAccountRepository(connections).findAdjustments("Store").isEmpty());
        var workspace=new PostgresPlatformRepository(connections).workspace();assertNotNull(workspace.get("plans"));
    }

    @Test void permanentlyDeletesOnlySelectedPlanAndAllItsChildren() throws Exception {
        var id=UUID.randomUUID();var other=UUID.randomUUID();var purchase=UUID.randomUUID();
        var plans=new PostgresShoppingPlanRepository(connections);
        plans.save(new ShoppingPlan(id,"Delete test",null,null,List.of(),1),"test");
        plans.save(new ShoppingPlan(other,"Keep test",null,null,List.of(),1),"test");
        try(var c=connections.open()) {
            try(var p=c.prepareStatement("INSERT INTO planned_purchase_current(purchase_id,plan_id,revision,payload) VALUES (?,?,1,?)")){p.setObject(1,purchase);p.setObject(2,id);p.setBytes(3,new byte[]{1});p.executeUpdate();}
            try(var p=c.prepareStatement("INSERT INTO planned_purchase_history(purchase_id,revision,changed_by,reason,payload) VALUES (?,1,'test','test',?)")){p.setObject(1,purchase);p.setBytes(2,new byte[]{1});p.executeUpdate();}
            try(var p=c.prepareStatement("INSERT INTO shopping_plan_checkout_current(plan_id,revision) VALUES (?,1)")){p.setObject(1,id);p.executeUpdate();}
        }
        var platform=new PostgresPlatformRepository(connections);
        assertThrows(java.util.ConcurrentModificationException.class,()->platform.deleteShoppingPlan(id,2,"test"));
        assertTrue(plans.findById(id).isPresent());
        platform.deleteShoppingPlan(id,1,"test");
        assertTrue(plans.findById(id).isEmpty());assertTrue(plans.findByRevision(id,1).isEmpty());assertTrue(plans.findById(other).isPresent());
        try(var c=connections.open()) {
            for(String table:List.of("planned_purchase_current","shopping_plan_checkout_current","shopping_plan_checkout_history"))
                try(var p=c.prepareStatement("SELECT count(*) FROM "+table+" WHERE plan_id=?")){p.setObject(1,id);try(var r=p.executeQuery()){r.next();assertEquals(0,r.getInt(1));}}
            try(var p=c.prepareStatement("SELECT count(*) FROM planned_purchase_history WHERE purchase_id=?")){p.setObject(1,purchase);try(var r=p.executeQuery()){r.next();assertEquals(0,r.getInt(1));}}
        }
    }

    @Test void receiptCreationMarksPlanPurchasedAtomicallyAndRejectsStalePlan() {
        var plans=new PostgresShoppingPlanRepository(connections);var id=UUID.randomUUID();var receiptId=UUID.randomUUID();
        plans.save(new ShoppingPlan(id,"Purchase test",null,null,List.of(),1),"test");
        var request=new ReceiptWriteRequest(UUID.randomUUID(),NOW);
        var tx=transaction();var snapshot=ReceiptSnapshot.from(tx);
        var result=repository.saveWithPlan(receiptId,tx,null,List.of(),request,new ReceiptCreateCtx("test"),List.of(),id,1);
        assertEquals(receiptId,plans.findById(id).orElseThrow().purchasedReceiptId());assertEquals(2,plans.findById(id).orElseThrow().revision());
        assertNull(plans.findByRevision(id,1).orElseThrow().purchasedReceiptId());
        assertEquals(result,repository.saveWithPlan(receiptId,snapshot.toTransaction(),null,List.of(),request,new ReceiptCreateCtx("test"),List.of(),id,1));
        var another=UUID.randomUUID();
        assertThrows(java.util.ConcurrentModificationException.class,()->repository.saveWithPlan(another,transaction(),null,List.of(),new ReceiptWriteRequest(UUID.randomUUID(),NOW),new ReceiptCreateCtx("test"),List.of(),id,1));
        assertTrue(repository.findById(another).isEmpty());
        var stale=UUID.randomUUID();plans.save(new ShoppingPlan(stale,"Stale",null,null,List.of(),1),"test");plans.update(new ShoppingPlan(stale,"Edited",null,null,List.of(),2),1,"test","Edit");
        assertThrows(java.util.ConcurrentModificationException.class,()->repository.saveWithPlan(another,transaction(),null,List.of(),new ReceiptWriteRequest(UUID.randomUUID(),NOW),new ReceiptCreateCtx("test"),List.of(),stale,1));
        assertTrue(repository.findById(another).isEmpty());assertNull(plans.findById(stale).orElseThrow().purchasedReceiptId());
    }

    @Test void importsReceiptInventoryOnceAndTracksUsageCorrectionsDeletionAndRestore() {
        UUID first=UUID.randomUUID(),second=UUID.randomUUID(),third=UUID.randomUUID();
        var a=inventoryTransaction("00123","Olay",2);var b=inventoryTransaction("00123","Olay alternate name",3);
        repository.save(first,a,null,List.of(),new ReceiptWriteRequest(UUID.randomUUID(),NOW),new ReceiptCreateCtx("test"));
        repository.save(second,b,null,List.of(),new ReceiptWriteRequest(UUID.randomUUID(),NOW),new ReceiptCreateCtx("test"));
        var inventory=new PostgresInventoryRepository(connections);assertEquals(5L,inventory.importReceipts("test").get("purchasedPackages"));
        var row=inventoryRow();assertEquals("00123",row.get("sku"));assertEquals(5L,row.get("totalPurchased"));assertEquals(5L,row.get("quantityOnHand"));assertNull(row.get("location"));assertEquals(true,row.get("needsProductReview"));
        var revision=(Long)row.get("revision");inventory.importReceipts("test");assertEquals(revision,inventoryRow().get("revision"));
        UUID stock=(UUID)row.get("inventoryId");inventory.adjust(stock,revision,"USE",1,null,"Used one without counting first","test");assertEquals(4,inventoryRow().get("quantityOnHand"));
        row=inventoryRow();inventory.adjust(stock,(Long)row.get("revision"),"USE",1,"Pantry","Used one","test");assertEquals(3,inventoryRow().get("quantityOnHand"));
        var c=inventoryTransaction("00123","Olay",2);repository.save(third,c,null,List.of(),new ReceiptWriteRequest(UUID.randomUUID(),NOW),new ReceiptCreateCtx("test"));
        assertEquals(7L,inventoryRow().get("totalPurchased"));assertEquals(5,inventoryRow().get("quantityOnHand"));assertEquals("Pantry",inventoryRow().get("location"));
        var line=c.getPurchaseItems().getFirst();var corrected=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,BigDecimal.ONE));corrected.addPurchaseItem(new PurchaseItem(line.getLineItemId(),"Olay","00123",BigDecimal.ONE,BigDecimal.ONE,1));
        repository.update(third,1,corrected,null,List.of(),new ReceiptWriteRequest(UUID.randomUUID(),NOW),changeContext());assertEquals(4,inventoryRow().get("quantityOnHand"));assertEquals(6L,inventoryRow().get("totalPurchased"));
        repository.softDelete(third,2,new ReceiptWriteRequest(UUID.randomUUID(),NOW),changeContext());assertEquals(3,inventoryRow().get("quantityOnHand"));assertEquals(5L,inventoryRow().get("totalPurchased"));
        repository.restore(third,3,new ReceiptWriteRequest(UUID.randomUUID(),NOW),changeContext());assertEquals(4,inventoryRow().get("quantityOnHand"));
        row=inventoryRow();inventory.adjust(stock,(Long)row.get("revision"),"SET",0,"","Counted","test");assertNull(inventoryRow().get("location"));
        repository.softDelete(third,4,new ReceiptWriteRequest(UUID.randomUUID(),NOW),changeContext());assertNotNull(inventoryRow().get("quantityOnHand"));
    }
    @SuppressWarnings("unchecked") private java.util.Map<String,Object> inventoryRow() {
        return (java.util.Map<String,Object>)((java.util.List<?>)new PostgresPlatformRepository(connections).workspace().get("inventory")).getFirst();
    }
    private static CouponTransaction inventoryTransaction(String sku,String name,int quantity) {
        var tx=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,BigDecimal.valueOf(quantity)));tx.addPurchaseItem(new PurchaseItem(name,sku,BigDecimal.ONE,BigDecimal.ONE,quantity));return tx;
    }

    @Test void savedDraftIsIsolatedVersionedAndConsumedOnlyWithSuccessfulReceiptSave() {
        var platform=new PostgresPlatformRepository(connections);var id=UUID.randomUUID();String payload="{\"id\":\""+id+"\",\"lines\":[],\"tax\":\"\"}";
        platform.saveReceiptDraft(id,0,payload,"Walmart",null,"test");assertEquals(payload,platform.receiptDraft(id,"test").get("payload"));assertTrue(repository.findById(id).isEmpty());assertTrue(((List<?>)platform.workspace().get("inventory")).isEmpty());
        assertThrows(java.util.ConcurrentModificationException.class,()->platform.saveReceiptDraft(id,0,payload,"Walmart",null,"test"));
        platform.saveReceiptDraft(id,1,payload,"Walmart",null,"test");
        assertThrows(java.util.ConcurrentModificationException.class,()->repository.saveWithDraft(id,transaction(),null,List.of(),new ReceiptWriteRequest(UUID.randomUUID(),NOW),new ReceiptCreateCtx("test"),List.of(),null,0,id,1));
        assertTrue(repository.findById(id).isEmpty());assertEquals(2L,platform.receiptDraft(id,"test").get("revision"));
        var tx=transaction();var snapshot=ReceiptSnapshot.from(tx);var request=new ReceiptWriteRequest(UUID.randomUUID(),NOW);
        var result=repository.saveWithDraft(id,tx,null,List.of(),request,new ReceiptCreateCtx("test"),List.of(),null,0,id,2);
        assertTrue(repository.findById(id).isPresent());assertThrows(java.util.NoSuchElementException.class,()->platform.receiptDraft(id,"test"));
        assertEquals(result,repository.saveWithDraft(id,snapshot.toTransaction(),null,List.of(),request,new ReceiptCreateCtx("test"),List.of(),null,0,id,2));
    }
    @Test void packageContentsDoNotMultiplyReceiptPurchasesAndIndividualUsageIsTracked() {
        var receipt=UUID.randomUUID();repository.save(receipt,inventoryTransaction("070330714170","Bic razors",1),null,List.of(),new ReceiptWriteRequest(UUID.randomUUID(),NOW),new ReceiptCreateCtx("test"));
        var inventory=new PostgresInventoryRepository(connections);inventory.importReceipts("test");var row=inventoryRow();UUID id=(UUID)row.get("inventoryId");
        inventory.adjust(id,(Long)row.get("revision"),"SET",1,null,"One unopened pack","test",null,4,"Razors","PACKAGE");row=inventoryRow();assertEquals(1L,row.get("totalPurchased"));assertEquals(1,row.get("quantityOnHand"));assertEquals(new BigDecimal("4"),row.get("individualPurchased"));assertEquals(new BigDecimal("4"),row.get("individualOnHand"));
        inventory.adjust(id,(Long)row.get("revision"),"SET",4,null,"Count individual razors","test",2,4,"Razors","ITEM");row=inventoryRow();
        inventory.adjust(id,(Long)row.get("revision"),"USE",1,null,"Used one razor","test",2,4,"Razors","ITEM");assertEquals(3,inventoryRow().get("quantityOnHand"));
        var another=UUID.randomUUID();repository.save(another,inventoryTransaction("070330714170","Bic razors",1),null,List.of(),new ReceiptWriteRequest(UUID.randomUUID(),NOW),new ReceiptCreateCtx("test"));assertEquals(7,inventoryRow().get("quantityOnHand"));assertEquals(2L,inventoryRow().get("totalPurchased"));
    }

    @Test void standaloneRewardCorrectionsReplaceDenominationAndMemberCorrectionsPostOnlyDifference() {
        var platform=new PostgresPlatformRepository(connections);var id=UUID.randomUUID();var reward=new coupons.RegisterReward(id,"Manual coupon",new BigDecimal("5"),null,null,null);platform.addStandaloneReward(reward);
        platform.correctStandaloneAmount(id,new BigDecimal("5"),new BigDecimal("7"));
        assertThrows(java.util.ConcurrentModificationException.class,()->platform.correctStandaloneAmount(id,new BigDecimal("5"),new BigDecimal("8")));
        var member=UUID.randomUUID();platform.saveMemberReward(new AccountAdjustment(member,java.time.LocalDate.of(2026,10,1),"ECB",null,new BigDecimal("2"),"Member reward: Monthly", "test",NOW));
        platform.correctMemberAmount(member,UUID.randomUUID(),new BigDecimal("2"),new BigDecimal("3"),"test");assertEquals(0,new BigDecimal("3").compareTo(platform.memberAmount(member)));
        assertThrows(java.util.ConcurrentModificationException.class,()->platform.correctMemberAmount(member,UUID.randomUUID(),new BigDecimal("2"),new BigDecimal("4"),"test"));
        platform.correctMemberAmount(member,UUID.randomUUID(),new BigDecimal("3"),new BigDecimal("4"),"test");assertEquals(0,new BigDecimal("4").compareTo(platform.memberAmount(member)));
        platform.correctMemberAmount(member,UUID.randomUUID(),new BigDecimal("4"),BigDecimal.ZERO,"test");assertEquals(0,platform.memberAmount(member).signum());var account=platform.account("ECB",java.time.LocalDate.of(2026,10,1),java.time.LocalDate.of(2026,10,31));assertTrue(!account.get("activity").toString().contains(member.toString()));platform.deleteStandaloneReward(id,"test");assertTrue(platform.standaloneRewards().isEmpty());
    }

    private static CouponTransaction transaction() {
        var transaction = new CouponTransaction(new ReceiptTotals(
                new BigDecimal("0.270"), new BigDecimal("5.280")));
        transaction.addPurchaseItem(new PurchaseItem("Product", null, null,
                new BigDecimal("5.000"), 1));
        return transaction;
    }

    private static ReceiptChangeContext changeContext() {
        return new ReceiptChangeContext("test-user", "reviewed", "test-user", NOW);
    }
}
