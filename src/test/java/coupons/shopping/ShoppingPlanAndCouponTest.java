package coupons.shopping;

import coupons.promotions.ClippedCoupon;
import coupons.promotions.Coupon;
import coupons.promotions.CouponStatus;
import coupons.promotions.CouponType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShoppingPlanAndCouponTest {
    @Test @SuppressWarnings("unchecked") void automaticStorePromotionSavingsRecalculateWithoutExtraCouponRows() {
        UUID id=UUID.randomUUID();
        for(String[] values:new String[][]{{"11.50","11","3","1.50"},{"12","11","3","3"},{"12","10","4","8"},{"10","11","3","0"}}) {
            var line=new ShoppingPlan.Line(id,"Pampers",null,new BigDecimal(values[0]),new BigDecimal(values[1]),Integer.parseInt(values[2]));
            var coupon=new ShoppingPlan.ScenarioCoupon(UUID.randomUUID(),"Manufacturer",new BigDecimal("3"),2,List.of(id),null);
            var scenario=new ShoppingPlan.Scenario(List.of(line),List.of(coupon),List.of(),BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("15"),BigDecimal.ZERO,null,"CASH","");var estimate=scenario.estimate();
            assertEquals(0,new BigDecimal(values[3]).compareTo((BigDecimal)estimate.get("storePromotionTotal")));assertEquals(0,new BigDecimal(values[3]).add(new BigDecimal("3")).compareTo((BigDecimal)estimate.get("totalDiscounts")));
            assertEquals(0,line.sale().multiply(BigDecimal.valueOf(line.quantity())).subtract(new BigDecimal("18")).compareTo((BigDecimal)estimate.get("calculatedOop")));
            var rows=(List<Map<String,Object>>)estimate.get("storePromotions");assertEquals(values[3].equals("0")?0:1,rows.size());assertEquals(1,((List<?>)estimate.get("coupons")).size());assertEquals(rows,scenario.estimate().get("storePromotions"));
        }
    }

    @Test @SuppressWarnings("unchecked") void storeRRReducesQualifyingSpendOnlyWhenExplicitlySelected() {
        UUID line=UUID.randomUUID(),source=UUID.randomUUID();var items=List.of(new ShoppingPlan.Line(line,"Package",null,new BigDecimal("25"),null,1));
        for(String basis:List.of("BEFORE_COUPONS","BEFORE_COUPONS_AFTER_STORE_RR")) {
            var promotion=new ShoppingPlan.ScenarioPromotion(UUID.randomUUID(),"Spend 25 get 5","REGISTER_REWARD",new BigDecimal("5"),0,new BigDecimal("25"),basis,true,List.of(line));
            var payments=List.of(new ShoppingPlan.ScenarioPayment(UUID.randomUUID(),"REGISTER_REWARD",new BigDecimal("10"),"Manufacturer","MANUFACTURER",source),new ShoppingPlan.ScenarioPayment(UUID.randomUUID(),"REGISTER_REWARD",new BigDecimal("6"),"Store","STORE",null));
            var scenario=new ShoppingPlan.Scenario(items,List.of(),List.of(promotion),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,null,"CASH","",null,payments,null);
            var row=((List<Map<String,Object>>)scenario.estimate().get("promotions")).getFirst();
            assertEquals(0,new BigDecimal(basis.equals("BEFORE_COUPONS")?"25":"19").compareTo((BigDecimal)row.get("qualifyingSpend")));
            assertEquals(basis.equals("BEFORE_COUPONS"),row.get("eligible"));
            var duplicate=List.of(payments.getFirst(),new ShoppingPlan.ScenarioPayment(UUID.randomUUID(),"REGISTER_REWARD",new BigDecimal("10"),"duplicate","MANUFACTURER",source));
            assertThrows(IllegalArgumentException.class,()->new ShoppingPlan.Scenario(items,List.of(),List.of(promotion),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,null,"CASH","",null,duplicate,null));
        }
    }

    @Test
    void planAndPurchaseCopyCollectionsAndRequirePositiveRevisions() {
        UUID planId = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        ShoppingPlan plan = new ShoppingPlan(planId, "Saturday trip", "Store",
                LocalDate.of(2026, 10, 24), List.of(purchaseId), 1);
        assertEquals(List.of(purchaseId), plan.plannedPurchaseIds());
        PlannedPurchase purchase = new PlannedPurchase(purchaseId, planId,
                "Soap", "123", 2, new BigDecimal("2.50"), null, List.of(), 1);
        assertEquals("Soap", purchase.productName());
        assertThrows(IllegalArgumentException.class, () -> new ShoppingPlan(
                planId, "Trip", null, null, List.of(), 0));
    }

    @Test
    void clippedCouponRequiresAppliedRedemptionAndPreservesCouponTerms() {
        Coupon coupon = new Coupon("2 dollars off", new BigDecimal("2.00"), 1,
                CouponType.STORE, LocalDate.of(2026, 10, 31));
        UUID id = UUID.randomUUID();
        ClippedCoupon clipped = new ClippedCoupon(id, "Walgreens", "ext-1",
                coupon, CouponStatus.CLIPPED, LocalDate.of(2026, 10, 19), 1);
        assertEquals(CouponType.STORE, clipped.coupon().getCouponType());
        assertThrows(IllegalArgumentException.class, () -> new ClippedCoupon(id,
                "Walgreens", null, coupon, CouponStatus.APPLIED,
                LocalDate.of(2026, 10, 19), 1));
    }
    @Test void estimatesMemberRewardsOnlyForWalgreensWithoutChangingCheckout() {
        var a=new ShoppingPlan.Line(UUID.randomUUID(),"National Brand","1",new BigDecimal("10"),null,1);
        var b=new ShoppingPlan.Line(UUID.randomUUID(),"WALG Vitamins","2",new BigDecimal("10"),null,1);
        var excluded=new ShoppingPlan.Line(UUID.randomUUID(),"Excluded","3",new BigDecimal("10"),null,1,0);
        var scenario=new ShoppingPlan.Scenario(List.of(a,b,excluded),List.of(),List.of(),new BigDecimal("2"),new BigDecimal("3"),BigDecimal.ZERO,BigDecimal.ZERO,null,"CASH","");
        var w=scenario.estimate("Walgreens");var other=scenario.estimate("CVS");
        assertEquals(new BigDecimal("0.54"),w.get("memberRewardEstimate"));assertEquals(BigDecimal.ZERO,other.get("memberRewardEstimate"));
        assertEquals(w.get("calculatedOop"),other.get("calculatedOop"));assertEquals(new BigDecimal("29"),w.get("calculatedOop"));
        assertEquals(new BigDecimal("28.46"),w.get("afterPotentialRewards"));
        assertThrows(IllegalArgumentException.class,()->new ShoppingPlan.Line(UUID.randomUUID(),"Bad",null,BigDecimal.ONE,null,1,2));
    }
}
