package coupons.repository;

import coupons.CouponApplied;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InventoryCostsTest {
    private static final UUID A=UUID.fromString("00000000-0000-0000-0000-000000000001"), B=UUID.fromString("00000000-0000-0000-0000-000000000002");
    private ReceiptSnapshot receipt(String a,String b,List<CouponApplied> discounts) {
        return new ReceiptSnapshot(List.of(new PurchaseLineSnap(A,"A","012345678901",new BigDecimal("10"),new BigDecimal(a),2),new PurchaseLineSnap(B,"B","012345678902",null,new BigDecimal(b),1)),List.of(),discounts,List.of(),List.of(),List.of(),List.of(),new ReceiptTotalsSnap(new BigDecimal("1.00"),new BigDecimal("99.00")));
    }
    private CouponApplied discount(String amount,List<UUID> ids,boolean whole) {
        return new CouponApplied(UUID.randomUUID(),"Discount",new BigDecimal(amount),ids,null,whole,Map.of());
    }
    @Test void usesSalePricesAndProductCouponsWhileExcludingReportedTaxAndTotal() {
        var costs=InventoryCosts.calculate(receipt("4.00","2.00",List.of(discount("2.00",List.of(A),false))));
        assertEquals(new BigDecimal("6.00"),costs.get(A));assertEquals(new BigDecimal("2.00"),costs.get(B));
    }
    @Test void allocatesSharedDiscountsAndConservesCents() {
        var costs=InventoryCosts.calculate(receipt("4.00","2.00",List.of(discount("1.01",List.of(),true))));
        assertEquals(new BigDecimal("7.19"),costs.get(A));assertEquals(new BigDecimal("1.80"),costs.get(B));
        assertEquals(new BigDecimal("8.99"),costs.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add));
    }
    @Test void respectsSelectedQuantities() {
        var discount=new CouponApplied(UUID.randomUUID(),"Selected products",new BigDecimal("1.00"),List.of(A,B),null,false,Map.of(A,1,B,1));
        var costs=InventoryCosts.calculate(receipt("4.00","2.00",List.of(discount)));
        assertEquals(new BigDecimal("7.33"),costs.get(A));assertEquals(new BigDecimal("1.67"),costs.get(B));
    }
    @Test void flagsUnresolvableAndNegativeCostsInsteadOfInventingValues() {
        assertTrue(InventoryCosts.calculate(receipt("0.00","0.00",List.of(discount("1.00",List.of(),true)))).isEmpty());
        assertTrue(InventoryCosts.calculate(receipt("1.00","1.00",List.of(discount("3.00",List.of(A),false)))).isEmpty());
    }
}
