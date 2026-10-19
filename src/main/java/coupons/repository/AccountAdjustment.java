package coupons.repository;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Explicit non-receipt movement, never a new receipt earning; signed values may be independently unknown. */
public record AccountAdjustment(UUID eventId, LocalDate date, String provider, BigDecimal unitChange,
        BigDecimal usdChange, String reason, String recordedBy, Instant recordedAt) implements Serializable {
    public AccountAdjustment {
        Objects.requireNonNull(eventId); Objects.requireNonNull(date); Objects.requireNonNull(provider);
        Objects.requireNonNull(reason); Objects.requireNonNull(recordedBy); Objects.requireNonNull(recordedAt);
        if (provider.isBlank() || reason.isBlank() || recordedBy.isBlank() || unitChange == null && usdChange == null)
            throw new IllegalArgumentException("An adjustment needs an account, amount, reason, and entrant.");
    }
}
