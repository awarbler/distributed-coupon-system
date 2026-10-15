package coupons; // Place the Payment class in the coupons package.
import java.math.BigDecimal;
import java.util.Objects; // Provide null checking for required object values.

/**
 * Represents one payment or redeemed-value amount used in a transaction.
 */
public class Payment { // Define the data and rules for one payment record.

    private final PaymentType paymentType; // Store the type of payment or reward redemption.
    private final BigDecimal amount; // Store the monetary amount applied to the transaction.

    /**
     * Creates a payment or redeemed-value record.
     *
     * @param paymentType type of payment or reward redemption
     * @param amount monetary amount of the payment
     * @throws NullPointerException if paymentType is null
     * @throws IllegalArgumentException if amount is not greater than zero
     */
    public Payment(PaymentType paymentType, BigDecimal amount) { // Create one valid payment record.

        this.paymentType = Objects.requireNonNull( // Require every payment to have a known type.
                paymentType, // Validate the payment type supplied by the caller.
                "Payment type cannot be null." // Explain why construction failed.
        );

        Objects.requireNonNull( // Require every payment to have a known amount.
                amount, // Validate the payment amount supplied by the caller.
                "Payment amount cannot be null." // Explain why construction failed.
        );
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException( // Reject any payment that is not greater than zero.
                    "Payment amount must be greater than zero." // Explain why construction failed.
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
    public BigDecimal getAmount() { // Provide read-only access to the payment amount.
        return amount; // Return the stored monetary amount.
    }
}