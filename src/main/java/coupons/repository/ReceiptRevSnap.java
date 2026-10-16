package coupons.repository;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Preserves a receipt's identity, lifecycle state, and contents.
 *
 * @param transactionId stable receipt identifier
 * @param revision captured receipt revision
 * @param status captured lifecycle status
 * @param contents immutable transaction contents
 * @param metadata captured identification details, null when unknown
 * @param evidenceIds captured evidence IDs; empty when none are recorded
 */
public record ReceiptRevSnap(
        UUID transactionId,
        long revision,
        ReceiptStatus status,
        ReceiptSnapshot contents,
        ReceiptMeta metadata,
        List<UUID> evidenceIds
) {

    /**
     * Creates a revision snapshot with unknown metadata.
     *
     * @param transactionId stable receipt identifier
     * @param revision captured revision
     * @param status captured lifecycle status
     * @param contents immutable transaction contents
     * @throws NullPointerException if a required reference is null
     * @throws IllegalArgumentException if revision or references are invalid
     */
    public ReceiptRevSnap(UUID transactionId, long revision,
            ReceiptStatus status, ReceiptSnapshot contents) {
        this(transactionId, revision, status, contents, null);
    }

    /**
     * Creates a revision snapshot with metadata and no evidence attachments.
     *
     * @param transactionId stable receipt identifier
     * @param revision captured revision
     * @param status captured lifecycle status
     * @param contents immutable transaction contents
     * @param metadata captured identification details, null when unknown
     * @throws NullPointerException if a required reference is null
     * @throws IllegalArgumentException if revision or references are invalid
     */
    public ReceiptRevSnap(UUID transactionId, long revision,
            ReceiptStatus status, ReceiptSnapshot contents, ReceiptMeta metadata) {
        this(transactionId, revision, status, contents, metadata, List.of());
    }

    /**
     * Validates the captured receipt revision.
     *
     * @param transactionId stable receipt identifier
     * @param revision captured receipt revision
     * @param status captured lifecycle status
     * @param contents immutable transaction contents
     * @param metadata captured identification details, null when unknown
     * @param evidenceIds captured evidence IDs; empty when none are recorded
     * @throws NullPointerException if a required reference, evidence list, or element is null
     * @throws IllegalArgumentException if revision is less than one
     *         or a redemption references another receipt or evidence IDs repeat
     */
    public ReceiptRevSnap {
        Objects.requireNonNull(
                transactionId,
                "Transaction ID cannot be null."
        );
        Objects.requireNonNull(status, "Status cannot be null.");
        Objects.requireNonNull(contents, "Contents cannot be null.");
        evidenceIds = List.copyOf(evidenceIds);
        if (new HashSet<>(evidenceIds).size() != evidenceIds.size()) {
            throw new IllegalArgumentException("Duplicate evidence IDs are not allowed.");
        }

        if (revision < 1) {
            throw new IllegalArgumentException(
                    "Revision must be at least 1."
            );
        }

        for (var redemption : contents.registerRewardRedemptions()) {
            if (!transactionId.equals(redemption.getRedeemedReceiptId())) {
                throw new IllegalArgumentException(
                        "Redemption references a different receipt."
                );
            }
        }
    }

    /**
     * Captures a receipt while its source contents remain stable.
     *
     * @param receipt receipt to capture
     * @return an immutable revision snapshot
     * @throws NullPointerException if receipt is null
     * @throws IllegalArgumentException if receipt relationships are invalid
     */
    public static ReceiptRevSnap from(ReceiptRecord receipt) {
        Objects.requireNonNull(receipt, "Receipt cannot be null.");

        return new ReceiptRevSnap(
                receipt.transactionId(),
                receipt.revision(),
                receipt.status(),
                ReceiptSnapshot.from(receipt.transaction()),
                receipt.metadata(),
                receipt.evidenceIds()
        );
    }
}
