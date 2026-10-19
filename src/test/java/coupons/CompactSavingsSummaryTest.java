package coupons;

import coupons.ReceiptObservations.*;
import coupons.repository.*;
import java.io.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompactSavingsSummaryTest {
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private Earning earning(EarningKind kind,String provider,String value,BenefitKind form,RebateStatus status,String valuation) {
        return new Earning(UUID.randomUUID(),kind,provider,null,Scope.RECEIPT,List.of(),null,
                new RebateBenefit(form,n(value),form==BenefitKind.POINTS?null:Currency.getInstance("USD"),provider),status,
                null,null,null,List.of(),null,null,valuation==null?null:new RecordedValuation(n(valuation),Currency.getInstance("USD"),"Recorded"));
    }
    private ReceiptRecord receipt() {
        var tx=new CouponTransaction(new ReceiptTotals(n(".21"),n(".79")));
        tx.addPurchaseItem(new PurchaseItem("Item",null,n("3.79"),n("3.79"),2));
        tx.addPayment(new Payment(PaymentType.WALGREENS_CASH,n("2")));tx.addPayment(new Payment(PaymentType.CASH,n(".79")));
        tx.setObservations(new ReceiptObservations(LocalDate.of(2026,10,5),"9am",List.of(
                earning(EarningKind.REGISTER_REWARD,"Walgreens","4",BenefitKind.STORE_COUPON,RebateStatus.PAID,null),
                earning(EarningKind.THIRD_PARTY_REBATE,"Fetch","25",BenefitKind.POINTS,RebateStatus.CREDITED,".025")),
                List.of(new UnidentifiedRR(UUID.randomUUID(),n("5"),"Prior RR")),null,List.of(),Map.of()));
        return new ReceiptRecord(UUID.randomUUID(),ReceiptStatus.ACTIVE,1,tx,new ReceiptMeta("Walgreens","Compact",null,null));
    }
    private String capture(java.util.function.Consumer<PrintStream> printer) {
        var bytes=new ByteArrayOutputStream();printer.accept(new PrintStream(bytes));return bytes.toString();
    }
    @Test void compactReceiptUsesActualTendersAndUnroundedEarningsWithNoAutomaticPeriodReport() {
        var receipt=receipt();var before=ReceiptSnapshot.from(receipt.transaction());
        String text=capture(out->ReceiptWorksheetReport.compact(receipt,out));
        assertTrue(text.contains("+----------------------------------+----------+"),text);
        for(String expected:List.of("Regular total including tax", "Register Rewards used", "Walgreens Cash used", "New Register Rewards earned", "TOTAL FUTURE VALUE EARNED", "PERCENT SAVED"))assertTrue(text.contains(expected),text);
        assertTrue(text.matches("(?s).*TOTAL FUTURE VALUE EARNED +\\| +\\$4.03 +\\|.*"),text);
        assertTrue(text.matches("(?s).*PAID AFTER EARNED BENEFITS +\\| +-\\$3.24 +\\|.*"),text);
        assertTrue(text.matches("(?s).*TOTAL SAVED +\\| +\\$11.03 +\\|.*"),text);
        assertTrue(text.matches("(?s).*PERCENT SAVED +\\| +141.53% +\\|.*"),text);
        assertFalse(text.contains("Recorded line totals"));assertFalse(text.contains("Rewards used total"));
        assertFalse(text.contains("SAVINGS SUMMARY"));assertFalse(text.contains("Month to date"));
        String full=capture(out->ReceiptReport.print(receipt,out));
        assertFalse(full.contains("SAVINGS SUMMARY"),full);
        assertEquals(text.substring(text.indexOf("RECEIPT TOTALS")),full.substring(full.indexOf("RECEIPT TOTALS"),full.lastIndexOf("--------------------------------------------------")));
        assertEquals(before,ReceiptSnapshot.from(receipt.transaction()));
    }
    @Test void expectedAndUnvaluedBenefitsDoNotBecomeConfirmedFutureEarnings() {
        var receipt=receipt();var tx=receipt.transaction();var o=tx.getObservations();var earnings=new ArrayList<>(o.earnings());
        earnings.add(earning(EarningKind.THIRD_PARTY_REBATE,"Ibotta","10",BenefitKind.CASH,RebateStatus.PENDING,null));
        earnings.add(earning(EarningKind.THIRD_PARTY_REBATE,"Unknown points","100",BenefitKind.POINTS,RebateStatus.CREDITED,null));
        tx.setObservations(new ReceiptObservations(o.purchaseDate(),o.printedTime(),earnings,o.unidentifiedRR(),o.walgreensCash(),o.savings(),o.discountKinds()));
        String text=capture(out->ReceiptWorksheetReport.compact(receipt,out));
        assertTrue(text.matches("(?s).*TOTAL FUTURE VALUE EARNED +\\| +partial \\$4.03 +\\|.*"),text);
        assertTrue(text.matches("(?s).*Expected, not confirmed +\\| +\\$10.00 +\\|.*"),text);
        assertTrue(text.matches("(?s).*PERCENT SAVED +\\| +N/A +\\|.*"),text);
        assertEquals(n("4.025"),SavingsReport.calculate(List.of(receipt)).newlyEarned());
    }
    @Test void periodTotalsUseLatestActiveRevisionsAndAggregateBeforeCalculatingPercentage() {
        var original=receipt();var revised=new ReceiptRecord(original.transactionId(),ReceiptStatus.ACTIVE,2,original.transaction(),original.metadata());
        String text=capture(out->SavingsReport.printCompact("Month 2026-10",List.of(original,revised),out));
        assertTrue(text.matches("(?s).*Transactions +\\| +1 +\\|.*"),text);
        assertTrue(text.matches("(?s).*TOTAL FUTURE VALUE EARNED +\\| +\\$4.03 +\\|.*"),text);
        assertTrue(text.contains("141.53%"),text);
        var deleted=new ReceiptRecord(original.transactionId(),ReceiptStatus.DELETED,3,original.transaction(),original.metadata());
        assertTrue(capture(out->SavingsReport.printCompact("Month 2026-10",List.of(original,revised,deleted),out)).contains("No saved receipts"));
    }
    @Test void periodDetailsOnlyPrintWhenRequested() {
        var repo=new InMemoryTransactionRepository();var r=receipt();repo.save(r.transactionId(),r.transaction(),r.metadata(),List.of(),
                new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptCreateCtx("Owner"));
        String prefix="6\nSAVINGS\nMONTH\n2026-10\n";
        String compact=capture(out->new ReceiptEntryApp(new Scanner(prefix+"BACK\nBACK\n9\n"),out,repo).run());
        assertTrue(compact.contains("TOTAL FUTURE VALUE EARNED"),compact);
        assertFalse(compact.contains("DETAILED BREAKDOWN TOTALS"),compact);
        String detailed=capture(out->new ReceiptEntryApp(new Scanner(prefix+"DETAILS\nBACK\nBACK\n9\n"),out,repo).run());
        assertTrue(detailed.contains("DETAILED BREAKDOWN TOTALS"),detailed);
        assertTrue(detailed.contains("EARNED BY RECEIPT AND PROVIDER"),detailed);
        assertEquals(1,repo.history().findByReceiptId(r.transactionId()).size());
    }
}
