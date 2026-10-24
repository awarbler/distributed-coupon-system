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
}
