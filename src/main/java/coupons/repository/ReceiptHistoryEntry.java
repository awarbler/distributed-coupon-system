package coupons.repository;

import java.util.Objects;
import java.util.UUID;

/**
 * Preserves one committed receipt change and its supporting context.
 *
 * <p>The resulting revision orders entries within a receipt.
 * The repository must enforce history uniqueness and continuity.
 *
 * @param historyId stable identifier of this history entry
 * @param request original write-request metadata
 * @param result original committed write result
 * @param before previous revision, null only for creation
 * @param after resulting revision
 * @param createCtx creation context, required only for creation
 * @param changeCtx approval context, required for subsequent changes
 */
public record ReceiptHistoryEntry(
        UUID historyId,
        ReceiptWriteRequest request,
        ReceiptWriteResult result,
        ReceiptRevSnap before,
        ReceiptRevSnap after,
        ReceiptCreateCtx createCtx,
        ReceiptChangeContext changeCtx
) implements java.io.Serializable {
    /**
     * Validates identity, revision, lifecycle, and context relationships.
     *
     * <p>Validation does not prove user approval or persistence occurred.
     *
     * @param historyId stable history-entry identifier
     * @param request original request metadata
     * @param result committed write result
     * @param before previous revision, null only for creation
     * @param after resulting revision
     * @param createCtx creation context, otherwise null
     * @param changeCtx approved-change context, otherwise null
     * @throws NullPointerException if required data is missing
     * @throws IllegalArgumentException if relationships, transitions,
     *         or context combinations are inconsistent
     */
    public ReceiptHistoryEntry {
        Objects.requireNonNull(historyId, "History ID cannot be null.");
        Objects.requireNonNull(request, "Request cannot be null.");
        Objects.requireNonNull(result, "Result cannot be null.");
        Objects.requireNonNull(after, "After snapshot cannot be null.");

        if (!request.requestId().equals(result.requestId())) {
            throw new IllegalArgumentException("Request and result IDs must match.");
        }
        if (!result.transactionId().equals(after.transactionId())
                || result.revision() != after.revision()
                || result.status() != after.status()) {
            throw new IllegalArgumentException(
                    "Result must match the resulting receipt revision.");
        }

        if (result.action() == ReceiptChangeAction.CREATED) {
            Objects.requireNonNull(createCtx, "Creation context is required.");
            if (before != null || changeCtx != null) {
                throw new IllegalArgumentException(
                        "Creation cannot have a previous revision or change context.");
            }
        } else {
            Objects.requireNonNull(before, "Previous revision is required.");
            Objects.requireNonNull(changeCtx, "Approved-change context is required.");
            if (createCtx != null) {
                throw new IllegalArgumentException(
                        "Existing receipt changes cannot have creation context.");
            }
            if (!before.transactionId().equals(after.transactionId())) {
                throw new IllegalArgumentException("Receipt identity cannot change.");
            }
            if (before.revision() >= after.revision()
                    || after.revision() - before.revision() != 1) {
                throw new IllegalArgumentException(
                        "Receipt revision must increase by exactly one.");
            }
            ReceiptStatus expectedBefore =
                    result.action() == ReceiptChangeAction.RESTORED
                            ? ReceiptStatus.DELETED : ReceiptStatus.ACTIVE;
            if (before.status() != expectedBefore) {
                throw new IllegalArgumentException(
                        "Previous status does not match the action.");
            }
            if ((result.action() == ReceiptChangeAction.DELETED
                    || result.action() == ReceiptChangeAction.RESTORED)
                    && (!before.contents().equals(after.contents())
                    || !Objects.equals(before.metadata(), after.metadata())
                    || !before.evidenceIds().equals(after.evidenceIds()))) {
                throw new IllegalArgumentException(
                        "Deletion and restoration cannot change contents, metadata, or evidence references.");
            }
        }
    }
}
