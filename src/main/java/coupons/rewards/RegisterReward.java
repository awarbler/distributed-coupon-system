package coupons.rewards;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents one individually issued Register Reward.
 *
 * <p>Dates and the earning receipt ID may be null when unknown.
 * Source text is preserved exactly as supplied.
 *
 * <p>Redemption and availability are tracked separately.
 */
public final class RegisterReward implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    private final UUID rewardId;
    private final String sourceDescription;
    private final BigDecimal faceValue;
    private final LocalDate earnedDate;
    private final LocalDate expirationDate;
    private final UUID earnedReceiptId;

    /**
     * Creates a newly recorded reward with a generated identifier.
     */
    public RegisterReward(
            String sourceDescription,
            BigDecimal faceValue,
            LocalDate earnedDate,
            LocalDate expirationDate,
            UUID earnedReceiptId) {

        this(
                UUID.randomUUID(),
                sourceDescription,
                faceValue,
                earnedDate,
                expirationDate,
                earnedReceiptId
        );
    }

    /**
     * Reconstructs or corrects a reward using its existing identifier.
     *
     * @throws NullPointerException if reward ID, source, or value is null
     * @throws IllegalArgumentException if source is blank, value is
     *         not positive, or expiration precedes the known earned date
     */
    public RegisterReward(
            UUID rewardId,
            String sourceDescription,
            BigDecimal faceValue,
            LocalDate earnedDate,
            LocalDate expirationDate,
            UUID earnedReceiptId) {

        this.rewardId = Objects.requireNonNull(
                rewardId,
                "Reward ID cannot be null."
        );

        Objects.requireNonNull(
                sourceDescription,
                "Source description cannot be null."
        );

        if (sourceDescription.isBlank()) {
            throw new IllegalArgumentException(
                    "Source description cannot be blank."
            );
        }

        this.faceValue = Objects.requireNonNull(
                faceValue,
                "Face value cannot be null."
        );

        if (faceValue.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Face value must be greater than zero."
            );
        }

        if (earnedDate != null
                && expirationDate != null
                && expirationDate.isBefore(earnedDate)) {
            throw new IllegalArgumentException(
                    "Expiration date cannot precede earned date."
            );
        }

        this.sourceDescription = sourceDescription;
        this.earnedDate = earnedDate;
        this.expirationDate = expirationDate;
        this.earnedReceiptId = earnedReceiptId;
    }

    public UUID getRewardId() {
        return rewardId;
    }

    public String getSourceDescription() {
        return sourceDescription;
    }

    public BigDecimal getFaceValue() {
        return faceValue;
    }

    public LocalDate getEarnedDate() {
        return earnedDate;
    }

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public UUID getEarnedReceiptId() {
        return earnedReceiptId;
    }
}