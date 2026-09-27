package coupons; // Place the ReceiptTotals class in the coupons package.

/**
 * Stores the final monetary totals reported by a retailer's receipt.
 */
public class ReceiptTotals { // Represent the retailer-reported totals for one completed transaction.

    private final double taxAmount; // Store the tax amount reported on the receipt.
    private final double finalTotal; // Store the final transaction total reported on the receipt.

    /**
     * Creates immutable receipt totals.
     *
     * @param taxAmount tax reported by the retailer
     * @param finalTotal final amount reported by the retailer
     * @throws IllegalArgumentException if an amount is negative or not finite
     */
    public ReceiptTotals(double taxAmount, double finalTotal) { // Create one validated set of receipt totals.

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
    public double getTaxAmount() { // Provide read-only access to the receipt tax.
        return taxAmount; // Return the stored tax amount.
    }

    /**
     * Returns the final total reported on the receipt.
     *
     * @return retailer-reported final total
     */
    public double getFinalTotal() { // Provide read-only access to the final receipt total.
        return finalTotal; // Return the stored final total.
    }

    /**
     * Validates a monetary amount that may be zero but cannot be negative or non-finite.
     */
    private static void validateNonNegativeAmount( // Reuse the same validation rule for receipt amounts.
            double amount, // Receive the monetary value being validated.
            String fieldName) { // Receive the field name used in an error message.

        if (!Double.isFinite(amount)) { // Reject NaN and positive or negative infinity.
            throw new IllegalArgumentException( // Prevent invalid numeric data from entering the receipt.
                    fieldName + " must be a finite number." // Explain which monetary value is invalid.
            );
        }

        if (amount < 0.0) { // Allow zero but reject negative receipt amounts.
            throw new IllegalArgumentException( // Prevent an impossible negative receipt value.
                    fieldName + " cannot be negative." // Explain which monetary rule was violated.
            );
        }
    }
}