package coupons.receipts; // Place the ReceiptTotals class in the coupons package.

import java.math.BigDecimal; // Use BigDecimal for precise monetary calculations.

/**
 * Stores the final monetary totals reported by a retailer's receipt.
 */
public class ReceiptTotals { // Represent the retailer-reported totals for one completed transaction.

    private final BigDecimal taxAmount; // Store the tax amount reported on the receipt.
    private final BigDecimal finalTotal; // Store the final transaction total reported on the receipt.

    /**
     * Creates immutable receipt totals.
     *
     * @param taxAmount tax reported by the retailer
     * @param finalTotal final amount reported by the retailer
     * @throws NullPointerException if an amount is null
     * @throws IllegalArgumentException if an amount is negative
     */
    public ReceiptTotals(BigDecimal taxAmount, BigDecimal finalTotal) { // Create one validated set of receipt totals.

        validateNonNegativeAmount( // Validate the retailer-reported tax before storing it.
                taxAmount, // Check the supplied tax amount.
                "Tax amount" // Identify this value in any validation error.
        );

        validateNonNegativeAmount( // Validate the retailer-reported final total before storing it.
                finalTotal, // Check the supplied final receipt total.
                "Receipt total" // Identify this value in any validation error.
        );

        this.taxAmount = taxAmount; // Store the validated retailer-reported tax.
        this.finalTotal = finalTotal; // Store the validated retailer-reported final total.
    }

    /**
     * Returns the tax reported on the receipt.
     *
     * @return retailer-reported tax amount
     */
    public BigDecimal getTaxAmount() { // Provide read-only access to the receipt tax.
        return taxAmount; // Return the stored tax amount.
    }

    /**
     * Returns the final total reported on the receipt.
     *
     * @return retailer-reported final total
     */
    public BigDecimal getFinalTotal() { // Provide read-only access to the final receipt total.
        return finalTotal; // Return the stored final total.
    }

    /**
     * Validates a monetary amount that may be zero but cannot be negative or
     * null or negative. Throws an exception if the amount is invalid.
     */
    private static void validateNonNegativeAmount(
            BigDecimal amount,
            String fieldName) {

        if (amount == null) {
            throw new NullPointerException(
                    fieldName + " cannot be null."
            );
        }

        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    fieldName + " cannot be negative."
            );
        }
    }
}
