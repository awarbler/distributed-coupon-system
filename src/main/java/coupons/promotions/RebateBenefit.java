package coupons.promotions;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * Describes a quantified rebate benefit without assigning cash equivalence.
 *
 * <p>Claim status and supporting evidence establish whether a benefit
 * is expected, approved, credited, or fulfilled.
 *
 * <p>Amounts preserve the supplied precision and scale. This type does not
 * enforce currency settlement precision or perform rounding. Financial
 * calculations and payouts require explicit precision and rounding rules.
 *
 * <p>Program is a recorded identifier or description, not a verified
 * statement of the benefit's issuer, issuing provider, accepted merchants,
 * or redemption restrictions. Those relationships require separate data.
 *
 * <p>Crediting and subsequently paying out the same benefit must not be
 * counted as two earnings. This value type does not implement that accounting.
 *
 * @param kind recorded benefit form
 * @param amount positive quantity or monetary face value
 * @param currency denomination for monetary face values; null for points
 * @param program recorded points or gift-card program; optional for cash
 */
public record RebateBenefit(
        BenefitKind kind,
        BigDecimal amount,
        Currency currency,
        String program
) implements java.io.Serializable {
    /**
     * Validates a quantified benefit without converting its value.
     *
     * <p>No exchange rates, points conversion rates, or gift-card discounts
     * are assumed. Unknown denomination details must not be invented.
     *
     * @param kind recorded benefit form
     * @param amount positive quantity or monetary face value
     * @param currency monetary face-value currency; null for points
     * @param program recorded points or gift-card program; optional for cash
     * @throws NullPointerException if kind or amount is null
     * @throws IllegalArgumentException if amount is not positive,
     *         required denomination information is missing,
     *         points include a currency, or supplied program text is blank
     */
    public RebateBenefit {
        Objects.requireNonNull(kind, "Benefit kind cannot be null.");
        Objects.requireNonNull(amount, "Benefit amount cannot be null.");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Benefit amount must be greater than zero.");
        }
        if (program != null && program.isBlank()) {
            throw new IllegalArgumentException("Program must be null or nonblank.");
        }
        if (kind == BenefitKind.POINTS) {
            if (currency != null || program == null) {
                throw new IllegalArgumentException(
                        "Points require a program and must not have a currency.");
            }
        } else if (currency == null) {
            throw new IllegalArgumentException("Monetary face values require a currency.");
        }
        if (kind == BenefitKind.GIFT_CARD && program == null) {
            throw new IllegalArgumentException("Gift cards require a recorded program.");
        }
    }
}
