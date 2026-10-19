package coupons;

import coupons.ReceiptObservations.DiscountKind;
import java.math.BigDecimal;
import java.util.*;

/** Selected merchandise after explicit sale reductions, before store/manufacturer coupons, rewards, and tax. */
public record QualifyingSpend(BigDecimal amount, String problem) {
    public static QualifyingSpend calculate(CouponTransaction transaction, List<UUID> lineIds) {
        var ids = new HashSet<>(lineIds);
        if (ids.isEmpty() || ids.size() != lineIds.size()) return new QualifyingSpend(null, "Select distinct eligible product lines");
        var lines = transaction.getPurchaseItems().stream().filter(line -> ids.contains(line.getLineItemId())).toList();
        if (lines.size() != ids.size()) return new QualifyingSpend(null, "A selected product line is missing");
        BigDecimal total = lines.stream().map(PurchaseItem::calculateLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        var kinds = transaction.getObservations().discountKinds();
        for (var discount : transaction.getAppliedCoupons()) {
            var links = discount.getLineItemIds();
            if (!links.isEmpty() && Collections.disjoint(ids, links)) continue;
            var kind = kinds.get(discount.getApplicationId());
            if (kind == DiscountKind.MANUFACTURER || kind == DiscountKind.STORE_COUPON) continue;
            if (kind != DiscountKind.SALE_PROMOTION)
                return new QualifyingSpend(null, "Classify sale/promotion reductions separately from coupons using DISCOUNTS");
            if(discount.isWholeReceipt()) {
                var all=transaction.getPurchaseItems().stream().map(PurchaseItem::getLineItemId).collect(java.util.stream.Collectors.toSet());
                if(!ids.containsAll(all))return new QualifyingSpend(null,"Whole-receipt sale reduction needs allocation before verifying only selected products");
                total=total.subtract(discount.getAppliedAmount());continue;
            }
            if (links.isEmpty() || !ids.containsAll(links))
                return new QualifyingSpend(null, "Sale reduction crosses selected/unselected products or has unknown product links; allocation needs review");
            total = total.subtract(discount.getAppliedAmount()); // one application, regardless of linked line count
        }
        if (total.signum() < 0) return new QualifyingSpend(null, "Sale reductions exceed selected merchandise; review unit prices and deductions");
        return new QualifyingSpend(total, null);
    }
}
