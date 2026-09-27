package coupons; // Place the coupon-status enumeration in the coupons package.

/** // Begin the documentation for the coupon lifecycle states.
 * Identifies the observed lifecycle state of a digital coupon. // Explain what this enum represents.
 */ // End the documentation comment.
public enum CouponStatus { // Define the coupon states that the tracker can record.

    AVAILABLE, // The coupon is available but has not been clipped.
    CLIPPED, // The coupon was selected in the retailer application.
    APPLIED, // The coupon was successfully applied to a transaction.
    NOT_APPLIED, // The coupon was clipped but did not apply to the transaction.
    NO_LONGER_AVAILABLE // The coupon was previously observed but later disappeared or became unavailable.

}