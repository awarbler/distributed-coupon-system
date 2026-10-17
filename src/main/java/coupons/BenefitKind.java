package coupons;

/**
 * Identifies the recorded form of a rebate benefit.
 *
 * <p>These classifications do not establish equivalent practical value
 * or confirm that a benefit has been received.
 */
public enum BenefitKind {
    /** Monetary benefit denominated in a currency. */
    CASH,
    /** Points denominated in a named program's units. */
    POINTS,
    /** Gift-card face value denominated in a currency. */
    GIFT_CARD,
    /** Restricted coupon face value, including a Register Reward. */
    STORE_COUPON,
    /** Restricted store reward value, including Walgreens Cash; not cash. */
    STORE_REWARD
}
