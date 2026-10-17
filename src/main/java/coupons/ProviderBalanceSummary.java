package coupons;

import coupons.ReceiptObservations.EarningKind;
import coupons.repository.ReceiptRecord;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Session-only balance check from explicit opening observations and recorded receipts.
 * This is a USD valuation view, not a provider ledger or a declaration of redeemability.
 * Unrecorded payouts, expirations and external account adjustments are not invented.
 */
public final class ProviderBalanceSummary {
    private static final List<String> PROGRAMS = List.of("Walgreens Cash", "Register Rewards", "ECB", "Walmart Cash",
            "Checkout 51", "Ibotta", "Venmo", "Fetch", "P&G Brand", "Swagbucks", "MyPoints", "Coupons DotCom",
            "PayPal", "Gift Cards", "Returns", "Target", "Merryfield", "Makeena", "Shopmium", "Rakuten");
    private static final Currency USD = Currency.getInstance("USD");

    /** Retains earlier starting observations when the entrant revises a setting. */
    public record Opening(LocalDate date, String provider, BigDecimal value, boolean monthStart, Instant recordedAt) { }
    private final List<Opening> openings = new ArrayList<>();

    /** Stores a user-supplied starting dollar value without implying an actual conversion. */
    public void recordOpening(LocalDate date, String provider, BigDecimal value, boolean monthStart) {
        Objects.requireNonNull(date);
        Objects.requireNonNull(value);
        Objects.requireNonNull(provider);
        if (provider.isBlank()) throw new IllegalArgumentException("Provider must be nonblank.");
        openings.add(new Opening(monthStart ? date.withDayOfMonth(1) : date, canonical(provider), value, monthStart, Instant.now()));
    }

    public List<Opening> openingHistory() { return List.copyOf(openings); }

    /** Includes named programs even when their balances have not yet been entered. */
    public List<String> programs(List<ReceiptRecord> records) {
        List<String> result = new ArrayList<>(PROGRAMS);
        openings.forEach(o -> { if (!result.contains(o.provider())) result.add(o.provider()); });
        records.forEach(r -> r.transaction().getObservations().earnings().forEach(e -> {
            String name = program(e.kind(), e.provider());
            if (!result.contains(name)) result.add(name);
        }));
        return List.copyOf(result);
    }

