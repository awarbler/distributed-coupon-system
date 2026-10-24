package coupons;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Plan-only estimates. Never posts rewards or changes a receipt or account
 * balance.
 */
public record ShoppingEstimate(BigDecimal regularTotal, BigDecimal saleTotal, BigDecimal saleSavings,
        BigDecimal couponSavings, BigDecimal expectedRewards, BigDecimal registerCost,
        BigDecimal finalCost, List<String> issues, List<DealProgress> dealProgress,
        BigDecimal knownPotentialRewards, boolean rewardsComplete, boolean reviewRequired) {

    public enum RewardEstimateStatus { NOT_APPLICABLE, KNOWN, UNKNOWN }
    public enum ThresholdStatus { NOT_APPLICABLE, MET, NOT_MET, UNKNOWN }
    public enum ReviewStatus { NOT_RECORDED, MATCHES, NEEDS_REVIEW }

    public record DealProgress(UUID dealId, long qualifyingQuantity, Integer requiredQuantity,
            Long quantityMissing, BigDecimal qualifyingSpend, BigDecimal requiredSpend,
            BigDecimal spendMissing, ThresholdStatus thresholdStatus, ReviewStatus storeStatus,
            ReviewStatus dateStatus, RewardEstimateStatus rewardStatus, BigDecimal potentialReward,
            BigDecimal rewardIfCompleted, List<String> messages) {
        public DealProgress { messages = List.copyOf(messages); }
    }

    public ShoppingEstimate {
        issues = List.copyOf(issues);
        dealProgress = List.copyOf(dealProgress);
    }

    /** Compatibility for callers supplying an already calculated aggregate. */
    public ShoppingEstimate(BigDecimal regularTotal, BigDecimal saleTotal, BigDecimal saleSavings,
            BigDecimal couponSavings, BigDecimal expectedRewards, BigDecimal registerCost,
            BigDecimal finalCost, List<String> issues) {
        this(regularTotal, saleTotal, saleSavings, couponSavings, expectedRewards, registerCost,
                finalCost, issues, List.of(), expectedRewards == null ? BigDecimal.ZERO : expectedRewards,
                expectedRewards != null, false);
    }

    public static ShoppingEstimate calculate(List<PlannedPurchase> items, Map<UUID, AdvertisedDeal> deals,
            Map<UUID, ClippedCoupon> coupons, LocalDate date, String retailer) {
        BigDecimal regular = BigDecimal.ZERO, sale = BigDecimal.ZERO, rewards = BigDecimal.ZERO, discount = BigDecimal.ZERO;
        boolean known = true, regularKnown = true;
        List<String> issues = new ArrayList<>();
        List<DealProgress> progress = new ArrayList<>();
        boolean rewardsComplete = true, reviewRequired = false;
        Map<UUID, BigDecimal> linePrices = new HashMap<>();
        for (var item : items) {
            var r = item.regularUnitPrice();
            if (r == null) {
                regularKnown = false;
                issues.add("Regular price missing: " + item.productName());
            } else {
                regular = regular.add(r.multiply(BigDecimal.valueOf(item.quantity())));
            }
            var unit = item.saleUnitPrice() != null ? item.saleUnitPrice() : item.regularUnitPrice() != null ? item.regularUnitPrice() : item.targetUnitPrice();
            if (unit == null) {
                known = false;
                issues.add("Price missing: " + item.productName());
            } else {
                linePrices.put(item.plannedPurchaseId(), unit.multiply(BigDecimal.valueOf(item.quantity())));
            }
        }
        for (var entry : deals.entrySet()) {
            var d = entry.getValue();
            var terms = d.pricing();
            if (terms == null) {
                continue;
            }
            var linked = items.stream().filter(i -> entry.getKey().equals(i.advertisedDealId())).toList();
            if (linked.isEmpty()) {
                continue;
            }
            ReviewStatus storeStatus = retailer == null || retailer.isBlank()
                    || d.retailer() == null || d.retailer().isBlank()
                    ? ReviewStatus.NOT_RECORDED
                    : d.retailer().trim().equalsIgnoreCase(retailer.trim())
                            ? ReviewStatus.MATCHES : ReviewStatus.NEEDS_REVIEW;
            boolean outsideDates = date != null && ((d.validFrom() != null && date.isBefore(d.validFrom()))
                    || (d.validThrough() != null && date.isAfter(d.validThrough())));
            ReviewStatus dateStatus = outsideDates ? ReviewStatus.NEEDS_REVIEW
                    : date == null || d.validFrom() == null || d.validThrough() == null
                            ? ReviewStatus.NOT_RECORDED : ReviewStatus.MATCHES;
            List<String> messages = new ArrayList<>();
            if (storeStatus == ReviewStatus.NOT_RECORDED)
                messages.add("Store not selected or promotion store not recorded; confirm store for " + d.title());
            if (storeStatus == ReviewStatus.NEEDS_REVIEW)
                messages.add("Is this the correct store? Plan: " + retailer + "; promotion: " + d.retailer());
            if (dateStatus == ReviewStatus.NOT_RECORDED)
                messages.add("Shopping date or promotion dates not recorded; confirm dates for " + d.title());
            if (dateStatus == ReviewStatus.NEEDS_REVIEW)
                messages.add("Is this the intended shopping date? It is outside the promotion period: " + d.title());
            boolean needsReview = storeStatus != ReviewStatus.MATCHES || dateStatus != ReviewStatus.MATCHES;
            reviewRequired |= needsReview;
            if (terms.loyaltyRequirement() != null || terms.qualifications() != null || terms.exclusions() != null)
                messages.add("Review recorded qualifications, loyalty terms and exclusions: " + d.title());
            // Linked purchases qualify by default; strict matching is explicitly enabled.
            var eligible = linked.stream()
                    .filter(item -> !terms.strictSkuMatching()
                            || (item.productCode() != null
                                && terms.qualifyingSkus().contains(item.productCode())))
                    .toList();
            if (terms.strictSkuMatching()) {
                for (var item : linked) {
                    if (!eligible.contains(item)) {
                        messages.add("Excluded from deal due to strict UPC/SKU matching: "
                                + item.productName() + " — " + d.title());
                    }
                }
            }
            long quantity = eligible.stream().mapToLong(PlannedPurchase::quantity).sum();
            boolean pricesKnown = eligible.stream().allMatch(i -> linePrices.containsKey(i.plannedPurchaseId()));
            BigDecimal spend = pricesKnown ? eligible.stream().map(i -> linePrices.get(i.plannedPurchaseId()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add) : null;
            boolean spendReward = terms.type() == DealTerms.Type.SPEND_REWARD;
            boolean quantityReward = terms.type() == DealTerms.Type.QUANTITY_REWARD;
            long count = quantity / terms.minimumQuantity();
            if (!terms.repeatable()) count = Math.min(count, 1);
            if (terms.limit() != null) count = Math.min(count, terms.limit());
            if (spendReward || quantityReward) {
                boolean determined = !spendReward || pricesKnown;
                BigDecimal groups = null;
                if (determined) {
                    groups = spendReward ? spend.divideToIntegralValue(terms.minimumSpend())
                            : BigDecimal.valueOf(count);
                    if (!terms.repeatable()) groups = groups.min(BigDecimal.ONE);
                    if (terms.limit() != null) groups = groups.min(BigDecimal.valueOf(terms.limit()));
                }
                BigDecimal potential = determined ? terms.rewardAmount().multiply(groups) : null;
                ThresholdStatus threshold = !determined ? ThresholdStatus.UNKNOWN
                        : groups.signum() > 0 ? ThresholdStatus.MET : ThresholdStatus.NOT_MET;
                Long missing = quantityReward ? Math.max(0L, (long) terms.minimumQuantity() - quantity) : null;
                BigDecimal spendMissing = spendReward && pricesKnown
                        ? terms.minimumSpend().subtract(spend).max(BigDecimal.ZERO) : null;
                if (!determined) {
                    rewardsComplete = false;
                    messages.add("Potential reward cannot yet be calculated; enter qualifying prices: " + d.title());
                } else {
                    rewards = rewards.add(potential);
                    if (threshold == ThresholdStatus.NOT_MET) {
                        messages.add("Threshold not met: " + d.title());
                        messages.add(quantityReward
                                ? "Add " + missing + " more qualifying item(s) to potentially earn " + terms.rewardAmount()
                                : "Add " + spendMissing + " in qualifying spend to potentially earn " + terms.rewardAmount());
                    }
                }
                progress.add(new DealProgress(d.dealId(), quantity,
                        quantityReward ? terms.minimumQuantity() : null, missing, spend,
                        spendReward ? terms.minimumSpend() : null, spendMissing, threshold, storeStatus, dateStatus,
                        determined ? RewardEstimateStatus.KNOWN : RewardEstimateStatus.UNKNOWN, potential,
                        threshold == ThresholdStatus.NOT_MET ? terms.rewardAmount() : null, messages));
                issues.addAll(messages);
                continue;
            }
            progress.add(new DealProgress(d.dealId(), quantity, null, null, spend, null, null,
                    ThresholdStatus.NOT_APPLICABLE, storeStatus, dateStatus,
                    RewardEstimateStatus.NOT_APPLICABLE, null, null, messages));
            issues.addAll(messages);
            // Planning reward assumptions must not authorize ineligible merchandise discounts.
            boolean valid = within(date, d.validFrom(), d.validThrough())
                    && (d.retailer() == null || retailer != null && d.retailer().trim().equalsIgnoreCase(retailer.trim()));
            if (!valid) {
                issues.add("Deal date/store not qualified for automatic sale pricing: " + d.title());
                continue;
            }
            if (count < 1) {
                issues.add("Threshold not met: " + d.title());
                continue;
            }
            if (terms.type() == DealTerms.Type.BUY_GET && !terms.cheapestItemsDiscounted()) {
                issues.add("Buy/get discounted product allocation is not recorded; using manual prices: " + d.title());
                continue;
            }
            var priced = eligible.stream().filter(i -> "DEAL".equals(i.priceSource())).toList();
            if (priced.size() != eligible.size()) {
                issues.add("Manual pricing retained for deal: " + d.title());
                continue;
            }
            if (terms.type() == DealTerms.Type.SALE) {
                for (var i : priced) {
                    linePrices.put(i.plannedPurchaseId(), terms.promotionalPrice().multiply(BigDecimal.valueOf(i.quantity())));
                }
            } else {
                // Pool linked units across all products. Full bundles only; remainder keeps each unit's recorded price.
                List<Map.Entry<BigDecimal, Integer>> units = new ArrayList<>();
                for (var i : priced) {
                    var unit = i.regularUnitPrice() != null ? i.regularUnitPrice() : i.targetUnitPrice();
                    if (unit == null) {
                        known = false;
                        continue;
                    }
                    units.add(Map.entry(unit, i.quantity()));
                }
                if (units.size() != priced.size()) {
                    continue;
                }
                BigDecimal total = units.stream().map(u -> u.getKey().multiply(BigDecimal.valueOf(u.getValue()))).reduce(BigDecimal.ZERO, BigDecimal::add);
                if (terms.type() == DealTerms.Type.MULTIBUY) {
                    units.sort(Map.Entry.<BigDecimal, Integer>comparingByKey().reversed());
                    long remaining = Math.multiplyExact(count, terms.minimumQuantity());
                    for (var unit : units) {
                        long n = Math.min(remaining, unit.getValue());
                        total = total.subtract(unit.getKey().multiply(BigDecimal.valueOf(n)));
                        remaining -= n;
                    }
                    total = total.add(terms.promotionalPrice().multiply(BigDecimal.valueOf(count)));
                } else {
                    long bundles = quantity / Math.addExact((long) terms.minimumQuantity(), terms.freeQuantity());
                    if (!terms.repeatable()) {
                        bundles = Math.min(bundles, 1);
                    }
                    if (terms.limit() != null) {
                        bundles = Math.min(bundles, terms.limit());
                    }
                    units.sort(Map.Entry.comparingByKey());
                    long remaining = Math.multiplyExact(bundles, terms.freeQuantity());
                    for (var unit : units) {
                        long n = Math.min(remaining, unit.getValue());
                        total = total.subtract(unit.getKey().subtract(terms.promotionalPrice().min(unit.getKey())).multiply(BigDecimal.valueOf(n)));
                        remaining -= n;
                    }
                }
                for (var i : priced) {
                    linePrices.put(i.plannedPurchaseId(), BigDecimal.ZERO);
                }
                linePrices.put(priced.getFirst().plannedPurchaseId(), total);
            }
        }
        sale = linePrices.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        Set<UUID> used = new HashSet<>();
        Map<UUID, Integer> couponUses = new HashMap<>();
        for (var item : items) {
            for (var id : item.clippedCouponIds()) {
                if (used.add(id)) {
                    var coupon = coupons.get(id);
                    if (coupon == null || coupon.status() != CouponStatus.CLIPPED || !within(date, coupon.clippedOn(), coupon.coupon().getExpirationDate())) {
                        issues.add("Coupon unavailable or expired: " + id);
                        continue;
                    }
                    var terms = coupon.coupon().getTerms();
                    if (terms != null && (!within(date, terms.startsOn(), coupon.coupon().getExpirationDate()) || terms.storeRestriction() != null && (retailer == null || !terms.storeRestriction().equalsIgnoreCase(retailer)))) {
                        issues.add("Coupon date/store restriction not met: " + coupon.coupon().getCouponDescription());
                        continue;
                    }
                    if (terms != null && terms.offerId() != null && couponUses.getOrDefault(terms.offerId(), 0) >= terms.usageLimit()) {
                        issues.add("Coupon usage limit reached: " + coupon.coupon().getCouponDescription());
                        continue;
                    }
                    var eligible = items.stream().filter(i -> i.clippedCouponIds().contains(id) && (terms == null || terms.qualifyingSkus().isEmpty() || (i.productCode() != null && terms.qualifyingSkus().contains(i.productCode())))).toList();
                    long quantity = eligible.stream().mapToLong(PlannedPurchase::quantity).sum();
                    if (quantity < coupon.coupon().getRequiredQuantity()) {
                        issues.add("Coupon quantity not met: " + coupon.coupon().getCouponDescription());
                        continue;
                    }
                    var eligiblePrice = eligible.stream().map(i -> linePrices.getOrDefault(i.plannedPurchaseId(), BigDecimal.ZERO)).reduce(BigDecimal.ZERO, BigDecimal::add);
                    if (terms != null && terms.minimumPurchase() != null && eligiblePrice.compareTo(terms.minimumPurchase()) < 0) {
                        issues.add("Coupon minimum spend not met: " + coupon.coupon().getCouponDescription());
                        continue;
                    }
                    var value = coupon.coupon().getDiscountAmount();
                    if (terms != null && terms.discount() == CouponTerms.Discount.PERCENT) {
                        value = eligiblePrice.multiply(value).movePointLeft(2);
                    }
                    if (terms != null && terms.discount() == CouponTerms.Discount.FREE_ITEM) {
                        if (eligible.stream().anyMatch(i -> "DEAL".equals(i.priceSource()) && i.advertisedDealId() != null && deals.containsKey(i.advertisedDealId()) && deals.get(i.advertisedDealId()).pricing() != null && Set.of(DealTerms.Type.MULTIBUY, DealTerms.Type.BUY_GET).contains(deals.get(i.advertisedDealId()).pricing().type()))) {
                            issues.add("Free-item coupon allocation with bundle pricing needs review: " + coupon.coupon().getCouponDescription());
                            continue;
                        }
                        value = eligible.stream().map(i -> linePrices.getOrDefault(i.plannedPurchaseId(), BigDecimal.ZERO).divide(BigDecimal.valueOf(i.quantity()))).min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
                    }
                    discount = discount.add(value.min(eligiblePrice));
                    if (terms != null && terms.offerId() != null) {
                        couponUses.merge(terms.offerId(), 1, Integer::sum);
                    }
                }
            }
        }
        discount = discount.min(sale);
        var register = sale.subtract(discount);
        return new ShoppingEstimate(regularKnown ? regular : null, known ? sale : null, regularKnown && known ? regular.subtract(sale) : null, known ? discount : null, rewardsComplete ? rewards : null, known ? register : null, known && rewardsComplete ? register.subtract(rewards) : null, List.copyOf(issues), progress, rewards, rewardsComplete, reviewRequired);
    }

    private static boolean within(LocalDate date, LocalDate from, LocalDate through) {
        return date != null && (from == null || !date.isBefore(from)) && (through == null || !date.isAfter(through));
    }
}
