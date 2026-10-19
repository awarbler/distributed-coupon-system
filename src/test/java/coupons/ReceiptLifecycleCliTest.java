package coupons;

import coupons.repository.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ReceiptLifecycleCliTest {
    @Test void createSaveRetrieveEditDeleteFindDeletedRestoreAndHistory() {
        var repo = new InMemoryTransactionRepository(); var output = new ByteArrayOutputStream();
        String create = String.join("\n", "Lifecycle-CLI", "9:40 AM", "10/4/2026", "Walgreens", "Owner", "000123", "Milk", "5.00", "1",
                "0", "0", "n", "n", "0", "0", "0", "n", "5.00", "n", "y", "0", "0", "0", "0%", "0", "0", "0", "0", "n", "SAVE");
        String rest = "\n2\nLifecycle-CLI\nBACK\nUPDATE\nLifecycle-CLI\nDETAILS\nTIME\n9:45 AM\nDONE\nSAVE\nDELETE\nLifecycle-CLI\nDELETE\n8\nRESTORE\n1\nRestore verified receipt\ny\n8\nHISTORY\n1\n11\n";
        new ReceiptEntryApp(new Scanner(create + rest), new PrintStream(output), repo).run();
        String text = output.toString();
        assertTrue(text.contains("Receipt restored successfully"), text);
        var receipt = repo.findPossibleDuplicates(new ReceiptMeta("Walgreens", "Lifecycle-CLI", null, null)).get(0);
        assertEquals(ReceiptStatus.ACTIVE, receipt.status()); assertEquals(4, receipt.revision());
        assertEquals("9:45 AM", ReceiptTimeInput.display(receipt.transaction().getObservations().printedTime()));
        assertEquals(List.of(ReceiptChangeAction.CREATED, ReceiptChangeAction.UPDATED, ReceiptChangeAction.DELETED, ReceiptChangeAction.RESTORED),
                repo.history().findByReceiptId(receipt.transactionId()).stream().map(h -> h.result().action()).toList());
        assertTrue(text.contains("DELETED RECEIPTS"), text); assertTrue(text.contains("History entries: 4"), text);
        assertFalse(text.contains("Input ended"), text);
    }
    @Test void reportedBalancesRemainVisibleForDeletedReceiptsAndAreNotCalculatedBalances() {
        var repo = new InMemoryTransactionRepository(); var id = UUID.randomUUID(); var tx = new CouponTransaction(new ReceiptTotals(java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO));
        var observed = new ReceiptObservations.WalgreensCashAccount(new java.math.BigDecimal("100"), java.math.BigDecimal.ZERO, new java.math.BigDecimal("2"), new java.math.BigDecimal("102"));
        tx.setObservations(new ReceiptObservations(java.time.LocalDate.of(2026, 10, 4), "9:40 AM", List.of(), List.of(), observed, List.of(), Map.of()));
        repo.save(id, tx, new ReceiptMeta("Walgreens", "Reported", null, null), List.of(), new ReceiptWriteRequest(UUID.randomUUID(), java.time.Instant.now()), new ReceiptCreateCtx("Owner"));
        repo.softDelete(id, 1, new ReceiptWriteRequest(UUID.randomUUID(), java.time.Instant.now()), new ReceiptChangeContext("Owner", "Remove duplicate", "Owner", java.time.Instant.now()));
        var output = new ByteArrayOutputStream(); new ReceiptEntryApp(new Scanner("6\nREPORTED\nBACK\n11\n"), new PrintStream(output), repo).run();
        String text = output.toString();
        assertTrue(text.contains("PROVIDER-REPORTED BALANCES"), text);
        assertTrue(text.contains("DELETED | Walgreens Cash | $100.00 | $2.00 | $0.00 | $102.00"), text);
        assertEquals(observed, repo.findDeletedById(id).orElseThrow().transaction().getObservations().walgreensCash());
    }

}
