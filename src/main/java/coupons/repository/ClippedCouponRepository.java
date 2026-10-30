package coupons.repository;

import coupons.promotions.ClippedCoupon;
import java.time.LocalDate;
import java.util.*;

public interface ClippedCouponRepository {
    void save(ClippedCoupon coupon, String recordedBy);
    Optional<ClippedCoupon> findById(UUID couponId);
    List<ClippedCoupon> findByProvider(String provider);
    ClippedCoupon update(ClippedCoupon coupon, long expectedRevision, String changedBy, String reason);
    ClippedCoupon redeem(UUID couponId, UUID transactionId, LocalDate redeemedOn,
            long expectedRevision, String actor);
}
