package coupons.repository;

import java.util.List;
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
 * <p>Receipt metadata must be stored and captured in audit history
 * atomically with receipt contents. Retry comparison must include
 * metadata values, including the distinction between known and unknown
 * metadata. Metadata corrections require the same revision check,
 * approval context, and history recording as other receipt updates.
 *
 * <p>Save and update accept newly attached evidence rather than a
 * replacement attachment list. The list and its elements must be non-null.
 * Every artifact must reference the transaction ID being written, and
 * evidence IDs must be unique within the submitted batch.
 * Implementations must validate and isolate the submitted batch using
 * EvidenceBatchValidator.validateAndCopy or equivalent checks before
 * comparing or storing its contents. Stored-state checks remain required.
 *
 * <p>Creation records the submitted evidence IDs on revision one.
 * Updates preserve all existing evidence references and append new IDs
 * in submitted order. An empty batch leaves existing attachments unchanged.
 *
 * <p>For a new request, submitted evidence IDs must not already exist.
 * Existing artifacts cannot be overwritten, reassigned, or removed.
 * A matching committed retry returns its original result before applying
 * these new-request duplicate checks.
 *
 * <p>Evidence bytes, receipt contents and metadata, evidence references,
 * redemptions, history, and the committed request outcome must succeed
 * or fail as one atomic logical operation. A failed operation must not
 * expose partially attached evidence or advance the receipt revision.
 *
 * <p>Retry comparison includes evidence IDs, receipt ownership, media
 * types, exact source bytes, and submitted order. Retries must preserve
 * these values and must not generate new artifact IDs.
 *
 * <p>Implementations must isolate mutable transaction inputs and caller-owned
 * collections before comparison and persistence. Concurrent mutation during
 * input capture is unsupported.
 *
 * <p>All required reference arguments reject null with
 * NullPointerException. Metadata supplied to save or update may be
 * null when unknown.
 */
public interface TransactionRepository {

    /**
     * Creates a receipt at revision one with ACTIVE status.
     *
     * <p>Commits the receipt, redemptions, creation history, and request
     * outcome atomically. A matching committed retry returns its original
     * result rather than failing because the receipt already exists.
     *
     * @param transactionId stable identifier of the new receipt
     * @param transaction receipt contents to save
     * @param metadata receipt identification details, null when unknown
     * @param newEvidence newly attached source artifacts; empty if none
     * @param request metadata identifying this logical write
     * @param context authenticated requester information
     * @return the original committed creation result
     * @throws NullPointerException if any required argument or evidence element is null
     * @throws IllegalArgumentException if the receipt ID already exists
     *         for a new request, references are invalid, a reward is already
     *         redeemed, the request ID was used with different contents,
     *         or evidence has duplicate IDs, invalid ownership, or IDs
     *         already used by another committed operation
     */
    ReceiptWriteResult save(
        UUID transactionId, 
        CouponTransaction transaction,
        ReceiptMeta metadata,
        List<ReceiptEvidence> newEvidence,
        ReceiptWriteRequest request,
        ReceiptCreateCtx context);

    /**
     * Finds an active receipt, including its identity and current revision.
     *
     * @return the receipt record, or Optional.empty() if missing or deleted
     */
    Optional<ReceiptRecord> findById(UUID transactionId);

    /**
     * Updates an active receipt and increments its revision by one.
     *
     * <p>Commits the receipt, new redemptions, history, and request outcome
     * atomically. Existing redemptions cannot be removed or altered.
     * Matching committed retries return their original results.
     *
     * <p>Metadata replaces the stored metadata in full. Null means the
     * resulting metadata is unknown; it does not mean preserve the old
     * metadata. Callers retaining existing metadata must supply it.
     *
     * @param transactionId stable identifier of the receipt
     * @param expectedRevision revision reviewed for this approved change
     * @param transaction replacement receipt contents
     * @param metadata receipt identification details, null when unknown
     * @param newEvidence newly attached source artifacts; empty if none
     * @param request metadata identifying this logical write
     * @param context requester, reason, and approval information
     * @return the original committed update result
     * @throws NullPointerException if any required argument or evidence element is null
     * @throws IllegalArgumentException if the receipt is missing or deleted,
     *         expectedRevision is less than one, references are invalid,
     *         redemption rules are violated, or the request ID was used
     *         with different contents, or evidence has duplicate IDs,
     *         invalid ownership, or IDs already used by another committed operation
     * @throws java.util.ConcurrentModificationException if a new request's
     *         expected revision does not match the stored revision
     */
    ReceiptWriteResult update(
            UUID transactionId,
            long expectedRevision,
            CouponTransaction transaction,
            ReceiptMeta metadata,
            List<ReceiptEvidence> newEvidence,
            ReceiptWriteRequest request,
            ReceiptChangeContext context
    );
    /**
     * Soft-deletes an active receipt and increments its revision by one.
     *
     * <p>Preserves original source data, receipt contents, and redemptions.
     * Commits the status change, history, and request outcome atomically.
     * Matching committed retries return their original results.
     *
     * @param transactionId stable identifier of the receipt
     * @param expectedRevision revision reviewed for this approved deletion
     * @param request metadata identifying this logical write
     * @param context requester, reason, and approval information
     * @return the original committed deletion result
     * @throws NullPointerException if any reference argument is null
     * @throws IllegalArgumentException if the receipt is missing or deleted,
     *         expectedRevision is less than one, or the request ID was used
     *         with different contents
     * @throws java.util.ConcurrentModificationException if a new request's
     *         expected revision does not match the stored revision
     */
    ReceiptWriteResult softDelete(
            UUID transactionId,
            long expectedRevision,
            ReceiptWriteRequest request,
            ReceiptChangeContext context
    );

