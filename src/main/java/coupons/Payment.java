package coupons; // Place the Payment class in the coupons package.
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID; // Provide null checking for required object values.

/**
 * Represents one payment or redeemed-value amount used in a transaction.
 */
public class Payment { // Define the data and rules for one payment record.

    /** Stable identity of this payment event, preserved during reconstruction. */
    private final UUID id;
    private final PaymentType paymentType; // Store the type of payment or reward redemption.
    private final String notes;
    private final String accountName;
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
        this(UUID.randomUUID(), paymentType, amount);
    }

    /**
     * Reconstructs a payment using its existing event identity.
     * @param paymentId stable payment identifier
     * @param paymentType recorded payment classification
     * @param amount positive recorded payment amount
     */
    public Payment(UUID paymentId, PaymentType paymentType, BigDecimal amount) {
        this(paymentId, paymentType, amount, null);
    }

    /** Restores the event and its recorded explanation without changing its identity. */
    public Payment(UUID paymentId, PaymentType paymentType, BigDecimal amount, String notes) {
        this(paymentId, paymentType, amount, notes, null);
    }

    public Payment(UUID paymentId, PaymentType paymentType, BigDecimal amount, String notes, String accountName) {
        if (accountName != null && (accountName.isBlank() || paymentType != PaymentType.GIFT_CARD && paymentType != PaymentType.OTHER))
            throw new IllegalArgumentException("A named redeemed account requires gift-card or other tender.");
        this.accountName = accountName;
        if (notes != null && notes.isBlank()) throw new IllegalArgumentException("Notes must be nonblank or unknown.");
        this.notes = notes;
        this.id = Objects.requireNonNull(paymentId, "Payment ID cannot be null.");

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

    /** Recorded tender or redemption explanation; null means unknown. */
    public String getNotes() { return notes; }
    public String getAccountName() { return accountName; }

    /** Returns the stable payment event identifier. */
    public UUID getPaymentId() {
        return id;
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
