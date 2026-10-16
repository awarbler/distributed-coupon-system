package coupons.repository;

import java.util.Objects;

/**
 * Identifies the actor requesting receipt creation.
 *
 * <p>Ctx means context. Request identity and timestamp belong
 * to ReceiptWriteRequest. Approval for later changes belongs
 * to ReceiptChangeContext.
 *
 * @param requestedBy stable identifier of the requesting user
 */
public record ReceiptCreateCtx(String requestedBy) {

    /**
     * Validates the requesting user's identifier.
     *
     * @param requestedBy stable identifier of the requesting user
     * @throws NullPointerException if requestedBy is null
     * @throws IllegalArgumentException if requestedBy is blank
     */
    public ReceiptCreateCtx {
        Objects.requireNonNull(
                requestedBy,
                "Requester cannot be null."
        );

        if (requestedBy.isBlank()) {
            throw new IllegalArgumentException(
                    "Requester cannot be blank."
            );
        }
    }
}