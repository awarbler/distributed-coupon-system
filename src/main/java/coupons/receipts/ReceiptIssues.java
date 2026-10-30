package coupons.receipts;

import coupons.promotions.BenefitKind;
import coupons.promotions.RebateStatus;

import coupons.repository.*;
import coupons.receipts.ReceiptObservations.*;
import java.io.PrintStream;
import java.util.*;

/** Current receipt checks, recomputed after edits. Recovery provenance is not an error. */
public final class ReceiptIssues {
    public record Issue(boolean blocking,String title,String location,String action) {}
    private ReceiptIssues() {}
    public static boolean online(ReceiptMeta metadata){return metadata!=null && metadata.purchaseChannel()==ReceiptMeta.PurchaseChannel.ONLINE;}
    public static String onlineRrProblem(ReceiptMeta metadata,CouponTransaction tx) {
        if(!online(metadata))return null;
        if(!tx.getRegisterRewardRedemptions().isEmpty() || !tx.getObservations().unidentifiedRR().isEmpty()
                || tx.getPayments().stream().anyMatch(p->p.getPaymentType()==PaymentType.REGISTER_REWARD))
            return "Online orders cannot use Register Rewards. Correct the RR use in Tax and payment.";
        if(tx.getObservations().earnings().stream().anyMatch(e->e.kind()==EarningKind.REGISTER_REWARD && e.confirmed()!=null && (e.status()==RebateStatus.APPROVED || e.status()==RebateStatus.CREDITED || e.status()==RebateStatus.PAID))
                || tx.getRewards().stream().anyMatch(r->Set.of("rr","registerreward","registerrewards").contains(Objects.toString(r.getSource(),"").toLowerCase(Locale.ROOT).replaceAll("[^a-z]",""))))
            return "Online orders cannot earn Register Rewards. Correct the entry in Rewards and rebates.";
        return null;
    }
    public static void validateOnlineRule(ReceiptMeta metadata,CouponTransaction tx) {
        String problem=onlineRrProblem(metadata,tx);if(problem!=null)throw new IllegalArgumentException(problem);
    }
    public static List<Issue> list(ReceiptRecord receipt) {
        var result=new ArrayList<Issue>();var tx=receipt.transaction();
        String rr=onlineRrProblem(receipt.metadata(),tx);
        if(rr!=null)result.add(new Issue(true,rr,"Order type: Online","EDIT > Tax and payment / Rewards and rebates; preserve the original evidence."));
        if(new TransactionCalculator().calculateReceiptDifference(tx).signum()!=0)
            result.add(new Issue(false,"Calculated checkout does not match printed total","Tax and payment","EDIT > Tax and payment; compare tax, payments, discounts and printed total."));
        for(int i=0;i<tx.getPurchaseItems().size();i++) {
            var item=tx.getPurchaseItems().get(i);
            if(item.getRegularUnitPrice()==null)result.add(new Issue(false,"Regular price missing","Product line "+(i+1)+": "+item.getProductName(),"EDIT > Products > Regular price. Savings remain partial until known."));
        }
        for(var coupon:tx.getAppliedCoupons()) {
            var kind=tx.getObservations().discountKinds().get(coupon.getApplicationId());
            if(kind==null || kind==DiscountKind.STORE || kind==DiscountKind.OTHER)
                result.add(new Issue(false,"Discount type needs classification",coupon.getPrintedLabel()+" | $"+coupon.getAppliedAmount(),"EDIT > Discounts and store promotions > Select > Discount type."));
            if(coupon.getLineItemIds().isEmpty() && !coupon.isWholeReceipt())
                result.add(new Issue(false,"Discount application is unknown",coupon.getPrintedLabel()+" | $"+coupon.getAppliedAmount(),"EDIT > Discounts and store promotions > Select > Products; choose 0 for Whole receipt or product numbers."));
        }
        for(var earning:tx.getObservations().earnings()) {
            if(earning.confirmed()!=null && earning.confirmed().kind()==BenefitKind.POINTS && earning.valuation()==null)
                result.add(new Issue(false,"Point dollar valuation missing",earning.provider()+" | "+earning.confirmed().amount()+" points","EDIT > Rewards and rebates > Select > Value; retain original points."));
        }
        for(var link:tx.getObservations().promotionLinks())if(link.reviewedEligibleAmount()==null)
            result.add(new Issue(false,"Online rebate eligible amount needs review","Online rebate eligibility","EDIT > Online rebate eligibility; confirm the selected products and eligible amount. Receipt can still be saved."));
        return List.copyOf(result);
    }
    public static void summary(ReceiptRecord receipt,PrintStream out) {
        var issues=list(receipt);long blocked=issues.stream().filter(Issue::blocking).count();
        out.println("\nRECEIPT ISSUES");
        if(issues.isEmpty())out.println("No current issues detected. Review before saving.");
        else out.println(blocked+" blocking | "+(issues.size()-blocked)+" need review — choose ISSUES for the affected fields and actions.");
        for(var issue:issues)out.println("  "+(issue.blocking()?"FIX: ":"REVIEW: ")+issue.title()+" | "+issue.location());
        if(online(receipt.metadata()))out.println("Online order: Register Rewards cannot be earned or used.");
    }
    public static void print(ReceiptRecord receipt,PrintStream out) {
        summary(receipt,out);var issues=list(receipt);
        for(int i=0;i<issues.size();i++) {
            var issue=issues.get(i);out.println("\n"+(i+1)+". "+(issue.blocking()?"FIX BEFORE SAVE: ":"REVIEW: ")+issue.title());
            out.println("   Where: "+issue.location());out.println("   Action: "+issue.action());
        }
    }
}
