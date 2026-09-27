package coupons; // Place the Coupon class in the coupons package.

import java.util.Objects; // Provide null checking for required object values.

/**
 * Represents a coupon that can be applied to qualifying purchased products.
 */
public class Coupon { // Define the data and rules for one coupon.

    private final String couponDescription; // Store the human-readable description of the coupon.
    private final double discountAmount; // Store the total monetary discount provided by the coupon.
    private final int requiredQuantity; // Store how many qualifying products are required.
    private final CouponType couponType; // Store whether this is a manufacturer or store coupon.

    /**
     * Creates a coupon with its qualification requirements and discount value.
     *
     * @param couponDescription description of the coupon
     * @param discountAmount monetary value of the coupon
     * @param requiredQuantity number of qualifying products required
     * @param couponType type of coupon
     * @throws NullPointerException if a required object is null
     * @throws IllegalArgumentException if the description is blank,
     *         discount is not positive, or quantity is less than one
     */
    public Coupon( // Construct one valid coupon.
            String couponDescription, // Receive the coupon's description.
            double discountAmount, // Receive the coupon's monetary discount.
            int requiredQuantity, // Receive the required product quantity.
            CouponType couponType) { // Receive the coupon classification.

        Objects.requireNonNull( // Reject a missing coupon description immediately.
                couponDescription, // Validate the supplied description.
                "Coupon description cannot be null." // Explain why construction failed.
        );

        if (couponDescription.isBlank()) { // Reject descriptions containing no meaningful text.
            throw new IllegalArgumentException( // Report invalid coupon data to the caller.
                    "Coupon description cannot be blank." // Explain the violated rule.
            );
        }

        if (!Double.isFinite(discountAmount) || discountAmount <= 0.0) { // Reject non-finite or non-positive discount amounts.
            throw new IllegalArgumentException( // Reject an invalid monetary value.
                    "Coupon discount must be a finite positive number." // Explain the violated rule.
            );
        }
        

        if (requiredQuantity < 1) { // Require at least one qualifying product.
            throw new IllegalArgumentException( // Reject an impossible coupon quantity.
                    "Required quantity must be at least 1." // Explain the violated rule.
            );
        }

        this.couponType = Objects.requireNonNull( // Reject a coupon without a classification.
                couponType, // Validate the supplied coupon type.
                "Coupon type cannot be null." // Explain why construction failed.
        );

        this.couponDescription = couponDescription; // Store the validated coupon description.
        this.discountAmount = discountAmount; // Store the validated coupon discount.
        this.requiredQuantity = requiredQuantity; // Store the validated qualification quantity.
    }

    /**
     * Returns the coupon's description.
     *
     * @return coupon description
     */
    public String getCouponDescription() { // Provide read-only access to the description.
        return couponDescription; // Return the stored coupon description.
    }

    /**
     * Returns the coupon's monetary discount.
     *
     * @return coupon discount
     */
    public double getDiscountAmount() { // Provide read-only access to the discount value.
        return discountAmount; // Return the stored coupon discount.
    }

    /**
     * Returns the number of qualifying products required by the coupon.
     *
     * @return required product quantity
     */
    public int getRequiredQuantity() { // Provide read-only access to the quantity requirement.
        return requiredQuantity; // Return the required number of qualifying products.
    }

    /**
     * Returns the coupon classification.
     *
     * @return coupon type
     */
    public CouponType getCouponType() { // Provide read-only access to the coupon type.
        return couponType; // Return the stored manufacturer or store classification.
    }
}