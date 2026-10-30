package coupons.receipts;

import coupons.promotions.CouponApplied;
import coupons.promotions.QualifyingSpend;

import coupons.receipts.ReceiptObservations.*;
import coupons.repository.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class WholeReceiptDiscountTest {
    private static BigDecimal n(String value){return new BigDecimal(value);}
    private CouponTransaction transaction(DiscountKind kind,CouponApplied coupon) {
        var tx=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,n("40")));
        tx.addPurchaseItem(new PurchaseItem("First",null,n("30"),n("30"),1));
        tx.addPurchaseItem(new PurchaseItem("Second",null,n("20"),n("20"),1));
        tx.addAppliedCoupon(coupon);
        tx.setObservations(new ReceiptObservations(null,null,List.of(),List.of(),null,List.of(),Map.of(coupon.getApplicationId(),kind)));
        return tx;
    }
    @Test void wholeReceiptSaleReductionIsCountedOnceButCannotBeGuessedForASubset() {
        var coupon=new CouponApplied(UUID.randomUUID(),"Receipt sale",n("10"),List.of(),null,true);
        var tx=transaction(DiscountKind.SALE_PROMOTION,coupon);
        var ids=tx.getPurchaseItems().stream().map(PurchaseItem::getLineItemId).toList();
        assertEquals(n("40"),QualifyingSpend.calculate(tx,ids).amount());
        assertNull(QualifyingSpend.calculate(tx,List.of(ids.get(0))).amount());
        assertTrue(QualifyingSpend.calculate(tx,List.of(ids.get(0))).problem().contains("allocation"));
        assertEquals(n("40"),new TransactionCalculator().calculateAfterCouponTotal(tx));
    }
    @Test void storeOnlineCouponDoesNotChangeSaleBeforeCouponQualification() {
        var coupon=new CouponApplied(UUID.randomUUID(),"Online code",n("10"),List.of(),null,true);
        var tx=transaction(DiscountKind.STORE_COUPON,coupon);
        assertEquals(n("50"),QualifyingSpend.calculate(tx,tx.getPurchaseItems().stream().map(PurchaseItem::getLineItemId).toList()).amount());
        assertEquals(n("40"),new TransactionCalculator().calculateAfterCouponTotal(tx));
    }
    @Test void explicitScopeSurvivesPersistenceAndLegacyEmptyAssociationsStayUnknown(@TempDir Path directory)throws Exception {
        UUID receiptId=UUID.randomUUID(),applicationId=UUID.randomUUID();
        var whole=new CouponApplied(applicationId,"Online code",n("10"),List.of(),null,true);
        var unknown=new CouponApplied(applicationId,"Online code",n("10"),List.of());
        assertFalse(unknown.isWholeReceipt());assertNotEquals(unknown,whole);
        Path database=directory.resolve("receipts.bin");
        try(var repo=InMemoryTransactionRepository.openLocal(database)) {
            repo.save(receiptId,transaction(DiscountKind.STORE_COUPON,whole),null,List.of(),new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptCreateCtx("Owner"));
        }
        try(var repo=InMemoryTransactionRepository.openLocal(database)) {
            var saved=repo.findById(receiptId).orElseThrow().transaction().getAppliedCoupons().get(0);
            assertEquals(whole,saved);assertTrue(saved.isWholeReceipt());
        }
        assertThrows(IllegalArgumentException.class,()->new CouponApplied(applicationId,"Invalid",n("10"),List.of(UUID.randomUUID()),null,true));
    }
}
