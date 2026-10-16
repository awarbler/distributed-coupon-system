package coupons.repository;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Identifies one logical receipt-write request.
 *
 * <p>A retry must reuse the original request ID and timestamp.
 * This metadata does not contain the operation's payload or approval.
 *
 * <p>Creating this record does not enforce idempotency.
 * Repository implementations must enforce the write contract atomically.
 *
 * @param requestId stable identifier reused for retries
 * @param requestedAt timestamp of the original request
 */
public record ReceiptWriteRequest(
        UUID requestId,
        Instant requestedAt
) {

    /**
     * Validates required request metadata.
     *
     * @param requestId stable identifier reused for retries
     * @param requestedAt timestamp of the original request
     * @throws NullPointerException if either argument is null
     */
    public ReceiptWriteRequest {
        Objects.requireNonNull(
                requestId,
                "Request ID cannot be null."
        );

        Objects.requireNonNull(
                requestedAt,
                "Request timestamp cannot be null."
        );
    }
}