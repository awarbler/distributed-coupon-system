package coupons.repository;

import coupons.receipts.Payment;
import coupons.receipts.PaymentType;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Preserves a payment's recorded type and amount.
 *
 * <p>This snapshot does not reclassify legacy payment entries.
 *
 * @param paymentId stable underlying event identifier
 * @param paymentType recorded payment classification
 * @param amount recorded payment amount
 */
public record PaymentSnap(
        UUID paymentId,
        PaymentType paymentType,
        BigDecimal amount,
        String notes, String accountName
) implements java.io.Serializable {

    public PaymentSnap(UUID paymentId, PaymentType paymentType, BigDecimal amount, String notes) {
        this(paymentId, paymentType, amount, notes, null);
    }
    /** Compatibility constructor for payments without notes. */
    public PaymentSnap(UUID paymentId, PaymentType paymentType, BigDecimal amount) {
        this(paymentId, paymentType, amount, null);
    }

    /** Creates a newly identified snapshot; restoration must supply its existing ID. */
    public PaymentSnap(PaymentType paymentType, BigDecimal amount) {
        this(UUID.randomUUID(), paymentType, amount);
    }

    /**
     * Validates payment snapshot values without changing them.
     *
     * @param paymentType recorded payment classification
     * @param amount recorded payment amount
     * @throws NullPointerException if either argument is null
     * @throws IllegalArgumentException if amount is not positive
     */
    public PaymentSnap {
        if (accountName != null && (accountName.isBlank() || paymentType != PaymentType.GIFT_CARD && paymentType != PaymentType.OTHER))
            throw new IllegalArgumentException("Named account requires gift-card or other tender.");
        if (notes != null && notes.isBlank()) throw new IllegalArgumentException("Notes must be nonblank or unknown.");
        Objects.requireNonNull(paymentId, "Payment ID cannot be null.");
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
                payment.getPaymentId(),
                payment.getPaymentType(),
                payment.getAmount(),
                payment.getNotes(), payment.getAccountName()
        );
    }
}
