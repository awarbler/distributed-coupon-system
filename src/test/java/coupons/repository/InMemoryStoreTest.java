package coupons.repository;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Tests the atomic-publication foundation independently of receipt writes. */
class InMemoryStoreTest {
    /** Verifies invalid replacement construction cannot publish partial state. */
    @Test
    void failureDoesNotPublishState() {
        InMemoryStore store = new InMemoryStore();
        synchronized (store) {
            InMemoryStore.State before = store.state;
            assertThrows(NullPointerException.class, () -> {
                InMemoryStore.State replacement = new InMemoryStore.State(
                        Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), null);
                store.state = replacement;
            });
            assertSame(before, store.state);
        }
    }

    /** Verifies publication isolates supplied maps and preserves older states. */
    @Test
    void publishedMapsAreIndependentAndUnmodifiable() {
        InMemoryStore store = new InMemoryStore();
        InMemoryStore.State before;
        synchronized (store) {
            before = store.state;
        }
        UUID receipt = UUID.randomUUID();
        ReceiptSnapshot contents = new ReceiptSnapshot(java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), new ReceiptTotalsSnap(java.math.BigDecimal.ZERO,
                        java.math.BigDecimal.ZERO));
        ReceiptRevSnap revision = new ReceiptRevSnap(receipt, 1, ReceiptStatus.ACTIVE, contents);
        Map<UUID, ReceiptRevSnap> supplied = new HashMap<>();
        supplied.put(receipt, revision);
        synchronized (store) {
            store.state = new InMemoryStore.State(supplied, Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        supplied.clear();
        synchronized (store) {
            assertEquals(revision, store.state.receipts().get(receipt));
            assertTrue(before.receipts().isEmpty());
            assertThrows(UnsupportedOperationException.class, () -> store.state.receipts().clear());
        }
    }
}
