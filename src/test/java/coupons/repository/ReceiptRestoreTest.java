package coupons.repository;

import coupons.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Tests restoration of recorded transaction facts without recalculation. */
class ReceiptRestoreTest {
    /**
     * Creates illustrative records in every supported category.
     *
     * <p>This reproduces regression arithmetic, not original receipt products.
     * @return populated transaction
     */
    private CouponTransaction recordedTransaction() {
        UUID receiptId = UUID.randomUUID();
        CouponTransaction transaction = new CouponTransaction(new ReceiptTotals(
                new BigDecimal("1.310"), new BigDecimal("7.280")));
        PurchaseItem item = new PurchaseItem(UUID.randomUUID(), "Illustrative product",
                "0012345", new BigDecimal("20.000"), new BigDecimal("18.480"), 2);
        transaction.addPurchaseItem(item);
        transaction.addCoupon(new Coupon("Illustrative offer", new BigDecimal("9.000"),
                2, CouponType.MANUFACTURER, LocalDate.of(2026, 12, 31)));
        transaction.addAppliedCoupon(new CouponApplied(" MFG COUPON ",
                new BigDecimal("9.000"), List.of(item.getLineItemId())));
        transaction.addPromotion(new Promotion("Illustrative reward offer", PromotionType.BUY,
                2, RewardType.REGISTER_REWARD, 5, false, true));
        transaction.addPayment(new Payment(PaymentType.WALGREENS_CASH, new BigDecimal("10.000")));
        transaction.addPayment(new Payment(PaymentType.CASH, new BigDecimal("7.280")));
        transaction.addReward(new Reward(" Legacy recorded reward ", 5.25, false));
        transaction.addRegisterRewardRedemption(new RegisterRewardRedemption(
                UUID.randomUUID(), receiptId, new BigDecimal("7.000"), LocalDate.of(2026, 10, 16)));
        transaction.addRegisterRewardRedemption(new RegisterRewardRedemption(
                UUID.randomUUID(), receiptId, new BigDecimal("5.000"), null));
        return transaction;
    }

    /** Verifies all categories, identifiers, order, and monetary scales survive. */
    @Test
    void restoresAllRecordedValuesExactly() {
        CouponTransaction original = recordedTransaction();
        ReceiptSnapshot snapshot = ReceiptSnapshot.from(original);
        CouponTransaction restored = snapshot.toTransaction();
        assertNotSame(original, restored);
        assertEquals(snapshot, ReceiptSnapshot.from(restored));
        assertEquals(original.getPurchaseItems().get(0).getLineItemId(),
                restored.getPurchaseItems().get(0).getLineItemId());
        assertEquals(2, restored.getPurchaseItems().get(0).getQuantity());
        assertEquals("0012345", restored.getPurchaseItems().get(0).getProductCode());
        assertEquals(new BigDecimal("18.480"), restored.getPurchaseItems().get(0).getSaleUnitPrice());
        assertEquals(original.getRegisterRewardRedemptions(), restored.getRegisterRewardRedemptions());
    }

    /** Verifies caller additions cannot change stored snapshots or other copies. */
    @Test
    void restorationsHaveIndependentMutableContents() {
        ReceiptSnapshot snapshot = ReceiptSnapshot.from(recordedTransaction());
        CouponTransaction first = snapshot.toTransaction();
        CouponTransaction second = snapshot.toTransaction();
        first.addPurchaseItem(new PurchaseItem("Additional product", "EXTRA",
                BigDecimal.ONE, BigDecimal.ONE, 1));
        first.addPayment(new Payment(PaymentType.CASH, BigDecimal.ONE));
        assertEquals(2, first.getPurchaseItems().size());
        assertEquals(1, second.getPurchaseItems().size());
        assertEquals(3, first.getPayments().size());
        assertEquals(2, second.getPayments().size());
        assertEquals(snapshot, ReceiptSnapshot.from(second));
        assertEquals(snapshot, ReceiptSnapshot.from(snapshot.toTransaction()));
    }

    /** Verifies the unchanged calculator still reports the recorded discrepancy. */
    @Test
    void preservesCheckoutDiscrepancyAndMonetaryScale() {
        CouponTransaction restored = ReceiptSnapshot.from(recordedTransaction()).toTransaction();
        TransactionCalculator calculator = new TransactionCalculator();
        assertEquals(new BigDecimal("7.280"), restored.getReceiptTotals().getFinalTotal());
        assertEquals(new BigDecimal("1.310"), restored.getReceiptTotals().getTaxAmount());
        assertEquals(0, new BigDecimal("7.27").compareTo(
                calculator.calculateExpectedCheckoutAmount(restored)));
        assertEquals(0, new BigDecimal("0.01").compareTo(
                calculator.calculateReceiptDifference(restored)));
    }

    /** Verifies incompatible historical values remain readable after failure. */
    @Test
    void historicalSnapshotRemainsReadableAfterReconstructionFails() {
        UUID receiptId = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();
        PurchaseLineSnap line = new PurchaseLineSnap(UUID.randomUUID(), "  HEB MILK  ",
                " CODE ", new BigDecimal("5.000"), new BigDecimal("5.000"), 1);
        ReceiptSnapshot snapshot = new ReceiptSnapshot(List.of(line), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                new ReceiptTotalsSnap(new BigDecimal("0.000"), new BigDecimal("5.000")));
        ReceiptRevSnap historical = new ReceiptRevSnap(receiptId, 1, ReceiptStatus.ACTIVE,
                snapshot, null, List.of(evidenceId));
        assertThrows(IllegalArgumentException.class, () -> historical.contents().toTransaction());
        assertEquals(receiptId, historical.transactionId());
        assertEquals(1L, historical.revision());
        assertEquals(List.of(evidenceId), historical.evidenceIds());
        assertSame(snapshot, historical.contents());
        assertEquals(line, historical.contents().purchaseItems().get(0));
        assertEquals("  HEB MILK  ", historical.contents().purchaseItems().get(0).productName());
        assertEquals(new BigDecimal("5.000"), historical.contents().receiptTotals().finalTotal());
    }

    /**
     * Verifies compatibility with values permitted by legacy Reward.
     * This does not define valid new rewards or weaken domain validation.
     */
    @Test
    void preservesLegacyRewardValuesWithoutDefiningNewRewardRules() {
        CouponTransaction original = new CouponTransaction(
                new ReceiptTotals(BigDecimal.ZERO, BigDecimal.ZERO));
        original.addReward(new Reward(null, Double.NaN, false));
        original.addReward(new Reward("Recorded correction", -2.0, true));
        ReceiptSnapshot snapshot = ReceiptSnapshot.from(original);
        assertEquals(snapshot, ReceiptSnapshot.from(snapshot.toTransaction()));
    }
}
