package coupons.receipts;

import coupons.promotions.Coupon;
import coupons.promotions.CouponApplied;
import coupons.promotions.Promotion;

import coupons.rewards.RegisterRewardRedemption;
import coupons.rewards.Reward;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents the contents of one coupon transaction.
 *
 * <p>
 * Receipt identity, status, and revision belong to ReceiptRecord. Receipt-ID
 * consistency and cross-receipt reward redemption rules must be enforced by the
 * receipt-aware service/repository.
 */
public class CouponTransaction {

    /** Immutable additional receipt facts; summaries do not create financial events. */
    private ReceiptObservations observations = ReceiptObservations.empty();

    /** Returns immutable recorded facts and confirmed relationships. */
    public ReceiptObservations getObservations() {
        return observations;
    }

    /** Replaces observations on this working transaction only. */
    public void setObservations(ReceiptObservations observations) {
        this.observations = Objects.requireNonNull(observations);
    }

    /**
     * Purchase lines belonging to this transaction.
     */
    private final List<PurchaseItem> purchaseItems;

    /**
     * Coupon offers and terms associated with this transaction.
     */
    private final List<Coupon> coupons;

    /**
     * Reward-earning promotions associated with this transaction.
     */
    private final List<Promotion> promotions;

    /**
     * Payments, including legacy Register Reward payment entries.
     */
    private final List<Payment> payments;

    /**
     * Generic earned-value records pending reward-model migration.
     */
    private final List<Reward> rewards;

    /**
     * Totals reported by the retailer.
     */
    private final ReceiptTotals receiptTotals;

    /**
     * Actual ordinary coupon deductions recorded on the receipt.
     */
    private final List<CouponApplied> appliedCoupons;

    /**
     * Specific Register Reward redemptions recorded for this transaction.
     */
    private final List<RegisterRewardRedemption> registerRewardRedemptions;

    /**
     * Creates an empty transaction with its retailer-reported receipt totals.
     *
     * @param receiptTotals totals reported on the retailer's receipt
     * @throws NullPointerException if receiptTotals is null
     */
    public CouponTransaction(ReceiptTotals receiptTotals) {
        this.receiptTotals = Objects.requireNonNull(
                receiptTotals,
                "Receipt totals cannot be null."
        );

        this.purchaseItems = new ArrayList<>();
        this.coupons = new ArrayList<>();
        this.promotions = new ArrayList<>();
        this.payments = new ArrayList<>();
        this.rewards = new ArrayList<>();
        this.appliedCoupons = new ArrayList<>();
        this.registerRewardRedemptions = new ArrayList<>();
    }

    /**
     * Adds a receipt line whose ID is unique within this transaction.
     *
     * @throws NullPointerException if item is null
     * @throws IllegalArgumentException if the line ID already exists
     */
    public void addPurchaseItem(PurchaseItem item) {
        Objects.requireNonNull(item, "Purchase item cannot be null.");

        boolean duplicateId = purchaseItems.stream()
                .anyMatch(existing
                        -> existing.getLineItemId().equals(item.getLineItemId()));

        if (duplicateId) {
            throw new IllegalArgumentException(
                    "Receipt line ID already exists in this transaction."
            );
        }

        purchaseItems.add(item);
    }

    /**
     * Adds coupon offer information associated with this transaction.
     *
     * @param coupon coupon offer to add
     * @throws NullPointerException if coupon is null
     */
    public void addCoupon(Coupon coupon) {
        coupons.add(
                Objects.requireNonNull(
                        coupon,
                        "Coupon cannot be null."
                )
        );
    }

    /**
     * Adds a promotion associated with this transaction.
     *
     * @param promotion promotion to add
     * @throws NullPointerException if promotion is null
     */
    public void addPromotion(Promotion promotion) {
        promotions.add(
                Objects.requireNonNull(
                        promotion,
                        "Promotion cannot be null."
                )
        );
    }

