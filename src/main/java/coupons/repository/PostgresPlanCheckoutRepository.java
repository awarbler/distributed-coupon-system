package coupons.repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

import coupons.ShoppingEstimate;

public final class PostgresPlanCheckoutRepository {

    private final PostgresConnectionFactory db;

    private static final String[] MONEY_FIELDS = {
        "promoDiscount",
        "walgreensCashUsed",
        "registerRewardsCoupon",
        "giftCardUsed",
        "cashPayment",
        "cardPayment",
        "otherPayment"
    };

    private static final String[] MONEY_COLUMNS = {
        "promo_discount",
        "walgreens_cash_used",
        "register_rewards_coupon",
        "gift_card_used",
        "cash_payment",
        "card_payment",
        "other_payment"
    };

    public PostgresPlanCheckoutRepository(
            PostgresConnectionFactory db) {
        this.db = Objects.requireNonNull(db);
    }

    private static BigDecimal amount(Object value) {
        if (value == null || value.toString().isBlank()) {
            return BigDecimal.ZERO.setScale(2);
        }

        BigDecimal result = new BigDecimal(value.toString());

        if (result.signum() < 0
                || result.scale() > 2
                || result.precision() > 14) {
            throw new IllegalArgumentException(
                    "Amounts must be nonnegative, with at most two decimals."
            );
        }

        return result.setScale(2);
    }

    private static BigDecimal optionalAmount(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return amount(value);
    }

    private static BigDecimal taxRate(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }

        BigDecimal rate = new BigDecimal(value.toString());

        if (rate.signum() < 0
                || rate.compareTo(new BigDecimal("100")) > 0
                || rate.scale() > 4) {
            throw new IllegalArgumentException(
                    "Tax rate must be between 0 and 100 percent."
            );
        }

