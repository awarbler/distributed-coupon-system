package coupons.repository;

import coupons.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Tests capture and isolation of structured receipt snapshots. */
class ReceiptSnapshotTest {
    /**
     * Creates hypothetical data containing every supported category.
     * @return a populated transaction
     */
    private CouponTransaction populatedTransaction() {
        CouponTransaction transaction = new CouponTransaction(new ReceiptTotals(
                new BigDecimal("1.310"), new BigDecimal("7.280")));
        addContents(transaction);
        return transaction;
    }

    /**
     * Adds distinct records in each category.
     * @param transaction transaction receiving the records
     */
    private void addContents(CouponTransaction transaction) {
        PurchaseItem item = new PurchaseItem("Scott", "TEST-SCOTT",
                new BigDecimal("5.00"), new BigDecimal("5.00"), 2);
        transaction.addPurchaseItem(item);
        transaction.addCoupon(new Coupon("$2 off two", new BigDecimal("2.00"),
                2, CouponType.MANUFACTURER, null));
        transaction.addAppliedCoupon(new CouponApplied("MFG COUPON",
                new BigDecimal("2.00"), List.of(item.getLineItemId())));
        transaction.addPromotion(new Promotion("Example reward offer", PromotionType.BUY,
                2, RewardType.REGISTER_REWARD, 5, false, false));
        transaction.addPayment(new Payment(PaymentType.WALGREENS_CASH, new BigDecimal("1.00")));
        transaction.addReward(new Reward("Legacy example", 5.00, false));
        transaction.addRegisterRewardRedemption(new RegisterRewardRedemption(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("3.00"), null));
    }

    /** Verifies exact capture of all supported categories. */
    @Test
    void capturesAllCategories() {
        CouponTransaction transaction = populatedTransaction();
        ReceiptSnapshot snapshot = ReceiptSnapshot.from(transaction);
        assertEquals(PurchaseLineSnap.from(transaction.getPurchaseItems().get(0)), snapshot.purchaseItems().get(0));
        assertEquals(CouponSnap.from(transaction.getCoupons().get(0)), snapshot.coupons().get(0));
        assertEquals(transaction.getAppliedCoupons(), snapshot.appliedCoupons());
        assertEquals(PromotionSnap.from(transaction.getPromotions().get(0)), snapshot.promotions().get(0));
        assertEquals(PaymentSnap.from(transaction.getPayments().get(0)), snapshot.payments().get(0));
        assertEquals(RewardSnap.from(transaction.getRewards().get(0)), snapshot.rewards().get(0));
        assertEquals(transaction.getRegisterRewardRedemptions(), snapshot.registerRewardRedemptions());
        assertEquals(new BigDecimal("1.310"), snapshot.receiptTotals().taxAmount());
        assertEquals(new BigDecimal("7.280"), snapshot.receiptTotals().finalTotal());
    }

    /** Verifies later additions cannot change previously captured contents. */
    @Test
    void laterAdditionsDoNotChangeSnapshot() {
        CouponTransaction transaction = populatedTransaction();
        ReceiptSnapshot before = ReceiptSnapshot.from(transaction);
        addContents(transaction);
        ReceiptSnapshot after = ReceiptSnapshot.from(transaction);
        for (List<?> list : collections(before)) {
            assertEquals(1, list.size());
        }
        for (List<?> list : collections(after)) {
            assertEquals(2, list.size());
        }
    }

    /**
     * Collects the snapshot's lists for shared isolation assertions.
     * @param snapshot snapshot to inspect
     * @return all collection components
     */
    private List<List<?>> collections(ReceiptSnapshot snapshot) {
        return List.of(snapshot.purchaseItems(), snapshot.coupons(), snapshot.appliedCoupons(),
                snapshot.promotions(), snapshot.payments(), snapshot.rewards(), snapshot.registerRewardRedemptions());
    }

    /** Verifies that all exposed lists reject modification. */
    @Test
    void allCollectionsAreUnmodifiable() {
        for (List<?> list : collections(ReceiptSnapshot.from(populatedTransaction()))) {
            assertThrows(UnsupportedOperationException.class, () -> list.clear());
        }
    }

    /** Verifies copying of caller-owned lists passed to the constructor. */
    @Test
    void constructorDefensivelyCopiesInputLists() {
        ReceiptSnapshot source = ReceiptSnapshot.from(populatedTransaction());
        var lines = new ArrayList<>(source.purchaseItems());
        var coupons = new ArrayList<>(source.coupons());
        var applied = new ArrayList<>(source.appliedCoupons());
        var promotions = new ArrayList<>(source.promotions());
        var payments = new ArrayList<>(source.payments());
        var rewards = new ArrayList<>(source.rewards());
        var redemptions = new ArrayList<>(source.registerRewardRedemptions());
        ReceiptSnapshot snapshot = new ReceiptSnapshot(lines, coupons, applied, promotions,
                payments, rewards, redemptions, source.receiptTotals());
        List<List<?>> inputs = List.of(lines, coupons, applied, promotions, payments, rewards, redemptions);
        inputs.forEach(List::clear);
        for (List<?> list : collections(snapshot)) {
            assertEquals(1, list.size());
        }
    }

    /** Verifies rejection of missing source, lists, elements, and totals. */
    @Test
    void rejectsNullInputs() {
        var totals = new ReceiptTotalsSnap(BigDecimal.ZERO, BigDecimal.ZERO);
        assertThrows(NullPointerException.class, () -> ReceiptSnapshot.from(null));
        assertThrows(NullPointerException.class, () -> new ReceiptSnapshot(null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), totals));
        assertThrows(NullPointerException.class, () -> new ReceiptSnapshot(
                Arrays.asList((PurchaseLineSnap) null), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), totals));
        assertThrows(NullPointerException.class, () -> new ReceiptSnapshot(List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null));
    }
}
