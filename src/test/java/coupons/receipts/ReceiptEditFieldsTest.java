package coupons.receipts;

import coupons.cli.ReceiptEntryApp;

import coupons.receipts.CouponTransaction;
import coupons.receipts.Payment;
import coupons.receipts.PaymentType;
import coupons.receipts.PurchaseItem;
import coupons.receipts.ReceiptObservations;
import coupons.receipts.ReceiptReport;
import coupons.receipts.ReceiptTimeInput;
import coupons.receipts.ReceiptTotals;

import coupons.promotions.BenefitKind;
import coupons.promotions.CouponApplied;
import coupons.promotions.RebateBenefit;
import coupons.promotions.RebateStatus;

import coupons.rewards.Reward;

import coupons.receipts.ReceiptObservations.*;
import coupons.repository.*;
import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReceiptEditFieldsTest {
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private ReceiptRecord seed(InMemoryTransactionRepository repo) {
        var tx=new CouponTransaction(new ReceiptTotals(n("0.10"),n("4.60")));
        tx.addPurchaseItem(new PurchaseItem("Milk","001",n("5"),n("5"),1));
        tx.addPurchaseItem(new PurchaseItem("Bread","002",n("3"),n("3"),1));
        var first=new CouponApplied("Manufacturer",n("1"),List.of(tx.getPurchaseItems().get(0).getLineItemId()));
        var second=new CouponApplied("IVC",n("0.50"),List.of(tx.getPurchaseItems().get(1).getLineItemId()));
        // Intentionally reversed: unrelated edits must not reorder deductions.
        tx.addAppliedCoupon(second);tx.addAppliedCoupon(first);
        tx.addPayment(new Payment(PaymentType.WALGREENS_CASH,n("1")));
        tx.addPayment(new Payment(PaymentType.CASH,n("4.60")));
        var fetch=new Earning(UUID.randomUUID(),EarningKind.THIRD_PARTY_REBATE,"Fetch","Receipt points",Scope.RECEIPT,List.of(),null,
                new RebateBenefit(BenefitKind.POINTS,n("25"),null,"Fetch"),RebateStatus.CREDITED,null,null,null,List.of(),null,null,
                new RecordedValuation(n("0.025"),Currency.getInstance("USD"),"Recorded"));
        tx.setObservations(new ReceiptObservations(LocalDate.of(2026,10,4),"940am",List.of(fetch),
                List.of(new UnidentifiedRR(UUID.randomUUID(),n("2"),"Previous coupon")),
                new WalgreensCashAccount(n("10"),n("1"),n("0"),n("9")),List.of(),
                Map.of(first.getApplicationId(),DiscountKind.MANUFACTURER,second.getApplicationId(),DiscountKind.STORE_COUPON)));
        UUID id=UUID.randomUUID();repo.save(id,tx,new ReceiptMeta("Walgreens","Edit-test",null,null),List.of(),
                new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptCreateCtx("Owner"));
        return repo.findById(id).orElseThrow();
    }
    private String run(InMemoryTransactionRepository repo,String script) {
        var bytes=new ByteArrayOutputStream();new ReceiptEntryApp(new Scanner(script),new PrintStream(bytes),repo).run();return bytes.toString();
    }
    @Test void registerRewardEditingRetainsPrintedStatusChoice() {
        var repo=new InMemoryTransactionRepository();var original=seed(repo);var tx=original.transaction();var o=tx.getObservations();
        var wc=new Earning(UUID.randomUUID(),EarningKind.WALGREENS_CASH,"Walgreens","Member Rewards",Scope.RECEIPT,List.of(),null,
                new RebateBenefit(BenefitKind.STORE_REWARD,n("1"),Currency.getInstance("USD"),"Walgreens"),RebateStatus.CREDITED,null,null,null,List.of());
        var rr=new Earning(UUID.randomUUID(),EarningKind.REGISTER_REWARD,"Walgreens","Pantene buy 3 get 4 rr",Scope.RECEIPT,List.of(),null,
                new RebateBenefit(BenefitKind.STORE_COUPON,n("4"),Currency.getInstance("USD"),"Walgreens"),RebateStatus.APPROVED,null,null,null,List.of());
        tx.setObservations(new ReceiptObservations(o.purchaseDate(),o.printedTime(),List.of(wc,rr),o.unidentifiedRR(),o.walgreensCash(),o.savings(),o.discountKinds()));
        repo.update(original.transactionId(),1,tx,original.metadata(),List.of(),new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptChangeContext("Owner","Add rewards","Owner",Instant.now()));
        String output=run(repo,"UPDATE\nEdit-test\nEARNINGS\nSELECT\n2\nSTATUS\n5\nDONE\nSAVE\n11\n");
        assertTrue(output.contains("1. Walgreens | Member Rewards"),output);
        assertTrue(output.contains("2. Walgreens | Pantene buy 3 get 4 rr"),output);
        assertFalse(output.contains("Selected earning:"),output);
        assertTrue(output.contains("1. Expected, not confirmed\n2. Submitted, awaiting result\n3. Approved / earned\n4. Added to program account\n5. Register Reward Printed\n6. Rejected / not issued"),output);
        var saved=repo.findById(original.transactionId()).orElseThrow().transaction().getObservations().earnings();
        assertEquals(RebateStatus.CREDITED,saved.get(0).status());assertEquals(RebateStatus.PAID,saved.get(1).status());
        assertEquals("Register Reward Printed",ReceiptReport.earningStatusText(saved.get(1)));
    }

    @Test void receiptWideOnlineDiscountCanBeReclassifiedAndLinkedWithoutAddingItAgain() {
        var repo=new InMemoryTransactionRepository();var original=seed(repo);
        // Add one receipt-wide online coupon, then edit its classification and scope.
        String output=run(repo,"UPDATE\nEdit-test\nDISCOUNTS\nADD\n2\nOnline code\n1.00\n0\nDONE\nSAVE\nUPDATE\nEdit-test\nDISCOUNTS\nSELECT\n3\nDISCOUNT TYPE\n1\nPRODUCTS\n0\nDONE\nSAVE\n11\n");
        assertTrue(output.contains("Whole receipt"),output);
        assertTrue(output.contains("2. Discount type"),output);
        assertFalse(output.contains("2. Kind"),output);
        var saved=repo.findById(original.transactionId()).orElseThrow();var tx=saved.transaction();
        assertEquals(3,tx.getAppliedCoupons().size());
        var coupon=tx.getAppliedCoupons().stream().filter(c->c.getPrintedLabel().equals("Online code")).findFirst().orElseThrow();
        assertTrue(coupon.isWholeReceipt());assertTrue(coupon.getLineItemIds().isEmpty());
        assertEquals(DiscountKind.SALE_PROMOTION,tx.getObservations().discountKinds().get(coupon.getApplicationId()));
        var earlier=repo.history().findByRevision(saved.transactionId(),2).orElseThrow().after().contents().appliedCoupons().stream().filter(c->c.getPrintedLabel().equals("Online code")).findFirst().orElseThrow();
        assertEquals(earlier.getApplicationId(),coupon.getApplicationId());assertTrue(earlier.isWholeReceipt());
        assertEquals(DiscountKind.STORE_COUPON,repo.history().findByRevision(saved.transactionId(),2).orElseThrow().after().contents().observations().discountKinds().get(coupon.getApplicationId()));
    }
    @Test void deletesCouponAndRewardThroughExplicitActionsWithHistory() {
        var repo=new InMemoryTransactionRepository();var original=seed(repo);
        var output=run(repo,"UPDATE\nEdit-test\nCOUPONS\nDELETE\n1\ny\nDONE\nEARNINGS\nDELETE\n1\ny\nDONE\nSAVE\n11\n");
        assertTrue(output.contains("Deleted from draft"),output);
        assertTrue(output.contains("Correction saved:"),output);
        var current=repo.findById(original.transactionId()).orElseThrow();
        assertEquals(1,current.transaction().getAppliedCoupons().size());assertTrue(current.transaction().getObservations().earnings().isEmpty());
        assertEquals(2,repo.history().findByRevision(original.transactionId(),1).orElseThrow().after().contents().appliedCoupons().size());
        assertEquals(1,repo.history().findByRevision(original.transactionId(),1).orElseThrow().after().contents().observations().earnings().size());
    }
    @Test void deletesProductAndItsOwnDiscountButCancellationKeepsCoupon() {
        var repo=new InMemoryTransactionRepository();var original=seed(repo);
        var output=run(repo,"UPDATE\nEdit-test\nCOUPONS\nDELETE\n1\nn\nDONE\nITEM\nDELETE\n2\ny\nDONE\nSAVE\n11\n");
        assertTrue(output.contains("Deleted from draft"),output);
        assertTrue(output.contains("Correction saved:"),output);
        var current=repo.findById(original.transactionId()).orElseThrow();
        assertEquals(1,current.transaction().getPurchaseItems().size());
        assertEquals("Milk",current.transaction().getPurchaseItems().get(0).getProductName());
        assertEquals(1,current.transaction().getAppliedCoupons().size());
        assertEquals(2,repo.history().findByRevision(original.transactionId(),1).orElseThrow().after().contents().purchaseItems().size());
    }
    @Test void earningsAddExplainsNewAndExistingChoicesAndCanReturnWithoutChangingAnything() {
        var repo=new InMemoryTransactionRepository();var original=seed(repo);
        String output=run(repo,"UPDATE\nEdit-test\nEARNINGS\nADD\nBACK\nCANCEL\ny\n9\n");
        assertTrue(output.contains("8. Add\n9. Update\n10. Delete\n11. Done — return to review"),output);
        assertTrue(output.contains("What did you earn" ) || output.contains("BACK returns to the previous question"),output);
        assertFalse(output.contains("Remove this earning?"),output);
        assertEquals(1,repo.findById(original.transactionId()).orElseThrow().revision());
    }
    @Test void retrievedReceiptCanBeEditedAndItsViewReloadsTheSavedRevision() {
        var repo=new InMemoryTransactionRepository();var original=seed(repo);
        String output=run(repo,"2\nEdit-test\n5\nDETAILS\nTIME\n945am\nDONE\nSAVE\nFULL\nBACK\n9\n");
        assertTrue(output.contains("5. Edit this receipt\n6. Issues\n7. Back"),output);
        assertTrue(output.contains("Correction saved:"),output);
        assertTrue(output.contains("9:45 AM"),output);
        assertTrue(output.contains("RECEIPT SAVED SUCCESSFULLY\nReceipt  : Edit-test\nRevision : 2\nStatus   : Saved for this session only"),output);
        assertEquals(2,repo.findById(original.transactionId()).orElseThrow().revision());
    }
    @Test void eachFieldEditorReturnsWithDoneAndPreservesUnrelatedFinancialIdentities() {
        var edits=List.of("TOTALS\nTAX\n.08\nDONE", "DETAILS\nTIME\n945am\nDONE",
                "ITEM\nQUANTITY\n1\n2\nDONE", "ACCOUNT\nEARNED\n.80\nDONE",
                "SAVINGS\nMYWALGREENS\n2\nDONE", "EARNINGS\nAMOUNT\n1\n50\nDONE",
                "COUPONS\nAMOUNT\n1\n.75\nDONE", "DISCOUNTS\nKIND\n1\n1\nDONE",
                "REGULAR PRICES\nREGULAR PRICE\n1\n6\nDONE", "CHECKOUT\nPAYMENTS\n2\n4.50\nDONE");
        for(String edit:edits) {
            var repo=new InMemoryTransactionRepository();var original=seed(repo);var before=ReceiptSnapshot.from(original.transaction());
            String output=run(repo,"UPDATE\nEdit-test\n"+edit+"\nSAVE\n9\n");
            assertTrue(output.contains("Correction saved:"),edit+"\n"+output);
            var saved=repo.findById(original.transactionId()).orElseThrow();var after=ReceiptSnapshot.from(saved.transaction());
            assertEquals(2,saved.revision(),edit);
            assertEquals(before,repo.history().findByRevision(original.transactionId(),1).orElseThrow().after().contents());
            assertEquals(before.payments().stream().map(PaymentSnap::paymentId).toList(),after.payments().stream().map(PaymentSnap::paymentId).toList(),edit);
            assertEquals(before.appliedCoupons().stream().map(CouponApplied::getApplicationId).toList(),after.appliedCoupons().stream().map(CouponApplied::getApplicationId).toList(),edit);
            assertEquals(before.observations().unidentifiedRR(),after.observations().unidentifiedRR(),edit);
            if(edit.startsWith("TOTALS")) {
                assertEquals(n(".08"),after.receiptTotals().taxAmount());
                assertEquals(before.payments(),after.payments());assertEquals(before.appliedCoupons(),after.appliedCoupons());
                assertEquals(before.observations().discountKinds(),after.observations().discountKinds());
            }
            if(edit.startsWith("CHECKOUT"))assertEquals(n("4.50"),after.payments().get(1).amount());
            if(edit.startsWith("DETAILS")) assertEquals(LocalTime.of(9,45),ReceiptTimeInput.parse(after.observations().printedTime()).orElseThrow());
            if(edit.startsWith("ITEM")) assertEquals(2,after.purchaseItems().get(0).quantity());
            if(edit.startsWith("ACCOUNT")) {assertEquals(n(".80"),after.observations().walgreensCash().earned());assertEquals(n("10"),after.observations().walgreensCash().startingBalance());}
            if(edit.startsWith("SAVINGS")) assertEquals(n("2"),after.observations().savings().get(0).reportedAmount());
            if(edit.startsWith("COUPONS")) assertEquals(n(".75"),after.appliedCoupons().stream().filter(c->c.getPrintedLabel().equals("Manufacturer")).findFirst().orElseThrow().getAppliedAmount());
            if(edit.startsWith("DISCOUNTS")) assertEquals(DiscountKind.SALE_PROMOTION,after.observations().discountKinds().get(before.appliedCoupons().get(1).getApplicationId()));
            if(edit.startsWith("REGULAR PRICES")) {assertEquals(n("6"),after.purchaseItems().get(0).regularUnitPrice());assertEquals(n("5"),after.purchaseItems().get(0).saleUnitPrice());}
            if(edit.startsWith("EARNINGS")) {assertNull(after.observations().earnings().get(0).valuation());assertEquals(n("50"),after.observations().earnings().get(0).confirmed().amount());}
        }
    }
    @Test void doneOrCancelInsideNextFieldRetainsCompletedTaxAndDoesNotSaveEarly() {
        for(String exit:List.of("DONE", "CANCEL\ny\nDONE", "BACK\nDONE")) {
            var repo=new InMemoryTransactionRepository();var original=seed(repo);
            String output=run(repo,"UPDATE\nEdit-test\nTOTALS\nTAX\n.08\nPRINTED TOTAL\n"+exit+"\nSAVE\n9\n");
            assertTrue(output.contains("Correction saved:"),output);
            var saved=repo.findById(original.transactionId()).orElseThrow();
            assertEquals(n(".08"),saved.transaction().getReceiptTotals().getTaxAmount());
            assertEquals(n("4.60"),saved.transaction().getReceiptTotals().getFinalTotal());
            assertEquals(2,repo.history().findByReceiptId(original.transactionId()).size());
        }
        var repo=new InMemoryTransactionRepository();var original=seed(repo);
        run(repo,"UPDATE\nEdit-test\nTOTALS\nTAX\n.08\nDONE\nCANCEL\ny\n9\n");
        assertEquals(1,repo.findById(original.transactionId()).orElseThrow().revision());
    }
    @Test void itemPromotionAndManufacturerCouponsAreStoredAsSeparateDeductions() {
        var repo=new InMemoryTransactionRepository();var original=seed(repo);
        String output=run(repo,"UPDATE\nEdit-test\nITEM\nSELECT\n1\nSTORE COUPONS AND PROMOS\n1\nMyWalgreens savings\n1.50\nn\nMANUFACTURER COUPONS\nDigital MQ\n2\n.25\n1\nDONE\nSAVE\n9\n");
        assertTrue(output.contains("Correction saved:"),output);
        var tx=repo.findById(original.transactionId()).orElseThrow().transaction();
        var added=tx.getAppliedCoupons().stream().filter(c->c.getPrintedLabel().equals("MyWalgreens savings")).toList();
        assertEquals(1,added.size());
        assertEquals(n("1.50"),added.get(0).getAppliedAmount());
        assertEquals(DiscountKind.SALE_PROMOTION,tx.getObservations().discountKinds().get(added.get(0).getApplicationId()));
        assertEquals(2,tx.getAppliedCoupons().stream().filter(c->c.getPrintedLabel().equals("Digital MQ")).count());
    }
    @Test void discountEditorCanAddAnIvcWithoutReplacingExistingCoupons() {
        var repo=new InMemoryTransactionRepository();var original=seed(repo);
        String output=run(repo,"UPDATE\nEdit-test\nDISCOUNTS\nADD\n2\nNew IVC\n.75\n1\nDONE\nSAVE\n9\n");
        assertTrue(output.contains("Correction saved:"),output);
        var tx=repo.findById(original.transactionId()).orElseThrow().transaction();
        assertEquals(3,tx.getAppliedCoupons().size());
        var added=tx.getAppliedCoupons().stream().filter(c->c.getPrintedLabel().equals("New IVC")).findFirst().orElseThrow();
        assertEquals(DiscountKind.STORE_COUPON,tx.getObservations().discountKinds().get(added.getApplicationId()));
    }
    @Test void numberedSectionAndFieldChoicesKeepOriginalCommandsAndItemSelectionMeaning() {
        var repo=new InMemoryTransactionRepository();var original=seed(repo);
        String output=run(repo,"UPDATE\nEdit-test\n2\n1\n2\n6\n2\n13\nSAVE\n9\n");
        assertTrue(output.contains("Correction saved:"),output);
        assertTrue(output.contains("1. Choose a product to edit\n2. Change product name\n3. Change SKU / UPC\n4. Change regular price per item\n5. Change entered price per item (before separate discounts)\n6. Change quantity purchased\n7. Add a store coupon or sale/promotion discount\n8. Add a manufacturer coupon\n9. Open the complete product entry form\n10. Add\n11. Update\n12. Delete\n13. Done editing products — return to receipt review"),output);
        assertFalse(output.contains("SELECT / DESCRIPTION"),output);
        assertFalse(output.contains("Selection retained. Choose a field to change, or DONE.\nField updated"),output);
        var saved=repo.findById(original.transactionId()).orElseThrow();
        assertEquals(1,saved.transaction().getPurchaseItems().get(0).getQuantity());
        assertEquals(2,saved.transaction().getPurchaseItems().get(1).getQuantity());
    }
    @Test void numericCancelAndExitStillRequireConfirmationAndRetainTheUnsavedReceiptWhenDeclined() {
        for(String choice:List.of("8", "9")) {
            var repo=new InMemoryTransactionRepository();
            String entry=String.join("\n","NEW","Numeric-navigation","","","Store","Owner","","Item","5","1","0","0","n","n",
                    "0","0","0","n","5","n","n","n","n")+"\n";
            String output=run(repo,entry+choice+"\nn\n1\n9\n");
            assertTrue(output.contains("Save result:"),output);
            assertTrue(output.contains(choice.equals("8")?"Discard this unsaved receipt":"Exit CLI and discard unsaved work?"),output);
            assertEquals(1,repo.creationHistory().size());
        }
    }
    @Test void draftTaxEditAndDoneFromEverySectionKeepTheDraftUntilSingleSave() {
        var repo=new InMemoryTransactionRepository();
        String entry=String.join("\n","NEW","Draft-fields","","","Store","Owner","","Item","5","1","0","0","n","n",
                "0","0","0","n","5","n","n","n","n")+"\n";
        String edit="EDIT\nCHECKOUT\nTAX\n.08\nDONE\n";
        for(String section:List.of("DETAILS","ITEM","CHECKOUT","WORKSHEET","DISCOUNTS","COUPONS","PROMOTIONS","EARNINGS","ACCOUNT","SAVINGS","SAVINGS LINKS","EVIDENCE"))
            edit+="EDIT\n"+section+"\nDONE\n";
        String output=run(repo,entry+edit+"SAVE\n9\n");
        assertTrue(output.contains("Save result:"),output);
        var saved=repo.findPossibleDuplicates(new ReceiptMeta("Store","Draft-fields",null,null)).get(0);
        assertEquals(n(".08"),saved.transaction().getReceiptTotals().getTaxAmount());
        assertEquals(n("5"),saved.transaction().getReceiptTotals().getFinalTotal());
        assertEquals(1,repo.history().findByReceiptId(saved.transactionId()).size());
    }
}
