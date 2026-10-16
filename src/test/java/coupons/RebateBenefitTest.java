package coupons;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Tests distinct benefit forms and exact denomination preservation. */
class RebateBenefitTest {
    /** Verifies cash and gift-card face values remain distinct. */
    @Test
    void distinguishesCashFromGiftCardValue() {
        Currency usd = Currency.getInstance("USD");
        RebateBenefit cash = new RebateBenefit(BenefitKind.CASH, new BigDecimal("5.00"), usd, null);
        RebateBenefit giftCard = new RebateBenefit(BenefitKind.GIFT_CARD,
                new BigDecimal("5.00"), usd, "Example retailer gift card");
        assertNotEquals(cash, giftCard);
        assertEquals(new BigDecimal("5.00"), giftCard.amount());
        assertEquals(usd, giftCard.currency());
        assertEquals("Example retailer gift card", giftCard.program());
    }

    /** Verifies points retain their program without an assumed cash value. */
    @Test
    void recordsPointsInTheirOwnProgram() {
        RebateBenefit points = new RebateBenefit(BenefitKind.POINTS,
                new BigDecimal("500"), null, "Example points program");
        assertEquals(new BigDecimal("500"), points.amount());
        assertNull(points.currency());
        assertEquals("Example points program", points.program());
    }

    /** Verifies recorded decimal scale and program text are unchanged. */
    @Test
    void preservesRecordedValuesExactly() {
        RebateBenefit benefit = new RebateBenefit(BenefitKind.GIFT_CARD,
                new BigDecimal("5.000"), Currency.getInstance("USD"), " Example program ");
        assertEquals(new BigDecimal("5.000"), benefit.amount());
        assertEquals(" Example program ", benefit.program());
    }

    /** Verifies currency amounts are not silently rounded to settlement precision. */
    @Test
    void preservesHighPrecisionMonetaryInterpretation() {
        RebateBenefit benefit = new RebateBenefit(BenefitKind.CASH,
                new BigDecimal("5.12345"), Currency.getInstance("USD"), null);
        assertEquals(new BigDecimal("5.12345"), benefit.amount());
        assertEquals(5, benefit.amount().scale());
    }

    /** Verifies required form and amount cannot be missing. */
    @Test
    void rejectsNullRequiredFields() {
        assertThrows(NullPointerException.class, () -> new RebateBenefit(
                null, BigDecimal.ONE, Currency.getInstance("USD"), null));
        assertThrows(NullPointerException.class, () -> new RebateBenefit(
                BenefitKind.CASH, null, Currency.getInstance("USD"), null));
    }

    /** Verifies invalid amounts and denomination combinations are rejected. */
    @Test
    void rejectsInvalidBenefitValues() {
        Currency usd = Currency.getInstance("USD");
        for (String amount : new String[]{"0", "-1"}) {
            assertThrows(IllegalArgumentException.class, () -> new RebateBenefit(
                    BenefitKind.CASH, new BigDecimal(amount), usd, null));
        }
        assertThrows(IllegalArgumentException.class, () -> new RebateBenefit(
                BenefitKind.CASH, BigDecimal.ONE, null, null));
        assertThrows(IllegalArgumentException.class, () -> new RebateBenefit(
                BenefitKind.POINTS, BigDecimal.ONE, null, null));
        assertThrows(IllegalArgumentException.class, () -> new RebateBenefit(
                BenefitKind.POINTS, BigDecimal.ONE, usd, "Program"));
        assertThrows(IllegalArgumentException.class, () -> new RebateBenefit(
                BenefitKind.GIFT_CARD, BigDecimal.ONE, usd, null));
        assertThrows(IllegalArgumentException.class, () -> new RebateBenefit(
                BenefitKind.GIFT_CARD, BigDecimal.ONE, usd, "   "));
    }
}
