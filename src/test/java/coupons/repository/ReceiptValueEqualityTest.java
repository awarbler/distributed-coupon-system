package coupons.repository;

import coupons.CouponApplied;
import coupons.RegisterRewardRedemption;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Tests value comparison of reconstructed receipt deductions. */
class ReceiptValueEqualityTest {

    /** Verifies coupon equality and hashing after reconstruction. */
    @Test
    void reconstructedCouponsAreEqual() {
        UUID id = UUID.randomUUID();
        List<UUID> lines = List.of(UUID.randomUUID());
        CouponApplied original = new CouponApplied(id, "MFG", new BigDecimal("2.00"), lines);
        CouponApplied copy = new CouponApplied(id, "MFG", new BigDecimal("2.00"), lines);
        assertNotSame(original, copy);
        assertEquals(original, copy);
        assertEquals(copy, original);
        assertEquals(original.hashCode(), copy.hashCode());
        assertNotEquals(original, null);
        assertNotEquals(original, "MFG");
    }

    /** Verifies each coupon field participates in equality without normalization. */
    @Test
    void changedCouponFieldsAreNotEqual() {
        UUID id = UUID.randomUUID();
        List<UUID> lines = List.of(UUID.randomUUID(), UUID.randomUUID());
        CouponApplied original = new CouponApplied(id, "MFG", new BigDecimal("2.00"), lines);
        List<CouponApplied> different = List.of(
                new CouponApplied(UUID.randomUUID(), "MFG", new BigDecimal("2.00"), lines),
                new CouponApplied(id, " MFG ", new BigDecimal("2.00"), lines),
                new CouponApplied(id, "MFG", new BigDecimal("3.00"), lines),
                new CouponApplied(id, "MFG", new BigDecimal("2.0"), lines),
                new CouponApplied(id, "MFG", new BigDecimal("2.00"), List.of()),
                new CouponApplied(id, "MFG", new BigDecimal("2.00"), List.of(lines.get(1), lines.get(0)))
        );
        for (CouponApplied changed : different) {
            assertNotEquals(original, changed);
        }
    }

    /** Verifies redemption equality and hashing with unknown and known dates. */
    @Test
    void reconstructedRedemptionsAreEqual() {
        UUID id = UUID.randomUUID();
        UUID rewardId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        for (LocalDate date : new LocalDate[]{null, LocalDate.of(2026, 10, 16)}) {
            RegisterRewardRedemption original = new RegisterRewardRedemption(
                    id, rewardId, receiptId, new BigDecimal("5.00"), date);
            RegisterRewardRedemption copy = new RegisterRewardRedemption(
                    id, rewardId, receiptId, new BigDecimal("5.00"), date);
            assertNotSame(original, copy);
            assertEquals(original, copy);
            assertEquals(copy, original);
            assertEquals(original.hashCode(), copy.hashCode());
            assertNotEquals(original, null);
            assertNotEquals(original, "redemption");
        }
    }

    /** Verifies each redemption field participates in equality. */
    @Test
    void changedRedemptionFieldsAreNotEqual() {
        UUID id = UUID.randomUUID();
        UUID rewardId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("5.00");
        RegisterRewardRedemption original = new RegisterRewardRedemption(id, rewardId, receiptId, amount, null);
        List<RegisterRewardRedemption> different = List.of(
                new RegisterRewardRedemption(UUID.randomUUID(), rewardId, receiptId, amount, null),
                new RegisterRewardRedemption(id, UUID.randomUUID(), receiptId, amount, null),
                new RegisterRewardRedemption(id, rewardId, UUID.randomUUID(), amount, null),
                new RegisterRewardRedemption(id, rewardId, receiptId, new BigDecimal("4.50"), null),
                new RegisterRewardRedemption(id, rewardId, receiptId, new BigDecimal("5.0"), null),
                new RegisterRewardRedemption(id, rewardId, receiptId, amount, LocalDate.of(2026, 10, 16))
        );
        for (RegisterRewardRedemption changed : different) {
            assertNotEquals(original, changed);
        }
    }

    /**
     * Builds a snapshot with supplied immutable deductions.
     *
     * @param coupon actual coupon deduction
     * @param redemption recorded reward redemption
     * @return an example snapshot
     */
    private ReceiptSnapshot snapshot(CouponApplied coupon, RegisterRewardRedemption redemption) {
        return new ReceiptSnapshot(List.of(), List.of(), List.of(coupon),
                List.of(), List.of(), List.of(), List.of(redemption),
                new ReceiptTotalsSnap(BigDecimal.ZERO, new BigDecimal("7.28")));
    }

    /** Verifies complete snapshots compare by values after reconstruction. */
    @Test
    void reconstructedSnapshotsAreEqualAndChangedValuesAreDetected() {
        UUID couponId = UUID.randomUUID();
        UUID redemptionId = UUID.randomUUID();
        UUID rewardId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        CouponApplied originalCoupon = new CouponApplied(couponId, "MFG", new BigDecimal("2.00"), List.of());
        RegisterRewardRedemption originalRedemption = new RegisterRewardRedemption(
                redemptionId, rewardId, receiptId, new BigDecimal("5.00"), null);
        ReceiptSnapshot original = snapshot(originalCoupon, originalRedemption);
        ReceiptSnapshot copy = snapshot(
                new CouponApplied(couponId, "MFG", new BigDecimal("2.00"), List.of()),
                new RegisterRewardRedemption(redemptionId, rewardId, receiptId, new BigDecimal("5.00"), null));
        assertEquals(original, copy);
        assertEquals(original.hashCode(), copy.hashCode());
        assertNotEquals(original, snapshot(
                new CouponApplied(couponId, "MFG", new BigDecimal("3.00"), List.of()), originalRedemption));
        assertNotEquals(original, snapshot(originalCoupon,
                new RegisterRewardRedemption(redemptionId, rewardId, receiptId, new BigDecimal("4.00"), null)));
    }
}
