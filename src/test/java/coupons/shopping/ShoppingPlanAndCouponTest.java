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
    @Test void couponQuantityCountsAllAssociatedProductLines(){
        for(String[] entry:new String[][]{{"CVS","5"},{"Walgreens","2"}}){
            var first=new ShoppingPlan.Line(UUID.randomUUID(),"Eligible first",null,new BigDecimal("8"),null,1);var second=new ShoppingPlan.Line(UUID.randomUUID(),"Eligible second",null,new BigDecimal("8"),null,1);
            var coupon=new ShoppingPlan.ScenarioCoupon(UUID.randomUUID(),"Recorded offer",new BigDecimal(entry[1]),2,List.of(first.id(),second.id()),null);
            var scenario=new ShoppingPlan.Scenario(List.of(first,second),List.of(coupon),List.of(),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,null,"CASH","");
            assertEquals(0,new BigDecimal(entry[1]).compareTo((BigDecimal)scenario.estimate(entry[0]).get("couponTotal")));
        }
    }
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

    @Test void unverifiedWebsiteTermsRemainExcludedEvenAfterClippingAndIssuerClassification() {
        for(String verification:List.of("NEEDS_REVIEW","VERIFIED")) {
            var id=UUID.randomUUID();var terms=new coupons.promotions.CouponTerms(id,coupons.promotions.CouponTerms.Source.DIGITAL_MANUFACTURER,coupons.promotions.CouponTerms.Discount.DOLLARS,"Walgreens","Walgreens",null,"Product",List.of("123"),null,null,null,1,null,null,null,"Selected products...",LocalDate.of(2026,11,8),"available.pdf",Map.of("qualificationStatus",verification));
            var offer=new ClippedCoupon(id,"Walgreens",null,new Coupon("Imported offer",new BigDecimal("2"),1,CouponType.MANUFACTURER,LocalDate.of(2026,11,30),terms),CouponStatus.CLIPPED,LocalDate.of(2026,11,8),1);
            var purchase=new PlannedPurchase(UUID.randomUUID(),UUID.randomUUID(),"Product","123",1,BigDecimal.TEN,null,List.of(id),1);
            var estimate=ShoppingEstimate.calculate(List.of(purchase),Map.of(),Map.of(id,offer),LocalDate.of(2026,11,8),"Walgreens");
            assertEquals(0,new BigDecimal(verification.equals("VERIFIED")?"2":"0").compareTo(estimate.couponSavings()));
            if(verification.equals("NEEDS_REVIEW"))assertTrue(estimate.issues().stream().anyMatch(x->x.contains("qualifying terms need review")));
        }
    }

    @Test @SuppressWarnings("unchecked") void selectedLibraryCouponCountsInPlanningWithoutConfirmingActualUse() throws Exception {
        UUID line=UUID.randomUUID(),saved=UUID.randomUUID();
        var coupon=new ShoppingPlan.ScenarioCoupon(UUID.randomUUID(),"Available Venus coupon",new BigDecimal("4"),1,List.of(line),saved,"MANUFACTURER",false,true);
        var scenario=new ShoppingPlan.Scenario(List.of(new ShoppingPlan.Line(line,"Venus","123",new BigDecimal("12"),null,1)),List.of(coupon),List.of(),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,null,"CASH","");
        var report=scenario.estimate("Walgreens");assertEquals(new BigDecimal("4"),report.get("couponTotal"));assertEquals(new BigDecimal("8"),report.get("calculatedOop"));assertEquals(true,((List<Map<String,Object>>)report.get("coupons")).getFirst().get("applied"));
        var bytes=new java.io.ByteArrayOutputStream();try(var out=coupons.serialization.LegacyObjectStreams.output(bytes)){out.writeObject(scenario);}try(var in=coupons.serialization.LegacyObjectStreams.input(new java.io.ByteArrayInputStream(bytes.toByteArray()))){var restored=(ShoppingPlan.Scenario)in.readObject();assertEquals(saved,restored.coupons().getFirst().savedCouponId());assertTrue(restored.coupons().getFirst().pendingVerification());assertEquals(report,restored.estimate("Walgreens"));}
    }
    @Test @SuppressWarnings("unchecked") void bounceDownyAndTideSelectedCouponsGiveThirteenDollarPlanningSubtotal() {
        var bounce=UUID.randomUUID();var downy=UUID.randomUUID();var tide=UUID.randomUUID();var items=List.of(new ShoppingPlan.Line(bounce,"BOUNCE",null,new BigDecimal("5"),null,1),new ShoppingPlan.Line(downy,"DOWNY",null,new BigDecimal("5"),null,1),new ShoppingPlan.Line(tide,"TIDE LAUNDRY CARE",null,new BigDecimal("5"),null,1));
        var coupons=List.of(new ShoppingPlan.ScenarioCoupon(UUID.randomUUID(),"Downy $1 off 1",BigDecimal.ONE,1,List.of(downy),UUID.randomUUID(),"UNKNOWN",false,true),new ShoppingPlan.ScenarioCoupon(UUID.randomUUID(),"Tide $1 off 1",BigDecimal.ONE,1,List.of(tide),UUID.randomUUID(),"UNKNOWN",false,true));
        var scenario=new ShoppingPlan.Scenario(items,coupons,List.of(),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,null,"CASH","");var result=scenario.estimate("Walgreens");assertEquals(new BigDecimal("15"),result.get("saleTotal"));assertEquals(new BigDecimal("2"),result.get("couponTotal"));assertEquals(new BigDecimal("13"),result.get("calculatedOop"));assertTrue(((List<Map<String,Object>>)result.get("coupons")).stream().allMatch(c->Boolean.TRUE.equals(c.get("applied"))));
        var tooFew=new ShoppingPlan.ScenarioCoupon(UUID.randomUUID(),"Requires two",BigDecimal.ONE,2,List.of(downy),UUID.randomUUID(),"UNKNOWN",false,true);var limited=new ShoppingPlan.Scenario(items,List.of(tooFew),List.of(),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,null,"CASH","");assertEquals(BigDecimal.ZERO,limited.estimate("Walgreens").get("couponTotal"));
    }
    @Test void zeroAmountPlannedSelectionDoesNotCauseAnAppliedZeroDiscountOrDivisionByZero() {
        var line=UUID.randomUUID();var selection=new ShoppingPlan.ScenarioCoupon(UUID.randomUUID(),"Percentage amount not entered",BigDecimal.ZERO,1,List.of(line),UUID.randomUUID(),"UNKNOWN",false,true);var scenario=new ShoppingPlan.Scenario(List.of(new ShoppingPlan.Line(line,"Product",null,BigDecimal.ZERO,null,1)),List.of(selection),List.of(),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,null,"CASH","");assertEquals(BigDecimal.ZERO,scenario.estimate().get("couponTotal"));
    }
    @Test void confirmedReceiptUseOfAvailableCouponDoesNotFabricateClipping() {
        var id=UUID.randomUUID();var coupon=new Coupon("Venus",new BigDecimal("4"),1,CouponType.MANUFACTURER,LocalDate.of(2026,11,30));
        var available=new ClippedCoupon(id,"Walgreens",null,coupon,CouponStatus.AVAILABLE,null,1);var used=new ClippedCoupon(id,"Walgreens",null,coupon,CouponStatus.APPLIED,available.clippedOn(),LocalDate.of(2026,11,9),UUID.randomUUID(),2);
        assertNull(used.clippedOn());assertEquals(CouponStatus.APPLIED,used.status());assertNotNull(used.redeemedTransactionId());assertEquals(CouponStatus.AVAILABLE,available.status());
    }
    @Test void importedRewardsRebatesAndBogoNeverBecomeImmediateCheckoutDeductions() {
        for(var mechanism:List.of(coupons.promotions.CouponTerms.Discount.REWARD,coupons.promotions.CouponTerms.Discount.REBATE,coupons.promotions.CouponTerms.Discount.BOGO,coupons.promotions.CouponTerms.Discount.UNKNOWN)) {
            var id=UUID.randomUUID();var terms=new coupons.promotions.CouponTerms(id,coupons.promotions.CouponTerms.Source.DIGITAL_MANUFACTURER,mechanism,"Walgreens","Walgreens",null,"Product",List.of(),null,null,null,1,"Recorded original terms",null,null);
            var offer=new ClippedCoupon(id,"Walgreens",null,new Coupon("Imported offer",null,0,CouponType.MANUFACTURER,LocalDate.of(2026,11,30),terms),CouponStatus.CLIPPED,LocalDate.of(2026,11,8),1);
            var purchase=new PlannedPurchase(UUID.randomUUID(),UUID.randomUUID(),"Product",null,1,BigDecimal.TEN,null,List.of(id),1);
            var estimate=ShoppingEstimate.calculate(List.of(purchase),Map.of(),Map.of(id,offer),LocalDate.of(2026,11,8),"Walgreens");assertEquals(0,estimate.couponSavings().signum());assertEquals(BigDecimal.TEN,estimate.registerCost());assertTrue(estimate.issues().stream().anyMatch(x->x.contains("separate promotion/rebate review")));
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
