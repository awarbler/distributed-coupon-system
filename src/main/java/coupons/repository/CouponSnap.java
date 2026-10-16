package coupons.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

import coupons.Coupon;
import coupons.CouponType;

/**
 * Preserves the coupon-offer fields currently supported by Coupon.
 *
 * <p>This snapshot does not represent an actual receipt deduction
 * or add eligibility rules missing from the current model.
 *
 * @param description recorded offer description
 * @param discountAmount advertised discount amount
 * @param requiredQuantity required qualifying quantity
 * @param couponType store or manufacturer classification
 * @param expirationDate stated expiration date, or null if unknown
 */
public record CouponSnap(
        String description,
        BigDecimal discountAmount,
        int requiredQuantity,
        CouponType couponType,
        LocalDate expirationDate
) {

    /**
     * Validates coupon snapshot values without altering them.
     *
     * @param description recorded offer description
     * @param discountAmount advertised discount amount
     * @param requiredQuantity required qualifying quantity
     * @param couponType store or manufacturer classification
     * @param expirationDate stated expiration date, or null if unknown
     * @throws NullPointerException if a required reference is null
     * @throws IllegalArgumentException if description is blank,
     *         discount is not positive, or quantity is less than one
     */
    public CouponSnap {
        Objects.requireNonNull(description, "Description cannot be null.");
        Objects.requireNonNull(discountAmount, "Discount cannot be null.");
        Objects.requireNonNull(couponType, "Coupon type cannot be null.");

        if (description.isBlank()) {
            throw new IllegalArgumentException(
                    "Description cannot be blank."
            );
        }

        if (discountAmount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Discount must be greater than zero."
            );
        }

        if (requiredQuantity < 1) {
            throw new IllegalArgumentException(
                    "Required quantity must be at least 1."
            );
        }
    }

    /**
     * Captures the current values of a coupon offer.
     *
     * @param coupon offer to capture
     * @return an immutable snapshot
     * @throws NullPointerException if coupon is null
     */
    public static CouponSnap from(Coupon coupon) {
        Objects.requireNonNull(coupon, "Coupon cannot be null.");

        return new CouponSnap(
                coupon.getCouponDescription(),
                coupon.getDiscountAmount(),
                coupon.getRequiredQuantity(),
                coupon.getCouponType(),
                coupon.getExpirationDate()
        );
    }
}