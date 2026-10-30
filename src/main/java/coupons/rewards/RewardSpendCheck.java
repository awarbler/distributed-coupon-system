package coupons.rewards;

import coupons.receipts.CouponTransaction;
import coupons.promotions.PromotionType;
import coupons.promotions.QualifyingSpend;

import coupons.receipts.ReceiptObservations.*;
import java.math.BigDecimal;

/** Rechecks recorded spending arithmetic; never credits rewards or verifies offer eligibility. */
public record RewardSpendCheck(Status status, BigDecimal qualifyingSpend, BigDecimal requiredSpend, String reason) {
    public enum Status { MET, NOT_MET, CANNOT_VERIFY }

    public static RewardSpendCheck evaluate(Earning earning, CouponTransaction transaction) {
        var requirement = earning.requirement();
        BigDecimal required = requirement == null ? null : requirement.amount();
        if (requirement == null || requirement.type() != PromotionType.SPEND)
            return new RewardSpendCheck(Status.CANNOT_VERIFY, null, required, "No recorded spend requirement");
        if (requirement.spendBasis() != SpendBasis.SALE_BEFORE_COUPONS)
            return new RewardSpendCheck(Status.CANNOT_VERIFY, null, required, "Spending basis is other or unknown");
        if ((earning.scope() != Scope.ITEM && earning.scope() != Scope.MULTI_ITEM) || earning.lineItemIds().isEmpty())
            return new RewardSpendCheck(Status.CANNOT_VERIFY, null, required, "Select eligible product lines (A selects all listed products)");
        var spend = QualifyingSpend.calculate(transaction, earning.lineItemIds());
        if (spend.amount() == null) return new RewardSpendCheck(Status.CANNOT_VERIFY, null, required, spend.problem());
        BigDecimal total = spend.amount();
        return new RewardSpendCheck(total.compareTo(required) >= 0 ? Status.MET : Status.NOT_MET, total, required,
                "After sale/promotion reductions, before coupons");
    }
}
