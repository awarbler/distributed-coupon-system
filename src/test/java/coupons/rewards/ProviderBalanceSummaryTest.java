package coupons.rewards;

import coupons.promotions.BenefitKind;
import coupons.receipts.CouponTransaction;
import coupons.receipts.Payment;
import coupons.receipts.PaymentType;
import coupons.promotions.RebateBenefit;
import coupons.promotions.RebateStatus;
import coupons.receipts.ReceiptObservations;
import coupons.receipts.ReceiptTotals;

import coupons.rewards.ProviderBalanceSummary.AccountActivityView;

import java.time.*;
import java.util.*;

import coupons.receipts.ReceiptObservations.*;
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
    @Test void memberRewardExpirationSurvivesSerializationAndAppearsInActivity() throws Exception {
        var day=LocalDate.of(2026,10,24);var expiry=day.plusDays(30);
        var value=new AccountAdjustment(UUID.randomUUID(),day,"ECB",null,BigDecimal.ONE,"Member reward: bonus","test",Instant.now(),expiry);
        var bytes=new ByteArrayOutputStream();try(var out=new java.io.ObjectOutputStream(bytes)){out.writeObject(value);}
        AccountAdjustment restored;try(var in=new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray()))){restored=(AccountAdjustment)in.readObject();}
        assertEquals(expiry,restored.expirationDate());
        var repository=new InMemoryTransactionRepository();repository.recordAccountAdjustment(restored);
        assertTrue(new ProviderBalanceSummary(repository).movements(List.of()).getFirst().source().contains("Expires "+expiry));
        assertNull(new AccountAdjustment(UUID.randomUUID(),day,"ECB",null,BigDecimal.ONE,"Legacy","test",Instant.now()).expirationDate());
        assertThrows(IllegalArgumentException.class,()->new AccountAdjustment(UUID.randomUUID(),day,"ECB",null,BigDecimal.ONE,"Bad date","test",Instant.now(),day.minusDays(1)));
    }

    private static final LocalDate DAY = LocalDate.of(2026, 10, 4);
    private static final Currency USD = Currency.getInstance("USD");

    private Earning earned(EarningKind kind, String provider, BenefitKind form, String amount,
            RebateStatus status, RecordedValuation valuation) {
        return new Earning(UUID.randomUUID(), kind, provider, null, Scope.RECEIPT, List.of(), null,
                new RebateBenefit(form, new BigDecimal(amount), form == BenefitKind.POINTS ? null : USD, provider),
                status, null, null, null, List.of(), null, null, valuation);
    }

    @Test void automaticCalendarWeekAndMonthToDateTables() {
        var balances=new ProviderBalanceSummary();balances.recordOpening(LocalDate.of(2026,9,1),"Walgreens Cash",new BigDecimal("100"),true);
        var records=List.of(
                receipt(LocalDate.of(2026,9,30),List.of(earned(EarningKind.WALGREENS_CASH,"Walgreens",BenefitKind.STORE_REWARD,"2",RebateStatus.CREDITED,null)),false),
                receipt(LocalDate.of(2026,10,1),List.of(earned(EarningKind.WALGREENS_CASH,"Walgreens",BenefitKind.STORE_REWARD,"3",RebateStatus.CREDITED,null)),false),
                receipt(LocalDate.of(2026,10,4),List.of(earned(EarningKind.WALGREENS_CASH,"Walgreens",BenefitKind.STORE_REWARD,"5",RebateStatus.CREDITED,null)),false),
                receipt(LocalDate.of(2026,10,5),List.of(earned(EarningKind.WALGREENS_CASH,"Walgreens",BenefitKind.STORE_REWARD,"10",RebateStatus.CREDITED,null)),false),
                receipt(LocalDate.of(2026,10,10),List.of(earned(EarningKind.WALGREENS_CASH,"Walgreens",BenefitKind.STORE_REWARD,"20",RebateStatus.CREDITED,null)),false));
        var bytes=new ByteArrayOutputStream();balances.printPeriods(LocalDate.of(2026,10,5),records,new PrintStream(bytes));String output=bytes.toString();
        assertTrue(output.contains("WEEK 2 ACCOUNT BALANCES — 2026-10-04 through 2026-10-10"),output);
        assertTrue(output.contains("MONTH TO DATE ACCOUNT BALANCES — 2026-10-01 through 2026-10-05"),output);
        var rows=output.lines().filter(line->line.startsWith("Walgreens Cash")).map(line->java.util.Arrays.stream(line.split("\\|")).map(String::trim).toList()).toList();
        assertEquals(List.of("Walgreens Cash","$105.00","$35.00","$0.00","$140.00"),rows.get(0));
        assertEquals(List.of("Walgreens Cash","$102.00","$18.00","$0.00","$120.00"),rows.get(1));
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
        assertTrue(output.contains("Walgreens Cash | $240.00 | $247.75 | $238.55"), output);
        assertTrue(output.contains("Register Rewards | $2.00 | $2.00 | $12.00"), output);
        assertTrue(output.contains("Fetch | $33.59 | $33.59 | $33.62"), output);
        assertFalse(output.contains("Opening source:"));
        assertEquals(output, print(summary, List.of(record, record, receipt(DAY.plusDays(1), List.of(), true))));
    }

    @Test
    void missingValuationAndStartingBalancesAreNotInventedAndPendingIsExcluded() {
        var summary = new ProviderBalanceSummary();
        summary.recordOpening(DAY, "Merryfield", BigDecimal.ONE, false);
        summary.recordOpening(DAY, "Ibotta", new BigDecimal("10"), false);
        String output = print(summary, List.of(receipt(DAY, List.of(
                earned(EarningKind.THIRD_PARTY_REBATE, "Merryfield", BenefitKind.POINTS, "25", RebateStatus.CREDITED, null),
                earned(EarningKind.THIRD_PARTY_REBATE, "Ibotta", BenefitKind.CASH, "3.05", RebateStatus.APPROVED, null)), false)));
        assertTrue(output.contains("Merryfield | Missing data | $1.00 | Missing data"), output);
        assertTrue(output.contains("Ibotta | Missing data | $10.00 | $10.00"), output);
        assertEquals(new BigDecimal("10"),summary.balance(DAY,"Ibotta",List.of()).endUsd());
        assertTrue(output.contains("partial"));
    }

    @Test
    void openingCorrectionsRetainPriorObservations() {
        var summary = new ProviderBalanceSummary();
        summary.recordOpening(DAY, "Swag", BigDecimal.ONE, false);
        summary.recordOpening(DAY, "Swagbucks", new BigDecimal("9.95"), false);
        assertEquals(2, summary.openingHistory().size());
        assertEquals(BigDecimal.ONE, summary.openingHistory().get(0).value());
        assertTrue(print(summary, List.of()).contains("Swagbucks | Missing data | $9.95 | $9.95"));
        assertThrows(UnsupportedOperationException.class, () -> summary.openingHistory().clear());
    }
}

