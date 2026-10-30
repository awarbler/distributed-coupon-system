package coupons.cli;

import coupons.receipts.PaymentType;
import coupons.receipts.ReceiptReport;
import coupons.receipts.ReceiptWorksheetReport;
import coupons.receipts.SavingsReport;

import coupons.promotions.Coupon;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Scanner;
import java.time.LocalDate;
import coupons.repository.InMemoryTransactionRepository;
import coupons.repository.ReceiptMeta;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Automated console checks supplement, but do not replace, personal receipt verification. */
class ReceiptEntryAppTest {
    /** Verifies unknown optional data, reported totals, and unchanged retry output. */
    @Test
    void entersReceiptWithoutInventingUnknownDetailsAndRetries() {
        String script = String.join("\n", "000123", "9:40 am", "", "H-E-B", "manual-user",
                "", "Milk", "invalid", "5.000", "1", "0", "0", "n", "n",
                "0.270", "0", "0", "n", "5.280", "n", "n", "n", "n", "SAVE",
                "2", "000123", "8", "HISTORY", "1", "8", "RETRY", "9") + "\n";
        var bytes = new ByteArrayOutputStream();
        new ReceiptEntryApp(new Scanner(script), new PrintStream(bytes)).run();
        String output = bytes.toString().replaceAll(" +", " ");
        assertTrue(output.contains("SKU / UPC: Unknown"));
        assertTrue(output.contains("Enter a nonnegative amount."));
        assertTrue(output.contains("Time: 9:40 AM"));
        assertTrue(output.contains("History entries: 1"));
        assertTrue(output.contains("Retailer-Reported Total: 5.28"));
        assertTrue(output.contains("Calculated Checkout: 5.27"));
        assertTrue(output.contains("Difference (Reported Minus Calculated): 0.01"));
        String first = output.lines().filter(line -> line.startsWith("Save result: "))
                .findFirst().orElseThrow().substring("Save result: ".length());
        assertTrue(output.contains("Original result: " + first));
    }

    @Test void zeroPaymentNeedsNoPositiveTenderAndSurvivesRestart(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        for(String method : java.util.List.of("1", "2", "3", "4", "0", "5")) {
            var file=dir.resolve("zero-"+method+".bin");
            String script=String.join("\n","Zero-"+method,"9:40 am","10/4/2026","H-E-B","owner",
                    "","Milk","5.00","1","0","0","n","n","0","0","5","",method)
                    + "\n" + (method.equals("0")||method.equals("5") ? "" : "0\n")
                    + "0\nn\nn\nn\nn\nSAVE\n9\n";
            var bytes=new ByteArrayOutputStream();
            try(var repo=coupons.repository.InMemoryTransactionRepository.openLocal(file)) {
                new ReceiptEntryApp(new Scanner(script),new PrintStream(bytes),repo).run();
                assertTrue(bytes.toString().contains("Save result:"),bytes.toString());
                assertFalse(bytes.toString().contains("Enter a positive amount."),bytes.toString());
            }
            try(var repo=coupons.repository.InMemoryTransactionRepository.openLocal(file)) {
                var receipt=repo.findPossibleDuplicates(new coupons.repository.ReceiptMeta("H-E-B","Zero-"+method,null,null)).get(0);
                assertEquals(0,receipt.transaction().getReceiptTotals().getFinalTotal().signum());
                assertEquals(1,receipt.transaction().getPayments().size()); // Existing WC use, no fabricated zero tender.
                assertEquals(PaymentType.WALGREENS_CASH,receipt.transaction().getPayments().get(0).getPaymentType());
                assertTrue(SavingsReport.calculate(java.util.List.of(receipt)).paymentsKnown());
                var display=new ByteArrayOutputStream();ReceiptReport.print(receipt,new PrintStream(display));
                assertFalse(display.toString().contains("tender not recorded"),display.toString());
                ReceiptWorksheetReport.compact(receipt,new PrintStream(display));
                assertTrue(display.toString().matches("(?s).*Retailer total due +\\| +\\$0.00.*"),display.toString());
            }
        }
    }

    /** Verifies incomplete entry never saves a fabricated receipt. */
    @Test
    void incompleteInputDoesNotSave() {
        var bytes = new ByteArrayOutputStream();
        new ReceiptEntryApp(new Scanner("1\nuser\n"), new PrintStream(bytes)).run();
        assertTrue(bytes.toString().contains("incomplete entry was not saved"));
        assertFalse(bytes.toString().contains("Save result:"));
    }

