package coupons.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import coupons.Promotion;
import coupons.PromotionType;
import coupons.RewardType;

/**
 * Tests capture of reward-earning promotion terms.
 */
class PromotionSnapTest {

    /**
     * Verifies preservation of spending rules and reward values.
     */
    @Test
    void capturesSpendPromotionFields() {
        Promotion promotion = new Promotion(
                "Example spend offer",
                PromotionType.SPEND,
                20.00,
                RewardType.WALGREENS_CASH,
                5.00,
                true,
                false
        );

        PromotionSnap snapshot = PromotionSnap.from(promotion);

        assertEquals("Example spend offer", snapshot.description());
        assertEquals(PromotionType.SPEND, snapshot.promotionType());
        assertEquals(20.00, snapshot.qualificationThreshold());
        assertEquals(RewardType.WALGREENS_CASH, snapshot.rewardType());
        assertEquals(5.00, snapshot.rewardAmount());
        assertTrue(snapshot.walgreensCashReducesQualifyingSpend());
        assertFalse(snapshot.couponsReduceQualifyingSpend());
    }

    /**
     * Verifies capture of quantity-based promotion rules.
     */
    @Test
    void capturesBuyPromotionFields() {
        PromotionSnap snapshot = PromotionSnap.from(new Promotion(
                "Example buy-two offer",
                PromotionType.BUY,
                2,
                RewardType.REGISTER_REWARD,
                5.00,
                false,
                true
        ));

        assertEquals(PromotionType.BUY, snapshot.promotionType());
        assertEquals(2.0, snapshot.qualificationThreshold());
        assertEquals(RewardType.REGISTER_REWARD, snapshot.rewardType());
        assertFalse(snapshot.walgreensCashReducesQualifyingSpend());
        assertTrue(snapshot.couponsReduceQualifyingSpend());
    }

    /**
     * Verifies that separate offer versions preserve separate values.
     */
    @Test
    void preservesOriginalAndCorrectedValues() {
        PromotionSnap before = PromotionSnap.from(new Promotion(
                "Example offer",
                PromotionType.SPEND,
                20.00,
                RewardType.WALGREENS_CASH,
                5.00,
                true,
                false
        ));

        PromotionSnap after = PromotionSnap.from(new Promotion(
                "Example offer",
                PromotionType.SPEND,
                25.00,
                RewardType.WALGREENS_CASH,
                5.00,
                true,
                false
        ));

        assertEquals(20.00, before.qualificationThreshold());
        assertEquals(25.00, after.qualificationThreshold());
        assertNotEquals(before, after);
    }

    /**
     * Verifies rejection of missing source and required references.
     */
    @Test
    void rejectsNullValues() {
        assertThrows(
                NullPointerException.class,
                () -> PromotionSnap.from(null)
        );

        assertThrows(
                NullPointerException.class,
                () -> new PromotionSnap(
                        null, PromotionType.SPEND, 20,
                        RewardType.WALGREENS_CASH, 5, false, false
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> new PromotionSnap(
                        "Example", null, 20,
                        RewardType.WALGREENS_CASH, 5, false, false
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> new PromotionSnap(
                        "Example", PromotionType.SPEND, 20,
                        null, 5, false, false
                )
        );
    }

    /**
     * Verifies rejection of blank descriptions.
     */
    @Test
    void rejectsBlankDescription() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PromotionSnap(
                        "   ", PromotionType.SPEND, 20,
                        RewardType.WALGREENS_CASH, 5, false, false
                )
        );
    }

    /**
     * Verifies rejection of invalid thresholds and reward amounts.
     */
    @Test
    void rejectsInvalidNumericValues() {
        double[] invalidValues = {
                0.0,
                -1.0,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        };

        for (double invalid : invalidValues) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new PromotionSnap(
                            "Example", PromotionType.SPEND, invalid,
                            RewardType.WALGREENS_CASH, 5, false, false
                    )
            );

            assertThrows(
                    IllegalArgumentException.class,
                    () -> new PromotionSnap(
                            "Example", PromotionType.SPEND, 20,
                            RewardType.WALGREENS_CASH, invalid, false, false
                    )
            );
        }
    }
}