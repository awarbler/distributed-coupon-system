package coupons.repository;

import coupons.CouponApplied;
import coupons.CouponTransaction;
import coupons.Coupon;
import coupons.Payment;
import coupons.Promotion;
import coupons.PurchaseItem;
import coupons.ReceiptTotals;
import coupons.Reward;
import coupons.RegisterRewardRedemption;
import coupons.ReceiptObservations;
import java.util.List;
import java.util.Objects;

/**
 * Preserves structured transaction contents for a receipt revision.
 *
 * <p>Collections and their elements are immutable. Original receipt images
 * and imported text require separate preservation. The source transaction
 * must remain stable during capture; concurrent mutation is not supported.
 *
 * @param purchaseItems captured purchase lines
 * @param coupons captured coupon offers
 * @param appliedCoupons actual ordinary coupon deductions
 * @param promotions captured reward-earning terms
 * @param payments captured payments
 * @param rewards captured legacy generic rewards
 * @param registerRewardRedemptions recorded reward redemptions
 * @param receiptTotals captured retailer-reported totals
 * @param observations preserved earnings, account values, and savings relationships
 */
public record ReceiptSnapshot(
        List<PurchaseLineSnap> purchaseItems,
        List<CouponSnap> coupons,
        List<CouponApplied> appliedCoupons,
        List<PromotionSnap> promotions,
        List<PaymentSnap> payments,
        List<RewardSnap> rewards,
        List<RegisterRewardRedemption> registerRewardRedemptions,
        ReceiptTotalsSnap receiptTotals,
        ReceiptObservations observations
) implements java.io.Serializable {
    /** Preserves existing callers that have no additional receipt observations. */
    public ReceiptSnapshot(List<PurchaseLineSnap> purchaseItems, List<CouponSnap> coupons,
            List<CouponApplied> appliedCoupons, List<PromotionSnap> promotions,
            List<PaymentSnap> payments, List<RewardSnap> rewards,
            List<RegisterRewardRedemption> registerRewardRedemptions, ReceiptTotalsSnap receiptTotals) {
        this(purchaseItems, coupons, appliedCoupons, promotions, payments, rewards,
                registerRewardRedemptions, receiptTotals, ReceiptObservations.empty());
    }
    /**
     * Copies collections without recalculating recorded values.
     *
     * @param purchaseItems captured purchase lines
     * @param coupons captured coupon offers
     * @param appliedCoupons actual deductions
     * @param promotions captured promotion terms
     * @param payments captured payments
     * @param rewards captured legacy rewards
     * @param registerRewardRedemptions recorded redemptions
     * @param receiptTotals captured totals
     * @throws NullPointerException if a list, element, or totals is null
     */
    public ReceiptSnapshot {
        purchaseItems = List.copyOf(purchaseItems);
        coupons = List.copyOf(coupons);
        appliedCoupons = List.copyOf(appliedCoupons);
        promotions = List.copyOf(promotions);
        payments = List.copyOf(payments);
        rewards = List.copyOf(rewards);
        registerRewardRedemptions = List.copyOf(registerRewardRedemptions);
        Objects.requireNonNull(receiptTotals, "Receipt totals cannot be null.");
        Objects.requireNonNull(observations, "Receipt observations cannot be null.");
    }

    /**
     * Captures a stable transaction without financial recalculation.
     *
     * @param transaction transaction to capture
     * @return an immutable structured snapshot
     * @throws NullPointerException if transaction is null
     */
    public static ReceiptSnapshot from(CouponTransaction transaction) {
        Objects.requireNonNull(transaction, "Transaction cannot be null.");
        return new ReceiptSnapshot(
                transaction.getPurchaseItems().stream().map(PurchaseLineSnap::from).toList(),
                transaction.getCoupons().stream().map(CouponSnap::from).toList(),
                transaction.getAppliedCoupons(),
                transaction.getPromotions().stream().map(PromotionSnap::from).toList(),
                transaction.getPayments().stream().map(PaymentSnap::from).toList(),
                transaction.getRewards().stream().map(RewardSnap::from).toList(),
                transaction.getRegisterRewardRedemptions(),
                ReceiptTotalsSnap.from(transaction.getReceiptTotals()),
                transaction.getObservations()
        );
    }

    /**
     * Restores recorded facts into a fresh, independently mutable transaction.
     *
     * <p>Immutable applied-coupon and redemption records may safely be shared.
     * Receipt identity, lifecycle, metadata, and evidence remain in the enclosing
     * revision. No totals are calculated, rounded, or reconciled here.
     *
     * <p>Reconstruction is optional. Historical snapshot components remain
     * directly readable even when current constructors reject or normalize
     * recorded values. Failure never modifies this snapshot or its evidence
     * associations. No replacement identifiers are generated.
     *
     * @return a newly reconstructed transaction
     * @throws IllegalArgumentException if recorded values or relationships
     *         cannot be represented unchanged by the current domain model
     */
    public CouponTransaction toTransaction() {
        CouponTransaction restored = new CouponTransaction(new ReceiptTotals(
                receiptTotals.taxAmount(), receiptTotals.finalTotal()));
        for (PurchaseLineSnap line : purchaseItems) {
            restored.addPurchaseItem(new PurchaseItem(line.lineItemId(),
                    line.productName(), line.productCode(), line.regularUnitPrice(),
                    line.saleUnitPrice(), line.quantity()));
        }
        for (CouponSnap coupon : coupons) {
            restored.addCoupon(new Coupon(coupon.description(), coupon.discountAmount(),
                    coupon.requiredQuantity(), coupon.couponType(), coupon.expirationDate()));
        }
        for (CouponApplied applied : appliedCoupons) {
            restored.addAppliedCoupon(applied);
        }
        for (PromotionSnap promotion : promotions) {
            restored.addPromotion(new Promotion(promotion.description(),
                    promotion.promotionType(), promotion.qualificationThreshold(),
                    promotion.rewardType(), promotion.rewardAmount(),
                    promotion.walgreensCashReducesQualifyingSpend(),
                    promotion.couponsReduceQualifyingSpend()));
        }
        for (PaymentSnap payment : payments) {
            restored.addPayment(new Payment(payment.paymentId(), payment.paymentType(), payment.amount(), payment.notes(), payment.accountName()));
        }
        for (RewardSnap reward : rewards) {
            restored.addReward(new Reward(reward.source(), reward.amount(), reward.cashEquivalent()));
        }
        for (RegisterRewardRedemption redemption : registerRewardRedemptions) {
            restored.addRegisterRewardRedemption(redemption);
        }
        restored.setObservations(observations);
        if (!equals(ReceiptSnapshot.from(restored))) {
            throw new IllegalArgumentException(
                    "Recorded snapshot values cannot be restored unchanged.");
        }
        return restored;
    }
}
