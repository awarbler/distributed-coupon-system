package coupons;

import coupons.repository.*;
import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CliNavigationAndPromotionProgressTest {
    private final InMemoryTransactionRepository repo = new InMemoryTransactionRepository();
    private ReceiptWriteRequest request() { return new ReceiptWriteRequest(UUID.randomUUID(), Instant.now()); }
    private ReceiptRecord receipt(String number, String name, String price, int quantity) {
        var tx = new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO, BigDecimal.ZERO));
        tx.addPurchaseItem(new PurchaseItem(name, null, null, new BigDecimal(price), quantity));
        tx.addPurchaseItem(new PurchaseItem("H&S excluded", null, null, new BigDecimal("7"), 1));
        tx.setObservations(new ReceiptObservations(LocalDate.of(2026,10,4),null,List.of(),List.of(),null,List.of(),Map.of()));
        var id = UUID.randomUUID();
        repo.save(id,tx,new ReceiptMeta("Walgreens",number,null,null),List.of(),request(),new ReceiptCreateCtx("AJ"));
        return repo.findById(id).orElseThrow();
    }
    private GiftCardPromotion offer() {
        var offer = new GiftCardPromotion(UUID.randomUUID(),"PG October Rebate",new BigDecimal("75.00"),new BigDecimal("25.00"),
                LocalDate.of(2026,10,1),LocalDate.of(2026,10,31),GiftCardPromotion.Basis.REVIEWED_ELIGIBLE_AMOUNT,
                "Subtract manufacturer coupon from promotion price",GiftCardPromotion.Status.TRACKING,Map.of());
        repo.saveGiftCardPromotion(offer); return offer;
    }
    private GiftCardPromotion.Contribution link(ReceiptRecord receipt, String amount) {
        return new GiftCardPromotion.Contribution(receipt.transactionId(),receipt.revision(),
                List.of(receipt.transaction().getPurchaseItems().get(0).getLineItemId()),new BigDecimal(amount),"Charmin only; H&S excluded");
    }
    private String run(String script) {
        var bytes = new ByteArrayOutputStream();
        new ReceiptEntryApp(new Scanner(script),new PrintStream(bytes),repo).run();
        return bytes.toString();
    }
    @Test void mainListShowsProgressAndDetailGroupsAllEligibleItemsByReceipt() {
        var first = receipt("R1","CHARMIN SOFT US", "5.49",4);
        var second = receipt("R2","Charmin second purchase","10.00",2);
        var offer = offer().withContribution(link(first,"20.96")).withContribution(link(second,"19.00"));
        repo.saveGiftCardPromotion(offer);
        String output = run("PROMOTIONS\nVIEW\n1\nBACK\n9\n");
        assertTrue(output.indexOf("Qualifying spend: $39.96 / $75.00") < output.indexOf("1. New\n2. View\n3. Link\n4. Review\n5. Status\n6. Back"), output);
        assertTrue(output.contains("Still needed: $35.04"), output);
        assertTrue(output.contains("Potential gift card: $25.00"), output);
        assertTrue(output.matches("(?s).*\\| Receipt +\\| R1 +\\|.*"), output);
        assertTrue(output.matches("(?s).*Quantity +\\| 4 .*Sale total +\\| \\$21.96 .*"), output);
        assertTrue(output.matches("(?s).*\\| Receipt +\\| R2 +\\|.*"), output);
        assertTrue(output.matches("(?s).*Quantity +\\| 2 .*Sale total +\\| \\$20.00 .*"), output);
        assertFalse(output.contains("  H&S excluded\n"), output);
        assertEquals(2,repo.giftCardPromotions().get(0).contributions().size());
    }
    @Test void unchangedCorrectionRetainsReviewedProgress() {
        var receipt=receipt("R1","Charmin","5.49",4);
        var offer=offer().withContribution(link(receipt,"20.96"));repo.saveGiftCardPromotion(offer);
        repo.update(receipt.transactionId(),1,receipt.transaction(),receipt.metadata(),List.of(),request(),
                new ReceiptChangeContext("AJ","Correction","AJ",Instant.now()));
        String output=run("PROMOTIONS\nVIEW\n1\nBACK\n9\n");
        assertTrue(output.contains("Qualifying spend: $20.96 / $75.00"),output);
        assertFalse(output.contains("Contributions needing review: 1"),output);
        assertTrue(output.matches("(?s).*\\| Applied to rebate +\\| \\$20.96 +\\|.*"),output);
    }
    @Test void changedSelectedPriceExplainsReviewAndReviewActionReplacesContribution() {
        var saved=receipt("R1","Charmin","5.49",4);
        var offer=offer().withContribution(link(saved,"20.96")); repo.saveGiftCardPromotion(offer);
        var snapshot=ReceiptSnapshot.from(saved.transaction()); var lines=new ArrayList<>(snapshot.purchaseItems());
        var item=lines.get(0); lines.set(0,new PurchaseLineSnap(item.lineItemId(),item.productName(),item.productCode(),item.regularUnitPrice(),new BigDecimal("6"),item.quantity()));
        var changed=new ReceiptSnapshot(lines,snapshot.coupons(),snapshot.appliedCoupons(),snapshot.promotions(),snapshot.payments(),snapshot.rewards(),snapshot.registerRewardRedemptions(),snapshot.receiptTotals(),snapshot.observations());
        repo.update(saved.transactionId(),1,changed.toTransaction(),saved.metadata(),List.of(),request(),new ReceiptChangeContext("AJ","Price correction","AJ",Instant.now()));
        String pending=run("PROMOTIONS\nVIEW\n1\nBACK\n9\n");
        assertTrue(pending.contains("Previously recorded: $20.96"),pending);
        assertTrue(pending.contains("quantity or price changed"),pending);
        String reviewed=run("PROMOTIONS\nREVIEW\n1\nR1\n1\n23.00\nCorrected price checked\ny\nBACK\n9\n");
        assertTrue(reviewed.contains("Qualifying spend: $23.00 / $75.00"),reviewed);
        assertEquals(1,repo.giftCardPromotions().get(0).contributions().size());
    }
    @Test void correctionSaveConfirmsPromotionAtomicallyWithoutASecondReview() {
        var saved=receipt("R1","Charmin","5.49",4);
        repo.saveGiftCardPromotion(offer().withContribution(link(saved,"20.96")));
        String output=run("UPDATE\nR1\nREASON\nCheck receipt\nSAVE\nPROMOTIONS\nVIEW\n1\nBACK\n9\n");
        assertTrue(output.contains("Final receipt confirmation also approves"),output);
        var current=repo.findById(saved.transactionId()).orElseThrow();
        assertEquals(2,current.revision());
        assertEquals(2,repo.giftCardPromotions().get(0).contributions().get(saved.transactionId()).reviewedRevision());
        assertTrue(output.matches("(?s).*\\| Applied to rebate +\\| \\$20.96 +\\|.*"),output);
    }
    @Test void unrelatedProductCorrectionKeepsCharminApproved() {
        var saved=receipt("R1","Charmin","5.49",4);
        repo.saveGiftCardPromotion(offer().withContribution(link(saved,"20.96")));
        var snap=ReceiptSnapshot.from(saved.transaction()); var items=new ArrayList<>(snap.purchaseItems());
        var excluded=items.get(1); items.set(1,new PurchaseLineSnap(excluded.lineItemId(),"H&S corrected name",null,null,new BigDecimal("8"),1));
        var changed=new ReceiptSnapshot(items,snap.coupons(),snap.appliedCoupons(),snap.promotions(),snap.payments(),snap.rewards(),snap.registerRewardRedemptions(),snap.receiptTotals(),snap.observations());
        repo.update(saved.transactionId(),1,changed.toTransaction(),saved.metadata(),List.of(),request(),new ReceiptChangeContext("AJ","Unrelated product","AJ",Instant.now()));
        assertTrue(run("PROMOTIONS\nVIEW\n1\nBACK\n9\n").matches("(?s).*\\| Applied to rebate +\\| \\$20.96 +\\|.*"));
    }
    @Test void relevantCouponChangeNeedsReviewAndShowsOriginalAmount() {
        var saved=receipt("R1","Charmin","5.49",4);
        repo.saveGiftCardPromotion(offer().withContribution(link(saved,"20.96")));
        var tx=saved.transaction(); var coupon=new CouponApplied("New manufacturer coupon",new BigDecimal("2"),List.of(tx.getPurchaseItems().get(0).getLineItemId()));
        tx.addAppliedCoupon(coupon);
        repo.update(saved.transactionId(),1,tx,saved.metadata(),List.of(),request(),new ReceiptChangeContext("AJ","Coupon correction","AJ",Instant.now()));
        var output=run("PROMOTIONS\nVIEW\n1\nBACK\n9\n");
        assertTrue(output.contains("Discount amounts or product associations changed."),output);
        assertTrue(output.contains("Previously recorded: $20.96"),output);
        assertTrue(output.matches("(?s).*\\| Applied to rebate +\\| awaiting confirmation +\\|.*"),output);
    }
    @Test void legacyDiscountClassificationKeepsReviewedAmountAndDisplaysActualSaleTotal() {
        var saved=receipt("R1","Charmin","7.49",4);
        var tx=saved.transaction(); var coupon=new CouponApplied("Sale savings",new BigDecimal("8.00"),List.of(tx.getPurchaseItems().get(0).getLineItemId()));
        tx.addAppliedCoupon(coupon); var obs=tx.getObservations();
        tx.setObservations(new ReceiptObservations(obs.purchaseDate(),null,List.of(),List.of(),null,List.of(),Map.of(coupon.getApplicationId(),ReceiptObservations.DiscountKind.STORE)));
        repo.update(saved.transactionId(),1,tx,saved.metadata(),List.of(),request(),new ReceiptChangeContext("AJ","Recorded savings","AJ",Instant.now()));
        saved=repo.findById(saved.transactionId()).orElseThrow();
        repo.saveGiftCardPromotion(offer().withContribution(link(saved,"20.96")));
        var output=run("UPDATE\nR1\nDISCOUNTS\nALL\n1\n1\n0\nREASON\nClassify sale\nSAVE\nPROMOTIONS\nVIEW\n1\nBACK\n9\n");
        assertTrue(output.matches("(?s).*\\| Sale total +\\| \\$21.96 +\\|.*"),output);
        assertFalse(output.matches("(?s).*\\| Sale total +\\| \\$29.96 +\\|.*"),output);
        assertTrue(output.matches("(?s).*\\| Applied to rebate +\\| \\$20.96 +\\|.*"),output);
        assertEquals(3,repo.giftCardPromotions().get(0).contributions().get(saved.transactionId()).reviewedRevision());
    }
    @Test void backAndCancelEscapePromotionSelectorsAndProductSelectionWithoutValidationErrorsOrWrites() {
        receipt("R1","Charmin","5.49",4);offer();
        String output=run("PROMOTIONS\nLINK\nBACK\nLINK\n1\nBACK\nLINK\n1\nR1\nback\nLINK\n1\nR1\ncancel\ny\nBACK\n9\n");
        assertFalse(output.contains("Enter a whole number"),output);
        assertFalse(output.contains("Choose listed products once each"),output);
        assertTrue(repo.giftCardPromotions().get(0).contributions().isEmpty());
        assertEquals(1,repo.creationHistory().size());
    }
    @Test void linkBackRetainsProductAndAmountButLetsUserCorrectNoteBeforeSingleCommit() {
        receipt("R1","Charmin","5.49",4);offer();
        String output=run("PROMOTIONS\nLINK\n1\nR1\n1\n20.96\nWrong note\nBACK\nCorrect note\ny\nBACK\n9\n");
        var contribution=repo.giftCardPromotions().get(0).contributions().values().iterator().next();
        assertEquals("Correct note",contribution.note());
        assertEquals(new BigDecimal("20.96"),contribution.reviewedAmount());
        assertEquals(1,contribution.lineIds().size());
        assertTrue(output.contains("earlier answers retained"),output);
        assertEquals(1,repo.giftCardPromotions().get(0).contributions().size());
    }
    @Test void newOfferBackRevisesPriorQuestionWithoutCreatingDuplicateOffers() {
        String output=run("PROMOTIONS\nNEW\nWrong name\nBACK\nCorrect name\n75\n25\n10/1/2026\n10/31/2026\n2\nCharmin only\ny\nBACK\n9\n");
        assertEquals(1,repo.giftCardPromotions().size(),output);
        assertEquals("Correct name",repo.giftCardPromotions().get(0).name());
        assertEquals(new BigDecimal("75"),repo.giftCardPromotions().get(0).threshold());
    }
    @Test void statusBackAndCancelledExitLeaveExistingOfferUnchanged() {
        receipt("R1","Charmin","5.49",4);offer();
        String output=run("PROMOTIONS\nSTATUS\n1\n3\nBACK\n1\ny\nLINK\n1\nR1\n1\nEXIT\nn\nCANCEL\ny\nBACK\n9\n");
        assertEquals(GiftCardPromotion.Status.TRACKING,repo.giftCardPromotions().get(0).status());
        assertTrue(repo.giftCardPromotions().get(0).contributions().isEmpty());
        assertTrue(output.contains("Exit CLI and discard unsaved work?"),output);
    }
    @Test void balanceBackRevisesOriginalPointsAndCancelNeverWritesAdjustment() {
        String output=run("BALANCE SETTINGS\n10/4/2026\n6\n8\ny\n33660\nBACK\n33661\n33.59\nRecorded valuation\nStatement\ny\n7\n8\n-25\nCANCEL\ny\n9\n9\n");
        assertEquals(1,repo.accountBalances().openings().size(),output);
        assertEquals(new BigDecimal("33661"),repo.accountBalances().openings().get(0).originalUnits());
        assertTrue(repo.accountBalances().adjustments().isEmpty());
    }
    @Test void standaloneRewardBackChangesAmountWithoutDuplicatingCoupon() {
        String output=run("5\n2\nNeutrogena\n7\nBACK\n8\n\n\ny\n5\n9\n");
        assertEquals(1,repo.recordedRewards().size(),output);
        assertEquals(new BigDecimal("8"),repo.recordedRewards().get(0).getFaceValue());
    }
    @Test void savedCorrectionBackAndDeclinedCancelRetainProposalUntilConfirmedSave() {
        var receipt=receipt("R1","Charmin","5.49",4);
        String output=run("UPDATE\nR1\nTOTALS\nALL\n1\nBACK\n2\n23.96\nCANCEL\nn\nREASON\nTax correction\nREASON\nBetter reason\nSAVE\n9\n");
        var current=repo.findById(receipt.transactionId()).orElseThrow();
        assertEquals(2,current.revision(),output);
        assertEquals(new BigDecimal("2"),current.transaction().getReceiptTotals().getTaxAmount());
        assertEquals("Better reason",repo.history().findByRevision(receipt.transactionId(),2).orElseThrow().changeCtx().reason());
    }
    @Test void newReceiptCanBackFromTaxToProductsAndDeclineExitWithoutLosingDraft() {
        String script=String.join("\n","NEW","DraftNav","953am","10/4/2026","Store","AJ",
                "001","Original product","5","1","0","0","n","n",
                "BACK","EDIT","1","001","Corrected product","6","1","0","0","DONE",
                "EXIT","n","0","0","0","n","6","n","n","n","n","SAVE", "9")+"\n";
        String output=run(script);
        var records=repo.findPossibleDuplicates(new ReceiptMeta("Store","DraftNav",null,null));
        assertEquals(1,records.size(),output);
        var tx=records.get(0).transaction();
        assertEquals(1,tx.getPurchaseItems().size());
        assertEquals("Corrected product",tx.getPurchaseItems().get(0).getProductName());
        assertEquals(new BigDecimal("6"),tx.getPurchaseItems().get(0).getSaleUnitPrice());
        assertEquals(1,repo.history().findByReceiptId(records.get(0).transactionId()).size());
        assertTrue(output.contains("Receipt draft retained"),output);
    }
    @Test void receiptMetadataBackRetainsReceiptNumberAndRevisesPreviousTime() {
        String script=String.join("\n","NEW","MetadataNav","940am","BACK","953am","10/4/2026","Store","AJ",
                "","Item","5","1","0","0","n","n","0","0","0","n","5","n","n","n","n","SAVE", "9")+"\n";
        String output=run(script);
        var records=repo.findPossibleDuplicates(new ReceiptMeta("Store","MetadataNav",null,null));
        assertEquals(1,records.size(),output);
        assertEquals("953am",records.get(0).transaction().getObservations().printedTime());
    }
    @Test void cancellationOfDraftReceiptRequiresConfirmationAndCreatesNothing() {
        String script=String.join("\n","NEW","CancelNav","","","Store","AJ",
                "","Item","5","1","0","0","n","n","0","0","0","n","5","n","n","n","n","CANCEL","y", "9")+"\n";
        String output=run(script);
        assertTrue(output.contains("Discard this unsaved receipt"),output);
        assertTrue(repo.creationHistory().isEmpty());
    }
}
