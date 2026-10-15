package coupons;

import java.math.BigDecimal;
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
    public BigDecimal calculateRegularMerchandiseTotal(
            CouponTransaction transaction) {

        validateTransaction(transaction);

        BigDecimal total = BigDecimal.ZERO;

        for (PurchaseItem item : transaction.getPurchaseItems()) {
            total = total.add(
                    item.getRegularUnitPrice()
                            .multiply(BigDecimal.valueOf(item.getQuantity()))
            );
        }

        return total;
    }

    /**
     * Calculates the sale-price merchandise total.
     *
     * @param transaction transaction to calculate
     * @return combined sale-price value of all purchased items
     */
    public BigDecimal calculateSaleMerchandiseTotal(
            CouponTransaction transaction) {

        validateTransaction(transaction);

        BigDecimal total = BigDecimal.ZERO;

        for (PurchaseItem item : transaction.getPurchaseItems()) {
            total = total.add(item.calculateLineTotal());
        }

        return total;
    }

    /**
     * Calculates the total value of all coupons applied to the transaction.
     *
     * @param transaction transaction to calculate
     * @return combined coupon discount
     */
    public BigDecimal calculateCouponTotal(
            CouponTransaction transaction) {

        validateTransaction(transaction);

        BigDecimal total = BigDecimal.ZERO;

        for (Coupon coupon : transaction.getCoupons()) {
          total = total.add(coupon.getDiscountAmount());
        }

        return total;
    }

    /**
     * Calculates the merchandise amount remaining after coupons.
     *
     * @param transaction transaction to calculate
     * @return sale merchandise total minus coupon discounts
     */
    public BigDecimal calculateAfterCouponTotal(
            CouponTransaction transaction) {

        validateTransaction(transaction);

        return calculateSaleMerchandiseTotal(transaction)
                .subtract(calculateCouponTotal(transaction));
    }

    /**
     * Calculates Walgreens Cash redeemed on the transaction.
     *
     * @param transaction transaction to calculate
     * @return total Walgreens Cash redeemed
     */
    public BigDecimal calculateWalgreensCashRedeemed(
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
    public BigDecimal calculateRegisterRewardsRedeemed(
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
    public BigDecimal calculateRewardRedemptionTotal(
            CouponTransaction transaction) {

        validateTransaction(transaction);

        return calculateWalgreensCashRedeemed(transaction)
                .add(calculateRegisterRewardsRedeemed(transaction));
    }

    /**
     * Calculates the merchandise amount remaining after coupons and reward
     * redemptions.
     *
     * @param transaction transaction to calculate
     * @return merchandise amount remaining after reward redemptions
     */
    public BigDecimal calculateAfterRewardRedemptions(
            CouponTransaction transaction) {

        validateTransaction(transaction);

        return calculateAfterCouponTotal(transaction)
                .subtract(calculateRewardRedemptionTotal(transaction));
    }

    /**
     * Calculates the expected checkout amount using the tax reported on the
     * receipt.
     *
     * @param transaction transaction to calculate
     * @return expected checkout amount
     */
    public BigDecimal calculateExpectedCheckoutAmount(
            CouponTransaction transaction) {

        validateTransaction(transaction);

        return calculateAfterRewardRedemptions(transaction)
                .add(transaction.getReceiptTotals().getTaxAmount());
    }

    /**
     * Calculates the difference between the retailer-reported final total and
     * the amount predicted by the current transaction model.
     *
     * @param transaction transaction to compare
     * @return receipt total minus calculated checkout amount
     */
    public BigDecimal calculateReceiptDifference(
            CouponTransaction transaction) {

        validateTransaction(transaction);

        return transaction.getReceiptTotals().getFinalTotal()
                .subtract(calculateExpectedCheckoutAmount(transaction));
    }

    /**
     * Calculates the total amount recorded for a particular payment type.
     */
    private BigDecimal calculatePaymentsByType(
            CouponTransaction transaction,
            PaymentType paymentType) {

        Objects.requireNonNull(
                paymentType,
                "Payment type cannot be null."
        );

        BigDecimal total = BigDecimal.ZERO;

        for (Payment payment : transaction.getPayments()) {
            if (payment.getPaymentType() == paymentType) {
                total = total.add(payment.getAmount());
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
