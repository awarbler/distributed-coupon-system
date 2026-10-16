package coupons.repository;

import java.time.Instant;
import java.util.Objects;

/**
 * Carries information about an approved inventory adjustment.
 *
 * <p>The application service verifies approval applies to the proposed
 * quantity and expected revision. This record validates required data only.
 *
 * @param requestedBy requesting user's stable identifier
 * @param reason explanation for the adjustment
 * @param approvedBy approving user's stable identifier
 * @param approvedAt recorded approval time
 */
public record InventoryAdjustCtx(
        String requestedBy,
        String reason,
        String approvedBy,
        Instant approvedAt
) {
    /**
     * Validates adjustment information without modifying it.
     *
     * @param requestedBy requesting user identifier
     * @param reason adjustment explanation
     * @param approvedBy approving user identifier
     * @param approvedAt approval time
     * @throws NullPointerException if any argument is null
     * @throws IllegalArgumentException if a text field is blank
     */
    public InventoryAdjustCtx {
        Objects.requireNonNull(requestedBy, "Requester cannot be null.");
        Objects.requireNonNull(reason, "Reason cannot be null.");
        Objects.requireNonNull(approvedBy, "Approver cannot be null.");
        Objects.requireNonNull(approvedAt, "Approval time cannot be null.");
        if (requestedBy.isBlank() || reason.isBlank() || approvedBy.isBlank()) {
            throw new IllegalArgumentException(
                    "Requester, reason, and approver cannot be blank.");
        }
    }
}
