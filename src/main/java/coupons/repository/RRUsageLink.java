package coupons.repository;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Confirmed association to an already recorded RR deduction; never another financial movement. */
public record RRUsageLink(UUID sourceId, boolean receiptEarning, UUID usageEventId, UUID spentReceiptId,
        BigDecimal amount, String reason, String confirmedBy, Instant confirmedAt, List<RRUsageLink> previousMatches) implements Serializable {
    public RRUsageLink(UUID source,boolean receiptEarning,UUID use,UUID receipt,BigDecimal amount,String reason,String actor,Instant at){this(source,receiptEarning,use,receipt,amount,reason,actor,at,List.of());}
    public RRUsageLink {
        previousMatches=previousMatches==null?List.of():List.copyOf(previousMatches);
        Objects.requireNonNull(sourceId); Objects.requireNonNull(usageEventId); Objects.requireNonNull(spentReceiptId);
        Objects.requireNonNull(amount); Objects.requireNonNull(reason); Objects.requireNonNull(confirmedBy); Objects.requireNonNull(confirmedAt);
        if(amount.signum()<=0 || reason.isBlank() || confirmedBy.isBlank()) throw new IllegalArgumentException("Confirm amount, source reason and entrant.");
    }
}
