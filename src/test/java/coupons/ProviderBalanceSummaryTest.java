package coupons;

import coupons.ReceiptObservations.*;
import coupons.repository.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProviderBalanceSummaryTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 4);
    private static final Currency USD = Currency.getInstance("USD");

    private Earning earned(EarningKind kind, String provider, BenefitKind form, String amount,
            RebateStatus status, RecordedValuation valuation) {
        return new Earning(UUID.randomUUID(), kind, provider, null, Scope.RECEIPT, List.of(), null,
                new RebateBenefit(form, new BigDecimal(amount), form == BenefitKind.POINTS ? null : USD, provider),
                status, null, null, null, List.of(), null, null, valuation);
    }

    private ReceiptRecord receipt(LocalDate day, List<Earning> earnings, boolean used) {
        var tx = new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO, BigDecimal.ZERO));
        if (used) tx.addPayment(new Payment(PaymentType.WALGREENS_CASH, new BigDecimal("10.00")));
        tx.setObservations(new ReceiptObservations(day, null, earnings,
                used ? List.of(new UnidentifiedRR(UUID.randomUUID(), new BigDecimal("2.00"), "Palmolive")) : List.of(),
                null, List.of(), Map.of()));
        return new ReceiptRecord(UUID.randomUUID(), ReceiptStatus.ACTIVE, 1, tx);
    }

    private String print(ProviderBalanceSummary summary, List<ReceiptRecord> records) {
        var bytes = new ByteArrayOutputStream();
        summary.print(DAY, records, new PrintStream(bytes));
        return bytes.toString().replaceAll(" +", " ");
    }

    @Test
    void computesDayActivityOnceAndKeepsMonthAndDayOpeningsSeparate() {
        var summary = new ProviderBalanceSummary();
        summary.recordOpening(DAY, "WC", new BigDecimal("240.00"), true);
        summary.recordOpening(DAY, "WC", new BigDecimal("247.75"), false);
        summary.recordOpening(DAY, "RR", new BigDecimal("2.00"), true);
        summary.recordOpening(DAY, "RR", new BigDecimal("2.00"), false);
        summary.recordOpening(DAY, "Fetch", new BigDecimal("33.59"), true);
        summary.recordOpening(DAY, "Fetch", new BigDecimal("33.59"), false);
        var record = receipt(DAY, List.of(
                earned(EarningKind.WALGREENS_CASH, "Walgreens", BenefitKind.STORE_REWARD, "0.80", RebateStatus.CREDITED, null),
                earned(EarningKind.REGISTER_REWARD, "Walgreens", BenefitKind.STORE_COUPON, "7", RebateStatus.PAID, null),
                earned(EarningKind.REGISTER_REWARD, "Walgreens", BenefitKind.STORE_COUPON, "5", RebateStatus.PAID, null),
                earned(EarningKind.THIRD_PARTY_REBATE, "Fetch", BenefitKind.POINTS, "25", RebateStatus.CREDITED,
                        new RecordedValuation(new BigDecimal("0.025"), USD, "spreadsheet"))), true);
        String output = print(summary, List.of(record, record, receipt(DAY.plusDays(1), List.of(), true)));
        assertTrue(output.contains("Walgreens Cash $240.00 $247.75 $238.55"), output);
        assertTrue(output.contains("Register Rewards $2.00 $2.00 $12.00"), output);
        assertTrue(output.contains("Fetch $33.59 $33.59 $33.62"), output);
        assertTrue(output.contains("1 recorded receipt(s)"));
        assertEquals(output, print(summary, List.of(record, record, receipt(DAY.plusDays(1), List.of(), true))));
    }

    @Test
    void missingValuationAndStartingBalancesAreNotInventedAndPendingIsExcluded() {
        var summary = new ProviderBalanceSummary();
        summary.recordOpening(DAY, "Fetch", BigDecimal.ONE, false);
        summary.recordOpening(DAY, "Ibotta", new BigDecimal("10"), false);
        String output = print(summary, List.of(receipt(DAY, List.of(
                earned(EarningKind.THIRD_PARTY_REBATE, "Fetch", BenefitKind.POINTS, "25", RebateStatus.CREDITED, null),
                earned(EarningKind.THIRD_PARTY_REBATE, "Ibotta", BenefitKind.CASH, "3.05", RebateStatus.APPROVED, null)), false)));
        assertTrue(output.contains("Fetch not entered $1.00 not entered"), output);
        assertTrue(output.contains("Ibotta not entered $10.00 $10.00"), output);
        assertTrue(output.contains("Expected/pending/approved benefits excluded from balances: 1"));
        assertTrue(output.contains("partial"));
    }

    @Test
    void openingCorrectionsRetainPriorObservations() {
        var summary = new ProviderBalanceSummary();
        summary.recordOpening(DAY, "Swag", BigDecimal.ONE, false);
        summary.recordOpening(DAY, "Swagbucks", new BigDecimal("9.95"), false);
        assertEquals(2, summary.openingHistory().size());
        assertEquals(BigDecimal.ONE, summary.openingHistory().get(0).value());
        assertTrue(print(summary, List.of()).contains("Swagbucks not entered $9.95 $9.95"));
        assertThrows(UnsupportedOperationException.class, () -> summary.openingHistory().clear());
    }
}
