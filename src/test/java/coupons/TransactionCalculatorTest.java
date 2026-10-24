package coupons;

import coupons.repository.*;
import coupons.ReceiptObservations.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import coupons.repository.ReceiptSnapshot;

/**
 * Tests the authoritative sources used for receipt deductions.
 */
class TransactionCalculatorTest {

    private final TransactionCalculator calculator =
            new TransactionCalculator();

    /**
     * Creates a transaction containing ten dollars of merchandise.
     *
     * @return transaction with recorded tax and final total
     */
    private CouponTransaction newTransaction() {
        CouponTransaction transaction = new CouponTransaction(
                new ReceiptTotals(
                        new BigDecimal("1.00"),
                        new BigDecimal("10.00")
                )
        );

        transaction.addPurchaseItem(new PurchaseItem(
                "Example product",
                "TEST-PRODUCT",
                new BigDecimal("5.00"),
                new BigDecimal("5.00"),
                2
        ));

        return transaction;
    }

    /**
     * Verifies that advertised offers alone cause no deduction.
     */
    @Test
    void offerWithoutApplicationDoesNotReduceTotal() {
        CouponTransaction transaction = newTransaction();

        transaction.addCoupon(new Coupon(
                "$2 off two",
                new BigDecimal("2.00"),
                2,
                CouponType.MANUFACTURER,
                null
        ));

        assertEquals(
                0,
                calculator.calculateCouponTotal(transaction)
                        .compareTo(BigDecimal.ZERO)
        );

        assertEquals(
                new BigDecimal("10.00"),
                calculator.calculateAfterCouponTotal(transaction)
        );
    }

    /**
     * Verifies that actual amounts override advertised amounts.
     */
    @Test
    void usesActualDeductionWithoutAddingOfferAmount() {
        CouponTransaction transaction = newTransaction();

        transaction.addCoupon(new Coupon(
                "$2 off two",
                new BigDecimal("2.00"),
                2,
                CouponType.MANUFACTURER,
                null
        ));

        transaction.addAppliedCoupon(new CouponApplied(
                "MFG COUPON",
                new BigDecimal("1.50"),
                List.of()
        ));

        assertEquals(
                new BigDecimal("1.50"),
                calculator.calculateCouponTotal(transaction)
        );
    }

    /**
     * Verifies that a recorded deduction needs no matching offer.
     */
    @Test
    void countsApplicationWithoutKnownOffer() {
        CouponTransaction transaction = newTransaction();

        transaction.addAppliedCoupon(new CouponApplied(
                "MFG COUPON",
                new BigDecimal("2.00"),
                List.of()
        ));

        assertEquals(
                new BigDecimal("2.00"),
                calculator.calculateCouponTotal(transaction)
        );
    }

    /**
     * Verifies two deductions against one quantity-two receipt line.
     */
    @Test
    void combinesScottDiscountsOnce() {
        CouponTransaction transaction = newTransaction();
        UUID lineId = transaction.getPurchaseItems().get(0).getLineItemId();

        transaction.addAppliedCoupon(new CouponApplied(
                "STR IVC",
                new BigDecimal("2.50"),
                List.of(lineId)
        ));

        transaction.addAppliedCoupon(new CouponApplied(
                "MFG COUPON",
                new BigDecimal("2.00"),
                List.of(lineId)
        ));

        assertEquals(
                new BigDecimal("4.50"),
                calculator.calculateCouponTotal(transaction)
        );

        assertEquals(
                new BigDecimal("5.50"),
                calculator.calculateAfterCouponTotal(transaction)
        );
    }

    /**
     * Verifies that legacy payment entries do not supply RR totals.
     */
    @Test
    void legacyRegisterRewardPaymentIsNotCounted() {
        CouponTransaction transaction = newTransaction();

        transaction.addPayment(new Payment(
                PaymentType.REGISTER_REWARD,
                new BigDecimal("5.00")
        ));

        assertEquals(
                0,
                calculator.calculateRegisterRewardsRedeemed(transaction)
                        .compareTo(BigDecimal.ZERO)
        );
    }

