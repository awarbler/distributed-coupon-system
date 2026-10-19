package coupons;

import coupons.ReceiptObservations.*;
import coupons.repository.*;
import java.io.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompactReceiptRewardTotalsTest {
    private BigDecimal n(String s) { return new BigDecimal(s); }
    private Earning reward(CouponTransaction tx,String offer,PromotionType type,String required,String amount,List<UUID> ids) {
        return new Earning(UUID.randomUUID(),EarningKind.REGISTER_REWARD,"Walgreens",offer,ids.size()==1?Scope.ITEM:Scope.MULTI_ITEM,ids,null,
                new RebateBenefit(BenefitKind.STORE_COUPON,n(amount),Currency.getInstance("USD"),"Walgreens"),RebateStatus.PAID,null,null,null,List.of(),null,
                type==null?null:new OfferRequirement(type,n(required),null,type==PromotionType.SPEND?SpendBasis.SALE_BEFORE_COUPONS:SpendBasis.UNKNOWN),null);
    }
    private String report(CouponTransaction tx) {
        var out=new ByteArrayOutputStream(); ReceiptReport.print(new ReceiptRecord(UUID.randomUUID(),ReceiptStatus.ACTIVE,1,tx),new PrintStream(out)); return out.toString();
    }
    @Test void shortLabelsUseRecordedRequirementsAndKeepUuidAssociations() {
        var tx=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,BigDecimal.ZERO));
        tx.addPurchaseItem(new PurchaseItem("CHARMIN SOFT US 208SH 4S",null,null,n("7.49"),4));
        tx.addPurchaseItem(new PurchaseItem("H&S SMOOTH SILKY SHAM",null,null,n("8.49"),1));
        tx.addPurchaseItem(new PurchaseItem("H&S SMOOTH SILKY COND",null,null,n("8.49"),1));
        var ids=tx.getPurchaseItems().stream().map(PurchaseItem::getLineItemId).toList();
        var charmin=reward(tx,"Buy 4 get 5rr",PromotionType.BUY,"4","5",List.of(ids.get(0)));
        var hs=reward(tx,"Buy 2 get 4rr",PromotionType.BUY,"2","4",ids.subList(1,3));
        var pg=reward(tx,"Spend 35 in pg products",PromotionType.SPEND,"35","10",ids);
        assertEquals("CHARMIN — buy 4, get $5 RR",RegisterRewardLabel.offer(tx,charmin));
        assertEquals("H&S — buy 2, get $4 RR",RegisterRewardLabel.offer(tx,hs));
        assertEquals("P&G — spend $35, get $10 RR",RegisterRewardLabel.offer(tx,pg));
        assertEquals("2, 3",RegisterRewardLabel.lines(tx,hs));
        assertEquals("Receipt lines 2, 3",RegisterRewardLabel.products(tx,hs));
        assertEquals("Lines 1,2,3 — P&G spend $35, get $10 RR",RegisterRewardLabel.couponSummary(tx,pg));
        var noTerms=reward(tx,null,null,null,"5",List.of(ids.get(0)));
        assertEquals("RR — receipt lines 1",RegisterRewardLabel.offer(tx,noTerms));
        assertEquals(ids,pg.lineItemIds());
        tx.setObservations(new ReceiptObservations(null,null,List.of(charmin,hs,pg),List.of(),null,List.of(),Map.of()));
        var output=report(tx); var rewards=output.substring(output.indexOf("REGISTER REWARDS\n"));
        assertFalse(rewards.contains("CHARMIN SOFT US"),rewards);
        assertTrue(rewards.contains("Receipt lines"),rewards);
        assertTrue(output.contains("Line 1: CHARMIN SOFT US"),output);
    }
    @Test void secondReceiptUsesTheUnroundedSpreadsheetPointFormula() {
        var tx=new CouponTransaction(new ReceiptTotals(n("1.73"),n("26.69")));
        tx.addPurchaseItem(new PurchaseItem("Charmin",null,n("7.49"),n("7.49"),4));
        tx.addPurchaseItem(new PurchaseItem("H&S shampoo",null,n("8.49"),n("8.49"),1));
        tx.addPurchaseItem(new PurchaseItem("H&S conditioner",null,n("8.49"),n("8.49"),1));
        var ids=tx.getPurchaseItems().stream().map(PurchaseItem::getLineItemId).toList();
        var sale=new CouponApplied("Sale",n("10.98"),ids); var mq=new CouponApplied("MQ",n("4"),ids);
        tx.addAppliedCoupon(sale);tx.addAppliedCoupon(mq);tx.addPayment(new Payment(PaymentType.CASH,n("26.69")));
        var earnings=new ArrayList<Earning>();
        earnings.add(reward(tx,"P&G spend 35",PromotionType.SPEND,"35","19",ids));
        earnings.add(new Earning(UUID.randomUUID(),EarningKind.WALGREENS_CASH,"Walgreens",null,Scope.RECEIPT,List.of(),null,new RebateBenefit(BenefitKind.STORE_REWARD,n("18.50"),Currency.getInstance("USD"),"Walgreens"),RebateStatus.CREDITED,null,null,null,List.of()));
        for(String provider:List.of("Fetch","P&G Brand")) {
            var points=provider.equals("Fetch")?n("25"):n("250");
            var dollars=provider.equals("Fetch")?n("0.025"):n("250").divide(n("150"),MathContext.DECIMAL128);
            earnings.add(new Earning(UUID.randomUUID(),EarningKind.THIRD_PARTY_REBATE,provider,null,Scope.RECEIPT,List.of(),null,
                    new RebateBenefit(BenefitKind.POINTS,points,null,provider),RebateStatus.CREDITED,null,null,null,List.of(),null,null,new RecordedValuation(dollars,Currency.getInstance("USD"),"Spreadsheet formula")));
        }
        tx.setObservations(new ReceiptObservations(LocalDate.of(2026,10,4),null,earnings,List.of(new UnidentifiedRR(UUID.randomUUID(),n("7"),"Neutrogena")),null,List.of(),Map.of(sale.getApplicationId(),DiscountKind.SALE_PROMOTION,mq.getApplicationId(),DiscountKind.MANUFACTURER)));
        var output=report(tx);
        assertTrue(output.matches("(?s).*Regular total including tax +\\| +\\$48.67 .*"),output);
        assertTrue(output.matches("(?s).*New Walgreens Cash earned +\\| +\\$18.50 .*"),output);
        assertTrue(output.matches("(?s).*Retailer total due +\\| +\\$26.69 .*"),output);
        assertTrue(output.matches("(?s).*PAID AFTER EARNED BENEFITS +\\| +-\\$12.50 .*"),output);
        assertTrue(output.matches("(?s).*TOTAL SAVED +\\| +\\$61.17 .*"),output);
    }
    @Test void unknownRegularPriceIsNotReplacedWithASalePriceAndKnownZeroTotalsAreHidden() {
        var tx=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,n("5")));
        tx.addPurchaseItem(new PurchaseItem("Sale item",null,null,n("5"),1));
        var output=report(tx); var totals=output.substring(output.indexOf("RECEIPT TOTALS"));
        assertTrue(totals.matches("(?s).*Regular merchandise +\\| +unknown .*"),totals);
        assertFalse(totals.contains("Paid card"),totals);
        assertFalse(totals.contains("Gift-card rebates earned"),totals);
        assertFalse(totals.contains("MQ applications"),totals);
        assertFalse(totals.matches("(?s).*\\| +\\$0.00 +\\|.*"),totals);
        assertTrue(totals.contains("unknown — tender not recorded"),totals);
    }
}
