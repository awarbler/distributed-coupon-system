package coupons.shopping;

import coupons.promotions.ClippedCoupon;
import coupons.promotions.Coupon;
import coupons.promotions.CouponStatus;
import coupons.promotions.CouponType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShoppingPlanAndCouponTest {
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
