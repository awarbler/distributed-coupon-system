package coupons.receipts;

import coupons.promotions.CouponApplied;

import coupons.rewards.RegisterRewardRedemption;
import coupons.rewards.Reward;

import java.math.BigDecimal;
import java.util.*;
import coupons.repository.ReceiptMeta;

/**
 * Calculates monetary totals from the contents of a coupon transaction.
 */
public class TransactionCalculator {

    public enum PaymentRole { FUNDING, CHECKOUT_DEDUCTION, UNRESOLVED }
    public enum ReconciliationStatus { BALANCED, DIFFERENCE, INCOMPLETE, CANNOT_VERIFY }
    public record PaymentReconciliation(BigDecimal printedTotal, BigDecimal calculatedPurchaseTotal,
            BigDecimal purchaseDifference, BigDecimal knownFundingSubtotal, BigDecimal recordedCashCardPaid,
            BigDecimal giftCardFunding, BigDecimal otherFunding, BigDecimal difference,
            ReconciliationStatus status, ReceiptMeta.PrintedTotalBasis printedTotalBasis,
            ReceiptMeta.PaymentEntryStatus paymentEntryStatus, List<UUID> unresolvedPaymentIds,
            String reason, boolean acknowledgementRequired) {}

    /** Classifies evidence; absent OTHER and legacy RR roles are never inferred. */
    public PaymentRole paymentRole(CouponTransaction tx, Payment payment) {
        validateTransaction(tx);
        var use = tx.getObservations().worksheet().uses().get(payment.getPaymentId());
        if (use != null && use.checkoutDeduction()) {
            // These types conflict with their ordinary funding semantics; require review.
            return switch (payment.getPaymentType()) {
                case CASH, CARD, CREDIT_CARD, DEBIT_CARD, REGISTER_REWARD -> PaymentRole.UNRESOLVED;
                default -> PaymentRole.CHECKOUT_DEDUCTION;
            };
        }
        return switch (payment.getPaymentType()) {
            case CASH, CARD, CREDIT_CARD, DEBIT_CARD, GIFT_CARD, WALGREENS_CASH -> PaymentRole.FUNDING;
            case OTHER -> use == null ? PaymentRole.UNRESOLVED : PaymentRole.FUNDING;
            case REGISTER_REWARD -> PaymentRole.UNRESOLVED;
        };
    }

    public PaymentReconciliation reconcilePayments(CouponTransaction tx, ReceiptMeta metadata) {
        validateTransaction(tx);
        var basis = metadata == null ? ReceiptMeta.PrintedTotalBasis.UNKNOWN : metadata.printedTotalBasis();
        var entry = metadata == null ? ReceiptMeta.PaymentEntryStatus.UNKNOWN : metadata.paymentEntryStatus();
        BigDecimal funding = BigDecimal.ZERO, cashCard = BigDecimal.ZERO,
                gift = BigDecimal.ZERO, other = BigDecimal.ZERO;
        boolean monetaryEntry = false;
        List<UUID> unresolved = new ArrayList<>();
        for (var payment : tx.getPayments()) {
            var role = paymentRole(tx, payment);
            if (role == PaymentRole.UNRESOLVED) {
                unresolved.add(payment.getPaymentId());
                continue;
            }
            if (role != PaymentRole.FUNDING) continue;
            var amount = payment.getAmount();
            switch (payment.getPaymentType()) {
                case CASH, CARD, CREDIT_CARD, DEBIT_CARD -> {
                    cashCard = cashCard.add(amount); monetaryEntry = true;
                }
                case GIFT_CARD -> gift = gift.add(amount);
                case OTHER -> other = other.add(amount);
                default -> { }
            }
            if (!(basis == ReceiptMeta.PrintedTotalBasis.AFTER_REWARD_REDEMPTIONS
                    && payment.getPaymentType() == PaymentType.WALGREENS_CASH))
                funding = funding.add(amount);
        }
        BigDecimal printed = tx.getReceiptTotals().getFinalTotal();
        BigDecimal calculated = basis == ReceiptMeta.PrintedTotalBasis.UNKNOWN ? null
                : calculateExpectedCheckoutAmount(tx).add(basis == ReceiptMeta.PrintedTotalBasis.BEFORE_PAYMENT_FUNDING
                        ? calculateWalgreensCashRedeemed(tx) : BigDecimal.ZERO);
        BigDecimal purchaseDifference = calculated == null ? null : printed.subtract(calculated);
        boolean complete = entry == ReceiptMeta.PaymentEntryStatus.COMPLETE && unresolved.isEmpty();
        BigDecimal difference = basis != ReceiptMeta.PrintedTotalBasis.UNKNOWN && complete
                ? printed.subtract(funding) : null;
        ReconciliationStatus status = basis == ReceiptMeta.PrintedTotalBasis.UNKNOWN
                ? ReconciliationStatus.CANNOT_VERIFY
                : !complete ? ReconciliationStatus.INCOMPLETE
                : difference.signum() == 0 && purchaseDifference.signum() == 0
                        ? ReconciliationStatus.BALANCED : ReconciliationStatus.DIFFERENCE;
        String reason = switch (status) {
            case BALANCED -> "Printed total, calculated purchase and recorded funding reconcile; payment entry was explicitly confirmed complete.";
            case DIFFERENCE -> "A purchase-total or funding difference remains. Preserve the printed evidence and review the difference.";
            case INCOMPLETE -> "Payment entry is not confirmed complete or payment roles are unresolved. Known subtotals are not proof of completeness.";
            case CANNOT_VERIFY -> "Printed-total basis is unknown. Historical values and legacy comparisons are preserved without reinterpretation.";
        };
        boolean acknowledge = basis == ReceiptMeta.PrintedTotalBasis.UNKNOWN
                ? calculateReceiptDifference(tx).signum() != 0
                : purchaseDifference.signum() != 0 || difference != null && difference.signum() != 0;
        // A gift card exactly covering a known printed funding basis establishes
        // zero cash/card without creating an invalid zero-value Payment object.
        boolean giftCardFullyFundsPurchase = basis != ReceiptMeta.PrintedTotalBasis.UNKNOWN
                && unresolved.isEmpty() && gift.signum() > 0 && funding.compareTo(printed) == 0;
        return new PaymentReconciliation(printed, calculated, purchaseDifference, funding,
                monetaryEntry || complete || giftCardFullyFundsPurchase ? cashCard : null, gift, other, difference, status, basis, entry,
                List.copyOf(unresolved), reason, acknowledge);
    }

