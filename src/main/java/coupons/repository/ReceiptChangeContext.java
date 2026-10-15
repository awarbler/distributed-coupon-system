package coupons.repository;

import java.time.Instant;
import java.util.Objects;

/**
 * Carries information about an approved receipt change.
 *
 * <p>The application service must verify that approval applies
 * to the proposed change and expected receipt revision.
 * This record validates required data; it does not verify approval.
 */
public record ReceiptChangeContext(
        String requestedBy,
        String reason,
        String approvedBy,
        Instant approvedAt
) {

    /**
     * Validates required change and approval information.
     *
     * @throws NullPointerException if any field is null
     * @throws IllegalArgumentException if a text field is blank
     */
    public ReceiptChangeContext {
        Objects.requireNonNull(requestedBy, "Requester cannot be null.");
        Objects.requireNonNull(reason, "Change reason cannot be null.");
        Objects.requireNonNull(approvedBy, "Approver cannot be null.");
        Objects.requireNonNull(approvedAt, "Approval time cannot be null.");

        if (requestedBy.isBlank()) {
            throw new IllegalArgumentException("Requester cannot be blank.");
        }

        if (reason.isBlank()) {
            throw new IllegalArgumentException("Change reason cannot be blank.");
        }

        if (approvedBy.isBlank()) {
            throw new IllegalArgumentException("Approver cannot be blank.");
        }
    }
}