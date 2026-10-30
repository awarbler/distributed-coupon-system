package coupons.cli;

import coupons.receipts.Payment;
import coupons.receipts.PaymentType;
import coupons.receipts.TransactionCalculator;

import coupons.promotions.BenefitKind;
import coupons.promotions.PromotionType;
import coupons.promotions.RebateBenefit;
import coupons.promotions.RebateStatus;

import coupons.receipts.ReceiptObservations.*;
import coupons.repository.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Protects qualification, valuation, and corrections through the proposed entry flow. */
class ReceiptCliRevisionTest {
    @Test
    void itemBackChangesQuantityWithoutDroppingItsNameOrCode() {
        String script = String.join("\n", "Back-item", "", "", "Walgreens", "aj", "000123", "Milk", "5.00", "2",
                "BACK", "1", "0", "0", "n", "n", "0", "0", "0", "n", "5.00", "n", "y", "0", "0", "0", "0%", "0", "0", "0", "0", "n", "SAVE", "9") + "\n";
        var repo = new InMemoryTransactionRepository();
        var bytes = new ByteArrayOutputStream();
        new ReceiptEntryApp(new Scanner(script), new PrintStream(bytes), repo).run();
        var records = repo.findPossibleDuplicates(new ReceiptMeta("Walgreens", "Back-item", null, null));
        assertEquals(1, records.size(), bytes.toString());
        var item = records.get(0).transaction().getPurchaseItems().get(0);
        assertEquals("000123", item.getProductCode());
        assertEquals("Milk", item.getProductName());
        assertEquals(1, item.getQuantity());
    }

    @Test
    void unknownQualificationAndValuationStayUnknownForExistingCallers() {
        var benefit = new RebateBenefit(BenefitKind.POINTS, new BigDecimal("25"), null, "Fetch");
        var earning = new Earning(UUID.randomUUID(), EarningKind.THIRD_PARTY_REBATE, "Fetch", null,
                Scope.RECEIPT, List.of(), null, benefit, RebateStatus.CREDITED, null, null, null, List.of());
        assertNull(earning.requirement());
        assertNull(earning.valuation());
        assertEquals(new BigDecimal("25"), earning.confirmed().amount());
    }

    @Test
    void requirementsValidateStructureWithoutClaimingEligibility() {
        assertThrows(IllegalArgumentException.class, () -> new OfferRequirement(PromotionType.BUY, new BigDecimal("1.5")));
        assertThrows(IllegalArgumentException.class, () -> new OfferRequirement(PromotionType.SPEND, BigDecimal.ZERO));
        var requirement = new OfferRequirement(PromotionType.SPEND, new BigDecimal("35.000"));
        assertEquals(new BigDecimal("35.000"), requirement.amount());
        assertThrows(IllegalArgumentException.class, () -> new RecordedValuation(new BigDecimal("-1"), Currency.getInstance("USD"), "spreadsheet"));
        assertThrows(IllegalArgumentException.class, () -> new RecordedValuation(BigDecimal.ONE, Currency.getInstance("USD"), " "));
    }

    @Test
    void otherGiftCardDoesNotBecomeCashOrASecondEarning() {
        String script = String.join("\n", "Gift-receipt", "", "", "Walgreens", "aj", "", "Item", "5.00", "1", "0", "0",
                "y", "Other", "3", "Different provider", "Gift card offer", "2", "35.00", "2", "1", "5.00", "1",
                "4.50", "my conservative valuation", "", "", "n", "n", "0", "0", "0", "n", "5.00", "n", "y", "0", "0", "0", "0%", "0", "0", "0", "0", "n", "SAVE", "9") + "\n";
        var repo = new InMemoryTransactionRepository();
        var bytes = new ByteArrayOutputStream();
        new ReceiptEntryApp(new Scanner(script), new PrintStream(bytes), repo).run();
        var records = repo.findPossibleDuplicates(new ReceiptMeta("Walgreens", "Gift-receipt", null, null));
        assertEquals(1, records.size(), bytes.toString());
        var transaction = records.get(0).transaction();
        var earnings = transaction.getObservations().earnings();
        assertEquals(1, earnings.size());
        assertEquals(BenefitKind.GIFT_CARD, earnings.get(0).expected().kind());
        assertNull(earnings.get(0).confirmed());
        assertEquals(new BigDecimal("4.50"), earnings.get(0).valuation().amount());
        assertEquals(new BigDecimal("5.00"), new TransactionCalculator().calculateExpectedCheckoutAmount(transaction));
    }
    /** Reproduces the confusing savings entry and attachment trap without losing the receipt. */
    @Test
    void requiredSavingsUsesUsdAndUnreadableOrBlankAttachmentCanBeSkipped() {
        for (String pathAnswers : new String[]{"", "/does/not/exist/receipt.png\nBACK"}) {
            String script = String.join("\n", "Savings-test", "", "", "Walgreens", "aj", "", "Milk", "5.00", "1", "0", "0",
                    "n", "n", "0", "0", "0", "n", "5.00", "n",
                    "y", "10.80", "0", "10.80", "43%", "0", "0", "0", "0", "y", pathAnswers, "SAVE", "2", "#1", "9") + "\n";
            var repo = new InMemoryTransactionRepository();
            var bytes = new ByteArrayOutputStream();
            new ReceiptEntryApp(new Scanner(script), new PrintStream(bytes), repo).run();
            var records = repo.findPossibleDuplicates(new ReceiptMeta("Walgreens", "Savings-test", null, null));
            assertEquals(1, records.size(), bytes.toString());
            var category = records.get(0).transaction().getObservations().savings().get(0);
            assertEquals("myWalgreens Savings", category.printedLabel());
            assertEquals(Currency.getInstance("USD"), category.currency());
            assertEquals(new BigDecimal("10.80"), category.reportedAmount());
            assertFalse(category.explanationComplete());
            assertTrue(records.get(0).evidenceIds().isEmpty());
            assertFalse(bytes.toString().contains("Currency code"));
            assertFalse(bytes.toString().contains("Have you identified everything"));
        }
    }