    /**
     * Restores a deleted receipt and increments its revision by one.
     *
     * <p>Preserves its contents and redemption records. Commits the status
     * change, history, and request outcome atomically. Matching committed
     * retries return their original results.
     *
     * @param transactionId stable identifier of the receipt
     * @param expectedRevision revision reviewed for this approved restoration
     * @param request metadata identifying this logical write
     * @param context requester, reason, and approval information
     * @return the original committed restoration result
     * @throws NullPointerException if any reference argument is null
     * @throws IllegalArgumentException if the receipt is missing or active,
     *         expectedRevision is less than one, or the request ID was used
     *         with different contents
     * @throws java.util.ConcurrentModificationException if a new request's
     *         expected revision does not match the stored revision
     */
    ReceiptWriteResult restore(
            UUID transactionId,
            long expectedRevision,
            ReceiptWriteRequest request,
            ReceiptChangeContext context
    );

    /**
     * Finds a soft-deleted receipt for review before restoration.
     *
     * @param transactionId stable identifier of the receipt
     * @return the receipt record, or Optional.empty() if missing or active
     * @throws NullPointerException if transactionId is null
     */
    Optional<ReceiptRecord> findDeletedById(UUID transactionId);

    /**
     * Lists soft-deleted receipts available for restoration review.
     *
     * <p>Only receipts with DELETED status are included. Results are
     * ordered by transaction ID using UUID natural ordering, not by
     * deletion time.
     *
     * <p>The returned list is unmodifiable. Returned transaction contents
     * must be isolated from stored data so caller changes cannot silently
     * modify persisted receipts.
     *
     * <p>This lookup does not restore receipts, change revisions,
     * create history entries, or release redeemed rewards.
     *
     * @return deleted receipts, or an empty list if none exist
     */
    List<ReceiptRecord> findDeleted();

    /**
     * Finds receipts that may duplicate the supplied identification details.
     *
     * <p>A candidate must have metadata with an exactly matching retailer.
     * It must also match at least one supplied, known identifier:
     * the receipt number or the printed local purchase date/time.
     * Null values must not match other null values as evidence.
     *
     * <p>If the supplied metadata and candidate both have known store
     * references, those references must match exactly. An unknown store
     * reference does not by itself exclude a candidate.
     *
     * <p>Matching uses recorded values without trimming, changing case,
     * removing punctuation, or assuming time zones. Consequently, formatting
     * differences can cause possible duplicates to remain undetected.
     *
     * <p>Results include active and soft-deleted receipts, ordered by
     * transaction ID using UUID natural ordering. The list is unmodifiable,
     * and returned transaction contents must be isolated from stored data.
     *
     * <p>This operation does not change receipts, revisions, rewards,
     * or history. Matches are candidates for user review, not proof of
     * duplication. When reviewing an existing receipt, the application
     * service must exclude that receipt's own transaction ID.
     *
     * @param metadata identification details used to find possible matches
     * @return candidate receipts, or an empty list if none match
     * @throws NullPointerException if metadata is null
     * @throws IllegalArgumentException if both receipt number and
     *         purchase date/time are unknown
     */
    List<ReceiptRecord> findPossibleDuplicates(ReceiptMeta metadata);

    /** Receipts documenting credits received for an original earning. */
    default List<ReceiptRecord> findRewardCredits(UUID receiptId,UUID earningId){return List.of();}

    /** Web import identity matching tolerates receipt-number separators and store casing. */
    default List<ReceiptRecord> findImportDuplicates(ReceiptMeta metadata){return findPossibleDuplicates(metadata);}

}
