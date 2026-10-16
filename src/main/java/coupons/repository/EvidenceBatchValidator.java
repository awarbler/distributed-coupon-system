package coupons.repository;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Validates newly submitted receipt evidence without accessing storage.
 *
 * <p>Existing-ID checks, append-only enforcement, and atomic persistence
 * remain repository responsibilities.
 */
public final class EvidenceBatchValidator {
    /** Prevents construction of this utility class. */
    private EvidenceBatchValidator() {
    }

    /**
     * Copies and validates evidence submitted for one receipt.
     *
     * <p>Input order is preserved. Validation does not modify the input.
     * Concurrent mutation during input capture is unsupported.
     *
     * @param transactionId enclosing receipt identifier
     * @param newEvidence newly submitted artifacts
     * @return an unmodifiable validated copy of the batch
     * @throws NullPointerException if the receipt ID, list, or an element is null
     * @throws IllegalArgumentException if artifact ownership is inconsistent
     *         or an evidence ID occurs more than once
     */
    public static List<ReceiptEvidence> validateAndCopy(
            UUID transactionId, List<ReceiptEvidence> newEvidence) {
        Objects.requireNonNull(transactionId, "Transaction ID cannot be null.");
        List<ReceiptEvidence> copied = List.copyOf(Objects.requireNonNull(
                newEvidence, "Evidence batch cannot be null."));
        Set<UUID> seenIds = new HashSet<>();
        for (ReceiptEvidence evidence : copied) {
            if (!transactionId.equals(evidence.getTransactionId())) {
                throw new IllegalArgumentException("Evidence references a different receipt.");
            }
            if (!seenIds.add(evidence.getEvidenceId())) {
                throw new IllegalArgumentException("Duplicate evidence ID in submitted batch.");
            }
        }
        return copied;
    }
}
