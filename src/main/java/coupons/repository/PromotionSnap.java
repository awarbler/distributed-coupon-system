package coupons.repository;

import java.util.Objects;

import coupons.Promotion;
import coupons.PromotionType;
import coupons.RewardType;

/**
 * Preserves the current reward-earning promotion model.
 *
 * <p>This snapshot records offer terms, not proof that a reward
 * was earned. It does not evaluate eligibility.
 *
 * <p>Numeric values retain the existing model's double representation.
 * The qualification threshold represents quantity for BUY promotions
 * and money for SPEND promotions.
 *
 * @param description recorded promotion description
 * @param promotionType quantity or spending qualification type
 * @param qualificationThreshold recorded qualification threshold
 * @param rewardType classification of the offered reward
 * @param rewardAmount recorded reward amount
 * @param walgreensCashReducesQualifyingSpend recorded redemption rule
 * @param couponsReduceQualifyingSpend recorded coupon rule
 */
public record PromotionSnap(
        String description,
        PromotionType promotionType,
        double qualificationThreshold,
        RewardType rewardType,
        double rewardAmount,
        boolean walgreensCashReducesQualifyingSpend,
        boolean couponsReduceQualifyingSpend
) implements java.io.Serializable {

    /**
     * Validates the fields supported by the current promotion model.
     *
     * @param description recorded promotion description
     * @param promotionType quantity or spending qualification type
     * @param qualificationThreshold recorded qualification threshold
     * @param rewardType classification of the offered reward
     * @param rewardAmount recorded reward amount
     * @param walgreensCashReducesQualifyingSpend recorded redemption rule
     * @param couponsReduceQualifyingSpend recorded coupon rule
     * @throws NullPointerException if a required reference is null
     * @throws IllegalArgumentException if description is blank or
     *         numeric values are not finite and positive
     */
    public PromotionSnap {
        Objects.requireNonNull(description, "Description cannot be null.");
        Objects.requireNonNull(
                promotionType,
                "Promotion type cannot be null."
        );
        Objects.requireNonNull(rewardType, "Reward type cannot be null.");

        if (description.isBlank()) {
            throw new IllegalArgumentException(
                    "Description cannot be blank."
            );
        }

        if (!Double.isFinite(qualificationThreshold)
                || qualificationThreshold <= 0.0) {
            throw new IllegalArgumentException(
                    "Qualification threshold must be finite and positive."
            );
        }

        if (!Double.isFinite(rewardAmount) || rewardAmount <= 0.0) {
            throw new IllegalArgumentException(
                    "Reward amount must be finite and positive."
            );
        }
    }

    /**
     * Captures a promotion's existing values without recalculation.
     *
     * @param promotion promotion to capture
     * @return an immutable snapshot
     * @throws NullPointerException if promotion is null
     */
    public static PromotionSnap from(Promotion promotion) {
        Objects.requireNonNull(promotion, "Promotion cannot be null.");

        return new PromotionSnap(
                promotion.getPromotionDescription(),
                promotion.getPromotionType(),
                promotion.getQualificationThreshold(),
                promotion.getRewardType(),
                promotion.getRewardAmount(),
                promotion.doesWalgreensCashReduceQualifyingSpend(),
                promotion.doCouponsReduceQualifyingSpend()
        );
    }
}