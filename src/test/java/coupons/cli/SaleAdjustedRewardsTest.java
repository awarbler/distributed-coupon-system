package coupons.cli;

import coupons.receipts.CouponTransaction;
import coupons.receipts.PurchaseItem;
import coupons.receipts.ReceiptObservations;
import coupons.receipts.ReceiptReport;
import coupons.receipts.ReceiptTotals;
import coupons.receipts.TransactionCalculator;

import coupons.promotions.BenefitKind;
import coupons.promotions.CouponApplied;
import coupons.promotions.PromotionType;
import coupons.promotions.QualifyingSpend;
import coupons.promotions.RebateBenefit;
import coupons.promotions.RebateStatus;

import coupons.rewards.RewardSpendCheck;

import coupons.receipts.ReceiptObservations.*;
import coupons.repository.*;
import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SaleAdjustedRewardsTest {
    private ReceiptWriteRequest request() { return new ReceiptWriteRequest(UUID.randomUUID(), Instant.now()); }
    private Earning earning(CouponTransaction tx, String description, String threshold, String reward, EarningKind kind) {
        return new Earning(UUID.randomUUID(), kind, "Walgreens", description, Scope.MULTI_ITEM,
                tx.getPurchaseItems().stream().map(PurchaseItem::getLineItemId).toList(), null,
                new RebateBenefit(kind==EarningKind.REGISTER_REWARD ? BenefitKind.STORE_COUPON : BenefitKind.STORE_REWARD,
                        new BigDecimal(reward), Currency.getInstance("USD"), "Walgreens"),RebateStatus.PAID,
                null,null,null,List.of(),null,new OfferRequirement(PromotionType.SPEND,new BigDecimal(threshold),null,SpendBasis.SALE_BEFORE_COUPONS),null);
    }
    private CouponTransaction regular(DiscountKind saleKind) {
        var tx=new CouponTransaction(new ReceiptTotals(new BigDecimal("1.73"),new BigDecimal("26.69")));
        tx.addPurchaseItem(new PurchaseItem("Charmin",null,null,new BigDecimal("7.49"),4));
        tx.addPurchaseItem(new PurchaseItem("H&S shampoo",null,null,new BigDecimal("8.49"),1));
        tx.addPurchaseItem(new PurchaseItem("H&S conditioner",null,null,new BigDecimal("8.49"),1));
        var kinds=new HashMap<UUID,DiscountKind>();String[] amounts={"8.00","1.49","1.49"};
        for(int i=0;i<3;i++) {
            var c=new CouponApplied("Printed promotion",new BigDecimal(amounts[i]),List.of(tx.getPurchaseItems().get(i).getLineItemId()));
            tx.addAppliedCoupon(c);kinds.put(c.getApplicationId(),saleKind);
        }
        var mq=new CouponApplied("Manufacturer coupons",new BigDecimal("4.00"),tx.getPurchaseItems().stream().map(PurchaseItem::getLineItemId).toList());
        tx.addAppliedCoupon(mq);kinds.put(mq.getApplicationId(),DiscountKind.MANUFACTURER);
        tx.setObservations(new ReceiptObservations(LocalDate.of(2026,10,4),"953am",List.of(),
                List.of(new UnidentifiedRR(UUID.randomUUID(),new BigDecimal("7.00"),"Neutrogena")),null,List.of(),kinds));
        return tx;
    }
    private String run(InMemoryTransactionRepository repo,String script) {
        var bytes=new ByteArrayOutputStream();new ReceiptEntryApp(new Scanner(script),new PrintStream(bytes),repo).run();return bytes.toString();
    }
    @Test void allThreeOffersUseSaleAdjustedSpendAndRenderOnceWithTheirOwnEarning() {
        var tx=regular(DiscountKind.SALE_PROMOTION);var obs=tx.getObservations();
        var earnings=List.of(earning(tx,"Spend 35 earn 9 WC","35","9",EarningKind.WALGREENS_CASH),
                earning(tx,"Spend 30 earn 8 WC","30","8",EarningKind.WALGREENS_CASH),
                earning(tx,"P&G spend 35 earn 10 RR","35","10",EarningKind.REGISTER_REWARD));
        tx.setObservations(new ReceiptObservations(obs.purchaseDate(),obs.printedTime(),earnings,obs.unidentifiedRR(),null,List.of(),obs.discountKinds()));
        for(var e:earnings) assertEquals(new BigDecimal("35.96"),RewardSpendCheck.evaluate(e,tx).qualifyingSpend());
        var bytes=new ByteArrayOutputStream();ReceiptReport.print(new ReceiptRecord(UUID.randomUUID(),ReceiptStatus.ACTIVE,1,tx),new PrintStream(bytes));
        String output=bytes.toString();
        assertFalse(output.contains("REWARD SPEND REQUIREMENTS"),output);
        assertFalse(output.contains("46.94\n  Spend requirement"),output);
        assertEquals(1,output.lines().filter(l->l.contains("Spend 35 earn 9 WC")).count());
        assertEquals(3,output.lines().filter(l->l.matches("(?s).*Qualifying spend +\\| \\$35.96 .*")).count());
        assertTrue(output.contains("Met — $5.96 above threshold"),output);
        assertEquals(2,output.lines().filter(l->l.contains("Met — $0.96 above threshold")).count());
        assertEquals(new BigDecimal("26.69"),new TransactionCalculator().calculateExpectedCheckoutAmount(tx));
    }
    @Test void ambiguousHistoricalDiscountRequiresExplicitClassificationAndPreservesIdsAndHistory(@TempDir Path dir) throws Exception {
        var file=dir.resolve("receipts.bin");var id=UUID.randomUUID();ReceiptSnapshot original;
        try(var repo=InMemoryTransactionRepository.openLocal(file)) {
            var tx=regular(DiscountKind.STORE);var obs=tx.getObservations();var e=earning(tx,"Spend 35 earn 9 WC","35","9",EarningKind.WALGREENS_CASH);
            tx.setObservations(new ReceiptObservations(obs.purchaseDate(),null,List.of(e),obs.unidentifiedRR(),null,List.of(),obs.discountKinds()));
            assertEquals(RewardSpendCheck.Status.CANNOT_VERIFY,RewardSpendCheck.evaluate(e,tx).status());
            original=ReceiptSnapshot.from(tx);
            repo.save(id,tx,new ReceiptMeta("Walgreens","PG",null,null),List.of(),request(),new ReceiptCreateCtx("AJ"));
            var output=run(repo,"UPDATE\nPG\nDISCOUNTS\nALL\n1\n1\n2\n1\n3\n1\n0\nREASON\nClassify printed sales\nSAVE\n9\n");
            assertTrue(output.matches("(?s).*Qualifying spend +\\| \\$35.96 .*"),output);
            var current=repo.findById(id).orElseThrow();
            assertEquals(original.appliedCoupons(),current.transaction().getAppliedCoupons());
            assertEquals(original,repo.history().findByRevision(id,1).orElseThrow().after().contents());
            assertEquals(2,current.revision());
        }
        try(var repo=InMemoryTransactionRepository.openLocal(file)) {
            var tx=repo.findById(id).orElseThrow().transaction();
            assertEquals(new BigDecimal("35.96"),RewardSpendCheck.evaluate(tx.getObservations().earnings().get(0),tx).qualifyingSpend());
        }
    }
    @Test void storeCouponsAreNotSaleReductionsAndSharedSaleReductionIsDeductedOnce() {
        var tx=regular(DiscountKind.SALE_PROMOTION);var obs=tx.getObservations();
        var storeCoupon=new CouponApplied("Store coupon",new BigDecimal("2.00"),List.of(tx.getPurchaseItems().get(0).getLineItemId()));
        tx.addAppliedCoupon(storeCoupon);var kinds=new HashMap<>(obs.discountKinds());kinds.put(storeCoupon.getApplicationId(),DiscountKind.STORE_COUPON);
        var shared=new CouponApplied("Shared sale",new BigDecimal("1.00"),List.of(tx.getPurchaseItems().get(1).getLineItemId(),tx.getPurchaseItems().get(2).getLineItemId()));
        tx.addAppliedCoupon(shared);kinds.put(shared.getApplicationId(),DiscountKind.SALE_PROMOTION);
        tx.setObservations(new ReceiptObservations(obs.purchaseDate(),null,List.of(),obs.unidentifiedRR(),null,List.of(),kinds));
        assertEquals(new BigDecimal("34.96"),QualifyingSpend.calculate(tx,tx.getPurchaseItems().stream().map(PurchaseItem::getLineItemId).toList()).amount());
        assertNull(QualifyingSpend.calculate(tx,List.of(tx.getPurchaseItems().get(1).getLineItemId())).amount());
    }
    @Test void newItemEntrySplitsSaleAndStoreCouponWithoutSubtractingManufacturerCouponFromSpend() {
        var repo=new InMemoryTransactionRepository();
        var script=String.join("\n","NEW","Split","","10/4/2026","Store","AJ","","Product","10","1",
                "3","3","2","1","1","","n","n","0","0","0","n","6","n","n","n","n","SAVE", "9")+"\n";
        var output=run(repo,script);var receipts=repo.findPossibleDuplicates(new ReceiptMeta("Store","Split",null,null));
        assertEquals(1,receipts.size(),output);var tx=receipts.get(0).transaction();
        assertEquals(new BigDecimal("8"),QualifyingSpend.calculate(tx,List.of(tx.getPurchaseItems().get(0).getLineItemId())).amount());
        assertEquals(new BigDecimal("6"),new TransactionCalculator().calculateExpectedCheckoutAmount(tx));
        assertEquals(Set.of(DiscountKind.SALE_PROMOTION,DiscountKind.STORE_COUPON,DiscountKind.MANUFACTURER),new HashSet<>(tx.getObservations().discountKinds().values()));
    }
}
