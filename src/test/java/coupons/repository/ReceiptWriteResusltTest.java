package coupons.repository;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests immutable acknowledgments of committed receipt writes.
 */
class ReceiptWriteResultTest {

    /**
     * Verifies preservation of the original committed values.
     */
    @Test
    void preservesCommittedValues() {
        UUID requestId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        Instant committedAt = Instant.parse("2026-10-15T18:00:00Z");

        ReceiptWriteResult result = new ReceiptWriteResult(
                requestId,
                receiptId,
                2,
                ReceiptChangeAction.UPDATED,
                ReceiptStatus.ACTIVE,
                committedAt
        );

        assertEquals(requestId, result.requestId());
        assertEquals(receiptId, result.transactionId());
        assertEquals(2L, result.revision());
        assertEquals(ReceiptChangeAction.UPDATED, result.action());
        assertEquals(ReceiptStatus.ACTIVE, result.status());
        assertEquals(committedAt, result.committedAt());
    }

    /**
     * Verifies the supported action and resulting-status combinations.
     */
    @Test
    void acceptsValidLifecycleResults() {
        Instant time = Instant.parse("2026-10-15T18:00:00Z");
        UUID requestId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();

        assertDoesNotThrow(() -> new ReceiptWriteResult(
                requestId, receiptId, 1,
                ReceiptChangeAction.CREATED, ReceiptStatus.ACTIVE, time
        ));

        assertDoesNotThrow(() -> new ReceiptWriteResult(
                requestId, receiptId, 2,
                ReceiptChangeAction.DELETED, ReceiptStatus.DELETED, time
        ));

        assertDoesNotThrow(() -> new ReceiptWriteResult(
                requestId, receiptId, 3,
                ReceiptChangeAction.RESTORED, ReceiptStatus.ACTIVE, time
        ));
    }

    /**
     * Verifies rejection of invalid revisions.
     */
    @Test
    void rejectsInvalidRevisions() {
        Instant time = Instant.parse("2026-10-15T18:00:00Z");
        UUID requestId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> new ReceiptWriteResult(
                        requestId, receiptId, 0,
                        ReceiptChangeAction.CREATED,
                        ReceiptStatus.ACTIVE, time
                ));

        assertThrows(IllegalArgumentException.class,
                () -> new ReceiptWriteResult(
                        requestId, receiptId, 2,
                        ReceiptChangeAction.CREATED,
                        ReceiptStatus.ACTIVE, time
                ));

        for (ReceiptChangeAction action : new ReceiptChangeAction[]{
                ReceiptChangeAction.UPDATED,
                ReceiptChangeAction.DELETED,
                ReceiptChangeAction.RESTORED
        }) {
            ReceiptStatus status =
                    action == ReceiptChangeAction.DELETED
                            ? ReceiptStatus.DELETED
                            : ReceiptStatus.ACTIVE;

            assertThrows(IllegalArgumentException.class,
                    () -> new ReceiptWriteResult(
                            requestId, receiptId, 1, action, status, time
                    ));
        }
    }

    /**
     * Verifies rejection of contradictory actions and statuses.
     */
    @Test
    void rejectsIncorrectStatusForEachAction() {
        for (ReceiptChangeAction action : ReceiptChangeAction.values()) {
            long revision = action == ReceiptChangeAction.CREATED ? 1 : 2;

            ReceiptStatus incorrectStatus =
                    action == ReceiptChangeAction.DELETED
                            ? ReceiptStatus.ACTIVE
                            : ReceiptStatus.DELETED;

            assertThrows(IllegalArgumentException.class,
                    () -> new ReceiptWriteResult(
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            revision,
                            action,
                            incorrectStatus,
                            Instant.parse("2026-10-15T18:00:00Z")
                    ));
        }
    }

    /**
     * Verifies rejection of missing required references.
     */
    @Test
    void rejectsNullFields() {
        UUID requestId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        Instant time = Instant.parse("2026-10-15T18:00:00Z");

        assertThrows(NullPointerException.class,
                () -> new ReceiptWriteResult(
                        null, receiptId, 1,
                        ReceiptChangeAction.CREATED,
                        ReceiptStatus.ACTIVE, time
                ));

        assertThrows(NullPointerException.class,
                () -> new ReceiptWriteResult(
                        requestId, null, 1,
                        ReceiptChangeAction.CREATED,
                        ReceiptStatus.ACTIVE, time
                ));

        assertThrows(NullPointerException.class,
                () -> new ReceiptWriteResult(
                        requestId, receiptId, 1,
                        null, ReceiptStatus.ACTIVE, time
                ));

        assertThrows(NullPointerException.class,
                () -> new ReceiptWriteResult(
                        requestId, receiptId, 1,
                        ReceiptChangeAction.CREATED, null, time
                ));

        assertThrows(NullPointerException.class,
                () -> new ReceiptWriteResult(
                        requestId, receiptId, 1,
                        ReceiptChangeAction.CREATED,
                        ReceiptStatus.ACTIVE, null
                ));
    }
}