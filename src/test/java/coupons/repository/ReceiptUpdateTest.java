package coupons.repository;

import coupons.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies approved replacements, immutable history, and original retry results. */
class ReceiptUpdateTest {
    private final ReceiptCreateCtx creator = new ReceiptCreateCtx("aj");
    private final ReceiptChangeContext approval = new ReceiptChangeContext("aj", "Receipt correction", "aj", Instant.now());
    private ReceiptWriteRequest request() { return new ReceiptWriteRequest(UUID.randomUUID(), Instant.now()); }
    private CouponTransaction transaction(String total) {
        var value = new CouponTransaction(new ReceiptTotals(new BigDecimal("1.04"), new BigDecimal(total)));
        value.addPurchaseItem(new PurchaseItem("Recorded product", null, null, new BigDecimal("7.79"), 2));
        return value;
    }
    private void create(InMemoryTransactionRepository repo, UUID id, CouponTransaction value) {
        repo.save(id, value, null, List.of(), request(), creator);
    }
    @Test void preservesHistoryAndOriginalResultsAfterLaterUpdates() {
        var repo = new InMemoryTransactionRepository();
        var id = UUID.randomUUID(); var original = transaction("7.280");
        var creationRequest = request();
        var created = repo.save(id, original, null, List.of(), creationRequest, creator);
        var revised = repo.findById(id).orElseThrow().transaction();
        revised.addPayment(new Payment(PaymentType.CASH, BigDecimal.ONE));
        var expected = ReceiptSnapshot.from(revised); var req = request();
        var metadata = new ReceiptMeta("Walgreens", "000123", null, null);
        var evidence = new ReceiptEvidence(UUID.randomUUID(), id, "image/png", new byte[]{1,2});
        var result = repo.update(id, 1, revised, metadata, List.of(evidence), req, approval);
        assertEquals(2, result.revision());
        var entry = repo.history().findByRevision(id, 2).orElseThrow();
        assertEquals(repo.history().findByRevision(id, 1).orElseThrow().after(), entry.before());
        assertEquals(expected, entry.after().contents());
        assertEquals(approval, entry.changeCtx());
        revised.addPayment(new Payment(PaymentType.CARD, BigDecimal.TEN));
        assertEquals(expected, entry.after().contents());
        repo.update(id, 2, revised, null, List.of(), request(), approval);
        assertEquals(result, repo.update(id, 1, expected.toTransaction(), metadata,
                List.of(new ReceiptEvidence(evidence.getEvidenceId(), id, "image/png", new byte[]{1,2})), req, approval));
        assertEquals(created, repo.save(id, original, null, List.of(), creationRequest, creator));
        assertEquals(3, repo.history().findByReceiptId(id).size());
        assertNull(repo.findById(id).orElseThrow().metadata());
        assertEquals(List.of(evidence.getEvidenceId()), repo.findById(id).orElseThrow().evidenceIds());
    }
    @Test void staleMissingAndConflictingRequestsLeaveStateUnchanged() {
        var repo = new InMemoryTransactionRepository(); var id = UUID.randomUUID(); var value = transaction("3.60");
        var createRequest = request(); repo.save(id, value, null, List.of(), createRequest, creator);
        assertThrows(IllegalArgumentException.class, () -> repo.update(id, 1, value, null, List.of(), createRequest, approval));
        assertThrows(IllegalArgumentException.class, () -> repo.update(UUID.randomUUID(), 1, value, null, List.of(), request(), approval));
        assertThrows(IllegalArgumentException.class, () -> repo.update(id, 0, value, null, List.of(), request(), approval));
        var req = request(); repo.update(id, 1, value, null, List.of(), req, approval);
        assertThrows(ConcurrentModificationException.class, () -> repo.update(id, 1, value, null, List.of(), request(), approval));
        assertThrows(IllegalArgumentException.class, () -> repo.update(id, 2, value, null, List.of(), req, approval));
        assertThrows(IllegalArgumentException.class, () -> repo.update(id, 1, value, null, List.of(), req,
                new ReceiptChangeContext("aj", "Different reason", "aj", approval.approvedAt())));
        assertEquals(2, repo.history().findByReceiptId(id).size());
        assertEquals(2, repo.findById(id).orElseThrow().revision());
    }
    @Test void preservesExistingRedemptionsAndRejectsTheirRemovalOrAlteration() {
        var repo = new InMemoryTransactionRepository(); var id = UUID.randomUUID(); var value = transaction("3.60");
        var reward = new RegisterReward("Palmolive", BigDecimal.TEN, null, null, null);
        repo.issuedRewards().save(reward);
        var redemption = new RegisterRewardRedemption(reward.getRewardId(), id, BigDecimal.ONE, null);
        value.addRegisterRewardRedemption(redemption); create(repo, id, value);
        repo.update(id, 1, value, null, List.of(), request(), approval);
        assertThrows(IllegalArgumentException.class, () -> repo.update(id, 2, transaction("3.60"), null, List.of(), request(), approval));
        var altered = transaction("3.60");
        altered.addRegisterRewardRedemption(new RegisterRewardRedemption(redemption.getRedemptionId(), reward.getRewardId(), id, BigDecimal.TEN, null));
        assertThrows(IllegalArgumentException.class, () -> repo.update(id, 2, altered, null, List.of(), request(), approval));
        assertEquals(redemption, repo.redemptions().findByRewardId(reward.getRewardId()).orElseThrow());
        assertEquals(2, repo.history().findByReceiptId(id).size());
    }
    @Test void newEvidenceAndRedemptionsCommitTogetherOrNotAtAll() {
        var repo = new InMemoryTransactionRepository(); var id = UUID.randomUUID(); var value = transaction("3.60");
        create(repo, id, value);
        var evidence = new ReceiptEvidence(UUID.randomUUID(), id, "image/png", new byte[]{3});
        var reward = new RegisterReward("Lysol", BigDecimal.TEN, null, null, null);
        value.addRegisterRewardRedemption(new RegisterRewardRedemption(reward.getRewardId(), id, BigDecimal.ONE, null));
        var req = request();
        assertThrows(IllegalArgumentException.class, () -> repo.update(id, 1, value, null, List.of(evidence), req, approval));
        assertTrue(repo.evidence().findById(evidence.getEvidenceId()).isEmpty());
        assertTrue(repo.history().findByRequestId(req.requestId()).isEmpty());
        repo.issuedRewards().save(reward);
        repo.update(id, 1, value, null, List.of(evidence), req, approval);
        assertTrue(repo.redemptions().findByRewardId(reward.getRewardId()).isPresent());
        assertThrows(IllegalArgumentException.class, () -> repo.update(id, 2, value, null, List.of(evidence), request(), approval));
        assertEquals(2, repo.findById(id).orElseThrow().revision());
    }
    @Test void updatesAndRetryInputsSurviveRestart(@TempDir Path directory) throws Exception {
        var path = directory.resolve("receipts.bin"); var id = UUID.randomUUID(); var value = transaction("3.600");
        var req = request(); ReceiptWriteResult result;
        try (var repo = InMemoryTransactionRepository.openLocal(path)) {
            create(repo, id, value);
            result = repo.update(id, 1, value, null, List.of(), req, approval);
        }
        try (var repo = InMemoryTransactionRepository.openLocal(path)) {
            assertEquals(result, repo.update(id, 1, ReceiptSnapshot.from(value).toTransaction(), null, List.of(), req, approval));
            assertEquals(2, repo.findById(id).orElseThrow().revision());
            assertEquals(2, repo.history().findByReceiptId(id).size());
        }
    }
    @Test void failedUpdatePreservesDiskAndHistoryThenRetries(@TempDir Path directory) throws Exception {
        var file = directory.resolve("receipts.bin");
        var backup = directory.resolve("before.bin");
        var id = UUID.randomUUID(); var req = request();
        try (var repo = InMemoryTransactionRepository.openLocal(file)) {
            create(repo, id, transaction("3.60"));
            var before = repo.history().findByRevision(id, 1).orElseThrow().after();
            byte[] bytes = java.nio.file.Files.readAllBytes(file);
            java.nio.file.Files.move(file, backup);
            java.nio.file.Files.createDirectory(file);
            var revised = transaction("4.60");
            var evidence = new ReceiptEvidence(UUID.randomUUID(), id, "image/png", new byte[]{9});
            assertThrows(IllegalStateException.class, () -> repo.update(id, 1, revised, null, List.of(evidence), req, approval));
            assertEquals(before.contents(), ReceiptSnapshot.from(repo.findById(id).orElseThrow().transaction()));
            assertEquals(1, repo.findById(id).orElseThrow().revision());
            assertEquals(1, repo.history().findByReceiptId(id).size());
            assertTrue(repo.history().findByRequestId(req.requestId()).isEmpty());
            assertTrue(repo.evidence().findById(evidence.getEvidenceId()).isEmpty());
            assertArrayEquals(bytes, java.nio.file.Files.readAllBytes(backup));
            java.nio.file.Files.delete(file);
            java.nio.file.Files.move(backup, file);
            var result = repo.update(id, 1, revised, null, List.of(evidence), req, approval);
            assertEquals(result, repo.update(id, 1, revised, null, List.of(evidence), req, approval));
            assertEquals(2, repo.history().findByReceiptId(id).size());
        }
        try (var repo = InMemoryTransactionRepository.openLocal(file)) {
            assertEquals(2, repo.findById(id).orElseThrow().revision());
            assertEquals(2, repo.history().findByReceiptId(id).size());
        }
    }

