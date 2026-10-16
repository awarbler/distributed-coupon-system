package coupons.repository;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Tests preservation and validation of receipt metadata. */
class ReceiptMetaTest {
    /** Verifies exact preservation of text and printed local time. */
    @Test
    void preservesRecordedValuesExactly() {
        LocalDateTime time = LocalDateTime.of(2026, 10, 15, 14, 35);
        ReceiptMeta metadata = new ReceiptMeta(" Walgreens ",
                "0476-1234-0748-2610-1303", time, "0021");
        assertEquals(" Walgreens ", metadata.retailer());
        assertEquals("0476-1234-0748-2610-1303", metadata.receiptNumber());
        assertEquals(time, metadata.purchasedAt());
        assertEquals("0021", metadata.storeReference());
    }

    /** Verifies that unknown optional information is not invented. */
    @Test
    void allowsUnknownOptionalFields() {
        ReceiptMeta metadata = new ReceiptMeta("Walgreens", null, null, null);
        assertNull(metadata.receiptNumber());
        assertNull(metadata.purchasedAt());
        assertNull(metadata.storeReference());
    }

    /** Verifies preservation of leading zeros and surrounding whitespace. */
    @Test
    void preservesReferenceFormatting() {
        ReceiptMeta metadata = new ReceiptMeta("Example retailer", " 000123-04 ", null, " 0007 ");
        assertEquals(" 000123-04 ", metadata.receiptNumber());
        assertEquals(" 0007 ", metadata.storeReference());
    }

    /** Verifies rejection of a missing retailer. */
    @Test
    void rejectsNullRetailer() {
        assertThrows(NullPointerException.class, () -> new ReceiptMeta(null, null, null, null));
    }

    /** Verifies rejection of supplied blank text. */
    @Test
    void rejectsBlankTextFields() {
        for (String blank : new String[]{"", "   "}) {
            assertThrows(IllegalArgumentException.class, () -> new ReceiptMeta(blank, null, null, null));
            assertThrows(IllegalArgumentException.class, () -> new ReceiptMeta("Walgreens", blank, null, null));
            assertThrows(IllegalArgumentException.class, () -> new ReceiptMeta("Walgreens", null, null, blank));
        }
    }

    /** Verifies value equality for reconstructed metadata. */
    @Test
    void reconstructedMetadataIsEqual() {
        LocalDateTime time = LocalDateTime.of(2026, 10, 15, 14, 35);
        ReceiptMeta original = new ReceiptMeta("Walgreens", "000123", time, "0021");
        ReceiptMeta copy = new ReceiptMeta("Walgreens", "000123", time, "0021");
        assertEquals(original, copy);
        assertEquals(original.hashCode(), copy.hashCode());
    }

    /** Verifies corrections do not mutate the original values. */
    @Test
    void correctionPreservesOriginalValues() {
        ReceiptMeta original = new ReceiptMeta("Walgreens", "000123", null, "0021");
        ReceiptMeta corrected = new ReceiptMeta(original.retailer(), "000124",
                original.purchasedAt(), original.storeReference());
        assertEquals("000123", original.receiptNumber());
        assertEquals("000124", corrected.receiptNumber());
        assertNotEquals(original, corrected);
    }
}
