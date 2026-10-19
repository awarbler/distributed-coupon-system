package coupons;

import java.math.BigDecimal;
import java.util.List;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;

/** One intended purchase line, kept distinct from a completed receipt line. */
public record PlannedPurchase(UUID plannedPurchaseId, UUID planId, String productName,
        String productCode, int quantity, BigDecimal targetUnitPrice,
        UUID advertisedDealId, List<UUID> clippedCouponIds, long revision) {
    public PlannedPurchase {
        Objects.requireNonNull(plannedPurchaseId, "Planned purchase ID cannot be null.");
        Objects.requireNonNull(planId, "Plan ID cannot be null.");
        Objects.requireNonNull(productName, "Product name cannot be null.");
        if (productName.isBlank()) throw new IllegalArgumentException("Product name cannot be blank.");
        if (productCode != null && productCode.isBlank()) throw new IllegalArgumentException("Product code must be null or nonblank.");
        if (quantity < 1) throw new IllegalArgumentException("Quantity must be at least one.");
        if (targetUnitPrice != null && targetUnitPrice.signum() < 0) throw new IllegalArgumentException("Target price cannot be negative.");
        Objects.requireNonNull(clippedCouponIds, "Clipped coupon IDs cannot be null.");
        clippedCouponIds = List.copyOf(clippedCouponIds);
        if (clippedCouponIds.stream().anyMatch(Objects::isNull)) throw new NullPointerException("Clipped coupon ID cannot be null.");
        if (new HashSet<>(clippedCouponIds).size() != clippedCouponIds.size()) throw new IllegalArgumentException("Clipped coupon IDs cannot repeat.");
        if (revision < 1) throw new IllegalArgumentException("Planned purchase revision must be at least one.");
    }
}
