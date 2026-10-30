package coupons.repository;

import coupons.promotions.BenefitKind;
import coupons.promotions.CouponApplied;
import coupons.receipts.CouponTransaction;
import coupons.receipts.Payment;
import coupons.receipts.PaymentType;
import coupons.rewards.ProviderBalanceSummary;
import coupons.receipts.PurchaseItem;
import coupons.promotions.RebateBenefit;
import coupons.promotions.RebateStatus;
import coupons.receipts.ReceiptObservations;
import coupons.receipts.ReceiptTotals;
import coupons.rewards.RegisterReward;
import coupons.rewards.RegisterRewardRedemption;

import coupons.receipts.ReceiptObservations.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ReceiptLifecycleTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 4);
    private static final Currency USD = Currency.getInstance("USD");
    private BigDecimal n(String value) { return new BigDecimal(value); }
    private ReceiptWriteRequest request() { return new ReceiptWriteRequest(UUID.randomUUID(), Instant.now()); }
    private ReceiptChangeContext approval() { return new ReceiptChangeContext("Owner", "Lifecycle verification", "Owner", Instant.now()); }
    private ReceiptMeta metadata() { return new ReceiptMeta("Walgreens", "Lifecycle", DAY.atTime(9, 40), "Store"); }
    private Earning earned(EarningKind kind, BenefitKind form, String amount) {
        return new Earning(UUID.randomUUID(), kind, "Walgreens", "Recorded offer", Scope.RECEIPT, List.of(), null,
                new RebateBenefit(form, n(amount), USD, "Walgreens"), RebateStatus.PAID, null, null, null, List.of());
    }
    private CouponTransaction transaction(UUID id, RegisterReward reward) {
        var tx = new CouponTransaction(new ReceiptTotals(n("0.50"), n("6.50")));
        var product = new PurchaseItem("Product", "000123", n("10"), n("10"), 1); tx.addPurchaseItem(product);
        var coupon = new CouponApplied("MQ", n("2"), List.of(product.getLineItemId())); tx.addAppliedCoupon(coupon);
        if (reward != null) tx.addRegisterRewardRedemption(new RegisterRewardRedemption(reward.getRewardId(), id, n("2"), DAY));
        tx.addPayment(new Payment(PaymentType.CASH, n("6.50")));
        tx.setObservations(new ReceiptObservations(DAY, "9:40 AM", List.of(
                earned(EarningKind.REGISTER_REWARD, BenefitKind.STORE_COUPON, "5"),
                earned(EarningKind.WALGREENS_CASH, BenefitKind.STORE_REWARD, "2")), List.of(),
                new WalgreensCashAccount(n("100"), n("0"), n("2"), n("102")), List.of(),
                Map.of(coupon.getApplicationId(), DiscountKind.MANUFACTURER)));
        return tx;
    }
    private List<ReceiptRecord> records(InMemoryTransactionRepository repo) {
        return repo.creationHistory().stream().map(h -> h.after().transactionId())
                .map(id -> repo.findById(id).or(() -> repo.findDeletedById(id)).orElseThrow()).toList();
    }

    @Test void completeLifecyclePreservesFinancialIdsEvidenceReportedValuesAndLateRetries(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("receipts.bin"); UUID id = UUID.randomUUID();
        var reward = new RegisterReward("Opening RR", n("2"), DAY.minusDays(1), null, null);
        var tx = transaction(id, reward); var original = ReceiptSnapshot.from(tx);
        var artifact = new ReceiptEvidence(UUID.randomUUID(), id, "image/png", new byte[]{1, 2, 3});
        var create = request(); var update = request(); var delete = request(); var restore = request();
        var context = approval(); var creator = new ReceiptCreateCtx("Owner");
        ReceiptWriteResult created, updated, deleted, restored; ReceiptSnapshot corrected;
        try (var repo = InMemoryTransactionRepository.openLocal(file)) {
            repo.issuedRewards().save(reward);
            var balances = new ProviderBalanceSummary(repo);
            balances.recordOpening(DAY.withDayOfMonth(1), "Walgreens Cash", n("100"), true);
            balances.recordOpening(DAY.withDayOfMonth(1), "Register Rewards", n("10"), true);
            created = repo.save(id, tx, metadata(), List.of(artifact), create, creator);
            assertEquals(original, ReceiptSnapshot.from(repo.findById(id).orElseThrow().transaction()));
            var working = repo.findById(id).orElseThrow().transaction(); working.addPayment(new Payment(PaymentType.CARD, n("1")));
            corrected = ReceiptSnapshot.from(working);
            updated = repo.update(id, 1, working, metadata(), List.of(), update, context);
            assertEquals(2, updated.revision());
            assertEquals(original, repo.history().findByRevision(id, 1).orElseThrow().after().contents());
            assertEquals(0, n("102").compareTo(balances.balance(DAY, "Walgreens Cash", records(repo)).endUsd()));
            assertEquals(0, n("13").compareTo(balances.balance(DAY, "Register Rewards", records(repo)).endUsd()));
            deleted = repo.softDelete(id, 2, delete, context);
            assertTrue(repo.findById(id).isEmpty()); assertEquals(1, repo.findDeleted().size());
            assertEquals(corrected, ReceiptSnapshot.from(repo.findDeletedById(id).orElseThrow().transaction()));
            assertEquals(0, n("100").compareTo(balances.balance(DAY, "Walgreens Cash", records(repo)).endUsd()));
            assertEquals(0, n("10").compareTo(balances.balance(DAY, "Register Rewards", records(repo)).endUsd()));
            assertEquals(n("102"), repo.findDeletedById(id).orElseThrow().transaction().getObservations().walgreensCash().endingBalance());
            assertTrue(repo.redemptions().findByRewardId(reward.getRewardId()).isPresent());
            var competing = new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO, BigDecimal.ZERO)); var competingId = UUID.randomUUID();
            competing.addRegisterRewardRedemption(new RegisterRewardRedemption(reward.getRewardId(), competingId, n("2"), DAY));
            assertThrows(IllegalArgumentException.class, () -> repo.save(competingId, competing, null, List.of(), request(), creator));
        }
        try (var repo = InMemoryTransactionRepository.openLocal(file)) {
            assertEquals(3, repo.findDeletedById(id).orElseThrow().revision());
            assertThrows(ConcurrentModificationException.class, () -> repo.restore(id, 2, request(), context));
            restored = repo.restore(id, 3, restore, context);
            var receipt = repo.findById(id).orElseThrow(); assertEquals(4, receipt.revision());
            assertEquals(corrected, ReceiptSnapshot.from(receipt.transaction()));
            assertEquals(List.of(artifact.getEvidenceId()), receipt.evidenceIds());
            assertArrayEquals(new byte[]{1, 2, 3}, repo.evidence().findById(artifact.getEvidenceId()).orElseThrow().getContent());
            assertEquals(List.of(ReceiptChangeAction.CREATED, ReceiptChangeAction.UPDATED, ReceiptChangeAction.DELETED, ReceiptChangeAction.RESTORED),
                    repo.history().findByReceiptId(id).stream().map(h -> h.result().action()).toList());
            var balances = new ProviderBalanceSummary(repo);
            assertEquals(0, n("102").compareTo(balances.balance(DAY, "Walgreens Cash", records(repo)).endUsd()));
            assertEquals(0, n("13").compareTo(balances.balance(DAY, "Register Rewards", records(repo)).endUsd()));
            repo.update(id, 4, corrected.toTransaction(), metadata(), List.of(), request(), approval());
            assertEquals(created, repo.save(id, original.toTransaction(), metadata(), List.of(artifact), create, creator));
            assertEquals(updated, repo.update(id, 1, corrected.toTransaction(), metadata(), List.of(), update, context));
            assertEquals(deleted, repo.softDelete(id, 2, delete, context));
            assertEquals(restored, repo.restore(id, 3, restore, context));
            assertEquals(5, repo.findById(id).orElseThrow().revision()); assertEquals(5, repo.history().findByReceiptId(id).size());
            assertThrows(IllegalArgumentException.class, () -> repo.softDelete(id, 2, restore, context));
            assertThrows(IllegalArgumentException.class, () -> repo.restore(id, 5, request(), context));
            assertThrows(ConcurrentModificationException.class, () -> repo.softDelete(id, 4, request(), context));
        }
        try (var repo = InMemoryTransactionRepository.openLocal(file)) {
            assertEquals(restored, repo.restore(id, 3, restore, context));
            assertEquals(5, repo.findById(id).orElseThrow().revision());
            assertEquals(5, repo.history().findByReceiptId(id).size());
        }
    }

    @Test void failedDeleteAndRestoreDoNotPublishAndSameRequestCanRetry(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("receipts.bin"), backup = dir.resolve("held.bin"); UUID id = UUID.randomUUID(); var context = approval();
        try (var repo = InMemoryTransactionRepository.openLocal(file)) {
            repo.save(id, transaction(id, null), metadata(), List.of(), request(), new ReceiptCreateCtx("Owner"));
            for (boolean deleting : List.of(true, false)) {
                long revision = deleting ? 1 : 2; var req = request(); byte[] before = Files.readAllBytes(file);
                Files.move(file, backup); Files.createDirectory(file);
                assertThrows(IllegalStateException.class, () -> {
                    if (deleting) repo.softDelete(id, revision, req, context); else repo.restore(id, revision, req, context);
                });
                assertEquals(revision, (deleting ? repo.findById(id) : repo.findDeletedById(id)).orElseThrow().revision());
                assertTrue(repo.history().findByRequestId(req.requestId()).isEmpty()); assertArrayEquals(before, Files.readAllBytes(backup));
                Files.delete(file); Files.move(backup, file);
                if (deleting) repo.softDelete(id, revision, req, context); else repo.restore(id, revision, req, context);
            }
            assertEquals(3, repo.findById(id).orElseThrow().revision());
        }
        try (var repo = InMemoryTransactionRepository.openLocal(file)) { assertEquals(3, repo.history().findByReceiptId(id).size()); }
    }

    @Test void receiptSourceDependencyBlocksDeleteAndRequiresSourceFirstRestoration() {
        var repo = new InMemoryTransactionRepository(); UUID originId = UUID.randomUUID(), spentId = UUID.randomUUID();
        var origin = transaction(originId, null); var earning = origin.getObservations().earnings().get(0);
        repo.save(originId, origin, metadata(), List.of(), request(), new ReceiptCreateCtx("Owner"));
        var spent = new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO, BigDecimal.ZERO));
        var usage = new UnidentifiedRR(UUID.randomUUID(), n("5"), "Printed RR");
        spent.setObservations(new ReceiptObservations(DAY, "10:00 AM", List.of(), List.of(usage), null, List.of(), Map.of()));
        repo.save(spentId, spent, new ReceiptMeta("Walgreens", "Use", DAY.atTime(10, 0), null), List.of(), request(), new ReceiptCreateCtx("Owner"));
        var link = new RRUsageLink(earning.earningId(), true, usage.eventId(), spentId, n("5"), "Verified", "Owner", Instant.now()); repo.linkRRUsage(link);
        assertThrows(IllegalArgumentException.class, () -> repo.softDelete(originId, 1, request(), approval()));
        repo.softDelete(spentId, 1, request(), approval()); repo.softDelete(originId, 1, request(), approval());
        assertThrows(IllegalArgumentException.class, () -> repo.restore(spentId, 2, request(), approval()));
        repo.restore(originId, 2, request(), approval()); repo.restore(spentId, 2, request(), approval());
        assertEquals(List.of(link), repo.rrUsageLinks()); assertEquals(3, repo.findById(spentId).orElseThrow().revision());
    }

    @Test void concurrentMatchingDeleteAndRestoreCommitOnce() {
        var repo = new InMemoryTransactionRepository(); UUID id = UUID.randomUUID();
        repo.save(id, transaction(id, null), metadata(), List.of(), request(), new ReceiptCreateCtx("Owner"));
        var context = approval();
        for (boolean deleting : List.of(true, false)) {
            var req = request(); long revision = deleting ? 1 : 2;
            var futures = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(i -> java.util.concurrent.CompletableFuture.supplyAsync(() -> deleting
                            ? repo.softDelete(id, revision, req, context) : repo.restore(id, revision, req, context)))
                    .toList();
            var result = futures.get(0).join();
            futures.forEach(f -> assertEquals(result, f.join()));
            assertEquals(revision + 1, repo.history().findByReceiptId(id).size());
        }
        assertEquals(3, repo.findById(id).orElseThrow().revision());
    }

    @Test void issuedRewardDependencyAlsoBlocksDeletionAndUsedRewardStaysReserved() {
        var repo = new InMemoryTransactionRepository(); UUID sourceId = UUID.randomUUID(), spendingId = UUID.randomUUID();
        repo.save(sourceId, transaction(sourceId, null), metadata(), List.of(), request(), new ReceiptCreateCtx("Owner"));
        var issued = new RegisterReward("Issued from source", n("2"), DAY, null, sourceId); repo.issuedRewards().save(issued);
        var spent = transaction(spendingId, issued);
        repo.save(spendingId, spent, new ReceiptMeta("Walgreens", "Spending", null, null), List.of(), request(), new ReceiptCreateCtx("Owner"));
        assertThrows(IllegalArgumentException.class, () -> repo.softDelete(sourceId, 1, request(), approval()));
        repo.softDelete(spendingId, 1, request(), approval()); repo.softDelete(sourceId, 1, request(), approval());
        assertThrows(IllegalArgumentException.class, () -> repo.restore(spendingId, 2, request(), approval()));
        repo.restore(sourceId, 2, request(), approval()); repo.restore(spendingId, 2, request(), approval());
        assertEquals(spendingId, repo.redemptions().findByRewardId(issued.getRewardId()).orElseThrow().getRedeemedReceiptId());
    }

    @Test void deletingAndRestoringDoesNotRewritePointUnitsOrTheirSavedDollarValuation() {
        var repo = new InMemoryTransactionRepository(); UUID id = UUID.randomUUID(); var tx = transaction(id, null); var o = tx.getObservations();
        var points = new Earning(UUID.randomUUID(), EarningKind.THIRD_PARTY_REBATE, "Fetch", "Scan", Scope.RECEIPT, List.of(), null,
                new RebateBenefit(BenefitKind.POINTS, n("25"), null, "Fetch"), RebateStatus.CREDITED, null, null, null, List.of(), null, null,
                new RecordedValuation(n("0.025"), USD, "User recorded"));
        tx.setObservations(new ReceiptObservations(DAY, "9:40 AM", List.of(points), List.of(), o.walgreensCash(), List.of(), Map.of()));
        var balances = new ProviderBalanceSummary(repo);
        balances.recordOpening(DAY.withDayOfMonth(1), "Fetch", n("1000"), n("1"), true, "Recorded", "Statement");
        repo.save(id, tx, metadata(), List.of(), request(), new ReceiptCreateCtx("Owner"));
        assertEquals(0, n("1025").compareTo(balances.balance(DAY, "Fetch", records(repo)).endPoints()));
        repo.softDelete(id, 1, request(), approval());
        assertEquals(0, n("1000").compareTo(balances.balance(DAY, "Fetch", records(repo)).endPoints()));
        repo.restore(id, 2, request(), approval());
        assertEquals(0, n("1025").compareTo(balances.balance(DAY, "Fetch", records(repo)).endPoints()));
        assertEquals(0, n("1.025").compareTo(balances.balance(DAY, "Fetch", records(repo)).endUsd()));
        assertEquals(points, repo.findById(id).orElseThrow().transaction().getObservations().earnings().get(0));
    }

    @Test void duplicateLookupIncludesDeletedIsExactOrderedAndIsolated() {
        var repo = new InMemoryTransactionRepository(); UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        repo.save(first, transaction(first, null), metadata(), List.of(), request(), new ReceiptCreateCtx("Owner"));
        repo.save(second, transaction(second, null), metadata(), List.of(), request(), new ReceiptCreateCtx("Owner"));
        repo.softDelete(first, 1, request(), approval()); var historySize = repo.history().findByReceiptId(first).size();
        var matches = repo.findPossibleDuplicates(metadata());
        assertEquals(java.util.stream.Stream.of(first, second).sorted().toList(), matches.stream().map(ReceiptRecord::transactionId).toList());
        assertTrue(matches.stream().anyMatch(r -> r.status() == ReceiptStatus.DELETED));
        assertThrows(UnsupportedOperationException.class, matches::clear);
        matches.get(0).transaction().addPayment(new Payment(PaymentType.CASH, n("99")));
        assertEquals(1, repo.findPossibleDuplicates(metadata()).get(0).transaction().getPayments().size());
        assertTrue(repo.findPossibleDuplicates(new ReceiptMeta("walgreens", "Lifecycle", null, null)).isEmpty());
        assertTrue(repo.findPossibleDuplicates(new ReceiptMeta("Walgreens", "Lifecycle", null, "Other")).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> repo.findPossibleDuplicates(new ReceiptMeta("Walgreens", null, null, null)));
        assertEquals(historySize, repo.history().findByReceiptId(first).size());
    }
}
