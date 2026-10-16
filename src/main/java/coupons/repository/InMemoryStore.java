package coupons.repository;

import coupons.RegisterReward;
import coupons.RegisterRewardRedemption;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Shared process-local state for receipt repository adapters.
 *
 * <p>All adapters must share this instance. Replacement states are built
 * before publication; an exception leaves the previous state unchanged.
 * No data survives destruction of this store or application restart.
 */
final class InMemoryStore {
    /** Current immutable committed state, accessed under this store's monitor. */
    private State state = new State(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());

    /**
     * Captures one consistent state for a lookup.
     * @return immutable committed state
     */
    synchronized State snapshot() {
        return state;
    }

    /**
     * Builds and publishes a complete replacement while excluding other writes.
     * The builder must not mutate the store or invoke nested writes.
     * @param builder operation producing an immutable replacement
     * @throws NullPointerException if builder or its result is null
     */
    synchronized void commit(Function<State, State> builder) {
        State replacement = Objects.requireNonNull(
                Objects.requireNonNull(builder).apply(state));
        state = replacement;
    }

    /**
     * Groups immutable receipt-related records for atomic publication.
     * Request payload comparison will be added with receipt-write operations;
     * history alone is not sufficient to implement idempotency.
     * @param receipts current immutable receipt revisions
     * @param rewards issued instruments
     * @param redemptions committed instrument uses
     * @param evidence original receipt artifacts
     * @param history committed changes, readable without reconstruction
     */
    record State(
            Map<UUID, ReceiptRevSnap> receipts,
            Map<UUID, RegisterReward> rewards,
            Map<UUID, RegisterRewardRedemption> redemptions,
            Map<UUID, ReceiptEvidence> evidence,
            Map<UUID, ReceiptHistoryEntry> history) {
        /** Copies every map before publication to isolate builder-owned maps. */
        State {
            receipts = Map.copyOf(receipts);
            rewards = Map.copyOf(rewards);
            redemptions = Map.copyOf(redemptions);
            evidence = Map.copyOf(evidence);
            history = Map.copyOf(history);
        }
    }
}
