package coupons.api;

import coupons.*;
import coupons.ReceiptObservations.Earning;
import coupons.ReceiptObservations.EarningKind;
import java.math.BigDecimal;

/** Narrow receipt status change; never a payout, redemption, amount correction, or new earning. */
final class ReceiptEarningStatusUpdate {
    private ReceiptEarningStatusUpdate() {}
    static Earning apply(Earning old,RebateStatus status,String amount,String unit,boolean confirmation,String rejectionReason) {
        boolean rr=old.kind()==EarningKind.REGISTER_REWARD;
        if(status!=RebateStatus.EXPECTED&&status!=RebateStatus.REJECTED&&status!=(rr?RebateStatus.PAID:RebateStatus.CREDITED))
            throw new IllegalArgumentException("Choose Expected, "+(rr?"Printed at register":"Added to program account")+", or Rejected. Legacy statuses remain stored but cannot be assigned here.");
        if(!rr&&old.status()==RebateStatus.PAID)throw new IllegalArgumentException("Existing received/payout records require a separate reconciliation workflow.");
        var confirmed=old.confirmed();var recorded=confirmed==null?old.expected():confirmed;
        if(status==RebateStatus.CREDITED||status==RebateStatus.PAID) {
            if(!confirmation||amount==null||unit==null)throw new IllegalArgumentException("Explicit confirmation of amount and original unit is required.");
            String originalUnit=recorded.kind()==BenefitKind.POINTS?"points":recorded.currency().getCurrencyCode();
            if(!originalUnit.equals(unit)||!amount.matches("[0-9]+(\\.[0-9]+)?")||new BigDecimal(amount).compareTo(recorded.amount())!=0)
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