    /**
     * Adds a payment or reward redemption to this transaction.
     *
     * @param payment payment to add
     * @throws NullPointerException if payment is null
     */
    public void addPayment(Payment payment) {
        payments.add(
                Objects.requireNonNull(
                        payment,
                        "Payment cannot be null."
                )
        );
    }

    /**
     * Adds a reward earned from this transaction.
     *
     * @param reward reward to add
     * @throws NullPointerException if reward is null
     */
    public void addReward(Reward reward) {
        rewards.add(
                Objects.requireNonNull(
                        reward,
                        "Reward cannot be null."
                )
        );
    }

    /**
     * Adds an actual coupon deduction.
     *
     * <p>
     * Referenced purchase lines must already exist. An empty association list
     * means the affected lines are unknown.
     *
     * @throws NullPointerException if applied is null
     * @throws IllegalArgumentException if the application ID already exists or
     * a referenced receipt line is missing
     */
    public void addAppliedCoupon(CouponApplied applied) {
        Objects.requireNonNull(applied, "Applied coupon cannot be null.");

        boolean duplicateId = appliedCoupons.stream()
                .anyMatch(existing
                        -> existing.getApplicationId()
                                .equals(applied.getApplicationId()));

        if (duplicateId) {
            throw new IllegalArgumentException(
                    "Coupon application ID already exists in this transaction."
            );
        }

        for (var lineId : applied.getLineItemIds()) {
            boolean exists = purchaseItems.stream()
                    .anyMatch(item -> item.getLineItemId().equals(lineId));

            if (!exists) {
                throw new IllegalArgumentException(
                        "Applied coupon references an unknown receipt line."
                );
            }
        }

        appliedCoupons.add(applied);
    }

    /**
     * Adds the recorded use of a specific Register Reward.
     *
     * <p>
     * Validates redemption-ID and reward-ID uniqueness within this transaction
     * before adding anything.
     *
     * <p>
     * Receipt-ID consistency and cross-receipt redemption checks belong to the
     * receipt-aware service/repository.
     *
     * @param redemption redemption record to add
     * @throws NullPointerException if redemption is null
     * @throws IllegalArgumentException if its redemption ID or reward ID
     * already exists in this transaction's redemptions
     */
    public void addRegisterRewardRedemption(
            RegisterRewardRedemption redemption) {

        Objects.requireNonNull(
                redemption,
                "Register Reward redemption cannot be null."
        );

        boolean duplicateRedemptionId = registerRewardRedemptions.stream()
                .anyMatch(existing
                        -> existing.getRedemptionId()
                                .equals(redemption.getRedemptionId()));

        if (duplicateRedemptionId) {
            throw new IllegalArgumentException(
                    "Redemption ID already exists in this transaction."
            );
        }

        boolean duplicateRewardId = registerRewardRedemptions.stream()
                .anyMatch(existing
                        -> existing.getRewardId()
                                .equals(redemption.getRewardId()));

        if (duplicateRewardId) {
            throw new IllegalArgumentException(
                    "Register Reward is already redeemed in this transaction."
            );
        }

        registerRewardRedemptions.add(redemption);
    }

    /**
     * Returns the recorded Register Reward redemptions.
     *
     * @return an unmodifiable copy of the redemption list
     */
    public List<RegisterRewardRedemption> getRegisterRewardRedemptions() {
        return List.copyOf(registerRewardRedemptions);
    }

    /**
     * Returns an unmodifiable copy of actual coupon deductions.
     */
    public List<CouponApplied> getAppliedCoupons() {
        return List.copyOf(appliedCoupons);
    }

    public List<PurchaseItem> getPurchaseItems() {
        return List.copyOf(purchaseItems);
    }

    public List<Coupon> getCoupons() {
        return List.copyOf(coupons);
    }

    public List<Promotion> getPromotions() {
        return List.copyOf(promotions);
    }

    public List<Payment> getPayments() {
        return List.copyOf(payments);
    }

    public List<Reward> getRewards() {
        return List.copyOf(rewards);
    }

    public ReceiptTotals getReceiptTotals() {
        return receiptTotals;
    }
}
