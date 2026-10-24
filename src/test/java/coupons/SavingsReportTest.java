package coupons;

import coupons.ReceiptObservations.*;
import coupons.repository.*;
import java.io.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SavingsReportTest {
    private static final LocalDate DAY=LocalDate.of(2026,10,4);
    private BigDecimal n(String s) { return new BigDecimal(s); }
    private Earning earning(EarningKind kind,String provider,String amount,BenefitKind form,RebateStatus status,BigDecimal valuation) {
        return new Earning(UUID.randomUUID(),kind,provider,null,Scope.RECEIPT,List.of(),null,
                new RebateBenefit(form,n(amount),form==BenefitKind.POINTS?null:Currency.getInstance("USD"),provider),status,
                null,null,null,List.of(),null,null,valuation==null?null:new RecordedValuation(valuation,Currency.getInstance("USD"),"Spreadsheet"));
    }
    private ReceiptRecord receipt(String number,String regular,String tax,String due,String cash,String rrUsed,String wcUsed,
            String sale,String mq,List<Earning> earnings,LocalDate date) {
        var tx=new CouponTransaction(new ReceiptTotals(n(tax),n(due)));
        tx.addPurchaseItem(new PurchaseItem("Products",null,n(regular),n(regular),1));
        var kinds=new HashMap<UUID,DiscountKind>();
        if(n(sale).signum()!=0) { var c=new CouponApplied("Promotion",n(sale),List.of(tx.getPurchaseItems().get(0).getLineItemId()));tx.addAppliedCoupon(c);kinds.put(c.getApplicationId(),DiscountKind.SALE_PROMOTION); }
        if(n(mq).signum()!=0) { var c=new CouponApplied("Manufacturer",n(mq),List.of(tx.getPurchaseItems().get(0).getLineItemId()));tx.addAppliedCoupon(c);kinds.put(c.getApplicationId(),DiscountKind.MANUFACTURER); }
        if(n(cash).signum()!=0) tx.addPayment(new Payment(PaymentType.CASH,n(cash)));
        if(n(wcUsed).signum()!=0) tx.addPayment(new Payment(PaymentType.WALGREENS_CASH,n(wcUsed)));
        tx.setObservations(new ReceiptObservations(date,null,earnings,n(rrUsed).signum()==0?List.of():List.of(new UnidentifiedRR(UUID.randomUUID(),n(rrUsed),"Prior coupon")),null,List.of(),kinds));
        return new ReceiptRecord(UUID.randomUUID(),ReceiptStatus.ACTIVE,1,tx,new ReceiptMeta("Walgreens",number,null,null));
    }
    private ReceiptRecord first() {
        return receipt("R1","22.16","1.04","3.60","3.60","2","10","3.60","4",List.of(
                earning(EarningKind.REGISTER_REWARD,"Walgreens","12",BenefitKind.STORE_COUPON,RebateStatus.PAID,null),
                earning(EarningKind.WALGREENS_CASH,"Walgreens","0.80",BenefitKind.STORE_REWARD,RebateStatus.CREDITED,null),
                earning(EarningKind.THIRD_PARTY_REBATE,"Fetch","25",BenefitKind.POINTS,RebateStatus.CREDITED,n("0.025"))),DAY);
    }
    private ReceiptRecord second() {
        return receipt("R2","46.94","1.73","26.69","26.69","7","0","10.98","4",List.of(
                earning(EarningKind.REGISTER_REWARD,"Walgreens","19",BenefitKind.STORE_COUPON,RebateStatus.PAID,null),
                earning(EarningKind.WALGREENS_CASH,"Walgreens","18.50",BenefitKind.STORE_REWARD,RebateStatus.CREDITED,null),
                earning(EarningKind.THIRD_PARTY_REBATE,"Fetch","25",BenefitKind.POINTS,RebateStatus.CREDITED,n("0.025")),
                earning(EarningKind.THIRD_PARTY_REBATE,"P&G Brand","250",BenefitKind.POINTS,RebateStatus.CREDITED,n("250").divide(n("150"),MathContext.DECIMAL128))),DAY);
    }
    @Test void rewardsOverviewAddsClosingBalancesToOutstandingExpectedRewardsOnce() {
        var summary=new ProviderBalanceSummary();summary.recordOpening(DAY,"Walgreens Cash",n("20"),false);
        var r=receipt("Rewards","10","0","2","2","0","0","0","0",List.of(
                earning(EarningKind.WALGREENS_CASH,"Walgreens","3",BenefitKind.STORE_REWARD,RebateStatus.CREDITED,null),
                earning(EarningKind.THIRD_PARTY_REBATE,"Ibotta","5",BenefitKind.CASH,RebateStatus.EXPECTED,null),
                earning(EarningKind.THIRD_PARTY_REBATE,"Ibotta","99",BenefitKind.CASH,RebateStatus.REJECTED,null)),DAY);
        var result=PostgresPlatformRepository.rewardsOverview(summary,List.of(r),DAY,List.of("Walgreens Cash"));
        assertEquals(0,n("23").compareTo((BigDecimal)result.get("available")));
        assertEquals(0,n("5").compareTo((BigDecimal)result.get("expected")));
        assertEquals(0,n("28").compareTo((BigDecimal)result.get("total")));
        assertEquals(1,result.get("expectedCount"));
    }
    @Test void benefitReportIncludesExistingPrintedWcFallbackWithoutCreatingAnEarning() {
        var r=receipt("Footer","10","0","2","2","0","0","0","0",List.of(),DAY);
        r.transaction().setObservations(new ReceiptObservations(DAY,null,List.of(),List.of(),new WalgreensCashAccount(n("5"),BigDecimal.ZERO,n("2.66"),n("7.66")),List.of(),Map.of()));
        var report=PostgresPlatformRepository.receiptReport(List.of(r),"benefits",null,null,"","newest");
        assertEquals(n("2.66"),report.get("matchedTotal"));
        assertTrue(r.transaction().getObservations().earnings().isEmpty());
    }
    @Test void benefitReportSeparatesConfirmedExpectedAndRejectedValues() {
        var receipt=receipt("Benefits","10","0","2","2","0","0","0","0",List.of(
                earning(EarningKind.WALGREENS_CASH,"Walgreens","7",BenefitKind.STORE_REWARD,RebateStatus.CREDITED,null),
                earning(EarningKind.WALGREENS_CASH,"Walgreens","5",BenefitKind.STORE_REWARD,RebateStatus.EXPECTED,null),
                earning(EarningKind.WALGREENS_CASH,"Walgreens","9",BenefitKind.STORE_REWARD,RebateStatus.REJECTED,null)),DAY);
        var report=PostgresPlatformRepository.receiptReport(List.of(receipt),"benefits",DAY,DAY,"","oldest");
        assertEquals(0,n("7").compareTo((BigDecimal)report.get("matchedTotal")));
        assertEquals(0,n("5").compareTo((BigDecimal)report.get("unconfirmedTotal")));
        assertEquals(3,((List<?>)report.get("rows")).size());
        var filtered=PostgresPlatformRepository.receiptReport(List.of(receipt),"benefits",DAY,DAY,"Unconfirmed","oldest");
        assertEquals(1,((List<?>)filtered.get("rows")).size());
        assertEquals(0,n("7").compareTo((BigDecimal)((Map<?,?>)filtered.get("summary")).get("newlyEarned")));
    }
    @Test void receiptMetricReportsReconcileAndFilterWithoutChangingPeriodTotals() {
        var first=first();var records=List.of(first,second());
        for(var metric:List.of("regular","oop","saved","net")) {
            var report=PostgresPlatformRepository.receiptReport(records,metric,DAY,DAY,"","newest");
            var summary=(Map<?,?>)report.get("summary");
            String key=switch(metric){case "regular"->"regularWithTax";case "oop"->"recordedCashCardPaid";case "saved"->"totalSaved";default->"paidAfterRebates";};
            assertEquals(0,((BigDecimal)summary.get(key)).compareTo((BigDecimal)report.get("matchedTotal")));
        }
        var regular=PostgresPlatformRepository.receiptReport(records,"regular",DAY,DAY,"Products","item");
        assertEquals(2,((List<?>)regular.get("rows")).size());
        assertEquals(n("69.10"),regular.get("matchedTotal"));
        var oop=PostgresPlatformRepository.receiptReport(List.of(first),"oop",DAY,DAY,"Cash","store");
        assertEquals(0,n("3.60").compareTo((BigDecimal)oop.get("matchedCash")));
        assertEquals(BigDecimal.ZERO,oop.get("matchedCard"));
        var deleted=new ReceiptRecord(first.transactionId(),ReceiptStatus.DELETED,2,first.transaction(),first.metadata(),first.evidenceIds());
        assertTrue(((List<?>)PostgresPlatformRepository.receiptReport(List.of(first,deleted),"regular",null,null,"","oldest").get("rows")).isEmpty());
    }
    @Test void productLookupUsesLatestActiveRevisionsAndRetainsSkuConflictsAndZeros() {
        var a=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,BigDecimal.ONE));
        a.addPurchaseItem(new PurchaseItem("Soap","00123",n("1"),n("1"),4));
        var first=new ReceiptRecord(UUID.randomUUID(),ReceiptStatus.ACTIVE,1,a);
        var b=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,BigDecimal.ONE));
        b.addPurchaseItem(new PurchaseItem("Soap","00123",n("2.49"),n("2.49"),2));
        var latest=new ReceiptRecord(first.transactionId(),ReceiptStatus.ACTIVE,2,b);
        var c=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,BigDecimal.ONE));
        c.addPurchaseItem(new PurchaseItem("Different description","00123",n("3"),n("3"),1));
        var other=new ReceiptRecord(UUID.randomUUID(),ReceiptStatus.ACTIVE,1,c);
        var suggestions=PostgresPlatformRepository.productLookup(List.of(first,latest,other));
        assertEquals(2,suggestions.size());assertTrue(suggestions.stream().allMatch(r->"00123".equals(r.get("sku"))));
        assertEquals(n("2.49"),suggestions.stream().filter(r->"Soap".equals(r.get("description"))).findFirst().orElseThrow().get("regularPrice"));
        var deleted=new ReceiptRecord(first.transactionId(),ReceiptStatus.DELETED,3,b);
        assertEquals(1,PostgresPlatformRepository.productLookup(List.of(first,latest,other,deleted)).size());
    }
    @Test void existingPgAndFetchPointsWithoutValuationsUseAutomaticRatesWithoutMutation() {
        var pg=earning(EarningKind.THIRD_PARTY_REBATE,"P&G Brand","100",BenefitKind.POINTS,RebateStatus.CREDITED,null);
        var fetch=earning(EarningKind.THIRD_PARTY_REBATE,"Fetch","25",BenefitKind.POINTS,RebateStatus.CREDITED,null);
        var r=receipt("Auto","10","0","2","2","0","0","0","0",List.of(pg,fetch),DAY);
        var totals=SavingsReport.calculate(List.of(r));
        assertTrue(totals.earningsKnown()); assertEquals(0,totals.newlyEarned().compareTo(n("0.425")));
        assertEquals(0,totals.paidAfterRebates().compareTo(n("1.575")));
        assertNull(pg.valuation()); assertNull(fetch.valuation());
        var explicit=earning(EarningKind.THIRD_PARTY_REBATE,"P&G Brand","100",BenefitKind.POINTS,RebateStatus.CREDITED,n("0.60"));
        assertEquals(n("0.60"),SavingsReport.effectiveValuation(explicit).amount());
    }
    @Test void dateRangesIncludeEndpointsAndExcludeDeletedLatestRevisions() {
        var first = first(); var second = second();
        var deleted = new ReceiptRecord(second.transactionId(), ReceiptStatus.DELETED, 2, second.transaction(), second.metadata(), second.evidenceIds());
        assertEquals(List.of(first), SavingsReport.range(List.of(first, second, deleted), DAY, DAY));
        assertTrue(SavingsReport.range(List.of(first), DAY.plusDays(1), DAY.plusDays(3)).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> SavingsReport.range(List.of(first), DAY.plusDays(1), DAY));
        assertEquals(SavingsReport.calculate(List.of(first)).paidAfterRebates(), SavingsReport.calculate(SavingsReport.range(List.of(first), DAY.minusDays(1), DAY.plusDays(1))).paidAfterRebates());
    }
    @Test void detailedReportsSeparateRewardProgramsPaymentsAndProvidersWithoutChangingTotals() {
        var r=first();r.transaction().addPayment(new Payment(PaymentType.CARD,n("1.00")));
        var before=ReceiptSnapshot.from(r.transaction());
        var bytes=new ByteArrayOutputStream();var out=new PrintStream(bytes);
        SavingsReport.print("Transaction",List.of(r),out);
        ReceiptWorksheetReport.period("Day",List.of(r),out);
        ReceiptWorksheetReport.itemized(r,out);
        String text=bytes.toString();
        assertTrue(text.matches("(?s).*Register Rewards used +\\| \\$2.00.*"),text);
        assertTrue(text.matches("(?s).*Walgreens Cash used +\\| \\$10.00.*"),text);
        assertTrue(text.matches("(?s).*Cash paid +\\| \\$3.60.*"),text);
        assertTrue(text.matches("(?s).*Card paid +\\| \\$1.00.*"),text);
        assertTrue(text.matches("(?s).*Register Rewards earned +\\| \\$12.00.*"),text);
        assertTrue(text.matches("(?s).*Walgreens Cash earned +\\| \\$0.80.*"),text);
        assertFalse(text.contains("Prior RR/WC used"));assertFalse(text.contains("New RR / Walgreens Cash earned"));
        assertFalse(text.contains("STORE_COUPON"));assertFalse(text.contains("STORE_REWARD"));
        assertTrue(text.contains("RR used")&&text.contains("WC used")&&text.contains("Other used"),text);
        assertTrue(text.matches("(?s).*R1 +\\| Fetch +\\| points +\\| +25 +\\| +\\$0.03.*"),text);
        var total=ReceiptWorksheetReport.total(List.of(r,r));
        assertEquals(n("2"),total.rrUsed);assertEquals(n("10"),total.wcUsed);
        assertEquals(n("12"),total.rrEarned);assertEquals(n("0.80"),total.wcEarned);
        assertEquals(n("12.825"),total.earnings());assertEquals(n("32.425"),total.saved());
        assertEquals(before,ReceiptSnapshot.from(r.transaction()));
    }
    @Test void transactionDayAndMonthUseIdenticalPrecisionAndSeparateCouponsFromOldBalances() {
        var r1=first();var r2=second();var records=List.of(r1,r2);
        var one=SavingsReport.calculate(List.of(r1));
        assertEquals(n("23.20"),one.regularWithTax());assertEquals(n("4"),one.couponSavings());
        assertEquals(n("7.60"),one.salesAndCoupons());assertEquals(n("12"),one.priorRewardsUsed());
        assertEquals(n("-9.225"),one.paidAfterRebates());
        var day=SavingsReport.calculate(SavingsReport.day(records,DAY));
        assertEquals(day,SavingsReport.calculate(SavingsReport.month(records,YearMonth.of(2026,10))));
        assertEquals(n("71.87"),day.regularWithTax());assertEquals(n("30.29"),day.due());assertEquals(n("8"),day.couponSavings());
        assertEquals(n("22.58"),day.salesAndCoupons());assertEquals(n("19"),day.priorRewardsUsed());
        assertEquals(n("-21.73"),day.paidAfterRebates().setScale(2,RoundingMode.HALF_UP));
        assertEquals(n("93.60"),day.totalSaved().setScale(2,RoundingMode.HALF_UP));
        assertEquals(n("130.23"),day.percentSaved().setScale(2,RoundingMode.HALF_UP));
        assertNotEquals(one.percentSaved().add(SavingsReport.calculate(List.of(r2)).percentSaved()).divide(n("2")),day.percentSaved());
    }
    @Test void latestRevisionAndDeletionReplaceRatherThanAddAndUndatedReceiptsStayOutOfPeriods() {
        var old=first(); var updated=new ReceiptRecord(old.transactionId(),ReceiptStatus.ACTIVE,2,second().transaction(),old.metadata());
        var deleted=new ReceiptRecord(old.transactionId(),ReceiptStatus.DELETED,3,updated.transaction(),old.metadata());
        assertEquals(1,SavingsReport.calculate(List.of(old,old,updated)).receipts());
        assertEquals(n("48.67"),SavingsReport.calculate(List.of(old,updated)).regularWithTax());
        assertEquals(0,SavingsReport.calculate(SavingsReport.day(List.of(old,updated,deleted),DAY)).receipts());
        var undated=receipt("Undated","10","0","10","10","0","0","0","0",List.of(),null);
        assertEquals(0,SavingsReport.month(List.of(undated),YearMonth.of(2026,10)).size());
        assertEquals(1,SavingsReport.calculate(List.of(undated)).receipts());
    }
    @Test void missingValuationAndPendingValuesAreNotInventedAndPercentIsUnavailable() {
        var receipt=receipt("Missing","10","0","10","10","0","0","0","0",List.of(
                earning(EarningKind.THIRD_PARTY_REBATE,"Merryfield","25",BenefitKind.POINTS,RebateStatus.CREDITED,null),
                earning(EarningKind.THIRD_PARTY_REBATE,"Ibotta","99",BenefitKind.CASH,RebateStatus.PENDING,null)),DAY);
        var t=SavingsReport.calculate(List.of(receipt));assertFalse(t.earningsKnown());assertNull(t.percentSaved());
        assertEquals(BigDecimal.ZERO,t.newlyEarned());assertEquals(Set.of("Merryfield"),t.unvaluedApps());
        var bytes=new ByteArrayOutputStream();SavingsReport.print("Day",List.of(receipt),new PrintStream(bytes));
        assertTrue(bytes.toString().contains("missing USD valuation for Merryfield"));assertFalse(bytes.toString().contains("$99.00"));
        var zero=receipt("Zero","0","0","0","0","0","0","0","0",List.of(),DAY);
        assertNull(SavingsReport.calculate(List.of(zero)).percentSaved());
    }
    @Test void printedWcIsAnAlternativeObservationAndGiftTenderIsNotANewCoupon() {
        var r=receipt("Gift","20","0","20","5","0","0","0","0",List.of(),DAY);var tx=r.transaction();
        tx.addPayment(new Payment(PaymentType.GIFT_CARD,n("15")));var o=tx.getObservations();
        tx.setObservations(new ReceiptObservations(DAY,null,List.of(),List.of(),new WalgreensCashAccount(null,null,n("2"),null),List.of(),Map.of()));
        var totals=SavingsReport.calculate(List.of(r));assertEquals(n("15"),totals.otherTender());assertEquals(n("2"),totals.newStoreRewards());
        assertEquals(n("3"),totals.paidAfterRebates());assertEquals(BigDecimal.ZERO,totals.couponSavings());
        tx.setObservations(new ReceiptObservations(DAY,null,List.of(earning(EarningKind.WALGREENS_CASH,"Walgreens","2",BenefitKind.STORE_REWARD,RebateStatus.CREDITED,null)),List.of(),new WalgreensCashAccount(null,null,n("2"),null),List.of(),Map.of()));
        assertEquals(n("2"),SavingsReport.calculate(List.of(r)).newStoreRewards());
    }
    @Test void cliOffersAllThreeReportsAndDoesNotWriteAnyHistory() {
        var repo=new InMemoryTransactionRepository();
        for(var r:List.of(first(),second())) repo.save(r.transactionId(),r.transaction(),r.metadata(),List.of(),new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptCreateCtx("AJ"));
        var bytes=new ByteArrayOutputStream();
        new ReceiptEntryApp(new Scanner("6\nSAVINGS\nTRANSACTION\nR1\nDAY\n10/4/2026\nMONTH\n10/2026\nBACK\nBACK\n9\n"),new PrintStream(bytes),repo).run();
        var out=bytes.toString();assertTrue(out.contains("SAVINGS SUMMARY — Transaction R1"),out);assertTrue(out.contains("SAVINGS SUMMARY — Day 2026-10-04"),out);assertTrue(out.contains("SAVINGS SUMMARY — Month 2026-10"),out);
        assertTrue(out.matches("(?s).*Transactions +\\| +2 +\\|.*"),out);assertFalse(out.contains("Operation not saved"),out);
        assertEquals(2,repo.creationHistory().size());
        for(var h:repo.creationHistory()) assertEquals(1,repo.history().findByReceiptId(h.after().transactionId()).size());
    }

    @Test void approvalDoesNotCountAsNewlyCreditedValue() {
        var approved=earning(EarningKind.THIRD_PARTY_REBATE,"Ibotta","2.50",BenefitKind.CASH,RebateStatus.APPROVED,null);
        assertFalse(SavingsReport.earned(approved));
        for(var status:List.of(RebateStatus.EXPECTED,RebateStatus.SUBMITTED,RebateStatus.PENDING,RebateStatus.REJECTED)) {
            assertFalse(SavingsReport.earned(earning(EarningKind.THIRD_PARTY_REBATE,"Ibotta","2.50",BenefitKind.CASH,status,null)));
        }
        assertTrue(SavingsReport.earned(earning(EarningKind.REGISTER_REWARD,"Walgreens","5",BenefitKind.STORE_COUPON,RebateStatus.PAID,null)));
        assertTrue(SavingsReport.earned(earning(EarningKind.WALGREENS_CASH,"Walgreens","5",BenefitKind.STORE_REWARD,RebateStatus.CREDITED,null)));
    }

    @Test void anExplicitUncreditedWalgreensRewardCannotLeakThroughThePrintedFooterFallback() {
        var approved=earning(EarningKind.WALGREENS_CASH,"Walgreens","5",BenefitKind.STORE_REWARD,RebateStatus.APPROVED,null);
        var tx=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,BigDecimal.ONE));tx.addPurchaseItem(new PurchaseItem("Soap",null,BigDecimal.ONE,BigDecimal.ONE,1));
        tx.addPayment(new Payment(PaymentType.CASH,BigDecimal.ONE));
        var footer=new WalgreensCashAccount(n("10"),BigDecimal.ZERO,n("5"),n("15"));
        for(var status:List.of(RebateStatus.APPROVED,RebateStatus.EXPECTED,RebateStatus.REJECTED)) {
            var e=new Earning(approved.earningId(),approved.kind(),approved.provider(),null,approved.scope(),List.of(),approved.confirmed(),approved.confirmed(),status,null,null,null,List.of());
            tx.setObservations(new ReceiptObservations(DAY,"9:00 AM",List.of(e),List.of(),footer,List.of(),Map.of()));
            var receipt=new ReceiptRecord(UUID.randomUUID(),ReceiptStatus.ACTIVE,1,tx,null,List.of());
            assertEquals(0,BigDecimal.ZERO.compareTo(SavingsReport.calculate(List.of(receipt)).newlyEarned()));
            assertTrue(new ProviderBalanceSummary().movements(List.of(receipt)).isEmpty());
        }
        tx.setObservations(new ReceiptObservations(DAY,"9:00 AM",List.of(),List.of(),footer,List.of(),Map.of()));
        assertEquals(0,n("5").compareTo(SavingsReport.calculate(List.of(new ReceiptRecord(UUID.randomUUID(),ReceiptStatus.ACTIVE,1,tx,null,List.of()))).newlyEarned()));
    }
    @Test void webPaidAfterRebatesIsExactlyTheExistingCliValueWithoutCompletenessMetadata() {
        for(var receipt:List.of(first(),second())) {
            var before=ReceiptSnapshot.from(receipt.transaction());
            var cli=SavingsReport.calculate(List.of(receipt));
            var totals=PostgresPlatformRepository.totals(receipt);
            @SuppressWarnings("unchecked") var savings=(Map<String,Object>)totals.get("savings");
            assertEquals(cli.paidAfterRebates(),savings.get("paidAfterRebates"));
            assertNotNull(savings.get("paidAfterRebates"));assertNull(totals.get("benefitAdjustedCashSpending"));
            assertEquals(ReceiptMeta.PaymentEntryStatus.UNKNOWN,receipt.metadata().paymentEntryStatus());
            assertEquals(before,ReceiptSnapshot.from(receipt.transaction()));
            var out=new ByteArrayOutputStream();SavingsReport.print("Spreadsheet",List.of(receipt),new PrintStream(out));
            assertTrue(out.toString().contains("-$"+cli.paidAfterRebates().abs().setScale(2,RoundingMode.HALF_UP).toPlainString()),out.toString());
        }
    }
}
