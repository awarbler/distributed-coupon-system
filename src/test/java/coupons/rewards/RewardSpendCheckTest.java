package coupons.rewards;

import coupons.promotions.BenefitKind;
import coupons.promotions.CouponApplied;
import coupons.receipts.CouponTransaction;
import coupons.receipts.Payment;
import coupons.receipts.PaymentType;
import coupons.promotions.PromotionType;
import coupons.receipts.PurchaseItem;
import coupons.promotions.RebateBenefit;
import coupons.promotions.RebateStatus;
import coupons.cli.ReceiptEntryApp;
import coupons.receipts.ReceiptObservations;
import coupons.receipts.ReceiptTotals;

import coupons.receipts.ReceiptObservations.*;
import coupons.repository.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.nio.file.Path;
import java.util.*;
import java.io.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RewardSpendCheckTest {
    private CouponTransaction transaction(String charminPrice) {
        var tx = new CouponTransaction(new ReceiptTotals(new BigDecimal("1.73"), new BigDecimal("26.69")));
        tx.addPurchaseItem(new PurchaseItem("Charmin", null, null, new BigDecimal(charminPrice), 4));
        tx.addPurchaseItem(new PurchaseItem("H&S shampoo", null, null, new BigDecimal("7.00"), 1));
        tx.addPurchaseItem(new PurchaseItem("H&S conditioner", null, null, new BigDecimal("7.00"), 1));
        tx.addAppliedCoupon(new CouponApplied("Product coupons", new BigDecimal("4.00"), List.of()));
        tx.addPayment(new Payment(PaymentType.WALGREENS_CASH, BigDecimal.TEN));
        tx.setObservations(new ReceiptObservations(null, null, List.of(), List.of(), null, List.of(), kinds(tx)));
        return tx;
    }
    private Map<UUID, DiscountKind> kinds(CouponTransaction tx) {
        var result = new HashMap<UUID, DiscountKind>();
        tx.getAppliedCoupons().forEach(c -> result.put(c.getApplicationId(), DiscountKind.MANUFACTURER));
        return result;
    }
    private Earning earning(CouponTransaction tx, SpendBasis basis, Scope scope, List<UUID> lines) {
        return new Earning(UUID.randomUUID(), EarningKind.REGISTER_REWARD, "Walgreens", "P&G spend $35 earn $10 RR",
                scope, lines, null, new RebateBenefit(BenefitKind.STORE_COUPON, BigDecimal.TEN, Currency.getInstance("USD"), "Walgreens"),
                RebateStatus.PAID, null, null, null, List.of(), null,
                new OfferRequirement(PromotionType.SPEND, new BigDecimal("35.00"), null, basis), null);
    }
    private List<UUID> ids(CouponTransaction tx) { return tx.getPurchaseItems().stream().map(PurchaseItem::getLineItemId).toList(); }

    @Test void beforeCouponSpendUsesSalePricesAndQuantitiesWithoutSubtractingDiscountsOrRedemptions() {
        var tx = transaction("5.49"); var e = earning(tx, SpendBasis.SALE_BEFORE_COUPONS, Scope.MULTI_ITEM, ids(tx));
        var check = RewardSpendCheck.evaluate(e, tx);
        assertEquals(RewardSpendCheck.Status.MET, check.status());
        assertEquals(new BigDecimal("35.96"), check.qualifyingSpend());
        assertEquals(new BigDecimal("0.96"), check.qualifyingSpend().subtract(check.requiredSpend()));
    }

    @Test void equalityMeetsThresholdAndBelowDoesNot() {
        var tx = transaction("5.25");
        assertEquals(RewardSpendCheck.Status.MET, RewardSpendCheck.evaluate(earning(tx, SpendBasis.SALE_BEFORE_COUPONS, Scope.MULTI_ITEM, ids(tx)), tx).status());
        var below = transaction("5.00");
        var check = RewardSpendCheck.evaluate(earning(below, SpendBasis.SALE_BEFORE_COUPONS, Scope.MULTI_ITEM, ids(below)), below);
        assertEquals(RewardSpendCheck.Status.NOT_MET, check.status());
        assertEquals(new BigDecimal("34.00"), check.qualifyingSpend());
    }

    @Test void unknownBasisWholeReceiptUnknownScopeAndMissingLinesCannotVerify() {
        var tx = transaction("5.49");
        assertEquals(RewardSpendCheck.Status.CANNOT_VERIFY, RewardSpendCheck.evaluate(earning(tx, SpendBasis.UNKNOWN, Scope.MULTI_ITEM, ids(tx)), tx).status());
        for (Scope scope : List.of(Scope.RECEIPT, Scope.UNKNOWN))
            assertEquals(RewardSpendCheck.Status.CANNOT_VERIFY, RewardSpendCheck.evaluate(earning(tx, SpendBasis.SALE_BEFORE_COUPONS, scope, List.of()), tx).status());
        assertEquals(RewardSpendCheck.Status.CANNOT_VERIFY, RewardSpendCheck.evaluate(earning(tx, SpendBasis.SALE_BEFORE_COUPONS, Scope.ITEM, List.of(UUID.randomUUID())), tx).status());
        assertEquals(SpendBasis.UNKNOWN, new OfferRequirement(PromotionType.SPEND, BigDecimal.TEN).spendBasis());
        assertEquals(SpendBasis.UNKNOWN, new OfferRequirement(PromotionType.SPEND, BigDecimal.TEN, null, null).spendBasis());
    }

    @Test void correctionRechecksLatestReceiptWhilePreservingHistoricalResultAndSurvivesRestart(@TempDir Path directory) throws Exception {
        var file = directory.resolve("receipts.bin"); var id = UUID.randomUUID();
        try (var repo = InMemoryTransactionRepository.openLocal(file)) {
            var tx = transaction("5.49"); var e = earning(tx, SpendBasis.SALE_BEFORE_COUPONS, Scope.MULTI_ITEM, ids(tx));
            tx.setObservations(new ReceiptObservations(null, null, List.of(e), List.of(), null, List.of(), kinds(tx)));
            repo.save(id, tx, new ReceiptMeta("Walgreens", "PG", null, null), List.of(),
                    new ReceiptWriteRequest(UUID.randomUUID(), Instant.now()), new ReceiptCreateCtx("AJ"));
            // Correct an existing line through the actual saved-receipt CLI, preserving event identity.
            var bytes = new ByteArrayOutputStream();
            new ReceiptEntryApp(new Scanner("UPDATE\nPG\nITEM\nALL\n1\nCharmin\n\n5.00\n4\nREASON\nCorrect sale price\nSAVE\n9\n"), new PrintStream(bytes), repo).run();
            assertTrue(bytes.toString().contains("Not met —"), bytes.toString());
            assertEquals(2, repo.findById(id).orElseThrow().revision());
            var before = repo.history().findByRevision(id, 1).orElseThrow().after().contents().toTransaction();
            assertEquals(RewardSpendCheck.Status.MET, RewardSpendCheck.evaluate(before.getObservations().earnings().get(0), before).status());
        }
        try (var repo = InMemoryTransactionRepository.openLocal(file)) {
            var tx = repo.findById(id).orElseThrow().transaction();
            assertEquals(RewardSpendCheck.Status.NOT_MET, RewardSpendCheck.evaluate(tx.getObservations().earnings().get(0), tx).status());
            assertEquals(SpendBasis.SALE_BEFORE_COUPONS, tx.getObservations().earnings().get(0).requirement().spendBasis());
            assertEquals(1, tx.getObservations().earnings().size());
        }
    }
    @Test void cliRecordsSpendBasisAndAllProductsThenShowsAutomaticVerification() {
        var repo = new InMemoryTransactionRepository(); var id = UUID.randomUUID();
        repo.save(id, transaction("5.49"), new ReceiptMeta("Walgreens", "PG", null, null), List.of(),
                new ReceiptWriteRequest(UUID.randomUUID(), Instant.now()), new ReceiptCreateCtx("AJ"));
        var bytes = new ByteArrayOutputStream();
        String script = "UPDATE\nPG\nEARNINGS\nALL\n0\nRegister Reward\nP&G spend 35 earn 10 RR\n2\n35.00\n1\nA\n10.00\n5\n\n\n\nREASON\nRecord P&G reward\nSAVE\n2\nPG\n9\n";
        new ReceiptEntryApp(new Scanner(script), new PrintStream(bytes), repo).run();
        String output = bytes.toString();
        assertTrue(output.contains("Met — $0.96 above threshold"), output);
        assertTrue(output.matches("(?s).*Qualifying spend +\\| \\$35.96 .*"), output);
        assertTrue(output.matches("(?s).*Required spend +\\| \\$35.00 .*"), output);
        var tx = repo.findById(id).orElseThrow().transaction();
        var e = tx.getObservations().earnings().get(0);
        assertEquals(SpendBasis.SALE_BEFORE_COUPONS, e.requirement().spendBasis());
        assertEquals(ids(tx), e.lineItemIds());
        assertEquals(new BigDecimal("10.00"), e.confirmed().amount());
        assertEquals(1, tx.getObservations().earnings().size());
    }
}
