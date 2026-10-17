package coupons.repository;

import coupons.CouponTransaction;
import coupons.RegisterReward;
import coupons.RegisterRewardRedemption;
import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import coupons.repository.InMemoryStore.CommittedWrite;
import coupons.repository.InMemoryStore.CreateInput;
import coupons.repository.InMemoryStore.State;

/**
 * Process-local receipt persistence for one application/user scope.
 *
 * <p>All adapters share one store and monitor. Save and lookups are implemented;
 * updates, deletion, and restoration explicitly remain pending later checkpoints.
 * Authentication and approval belong to the calling service. Default constructors
 * are memory-only; openLocal explicitly enables a versioned local checkpoint. A lookup returns a fresh working transaction; history is
 * available directly even if working reconstruction is no longer possible.
 */
public final class InMemoryTransactionRepository implements TransactionRepository, AutoCloseable {
    private final InMemoryStore store;
    private final Clock clock;

    /** Creates an empty isolated store using the UTC system clock. */
    public InMemoryTransactionRepository() {
        this(Clock.systemUTC());
    }

    /**
     * Creates an empty store with an injected commit clock.
     * @param clock commit timestamp source
     */
    public InMemoryTransactionRepository(Clock clock) {
        this(new InMemoryStore(), clock);
    }

    /** Shares state with package-local integration tests and adapters. */
    InMemoryTransactionRepository(InMemoryStore store, Clock clock) {
        this.store = Objects.requireNonNull(store);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Opens the CLI's local checkpoint; default constructors still create memory-only stores. */
    public static InMemoryTransactionRepository openLocal(java.nio.file.Path path) throws java.io.IOException {
        LocalReceiptFile file = new LocalReceiptFile(path);
        try {
            return new InMemoryTransactionRepository(new InMemoryStore(file, file.load()), Clock.systemUTC());
        } catch (java.io.IOException | RuntimeException failure) {
            try { file.close(); } catch (java.io.IOException closing) { failure.addSuppressed(closing); }
            throw failure;
        }
    }

    /** Current storage location, if local persistence was explicitly enabled. */
    public Optional<java.nio.file.Path> storagePath() {
        return store.disk == null ? Optional.empty() : Optional.of(store.disk.path);
    }

    /** Creation history also restores receipt selection and the original last retry input. */
    public List<ReceiptHistoryEntry> creationHistory() {
        return snapshot().history().values().stream()
                .filter(entry -> entry.result().action() == ReceiptChangeAction.CREATED)
                .sorted(java.util.Comparator.comparing((ReceiptHistoryEntry entry) -> entry.result().committedAt())
                        .thenComparing(ReceiptHistoryEntry::historyId)).toList();
    }

    @Override public void close() throws java.io.IOException {
        synchronized (store) { if (store.disk != null) store.disk.close(); }
    }

    /** Captures stable input, validates relationships, and publishes one state. */
    @Override
    public ReceiptWriteResult save(UUID transactionId, CouponTransaction transaction,
            ReceiptMeta metadata, List<ReceiptEvidence> newEvidence,
            ReceiptWriteRequest request, ReceiptCreateCtx context) {
        CreateInput input = new CreateInput(transactionId, ReceiptSnapshot.from(transaction),
                metadata, newEvidence, request, context);
        synchronized (store) {
            State current = store.state;
            CommittedWrite committed = current.requests().get(request.requestId());
            if (committed != null) {
                return committed.resultForMatching(input);
            }
            ReceiptRevSnap receipt = new ReceiptRevSnap(transactionId, 1,
                    ReceiptStatus.ACTIVE, input.contents(), metadata,
                    input.evidence().stream().map(ReceiptEvidence::getEvidenceId).toList());
            validateCreation(current, receipt, input.evidence());
            ReceiptWriteResult result = new ReceiptWriteResult(request.requestId(), transactionId,
                    1, ReceiptChangeAction.CREATED, ReceiptStatus.ACTIVE, clock.instant());
            ReceiptHistoryEntry historyEntry = new ReceiptHistoryEntry(UUID.randomUUID(),
                    request, result, null, receipt, context, null);

            var receipts = new HashMap<>(current.receipts());
            var redemptions = new HashMap<>(current.redemptions());
            var evidence = new HashMap<>(current.evidence());
            var history = new HashMap<>(current.history());
            var requests = new HashMap<>(current.requests());
            receipts.put(transactionId, receipt);
            for (var redemption : input.contents().registerRewardRedemptions()) {
                redemptions.put(redemption.getRedemptionId(), redemption);
            }
            for (var artifact : input.evidence()) {
                evidence.put(artifact.getEvidenceId(), artifact);
            }
            history.put(historyEntry.historyId(), historyEntry);
            requests.put(request.requestId(), new CommittedWrite(input, result));
            State replacement = new State(receipts, current.rewards(), redemptions,
                    evidence, history, requests, current.rewardVoids());
            if (store.disk != null) {
                try { store.disk.save(replacement); }
                catch (java.io.IOException failure) {
                    throw new IllegalStateException("Could not write receipts to disk; nothing committed: " + failure.getMessage(), failure);
                }
            }
            store.state = replacement;
            return result;
        }
    }

    /** Checks references and uniqueness before any committed state changes. */
    private void validateCreation(State current, ReceiptRevSnap receipt,
            List<ReceiptEvidence> evidence) {
        if (current.receipts().containsKey(receipt.transactionId())) {
            throw new IllegalArgumentException("Receipt ID already exists.");
        }
        var incomingEventIds = ReceiptObservationValidator.eventIds(receipt.contents());
        for (var existing : current.receipts().values()) {
            if (ReceiptObservationValidator.eventIds(existing.contents()).stream().anyMatch(incomingEventIds::contains)) {
                throw new IllegalArgumentException("A financial event already belongs to another receipt.");
            }
        }
        var lines = new HashSet<UUID>();
        for (var line : receipt.contents().purchaseItems()) {
            if (!lines.add(line.lineItemId())) {
                throw new IllegalArgumentException("Duplicate receipt-line ID.");
            }
        }
        var applications = new HashSet<UUID>();
        for (var applied : receipt.contents().appliedCoupons()) {
            if (!applications.add(applied.getApplicationId())
                    || !lines.containsAll(applied.getLineItemIds())) {
                throw new IllegalArgumentException("Invalid applied-coupon relationship.");
            }
        }
        var redemptionIds = new HashSet<UUID>();
        var rewardIds = new HashSet<UUID>();
        for (var redemption : receipt.contents().registerRewardRedemptions()) {
            if (!redemptionIds.add(redemption.getRedemptionId())
                    || current.redemptions().containsKey(redemption.getRedemptionId())) {
                throw new IllegalArgumentException("Redemption ID already exists.");
            }
            if (!rewardIds.add(redemption.getRewardId())
                    || !current.rewards().containsKey(redemption.getRewardId())
                    || current.rewardVoids().containsKey(redemption.getRewardId())) {
                throw new IllegalArgumentException("Reward is missing or repeated.");
            }
            if (current.redemptions().values().stream().anyMatch(existing ->
                    existing.getRewardId().equals(redemption.getRewardId()))) {
                throw new IllegalArgumentException("Reward has already been redeemed.");
            }
        }
        for (var artifact : evidence) {
            if (current.evidence().containsKey(artifact.getEvidenceId())) {
                throw new IllegalArgumentException("Evidence ID already exists.");
            }
        }
        for (var unidentified : receipt.contents().observations().unidentifiedRR()) {
            boolean duplicate = current.redemptions().containsKey(unidentified.eventId())
                    || current.receipts().values().stream().anyMatch(existing ->
                    existing.contents().observations().unidentifiedRR().stream()
                            .anyMatch(value -> value.eventId().equals(unidentified.eventId())));
            if (duplicate) {
                throw new IllegalArgumentException("RR deduction event already belongs to another receipt.");
            }
        }
        for (var redemption : receipt.contents().registerRewardRedemptions()) {
            boolean duplicate = current.receipts().values().stream().anyMatch(existing ->
                    existing.contents().observations().unidentifiedRR().stream()
                            .anyMatch(value -> value.eventId().equals(redemption.getRedemptionId())));
            if (duplicate) {
                throw new IllegalArgumentException("RR deduction event already belongs to another receipt.");
            }
        }
        for (var earning : receipt.contents().observations().earnings()) {
            if (earning.instrumentId() != null) {
                var instrument = current.rewards().get(earning.instrumentId());
                if (instrument == null || instrument.getEarnedReceiptId() == null
                        || !instrument.getEarnedReceiptId().equals(receipt.transactionId())) {
                    throw new IllegalArgumentException("Earning instrument does not reference this receipt.");
                }
            }
            if (earning.claimId() != null) {
                throw new IllegalArgumentException("Claim references require rebate repository integration; record the offer reference instead.");
            }
        }
    }

    /** Captures one immutable state under the shared monitor. */
    private State snapshot() {
        synchronized (store) {
            return store.state;
        }
    }

    /** Reconstructs caller-owned working contents, preserving enclosing facts. */
    private ReceiptRecord workingReceipt(ReceiptRevSnap receipt) {
        return new ReceiptRecord(receipt.transactionId(), receipt.status(), receipt.revision(),
                receipt.contents().toTransaction(), receipt.metadata(), receipt.evidenceIds());
    }

    /** Finds a receipt of the required status without changing history. */
    private Optional<ReceiptRecord> findReceipt(UUID id, ReceiptStatus status) {
        Objects.requireNonNull(id);
        ReceiptRevSnap receipt = snapshot().receipts().get(id);
        return receipt == null || receipt.status() != status
                ? Optional.empty() : Optional.of(workingReceipt(receipt));
    }

    /** Returns an independent active receipt or an empty result. */
    @Override
    public Optional<ReceiptRecord> findById(UUID transactionId) {
        return findReceipt(transactionId, ReceiptStatus.ACTIVE);
    }

    /** Returns an independent deleted receipt or an empty result. */
    @Override
    public Optional<ReceiptRecord> findDeletedById(UUID transactionId) {
        return findReceipt(transactionId, ReceiptStatus.DELETED);
    }

    /** Lists isolated deleted receipts in UUID natural order. */
    @Override
    public List<ReceiptRecord> findDeleted() {
        return snapshot().receipts().values().stream()
                .filter(receipt -> receipt.status() == ReceiptStatus.DELETED)
                .sorted((a, b) -> a.transactionId().compareTo(b.transactionId()))
                .map(this::workingReceipt).toList();
    }

    /** Finds candidates using exact known metadata, including deleted receipts. */
    @Override
    public List<ReceiptRecord> findPossibleDuplicates(ReceiptMeta metadata) {
        Objects.requireNonNull(metadata);
        if (metadata.receiptNumber() == null && metadata.purchasedAt() == null) {
            throw new IllegalArgumentException("Receipt number or purchase date/time is required.");
        }
        return snapshot().receipts().values().stream()
                .filter(receipt -> matchesMetadata(metadata, receipt.metadata()))
                .sorted((a, b) -> a.transactionId().compareTo(b.transactionId()))
                .map(this::workingReceipt).toList();
    }

    /** Compares only recorded metadata without inventing missing values. */
    private boolean matchesMetadata(ReceiptMeta a, ReceiptMeta b) {
        if (b == null || !a.retailer().equals(b.retailer())) {
            return false;
        }
        if (a.storeReference() != null && b.storeReference() != null
                && !a.storeReference().equals(b.storeReference())) {
            return false;
        }
        return (a.receiptNumber() != null && a.receiptNumber().equals(b.receiptNumber()))
                || (a.purchasedAt() != null && a.purchasedAt().equals(b.purchasedAt()));
    }

    /** Lists individually registered immutable coupons without asserting their availability. */
    public List<RegisterReward> recordedRewards() {
        State current = snapshot();
        return current.rewards().values().stream().filter(reward -> !current.rewardVoids().containsKey(reward.getRewardId()))
                .sorted(java.util.Comparator.comparing(RegisterReward::getRewardId)).toList();
    }

    /** Removes only unlinked standalone mistakes, retaining the coupon and an immutable audit record. */
    public RewardVoid voidRecordedReward(UUID id, String reason, String recordedBy) {
        Objects.requireNonNull(id);
        synchronized (store) {
            State current = store.state;
            var previous = current.rewardVoids().get(id);
            if (previous != null) return previous;
            RegisterReward reward = current.rewards().get(id);
            if (reward == null) throw new IllegalArgumentException("RR coupon not found.");
            boolean linked = reward.getEarnedReceiptId() != null
                    || current.redemptions().values().stream().anyMatch(r -> r.getRewardId().equals(id))
                    || current.receipts().values().stream().anyMatch(r -> r.contents().observations().earnings().stream()
                            .anyMatch(e -> id.equals(e.instrumentId())));
            if (linked) throw new IllegalArgumentException("This RR is linked to a receipt and cannot be removed as a standalone mistake.");
            RewardVoid removal = new RewardVoid(id, reason, recordedBy, clock.instant());
            var removals = new HashMap<>(current.rewardVoids());
            removals.put(id, removal);
            State replacement = new State(current.receipts(), current.rewards(), current.redemptions(), current.evidence(),
                    current.history(), current.requests(), removals);
            if (store.disk != null) {
                try { store.disk.save(replacement); }
                catch (java.io.IOException failure) { throw new IllegalStateException("Could not save RR removal; nothing changed.", failure); }
            }
            store.state = replacement;
            return removal;
        }
    }

    public List<RewardVoid> rewardRemovalHistory() { return List.copyOf(snapshot().rewardVoids().values()); }

    /**
     * Provides instrument storage in this receipt store's scope.
     * @return issued-reward adapter sharing the same monitor
     */
    public RegisterRewardRepository issuedRewards() {
        return new RegisterRewardRepository() {
            @Override
            public void save(RegisterReward reward) {
                Objects.requireNonNull(reward);
                synchronized (store) {
                    State current = store.state;
                    if (current.rewards().containsKey(reward.getRewardId())) {
                        throw new IllegalArgumentException("Reward ID already exists.");
                    }
                    if (reward.getEarnedReceiptId() != null
                            && !current.receipts().containsKey(reward.getEarnedReceiptId())) {
                        throw new IllegalArgumentException("Earning receipt does not exist.");
                    }
                    var rewards = new HashMap<>(current.rewards());
                    rewards.put(reward.getRewardId(), reward);
                    State replacement = new State(current.receipts(), rewards,
                            current.redemptions(), current.evidence(), current.history(), current.requests(), current.rewardVoids());
                    if (store.disk != null) {
                        try { store.disk.save(replacement); }
                        catch (java.io.IOException failure) {
                            throw new IllegalStateException("Could not write receipts to disk; nothing committed: " + failure.getMessage(), failure);
                        }
                    }
                    store.state = replacement;
                }
            }

            @Override
            public Optional<RegisterReward> findById(UUID rewardId) {
                return Optional.ofNullable(snapshot().rewards().get(Objects.requireNonNull(rewardId)));
            }
        };
    }

    /**
     * Provides history access without reconstructing working transactions.
     * @return immutable history lookup adapter
     */
    public ReceiptHistoryRepository history() {
        return new ReceiptHistoryRepository() {
            @Override
            public Optional<ReceiptHistoryEntry> findById(UUID historyId) {
                return Optional.ofNullable(snapshot().history().get(Objects.requireNonNull(historyId)));
            }

            @Override
            public Optional<ReceiptHistoryEntry> findByRequestId(UUID requestId) {
                Objects.requireNonNull(requestId);
                return snapshot().history().values().stream()
                        .filter(entry -> entry.request().requestId().equals(requestId)).findFirst();
            }

            @Override
            public Optional<ReceiptHistoryEntry> findByRevision(UUID transactionId, long revision) {
                Objects.requireNonNull(transactionId);
                if (revision < 1) {
                    throw new IllegalArgumentException("Revision must be positive.");
                }
                return findByReceiptId(transactionId).stream()
                        .filter(entry -> entry.after().revision() == revision).findFirst();
            }

            @Override
            public List<ReceiptHistoryEntry> findByReceiptId(UUID transactionId) {
                Objects.requireNonNull(transactionId);
                return snapshot().history().values().stream()
                        .filter(entry -> entry.after().transactionId().equals(transactionId))
                        .sorted((a, b) -> Long.compare(a.after().revision(), b.after().revision())).toList();
            }
        };
    }

    /**
     * Provides original evidence lookup without mutable byte-array exposure.
     * @return evidence lookup adapter
     */
    public ReceiptEvidenceRepository evidence() {
        return new ReceiptEvidenceRepository() {
            @Override
            public Optional<ReceiptEvidence> findById(UUID evidenceId) {
                return Optional.ofNullable(snapshot().evidence().get(Objects.requireNonNull(evidenceId)));
            }

            @Override
            public List<ReceiptEvidence> findByReceiptId(UUID transactionId) {
                Objects.requireNonNull(transactionId);
                return snapshot().evidence().values().stream()
                        .filter(value -> value.getTransactionId().equals(transactionId))
                        .sorted((a, b) -> a.getEvidenceId().compareTo(b.getEvidenceId())).toList();
            }
        };
    }

    /**
     * Provides redemption lookup, including redemptions on deleted receipts.
     * @return redemption lookup adapter
     */
    public RRRedemptionRepository redemptions() {
        return new RRRedemptionRepository() {
            @Override
            public Optional<RegisterRewardRedemption> findById(UUID redemptionId) {
                return Optional.ofNullable(snapshot().redemptions().get(Objects.requireNonNull(redemptionId)));
            }

            @Override
            public Optional<RegisterRewardRedemption> findByRewardId(UUID rewardId) {
                Objects.requireNonNull(rewardId);
                return snapshot().redemptions().values().stream()
                        .filter(value -> value.getRewardId().equals(rewardId)).findFirst();
            }

            @Override
            public List<RegisterRewardRedemption> findByReceiptId(UUID receiptId) {
                Objects.requireNonNull(receiptId);
                return snapshot().redemptions().values().stream()
                        .filter(value -> value.getRedeemedReceiptId().equals(receiptId))
                        .sorted((a, b) -> a.getRedemptionId().compareTo(b.getRedemptionId())).toList();
            }
        };
    }

    /** Revision-aware updates belong to Checkpoint 3. */
    @Override
    public ReceiptWriteResult update(UUID id, long revision, CouponTransaction transaction,
            ReceiptMeta metadata, List<ReceiptEvidence> evidence, ReceiptWriteRequest request,
            ReceiptChangeContext context) {
        throw new UnsupportedOperationException("Receipt updates are pending Checkpoint 3.");
    }

    /** Soft deletion belongs to the lifecycle checkpoint. */
    @Override
    public ReceiptWriteResult softDelete(UUID id, long revision, ReceiptWriteRequest request,
            ReceiptChangeContext context) {
        throw new UnsupportedOperationException("Receipt deletion is not implemented yet.");
    }

    /** Restoration belongs to the lifecycle checkpoint. */
    @Override
    public ReceiptWriteResult restore(UUID id, long revision, ReceiptWriteRequest request,
            ReceiptChangeContext context) {
        throw new UnsupportedOperationException("Receipt restoration is not implemented yet.");
    }
}
