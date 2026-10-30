package coupons.receipts;

import coupons.promotions.CouponApplied;

import coupons.rewards.RegisterRewardRedemption;
import coupons.rewards.Reward;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests transaction-local Register Reward redemption validation.
 */
class CouponTransactionTest {

    /**
     * Creates an empty transaction for testing.
     *
     * @return a transaction with zero receipt totals
     */
    private CouponTransaction newTransaction() {
        return new CouponTransaction(
                new ReceiptTotals(BigDecimal.ZERO, BigDecimal.ZERO)
        );
    }

    private PurchaseItem newItem() {
        return new PurchaseItem(
                "Scott", "TEST-SCOTT",
                new BigDecimal("5.00"),
                new BigDecimal("5.00"),
                2
        );
    }

    @Test
    void acceptsTwoDiscountsForOneLineContainingTwoUnits() {
        CouponTransaction transaction = newTransaction();
        PurchaseItem item = newItem();
        transaction.addPurchaseItem(item);

        transaction.addAppliedCoupon(new CouponApplied(
                "STR IVC",
                new BigDecimal("2.50"),
                List.of(item.getLineItemId())
        ));

        transaction.addAppliedCoupon(new CouponApplied(
                "MFG COUPON",
                new BigDecimal("2.00"),
                List.of(item.getLineItemId())
        ));

        assertEquals(1, transaction.getPurchaseItems().size());
        assertEquals(2, transaction.getPurchaseItems().get(0).getQuantity());
        assertEquals(2, transaction.getAppliedCoupons().size());
    }

    @Test
    void acceptsUnknownItemAssociation() {
        CouponTransaction transaction = newTransaction();

        transaction.addAppliedCoupon(new CouponApplied(
                "MFG COUPON",
                new BigDecimal("2.00"),
                List.of()
        ));

        assertEquals(1, transaction.getAppliedCoupons().size());
    }

    @Test
    void rejectsUnknownLineWithoutAddingCoupon() {
        CouponTransaction transaction = newTransaction();
        PurchaseItem item = newItem();
        transaction.addPurchaseItem(item);

        CouponApplied applied = new CouponApplied(
                "MFG COUPON",
                new BigDecimal("2.00"),
                List.of(item.getLineItemId(), UUID.randomUUID())
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> transaction.addAppliedCoupon(applied)
        );

        assertTrue(transaction.getAppliedCoupons().isEmpty());
    }

    @Test
    void rejectsDuplicateApplicationId() {
        CouponTransaction transaction = newTransaction();
        UUID id = UUID.randomUUID();

        transaction.addAppliedCoupon(new CouponApplied(
                id,
                "MFG COUPON",
                new BigDecimal("2.00"),
                List.of()
        ));

        assertThrows(
                IllegalArgumentException.class,
                () -> transaction.addAppliedCoupon(new CouponApplied(
                        id,
                        "MFG COUPON",
                        new BigDecimal("3.00"),
                        List.of()
                ))
        );

        assertEquals(1, transaction.getAppliedCoupons().size());
        assertEquals(
                new BigDecimal("2.00"),
                transaction.getAppliedCoupons().get(0).getAppliedAmount()
        );
    }

    @Test
    void rejectsSeparatePurchaseObjectWithDuplicateLineId() {
        CouponTransaction transaction = newTransaction();
        PurchaseItem original = newItem();
        transaction.addPurchaseItem(original);

        PurchaseItem corrected = new PurchaseItem(
                original.getLineItemId(),
                original.getProductName(),
                original.getProductCode(),
                original.getRegularUnitPrice(),
                new BigDecimal("4.75"),
                original.getQuantity()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> transaction.addPurchaseItem(corrected)
        );

        assertEquals(1, transaction.getPurchaseItems().size());
        assertEquals(
                new BigDecimal("5.00"),
                transaction.getPurchaseItems().get(0).getSaleUnitPrice()
        );
    }

    @Test
    void rejectsNullAppliedCoupon() {
        CouponTransaction transaction = newTransaction();

        assertThrows(
                NullPointerException.class,
                () -> transaction.addAppliedCoupon(null)
        );
    }

    @Test
    void returnedAppliedCouponListCannotBeModified() {
        CouponTransaction transaction = newTransaction();

        transaction.addAppliedCoupon(new CouponApplied(
                "MFG COUPON",
                new BigDecimal("2.00"),
                List.of()
        ));

        assertThrows(
                UnsupportedOperationException.class,
                () -> transaction.getAppliedCoupons().clear()
        );

        assertEquals(1, transaction.getAppliedCoupons().size());
    }

