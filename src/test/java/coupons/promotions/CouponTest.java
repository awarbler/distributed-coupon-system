package coupons.promotions;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;


public class CouponTest {
    @Test 
    void rejectZeroAndNegativeDiscounts() {
        for (String amount: new String[] {"0.00", "-2.00"}) {
            assertThrows(
                IllegalArgumentException.class, 
                () -> new Coupon(
                        "Manufacturer Coupon",
                        new BigDecimal(amount),
                        2,
                        CouponType.MANUFACTURER,
                        null
                )
            );
        }
    }

    @Test 
    void preservesPositiveDiscountAndUnknownExpiration() {
        Coupon coupon = new Coupon(
                "$2.00 off 2",
                new BigDecimal("2.00"),
                2,
                CouponType.MANUFACTURER,
                null
        );
        assertEquals(new BigDecimal("2.00"), coupon.getDiscountAmount());
        assertNull(coupon.getExpirationDate());
    }
    
}
