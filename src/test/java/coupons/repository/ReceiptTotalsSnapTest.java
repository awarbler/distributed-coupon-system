package coupons.repository;

import coupons.receipts.ReceiptTotals;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests preservation of retailer-reported receipt totals.
 */
class ReceiptTotalsSnapTest {

    /**
     * Verifies exact capture without replacing the reported total.
     */
    @Test
    void preservesReportedValuesAndScale() {
        ReceiptTotalsSnap snapshot = ReceiptTotalsSnap.from(
                new ReceiptTotals(
                        new BigDecimal("1.310"),
                        new BigDecimal("7.280")
                )
        );

        assertEquals(new BigDecimal("1.310"), snapshot.taxAmount());
        assertEquals(new BigDecimal("7.280"), snapshot.finalTotal());
    }

    /**
     * Verifies that zero amounts are valid.
     */
    @Test
    void acceptsZeroTotals() {
        assertDoesNotThrow(
                () -> new ReceiptTotalsSnap(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                )
        );
    }

    /**
     * Verifies that original and corrected totals remain separate.
     */
    @Test
    void preservesOriginalAndCorrectedValues() {
        ReceiptTotalsSnap before = ReceiptTotalsSnap.from(
                new ReceiptTotals(
                        new BigDecimal("1.31"),
                        new BigDecimal("7.28")
                )
        );

        ReceiptTotalsSnap after = ReceiptTotalsSnap.from(
                new ReceiptTotals(
                        new BigDecimal("1.31"),
                        new BigDecimal("7.29")
                )
        );

        assertEquals(new BigDecimal("7.28"), before.finalTotal());
        assertEquals(new BigDecimal("7.29"), after.finalTotal());
        assertNotEquals(before, after);
    }

    /**
     * Verifies rejection of missing source or amount values.
     */
    @Test
    void rejectsNullValues() {
        assertThrows(
                NullPointerException.class,
                () -> ReceiptTotalsSnap.from(null)
        );

        assertThrows(
                NullPointerException.class,
                () -> new ReceiptTotalsSnap(null, BigDecimal.ZERO)
        );

        assertThrows(
                NullPointerException.class,
                () -> new ReceiptTotalsSnap(BigDecimal.ZERO, null)
        );
    }

    /**
     * Verifies rejection of negative tax or final totals.
     */
    @Test
    void rejectsNegativeAmounts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ReceiptTotalsSnap(
                        new BigDecimal("-0.01"),
                        BigDecimal.ZERO
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new ReceiptTotalsSnap(
                        BigDecimal.ZERO,
                        new BigDecimal("-0.01")
                )
        );
    }
}
