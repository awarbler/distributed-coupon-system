package coupons.api;

import coupons.promotions.BenefitKind;
import coupons.rewards.EarningDetails;
import coupons.promotions.PromotionType;
import coupons.rewards.ProviderBalanceSummary;
import coupons.promotions.RebateBenefit;
import coupons.promotions.RebateStatus;
import coupons.rewards.Reward;
import coupons.receipts.ReceiptObservations.*;
import java.time.LocalDate;
import java.util.*;
import static coupons.api.Json.*;
import static coupons.api.ReceiptInput.*;

/** Shared new-earning input during creation and post-save additions. No payout event is generated. */
final class EarningInput {
    static Earning read(Map<String,Object> e) {
            known(e,"originalReceiptId","originalEarningId","requirement","qualifyingQuantities","payoutDestination","submittedOn","submissionReference","confirmationReference","evidenceIds","earningId","kind","provider","offer","form","amount","status","confirmedAmount","unit","confirmAmountAndUnit","valuation","valuationBasis","expirationDate","lineItemIds","rejectionReason");
            var kind=EarningKind.valueOf(required(e,"kind"));var form=BenefitKind.valueOf(required(e,"form"));
            if(kind==EarningKind.REGISTER_REWARD&&form!=BenefitKind.STORE_COUPON||kind==EarningKind.WALGREENS_CASH&&form!=BenefitKind.STORE_REWARD)throw new IllegalArgumentException("Reward form must match its program.");
            String provider=kind==EarningKind.THIRD_PARTY_REBATE?required(e,"provider"):"Walgreens";
            var lines=ids(e,"lineItemIds");var scope=lines.isEmpty()?Scope.RECEIPT:lines.size()==1?Scope.ITEM:Scope.MULTI_ITEM;
            var amount=decimal(e,"amount",false);var benefit=new RebateBenefit(form,amount,form==BenefitKind.POINTS?null:Currency.getInstance("USD"),form==BenefitKind.CASH?null:provider);
            var val=decimal(e,"valuation",true);
            boolean automaticFetch = form==BenefitKind.POINTS && provider.trim().equalsIgnoreCase("Fetch") && val==null;
            boolean automaticPG = form==BenefitKind.POINTS && ProviderBalanceSummary.accountName(provider).equals("P&G Brand");
            if(automaticFetch) val=amount.movePointLeft(3);
            if(automaticPG) val=amount.divide(new java.math.BigDecimal("150"), java.math.MathContext.DECIMAL128);if(val!=null&&form!=BenefitKind.POINTS)throw new IllegalArgumentException("Saved USD valuation applies only to points.");
            String expiry=optional(e,"expirationDate");var expected=new Earning(uuid(e,"earningId"),kind,provider,optional(e,"offer"),scope,lines,benefit,null,RebateStatus.EXPECTED,expiry==null?null:LocalDate.parse(expiry),null,null,ids(e,"evidenceIds"),null,requirement(e),val==null?null:new RecordedValuation(val,Currency.getInstance("USD"),automaticFetch?"Worksheet Fetch rate: 1000 points = 1 USD":automaticPG?"Worksheet P&G rate: 150 points = 1 USD":required(e,"valuationBasis")),details(e));
            try {
                return ReceiptEarningStatusUpdate.apply(expected,RebateStatus.valueOf(required(e,"status")),optional(e,"confirmedAmount"),optional(e,"unit"),flag(e,"confirmAmountAndUnit"),optional(e,"rejectionReason"));
            } catch (IllegalArgumentException invalid) {
                if (!Set.of("CREDITED", "PAID").contains(required(e,"status"))) throw invalid;
                throw new IllegalArgumentException("Reward " + Objects.toString(optional(e,"offer"), provider) + ": confirm the received amount as " + amount.toPlainString() + " " + (form==BenefitKind.POINTS ? "points" : "USD") + ", select that unit and check the received confirmation. If the received amount differs, correct Reward amount first. " + (RebateStatus.valueOf(required(e,"status")) == RebateStatus.REJECTED ? invalid.getMessage() : ""));
            }
    }
    private static OfferRequirement requirement(Map<String,Object> e){
        if(e.get("requirement")==null)return null;
        if(!(e.get("requirement") instanceof Map<?,?>))throw new IllegalArgumentException("Requirement must be an object.");
        @SuppressWarnings("unchecked") var r=(Map<String,Object>)e.get("requirement");
        known(r,"type","amount","category","spendBasis");
        String type = optional(r,"type");
        String basis = optional(r,"spendBasis");
        return new OfferRequirement(type == null ? null : PromotionType.valueOf(type),
                decimal(r,"amount",type == null),optional(r,"category"),
                basis == null ? SpendBasis.UNKNOWN : SpendBasis.valueOf(basis));
    }
    private static EarningDetails details(Map<String,Object> e){
        var quantities=quantities(e);
        var day=optional(e,"submittedOn");return new EarningDetails(quantities,optional(e,"payoutDestination"),day==null?null:LocalDate.parse(day),optional(e,"submissionReference"),optional(e,"confirmationReference"),null,null,null,optional(e,"originalReceiptId")==null?null:UUID.fromString(required(e,"originalReceiptId")),optional(e,"originalEarningId")==null?null:UUID.fromString(required(e,"originalEarningId")));
    }
}
