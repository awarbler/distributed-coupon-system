package coupons.repository;

import java.util.Optional;
import java.util.UUID;

import coupons.CouponTransaction;

/**
 * Defines database-independent storage operations for receipts.
 *
 * <p>A transaction ID remains unchanged throughout a receipt's lifecycle.
 * IDs belonging to soft-deleted receipts cannot be reused.
 *
 * <p>Implementations must preserve original receipt source data and record
 * creation, updates, deletions, and restorations in a change history.
 * Each mutation and its history entry must succeed or fail together.
 *
 * <p>Changing a supplied or retrieved transaction must not silently change
 * the stored receipt. Changes must pass through repository write operations.
 *
 * <p>The application service manages user confirmation and approval before
 * calling operations that require them.
 *
 * <p>Before saving or updating, implementations must validate receipt
 * relationships using ReceiptValidator or equivalent checks. Every
 * Register Reward redemption must reference the transaction ID supplied
 * to the operation. Invalid relationships must be rejected without
 * changing stored data or history.
 *
 * <p>Receipt creation and updates must commit the receipt, its Register
 * Reward redemptions, and its audit-history entry as one atomic operation.
 * If any validation or write fails, none of those changes may be committed.
 *
 * <p>Every redemption must reference an existing issued reward. A reward
 * must not be redeemed on more than one receipt. Implementations must
 * enforce this rule atomically across concurrent writes, rather than
 * relying only on transaction-local checks.
 *
 * <p>On update, unchanged redemptions retain their existing identifiers
 * and must not be treated as new redemption attempts. Adding a redemption
 * must pass the same validation as recording it during receipt creation.
 *
 * <p>Ordinary receipt updates must reject removal or alteration of an
 * existing redemption. Reversals and redemption corrections require a
 * separate explicit operation with approval and audit history.
 *
 * <p>Soft deletion and restoration change receipt visibility only.
 * They must preserve redemption records and must not release redeemed
 * rewards for reuse.
 *
 * <p>Updates, soft deletions, and restorations must check the expected
 * receipt revision atomically with all associated writes. A failed
 * revision check must leave the receipt, redemption records, and
 * history unchanged.
 *
 * <p>Each logical write must have a stable request ID and original request
 * timestamp. Retries must reuse both values.
 *
 * <p>Implementations must retain the committed request identity, operation
 * contents, and original result atomically with the receipt, redemption,
 * and audit-history changes.
 *
 * <p>A retry matching a committed request must return the original result
 * without creating another revision, redemption, or history entry.
 * This result must describe the original committed operation, even if
 * the receipt has subsequently changed.
 *
 * <p>Reusing a request ID with different operation contents must be rejected.
 * Contents include the operation type, receipt ID, expected revision where
 * applicable, submitted values, actor and approval context, and original
 * request timestamp. Comparison must use values rather than object identity.
 *
 * <p>Committed-request detection must precede expected-revision validation
 * for a matching retry. This does not bypass authentication or authorization.
 *
 * <p>Concurrent matching requests must produce at most one committed
 * operation. Other callers may wait or receive a retryable in-progress
 * response before retrieving the original committed result.
 *
 * <p>A failure before commit must not leave partial receipt, redemption,
 * history, or successful-request records.
 *
 * <p>All methods reject null arguments with NullPointerException.
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
