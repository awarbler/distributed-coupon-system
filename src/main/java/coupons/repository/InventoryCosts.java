package coupons.repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Purchase cost excludes tax, funding and earned rewards. Shared deductions are prorated by sale value. */
public final class InventoryCosts {
    private InventoryCosts() {}

    public static Map<UUID, BigDecimal> calculate(ReceiptSnapshot receipt) {
        var base = new LinkedHashMap<UUID, BigDecimal>();
        for (var line : receipt.purchaseItems())
            base.put(line.lineItemId(), line.saleUnitPrice().multiply(BigDecimal.valueOf(line.quantity())));
        var net = new LinkedHashMap<>(base);
        for (var discount : receipt.appliedCoupons()) {
            var ids = discount.isWholeReceipt() ? new ArrayList<>(base.keySet()) : discount.getLineItemIds();
            if (ids.isEmpty() || !base.keySet().containsAll(ids)) return Map.of();
            var weights = new LinkedHashMap<UUID, BigDecimal>();
            for (var id : ids) {
                var line = receipt.purchaseItems().stream().filter(l -> l.lineItemId().equals(id)).findFirst().orElseThrow();
                int quantity = discount.getQualifyingQuantities().getOrDefault(id, line.quantity());
                weights.put(id, line.saleUnitPrice().multiply(BigDecimal.valueOf(quantity)));
            }
            BigDecimal total = weights.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.signum() == 0 && discount.getAppliedAmount().signum() != 0) return Map.of();
            BigDecimal remaining = discount.getAppliedAmount().setScale(2, RoundingMode.HALF_UP);
            // Largest remainder allocation conserves every cent, including very small shared coupons.
            var shares = new LinkedHashMap<UUID, BigDecimal>();
            var fractions = new HashMap<UUID, BigDecimal>();
            for (var id : ids) {
                BigDecimal exact = total.signum() == 0 ? BigDecimal.ZERO : discount.getAppliedAmount().multiply(weights.get(id)).divide(total, 12, RoundingMode.HALF_UP);
                BigDecimal share = exact.setScale(2, RoundingMode.DOWN);
                shares.put(id, share); fractions.put(id, exact.subtract(share)); remaining = remaining.subtract(share);
            }
            var ordered = ids.stream().sorted(Comparator.comparing((UUID id) -> fractions.get(id)).reversed().thenComparing(UUID::toString)).toList();
            for (int i = 0; remaining.signum() > 0; i++) {
                UUID id = ordered.get(i % ordered.size());
                shares.put(id, shares.get(id).add(new BigDecimal("0.01"))); remaining = remaining.subtract(new BigDecimal("0.01"));
            }
            shares.forEach((id, share) -> net.put(id, net.get(id).subtract(share)));
        }
        if (net.values().stream().anyMatch(value -> value.signum() < 0)) return Map.of();
        return Collections.unmodifiableMap(net);
    }
}
