package coupons.rewards;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.*;

/** Nonfinancial claim context and exact selected quantities, preserved with each receipt revision. */
public record EarningDetails(Map<UUID,Integer> qualifyingQuantities, String payoutDestination,
        LocalDate submittedOn, String submissionReference, String confirmationReference, String submissionStatus, LocalDate followUpOn, String followUpNote,UUID originalReceiptId,UUID originalEarningId,String registerRewardType,String originProduct,String originBrand,String originProgram,String originPromotion) implements Serializable {
    public EarningDetails(Map<UUID,Integer> q,String payout,LocalDate submitted,String reference,String confirmation,String status,LocalDate due,String note,UUID receipt,UUID earning,String type){this(q,payout,submitted,reference,confirmation,status,due,note,receipt,earning,type,null,null,null,null);}
    public EarningDetails(Map<UUID,Integer> q,String payout,LocalDate submitted,String reference,String confirmation,String status,LocalDate due,String note,UUID receipt,UUID earning){this(q,payout,submitted,reference,confirmation,status,due,note,receipt,earning,null);}
    public EarningDetails(Map<UUID,Integer> q,String payout,LocalDate submitted,String reference,String confirmation,String status,LocalDate due,String note){this(q,payout,submitted,reference,confirmation,status,due,note,null,null);}
    public EarningDetails(Map<UUID,Integer> quantities,String destination,LocalDate submitted,String reference,String confirmation) { this(quantities,destination,submitted,reference,confirmation,null,null,null); }
    public EarningDetails {
        if(registerRewardType!=null&&!Set.of("MANUFACTURER","STORE","UNKNOWN").contains(registerRewardType))throw new IllegalArgumentException("Choose manufacturer, store or unknown Register Reward type.");
        if((originalReceiptId==null)!=(originalEarningId==null))throw new IllegalArgumentException("Choose both the original receipt and reward.");
        if(submissionStatus!=null&&!Set.of("NOT_SUBMITTED","SUBMITTED","FOLLOW_UP","COMPLETE","NOT_REQUIRED").contains(submissionStatus))throw new IllegalArgumentException("Unknown submission status.");
        if(followUpNote!=null&&followUpNote.length()>2000)throw new IllegalArgumentException("Follow-up note is limited to 2000 characters.");
        qualifyingQuantities=qualifyingQuantities==null?Map.of():Map.copyOf(qualifyingQuantities);
        if(qualifyingQuantities.values().stream().anyMatch(q->q<1))throw new IllegalArgumentException("Qualifying quantities must be positive.");
        if(payoutDestination!=null&&!Set.of("PAYPAL","VENMO","BANK","GIFT_CARD","OTHER").contains(payoutDestination))throw new IllegalArgumentException("Unknown payout destination.");
    }
}
