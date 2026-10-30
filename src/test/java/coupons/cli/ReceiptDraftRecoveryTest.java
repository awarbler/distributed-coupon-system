package coupons.cli;

import coupons.receipts.ReceiptImportReader;

import coupons.repository.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ReceiptDraftRecoveryTest {
    @TempDir Path directory;
    private Path manifest() throws Exception {
        Files.write(directory.resolve("photo.jpeg"),new byte[]{1,2,3});
        Path manifest=directory.resolve("receipt.properties");
        Files.writeString(manifest,"""
                format=receipt-import-v1
                image.count=1
                image.1.path=photo.jpeg
                receipt.retailer=Walgreens
                receipt.number=Recover-me
                receipt.date=2026-10-08
                receipt.time=9:06 AM
                receipt.tax=0
                receipt.total=5
                receipt.footer=NOT_PRINTED
                item.count=1
                item.1.description=Soap
                item.1.sku=01234567890
                item.1.quantity=1
                item.1.regularUnitPrice=5
                item.1.enteredUnitPrice=5
                payment.count=1
                payment.1.type=CASH
                payment.1.amount=5
                """);return manifest;
    }
    private String run(InMemoryTransactionRepository repo,String script) {
        var output=new ByteArrayOutputStream();new ReceiptEntryApp(new Scanner(script),new PrintStream(output),repo).run();return output.toString();
    }
    @Test void completedManualItemsAreCheckpointedBeforeCheckoutAndResumeWithoutDuplication() throws Exception {
        Path database=directory.resolve("receipts.bin");
        try(var repo=InMemoryTransactionRepository.openLocal(database)) {
            run(repo,"NEW\nAuto-progress\n9:00 AM\n10/5/2026\nStore\nAJ\n\nSoap\n5\n1\n0\n0\n");
            assertTrue(repo.creationHistory().isEmpty());
        }
        Path checkpoint;
        try(var files=Files.list(ReceiptDraftFile.folder(database))){checkpoint=files.filter(p->p.toString().endsWith(".draft")).findFirst().orElseThrow();}
        var draft=ReceiptDraftFile.read(checkpoint);assertTrue(draft.partial());assertFalse(draft.taxKnown());assertFalse(draft.totalKnown());
        assertEquals(1,draft.snapshot().purchaseItems().size());
        try(var repo=InMemoryTransactionRepository.openLocal(database)) {
            String output=run(repo,"10\n1\nDONE\n0\n0\n0\nn\n5\nn\nn\nn\nn\nSAVE\n11\n");
            assertTrue(output.contains("RECEIPT SAVED SUCCESSFULLY"),output);
            var saved=repo.findById(draft.id()).orElseThrow();assertEquals(1,saved.transaction().getPurchaseItems().size());
            assertEquals(1,repo.creationHistory().size());assertFalse(Files.exists(checkpoint));
        }
    }
    @Test void balanceMenuAutomaticallyShowsWeeklyAndMonthlyAccountAndSavingsTables() {
        String output=run(new InMemoryTransactionRepository(),"6\nBACK\n11\n");
        assertTrue(output.matches("(?s).*WEEK [1-6] ACCOUNT BALANCES.*"),output);assertTrue(output.contains("MONTH TO DATE ACCOUNT BALANCES"),output);
        assertTrue(output.contains("SAVINGS SUMMARY — WEEK "),output);assertTrue(output.contains("SAVINGS SUMMARY — Month to date through"),output);
        assertFalse(output.contains("Input ended"),output);
    }
    @Test void mainMenuUsesNineForImportTenForResumeAndElevenForExit() {
        var repo=new InMemoryTransactionRepository();String output=run(repo,"9\nBACK\n10\n11\n");
        assertTrue(output.contains("9. Import a receipt file"),output);assertTrue(output.contains("10. Resume an unfinished receipt\n11. Exit"),output);
        assertTrue(output.contains("Receipt import:"),output);assertTrue(output.contains("Draft recovery requires local storage"),output);
        assertFalse(output.contains("Input ended"),output);
    }
    @Test void pauseRetainsEditedDraftAndEvidenceAcrossRestartWithoutCreditingBalances() throws Exception {
        Path manifest=manifest(),database=directory.resolve("receipts.bin");UUID id=ReceiptImportReader.read(manifest).importId();
        try(var repo=InMemoryTransactionRepository.openLocal(database)) {
            String output=run(repo,"IMPORT\n"+manifest+"\nEDIT\nITEM\nDESCRIPTION\n1\nCorrected soap\nDONE\nPAUSE\n9\n");
            assertTrue(output.contains("Draft kept on this device"),output);
            assertTrue(repo.creationHistory().isEmpty());assertTrue(repo.findById(id).isEmpty());
        }
        try(var repo=InMemoryTransactionRepository.openLocal(database)) {
            String output=run(repo,"R\n1\nSAVE\n9\n");assertTrue(output.contains("Save result:"),output);
            assertTrue(output.contains("RECEIPT SAVED SUCCESSFULLY\nReceipt  : Recover-me\nRevision : 1\nStatus   : Saved on this device"),output);
            assertTrue(output.lastIndexOf("RECEIPT SAVED SUCCESSFULLY")>output.lastIndexOf("RECEIPT TOTALS"),output);
            var saved=repo.findById(id).orElseThrow();assertEquals("Corrected soap",saved.transaction().getPurchaseItems().get(0).getProductName());
            assertEquals(1,repo.creationHistory().size());assertEquals(3,saved.evidenceIds().size());
            assertFalse(Files.exists(ReceiptDraftFile.folder(database).resolve(id+".draft")));
            assertTrue(run(repo,"R\n9\n").contains("No unfinished receipts"));
        }
    }
    @Test void unexpectedInputEndKeepsCheckpointButExplicitCancelRemovesIt() throws Exception {
        Path manifest=manifest(),database=directory.resolve("receipts.bin");UUID id=ReceiptImportReader.read(manifest).importId();
        try(var repo=InMemoryTransactionRepository.openLocal(database)) {
            run(repo,"IMPORT\n"+manifest+"\n");assertTrue(repo.creationHistory().isEmpty());
        }
        assertTrue(Files.exists(ReceiptDraftFile.folder(database).resolve(id+".draft")));
        try(var repo=InMemoryTransactionRepository.openLocal(database)) {
            String output=run(repo,"R\n1\nCANCEL\ny\n9\n");assertTrue(output.contains("Cancelled; nothing saved"),output);
        }
        assertFalse(Files.exists(ReceiptDraftFile.folder(database).resolve(id+".draft")));
    }
    @Test void exitingLocalReceiptReviewKeepsCompletedDraftForResume() throws Exception {
        Path manifest=manifest(),database=directory.resolve("receipts.bin");UUID id=ReceiptImportReader.read(manifest).importId();
        try(var repo=InMemoryTransactionRepository.openLocal(database)) {
            String output=run(repo,"IMPORT\n"+manifest+"\nEXIT\ny\n");
            assertTrue(output.contains("Completed draft checkpoints are kept for Resume"),output);
            assertTrue(repo.creationHistory().isEmpty());
        }
        assertTrue(Files.exists(ReceiptDraftFile.folder(database).resolve(id+".draft")));
        try(var repo=InMemoryTransactionRepository.openLocal(database)) {
            String output=run(repo,"10\n1\nSAVE\n11\n");assertTrue(output.contains("RECEIPT SAVED SUCCESSFULLY"),output);
            assertEquals(1,repo.creationHistory().size());
        }
    }
    @Test void corruptDraftIsPreservedAndNeverImportedAsFinancialActivity() throws Exception {
        Path database=directory.resolve("receipts.bin"),folder=ReceiptDraftFile.folder(database);Files.createDirectories(folder);
        Path draft=folder.resolve(UUID.randomUUID()+".draft");Files.writeString(draft,"corrupted");
        try(var repo=InMemoryTransactionRepository.openLocal(database)) {
            assertTrue(run(repo,"R\n9\n").contains("retained for recovery"));assertTrue(repo.creationHistory().isEmpty());
        }
        assertEquals("corrupted",Files.readString(draft));
    }
}
