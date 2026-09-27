package coupons; // Declare the package containing the coupon-system classes.

/**
 * Identifies the form of value produced by a reward or promotion.
 */
public enum RewardType { // Define the reward types currently represented by the coupon system.

    REGISTER_REWARD, // Represent a Register Reward earned from a qualifying promotion.
    WALGREENS_CASH, // Represent Walgreens Cash earned from a qualifying purchase.
    IBOTTA_REBATE, // Represent cash-back value earned through Ibotta.
    FETCH_POINTS, // Represent points earned through Fetch.
    GIFT_CARD // Represent gift-card value earned or received.

}
