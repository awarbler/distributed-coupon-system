package coupons.cli;

import coupons.receipts.ReceiptObservations;

import coupons.promotions.BenefitKind;
import coupons.promotions.PromotionType;
import coupons.promotions.RebateStatus;

import coupons.rewards.Reward;

import coupons.repository.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.Scanner;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the revised receipt-facing questions through real console sessions. */
class ReceiptEarningNavigationTest {
    private record Session(String output, ReceiptRecord receipt, InMemoryTransactionRepository repository) { }

    private Session enter(String... answers) {
        String prefix = String.join("\n", "Nav-Receipt", "9:40 am", "10/4/2026", "Walgreens", "aj",
                "", "neutrogena", "5.00", "2", "0", "0", "n", "n", "0", "0", "0", "n", "10.00");
        String script = prefix + "\n" + String.join("\n", answers)
                + "\n" + String.join("\n", "y", "0", "0", "0", "0%", "0", "0", "0", "0", "n", "SAVE", "8", "RETRY", "9") + "\n";
        var repo = new InMemoryTransactionRepository();
        var bytes = new ByteArrayOutputStream();
        new ReceiptEntryApp(new Scanner(script), new PrintStream(bytes), repo).run();
        String output = bytes.toString();
        assertFalse(output.contains("Operation not saved:"), output);
        var receipts = repo.findPossibleDuplicates(new ReceiptMeta("Walgreens", "Nav-Receipt", null, null));
        assertEquals(1, receipts.size(), output);
        return new Session(output, receipts.get(0), repo);
    }

    @Test
    void changesAssociatedProductsWithoutInventingAdditionalLines() {
        Session s = enter("y", "Register Reward", "Buy 2 get 7 RR", "1", "2", "U", "BACK", "1",
                "7.00", "5", "", "", "", "n");
        var earning = s.receipt().transaction().getObservations().earnings().get(0);
        assertTrue(s.output().contains("REGISTER REWARD STATUS — choose 5 if the coupon printed."),s.output());
        assertTrue(s.output().contains("5. Register Reward Printed"),s.output());
        assertEquals(RebateStatus.PAID,earning.status());
        assertEquals(ReceiptObservations.Scope.ITEM, earning.scope());
        assertEquals("Walgreens", earning.provider());
        assertEquals(PromotionType.BUY, earning.requirement().type());
        assertEquals(new BigDecimal("2"), earning.requirement().amount());
        assertEquals(new BigDecimal("7.00"), earning.confirmed().amount());
        assertEquals(1, earning.lineItemIds().size());
        assertEquals(1, s.repository().history().findByReceiptId(s.receipt().transactionId()).size());
        assertFalse(s.output().contains("Currency code"));
        assertFalse(s.output().contains("Provider / reward program"));
    }

    @Test
    void correctsEnteredAmountBeforeConfirmingIt() {
        Session s = enter("y", "Ibotta", "Separate offer", "3", "0", "3.05", "BACK", "2.95", "1", "", "", "n");
        var earning = s.receipt().transaction().getObservations().earnings().get(0);
        assertEquals("Ibotta", earning.provider());
        assertEquals(new BigDecimal("2.95"), earning.expected().amount());
        assertNull(earning.confirmed());
        assertTrue(s.output().contains("Back to the previous earning question"));
    }

    @Test
    void keepsFetchPointsAndValuationOnOneEarningThroughHistoryAndRetry() {
        Session s = enter("y", "BACK", "y", "Fetch", "Receipt scan", "3", "0", "25", "4", "",
                "0.025", "my spreadsheet", "", "", "n");
        var earnings = s.receipt().transaction().getObservations().earnings();
        assertEquals(1, earnings.size());
        var e = earnings.get(0);
        assertEquals(BenefitKind.POINTS, e.confirmed().kind());
        assertEquals(new BigDecimal("25"), e.confirmed().amount());
        assertEquals(new BigDecimal("0.025"), e.valuation().amount());
        assertNull(e.confirmed().currency());
        var historical = s.repository().history().findByRevision(s.receipt().transactionId(), 1).orElseThrow();
        assertEquals(e, historical.after().contents().observations().earnings().get(0));
        assertEquals(1, s.repository().history().findByReceiptId(s.receipt().transactionId()).size());
        assertTrue(s.output().contains("Original result:"));
        assertFalse(s.output().split("PROVIDER BALANCE CHECK")[0].contains("Target"));
        assertFalse(s.output().split("PROVIDER BALANCE CHECK")[0].contains("Walmart Cash"));
        assertFalse(s.output().split("PROVIDER BALANCE CHECK")[0].contains("CVS ExtraBucks"));
    }
    @Test
    void viewListsExistingRewardsWithoutCreatingAnotherEarning() {
        Session s = enter("y", "Register Reward", "Buy 2 get 7", "1", "2", "1", "7.00", "5", "", "", "",
                "VIEW", "y", "Fetch", "Receipt scan", "3", "0", "25", "4", "", "0.025", "Spreadsheet", "", "", "2", "n");
        assertEquals(2, s.receipt().transaction().getObservations().earnings().size());
        assertTrue(s.output().contains("REWARDS ALREADY ENTERED — 1"));
        assertTrue(s.output().contains("REWARDS ALREADY ENTERED — 2"));
        assertTrue(s.output().contains("Fetch — 25 points — Whole receipt"));
        assertTrue(s.output().contains("Register Reward Printed"));
    }
    @Test
    void receiptAndOtherRequirementsKeepTheirExplicitMeaningInHistory() {
        for (String choice : java.util.List.of("3", "4")) {
            Session s = enter("y", "Fetch", "Receipt scan", choice, "0", "25", "4", "", "0.025", "Spreadsheet", "", "", "n");
            var earning = s.receipt().transaction().getObservations().earnings().get(0);
            assertEquals(choice.equals("3") ? "Receipt" : "Other", earning.requirement().category());
            assertNull(earning.requirement().amount());
            assertEquals(earning, s.repository().history().findByRevision(s.receipt().transactionId(), 1)
                    .orElseThrow().after().contents().observations().earnings().get(0));
            assertTrue(s.output().contains("3. Receipt\n4. Other"));
            assertFalse(s.output().contains("Unknown / no stated requirement"));
        }
    }
}
