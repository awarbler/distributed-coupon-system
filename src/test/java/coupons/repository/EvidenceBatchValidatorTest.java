package coupons.repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Tests evidence-batch ownership, uniqueness, and isolation. */
class EvidenceBatchValidatorTest {
    /**
     * Creates a hypothetical artifact.
     * @param evidenceId artifact identifier
     * @param receiptId owning receipt identifier
     * @return immutable example evidence
     */
    private ReceiptEvidence evidence(UUID evidenceId, UUID receiptId) {
        return new ReceiptEvidence(evidenceId, receiptId,
                "application/octet-stream", new byte[]{1, 2, 3});
    }

    /** Verifies acceptance of an empty batch. */
    @Test
    void acceptsEmptyBatch() {
        assertTrue(EvidenceBatchValidator.validateAndCopy(UUID.randomUUID(), List.of()).isEmpty());
    }

    /** Verifies order preservation and isolation from the caller's list. */
    @Test
    void copiesValidBatchAndPreservesOrder() {
        UUID receiptId = UUID.randomUUID();
        ReceiptEvidence first = evidence(UUID.randomUUID(), receiptId);
        ReceiptEvidence second = evidence(UUID.randomUUID(), receiptId);
        List<ReceiptEvidence> supplied = new ArrayList<>(List.of(first, second));
        List<ReceiptEvidence> validated = EvidenceBatchValidator.validateAndCopy(receiptId, supplied);
        supplied.clear();
        assertEquals(List.of(first, second), validated);
        assertThrows(UnsupportedOperationException.class, () -> validated.clear());
    }

    /** Verifies rejection without modifying the submitted list. */
    @Test
    void rejectsWrongReceiptWithoutChangingInput() {
        UUID receiptId = UUID.randomUUID();
        List<ReceiptEvidence> supplied = new ArrayList<>(List.of(
                evidence(UUID.randomUUID(), receiptId), evidence(UUID.randomUUID(), UUID.randomUUID())));
        List<ReceiptEvidence> before = List.copyOf(supplied);
        assertThrows(IllegalArgumentException.class,
                () -> EvidenceBatchValidator.validateAndCopy(receiptId, supplied));
        assertEquals(before, supplied);
    }

    /** Verifies duplicate identity rejection for separate artifact objects. */
    @Test
    void rejectsDuplicateEvidenceIds() {
        UUID receiptId = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();
        List<ReceiptEvidence> supplied = List.of(evidence(evidenceId, receiptId), evidence(evidenceId, receiptId));
        assertThrows(IllegalArgumentException.class,
                () -> EvidenceBatchValidator.validateAndCopy(receiptId, supplied));
    }

    /** Verifies required receipt, batch, and element validation. */
    @Test
    void rejectsNullInputs() {
        UUID receiptId = UUID.randomUUID();
        assertThrows(NullPointerException.class, () -> EvidenceBatchValidator.validateAndCopy(null, List.of()));
        assertThrows(NullPointerException.class, () -> EvidenceBatchValidator.validateAndCopy(receiptId, null));
        assertThrows(NullPointerException.class, () -> EvidenceBatchValidator.validateAndCopy(receiptId,
                Arrays.asList(evidence(UUID.randomUUID(), receiptId), null)));
    }
}
