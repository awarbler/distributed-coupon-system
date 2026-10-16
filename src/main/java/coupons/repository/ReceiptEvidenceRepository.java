package coupons.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Defines database-independent lookup of original receipt evidence.
 *
 * <p>Evidence is append-only. Existing artifacts must not be overwritten,
 * reassigned to another receipt, or removed through ordinary receipt edits.
 * Additional source artifacts receive distinct evidence identifiers.
 *
 * <p>Attachments must be recorded through an atomic receipt-write
 * operation that also records the associated revision and audit history.
 * This interface exposes lookup operations only.
 *
 * <p>Soft deletion must preserve evidence. Retrieving evidence does
 * not restore the receipt or change its revision.
 */
public interface ReceiptEvidenceRepository {

    /**
     * Finds an original source artifact by its stable identifier.
     *
     * <p>Evidence remains retrievable when its receipt is soft deleted.
     *
     * @param evidenceId stable identifier of the artifact
     * @return the artifact, or Optional.empty() if absent
     * @throws NullPointerException if evidenceId is null
     */
    Optional<ReceiptEvidence> findById(UUID evidenceId);

    /**
     * Finds all original source artifacts associated with a receipt.
     *
     * <p>Returns an unmodifiable list ordered by evidence ID using
     * UUID natural ordering. This order does not indicate capture time.
     *
     * <p>Results include evidence for soft-deleted receipts. An empty
     * result means no evidence is recorded; it does not prove that
     * the receipt itself is absent.
     *
     * @param transactionId stable identifier of the receipt
     * @return associated evidence, or an empty list if none exists
     * @throws NullPointerException if transactionId is null
     */
    List<ReceiptEvidence> findByReceiptId(UUID transactionId);
}