    @Test void correctedPointAmountAndStatusPreserveIdentityAndHistoricalValuation() {
        var repo = new InMemoryTransactionRepository(); var id = UUID.randomUUID();
        var event = UUID.randomUUID(); var value = transaction("3.60");
        var valuation = new ReceiptObservations.RecordedValuation(new BigDecimal("1.25"),
                Currency.getInstance("USD"), "User recorded estimate");
        var points = new RebateBenefit(BenefitKind.POINTS, new BigDecimal("1250"), null, "Fetch");
        var original = new ReceiptObservations.Earning(event, ReceiptObservations.EarningKind.THIRD_PARTY_REBATE,
                "Fetch", "Offer", ReceiptObservations.Scope.RECEIPT, List.of(), points, null,
                RebateStatus.PENDING, null, null, null, List.of(), null, null, valuation);
        value.setObservations(new ReceiptObservations(null, null, List.of(original), List.of(), null, List.of(), Map.of()));
        create(repo, id, value);
        var confirmed = new RebateBenefit(BenefitKind.POINTS, new BigDecimal("1500"), null, "Fetch");
        var corrected = new ReceiptObservations.Earning(event, original.kind(), "Fetch", "Offer", original.scope(),
                List.of(), points, confirmed, RebateStatus.CREDITED, null, null, null, List.of(), null, null, valuation);
        value.setObservations(new ReceiptObservations(null, null, List.of(corrected), List.of(), null, List.of(), Map.of()));
        var req = request(); var result = repo.update(id, 1, value, null, List.of(), req, approval);
        assertEquals(original, repo.history().findByRevision(id, 1).orElseThrow().after().contents().observations().earnings().get(0));
        assertEquals(corrected, repo.findById(id).orElseThrow().transaction().getObservations().earnings().get(0));
        assertEquals(points, corrected.expected());
        assertEquals(valuation, corrected.valuation());
        assertEquals(event, corrected.earningId());
        assertEquals(result, repo.update(id, 1, value, null, List.of(), req, approval));
        assertThrows(ConcurrentModificationException.class, () -> repo.update(id, 1, value, null, List.of(), request(), approval));
        assertEquals(2, repo.history().findByReceiptId(id).size());
    }
    private record UpdateOutcome(ReceiptWriteResult result, RuntimeException failure) { }

