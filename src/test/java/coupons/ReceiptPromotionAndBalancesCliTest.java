package coupons;

import coupons.repository.*;
import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ReceiptPromotionAndBalancesCliTest {
    private String run(InMemoryTransactionRepository repo, String script) {
        var bytes = new ByteArrayOutputStream();
        new ReceiptEntryApp(new Scanner(script), new PrintStream(bytes), repo).run();
        return bytes.toString();
    }
    @Test void newReceiptStoresCharminEligibilityWithoutCreditingCardAndCorrectionsRetainIt(@TempDir Path directory) throws Exception {
        var file=directory.resolve("receipts.bin"); UUID id;
        var offer=new GiftCardPromotion(UUID.randomUUID(),"P&G online 75/25",new BigDecimal("75"),new BigDecimal("25"),
                LocalDate.of(2026,10,1),LocalDate.of(2026,10,31),GiftCardPromotion.Basis.REVIEWED_ELIGIBLE_AMOUNT,
                "User-confirmed Charmin eligibility",GiftCardPromotion.Status.TRACKING,Map.of());
        try(var repo=InMemoryTransactionRepository.openLocal(file)) {
            repo.saveGiftCardPromotion(offer);
            String script=String.join("\n","NEW","Oct4","953am","10/4/2026","Walgreens","AJ",
                    "01","Charmin","5.49","4","0","1","1.00","","n","y",
                    "02","H&S shampoo","7.00","1","0","0","n","y",
                    "03","H&S conditioner","7.00","1","0","0","n","n",
                    "COUPONS","y","2,3","H&S coupon","3.00","2",
                    "PROMOTIONS","1","1","20.96","Charmin qualifies; both H&S excluded",
                    "1.73","1","7","Neutrogena","0","2","26.69","n","26.69","n",
                    "y","10.98","11","21.98","38","0","0","0","0","n","SAVE","3", "9")+"\n";
            var output=run(repo,script);
            assertTrue(output.contains("Save result:"),output);
            assertTrue(output.contains("Charmin qualifies; both H&S excluded"),output);
            var saved=repo.findPossibleDuplicates(new ReceiptMeta("Walgreens","Oct4",null,null)).get(0);id=saved.transactionId();
            var link=saved.transaction().getObservations().promotionLinks().get(0);
            assertEquals(List.of(saved.transaction().getPurchaseItems().get(0).getLineItemId()),link.eligibleLineIds());
            assertEquals(new BigDecimal("26.69"),new TransactionCalculator().calculateExpectedCheckoutAmount(saved.transaction()));
            assertEquals(1,repo.history().findByReceiptId(id).size());
            assertEquals(1,repo.giftCardPromotions().get(0).contributions().size());
            assertTrue(saved.transaction().getObservations().earnings().isEmpty());
            run(repo,"UPDATE\nOct4\nTOTALS\nALL\n1.73\n26.69\nREASON\nRecheck printed totals\nSAVE\n9\n");
            var refreshedLink=repo.findById(id).orElseThrow().transaction().getObservations().promotionLinks().get(0);
            assertEquals(link.eligibleLineIds(),refreshedLink.eligibleLineIds());
            assertEquals(link.reviewedEligibleAmount(),refreshedLink.reviewedEligibleAmount());
            assertEquals(link.eligibilityNote(),refreshedLink.eligibilityNote());
            var current=repo.giftCardPromotions().get(0);
            assertEquals(new BigDecimal("20.96"),current.eligibleAmount(current.contributions().get(id),repo.findById(id).orElseThrow()).orElseThrow());
            output=run(repo,"UPDATE\nOct4\nPROMOTIONS\n1\n1\n20.96\nRechecked online eligibility\nREASON\nEligibility review\nSAVE\n9\n");
            assertTrue(output.contains("Correction saved:"),output);
            current=repo.giftCardPromotions().get(0);
            assertEquals(new BigDecimal("20.96"),current.eligibleAmount(current.contributions().get(id),repo.findById(id).orElseThrow()).orElseThrow());
        }
        try(var repo=InMemoryTransactionRepository.openLocal(file)) {
            assertEquals(3,repo.findById(id).orElseThrow().revision());
            assertEquals(1,repo.giftCardPromotions().get(0).contributions().size());
        }
    }
    @Test void advancedPointsOpeningPersistsAndIsShownOnLaterDay(@TempDir Path directory) throws Exception {
        var file=directory.resolve("receipts.bin");
        try(var repo=InMemoryTransactionRepository.openLocal(file)) {
            var output=run(repo,"BALANCE SETTINGS\n10/4/2026\n6\n8\ny\n33660\n33.59\nRecorded spreadsheet USD valuation\nOctober opening statement\ny\n9\n9\n");
            assertFalse(output.contains("Operation not saved:"),output);
            assertEquals(1,repo.accountBalances().openings().size());
        }
        try(var repo=InMemoryTransactionRepository.openLocal(file)) {
            var output=run(repo,"BALANCE SETTINGS\n10/5/2026\n4\n5\n9\n9\n");
            assertTrue(output.matches("(?s).*Points +\\| +33660 +\\| +33660 +\\| +33660.*"),output);
            assertTrue(output.contains("October opening statement"),output);
            assertEquals(new BigDecimal("33.59"),new ProviderBalanceSummary(repo).balance(LocalDate.of(2026,10,5),"Fetch",List.of()).startUsd());
        }
    }
    @Test void editingDraftFinancialsCanSaveReceiptWhileRebateAmountAwaitsReview() {
        var repo=new InMemoryTransactionRepository();
        var offer=new GiftCardPromotion(UUID.randomUUID(),"Online",new BigDecimal("75"),new BigDecimal("25"),
                LocalDate.of(2026,10,1),LocalDate.of(2026,10,31),GiftCardPromotion.Basis.REVIEWED_ELIGIBLE_AMOUNT,
                "Confirmed online terms",GiftCardPromotion.Status.TRACKING,Map.of());
        repo.saveGiftCardPromotion(offer);
        String script=String.join("\n","NEW","Guard","","10/4/2026","Store","AJ",
                "","Charmin","5","1","0","0","n","n",
                "PROMOTIONS","1","1","4.00","Charmin qualifies",
                "0","0","0","n","5","n","n","n","n",
                "EDIT","ITEM", "ALL","1","","Charmin","6","1","0","0",
                "SAVE", "11")+"\n";
        var output=run(repo,script);
        assertTrue(output.contains("Online eligible amount needs review"),output);
        assertFalse(output.contains("Receipt not saved:"),output);
        assertTrue(output.contains("RECEIPT SAVED SUCCESSFULLY"),output);
        assertTrue(output.contains("Online rebate links awaiting amount review: 1"),output);
        var records=repo.findPossibleDuplicates(new ReceiptMeta("Store","Guard",null,null));
        assertEquals(1,records.size(),output);
        assertEquals(1,repo.history().findByReceiptId(records.get(0).transactionId()).size());
        assertNull(records.get(0).transaction().getObservations().promotionLinks().get(0).reviewedEligibleAmount());
        var savedOffer=repo.giftCardPromotions().get(0);
        assertTrue(savedOffer.eligibleAmount(savedOffer.contributions().get(records.get(0).transactionId()),records.get(0)).isEmpty());
        assertEquals(1,repo.giftCardPromotions().get(0).contributions().size());
    }
}
