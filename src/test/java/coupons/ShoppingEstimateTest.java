package coupons;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ShoppingEstimateTest {
    private static BigDecimal n(String v){return new BigDecimal(v);}
    private static ShoppingPlan.Line line(String name,String regular,String sale,int qty){return new ShoppingPlan.Line(UUID.randomUUID(),name,null,n(regular),sale==null?null:n(sale),qty);}
    private static ShoppingPlan.ScenarioCoupon coupon(String amount,int qty,ShoppingPlan.Line...lines){return new ShoppingPlan.ScenarioCoupon(UUID.randomUUID(),"Coupon",n(amount),qty,Arrays.stream(lines).map(ShoppingPlan.Line::id).toList(),null);}
    private static ShoppingPlan.ScenarioPromotion promotion(String name,String reward,int qty,String spend,boolean wc,ShoppingPlan.Line...lines){return new ShoppingPlan.ScenarioPromotion(UUID.randomUUID(),name,"REGISTER_REWARD",n(reward),qty,n(spend),"BEFORE_COUPONS",wc,Arrays.stream(lines).map(ShoppingPlan.Line::id).toList());}
    private static ShoppingPlan.Scenario scenario(List<ShoppingPlan.Line> items,List<ShoppingPlan.ScenarioCoupon> coupons,List<ShoppingPlan.ScenarioPromotion> promos,String tax,String wc,String rr){return new ShoppingPlan.Scenario(items,coupons,promos,n(tax),n(wc),n(rr),BigDecimal.ZERO,null,"CASH","");}
    private static void money(Map<String,Object> result,String key,String value){assertEquals(0,n(value).compareTo((BigDecimal)result.get(key)),key);}
    @Test void scenarioNeutrogenaAndLysolMatchOwnerExamples(){
        var neut=line("Neutrogena","7.79","5.99",2);var lysol=line("Lysol","3.29",null,2);
        var s=scenario(List.of(neut,lysol),List.of(coupon("4",2,neut)),List.of(promotion("Neutrogena RR","7",2,"0",true,neut),promotion("Lysol RR","5",2,"0",true,lysol)),"1.04","10","2");
        money(s.estimate(),"saleTotal","18.56");money(s.estimate(),"calculatedOop","3.60");money(s.estimate(),"potentialRewards","12");money(s.estimate(),"afterPotentialRewards","-8.40");
        var caramels=line("Caramels","0.50","0.43",3);var other=scenario(List.of(neut,lysol,caramels),s.coupons(),s.promotions(),"1.31","10","0");money(other.estimate(),"calculatedOop","7.16");
    }
    @Test void scenarioSharedCouponsAndMultipleSpendRewardsCountOnceAndRespectWcRestriction(){
        var shampoo=line("H&S shampoo","8.99","7",1);var conditioner=line("H&S conditioner","8.99","7",1);var charmin=line("Charmin","7.49","5.49",4);var items=List.of(shampoo,conditioner,charmin);
        var coupons=List.of(coupon("3",2,shampoo,conditioner),coupon("1",1,charmin));var promos=List.of(promotion("H&S","4",2,"0",true,shampoo,conditioner),promotion("Charmin","5",4,"0",true,charmin),promotion("Spend 30","7",0,"30",false,shampoo,conditioner,charmin),promotion("P&G","10",0,"35",true,shampoo,conditioner,charmin));
        var result=scenario(items,coupons,promos,"1.73","0","7").estimate();money(result,"regularTotal","47.94");money(result,"calculatedOop","26.69");money(result,"potentialRewards","26");
        money(scenario(items,coupons,promos,"1.73","5","7").estimate(),"potentialRewards","19");
        @SuppressWarnings("unchecked") var itemRows=(List<Map<String,Object>>)result.get("items");assertEquals(0,n("26").compareTo(itemRows.stream().map(r->(BigDecimal)r.get("possibleRewards")).reduce(BigDecimal.ZERO,BigDecimal::add)));
    }
    @Test void scenarioColgateScottAndLysolQualification(){
        var colgate=line("Colgate","6",null,2);var cg=scenario(List.of(colgate),List.of(coupon("6",2,colgate)),List.of(promotion("Colgate RR","5",2,"0",true,colgate)),"0","0","0").estimate();money(cg,"calculatedOop","6");money(cg,"afterPotentialRewards","1");
        var scott=line("Scott","5",null,2);var sc=scenario(List.of(scott),List.of(coupon("2",2,scott)),List.of(promotion("Spend20","10",0,"20",true,scott)),"0","0","0").estimate();money(sc,"calculatedOop","8");money(sc,"potentialRewards","0");
        var lysol=line("Lysol","3.29",null,2);money(scenario(List.of(lysol),List.of(),List.of(promotion("Lysol","5",2,"0",true,lysol)),"0.54","5","0").estimate(),"calculatedOop","2.12");
        var one=line("One","5",null,1);money(scenario(List.of(one),List.of(coupon("2",2,one)),List.of(),"0","0","0").estimate(),"couponTotal","0");
    }
    @Test void scenarioRoundTripsInPlanPayloadWithoutChangingLegacyConstructor()throws Exception{
        var l=line("Scott","5",null,2);var s=scenario(List.of(l),List.of(),List.of(),"0","0","0");var plan=new ShoppingPlan(UUID.randomUUID(),"Synthetic","Store",null,List.of(),1,s);
        var buffer=new java.io.ByteArrayOutputStream();try(var out=new java.io.ObjectOutputStream(buffer)){out.writeObject(plan);}try(var in=new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(buffer.toByteArray()))){assertEquals(plan,in.readObject());}
        assertNull(new ShoppingPlan(UUID.randomUUID(),"Legacy",null,null,List.of(),1).scenario());
    }
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

    @Test void linkedProductsWithDifferentCodesQualifyByDefault() {
        var offer = deal(
                DealTerms.Type.QUANTITY_REWARD,
                null, 2, null, "5");

        var first = new PlannedPurchase(
                UUID.randomUUID(), plan, "Dove Shampoo", "UPC-A",
                1, null, deal, List.of(), 1,
                new BigDecimal("8"), new BigDecimal("6"), "MANUAL");

        var second = new PlannedPurchase(
                UUID.randomUUID(), plan, "Dove Conditioner", "UPC-B",
                1, null, deal, List.of(), 1,
                new BigDecimal("8"), new BigDecimal("6"), "MANUAL");

        var result = estimate(List.of(first, second), offer);

        assertEquals(0, result.expectedRewards()
                .compareTo(new BigDecimal("5")));
        assertEquals(0, result.registerCost()
                .compareTo(new BigDecimal("12")));
    }

    @Test void linkedProductWithoutCodeQualifiesByDefault() {
        var offer = deal(
                DealTerms.Type.QUANTITY_REWARD,
                null, 2, null, "5");

        var first = new PlannedPurchase(
                UUID.randomUUID(), plan, "Dove Shampoo", null,
                1, null, deal, List.of(), 1,
                new BigDecimal("8"), new BigDecimal("6"), "MANUAL");

        var second = new PlannedPurchase(
                UUID.randomUUID(), plan, "Dove Conditioner", "UPC-B",
                1, null, deal, List.of(), 1,
                new BigDecimal("8"), new BigDecimal("6"), "MANUAL");

        var result = estimate(List.of(first, second), offer);

        assertEquals(0, result.expectedRewards()
                .compareTo(new BigDecimal("5")));
    }


    @Test
    void strictMatchingIncludesOnlyMatchingUpcs() {
        var terms = new DealTerms(
                DealTerms.Type.QUANTITY_REWARD,
                "Dove",
                null,
                null,
                2,
                null,
                0,
                new BigDecimal("5"),
                false,
                null,
                List.of("DOVE-001"),
                null,
                null,
                null,
                false,
                true
        );

        var offer = new AdvertisedDeal(
                deal,
                "Dove reward",
                "Buy two qualifying products",
                "Store",
                null,
                null,
                List.of(UUID.randomUUID()),
                1,
                terms
        );

        var matching = new PlannedPurchase(
                UUID.randomUUID(),
                plan,
                "Dove Shampoo",
                "DOVE-001",
                2,
                null,
                deal,
                List.of(),
                1,
                new BigDecimal("8"),
                new BigDecimal("6"),
                "MANUAL"
        );

        var different = new PlannedPurchase(
                UUID.randomUUID(),
                plan,
                "Dove Conditioner",
                "OTHER-UPC",
                1,
                null,
                deal,
                List.of(),
                1,
                new BigDecimal("8"),
                new BigDecimal("6"),
                "MANUAL"
        );

        var result = estimate(List.of(matching, different), offer);

        assertEquals(
                0,
                result.expectedRewards().compareTo(new BigDecimal("5"))
        );

        assertTrue(
                result.issues().stream()
                        .anyMatch(issue ->
                                issue.contains("Excluded from deal"))
        );
    }

    @Test
    void strictMatchingExcludesMissingUpc() {
        var terms = new DealTerms(
                DealTerms.Type.QUANTITY_REWARD,
                "Dove",
                null,
                null,
                2,
                null,
                0,
                new BigDecimal("5"),
                false,
                null,
                List.of("DOVE-001"),
                null,
                null,
                null,
                false,
                true
        );

        var offer = new AdvertisedDeal(
                deal,
                "Dove reward",
                "Buy two qualifying products",
                "Store",
                null,
                null,
                List.of(UUID.randomUUID()),
                1,
                terms
        );

        var missingCode = new PlannedPurchase(
                UUID.randomUUID(),
                plan,
                "Dove Shampoo",
                null,
                2,
                null,
                deal,
                List.of(),
                1,
                new BigDecimal("8"),
                new BigDecimal("6"),
                "MANUAL"
        );

        var result = estimate(List.of(missingCode), offer);

        assertEquals(0, result.expectedRewards().signum());

        assertTrue(
                result.issues().stream()
                        .anyMatch(issue ->
                                issue.contains("strict UPC/SKU matching"))
        );
    }

    @Test
    void strictMatchingRequiresQualifyingSkus() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DealTerms(
                        DealTerms.Type.QUANTITY_REWARD,
                        "Dove",
                        null,
                        null,
                        2,
                        null,
                        0,
                        new BigDecimal("5"),
                        false,
                        null,
                        List.of(),
                        null,
                        null,
                        null,
                        false,
                        true
                )
        );
    }

    private ShoppingEstimate planningEstimate(DealTerms.Type type, int quantity, String price,
            String store, LocalDate date) {
        var terms = new DealTerms(type, "Dove", null,
                type == DealTerms.Type.SALE ? new BigDecimal("3") : null,
                2, type == DealTerms.Type.SPEND_REWARD ? new BigDecimal("10") : null,
                0, type == DealTerms.Type.SALE ? null : new BigDecimal("5"),
                false, null, List.of(), null, null, null);
        var offer = new AdvertisedDeal(deal, "Dove", "Recorded terms", "Walgreens",
                LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 24), List.of(UUID.randomUUID()), 1, terms);
        var line = new PlannedPurchase(UUID.randomUUID(), plan, "Dove", null, quantity, null,
                deal, List.of(), 1, null, price == null ? null : new BigDecimal(price), "DEAL");
        return ShoppingEstimate.calculate(List.of(line), Map.of(deal, offer), Map.of(), date, store);
    }

    @Test void rewardPlanningIncludesMissingAndConflictingStoresAndDates() {
        for (String store : Arrays.asList(null, "", "Target", "Walgreens")) {
            for (LocalDate date : Arrays.asList(null, LocalDate.of(2026, 10, 17),
                    LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 25))) {
                var result = planningEstimate(DealTerms.Type.QUANTITY_REWARD, 2, "5", store, date);
                assertEquals(0, result.expectedRewards().compareTo(new BigDecimal("5")));
                assertEquals(store == null || store.isBlank() || !store.equals("Walgreens")
                        || date == null || date.isBefore(LocalDate.of(2026, 10, 18))
                        || date.isAfter(LocalDate.of(2026, 10, 24)), result.reviewRequired());
            }
        }
    }

    @Test void quantityRewardDoesNotNeedPricesButRegisterCostDoes() {
        var result = planningEstimate(DealTerms.Type.QUANTITY_REWARD, 2, null, "Walgreens", LocalDate.of(2026, 10, 21));
        assertEquals(0, result.expectedRewards().compareTo(new BigDecimal("5")));
        assertTrue(result.rewardsComplete());
        assertNull(result.registerCost());
        assertNull(result.finalCost());
    }

    @Test void missingSpendPricesAreUnknownRatherThanZero() {
        var result = planningEstimate(DealTerms.Type.SPEND_REWARD, 2, null, "Walgreens", LocalDate.of(2026, 10, 21));
        assertNull(result.expectedRewards());
        assertNull(result.finalCost());
        assertFalse(result.rewardsComplete());
        var progress = result.dealProgress().getFirst();
        assertEquals(ShoppingEstimate.RewardEstimateStatus.UNKNOWN, progress.rewardStatus());
        assertEquals(ShoppingEstimate.ThresholdStatus.UNKNOWN, progress.thresholdStatus());
        assertNull(progress.spendMissing());
    }

    @Test void incompleteQuantityIsKnownZeroAndRetainsCompletionOpportunity() {
        var result = planningEstimate(DealTerms.Type.QUANTITY_REWARD, 1, "5", null, null);
        var progress = result.dealProgress().getFirst();
        assertEquals(0, result.expectedRewards().signum());
        assertEquals(Long.valueOf(1), progress.quantityMissing());
        assertEquals(0, progress.rewardIfCompleted().compareTo(new BigDecimal("5")));
        assertEquals(ShoppingEstimate.RewardEstimateStatus.KNOWN, progress.rewardStatus());
    }

    @Test void saleOnlyHasNoRewardAndConflictDoesNotAuthorizeSalePrice() {
        var result = planningEstimate(DealTerms.Type.SALE, 2, "5", "Target", LocalDate.of(2026, 10, 21));
        assertEquals(ShoppingEstimate.RewardEstimateStatus.NOT_APPLICABLE, result.dealProgress().getFirst().rewardStatus());
        assertNull(result.dealProgress().getFirst().potentialReward());
        assertEquals(0, result.saleTotal().compareTo(new BigDecimal("10")));
    }

    @Test void spendProgressReportsKnownShortage() {
        var result = planningEstimate(DealTerms.Type.SPEND_REWARD, 1, "4", "Walgreens", LocalDate.of(2026, 10, 21));
        assertEquals(0, result.dealProgress().getFirst().spendMissing().compareTo(new BigDecimal("6")));
        assertEquals(0, result.expectedRewards().signum());
    }

    @Test void mixedUnknownRewardsExposeOnlyKnownSubtotal() {
        var otherId = UUID.randomUUID();
        var quantityOffer = deal(DealTerms.Type.QUANTITY_REWARD, null, 2, null, "5");
        var spendTerms = new DealTerms(DealTerms.Type.SPEND_REWARD, "Other", null, null, 1,
                new BigDecimal("10"), 0, new BigDecimal("2"), false, null, List.of(), null, null, null);
        var spendOffer = new AdvertisedDeal(otherId, "Other", "Spend", "Store", null, null,
                List.of(UUID.randomUUID()), 1, spendTerms);
        var unknown = new PlannedPurchase(UUID.randomUUID(), plan, "Unknown", null, 1, null,
                otherId, List.of(), 1, null, null, "MANUAL");
        var result = ShoppingEstimate.calculate(List.of(item("5", "5", 2, deal), unknown),
                Map.of(deal, quantityOffer, otherId, spendOffer), Map.of(), LocalDate.of(2026, 10, 21), "Store");
        assertNull(result.expectedRewards());
        assertEquals(0, result.knownPotentialRewards().compareTo(new BigDecimal("5")));
        assertFalse(result.rewardsComplete());
    }

    @Test void incompletePromotionDatesAreReviewableAndRepeatabilityStillApplies() {
        var offer = deal(DealTerms.Type.QUANTITY_REWARD, null, 2, null, "5");
        var result = estimate(List.of(item("5", "5", 4, deal)), offer);
        assertEquals(ShoppingEstimate.ReviewStatus.NOT_RECORDED, result.dealProgress().getFirst().dateStatus());
        assertEquals(0, result.expectedRewards().compareTo(new BigDecimal("10")));
        var t = offer.pricing();
        var capped = new DealTerms(t.type(), t.productDescription(), t.regularUnitPrice(), t.promotionalPrice(),
                t.minimumQuantity(), t.minimumSpend(), t.freeQuantity(), t.rewardAmount(), true, 1,
                t.qualifyingSkus(), t.qualifications(), t.loyaltyRequirement(), t.exclusions());
        var limited = new AdvertisedDeal(deal, offer.title(), offer.interpretedTerms(), offer.retailer(),
                offer.validFrom(), offer.validThrough(), offer.sourceArtifactIds(), 1, capped);
        assertEquals(0, estimate(List.of(item("5", "5", 4, deal)), limited).expectedRewards().compareTo(new BigDecimal("5")));
    }
    @org.junit.jupiter.api.Test void olayDealSeparatesCashAndOtherRebatesAndCalculatesPercentageTax() {
        var id=java.util.UUID.randomUUID();var ids=java.util.List.of(id);
        var line=new ShoppingPlan.Line(id,"Olay Premium",null,new java.math.BigDecimal("13.99"),new java.math.BigDecimal("11"),2);
        var coupon=new ShoppingPlan.ScenarioCoupon(java.util.UUID.randomUUID(),"Olay 5 off 2",new java.math.BigDecimal("2"),2,ids,null);
        var offers=new java.util.ArrayList<ShoppingPlan.ScenarioPromotion>();
        for(var entry:java.util.Map.of("FETCH","10000","REGISTER_REWARD","5","IBOTTA","3","PAYPAL","1.90").entrySet())offers.add(new ShoppingPlan.ScenarioPromotion(java.util.UUID.randomUUID(),entry.getKey(),entry.getKey(),new java.math.BigDecimal(entry.getValue()),1,java.math.BigDecimal.ZERO,"BEFORE_COUPONS",true,ids));
        var scenario=new ShoppingPlan.Scenario(java.util.List.of(line),java.util.List.of(coupon),offers,java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,null,"CASH","",new java.math.BigDecimal("8.25"));
        var r=scenario.estimate();
        for(var e:java.util.Map.of("saleTotal","22","merchandiseAfterCoupons","20","tax","1.65","calculatedOop","21.65","cashRebates","4.90","otherRebates","15","potentialRewards","19.90","afterCashRebates","16.75","beforeTaxAfterCashRebates","15.10","netBeforeTax","0.10").entrySet())org.junit.jupiter.api.Assertions.assertEquals(0,new java.math.BigDecimal(e.getValue()).compareTo((java.math.BigDecimal)r.get(e.getKey())),e.getKey());
        org.junit.jupiter.api.Assertions.assertEquals(0,new java.math.BigDecimal("1.75").compareTo((java.math.BigDecimal)r.get("afterPotentialRewards")));
    }

    @org.junit.jupiter.api.Test void repeatablePaymentsSeparateFundingEcbAndCashCredit() {
        var line=new ShoppingPlan.Line(java.util.UUID.randomUUID(),"Olay",null,new java.math.BigDecimal("11"),null,2);
        var payments=new java.util.ArrayList<ShoppingPlan.ScenarioPayment>();
        for(var entry:java.util.Map.of("ECB","5","REGISTER_REWARD","2","WALGREENS_CASH","3","CASH","4","CARD","9.82").entrySet())payments.add(new ShoppingPlan.ScenarioPayment(java.util.UUID.randomUUID(),entry.getKey(),new java.math.BigDecimal(entry.getValue()),"test"));
        var scenario=new ShoppingPlan.Scenario(java.util.List.of(line),java.util.List.of(),java.util.List.of(),java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,null,"CASH","",new java.math.BigDecimal("8.25"),payments);
        var r=scenario.estimate();
        for(var e:java.util.Map.of("tax","1.82","totalWithTax","23.82","ecbUsed","5","calculatedOop","13.82","selectedOop","13.82","manualDifference","0").entrySet())org.junit.jupiter.api.Assertions.assertEquals(0,new java.math.BigDecimal(e.getValue()).compareTo((java.math.BigDecimal)r.get(e.getKey())),e.getKey());
    }

    @org.junit.jupiter.api.Test void storePromotionAndWholeReceiptCodeDeductOnceAcrossCurrentProducts() {
        var a=new ShoppingPlan.Line(java.util.UUID.randomUUID(),"Olay",null,new java.math.BigDecimal("11"),null,2);
        var b=new ShoppingPlan.Line(java.util.UUID.randomUUID(),"Added later",null,new java.math.BigDecimal("8"),null,1);
        var item=new ShoppingPlan.ScenarioCoupon(java.util.UUID.randomUUID(),"Store sale",new java.math.BigDecimal("2"),1,java.util.List.of(a.id()),null,"SALE_PROMOTION");
        var whole=new ShoppingPlan.ScenarioCoupon(java.util.UUID.randomUUID(),"ONLINE5",new java.math.BigDecimal("5"),1,java.util.List.of(),null,"STORE_COUPON",true);
        var scenario=new ShoppingPlan.Scenario(java.util.List.of(a,b),java.util.List.of(item,whole),java.util.List.of(),java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,null,"CASH","",new java.math.BigDecimal("8.25"));
        var r=scenario.estimate();
        for(var e:java.util.Map.of("couponTotal","7","merchandiseAfterCoupons","23","tax","1.90","calculatedOop","24.90").entrySet())org.junit.jupiter.api.Assertions.assertEquals(0,new java.math.BigDecimal(e.getValue()).compareTo((java.math.BigDecimal)r.get(e.getKey())),e.getKey());
    }

    @org.junit.jupiter.api.Test void wholeReceiptRebateIncludesAllProductsAndCountsRewardOnce() {
        var a=new ShoppingPlan.Line(java.util.UUID.randomUUID(),"First",null,new java.math.BigDecimal("10"),null,1);
        var b=new ShoppingPlan.Line(java.util.UUID.randomUUID(),"Later",null,new java.math.BigDecimal("20"),null,1);
        var offer=new ShoppingPlan.ScenarioPromotion(java.util.UUID.randomUUID(),"Spend 30 get 5","REGISTER_REWARD",new java.math.BigDecimal("5"),2,new java.math.BigDecimal("30"),"BEFORE_COUPONS",true,java.util.List.of(),true);
        var scenario=new ShoppingPlan.Scenario(java.util.List.of(a,b),java.util.List.of(),java.util.List.of(offer),java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,null,"CASH","");
        org.junit.jupiter.api.Assertions.assertEquals(0,new java.math.BigDecimal("5").compareTo((java.math.BigDecimal)scenario.estimate().get("potentialRewards")));
    }

    @org.junit.jupiter.api.Test void walmartCashCountsAsOtherRewardsNotCashRebates() {
        var line=new ShoppingPlan.Line(java.util.UUID.randomUUID(),"Product",null,new java.math.BigDecimal("10"),null,1);
        var reward=new ShoppingPlan.ScenarioPromotion(java.util.UUID.randomUUID(),"Walmart offer","WALMART_CASH",new java.math.BigDecimal("3"),1,java.math.BigDecimal.ZERO,"BEFORE_COUPONS",true,java.util.List.of(),true);
        var r=new ShoppingPlan.Scenario(java.util.List.of(line),java.util.List.of(),java.util.List.of(reward),java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,null,"CASH","").estimate();
        org.junit.jupiter.api.Assertions.assertEquals(0,new java.math.BigDecimal("3").compareTo((java.math.BigDecimal)r.get("otherRebates")));
        org.junit.jupiter.api.Assertions.assertEquals(0,((java.math.BigDecimal)r.get("cashRebates")).signum());
    }

    @org.junit.jupiter.api.Test void walmartCashFundingUsesItsOwnRebatePermission() {
        var line=new ShoppingPlan.Line(java.util.UUID.randomUUID(),"Product",null,new java.math.BigDecimal("10"),null,1);
        var reward=new ShoppingPlan.ScenarioPromotion(java.util.UUID.randomUUID(),"Offer","WALMART_CASH",new java.math.BigDecimal("4"),1,java.math.BigDecimal.ZERO,"BEFORE_COUPONS",true,java.util.List.of(),true,false);
        var payment=new ShoppingPlan.ScenarioPayment(java.util.UUID.randomUUID(),"WALMART_CASH",new java.math.BigDecimal("3"),"Walmart balance");
        var r=new ShoppingPlan.Scenario(java.util.List.of(line),java.util.List.of(),java.util.List.of(reward),java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,null,"CASH","",null,java.util.List.of(payment)).estimate();
        org.junit.jupiter.api.Assertions.assertEquals(0,new java.math.BigDecimal("7").compareTo((java.math.BigDecimal)r.get("calculatedOop")));
        org.junit.jupiter.api.Assertions.assertEquals(0,((java.math.BigDecimal)r.get("potentialRewards")).signum());
    }

    @org.junit.jupiter.api.Test void noCouponsAndExpectedRebatesWithoutThresholdsCanBeCalculated() {
        var line=new ShoppingPlan.Line(java.util.UUID.randomUUID(),"Food",null,new java.math.BigDecimal("8.98"),null,1);
        var reward=new ShoppingPlan.ScenarioPromotion(java.util.UUID.randomUUID(),"Walmart offer","WALMART_CASH",new java.math.BigDecimal("3"),0,java.math.BigDecimal.ZERO,"BEFORE_COUPONS",true,java.util.List.of(),true);
        var r=new ShoppingPlan.Scenario(java.util.List.of(line),java.util.List.of(),java.util.List.of(reward),java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,null,"CASH","").estimate();
        org.junit.jupiter.api.Assertions.assertEquals(0,new java.math.BigDecimal("8.98").compareTo((java.math.BigDecimal)r.get("calculatedOop")));
        org.junit.jupiter.api.Assertions.assertEquals(0,new java.math.BigDecimal("3").compareTo((java.math.BigDecimal)r.get("potentialRewards")));
    }

}