    /** Store rewards are typed as restricted value and retain compatibility with historical snapshots. */
    @Test
    void retailerRewardKindsAreDistinctFromCash() {
        for (var kind : List.of(EarningKind.REGISTER_REWARD, EarningKind.WALGREENS_CASH)) {
            BenefitKind form = kind == EarningKind.REGISTER_REWARD ? BenefitKind.STORE_COUPON : BenefitKind.STORE_REWARD;
            var value = new RebateBenefit(form, new BigDecimal("7.00"), Currency.getInstance("USD"), "Walgreens");
            var earning = new Earning(UUID.randomUUID(), kind, "Walgreens", null, Scope.RECEIPT, List.of(),
                    null, value, RebateStatus.PAID, null, null, null, List.of());
            assertNotEquals(BenefitKind.CASH, earning.confirmed().kind());
            var old = new RebateBenefit(BenefitKind.CASH, new BigDecimal("7.00"), Currency.getInstance("USD"), null);
            assertDoesNotThrow(() -> new Earning(UUID.randomUUID(), kind, "Walgreens", null, Scope.RECEIPT, List.of(),
                    null, old, RebateStatus.PAID, null, null, null, List.of()));
        }
    }
    @Test
    void acknowledgesChosenShortDateAndPreservesItOnRetrieval() {
        String script = String.join("\n", "Date-short", "", "10/4/26", "Walgreens", "aj", "", "Milk", "5.00", "1", "0", "0",
                "n", "n", "0", "0", "0", "n", "5.00", "n", "y", "0", "0", "0", "0%", "0", "0", "0", "0", "n", "SAVE", "2", "#1", "9") + "\n";
        var repo = new InMemoryTransactionRepository();
        var bytes = new ByteArrayOutputStream();
        new ReceiptEntryApp(new Scanner(script), new PrintStream(bytes), repo).run();
        var records = repo.findPossibleDuplicates(new ReceiptMeta("Walgreens", "Date-short", null, null));
        assertEquals(1, records.size(), bytes.toString());
        assertEquals(java.time.LocalDate.of(2026, 10, 4), records.get(0).transaction().getObservations().purchaseDate());
        assertTrue(bytes.toString().contains("Date understood as: October 4, 2026 (2026-10-04)"));
        assertFalse(bytes.toString().contains("Date choice"));
    }
    @Test
    void recordsEveryTenderChoiceAndRestoresOtherPaymentNotes() {
        String script = String.join("\n", "Tender-test", "", "", "Walgreens", "aj", "", "Item", "3.60", "1", "0", "0", "n", "n",
                "0", "0", "0", "y", "1", "1.00", "y", "2", "1.00", "y", "3", "1.00", "", "y", "4", "0.60", "Store credit", "", "n",
                "3.60", "n", "y", "0", "0", "0", "0%", "0", "0", "0", "0", "n", "SAVE", "2", "#1", "3", "9") + "\n";
        var repo = new InMemoryTransactionRepository();
        var bytes = new ByteArrayOutputStream();
        new ReceiptEntryApp(new Scanner(script), new PrintStream(bytes), repo).run();
        var records = repo.findPossibleDuplicates(new ReceiptMeta("Walgreens", "Tender-test", null, null));
        assertEquals(1, records.size(), bytes.toString());
        var receipt = records.get(0);
        var payments = receipt.transaction().getPayments();
        assertEquals(List.of(PaymentType.CASH, PaymentType.CARD, PaymentType.GIFT_CARD, PaymentType.OTHER),
                payments.stream().map(Payment::getPaymentType).toList());
        assertEquals("Store credit", payments.get(3).getNotes());
        assertEquals("Store credit", repo.history().findByRevision(receipt.transactionId(), 1).orElseThrow()
                .after().contents().payments().get(3).notes());
        assertEquals(new BigDecimal("3.60"), new TransactionCalculator().calculateExpectedCheckoutAmount(receipt.transaction()));
        assertEquals(1, repo.history().findByReceiptId(receipt.transactionId()).size());
        assertFalse(bytes.toString().contains("AMOUNT DUE (calculated)"));
        assertFalse(bytes.toString().contains("Calculated Checkout:"));
    }

