package coupons.repository;

import coupons.Payment;
import coupons.PaymentType;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Preserves a payment's recorded type and amount.
 *
 * <p>This snapshot does not reclassify legacy payment entries.
 *
 * @param paymentType recorded payment classification
 * @param amount recorded payment amount
 */
public record PaymentSnap(
        PaymentType paymentType,
        BigDecimal amount
) {

    /**
     * Validates payment snapshot values without changing them.
     *
     * @param paymentType recorded payment classification
     * @param amount recorded payment amount
     * @throws NullPointerException if either argument is null
     * @throws IllegalArgumentException if amount is not positive
     */
    public PaymentSnap {
        Objects.requireNonNull(
                paymentType,
                "Payment type cannot be null."
        );
        Objects.requireNonNull(amount, "Payment amount cannot be null.");

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero."
            );
        }
    }

    /**
     * Captures a payment's current values.
     *
     * @param payment payment to capture
     * @return an immutable snapshot
     * @throws NullPointerException if payment is null
     */
    public static PaymentSnap from(Payment payment) {
        Objects.requireNonNull(payment, "Payment cannot be null.");

        return new PaymentSnap(
                payment.getPaymentType(),
                payment.getAmount()
        );
    }
}