    /** Recomputes from distinct receipt IDs; displaying or retrying cannot accumulate activity twice. */
    public void print(LocalDate day, List<ReceiptRecord> records, PrintStream out) {
        out.println();
        out.println("PROVIDER BALANCE CHECK — recorded USD values");
        if (day == null) {
            out.println("Receipt date not entered; day/month balances cannot be calculated.");
            return;
        }
        Map<java.util.UUID, ReceiptRecord> distinct = new LinkedHashMap<>();
        records.forEach(r -> distinct.put(r.transactionId(), r));
        Map<String, BigDecimal> changes = new HashMap<>();
        Set<String> missingValues = new HashSet<>();
        int pending = 0;
        int count = 0;
        for (ReceiptRecord receipt : distinct.values()) {
            var tx = receipt.transaction();
            if (!day.equals(tx.getObservations().purchaseDate())) continue;
            count++;
            for (Payment payment : tx.getPayments()) {
                if (payment.getPaymentType() == PaymentType.WALGREENS_CASH) add(changes, "Walgreens Cash", payment.getAmount().negate());
                if (payment.getPaymentType() == PaymentType.GIFT_CARD) add(changes, "Gift Cards", payment.getAmount().negate());
            }
            add(changes, "Register Rewards", new TransactionCalculator().calculateRegisterRewardsRedeemed(tx).negate());
            for (var earning : tx.getObservations().earnings()) {
                if (earning.status() == RebateStatus.REJECTED) continue;
                if (earning.confirmed() == null || earning.status() != RebateStatus.CREDITED && earning.status() != RebateStatus.PAID) {
                    pending++;
                    continue;
                }
                String provider = program(earning.kind(), earning.provider());
                var benefit = earning.confirmed();
                if (benefit.kind() == BenefitKind.POINTS) {
                    if (earning.valuation() == null || !USD.equals(earning.valuation().currency())) missingValues.add(provider);
                    else add(changes, provider, earning.valuation().amount());
                } else if (USD.equals(benefit.currency())) add(changes, provider, benefit.amount());
                else missingValues.add(provider);
            }
        }
        var providers = programs(List.copyOf(distinct.values()));
        out.println("Day: " + day + " | " + count + " recorded receipt(s), including this review");
        out.printf("%-20s %16s %16s %18s%n", "Provider", "Month start", "Day start", "Day end (estimate)");
        BigDecimal monthTotal = BigDecimal.ZERO, dayTotal = BigDecimal.ZERO, endTotal = BigDecimal.ZERO;
        boolean monthComplete = true, dayComplete = true, endComplete = true;
        for (String provider : providers) {
            BigDecimal month = opening(day.withDayOfMonth(1), provider, true);
            BigDecimal start = opening(day, provider, false);
            BigDecimal end = start == null || missingValues.contains(provider) ? null
                    : start.add(changes.getOrDefault(provider, BigDecimal.ZERO));
            out.printf("%-20s %16s %16s %18s%n", provider, value(month), value(start), value(end));
            if (month == null) monthComplete = false; else monthTotal = monthTotal.add(month);
            if (start == null) dayComplete = false; else dayTotal = dayTotal.add(start);
            if (end == null) endComplete = false; else endTotal = endTotal.add(end);
        }
        out.printf("%-20s %16s %16s %18s%n", "USD value total", total(monthTotal, monthComplete),
                total(dayTotal, dayComplete), total(endTotal, endComplete));
        out.println("Not entered = missing starting balance or missing USD valuation; it does not mean zero.");
        out.println("Use menu 7 (Balances) to enter month/day starting values.");
        out.println("End estimates use recorded received/credited benefits and redemptions only.");
        out.println("Points use their saved valuations once; USD value totals are not cash available to spend.");
        if (pending > 0) out.println("Expected/pending/approved benefits excluded from balances: " + pending);
        out.println("External payouts, conversions, expirations and adjustments are not tracked in this view yet.");
    }

    private BigDecimal opening(LocalDate date, String provider, boolean month) {
        for (int i = openings.size() - 1; i >= 0; i--) {
            Opening o = openings.get(i);
            if (o.date().equals(date) && o.provider().equals(provider) && o.monthStart() == month) return o.value();
        }
        return null;
    }

    private static void add(Map<String, BigDecimal> values, String program, BigDecimal amount) {
        values.merge(program, amount, BigDecimal::add);
    }

    private static String program(EarningKind kind, String provider) {
        return kind == EarningKind.REGISTER_REWARD ? "Register Rewards"
                : kind == EarningKind.WALGREENS_CASH ? "Walgreens Cash" : canonical(provider);
    }

    private static String canonical(String name) {
        String compact = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return switch (compact) {
            case "wc", "walgreenscash" -> "Walgreens Cash";
            case "rr", "registerreward", "registerrewards" -> "Register Rewards";
            case "swag", "swagbuck", "swagbucks" -> "Swagbucks";
            case "mypoint", "mypoints" -> "MyPoints";
            case "pgbrand", "pg", "pggoodeveryday" -> "P&G Brand";
            case "couponcom", "couponscom", "couponsdotcom" -> "Coupons DotCom";
            case "mckenna", "makenna", "makeena" -> "Makeena";
            case "shop", "shopmuim", "shopmium" -> "Shopmium";
            case "giftcard", "giftcards" -> "Gift Cards";
            case "ecb", "cvsextrabucks" -> "ECB";
            default -> PROGRAMS.stream().filter(p -> p.equalsIgnoreCase(name.trim())).findFirst().orElse(name);
        };
    }

    private static String value(BigDecimal value) {
        return value == null ? "not entered" : "$" + value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String total(BigDecimal value, boolean complete) {
        return (complete ? "" : "partial ") + value(value);
    }
}
