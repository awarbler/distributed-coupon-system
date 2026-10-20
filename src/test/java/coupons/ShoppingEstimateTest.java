package coupons;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ShoppingEstimateTest {
    final UUID plan=UUID.randomUUID(),deal=UUID.randomUUID();
    PlannedPurchase item(String regular,String sale,int quantity,UUID link){return new PlannedPurchase(UUID.randomUUID(),plan,"Soap","sku",quantity,null,link,List.of(),1,new BigDecimal(regular),sale==null?null:new BigDecimal(sale),link==null?"MANUAL":"DEAL");}
    AdvertisedDeal deal(DealTerms.Type type,String price,int quantity,String spend,String reward){return new AdvertisedDeal(deal,"Offer","Recorded terms","Store",null,null,List.of(UUID.randomUUID()),1,new DealTerms(type,"Soap",null,price==null?null:new BigDecimal(price),quantity,spend==null?null:new BigDecimal(spend),1,reward==null?null:new BigDecimal(reward),true,null,List.of("sku"),null,null,null));}
    ShoppingEstimate estimate(List<PlannedPurchase> items,AdvertisedDeal d){return ShoppingEstimate.calculate(items,d==null?Map.of():Map.of(deal,d),Map.of(),LocalDate.of(2026,10,19),"Store");}
    @Test void manualSaleAndExactSavings(){var v=estimate(List.of(item("7.0000","5.123456789",3,null)),null);assertEquals(0,v.regularTotal().compareTo(new BigDecimal("21")));assertEquals(new BigDecimal("15.370370367"),v.saleTotal());assertEquals(new BigDecimal("5.629629633"),v.saleSavings());}
    @Test void bundleSpansLinkedProductsAndKeepsRemainder(){var v=estimate(List.of(item("3",null,1,deal),item("3",null,2,deal)),deal(DealTerms.Type.MULTIBUY,"5",2,null,null));assertEquals(0,v.saleTotal().compareTo(new BigDecimal("8")));assertEquals(0,v.registerCost().compareTo(new BigDecimal("8")));}
    @Test void spendRewardsDoNotReduceRegisterCost(){var v=estimate(List.of(item("10",null,2,deal)),deal(DealTerms.Type.SPEND_REWARD,null,1,"20","5"));assertEquals(0,v.expectedRewards().compareTo(new BigDecimal("5")));assertEquals(0,v.registerCost().compareTo(new BigDecimal("20")));assertEquals(0,v.finalCost().compareTo(new BigDecimal("15")));}
    @Test void unmetAndUnlinkedItemsDoNotEarn(){var v=estimate(List.of(item("10",null,1,deal),item("100",null,1,null)),deal(DealTerms.Type.SPEND_REWARD,null,1,"20","5"));assertEquals(0,v.expectedRewards().signum());assertTrue(v.issues().stream().anyMatch(i->i.contains("Threshold")));}
    @Test void couponsCheckSkusDatesQuantityAndPhysicalCopyLimits(){
        var group=UUID.randomUUID();var terms=new CouponTerms(group,CouponTerms.Source.PAPER_MANUFACTURER,CouponTerms.Discount.PERCENT,"Maker",null,null,"Soap",List.of("sku"),null,new BigDecimal("5"),LocalDate.of(2026,10,1),1,null,null,null);
        var c=new Coupon("10 percent",new BigDecimal("10"),2,CouponType.MANUFACTURER,LocalDate.of(2026,10,31),terms);var a=new ClippedCoupon(UUID.randomUUID(),"Maker",null,c,CouponStatus.CLIPPED,LocalDate.of(2026,10,1),1);var b=new ClippedCoupon(UUID.randomUUID(),"Maker",null,c,CouponStatus.CLIPPED,LocalDate.of(2026,10,1),1);
        var line=new PlannedPurchase(UUID.randomUUID(),plan,"Soap","sku",2,new BigDecimal("5"),null,List.of(a.couponId(),b.couponId()),1,new BigDecimal("5"),null,"MANUAL");
        var result=ShoppingEstimate.calculate(List.of(line),Map.of(),Map.of(a.couponId(),a,b.couponId(),b),LocalDate.of(2026,10,19),"Store");assertEquals(0,result.couponSavings().compareTo(BigDecimal.ONE));assertTrue(result.issues().stream().anyMatch(i->i.contains("usage limit")));
        var expired=ShoppingEstimate.calculate(List.of(line),Map.of(),Map.of(a.couponId(),a,b.couponId(),b),LocalDate.of(2026,11,1),"Store");assertEquals(0,expired.couponSavings().signum());
    }
    @Test void freeItemCouponHasNoInventedDollarFaceValue(){
        var terms=new CouponTerms(UUID.randomUUID(),CouponTerms.Source.PAPER_STORE,CouponTerms.Discount.FREE_ITEM,"Store",null,null,"Soap",List.of(),null,null,null,1,null,null,null);
        var coupon=new Coupon("Free soap",null,1,CouponType.STORE,LocalDate.of(2026,10,31),terms);assertNull(coupon.getDiscountAmount());var saved=new ClippedCoupon(UUID.randomUUID(),"Store",null,coupon,CouponStatus.CLIPPED,LocalDate.of(2026,10,1),1);
        var line=new PlannedPurchase(UUID.randomUUID(),plan,"Soap",null,1,new BigDecimal("3.14159265358979323846"),null,List.of(saved.couponId()),1,new BigDecimal("3.14159265358979323846"),null,"MANUAL");
        var result=ShoppingEstimate.calculate(List.of(line),Map.of(),Map.of(saved.couponId(),saved),LocalDate.of(2026,10,19),"Store");assertEquals(new BigDecimal("3.14159265358979323846"),result.couponSavings());assertEquals(0,result.registerCost().signum());
    }
    @Test void buyGetNeverAssumesWhichProductsAreDiscounted(){
        var unknown=deal(DealTerms.Type.BUY_GET,"0",2,null,null);var lines=List.of(item("4",null,3,deal));assertEquals(0,estimate(lines,unknown).saleTotal().compareTo(new BigDecimal("12")));
        var p=unknown.pricing();var recorded=new DealTerms(p.type(),p.productDescription(),p.regularUnitPrice(),p.promotionalPrice(),p.minimumQuantity(),p.minimumSpend(),p.freeQuantity(),p.rewardAmount(),p.repeatable(),p.limit(),p.qualifyingSkus(),p.qualifications(),p.loyaltyRequirement(),p.exclusions(),true);
        var confirmed=new AdvertisedDeal(unknown.dealId(),unknown.title(),unknown.interpretedTerms(),unknown.retailer(),unknown.validFrom(),unknown.validThrough(),unknown.sourceArtifactIds(),1,recorded);assertEquals(0,estimate(lines,confirmed).saleTotal().compareTo(new BigDecimal("8")));
    }
}
