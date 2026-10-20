package coupons.api;
import coupons.*;
import coupons.ReceiptObservations.*;
import java.time.LocalDate;
import java.util.*;
import static coupons.api.Json.*;
import static coupons.api.ReceiptInput.*;

/** Shared new-earning input during creation and post-save additions. No payout event is generated. */
final class EarningInput {
    static Earning read(Map<String,Object> e) {
            known(e,"requirement","qualifyingQuantities","payoutDestination","submittedOn","submissionReference","confirmationReference","evidenceIds","earningId","kind","provider","offer","form","amount","status","confirmedAmount","unit","confirmAmountAndUnit","valuation","valuationBasis","expirationDate","lineItemIds","rejectionReason");
            var kind=EarningKind.valueOf(required(e,"kind"));var form=BenefitKind.valueOf(required(e,"form"));
            if(kind==EarningKind.REGISTER_REWARD&&form!=BenefitKind.STORE_COUPON||kind==EarningKind.WALGREENS_CASH&&form!=BenefitKind.STORE_REWARD)throw new IllegalArgumentException("Reward form must match its program.");
            String provider=kind==EarningKind.THIRD_PARTY_REBATE?required(e,"provider"):"Walgreens";
            var lines=ids(e,"lineItemIds");var scope=lines.isEmpty()?Scope.RECEIPT:lines.size()==1?Scope.ITEM:Scope.MULTI_ITEM;
            var amount=decimal(e,"amount",false);var benefit=new RebateBenefit(form,amount,form==BenefitKind.POINTS?null:Currency.getInstance("USD"),form==BenefitKind.CASH?null:provider);
            var val=decimal(e,"valuation",true);if(val!=null&&form!=BenefitKind.POINTS)throw new IllegalArgumentException("Saved USD valuation applies only to points.");
            String expiry=optional(e,"expirationDate");var expected=new Earning(uuid(e,"earningId"),kind,provider,optional(e,"offer"),scope,lines,benefit,null,RebateStatus.EXPECTED,expiry==null?null:LocalDate.parse(expiry),null,null,ids(e,"evidenceIds"),null,requirement(e),val==null?null:new RecordedValuation(val,Currency.getInstance("USD"),required(e,"valuationBasis")),details(e));
            return ReceiptEarningStatusUpdate.apply(expected,RebateStatus.valueOf(required(e,"status")),optional(e,"confirmedAmount"),optional(e,"unit"),flag(e,"confirmAmountAndUnit"),optional(e,"rejectionReason"));
    }
    private static OfferRequirement requirement(Map<String,Object> e){
        if(e.get("requirement")==null)return null;
        if(!(e.get("requirement") instanceof Map<?,?>))throw new IllegalArgumentException("Requirement must be an object.");
        @SuppressWarnings("unchecked") var r=(Map<String,Object>)e.get("requirement");known(r,"type","amount","spendBasis");
        var type=PromotionType.valueOf(required(r,"type"));return new OfferRequirement(type,decimal(r,"amount",false),null,type==PromotionType.SPEND?SpendBasis.valueOf(required(r,"spendBasis")):SpendBasis.UNKNOWN);
    }
    private static EarningDetails details(Map<String,Object> e){
        var quantities=quantities(e);
        var day=optional(e,"submittedOn");return new EarningDetails(quantities,optional(e,"payoutDestination"),day==null?null:LocalDate.parse(day),optional(e,"submissionReference"),optional(e,"confirmationReference"));
    }
}