    /**
     * Verifies that accepted redemption value drives the calculation.
     */
    @Test
    void usesAcceptedAmountRatherThanRewardFaceValue() {
        CouponTransaction transaction = newTransaction();

        RegisterReward reward = new RegisterReward(
                "Example promotion",
                new BigDecimal("5.00"),
                null,
                null,
                null
        );

        transaction.addRegisterRewardRedemption(
                new RegisterRewardRedemption(
                        reward.getRewardId(),
                        UUID.randomUUID(),
                        new BigDecimal("4.50"),
                        null
                )
        );

        assertEquals(
                new BigDecimal("4.50"),
                calculator.calculateRegisterRewardsRedeemed(transaction)
        );
    }

    /**
     * Verifies unavailable receipt details remain unknown after restoration.
     */
    @Test
    void preservesUnknownProductCodeAndRegularPrice() {
        CouponTransaction original = new CouponTransaction(
                new ReceiptTotals(
                        new BigDecimal("0.27"),
                        new BigDecimal("5.28")
                )
        );

        PurchaseItem line = new PurchaseItem(
                "Recorded product",
                null,
                null,
                new BigDecimal("5.00"),
                1
        );

        original.addPurchaseItem(line);

        ReceiptSnapshot snapshot = ReceiptSnapshot.from(original);
        CouponTransaction restored = snapshot.toTransaction();

        assertEquals(snapshot, ReceiptSnapshot.from(restored));
        assertEquals(
                line.getLineItemId(),
                restored.getPurchaseItems().get(0).getLineItemId()
        );
        assertNull(restored.getPurchaseItems().get(0).getProductCode());
        assertNull(restored.getPurchaseItems().get(0).getRegularUnitPrice());

        TransactionCalculator calculator = new TransactionCalculator();

        assertThrows(
                IllegalStateException.class,
                () -> calculator.calculateRegularMerchandiseTotal(restored)
        );

        assertEquals(
                0,
                new BigDecimal("5.27").compareTo(
                        calculator.calculateExpectedCheckoutAmount(restored)
                )
        );
    }

    /**
     * Verifies separate deductions and exclusion of ordinary cash tender.
     */
    @Test
    void calculatesCheckoutUsingEachCategoryOnce() {
        CouponTransaction transaction = newTransaction();

        transaction.addAppliedCoupon(new CouponApplied(
                "MFG COUPON",
                new BigDecimal("2.00"),
                List.of()
        ));

        transaction.addRegisterRewardRedemption(
                new RegisterRewardRedemption(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        new BigDecimal("3.00"),
                        null
                )
        );

        transaction.addPayment(new Payment(
                PaymentType.WALGREENS_CASH,
                new BigDecimal("1.00")
        ));

        transaction.addPayment(new Payment(
                PaymentType.CASH,
                new BigDecimal("5.00")
        ));

        assertEquals(
                new BigDecimal("2.00"),
                calculator.calculateCouponTotal(transaction)
        );

        assertEquals(
                new BigDecimal("3.00"),
                calculator.calculateRegisterRewardsRedeemed(transaction)
        );

        assertEquals(
                new BigDecimal("1.00"),
                calculator.calculateWalgreensCashRedeemed(transaction)
        );

        assertEquals(
                new BigDecimal("5.00"),
                calculator.calculateExpectedCheckoutAmount(transaction)
        );

        assertEquals(
                new BigDecimal("5.00"),
                calculator.calculateReceiptDifference(transaction)
        );
    }

