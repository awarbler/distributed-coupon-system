package coupons.repository;

import coupons.receipts.CouponTransaction;
import coupons.receipts.ReceiptTotals;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Tests metadata capture and lifecycle-history preservation. */
class ReceiptMetadataCaptureTest {
    /**
     * Creates empty transaction contents.
     * @return an empty example transaction
     */
    private CouponTransaction transaction() {
        return new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO, BigDecimal.ZERO));
    }

    /** Verifies metadata is included in a captured revision. */
    @Test
    void capturesMetadata() {
        ReceiptMeta metadata = new ReceiptMeta("Walgreens", "000123", null, "0021");
        ReceiptRecord receipt = new ReceiptRecord(UUID.randomUUID(), ReceiptStatus.ACTIVE,
                1, transaction(), metadata);
        assertEquals(metadata, receipt.metadata());
        assertEquals(metadata, ReceiptRevSnap.from(receipt).metadata());
    }

    /** Verifies older constructors retain unknown metadata. */
    @Test
    void preservesUnknownMetadata() {
        ReceiptRecord receipt = new ReceiptRecord(UUID.randomUUID(), ReceiptStatus.ACTIVE, 1, transaction());
        assertNull(receipt.metadata());
        assertNull(ReceiptRevSnap.from(receipt).metadata());
        ReceiptRevSnap legacy = new ReceiptRevSnap(receipt.transactionId(), 1,
                ReceiptStatus.ACTIVE, ReceiptSnapshot.from(receipt.transaction()));
        assertNull(legacy.metadata());
    }

    /** Verifies metadata-only corrections preserve original and approved values. */
    @Test
    void recordsMetadataCorrectionInHistory() {
        UUID id = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        Instant time = Instant.parse("2026-10-16T15:00:00Z");
        ReceiptSnapshot contents = ReceiptSnapshot.from(transaction());
        ReceiptRevSnap before = new ReceiptRevSnap(id, 1, ReceiptStatus.ACTIVE, contents,
                new ReceiptMeta("Walgreens", "000123", null, null));
        ReceiptRevSnap after = new ReceiptRevSnap(id, 2, ReceiptStatus.ACTIVE, contents,
                new ReceiptMeta("Walgreens", "000124", null, null));
        ReceiptHistoryEntry history = new ReceiptHistoryEntry(UUID.randomUUID(),
                new ReceiptWriteRequest(requestId, time),
                new ReceiptWriteResult(requestId, id, 2, ReceiptChangeAction.UPDATED, ReceiptStatus.ACTIVE, time),
                before, after, null, new ReceiptChangeContext("user-123", "Correct reference", "user-123", time));
        assertEquals("000123", history.before().metadata().receiptNumber());
        assertEquals("000124", history.after().metadata().receiptNumber());
        assertNotEquals(before, after);
    }

    /** Verifies lifecycle transitions cannot conceal metadata corrections. */
    @Test
    void rejectsMetadataChangesDuringDeletionAndRestoration() {
        UUID id = UUID.randomUUID();
        ReceiptSnapshot contents = ReceiptSnapshot.from(transaction());
        Instant time = Instant.parse("2026-10-16T15:00:00Z");
        for (ReceiptChangeAction action : new ReceiptChangeAction[]{ReceiptChangeAction.DELETED, ReceiptChangeAction.RESTORED}) {
            UUID requestId = UUID.randomUUID();
            ReceiptStatus previous = action == ReceiptChangeAction.DELETED ? ReceiptStatus.ACTIVE : ReceiptStatus.DELETED;
            ReceiptStatus next = action == ReceiptChangeAction.DELETED ? ReceiptStatus.DELETED : ReceiptStatus.ACTIVE;
            ReceiptRevSnap before = new ReceiptRevSnap(id, 2, previous, contents,
                    new ReceiptMeta("Walgreens", "000123", null, null));
            ReceiptRevSnap after = new ReceiptRevSnap(id, 3, next, contents,
                    new ReceiptMeta("Walgreens", "000124", null, null));
            assertThrows(IllegalArgumentException.class, () -> new ReceiptHistoryEntry(UUID.randomUUID(),
                    new ReceiptWriteRequest(requestId, time),
                    new ReceiptWriteResult(requestId, id, 3, action, next, time), before, after, null,
                    new ReceiptChangeContext("user-123", "Lifecycle change", "user-123", time)));
        }
    }
}
