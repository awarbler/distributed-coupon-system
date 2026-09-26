package edu.utexas.ece382n.coupons; // Declare the package that contains the coupon-system classes.

import java.util.ArrayList; // Provide a resizable collection for storing rewards associated with a transaction.
import java.util.List; // Provide the List interface used to represent a collection of rewards.

/**
 * Represents a single coupon transaction and the rewards associated with it.
 *
 * <p>
 * A transaction stores the item's original price, sale price, coupon amount,
 * and a collection of rewards or rebates associated with the purchase.</p>
 *
 * <p>
 * The transaction can calculate checkout savings, cash-equivalent rebates,
 * non-cash rewards, net cash cost, and effective net cost.</p>
 *
 * <p>
 * This class begins as a local data model and will be extended as
 * distributed-systems concepts are introduced in ECE 382N.</p>
 */
public class CouponTransaction { // Represent one coupon transaction and its associated rewards.

    private final double originalPrice; // Store the item's price before sales or discounts.
    private final double salePrice; // Store the item's sale price before applying the coupon.
    private final double couponAmount; // Store the coupon amount applied at checkout.
    private final List<Reward> rewards; // Store the rewards and rebates associated with this transaction.

    /**
     * Constructs a coupon transaction with its purchase prices and coupon.
     *
     * @param originalPrice the item's price before sales or discounts
     * @param salePrice the item's sale price before applying the coupon
     * @param couponAmount the coupon amount applied at checkout
     */
    public CouponTransaction( // Construct a coupon transaction with an initially empty reward collection.
            double originalPrice, // Receive the item's original price.
            double salePrice, // Receive the item's sale price.
            double couponAmount // Receive the coupon amount applied at checkout.
    ) { // Begin the constructor.
        this.originalPrice = originalPrice; // Store the supplied original price.
        this.salePrice = salePrice; // Store the supplied sale price.
        this.couponAmount = couponAmount; // Store the supplied coupon amount.
        this.rewards = new ArrayList<>(); // Create an empty collection for this transaction's rewards.
    } // End the constructor.

    /**
     * Adds a reward or rebate to this transaction.
     *
     * @param reward the reward or rebate to associate with this transaction
     */
    public void addReward(Reward reward) { // Associate one Reward object with this transaction.
        rewards.add(reward); // Add the supplied reward to this transaction's reward collection.
    } // End the addReward method.

    /**
     * Calculates the amount paid at checkout after applying the coupon.
     *
     * @return the sale price minus the coupon amount
     */
    public double calculateOutOfPocket() { // Calculate the amount paid at checkout.
        return salePrice - couponAmount; // Subtract the coupon amount from the sale price.
    } // End the calculateOutOfPocket method.

    /**
     * Calculates the savings realized at checkout.
     *
     * <p>
     * This value compares the original price with the amount paid at checkout
     * and does not include later rebates or rewards earned.</p>
     *
     * @return the original price minus the checkout cost
     */
    public double calculateCheckoutSavings() { // Calculate savings already realized at checkout.
        return originalPrice - calculateOutOfPocket(); // Compare the original price with the checkout payment.
    } // End the calculateCheckoutSavings method.

    /**
     * Calculates the total value of cash-equivalent rewards and rebates.
     *
     * @return the total cash-equivalent reward value
     */
    public double calculateCashRebates() { // Calculate the combined value of all cash-equivalent rewards.
        double total = 0.0; // Begin with no cash-equivalent reward value counted.

        for (Reward reward : rewards) { // Examine each Reward object associated with this transaction.
            if (reward.isCashEquivalent()) { // Determine whether this reward should reduce net cash cost.
                total += reward.getAmount(); // Add this cash-equivalent reward's amount to the running total.
            } // End the cash-equivalent check.
        } // End the traversal of the reward collection.

        return total; // Return the combined cash-equivalent reward value.
    } // End the calculateCashRebates method.

    /**
     * Calculates the total value of non-cash rewards earned.
     *
     * @return the total non-cash reward value
     */
    public double calculateNonCashRewards() { // Calculate the combined value of all non-cash rewards.
        double total = 0.0; // Begin with no non-cash reward value counted.

        for (Reward reward : rewards) { // Examine each Reward object associated with this transaction.
            if (!reward.isCashEquivalent()) { // Determine whether this reward represents non-cash value.
                total += reward.getAmount(); // Add this non-cash reward's amount to the running total.
            } // End the non-cash classification check.
        } // End the traversal of the reward collection.

        return total; // Return the combined non-cash reward value.
    } // End the calculateNonCashRewards method.

    /**
     * Calculates the net cash cost after all cash-equivalent rewards and
     * rebates are received.
     *
     * @return the checkout cost minus all cash-equivalent reward value
     */
    public double calculateNetCashCost() { // Calculate the cash cost after receiving cash-equivalent rewards.
        return calculateOutOfPocket() - calculateCashRebates(); // Subtract cash-equivalent rewards from checkout cost.
    } // End the calculateNetCashCost method.

    /**
     * Calculates the effective net cost after both cash-equivalent and non-cash
     * rewards are considered.
     *
     * <p>
     * Non-cash rewards are treated as value received from the transaction, even
     * though they do not necessarily reduce the amount of cash paid at
     * checkout.</p>
     *
     * @return the net cash cost minus the value of non-cash rewards
     */
    public double calculateEffectiveNetCost() { // Calculate cost after accounting for both cash and non-cash value.
        return calculateNetCashCost() - calculateNonCashRewards(); // Subtract non-cash rewards from the net cash cost.
    } // End the calculateEffectiveNetCost method.

    /**
     * Returns an unmodifiable copy of the rewards associated with this
     * transaction.
     *
     * @return the rewards associated with this transaction
     */
    public List<Reward> getRewards() { // Provide read-only access to the transaction's rewards.
        return List.copyOf(rewards); // Return a copy that callers cannot modify directly.
    } // End the getRewards method.

} // End the CouponTransaction class.
