package coupons; // Declare the package containing the coupon-system classes.

/**
 * Identifies how a customer qualifies for a promotion.
 */
public enum PromotionType { // Define the supported promotion qualification types.

    BUY, // Require the customer to purchase a specified quantity of qualifying products.
    SPEND // Require the customer to reach a specified qualifying purchase amount.

}
