package coupons.repository;

import coupons.ReceiptTotals;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Preserves retailer-reported receipt totals for a revision.
 *
 * <p>Values are captured without rounding or recalculation.
 * Discrepancies between reported and calculated totals remain intact.
 *
 * @param taxAmount tax reported by the retailer
 * @param finalTotal final total reported by the retailer
 */
public record ReceiptTotalsSnap(
        BigDecimal taxAmount,
        BigDecimal finalTotal
) {

    /**
     * Validates recorded receipt totals.
     *
     * @param taxAmount tax reported by the retailer
     * @param finalTotal final total reported by the retailer
     * @throws NullPointerException if either amount is null
     * @throws IllegalArgumentException if either amount is negative
     */
    public ReceiptTotalsSnap {
        Objects.requireNonNull(taxAmount, "Tax amount cannot be null.");
        Objects.requireNonNull(finalTotal, "Final total cannot be null.");

        if (taxAmount.signum() < 0 || finalTotal.signum() < 0) {
            throw new IllegalArgumentException(
                    "Receipt totals cannot be negative."
            );
        }
    }

    /**
     * Captures retailer-reported totals.
     *
     * @param totals receipt totals to capture
     * @return an immutable snapshot
     * @throws NullPointerException if totals is null
     */
    public static ReceiptTotalsSnap from(ReceiptTotals totals) {
        Objects.requireNonNull(totals, "Receipt totals cannot be null.");

        return new ReceiptTotalsSnap(
                totals.getTaxAmount(),
                totals.getFinalTotal()
        );
    }
}
