package coupons.receipts;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class WalgreensImageImportTest {
    @TempDir Path directory;
    private static final String TOP="""
        Walgreens
        239 1701 0022 10/08/2026 9:06 AM
        << BUY 1 GET 1 EQUAL/LESS FOR 50% OFF >>
        GILLETTE SKN GRD RAZOR 1UP
        04740069058 A 6.49
        REGULAR PRICE 12.99
        MYWALGREENS SAVINGS 6.50
        RETURN VALUE 6.81
        PANTENE DAILY MOIST RNW COND 12OZ
        08087819495 A 5.49 SALE
        REGULAR PRICE 6.79
        MYWALGREENS SAVINGS 1.30
        RETURN VALUE 5.49
        PANTENE MOIST RENEWAL SHAM 12.6Z
        08087819556 A 5.49 SALE
        REGULAR PRICE 6.79
        MYWALGREENS SAVINGS 1.30
        RETURN VALUE 2.49
        PANTENE CURL DEFINING MOUSE 6.6OZ
        08087804311 A 8.00 SALE
        REGULAR PRICE 9.29
        MYWALGREENS SAVINGS 1.29
        RETURN VALUE 6.50
        VENUS SMOOTH RZR+1CT
        04740014036 A 13.49
        RETURN VALUE 7.17
        MFG ECPN -3.00
        MFG ECPN -3.00
        MFG ECPN -3.00
        MFG ECPN -1.50
        SUBTOTAL 28.46
        SALES TAX A=8.25% 2.35
        TOTAL 30.81
        Coin Rounding -0.01
        CASH 31.00
        CHANGE 0.20
        """;
    private static final String MIDDLE="""
        VENUS SMOOTH RZR+1CT
        04740014036 A 13.49
        RETURN VALUE 7.17
        MFG ECPN -3.00
        MFG ECPN -3.00
        MFG ECPN -3.00
        MFG ECPN -1.50
        SUBTOTAL 28.46
        SALES TAX A=8.25% 2.35
        TOTAL 30.81
        Coin Rounding -0.01
        CASH 31.00
        CHANGE 0.20
        RFN# 0445-8221-7010-2610-0803
        MYWALGREENS SAVINGS 10.39
        MFG COUPON SAVINGS 10.50
        TOTAL SAVINGS $20.89
        SAVINGS VALUE 42%
        OPENING BALANCE $271.15
        EARNED THIS VISIT $3.80
        CLOSING BALANCE $274.95
        """;
    @Test void recognizesSkuAfterFsaAndOcrNoisePrefixes() throws Exception {
        var fields=WalgreensImageImport.parse(java.util.List.of("""
                Walgreens
                10/08/2026 9:34 AM
                AQUAPHOR HEALING BALM
                FSA 07214003127 A 6.74
                REGULAR PRICE 13.49
                MYWALGREENS SAVINGS 6.75
                RETURN VALUE 8.24
                NIVEA NOURSHNG BODY LTN
                oF 07214002471 A 14,49
                RETURN VALUE 0.00
                MEG CPN -4,99
                SUBTOTAL 3.23
                Pe SALES TAX 0.27
                | TOTAL 3.50
                TOTAL FSA ITEMS 6.74
                """));
        assertEquals("3.50",fields.getProperty("receipt.total"));
        assertEquals("2",fields.getProperty("item.count"));
        assertEquals("07214003127",fields.getProperty("item.1.sku"));
        assertEquals("07214002471",fields.getProperty("item.2.sku"));
    }

    @Test void threeOverlappingPhotosKeepFiveItemsEightDeductionsAndOneEarning() throws Exception {
        var p=WalgreensImageImport.parse(List.of(TOP,MIDDLE,MIDDLE.substring(MIDDLE.indexOf("RFN#"))));
        assertEquals("5",p.getProperty("item.count"));assertEquals("8",p.getProperty("coupon.count"));
        assertEquals("12.99",p.getProperty("item.1.enteredUnitPrice"));
        assertEquals("6.50",p.getProperty("coupon.5.amount"));assertEquals("SALE_PROMOTION",p.getProperty("coupon.5.kind"));
        assertEquals("5.49",new java.math.BigDecimal(p.getProperty("item.3.enteredUnitPrice")).subtract(new java.math.BigDecimal(p.getProperty("coupon.7.amount"))).toPlainString());
        assertNull(p.getProperty("item.5.regularUnitPrice"));
        assertEquals("30.81",p.getProperty("receipt.total"));assertEquals("30.80",p.getProperty("payment.1.amount"));
        assertEquals("3.80",p.getProperty("earning.1.amount"));assertEquals("1",p.getProperty("earning.count"));
        assertEquals("0445-8221-7010-2610-0803",p.getProperty("receipt.number"));
        assertEquals("2026-10-08",p.getProperty("receipt.date"));
        assertEquals("",p.getProperty("coupon.1.lines"));
        assertTrue(p.stringPropertyNames().stream().noneMatch(k->k.startsWith("seen.")));
        // Exercise the existing strict importer and calculator, not a second financial implementation.
        Path photo=directory.resolve("original.jpeg");Files.write(photo,new byte[]{1,2,3});
        p.setProperty("format","receipt-import-v1");p.setProperty("image.count","1");p.setProperty("image.1.path",photo.toString());
        Path manifest=directory.resolve("receipt.properties");try(var writer=Files.newBufferedWriter(manifest)){p.store(writer,"fixture");}
        var imported=ReceiptImportReader.read(manifest);
        assertEquals(5,imported.items().size());assertEquals(8,imported.discounts().size());
    }
    @Test void separateReceiptsAndUnreadableProductsAreRejected() {
        assertThrows(java.io.IOException.class,()->WalgreensImageImport.parse(List.of(TOP,MIDDLE.replace("0445-8221-7010-2610-0803","1151-3212-2783-2610-0403"),MIDDLE)));
        assertThrows(java.io.IOException.class,()->WalgreensImageImport.parse(List.of(TOP,TOP.replace("10/08/2026","10/09/2026"))));
        assertThrows(java.io.IOException.class,()->WalgreensImageImport.parse(List.of("Unreadable receipt")));
    }
    @Test void quantityAndExtendedPriceArePreservedForMultiUnitLines() throws Exception {
        var p=WalgreensImageImport.parse(List.of("CHARMIN 21.96\n03077219261 QTY:4\nREGULAR PRICE 7.49\nMYWALGREENS SAVINGS 8.00\nRETURN VALUE 5.49\nSUBTOTAL 21.96\nTOTAL 21.96"));
        assertEquals("4",p.getProperty("item.1.quantity"));assertEquals("7.49",p.getProperty("item.1.enteredUnitPrice"));
        assertEquals("8.00",p.getProperty("coupon.1.amount"));assertEquals("CHARMIN",p.getProperty("item.1.description"));
    }
    @Test void repeatedPurchasesOnOnePageAreNotDeduplicatedAndReturnPricesAreIgnored() throws Exception {
        String item="SOAP\n01234567890 A 5.00\nRETURN VALUE 1.00\n";
        var p=WalgreensImageImport.parse(List.of(item+item+"SUBTOTAL 10.00\nTOTAL 10.00"));
        assertEquals("2",p.getProperty("item.count"));assertEquals("5.00",p.getProperty("item.1.enteredUnitPrice"));
    }
    @Test void reusedExtractionDoesNotNeedOcrAndDuplicatePhotosAreRejected() throws Exception {
        Path image=directory.resolve("photo.jpeg");Files.writeString(image,"cached fixture");
        String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(image)));
        UUID id=UUID.nameUUIDFromBytes(hash.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Path root=directory.resolve("imports"),folder=root.resolve(id.toString());Files.createDirectories(folder);
        Path manifest=folder.resolve("receipt.properties");Files.writeString(manifest,"retained extraction");
        assertEquals(manifest,new WalgreensImageImport().prepare(List.of(image),root));
        assertEquals("retained extraction",Files.readString(manifest));
        assertThrows(java.io.IOException.class,()->new WalgreensImageImport().prepare(List.of(image,image),root));
    }
    @Test void missingCashChangeDoesNotInventPaymentAndMissingTotalStaysMissing() throws Exception {
        var p=WalgreensImageImport.parse(List.of(TOP.substring(0,TOP.indexOf("TOTAL 30.81"))));
        assertNull(p.getProperty("payment.count"));assertNull(p.getProperty("receipt.total"));
    }
}
