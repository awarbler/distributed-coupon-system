package coupons.api;

import coupons.*;
import coupons.ReceiptObservations.Earning;
import coupons.ReceiptObservations.EarningKind;
import java.math.BigDecimal;

/** Narrow receipt status change; never a payout, redemption, amount correction, or new earning. */
final class ReceiptEarningStatusUpdate {
    private ReceiptEarningStatusUpdate() {}
    /** Correct an existing earning in place; its ID and receipt revision history remain intact. */
    static Earning correctAmount(Earning old,String amount,String unit) {
        var previous=old.confirmed()!=null?old.confirmed():old.expected();
        if(previous==null)throw new IllegalArgumentException("No original reward amount is recorded.");
        String original=previous.kind()==BenefitKind.POINTS?"points":previous.currency().getCurrencyCode();
        if(!original.equals(unit)||amount==null||!amount.matches("(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)")||new BigDecimal(amount).signum()<=0)
            throw new IllegalArgumentException("Enter a reward amount greater than zero in the original unit.");
        var value=new RebateBenefit(previous.kind(),new BigDecimal(amount),previous.currency(),previous.program());
        var valuation=old.valuation();
        String provider=ProviderBalanceSummary.accountName(old.provider());
        if(previous.kind()==BenefitKind.POINTS) {
            if(provider.equals("Fetch"))valuation=new coupons.ReceiptObservations.RecordedValuation(value.amount().movePointLeft(3),java.util.Currency.getInstance("USD"),"Worksheet Fetch rate: 1000 points = 1 USD");
            else if(provider.equals("P&G Brand"))valuation=new coupons.ReceiptObservations.RecordedValuation(value.amount().divide(new BigDecimal("150"),java.math.MathContext.DECIMAL128),java.util.Currency.getInstance("USD"),"Worksheet P&G rate: 150 points = 1 USD");
            else if(valuation!=null&&previous.amount().signum()==0)valuation=null;
            else if(valuation!=null)valuation=new coupons.ReceiptObservations.RecordedValuation(valuation.amount().multiply(value.amount()).divide(previous.amount(),java.math.MathContext.DECIMAL128),valuation.currency(),valuation.basis());
        }
        return new Earning(old.earningId(),old.kind(),old.provider(),old.offerReference(),old.scope(),old.lineItemIds(),old.confirmed()==null?value:old.expected(),old.confirmed()==null?null:value,old.status(),old.expirationDate(),old.instrumentId(),old.claimId(),old.evidenceIds(),old.recordedLifecycle(),old.requirement(),valuation,old.details());
    }
    static Earning source(Earning old,java.util.UUID receipt,java.util.UUID earning){var d=old.details();var next=new EarningDetails(d==null?java.util.Map.of():d.qualifyingQuantities(),d==null?null:d.payoutDestination(),d==null?null:d.submittedOn(),d==null?null:d.submissionReference(),d==null?null:d.confirmationReference(),d==null?null:d.submissionStatus(),d==null?null:d.followUpOn(),d==null?null:d.followUpNote(),receipt,earning);return new Earning(old.earningId(),old.kind(),old.provider(),old.offerReference(),old.scope(),old.lineItemIds(),old.expected(),old.confirmed(),old.status(),old.expirationDate(),old.instrumentId(),old.claimId(),old.evidenceIds(),old.recordedLifecycle(),old.requirement(),old.valuation(),next);}
    static Earning followUp(Earning old,String status,String submitted,String reference,String due,String note) {
        var d=old.details();
        var details=new EarningDetails(d==null?java.util.Map.of():d.qualifyingQuantities(),d==null?null:d.payoutDestination(),submitted==null?null:java.time.LocalDate.parse(submitted),reference,d==null?null:d.confirmationReference(),status,due==null?null:java.time.LocalDate.parse(due),note,d==null?null:d.originalReceiptId(),d==null?null:d.originalEarningId());
        return new Earning(old.earningId(),old.kind(),old.provider(),old.offerReference(),old.scope(),old.lineItemIds(),old.expected(),old.confirmed(),old.status(),old.expirationDate(),old.instrumentId(),old.claimId(),old.evidenceIds(),old.recordedLifecycle(),old.requirement(),old.valuation(),details);
    }
    static Earning remove(Earning old) {return new Earning(old.earningId(),old.kind(),old.provider(),old.offerReference(),old.scope(),old.lineItemIds(),old.expected(),old.confirmed(),RebateStatus.REJECTED,old.expirationDate(),old.instrumentId(),old.claimId(),old.evidenceIds(),java.util.Objects.toString(old.recordedLifecycle(),"")+"\nOwner deleted reward; original values retained in history.",old.requirement(),old.valuation(),old.details());}
    static Earning apply(Earning old,RebateStatus status,String amount,String unit,boolean confirmation,String rejectionReason) {
        boolean rr=old.kind()==EarningKind.REGISTER_REWARD;
        if(status!=RebateStatus.EXPECTED&&status!=RebateStatus.REJECTED&&status!=(rr?RebateStatus.PAID:RebateStatus.CREDITED))
            throw new IllegalArgumentException("Choose Expected, "+(rr?"Printed at register":"Added to program account")+", or Rejected. Legacy statuses remain stored but cannot be assigned here.");
        if(!rr&&old.status()==RebateStatus.PAID)throw new IllegalArgumentException("Existing received/payout records require a separate reconciliation workflow.");
        var confirmed=old.confirmed();var recorded=confirmed==null?old.expected():confirmed;
        if(status==RebateStatus.CREDITED||status==RebateStatus.PAID) {
            if(!confirmation||amount==null||unit==null)throw new IllegalArgumentException("Explicit confirmation of amount and original unit is required.");
            String originalUnit=recorded.kind()==BenefitKind.POINTS?"points":recorded.currency().getCurrencyCode();
            if(!originalUnit.equals(unit)||!amount.matches("(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)")||new BigDecimal(amount).compareTo(recorded.amount())!=0)
                throw new IllegalArgumentException("Confirm the recorded amount and unit exactly. Amount corrections are outside this status-only workflow.");
            confirmed=recorded; // preserve original scale, point quantity, form and currency
        } else if(amount!=null||unit!=null||confirmation)throw new IllegalArgumentException("Amount confirmation is only applicable to received rewards.");
        String lifecycle=old.recordedLifecycle();
        if(rejectionReason!=null&&!rejectionReason.isBlank()) {
            if(status!=RebateStatus.REJECTED||rejectionReason.length()>1000)throw new IllegalArgumentException("A rejection reason is optional, limited to 1000 characters, and only applies to Rejected.");
            lifecycle=(lifecycle==null?"":lifecycle+"\n")+"Rejection reason: "+rejectionReason.trim();
        }
        return new Earning(old.earningId(),old.kind(),old.provider(),old.offerReference(),old.scope(),old.lineItemIds(),old.expected(),confirmed,status,
                old.expirationDate(),old.instrumentId(),old.claimId(),old.evidenceIds(),lifecycle,old.requirement(),old.valuation(),old.details());
    }
}
