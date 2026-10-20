package coupons.repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

import coupons.AdvertisedDeal;
import coupons.ClippedCoupon;
import coupons.PlannedPurchase;
import coupons.PromotionQualification;
import coupons.ProviderBalanceSummary;
import coupons.PurchaseItem;
import coupons.ReceiptTimeInput;
import coupons.ReceiptWorksheetReport;
import coupons.SavingsReport;
import coupons.ShoppingEstimate;
import coupons.SourceArtifact;
import coupons.TransactionCalculator;
import static coupons.repository.PostgresRepositorySupport.transaction;

/**
 * Optional web read model and narrow actions, reusing domain repositories and
 * calculations.
 */
public final class PostgresPlatformRepository {

    private final PostgresConnectionFactory db;

    public PostgresPlatformRepository(PostgresConnectionFactory db) {
        this.db = Objects.requireNonNull(db);
    }

    public Map<String, Object> workspace() {
        return transaction(db, c -> {
            c.setReadOnly(true);
            var state = PostgresReceiptState.read(c);
            var records = state.receipts().values().stream().map(r -> new ReceiptRecord(r.transactionId(), r.status(), r.revision(), r.contents().toTransaction(), r.metadata(), r.evidenceIds()))
                    .sorted(Comparator.comparing((ReceiptRecord r) -> r.transaction().getObservations().purchaseDate(), Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(r -> ReceiptTimeInput.parse(r.transaction().getObservations().printedTime()).orElse(LocalTime.MIN), Comparator.reverseOrder())).toList();
            var summary = new ProviderBalanceSummary(PostgresReceiptState.rules(state));
            LocalDate day = records.stream().map(r -> r.transaction().getObservations().purchaseDate()).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(LocalDate.now());
            List<Object> balances = new ArrayList<>();
            boolean targetSplit = state.balances().openings().stream().anyMatch(o -> !o.date().isAfter(day) && (o.provider().equals("Target Gift Card") || o.provider().equals("Target Circle")))
                    || summary.movements(records).stream().anyMatch(m -> m.date() != null && !m.date().isAfter(day) && (m.provider().equals("Target Gift Card") || m.provider().equals("Target Circle")));
            for (String name : summary.programs(records)) {
                if (name.equals("Target") && targetSplit) {
                    continue;
                }
                var balance = summary.balance(day, name, records);
                boolean known = java.util.stream.Stream.of(balance.monthUsd(), balance.startUsd(), balance.endUsd(), balance.startPoints(), balance.endPoints()).anyMatch(v -> v != null && v.signum() != 0);
                if (known) {
                    balances.add(fields("provider", name, "balance", balance));
                }
            }
            var plans = new PostgresShoppingPlanRepository(db);
            var purchases = new PostgresPlannedPurchaseRepository(db);
            List<Object> planRows = new ArrayList<>();
            for (UUID id : ids(c, "SELECT plan_id FROM shopping_plan_current ORDER BY shopping_date NULLS LAST,plan_id")) {
                var plan = plans.findById(id).orElseThrow();
                var items = purchases.findByPlanId(id);
                Map<UUID, AdvertisedDeal> planDeals = new HashMap<>();
                Map<UUID, ClippedCoupon> planCoupons = new HashMap<>();
                for (var item : items) {
                    if (item.advertisedDealId() != null) {
                        new PostgresDealRepository(db).findById(item.advertisedDealId()).ifPresent(d -> planDeals.put(d.dealId(), d));

                    }
                    for (var couponId : item.clippedCouponIds()) {
                        new PostgresClippedCouponRepository(db).findById(couponId).ifPresent(v -> planCoupons.put(v.couponId(), v));

                    }
                }
                var pricing = ShoppingEstimate.calculate(items, planDeals, planCoupons, plan.shoppingDate(), plan.retailer());
                var checkout = new PostgresPlanCheckoutRepository(db).view(id, pricing);
                planRows.add(fields("pricing", pricing, "checkout", checkout, "plan", plan, "items", items, "estimatedMerchandise", items.isEmpty() ? null : pricing.saleTotal(), "estimateComplete", !items.isEmpty() && pricing.saleTotal() != null));
            }
            var couponRepo = new PostgresClippedCouponRepository(db);
            List<Object> coupons = new ArrayList<>();
            for (UUID id : ids(c, "SELECT coupon_id FROM clipped_coupon_current ORDER BY provider,coupon_id")) {
                coupons.add(couponFields(couponRepo.findById(id).orElseThrow()));
            }
            var dealRepo = new PostgresDealRepository(db);
            List<Object> deals = new ArrayList<>();
            for (UUID id : ids(c, "SELECT deal_id FROM deal_current ORDER BY valid_through NULLS LAST,deal_id")) {
                deals.add(dealRepo.findById(id).orElseThrow());
            }
            var rebateRepo = new PostgresRebateRepository(db);
            List<Object> rebates = new ArrayList<>();
            for (UUID id : ids(c, "SELECT claim_id FROM rebate_current ORDER BY provider,claim_id")) {
                rebates.add(rebateRepo.findById(id).orElseThrow());
            }
            var inventoryRepo = new PostgresInventoryRepository(db);
            List<Object> inventory = new ArrayList<>();
            for (UUID id : ids(c, "SELECT inventory_id FROM inventory_current ORDER BY product_id")) {
                inventory.add(inventoryRepo.findById(id).orElseThrow());
            }
            List<Object> reported = new ArrayList<>();
            for (var r : records) {
                if (r.transaction().getObservations().walgreensCash() != null) {
                    reported.add(fields("transactionId", r.transactionId(), "receiptNumber", r.metadata() == null ? null : r.metadata().receiptNumber(), "date", r.transaction().getObservations().purchaseDate(), "status", r.status(), "values", r.transaction().getObservations().walgreensCash()));
                }
            }
            return fields("receiptEarnings", records.stream().flatMap(r -> r.transaction().getObservations().earnings().stream().map(v -> fields("receiptId", r.transactionId(), "receiptNumber", r.metadata() == null ? null : r.metadata().receiptNumber(), "receiptStatus", r.status(), "earning", v))).toList(), "rewardOffers", records.stream().flatMap(r -> r.transaction().getObservations().earnings().stream()).filter(v -> v.requirement() != null).map(v -> fields("id", v.earningId(), "title", v.offerReference(), "kind", v.kind(), "provider", v.provider(), "amount", v.expected() == null ? v.confirmed().amount() : v.expected().amount(), "requirement", v.requirement())).toList(), "asOf", day, "receipts", records.stream().map(r -> fields("transactionId", r.transactionId(), "revision", r.revision(), "status", r.status(), "metadata", r.metadata(), "date", r.transaction().getObservations().purchaseDate(), "time", r.transaction().getObservations().printedTime(), "totals", totals(r))).toList(),
                    "summaries", fields("all", savings(records), "day", savings(SavingsReport.day(records, day)), "week", savings(ReceiptWorksheetReport.week(records, day)), "month", savings(SavingsReport.month(records, YearMonth.from(day)))), "plans", planRows, "coupons", coupons, "deals", deals, "rebates", rebates, "inventory", inventory, "balances", balances, "reportedBalances", reported,
                    "accountHistory", state.balances(), "movements", summary.movements(records), "promotions", state.giftCardPromotions().values(), "rrMatches", state.rrUsageLinks().values(), "rewardRemovals", state.rewardVoids().values());
        });
    }

    public Map<String, Object> receiptTotals(UUID id) {
        var receipt = new PostgresTransactionRepository(db).findById(id).or(() -> new PostgresTransactionRepository(db).findDeletedById(id)).orElseThrow();
        return totals(receipt);
    }

    public static Map<String, Object> totals(ReceiptRecord receipt) {
        var tx = receipt.transaction();
        var calculator = new TransactionCalculator();
        Map<String, List<PurchaseItem>> groups = new LinkedHashMap<>();
        for (var line : tx.getPurchaseItems()) {
            String key = line.getProductCode() != null ? "sku:" + line.getProductCode() : "description:" + line.getProductName().trim().toLowerCase(java.util.Locale.ROOT);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(line);
        }
        return fields("lineTotals", tx.getPurchaseItems().stream().collect(java.util.stream.Collectors.toMap(PurchaseItem::getLineItemId, PurchaseItem::calculateLineTotal)), "productGroups", groups.entrySet().stream().map(g -> fields("identity", g.getKey(), "description", g.getValue().getFirst().getProductName(), "quantity", g.getValue().stream().mapToLong(PurchaseItem::getQuantity).sum(), "lineItemIds", g.getValue().stream().map(PurchaseItem::getLineItemId).toList())).toList(),
                "qualification", tx.getObservations().earnings().stream().filter(e -> e.requirement() != null).map(e -> fields("earningId", e.earningId(), "review", PromotionQualification.evaluate(e, tx))).toList(), "regularMerchandise", calculator.calculateRegularMerchandiseTotal(tx), "saleMerchandise", calculator.calculateSaleMerchandiseTotal(tx), "coupons", calculator.calculateCouponTotal(tx),
                "rrUsed", calculator.calculateRegisterRewardsRedeemed(tx), "wcUsed", calculator.calculateWalgreensCashRedeemed(tx), "tax", tx.getReceiptTotals().getTaxAmount(), "calculatedDue", calculator.calculateExpectedCheckoutAmount(tx),
                "reportedDue", tx.getReceiptTotals().getFinalTotal(), "difference", calculator.calculateReceiptDifference(tx), "savings", savings(List.of(new ReceiptRecord(receipt.transactionId(), ReceiptStatus.ACTIVE, receipt.revision(), tx, receipt.metadata(), receipt.evidenceIds()))));
    }

    private static Map<String, Object> savings(List<ReceiptRecord> records) {
        var t = SavingsReport.calculate(records);
        return fields("receipts", t.receipts(), "regularWithTax", t.regularKnown() ? t.regularWithTax() : null,
                "due", t.due(), "cash", t.paymentsKnown() ? t.cash() : null, "card", t.paymentsKnown() ? t.card() : null,
                "newlyEarned", t.earningsKnown() ? t.newlyEarned() : null,
                "paidAfterRebates", t.paymentsKnown() && t.earningsKnown() ? t.paidAfterRebates() : null,
                "totalSaved", t.regularKnown() && t.paymentsKnown() && t.earningsKnown() ? t.totalSaved() : null,
                "percentSaved", t.percentSaved(), "issues", t.issues());
    }

    public List<ReceiptHistoryEntry> history(UUID id) {
        var history = new PostgresTransactionRepository(db).findHistoryByReceiptId(id);
        if (history.isEmpty()) {
            throw new NoSuchElementException();

        }
        return history;
    }

    public ReceiptEvidence evidence(UUID receipt, UUID evidence) {
        var artifact = new PostgresTransactionRepository(db).findEvidenceById(evidence).orElseThrow();
        if (!receipt.equals(artifact.getTransactionId())) {
            throw new NoSuchElementException();

        }
        return artifact;
    }

    public PlannedPurchase savePurchase(UUID planId, UUID id, String name, String code, int quantity, BigDecimal target,
            UUID deal, List<UUID> coupons, long expected, String actor) {
        var planRepo = new PostgresShoppingPlanRepository(db);
        planRepo.findById(planId).orElseThrow();
        if (deal != null) {
            new PostgresDealRepository(db).findById(deal).orElseThrow();
        }
        var couponRepo = new PostgresClippedCouponRepository(db);
        for (var coupon : coupons) {
            couponRepo.findById(coupon).orElseThrow();
        }
        var repo = new PostgresPlannedPurchaseRepository(db);
        if (expected > 0) {
            var old = repo.findById(id).orElseThrow();
            if (!old.planId().equals(planId)) {
                throw new IllegalArgumentException("Purchase belongs to another plan.");

            }
        }
        var next = new PlannedPurchase(id, planId, name, code, quantity, target, deal, coupons, expected == 0 ? 1 : Math.addExact(expected, 1));
        if (expected == 0) {
            repo.save(next, actor);
            return next;
        }
        return repo.update(next, expected, actor, "Web plan item update");
    }

    public void saveCoupons(List<ClippedCoupon> copies, String actor) {
        new PostgresClippedCouponRepository(db).saveAll(copies, actor);
    }

    public void saveDeal(AdvertisedDeal deal, String actor, SourceArtifact source) {
        new PostgresDealRepository(db).save(deal, actor, source);
    }

    public PlannedPurchase savePurchase(UUID planId, UUID id, String name, String code, int quantity, BigDecimal regular, BigDecimal sale, String source,
            UUID deal, List<UUID> coupons, long expected, String actor) {
        new PostgresShoppingPlanRepository(db).findById(planId).orElseThrow();
        if (deal != null) {
            new PostgresDealRepository(db).findById(deal).orElseThrow();
        }
        for (var coupon : coupons) {
            new PostgresClippedCouponRepository(db).findById(coupon).orElseThrow();
        }
        var repo = new PostgresPlannedPurchaseRepository(db);
        if (expected > 0 && !repo.findById(id).orElseThrow().planId().equals(planId)) {
            throw new IllegalArgumentException("Purchase belongs to another plan.");
        }
        var next = new PlannedPurchase(id, planId, name, code, quantity, sale != null ? sale : regular, deal, coupons, expected == 0 ? 1 : Math.addExact(expected, 1), regular, sale, source);
        if (expected == 0) {
            repo.save(next, actor);
            return next;
        }
        return repo.update(next, expected, actor, "Web plan pricing update");
    }

    private static List<UUID> ids(Connection c, String sql) throws SQLException {
        List<UUID> out = new ArrayList<>();
        try (var p = c.prepareStatement(sql); var r = p.executeQuery()) {
            while (r.next()) {
                out.add(r.getObject(1, UUID.class));

            }
        }
        return out;
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


    public Map<String, Object> checkout(UUID planId) {
        var plan = new PostgresShoppingPlanRepository(db)
                .findById(planId)
                .orElseThrow();

        var items = new PostgresPlannedPurchaseRepository(db)
                .findByPlanId(planId);

        Map<UUID, AdvertisedDeal> deals = new HashMap<>();
        Map<UUID, ClippedCoupon> coupons = new HashMap<>();

        var dealRepository = new PostgresDealRepository(db);
        var couponRepository = new PostgresClippedCouponRepository(db);

        for (var item : items) {
            if (item.advertisedDealId() != null) {
                dealRepository.findById(item.advertisedDealId())
                        .ifPresent(d -> deals.put(d.dealId(), d));
            }

            for (var couponId : item.clippedCouponIds()) {
                couponRepository.findById(couponId)
                        .ifPresent(c -> coupons.put(c.couponId(), c));
            }
        }

        var pricing = ShoppingEstimate.calculate(items, deals, coupons, plan.shoppingDate(), plan.retailer());
        return new PostgresPlanCheckoutRepository(db)
                .view(planId, pricing);
    }

    public Map<String, Object> saveCheckout(
            UUID planId,
            long expectedRevision,
            Map<String, Object> values,
            String actor) {

        new PostgresPlanCheckoutRepository(db)
                .save(planId, expectedRevision, values, actor);

        return checkout(planId);
    }

}
