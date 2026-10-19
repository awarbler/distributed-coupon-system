package coupons.repository;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Confirmed beginning-of-day observation; new observations supersede rather than add. */
public record AccountOpening(UUID observationId, LocalDate date, String provider, BigDecimal originalUnits,
        BigDecimal usdValue, boolean monthStart, String valuationBasis, String sourceNote, Instant recordedAt) implements Serializable {
    public AccountOpening {
        Objects.requireNonNull(observationId); Objects.requireNonNull(date); Objects.requireNonNull(provider);
        Objects.requireNonNull(valuationBasis); Objects.requireNonNull(sourceNote); Objects.requireNonNull(recordedAt);
        if (provider.isBlank() || sourceNote.isBlank() || originalUnits == null && usdValue == null)
            throw new IllegalArgumentException("An opening requires an account, source, and at least one known value.");
        if (monthStart && date.getDayOfMonth() != 1) throw new IllegalArgumentException("Month opening must be on the first.");
        if (usdValue != null && originalUnits != null && valuationBasis.isBlank())
            throw new IllegalArgumentException("Record the basis for the separate USD valuation.");
    }
    public BigDecimal value() { return usdValue; }
}
