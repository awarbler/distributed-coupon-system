package coupons.api;

import coupons.promotions.ClippedCoupon;
import coupons.promotions.Coupon;
import coupons.promotions.CouponStatus;
import coupons.promotions.CouponTerms;
import coupons.promotions.CouponType;
import coupons.shopping.ShoppingPlan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShoppingNotebookTest {
    static BigDecimal n(String value){return new BigDecimal(value);}
    static ShoppingPlan.Line line(String name,String price,int quantity,ShoppingPlan.PriceRule rule){return new ShoppingPlan.Line(UUID.randomUUID(),name,"123",null,n(price),quantity,null,rule);}
    static ShoppingPlan.Scenario scenario(List<ShoppingPlan.Line> lines,String coupon,String tax,String rr,String wc){
        var coupons=coupon==null?List.<ShoppingPlan.ScenarioCoupon>of():List.of(new ShoppingPlan.ScenarioCoupon(UUID.randomUUID(),"Entered coupon",n(coupon),1,lines.stream().map(ShoppingPlan.Line::id).toList(),null));
        return new ShoppingPlan.Scenario(lines,coupons,List.of(),n(tax),n(wc),n(rr),BigDecimal.ZERO,null,"CASH","",null,null);
    }
    @Test void sameProductSaleButtonsApplyOnlyToCompleteGroupsAndPreserveExactDecimals(){
        assertEquals(n("10.98"),new ShoppingPlan.PriceRule("BOGO_FREE",null).total(n("5.49"),3));
        assertEquals(n("5.49"),new ShoppingPlan.PriceRule("BUY_ONE_GET_TWO",null).total(n("5.49"),3));
        assertEquals(n("10.98"),new ShoppingPlan.PriceRule("BUY_ONE_GET_TWO",null).total(n("5.49"),4));
        assertEquals(n("8.235"),new ShoppingPlan.PriceRule("BOGO_HALF",null).total(n("5.49"),2));
        assertEquals(n("11.00"),new ShoppingPlan.PriceRule("TWO_FOR",n("5.00")).total(n("6.00"),3));
    }
    @Test void mixedPriceBogoGroupsDiscountCheapestEligibleUnitsAndRejectOverlap(){
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();var rule=new ShoppingPlan.PriceRule("BOGO_HALF",null,List.of(a,b));
        var first=new ShoppingPlan.Line(a,"Aquaphor",null,n("13.99"),null,1,null,rule);
        var second=new ShoppingPlan.Line(b,"Eucerin",null,n("12.99"),null,1);
        var estimate=scenario(List.of(first,second),null,"0","0","0").estimate();
        assertEquals(n("20.485"),estimate.get("saleTotal"));
        assertEquals(n("6.495"),estimate.get("saleReductionTotal"));
        var overlap=new ShoppingPlan.Line(b,"Eucerin",null,n("12.99"),null,1,null,new ShoppingPlan.PriceRule("BOGO_FREE",null));
        assertThrows(IllegalArgumentException.class,()->scenario(List.of(first,overlap),null,"0","0","0").estimate());
    }

    @Test void unknownRegularPriceAndKnownZeroStayDistinct(){
        var estimate=scenario(List.of(line("Colgate","6.0000",2,null)),"5","0","0","0").estimate();
        assertNull(estimate.get("regularTotal"));assertEquals(n("7.0000"),estimate.get("calculatedOop"));
        var free=scenario(List.of(line("Free item","0.0000",1,null)),null,"0","0","0").estimate();
        assertEquals(n("0.0000"),free.get("saleTotal"));
        assertThrows(IllegalArgumentException.class,()->new ShoppingPlan.Line(UUID.randomUUID(),"Unpriced",null,null,null,1));
    }
    @Test void approvedABComparisonUsesExistingEstimatesAndCountsFundingOnce(){
        var a=scenario(List.of(line("Original products","22.55",1,null)),"7","1.58","15","2");
        var b=scenario(List.of(line("Original products","22.55",1,null),new ShoppingPlan.Line(UUID.randomUUID(),"Scott packages",null,null,n("5"),2)),"11.50","1.63","20","2");
        var result=ShoppingReview.compare(a,null,b,null);
        @SuppressWarnings("unchecked") var left=(Map<String,Object>)result.get("a");
        @SuppressWarnings("unchecked") var right=(Map<String,Object>)result.get("b");
        assertEquals(n("24.13"),left.get("total"));assertEquals(n("17.13"),left.get("totalWithTax"));assertEquals(n("0.13"),left.get("calculatedOop"));
        assertEquals(n("34.18"),right.get("total"));assertEquals(n("22.68"),right.get("totalWithTax"));assertEquals(n("0.68"),right.get("calculatedOop"));
        assertEquals(n("0.55"),result.get("cashDifference"));assertEquals(n("5"),result.get("rrDifference"));
        assertTrue(Json.encode(result.get("addedItems")).contains("Scott packages"));
    }
    @Test void reconciliationPreservesMissingReportedBalanceAndSignedDifference(){
        var result=ShoppingReview.reconcile(n("202.73"),n("23.75"),n("43.00"),null);
        assertEquals(n("183.48"),result.get("calculatedEnding"));assertNull(result.get("difference"));
        assertEquals(n("0.01"),ShoppingReview.reconcile(n("202.73"),n("23.75"),n("43.00"),n("183.49")).get("difference"));
        assertNull(ShoppingReview.reconcile(null,n("0"),n("0"),n("0")).get("calculatedEnding"));
    }
    @Test void pastedNotesKeepUnrecognizedTermsAndOriginalText(){
        String text="Colgate 6 x2\n5 off 2\nearn 5 RR\nOffer applies only to one account";
        var source=new ShoppingPlan.PlanningSource(text,null,null);
        var result=ShoppingNoteInput.parse(text,source);
        assertEquals(1,((List<?>)result.get("items")).size());assertEquals(1,((List<?>)result.get("coupons")).size());assertEquals(1,((List<?>)result.get("promotions")).size());
        assertEquals(List.of("Offer applies only to one account"),result.get("unresolved"));assertEquals(text,((ShoppingPlan.PlanningSource)result.get("source")).text());
    }
    @Test void screenshotStyleQuantityIsParsedWithoutInferringAccountEligibility(){
        var result=ShoppingNoteInput.parse("Buy (2) Colgate Dental Care $6 each\nUse $6/2 Colgate digital coupon\nPay $6 & get a $5 RR",null);
        assertEquals(1,((List<?>)result.get("items")).size());assertEquals(1,((List<?>)result.get("coupons")).size());
        assertEquals(1,((List<?>)result.get("unresolved")).size());
    }
    @Test void covergirlPosterImportsQuantityBogoAndIbottaAsPostPurchaseBenefit(){
        String text="In-store Deal at Walgreens\n2 CoverGirl LashBlast Mascaras\n$13.49 $25.98 48% off\nWhat to buy\n2 CoverGirl LashBlast Volume Mascara, $12.99 ›\nBuy 1 get 1 50% off\nRedeem $3.00/1 Covergirl Ibotta rebate 2 times\nRebate coupons\n$3.00/1 - Covergirl Eye Cosmetic Product, Ibotta App Rebate\nPrice Summary\nPay $19.49, get $6 Ibotta rebate\n$6.74 each";
        var result=ShoppingNoteInput.parse(text,new ShoppingPlan.PlanningSource(text,null,null));
        @SuppressWarnings("unchecked") var items=(List<ShoppingPlan.Line>)result.get("items");
        @SuppressWarnings("unchecked") var offers=(List<ShoppingPlan.ScenarioPromotion>)result.get("promotions");
        assertEquals("Walgreens",result.get("retailer"));assertEquals(1,items.size());assertEquals(2,items.getFirst().quantity());assertEquals(n("12.99"),items.getFirst().sale());
        assertEquals("BOGO_HALF",items.getFirst().priceRule().type());assertTrue(((List<?>)result.get("coupons")).isEmpty());
        assertEquals(1,offers.size());assertEquals("IBOTTA",offers.getFirst().rewardType());assertEquals(n("6.00"),offers.getFirst().rewardAmount());assertEquals(2,offers.getFirst().requiredQuantity());
        var scenario=new ShoppingPlan.Scenario(items,List.of(),offers,n("0"),n("0"),n("0"),n("0"),null,"CASH","");
        assertEquals(n("19.485"),scenario.estimate().get("calculatedOop"));assertEquals(n("6.00"),scenario.estimate().get("cashRebates"));
        assertEquals(n("13.485"),scenario.estimate().get("afterCashRebates"));assertEquals(text,((ShoppingPlan.PlanningSource)result.get("source")).text());
    }
    @Test void formattedClipboardCovergirlDealKeepsLinksOutOfAmountsAndPreservesOriginal(){
        String product="https://example.invalid/click?sid=example&url=https%3A%2F%2Fwww.walgreens.com%2Fstore%2Fc%2Fmascara%3FskuId%3Dsku3854470";
        String text="""
                [**2 CoverGirl LashBlast Mascaras**]({product})
                **$13.49$25.9848% off**
                **svg**
                **What to buy**
                [**2\u00a0CoverGirl LashBlast Volume Mascara,\u00a0$12.99\u00a0svg**]({product})
                *Buy 1 get 1 50% off*
                *Redeem $3.00/1 Covergirl Ibotta rebate 2 times*
                **svg**
                **Rebate couponssvg**
                [**$3.00/1 - Covergirl Eye Cosmetic Product, Ibotta App Rebate**](https://example.invalid/ibotta?friend_code=example)**\u200bsvg**
                Price Summary
                Pay $19.49, get $6 Ibotta rebate
                $6.74 each
                """.replace("{product}",product);
        var source=new ShoppingPlan.PlanningSource(text,null,null);var result=ShoppingNoteInput.parse(text,source);
        @SuppressWarnings("unchecked") var items=(List<ShoppingPlan.Line>)result.get("items");
        @SuppressWarnings("unchecked") var offers=(List<ShoppingPlan.ScenarioPromotion>)result.get("promotions");
        assertEquals(1,items.size());assertEquals("CoverGirl LashBlast Volume Mascara",items.getFirst().name());
        assertEquals(2,items.getFirst().quantity());assertEquals(n("12.99"),items.getFirst().sale());
        assertEquals("BOGO_HALF",items.getFirst().priceRule().type());assertEquals(1,offers.size());
        assertEquals("IBOTTA",offers.getFirst().rewardType());assertEquals(n("6.00"),offers.getFirst().rewardAmount());
        assertTrue(((List<?>)result.get("coupons")).isEmpty());assertSame(source,result.get("source"));assertEquals(text,source.text());
        assertTrue(((List<?>)result.get("unresolved")).stream().noneMatch(v->v.toString().contains("https://")));
    }
    @Test void clipboardNormalizationRetainsMultiplicationAndHandlesBalancedUrlParentheses(){
        var result=ShoppingNoteInput.parse("**Colgate 6 * 2**\n[**2 Mascara, $12.99 svg**](https://example.invalid/item(size)?x=1)\n*Buy 1 get 1 50% off*",null);
        @SuppressWarnings("unchecked") var items=(List<ShoppingPlan.Line>)result.get("items");
        assertEquals(2,items.size());assertEquals(2,items.getFirst().quantity());assertEquals(n("6"),items.getFirst().sale());
        assertEquals("Mascara",items.getLast().name());assertEquals("BOGO_HALF",items.getLast().priceRule().type());
    }

    @Test void cvsGainPosterImportsPrefixQuantitiesAndCountsRepeatedEcbOnlyOnce(){
        String text="CVS Weekly Deals\nBuy\n1x Gain flings $9.99\n1x gain liquid detergent $9.99\n1x gain fabric softener $5.99\nSpend $25 get $8ECB\nPay\n$17.97\nUse $4 MFC, $3 MFC, $1 MFC\nGet\n$8 ECB";
        var result=ShoppingNoteInput.parse(text,null);
        @SuppressWarnings("unchecked") var items=(List<ShoppingPlan.Line>)result.get("items");
        @SuppressWarnings("unchecked") var coupons=(List<ShoppingPlan.ScenarioCoupon>)result.get("coupons");
        @SuppressWarnings("unchecked") var offers=(List<ShoppingPlan.ScenarioPromotion>)result.get("promotions");
        assertEquals("CVS",result.get("retailer"));assertEquals(3,items.size());assertEquals(n("5.99"),items.getLast().sale());assertEquals(1,coupons.size());
        assertEquals(n("8"),coupons.getFirst().amount());assertTrue(coupons.getFirst().wholeReceipt());assertTrue(coupons.getFirst().lineIds().isEmpty());
        assertEquals(1,offers.size());assertEquals("ECB",offers.getFirst().rewardType());assertEquals(n("25"),offers.getFirst().requiredSpend());
        assertEquals("AFTER_COUPONS",offers.getFirst().spendBasis());
        var scenario=new ShoppingPlan.Scenario(items,coupons,offers,n("0"),n("0"),n("0"),n("0"),null,"CASH","");
        assertEquals(n("17.97"),scenario.estimate().get("calculatedOop"));
        assertTrue(((List<?>)result.get("unresolved")).stream().anyMatch(v->v.toString().contains("Spend basis")));
    }
    @Test void incompleteManufacturerCouponListDoesNotSilentlyApplyAPartialTotal(){
        var result=ShoppingNoteInput.parse("1x Gain flings $9.99\nUse $4 MFC, $3 MFC, $1 unreadable",null);
        assertTrue(((List<?>)result.get("coupons")).isEmpty());
        assertTrue(((List<?>)result.get("unresolved")).stream().anyMatch(v->v.toString().contains("no partial coupon total")));
    }
    @Test void bareIbottaReferencesAreNeverMisclassifiedAsCheckoutCoupons(){
        var result=ShoppingNoteInput.parse("2 Mascara $12.99\n$3.00/1 - Covergirl Ibotta App Rebate",null);
        assertEquals(1,((List<?>)result.get("items")).size());assertTrue(((List<?>)result.get("coupons")).isEmpty());
        assertTrue(((List<?>)result.get("promotions")).isEmpty());assertEquals(1,((List<?>)result.get("unresolved")).size());
    }

    @Test void skuQualifiedCouponsMatchButAmbiguousOrExpiredOffersDoNot(){
        var terms=new CouponTerms(UUID.randomUUID(),CouponTerms.Source.DIGITAL_MANUFACTURER,CouponTerms.Discount.DOLLARS,"Brand","Walgreens",null,"Colgate",List.of("123"),null,null,LocalDate.of(2026,10,1),1,null,null,null);
        var coupon=new Coupon("$5 off 2",n("5"),2,CouponType.MANUFACTURER,LocalDate.of(2026,10,31),terms);
        var clipped=new ClippedCoupon(UUID.randomUUID(),"Brand",null,coupon,CouponStatus.CLIPPED,LocalDate.of(2026,10,1),1);
        var plan=scenario(List.of(line("Colgate","6",2,null)),null,"0","0","0");
        var result=ShoppingOfferMatch.match(plan,"Walgreens",LocalDate.of(2026,10,27),List.of(clipped));
        assertEquals(1,result.size());assertEquals(clipped.couponId(),result.getFirst().savedCouponId());assertEquals(n("5"),result.getFirst().amount());
        assertTrue(ShoppingOfferMatch.match(plan,"CVS",LocalDate.of(2026,10,27),List.of(clipped)).isEmpty());
        assertTrue(ShoppingOfferMatch.match(plan,"Walgreens",LocalDate.of(2026,11,1),List.of(clipped)).isEmpty());
        assertTrue(ShoppingOfferMatch.match(plan,"Walgreens",null,List.of(clipped)).isEmpty());
    }
    @Test void inlineDealCreatesProductsAndLinksNamedCouponsWithoutInventingAmbiguousRewards(){
        String text="Buy 4 Kotex BioCare Ultra Thin Pads $7.49 each (buy 1, get 1 50% off)\nBuy 1 Venus Women's Comfort Glide Razor + 2 Refills $11.49 (buy 1, get 1 50% off)\nBuy 1 Gillette Skin Guard Men’s Razor + 1 Refill $11.99 (buy 1, get 1 50% off)\nBuy 2 Dove Aluminum Free Deodorant 2.40z. $11.49 each (buy 1, get 150% off)\nUse the $3 off 2 Kotex coupon (exp. 11/28)\nUse the $2 off Kotex coupon (exp. 11/7)\nUse the $3 off Venus coupon (exp. 11/7)\nUse the $3 off Gillette coupon (exp. 11/7)\nUse the $4 off 2 Dove coupon (exp. 10/31)\nUse code OCT20 at checkout - will deduct $8.49\nClip the $10 Cash Rewards WYB $40 + myWalgreens coupon (exp. 10/31)\nGet back $7 Cash Rewards WYB 3 Kotex Products\nGet back $6 Cash Rewards WYB 2 Gillette/Venus Razors\nGet back $10 Booster Cash Rewards\nGet back 48¢ Cash Rewards for purchase\nGet back 4,000 points via Fetch Rewards for Dove Aluminum Free Deodorant (limit 3) = $8";
        var result=ShoppingNoteInput.parse(text,null);
        @SuppressWarnings("unchecked") var items=(List<ShoppingPlan.Line>)result.get("items");
        @SuppressWarnings("unchecked") var coupons=(List<ShoppingPlan.ScenarioCoupon>)result.get("coupons");
        @SuppressWarnings("unchecked") var offers=(List<ShoppingPlan.ScenarioPromotion>)result.get("promotions");
        assertEquals(List.of(4,1,1,2),items.stream().map(ShoppingPlan.Line::quantity).toList());
        assertEquals("BOGO_HALF",items.getFirst().priceRule().type());assertNull(items.getLast().priceRule());
        assertEquals(6,coupons.size());assertEquals(List.of(items.getFirst().id()),coupons.getFirst().lineIds());
        assertEquals(List.of(items.get(1).id()),coupons.get(2).lineIds());assertTrue(coupons.getLast().wholeReceipt());
        assertEquals(n("8.49"),coupons.getLast().amount());assertEquals(3,offers.size());
        assertEquals(2,offers.get(1).lineIds().size());assertEquals(n("0.48"),offers.getLast().rewardAmount());
        assertTrue(((List<?>)result.get("unresolved")).stream().anyMatch(v->v.toString().contains("Fetch")));
        assertTrue(((List<?>)result.get("unresolved")).stream().anyMatch(v->v.toString().contains("150%")));
    }

    @Test void rewardHeadingSuppliesQuantityAndRepeatedSummaryDoesNotDuplicateReward(){
        String text="Buy 2 Get $4RR\nColgate or Kid’s Hello Toothpaste $5 each\nTotal: $10\n$5/2 Digitals\nPay $5 Get $4RR\nFinal Cost: $1 for 2 or $0.50 each\nCan Pay With WCash or RR";
        var result=ShoppingNoteInput.parse(text,null);
        @SuppressWarnings("unchecked") var items=(List<ShoppingPlan.Line>)result.get("items");
        @SuppressWarnings("unchecked") var coupons=(List<ShoppingPlan.ScenarioCoupon>)result.get("coupons");
        @SuppressWarnings("unchecked") var offers=(List<ShoppingPlan.ScenarioPromotion>)result.get("promotions");
        assertEquals(1,items.size());assertEquals(2,items.getFirst().quantity());assertEquals(n("5"),items.getFirst().sale());
        assertEquals(1,coupons.size());assertEquals(n("5"),coupons.getFirst().amount());assertEquals(2,coupons.getFirst().requiredQuantity());
        assertEquals(List.of(items.getFirst().id()),coupons.getFirst().lineIds());assertEquals(1,offers.size());
        assertEquals(n("4"),offers.getFirst().rewardAmount());assertEquals(2,offers.getFirst().requiredQuantity());
        var scenario=new ShoppingPlan.Scenario(items,coupons,offers,n("0"),n("0"),n("0"),n("0"),null,"CASH","");
        assertEquals(n("5"),scenario.estimate().get("calculatedOop"));
    }
    @Test void unmatchedBuyRewardHeadingIsPreservedWithoutGuessingAProduct(){
        var result=ShoppingNoteInput.parse("Buy 2 Get $4RR\nSome unreadable text\nColgate $5 each",null);
        assertTrue(((List<?>)result.get("items")).isEmpty());assertTrue(((List<?>)result.get("promotions")).isEmpty());
        assertTrue(((List<?>)result.get("unresolved")).contains("Buy 2 Get $4RR"));
    }

    @Test void kotexWCashDealImportsInlineBogoAndBothDigitalCoupons(){
        String text="Buy 3 Get $7WCash\nU By Kotex $7.49 Bogo 50% off\nTotal: $18.72\n$2/1 Digital\n$3/2 Digital\nPay $13.72 Get $7WCash\nFinal Cost: $6.72 for 3 or $2.24 each\nCan Pay With WCash or RR";
        var result=ShoppingNoteInput.parse(text,null);
        @SuppressWarnings("unchecked") var items=(List<ShoppingPlan.Line>)result.get("items");
        @SuppressWarnings("unchecked") var coupons=(List<ShoppingPlan.ScenarioCoupon>)result.get("coupons");
        @SuppressWarnings("unchecked") var offers=(List<ShoppingPlan.ScenarioPromotion>)result.get("promotions");
        assertEquals(1,items.size());assertEquals(3,items.getFirst().quantity());assertEquals(n("7.49"),items.getFirst().sale());
        assertEquals("BOGO_HALF",items.getFirst().priceRule().type());assertEquals(2,coupons.size());
        assertEquals(n("2"),coupons.getFirst().amount());assertEquals(1,coupons.getFirst().requiredQuantity());
        assertEquals(n("3"),coupons.getLast().amount());assertEquals(2,coupons.getLast().requiredQuantity());
        assertEquals(1,offers.size());assertEquals("WALGREENS_CASH",offers.getFirst().rewardType());assertEquals(n("7"),offers.getFirst().rewardAmount());
        var scenario=new ShoppingPlan.Scenario(items,coupons,offers,n("0"),n("0"),n("0"),n("0"),null,"CASH","");
        // Preserve existing exact half-price arithmetic; do not force the advertisement's rounded total.
        assertEquals(n("13.725"),scenario.estimate().get("calculatedOop"));
    }

    @Test void screenshotBundleKeepsProductCorrectionCouponsAndIbottaSeparate(){
        var result=ShoppingNoteInput.parse("Product: Poise pads\n= $5 off 1 ;\n$2.50 off 1\nBuy 2 for $14.00\n-clip coupons\nPay = $6.50 (OOP/WCR)\n-$2.00 Ibotta (expiring)",null);
        @SuppressWarnings("unchecked") var items=(List<ShoppingPlan.Line>)result.get("items");
        @SuppressWarnings("unchecked") var coupons=(List<ShoppingPlan.ScenarioCoupon>)result.get("coupons");
        @SuppressWarnings("unchecked") var offers=(List<ShoppingPlan.ScenarioPromotion>)result.get("promotions");
        assertEquals("Poise pads",items.getFirst().name());assertEquals(2,items.getFirst().quantity());assertEquals(n("7.00"),items.getFirst().sale());
        assertEquals(2,coupons.size());assertEquals(n("5"),coupons.getFirst().amount());assertEquals(n("2.50"),coupons.getLast().amount());
        assertEquals(1,offers.size());assertEquals("IBOTTA",offers.getFirst().rewardType());assertEquals(n("2.00"),offers.getFirst().rewardAmount());
    }

    @Test void febrezePosterJoinsProductPriceAndClipLinesWithoutCountingCouponCardTwice(){
        String text="WALGREENS\n10/04-10/10 In Store or Online\nGrab\n1 Febreze small space\n$3.49\nClip\n$2 off\nPay\n$1.49\n$2 off 1\nFebreze Small Spaces\nand Bathroom\nManufacturer's coupon\nRemove";
        var result=ShoppingNoteInput.parse(text,null);
        @SuppressWarnings("unchecked") var items=(List<ShoppingPlan.Line>)result.get("items");
        @SuppressWarnings("unchecked") var coupons=(List<ShoppingPlan.ScenarioCoupon>)result.get("coupons");
        assertEquals(1,items.size());assertEquals("Febreze small space",items.getFirst().name());assertEquals(1,items.getFirst().quantity());assertEquals(n("3.49"),items.getFirst().sale());
        assertEquals(1,coupons.size());assertEquals(n("2"),coupons.getFirst().amount());assertTrue(((List<?>)result.get("promotions")).isEmpty());
        var scenario=new ShoppingPlan.Scenario(items,coupons,List.of(),n("0"),n("0"),n("0"),n("0"),null,"CASH","");
        assertEquals(n("1.49"),scenario.estimate().get("calculatedOop"));
    }
    @Test void isolatedPayAmountIsNotTreatedAsAProductPrice(){
        var result=ShoppingNoteInput.parse("Pay\n$1.49\nClip\n$2 off",null);
        assertTrue(((List<?>)result.get("items")).isEmpty());assertTrue(((List<?>)result.get("coupons")).isEmpty());
    }

    @Test void actualFebrezeOcrRecognizesJoinedQuantityAndInterleavedPackagingText(){
        String text="10:14 4\natl 5G 657\n< (Ginioaesanionnees Soma\nea\nGrab\nNO.\nOUTLETS\n1Febreze small space\nfebreze\nareas\n$3.49\na»\nBATHROOM\nt\n2\nSy\nPay\n$1.49\no\n—Taremone\nFebreze Small Spaces\na\nwy \\a\na\nand Bathroom\nManufacturer's coupon\n\\\n)\n( Remove\n~\n4\nWalgreens deals this week.\n$2 off 1\nClip\n$2 off";
        var result=ShoppingNoteInput.parse(text,null);
        @SuppressWarnings("unchecked") var items=(List<ShoppingPlan.Line>)result.get("items");
        @SuppressWarnings("unchecked") var coupons=(List<ShoppingPlan.ScenarioCoupon>)result.get("coupons");
        assertEquals(1,items.size());assertEquals("Febreze small space",items.getFirst().name());assertEquals(n("3.49"),items.getFirst().sale());
        assertEquals(1,coupons.size());assertEquals(n("2"),coupons.getFirst().amount());
        var scenario=new ShoppingPlan.Scenario(items,coupons,List.of(),n("0"),n("0"),n("0"),n("0"),null,"CASH","");
        assertEquals(n("1.49"),scenario.estimate().get("calculatedOop"));
    }
    @Test void grabProductCannotBorrowTheAdvertisedPayPrice(){
        var result=ShoppingNoteInput.parse("Grab\n1Febreze small space\nfebreze\nPay\n$1.49\nClip\n$2 off",null);
        assertTrue(((List<?>)result.get("items")).isEmpty());
    }

    @Test void colgatePosterKeepsEachPriceBothCouponCardsAndWybReward(){
        var result=ShoppingNoteInput.parse("Product: Colgate Sensitive toothpaste\n$2 off 1\nColgate Toothpaste\n$2.50 off 1\nColgate Toothpaste\nBuy 2 for $5.00 ea\n-clip coupons\nPay = $5.50 (OOP/WCR)\n-$5.00 RR WYB2",null);
        @SuppressWarnings("unchecked") var items=(List<ShoppingPlan.Line>)result.get("items");
        @SuppressWarnings("unchecked") var coupons=(List<ShoppingPlan.ScenarioCoupon>)result.get("coupons");
        @SuppressWarnings("unchecked") var rewards=(List<ShoppingPlan.ScenarioPromotion>)result.get("promotions");
        assertEquals(n("5.00"),items.getFirst().sale());assertEquals(2,items.getFirst().quantity());assertEquals(2,coupons.size());
        assertEquals(n("2"),coupons.getFirst().amount());assertEquals(n("2.50"),coupons.getLast().amount());
        assertEquals(1,rewards.size());assertEquals("REGISTER_REWARD",rewards.getFirst().rewardType());assertEquals(n("5.00"),rewards.getFirst().rewardAmount());assertEquals(2,rewards.getFirst().requiredQuantity());
        var estimate=new ShoppingPlan.Scenario(items,coupons,rewards,n("0"),n("0"),n("0"),n("0"),null,"CASH","").estimate();
        assertEquals(n("5.50"),estimate.get("calculatedOop"));assertEquals(n("5.00"),estimate.get("potentialRewards"));
    }
    @Test void crestPosterSupportsEaWithoutSpaceAndCouponRequiredQuantityThree(){
        var result=ShoppingNoteInput.parse("$3 off 3\nProduct: Crest toothpaste\nBUY 3 FOR $5.00EA\n-DQ\nPAY $12.00\n-$6 RR\n=$6.00",null);
        @SuppressWarnings("unchecked") var items=(List<ShoppingPlan.Line>)result.get("items");
        @SuppressWarnings("unchecked") var coupons=(List<ShoppingPlan.ScenarioCoupon>)result.get("coupons");
        @SuppressWarnings("unchecked") var rewards=(List<ShoppingPlan.ScenarioPromotion>)result.get("promotions");
        assertEquals(n("5.00"),items.getFirst().sale());assertEquals(3,items.getFirst().quantity());assertEquals(1,coupons.size());assertEquals(3,coupons.getFirst().requiredQuantity());
        assertEquals(n("12.00"),new ShoppingPlan.Scenario(items,coupons,rewards,n("0"),n("0"),n("0"),n("0"),null,"CASH","").estimate().get("calculatedOop"));
        assertEquals(n("6"),rewards.getFirst().rewardAmount());
    }
    @Test void listerineStoreRewardAndSplitRegisterRewardAreOneExpectedBenefit(){
        var result=ShoppingNoteInput.parse("2 Listerine mouthwash packs $3 each\nearn 4 in store reward\nReceive\n$4 REGISTER\nREWARD",null);
        @SuppressWarnings("unchecked") var items=(List<ShoppingPlan.Line>)result.get("items");
        @SuppressWarnings("unchecked") var rewards=(List<ShoppingPlan.ScenarioPromotion>)result.get("promotions");
        assertEquals(1,rewards.size());assertEquals("REGISTER_REWARD",rewards.getFirst().rewardType());assertEquals(n("4"),rewards.getFirst().rewardAmount());
        var estimate=new ShoppingPlan.Scenario(items,List.of(),rewards,n("0"),n("0"),n("0"),n("0"),null,"CASH","").estimate();
        assertEquals(n("6"),estimate.get("calculatedOop"));assertEquals(n("4"),estimate.get("potentialRewards"));
    }

    @Test void couponAndRewardAmountsMayBeSeparatedFromTheirOcrLabels(){
        var result=ShoppingNoteInput.parse("Product: Colgate toothpaste\n$2.50 off\n1\nBuy 2 for $5.00 ea\nGet\n$5.00\nREGISTER\nREWARD\nWYB2",null);
        @SuppressWarnings("unchecked") var coupons=(List<ShoppingPlan.ScenarioCoupon>)result.get("coupons");
        @SuppressWarnings("unchecked") var rewards=(List<ShoppingPlan.ScenarioPromotion>)result.get("promotions");
        assertEquals(1,coupons.size());assertEquals(n("2.50"),coupons.getFirst().amount());
        assertEquals(1,rewards.size());assertEquals(n("5.00"),rewards.getFirst().rewardAmount());assertEquals(2,rewards.getFirst().requiredQuantity());
    }

}
