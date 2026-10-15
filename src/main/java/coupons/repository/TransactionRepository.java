package coupons.repository;

import java.util.Optional;
import java.util.UUID;

import coupons.CouponTransaction;

/**
 * Defines database-independent storage operations for receipts.
 *
 * <p>
 * A transaction ID remains unchanged throughout a receipt's lifecycle. IDs
 * belonging to soft-deleted receipts cannot be reused.
 *
 * <p>
 * Implementations must preserve original receipt source data and record
 * updates, deletions, and restorations in a change history. Each mutation and
 * its history entry must succeed or fail together.
 *
 * <p>
 * Changing a supplied or retrieved transaction must not silently change the
 * stored receipt. Changes must pass through update().
 *
 * <p>
 * The application service manages user confirmation and approval before calling
 * operations that require them.
 *
 * <p>
 * All methods reject null arguments with NullPointerException.
 */
public interface TransactionRepository {

    /**
     * Saves a new receipt with the supplied stable identifier.
     *
     * @throws IllegalArgumentException if the ID already exists, including for
     * a soft-deleted receipt
     */
    void save(UUID transactionId, CouponTransaction transaction);

    /**
     * Finds an active receipt, including its identity and current revision.
     *
     * @return the receipt record, or Optional.empty() if missing or deleted
     */
    Optional<ReceiptRecord> findById(UUID transactionId);

    /**
     * Updates an active receipt without changing its identifier.
     * Preserves original source data and records the approved change.
     *
     * <p>The revision check, receipt change, revision increment, and
     * history entry must succeed or fail together.
     *
     * @throws IllegalArgumentException if the ID is missing or deleted,
     *         or expectedRevision is less than 1
     * @throws java.util.ConcurrentModificationException if the stored
     *         revision does not match expectedRevision
     */
    void update(
            UUID transactionId,
            long expectedRevision,
            CouponTransaction transaction,
            ReceiptChangeContext context
    );

    /**
     * Soft-deletes an active receipt and records the approved deletion.
     * Preserves its contents, original source data, and history.
     *
     * <p>The revision check, deletion, revision increment, and
     * history entry must succeed or fail together.
     *
     * @throws IllegalArgumentException if the ID is missing or deleted,
     *         or expectedRevision is less than 1
     * @throws java.util.ConcurrentModificationException if the stored
     *         revision does not match expectedRevision
     */
    void softDelete(
            UUID transactionId,
            long expectedRevision,
            ReceiptChangeContext context
    );

    /**
     * Restores a deleted receipt and records the approved restoration.
     *
     * <p>The revision check, restoration, revision increment, and
     * history entry must succeed or fail together.
     *
     * @throws IllegalArgumentException if the ID is missing or active,
     *         or expectedRevision is less than 1
     * @throws java.util.ConcurrentModificationException if the stored
     *         revision does not match expectedRevision
     */
    void restore(
            UUID transactionId,
            long expectedRevision,
            ReceiptChangeContext context
    );

    /**
     * Finds a soft-deleted receipt for review before restoration.
     *
     * @return the receipt record, or Optional.empty() if missing or active
     */
    Optional<ReceiptRecord> findDeletedById(UUID transactionId);

}
