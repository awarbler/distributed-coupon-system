package coupons.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import coupons.promotions.Coupon;
import coupons.promotions.CouponType;

/**
 * Tests immutable capture of coupon-offer values.
 */
class CouponSnapTest {

    /**
     * Verifies preservation of all supported offer fields.
     */
    @Test
    void capturesOfferValuesExactly() {
        LocalDate expiration = LocalDate.of(2026, 10, 31);

        Coupon coupon = new Coupon(
                "$2 off two",
                new BigDecimal("2.000"),
                2,
                CouponType.MANUFACTURER,
                expiration
        );

        CouponSnap snapshot = CouponSnap.from(coupon);

        assertEquals("$2 off two", snapshot.description());
        assertEquals(new BigDecimal("2.000"), snapshot.discountAmount());
        assertEquals(2, snapshot.requiredQuantity());
        assertEquals(CouponType.MANUFACTURER, snapshot.couponType());
        assertEquals(expiration, snapshot.expirationDate());
    }

    /**
     * Verifies that an unknown expiration stays unknown.
     */
    @Test
    void preservesUnknownExpiration() {
        CouponSnap snapshot = CouponSnap.from(new Coupon(
                "Store offer",
                new BigDecimal("1.25"),
                1,
                CouponType.STORE,
                null
        ));

        assertNull(snapshot.expirationDate());
    }

    /**
     * Verifies that separate captures preserve original and corrected values.
     */
    @Test
    void preservesOriginalAndCorrectedAmounts() {
        CouponSnap before = CouponSnap.from(new Coupon(
                "Example offer",
                new BigDecimal("2.00"),
                2,
                CouponType.MANUFACTURER,
                null
        ));

        CouponSnap after = CouponSnap.from(new Coupon(
                "Example offer",
                new BigDecimal("3.00"),
                2,
                CouponType.MANUFACTURER,
                null
        ));

        assertEquals(new BigDecimal("2.00"), before.discountAmount());
        assertEquals(new BigDecimal("3.00"), after.discountAmount());
        assertNotEquals(before, after);
    }

    /**
     * Verifies rejection of a missing source offer.
     */
    @Test
    void rejectsNullSource() {
        assertThrows(
                NullPointerException.class,
                () -> CouponSnap.from(null)
        );
    }

    /**
     * Verifies rejection of nonpositive snapshot discounts.
     */
    @Test
    void rejectsNonpositiveDiscounts() {
        for (String amount : List.of("0.00", "-2.00")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new CouponSnap(
                            "Example offer",
                            new BigDecimal(amount),
                            1,
                            CouponType.STORE,
                            null
                    )
            );
        }
    }
}