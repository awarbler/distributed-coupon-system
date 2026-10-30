package coupons.cli;

import coupons.receipts.CouponTransaction;
import coupons.receipts.Payment;
import coupons.receipts.PaymentType;
import coupons.receipts.PurchaseItem;
import coupons.receipts.ReceiptObservations;
import coupons.receipts.ReceiptTotals;

import coupons.promotions.BenefitKind;
import coupons.promotions.RebateBenefit;
import coupons.promotions.RebateStatus;

import coupons.repository.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReceiptSavedEditingTest {
    private final InMemoryTransactionRepository repo = new InMemoryTransactionRepository();
    private final UUID id = UUID.randomUUID();

    private void seed() {
        var tx = new CouponTransaction(new ReceiptTotals(BigDecimal.ONE, new BigDecimal("8.00")));
        tx.addPurchaseItem(new PurchaseItem("Soap", null, null, new BigDecimal("7.00"), 1));
        tx.addPayment(new Payment(PaymentType.CARD, new BigDecimal("8.00")));
        repo.save(id, tx, new ReceiptMeta("Target", "001", null, "store-1"), List.of(),
                new ReceiptWriteRequest(UUID.randomUUID(), Instant.now()), new ReceiptCreateCtx("AJ"));
    }

    private String run(String script) {
        var bytes = new ByteArrayOutputStream();
        new ReceiptEntryApp(new Scanner(script), new PrintStream(bytes), repo).run();
        return bytes.toString();
    }

    @Test void confirmsCorrectionAndShowsHistoryWhilePreservingOtherFinancialRecords() {
        seed();
        var before = ReceiptSnapshot.from(repo.findById(id).orElseThrow().transaction());
        String output = run("UPDATE\n001\nTOTALS\nALL\n1.25\n8.25\nREASON\nPrinted tax corrected\nSAVE\n9\n");
        var saved = repo.findById(id).orElseThrow();
        var after = ReceiptSnapshot.from(saved.transaction());
        assertEquals(2, saved.revision());
        assertEquals(new BigDecimal("1.25"), after.receiptTotals().taxAmount());
        assertEquals(before.payments(), after.payments());
        assertEquals(before.purchaseItems(), after.purchaseItems());
        assertEquals(before, repo.history().findByRevision(id, 1).orElseThrow().after().contents());
        assertEquals("Printed tax corrected", repo.history().findByRevision(id, 2).orElseThrow().changeCtx().reason());
        assertTrue(output.contains("BEFORE — saved revision 1"));
        assertTrue(output.contains("PROPOSED — unsaved changes"));
        assertTrue(output.contains("Correction saved:"));
        assertTrue(output.contains("History entries: 2"));
    }

    @Test void cancellationBeforeSaveLeavesReceiptUnchanged() {
        seed();
        run("UPDATE\n001\nTOTALS\nALL\n2\n9\nCANCEL\ny\n9\n");
        assertEquals(1, repo.findById(id).orElseThrow().revision());
        assertEquals(1, repo.history().findByReceiptId(id).size());
    }

    @Test void itemCorrectionKeepsLineIdentityAndMetadata() {
        seed();
        var before = ReceiptSnapshot.from(repo.findById(id).orElseThrow().transaction());
        run("UPDATE\n001\nITEM\nALL\n1\nSoap corrected\n00012\n3.50\n2\nREASON\nQuantity correction\nSAVE\n9\n");
        var saved = repo.findById(id).orElseThrow();
        var line = saved.transaction().getPurchaseItems().get(0);
        assertEquals(before.purchaseItems().get(0).lineItemId(), line.getLineItemId());
        assertEquals(2, line.getQuantity());
        assertEquals("00012", line.getProductCode());
        assertEquals("store-1", saved.metadata().storeReference());
    }

    @Test void conflictRetainsProposedEditsAndNeverOverwritesNewRevision() {
        seed();
        var bytes = new ByteArrayOutputStream();
        var stream = new PrintStream(bytes) {
            private boolean changed;
            @Override public void println(String text) {
                super.println(text);
                if (!changed && text.startsWith("PROPOSED — unsaved changes")) {
                    changed = true;
                    var saved = repo.findById(id).orElseThrow();
                    repo.update(id, 1, saved.transaction(), saved.metadata(), List.of(),
                            new ReceiptWriteRequest(UUID.randomUUID(), Instant.now()),
                            new ReceiptChangeContext("Other", "Concurrent correction", "Other", Instant.now()));
                }
            }
        };
        new ReceiptEntryApp(new Scanner("UPDATE\n001\nTOTALS\nALL\n2\n9\nREASON\nCorrection\nSAVE\nCANCEL\ny\n9\n"), stream, repo).run();
        String output = bytes.toString();
        assertTrue(output.contains("Revision conflict"));
        assertTrue(output.contains("proposed edits") || output.contains("Proposed edits"));
        assertTrue(output.lastIndexOf("PROPOSED — unsaved changes") > output.indexOf("Revision conflict"));
        assertEquals(new BigDecimal("8.00"), repo.findById(id).orElseThrow().transaction().getReceiptTotals().getFinalTotal());
        assertEquals(2, repo.history().findByReceiptId(id).size());
    }
    @Test void pointCorrectionThroughCliKeepsEventAndHistoricalAmounts() {
        seed();
        var saved = repo.findById(id).orElseThrow();
        var tx = saved.transaction();
        var event = UUID.randomUUID();
        var points = new RebateBenefit(BenefitKind.POINTS, new BigDecimal("1250"), null, "Fetch");
        var valuation = new ReceiptObservations.RecordedValuation(new BigDecimal("1.25"),
                java.util.Currency.getInstance("USD"), "Spreadsheet");
        var earning = new ReceiptObservations.Earning(event, ReceiptObservations.EarningKind.THIRD_PARTY_REBATE,
                "Fetch", "Offer", ReceiptObservations.Scope.RECEIPT, List.of(), points, null,
                RebateStatus.PENDING, null, null, null, List.of(), null, null, valuation);
        tx.setObservations(new ReceiptObservations(null, null, List.of(earning), List.of(), null, List.of(), java.util.Map.of()));
        repo.update(id, 1, tx, saved.metadata(), List.of(), new ReceiptWriteRequest(UUID.randomUUID(), Instant.now()),
                new ReceiptChangeContext("AJ", "Record pending points", "AJ", Instant.now()));
        run("UPDATE\n001\nEARNINGS\nALL\n1\nn\nFetch\nOffer\n3\n0\n1500\n4\n1250\n1.50\nSpreadsheet\n\n\nREASON\nPoints credited\nSAVE\n9\n");
        var corrected = repo.findById(id).orElseThrow().transaction().getObservations().earnings().get(0);
        assertEquals(event, corrected.earningId());
        assertEquals(points, corrected.expected());
        assertEquals(new BigDecimal("1500"), corrected.confirmed().amount());
        assertEquals(new BigDecimal("1.50"), corrected.valuation().amount());
        assertEquals(RebateStatus.CREDITED, corrected.status());
        assertEquals(earning, repo.history().findByRevision(id, 2).orElseThrow().after().contents().observations().earnings().get(0));
        assertEquals(3, repo.history().findByReceiptId(id).size());
    }
}
