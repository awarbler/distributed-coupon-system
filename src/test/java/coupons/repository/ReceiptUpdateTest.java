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
}
