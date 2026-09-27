package coupons; // Place the Promotion class in the coupons package.

import java.util.Objects; // Provide null checking for required object values.

/**
 * Represents a promotion that can earn a reward when its requirements are met.
 */
public class Promotion { // Define the data and qualification rules for one promotion.

    private final String promotionDescription; // Store the human-readable promotion description.
    private final PromotionType promotionType; // Store whether the promotion is based on buying or spending.
    private final double qualificationThreshold; // Store the quantity or dollar threshold required to qualify.
    private final RewardType rewardType; // Store the type of reward earned by the promotion.
    private final double rewardAmount; // Store the value of the reward earned.
    private final boolean walgreensCashReducesQualifyingSpend; // Record whether redeemed Walgreens Cash reduces qualifying spend.
    private final boolean couponsReduceQualifyingSpend; // Record whether coupons reduce qualifying spend.

    /**
     * Creates a promotion and its qualification rules.
     *
     * @param promotionDescription description of the promotion
     * @param promotionType BUY or SPEND promotion type
     * @param qualificationThreshold quantity or spending threshold required
     * @param rewardType type of reward earned
     * @param rewardAmount value of the reward earned
     * @param walgreensCashReducesQualifyingSpend whether Walgreens Cash
     *        redemption reduces the amount counted toward qualification
     * @param couponsReduceQualifyingSpend whether coupons reduce the amount
     *        counted toward qualification
     * @throws NullPointerException if a required object is null
     * @throws IllegalArgumentException if text is blank or a numeric value
     *         is not greater than zero
     */
    public Promotion( // Create one valid promotion.
            String promotionDescription, // Receive the promotion's description.
            PromotionType promotionType, // Receive the BUY or SPEND classification.
            double qualificationThreshold, // Receive the amount required to qualify.
            RewardType rewardType, // Receive the type of reward earned.
            double rewardAmount, // Receive the reward's monetary value.
            boolean walgreensCashReducesQualifyingSpend, // Receive the Walgreens Cash qualification rule.
            boolean couponsReduceQualifyingSpend) { // Receive the coupon qualification rule.

        Objects.requireNonNull( // Reject a missing promotion description.
                promotionDescription, // Validate the supplied description.
                "Promotion description cannot be null." // Explain why construction failed.
        );

        if (promotionDescription.isBlank()) { // Reject a description containing no meaningful text.
            throw new IllegalArgumentException( // Prevent an invalid promotion from being created.
                    "Promotion description cannot be blank." // Explain the violated rule.
            );
        }

        this.promotionType = Objects.requireNonNull( // Require every promotion to have a known type.
                promotionType, // Validate the supplied promotion type.
                "Promotion type cannot be null." // Explain why construction failed.
        );

        if (!Double.isFinite(qualificationThreshold) || qualificationThreshold <= 0.0) { // Require a finite positive qualification threshold.
            throw new IllegalArgumentException( // Reject an invalid qualification threshold.
                    "Qualification threshold must be a finite positive number." // Explain the violated rule.
            );
        }
        this.rewardType = Objects.requireNonNull( // Require every promotion to identify its reward type.
                rewardType, // Validate the supplied reward type.
                "Reward type cannot be null." // Explain why construction failed.
        );

        if (!Double.isFinite(rewardAmount) || rewardAmount <= 0.0) { // Require a finite positive reward value.
            throw new IllegalArgumentException( // Reject an invalid reward amount.
                    "Reward amount must be a finite positive number." // Explain the violated rule.
            );
        }

        this.promotionDescription = promotionDescription.trim(); // Store the validated description without surrounding whitespace.
        this.qualificationThreshold = qualificationThreshold; // Store the validated qualification threshold.
        this.rewardAmount = rewardAmount; // Store the validated reward value.
        this.walgreensCashReducesQualifyingSpend = walgreensCashReducesQualifyingSpend; // Store the Walgreens Cash qualification rule.
        this.couponsReduceQualifyingSpend = couponsReduceQualifyingSpend; // Store the coupon qualification rule.
    }

    /**
     * Returns the promotion's description.
     *
     * @return promotion description
     */
    public String getPromotionDescription() { // Provide read-only access to the promotion description.
        return promotionDescription; // Return the stored promotion description.
    }

    /**
     * Returns whether this is a BUY or SPEND promotion.
     *
     * @return promotion type
     */
    public PromotionType getPromotionType() { // Provide read-only access to the promotion type.
        return promotionType; // Return the stored BUY or SPEND classification.
    }

    /**
     * Returns the threshold required to qualify for the promotion.
     *
     * @return qualification threshold
     */
    public double getQualificationThreshold() { // Provide read-only access to the qualification threshold.
        return qualificationThreshold; // Return the stored qualification threshold.
    }

    /**
     * Returns the type of reward earned by the promotion.
     *
     * @return reward type
     */
    public RewardType getRewardType() { // Provide read-only access to the reward type.
        return rewardType; // Return the stored reward classification.
    }

    /**
     * Returns the value of the reward earned by the promotion.
     *
     * @return reward amount
     */
    public double getRewardAmount() { // Provide read-only access to the reward amount.
        return rewardAmount; // Return the stored reward value.
    }

    /**
     * Reports whether redeemed Walgreens Cash reduces qualifying spend.
     *
     * @return true when Walgreens Cash must be subtracted from qualifying spend
     */
    public boolean doesWalgreensCashReduceQualifyingSpend() { // Expose the Walgreens Cash qualification rule.
        return walgreensCashReducesQualifyingSpend; // Return whether Walgreens Cash reduces qualifying spend.
    }

    /**
     * Reports whether coupons reduce qualifying spend.
     *
     * @return true when coupons must be subtracted from qualifying spend
     */
    public boolean doCouponsReduceQualifyingSpend() { // Expose the coupon qualification rule.
        return couponsReduceQualifyingSpend; // Return whether coupons reduce qualifying spend.
    }
}