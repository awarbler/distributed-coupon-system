package coupons;

import coupons.repository.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.Scanner;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Required printed observations never become invented financial events. */
class ReceiptFooterTest {
    private String prefix() {
        return String.join("\n", "Footer-test", "940 am", "10/4/26", "Walgreens", "aj", "", "Milk", "5.00", "1", "0", "0",
                "n", "n", "0", "0", "0", "5", "5.00", "n") + "\n";
    }

    @Test void requiresEachPrintedValueAndPreservesItsIndependentMeaningThroughSaveAndHistory() {
        String script = prefix() + String.join("\n", "", "bad", "3.60", "6.00", "9.60", "", "bad%", "43%",
                "", "oops", "247.75", "", "-1", "0.80", "", "-1", "10.00", "", "bad", "238.55",
                "n", "SAVE", "2", "#1", "3", "6") + "\n";
        var repo = new InMemoryTransactionRepository();
        var bytes = new ByteArrayOutputStream();
        new ReceiptEntryApp(new Scanner(script), new PrintStream(bytes), repo).run();
        String output = bytes.toString();
        var saved = repo.findPossibleDuplicates(new ReceiptMeta("Walgreens", "Footer-test", null, null));
        assertEquals(1, saved.size(), output);
        var receipt = saved.get(0);
        var observations = receipt.transaction().getObservations();
        assertEquals(new BigDecimal("43"), observations.receiptSavingValuePercent());
        assertEquals(new BigDecimal("9.60"), observations.savings().get(2).reportedAmount());
        assertEquals(new BigDecimal("247.75"), observations.walgreensCash().startingBalance());
        assertEquals(new BigDecimal("10.00"), observations.walgreensCash().used());
        assertEquals(new BigDecimal("0.80"), observations.walgreensCash().earned());
        assertEquals(new BigDecimal("238.55"), observations.walgreensCash().endingBalance());
        assertTrue(observations.earnings().isEmpty());
        assertEquals(new BigDecimal("5.00"), new TransactionCalculator().calculateExpectedCheckoutAmount(receipt.transaction()));
        assertEquals(ReceiptSnapshot.from(receipt.transaction()), repo.history().findByRevision(receipt.transactionId(), 1).orElseThrow().after().contents());
        assertEquals(1, repo.history().findByReceiptId(receipt.transactionId()).size());
        assertFalse(output.contains("Add a printed savings"));
        assertFalse(output.contains("Enter printed Walgreens Cash account"));
        assertFalse(output.contains("Does that match"));
        assertEquals(3, output.lines().filter(line -> line.equals("Receipt Opening Balance:")).count());
        assertEquals(3, output.lines().filter(line -> line.equals("Receipt Closing Balance:")).count());
        assertTrue(output.replaceAll(" +", " ").contains("Receipt Saving Value 43%"));
    }

    @Test void cannotReachReviewOrSaveWhenAnyRequiredAccountValueIsMissing() {
        String[] balances = {"247.75", "0.80", "10.00", "238.55"};
        for (int missing = 0; missing < balances.length; missing++) {
            String script = prefix() + "0\n0\n0\n0%\n";
            for (int i = 0; i < missing; i++) script += balances[i] + "\n";
            var repo = new InMemoryTransactionRepository();
            var bytes = new ByteArrayOutputStream();
            new ReceiptEntryApp(new Scanner(script), new PrintStream(bytes), repo).run();
            assertTrue(repo.creationHistory().isEmpty());
            assertFalse(bytes.toString().contains("REVIEW —"));
            assertFalse(bytes.toString().contains("Save result:"));
        }
    }
}
