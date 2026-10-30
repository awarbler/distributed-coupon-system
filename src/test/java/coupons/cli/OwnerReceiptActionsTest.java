package coupons.cli;

import coupons.receipts.CouponTransaction;
import coupons.receipts.Payment;
import coupons.receipts.PaymentType;
import coupons.receipts.PurchaseItem;
import coupons.receipts.ReceiptObservations;
import coupons.receipts.ReceiptTotals;
import coupons.receipts.TransactionCalculator;

import coupons.promotions.BenefitKind;
import coupons.promotions.CouponApplied;
import coupons.promotions.RebateBenefit;
import coupons.promotions.RebateStatus;

import coupons.rewards.ProviderBalanceSummary;

import coupons.receipts.ReceiptObservations.*;
import coupons.repository.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class OwnerReceiptActionsTest {
    private static final LocalDate DAY = LocalDate.of(2026,10,4);
    private static final Currency USD = Currency.getInstance("USD");
    private BigDecimal n(String value) { return new BigDecimal(value); }
    private ReceiptWriteRequest request() { return new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()); }
    private Earning earn(EarningKind kind, String provider, BenefitKind form, String amount, String usd) {
        return new Earning(UUID.randomUUID(),kind,provider,null,Scope.RECEIPT,List.of(),null,
                new RebateBenefit(form,n(amount),form == BenefitKind.POINTS ? null : USD,provider),RebateStatus.PAID,
                null,null,null,List.of(),null,null,usd == null ? null : new RecordedValuation(n(usd),USD,"Spreadsheet"));
    }
    private ReceiptRecord receipt(InMemoryTransactionRepository repo) {
        var tx = new CouponTransaction(new ReceiptTotals(n("1.04"),n("3.60")));
        tx.addPurchaseItem(new PurchaseItem("Neutrogena",null,n("7.79"),n("7.79"),2));
        tx.addPurchaseItem(new PurchaseItem("Lysol",null,n("3.29"),n("3.29"),2));
        var sale = new CouponApplied("Sale",n("3.60"),List.of(tx.getPurchaseItems().get(0).getLineItemId()));
        var mq = new CouponApplied("MQ",n("4.00"),List.of(tx.getPurchaseItems().get(0).getLineItemId()));
        tx.addAppliedCoupon(sale); tx.addAppliedCoupon(mq);
        tx.addPayment(new Payment(PaymentType.WALGREENS_CASH,n("10.00")));
        tx.addPayment(new Payment(PaymentType.CASH,n("3.60")));
        tx.setObservations(new ReceiptObservations(DAY,"9:40AM",List.of(
                earn(EarningKind.REGISTER_REWARD,"Walgreens",BenefitKind.STORE_COUPON,"7",null),
                earn(EarningKind.REGISTER_REWARD,"Walgreens",BenefitKind.STORE_COUPON,"5",null),
                earn(EarningKind.WALGREENS_CASH,"Walgreens",BenefitKind.STORE_REWARD,"0.80",null),
                earn(EarningKind.THIRD_PARTY_REBATE,"Fetch",BenefitKind.POINTS,"25","0.025")),
                List.of(new UnidentifiedRR(UUID.randomUUID(),n("2.00"),"Palmolive")),null,List.of(),
                Map.of(sale.getApplicationId(),DiscountKind.SALE_PROMOTION,mq.getApplicationId(),DiscountKind.MANUFACTURER)));
        var id = UUID.randomUUID();
        repo.save(id,tx,new ReceiptMeta("Walgreens","R1",null,null),List.of(),request(),new ReceiptCreateCtx("AJ"));
        return repo.findById(id).orElseThrow();
    }
    private String run(InMemoryTransactionRepository repo, String script) {
        var bytes = new ByteArrayOutputStream();
        new ReceiptEntryApp(new Scanner(script),new PrintStream(bytes),repo).run(); return bytes.toString();
    }
    @Test void spreadsheetSummaryKeepsPointPrecisionAndCountsEachBenefitOnce() {
        var repo = new InMemoryTransactionRepository(); receipt(repo);
        var output = run(repo,"2\nR1\nFULL\nBACK\n9\n");
        assertTrue(output.matches("(?s).*Other rebates/point value earned +\\| +\\$0.03 .*"),output);
        assertTrue(output.matches("(?s).*TOTAL FUTURE VALUE EARNED +\\| +\\$12.83 .*"),output);
        assertTrue(output.matches("(?s).*PAID AFTER EARNED BENEFITS +\\| +-\\$9.23 .*"),output);
        assertTrue(output.matches("(?s).*TOTAL SAVED +\\| +\\$32.43 .*"),output);
        assertTrue(output.indexOf("REGISTER REWARDS\n") < output.indexOf("WALGREENS CASH\n"));
        assertTrue(output.indexOf("WALGREENS CASH\n") < output.indexOf("OTHER REBATES\n"));
        assertTrue(output.contains("25 points"),output);
    }
    @Test void salePricesAlreadyEnteredDoNotDeductSaleSavingsAgainInSpreadsheetTotals() {
        var repo = new InMemoryTransactionRepository(); var saved = receipt(repo);
        var before = ReceiptSnapshot.from(saved.transaction()); var lines = new ArrayList<>(before.purchaseItems());
        var line = lines.get(0); lines.set(0,new PurchaseLineSnap(line.lineItemId(),line.productName(),line.productCode(),line.regularUnitPrice(),n("5.99"),2));
        var coupons = before.appliedCoupons().stream().filter(c -> !c.getPrintedLabel().equals("Sale")).toList();
        var o = before.observations(); var kinds = new HashMap<UUID,DiscountKind>(); coupons.forEach(c -> kinds.put(c.getApplicationId(),DiscountKind.MANUFACTURER));
        var obs = new ReceiptObservations(o.purchaseDate(),o.printedTime(),o.earnings(),o.unidentifiedRR(),o.walgreensCash(),o.savings(),kinds);
        var changed = new ReceiptSnapshot(lines,before.coupons(),coupons,before.promotions(),before.payments(),before.rewards(),before.registerRewardRedemptions(),before.receiptTotals(),obs);
        repo.update(saved.transactionId(),1,changed.toTransaction(),saved.metadata(),List.of(),request(),new ReceiptChangeContext("AJ","Recorded sale price","AJ",Instant.now()));
        var output = run(repo,"2\nR1\nFULL\nBACK\n9\n");
        assertTrue(output.matches("(?s).*Sale/promotion reductions +\\| +\\$3.60 .*"),output);
        assertTrue(output.matches("(?s).*Regular total including tax +\\| +\\$23.20 .*"),output);
        assertTrue(output.matches("(?s).*PAID AFTER EARNED BENEFITS +\\| +-\\$9.23 .*"),output);
        assertEquals(n("3.60"),new TransactionCalculator().calculateExpectedCheckoutAmount(changed.toTransaction()));
    }
    @Test void ownerSaveCommitsImmediatelyAndNotesRetainTheExistingRedemptionIdentity(@TempDir Path directory) throws Exception {
        var file = directory.resolve("receipts.bin"); UUID id; UUID event;
        try (var repo = InMemoryTransactionRepository.openLocal(file)) {
            var saved = receipt(repo); id = saved.transactionId(); event = saved.transaction().getObservations().unidentifiedRR().get(0).eventId();
            var output = run(repo,"3\nR1\nRR NOTES\n1\nFrom previous month: September\nSAVE\n5\n1\n5\n9\n");
            assertTrue(output.contains("Correction saved:"),output);
            assertFalse(output.contains("Correction confirmed by"),output);
            assertFalse(output.contains("Save these proposed changes"),output);
            assertTrue(output.contains("From previous month: September"),output);
            assertEquals(2,repo.findById(id).orElseThrow().revision());
            assertEquals(event,repo.findById(id).orElseThrow().transaction().getObservations().unidentifiedRR().get(0).eventId());
        }
        try (var repo = InMemoryTransactionRepository.openLocal(file)) {
            var use = repo.findById(id).orElseThrow().transaction().getObservations().unidentifiedRR().get(0);
            assertEquals("From previous month: September",use.notes()); assertEquals(n("2.00"),use.amount());
        }
    }
    @Test void deletionIsAtomicRetryableAndHistoricalAcrossRestart(@TempDir Path directory) throws Exception {
        var file = directory.resolve("receipts.bin"); UUID id; ReceiptSnapshot original;
        var request = request(); var context = new ReceiptChangeContext("Local owner","Delete duplicate","Local owner",Instant.now());
        try (var repo = InMemoryTransactionRepository.openLocal(file)) {
            var saved = receipt(repo); id = saved.transactionId(); original = ReceiptSnapshot.from(saved.transaction());
            assertThrows(java.util.ConcurrentModificationException.class,() -> repo.softDelete(saved.transactionId(),99,request(),context));
            var backup = directory.resolve("backup.bin"); Files.move(file,backup); Files.createDirectory(file);
            assertThrows(IllegalStateException.class,() -> repo.softDelete(saved.transactionId(),1,request,context));
            assertEquals(1,repo.findById(id).orElseThrow().revision()); assertEquals(1,repo.history().findByReceiptId(id).size());
            Files.delete(file); Files.move(backup,file);
            var result = repo.softDelete(id,1,request,context);
            assertEquals(result,repo.softDelete(id,1,request,context));
            assertEquals(2,repo.history().findByReceiptId(id).size());
            assertTrue(repo.findById(id).isEmpty());
            assertEquals(original,ReceiptSnapshot.from(repo.findDeletedById(id).orElseThrow().transaction()));
        }
        try (var repo = InMemoryTransactionRepository.openLocal(file)) {
            assertEquals(ReceiptStatus.DELETED,repo.findDeletedById(id).orElseThrow().status());
            var output = run(repo,"8\nHISTORY\n1\n9\n");
            assertTrue(output.contains("Receipt Deleted"),output);
            assertFalse(output.contains("Operation not saved"),output);
            assertEquals(ReceiptChangeAction.DELETED,repo.softDelete(id,1,request,context).action());
        }
    }
    @Test void cliDeletionRemovesActivityAndPromotionProgressButKeepsHistory() {
        var repo = new InMemoryTransactionRepository(); var saved = receipt(repo);
        var balances = new ProviderBalanceSummary(repo); balances.recordOpening(DAY,"RR",n("2.00"),true);
        var offer = new GiftCardPromotion(UUID.randomUUID(),"Offer",n("75"),n("25"),DAY,DAY.plusDays(20),GiftCardPromotion.Basis.REVIEWED_ELIGIBLE_AMOUNT,"Reviewed",GiftCardPromotion.Status.TRACKING,Map.of());
        var link = new GiftCardPromotion.Contribution(saved.transactionId(),1,List.of(saved.transaction().getPurchaseItems().get(0).getLineItemId()),n("10"),"Eligible");
        repo.saveGiftCardPromotion(offer.withContribution(link));
        var output = run(repo,"4\nR1\nDELETE\n6\nBACK\n9\n");
        assertTrue(output.contains("Receipt deleted."),output);
        assertEquals(n("2.00"),balances.balance(DAY,"RR",List.of()).endUsd());
        assertTrue(repo.giftCardPromotions().get(0).eligibleAmount(link,repo.findById(saved.transactionId()).orElse(null)).isEmpty());
        assertEquals(2,repo.history().findByReceiptId(saved.transactionId()).size());
    }
    @Test void missingPointValuationProducesPartialTotalsInsteadOfAnInventedConversion() {
        var repo = new InMemoryTransactionRepository(); var saved = receipt(repo); var tx = saved.transaction(); var o = tx.getObservations();
        var earnings = new ArrayList<>(o.earnings()); earnings.add(earn(EarningKind.THIRD_PARTY_REBATE,"Merryfield",BenefitKind.POINTS,"250",null));
        tx.setObservations(new ReceiptObservations(o.purchaseDate(),o.printedTime(),earnings,o.unidentifiedRR(),o.walgreensCash(),o.savings(),o.discountKinds()));
        repo.update(saved.transactionId(),1,tx,saved.metadata(),List.of(),request(),new ReceiptChangeContext("AJ","Add PG","AJ",Instant.now()));
        var output = run(repo,"2\nR1\nFULL\nBACK\n9\n");
        assertTrue(output.contains("250 points"),output);
        assertTrue(output.matches("(?s).*TOTAL FUTURE VALUE EARNED +\\| +partial \\$12.83 .*"),output);
        assertTrue(output.contains("Missing USD valuations"),output);
    }
}
