package coupons.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

/**
 * Tests receipt-creation actor information.
 */
class ReceiptCreateCtxTest {

    /**
     * Verifies that the supplied user identifier is preserved.
     */
    @Test
    void preservesRequester() {
        ReceiptCreateCtx context = new ReceiptCreateCtx("user-123");

        assertEquals("user-123", context.requestedBy());
    }

    /**
     * Verifies that missing actor information is rejected.
     */
    @Test
    void rejectsNullRequester() {
        assertThrows(
                NullPointerException.class,
                () -> new ReceiptCreateCtx(null)
        );
    }

    /**
     * Verifies that empty or whitespace-only identifiers are rejected.
     */
    @Test
    void rejectsBlankRequester() {
        for (String requester : new String[]{"", "   "}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new ReceiptCreateCtx(requester)
            );
        }
    }
}