    /**
     * Verifies that distinct rewards can be recorded on one receipt.
     */
    @Test
    void acceptsDistinctRewards() {
        CouponTransaction transaction = newTransaction();
        UUID receiptId = UUID.randomUUID();

        RegisterRewardRedemption first = new RegisterRewardRedemption(
                UUID.randomUUID(),
                receiptId,
                new BigDecimal("7.00"),
                null
        );

        RegisterRewardRedemption second = new RegisterRewardRedemption(
                UUID.randomUUID(),
                receiptId,
                new BigDecimal("5.00"),
                null
        );

        transaction.addRegisterRewardRedemption(first);
        transaction.addRegisterRewardRedemption(second);

        assertEquals(
                List.of(first, second),
                transaction.getRegisterRewardRedemptions()
        );
    }

    /**
     * Verifies that null records are rejected without mutation.
     */
    @Test
    void rejectsNullRedemption() {
        CouponTransaction transaction = newTransaction();

        assertThrows(
                NullPointerException.class,
                () -> transaction.addRegisterRewardRedemption(null)
        );

        assertTrue(transaction.getRegisterRewardRedemptions().isEmpty());
    }

    /**
     * Verifies redemption-ID uniqueness even for different rewards.
     */
    @Test
    void rejectsDuplicateRedemptionIdWithoutChangingContents() {
        CouponTransaction transaction = newTransaction();
        UUID receiptId = UUID.randomUUID();
        UUID redemptionId = UUID.randomUUID();

        RegisterRewardRedemption original = new RegisterRewardRedemption(
                redemptionId,
                UUID.randomUUID(),
                receiptId,
                new BigDecimal("5.00"),
                null
        );

        RegisterRewardRedemption duplicate = new RegisterRewardRedemption(
                redemptionId,
                UUID.randomUUID(),
                receiptId,
                new BigDecimal("7.00"),
                null
        );

        transaction.addRegisterRewardRedemption(original);

        List<RegisterRewardRedemption> before
                = transaction.getRegisterRewardRedemptions();

        assertThrows(
                IllegalArgumentException.class,
                () -> transaction.addRegisterRewardRedemption(duplicate)
        );

        assertEquals(before, transaction.getRegisterRewardRedemptions());
    }

    /**
     * Verifies that different redemption IDs cannot reuse one reward.
     */
    @Test
    void rejectsRepeatedRewardIdWithoutChangingContents() {
        CouponTransaction transaction = newTransaction();
        UUID receiptId = UUID.randomUUID();
        UUID rewardId = UUID.randomUUID();

        RegisterRewardRedemption original = new RegisterRewardRedemption(
                rewardId,
                receiptId,
                new BigDecimal("5.00"),
                null
        );

        RegisterRewardRedemption duplicate = new RegisterRewardRedemption(
                rewardId,
                receiptId,
                new BigDecimal("5.00"),
                null
        );

        transaction.addRegisterRewardRedemption(original);

        List<RegisterRewardRedemption> before
                = transaction.getRegisterRewardRedemptions();

        assertThrows(
                IllegalArgumentException.class,
                () -> transaction.addRegisterRewardRedemption(duplicate)
        );

        assertEquals(before, transaction.getRegisterRewardRedemptions());
    }

    /**
     * Verifies that returned lists are unmodifiable snapshots.
     */
    @Test
    void returnsUnmodifiableIndependentList() {
        CouponTransaction transaction = newTransaction();
        UUID receiptId = UUID.randomUUID();

        RegisterRewardRedemption first = new RegisterRewardRedemption(
                UUID.randomUUID(),
                receiptId,
                new BigDecimal("5.00"),
                null
        );

        transaction.addRegisterRewardRedemption(first);

        List<RegisterRewardRedemption> snapshot
                = transaction.getRegisterRewardRedemptions();

        assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.clear()
        );

        transaction.addRegisterRewardRedemption(
                new RegisterRewardRedemption(
                        UUID.randomUUID(),
                        receiptId,
                        new BigDecimal("7.00"),
                        null
                )
        );

        assertEquals(List.of(first), snapshot);
        assertEquals(2, transaction.getRegisterRewardRedemptions().size());
    }

}
