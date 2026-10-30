package coupons.repository;

import coupons.promotions.Coupon;
import coupons.promotions.Promotion;
import coupons.rewards.Reward;

import coupons.receipts.CouponTransaction;
import coupons.rewards.RegisterReward;
import coupons.rewards.RegisterRewardRedemption;
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
import coupons.repository.InMemoryStore.UpdateInput;
import coupons.repository.InMemoryStore.DeleteInput;
import coupons.repository.InMemoryStore.RestoreInput;

/**
 * Process-local receipt persistence for one application/user scope.
 *
 * <p>All adapters share one store and monitor. Save, revision-aware updates, and lookups are implemented;
 * soft deletion and restoration preserve history and financial identities.
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

    public AccountBalanceState accountBalances() { return snapshot().balances(); }

    public void recordAccountOpening(AccountOpening opening) {
        Objects.requireNonNull(opening);
        synchronized (store) {
            var existing = store.state.balances();
            var duplicate = existing.openings().stream().filter(o -> o.observationId().equals(opening.observationId())).findFirst();
            if (duplicate.isPresent()) {
                if (!duplicate.orElseThrow().equals(opening)) throw new IllegalArgumentException("Opening ID already used.");
                return;
            }
            var effective = existing.openings().stream().filter(o -> o.provider().equals(opening.provider())
                    && o.date().equals(opening.date()) && o.monthStart() == opening.monthStart()).reduce((a,b) -> b);
            if (effective.isPresent()) {
                var old = effective.orElseThrow();
                if (Objects.equals(old.originalUnits(), opening.originalUnits()) && Objects.equals(old.usdValue(), opening.usdValue())
                        && old.valuationBasis().equals(opening.valuationBasis()) && old.sourceNote().equals(opening.sourceNote())) return;
            }
            var values = new java.util.ArrayList<>(existing.openings()); values.add(opening);
            publishBalances(new AccountBalanceState(values, existing.adjustments(), existing.redemptionHistory()));
        }
    }

    public void recordAccountAdjustment(AccountAdjustment adjustment) {
        Objects.requireNonNull(adjustment);
        synchronized (store) {
            var existing = store.state.balances();
            var duplicate = existing.adjustments().stream().filter(a -> a.eventId().equals(adjustment.eventId())).findFirst();
            if (duplicate.isPresent()) {
                if (!duplicate.orElseThrow().equals(adjustment)) throw new IllegalArgumentException("Adjustment ID already used.");
                return;
            }
            var values = new java.util.ArrayList<>(existing.adjustments()); values.add(adjustment);
            publishBalances(new AccountBalanceState(existing.openings(), values, existing.redemptionHistory()));
        }
    }

    public List<ProviderRedemption> providerRedemptions() {
        var latest=new java.util.LinkedHashMap<UUID,ProviderRedemption>();
        accountBalances().redemptionHistory().forEach(r->latest.put(r.redemptionId(),r));
        return List.copyOf(latest.values());
    }
    public ProviderRedemption recordProviderRedemption(ProviderRedemption event,AccountOpening destinationOpening) {
        Objects.requireNonNull(event);
        synchronized(store) {
            var current=store.state.balances();
            var existing=providerRedemptions().stream().filter(r->r.redemptionId().equals(event.redemptionId())).findFirst();
            if(existing.isPresent()) {
                var prior=existing.orElseThrow(); if(prior.equals(event)) return prior;
                if(prior.status()!=ProviderRedemption.Status.REQUESTED || event.status()==ProviderRedemption.Status.REQUESTED || !prior.sameTerms(event))
                    throw new IllegalArgumentException("Only an unchanged pending redemption can be received or cancelled.");
            } else {
                if(event.status()==ProviderRedemption.Status.CANCELLED) throw new IllegalArgumentException("Request a redemption before cancelling it.");
                var receipts=creationHistory().stream().map(h->findById(h.after().transactionId()).orElse(null)).filter(Objects::nonNull).toList();
                var balance=new coupons.rewards.ProviderBalanceSummary(this).balance(event.requestedDate(),event.provider(),receipts);
                if(balance.endUsd()==null || balance.endUsd().compareTo(event.sourceUsdValue())<0) throw new IllegalArgumentException("Record a sufficient confirmed source balance before redeeming.");
                if(event.originalUnitsUsed()!=null && (balance.endPoints()==null || balance.endPoints().compareTo(event.originalUnitsUsed())<0))
                    throw new IllegalArgumentException("Record sufficient original points before redeeming.");
                for(UUID id:event.sourceEarningIds()) if(receipts.stream().flatMap(r->r.transaction().getObservations().earnings().stream()).noneMatch(e->e.earningId().equals(id) && e.provider().equalsIgnoreCase(event.provider())))
                    throw new IllegalArgumentException("Source earning must belong to the selected provider.");
            }
            var openings=new java.util.ArrayList<>(current.openings());
            if(destinationOpening!=null) {
                if(event.status()!=ProviderRedemption.Status.RECEIVED || event.destination()==ProviderRedemption.Destination.PURCHASE
                        || !destinationOpening.provider().equals(event.destinationAccount()) || !destinationOpening.date().equals(event.completedDate())
                        || openings.stream().anyMatch(o->o.provider().equals(destinationOpening.provider()))) throw new IllegalArgumentException("Destination opening must describe a newly confirmed receiving account.");
                openings.add(destinationOpening);
            }
            var history=new java.util.ArrayList<>(current.redemptionHistory());history.add(event);
            publishBalances(new AccountBalanceState(openings,current.adjustments(),history));
            return event;
        }
    }

    private void publishBalances(AccountBalanceState balances) {
        var current = store.state;
        var replacement = new State(current.receipts(), current.rewards(), current.redemptions(), current.evidence(),
                current.history(), current.requests(), current.rewardVoids(), current.giftCardPromotions(), balances, current.rrUsageLinks());
        if (store.disk != null) {
            try { store.disk.save(replacement); }
            catch (java.io.IOException failure) { throw new IllegalStateException("Could not save account settings; nothing committed.", failure); }
        }
        store.state = replacement;
    }

    public List<GiftCardPromotion> giftCardPromotions() {
        return snapshot().giftCardPromotions().values().stream()
                .sorted(java.util.Comparator.comparing(GiftCardPromotion::name).thenComparing(GiftCardPromotion::id)).toList();
    }

    /** Publishes offer settings and receipt links using the existing locked atomic checkpoint. */
    public void saveGiftCardPromotion(GiftCardPromotion promotion) {
        Objects.requireNonNull(promotion);
        synchronized (store) {
            State current = store.state;
            var previous = current.giftCardPromotions().get(promotion.id());
            for (var link : promotion.contributions().values()) {
                if (previous != null && link.equals(previous.contributions().get(link.receiptId()))) continue;
                var receipt = current.receipts().get(link.receiptId());
                if (receipt == null || receipt.revision() != link.reviewedRevision()
                        || !receipt.contents().purchaseItems().stream().map(PurchaseLineSnap::lineItemId).toList().containsAll(link.lineIds()))
                    throw new IllegalArgumentException("Receipt changed or eligible products are missing; review the link again.");
            }
            var promotions = new HashMap<>(current.giftCardPromotions());
            promotions.put(promotion.id(), promotion);
            State replacement = new State(current.receipts(), current.rewards(), current.redemptions(), current.evidence(),
                    current.history(), current.requests(), current.rewardVoids(), promotions, current.balances(), current.rrUsageLinks());
            if (store.disk != null) {
                try { store.disk.save(replacement); }
                catch (java.io.IOException failure) { throw new IllegalStateException("Could not save gift-card promotion; nothing committed.", failure); }
            }
            store.state = replacement;
        }
    }

    java.util.Map<UUID, GiftCardPromotion> receiptPromotions(State current, ReceiptRevSnap receipt, ReceiptRevSnap previous) {
        var promotions = new HashMap<>(current.giftCardPromotions());
        var lineIds = receipt.contents().purchaseItems().stream().map(PurchaseLineSnap::lineItemId).toList();
        if(previous!=null) {
            var retained=receipt.contents().observations().promotionLinks().stream().map(coupons.receipts.ReceiptPromotionLink::promotionId).collect(java.util.stream.Collectors.toSet());
            for(var old:previous.contents().observations().promotionLinks())if(!retained.contains(old.promotionId())) {
                var offer=promotions.get(old.promotionId());
                if(offer!=null){var contributions=new HashMap<>(offer.contributions());contributions.remove(receipt.transactionId());
                    promotions.put(offer.id(),new GiftCardPromotion(offer.id(),offer.name(),offer.threshold(),offer.giftCardValue(),offer.start(),offer.end(),offer.basis(),offer.terms(),offer.status(),contributions));}
            }
        }
        for (var link : receipt.contents().observations().promotionLinks()) {
            if (!lineIds.containsAll(link.eligibleLineIds())) throw new IllegalArgumentException("Promotion links reference missing products.");
            var offer = promotions.get(link.promotionId());
            if (offer == null) throw new IllegalArgumentException("Online promotion not found; create it in menu 7 first.");
            if (previous != null && previous.contents().observations().promotionLinks().contains(link)) continue;
            var contribution = new GiftCardPromotion.Contribution(receipt.transactionId(), receipt.revision(),
                    link.eligibleLineIds(), link.reviewedEligibleAmount(), link.eligibilityNote());
            promotions.put(offer.id(), offer.withContribution(contribution));
        }
        return promotions;
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
            validateWrite(current, receipt, input.evidence(), null);
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
                    evidence, history, requests, current.rewardVoids(), receiptPromotions(current, receipt, null), current.balances(), current.rrUsageLinks());
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
    void validateWrite(State current, ReceiptRevSnap receipt,
            List<ReceiptEvidence> evidence, ReceiptRevSnap previous) {
        if (previous == null && current.receipts().containsKey(receipt.transactionId())) {
            throw new IllegalArgumentException("Receipt ID already exists.");
        }
        validateStoredReferences(current, receipt, evidence, previous);
        if(coupons.receipts.ReceiptIssues.online(receipt.metadata()))
            coupons.receipts.ReceiptIssues.validateOnlineRule(receipt.metadata(),receipt.contents().toTransaction());
        var incomingEventIds = ReceiptObservationValidator.eventIds(receipt.contents());
        for (var existing : current.receipts().values()) {
            if (existing.transactionId().equals(receipt.transactionId())) continue;
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
        if (previous != null && !receipt.contents().registerRewardRedemptions()
                .containsAll(previous.contents().registerRewardRedemptions())) {
            throw new IllegalArgumentException("Existing redemptions cannot be removed or altered.");
        }
        var redemptionIds = new HashSet<UUID>();
        var rewardIds = new HashSet<UUID>();
        for (var redemption : receipt.contents().registerRewardRedemptions()) {
            if (current.rrUsageLinks().containsKey(redemption.getRewardId())
                    || current.receipts().values().stream().flatMap(r -> r.contents().observations().earnings().stream())
                            .anyMatch(e -> redemption.getRewardId().equals(e.instrumentId()) && current.rrUsageLinks().containsKey(e.earningId())))
                throw new IllegalArgumentException("RR already consumed by a confirmed receipt-reported deduction.");
            if (previous != null && previous.contents().registerRewardRedemptions().contains(redemption)) {
                if (!redemptionIds.add(redemption.getRedemptionId()) || !rewardIds.add(redemption.getRewardId())) {
                    throw new IllegalArgumentException("Duplicate redemption or reward.");
                }
                continue;
            }
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
                    || current.receipts().values().stream().filter(existing -> !existing.transactionId().equals(receipt.transactionId())).anyMatch(existing ->
                    existing.contents().observations().unidentifiedRR().stream()
                            .anyMatch(value -> value.eventId().equals(unidentified.eventId())));
            if (duplicate) {
                throw new IllegalArgumentException("RR deduction event already belongs to another receipt.");
            }
        }
        for (var redemption : receipt.contents().registerRewardRedemptions()) {
            boolean duplicate = current.receipts().values().stream().filter(existing -> !existing.transactionId().equals(receipt.transactionId())).anyMatch(existing ->
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

    /** Validate existing references too; unchanged financial events are not new redemptions. */
    void validateStoredReferences(State current, ReceiptRevSnap receipt,
            List<ReceiptEvidence> incoming, ReceiptRevSnap previous) {
        var artifacts = new HashMap<>(current.evidence());
        for (var artifact : EvidenceBatchValidator.validateAndCopy(receipt.transactionId(), incoming)) {
            if (artifacts.putIfAbsent(artifact.getEvidenceId(), artifact) != null)
                throw new IllegalArgumentException("Evidence ID already exists.");
        }
        for (var id : receipt.evidenceIds()) {
            var artifact = artifacts.get(id);
            if (artifact == null || !receipt.transactionId().equals(artifact.getTransactionId()))
                throw new IllegalArgumentException("Receipt evidence is missing or belongs to another receipt.");
        }
        for (var redemption : receipt.contents().registerRewardRedemptions()) {
            var reward = current.rewards().get(redemption.getRewardId());
            if (reward == null || current.rewardVoids().containsKey(redemption.getRewardId()))
                throw new IllegalArgumentException("Referenced reward is missing or voided.");
            validateRewardOrigin(current, receipt, reward);
            if (previous != null && previous.contents().registerRewardRedemptions().contains(redemption)
                    && !redemption.equals(current.redemptions().get(redemption.getRedemptionId())))
                throw new IllegalArgumentException("Stored redemption is missing or inconsistent.");
        }
        for (var earning : receipt.contents().observations().earnings()) {
            if (earning.instrumentId() != null) {
                var reward = current.rewards().get(earning.instrumentId());
                if (reward == null || current.rewardVoids().containsKey(earning.instrumentId())
                        || !receipt.transactionId().equals(reward.getEarnedReceiptId()))
                    throw new IllegalArgumentException("Earning instrument is missing, voided, or owned by another receipt.");
            }
            if (earning.claimId() != null)
                throw new IllegalArgumentException("Claim references require rebate repository integration.");
        }
        // Historical RR matches remain reserved when corrections require review.
        // Restoration must not activate a use while its earning receipt is deleted.
        if (receipt.status() == ReceiptStatus.ACTIVE) {
            for (var link : current.rrUsageLinks().values()) {
                if (!receipt.transactionId().equals(link.spentReceiptId())) continue;
                if (link.receiptEarning()) {
                    var source = earningReceipt(current, link.sourceId());
                    if (source == null || source.status() != ReceiptStatus.ACTIVE)
                        throw new IllegalArgumentException("Restore the RR earning receipt before its spending receipt.");
                } else {
                    var reward = current.rewards().get(link.sourceId());
                    if (reward == null || current.rewardVoids().containsKey(link.sourceId()))
                        throw new IllegalArgumentException("Matched physical RR is missing or voided.");
                    validateRewardOrigin(current, receipt, reward);
                }
            }
        }
    }

    private ReceiptRevSnap earningReceipt(State current, UUID earningId) {
        for (var r : current.receipts().values())
            if (r.contents().observations().earnings().stream().anyMatch(e -> e.earningId().equals(earningId))) return r;
        // A correction can remove an earning from the current revision without erasing its match history.
        return current.history().values().stream()
                .filter(h -> h.after().contents().observations().earnings().stream().anyMatch(e -> e.earningId().equals(earningId)))
                .map(h -> current.receipts().get(h.after().transactionId())).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private void validateRewardOrigin(State current, ReceiptRevSnap spending, RegisterReward reward) {
        var aliases=new java.util.HashSet<UUID>();aliases.add(reward.getRewardId());current.receipts().values().stream().flatMap(r->r.contents().observations().earnings().stream()).filter(e->reward.getRewardId().equals(e.instrumentId())).forEach(e->aliases.add(e.earningId()));
        var day=spending.contents().observations().purchaseDate();
        if(current.balances().adjustments().stream().anyMatch(a->a.unavailable()&&aliases.contains(a.unavailableSource())&&(day==null||!a.date().isAfter(day))))throw new IllegalArgumentException("This reward was recorded as unavailable before this purchase.");
        UUID sourceId = reward.getEarnedReceiptId();
        if (sourceId == null) return; // An explicitly recorded pre-existing physical coupon.
        var source = sourceId.equals(spending.transactionId()) ? spending : current.receipts().get(sourceId);
        if (source == null) throw new IllegalArgumentException("Reward earning receipt is missing.");
        if (spending.status() == ReceiptStatus.ACTIVE && source.status() != ReceiptStatus.ACTIVE)
            throw new IllegalArgumentException("Restore the reward earning receipt before using or restoring its use.");
    }

    /** Do not hide an earning whose recorded value is consumed by an active receipt or transfer. */
    void validateDeletionDependencies(State current, ReceiptRevSnap receipt) {
        var earningIds = receipt.contents().observations().earnings().stream()
                .map(coupons.receipts.ReceiptObservations.Earning::earningId).collect(java.util.stream.Collectors.toSet());
        current.history().values().stream().filter(h -> h.after().transactionId().equals(receipt.transactionId()))
                .flatMap(h -> h.after().contents().observations().earnings().stream())
                .forEach(e -> earningIds.add(e.earningId()));
        var instruments = new HashSet<UUID>();
        current.rewards().values().stream()
                .filter(r -> receipt.transactionId().equals(r.getEarnedReceiptId()))
                .forEach(r -> instruments.add(r.getRewardId()));
        receipt.contents().observations().earnings().stream().map(coupons.receipts.ReceiptObservations.Earning::instrumentId)
                .filter(Objects::nonNull).forEach(instruments::add);
        for (var redemption : current.redemptions().values()) {
            var spending = current.receipts().get(redemption.getRedeemedReceiptId());
            if (spending != null && spending.status() == ReceiptStatus.ACTIVE
                    && !spending.transactionId().equals(receipt.transactionId())
                    && instruments.contains(redemption.getRewardId()))
                throw new IllegalArgumentException("An active receipt uses this receipt's reward: "
                        + receiptReference(spending));
        }
        for (var link : current.rrUsageLinks().values()) {
            var spending = current.receipts().get(link.spentReceiptId());
            if (spending != null && spending.status() == ReceiptStatus.ACTIVE
                    && !spending.transactionId().equals(receipt.transactionId())
                    && (link.receiptEarning() ? earningIds.contains(link.sourceId()) : instruments.contains(link.sourceId())))
                throw new IllegalArgumentException("An active receipt uses this receipt's RR: "
                        + receiptReference(spending));
        }
        var transfers = new HashMap<UUID, ProviderRedemption>();
        current.balances().redemptionHistory().forEach(r -> transfers.put(r.redemptionId(), r));
        if (transfers.values().stream().anyMatch(r -> r.status() != ProviderRedemption.Status.CANCELLED
                && r.sourceEarningIds().stream().anyMatch(earningIds::contains)))
            throw new IllegalArgumentException("A recorded provider redemption references this receipt's earnings.");
    }

    private String receiptReference(ReceiptRevSnap receipt) {
        return receipt.metadata() != null && receipt.metadata().receiptNumber() != null
                ? receipt.metadata().receiptNumber() : receipt.transactionId().toString();
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

    @Override public List<ReceiptRecord> findRewardCredits(UUID receiptId,UUID earningId){return snapshot().receipts().values().stream().map(this::workingReceipt).filter(r->r.transaction().getObservations().earnings().stream().anyMatch(e->e.details()!=null&&receiptId.equals(e.details().originalReceiptId())&&earningId.equals(e.details().originalEarningId()))).toList();}
    @Override public List<ReceiptRecord> findImportDuplicates(ReceiptMeta metadata){
        Objects.requireNonNull(metadata);
        return snapshot().receipts().values().stream().filter(r->{var m=r.metadata();return m!=null&&metadata.retailer().trim().equalsIgnoreCase(m.retailer().trim())&&metadata.receiptNumber()!=null&&m.receiptNumber()!=null&&metadata.receiptNumber().replaceAll("[^A-Za-z0-9]","").equalsIgnoreCase(m.receiptNumber().replaceAll("[^A-Za-z0-9]",""));}).sorted((a,b)->a.transactionId().compareTo(b.transactionId())).map(this::workingReceipt).toList();
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
        return a.receiptNumber() != null && a.receiptNumber().equals(b.receiptNumber());
    }

    public List<RRUsageLink> rrUsageLinks() {
        return snapshot().rrUsageLinks().values().stream().sorted(java.util.Comparator.comparing(RRUsageLink::confirmedAt).thenComparing(RRUsageLink::sourceId)).toList();
    }
    public boolean rrUsageNeedsReview(RRUsageLink link) {
        try { validateUsageLink(snapshot(), link); return false; }
        catch (IllegalArgumentException failure) { return true; }
    }
    private void validateUsageLink(State current, RRUsageLink link) {
        var spent = current.receipts().get(link.spentReceiptId());
        if (spent == null || spent.status() != ReceiptStatus.ACTIVE) throw new IllegalArgumentException("Spending receipt not found.");
        var usage = spent.contents().observations().unidentifiedRR().stream().filter(u -> u.eventId().equals(link.usageEventId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Receipt-reported RR deduction not found."));
        if (usage.amount().compareTo(link.amount()) != 0) throw new IllegalArgumentException("Recorded deduction amount changed; review association.");
        java.time.LocalDate earnedDate = null;
        java.math.BigDecimal face;
        if (link.receiptEarning()) {
            var origin = current.receipts().values().stream().filter(r -> r.contents().observations().earnings().stream()
                    .anyMatch(e -> e.earningId().equals(link.sourceId()))).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Original receipt earning not found."));
            if (origin.status() != ReceiptStatus.ACTIVE) throw new IllegalArgumentException("Original earning receipt is deleted; restore it first.");
            if (origin.transactionId().equals(link.spentReceiptId())) throw new IllegalArgumentException("Choose an RR from an earlier purchase, not earned on the spending receipt.");
            var earning = origin.contents().observations().earnings().stream().filter(e -> e.earningId().equals(link.sourceId())).findFirst().orElseThrow();
            if (earning.kind() != coupons.receipts.ReceiptObservations.EarningKind.REGISTER_REWARD || earning.confirmed() == null
                    || earning.status() != coupons.promotions.RebateStatus.PAID) throw new IllegalArgumentException("Only a confirmed printed RR can be matched.");
            if (earning.instrumentId() != null && current.redemptions().values().stream().anyMatch(r -> r.getRewardId().equals(earning.instrumentId())))
                throw new IllegalArgumentException("This earning is already redeemed through its issued instrument.");
            face = earning.confirmed().amount(); earnedDate = origin.contents().observations().purchaseDate();
        } else {
            var reward = current.rewards().get(link.sourceId());
            if (reward == null || current.rewardVoids().containsKey(link.sourceId())) throw new IllegalArgumentException("Active physical RR not found.");
            if (current.redemptions().values().stream().anyMatch(r -> r.getRewardId().equals(link.sourceId()))) throw new IllegalArgumentException("This instrument is already redeemed.");
            if (current.receipts().values().stream().anyMatch(r -> r.contents().observations().earnings().stream()
                    .anyMatch(e -> link.sourceId().equals(e.instrumentId())))) throw new IllegalArgumentException("Match the original receipt earning for this instrument.");
            validateRewardOrigin(current, spent, reward);
            face = reward.getFaceValue(); earnedDate = reward.getEarnedDate();
        }
        if (face.compareTo(link.amount()) != 0) throw new IllegalArgumentException("Coupon face value must match the recorded deduction exactly.");
        var spentDate = spent.contents().observations().purchaseDate();
        if(current.balances().adjustments().stream().anyMatch(a->a.unavailable()&&link.sourceId().equals(a.unavailableSource())&&(spentDate==null||!a.date().isAfter(spentDate))))throw new IllegalArgumentException("This RR was marked unavailable before the purchase.");
        if (earnedDate != null && spentDate != null && earnedDate.isAfter(spentDate)) throw new IllegalArgumentException("RR cannot be used before it was earned.");
    }
    public RRUsageLink linkRRUsage(RRUsageLink link) {
        Objects.requireNonNull(link);
        synchronized(store) {
            var current=store.state; var previous=current.rrUsageLinks().get(link.sourceId());
            if(previous != null) {
                if(!previous.equals(link)) throw new IllegalArgumentException("This coupon is already matched; review its existing usage.");
                return previous;
            }
            if(current.rrUsageLinks().values().stream().anyMatch(l -> l.usageEventId().equals(link.usageEventId())))
                throw new IllegalArgumentException("This deduction is already matched to another coupon.");
            validateUsageLink(current,link);
            var links=new HashMap<>(current.rrUsageLinks()); links.put(link.sourceId(),link);
            var replacement=new State(current.receipts(),current.rewards(),current.redemptions(),current.evidence(),
                    current.history(),current.requests(),current.rewardVoids(),current.giftCardPromotions(),current.balances(),links);
            if(store.disk != null) {
                try { store.disk.save(replacement); }
                catch(java.io.IOException failure) { throw new IllegalStateException("Could not save RR association; nothing committed.",failure); }
            }
            store.state=replacement;return link;
        }
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
            boolean linked = current.rrUsageLinks().containsKey(id) || reward.getEarnedReceiptId() != null
                    || current.redemptions().values().stream().anyMatch(r -> r.getRewardId().equals(id))
                    || current.receipts().values().stream().anyMatch(r -> r.contents().observations().earnings().stream()
                            .anyMatch(e -> id.equals(e.instrumentId())));
            if (linked) throw new IllegalArgumentException("This RR is linked to a receipt and cannot be removed as a standalone mistake.");
            RewardVoid removal = new RewardVoid(id, reason, recordedBy, clock.instant());
            var removals = new HashMap<>(current.rewardVoids());
            removals.put(id, removal);
            State replacement = new State(current.receipts(), current.rewards(), current.redemptions(), current.evidence(),
                    current.history(), current.requests(), removals, current.giftCardPromotions(), current.balances(), current.rrUsageLinks());
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
                            current.redemptions(), current.evidence(), current.history(), current.requests(), current.rewardVoids(), current.giftCardPromotions(), current.balances(), current.rrUsageLinks());
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

    /** Commits an approved replacement and its historical before/after snapshots atomically. */
    @Override
    public ReceiptWriteResult update(UUID id, long revision, CouponTransaction transaction,
            ReceiptMeta metadata, List<ReceiptEvidence> evidence, ReceiptWriteRequest request,
            ReceiptChangeContext context) {
        UpdateInput input = new UpdateInput(id, revision, ReceiptSnapshot.from(transaction),
                metadata, evidence, request, context);
        synchronized (store) {
            State current = store.state;
            CommittedWrite committed = current.requests().get(request.requestId());
            if (committed != null) return committed.resultForMatching(input);
            ReceiptRevSnap before = current.receipts().get(id);
            if (before == null || before.status() != ReceiptStatus.ACTIVE) {
                throw new IllegalArgumentException("Active receipt not found.");
            }
            if (before.revision() != revision) {
                throw new java.util.ConcurrentModificationException("Receipt revision has changed.");
            }
            var evidenceIds = new java.util.ArrayList<>(before.evidenceIds());
            input.evidence().forEach(artifact -> evidenceIds.add(artifact.getEvidenceId()));
            long nextRevision = Math.addExact(revision, 1);
            ReceiptRevSnap after = new ReceiptRevSnap(id, nextRevision, ReceiptStatus.ACTIVE,
                    input.contents(), metadata, evidenceIds);
            validateWrite(current, after, input.evidence(), before);
            ReceiptWriteResult result = new ReceiptWriteResult(request.requestId(), id,
                    nextRevision, ReceiptChangeAction.UPDATED, ReceiptStatus.ACTIVE, clock.instant());
            ReceiptHistoryEntry entry = new ReceiptHistoryEntry(UUID.randomUUID(), request,
                    result, before, after, null, context);
            var receipts = new HashMap<>(current.receipts());
            var redemptions = new HashMap<>(current.redemptions());
            var artifacts = new HashMap<>(current.evidence());
            var history = new HashMap<>(current.history());
            var requests = new HashMap<>(current.requests());
            receipts.put(id, after);
            input.contents().registerRewardRedemptions().forEach(value -> redemptions.put(value.getRedemptionId(), value));
            input.evidence().forEach(value -> artifacts.put(value.getEvidenceId(), value));
            history.put(entry.historyId(), entry);
            requests.put(request.requestId(), new CommittedWrite(input, result));
            State replacement = new State(receipts, current.rewards(), redemptions, artifacts,
                    history, requests, current.rewardVoids(), receiptPromotions(current, after, before), current.balances(), current.rrUsageLinks());
            if (store.disk != null) {
                try { store.disk.save(replacement); }
                catch (java.io.IOException failure) {
                    throw new IllegalStateException("Could not write receipt update; nothing committed.", failure);
                }
            }
            store.state = replacement;
            return result;
        }
    }

    @Override
    public ReceiptWriteResult softDelete(UUID id, long revision, ReceiptWriteRequest request,
            ReceiptChangeContext context) {
        return changeStatus(new DeleteInput(id, revision, request, context), id, revision,
                request, context, ReceiptStatus.ACTIVE, ReceiptStatus.DELETED, ReceiptChangeAction.DELETED);
    }

    @Override
    public ReceiptWriteResult restore(UUID id, long revision, ReceiptWriteRequest request,
            ReceiptChangeContext context) {
        return changeStatus(new RestoreInput(id, revision, request, context), id, revision,
                request, context, ReceiptStatus.DELETED, ReceiptStatus.ACTIVE, ReceiptChangeAction.RESTORED);
    }

    private ReceiptWriteResult changeStatus(InMemoryStore.WriteInput input, UUID id, long revision,
            ReceiptWriteRequest request, ReceiptChangeContext context, ReceiptStatus expectedStatus,
            ReceiptStatus status, ReceiptChangeAction action) {
        synchronized (store) {
            var current = store.state;
            var committed = current.requests().get(request.requestId());
            if (committed != null) return committed.resultForMatching(input);
            var before = current.receipts().get(id);
            if (before == null || before.status() != expectedStatus)
                throw new IllegalArgumentException(expectedStatus == ReceiptStatus.ACTIVE
                        ? "Active receipt not found." : "Deleted receipt not found.");
            if (before.revision() != revision)
                throw new java.util.ConcurrentModificationException("Receipt revision has changed.");
            var after = new ReceiptRevSnap(id, Math.addExact(revision, 1), status,
                    before.contents(), before.metadata(), before.evidenceIds());
            validateStoredReferences(current, after, List.of(), before);
            if (status == ReceiptStatus.DELETED) validateDeletionDependencies(current, before);
            var result = new ReceiptWriteResult(request.requestId(), id, after.revision(), action, status, clock.instant());
            var entry = new ReceiptHistoryEntry(UUID.randomUUID(), request, result, before, after, null, context);
            var receipts = new HashMap<>(current.receipts()); receipts.put(id, after);
            var history = new HashMap<>(current.history()); history.put(entry.historyId(), entry);
            var requests = new HashMap<>(current.requests()); requests.put(request.requestId(), new CommittedWrite(input, result));
            var replacement = new State(receipts, current.rewards(), current.redemptions(), current.evidence(),
                    history, requests, current.rewardVoids(), current.giftCardPromotions(), current.balances(), current.rrUsageLinks());
            if (store.disk != null) {
                try { store.disk.save(replacement); }
                catch (java.io.IOException failure) {
                    throw new IllegalStateException("Could not change receipt status; nothing committed.", failure);
                }
            }
            store.state = replacement;
            return result;
        }
    }
}
