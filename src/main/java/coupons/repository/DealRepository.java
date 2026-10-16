package coupons.repository;

import coupons.AdvertisedDeal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Defines database-independent storage for advertised offers.
 *
 * <p>Original source artifacts must already exist and remain unchanged.
 * Artifacts may support multiple offers; offers may reference multiple
 * artifacts. Similar titles or terms must not force an automatic merge.
 *
 * <p>Writes must preserve all offer revisions and record the actor,
 * commit time, and correction reason for updates. Offer and history
 * changes must commit atomically.
 *
 * <p>This interface does not store shopping scenarios, calculate
 * eligibility, issue rewards, or modify completed receipts.
 *
 * <p>Storage and retrieval must not depend on a selected optimization
 * strategy. Saving an offer does not confirm eligibility, reserve rewards,
 * issue benefits, or make expected rewards available for redemption.
 * Unknown validity dates must not be interpreted as indefinite validity.
 */
public interface DealRepository {

    /**
     * Saves a new advertised offer at revision one.
     *
     * @param deal source-backed offer
     * @param recordedBy authenticated user recording the offer
     * @throws NullPointerException if either argument is null
     * @throws IllegalArgumentException if recordedBy is blank,
     *         revision is not one, the deal ID exists, or a source is missing
     */
    void save(AdvertisedDeal deal, String recordedBy);

    /**
     * Retrieves the current offer interpretation, even if its dates have passed.
     *
     * @param dealId stable offer identifier
     * @return current offer, or Optional.empty() if absent
     * @throws NullPointerException if dealId is null
     */
    Optional<AdvertisedDeal> findById(UUID dealId);

    /**
     * Retrieves a preserved offer revision.
     *
     * @param dealId stable offer identifier
     * @param revision revision to retrieve
     * @return recorded revision, or Optional.empty() if absent
     * @throws NullPointerException if dealId is null
     * @throws IllegalArgumentException if revision is less than one
     */
    Optional<AdvertisedDeal> findByRevision(UUID dealId, long revision);

    /**
     * Finds current offer revisions referencing an original artifact.
     *
     * <p>This searches current revisions only, not every historical association.
     * Results are unmodifiable and ordered by deal ID using UUID natural ordering.
     * Historical associations remain available through preserved revisions even
     * when current source associations change.
     *
     * @param artifactId original source identifier
     * @return current matching offers, or an empty list
     * @throws NullPointerException if artifactId is null
     */
    List<AdvertisedDeal> findBySourceId(UUID artifactId);

    /**
     * Records a corrected interpretation without modifying source artifacts.
     *
     * <p>The supplied revision must equal expectedRevision plus one.
     * The stored revision check, replacement, and history write must
     * be atomic. Failure must leave current and historical data unchanged.
     *
     * <p>After an uncertain outcome, retrieve and reconcile before retrying.
     * Do not silently resubmit against a newer revision.
     *
     * @param deal replacement interpretation with the existing deal ID
     * @param expectedRevision revision reviewed before this correction
     * @param changedBy authenticated user making the correction
     * @param reason explanation supporting the correction
     * @return the committed interpretation
     * @throws NullPointerException if a reference argument is null
     * @throws IllegalArgumentException if actor or reason is blank,
     *         the deal or a source is missing, revision values are invalid,
     *         or revision cannot be incremented
     * @throws java.util.ConcurrentModificationException if the stored
     *         revision differs from expectedRevision
     */
    AdvertisedDeal update(
            AdvertisedDeal deal,
            long expectedRevision,
            String changedBy,
            String reason
    );
}
