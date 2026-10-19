package coupons.repository;

import coupons.*;
import coupons.ReceiptObservations.WalgreensCashAccount;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Financial regression only: lifecycle writes must not reconcile or rewrite source values. */
class ReceiptFinancialRegressionTest {
    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private static ReceiptWriteRequest request() {
        return new ReceiptWriteRequest(UUID.randomUUID(), Instant.now());
    }

    private static List<BigDecimal> financialValues(CouponTransaction transaction) {
        var calculator = new TransactionCalculator();
        return List.of(
                calculator.calculateRegularMerchandiseTotal(transaction),
                calculator.calculateSaleMerchandiseTotal(transaction),
                calculator.calculateCouponTotal(transaction),
                calculator.calculateAfterCouponTotal(transaction),
                calculator.calculateRegisterRewardsRedeemed(transaction),
                calculator.calculateWalgreensCashRedeemed(transaction),
                calculator.calculateRewardRedemptionTotal(transaction),
                calculator.calculateAfterRewardRedemptions(transaction),
                transaction.getReceiptTotals().getTaxAmount(),
                calculator.calculateExpectedCheckoutAmount(transaction),
                transaction.getReceiptTotals().getFinalTotal(),
                calculator.calculateReceiptDifference(transaction));
    }

    @Test
    void lifecyclePreservesWalgreensCalculationsReportedObservationsAndOneCentDifference()
            throws Exception {
        try (var repository = new InMemoryTransactionRepository()) {
            UUID receiptId = UUID.randomUUID();
            var transaction = new CouponTransaction(
                    new ReceiptTotals(money("1.31"), money("7.28")));
            transaction.addPurchaseItem(new PurchaseItem(
                    "Gain", "01234567890", money("11.99"), money("9.99"), 1));
            transaction.addPurchaseItem(new PurchaseItem(
                    "Tide Pods", "03700000997", money("17.99"), money("15.99"), 1));
            transaction.addPurchaseItem(new PurchaseItem(
                    "Dawn Platinum", "DAWN-PLATINUM", money("7.49"), money("4.99"), 1));
            transaction.addPurchaseItem(new PurchaseItem(
                    "Dawn Powerwash", "DAWN-POWERWASH", money("6.99"), money("5.99"), 1));
            transaction.addAppliedCoupon(new CouponApplied(
                    "$3 off Tide", money("3.00"), List.of()));
            transaction.addAppliedCoupon(new CouponApplied(
                    "$2 off Gain", money("2.00"), List.of()));
            transaction.addAppliedCoupon(new CouponApplied(
                    "$4 off 2 Dawn", money("4.00"), List.of()));

            // Existing instruments with unknown earning sources are permitted by the contract.
            // Register each instrument before the receipt attempts to redeem it.
            for (String amount : List.of("7.00", "5.00")) {
                var reward = new RegisterReward(
                        "Unknown sample source", money(amount), null, null, null);
                repository.issuedRewards().save(reward);
                assertNull(repository.issuedRewards().findById(reward.getRewardId())
                        .orElseThrow().getEarnedReceiptId());
                transaction.addRegisterRewardRedemption(new RegisterRewardRedemption(
                        reward.getRewardId(), receiptId, money(amount), null));
            }
            assertEquals(2, transaction.getRegisterRewardRedemptions().stream()
                    .map(RegisterRewardRedemption::getRewardId).distinct().count());
            transaction.addPayment(new Payment(PaymentType.WALGREENS_CASH, money("10.00")));
            transaction.addPayment(new Payment(PaymentType.CASH, money("7.28")));

            // Synthetic footer fixture, separate from the historical Walgreens arithmetic above.
            // These source observations must stay intact; lifecycle writes must not recalculate them.
            var reportedAccount = new WalgreensCashAccount(
                    money("100.00"), money("10.00"), money("2.00"), money("92.00"));
            var observed = new ReceiptObservations(null, null, List.of(), List.of(),
                    reportedAccount, List.of(), Map.of());
            transaction.setObservations(observed);

            var expectedValues = List.of(
                    money("44.46"), // Regular merchandise
                    money("36.96"), // Sale merchandise
                    money("9.00"),  // Coupon deductions
                    money("27.96"), // After coupons
                    money("12.00"), // RR used
                    money("10.00"), // WC used
                    money("22.00"), // All rewards used
                    money("5.96"),  // After rewards, before tax
                    money("1.31"),  // Reported tax
                    money("7.27"),  // Calculated checkout
                    money("7.28"),  // Reported checkout
                    money("0.01")); // Preserved discrepancy
            assertEquals(expectedValues, financialValues(transaction));

            var originalSnapshot = ReceiptSnapshot.from(transaction);
            var originalMetadata = new ReceiptMeta("Walgreens", "000123", null, null);
            var evidence = new ReceiptEvidence(UUID.randomUUID(), receiptId,
                    "text/plain", new byte[]{1, 2, 3});
            repository.save(receiptId, transaction, originalMetadata, List.of(evidence),
                    request(), new ReceiptCreateCtx("Owner"));
            var retrieved = repository.findById(receiptId).orElseThrow();
            assertEquals(expectedValues, financialValues(retrieved.transaction()));
            assertEquals(originalSnapshot, ReceiptSnapshot.from(retrieved.transaction()));
            assertEquals(observed, retrieved.transaction().getObservations());

            // An identification correction changes metadata only, not the financial receipt.
            var correctedMetadata = new ReceiptMeta(
                    "Walgreens", "000123", null, "Verified store");
            var approval = new ReceiptChangeContext("Owner", "Added verified store reference",
                    "Owner", Instant.now());
            repository.update(receiptId, 1, retrieved.transaction(), correctedMetadata,
                    List.of(), request(), approval);
            assertEquals(expectedValues, financialValues(repository.findById(receiptId)
                    .orElseThrow().transaction()));

            repository.softDelete(receiptId, 2, request(), approval);
            assertTrue(repository.findById(receiptId).isEmpty());
            var deleted = repository.findDeletedById(receiptId).orElseThrow();
            assertEquals(expectedValues, financialValues(deleted.transaction()));
            assertEquals(originalSnapshot, ReceiptSnapshot.from(deleted.transaction()));
            assertEquals(observed, deleted.transaction().getObservations());

            repository.restore(receiptId, 3, request(), approval);
            var restored = repository.findById(receiptId).orElseThrow();
            assertEquals(receiptId, restored.transactionId());
            assertEquals(4, restored.revision());
            assertEquals("000123", restored.metadata().receiptNumber());
            assertEquals(correctedMetadata, restored.metadata());
            assertEquals(expectedValues, financialValues(restored.transaction()));
            assertEquals(originalSnapshot, ReceiptSnapshot.from(restored.transaction()));
            assertEquals(observed, restored.transaction().getObservations());
            assertEquals(List.of(evidence.getEvidenceId()), restored.evidenceIds());
            assertArrayEquals(evidence.getContent(), repository.evidence()
                    .findById(evidence.getEvidenceId()).orElseThrow().getContent());
            for (var redemption : transaction.getRegisterRewardRedemptions()) {
                assertEquals(receiptId, redemption.getRedeemedReceiptId());
                assertEquals(redemption, repository.redemptions()
                        .findByRewardId(redemption.getRewardId()).orElseThrow());
            }

            var history = repository.history().findByReceiptId(receiptId);
            assertEquals(List.of(ReceiptChangeAction.CREATED, ReceiptChangeAction.UPDATED,
                            ReceiptChangeAction.DELETED, ReceiptChangeAction.RESTORED),
                    history.stream().map(entry -> entry.result().action()).toList());
            for (int index = 0; index < history.size(); index++) {
                var entry = history.get(index);
                assertEquals(index + 1, entry.after().revision());
                assertEquals(receiptId, entry.after().transactionId());
                assertEquals(originalSnapshot, entry.after().contents());
                assertEquals(observed, entry.after().contents().observations());
                assertEquals(money("1.31"), entry.after().contents().receiptTotals().taxAmount());
                assertEquals(money("7.28"), entry.after().contents().receiptTotals().finalTotal());
                assertEquals(List.of(evidence.getEvidenceId()), entry.after().evidenceIds());
                assertEquals(expectedValues, financialValues(entry.after().contents().toTransaction()));
                if (index > 0) assertEquals(history.get(index - 1).after(), entry.before());
            }
            assertEquals(originalMetadata, history.get(0).after().metadata());
        }
    }
}
