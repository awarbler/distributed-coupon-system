package coupons.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import coupons.*;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @BeforeEach
    void setUp() throws Exception {
        Assumptions.assumeTrue(System.getenv("COUPON_POSTGRES_TEST_URL") != null,
                "PostgreSQL integration tests require an explicitly configured test database.");
        connections = new PostgresConnectionFactory(
                System.getenv("COUPON_POSTGRES_TEST_URL"),
                System.getenv("COUPON_POSTGRES_TEST_USER"),
                System.getenv("COUPON_POSTGRES_TEST_PASSWORD"));
        try (Connection connection = connections.open()) {
            PostgresSchema.apply(connection);
            connection.createStatement().executeUpdate(
                    "TRUNCATE rr_usage_links, reward_removals, gift_card_promotions, receipt_write_requests, receipt_history, register_reward_redemptions, "
                            + "receipt_evidence, register_rewards, receipt_current");
        }
        repository = new PostgresTransactionRepository(connections,
                Clock.fixed(NOW, ZoneOffset.UTC));
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

    @Test void selectedCouponsCommitWithReceiptAndFailureRollsEverythingBack() {
        var couponRepo=new PostgresClippedCouponRepository(connections);
        var coupon=new coupons.ClippedCoupon(UUID.randomUUID(),"Synthetic",null,new coupons.Coupon("Synthetic coupon",BigDecimal.ONE,1,coupons.CouponType.MANUFACTURER,java.time.LocalDate.of(2026,10,31)),coupons.CouponStatus.CLIPPED,java.time.LocalDate.of(2026,10,1),null,null,1);
        couponRepo.save(coupon,"test");
        var id=UUID.randomUUID();var tx=transaction();
        tx.addAppliedCoupon(new coupons.CouponApplied(coupon.couponId(),"Synthetic coupon",BigDecimal.ONE,List.of(),1,true));
        tx.setObservations(new coupons.ReceiptObservations(java.time.LocalDate.of(2026,10,18),null,List.of(),List.of(),null,List.of(),java.util.Map.of()));
        var metadata=new ReceiptMeta("Synthetic",id.toString(),null,null);
        repository.saveWithCoupons(id,tx,metadata,List.of(),new ReceiptWriteRequest(UUID.randomUUID(),NOW),new ReceiptCreateCtx("test"),List.of(coupon.couponId()));
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
