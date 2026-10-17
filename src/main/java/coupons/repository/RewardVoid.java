package coupons.repository;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Audited removal from the active standalone-coupon list; the original coupon is retained. */
public record RewardVoid(UUID rewardId, String reason, String recordedBy, Instant recordedAt) implements java.io.Serializable {
    public RewardVoid {
        Objects.requireNonNull(rewardId);
        Objects.requireNonNull(recordedAt);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(recordedBy);
        if (reason.isBlank() || recordedBy.isBlank()) throw new IllegalArgumentException("Reason and confirmed-by are required.");
    }
}
