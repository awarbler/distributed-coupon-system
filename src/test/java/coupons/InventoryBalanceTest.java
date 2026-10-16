package coupons;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Tests whole-package inventory balances. */
class InventoryBalanceTest {
    /** Verifies preservation of identity and package quantity. */
    @Test
    void preservesRecordedValues() {
        UUID inventoryId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        InventoryBalance balance = new InventoryBalance(inventoryId, productId, 3, 1);
        assertEquals(inventoryId, balance.inventoryId());
        assertEquals(productId, balance.productId());
        assertEquals(3, balance.quantityOnHand());
        assertEquals(1L, balance.revision());
    }

    /** Verifies zero packages is a valid recorded balance. */
    @Test
    void allowsZeroPackages() {
        assertDoesNotThrow(() -> new InventoryBalance(UUID.randomUUID(), UUID.randomUUID(), 0, 1));
    }

    /** Verifies separate versions leave the previous balance unchanged. */
    @Test
    void preservesOriginalBalanceDuringCorrection() {
        InventoryBalance before = new InventoryBalance(UUID.randomUUID(), UUID.randomUUID(), 3, 1);
        InventoryBalance after = new InventoryBalance(before.inventoryId(), before.productId(), 2, 2);
        assertEquals(before.inventoryId(), after.inventoryId());
        assertEquals(before.productId(), after.productId());
        assertEquals(3, before.quantityOnHand());
        assertEquals(2, after.quantityOnHand());
        assertEquals(1L, before.revision());
        assertEquals(2L, after.revision());
    }

    /** Verifies rejection of missing identifiers. */
    @Test
    void rejectsNullIdentifiers() {
        assertThrows(NullPointerException.class, () -> new InventoryBalance(null, UUID.randomUUID(), 1, 1));
        assertThrows(NullPointerException.class, () -> new InventoryBalance(UUID.randomUUID(), null, 1, 1));
    }

    /** Verifies rejection of negative quantities and invalid revisions. */
    @Test
    void rejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new InventoryBalance(UUID.randomUUID(), UUID.randomUUID(), -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new InventoryBalance(UUID.randomUUID(), UUID.randomUUID(), 1, 0));
    }
}