    private UpdateOutcome attemptUpdate(InMemoryTransactionRepository repo, UUID id,
            CouponTransaction transaction, ReceiptMeta metadata, ReceiptEvidence evidence,
            ReceiptWriteRequest request) {
        try {
            return new UpdateOutcome(repo.update(id, 1, transaction, metadata,
                    List.of(evidence), request, approval), null);
        } catch (RuntimeException failure) {
            return new UpdateOutcome(null, failure);
        }
    }

    /** Release both callers together; use bounded waits rather than timing sleeps. */
    private <T> List<T> together(java.util.concurrent.Callable<T> first,
            java.util.concurrent.Callable<T> second) throws Exception {
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, java.util.concurrent.TimeUnit.SECONDS));
                return first.call();
            });
            var b = pool.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, java.util.concurrent.TimeUnit.SECONDS));
                return second.call();
            });
            try {
                assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
            } finally {
                start.countDown();
            }
            return List.of(a.get(10, java.util.concurrent.TimeUnit.SECONDS),
                    b.get(10, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    @Test void concurrentUpdatesToSameRevisionCommitOneCompleteProposal() throws Exception {
        var store = new InMemoryStore();
        var repo = new InMemoryTransactionRepository(store, java.time.Clock.systemUTC());
        var id = UUID.randomUUID();
        var originalEvidence = new ReceiptEvidence(UUID.randomUUID(), id, "image/png", new byte[]{9});
        repo.save(id, transaction("3.60"), new ReceiptMeta("Walgreens", "Original", null, null),
                List.of(originalEvidence), request(), creator);
        var original = repo.history().findByRevision(id, 1).orElseThrow().after();
        var first = original.contents().toTransaction();
        first.addPayment(new Payment(PaymentType.CASH, BigDecimal.ONE));
        var second = original.contents().toTransaction();
        second.addPayment(new Payment(PaymentType.CARD, new BigDecimal("2")));
        var proposals = List.of(first, second);
        var metadata = List.of(new ReceiptMeta("Walgreens", "First correction", null, null),
                new ReceiptMeta("Walgreens", "Second correction", null, null));
        var evidence = List.of(new ReceiptEvidence(UUID.randomUUID(), id, "image/png", new byte[]{1}),
                new ReceiptEvidence(UUID.randomUUID(), id, "image/png", new byte[]{2}));
        var requests = List.of(request(), request());
        var results = together(
                () -> attemptUpdate(repo, id, proposals.get(0), metadata.get(0), evidence.get(0), requests.get(0)),
                () -> attemptUpdate(repo, id, proposals.get(1), metadata.get(1), evidence.get(1), requests.get(1)));
        assertEquals(1, results.stream().filter(r -> r.result() != null).count());
        int winner = results.get(0).result() != null ? 0 : 1;
        int loser = 1 - winner;
        assertInstanceOf(ConcurrentModificationException.class, results.get(loser).failure());
        var receipt = repo.findById(id).orElseThrow();
        assertEquals(2, receipt.revision());
        assertEquals(ReceiptSnapshot.from(proposals.get(winner)), ReceiptSnapshot.from(receipt.transaction()));
        assertEquals(metadata.get(winner), receipt.metadata());
        assertEquals(List.of(originalEvidence.getEvidenceId(), evidence.get(winner).getEvidenceId()), receipt.evidenceIds());
        assertEquals(original, repo.history().findByRevision(id, 1).orElseThrow().after());
        assertEquals(original, repo.history().findByRevision(id, 2).orElseThrow().before());
        assertEquals(2, repo.history().findByReceiptId(id).size());
        assertEquals(evidence.get(winner), repo.evidence().findById(evidence.get(winner).getEvidenceId()).orElseThrow());
        assertTrue(repo.evidence().findById(evidence.get(loser).getEvidenceId()).isEmpty());
        assertTrue(repo.history().findByRequestId(requests.get(loser).requestId()).isEmpty());
        assertFalse(store.state.requests().containsKey(requests.get(loser).requestId()));
        assertEquals(2, store.state.requests().size());
        assertEquals(2, store.state.evidence().size());
        assertTrue(store.state.redemptions().isEmpty());
        var committed = store.state;
        assertEquals(results.get(winner).result(), repo.update(id, 1, proposals.get(winner), metadata.get(winner),
                List.of(evidence.get(winner)), requests.get(winner), approval));
        assertThrows(ConcurrentModificationException.class, () -> repo.update(id, 1, proposals.get(loser), metadata.get(loser),
                List.of(evidence.get(loser)), requests.get(loser), approval));
        assertSame(committed, store.state);
    }

    @Test void concurrentUpdatesOfDifferentReceiptsCannotRedeemSameReward() throws Exception {
        var store = new InMemoryStore();
        var repo = new InMemoryTransactionRepository(store, java.time.Clock.systemUTC());
        var reward = new RegisterReward("Shared physical RR", new BigDecimal("5"), null, null, null);
        repo.issuedRewards().save(reward);
        var ids = List.of(UUID.randomUUID(), UUID.randomUUID());
        var proposals = new ArrayList<CouponTransaction>();
        var originals = new ArrayList<ReceiptRevSnap>();
        var redemptions = new ArrayList<RegisterRewardRedemption>();
        var evidence = new ArrayList<ReceiptEvidence>();
        var metadata = new ArrayList<ReceiptMeta>();
        var requests = List.of(request(), request());
        for (int i = 0; i < 2; i++) {
            var id = ids.get(i);
            var initialEvidence = new ReceiptEvidence(UUID.randomUUID(), id, "image/png", new byte[]{9});
            repo.save(id, transaction("3.60"), new ReceiptMeta("Walgreens", "Original-" + i, null, null),
                    List.of(initialEvidence), request(), creator);
            var before = repo.history().findByRevision(id, 1).orElseThrow().after(); originals.add(before);
            var proposed = before.contents().toTransaction();
            var redemption = new RegisterRewardRedemption(reward.getRewardId(), id, new BigDecimal("5"), null);
            proposed.addRegisterRewardRedemption(redemption);
            proposed.addPayment(new Payment(PaymentType.CASH, BigDecimal.valueOf(i + 1)));
            redemptions.add(redemption); proposals.add(proposed);
            evidence.add(new ReceiptEvidence(UUID.randomUUID(), id, "image/png", new byte[]{(byte)i}));
            metadata.add(new ReceiptMeta("Walgreens", "Corrected-" + i, null, null));
        }
        var results = together(
                () -> attemptUpdate(repo, ids.get(0), proposals.get(0), metadata.get(0), evidence.get(0), requests.get(0)),
                () -> attemptUpdate(repo, ids.get(1), proposals.get(1), metadata.get(1), evidence.get(1), requests.get(1)));
        assertEquals(1, results.stream().filter(r -> r.result() != null).count());
        int winner = results.get(0).result() != null ? 0 : 1;
        int loser = 1 - winner;
        assertInstanceOf(IllegalArgumentException.class, results.get(loser).failure());
        var accepted = repo.findById(ids.get(winner)).orElseThrow();
        assertEquals(2, accepted.revision());
        assertEquals(ReceiptSnapshot.from(proposals.get(winner)), ReceiptSnapshot.from(accepted.transaction()));
        assertEquals(metadata.get(winner), accepted.metadata());
        assertEquals(List.of(originals.get(winner).evidenceIds().get(0), evidence.get(winner).getEvidenceId()), accepted.evidenceIds());
        assertEquals(originals.get(loser), store.state.receipts().get(ids.get(loser)));
        assertEquals(1, repo.findById(ids.get(loser)).orElseThrow().revision());
        assertEquals(1, repo.history().findByReceiptId(ids.get(loser)).size());
        assertEquals(2, repo.history().findByReceiptId(ids.get(winner)).size());
        for (int i = 0; i < 2; i++) assertEquals(originals.get(i), repo.history().findByRevision(ids.get(i), 1).orElseThrow().after());
        assertEquals(redemptions.get(winner), repo.redemptions().findByRewardId(reward.getRewardId()).orElseThrow());
        assertTrue(repo.redemptions().findById(redemptions.get(loser).getRedemptionId()).isEmpty());
        assertEquals(evidence.get(winner), repo.evidence().findById(evidence.get(winner).getEvidenceId()).orElseThrow());
        assertTrue(repo.evidence().findById(evidence.get(loser).getEvidenceId()).isEmpty());
        assertTrue(repo.history().findByRequestId(requests.get(loser).requestId()).isEmpty());
        assertFalse(store.state.requests().containsKey(requests.get(loser).requestId()));
        assertEquals(3, store.state.requests().size());
        assertEquals(3, store.state.history().size());
        assertEquals(3, store.state.evidence().size());
        assertEquals(1, store.state.redemptions().size());
        var committed = store.state;
        assertEquals(results.get(winner).result(), repo.update(ids.get(winner), 1, proposals.get(winner), metadata.get(winner),
                List.of(evidence.get(winner)), requests.get(winner), approval));
        assertThrows(IllegalArgumentException.class, () -> repo.update(ids.get(loser), 1, proposals.get(loser), metadata.get(loser),
                List.of(evidence.get(loser)), requests.get(loser), approval));
        assertSame(committed, store.state);
    }

}
