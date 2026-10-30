package coupons.repository;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import coupons.receipts.Payment;
import coupons.receipts.PaymentType;

/**
 * Tests immutable capture of payment values.
 */
class PaymentSnapTest {

    /**
     * Verifies exact preservation of type, amount, and monetary scale.
     */
    @Test
    void capturesPaymentValuesExactly() {
        Payment payment = new Payment(
                PaymentType.WALGREENS_CASH,
                new BigDecimal("10.000")
        );

        PaymentSnap snapshot = PaymentSnap.from(payment);

        assertEquals(PaymentType.WALGREENS_CASH, snapshot.paymentType());
        assertEquals(new BigDecimal("10.000"), snapshot.amount());
    }

    /**
     * Verifies that historical classifications are not silently changed.
     */
    @Test
    void preservesLegacyRegisterRewardClassification() {
        PaymentSnap snapshot = PaymentSnap.from(new Payment(
                PaymentType.REGISTER_REWARD,
                new BigDecimal("5.00")
        ));

        assertEquals(PaymentType.REGISTER_REWARD, snapshot.paymentType());
        assertEquals(new BigDecimal("5.00"), snapshot.amount());
    }

    /**
     * Verifies that original and corrected amounts remain distinct.
     */
    @Test
    void preservesSeparateOriginalAndCorrectedValues() {
        PaymentSnap before = PaymentSnap.from(new Payment(
                PaymentType.CASH,
                new BigDecimal("3.60")
        ));

        PaymentSnap after = PaymentSnap.from(new Payment(
                PaymentType.CASH,
                new BigDecimal("3.61")
        ));

        assertEquals(new BigDecimal("3.60"), before.amount());
        assertEquals(new BigDecimal("3.61"), after.amount());
        assertNotEquals(before, after);
    }

    /**
     * Verifies rejection of missing source and component values.
     */
    @Test
    void rejectsNullValues() {
        assertThrows(
                NullPointerException.class,
                () -> PaymentSnap.from(null)
        );

        assertThrows(
                NullPointerException.class,
                () -> new PaymentSnap(null, new BigDecimal("1.00"))
        );

        assertThrows(
                NullPointerException.class,
                () -> new PaymentSnap(PaymentType.CASH, null)
        );
    }

    /**
     * Verifies rejection of zero and negative amounts.
     */
    @Test
    void rejectsNonpositiveAmounts() {
        for (String amount : List.of("0.00", "-1.00")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new PaymentSnap(
                            PaymentType.CASH,
                            new BigDecimal(amount)
                    )
            );
        }
    }
}