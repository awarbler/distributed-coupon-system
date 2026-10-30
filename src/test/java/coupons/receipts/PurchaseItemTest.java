package coupons.receipts;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class PurchaseItemTest {

    @Test
    void separateReceiptLinesHaveDistinctIdsForTheSameProduct() {
        // Hypothetical separate receipt lines, not the actual Scott receipt.
        PurchaseItem first = new PurchaseItem(
                "Example product", "TEST-PRODUCT",
                new BigDecimal("5.00"), new BigDecimal("5.00"), 1
        );

        PurchaseItem second = new PurchaseItem(
                "Example product", "TEST-PRODUCT",
                new BigDecimal("5.00"), new BigDecimal("5.00"), 1
        );

        assertNotNull(first.getLineItemId());
        assertNotNull(second.getLineItemId());
        assertNotEquals(first.getLineItemId(), second.getLineItemId());
        assertEquals(first.getProductCode(), second.getProductCode());
    }

    @Test
    void correctionKeepsLineIdAndQuantityWithoutChangingOriginal() {
        UUID id = UUID.randomUUID();

        // One receipt line containing two units.
        PurchaseItem original = new PurchaseItem(
                id, "Scott", "TEST-SCOTT",
                new BigDecimal("5.00"), new BigDecimal("5.00"), 2
        );

        // Hypothetical price correction, preserving the line's identity.
        PurchaseItem corrected = new PurchaseItem(
                original.getLineItemId(),
                original.getProductName(),
                original.getProductCode(),
                original.getRegularUnitPrice(),
                new BigDecimal("4.75"),
                original.getQuantity()
        );

        assertNotSame(original, corrected);

        assertEquals(id, original.getLineItemId());
        assertEquals(id, corrected.getLineItemId());

        assertEquals(2, original.getQuantity());
        assertEquals(2, corrected.getQuantity());

        assertEquals(
                new BigDecimal("5.00"),
                original.getSaleUnitPrice()
        );
        assertEquals(
                new BigDecimal("4.75"),
                corrected.getSaleUnitPrice()
        );

        assertEquals(
                new BigDecimal("10.00"),
                original.calculateLineTotal()
        );
        assertEquals(
                new BigDecimal("9.50"),
                corrected.calculateLineTotal()
        );
    }

    @Test
    void rejectsNullLineId() {
        assertThrows(
                NullPointerException.class,
                () -> new PurchaseItem(
                        null, "Example product", "TEST-PRODUCT",
                        new BigDecimal("5.00"),
                        new BigDecimal("5.00"),
                        2
                )
        );
    }
}