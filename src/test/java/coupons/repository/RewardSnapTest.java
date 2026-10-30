package coupons.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import coupons.rewards.Reward;

/**
 * Tests preservation of legacy generic reward values.
 */
class RewardSnapTest {

    /**
     * Verifies capture of all existing reward fields.
     */
    @Test
    void capturesRecordedValues() {
        RewardSnap snapshot = RewardSnap.from(
                new Reward("Example source", 5.25, true)
        );

        assertEquals("Example source", snapshot.source());
        assertEquals(5.25, snapshot.amount());
        assertTrue(snapshot.cashEquivalent());
    }

    /**
     * Verifies preservation of non-cash classification.
     */
    @Test
    void preservesNonCashClassification() {
        RewardSnap snapshot = RewardSnap.from(
                new Reward("Example source", 5.00, false)
        );

        assertFalse(snapshot.cashEquivalent());
    }

    /**
     * Verifies that snapshotting does not introduce legacy-data validation.
     */
    @Test
    void preservesValuesAllowedByLegacyModel() {
        RewardSnap snapshot = RewardSnap.from(
                new Reward(null, -1.00, false)
        );

        assertNull(snapshot.source());
        assertEquals(-1.00, snapshot.amount());
        assertFalse(snapshot.cashEquivalent());
    }

    /**
     * Verifies that a non-finite legacy value is preserved for review.
     */
    @Test
    void preservesNonFiniteLegacyAmount() {
        RewardSnap snapshot = RewardSnap.from(
                new Reward("Legacy entry", Double.NaN, false)
        );

        assertTrue(Double.isNaN(snapshot.amount()));
    }

    /**
     * Verifies separate capture of original and corrected values.
     */
    @Test
    void preservesOriginalAndCorrectedValues() {
        RewardSnap before = RewardSnap.from(
                new Reward("Example source", 5.00, false)
        );

        RewardSnap after = RewardSnap.from(
                new Reward("Example source", 6.00, false)
        );

        assertEquals(5.00, before.amount());
        assertEquals(6.00, after.amount());
        assertNotEquals(before, after);
    }

    /**
     * Verifies rejection of a missing source object.
     */
    @Test
    void rejectsNullReward() {
        assertThrows(
                NullPointerException.class,
                () -> RewardSnap.from(null)
        );
    }
}