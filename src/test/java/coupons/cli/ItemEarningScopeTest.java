package coupons.cli;

import coupons.receipts.PurchaseItem;
import coupons.receipts.ReceiptObservations;
import coupons.receipts.TransactionCalculator;

import coupons.rewards.Reward;

import coupons.repository.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ItemEarningScopeTest {
    private String script(String scope, String deferred) {
        return String.join("\n", "NEW", "HS", "953am", "10/4/2026", "Walgreens", "AJ",
                "01", "H&S shampoo", "7", "1", "0", "0", "y", "Register Reward", "Buy 2 H&S get 4 RR",
                "1", "2", scope, "4.00", "5", "", "", "", "n", "y",
                "02", "H&S conditioner", "7", "1", "0", "0", "n", "n") + "\n"
                + deferred + String.join("\n", "0", "0", "0", "n", "14", "n",
                "y", "0", "0", "0", "0", "0", "0", "0", "0", "n", "SAVE", "3", "9") + "\n";
    }

    private ReceiptRecord run(InMemoryTransactionRepository repo, String script) {
        var bytes = new ByteArrayOutputStream();
        new ReceiptEntryApp(new Scanner(script), new PrintStream(bytes), repo).run();
        var records = repo.findPossibleDuplicates(new ReceiptMeta("Walgreens", "HS", null, null));
        assertEquals(1, records.size(), bytes.toString());
        assertFalse(bytes.toString().contains("Operation not saved:"), bytes.toString());
        assertTrue(bytes.toString().contains("2. Select multiple products after all items are entered"));
        return records.get(0);
    }

    @Test void deferredRewardLinksBothProductsOnceAndSurvivesRestart(@TempDir Path directory) throws Exception {
        var file = directory.resolve("receipts.bin");
        java.util.UUID id;
        ReceiptObservations.Earning original;
        try (var repo = InMemoryTransactionRepository.openLocal(file)) {
            // Reject one product, duplicate products and an unavailable product before selecting both.
            var receipt = run(repo, script("2", "1\n1,1\n1,3\nA\n"));
            id = receipt.transactionId();
            var tx = receipt.transaction();
            assertEquals(1, tx.getObservations().earnings().size());
            original = tx.getObservations().earnings().get(0);
            assertEquals(ReceiptObservations.Scope.MULTI_ITEM, original.scope());
            assertEquals(tx.getPurchaseItems().stream().map(PurchaseItem::getLineItemId).toList(), original.lineItemIds());
            assertEquals(new BigDecimal("4.00"), original.confirmed().amount());
            assertEquals(new BigDecimal("14"), new TransactionCalculator().calculateExpectedCheckoutAmount(tx));
            assertEquals(1, repo.history().findByReceiptId(id).size());
        }
        try (var repo = InMemoryTransactionRepository.openLocal(file)) {
            assertEquals(List.of(original), repo.findById(id).orElseThrow().transaction().getObservations().earnings());
            assertEquals(List.of(original), repo.history().findByRevision(id, 1).orElseThrow().after().contents().observations().earnings());
        }
    }

    @Test void currentItemWholeReceiptAndUnknownRemainDistinct() {
        for (String choice : List.of("1", "3", "4")) {
            var repo = new InMemoryTransactionRepository();
            var receipt = run(repo, script(choice, ""));
            var earning = receipt.transaction().getObservations().earnings().get(0);
            assertEquals(switch (choice) {
                case "1" -> ReceiptObservations.Scope.ITEM;
                case "3" -> ReceiptObservations.Scope.RECEIPT;
                default -> ReceiptObservations.Scope.UNKNOWN;
            }, earning.scope());
            assertEquals(choice.equals("1") ? List.of(receipt.transaction().getPurchaseItems().get(0).getLineItemId()) : List.of(), earning.lineItemIds());
        }
    }

    @Test void backFromAmountCanCancelDeferralWithoutLeavingPendingAssociation() {
        var repo = new InMemoryTransactionRepository();
        var receipt = run(repo, script("2\nBACK\n1", ""));
        assertEquals(ReceiptObservations.Scope.ITEM, receipt.transaction().getObservations().earnings().get(0).scope());
    }
}
