package coupons;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class CouponAppliedTest {

    @Test
    void recordsTotalDiscountForOneLineContainingTwoUnits() {
        PurchaseItem scott = new PurchaseItem(
                "Scott", "TEST-SCOTT",
                new BigDecimal("5.00"),
                new BigDecimal("5.00"),
                2
        );

        CouponApplied applied = new CouponApplied(
                "STR IVC 2 @ -1.25",
                new BigDecimal("2.50"),
                List.of(scott.getLineItemId())
        );

        assertNotNull(applied.getApplicationId());
        assertEquals(2, scott.getQuantity());
        assertEquals(
                new BigDecimal("2.50"),
                applied.getAppliedAmount()
        );
        assertEquals(
                List.of(scott.getLineItemId()),
                applied.getLineItemIds()
        );
    }

    @Test
    void preservesPrintedLabelExactly() {
        String label = "  MFG COUPON  ";

        CouponApplied applied = new CouponApplied(
                label,
                new BigDecimal("2.00"),
                List.of()
        );

        assertEquals(label, applied.getPrintedLabel());
    }

    @Test
    void allowsUnknownItemAssociations() {
        CouponApplied applied = new CouponApplied(
                "MFG COUPON",
                new BigDecimal("2.00"),
                List.of()
        );

        assertTrue(applied.getLineItemIds().isEmpty());
    }

    @Test
    void rejectsZeroAndNegativeAmounts() {
        for (String amount : List.of("0.00", "-2.00")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new CouponApplied(
                            "MFG COUPON",
                            new BigDecimal(amount),
                            List.of()
                    )
            );
        }
    }

    @Test
    void rejectsNullRequiredArguments() {
        assertThrows(
                NullPointerException.class,
                () -> new CouponApplied(
                        null,
                        "MFG COUPON",
                        new BigDecimal("2.00"),
                        List.of()
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> new CouponApplied(
                        (String) null,
                        new BigDecimal("2.00"),
                        List.of()
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> new CouponApplied(
                        "MFG COUPON",
                        null,
                        List.of()
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> new CouponApplied(
                        "MFG COUPON",
                        new BigDecimal("2.00"),
                        null
                )
        );
    }

    @Test
    void rejectsBlankLabels() {
        for (String label : List.of("", "   ")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new CouponApplied(
                            label,
                            new BigDecimal("2.00"),
                            List.of()
                    )
            );
        }
    }

    @Test
    void rejectsNullElementsAndDuplicateLineIds() {
        UUID id = UUID.randomUUID();

        assertThrows(
                NullPointerException.class,
                () -> new CouponApplied(
                        "STORE IVC",
                        new BigDecimal("2.50"),
                        Arrays.asList(id, null)
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new CouponApplied(
                        "STORE IVC",
                        new BigDecimal("2.50"),
                        List.of(id, id)
                )
        );
    }

    @Test
    void defensivelyCopiesLineIdsAndReturnsUnmodifiableList() {
        UUID id = UUID.randomUUID();
        List<UUID> suppliedIds = new ArrayList<>();
        suppliedIds.add(id);

        CouponApplied applied = new CouponApplied(
                "STORE IVC",
                new BigDecimal("2.50"),
                suppliedIds
        );

        suppliedIds.clear();

        assertEquals(List.of(id), applied.getLineItemIds());

        assertThrows(
                UnsupportedOperationException.class,
                () -> applied.getLineItemIds().clear()
        );
    }

    @Test
    void correctionPreservesIdWithoutChangingOriginal() {
        UUID applicationId = UUID.randomUUID();

        CouponApplied original = new CouponApplied(
                applicationId,
                "MFG COUPON",
                new BigDecimal("2.00"),
                List.of()
        );

        CouponApplied corrected = new CouponApplied(
                original.getApplicationId(),
                original.getPrintedLabel(),
                new BigDecimal("3.00"),
                original.getLineItemIds()
        );

        assertNotSame(original, corrected);
        assertEquals(applicationId, original.getApplicationId());
        assertEquals(applicationId, corrected.getApplicationId());
        assertEquals(
                new BigDecimal("2.00"),
                original.getAppliedAmount()
        );
        assertEquals(
                new BigDecimal("3.00"),
                corrected.getAppliedAmount()
        );
    }

    @Test
    void recordsTwoDollarDiscountForOneLineContainingTwoUnits() {
        PurchaseItem scott = new PurchaseItem(
                "Scott", "TEST-SCOTT",
                new BigDecimal("5.00"),
                new BigDecimal("5.00"),
                2
        );

        CouponApplied applied = new CouponApplied(
                "MFG COUPON",
                new BigDecimal("2.00"),
                List.of(scott.getLineItemId())
        );

        assertEquals(2, scott.getQuantity());
        assertEquals(
                new BigDecimal("2.00"),
                applied.getAppliedAmount()
        );
        assertEquals(
                List.of(scott.getLineItemId()),
                applied.getLineItemIds()
        );
    }
}
