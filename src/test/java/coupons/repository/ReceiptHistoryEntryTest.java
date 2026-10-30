package coupons.repository;

import coupons.receipts.CouponTransaction;
import coupons.receipts.ReceiptTotals;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Tests receipt-history lifecycle and context requirements. */
class ReceiptHistoryEntryTest {
    /**
     * Creates immutable example contents.
     * @param total reported final total
     * @return captured contents
     */
    private ReceiptSnapshot contents(String total) {
        return ReceiptSnapshot.from(new CouponTransaction(new ReceiptTotals(
                BigDecimal.ZERO, new BigDecimal(total))));
    }

    /**
     * Creates consistent metadata for a history entry.
     * @param action committed action
     * @param before previous revision
     * @param after resulting revision
     * @return validated entry
     */
    private ReceiptHistoryEntry entry(ReceiptChangeAction action,
            ReceiptRevSnap before, ReceiptRevSnap after) {
        Instant time = Instant.parse("2026-10-16T15:00:00Z");
        UUID requestId = UUID.randomUUID();
        return new ReceiptHistoryEntry(UUID.randomUUID(),
                new ReceiptWriteRequest(requestId, time),
                new ReceiptWriteResult(requestId, after.transactionId(),
                        after.revision(), action, after.status(), time),
                before, after,
                action == ReceiptChangeAction.CREATED ? new ReceiptCreateCtx("user-123") : null,
                action == ReceiptChangeAction.CREATED ? null : new ReceiptChangeContext(
                        "user-123", "Confirmed correction or lifecycle change", "user-123", time));
    }

    /** Verifies initial creation and its actor context. */
    @Test
    void recordsCreation() {
        ReceiptRevSnap after = new ReceiptRevSnap(UUID.randomUUID(), 1,
                ReceiptStatus.ACTIVE, contents("7.28"));
        ReceiptHistoryEntry history = entry(ReceiptChangeAction.CREATED, null, after);
        assertNull(history.before());
        assertNull(history.changeCtx());
        assertEquals("user-123", history.createCtx().requestedBy());
        assertEquals(after, history.after());
    }

    /** Verifies separate original and approved corrected values. */
    @Test
    void recordsApprovedCorrection() {
        UUID id = UUID.randomUUID();
        ReceiptRevSnap before = new ReceiptRevSnap(id, 1, ReceiptStatus.ACTIVE, contents("7.28"));
        ReceiptRevSnap after = new ReceiptRevSnap(id, 2, ReceiptStatus.ACTIVE, contents("7.29"));
        ReceiptHistoryEntry history = entry(ReceiptChangeAction.UPDATED, before, after);
        assertEquals(new BigDecimal("7.28"), history.before().contents().receiptTotals().finalTotal());
        assertEquals(new BigDecimal("7.29"), history.after().contents().receiptTotals().finalTotal());
        assertEquals("user-123", history.changeCtx().approvedBy());
        assertNull(history.createCtx());
    }

    /** Verifies deletion and restoration with unchanged contents. */
    @Test
    void recordsDeletionAndRestoration() {
        UUID id = UUID.randomUUID();
        ReceiptSnapshot values = contents("7.28");
        ReceiptRevSnap active = new ReceiptRevSnap(id, 1, ReceiptStatus.ACTIVE, values);
        ReceiptRevSnap deleted = new ReceiptRevSnap(id, 2, ReceiptStatus.DELETED, values);
        ReceiptRevSnap restored = new ReceiptRevSnap(id, 3, ReceiptStatus.ACTIVE, values);
        assertDoesNotThrow(() -> entry(ReceiptChangeAction.DELETED, active, deleted));
        assertDoesNotThrow(() -> entry(ReceiptChangeAction.RESTORED, deleted, restored));
    }

    /** Verifies that a history transition cannot skip revisions. */
    @Test
    void rejectsSkippedRevision() {
        UUID id = UUID.randomUUID();
        ReceiptSnapshot values = contents("7.28");
        assertThrows(IllegalArgumentException.class, () -> entry(ReceiptChangeAction.UPDATED,
                new ReceiptRevSnap(id, 1, ReceiptStatus.ACTIVE, values),
                new ReceiptRevSnap(id, 3, ReceiptStatus.ACTIVE, values)));
    }

    /** Verifies stable receipt identity across revisions. */
    @Test
    void rejectsChangedReceiptIdentity() {
        assertThrows(IllegalArgumentException.class, () -> entry(ReceiptChangeAction.UPDATED,
                new ReceiptRevSnap(UUID.randomUUID(), 1, ReceiptStatus.ACTIVE, contents("7.28")),
                new ReceiptRevSnap(UUID.randomUUID(), 2, ReceiptStatus.ACTIVE, contents("7.29"))));
    }

    /** Verifies deletion cannot conceal a content edit. */
    @Test
    void rejectsContentChangesDuringDeletion() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> entry(ReceiptChangeAction.DELETED,
                new ReceiptRevSnap(id, 1, ReceiptStatus.ACTIVE, contents("7.28")),
                new ReceiptRevSnap(id, 2, ReceiptStatus.DELETED, contents("7.29"))));
    }

    /** Verifies restoration requires a deleted previous revision. */
    @Test
    void rejectsRestorationOfActiveReceipt() {
        UUID id = UUID.randomUUID();
        ReceiptSnapshot values = contents("7.28");
        assertThrows(IllegalArgumentException.class, () -> entry(ReceiptChangeAction.RESTORED,
                new ReceiptRevSnap(id, 1, ReceiptStatus.ACTIVE, values),
                new ReceiptRevSnap(id, 2, ReceiptStatus.ACTIVE, values)));
    }

    /** Verifies request/result identity and mandatory context validation. */
    @Test
    void rejectsMismatchedRequestAndMissingApproval() {
        UUID id = UUID.randomUUID();
        ReceiptRevSnap before = new ReceiptRevSnap(id, 1, ReceiptStatus.ACTIVE, contents("7.28"));
        ReceiptRevSnap after = new ReceiptRevSnap(id, 2, ReceiptStatus.ACTIVE, contents("7.29"));
        ReceiptHistoryEntry valid = entry(ReceiptChangeAction.UPDATED, before, after);
        assertThrows(IllegalArgumentException.class, () -> new ReceiptHistoryEntry(
                valid.historyId(), new ReceiptWriteRequest(UUID.randomUUID(), valid.request().requestedAt()),
                valid.result(), before, after, null, valid.changeCtx()));
        assertThrows(NullPointerException.class, () -> new ReceiptHistoryEntry(
                valid.historyId(), valid.request(), valid.result(), before, after, null, null));
    }

    /** Verifies result metadata must describe the after snapshot. */
    @Test
    void rejectsResultSnapshotMismatch() {
        ReceiptRevSnap after = new ReceiptRevSnap(UUID.randomUUID(), 1,
                ReceiptStatus.ACTIVE, contents("7.28"));
        ReceiptHistoryEntry valid = entry(ReceiptChangeAction.CREATED, null, after);
        ReceiptWriteResult wrong = new ReceiptWriteResult(valid.request().requestId(),
                UUID.randomUUID(), 1, ReceiptChangeAction.CREATED, ReceiptStatus.ACTIVE,
                valid.result().committedAt());
        assertThrows(IllegalArgumentException.class, () -> new ReceiptHistoryEntry(
                valid.historyId(), valid.request(), wrong, null, after, valid.createCtx(), null));
    }
}