        return rate;
    }

    private static String optionalText(Object value, int limit) {
        if (value == null) {
            return null;
        }

        String text = value.toString().trim();

        if (text.isEmpty()) {
            return null;
        }

        if (text.length() > limit) {
            throw new IllegalArgumentException("Text is too long.");
        }

        return text;
    }

    private static Map<String, Object> defaults(UUID planId) {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("planId", planId);
        result.put("revision", 0L);
        result.put("promoCode", null);
        result.put("promoDiscount", BigDecimal.ZERO);
        result.put("taxRate", null);
        result.put("taxableBase", null);
        result.put("walgreensCashUsed", BigDecimal.ZERO);
        result.put("registerRewardsCoupon", BigDecimal.ZERO);
        result.put("giftCardUsed", BigDecimal.ZERO);
        result.put("cashPayment", BigDecimal.ZERO);
        result.put("cardPayment", BigDecimal.ZERO);
        result.put("otherPayment", BigDecimal.ZERO);
        result.put("notes", null);

        return result;
    }

    private static Map<String, Object> read(ResultSet r)
            throws SQLException {

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("planId", r.getObject("plan_id", UUID.class));
        result.put("revision", r.getLong("revision"));
        result.put("promoCode", r.getString("promo_code"));
        result.put("promoDiscount", r.getBigDecimal("promo_discount"));
        result.put("taxRate", r.getBigDecimal("tax_rate"));
        result.put("taxableBase", r.getBigDecimal("taxable_base"));

        for (int i = 0; i < MONEY_FIELDS.length; i++) {
            if (!MONEY_FIELDS[i].equals("promoDiscount")) {
                result.put(
                        MONEY_FIELDS[i],
                        r.getBigDecimal(MONEY_COLUMNS[i])
                );
            }
        }

        result.put("notes", r.getString("notes"));

        return result;
    }

    public Map<String, Object> find(UUID planId) {
        return PostgresRepositorySupport.transaction(db, c -> {
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT * FROM shopping_plan_checkout_current "
                    + "WHERE plan_id=?"
            )) {
                p.setObject(1, planId);

                try (ResultSet r = p.executeQuery()) {
                    return r.next()
                            ? read(r)
                            : defaults(planId);
                }
            }
        });
    }

    private static void bindValues(
            PreparedStatement p,
            Map<String, Object> values,
            int start) throws SQLException {

        p.setString(start, (String) values.get("promoCode"));
        p.setBigDecimal(
                start + 1,
                (BigDecimal) values.get("promoDiscount")
        );
        p.setBigDecimal(
                start + 2,
                (BigDecimal) values.get("taxRate")
        );
        p.setBigDecimal(
                start + 3,
                (BigDecimal) values.get("taxableBase")
        );
        p.setBigDecimal(
                start + 4,
                (BigDecimal) values.get("walgreensCashUsed")
        );
        p.setBigDecimal(
                start + 5,
                (BigDecimal) values.get("registerRewardsCoupon")
        );
        p.setBigDecimal(
                start + 6,
                (BigDecimal) values.get("giftCardUsed")
        );
        p.setBigDecimal(
                start + 7,
                (BigDecimal) values.get("cashPayment")
        );
        p.setBigDecimal(
                start + 8,
                (BigDecimal) values.get("cardPayment")
        );
        p.setBigDecimal(
                start + 9,
                (BigDecimal) values.get("otherPayment")
        );
        p.setString(start + 10, (String) values.get("notes"));
    }

    public Map<String, Object> save(
            UUID planId,
            long expectedRevision,
            Map<String, Object> input,
            String actor) {

        Objects.requireNonNull(planId);
        Objects.requireNonNull(input);

        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("Actor required.");
        }

        if (expectedRevision < 0) {
            throw new IllegalArgumentException(
                    "Invalid checkout revision."
            );
        }

        List<String> allowed = new ArrayList<>();
        allowed.add("promoCode");
        allowed.add("taxRate");
        allowed.add("taxableBase");
        allowed.add("notes");
        allowed.addAll(List.of(MONEY_FIELDS));

        for (String key : input.keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException(
                        "Unknown checkout field: " + key
                );
            }
        }

        Map<String, Object> values = defaults(planId);

        values.put(
                "promoCode",
                optionalText(input.get("promoCode"), 200)
        );
        values.put(
                "notes",
                optionalText(input.get("notes"), 2000)
        );
        values.put("taxRate", taxRate(input.get("taxRate")));
        values.put(
                "taxableBase",
                optionalAmount(input.get("taxableBase"))
        );

        for (String field : MONEY_FIELDS) {
            values.put(field, amount(input.get(field)));
        }

        return PostgresRepositorySupport.transaction(db, c -> {
            // Lock the parent plan to serialize checkout creation.
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT plan_id FROM shopping_plan_current "
                    + "WHERE plan_id=? FOR UPDATE"
            )) {
                p.setObject(1, planId);

                try (ResultSet r = p.executeQuery()) {
                    if (!r.next()) {
                        throw new NoSuchElementException(
                                "Shopping plan does not exist."
                        );
                    }
                }
            }

            long storedRevision = 0;

            try (PreparedStatement p = c.prepareStatement(
                    "SELECT revision FROM shopping_plan_checkout_current "
                    + "WHERE plan_id=? FOR UPDATE"
            )) {
                p.setObject(1, planId);

                try (ResultSet r = p.executeQuery()) {
                    if (r.next()) {
                        storedRevision = r.getLong(1);
                    }
                }
            }

            if (storedRevision != expectedRevision) {
                throw new ConcurrentModificationException(
                        "Checkout revision changed."
                );
            }

            long next = Math.addExact(storedRevision, 1);

            String columns
                    = "promo_code,promo_discount,tax_rate,taxable_base,"
                    + "walgreens_cash_used,register_rewards_coupon,"
                    + "gift_card_used,cash_payment,card_payment,"
                    + "other_payment,notes";

            if (storedRevision == 0) {
                String sql
                        = "INSERT INTO shopping_plan_checkout_current "
                        + "(plan_id,revision," + columns + ") "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";

                try (PreparedStatement p = c.prepareStatement(sql)) {
                    p.setObject(1, planId);
                    p.setLong(2, next);
                    bindValues(p, values, 3);
                    p.executeUpdate();
                }
            } else {
                String sql
                        = "UPDATE shopping_plan_checkout_current SET "
                        + "revision=?,promo_code=?,promo_discount=?,"
                        + "tax_rate=?,taxable_base=?,"
                        + "walgreens_cash_used=?,"
                        + "register_rewards_coupon=?,"
                        + "gift_card_used=?,cash_payment=?,"
                        + "card_payment=?,other_payment=?,notes=? "
                        + "WHERE plan_id=?";

                try (PreparedStatement p = c.prepareStatement(sql)) {
                    p.setLong(1, next);
                    bindValues(p, values, 2);
                    p.setObject(13, planId);
                    p.executeUpdate();
                }
            }

            String historySql
                    = "INSERT INTO shopping_plan_checkout_history "
                    + "(plan_id,revision,changed_by," + columns + ") "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

            try (PreparedStatement p = c.prepareStatement(historySql)) {
                p.setObject(1, planId);
                p.setLong(2, next);
                p.setString(3, actor);
                bindValues(p, values, 4);
                p.executeUpdate();
            }

            values.put("revision", next);

            return values;
        });
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal value(
            Map<String, Object> values,
            String key) {

        return (BigDecimal) values.get(key);
    }

    public Map<String, Object> view(
            UUID planId,
            ShoppingEstimate pricing) {

        Map<String, Object> result
                = new LinkedHashMap<>(find(planId));

        BigDecimal merchandise = pricing.saleTotal();
        BigDecimal registerCost = pricing.registerCost();

        List<String> issues = new ArrayList<>();

        BigDecimal promo = value(result, "promoDiscount");
        BigDecimal rr = value(result, "registerRewardsCoupon");

        BigDecimal taxRate = value(result, "taxRate");
        BigDecimal taxableBase = value(result, "taxableBase");

        BigDecimal tax = null;
        BigDecimal due = null;
        BigDecimal remaining = null;

        if (registerCost == null || merchandise == null) {
            issues.add(
                    "Merchandise or coupon estimate is incomplete."
            );
        } else {
            if (taxRate == null) {
                issues.add(
                        "Sales tax is not configured; amounts due exclude tax."
                );
            } else {
                BigDecimal base = taxableBase == null
                        ? merchandise
                        : taxableBase;

                tax = money(
                        base.multiply(taxRate)
                                .divide(new BigDecimal("100"), 8,
                                        RoundingMode.HALF_UP)
                );
            }

            due = registerCost.subtract(promo).subtract(rr);

            if (due.signum() < 0) {
                issues.add(
                        "Promotional discounts exceed estimated register cost."
                );
                due = BigDecimal.ZERO;
            }

            if (tax != null) {
                due = due.add(tax);
            }

            due = money(due);

            BigDecimal payments
                    = value(result, "walgreensCashUsed")
                            .add(value(result, "giftCardUsed"))
                            .add(value(result, "cashPayment"))
                            .add(value(result, "cardPayment"))
                            .add(value(result, "otherPayment"));

            remaining = money(due.subtract(payments));

            if (remaining.signum() < 0) {
                issues.add(
                        "Planned payments exceed the estimated amount due."
                );
            }
        }

        if (rr.signum() > 0) {
            issues.add(
                    "Register Rewards are treated as checkout coupons. "
                    + "Verify coupon eligibility and avoid recording the "
                    + "same reward twice."
            );
        }

        Map<String, Object> summary = new LinkedHashMap<>();

        summary.put("tax", tax);
        summary.put("amountDue", tax == null ? null : due);
        summary.put(
                "remainingPayment",
                tax == null ? null : remaining
        );
        summary.put("expectedRewards", pricing.expectedRewards());
        summary.put("issues", issues);

        result.put("summary", summary);

        result.put("estimateComplete",
                registerCost != null
                && merchandise != null
                && taxRate != null
        );

        return result;
    }
}