    /** Verifies malformed dates re-prompt the field instead of discarding entry. */
    @Test
    void invalidRewardDateReturnsToMenuWithoutSaving() {
        var bytes = new ByteArrayOutputStream();
        new ReceiptEntryApp(new Scanner("5\n2\nSource\n5.00\nnot-a-date\n\n\ny\n5\n9\n"), new PrintStream(bytes)).run();
        assertTrue(bytes.toString().contains("Enter a date such as 10/4/2026 or 2026-10-04"));
        assertTrue(bytes.toString().contains("RR Coupon Recorded."));
    }

    /** Verifies both supported date forms and rejection of impossible calendar dates. */
    @Test
    void acceptsUSAndISOReceiptDatesWithStrictValidation() {
        for (String date : new String[]{"10/4/2026", "2026-10-04"}) {
            String script = String.join("\n", "Date-test", "9:40 am", "2/30/2026", date, "Store", "owner",
                    "", "Milk", "5.00", "1", "0", "0", "n", "",
                    "0.27", "0", "0", "n", "5.27", "n", "n", "n", "n", "SAVE", "9") + "\n";
            var repo = new InMemoryTransactionRepository();
            var bytes = new ByteArrayOutputStream();
            new ReceiptEntryApp(new Scanner(script), new PrintStream(bytes), repo).run();
            String output = bytes.toString().replaceAll(" +", " ");
            assertTrue(output.contains("Enter a date such as"), output);
            var records = repo.findPossibleDuplicates(new ReceiptMeta("Store", "Date-test", null, null));
            assertEquals(1, records.size(), output);
            assertEquals(LocalDate.of(2026, 10, 4), records.get(0).transaction().getObservations().purchaseDate());
            assertEquals(1, records.get(0).transaction().getPurchaseItems().size());
        }
    }

    /** Verifies escape from price and quantity preserves previous items and reaches tax. */
    @Test
    void cancelsAccidentalTaxItemAtPriceOrQuantity() {
        for (String cancelledFields : new String[]{"DONE", "0\n0\nDONE"}) {
            String script = String.join("\n", "Escape-test", "", "", "Store", "owner",
                    "", "Milk", "5.00", "1", "0", "0", "n", "y",
                    "", "sales tax", cancelledFields,
                    "0.27", "0", "0", "n", "5.27", "n", "n", "n", "n", "SAVE", "9") + "\n";
            var repo = new InMemoryTransactionRepository();
            var bytes = new ByteArrayOutputStream();
            new ReceiptEntryApp(new Scanner(script), new PrintStream(bytes), repo).run();
            String output = bytes.toString().replaceAll(" +", " ");
            assertTrue(output.contains("Current item discarded."), output);
            var records = repo.findPossibleDuplicates(new ReceiptMeta("Store", "Escape-test", null, null));
            assertEquals(1, records.size(), output);
            assertEquals(1, records.get(0).transaction().getPurchaseItems().size());
            assertEquals("Milk", records.get(0).transaction().getPurchaseItems().get(0).getProductName());
            assertEquals(new java.math.BigDecimal("0.27"), records.get(0).transaction().getReceiptTotals().getTaxAmount());
        }
    }

    /** Verifies cancelling the first item requires a real purchased item before checkout. */
    @Test
    void cancellingFirstItemDoesNotPermitAnEmptyReceipt() {
        String script = String.join("\n", "First-test", "", "", "Store", "owner", "DONE",
                "", "Milk", "5.00", "1", "0", "0", "n", "n",
                "0", "0", "0", "n", "5.00", "n", "n", "n", "n", "SAVE", "9") + "\n";
        var repo = new InMemoryTransactionRepository();
        var bytes = new ByteArrayOutputStream();
        new ReceiptEntryApp(new Scanner(script), new PrintStream(bytes), repo).run();
        assertTrue(bytes.toString().contains("Enter at least one purchased item before checkout."));
        assertEquals(1, repo.findPossibleDuplicates(new ReceiptMeta("Store", "First-test", null, null)).size());
    }

    /** Verifies BACK during an edit keeps the original item instead of inserting null. */
    @Test
    void cancellingItemEditPreservesOriginal() {
        String script = String.join("\n", "Edit-test", "", "", "Store", "owner",
                "", "Milk", "5.00", "1", "0", "0", "n", "n",
                "0", "0", "0", "n", "5.00", "n", "n", "n", "n",
                "EDIT", "ITEM", "ALL", "1", "", "Changed name", "DONE", "SAVE", "9") + "\n";
        var repo = new InMemoryTransactionRepository();
        var bytes = new ByteArrayOutputStream();
        new ReceiptEntryApp(new Scanner(script), new PrintStream(bytes), repo).run();
        var records = repo.findPossibleDuplicates(new ReceiptMeta("Store", "Edit-test", null, null));
        assertEquals(1, records.size(), bytes.toString());
        assertEquals("Milk", records.get(0).transaction().getPurchaseItems().get(0).getProductName());
    }
}
