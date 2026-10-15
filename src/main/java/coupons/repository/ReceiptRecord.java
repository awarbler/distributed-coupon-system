package coupons.repository;

import java.util.Objects;
import java.util.UUID;

import coupons.CouponTransaction;

/**
 * Represents a receipt's current identity, state, revision, and data.
 *
 * <p>This record does not represent a history entry or preserve
 * original receipt source data.
 *
 * <p>The transaction remains mutable. Repository implementations
 * must isolate stored data from supplied and returned objects.
 */
public record ReceiptRecord(
        UUID transactionId,
        ReceiptStatus status,
        long revision,
        CouponTransaction transaction
) {

    /**
     * Validates the receipt's required fields and revision.
     *
     * @throws NullPointerException if a required field is null
     * @throws IllegalArgumentException if revision is less than 1
     */
    public ReceiptRecord {
        Objects.requireNonNull(transactionId, "Transaction ID cannot be null.");
        Objects.requireNonNull(status, "Receipt status cannot be null.");
        Objects.requireNonNull(transaction, "Transaction cannot be null.");

        if (revision < 1) {
            throw new IllegalArgumentException(
                    "Receipt revision must be at least 1."
            );
        }
    }
}