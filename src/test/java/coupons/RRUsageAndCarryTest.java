package coupons;

import coupons.ReceiptObservations.*;
import coupons.repository.*;
import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RRUsageAndCarryTest {
    private ReceiptWriteRequest request() { return new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()); }
    private Earning rr(String amount) {
        return new Earning(UUID.randomUUID(),EarningKind.REGISTER_REWARD,"Walgreens","Printed RR",Scope.RECEIPT,List.of(),null,
                new RebateBenefit(BenefitKind.STORE_COUPON,new BigDecimal(amount),Currency.getInstance("USD"),"Walgreens"),
                RebateStatus.PAID,null,null,null,List.of());
    }
    private Earning wc(String amount) {
        return new Earning(UUID.randomUUID(),EarningKind.WALGREENS_CASH,"Walgreens",null,Scope.RECEIPT,List.of(),null,
                new RebateBenefit(BenefitKind.STORE_REWARD,new BigDecimal(amount),Currency.getInstance("USD"),"Walgreens"),
                RebateStatus.CREDITED,null,null,null,List.of());
    }
    private ReceiptRecord receipt(InMemoryTransactionRepository repo,String number,List<Earning> earnings,String wcUsed,String rrUsed) {
        var tx=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,BigDecimal.ZERO));
        if(new BigDecimal(wcUsed).signum()>0) tx.addPayment(new Payment(PaymentType.WALGREENS_CASH,new BigDecimal(wcUsed)));
        tx.setObservations(new ReceiptObservations(LocalDate.of(2026,10,4),number.equals("R1")?"940am":"953am",earnings,
                List.of(new UnidentifiedRR(UUID.randomUUID(),new BigDecimal(rrUsed),number.equals("R1")?"Palmolive":"Neutrogena")),null,List.of(),Map.of()));
        var id=UUID.randomUUID();repo.save(id,tx,new ReceiptMeta("Walgreens",number,null,null),List.of(),request(),new ReceiptCreateCtx("AJ"));
        return repo.findById(id).orElseThrow();
    }
    private List<ReceiptRecord> records(InMemoryTransactionRepository repo) {
        return repo.creationHistory().stream().map(h->repo.findById(h.after().transactionId()).orElseThrow()).toList();
    }
    private String run(InMemoryTransactionRepository repo,String script) {
        var bytes=new ByteArrayOutputStream();new ReceiptEntryApp(new Scanner(script),new PrintStream(bytes),repo).run();return bytes.toString();
    }
    @Test void matchingRemovesUsedCouponFromActiveListAndPreservesTotalsBalancesAndHistoryAcrossRestart(@TempDir Path directory) throws Exception {
        var file=directory.resolve("receipts.bin");UUID firstId;RRUsageLink expected;
        try(var repo=InMemoryTransactionRepository.openLocal(file)) {
            var earned=rr("7.00");var first=receipt(repo,"R1",List.of(earned,rr("5.00"),wc("0.80")),"10.00","2.00");firstId=first.transactionId();
            var second=receipt(repo,"R2",List.of(rr("5.00"),rr("4.00"),rr("10.00"),wc("18.50")),"0","7.00");
            var b=new ProviderBalanceSummary(repo);b.recordOpening(LocalDate.of(2026,10,4),"RR",new BigDecimal("2.00"),true);
            b.recordOpening(LocalDate.of(2026,10,4),"WC",new BigDecimal("247.75"),false);
            var before=b.balance(LocalDate.of(2026,10,4),"RR",records(repo));
            String output=run(repo,"5\n4\nR2\n1\ny\n1\n5\n9\n");
            assertTrue(output.contains("RR marked USED"),output);
            assertTrue(output.matches("(?s).*2026-10-04 +\\| +1 \\| [^\n]*R2.*"),output);
            assertTrue(output.contains("5. Back to main menu"),output);
            assertTrue(output.matches("(?s).*USED REGISTER REWARDS\\RDate used +\\| +# \\| Source receipt / coupon.*"),output);
            assertFalse(output.contains("3. Return"),output);
            assertTrue(output.contains("Total RR earned from saved receipts: $31.00"),output);
            assertTrue(output.matches("(?s).*2026-10 +\\| +\\$2.00 +\\| +\\$31.00 +\\| +\\$9.00 +\\| +\\$24.00.*"),output);
            assertTrue(output.contains("Available balance as of 2026-10-04: $24.00"),output);
            assertTrue(output.matches("(?s).*2026-10-04 +R1 +\\$2.00.*2026-10-04 +R2 +\\$7.00.*"),output);
            assertTrue(output.contains("Total RR used on saved receipts: $9.00"),output);
            assertTrue(output.contains("Receipt RR not linked as used (unreconciled): $24.00"),output);
            var active=output.substring(output.indexOf("ACTIVE PRINTED RR"),output.indexOf("USED REGISTER REWARDS"));
            assertFalse(active.contains("7.00 RR"),active);
            assertTrue(output.matches("(?s).*\\$7.00 +\\| [^\n]*\\| R2.*"),output);
            assertEquals(before,b.balance(LocalDate.of(2026,10,4),"RR",records(repo)));
            assertEquals(new BigDecimal("24.00"),before.endUsd());
            assertEquals(1,repo.history().findByReceiptId(firstId).size());
            expected=repo.rrUsageLinks().get(0);assertEquals(expected,repo.linkRRUsage(expected));
            // Settings and receipt updates must preserve usage links.
            b.recordOpening(LocalDate.of(2026,10,4),"Fetch",new BigDecimal("33.59"),true);
            repo.update(second.transactionId(),1,second.transaction(),second.metadata(),List.of(),request(),
                    new ReceiptChangeContext("AJ","Recheck","AJ",Instant.now()));
            assertFalse(repo.rrUsageNeedsReview(expected));
        }
        try(var repo=InMemoryTransactionRepository.openLocal(file)) {
            assertEquals(List.of(expected),repo.rrUsageLinks());
            assertEquals(1,repo.history().findByReceiptId(firstId).size());
            assertTrue(run(repo,"5\n1\n5\n9\n").matches("(?s).*\\$7.00 +\\| [^\n]*\\| R2.*"));
        }
    }
    @Test void simplifiedMatchingRequiresAnExplicitCouponAndConfirmationAndBackRetainsReceipt() {
        var repo=new InMemoryTransactionRepository();var first=rr("7");var second=rr("7");
        receipt(repo,"R1",List.of(first,second),"0","2");
        var spent=receipt(repo,"R2",List.of(),"0","7");
        var before=ReceiptSnapshot.from(spent.transaction());
        for(String answer:List.of("0", "3", "1\nn")) {
            String output=run(repo,"5\n4\nR2\n"+answer+"\n5\n9\n");
            assertTrue(repo.rrUsageLinks().isEmpty(),output);
            assertFalse(output.contains("Deduction choice"),output);
            assertFalse(output.contains("Source confirmation / reason:"),output);
            assertFalse(output.contains("Confirmed by:"),output);
            assertTrue(output.contains("None of these"),output);
        }
        String output=run(repo,"5\n4\nR2\n1\nBACK\n2\ny\n5\n9\n");
        assertTrue(output.contains("RR marked USED"),output);
        assertEquals(second.earningId(),repo.rrUsageLinks().get(0).sourceId());
        assertEquals("Local owner",repo.rrUsageLinks().get(0).confirmedBy());
        assertEquals(before,ReceiptSnapshot.from(repo.findById(spent.transactionId()).orElseThrow().transaction()));
        String again=run(repo,"5\n4\nR2\n5\n9\n");
        assertTrue(again.contains("No unmatched RR deductions"),again);
        assertEquals(1,repo.rrUsageLinks().size());
    }
    @Test void matchingHidesMatchedDeductionsButStillAsksWhenSeveralRemain() {
        var repo=new InMemoryTransactionRepository();var seven=rr("7");var five=rr("5");
        receipt(repo,"R1",List.of(seven,five),"0","2");
        var spent=receipt(repo,"R2",List.of(),"0","7");var tx=spent.transaction();var o=tx.getObservations();
        var uses=new ArrayList<>(o.unidentifiedRR());uses.add(new UnidentifiedRR(UUID.randomUUID(),new BigDecimal("5"),"Second RR"));
        tx.setObservations(new ReceiptObservations(o.purchaseDate(),o.printedTime(),o.earnings(),uses,o.walgreensCash(),o.savings(),o.discountKinds()));
        repo.update(spent.transactionId(),1,tx,spent.metadata(),List.of(),request(),new ReceiptChangeContext("Local owner","Second deduction","Local owner",Instant.now()));
        String first=run(repo,"5\n4\nR2\n1\n1\ny\n5\n9\n");
        assertTrue(first.contains("Deduction choice"),first);
        String remaining=run(repo,"5\n4\nR2\n1\ny\n5\n9\n");
        assertFalse(remaining.contains("Deduction choice"),remaining);
        assertTrue(remaining.contains("RR used: $5.00 — Second RR"),remaining);
        assertEquals(2,repo.rrUsageLinks().size());
        assertEquals(0,new TransactionCalculator().calculateRegisterRewardsRedeemed(repo.findById(spent.transactionId()).orElseThrow().transaction()).compareTo(new BigDecimal("12")));
    }
    @Test void monthlyTableSeparatesMonthsAndCarriesOpeningWithoutCountingCouponMatchesTwice() {
        var repo=new InMemoryTransactionRepository();
        receipt(repo,"R1",List.of(rr("12.00")),"0","2.00");
        var second=receipt(repo,"R2",List.of(rr("19.00")),"0","7.00");
        var tx=second.transaction(); var o=tx.getObservations();
        tx.setObservations(new ReceiptObservations(LocalDate.of(2026,11,4),o.printedTime(),o.earnings(),o.unidentifiedRR(),o.walgreensCash(),o.savings(),o.discountKinds()));
        repo.update(second.transactionId(),1,tx,second.metadata(),List.of(),request(),new ReceiptChangeContext("AJ","Correct date","AJ",Instant.now()));
        new ProviderBalanceSummary(repo).recordOpening(LocalDate.of(2026,10,1),"RR",new BigDecimal("2.00"),true);
        var output=run(repo,"5\n1\n5\n9\n");
        assertTrue(output.matches("(?s).*2026-10 +\\| +\\$2.00 +\\| +\\$12.00 +\\| +\\$2.00 +\\| +\\$12.00.*"),output);
        assertTrue(output.matches("(?s).*2026-11 +\\| +\\$12.00 +\\| +\\$19.00 +\\| +\\$7.00 +\\| +\\$24.00.*"),output);
        assertTrue(output.contains("Available balance as of 2026-11-04: $24.00"),output);
    }
    @Test void mismatchedAmountAndDuplicateDeductionCannotConsumeAnotherCoupon() {
        var repo=new InMemoryTransactionRepository();var source=rr("7");var other=rr("7");
        receipt(repo,"R1",List.of(source,other),"0","2");var spent=receipt(repo,"R2",List.of(rr("5")),"0","7");
        var event=spent.transaction().getObservations().unidentifiedRR().get(0).eventId();
        var good=new RRUsageLink(source.earningId(),true,event,spent.transactionId(),new BigDecimal("7"),"Confirmed","AJ",Instant.now());
        assertThrows(IllegalArgumentException.class,()->repo.linkRRUsage(new RRUsageLink(source.earningId(),true,event,spent.transactionId(),new BigDecimal("5"),"Bad amount","AJ",Instant.now())));
        repo.linkRRUsage(good);
        assertThrows(IllegalArgumentException.class,()->repo.linkRRUsage(new RRUsageLink(other.earningId(),true,event,spent.transactionId(),new BigDecimal("7"),"Duplicate","AJ",Instant.now())));
        assertEquals(1,repo.rrUsageLinks().size());
    }
    @Test void failurePublishesNoAssociationAndCanRetrySameConfirmation(@TempDir Path directory) throws Exception {
        var file=directory.resolve("receipts.bin");
        try(var repo=InMemoryTransactionRepository.openLocal(file)) {
            var source=rr("7");receipt(repo,"R1",List.of(source),"0","2");var spent=receipt(repo,"R2",List.of(rr("5")),"0","7");
            var event=spent.transaction().getObservations().unidentifiedRR().get(0).eventId();
            var link=new RRUsageLink(source.earningId(),true,event,spent.transactionId(),new BigDecimal("7"),"Confirmed","AJ",Instant.now());
            var backup=directory.resolve("backup.bin");Files.move(file,backup);Files.createDirectory(file);
            assertThrows(IllegalStateException.class,()->repo.linkRRUsage(link));assertTrue(repo.rrUsageLinks().isEmpty());
            Files.delete(file);Files.move(backup,file);assertEquals(link,repo.linkRRUsage(link));assertEquals(link,repo.linkRRUsage(link));
            assertEquals(1,repo.rrUsageLinks().size());
        }
    }
    @Test void sourceCorrectionLeavesUsedCouponReservedAndMarksAssociationForReview() {
        var repo=new InMemoryTransactionRepository();var source=rr("7");var origin=receipt(repo,"R1",List.of(source),"0","2");
        var spent=receipt(repo,"R2",List.of(rr("5")),"0","7");var event=spent.transaction().getObservations().unidentifiedRR().get(0).eventId();
        var link=new RRUsageLink(source.earningId(),true,event,spent.transactionId(),new BigDecimal("7"),"Confirmed","AJ",Instant.now());repo.linkRRUsage(link);
        var tx=origin.transaction();var obs=tx.getObservations();
        var changed=new Earning(source.earningId(),source.kind(),source.provider(),source.offerReference(),source.scope(),source.lineItemIds(),null,
                new RebateBenefit(BenefitKind.STORE_COUPON,new BigDecimal("8"),Currency.getInstance("USD"),"Walgreens"),source.status(),null,null,null,List.of());
        tx.setObservations(new ReceiptObservations(obs.purchaseDate(),obs.printedTime(),List.of(changed),obs.unidentifiedRR(),null,List.of(),Map.of()));
        repo.update(origin.transactionId(),1,tx,origin.metadata(),List.of(),request(),new ReceiptChangeContext("AJ","Correct RR value","AJ",Instant.now()));
        assertTrue(repo.rrUsageNeedsReview(link));
        var output=run(repo,"5\n1\n5\n9\n");assertTrue(output.contains("Needs review"),output);
        var active=output.substring(output.indexOf("ACTIVE PRINTED RR"),output.indexOf("USED REGISTER REWARDS"));assertFalse(active.contains("8.00 RR"));
    }
    @Test void correctionFeedbackShowsDayCloseAndNextOpeningWithoutAddingMovements() {
        var repo=new InMemoryTransactionRepository();var b=new ProviderBalanceSummary(repo);
        b.recordOpening(LocalDate.of(2026,10,4),"WC",new BigDecimal("247.75"),false);
        b.recordOpening(LocalDate.of(2026,10,4),"RR",new BigDecimal("2.00"),true);
        receipt(repo,"R1",List.of(rr("12.00"),wc("0.80")),"10.00","2.00");
        var second=receipt(repo,"R2",List.of(rr("19.00"),wc("18.50")),"0","7.00");
        var before=b.movements(records(repo));String output=run(repo,"UPDATE\nR2\nTOTALS\nALL\n1.73\n26.69\nREASON\nPrinted total correction\nSAVE\n9\n");
        String normalized=output.replaceAll(" +"," ");
        assertTrue(normalized.contains("ACCOUNT BALANCES AFTER SAVE"),output);
        assertTrue(normalized.contains("Walgreens Cash $247.75 $257.05 $257.05"),output);
        assertTrue(normalized.contains("Register Rewards $2.00 $24.00 $24.00"),output);
        assertEquals(before.size(),b.movements(records(repo)).size());
        assertEquals(2,repo.history().findByReceiptId(second.transactionId()).size());
    }
    @Test void matchedOpeningCouponCannotBeRedeemedAgainAndRemovedEarningStillHasUsedHistory() {
        var repo=new InMemoryTransactionRepository();var physical=new RegisterReward("Opening Palmolive",new BigDecimal("2"),null,null,null);
        repo.issuedRewards().save(physical);
        var source=rr("7");var origin=receipt(repo,"R1",List.of(source),"0","2");
        var firstUse=origin.transaction().getObservations().unidentifiedRR().get(0);
        var openingLink=new RRUsageLink(physical.getRewardId(),false,firstUse.eventId(),origin.transactionId(),new BigDecimal("2"),"Opening coupon used","AJ",Instant.now());
        repo.linkRRUsage(openingLink);
        var duplicate=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,BigDecimal.ZERO));var duplicateId=UUID.randomUUID();
        duplicate.addRegisterRewardRedemption(new RegisterRewardRedemption(physical.getRewardId(),duplicateId,new BigDecimal("2"),null));
        assertThrows(IllegalArgumentException.class,()->repo.save(duplicateId,duplicate,null,List.of(),request(),new ReceiptCreateCtx("AJ")));
        assertThrows(IllegalArgumentException.class,()->repo.voidRecordedReward(physical.getRewardId(),"Mistake","AJ"));
        var spent=receipt(repo,"R2",List.of(rr("5")),"0","7");var secondUse=spent.transaction().getObservations().unidentifiedRR().get(0);
        repo.linkRRUsage(new RRUsageLink(source.earningId(),true,secondUse.eventId(),spent.transactionId(),new BigDecimal("7"),"Neutrogena used","AJ",Instant.now()));
        var tx=origin.transaction();var obs=tx.getObservations();tx.setObservations(new ReceiptObservations(obs.purchaseDate(),null,List.of(),obs.unidentifiedRR(),null,List.of(),Map.of()));
        repo.update(origin.transactionId(),1,tx,origin.metadata(),List.of(),request(),new ReceiptChangeContext("AJ","Correct earning list","AJ",Instant.now()));
        var output=run(repo,"5\n1\n5\n9\n");
        assertTrue(output.contains("Needs review"),output);
        assertTrue(output.matches("(?s).*\\$7.00 +\\| [^\n]*\\| R2.*"),output);
        assertTrue(output.matches("(?s).*\\$2.00 +\\| [^\n]*\\| R1.*"),output);
        assertEquals(2,repo.rrUsageLinks().size());
    }
}
