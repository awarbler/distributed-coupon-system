package coupons.repository;

import java.util.Objects;

import coupons.RegisterRewardRedemption;

/**
 * Validates relationships between a receipt and its contents.
 *
 * <p>Repository implementations must validate before saving or updating.
 * This validator does not verify reward existence or prevent redemption
 * across different receipts.
 */
public final class ReceiptValidator {

    /**
     * Prevents construction of this utility class.
     */
    private ReceiptValidator() {
    }

    /**
     * Verifies that every Register Reward redemption references
     * the enclosing receipt.
     *
     * <p>Validation does not modify the receipt. Call again before
     * persistence because transaction contents remain mutable.
     *
     * @param receipt receipt to validate
     * @throws NullPointerException if receipt is null
     * @throws IllegalArgumentException if a redemption references
     *         a different receipt
     */
    public static void validate(ReceiptRecord receipt) {
        Objects.requireNonNull(receipt, "Receipt cannot be null.");
        ReceiptObservationValidator.validate(
                ReceiptSnapshot.from(receipt.transaction()), receipt.evidenceIds());

        for (RegisterRewardRedemption redemption
                : receipt.transaction().getRegisterRewardRedemptions()) {

            if (!receipt.transactionId()
                    .equals(redemption.getRedeemedReceiptId())) {
                throw new IllegalArgumentException(
                        "Register Reward redemption "
                                + redemption.getRedemptionId()
                                + " references a different receipt."
                );
            }
        }
    }
}
