package coupons;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents the data associated with one coupon transaction.
 */
public class CouponTransaction {

    private final List<PurchaseItem> purchaseItems; // Store the products purchased in this transaction.
    private final List<Coupon> coupons; // Store the coupons applied to this transaction.
    private final List<Promotion> promotions; // Store promotions associated with this transaction.
    private final List<Payment> payments; // Store payments and reward redemptions used in this transaction.
    private final List<Reward> rewards; // Store rewards earned from this transaction.
    private final ReceiptTotals receiptTotals; // Store the final totals reported by the retailer.

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
    }

    /**
     * Adds a purchased product to this transaction.
     *
     * @param item purchased product to add
     * @throws NullPointerException if item is null
     */
    public void addPurchaseItem(PurchaseItem item) {
        purchaseItems.add(
                Objects.requireNonNull(
                        item,
                        "Purchase item cannot be null."
                )
        );
    }

    /**
     * Adds a coupon to this transaction.
     *
     * @param coupon coupon to add
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