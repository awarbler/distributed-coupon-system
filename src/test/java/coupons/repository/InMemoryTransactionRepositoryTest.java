package coupons.repository;

import coupons.receipts.CouponTransaction;
import coupons.receipts.Payment;
import coupons.receipts.PaymentType;
import coupons.receipts.PurchaseItem;
import coupons.receipts.ReceiptTotals;
import coupons.rewards.RegisterReward;
import coupons.rewards.RegisterRewardRedemption;
import coupons.receipts.TransactionCalculator;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Tests observable receipt persistence, retries, related records, and conflicts. */
class InMemoryTransactionRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final ReceiptCreateCtx ACTOR = new ReceiptCreateCtx("test-user");

    @Test void unsupportedLinkedSaveDoesNotPublishReceiptEvidenceOrHistory() throws Exception {
        try (var memory = new InMemoryTransactionRepository()) {
            TransactionRepository storage = memory;
            for (int kind = 0; kind < 3; kind++) {
                UUID id = UUID.randomUUID();
                var evidence = new ReceiptEvidence(UUID.randomUUID(), id, "text/plain", new byte[]{1});
                var couponIds = kind == 0 ? List.of(UUID.randomUUID()) : List.<UUID>of();
                UUID plan = kind == 1 ? UUID.randomUUID() : null;
                UUID draft = kind == 2 ? id : null;
                assertThrows(IllegalArgumentException.class, () -> storage.saveWithDraft(id,
                        transaction(), null, List.of(evidence), request(), ACTOR, couponIds, plan, 1, draft, 1));
                assertTrue(storage.findById(id).isEmpty());
                assertTrue(memory.history().findByReceiptId(id).isEmpty());
                assertTrue(memory.evidence().findById(evidence.getEvidenceId()).isEmpty());
            }
            UUID id = UUID.randomUUID();
            storage.saveWithDraft(id, transaction(), null, List.of(), request(), ACTOR, List.of(), null, 0, null, 0);
            assertTrue(storage.findById(id).isPresent());
        }
    }

    /** Creates a new logical write identity. */
    private ReceiptWriteRequest request() {
        return new ReceiptWriteRequest(UUID.randomUUID(), NOW);
    }

    /** Creates an illustrative transaction with genuinely unknown optional data. */
    private CouponTransaction transaction() {
        CouponTransaction transaction = new CouponTransaction(new ReceiptTotals(
                new BigDecimal("0.270"), new BigDecimal("5.280")));
        transaction.addPurchaseItem(new PurchaseItem("Product", null, null,
                new BigDecimal("5.000"), 1));
        return transaction;
    }

    /** Saves a simple receipt using a new logical request. */
    private ReceiptWriteResult save(InMemoryTransactionRepository repo, UUID id,
            CouponTransaction transaction) {
        return repo.save(id, transaction, null, List.of(), request(), ACTOR);
    }

    /** Verifies identity, unknown values, exact scale, history, and discrepancy. */
    @Test
    void savesRecordedFactsWithoutRecalculation() {
        var repo = new InMemoryTransactionRepository(CLOCK);
        UUID id = UUID.randomUUID();
        var original = transaction();
        var req = request();
        var meta = new ReceiptMeta(" H-E-B ", "000123", null, null);
        var result = repo.save(id, original, meta, List.of(), req, ACTOR);
        var stored = repo.findById(id).orElseThrow();
        assertEquals(id, stored.transactionId());
        assertEquals(1L, stored.revision());
        assertEquals(ReceiptStatus.ACTIVE, stored.status());
        assertEquals(meta, stored.metadata());
        assertEquals(ReceiptSnapshot.from(original), ReceiptSnapshot.from(stored.transaction()));
        assertNull(stored.transaction().getPurchaseItems().get(0).getProductCode());
        assertNull(stored.transaction().getPurchaseItems().get(0).getRegularUnitPrice());
        assertEquals(new BigDecimal("5.280"), stored.transaction().getReceiptTotals().getFinalTotal());
        assertEquals(NOW, result.committedAt());
        var entry = repo.history().findByRequestId(req.requestId()).orElseThrow();
        assertEquals(result, entry.result());
        assertEquals(ACTOR, entry.createCtx());
        assertNull(entry.before());
        assertEquals(entry, repo.history().findById(entry.historyId()).orElseThrow());
        assertEquals(entry, repo.history().findByRevision(id, 1).orElseThrow());
        var calculator = new TransactionCalculator();
        assertEquals(0, new BigDecimal("5.27").compareTo(calculator.calculateExpectedCheckoutAmount(stored.transaction())));
        assertEquals(0, new BigDecimal("0.01").compareTo(calculator.calculateReceiptDifference(stored.transaction())));
        assertThrows(IllegalStateException.class, () -> calculator.calculateRegularMerchandiseTotal(stored.transaction()));
    }

    /** Verifies input and output mutations cannot change committed history. */
    @Test
    void isolatesMutableWorkingTransactions() {
        var repo = new InMemoryTransactionRepository(CLOCK);
        UUID id = UUID.randomUUID();
        var original = transaction();
        var expected = ReceiptSnapshot.from(original);
        save(repo, id, original);
        original.addPayment(new Payment(PaymentType.CASH, BigDecimal.ONE));
        var retrieved = repo.findById(id).orElseThrow().transaction();
        assertEquals(expected, ReceiptSnapshot.from(retrieved));
        retrieved.addPurchaseItem(new PurchaseItem("Extra", null, null, BigDecimal.ONE, 1));
        assertEquals(expected, ReceiptSnapshot.from(repo.findById(id).orElseThrow().transaction()));
        assertEquals(expected, repo.history().findByRevision(id, 1).orElseThrow().after().contents());
    }

    /** Verifies equivalent separately constructed requests return the original result. */
    @Test
    void matchingRetryUsesValuesAndDoesNotRepeatWrites() {
        var repo = new InMemoryTransactionRepository(CLOCK);
        UUID id = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        var transaction = transaction();
        var captured = ReceiptSnapshot.from(transaction);
        var req = request();
        var first = repo.save(id, transaction, null,
                List.of(new ReceiptEvidence(artifactId, id, "text/plain", new byte[]{1, 2})), req, ACTOR);
        var retry = repo.save(id, captured.toTransaction(), null,
                List.of(new ReceiptEvidence(artifactId, id, "text/plain", new byte[]{1, 2})),
                new ReceiptWriteRequest(req.requestId(), req.requestedAt()), new ReceiptCreateCtx("test-user"));
        assertEquals(first, retry);
        assertEquals(1, repo.history().findByReceiptId(id).size());
        assertEquals(1, repo.evidence().findByReceiptId(id).size());
    }

    /** Verifies all original submitted values participate in retry comparison. */
    @Test
    void rejectsChangedRetryContentsWithoutPublishing() {
        var store = new InMemoryStore();
        var repo = new InMemoryTransactionRepository(store, CLOCK);
        UUID id = UUID.randomUUID();
        var transaction = transaction();
        var captured = ReceiptSnapshot.from(transaction);
        var req = request();
        var meta = new ReceiptMeta("Store", "001", null, null);
        var a = new ReceiptEvidence(UUID.randomUUID(), id, "text/plain", new byte[]{1});
        var b = new ReceiptEvidence(UUID.randomUUID(), id, "text/plain", new byte[]{2});
        repo.save(id, transaction, meta, List.of(a, b), req, ACTOR);
        InMemoryStore.State before = store.state;
        var changed = captured.toTransaction();
        changed.addPayment(new Payment(PaymentType.CASH, BigDecimal.ONE));
        List<Callable<ReceiptWriteResult>> conflicts = List.of(
                () -> repo.save(id, changed, meta, List.of(a, b), req, ACTOR),
                () -> repo.save(id, captured.toTransaction(), null, List.of(a, b), req, ACTOR),
                () -> repo.save(id, captured.toTransaction(), meta, List.of(a, b), req, new ReceiptCreateCtx("other")),
                () -> repo.save(id, captured.toTransaction(), meta, List.of(a, b),
                        new ReceiptWriteRequest(req.requestId(), NOW.plusSeconds(1)), ACTOR),
                () -> repo.save(id, captured.toTransaction(), meta, List.of(b, a), req, ACTOR),
                () -> repo.save(id, captured.toTransaction(), meta, List.of(
                        new ReceiptEvidence(a.getEvidenceId(), id, "text/plain", new byte[]{9}), b), req, ACTOR),
                () -> repo.save(UUID.randomUUID(), captured.toTransaction(), meta, List.of(), req, ACTOR));
        for (var conflict : conflicts) {
            assertThrows(IllegalArgumentException.class, conflict::call);
            assertSame(before, store.state);
        }
        assertThrows(IllegalArgumentException.class, () -> save(repo, id, captured.toTransaction()));
        assertSame(before, store.state);
    }

    /** Verifies failed reference validation does not consume the request identity. */
    @Test
    void missingRewardCanBeRecordedThenSameRequestRetried() {
        var store = new InMemoryStore();
        var repo = new InMemoryTransactionRepository(store, CLOCK);
        UUID id = UUID.randomUUID();
        UUID rewardId = UUID.randomUUID();
        var transaction = transaction();
        var redemption = new RegisterRewardRedemption(rewardId, id, BigDecimal.ONE, null);
        transaction.addRegisterRewardRedemption(redemption);
        var artifact = new ReceiptEvidence(UUID.randomUUID(), id, "text/plain", new byte[]{1});
        var req = request();
        var before = store.state;
        assertThrows(IllegalArgumentException.class, () -> repo.save(id, transaction, null, List.of(artifact), req, ACTOR));
        assertSame(before, store.state);
        repo.issuedRewards().save(new RegisterReward(rewardId, "Recorded reward", BigDecimal.ONE, null, null, null));
        repo.save(id, transaction, null, List.of(artifact), req, ACTOR);
        assertEquals(redemption, repo.redemptions().findById(redemption.getRedemptionId()).orElseThrow());
        assertEquals(redemption, repo.redemptions().findByRewardId(rewardId).orElseThrow());
        assertEquals(List.of(redemption), repo.redemptions().findByReceiptId(id));
        assertEquals(1, repo.history().findByReceiptId(id).size());
    }

    /** Verifies evidence ownership, global uniqueness, and byte isolation. */
    @Test
    void protectsEvidenceAndRejectsInvalidBatches() {
        var store = new InMemoryStore();
        var repo = new InMemoryTransactionRepository(store, CLOCK);
        UUID id = UUID.randomUUID();
        byte[] bytes = {1, 2, 3};
        UUID artifactId = UUID.randomUUID();
        var artifact = new ReceiptEvidence(artifactId, id, "image/png", bytes);
        bytes[0] = 9;
        var batch = new ArrayList<>(List.of(artifact));
        repo.save(id, transaction(), null, batch, request(), ACTOR);
        batch.clear();
        var returned = repo.evidence().findById(artifactId).orElseThrow().getContent();
        returned[0] = 8;
        assertArrayEquals(new byte[]{1, 2, 3}, repo.evidence().findById(artifactId).orElseThrow().getContent());
        var before = store.state;
        UUID next = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> repo.save(next, transaction(), null,
                List.of(artifact), request(), ACTOR));
        var duplicateId = new ReceiptEvidence(artifactId, next, "image/png", new byte[]{4});
        assertThrows(IllegalArgumentException.class, () -> repo.save(next, transaction(), null,
                List.of(duplicateId), request(), ACTOR));
        var fresh = new ReceiptEvidence(UUID.randomUUID(), next, "image/png", new byte[]{4});
        assertThrows(IllegalArgumentException.class, () -> repo.save(next, transaction(), null,
                List.of(fresh, fresh), request(), ACTOR));
        assertSame(before, store.state);
        assertThrows(UnsupportedOperationException.class, () -> repo.evidence().findByReceiptId(id).clear());
    }

    /** Verifies immutable history remains readable when working restoration fails. */
    @Test
    void historyDoesNotDependOnWorkingReconstruction() {
        var store = new InMemoryStore();
        var repo = new InMemoryTransactionRepository(store, CLOCK);
        UUID id = UUID.randomUUID();
        var base = transaction();
        base.addPurchaseItem(new PurchaseItem("Other", "CODE", BigDecimal.ONE, BigDecimal.ONE, 1) {
            @Override
            public String getProductName() {
                return "  HEB MILK  ";
            }
        });
        var artifact = new ReceiptEvidence(UUID.randomUUID(), id, "text/plain", new byte[]{1});
        repo.save(id, base, null, List.of(artifact), request(), ACTOR);
        var before = store.state;
        assertThrows(IllegalArgumentException.class, () -> repo.findById(id));
        var history = repo.history().findByRevision(id, 1).orElseThrow();
        assertEquals("  HEB MILK  ", history.after().contents().purchaseItems().get(1).productName());
        assertEquals(List.of(artifact.getEvidenceId()), history.after().evidenceIds());
        assertEquals(artifact, repo.evidence().findById(artifact.getEvidenceId()).orElseThrow());
        assertSame(before, store.state);
    }

    /** Verifies exact duplicate-candidate rules, missing lookups, and immutable results. */
    @Test
    void lookupContractsAreExplicit() {
        var repo = new InMemoryTransactionRepository(CLOCK);
        UUID missing = UUID.randomUUID();
        assertTrue(repo.findById(missing).isEmpty());
        assertTrue(repo.findDeletedById(missing).isEmpty());
        assertTrue(repo.findDeleted().isEmpty());
        assertTrue(repo.history().findByReceiptId(missing).isEmpty());
        assertTrue(repo.evidence().findById(missing).isEmpty());
        assertTrue(repo.redemptions().findByRewardId(missing).isEmpty());
        assertTrue(repo.issuedRewards().findById(missing).isEmpty());
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var time = LocalDateTime.of(2026, 10, 17, 10, 0);
        repo.save(first, transaction(), new ReceiptMeta("Store", "001", time, "A"), List.of(), request(), ACTOR);
        repo.save(second, transaction(), new ReceiptMeta("Store", "001", time, "B"), List.of(), request(), ACTOR);
        var candidates = repo.findPossibleDuplicates(new ReceiptMeta("Store", "001", null, "A"));
        assertEquals(List.of(first), candidates.stream().map(ReceiptRecord::transactionId).toList());
        assertTrue(repo.findPossibleDuplicates(new ReceiptMeta("store", "001", null, null)).isEmpty());
        assertTrue(repo.findPossibleDuplicates(new ReceiptMeta("Store", null, time, null)).isEmpty());
        assertTrue(repo.findPossibleDuplicates(new ReceiptMeta("Store", "002", time, "A")).isEmpty());
        assertThrows(UnsupportedOperationException.class, candidates::clear);
        assertThrows(IllegalArgumentException.class, () -> repo.findPossibleDuplicates(new ReceiptMeta("Store", null, null, null)));
        assertThrows(NullPointerException.class, () -> repo.findById(null));
        assertThrows(IllegalArgumentException.class, () -> repo.history().findByRevision(first, 0));
    }

    /** Verifies cross-receipt reward and redemption-ID conflicts leave state unchanged. */
    @Test
    void preventsRewardReuseAndRewardOverwrite() {
        var store = new InMemoryStore();
        var repo = new InMemoryTransactionRepository(store, CLOCK);
        var reward = new RegisterReward("Known source", new BigDecimal("5.00"), null, null, null);
        repo.issuedRewards().save(reward);
        assertThrows(IllegalArgumentException.class, () -> repo.issuedRewards().save(reward));
        assertThrows(IllegalArgumentException.class, () -> repo.issuedRewards().save(new RegisterReward(
                "Unknown receipt", BigDecimal.ONE, null, null, UUID.randomUUID())));
        UUID first = UUID.randomUUID();
        var a = transaction();
        var redemption = new RegisterRewardRedemption(reward.getRewardId(), first, new BigDecimal("4.50"), null);
        a.addRegisterRewardRedemption(redemption);
        save(repo, first, a);
        var before = store.state;
        UUID second = UUID.randomUUID();
        var b = transaction();
        b.addRegisterRewardRedemption(new RegisterRewardRedemption(reward.getRewardId(), second, BigDecimal.ONE, null));
        assertThrows(IllegalArgumentException.class, () -> save(repo, second, b));
        assertSame(before, store.state);
        var other = new RegisterReward("Other source", BigDecimal.ONE, null, null, first);
        repo.issuedRewards().save(other);
        var c = transaction();
        c.addRegisterRewardRedemption(new RegisterRewardRedemption(redemption.getRedemptionId(),
                other.getRewardId(), second, BigDecimal.ONE, null));
        before = store.state;
        assertThrows(IllegalArgumentException.class, () -> save(repo, second, c));
        assertSame(before, store.state);
        var wrongOwner = transaction();
        wrongOwner.addRegisterRewardRedemption(new RegisterRewardRedemption(other.getRewardId(), first, BigDecimal.ONE, null));
        assertThrows(IllegalArgumentException.class, () -> save(repo, second, wrongOwner));
    }

    /** Runs two calls simultaneously with a bounded wait and no timing sleeps. */
    private <T> List<T> together(Callable<T> first, Callable<T> second) throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            Callable<T> a = () -> { ready.countDown(); start.await(); return first.call(); };
            Callable<T> b = () -> { ready.countDown(); start.await(); return second.call(); };
            var x = pool.submit(a);
            var y = pool.submit(b);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            return List.of(x.get(5, TimeUnit.SECONDS), y.get(5, TimeUnit.SECONDS));
        }
    }

    /** Verifies simultaneous matching retries produce one result and history entry. */
    @Test
    void concurrentMatchingRequestsCommitOnce() throws Exception {
        var repo = new InMemoryTransactionRepository(CLOCK);
        UUID id = UUID.randomUUID();
        var snapshot = ReceiptSnapshot.from(transaction());
        var req = request();
        Callable<ReceiptWriteResult> save = () -> repo.save(id, snapshot.toTransaction(), null, List.of(), req, ACTOR);
        var results = together(save, save);
        assertEquals(results.get(0), results.get(1));
        assertEquals(1, repo.history().findByReceiptId(id).size());
    }

    /** Verifies simultaneous independent saves cannot overwrite the same receipt. */
    @Test
    void concurrentNewRequestsCannotOverwriteReceipt() throws Exception {
        var repo = new InMemoryTransactionRepository(CLOCK);
        UUID id = UUID.randomUUID();
        Callable<Boolean> attempt = () -> {
            try {
                save(repo, id, transaction());
                return true;
            } catch (IllegalArgumentException expected) {
                return false;
            }
        };
        var results = together(attempt, attempt);
        assertEquals(1L, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, repo.history().findByReceiptId(id).size());
        assertEquals(1L, repo.findById(id).orElseThrow().revision());
    }

    /** Verifies required input and lookup validation does not publish a request. */
    @Test
    void nullArgumentsDoNotChangeState() {
        var store = new InMemoryStore();
        var repo = new InMemoryTransactionRepository(store, CLOCK);
        UUID id = UUID.randomUUID();
        var req = request();
        var before = store.state;
        assertThrows(NullPointerException.class, () -> repo.save(null, transaction(), null, List.of(), req, ACTOR));
        assertThrows(NullPointerException.class, () -> repo.save(id, null, null, List.of(), req, ACTOR));
        assertThrows(NullPointerException.class, () -> repo.save(id, transaction(), null, null, req, ACTOR));
        assertThrows(NullPointerException.class, () -> repo.save(id, transaction(), null, List.of(), null, ACTOR));
        assertThrows(NullPointerException.class, () -> repo.save(id, transaction(), null, List.of(), req, null));
        assertThrows(NullPointerException.class, () -> repo.save(id, transaction(), null,
                java.util.Arrays.asList((ReceiptEvidence) null), req, ACTOR));
        assertThrows(NullPointerException.class, () -> repo.issuedRewards().save(null));
        assertSame(before, store.state);
    }

    /** Verifies conflicting concurrent requests cannot redeem the same reward twice. */
    @Test
    void concurrentRewardUsesCommitAtMostOnce() throws Exception {
        var repo = new InMemoryTransactionRepository(CLOCK);
        var reward = new RegisterReward("Existing instrument", BigDecimal.ONE, null, null, null);
        repo.issuedRewards().save(reward);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        java.util.function.Function<UUID, Boolean> attempt = id -> {
            var transaction = transaction();
            transaction.addRegisterRewardRedemption(new RegisterRewardRedemption(reward.getRewardId(), id, BigDecimal.ONE, null));
            try {
                save(repo, id, transaction);
                return true;
            } catch (IllegalArgumentException expected) {
                return false;
            }
        };
        var results = together(() -> attempt.apply(a), () -> attempt.apply(b));
        assertEquals(1L, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, repo.history().findByReceiptId(a).size() + repo.history().findByReceiptId(b).size());
    }

    /** Verifies unexpected preparation failure publishes none of the related maps. */
    @Test
    void clockFailureCannotPartiallyCommit() {
        var store = new InMemoryStore();
        Clock failing = new Clock() {
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { throw new IllegalStateException("Clock unavailable"); }
        };
        var repo = new InMemoryTransactionRepository(store, failing);
        var before = store.state;
        UUID id = UUID.randomUUID();
        assertThrows(IllegalStateException.class, () -> repo.save(id, transaction(), null,
                List.of(new ReceiptEvidence(UUID.randomUUID(), id, "text/plain", new byte[]{1})), request(), ACTOR));
        assertSame(before, store.state);
    }

    /** Verifies deletion and restoration reject missing approval context. */
    @Test
    void lifecycleOperationsRequireApprovalContext() {
        var repo = new InMemoryTransactionRepository(CLOCK);
        UUID id = UUID.randomUUID();
        assertThrows(NullPointerException.class, () -> repo.softDelete(id, 1, request(), null));
        assertThrows(NullPointerException.class, () -> repo.restore(id, 1, request(), null));
    }
}
