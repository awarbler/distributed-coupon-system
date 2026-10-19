package coupons;

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

class ReceiptHistoryDisplayTest {
    @Test void historyAndRetryExplainTheResultWithoutJavaRecordDumpsOrNewWrites() {
        var repo = new InMemoryTransactionRepository();
        UUID id = UUID.randomUUID();
        var tx = new CouponTransaction(new ReceiptTotals(new BigDecimal("1.04"), new BigDecimal("3.60")));
        tx.addPurchaseItem(new PurchaseItem("Neutrogena", "00123", null, new BigDecimal("7.79"), 2));
        repo.save(id, tx, new ReceiptMeta("Walgreens", "000123", null, null), List.of(),
                new ReceiptWriteRequest(UUID.randomUUID(), Instant.now()), new ReceiptCreateCtx("aj"));
        var bytes = new ByteArrayOutputStream();
        new ReceiptEntryApp(new Scanner("8\nHISTORY\n1\n8\nRETRY\n8\nHISTORY\n1\n9\n"), new PrintStream(bytes), repo).run();
        String output = bytes.toString();
        assertTrue(output.contains("Version 1 — Receipt Saved"), output);
        assertTrue(output.contains("Entered By: Aj"));
        assertTrue(output.contains("Receipt Lines: 1 | Products Purchased: 2"));
        assertTrue(output.contains("Receipt Total: 3.60"));
        assertTrue(output.contains("No Duplicate Receipt Or History Entry Created"));
        assertTrue(output.contains("Original result: Receipt 000123 | Version 1 | Active"));
        assertFalse(output.contains("ReceiptHistoryEntry["));
        assertFalse(output.contains("ReceiptWriteResult["));
        assertFalse(output.contains("ReceiptSnapshot["));
        assertFalse(output.contains(id.toString()));
        assertEquals(1, repo.history().findByReceiptId(id).size());
    }

    @Test void historySelectionSortsReceiptDatesAndParsedTimesWithoutChangingStorage() {
        var repo = new InMemoryTransactionRepository();
        var day = java.time.LocalDate.of(2026, 10, 5);
        var dates = java.util.Arrays.asList(day, day, day, day.minusDays(1), day, day, day, null, day);
        var times = java.util.Arrays.asList("4:22 PM", "4:23 PM", "12:48 PM", "9:40 AM", "12:00 AM", "12:00 PM", null, "8:00 AM", "not recorded");
        var ids = new java.util.ArrayList<UUID>();
        for (int i = 0; i < dates.size(); i++) {
            var id = new UUID(0, i + 1); ids.add(id);
            var tx = new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO, BigDecimal.valueOf(i + 1)));
            tx.setObservations(new ReceiptObservations(dates.get(i), times.get(i), List.of(), List.of(), null, List.of(), java.util.Map.of()));
            repo.save(id, tx, new ReceiptMeta("Walgreens", "Receipt-" + (i + 1), null, null), List.of(),
                    new ReceiptWriteRequest(UUID.randomUUID(), Instant.now()), new ReceiptCreateCtx("Owner"));
        }
        repo.softDelete(ids.get(2), 1, new ReceiptWriteRequest(UUID.randomUUID(), Instant.now()),
                new ReceiptChangeContext("Owner", "History display test", "Owner", Instant.now()));
        var creationOrder = repo.creationHistory();
        var snapshots = ids.stream().map(id -> repo.history().findByReceiptId(id)).toList();
        var bytes = new ByteArrayOutputStream();
        new ReceiptEntryApp(new Scanner("8\nHISTORY\n4\n11\n"), new PrintStream(bytes), repo).run();
        String output = bytes.toString();
        var rows = output.lines().filter(line -> line.matches("\\d+ \\| .*Receipt-.*")).toList();
        assertEquals(9, rows.size(), output);
        var ordered = List.of(4, 5, 6, 3, 1, 2, 7, 9, 8);
        for (int i = 0; i < ordered.size(); i++) assertTrue(rows.get(i).contains(" | Receipt-" + ordered.get(i) + " | "), output);
        assertTrue(rows.get(3).endsWith("DELETED"), output);
        assertTrue(output.contains("History entries: 2"), output); // Row 4 is the deleted 12:48 PM receipt.
        assertEquals(creationOrder, repo.creationHistory());
        assertEquals(snapshots, ids.stream().map(id -> repo.history().findByReceiptId(id)).toList());
        assertEquals(2, repo.findDeletedById(ids.get(2)).orElseThrow().revision());
        assertFalse(output.contains("Input ended"), output);
    }

    @Test void registeredCouponMenuCanBeViewedOrCancelledAndRequiresSaveConfirmation() {
        var repo = new InMemoryTransactionRepository();
        var bytes = new ByteArrayOutputStream();
        String script = "5\n1\n2\nBACK\n2\nneutrogena\n7\n\n\nn\n3\n9\n";
        new ReceiptEntryApp(new Scanner(script), new PrintStream(bytes), repo).run();
        assertTrue(repo.recordedRewards().isEmpty());
        assertTrue(bytes.toString().contains("Cancelled — No RR Coupon Added."));
        bytes.reset();
        new ReceiptEntryApp(new Scanner("5\n2\nneutrogena\n7\n\n\ny\n1\n5\n9\n"), new PrintStream(bytes), repo).run();
        assertEquals(1, repo.recordedRewards().size());
        assertEquals("neutrogena", repo.recordedRewards().get(0).getSourceDescription());
        assertTrue(bytes.toString().contains("Neutrogena — 7.00 RR"));
        assertTrue(bytes.toString().contains("Saved Receipts Were Not Changed."));
        assertTrue(repo.creationHistory().isEmpty());
        assertFalse(bytes.toString().contains("Recorded reward UUID:"));
    }
}
