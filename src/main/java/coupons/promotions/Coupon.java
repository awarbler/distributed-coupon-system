package coupons.promotions; // Place the Coupon class in the coupons package.
import java.math.BigDecimal;
import java.time.LocalDate; // Represent the coupon's stated calendar expiration date.
import java.util.Objects; // Provide null checking for required object values.

/**
 * Represents a coupon that can be applied to qualifying purchased products.
 */
public class Coupon implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    private final CouponTerms terms;
    public CouponTerms getTerms(){return terms;}
    public Coupon(String description,BigDecimal amount,int quantity,CouponType type,LocalDate expiration){
        this(description,amount,quantity,type,expiration,null);
    }

    private final String couponDescription; // Store the human-readable description of the coupon.
    private final BigDecimal discountAmount; // Store the total monetary discount provided by the coupon.
    private final int requiredQuantity; // Store how many qualifying products are required.
    private final CouponType couponType; // Store whether this is a manufacturer or store coupon.
    private final LocalDate expirationDate; // Store the coupon's stated expiration date independently from whether it actually applied.

    /**
     * Creates a coupon with its qualification requirements, discount value,
     * classification, and stated expiration date.
     *
     * @param couponDescription description of the coupon
     * @param discountAmount monetary value of the coupon
     * @param requiredQuantity number of qualifying products required
     * @param couponType type of coupon
     * @param expirationDate stated expiration date of the coupon
     * @throws NullPointerException if couponDescription or couponType is null
     * @throws IllegalArgumentException if the description is blank,
     *         discount is null or not positive, or quantity is less than one
     */
    public Coupon( // Construct one valid coupon.
            String couponDescription, // Receive the coupon's description.
            BigDecimal discountAmount, // Receive the coupon's monetary discount.
            int requiredQuantity, // Receive the required product quantity.
            CouponType couponType, // Receive the coupon classification.
            LocalDate expirationDate, CouponTerms terms) { // Receive the coupon's stated expiration date.



        Objects.requireNonNull( // Reject a missing coupon description immediately.
                couponDescription, // Validate the supplied description.
                "Coupon description cannot be null." // Explain why construction failed.
        );

        if (couponDescription.isBlank()) { // Reject descriptions containing no meaningful text.
            throw new IllegalArgumentException( // Report invalid coupon data to the caller.
                    "Coupon description cannot be blank." // Explain the violated rule.
            );
        }

        if (discountAmount == null && (terms==null || terms.discount()!=CouponTerms.Discount.FREE_ITEM)) { // Reject a missing discount amount.
            throw new IllegalArgumentException( // Report invalid coupon data to the caller.
                    "Coupon discount cannot be null." // Explain the violated rule.
            );
        }
        if (discountAmount != null && discountAmount.signum() <= 0) { // Reject a non-positive discount amount.
            throw new IllegalArgumentException( // Report invalid coupon data to the caller.
                    "Coupon discount must be positive." // Explain the violated rule.
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

        if(terms!=null && terms.discount()==CouponTerms.Discount.PERCENT && discountAmount.compareTo(new BigDecimal("100"))>0)throw new IllegalArgumentException("Percentage exceeds 100.");
        if(terms!=null && terms.startsOn()!=null && expirationDate!=null && expirationDate.isBefore(terms.startsOn()))throw new IllegalArgumentException("Coupon dates are reversed.");
        this.terms=terms;
        this.expirationDate = expirationDate; // Store the stated expiration date, or null when the expiration date is unknown.

        this.couponDescription = couponDescription.trim(); // Store the validated description without surrounding whitespace.
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
    public BigDecimal getDiscountAmount() { // Provide read-only access to the discount value.
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

    /**
     * Returns the coupon's stated expiration date.
     *
     * @return coupon expiration date
     */
    public LocalDate getExpirationDate() { // Provide read-only access to the coupon's stated expiration date.
        return expirationDate; // Return the stored expiration date.
    }

} 
