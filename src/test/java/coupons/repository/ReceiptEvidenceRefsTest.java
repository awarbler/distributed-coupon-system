package coupons.repository;

import coupons.receipts.CouponTransaction;
import coupons.receipts.ReceiptTotals;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Tests evidence references and constructor compatibility. */
class ReceiptEvidenceRefsTest {
    /**
     * Creates empty transaction contents.
     * @return an empty example transaction
     */
    private CouponTransaction transaction() {
        return new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO, BigDecimal.ZERO));
    }

    /** Verifies isolation of supplied lists and capture into immutable revisions. */
    @Test
    void capturesEvidenceReferencesWithoutSharingMutableLists() {
        UUID evidenceId = UUID.randomUUID();
        List<UUID> supplied = new ArrayList<>(List.of(evidenceId));
        ReceiptRecord receipt = new ReceiptRecord(UUID.randomUUID(), ReceiptStatus.ACTIVE,
                1, transaction(), null, supplied);
        ReceiptRevSnap snapshot = ReceiptRevSnap.from(receipt);
        supplied.clear();
        assertEquals(List.of(evidenceId), receipt.evidenceIds());
        assertEquals(List.of(evidenceId), snapshot.evidenceIds());
        assertThrows(UnsupportedOperationException.class, () -> receipt.evidenceIds().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.evidenceIds().clear());
        List<UUID> directInput = new ArrayList<>(List.of(evidenceId));
        ReceiptRevSnap direct = new ReceiptRevSnap(receipt.transactionId(), 1,
                ReceiptStatus.ACTIVE, snapshot.contents(), null, directInput);
        directInput.clear();
        assertEquals(List.of(evidenceId), direct.evidenceIds());
    }

    /** Verifies four-argument constructor delegation for both record types. */
    @Test
    void fourArgumentConstructorsHaveUnknownMetadataAndEmptyEvidence() {
        UUID id = UUID.randomUUID();
        ReceiptRecord receipt = new ReceiptRecord(id, ReceiptStatus.ACTIVE, 1, transaction());
        ReceiptRevSnap snapshot = new ReceiptRevSnap(id, 1, ReceiptStatus.ACTIVE,
                ReceiptSnapshot.from(receipt.transaction()));
        assertNull(receipt.metadata());
        assertNull(snapshot.metadata());
        assertTrue(receipt.evidenceIds().isEmpty());
        assertTrue(snapshot.evidenceIds().isEmpty());
    }

    /** Verifies five-argument constructor delegation preserves metadata. */
    @Test
    void fiveArgumentConstructorsPreserveMetadataAndHaveEmptyEvidence() {
        UUID id = UUID.randomUUID();
        ReceiptMeta metadata = new ReceiptMeta("Walgreens", "000123", null, null);
        ReceiptRecord receipt = new ReceiptRecord(id, ReceiptStatus.ACTIVE, 1, transaction(), metadata);
        ReceiptRevSnap snapshot = new ReceiptRevSnap(id, 1, ReceiptStatus.ACTIVE,
                ReceiptSnapshot.from(receipt.transaction()), metadata);
        assertEquals(metadata, receipt.metadata());
        assertEquals(metadata, snapshot.metadata());
        assertTrue(receipt.evidenceIds().isEmpty());
        assertTrue(snapshot.evidenceIds().isEmpty());
    }

    /** Verifies duplicate IDs, null lists, and null elements are rejected by both types. */
    @Test
    void rejectsInvalidEvidenceLists() {
        UUID id = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();
        CouponTransaction transaction = transaction();
        ReceiptSnapshot contents = ReceiptSnapshot.from(transaction);
        assertThrows(IllegalArgumentException.class, () -> new ReceiptRecord(
                id, ReceiptStatus.ACTIVE, 1, transaction, null, List.of(evidenceId, evidenceId)));
        assertThrows(IllegalArgumentException.class, () -> new ReceiptRevSnap(
                id, 1, ReceiptStatus.ACTIVE, contents, null, List.of(evidenceId, evidenceId)));
        assertThrows(NullPointerException.class, () -> new ReceiptRecord(
                id, ReceiptStatus.ACTIVE, 1, transaction, null, null));
        assertThrows(NullPointerException.class, () -> new ReceiptRevSnap(
                id, 1, ReceiptStatus.ACTIVE, contents, null, null));
        assertThrows(NullPointerException.class, () -> new ReceiptRecord(
                id, ReceiptStatus.ACTIVE, 1, transaction, null, Arrays.asList(evidenceId, null)));
        assertThrows(NullPointerException.class, () -> new ReceiptRevSnap(
                id, 1, ReceiptStatus.ACTIVE, contents, null, Arrays.asList(evidenceId, null)));
    }

    /** Verifies earlier revisions retain their original evidence references. */
    @Test
    void laterRevisionDoesNotAlterEarlierReferences() {
        UUID id = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ReceiptSnapshot contents = ReceiptSnapshot.from(transaction());
        ReceiptRevSnap before = new ReceiptRevSnap(id, 1, ReceiptStatus.ACTIVE, contents, null, List.of(first));
        ReceiptRevSnap after = new ReceiptRevSnap(id, 2, ReceiptStatus.ACTIVE, contents, null, List.of(first, second));
        assertEquals(List.of(first), before.evidenceIds());
        assertEquals(List.of(first, second), after.evidenceIds());
        assertNotEquals(before, after);
    }

    /** Verifies lifecycle transitions preserve evidence while ordinary updates may append references. */
    @Test
    void historyPreservesEvidenceDuringDeletionAndRestoration() {
        UUID id = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ReceiptSnapshot contents = ReceiptSnapshot.from(transaction());
        Instant time = Instant.parse("2026-10-16T15:00:00Z");
        for (ReceiptChangeAction action : new ReceiptChangeAction[]{
                ReceiptChangeAction.UPDATED, ReceiptChangeAction.DELETED, ReceiptChangeAction.RESTORED}) {
            UUID requestId = UUID.randomUUID();
            ReceiptStatus previous = action == ReceiptChangeAction.RESTORED ? ReceiptStatus.DELETED : ReceiptStatus.ACTIVE;
            ReceiptStatus next = action == ReceiptChangeAction.DELETED ? ReceiptStatus.DELETED : ReceiptStatus.ACTIVE;
            ReceiptRevSnap before = new ReceiptRevSnap(id, 2, previous, contents, null, List.of(first));
            ReceiptRevSnap unchanged = new ReceiptRevSnap(id, 3, next, contents, null, List.of(first));
            ReceiptRevSnap appended = new ReceiptRevSnap(id, 3, next, contents, null, List.of(first, second));
            ReceiptWriteRequest request = new ReceiptWriteRequest(requestId, time);
            ReceiptWriteResult result = new ReceiptWriteResult(requestId, id, 3, action, next, time);
            ReceiptChangeContext context = new ReceiptChangeContext("user-123", "Review attachments", "user-123", time);
            assertDoesNotThrow(() -> new ReceiptHistoryEntry(UUID.randomUUID(), request,
                    result, before, unchanged, null, context));
            if (action == ReceiptChangeAction.UPDATED) {
                assertDoesNotThrow(() -> new ReceiptHistoryEntry(UUID.randomUUID(), request,
                        result, before, appended, null, context));
            } else {
                assertThrows(IllegalArgumentException.class, () -> new ReceiptHistoryEntry(UUID.randomUUID(),
                        request, result, before, appended, null, context));
            }
        }
    }
}
