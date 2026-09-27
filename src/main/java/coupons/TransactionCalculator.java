package coupons;

import java.util.Objects;

/**
 * Calculates monetary totals from the contents of a coupon transaction.
 */
public class TransactionCalculator {

    /**
     * Calculates the regular-price merchandise total.
     *
     * @param transaction transaction to calculate
     * @return combined regular-price value of all purchased items
     */
    public double calculateRegularMerchandiseTotal(
            CouponTransaction transaction) {

        validateTransaction(transaction);

        double total = 0.0;

        for (PurchaseItem item : transaction.getPurchaseItems()) {
            total += item.getRegularUnitPrice() * item.getQuantity();
        }

        return total;
    }

    /**
     * Calculates the sale-price merchandise total.
     *
     * @param transaction transaction to calculate
     * @return combined sale-price value of all purchased items
     */
    public double calculateSaleMerchandiseTotal(
            CouponTransaction transaction) {

        validateTransaction(transaction);

        double total = 0.0;

        for (PurchaseItem item : transaction.getPurchaseItems()) {
            total += item.calculateLineTotal();
        }

        return total;
    }

    /**
     * Calculates the total value of all coupons applied to the transaction.
     *
     * @param transaction transaction to calculate
     * @return combined coupon discount
     */
    public double calculateCouponTotal(
            CouponTransaction transaction) {

        validateTransaction(transaction);

        double total = 0.0;

        for (Coupon coupon : transaction.getCoupons()) {
            total += coupon.getDiscountAmount();
        }

        return total;
    }

    /**
     * Calculates the merchandise amount remaining after coupons.
     *
     * @param transaction transaction to calculate
     * @return sale merchandise total minus coupon discounts
     */
    public double calculateAfterCouponTotal(
            CouponTransaction transaction) {

        validateTransaction(transaction);

        return calculateSaleMerchandiseTotal(transaction)
                - calculateCouponTotal(transaction);
    }

    /**
     * Calculates Walgreens Cash redeemed on the transaction.
     *
     * @param transaction transaction to calculate
     * @return total Walgreens Cash redeemed
     */
    public double calculateWalgreensCashRedeemed(
            CouponTransaction transaction) {

        validateTransaction(transaction);

        return calculatePaymentsByType(
                transaction,
                PaymentType.WALGREENS_CASH
        );
    }

    /**
     * Calculates Register Rewards redeemed on the transaction.
     *
     * @param transaction transaction to calculate
     * @return total Register Reward value redeemed
     */
    public double calculateRegisterRewardsRedeemed(
            CouponTransaction transaction) {

        validateTransaction(transaction);

        return calculatePaymentsByType(
                transaction,
                PaymentType.REGISTER_REWARD
        );
    }

    /**
     * Calculates the total reward value redeemed on the transaction.
     *
     * @param transaction transaction to calculate
     * @return combined Walgreens Cash and Register Reward redemptions
     */
    public double calculateRewardRedemptionTotal(
            CouponTransaction transaction) {

        validateTransaction(transaction);

        return calculateWalgreensCashRedeemed(transaction)
                + calculateRegisterRewardsRedeemed(transaction);
    }

    /**
     * Calculates the merchandise amount remaining after coupons and reward
     * redemptions.
     *
     * @param transaction transaction to calculate
     * @return merchandise amount remaining after reward redemptions
     */
    public double calculateAfterRewardRedemptions(
            CouponTransaction transaction) {

        validateTransaction(transaction);

        return calculateAfterCouponTotal(transaction)
                - calculateRewardRedemptionTotal(transaction);
    }

    /**
     * Calculates the expected checkout amount using the tax reported on the
     * receipt.
     *
     * @param transaction transaction to calculate
     * @return expected checkout amount
     */
    public double calculateExpectedCheckoutAmount(
            CouponTransaction transaction) {

        validateTransaction(transaction);

        return calculateAfterRewardRedemptions(transaction)
                + transaction.getReceiptTotals().getTaxAmount();
    }

    /**
     * Calculates the difference between the retailer-reported final total and
     * the amount predicted by the current transaction model.
     *
     * @param transaction transaction to compare
     * @return receipt total minus calculated checkout amount
     */
    public double calculateReceiptDifference(
            CouponTransaction transaction) {

        validateTransaction(transaction);

        return transaction.getReceiptTotals().getFinalTotal()
                - calculateExpectedCheckoutAmount(transaction);
    }

    /**
     * Calculates the total amount recorded for a particular payment type.
     */
    private double calculatePaymentsByType(
            CouponTransaction transaction,
            PaymentType paymentType) {

        Objects.requireNonNull(
                paymentType,
                "Payment type cannot be null."
        );

        double total = 0.0;

        for (Payment payment : transaction.getPayments()) {
            if (payment.getPaymentType() == paymentType) {
                total += payment.getAmount();
            }
        }

        return total;
    }

    /**
     * Rejects a null transaction before performing a calculation.
     */
    private void validateTransaction(CouponTransaction transaction) {
        Objects.requireNonNull(
                transaction,
                "Transaction cannot be null."
        );
    }
}