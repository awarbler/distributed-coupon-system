package coupons.repository;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Describes one committed receipt operation.
 *
 * <p>A matching successful retry returns these original values.
 * This result contains no mutable transaction data.
 *
 * @param requestId identifier of the committed request
 * @param transactionId stable identifier of the receipt
 * @param revision receipt revision produced by the operation
 * @param action committed operation
 * @param status receipt status immediately after the operation
 * @param committedAt timestamp assigned when the operation committed
 */
public record ReceiptWriteResult(
        UUID requestId,
        UUID transactionId,
        long revision,
        ReceiptChangeAction action,
        ReceiptStatus status,
        Instant committedAt
) implements java.io.Serializable {

    /**
     * Validates committed-result fields and lifecycle consistency.
     *
     * @param requestId identifier of the committed request
     * @param transactionId stable identifier of the receipt
     * @param revision committed receipt revision
     * @param action committed operation
     * @param status resulting receipt status
     * @param committedAt commit timestamp
     * @throws NullPointerException if a required reference is null
     * @throws IllegalArgumentException if revision or lifecycle
     *         information is inconsistent
     */
    public ReceiptWriteResult {
        Objects.requireNonNull(requestId, "Request ID cannot be null.");
        Objects.requireNonNull(transactionId, "Transaction ID cannot be null.");
        Objects.requireNonNull(action, "Action cannot be null.");
        Objects.requireNonNull(status, "Status cannot be null.");
        Objects.requireNonNull(committedAt, "Commit timestamp cannot be null.");

        if (revision < 1) {
            throw new IllegalArgumentException(
                    "Revision must be at least 1."
            );
        }

        if (action == ReceiptChangeAction.CREATED && revision != 1) {
            throw new IllegalArgumentException(
                    "Creation must produce revision 1."
            );
        }

        if (action != ReceiptChangeAction.CREATED && revision < 2) {
            throw new IllegalArgumentException(
                    "Changes to an existing receipt require revision 2 or later."
            );
        }

        ReceiptStatus expectedStatus =
                action == ReceiptChangeAction.DELETED
                        ? ReceiptStatus.DELETED
                        : ReceiptStatus.ACTIVE;

        if (status != expectedStatus) {
            throw new IllegalArgumentException(
                    "Receipt status does not match the committed action."
            );
        }
    }
}