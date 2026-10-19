package coupons;

import coupons.ReceiptObservations.*;
import coupons.repository.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SpreadsheetBalancePersistenceTest {
    private static final LocalDate DAY=LocalDate.of(2026,10,4);
    private BigDecimal n(String x) { return new BigDecimal(x); }
    private ReceiptRecord receipt(InMemoryTransactionRepository repo,String number,boolean pg) {
        var tx=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,BigDecimal.ZERO));
        var earnings=new ArrayList<Earning>();
        earnings.add(earning("Fetch","25")); if(pg) earnings.add(earning("P&G Brand","250"));
        tx.setObservations(new ReceiptObservations(DAY,null,earnings,List.of(),null,List.of(),Map.of()));
        var id=UUID.randomUUID(); repo.save(id,tx,new ReceiptMeta("Walgreens",number,null,null),List.of(),
                new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptCreateCtx("AJ"));
        return repo.findById(id).orElseThrow();
    }
    private Earning earning(String provider,String amount) {
        return new Earning(UUID.randomUUID(),EarningKind.THIRD_PARTY_REBATE,provider,null,Scope.RECEIPT,List.of(),null,
                new RebateBenefit(BenefitKind.POINTS,n(amount),null,provider),RebateStatus.CREDITED,null,null,null,List.of());
    }
    private List<ReceiptRecord> records(InMemoryTransactionRepository repo) {
        return repo.creationHistory().stream().map(h->repo.findById(h.after().transactionId()).orElseThrow()).toList();
    }
    private List<SpreadsheetBalanceImport.Row> rows() {
        return List.of(new SpreadsheetBalanceImport.Row("OPENING",DAY.withDayOfMonth(1),"Fetch",n("33660"),n("33.59"),""),
                new SpreadsheetBalanceImport.Row("OPENING",DAY.withDayOfMonth(1),"P&G Brand",n("1000"),n("6.67"),""),
                new SpreadsheetBalanceImport.Row("EARNING",DAY,"Fetch",n("25"),n("0.025"),"R1"),
                new SpreadsheetBalanceImport.Row("EARNING",DAY,"Fetch",n("25"),n("0.025"),"R2"),
                new SpreadsheetBalanceImport.Row("EARNING",DAY,"P&G Brand",n("250"),n("1.67"),"R2"));
    }
    @Test void suppliedValuesPersistWithoutDuplicateActivityAndNextMonthCarriesAutomatically(@TempDir Path dir) throws Exception {
        var file=dir.resolve("receipts.bin"); UUID id; UUID event;
        try(var repo=InMemoryTransactionRepository.openLocal(file)) {
            var saved=receipt(repo,"R1",false); id=saved.transactionId(); event=saved.transaction().getObservations().earnings().get(0).earningId();
            receipt(repo,"R2",true);
            SpreadsheetBalanceImport.apply(repo,SpreadsheetBalanceImport.plan(repo,rows(),"User spreadsheet"),"Approved spreadsheet");
            assertTrue(SpreadsheetBalanceImport.plan(repo,rows(),"User spreadsheet").empty());
            assertEquals(2,repo.history().findByReceiptId(id).size());
            assertEquals(event,repo.findById(id).orElseThrow().transaction().getObservations().earnings().get(0).earningId());
            assertNull(repo.history().findByRevision(id,1).orElseThrow().after().contents().observations().earnings().get(0).valuation());
            var balances=new ProviderBalanceSummary(repo);
            var fetch=balances.balance(DAY,"Fetch",records(repo));
            assertEquals(n("33.640"),fetch.endUsd()); assertEquals(n("33710"),fetch.endPoints());
            assertEquals(n("8.34"),balances.balance(DAY,"P&G Brand",records(repo)).endUsd());
            assertEquals(n("1250"),balances.balance(DAY,"P&G Brand",records(repo)).endPoints());
            assertEquals(n("33.640"),balances.balance(LocalDate.of(2026,11,1),"Fetch",records(repo)).monthUsd());
        }
        try(var repo=InMemoryTransactionRepository.openLocal(file)) {
            assertTrue(SpreadsheetBalanceImport.plan(repo,rows(),"User spreadsheet").empty());
            var b=new ProviderBalanceSummary(repo);
            assertEquals(n("33.640"),b.balance(LocalDate.of(2026,11,2),"Fetch",records(repo)).startUsd());
            b.recordOpening(LocalDate.of(2026,11,1),"Fetch",n("34000"),n("34.00"),true,"Statement","November statement");
            b.recordOpening(LocalDate.of(2026,11,1),"Fetch",n("34000"),n("34.00"),true,"Statement","November statement");
            b.recordOpening(LocalDate.of(2026,11,1),"Fetch",n("34.00"),true);
            assertEquals(3,repo.accountBalances().openings().size());
            assertEquals(n("34.00"),b.balance(LocalDate.of(2026,11,2),"Fetch",records(repo)).monthUsd());
            assertEquals(n("34000"),b.balance(LocalDate.of(2026,11,2),"Fetch",records(repo)).endPoints());
        }
    }
    @Test void samePointQuantityCanReuseItsSavedValueWithoutReenteringIt() {
        var repo=new InMemoryTransactionRepository(); receipt(repo,"R1",false); receipt(repo,"R2",true);
        SpreadsheetBalanceImport.apply(repo,SpreadsheetBalanceImport.plan(repo,rows(),"User spreadsheet"),"Approved spreadsheet");
        var bytes=new ByteArrayOutputStream();
        new ReceiptEntryApp(new Scanner("UPDATE\nR1\nEARNINGS\nALL\n1\nn\nFetch\nReceipt scan\n3\n0\n25\n4\n\n\n\n\nSAVE\n9\n"),new PrintStream(bytes),repo).run();
        var current=repo.findPossibleDuplicates(new ReceiptMeta("Walgreens","R1",null,null)).get(0);
        assertEquals(3,current.revision(),bytes.toString());
        assertEquals(n("0.025"),current.transaction().getObservations().earnings().get(0).valuation().amount());
        assertTrue(bytes.toString().contains("Enter keeps saved value"),bytes.toString());
    }
    @Test void explicitRegularPriceImportPreservesEnteredPricesAndRoundsFormulasOnlyAtDisplay(@TempDir Path dir) throws Exception {
        var repo=new InMemoryTransactionRepository(); var saved=receipt(repo,"R1",false); var tx=saved.transaction();
        tx.addPurchaseItem(new PurchaseItem("Neutrogena",null,null,n("7.79"),2));
        tx.addPurchaseItem(new PurchaseItem("Lysol",null,null,n("3.29"),2));
        repo.update(saved.transactionId(),1,tx,saved.metadata(),List.of(),new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptChangeContext("AJ","Products","AJ",Instant.now()));
        var source=dir.resolve("regular.tsv");
        Files.writeString(source,"REGULAR_PRICE\t2026-10-04\t"+tx.getPurchaseItems().get(0).getLineItemId()+"\t\t7.79\tR1\n"
                +"REGULAR_PRICE\t2026-10-04\t"+tx.getPurchaseItems().get(1).getLineItemId()+"\t\t3.29\tR1\n"
                +"EARNING\t2026-10-04\tFetch\t25\t25/1000\tR1\n");
        var rows=SpreadsheetBalanceImport.read(source);
        SpreadsheetBalanceImport.apply(repo,SpreadsheetBalanceImport.plan(repo,rows,"User spreadsheet"),"Approved regular prices");
        var current=repo.findById(saved.transactionId()).orElseThrow();
        assertEquals(n("22.16"),new TransactionCalculator().calculateRegularMerchandiseTotal(current.transaction()));
        assertEquals(n("7.79"),current.transaction().getPurchaseItems().get(0).getSaleUnitPrice());
        assertEquals(tx.getPurchaseItems().get(0).getLineItemId(),current.transaction().getPurchaseItems().get(0).getLineItemId());
        assertNull(repo.history().findByRevision(saved.transactionId(),2).orElseThrow().after().contents().purchaseItems().get(0).regularUnitPrice());
        assertEquals(n("0.025"),current.transaction().getObservations().earnings().get(0).valuation().amount());
        assertTrue(SpreadsheetBalanceImport.plan(repo,rows,"User spreadsheet").empty());
    }
    @Test void unknownOrAmbiguousSpreadsheetReceiptCannotBeApplied() {
        var repo=new InMemoryTransactionRepository(); receipt(repo,"R1",false);
        assertThrows(IllegalArgumentException.class,()->SpreadsheetBalanceImport.plan(repo,rows(),"User spreadsheet"));
        assertTrue(repo.accountBalances().openings().isEmpty());
        assertEquals(1,repo.creationHistory().size());
    }
}
