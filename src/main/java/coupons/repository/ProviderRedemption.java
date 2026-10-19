package coupons.repository;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** A transfer of already earned value. Status history is retained by the repository. */
public record ProviderRedemption(UUID redemptionId,String provider,LocalDate requestedDate,LocalTime requestedTime,
        BigDecimal originalUnitsUsed,BigDecimal sourceUsdValue,Destination destination,String destinationAccount,
        BigDecimal destinationUsdValue,Status status,LocalDate completedDate,LocalTime completedTime,
        List<UUID> sourceEarningIds,String reference,String notes) implements Serializable {
    public enum Destination { CASH, GIFT_CARD, PURCHASE, OTHER }
    public enum Status { REQUESTED, RECEIVED, CANCELLED }
    public ProviderRedemption {
        Objects.requireNonNull(redemptionId); Objects.requireNonNull(provider); Objects.requireNonNull(requestedDate);
        Objects.requireNonNull(sourceUsdValue); Objects.requireNonNull(destination); Objects.requireNonNull(status);
        Objects.requireNonNull(destinationUsdValue); Objects.requireNonNull(reference);
        sourceEarningIds=List.copyOf(sourceEarningIds);
        if(provider.isBlank() || reference.isBlank() || destinationAccount==null || destinationAccount.isBlank()
                || sourceUsdValue.signum()<=0 || destinationUsdValue.signum()<0
                || originalUnitsUsed!=null && originalUnitsUsed.signum()<=0 || provider.equalsIgnoreCase(destinationAccount)
                || new HashSet<>(sourceEarningIds).size()!=sourceEarningIds.size()) throw new IllegalArgumentException("Provide distinct source/destination, positive source value and a reference.");
        if(status==Status.REQUESTED && completedDate!=null || status!=Status.REQUESTED && completedDate==null
                || completedDate!=null && completedDate.isBefore(requestedDate)
                || completedDate!=null && completedDate.equals(requestedDate) && requestedTime!=null && completedTime!=null && completedTime.isBefore(requestedTime))
            throw new IllegalArgumentException("Completion/cancellation must be dated on or after request.");
        if(status==Status.REQUESTED && completedTime!=null) throw new IllegalArgumentException("Requested transfers have no completion time.");
    }
    public ProviderRedemption complete(Status next,LocalDate date,LocalTime time) {
        return new ProviderRedemption(redemptionId,provider,requestedDate,requestedTime,originalUnitsUsed,sourceUsdValue,destination,destinationAccount,destinationUsdValue,next,date,time,sourceEarningIds,reference,notes);
    }
    public boolean sameTerms(ProviderRedemption other) {
        return complete(other.status(),other.completedDate(),other.completedTime()).equals(other);
    }
}
