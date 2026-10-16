package coupons.repository;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Tests the atomic-publication foundation independently of receipt writes. */
class InMemoryStoreTest {
    /** Verifies failed builders leave the committed state unchanged. */
    @Test
    void failureDoesNotPublishState() {
        InMemoryStore store = new InMemoryStore();
        InMemoryStore.State before = store.snapshot();
        assertThrows(IllegalArgumentException.class, () -> store.commit(previous -> {
            throw new IllegalArgumentException("Rejected write");
        }));
        assertSame(before, store.snapshot());
        assertThrows(NullPointerException.class, () -> store.commit(previous -> null));
        assertSame(before, store.snapshot());
    }

    /** Verifies publication isolates supplied maps and preserves older states. */
    @Test
    void publishedMapsAreIndependentAndUnmodifiable() {
        InMemoryStore store = new InMemoryStore();
        InMemoryStore.State before = store.snapshot();
        UUID receipt = UUID.randomUUID();
        ReceiptSnapshot contents = new ReceiptSnapshot(java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), new ReceiptTotalsSnap(java.math.BigDecimal.ZERO,
                        java.math.BigDecimal.ZERO));
        ReceiptRevSnap revision = new ReceiptRevSnap(receipt, 1, ReceiptStatus.ACTIVE, contents);
        Map<UUID, ReceiptRevSnap> supplied = new HashMap<>();
        supplied.put(receipt, revision);
        store.commit(previous -> new InMemoryStore.State(supplied, Map.of(), Map.of(), Map.of(), Map.of()));
        supplied.clear();
        assertEquals(revision, store.snapshot().receipts().get(receipt));
        assertTrue(before.receipts().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> store.snapshot().receipts().clear());
    }
}
