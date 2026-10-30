package coupons.repository;

import coupons.receipts.PurchaseItem;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Tests immutable purchase-line snapshots. */
class PurchaseLineSnapTest {
    /** Verifies exact capture of all purchase-line fields. */
    @Test
    void capturesPurchaseLineValuesExactly() {
        UUID id = UUID.randomUUID();
        PurchaseItem item = new PurchaseItem(id, "Scott", "TEST-SCOTT",
                new BigDecimal("5.000"), new BigDecimal("4.750"), 2);
        PurchaseLineSnap snap = PurchaseLineSnap.from(item);
        assertEquals(id, snap.lineItemId());
        assertEquals("Scott", snap.productName());
        assertEquals("TEST-SCOTT", snap.productCode());
        assertEquals(new BigDecimal("5.000"), snap.regularUnitPrice());
        assertEquals(new BigDecimal("4.750"), snap.saleUnitPrice());
        assertEquals(2, snap.quantity());
    }

    /** Verifies separate original and corrected values with shared identity. */
    @Test
    void preservesOriginalAndCorrectedSnapshots() {
        PurchaseItem original = new PurchaseItem("Scott", "TEST-SCOTT",
                new BigDecimal("5.00"), new BigDecimal("5.00"), 2);
        PurchaseLineSnap before = PurchaseLineSnap.from(original);
        PurchaseItem corrected = new PurchaseItem(original.getLineItemId(),
                original.getProductName(), original.getProductCode(),
                original.getRegularUnitPrice(), new BigDecimal("4.75"), 2);
        PurchaseLineSnap after = PurchaseLineSnap.from(corrected);
        assertEquals(before.lineItemId(), after.lineItemId());
        assertEquals(new BigDecimal("5.00"), before.saleUnitPrice());
        assertEquals(new BigDecimal("4.75"), after.saleUnitPrice());
        assertNotEquals(before, after);
    }

    /** Verifies value equality of equivalent captures. */
    @Test
    void equivalentCapturesAreEqual() {
        PurchaseItem item = new PurchaseItem("Example", "TEST",
                new BigDecimal("5.00"), new BigDecimal("4.00"), 1);
        assertEquals(PurchaseLineSnap.from(item), PurchaseLineSnap.from(item));
    }

    /** Verifies rejection of a missing source. */
    @Test
    void rejectsNullSource() {
        assertThrows(NullPointerException.class, () -> PurchaseLineSnap.from(null));
    }

    /** Verifies rejection of an invalid quantity. */
    @Test
    void rejectsInvalidQuantity() {
        assertThrows(IllegalArgumentException.class, () -> new PurchaseLineSnap(
                UUID.randomUUID(), "Example", "TEST",
                new BigDecimal("5.00"), new BigDecimal("4.00"), 0));
    }

    /** Verifies rejection of negative regular and sale prices. */
    @Test
    void rejectsNegativePrices() {
        assertThrows(IllegalArgumentException.class, () -> new PurchaseLineSnap(
                UUID.randomUUID(), "Example", "TEST",
                new BigDecimal("-1.00"), new BigDecimal("4.00"), 1));
        assertThrows(IllegalArgumentException.class, () -> new PurchaseLineSnap(
                UUID.randomUUID(), "Example", "TEST",
                new BigDecimal("5.00"), new BigDecimal("-1.00"), 1));
    }
}
