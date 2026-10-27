package coupons.repository;

import coupons.*;
import java.math.BigDecimal;
import java.util.*;

/** Pure receipt views; no storage access or financial formula changes. */
public final class ReceiptViews {
    private ReceiptViews() {}
    public static Map<String, Object> totals(ReceiptRecord receipt) {
        var tx = receipt.transaction();
        var calculator = new TransactionCalculator();
        Map<String, List<PurchaseItem>> groups = new LinkedHashMap<>();
        for (var line : tx.getPurchaseItems()) {
            String key = line.getProductCode() != null ? "sku:" + line.getProductCode() : "description:" + line.getProductName().trim().toLowerCase(java.util.Locale.ROOT);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(line);
        }
        var paymentReview = calculator.reconcilePayments(tx, receipt.metadata());
        var benefitTotals = SavingsReport.calculate(List.of(receipt));
        boolean paymentComplete = paymentReview.paymentEntryStatus() == ReceiptMeta.PaymentEntryStatus.COMPLETE
                && paymentReview.unresolvedPaymentIds().isEmpty();
        BigDecimal adjusted = paymentComplete && benefitTotals.earningsKnown()
                ? paymentReview.recordedCashCardPaid().subtract(benefitTotals.knownConfirmedBenefitValue()) : null;
        return fields("calculationChecks", calculator.recordedChecks(tx,receipt.metadata()), "printedObservations", tx.getObservations(), "discountBreakdown", discountBreakdown(tx), "paymentReview", paymentReview, "benefitAdjustedCashSpending", adjusted,
                "benefitObservations", tx.getObservations().earnings().stream().map(e -> fields(
                        "earningId", e.earningId(), "provider", e.provider(), "status", e.status(),
                        "expected", e.expected(), "confirmed", e.confirmed(), "valuation", SavingsReport.effectiveValuation(e))).toList(),
                "lineTotals", tx.getPurchaseItems().stream().collect(java.util.stream.Collectors.toMap(PurchaseItem::getLineItemId, PurchaseItem::calculateLineTotal)), "productGroups", groups.entrySet().stream().map(g -> fields("identity", g.getKey(), "description", g.getValue().getFirst().getProductName(), "quantity", g.getValue().stream().mapToLong(PurchaseItem::getQuantity).sum(), "lineItemIds", g.getValue().stream().map(PurchaseItem::getLineItemId).toList())).toList(),
                "qualification", tx.getObservations().earnings().stream().filter(e -> e.requirement() != null).map(e -> fields("earningId", e.earningId(), "review", PromotionQualification.evaluate(e, tx))).toList(), "regularMerchandise", tx.getPurchaseItems().stream().allMatch(i -> i.getRegularUnitPrice() != null) ? calculator.calculateRegularMerchandiseTotal(tx) : null, "saleMerchandise", calculator.calculateSaleMerchandiseTotal(tx), "coupons", calculator.calculateCouponTotal(tx),
                "rrUsed", calculator.calculateRegisterRewardsRedeemed(tx), "wcUsed", calculator.calculateWalgreensCashRedeemed(tx), "tax", tx.getReceiptTotals().getTaxAmount(), "calculatedDue", calculator.calculateExpectedCheckoutAmount(tx),
                "reportedDue", tx.getReceiptTotals().getFinalTotal(), "difference", calculator.calculateReceiptDifference(tx), "savings", savings(List.of(new ReceiptRecord(receipt.transactionId(), ReceiptStatus.ACTIVE, receipt.revision(), tx, receipt.metadata(), receipt.evidenceIds()))));
    }

    static Map<String,Object> discountBreakdown(coupons.CouponTransaction tx) {
        Map<String,BigDecimal> totals = new LinkedHashMap<>();
        for (String name : List.of("salePromotions","manufacturerCoupons","storeDiscounts","unclassified")) totals.put(name,BigDecimal.ZERO);
        for (var discount : tx.getAppliedCoupons()) {
            var kind = tx.getObservations().discountKinds().get(discount.getApplicationId());
            String group = kind == null ? "unclassified" : switch (kind) {
                case SALE_PROMOTION -> "salePromotions";
                case MANUFACTURER -> "manufacturerCoupons";
                case STORE, STORE_COUPON -> "storeDiscounts";
                default -> "unclassified";
            };
            totals.put(group,totals.get(group).add(discount.getAppliedAmount()));
        }
        return new LinkedHashMap<>(totals);
    }

    static Map<String, Object> savings(List<ReceiptRecord> records) {
        var t = SavingsReport.calculate(records);
        return fields("receipts", t.receipts(), "regularWithTax", t.regularKnown() ? t.regularWithTax() : null,
                "recordedCashCardPaid", t.cash().add(t.card()),
                "recordedCashCardComplete", t.paymentsKnown(),
                "due", t.due(), "cash", t.paymentsKnown() ? t.cash() : null, "card", t.paymentsKnown() ? t.card() : null,
                "knownConfirmedBenefitValue", t.knownConfirmedBenefitValue(),
                "confirmedBenefitValue", t.confirmedBenefitValue(), "confirmedBenefitsComplete", t.earningsKnown(),
                "unvaluedProviders", t.unvaluedApps(),
                "newlyEarned", t.earningsKnown() ? t.newlyEarned() : null,
                "paidAfterRebates", t.paymentsKnown() && t.earningsKnown() ? t.paidAfterRebates() : null,
                "totalSaved", t.regularKnown() && t.paymentsKnown() && t.earningsKnown() ? t.totalSaved() : null,
                "percentSaved", t.percentSaved(), "issues", t.issues());
    }

    public static Map<String, Object> couponFields(ClippedCoupon c) {
        return fields("couponId", c.couponId(), "provider", c.provider(), "externalId", c.externalId(), "status", c.status(), "clippedOn", c.clippedOn(), "redeemedOn", c.redeemedOn(), "redeemedTransactionId", c.redeemedTransactionId(), "revision", c.revision(), "description", c.coupon().getCouponDescription(), "discountAmount", c.coupon().getDiscountAmount(), "couponType", c.coupon().getCouponType(), "requiredQuantity", c.coupon().getRequiredQuantity(), "terms", c.coupon().getTerms(), "expirationDate", c.coupon().getExpirationDate());
    }

    private static Map<String, Object> fields(Object... pairs) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.put((String) pairs[i], pairs[i + 1]);

        }
        return out;
    }
}
