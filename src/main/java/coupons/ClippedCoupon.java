package coupons;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** A provider coupon observed as clipped, with its lifecycle and redemption identity. */
public record ClippedCoupon(UUID couponId, String provider, String externalId,
        Coupon coupon, CouponStatus status, LocalDate clippedOn,
        LocalDate redeemedOn, UUID redeemedTransactionId, long revision) implements java.io.Serializable {
    public ClippedCoupon {
        Objects.requireNonNull(couponId, "Coupon ID cannot be null.");
        requireText(provider, "Provider");
        if (externalId != null && externalId.isBlank()) throw new IllegalArgumentException("External ID must be null or nonblank.");
        Objects.requireNonNull(coupon, "Coupon cannot be null.");
        Objects.requireNonNull(status, "Coupon status cannot be null.");
        if (clippedOn == null && status != CouponStatus.AVAILABLE) throw new IllegalArgumentException("Non-available coupons require a clipped date.");
        if (redeemedTransactionId != null && status != CouponStatus.APPLIED) throw new IllegalArgumentException("A redeemed transaction requires APPLIED status.");
        if (status == CouponStatus.APPLIED && redeemedTransactionId == null) throw new IllegalArgumentException("APPLIED coupons require a transaction.");
        if (redeemedOn != null && redeemedTransactionId == null) throw new IllegalArgumentException("Redemption date requires a transaction.");
        if (clippedOn != null && redeemedOn != null && redeemedOn.isBefore(clippedOn)) throw new IllegalArgumentException("Redemption cannot precede clipping.");
        if (revision < 1) throw new IllegalArgumentException("Coupon revision must be at least one.");
    }
    public ClippedCoupon(UUID id, String provider, String externalId, Coupon coupon, CouponStatus status, LocalDate clippedOn, long revision) {
        this(id, provider, externalId, coupon, status, clippedOn, null, null, revision);
    }
    private static void requireText(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null.");
        if (value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank.");
    }
}
