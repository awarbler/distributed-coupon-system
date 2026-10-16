package coupons.repository;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Tests inventory adjustment context validation. */
class InventoryAdjustCtxTest {
    /** Verifies preservation of actor, reason, and approval information. */
    @Test
    void preservesRecordedContext() {
        Instant time = Instant.parse("2026-10-16T15:00:00Z");
        InventoryAdjustCtx context = new InventoryAdjustCtx(
                "user-123", "Physical inventory correction", "user-123", time);
        assertEquals("user-123", context.requestedBy());
        assertEquals("Physical inventory correction", context.reason());
        assertEquals("user-123", context.approvedBy());
        assertEquals(time, context.approvedAt());
    }

    /** Verifies rejection of missing required information. */
    @Test
    void rejectsNullFields() {
        Instant time = Instant.parse("2026-10-16T15:00:00Z");
        assertThrows(NullPointerException.class, () -> new InventoryAdjustCtx(null, "Correction", "user-123", time));
        assertThrows(NullPointerException.class, () -> new InventoryAdjustCtx("user-123", null, "user-123", time));
        assertThrows(NullPointerException.class, () -> new InventoryAdjustCtx("user-123", "Correction", null, time));
        assertThrows(NullPointerException.class, () -> new InventoryAdjustCtx("user-123", "Correction", "user-123", null));
    }

    /** Verifies rejection of blank actor, reason, or approver. */
    @Test
    void rejectsBlankText() {
        Instant time = Instant.parse("2026-10-16T15:00:00Z");
        for (String blank : new String[]{"", "   "}) {
            assertThrows(IllegalArgumentException.class, () -> new InventoryAdjustCtx(blank, "Correction", "user-123", time));
            assertThrows(IllegalArgumentException.class, () -> new InventoryAdjustCtx("user-123", blank, "user-123", time));
            assertThrows(IllegalArgumentException.class, () -> new InventoryAdjustCtx("user-123", "Correction", blank, time));
        }
    }
}