    private CouponTransaction splitPaymentReceipt() {
        var tx = new CouponTransaction(new ReceiptTotals(new BigDecimal("1.98"), new BigDecimal("25.96")));
        tx.addPurchaseItem(new PurchaseItem("Basket", null, new BigDecimal("44.96"), new BigDecimal("31.98"), 1));
        tx.addAppliedCoupon(new CouponApplied(UUID.randomUUID(), "Recorded discounts", new BigDecimal("8"), List.of(), null, true));
        tx.addPayment(new Payment(PaymentType.WALGREENS_CASH, new BigDecimal("20")));
        tx.addPayment(new Payment(PaymentType.CARD, new BigDecimal("5.96")));
        return tx;
    }
    private coupons.repository.ReceiptMeta paymentMetadata(
            coupons.repository.ReceiptMeta.PrintedTotalBasis basis,
            coupons.repository.ReceiptMeta.PaymentEntryStatus entry) {
        return new coupons.repository.ReceiptMeta("Walgreens", "test", null, null,
                coupons.repository.ReceiptMeta.PurchaseChannel.IN_STORE, basis, entry);
    }
    @Test void splitFundingBalancesWithoutChangingLegacyDifference() {
        var tx = splitPaymentReceipt();
        var review = calculator.reconcilePayments(tx, paymentMetadata(
                coupons.repository.ReceiptMeta.PrintedTotalBasis.BEFORE_PAYMENT_FUNDING,
                coupons.repository.ReceiptMeta.PaymentEntryStatus.COMPLETE));
        assertEquals(TransactionCalculator.ReconciliationStatus.BALANCED, review.status());
        assertEquals(0, review.recordedCashCardPaid().compareTo(new BigDecimal("5.96")));
        assertEquals(0, review.difference().signum());
        assertEquals(0, review.purchaseDifference().signum());
        assertEquals(0, calculator.calculateReceiptDifference(tx).compareTo(new BigDecimal("20")));
        org.junit.jupiter.api.Assertions.assertFalse(review.acknowledgementRequired());
    }
    @Test void onePaymentDoesNotEstablishCompletenessAndUnknownBasisIsPreserved() {
        var tx = splitPaymentReceipt();
        var partial = calculator.reconcilePayments(tx, paymentMetadata(
                coupons.repository.ReceiptMeta.PrintedTotalBasis.BEFORE_PAYMENT_FUNDING,
                coupons.repository.ReceiptMeta.PaymentEntryStatus.PARTIAL));
        assertEquals(TransactionCalculator.ReconciliationStatus.INCOMPLETE, partial.status());
        assertNull(partial.difference());
        var unknown = calculator.reconcilePayments(tx, new coupons.repository.ReceiptMeta("Walgreens", null, null, null));
        assertEquals(TransactionCalculator.ReconciliationStatus.CANNOT_VERIFY, unknown.status());
        assertNull(unknown.calculatedPurchaseTotal());
        assertNull(unknown.difference());
    }
    @Test void legacyOtherAndRegisterRewardTenderRemainUnresolved() {
        var tx = splitPaymentReceipt();
        tx.addPayment(new Payment(PaymentType.OTHER, BigDecimal.ONE));
        tx.addPayment(new Payment(PaymentType.REGISTER_REWARD, BigDecimal.ONE));
        var result = calculator.reconcilePayments(tx, paymentMetadata(
                coupons.repository.ReceiptMeta.PrintedTotalBasis.BEFORE_PAYMENT_FUNDING,
                coupons.repository.ReceiptMeta.PaymentEntryStatus.COMPLETE));
        assertEquals(2, result.unresolvedPaymentIds().size());
        assertEquals(TransactionCalculator.ReconciliationStatus.INCOMPLETE, result.status());
        assertNull(result.difference());
    }
    @Test void afterRedemptionTotalDoesNotCountWalgreensCashTwice() {
        var tx = new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO, new BigDecimal("5")));
        tx.addPurchaseItem(new PurchaseItem("Soap", null, new BigDecimal("25"), new BigDecimal("25"), 1));
        tx.addPayment(new Payment(PaymentType.WALGREENS_CASH, new BigDecimal("20")));
        tx.addPayment(new Payment(PaymentType.CASH, new BigDecimal("5")));
        var result = calculator.reconcilePayments(tx, paymentMetadata(
                coupons.repository.ReceiptMeta.PrintedTotalBasis.AFTER_REWARD_REDEMPTIONS,
                coupons.repository.ReceiptMeta.PaymentEntryStatus.COMPLETE));
        assertEquals(TransactionCalculator.ReconciliationStatus.BALANCED, result.status());
        assertEquals(0, result.knownFundingSubtotal().compareTo(new BigDecimal("5")));
    }
    @Test void explicitOtherDeductionIsNotFundingAndUnrecordedZeroIsNotInvented() {
        var tx = new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO, BigDecimal.ZERO));
        var other = new Payment(PaymentType.OTHER, new BigDecimal("2")); tx.addPayment(other);
        var o = tx.getObservations();
        tx.setObservations(new ReceiptObservations(o.purchaseDate(), o.printedTime(), o.earnings(), o.unidentifiedRR(),
                o.walgreensCash(), o.savings(), o.discountKinds(), o.receiptSavingValuePercent(), o.promotionLinks(),
                new ReceiptWorksheetData(java.util.Map.of(), java.util.Map.of(other.getPaymentId(),
                        new ReceiptWorksheetData.Use(List.of(), null, true)), null), o.footerStatus()));
        var result = calculator.reconcilePayments(tx, null);
        assertEquals(TransactionCalculator.PaymentRole.CHECKOUT_DEDUCTION, calculator.paymentRole(tx, other));
        assertEquals(0, result.knownFundingSubtotal().signum()); assertNull(result.recordedCashCardPaid());
    }

}
class RecordedCalculationChecksTest {
    @Test void showsBothCalculationsForHistoricalReceiptsWithoutChangingTheirFacts() {
        var tx=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,new BigDecimal("25.96")));
        tx.addPurchaseItem(new PurchaseItem("Product",null,new BigDecimal("25.96"),new BigDecimal("25.96"),1));
        tx.addPayment(new Payment(PaymentType.WALGREENS_CASH,new BigDecimal("20")));tx.addPayment(new Payment(PaymentType.CARD,new BigDecimal("5.96")));
        var metadata=new ReceiptMeta("Walgreens","original",null,null);var before=ReceiptSnapshot.from(tx);var calculator=new TransactionCalculator();
        var result=calculator.recordedChecks(tx,metadata);
        assertEquals(new BigDecimal("25.96"),result.beforeFunding().calculatedTotal());assertEquals(new BigDecimal("5.96"),result.afterRewards().calculatedTotal());
        assertEquals(0,result.beforeFunding().purchaseDifference().signum());assertEquals(0,result.beforeFunding().paymentDifference().signum());
        assertEquals(new BigDecimal("20.00"),result.afterRewards().purchaseDifference());assertFalse(result.beforeFunding().verified());assertFalse(result.afterRewards().verified());
        assertEquals(before,ReceiptSnapshot.from(tx));assertEquals(ReceiptMeta.PrintedTotalBasis.UNKNOWN,metadata.printedTotalBasis());assertNull(calculator.reconcilePayments(tx,metadata).calculatedPurchaseTotal());
    }
    @Test void zerosAreDisplayedButVerificationStillRequiresExplicitBasisAndCompleteResolvedEntry() {
        var tx=new CouponTransaction(new ReceiptTotals(new BigDecimal("0.21"),new BigDecimal("0.79")));
        tx.addPurchaseItem(new PurchaseItem("Nivea",null,new BigDecimal("3.79"),new BigDecimal("3.79"),2));
        tx.addPayment(new Payment(PaymentType.WALGREENS_CASH,new BigDecimal("2")));tx.addPayment(new Payment(PaymentType.CASH,new BigDecimal("0.79")));
        tx.setObservations(new ReceiptObservations(null,null,List.of(),List.of(new UnidentifiedRR(UUID.randomUUID(),new BigDecimal("5"),"Lysol")),null,List.of(),Map.of()));
        var metadata=new ReceiptMeta("Walgreens",null,null,null,ReceiptMeta.PurchaseChannel.UNKNOWN,ReceiptMeta.PrintedTotalBasis.AFTER_REWARD_REDEMPTIONS,ReceiptMeta.PaymentEntryStatus.COMPLETE);
        var calculator=new TransactionCalculator();var checks=calculator.recordedChecks(tx,metadata);
        assertEquals(new BigDecimal("0.79"),checks.afterRewards().calculatedTotal());assertEquals(0,checks.afterRewards().purchaseDifference().signum());assertEquals(0,checks.afterRewards().paymentDifference().signum());assertTrue(checks.afterRewards().verified());assertFalse(checks.beforeFunding().verified());
        tx.addPayment(new Payment(PaymentType.OTHER,BigDecimal.ONE));
        assertFalse(calculator.recordedChecks(tx,metadata).afterRewards().verified());
    }
}