    @Test
    void redemptionSourceAndPurposeSurviveSaveRetrievalAndHistory() {
        String script = String.join("\n", "Used-test", "", "", "Walgreens", "aj", "", "Item", "15.60", "1", "0", "0", "n", "n",
                "0", "1", "2.00", "Palmolive", "10.00", "This shopping trip", "y", "1", "3.60", "n", "3.60",
                "n", "y", "0", "0", "0", "0%", "0", "0", "0", "0", "n", "SAVE", "2", "#1", "3", "9") + "\n";
        var repo = new InMemoryTransactionRepository();
        var bytes = new ByteArrayOutputStream();
        new ReceiptEntryApp(new Scanner(script), new PrintStream(bytes), repo).run();
        var records = repo.findPossibleDuplicates(new ReceiptMeta("Walgreens", "Used-test", null, null));
        assertEquals(1, records.size(), bytes.toString());
        var receipt = records.get(0);
        var transaction = receipt.transaction();
        assertEquals("Palmolive", transaction.getObservations().unidentifiedRR().get(0).recordedDescription());
        assertEquals("This shopping trip", transaction.getPayments().get(0).getNotes());
        assertEquals(ReceiptSnapshot.from(transaction), repo.history().findByRevision(receipt.transactionId(), 1).orElseThrow().after().contents());
        assertTrue(bytes.toString().contains("Source: Palmolive"));
        assertTrue(bytes.toString().contains("Used For: This Shopping Trip"));
        assertEquals(1, repo.history().findByReceiptId(receipt.transactionId()).size());
    }
    @Test
    void threeRegisterRewardsRetainSeparateAmountsSourcesAndIdsAfterRetry() {
        String script = String.join("\n", "Three-RR", "", "", "Walgreens", "aj", "", "Item", "20.00", "1", "0", "0", "n", "n",
                "0", "3", "2.00", "Palmolive", "7.00", "Neutrogena", "5.00", "Lysol",
                "0", "n", "6.00", "n", "y", "0", "0", "0", "0%", "0", "0", "0", "0", "n", "SAVE", "2", "#1", "3", "9") + "\n";
        var repo = new InMemoryTransactionRepository();
        var bytes = new ByteArrayOutputStream();
        new ReceiptEntryApp(new Scanner(script), new PrintStream(bytes), repo).run();
        var records = repo.findPossibleDuplicates(new ReceiptMeta("Walgreens", "Three-RR", null, null));
        assertEquals(1, records.size(), bytes.toString());
        var receipt = records.get(0);
        var used = receipt.transaction().getObservations().unidentifiedRR();
        assertEquals(List.of("Palmolive", "Neutrogena", "Lysol"), used.stream().map(UnidentifiedRR::recordedDescription).toList());
        assertEquals(List.of(new BigDecimal("2.00"), new BigDecimal("7.00"), new BigDecimal("5.00")),
                used.stream().map(UnidentifiedRR::amount).toList());
        assertEquals(3, used.stream().map(UnidentifiedRR::eventId).distinct().count());
        assertEquals(new BigDecimal("14.00"), new TransactionCalculator().calculateRegisterRewardsRedeemed(receipt.transaction()));
        assertEquals(new BigDecimal("6.00"), new TransactionCalculator().calculateExpectedCheckoutAmount(receipt.transaction()));
        assertEquals(ReceiptSnapshot.from(receipt.transaction()), repo.history().findByRevision(receipt.transactionId(), 1).orElseThrow().after().contents());
        assertEquals(1, repo.history().findByReceiptId(receipt.transactionId()).size());
        assertTrue(bytes.toString().contains("Total Register Rewards used: 14.00"));
        assertFalse(bytes.toString().contains("already recorded in this session"));
        assertFalse(bytes.toString().contains("Recorded reward UUID"));
        assertTrue(bytes.toString().contains("Amount:"));
        assertTrue(bytes.toString().contains("Source (e.g. Palmolive; blank unknown):"));
    }
    @Test
    void exitFromReceiptReviewCanBeConfirmedOrDeclinedWithoutSavingAccidentally() {
        for (boolean confirm : new boolean[]{true, false}) {
            String script = String.join("\n", "Exit-test", "", "", "Walgreens", "aj", "", "Item", "5.00", "1", "0", "0", "n", "n",
                    "0", "0", "0", "n", "5.00", "n", "y", "0", "0", "0", "0%", "0", "0", "0", "0", "n", "EXIT", confirm ? "y" : "n") + "\n"
                    + (confirm ? "" : "SAVE\n9\n");
            var repo = new InMemoryTransactionRepository();
            var bytes = new ByteArrayOutputStream();
            new ReceiptEntryApp(new Scanner(script), new PrintStream(bytes), repo).run();
            assertTrue(bytes.toString().contains("1. Save\n2. Edit\n3. Itemized\n4. Totals\n5. Accounts\n6. Issues\n7. Keep draft — return to main menu (resume later)\n8. Cancel draft — return to main menu (discard unsaved changes)\n9. Exit program"));
            assertEquals(confirm ? 0 : 1, repo.findPossibleDuplicates(new ReceiptMeta("Walgreens", "Exit-test", null, null)).size(), bytes.toString());
            if (confirm) {
                assertTrue(bytes.toString().contains("CLI closed."));
                assertFalse(bytes.toString().contains("Input ended"));
            } else assertTrue(bytes.toString().contains("Save result:"));
        }
    }
    @Test
    void directlySelectsPaymentWithoutAutomaticallyPrintingProviderBalances() {
        String script = String.join("\n", "BALANCE SETTINGS", "10/4/26", "3", "1", "247.75", "3", "2", "2.00", "9",
                "Balance-receipt", "9:40 am", "10/4/26", "Walgreens", "aj", "", "Item", "15.60", "1", "0", "0", "n", "n",
                "0", "1", "2.00", "Palmolive", "10.00", "", "1", "3.60", "n", "3.60",
                "y", "Walgreens Cash", "Receipt earning", "3", "0", "0.80", "4", "", "", "", "n",
                "y", "0", "0", "0", "0%", "0", "0", "0", "0", "n", "SAVE", "2", "#1", "3", "9") + "\n";
        var repo = new InMemoryTransactionRepository();
        var bytes = new ByteArrayOutputStream();
        new ReceiptEntryApp(new Scanner(script), new PrintStream(bytes), repo).run();
        var records = repo.findPossibleDuplicates(new ReceiptMeta("Walgreens", "Balance-receipt", null, null));
        assertEquals(1, records.size(), bytes.toString());
        assertEquals(PaymentType.CASH, records.get(0).transaction().getPayments().get(1).getPaymentType());
        String output = bytes.toString().replaceAll(" +", " ");
        assertFalse(output.contains("PROVIDER BALANCE CHECK"), output);
        assertFalse(output.contains("Record how you paid"));
        assertEquals(1, repo.history().findByReceiptId(records.get(0).transactionId()).size());
    }
    @Test
    void clearerMqPromptsAcceptFourOffTwoWithoutRepeatingTheQuestion() {
        String script = String.join("\n", "MQ-labels", "", "", "Walgreens", "aj", "", "Item", "10.00", "2", "0", "1", "4.00", "4 off 2",
                "n", "n", "0", "0", "0", "5", "16.00", "n", "y", "0", "0", "0", "0%", "0", "0", "0", "0", "n", "SAVE", "9") + "\n";
        var repo = new InMemoryTransactionRepository();
        var bytes = new ByteArrayOutputStream();
        new ReceiptEntryApp(new Scanner(script), new PrintStream(bytes), repo).run();
        var records = repo.findPossibleDuplicates(new ReceiptMeta("Walgreens", "MQ-labels", null, null));
        assertEquals(1, records.size(), bytes.toString());
        var coupon = records.get(0).transaction().getAppliedCoupons().get(0);
        assertEquals(Integer.valueOf(2), coupon.getRequiredQuantity());
        assertEquals(new BigDecimal("4.00"), coupon.getAppliedAmount());
        String output = bytes.toString();
        assertTrue(output.contains("Separately entered sale/promotion reductions and store coupons"));
        assertTrue(output.contains("Quantity of MQs (0 if none):"));
        assertTrue(output.contains("Value of MQs:"));
        assertTrue(output.contains("Add rebate for current item [y/N; VIEW lists rewards already entered]:"));
        assertEquals(1, output.lines().filter(line -> line.equals("Items required per MQ (e.g. 2; blank unknown):")).count());
        assertFalse(output.contains("Enter a positive whole number"));
    }
}
