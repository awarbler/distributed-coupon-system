package coupons;

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
}