package coupons;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents one individually recorded Register Reward redemption. 
 * 
 * RegisterRewardRedemption
 * 
 */
public final class RegisterRewardRedemption {

    private final UUID redemptionId;
    private final UUID rewardId;
    private final UUID redeemedReceiptId;
    private final BigDecimal redeemedAmount;
    private final LocalDate redeemedDate;

    /**
     * Creates a redemption with a generated identifier.
     */
    public RegisterRewardRedemption(
            UUID rewardId,
            UUID redeemedReceiptId,
            BigDecimal redeemedAmount,
            LocalDate redeemedDate) {

        this(
                UUID.randomUUID(),
                rewardId,
                redeemedReceiptId,
                redeemedAmount,
                redeemedDate
        );
    }

    /**
     * Reconstructs or corrects a redemption using its existing ID.
     *
     * @throws NullPointerException if any required ID or amount is null
     * @throws IllegalArgumentException if the amount is not positive
     */
    public RegisterRewardRedemption(
            UUID redemptionId,
            UUID rewardId,
            UUID redeemedReceiptId,
            BigDecimal redeemedAmount,
            LocalDate redeemedDate) {

        this.redemptionId = Objects.requireNonNull(
                redemptionId,
                "Redemption ID cannot be null."
        );

        this.rewardId = Objects.requireNonNull(
                rewardId,
                "Reward ID cannot be null."
        );

        this.redeemedReceiptId = Objects.requireNonNull(
                redeemedReceiptId,
                "Redemption receipt ID cannot be null."
        );

        this.redeemedAmount = Objects.requireNonNull(
                redeemedAmount,
                "Redeemed amount cannot be null."
        );

        if (redeemedAmount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Redeemed amount must be greater than zero."
            );
        }

        this.redeemedDate = redeemedDate;
    }

    public UUID getRedemptionId() {
        return redemptionId;
    }

    public UUID getRewardId() {
        return rewardId;
    }

    public UUID getRedeemedReceiptId() {
        return redeemedReceiptId;
    }

    public BigDecimal getRedeemedAmount() {
        return redeemedAmount;
    }

    public LocalDate getRedeemedDate() {
        return redeemedDate;
    }
}