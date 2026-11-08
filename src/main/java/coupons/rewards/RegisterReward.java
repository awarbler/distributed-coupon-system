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
    private final String registerRewardType;
    private final String originProduct,originBrand,originProgram,originPromotion;

    private java.util.List<RegisterReward> previousVersions;
    private String correctedBy;
    private java.time.Instant correctedAt;
    public java.util.List<RegisterReward> getPreviousVersions(){return previousVersions==null?java.util.List.of():java.util.List.copyOf(previousVersions);}
    public String getCorrectedBy(){return correctedBy;}
    public java.time.Instant getCorrectedAt(){return correctedAt;}
    public RegisterReward retainingHistory(RegisterReward previous,String actor){var copy=new RegisterReward(rewardId,sourceDescription,faceValue,earnedDate,expirationDate,earnedReceiptId,registerRewardType,originProduct,originBrand,originProgram,originPromotion);copy.previousVersions=java.util.List.of(previous);copy.correctedBy=actor;copy.correctedAt=java.time.Instant.now();return copy;}

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
        this(rewardId,sourceDescription,faceValue,earnedDate,expirationDate,earnedReceiptId,null);
    }
    public RegisterReward(UUID rewardId,String sourceDescription,BigDecimal faceValue,LocalDate earnedDate,LocalDate expirationDate,UUID earnedReceiptId,String registerRewardType) {this(rewardId,sourceDescription,faceValue,earnedDate,expirationDate,earnedReceiptId,registerRewardType,null,null,null,null);}
    public RegisterReward(UUID rewardId,String sourceDescription,BigDecimal faceValue,LocalDate earnedDate,LocalDate expirationDate,UUID earnedReceiptId,String registerRewardType,String originProduct,String originBrand,String originProgram,String originPromotion) {
        this.originProduct=originProduct;this.originBrand=originBrand;this.originProgram=originProgram;this.originPromotion=originPromotion;
        if(registerRewardType!=null&&!java.util.Set.of("MANUFACTURER","STORE","UNKNOWN").contains(registerRewardType))throw new IllegalArgumentException("Unknown Register Reward type.");
        this.registerRewardType=registerRewardType;
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

    public String getOriginProduct(){return originProduct;}
    public String getOriginBrand(){return originBrand;}
    public String getOriginProgram(){return originProgram;}
    public String getOriginPromotion(){return originPromotion;}
    public String getRegisterRewardType() { return registerRewardType; }

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