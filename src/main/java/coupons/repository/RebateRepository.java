package coupons.repository;

import coupons.promotions.RebateClaim;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Defines database-independent storage for rebate claims.
 *
 * <p>Implementations enforce ownership and access scope. Writes preserve all
 * claim revisions and audit information, including actor, commit time, and
 * update reason. Current and historical changes commit atomically; failure
 * leaves both unchanged.
 *
 * <p>Referenced artifacts and completed receipts must exist in the permitted
 * scope. Planned-purchase references require validation when their identity
 * contract is integrated. A UUID alone does not establish a valid purchase.
 *
 * <p>Artifacts and claims have many-to-many associations. Artifact existence
 * does not prove eligibility, approval, credit, or payment. Original artifacts
 * remain unchanged. Similar provider references, amounts, or purchases must
 * not force automatic claim merging.
 *
 * <p>Saving claims does not change account balances, redeem points, request
 * withdrawals, or deliver benefits. Those operations require separate ledger
 * and fulfillment records. Credit and later payout must not be counted as
 * separate earnings. Redemption eligibility requires versioned or otherwise
 * historically traceable provider policies, not hardcoded thresholds.
 *
 * <p>No mandatory status sequence is imposed. Corrections preserve earlier
 * interpretations through revision history.
 */
public interface RebateRepository {
    /**
     * Saves a new claim at revision one.
     *
     * @param claim claim to record
     * @param recordedBy authenticated actor
     * @throws NullPointerException if either argument is null
     * @throws IllegalArgumentException if actor is blank, revision is not one,
     *         claim ID exists, or a reference fails applicable validation
     */
    void save(RebateClaim claim, String recordedBy);

    /**
     * Retrieves the current claim revision.
     *
     * @param claimId stable claim identifier
     * @return current claim, or empty if absent
     * @throws NullPointerException if claimId is null
     */
    Optional<RebateClaim> findById(UUID claimId);

    /**
     * Retrieves a preserved revision.
     *
     * @param claimId stable claim identifier
     * @param revision revision to retrieve
     * @return recorded revision, or empty if absent
     * @throws NullPointerException if claimId is null
     * @throws IllegalArgumentException if revision is less than one
     */
    Optional<RebateClaim> findByRevision(UUID claimId, long revision);

    /**
     * Finds current claims for a completed receipt, including deleted receipts.
     *
     * @param transactionId receipt identifier
     * @return unmodifiable results ordered by claim UUID natural ordering
     * @throws NullPointerException if transactionId is null
     */
    List<RebateClaim> findByReceiptId(UUID transactionId);

    /**
     * Finds current claims for a planned purchase.
     *
     * @param plannedPurchaseId planning reference
     * @return unmodifiable results ordered by claim UUID natural ordering
     * @throws NullPointerException if plannedPurchaseId is null
     */
    List<RebateClaim> findByPlannedPurchaseId(UUID plannedPurchaseId);

    /**
     * Records a correction to an existing claim.
     *
     * <p>Supplied revision must equal expectedRevision plus one. Checking the
     * stored revision, replacing the claim, and writing history must be atomic.
     * Earlier values and associations remain available in historical revisions.
     * Reference validation also applies to updates. No financial balances change.
     *
     * <p>After an uncertain outcome, retrieve and reconcile before retrying;
     * do not silently resubmit against a newer revision.
     *
     * @param claim replacement with the existing claim ID
     * @param expectedRevision revision reviewed before updating
     * @param changedBy authenticated actor
     * @param reason explanation supporting the correction
     * @return committed revision
     * @throws NullPointerException if a reference argument is null
     * @throws IllegalArgumentException if actor or reason is blank, claim is
     *         missing, references fail validation, revisions are invalid,
     *         or revision cannot be incremented
     * @throws java.util.ConcurrentModificationException if the stored revision
     *         differs from expectedRevision
     */
    RebateClaim update(RebateClaim claim, long expectedRevision,
            String changedBy, String reason);
}
