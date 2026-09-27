package coupons; // Place the Payment class in the coupons package.

import java.util.Objects; // Provide null checking for required object values.

/**
 * Represents one payment or redeemed-value amount used in a transaction.
 */
public class Payment { // Define the data and rules for one payment record.

    private final PaymentType paymentType; // Store the type of payment or reward redemption.
    private final double amount; // Store the monetary amount applied to the transaction.

    /**
     * Creates a payment or redeemed-value record.
     *
     * @param paymentType type of payment or reward redemption
     * @param amount monetary amount of the payment
     * @throws NullPointerException if paymentType is null
     * @throws IllegalArgumentException if amount is not greater than zero
     */
    public Payment(PaymentType paymentType, double amount) { // Create one valid payment record.

        this.paymentType = Objects.requireNonNull( // Require every payment to have a known type.
                paymentType, // Validate the payment type supplied by the caller.
                "Payment type cannot be null." // Explain why construction failed.
        );

        if (!Double.isFinite(amount) || amount <= 0.0) { // Require a finite positive payment amount.
            throw new IllegalArgumentException( // Prevent an invalid payment from being created.
                    "Payment amount must be a finite positive number." // Explain the violated payment rule.
            );
        }

        this.amount = amount; // Store the validated payment amount.
    }

    /**
     * Returns the type of payment or redeemed value.
     *
     * @return payment type
     */
    public PaymentType getPaymentType() { // Provide read-only access to the payment type.
        return paymentType; // Return the stored payment classification.
    }

    /**
     * Returns the monetary amount of the payment.
     *
     * @return payment amount
     */
    public double getAmount() { // Provide read-only access to the payment amount.
        return amount; // Return the stored monetary amount.
    }
}