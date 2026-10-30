package coupons.repository;

import coupons.promotions.SourceArtifact;
import java.util.Optional;
import java.util.UUID;

/**
 * Defines database-independent preservation of original source artifacts.
 *
 * <p>Stored artifacts are immutable. This interface provides no replace,
 * update, or delete operation. Corrected interpretations must be recorded
 * separately without modifying their supporting artifacts.
 *
 * <p>Artifacts do not belong exclusively to one deal. Multiple offers
 * may reference the same artifact, and one offer may reference multiple
 * artifacts. Those associations belong to the deal contracts.
 *
 * <p>Implementations must preserve the original bytes and metadata
 * without normalization, decoding, conversion, or recompression.
 */
public interface SourceArtifactRepository {
    /**
     * Stores an original source artifact without overwriting existing data.
     *
     * <p>If the identifier is absent, stores the complete artifact atomically.
     * If the same identifier and all recorded values already exist, treats
     * the call as a successful retry without creating another artifact.
     *
     * <p>Reusing an identifier with different bytes or metadata must fail
     * without altering the original. Concurrent calls must enforce these
     * rules atomically.
     *
     * <p>Identical bytes with different identifiers are permitted. Content
     * similarity alone must not automatically merge source records.
     *
     * @param artifact original source artifact to preserve
     * @throws NullPointerException if artifact is null
     * @throws IllegalArgumentException if the identifier already exists
     *         with different recorded values
     */
    void save(SourceArtifact artifact);

    /**
     * Retrieves the original artifact by its stable identifier.
     *
     * <p>Returned content must preserve the stored bytes exactly.
     * Callers must not be able to modify stored content through returned
     * byte arrays.
     *
     * @param artifactId stable artifact identifier
     * @return the original artifact, or Optional.empty() if absent
     * @throws NullPointerException if artifactId is null
     */
    Optional<SourceArtifact> findById(UUID artifactId);
}