class AccountActivityViewTest {
    @Test void linksExistingEarningAndUseWithoutAddingAMovementAndCarriesOpening() {
        var day=LocalDate.of(2026,10,5);var source=UUID.randomUUID();var spent=UUID.randomUUID();var event=UUID.randomUUID();var earning=UUID.randomUUID();
        var tx=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,BigDecimal.ZERO));
        var e=new Earning(earning,EarningKind.REGISTER_REWARD,"Walgreens","Neutrogena — Buy 2, Get $7 RR",Scope.RECEIPT,List.of(),null,new RebateBenefit(BenefitKind.STORE_COUPON,new BigDecimal("7"),Currency.getInstance("USD"),"Walgreens"),RebateStatus.PAID,null,null,null,List.of());
        tx.setObservations(new ReceiptObservations(day,"8:17 AM",List.of(e),List.of(),null,List.of(),Map.of()));
        var used=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,BigDecimal.ZERO));used.setObservations(new ReceiptObservations(day,"10:13 AM",List.of(),List.of(new UnidentifiedRR(event,new BigDecimal("7"),"RR")),null,List.of(),Map.of()));
        var records=List.of(new ReceiptRecord(source,ReceiptStatus.ACTIVE,1,tx,new ReceiptMeta("Walgreens","earned",null,null)),new ReceiptRecord(spent,ReceiptStatus.ACTIVE,1,used,new ReceiptMeta("Walgreens","used",null,null)));
        var summary=new ProviderBalanceSummary();summary.recordOpening(day.minusDays(1),"Register Rewards",new BigDecimal("2"),false);
        var link=new RRUsageLink(earning,true,event,spent,new BigDecimal("7"),"Confirmed","owner",Instant.now());
        var rows=AccountActivityView.project(summary,records,List.of(link));assertEquals(2,rows.size());
        assertEquals(new BigDecimal("2"),rows.getFirst().beforeUsd());assertEquals(new BigDecimal("9"),rows.getFirst().afterUsd());assertEquals(new BigDecimal("2"),rows.getLast().afterUsd());
        assertEquals(source,rows.getLast().rewards().getFirst().receiptId());assertEquals(spent,rows.getFirst().rewards().getFirst().spentReceiptId());
        assertTrue(rows.getLast().description().startsWith("Used "));assertEquals(BigDecimal.ZERO,rows.stream().map(a->a.movement().usd()).reduce(BigDecimal.ZERO,BigDecimal::add));
        assertEquals(new BigDecimal("2"),summary.balance(day.plusDays(1),"Register Rewards",records).startUsd());
        assertNull(AccountActivityView.project(new ProviderBalanceSummary(),records,List.of(link)).getFirst().beforeUsd());
    }
    @Test void unvaluedPointsKeepNativeBalancesWithoutInventingDollarsOrPayouts() {
        var day=LocalDate.of(2026,10,5);var summary=new ProviderBalanceSummary();
        summary.recordOpening(day,"Merryfield",new BigDecimal("100"),null,false,"No dollar valuation","Confirmed points");
        var e=new Earning(UUID.randomUUID(),EarningKind.THIRD_PARTY_REBATE,"Merryfield","Receipt scan",Scope.RECEIPT,List.of(),null,new RebateBenefit(BenefitKind.POINTS,new BigDecimal("25"),null,"Merryfield"),RebateStatus.CREDITED,null,null,null,List.of());
        var tx=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,BigDecimal.ZERO));
        tx.setObservations(new ReceiptObservations(day,"9:00 AM",List.of(e),List.of(),null,List.of(),Map.of()));
        var record=new ReceiptRecord(UUID.randomUUID(),ReceiptStatus.ACTIVE,1,tx);
        var row=AccountActivityView.project(summary,List.of(record),List.of()).getFirst();
        assertEquals(new BigDecimal("100"),row.beforePoints());assertEquals(new BigDecimal("125"),row.afterPoints());
        assertNull(row.movement().usd());assertNull(row.beforeUsd());assertNull(row.afterUsd());assertNull(row.valuationBasis());assertTrue(row.rewards().isEmpty());
    }
}
