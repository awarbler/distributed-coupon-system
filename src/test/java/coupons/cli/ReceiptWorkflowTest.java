package coupons.cli;

import coupons.receipts.ReceiptObservations;
import coupons.receipts.ReceiptReport;
import coupons.receipts.TransactionCalculator;

import coupons.promotions.BenefitKind;

import coupons.rewards.Reward;

import coupons.repository.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Scanner;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** End-to-end console acceptance tests supplement personal receipt verification. */
class ReceiptWorkflowTest {
    /** Verifies the actual requested entry order, linked savings, retrieval, and retry. */
    @Test
    void entersWalgreensReceiptAndRetrievesEveryRecordedSection() {
        String script = String.join("\n",
                "1115-13212274126100403", "9:40 am", "", "Walgreens", "receipt-owner",
                "07050111090", "Neutrogenta", "7.79", "2", "3.60", "1", "1", "4.00", "2", "y", "Register Reward", "Neutrogena offer", "1", "2", "1", "7.00", "5", "", "", "", "n", "y",
                "01920099717", "Lysol travel wipes", "3.29", "2", "0", "0", "y", "Register Reward", "Lysol offer", "1", "2", "1", "5.00", "5", "", "", "expired", "n", "n",
                "1.04", "1", "2.00", "Palmolive", "10.00", "", "n", "3.60",
                "y", "Walgreens Cash", "Receipt earning", "3", "0", "0.80", "4", "", "", "", "n",
                "y", "10.80", "6.00", "16.80", "43%", "247.75", "0.80", "10.00", "238.55",
                "n", "SAVE", "2", "1115-13212274126100403", "8", "HISTORY", "1", "8", "RETRY", "9") + "\n";
        var repo = new InMemoryTransactionRepository();
        var bytes = new ByteArrayOutputStream();
        new ReceiptEntryApp(new Scanner(script), new PrintStream(bytes), repo).run();
        String output = bytes.toString().replaceAll(" +", " ");
        assertFalse(output.contains("Operation not saved:"), output);
        assertTrue(output.contains("Save result:"), output);
        assertTrue(output.contains("Retailer-Reported Total: 3.60"), output);
        assertFalse(output.contains("Calculated Checkout:"), output);
        assertFalse(output.contains("Difference (Reported Minus Calculated):"), output);
        assertTrue(output.contains("History entries: 1"), output);
        assertTrue(output.contains("Original result:"), output);
        var saved = repo.findPossibleDuplicates(new ReceiptMeta("Walgreens", "1115-13212274126100403", null, null));
        assertEquals(1, saved.size());
        var transaction = saved.get(0).transaction();
        var manufacturerCoupon = transaction.getAppliedCoupons().stream()
                .filter(c -> c.getPrintedLabel().equals("Manufacturer coupon")).findFirst().orElseThrow();
        assertEquals(Integer.valueOf(2), manufacturerCoupon.getRequiredQuantity());
        var receiptOutput = new ByteArrayOutputStream();
        ReceiptReport.print(saved.get(0), new PrintStream(receiptOutput));
        String formatted = receiptOutput.toString();
        assertFalse(formatted.contains("Receipt UUID"));
        assertFalse(formatted.contains("scope="));
        assertFalse(formatted.contains("confirmed entitlement"));
        assertEquals(2, formatted.lines().filter(line -> line.contains("Status") && line.contains("Register Reward Printed")).count());
        assertTrue(formatted.contains("Requires 2 products per coupon"));
        assertTrue(formatted.replaceAll(" +", " ").contains("SUBTOTAL (Calculated) 12.56"));
        assertTrue(formatted.replaceAll(" +", " ").contains("TOTAL Before Walgreens Cash 13.60"));
        assertFalse(formatted.contains("Status: Register Reward Printed"));
        assertTrue(formatted.matches("(?s).*Recorded Earned +\\| 7.00 RR .*"));
        assertFalse(formatted.contains("  Offer:"));
        assertFalse(formatted.contains("  Requires:"));
        assertFalse(formatted.contains("Explained amount"));
        assertFalse(formatted.contains("Unexplained difference"));
        assertFalse(formatted.contains("Breakdown:"));
        var observations = transaction.getObservations();
        assertEquals("9:40 am", observations.printedTime());
        assertNull(observations.purchaseDate());
        assertEquals(3, observations.earnings().size());
        assertEquals(BenefitKind.STORE_COUPON, observations.earnings().get(0).confirmed().kind());
        assertEquals(BenefitKind.STORE_REWARD, observations.earnings().get(2).confirmed().kind());
        assertFalse(output.contains("Expected: CASH 7"), output);
        assertEquals("expired", observations.earnings().get(1).recordedLifecycle());
        assertEquals(1, observations.unidentifiedRR().size());
        assertEquals(new BigDecimal("247.75"), observations.walgreensCash().startingBalance());
        assertEquals(new BigDecimal("238.55"), observations.walgreensCash().endingBalance());
        assertEquals(new BigDecimal("0.80"), observations.walgreensCash().earned());
        assertEquals(List.of(new BigDecimal("10.80"), new BigDecimal("6.00"), new BigDecimal("16.80")),
                observations.savings().stream().map(ReceiptObservations.SavingsCategory::reportedAmount).toList());
        assertTrue(observations.savings().get(0).inclusions().isEmpty());
        assertEquals(new BigDecimal("43"), observations.receiptSavingValuePercent());
        assertFalse(observations.savings().get(0).explanationComplete());
        assertEquals(ReceiptSnapshot.from(transaction), repo.history().findByRevision(saved.get(0).transactionId(), 1)
                .orElseThrow().after().contents());
    }

