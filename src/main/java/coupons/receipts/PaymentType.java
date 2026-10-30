package coupons.receipts; // Declare the package containing the coupon-system classes.

import coupons.rewards.Reward;

/**
 * Identifies the form of payment used in a transaction.
 */
public enum PaymentType { // Define the payment types currently supported by the system.

    CASH, // Represent payment made with physical cash.
    CREDIT_CARD, // Represent payment charged to a credit card.
    DEBIT_CARD, // Represent payment charged directly to a debit card.
    GIFT_CARD, // Represent payment made using stored gift-card value.
    WALGREENS_CASH, // Represent payment made by redeeming previously earned Walgreens Cash.
    CARD, // Card tender when credit/debit classification was not recorded.
    OTHER, // Other tender, described by payment notes.
    REGISTER_REWARD // Represent payment made by redeeming a previously earned Register Reward.

}
