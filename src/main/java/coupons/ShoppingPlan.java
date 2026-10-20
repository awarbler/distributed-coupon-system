package coupons;

import java.time.LocalDate;
import java.util.List;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;

/** A dated, revisioned shopping plan. Planned purchases are stored separately. */
public record ShoppingPlan(UUID planId, String name, String retailer,
        LocalDate shoppingDate, List<UUID> plannedPurchaseIds, long revision) implements java.io.Serializable {
    public ShoppingPlan {
        Objects.requireNonNull(planId, "Plan ID cannot be null.");
        requireText(name, "Plan name");
        if (retailer != null && retailer.isBlank()) throw new IllegalArgumentException("Retailer must be null or nonblank.");
        Objects.requireNonNull(plannedPurchaseIds, "Planned purchase IDs cannot be null.");
        plannedPurchaseIds = List.copyOf(plannedPurchaseIds);
        if (plannedPurchaseIds.stream().anyMatch(Objects::isNull)) throw new NullPointerException("Planned purchase ID cannot be null.");
        if (new HashSet<>(plannedPurchaseIds).size() != plannedPurchaseIds.size()) throw new IllegalArgumentException("Planned purchase IDs cannot repeat.");
        if (revision < 1) throw new IllegalArgumentException("Plan revision must be at least one.");
    }
    private static void requireText(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null.");
        if (value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank.");
    }
}
