package coupons;

import coupons.repository.*;
import coupons.ReceiptObservations.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PersistentBalancesTest {
    private final LocalDate day = LocalDate.of(2026,10,4);
    private ReceiptWriteRequest request() { return new ReceiptWriteRequest(UUID.randomUUID(), Instant.now()); }
    private Earning earning(EarningKind kind, String provider, BenefitKind form, String amount, String usd) {
        return new Earning(UUID.randomUUID(), kind, provider, null, Scope.RECEIPT, List.of(), null,
                new RebateBenefit(form, new BigDecimal(amount), form == BenefitKind.POINTS ? null : Currency.getInstance("USD"), provider),
                kind == EarningKind.REGISTER_REWARD ? RebateStatus.PAID : RebateStatus.CREDITED, null, null, null, List.of(), null, null,
                usd == null ? null : new RecordedValuation(new BigDecimal(usd), Currency.getInstance("USD"), "Recorded spreadsheet basis"));
    }
    private UUID save(InMemoryTransactionRepository repo, String wc, String rr, String usedWc, String usedRr) {
        var id = UUID.randomUUID(); var tx = new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO, BigDecimal.ZERO));
        if (new BigDecimal(usedWc).signum() > 0) tx.addPayment(new Payment(PaymentType.WALGREENS_CASH, new BigDecimal(usedWc)));
        var earnings = List.of(earning(EarningKind.WALGREENS_CASH,"Walgreens",BenefitKind.STORE_REWARD,wc,null),
                earning(EarningKind.REGISTER_REWARD,"Walgreens",BenefitKind.STORE_COUPON,rr,null),
                earning(EarningKind.THIRD_PARTY_REBATE,"Fetch",BenefitKind.POINTS,"25","0.025"));
        tx.setObservations(new ReceiptObservations(day,null,earnings,new BigDecimal(usedRr).signum() > 0
                ? List.of(new UnidentifiedRR(UUID.randomUUID(),new BigDecimal(usedRr),"Source")) : List.of(),null,List.of(),Map.of()));
        repo.save(id,tx,null,List.of(),request(),new ReceiptCreateCtx("AJ")); return id;
    }
    private List<ReceiptRecord> records(InMemoryTransactionRepository repo) {
        return repo.creationHistory().stream().map(e -> repo.findById(e.after().transactionId()).orElseThrow()).toList();
    }
    @Test void twoReceiptsCarryForwardAndCorrectionsReplaceActivityAcrossRestart(@TempDir Path dir) throws Exception {
        var file = dir.resolve("receipts.bin"); UUID first;
        try (var repo = InMemoryTransactionRepository.openLocal(file)) {
            var b = new ProviderBalanceSummary(repo);
            b.recordOpening(day,"WC",new BigDecimal("247.75"),true);
            b.recordOpening(day,"RR",new BigDecimal("2.00"),true);
            b.recordOpening(day,"Fetch",new BigDecimal("33660"),new BigDecimal("33.59"),true,"Spreadsheet recorded valuation","October opening");
            first = save(repo,"0.80","12.00","10.00","2.00"); save(repo,"18.50","19.00","0","7.00");
            var receipts = records(repo);
            assertEquals(new BigDecimal("257.05"), b.balance(day,"WC",receipts).endUsd());
            assertEquals(new BigDecimal("24.00"), b.balance(day,"RR",receipts).endUsd());
            assertEquals(new BigDecimal("33710"), b.balance(day,"Fetch",receipts).endPoints());
            assertEquals(new BigDecimal("33.640"), b.balance(day,"Fetch",receipts).endUsd());
            assertEquals(new BigDecimal("257.05"),b.balance(day.plusDays(1),"WC",receipts).startUsd());
            assertEquals(new BigDecimal("257.05"),b.balance(day.plusMonths(1),"WC",receipts).startUsd());
            // Duplicate read/review and duplicate revisions do not change activity.
            var duplicate = new ArrayList<>(receipts); duplicate.addAll(receipts);
            assertEquals(b.balance(day,"RR",receipts), b.balance(day,"RR",duplicate));
            var saved = repo.findById(first).orElseThrow(); var tx = saved.transaction();
            tx.addPayment(new Payment(PaymentType.WALGREENS_CASH,BigDecimal.ONE));
            var req = request(); var approval = new ReceiptChangeContext("AJ","Correct WC used","AJ",Instant.now());
            var result = repo.update(first,1,tx,null,List.of(),req,approval);
            assertEquals(result,repo.update(first,1,tx,null,List.of(),req,approval));
            assertEquals(new BigDecimal("256.05"),b.balance(day,"WC",records(repo)).endUsd());
            assertTrue(b.movements(records(repo)).stream().allMatch(m -> m.source().contains("revision")));
        }
        try (var repo = InMemoryTransactionRepository.openLocal(file)) {
            var b = new ProviderBalanceSummary(repo);
            assertEquals(3,b.openingHistory().size());
            assertEquals(new BigDecimal("256.05"),b.balance(day.plusDays(1),"WC",records(repo)).startUsd());
            assertEquals(2,repo.history().findByReceiptId(first).size());
        }
    }
    @Test void explicitDayOpeningResetsCarryWithoutAddingAndUnknownPointBaselineStaysUnknown() {
        var repo = new InMemoryTransactionRepository();var b = new ProviderBalanceSummary(repo);
        b.recordOpening(day,"WC",new BigDecimal("240.00"),true);
        b.recordOpening(day,"WC",new BigDecimal("247.75"),false);
        b.recordOpening(day,"Fetch",new BigDecimal("33.59"),false);
        save(repo,"0.80","12.00","10.00","2.00");
        assertEquals(new BigDecimal("238.55"),b.balance(day,"WC",records(repo)).endUsd());
        assertEquals(new BigDecimal("240.00"),b.balance(day,"WC",records(repo)).monthUsd());
        assertNull(b.balance(day,"Fetch",records(repo)).endPoints());
        b.recordOpening(day.plusDays(1),"WC",new BigDecimal("300.00"),false);
        assertEquals(new BigDecimal("300.00"),b.balance(day.plusDays(2),"WC",records(repo)).startUsd());
        assertEquals(4,b.openingHistory().size());
        assertEquals(new BigDecimal("61.45"),b.openingDifference(day.plusDays(1),"WC",records(repo)));
    }
    @Test void adjustmentsPersistAndFailedOpeningWriteDoesNotPublish(@TempDir Path dir) throws Exception {
        var file = dir.resolve("receipts.bin");
        try(var repo=InMemoryTransactionRepository.openLocal(file)) {
            var b=new ProviderBalanceSummary(repo);b.recordOpening(day,"WC",new BigDecimal("10.00"),true);
            b.recordAdjustment(day,"WC",null,new BigDecimal("-2.00"),"External expiration","AJ");
            assertEquals(new BigDecimal("8.00"),b.balance(day,"WC",List.of()).endUsd());
            var backup=dir.resolve("backup.bin");Files.move(file,backup);Files.createDirectory(file);
            assertThrows(IllegalStateException.class,()->b.recordOpening(day,"WC",new BigDecimal("99"),false));
            assertEquals(1,b.openingHistory().size());
            Files.delete(file);Files.move(backup,file);
        }
        try(var repo=InMemoryTransactionRepository.openLocal(file)) {
            var b=new ProviderBalanceSummary(repo);
            assertEquals(new BigDecimal("8.00"),b.balance(day.plusDays(1),"WC",List.of()).startUsd());
            assertTrue(b.movements(List.of()).get(0).source().contains("External expiration"));
        }
    }
    @Test void targetGiftCardAndCircleHaveSeparateOpeningsAndReceiptUses() {
        var repo=new InMemoryTransactionRepository();var b=new ProviderBalanceSummary(repo);
        b.recordOpening(day,"Target Gift Card",new BigDecimal("15.00"),false);
        b.recordOpening(day,"Target Circle",new BigDecimal("20.00"),false);
        var tx=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,new BigDecimal("8.00")));
        tx.addPayment(new Payment(UUID.randomUUID(),PaymentType.GIFT_CARD,new BigDecimal("5.00"),null,"Target Gift Card"));
        tx.addPayment(new Payment(UUID.randomUUID(),PaymentType.OTHER,new BigDecimal("3.00"),"Circle redemption","Target Circle"));
        tx.setObservations(new ReceiptObservations(day,null,List.of(),List.of(),null,List.of(),Map.of()));
        var id=UUID.randomUUID();repo.save(id,tx,null,List.of(),request(),new ReceiptCreateCtx("AJ"));
        var restored=repo.findById(id).orElseThrow();
        assertEquals("Target Gift Card",restored.transaction().getPayments().get(0).getAccountName());
        assertEquals(new BigDecimal("10.00"),b.balance(day,"Target Gift Card",List.of(restored)).endUsd());
        assertEquals(new BigDecimal("17.00"),b.balance(day,"Target Circle",List.of(restored)).endUsd());
        assertNull(b.balance(day,"Target",List.of(restored)).endUsd());
        assertEquals(2,b.movements(List.of(restored)).size());
    }
}
