package coupons.repository;

import coupons.promotions.BenefitKind;
import coupons.promotions.Coupon;
import coupons.promotions.CouponApplied;
import coupons.receipts.CouponTransaction;
import coupons.promotions.CouponType;
import coupons.receipts.Payment;
import coupons.receipts.PaymentType;
import coupons.promotions.Promotion;
import coupons.promotions.PromotionType;
import coupons.receipts.PurchaseItem;
import coupons.promotions.RebateBenefit;
import coupons.promotions.RebateStatus;
import coupons.receipts.ReceiptObservations;
import coupons.receipts.ReceiptTotals;
import coupons.rewards.RegisterReward;
import coupons.rewards.RegisterRewardRedemption;
import coupons.rewards.Reward;
import coupons.rewards.RewardType;

import coupons.receipts.ReceiptObservations.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LocalReceiptPersistenceTest {
    @TempDir Path directory;
    private BigDecimal money(String value) { return new BigDecimal(value); }
    private ReceiptWriteRequest request() { return new ReceiptWriteRequest(UUID.randomUUID(), Instant.now()); }
    private CouponTransaction empty() { return new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO, BigDecimal.ZERO)); }

    @Test void reopensEveryRecordedSectionEvidenceHistoryAndOriginalRetryResult() throws Exception {
        Path file = directory.resolve("receipts.bin");
        UUID id = UUID.randomUUID();
        var tx = new CouponTransaction(new ReceiptTotals(money("1.040"), money("3.600")));
        var item = new PurchaseItem("Neutrogena", "07050111090", money("7.79"), money("7.79"), 2);
        tx.addPurchaseItem(item);
        var applied = new CouponApplied(UUID.randomUUID(), "MQ", money("4.00"), List.of(item.getLineItemId()), 2);
        tx.addAppliedCoupon(applied);
        tx.addCoupon(new Coupon("Offer", money("4.00"), 2, CouponType.MANUFACTURER, null));
        tx.addPromotion(new Promotion("Buy two", PromotionType.BUY, 2, RewardType.REGISTER_REWARD, 7, false, false));
        tx.addReward(new Reward("Legacy observation", Double.NaN, false));
        tx.addPayment(new Payment(UUID.randomUUID(), PaymentType.WALGREENS_CASH, money("10.00"), "Trip"));
        var issued = new RegisterReward("Palmolive", money("2.00"), null, null, null);
        tx.addRegisterRewardRedemption(new RegisterRewardRedemption(issued.getRewardId(), id, money("2.00"), null));
        var artifact = new ReceiptEvidence(UUID.randomUUID(), id, "image/png", new byte[]{1, 2, 3});
        var points = new RebateBenefit(BenefitKind.POINTS, money("25"), null, "Fetch");
        var earning = new Earning(UUID.randomUUID(), EarningKind.THIRD_PARTY_REBATE, "Fetch", "Scan", Scope.RECEIPT,
                List.of(), null, points, RebateStatus.CREDITED, null, null, null, List.of(artifact.getEvidenceId()), null,
                new OfferRequirement(null, null, "Receipt"), new RecordedValuation(money("0.025"), Currency.getInstance("USD"), "spreadsheet"));
        var category = new SavingsCategory(UUID.randomUUID(), "Mfg coupon savings", Currency.getInstance("USD"), money("6.00"),
                List.of(new Inclusion(new EventReference(EventKind.COUPON_APPLIED, applied.getApplicationId()), money("4.00"), "aj", Instant.now())), false);
        tx.setObservations(new ReceiptObservations(LocalDate.of(2026, 10, 4), "940 am", List.of(earning),
                List.of(new UnidentifiedRR(UUID.randomUUID(), money("1.00"), "Other RR")),
                new WalgreensCashAccount(money("247.75"), money("10.00"), money("0.80"), money("238.55")),
                List.of(category), Map.of(applied.getApplicationId(), DiscountKind.MANUFACTURER)));
        var meta = new ReceiptMeta("Walgreens", "000123", null, null);
        var request = request();
        var context = new ReceiptCreateCtx("aj");
        ReceiptWriteResult result;
        try (var repo = InMemoryTransactionRepository.openLocal(file)) {
            repo.issuedRewards().save(issued);
            result = repo.save(id, tx, meta, List.of(artifact), request, context);
        }
        try (var repo = InMemoryTransactionRepository.openLocal(file)) {
            assertEquals(ReceiptSnapshot.from(tx), ReceiptSnapshot.from(repo.findById(id).orElseThrow().transaction()));
            assertArrayEquals(artifact.getContent(), repo.evidence().findById(artifact.getEvidenceId()).orElseThrow().getContent());
            var reloadedReward = repo.issuedRewards().findById(issued.getRewardId()).orElseThrow();
            assertEquals(issued.getRewardId(), reloadedReward.getRewardId());
            assertEquals(issued.getSourceDescription(), reloadedReward.getSourceDescription());
            assertEquals(issued.getFaceValue(), reloadedReward.getFaceValue());
            assertEquals(1, repo.redemptions().findByReceiptId(id).size());
            assertEquals(result, repo.save(id, tx, meta, List.of(artifact), request, context));
            assertEquals(1, repo.history().findByReceiptId(id).size());
            assertEquals(result, repo.creationHistory().get(0).result());
        }
    }

    @Test void failedCheckpointPublishesNothingAndCanRetryAfterRepair() throws Exception {
        Path file = directory.resolve("receipts.bin");
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        var retry = request();
        var context = new ReceiptCreateCtx("aj");
        var tx = empty();
        try (var repo = InMemoryTransactionRepository.openLocal(file)) {
            repo.save(first, empty(), null, List.of(), request(), context);
            byte[] before = Files.readAllBytes(file);
            Path backup = directory.resolve("original.bin");
            Files.move(file, backup);
            Files.createDirectory(file);
            assertThrows(IllegalStateException.class, () -> repo.save(second, tx, null, List.of(), retry, context));
            assertTrue(repo.findById(second).isEmpty());
            assertTrue(repo.history().findByRequestId(retry.requestId()).isEmpty());
            Files.delete(file);
            Files.move(backup, file);
            assertArrayEquals(before, Files.readAllBytes(file));
            repo.save(second, tx, null, List.of(), retry, context);
        }
        try (var repo = InMemoryTransactionRepository.openLocal(file)) {
            assertTrue(repo.findById(first).isPresent());
            assertTrue(repo.findById(second).isPresent());
        }
    }

    @Test void rejectsConcurrentWriterAndLeavesUnreadableDataUnchanged() throws Exception {
        Path file = directory.resolve("receipts.bin");
        try (var first = InMemoryTransactionRepository.openLocal(file)) {
            assertThrows(java.io.IOException.class, () -> InMemoryTransactionRepository.openLocal(file));
        }
        byte[] damaged = {1, 2, 3, 4};
        Files.write(file, damaged);
        assertThrows(java.io.IOException.class, () -> InMemoryTransactionRepository.openLocal(file));
        assertArrayEquals(damaged, Files.readAllBytes(file));
    }
    @Test void couponRemovalSurvivesFurtherSavesAndRestartWithoutErasingOriginal() throws Exception {
        Path file = directory.resolve("voids.bin");
        var reward = new RegisterReward("Mistaken entry", money("7.00"), null, null, null);
        UUID receiptId = UUID.randomUUID();
        try (var repo = InMemoryTransactionRepository.openLocal(file)) {
            repo.issuedRewards().save(reward);
            repo.voidRecordedReward(reward.getRewardId(), "Entered by mistake", "aj");
            repo.save(receiptId, empty(), null, List.of(), request(), new ReceiptCreateCtx("aj"));
            repo.issuedRewards().save(new RegisterReward("Other coupon", money("5.00"), null, null, null));
        }
        try (var repo = InMemoryTransactionRepository.openLocal(file)) {
            assertEquals(1, repo.recordedRewards().size());
            assertEquals(1, repo.rewardRemovalHistory().size());
            assertTrue(repo.issuedRewards().findById(reward.getRewardId()).isPresent());
            assertEquals(1, repo.history().findByReceiptId(receiptId).size());
            var tx = empty();
            UUID second = UUID.randomUUID();
            tx.addRegisterRewardRedemption(new RegisterRewardRedemption(reward.getRewardId(), second, money("7.00"), null));
            assertThrows(IllegalArgumentException.class, () -> repo.save(second, tx, null, List.of(), request(), new ReceiptCreateCtx("aj")));
        }
    }
    @Test void receiptLinkedCouponCannotBeRemovedAsStandaloneMistake() {
        var repo = new InMemoryTransactionRepository();
        var reward = new RegisterReward("Palmolive", money("2.00"), null, null, null);
        repo.issuedRewards().save(reward);
        UUID id = UUID.randomUUID();
        var tx = empty();
        tx.addRegisterRewardRedemption(new RegisterRewardRedemption(reward.getRewardId(), id, money("2.00"), null));
        repo.save(id, tx, null, List.of(), request(), new ReceiptCreateCtx("aj"));
        assertThrows(IllegalArgumentException.class, () -> repo.voidRecordedReward(reward.getRewardId(), "Mistake", "aj"));
        assertEquals(1, repo.recordedRewards().size());
        assertTrue(repo.rewardRemovalHistory().isEmpty());
        assertEquals(1, repo.history().findByReceiptId(id).size());
    }
}
