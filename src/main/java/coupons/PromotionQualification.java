package coupons;

import coupons.ReceiptObservations.*;
import java.math.BigDecimal;
import java.util.*;

/** Auditable qualification arithmetic only: never issues or credits a reward. */
public record PromotionQualification(String status, long qualifyingQuantity, BigDecimal qualifyingSpend,
        String reason) {
    public static PromotionQualification evaluate(Earning earning,CouponTransaction tx) {
        var requirement=earning.requirement();if(requirement==null)return new PromotionQualification("NOT_RECORDED",0,null,"No qualification recorded");
        if(earning.lineItemIds().isEmpty())return new PromotionQualification("CANNOT_VERIFY",0,null,"Select qualifying receipt lines; whole receipt does not assume eligibility");
        var ids=new HashSet<>(earning.lineItemIds());long quantity=0;boolean partial=false;
        BigDecimal subtotal=BigDecimal.ZERO;
        for(var id:ids){var line=tx.getPurchaseItems().stream().filter(l->l.getLineItemId().equals(id)).findFirst().orElseThrow();
            int q=earning.details()==null?line.getQuantity():earning.details().qualifyingQuantities().getOrDefault(id,line.getQuantity());
            if(q<1||q>line.getQuantity())throw new IllegalArgumentException("Qualifying quantity exceeds purchased quantity.");
            partial|=q!=line.getQuantity();quantity=Math.addExact(quantity,q);subtotal=subtotal.add(line.getSaleUnitPrice().multiply(BigDecimal.valueOf(q)));
        }
        if(requirement.type()==PromotionType.BUY)return new PromotionQualification(BigDecimal.valueOf(quantity).compareTo(requirement.amount())>=0?"MET":"NOT_MET",quantity,subtotal,"Recorded qualifying purchased quantities");
        if(requirement.spendBasis()==SpendBasis.UNKNOWN)return new PromotionQualification("CANNOT_VERIFY",quantity,null,"Record whether qualifying spend is before or after coupons");
        var before=QualifyingSpend.calculate(tx,earning.lineItemIds());if(before.amount()==null)return new PromotionQualification("CANNOT_VERIFY",quantity,null,before.problem());
        if(partial && tx.getAppliedCoupons().stream().anyMatch(c->(tx.getObservations().discountKinds().get(c.getApplicationId())==DiscountKind.SALE_PROMOTION||requirement.spendBasis()==SpendBasis.SALE_AFTER_COUPONS)&&(c.isWholeReceipt()||!Collections.disjoint(ids,c.getLineItemIds()))))return new PromotionQualification("CANNOT_VERIFY",quantity,null,"Partial quantities require explicit deduction allocation; none is inferred");
        BigDecimal spend=partial?subtotal:before.amount();
        if(requirement.spendBasis()==SpendBasis.SALE_AFTER_COUPONS)for(var coupon:tx.getAppliedCoupons()) {
            if(!coupon.isWholeReceipt()&&coupon.getLineItemIds().isEmpty())return new PromotionQualification("CANNOT_VERIFY",quantity,null,"Coupon product allocation is unknown");
            if(!coupon.isWholeReceipt()&&Collections.disjoint(ids,coupon.getLineItemIds()))continue;
            var kind=tx.getObservations().discountKinds().get(coupon.getApplicationId());if(kind==DiscountKind.SALE_PROMOTION)continue;
            if(coupon.isWholeReceipt() && ids.size()!=tx.getPurchaseItems().size() || !coupon.isWholeReceipt()&&!ids.containsAll(coupon.getLineItemIds()))return new PromotionQualification("CANNOT_VERIFY",quantity,null,"Coupon crosses eligible and ineligible products; allocation needs review");
            spend=spend.subtract(coupon.getAppliedAmount());
        }
        if(spend.signum()<0)return new PromotionQualification("CANNOT_VERIFY",quantity,null,"Deductions exceed qualifying merchandise");
        return new PromotionQualification(spend.compareTo(requirement.amount())>=0?"MET":"NOT_MET",quantity,spend,requirement.spendBasis()==SpendBasis.SALE_BEFORE_COUPONS?"Sale merchandise before coupons":"Sale merchandise after recorded coupons");
    }
}
