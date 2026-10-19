package coupons;

import coupons.ReceiptObservations.*;
import coupons.repository.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ReceiptIssuesAndOnlineOrdersTest {
    private static BigDecimal n(String value){return new BigDecimal(value);}
    private static ReceiptMeta metadata(ReceiptMeta.PurchaseChannel channel){return new ReceiptMeta("Walgreens","Online-test",null,null,channel);}
    private CouponTransaction transaction(boolean earn,boolean use) {
        var tx=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,n("5")));
        tx.addPurchaseItem(new PurchaseItem("Soap","000123",n("5"),n("5"),1));
        var rewards=earn?List.of(new Earning(UUID.randomUUID(),EarningKind.REGISTER_REWARD,"Walgreens","Buy soap",Scope.RECEIPT,List.of(),null,new RebateBenefit(BenefitKind.STORE_COUPON,n("5"),Currency.getInstance("USD"),"Walgreens"),RebateStatus.PAID,null,null,null,List.of(),null,null,null)):List.<Earning>of();
        tx.setObservations(new ReceiptObservations(LocalDate.of(2026,10,5),"12:48 PM",rewards,use?List.of(new UnidentifiedRR(UUID.randomUUID(),n("2"),"Existing RR")):List.of(),null,List.of(),Map.of()));
        return tx;
    }
    private String run(InMemoryTransactionRepository repo,String input) {
        var out=new ByteArrayOutputStream();new ReceiptEntryApp(new Scanner(input),new PrintStream(out),repo).run();return out.toString();
    }
    @Test void onlineRuleBlocksEarningAndUseAtomicallyButKeepsInStoreSupported() {
        for(boolean earn:List.of(true,false)) {
            var repo=new InMemoryTransactionRepository();var tx=transaction(earn,!earn);UUID id=UUID.randomUUID();
            assertThrows(IllegalArgumentException.class,()->repo.save(id,tx,metadata(ReceiptMeta.PurchaseChannel.ONLINE),List.of(),new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptCreateCtx("Owner")));
            assertTrue(repo.creationHistory().isEmpty());assertTrue(repo.findById(id).isEmpty());
            repo.save(id,tx,metadata(ReceiptMeta.PurchaseChannel.IN_STORE),List.of(),new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptCreateCtx("Owner"));
            assertEquals(1,repo.creationHistory().size());
        }
    }
    @Test void onlineChannelAndRuleSurviveRestartAndFailedUpdateDoesNotChangeHistory(@TempDir Path folder)throws Exception {
        Path file=folder.resolve("receipts.bin");UUID id=UUID.randomUUID();
        try(var repo=InMemoryTransactionRepository.openLocal(file)) {
            repo.save(id,transaction(false,false),metadata(ReceiptMeta.PurchaseChannel.ONLINE),List.of(),new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptCreateCtx("Owner"));
        }
        try(var repo=InMemoryTransactionRepository.openLocal(file)) {
            var original=repo.findById(id).orElseThrow();assertEquals(ReceiptMeta.PurchaseChannel.ONLINE,original.metadata().purchaseChannel());
            assertThrows(IllegalArgumentException.class,()->repo.update(id,1,transaction(true,false),original.metadata(),List.of(),new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptChangeContext("Owner","Wrong RR","Owner",Instant.now())));
            assertEquals(1,repo.findById(id).orElseThrow().revision());assertEquals(1,repo.history().findByReceiptId(id).size());
        }
    }
    @Test void fixedDiscountIssuesDisappearRatherThanRepeatingRecoveryWarnings() {
        var tx=transaction(false,false);var id=UUID.randomUUID();
        var unknown=new CouponApplied(id,"Online discount",n("1"),List.of());tx.addAppliedCoupon(unknown);
        // Use matching checkout totals so this test isolates classification and scope.
        var original=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,n("4")));tx.getPurchaseItems().forEach(original::addPurchaseItem);original.addAppliedCoupon(unknown);
        var record=new ReceiptRecord(UUID.randomUUID(),ReceiptStatus.ACTIVE,1,original,metadata(ReceiptMeta.PurchaseChannel.ONLINE));
        assertEquals(2,ReceiptIssues.list(record).size());
        var corrected=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,n("4")));tx.getPurchaseItems().forEach(corrected::addPurchaseItem);
        corrected.addAppliedCoupon(new CouponApplied(id,"Online discount",n("1"),List.of(),null,true));
        corrected.setObservations(new ReceiptObservations(null,null,List.of(),List.of(),null,List.of(),Map.of(id,DiscountKind.STORE_COUPON)));
        assertTrue(ReceiptIssues.list(new ReceiptRecord(record.transactionId(),ReceiptStatus.ACTIVE,2,corrected,record.metadata())).isEmpty());
    }
    @Test void existingReceiptCanSetOnlineOrderTypeAndIssuesAreVisibleFromRetrieval() {
        var repo=new InMemoryTransactionRepository();UUID id=UUID.randomUUID();
        repo.save(id,transaction(false,false),metadata(ReceiptMeta.PurchaseChannel.UNKNOWN),List.of(),new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptCreateCtx("Owner"));
        String output=run(repo,"UPDATE\nOnline-test\nDETAILS\nORDER TYPE\n2\nDONE\nSAVE\n2\nOnline-test\nISSUES\nBACK\n11\n");
        assertTrue(output.contains("Online order: Register Rewards cannot be earned or used"),output);
        assertTrue(output.contains("RECEIPT ISSUES"),output);assertTrue(output.contains("No current issues detected"),output);
        assertTrue(output.contains("Receipt details (store, date, order type)"),output);
        assertEquals(ReceiptMeta.PurchaseChannel.ONLINE,repo.findById(id).orElseThrow().metadata().purchaseChannel());
    }
    @Test void oldFourArgumentMetadataDoesNotInventOrderType() {
        assertEquals(ReceiptMeta.PurchaseChannel.UNKNOWN,new ReceiptMeta("Walgreens","Old",null,null).purchaseChannel());
    }
}
