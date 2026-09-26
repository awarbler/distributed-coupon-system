package coupons; // Declare the package containing the distributed coupon-system classes.

/**
 * Represents one reward, rebate, refund, or other value associated with a
 * coupon transaction.
 *
 * <p>
 * A reward records its source, monetary amount, and whether it represents
 * cash-equivalent value or non-cash reward value.</p>
 */
public class Reward { // Represent one source of value associated with a transaction.

    private final String source; // Store the name of the reward or rebate source.
    private final double amount; // Store the monetary value of the reward or rebate.
    private final boolean cashEquivalent; // Record whether the value should reduce net cash cost.

    /**
     * Constructs a reward with its source, amount, and value classification.
     *
     * @param source the source that provides the reward or rebate
     * @param amount the monetary value of the reward or rebate
     * @param cashEquivalent {@code true} when the value reduces net cash cost;
     * {@code false} when the value represents non-cash reward value
     */
    public Reward( // Construct one reward or rebate.
            String source, // Receive the name of the reward source.
            double amount, // Receive the monetary value.
            boolean cashEquivalent // Receive the cash-equivalent classification.
    ) { // Begin the Reward constructor.
        this.source = source; // Store the reward source in this object.
        this.amount = amount; // Store the reward amount in this object.
        this.cashEquivalent = cashEquivalent; // Store whether this value is cash-equivalent.
    }

    /**
     * Returns the source of this reward or rebate.
     *
     * @return the reward source
     */
    public String getSource() {
        return source; // Return the source of this reward or rebate.
    }

    /**
     * Returns the monetary value of this reward or rebate.
     *
     * @return the reward amount
     */
    public double getAmount() {
        return amount; // Return the monetary value of this reward or rebate.
    }

    /**
     * Determines whether this reward is cash-equivalent.
     *
     * @return {@code true} if the reward reduces net cash cost; otherwise
     * {@code false}
     */
    public boolean isCashEquivalent() {
        return cashEquivalent; // Return whether this reward is cash-equivalent.
    }

} // End the Reward class.
