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

    @Test void oldSerializedMetadataDefaultsNewEvidenceFieldsToUnknown(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var source = directory.resolve("coupons/repository/ReceiptMeta.java");
        java.nio.file.Files.createDirectories(source.getParent());
        java.nio.file.Files.writeString(source, """
                package coupons.repository;
                public record ReceiptMeta(String retailer, String receiptNumber,
                    java.time.LocalDateTime purchasedAt, String storeReference,
                    PurchaseChannel purchaseChannel) implements java.io.Serializable {
                    public enum PurchaseChannel { IN_STORE, ONLINE, UNKNOWN }
                }
                """);
        assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-d", directory.toString(), source.toString()));
        byte[] bytes;
        try (var loader = new java.net.URLClassLoader(new java.net.URL[]{directory.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            var old = loader.loadClass("coupons.repository.ReceiptMeta");
            var channel = loader.loadClass("coupons.repository.ReceiptMeta$PurchaseChannel");
            Object value = old.getConstructors()[0].newInstance("Walgreens", "000123", null, null, channel.getEnumConstants()[0]);
            var output = new java.io.ByteArrayOutputStream();
            try (var stream = new java.io.ObjectOutputStream(output)) { stream.writeObject(value); }
            bytes = output.toByteArray();
        }
        try (var stream = new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes))) {
            var current = (ReceiptMeta) stream.readObject();
            assertEquals("000123", current.receiptNumber());
            assertEquals(ReceiptMeta.PurchaseChannel.IN_STORE, current.purchaseChannel());
            assertEquals(ReceiptMeta.PrintedTotalBasis.UNKNOWN, current.printedTotalBasis());
            assertEquals(ReceiptMeta.PaymentEntryStatus.UNKNOWN, current.paymentEntryStatus());
        }
        var explicit = new ReceiptMeta("Walgreens", "test", null, null, ReceiptMeta.PurchaseChannel.IN_STORE,
                ReceiptMeta.PrintedTotalBasis.BEFORE_PAYMENT_FUNDING, ReceiptMeta.PaymentEntryStatus.COMPLETE);
        var bytesOut = new java.io.ByteArrayOutputStream();
        try (var stream = new java.io.ObjectOutputStream(bytesOut)) { stream.writeObject(explicit); }
        try (var stream = new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytesOut.toByteArray()))) {
            assertEquals(explicit, stream.readObject());
        }
    }

}
