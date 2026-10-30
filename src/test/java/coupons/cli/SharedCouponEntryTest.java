package coupons.cli;

import coupons.receipts.ReceiptObservations;
import coupons.receipts.TransactionCalculator;

import coupons.promotions.Coupon;

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

class SharedCouponEntryTest {
    private String run(InMemoryTransactionRepository repo, String script) {
        var bytes = new ByteArrayOutputStream();
        new ReceiptEntryApp(new Scanner(script), new PrintStream(bytes), repo).run();
        return bytes.toString();
    }

    private String entry(String review) {
        return String.join("\n", "NEW", "Oct4", "953am", "10/4/2026", "Walgreens", "AJ",
                "03077219261", "Charmin", "5.49", "4", "0", "1", "1.00", "", "n", "y",
                "03077221410", "H&S shampoo", "7.00", "1", "0", "0", "n", "y",
                "0003077221411", "H&S conditioner", "7.00", "1", "0", "0", "n", "n",
                "COUPONS", "y", "2,2", "4,3", "2", "2,3", "H&S digital coupon", "3.00", "2",
                "1.73", "1", "7.00", "Neutrogena", "0", "2", "26.69", "n", "26.69", "n",
                "y", "10.98", "11.00", "21.98", "38", "0", "0", "0", "0", "n", review, "9") + "\n";
    }

    @Test void newReceiptLinksTwoLinesAndDeductsCouponOnce() {
        var repo = new InMemoryTransactionRepository();
        String output = run(repo, entry("SAVE"));
        assertTrue(output.contains("Save result:"), output);
        assertTrue(output.contains("Choose at least two different existing item numbers"));
        var receipt = repo.findPossibleDuplicates(new ReceiptMeta("Walgreens", "Oct4", null, null)).get(0);
        var tx = receipt.transaction();
        var shared = tx.getAppliedCoupons().stream().filter(c -> c.getLineItemIds().size() == 2).findFirst().orElseThrow();
        assertEquals(List.of(tx.getPurchaseItems().get(1).getLineItemId(), tx.getPurchaseItems().get(2).getLineItemId()), shared.getLineItemIds());
        assertEquals(2, shared.getRequiredQuantity());
        assertEquals(2, tx.getAppliedCoupons().size());
        assertEquals(new BigDecimal("4.00"), new TransactionCalculator().calculateCouponTotal(tx));
        assertEquals(new BigDecimal("26.69"), new TransactionCalculator().calculateExpectedCheckoutAmount(tx));
        assertTrue(output.contains("Receipt lines 2, 3"), output);
    }

    @Test void draftCorrectionReplacesSharedCouponInsteadOfAddingAnotherDeduction() {
        var repo = new InMemoryTransactionRepository();
        String output = run(repo, entry("EDIT\nCOUPONS\nALL\nCORRECT\n1\n2,3\nCorrected H&S label\n3.00\n2\nSAVE"));
        assertTrue(output.contains("Save result:"), output);
        var receipt = repo.findPossibleDuplicates(new ReceiptMeta("Walgreens", "Oct4", null, null)).get(0);
        assertEquals(2, receipt.transaction().getAppliedCoupons().size());
        assertEquals(new BigDecimal("26.69"), new TransactionCalculator().calculateExpectedCheckoutAmount(receipt.transaction()));
        assertEquals(1, repo.history().findByReceiptId(receipt.transactionId()).size());
    }

    @Test void deletesSharedCouponOnlyAfterConfirmation() {
        var repo=new InMemoryTransactionRepository();
        String output=run(repo,entry("EDIT\nCOUPONS\nALL\nDELETE\n1\ny\nSAVE"));
        assertTrue(output.contains("Coupon deleted from draft"),output);
        var receipt=repo.findPossibleDuplicates(new ReceiptMeta("Walgreens","Oct4",null,null)).get(0);
        assertEquals(1,receipt.transaction().getAppliedCoupons().size());
        assertEquals(new BigDecimal("1.00"),new TransactionCalculator().calculateCouponTotal(receipt.transaction()));
    }
    @Test void decliningDeletionRetainsSharedCoupon() {
        var repo=new InMemoryTransactionRepository();
        String output=run(repo,entry("EDIT\nCOUPONS\nALL\nDELETE\n1\nn\nSAVE"));
        assertTrue(output.contains("Coupon retained."),output);
        var receipt=repo.findPossibleDuplicates(new ReceiptMeta("Walgreens","Oct4",null,null)).get(0);
        assertEquals(2,receipt.transaction().getAppliedCoupons().size());
    }

    @Test void savedCorrectionPreservesCouponIdHistoryAndLinksAcrossRestart(@TempDir Path directory) throws Exception {
        var file = directory.resolve("receipts.bin");
        java.util.UUID receiptId, couponId;
        try (var repo = InMemoryTransactionRepository.openLocal(file)) {
            run(repo, entry("SAVE"));
            var receipt = repo.findPossibleDuplicates(new ReceiptMeta("Walgreens", "Oct4", null, null)).get(0);
            receiptId = receipt.transactionId();
            var before = ReceiptSnapshot.from(receipt.transaction());
            var coupon = before.appliedCoupons().stream().filter(c -> c.getLineItemIds().size() == 2).findFirst().orElseThrow();
            couponId = coupon.getApplicationId();
            String output = run(repo, "UPDATE\nOct4\nCOUPONS\nALL\nCORRECT\n1\n1,2\nCorrected association\n2.50\n2\nREASON\nCoupon correction\nSAVE\n9\n");
            assertTrue(output.contains("Correction saved:"), output);
            assertEquals(before, repo.history().findByRevision(receiptId, 1).orElseThrow().after().contents());
            assertEquals(2, repo.history().findByReceiptId(receiptId).size());
        }
        try (var repo = InMemoryTransactionRepository.openLocal(file)) {
            var receipt = repo.findById(receiptId).orElseThrow();
            var tx = receipt.transaction();
            var coupon = tx.getAppliedCoupons().stream().filter(c -> c.getApplicationId().equals(couponId)).findFirst().orElseThrow();
            assertEquals(new BigDecimal("2.50"), coupon.getAppliedAmount());
            assertEquals(List.of(tx.getPurchaseItems().get(0).getLineItemId(), tx.getPurchaseItems().get(1).getLineItemId()), coupon.getLineItemIds());
            assertEquals(2, tx.getAppliedCoupons().size());
            assertEquals(ReceiptObservations.DiscountKind.MANUFACTURER, tx.getObservations().discountKinds().get(couponId));
            assertEquals(2, receipt.revision());
        }
    }
}
