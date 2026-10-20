package coupons;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.*;

/** Nonfinancial claim context and exact selected quantities, preserved with each receipt revision. */
public record EarningDetails(Map<UUID,Integer> qualifyingQuantities, String payoutDestination,
        LocalDate submittedOn, String submissionReference, String confirmationReference) implements Serializable {
    public EarningDetails {
        qualifyingQuantities=qualifyingQuantities==null?Map.of():Map.copyOf(qualifyingQuantities);
        if(qualifyingQuantities.values().stream().anyMatch(q->q<1))throw new IllegalArgumentException("Qualifying quantities must be positive.");
        if(payoutDestination!=null&&!Set.of("PAYPAL","VENMO","BANK","GIFT_CARD","OTHER").contains(payoutDestination))throw new IllegalArgumentException("Unknown payout destination.");
    }
}
