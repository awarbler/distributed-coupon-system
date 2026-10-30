package coupons.cli;

import coupons.receipts.ReceiptImportReader;
import coupons.receipts.SavingsReport;
import coupons.receipts.TransactionCalculator;

import coupons.promotions.BenefitKind;
import coupons.promotions.QualifyingSpend;
import coupons.promotions.RebateStatus;

import coupons.rewards.Reward;

import coupons.receipts.ReceiptObservations.*;
import coupons.repository.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ReceiptImportTest {
    @TempDir Path dir;
    private byte[] image() {return Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l9sAAAAASUVORK5CYII=");}
    private String manifest() {
        return """
                format=receipt-import-v1
                image.count=1
                image.1.path=receipt.png
                receipt.retailer=Walgreens
                receipt.number=Imported-1
                receipt.date=2026-10-05
                receipt.time=9:40 AM
                receipt.tax=0
                receipt.total=5
                receipt.footer=NOT_PRINTED
                item.count=2
                item.1.description=Shampoo
                item.1.sku=000123
                item.1.quantity=1
                item.1.regularUnitPrice=5
                item.1.enteredUnitPrice=5
                item.2.description=Conditioner
                item.2.quantity=1
                item.2.regularUnitPrice=5
                item.2.enteredUnitPrice=5
                coupon.count=1
                coupon.1.description=Two-product MQ
                coupon.1.amount=2
                coupon.1.kind=MANUFACTURER
                coupon.1.lines=1,2
                coupon.1.requiredQuantity=2
                payment.count=2
                payment.1.type=WALGREENS_CASH
                payment.1.amount=3
                payment.2.type=CASH
                payment.2.amount=5
                earning.count=3
                earning.1.kind=REGISTER_REWARD
                earning.1.provider=Walgreens
                earning.1.amount=5
                earning.1.status=PAID
                earning.1.lines=1,2
                earning.1.offer=Buy two get five RR
                earning.1.requirementType=BUY
                earning.1.requirementAmount=2
                earning.2.kind=WALGREENS_CASH
                earning.2.provider=Walgreens
                earning.2.amount=1
                earning.2.status=CREDITED
                earning.2.scope=RECEIPT
                earning.3.kind=THIRD_PARTY_REBATE
                earning.3.provider=Fetch
                earning.3.form=POINTS
                earning.3.amount=25
                earning.3.status=CREDITED
                earning.3.scope=RECEIPT
                earning.3.usdValue=0.025
                earning.3.valuationBasis=User recorded valuation
                """;
    }
    private Path file(String content) throws IOException {
        Files.write(dir.resolve("receipt.png"),image());var path=dir.resolve("receipt.properties");Files.writeString(path,content);return path;
    }
    private String run(InMemoryTransactionRepository repo,String script) {
        var bytes=new ByteArrayOutputStream();new ReceiptEntryApp(new Scanner(script),new PrintStream(bytes),repo).run();return bytes.toString();
    }
    @Test void importedReceiptEntryLetsOwnerSelectRegisterRewardPrinted() throws Exception {
        var path=file(manifest());var repo=new InMemoryTransactionRepository();
        String output=run(repo,"I\n"+path+"\nREBATES\ny\nRegister Reward\nPrinted Pantene reward\n3\n0\n4\n5\n\n\n\nn\nSAVE\n11\n");
        assertTrue(output.contains("REGISTER REWARD STATUS — choose 5 if the coupon printed."),output);
        assertTrue(output.contains("5. Register Reward Printed"),output);
        var saved=repo.findById(ReceiptImportReader.read(path).importId()).orElseThrow();
        var earning=saved.transaction().getObservations().earnings().stream().filter(e->"Printed Pantene reward".equals(e.offerReference())).findFirst().orElseThrow();
        assertEquals(EarningKind.REGISTER_REWARD,earning.kind());assertEquals(RebateStatus.PAID,earning.status());
        assertEquals(new BigDecimal("4"),earning.confirmed().amount());
    }

    @Test void extractionLoadsOriginalUnitsLinksAndImageWithoutAnyFinancialWrite() throws Exception {
        var path=file(manifest());var imported=ReceiptImportReader.read(path);var again=ReceiptImportReader.read(path);
        assertEquals(imported.importId(),again.importId());assertEquals(imported.items(),again.items());
        assertEquals(2,imported.evidence().size());assertArrayEquals(image(),imported.evidence().get(0).getContent());
        assertArrayEquals(Files.readAllBytes(path),imported.evidence().get(1).getContent());
        assertEquals("000123",imported.items().get(0).sku());assertEquals(2,imported.discounts().get(0).application().getLineItemIds().size());
        assertEquals(new BigDecimal("25"),imported.earnings().get(2).confirmed().amount());
        assertEquals(new BigDecimal("0.025"),imported.earnings().get(2).valuation().amount());assertTrue(imported.issues().isEmpty());
        var repo=new InMemoryTransactionRepository();String output=run(repo,"IMPORT\n"+path+"\nCANCEL\ny\n9\n");
        assertTrue(output.contains("REVIEW — all changes are still unsaved"),output);assertTrue(repo.creationHistory().isEmpty());
        assertTrue(repo.recordedRewards().isEmpty());assertTrue(repo.accountBalances().openings().isEmpty());
    }
    @Test void approvedImportSavesEvidenceHistoryAndBalancesOnceAcrossRestartAndReimport() throws Exception {
        var path=file(manifest());var storage=dir.resolve("receipts.bin");UUID id=ReceiptImportReader.read(path).importId();
        try(var repo=InMemoryTransactionRepository.openLocal(storage)) {
            String output=run(repo,"IMPORT\n"+path+"\nSAVE\n9\n");assertTrue(output.contains("Save result:"),output);
            var saved=repo.findById(id).orElseThrow();assertEquals(1,repo.history().findByReceiptId(id).size());
            assertEquals(3,saved.evidenceIds().size()); // Image, original manifest, approval audit.
            assertArrayEquals(image(),repo.evidence().findById(saved.evidenceIds().get(0)).orElseThrow().getContent());
            var tx=saved.transaction();assertEquals(new BigDecimal("5"),new TransactionCalculator().calculateExpectedCheckoutAmount(tx));
            assertEquals(new BigDecimal("6.025"),SavingsReport.calculate(List.of(saved)).newlyEarned());
            assertEquals(FooterStatus.NOT_PRINTED,tx.getObservations().footerStatus());assertNull(tx.getObservations().walgreensCash());
        }
        try(var repo=InMemoryTransactionRepository.openLocal(storage)) {
            String output=run(repo,"IMPORT\n"+path+"\n9\n");assertTrue(output.contains("already saved"),output);
            assertEquals(1,repo.creationHistory().size());assertEquals(1,repo.history().findByReceiptId(id).size());
        }
    }
    @Test void importedDraftAllowsTargetedCorrectionAndAdditionalRebatesBeforeSave() throws Exception {
        var path=file(manifest());var repo=new InMemoryTransactionRepository();
        String script="I\n"+path+"\nEDIT\nCHECKOUT\nTAX\n.08\nPRINTED TOTAL\n5.08\nDONE\nREBATES\ny\nIbotta\nExtra cashback\n3\n0\n2.50\n5\n\n\n\nn\nSAVE\n9\n";
        String output=run(repo,script);assertTrue(output.contains("Save result:"),output);
        var saved=repo.findById(ReceiptImportReader.read(path).importId()).orElseThrow();
        assertEquals(new BigDecimal(".08"),saved.transaction().getReceiptTotals().getTaxAmount());
        assertEquals(new BigDecimal("5.08"),saved.transaction().getReceiptTotals().getFinalTotal());
        assertEquals(4,saved.transaction().getObservations().earnings().size());
        assertEquals("Ibotta",saved.transaction().getObservations().earnings().get(3).provider());
        assertEquals(1,repo.history().findByReceiptId(saved.transactionId()).size());
    }
    @Test void missingFieldsAskOnlyForRequiredFactsAndSaveIsExplicitApproval() throws Exception {
        var path=file(manifest().replace("item.1.description=Shampoo\n","").replace("item.1.quantity=1\n","")
                .replace("item.1.enteredUnitPrice=5\n","").replace("item.1.regularUnitPrice=5\n", ""));
        var repo=new InMemoryTransactionRepository();String output=run(repo,"IMPORT\n"+path+"\nShampoo\n1\n5\nISSUES\nCANCEL\ny\n11\n");
        assertTrue(output.contains("Missing description for line 1"),output);assertTrue(output.contains("Regular price missing"),output);assertFalse(output.contains("Approve the flagged"),output);
        assertFalse(output.contains("Missing description for line 2"),output);assertTrue(repo.creationHistory().isEmpty());
        output=run(repo,"IMPORT\n"+path+"\nShampoo\n1\n5\nSAVE\n11\n");assertTrue(output.contains("Save result:"),output);
        var saved=repo.findById(ReceiptImportReader.read(path).importId()).orElseThrow();
        assertNull(saved.transaction().getPurchaseItems().get(0).getRegularUnitPrice());
        assertFalse(SavingsReport.calculate(List.of(saved)).regularKnown());
    }
    @Test void zeroCheckoutNeedsNoInventedPaymentAndExpectedRewardsStayUnconfirmed() throws Exception {
        String content=manifest().replace("receipt.total=5","receipt.total=0").replace("coupon.1.amount=2","coupon.1.amount=7")
                .replace("payment.count=2","payment.count=1").replace("payment.2.type=CASH\n","").replace("payment.2.amount=5\n","")
                .replace("earning.1.status=PAID\n","");
        var path=file(content);var repo=new InMemoryTransactionRepository();var imported=ReceiptImportReader.read(path);
        assertNull(imported.earnings().get(0).confirmed());assertEquals(RebateStatus.EXPECTED,imported.earnings().get(0).status());
        String output=run(repo,"IMPORT\n"+path+"\nSAVE\n9\n");assertTrue(output.contains("Save result:"),output);
        var saved=repo.findById(imported.importId()).orElseThrow();
        assertEquals(1,saved.transaction().getPayments().size());assertTrue(SavingsReport.calculate(List.of(saved)).paymentsKnown());
        assertEquals(new BigDecimal("1.025"),SavingsReport.calculate(List.of(saved)).newlyEarned());
    }
    @Test void invalidUnknownDuplicateFieldsBadLinksAndMissingImagesFailWithoutSaving() throws Exception {
        for(String content:List.of(manifest()+"unknown.option=1\n",manifest()+"receipt.total=6\n",
                manifest().replace("coupon.1.lines=1,2","coupon.1.lines=1,1"),manifest().replace("image.1.path=receipt.png","image.1.path=missing.png"),
                manifest().replace("earning.3.form=POINTS\n",""),manifest().replace("receipt.tax=0","receipt.tax=-1"))) {
            var path=file(content);var repo=new InMemoryTransactionRepository();String output=run(repo,"IMPORT\n"+path+"\n9\n");
            assertTrue(output.contains("Operation not saved:"),output);assertTrue(repo.creationHistory().isEmpty());
        }
    }
    @Test void failedImportWritePublishesNothingAndRetriesTheSameDraftWithoutDuplicateEvidence() throws Exception {
        var path=file(manifest());var storage=dir.resolve("retry.bin");var imported=ReceiptImportReader.read(path);
        var bytes=new ByteArrayOutputStream();boolean[] failed={false};
        try(var repo=InMemoryTransactionRepository.openLocal(storage)) {
            Files.createDirectory(storage); // A directory cannot be replaced with the checkpoint file.
            var output=new PrintStream(bytes) {
                @Override public void println(String message) {
                    super.println(message);
                    if(message.startsWith("Receipt not saved:")) {
                        failed[0]=true;assertTrue(repo.creationHistory().isEmpty());assertTrue(repo.findById(imported.importId()).isEmpty());
                        for(var evidence:imported.evidence())assertTrue(repo.evidence().findById(evidence.getEvidenceId()).isEmpty());
                        try {Files.delete(storage);}catch(IOException failure){throw new UncheckedIOException(failure);}
                    }
                }
            };
            new ReceiptEntryApp(new Scanner("IMPORT\n"+path+"\nSAVE\nSAVE\n9\n"),output,repo).run();
            assertTrue(failed[0],bytes.toString());assertTrue(bytes.toString().contains("Save result:"),bytes.toString());
            assertEquals(1,repo.history().findByReceiptId(imported.importId()).size());
            assertEquals(3,repo.findById(imported.importId()).orElseThrow().evidenceIds().size());
        }
        try(var repo=InMemoryTransactionRepository.openLocal(storage)) {assertEquals(1,repo.creationHistory().size());}
    }
    @Test void differentImportIdentityWithMatchingReceiptMetadataRequiresDuplicateApproval() throws Exception {
        var path=file(manifest());var repo=new InMemoryTransactionRepository();
        run(repo,"IMPORT\n"+path+"\nSAVE\n9\n");
        Files.writeString(path,manifest()+"receipt.id="+UUID.randomUUID()+"\n");
        String output=run(repo,"IMPORT\n"+path+"\nSAVE\nn\nCANCEL\ny\n9\n");
        assertTrue(output.contains("Possible duplicate"),output);assertTrue(output.contains("Save a separate receipt anyway?"),output);
        assertEquals(1,repo.creationHistory().size());
    }
    @Test void imagePathDirectsToPhotoSelectionWithoutSaving() throws Exception {
        file(manifest());var repo=new InMemoryTransactionRepository();String output=run(repo,"IMPORT\n"+dir.resolve("receipt.png")+"\n9\n");
        assertTrue(output.contains("Choose PHOTOS for local image reading"),output);assertTrue(repo.creationHistory().isEmpty());
    }
    @Test void importedReceiptWideCouponIsExplicitAndDoesNotWarnAboutUnknownProducts() throws Exception {
        var path=file(manifest().replace("coupon.1.lines=1,2","coupon.1.lines=\ncoupon.1.scope=RECEIPT").replace("coupon.1.kind=MANUFACTURER","coupon.1.kind=STORE_COUPON"));
        var draft=ReceiptImportReader.read(path);assertTrue(draft.discounts().get(0).application().isWholeReceipt());
        assertTrue(draft.issues().stream().noneMatch(issue->issue.field().equals("coupon.1.lines")));
        var repo=new InMemoryTransactionRepository();String output=run(repo,"IMPORT\n"+path+"\nSAVE\n11\n");
        assertTrue(output.contains("Whole receipt"),output);assertTrue(output.contains("RECEIPT SAVED SUCCESSFULLY"),output);
        assertTrue(repo.findById(draft.importId()).orElseThrow().transaction().getAppliedCoupons().get(0).isWholeReceipt());
    }
    @Test void receiptIssuesShowsCurrentMissingFieldsWithoutSaving() throws Exception {
        var path=file(manifest().replace("item.1.regularUnitPrice=5","item.1.regularUnitPrice="));
        var repo=new InMemoryTransactionRepository();String output=run(repo,"IMPORT\n"+path+"\nISSUES\nCANCEL\ny\n11\n");
        assertTrue(output.contains("RECEIPT ISSUES"),output);
        assertTrue(output.contains("Regular price missing"),output);
        assertTrue(output.contains("Where: Product line 1"),output);assertTrue(output.contains("EDIT > Products > Regular price"),output);
        assertTrue(repo.creationHistory().isEmpty());
    }
    @Test void actualGiftCardFormAndProgramArePreservedWithoutConvertingPointsOrAssumingCash() throws Exception {
        var content=manifest().replace("earning.count=3","earning.count=4")+"""
                earning.4.kind=THIRD_PARTY_REBATE
                earning.4.provider=Rebate provider
                earning.4.form=GIFT_CARD
                earning.4.program=Target gift card
                earning.4.amount=10
                earning.4.status=CREDITED
                earning.4.lines=1
                """;
        var path=file(content);var imported=ReceiptImportReader.read(path);var benefit=imported.earnings().get(3).confirmed();
        assertEquals(BenefitKind.GIFT_CARD,benefit.kind());assertEquals("Target gift card",benefit.program());
        var repo=new InMemoryTransactionRepository();String output=run(repo,"IMPORT\n"+path+"\nSAVE\n9\n");
        assertTrue(output.contains("Save result:"),output);
        var saved=repo.findById(imported.importId()).orElseThrow();
        assertEquals(new BigDecimal("16.025"),SavingsReport.calculate(List.of(saved)).newlyEarned());
    }
    @Test void sharedSaleReductionRetainsItsKindAndIsNotMisclassifiedAsManufacturerCoupon() throws Exception {
        var path=file(manifest().replace("coupon.1.kind=MANUFACTURER","coupon.1.kind=SALE_PROMOTION"));var repo=new InMemoryTransactionRepository();
        String output=run(repo,"IMPORT\n"+path+"\nSAVE\n9\n");assertTrue(output.contains("Save result:"),output);
        var saved=repo.findById(ReceiptImportReader.read(path).importId()).orElseThrow();
        var coupon=saved.transaction().getAppliedCoupons().get(0);
        assertEquals(DiscountKind.SALE_PROMOTION,saved.transaction().getObservations().discountKinds().get(coupon.getApplicationId()));
        assertEquals(new BigDecimal("8"),QualifyingSpend.calculate(saved.transaction(),coupon.getLineItemIds()).amount());
    }
}
