package coupons.repository;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.List;

import coupons.RegisterReward;
import coupons.RegisterRewardRedemption;

/**
 * Shared process-local state for receipt repository adapters.
 *
 * <p>All adapters must share this instance. Replacement states are built
 * before publication; an exception leaves the previous state unchanged.
 * The default store is memory-only. The CLI can attach an explicit local checkpoint
 * which must be written before publishing a replacement state.
 */
final class InMemoryStore {
    final LocalReceiptFile disk;

    InMemoryStore() { disk = null; }
    InMemoryStore(LocalReceiptFile disk, State loaded) {
        this.disk = disk;
        this.state = loaded;
    }

    /** Current immutable committed state, accessed under this store's monitor. */
    State state = new State(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());

    /**
     * Groups immutable receipt-related records for atomic publication.
     * @param receipts current immutable receipt revisions
     * @param rewards issued instruments
     * @param redemptions committed instrument uses
     * @param evidence original receipt artifacts
     * @param history committed changes, readable without reconstruction
     * @param requests original operations and their committed results
     * @param rewardVoids audited removals of unlinked standalone coupon mistakes
     */
    record State(
            Map<UUID, ReceiptRevSnap> receipts,
            Map<UUID, RegisterReward> rewards,
            Map<UUID, RegisterRewardRedemption> redemptions,
            Map<UUID, ReceiptEvidence> evidence,
            Map<UUID, ReceiptHistoryEntry> history,
            Map<UUID, CommittedWrite> requests, Map<UUID, RewardVoid> rewardVoids) implements java.io.Serializable {
        /** Preserves existing builders and pre-void checkpoint files. */
        State(Map<UUID, ReceiptRevSnap> receipts, Map<UUID, RegisterReward> rewards,
                Map<UUID, RegisterRewardRedemption> redemptions, Map<UUID, ReceiptEvidence> evidence,
                Map<UUID, ReceiptHistoryEntry> history, Map<UUID, CommittedWrite> requests) {
            this(receipts, rewards, redemptions, evidence, history, requests, Map.of());
        }

        /** Copies every map before publication to isolate builder-owned maps. */
        State {
            receipts = Map.copyOf(receipts);
            rewards = Map.copyOf(rewards);
            redemptions = Map.copyOf(redemptions);
            evidence = Map.copyOf(evidence);
            history = Map.copyOf(history);
            requests = Map.copyOf(requests);
            rewardVoids = rewardVoids == null ? Map.of() : Map.copyOf(rewardVoids);
        }
    }

    /** Typed operation values; later operation types share the same request map. */
    sealed interface WriteInput extends java.io.Serializable permits CreateInput { }

    /** Original immutable creation input, compared by value on retries. */
    record CreateInput(UUID transactionId, ReceiptSnapshot contents,
            ReceiptMeta metadata, List<ReceiptEvidence> evidence,
            ReceiptWriteRequest request, ReceiptCreateCtx context) implements WriteInput {
        /** Validates and isolates input without changing recorded values. */
        CreateInput {
            Objects.requireNonNull(transactionId);
            Objects.requireNonNull(contents);
            Objects.requireNonNull(request);
            Objects.requireNonNull(context);
            evidence = EvidenceBatchValidator.validateAndCopy(transactionId, evidence);
        }
    }

    /** A successful request and its original outcome, published with all writes. */
    record CommittedWrite(WriteInput input, ReceiptWriteResult result) implements java.io.Serializable {
        /** Requires original input and outcome. */
        CommittedWrite {
            Objects.requireNonNull(input);
            Objects.requireNonNull(result);
        }

        /** Returns the original result or rejects conflicting request reuse. */
        ReceiptWriteResult resultForMatching(WriteInput retry) {
            if (!input.equals(retry)) {
                throw new IllegalArgumentException("Request ID was used with different contents.");
            }
            return result;
        }
    }
}
