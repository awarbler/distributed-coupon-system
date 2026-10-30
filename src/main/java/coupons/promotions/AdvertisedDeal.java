package coupons.promotions;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a revision of a source-backed advertised offer.
 *
 * <p>Terms are an interpretation, not verified eligibility or proof
 * that a reward was issued. Original artifacts remain unchanged.
 *
 * <p>Optimization preferences belong to shopping scenarios, not this offer.
 * The same offer may participate in scenarios prioritizing checkout cash,
 * reward preservation, or effective cost.
 *
 * <p>Null validity dates mean unknown, not indefinitely valid. Dates refer
 * to advertised calendar dates, not source capture timestamps.
 *
 * @param dealId stable offer identifier
 * @param title descriptive offer title
 * @param interpretedTerms current interpretation of conditions
 * @param retailer recorded retailer, null if unknown
 * @param validFrom first advertised date, null if unknown
 * @param validThrough last advertised date, null if unknown
 * @param sourceArtifactIds supporting original artifacts
 * @param revision offer revision, beginning at one
 */
public record AdvertisedDeal(
        UUID dealId,
        String title,
        String interpretedTerms,
        String retailer,
        LocalDate validFrom,
        LocalDate validThrough,
        List<UUID> sourceArtifactIds,
        long revision,
        DealTerms pricing
) {

    public AdvertisedDeal(UUID id, String title, String terms, String retailer, LocalDate from,
            LocalDate through, List<UUID> sources, long revision) {
        this(id,title,terms,retailer,from,through,sources,revision,null);
    }

    /**
     * Validates offer values without normalizing text.
     *
     * <p>No dates, retailer policies, or product associations are inferred.
     * Source references are copied; their existence is checked by persistence.
     *
     * @param dealId stable offer identifier
     * @param title descriptive offer title
     * @param interpretedTerms current interpretation of conditions
     * @param retailer recorded retailer, null if unknown
     * @param validFrom first advertised date, null if unknown
     * @param validThrough last advertised date, null if unknown
     * @param sourceArtifactIds supporting original artifacts
     * @param revision offer revision
     * @throws NullPointerException if required references or source IDs are null
     * @throws IllegalArgumentException if text is invalid, dates are reversed,
     *         sources are empty or duplicated, or revision is less than one
     */
    public AdvertisedDeal {
        Objects.requireNonNull(dealId, "Deal ID cannot be null.");
        Objects.requireNonNull(title, "Title cannot be null.");
        Objects.requireNonNull(interpretedTerms, "Interpreted terms cannot be null.");

        if (title.isBlank() || interpretedTerms.isBlank()) {
            throw new IllegalArgumentException(
                    "Title and interpreted terms cannot be blank."
            );
        }

        if (retailer != null && retailer.isBlank()) {
            throw new IllegalArgumentException("Retailer must be null or nonblank.");
        }

        if (validFrom != null && validThrough != null
                && validThrough.isBefore(validFrom)) {
            throw new IllegalArgumentException("Validity end cannot precede its start.");
        }

        sourceArtifactIds = List.copyOf(sourceArtifactIds);
        if (sourceArtifactIds.isEmpty()
                || new HashSet<>(sourceArtifactIds).size() != sourceArtifactIds.size()) {
            throw new IllegalArgumentException(
                    "At least one distinct source artifact is required."
            );
        }

        if (revision < 1) {
            throw new IllegalArgumentException("Deal revision must be at least 1.");
        }
    }
}
