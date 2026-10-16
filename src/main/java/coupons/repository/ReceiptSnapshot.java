package coupons.repository;

import coupons.CouponApplied;
import coupons.CouponTransaction;
import coupons.RegisterRewardRedemption;
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
 */
public record ReceiptSnapshot(
        List<PurchaseLineSnap> purchaseItems,
        List<CouponSnap> coupons,
        List<CouponApplied> appliedCoupons,
        List<PromotionSnap> promotions,
        List<PaymentSnap> payments,
        List<RewardSnap> rewards,
        List<RegisterRewardRedemption> registerRewardRedemptions,
        ReceiptTotalsSnap receiptTotals
) {
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
                ReceiptTotalsSnap.from(transaction.getReceiptTotals())
        );
    }
}
