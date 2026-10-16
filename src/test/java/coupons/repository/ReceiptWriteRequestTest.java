package coupons.repository;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

/**
 * Tests receipt-write request metadata.
 */
class ReceiptWriteRequestTest {

    /**
     * Verifies that supplied identity and timestamp are preserved.
     */
    @Test
    void preservesOriginalMetadata() {
        UUID id = UUID.randomUUID();
        Instant timestamp = Instant.parse("2026-10-15T18:00:00Z");

        ReceiptWriteRequest request =
                new ReceiptWriteRequest(id, timestamp);

        assertEquals(id, request.requestId());
        assertEquals(timestamp, request.requestedAt());
    }

    /**
     * Verifies that metadata can be reconstructed unchanged for a retry.
     */
    @Test
    void reconstructedRetryHasSameMetadata() {
        ReceiptWriteRequest original = new ReceiptWriteRequest(
                UUID.randomUUID(),
                Instant.parse("2026-10-15T18:00:00Z")
        );

        ReceiptWriteRequest retry = new ReceiptWriteRequest(
                original.requestId(),
                original.requestedAt()
        );

        assertEquals(original, retry);
    }

    /**
     * Verifies that a missing request identifier is rejected.
     */
    @Test
    void rejectsNullRequestId() {
        assertThrows(
                NullPointerException.class,
                () -> new ReceiptWriteRequest(
                        null,
                        Instant.parse("2026-10-15T18:00:00Z")
                )
        );
    }

    /**
     * Verifies that a missing original timestamp is rejected.
     */
    @Test
    void rejectsNullTimestamp() {
        assertThrows(
                NullPointerException.class,
                () -> new ReceiptWriteRequest(
                        UUID.randomUUID(),
                        null
                )
        );
    }
}