    /** Verifies a pre-save item edit changes the draft, not an already committed receipt. */
    @Test
    void editsDraftWithoutSavingAnIntermediateRevision() {
        String script = String.join("\n", "NEW", "Receipt-A", "", "", "Store", "owner",
                "", "Item", "5.00", "1", "0", "0", "n", "n",
                "0", "0", "0", "n", "4.00", "n", "n", "n", "n",
                "EDIT", "ITEM", "ALL", "1", "", "Item corrected", "4.00", "1", "0", "0",
                "SAVE", "9") + "\n";
        var repo = new InMemoryTransactionRepository();
        var bytes = new ByteArrayOutputStream();
        new ReceiptEntryApp(new Scanner(script), new PrintStream(bytes), repo).run();
        assertFalse(bytes.toString().contains("Operation not saved:"), bytes.toString());
        var receipt = repo.findPossibleDuplicates(new ReceiptMeta("Store", "Receipt-A", null, null)).get(0);
        assertEquals("Item corrected", receipt.transaction().getPurchaseItems().get(0).getProductName());
        assertEquals(new BigDecimal("4.00"), new TransactionCalculator().calculateExpectedCheckoutAmount(receipt.transaction()));
        assertEquals(1, repo.history().findByReceiptId(receipt.transactionId()).size());
    }

    /** Verifies two distinct expected providers persist and remain outside checkout. */
    @Test
    void entersCoverGirlWithDistinctCashOffers() {
        String script = String.join("\n", "CoverGirl-Receipt", "", "", "Store", "owner",
                "61630453095", "covergirl", "9.29", "1", "0", "1", "2.00", "",
                "y", "Ibotta", "CoverGirl offer", "3", "1", "3.05", "1", "", "",
                "y", "PayPal", "Separate promotional offer", "3", "1", "1.90", "1", "", "", "n", "n",
                "0", "0", "0", "n", "7.29", "n",
                "n", "n", "n", "SAVE", "2", "#1", "9") + "\n";
        var repo = new InMemoryTransactionRepository();
        var bytes = new ByteArrayOutputStream();
        new ReceiptEntryApp(new Scanner(script), new PrintStream(bytes), repo).run();
        assertFalse(bytes.toString().contains("Operation not saved:"), bytes.toString());
        assertTrue(bytes.toString().replaceAll(" +", " ").contains("After Cash Rebates, Before Tax 2.34"), bytes.toString());
        var receipt = repo.findPossibleDuplicates(new ReceiptMeta("Store", "CoverGirl-Receipt", null, null)).get(0);
        assertEquals(new BigDecimal("7.29"), new TransactionCalculator().calculateExpectedCheckoutAmount(receipt.transaction()));
        assertEquals(2, receipt.transaction().getObservations().earnings().size());
        assertTrue(receipt.transaction().getObservations().earnings().stream().allMatch(value -> value.confirmed() == null));
    }
}
