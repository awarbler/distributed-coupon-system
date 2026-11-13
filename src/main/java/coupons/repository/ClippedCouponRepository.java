package coupons.repository;

import coupons.promotions.ClippedCoupon;
import java.time.LocalDate;
import java.util.*;

public interface ClippedCouponRepository {
    void save(ClippedCoupon coupon, String recordedBy);
    Optional<ClippedCoupon> findById(UUID couponId);
    List<ClippedCoupon> findByProvider(String provider);
    ClippedCoupon update(ClippedCoupon coupon, long expectedRevision, String changedBy, String reason);
    /** Reviewed snapshot changes must commit together, preserving coupon identities and revision checks. */
    default void importReviewed(List<ClippedCoupon> coupons, Map<UUID,Long> expected, String actor) {
        throw new UnsupportedOperationException("Reviewed import is not supported by this repository.");
    }
    ClippedCoupon redeem(UUID couponId, UUID transactionId, LocalDate redeemedOn,
            long expectedRevision, String actor);
}
