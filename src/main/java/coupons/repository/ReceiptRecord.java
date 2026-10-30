package coupons.repository;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import coupons.receipts.CouponTransaction;

/**
 * Represents a receipt's current identity, state, revision, and data.
 *
 * <p>This record does not represent a history entry or preserve
 * original receipt source data.
 *
 * <p>The transaction remains mutable. Repository implementations
 * must isolate stored data from supplied and returned objects.
 *
 * @param transactionId stable receipt identifier
 * @param status current lifecycle status
 * @param revision current revision
 * @param transaction current transaction contents
 * @param metadata recorded identification details, null when unknown
 * @param evidenceIds associated evidence IDs; empty when none are recorded
 */
public record ReceiptRecord(
        UUID transactionId,
        ReceiptStatus status,
        long revision,
        CouponTransaction transaction,
        ReceiptMeta metadata,
        List<UUID> evidenceIds
) {

    /**
     * Creates a receipt with unknown identification metadata.
     *
     * @param transactionId stable receipt identifier
     * @param status current lifecycle status
     * @param revision current revision
     * @param transaction current transaction contents
     * @throws NullPointerException if a required reference is null
     * @throws IllegalArgumentException if revision is less than one
     */
    public ReceiptRecord(UUID transactionId, ReceiptStatus status,
            long revision, CouponTransaction transaction) {
        this(transactionId, status, revision, transaction, null);
    }

    /**
     * Creates a receipt with metadata and no recorded evidence attachments.
     *
     * @param transactionId stable receipt identifier
     * @param status current lifecycle status
     * @param revision current revision
     * @param transaction current transaction contents
     * @param metadata identification details, null when unknown
     * @throws NullPointerException if a required reference is null
     * @throws IllegalArgumentException if revision is less than one
     */
    public ReceiptRecord(UUID transactionId, ReceiptStatus status,
            long revision, CouponTransaction transaction, ReceiptMeta metadata) {
        this(transactionId, status, revision, transaction, metadata, List.of());
    }

    /**
     * Validates the receipt's required fields and revision.
     *
     * @param transactionId stable receipt identifier
     * @param status current lifecycle status
     * @param revision current revision
     * @param transaction current transaction contents
     * @param metadata recorded identification details, null when unknown
     * @param evidenceIds associated evidence IDs; empty when none are recorded
     * @throws NullPointerException if a required field, evidence list, or element is null
     * @throws IllegalArgumentException if revision is less than one or evidence IDs repeat
     */
    public ReceiptRecord {
        Objects.requireNonNull(transactionId, "Transaction ID cannot be null.");
        Objects.requireNonNull(status, "Receipt status cannot be null.");
        Objects.requireNonNull(transaction, "Transaction cannot be null.");
        evidenceIds = List.copyOf(evidenceIds);
        if (new HashSet<>(evidenceIds).size() != evidenceIds.size()) {
            throw new IllegalArgumentException("Duplicate evidence IDs are not allowed.");
        }

        if (revision < 1) {
            throw new IllegalArgumentException(
                    "Receipt revision must be at least 1."
            );
        }
    }
}
