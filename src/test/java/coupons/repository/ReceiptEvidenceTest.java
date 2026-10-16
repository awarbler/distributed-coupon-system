package coupons.repository;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Tests exact source preservation and defensive copying. */
class ReceiptEvidenceTest {
    /** Verifies exact recorded identifiers, media type, and original text bytes. */
    @Test
    void preservesOriginalBytesExactly() {
        UUID evidenceId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        byte[] bytes = "Scott  2 @ 5.00\r\nMFG COUPON -2.00\r\n".getBytes(StandardCharsets.UTF_8);
        ReceiptEvidence evidence = new ReceiptEvidence(evidenceId, receiptId,
                "text/plain; charset=UTF-8", bytes);
        assertEquals(evidenceId, evidence.getEvidenceId());
        assertEquals(receiptId, evidence.getTransactionId());
        assertEquals("text/plain; charset=UTF-8", evidence.getMediaType());
        assertArrayEquals(bytes, evidence.getContent());
    }

    /** Verifies isolation from supplied and returned arrays. */
    @Test
    void protectsContentFromMutation() {
        byte[] supplied = {1, 2, 3};
        ReceiptEvidence evidence = new ReceiptEvidence(UUID.randomUUID(), UUID.randomUUID(),
                "application/octet-stream", supplied);
        supplied[0] = 9;
        byte[] returned = evidence.getContent();
        returned[1] = 9;
        assertArrayEquals(new byte[]{1, 2, 3}, evidence.getContent());
    }

    /** Verifies value equality and detection of changed original bytes. */
    @Test
    void comparesReconstructedContentByValue() {
        UUID evidenceId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        ReceiptEvidence original = new ReceiptEvidence(evidenceId, receiptId,
                "application/pdf", new byte[]{1, 2, 3});
        ReceiptEvidence copy = new ReceiptEvidence(evidenceId, receiptId,
                "application/pdf", new byte[]{1, 2, 3});
        ReceiptEvidence changed = new ReceiptEvidence(evidenceId, receiptId,
                "application/pdf", new byte[]{1, 2, 4});
        assertEquals(original, copy);
        assertEquals(original.hashCode(), copy.hashCode());
        assertNotEquals(original, changed);
    }

    /** Verifies rejection of missing required values. */
    @Test
    void rejectsNullArguments() {
        UUID evidenceId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        byte[] bytes = {1};
        assertThrows(NullPointerException.class, () -> new ReceiptEvidence(null, receiptId, "image/jpeg", bytes));
        assertThrows(NullPointerException.class, () -> new ReceiptEvidence(evidenceId, null, "image/jpeg", bytes));
        assertThrows(NullPointerException.class, () -> new ReceiptEvidence(evidenceId, receiptId, null, bytes));
        assertThrows(NullPointerException.class, () -> new ReceiptEvidence(evidenceId, receiptId, "image/jpeg", null));
    }

    /** Verifies rejection of blank media types and empty source content. */
    @Test
    void rejectsBlankTypeAndEmptyContent() {
        UUID evidenceId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> new ReceiptEvidence(evidenceId, receiptId, "   ", new byte[]{1}));
        assertThrows(IllegalArgumentException.class,
                () -> new ReceiptEvidence(evidenceId, receiptId, "image/jpeg", new byte[0]));
    }
}