    public record CalculationScenario(ReceiptMeta.PrintedTotalBasis basis,BigDecimal calculatedTotal,
            BigDecimal purchaseDifference,BigDecimal knownFundingSubtotal,BigDecimal paymentDifference,boolean verified) { }
    public record RecordedChecks(CalculationScenario beforeFunding,CalculationScenario afterRewards) { }
    /** Always available arithmetic; verification remains conditional on explicit receipt metadata. */
    public RecordedChecks recordedChecks(CouponTransaction tx,ReceiptMeta metadata) {
        var entry=metadata==null?ReceiptMeta.PaymentEntryStatus.UNKNOWN:metadata.paymentEntryStatus();
        var values=new ArrayList<CalculationScenario>();
        for(var basis:List.of(ReceiptMeta.PrintedTotalBasis.BEFORE_PAYMENT_FUNDING,ReceiptMeta.PrintedTotalBasis.AFTER_REWARD_REDEMPTIONS)) {
            var review=reconcilePayments(tx,new ReceiptMeta("Recorded calculation",null,null,null,ReceiptMeta.PurchaseChannel.UNKNOWN,basis,entry));
            boolean verified=metadata!=null && metadata.printedTotalBasis()==basis && entry==ReceiptMeta.PaymentEntryStatus.COMPLETE && review.unresolvedPaymentIds().isEmpty();
            values.add(new CalculationScenario(basis,review.calculatedPurchaseTotal(),review.purchaseDifference(),review.knownFundingSubtotal(),review.printedTotal().subtract(review.knownFundingSubtotal()),verified));
        }
        return new RecordedChecks(values.get(0),values.get(1));
    }

    /**
     * Calculates the regular-price merchandise total.
     *
     * @param transaction transaction to calculate
     * @return combined regular-price value of all purchased items
     * @throws IllegalStateException if a regular unit price is unknown
     */
    public BigDecimal calculateRegularMerchandiseTotal(
            CouponTransaction transaction) {

        validateTransaction(transaction);

        BigDecimal total = BigDecimal.ZERO;

        for (PurchaseItem item : transaction.getPurchaseItems()) {
            BigDecimal regularUnitPrice = item.getRegularUnitPrice();

            if (regularUnitPrice == null) {
                throw new IllegalStateException(
                        "Regular merchandise total is unknown because "
                        + "a receipt line has no regular unit price."
                );
            }

            total = total.add(
                    regularUnitPrice.multiply(
                            BigDecimal.valueOf(item.getQuantity())
                    )
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
     * Calculates actual ordinary coupon deductions.
     *
     * @param transaction transaction to calculate
     * @return total applied coupon deductions
     * @throws NullPointerException if transaction is null
     */
    public BigDecimal calculateCouponTotal(
            CouponTransaction transaction) {

        validateTransaction(transaction);

        BigDecimal total = BigDecimal.ZERO;

        for (CouponApplied applied : transaction.getAppliedCoupons()) {
            total = total.add(applied.getAppliedAmount());
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
     * Calculates actual Register Reward redemption amounts.
     *
     * <p>
     * Legacy Register Reward payment entries are not included.
     * Recorded unidentified-instrument deductions are included once, separately
     * from identified redemptions. Printed savings and account summaries are not deductions.
     *
     * @param transaction transaction to calculate
     * @return total recorded Register Reward redemptions
     * @throws NullPointerException if transaction is null
     */
    public BigDecimal calculateRegisterRewardsRedeemed(
            CouponTransaction transaction) {

        validateTransaction(transaction);
        BigDecimal total = BigDecimal.ZERO;

        for (RegisterRewardRedemption redemption
                : transaction.getRegisterRewardRedemptions()) {
            total = total.add(redemption.getRedeemedAmount());
        }

        for (ReceiptObservations.UnidentifiedRR deduction
                : transaction.getObservations().unidentifiedRR()) {
            total = total.add(deduction.amount());
        }

        return total;
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

        BigDecimal other = transaction.getPayments().stream().filter(p -> {
            var use=transaction.getObservations().worksheet().uses().get(p.getPaymentId());
            return use!=null && use.checkoutDeduction();
        }).map(Payment::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        return calculateWalgreensCashRedeemed(transaction).add(calculateRegisterRewardsRedeemed(transaction)).add(other);
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
