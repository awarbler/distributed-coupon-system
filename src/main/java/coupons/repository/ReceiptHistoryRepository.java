package coupons.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Defines database-independent lookup of committed receipt history.
 *
 * <p>History entries are immutable. This interface provides no
 * independent creation, update, or deletion operations.
 *
 * <p>Entries must be recorded atomically with receipt writes.
 * History remains accessible when a receipt is soft deleted.
 */
public interface ReceiptHistoryRepository {

    /**
     * Finds a history entry by its stable identifier.
     *
     * @param historyId identifier of the history entry
     * @return the entry, or Optional.empty() if absent
     * @throws NullPointerException if historyId is null
     */
    Optional<ReceiptHistoryEntry> findById(UUID historyId);

    /**
     * Finds the history entry produced by a committed write request.
     *
     * <p>A successful retry must refer to the same committed entry
     * rather than create another entry.
     *
     * @param requestId identifier of the write request
     * @return the entry, or Optional.empty() if no committed entry exists
     * @throws NullPointerException if requestId is null
     */
    Optional<ReceiptHistoryEntry> findByRequestId(UUID requestId);

    /**
     * Finds a receipt's history at a particular resulting revision.
     *
     * @param transactionId stable receipt identifier
     * @param revision resulting revision to retrieve
     * @return the entry, or Optional.empty() if absent
     * @throws NullPointerException if transactionId is null
     * @throws IllegalArgumentException if revision is less than one
     */
    Optional<ReceiptHistoryEntry> findByRevision(
            UUID transactionId,
            long revision
    );

    /**
     * Retrieves all committed history for a receipt.
     *
     * <p>Entries are ordered by resulting revision, ascending.
     * Results include creation, updates, deletions, and restorations.
     * Timestamp ordering must not replace revision ordering.
     *
     * @param transactionId stable receipt identifier
     * @return an unmodifiable list, empty if no history exists
     * @throws NullPointerException if transactionId is null
     */
    List<ReceiptHistoryEntry> findByReceiptId(UUID transactionId);
}
