package coupons.cli;

import coupons.receipts.ReceiptObservations;
import coupons.receipts.TransactionCalculator;

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
        String script = prefix() + String.join("\n", "y", "", "bad", "3.60", "6.00", "9.60", "", "bad%", "43%",
                "", "oops", "247.75", "", "-1", "0.80", "", "-1", "10.00", "", "bad", "238.55",
                "n", "SAVE", "2", "#1", "3", "9") + "\n";
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

    @Test void absentFooterSurvivesRestartAndCorrectionWithoutLosingPaymentsOrEarnings(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var file=directory.resolve("receipts.bin");java.util.UUID id;
        String script=String.join("\n","Footer-absent","940am","10/4/26","Walgreens","aj",
                "","Milk","5","1","0","0","n","n","0","0","5","","0","0",
                "y","Walgreens Cash","Receipt earning","3","0","0.80","4","","","","n",
                "n","n","SAVE","9")+"\n";
        try(var repo=InMemoryTransactionRepository.openLocal(file)) {
            var bytes=new ByteArrayOutputStream();new ReceiptEntryApp(new Scanner(script),new PrintStream(bytes),repo).run();
            var records=repo.findPossibleDuplicates(new ReceiptMeta("Walgreens","Footer-absent",null,null));
            assertEquals(1,records.size(),bytes.toString());id=records.get(0).transactionId();
            assertFalse(bytes.toString().contains("Receipt Opening Balance:"),bytes.toString());
            assertFalse(bytes.toString().contains("Receipt myWalgreens Savings:"),bytes.toString());
            assertTrue(bytes.toString().contains("footer: Not printed"),bytes.toString());
        }
        try(var repo=InMemoryTransactionRepository.openLocal(file)) {
            var receipt=repo.findById(id).orElseThrow();var o=receipt.transaction().getObservations();
            assertEquals(ReceiptObservations.FooterStatus.NOT_PRINTED,o.footerStatus());
            assertNull(o.walgreensCash());assertNull(o.receiptSavingValuePercent());assertTrue(o.savings().isEmpty());
            assertEquals(1,o.earnings().size());assertEquals(new BigDecimal("0.80"),o.earnings().get(0).confirmed().amount());
            assertEquals(new BigDecimal("5"),new TransactionCalculator().calculateWalgreensCashRedeemed(receipt.transaction()));
            var original=ReceiptSnapshot.from(receipt.transaction());
            var bytes=new ByteArrayOutputStream();new ReceiptEntryApp(new Scanner("UPDATE\nFooter-absent\nTOTALS\nALL\n0\n0\nSAVE\n9\n"),new PrintStream(bytes),repo).run();
            assertTrue(bytes.toString().contains("Correction saved:"),bytes.toString());
            var updated=repo.findById(id).orElseThrow().transaction().getObservations();
            assertEquals(ReceiptObservations.FooterStatus.NOT_PRINTED,updated.footerStatus());
            assertEquals(o.earnings(),updated.earnings());
            assertEquals(original,repo.history().findByRevision(id,1).orElseThrow().after().contents());
        }
    }

    @Test void cannotReachReviewOrSaveWhenAnyRequiredAccountValueIsMissing() {
        String[] balances = {"247.75", "0.80", "10.00", "238.55"};
        for (int missing = 0; missing < balances.length; missing++) {
            String script = prefix() + "y\n0\n0\n0%\n";
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
