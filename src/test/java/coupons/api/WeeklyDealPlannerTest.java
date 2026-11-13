package coupons.api;

import coupons.shopping.ShoppingPlan;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeeklyDealPlannerTest {
    static BigDecimal n(String n){return new BigDecimal(n);}
    static WeeklyDealPlanner.Candidate deal(String id,String price,String earning){var item=new ShoppingPlan.Line(UUID.randomUUID(),id,null,null,n(price),1);var reward=new ShoppingPlan.ScenarioPromotion(UUID.randomUUID(),"Expected reward","REGISTER_REWARD",n(earning),1,BigDecimal.ZERO,"BEFORE_COUPONS",true,List.of(item.id()),false,true,false,null,null,null,null,null);return new WeeklyDealPlanner.Candidate(id,id,"Walgreens",new ShoppingPlan.Scenario(List.of(item),List.of(),List.of(reward),n("0"),n("0"),n("0"),n("0"),null,"CASH",""));}
    @Test void rewardEarningModePrioritizesVerifiedEarningsBeforeCash() {
        var candidates=List.of(deal("cheap","2","0"),deal("earner","8","10"));
        var result=WeeklyDealPlanner.plan(candidates,List.of(),n("0"),n("8"),"MAX_REWARDS",DAY);
        assertEquals(n("8"),result.get("cash"));assertEquals(n("10.08"),result.get("earnings"));
        assertEquals(n("0"),result.get("registerRewardsUsedValue"));
    }
    @Test void rewardModeSeparatesRebatesAndNeverFundsTheirOriginatingTransaction() {
        var base=deal("rebate","10","2");var source=base.scenario();var line=source.items().getFirst();
        var rebate=new ShoppingPlan.ScenarioPromotion(UUID.randomUUID(),"Recorded Ibotta offer","IBOTTA",n("3"),1,n("0"),"BEFORE_COUPONS",true,List.of(line.id()),false,true,false,null,null,null,null,null,true);
        var scenario=new ShoppingPlan.Scenario(source.items(),List.of(),List.of(source.promotions().getFirst(),rebate),n("0"),n("0"),n("0"),n("0"),null,"CASH","");
        var result=WeeklyDealPlanner.plan(List.of(new WeeklyDealPlanner.Candidate("rebate","Rebate offer","Walgreens",scenario)),List.of(),n("0"),n("20"),"MAX_REWARDS",DAY);
        var step=(WeeklyDealPlanner.Step)((List<?>)result.get("steps")).getFirst();
        assertEquals(n("10"),result.get("cash"));assertEquals(n("2"),step.estimate().get("expectedRegisterRewards"));assertEquals(n("3"),step.estimate().get("cashRebates"));assertEquals(n("0"),step.estimate().get("registerRewardsUsed"));assertEquals(n("5.10"),result.get("earnings"));
    }
    @Test void blankPreferenceComparesExistingStrategies() {
        var result=WeeklyDealPlanner.plan(List.of(deal("a","3","2")),List.of(),n("0"),n("8"),"",DAY);
        assertEquals(6,((List<?>)result.get("alternatives")).size());assertEquals(n("3"),result.get("cash"));
    }
    @Test void reconcilesBudgetWithDisplayedCheckoutCents(){
        var a=deal("a","0.005","0");var b=deal("b","0.50","0");
        var required=List.of(new WeeklyDealPlanner.RequiredItem(a.id(),a.scenario().items().getFirst().id(),1),new WeeklyDealPlanner.RequiredItem(b.id(),b.scenario().items().getFirst().id(),1));
        var result=WeeklyDealPlanner.plan(List.of(a,b),List.of(),n("0"),n("18.40"),"REWARDS",DAY,required,false);
        assertEquals(0,n("0.51").compareTo((BigDecimal)result.get("cash")));assertEquals(0,n("17.89").compareTo((BigDecimal)result.get("remaining")));
    }
    @Test void verifiedNetworkCardUsesRecordedAcceptanceAcrossRetailers(){
        var rule=new coupons.rewards.PaymentEligibility.Rule(UUID.randomUUID(),List.of("Walgreens","CVS","Walmart"),List.of(),"Visa",DAY.plusDays(30),DAY,true,n("50"));
        for(String store:List.of("Walgreens","CVS","Walmart"))assertEquals("Met",coupons.rewards.PaymentEligibility.check(store,"GIFT_CARD",rule,n("10"),DAY));
        assertEquals("Not Met",coupons.rewards.PaymentEligibility.check("Walgreens","GIFT_CARD",rule,n("51"),DAY));
        assertEquals("Needs Review",coupons.rewards.PaymentEligibility.check("Walgreens","GIFT_CARD",null,n("10"),DAY));
        assertEquals("Not Met",coupons.rewards.PaymentEligibility.check("Walgreens","GIFT_CARD",rule,n("10"),DAY.plusDays(31)));
        for(String type:List.of("REGISTER_REWARD","WALGREENS_CASH"))assertEquals("Not Met",coupons.rewards.PaymentEligibility.check("CVS",type,null,n("1"),DAY));
        assertEquals("Not Met",coupons.rewards.PaymentEligibility.check("Walgreens","ECB",null,n("1"),DAY));assertEquals("Not Met",coupons.rewards.PaymentEligibility.check("CVS","WALMART_CASH",null,n("1"),DAY));
    }
    @SuppressWarnings("unchecked") @Test void respectsBudgetAndDoesNotSpendSameRewardTwice(){
        var rr=new WeeklyDealPlanner.Reward("rr","Palmolive","REGISTER_REWARD","Walgreens",n("2"),LocalDate.parse("2026-11-05"),"MANUFACTURER",null,"Other",null,null);
        var result=WeeklyDealPlanner.plan(List.of(deal("a","4","3"),deal("b","4","3")),List.of(rr),n("0"),n("5"),"REWARDS",LocalDate.parse("2026-11-04"));
        assertEquals(1,((List<?>)result.get("steps")).size());assertEquals(n("2"),result.get("cash"));assertEquals(Set.of("rr"),result.get("rewardsUsed"));
    }
    @Test void expiredRewardIsNotSpendableAndNewEarningsDoNotFundPurchase(){var rr=new WeeklyDealPlanner.Reward("old","Old","REGISTER_REWARD","Walgreens",n("10"),LocalDate.parse("2026-11-01"));var result=WeeklyDealPlanner.plan(List.of(deal("a","25","100")),List.of(rr),n("0"),n("20"),"REWARDS",LocalDate.parse("2026-11-04"));assertTrue(((List<?>)result.get("steps")).isEmpty());}
    @Test void lowerOopPrecedesExpirationPriority(){var early=new WeeklyDealPlanner.Reward("early","Early","REGISTER_REWARD","Walgreens",n("2"),LocalDate.parse("2026-11-05"),"MANUFACTURER",null,"Other",null,null);var later=new WeeklyDealPlanner.Reward("later","Later","REGISTER_REWARD","Walgreens",n("4"),LocalDate.parse("2026-11-20"),"MANUFACTURER",null,"Other",null,null);var result=WeeklyDealPlanner.plan(List.of(deal("a","4","1")),List.of(later,early),n("0"),n("20"),"LOWEST_OOP",LocalDate.parse("2026-11-04"));assertEquals(Set.of("later"),result.get("rewardsUsed"));}
    @Test void bothPreferencesMinimizeOopBeforeExpectedEarnings(){var candidates=List.of(deal("cheap","2","0"),deal("earner","8","10"));var low=WeeklyDealPlanner.plan(candidates,List.of(),n("0"),n("8"),"LOWEST_OOP",LocalDate.now());var rewards=WeeklyDealPlanner.plan(candidates,List.of(),n("0"),n("8"),"REWARDS",LocalDate.now());assertEquals(n("2"),low.get("cash"));assertEquals(n("2"),rewards.get("cash"));}
    @Test void maximizeRegisterRewardsReportsUsedEarnedAndNetSeparately(){
        var expiring=new WeeklyDealPlanner.Reward("rr5","Expiring RR","REGISTER_REWARD","Walgreens",n("5"),DAY.plusDays(1),"MANUFACTURER",null,"Other",null,null);
        var result=WeeklyDealPlanner.plan(List.of(deal("replacement","5","4")),List.of(expiring),n("0"),n("5"),"MAX_REGISTER_REWARDS",DAY);
        assertEquals(Set.of("rr5"),result.get("rewardsUsed"));assertEquals(n("0"),result.get("cash"));
        assertEquals(n("5"),result.get("registerRewardsUsedValue"));assertEquals(n("4"),result.get("expectedRegisterRewards"));assertEquals(n("-1"),result.get("netRegisterRewardChange"));
    }

    static final LocalDate DAY=LocalDate.parse("2026-11-04");
    static WeeklyDealPlanner.Candidate restricted(String name,String price,String threshold,String basis,boolean wc,boolean same,String program,String product,String promotion){
        var line=new ShoppingPlan.Line(UUID.randomUUID(),name,null,null,n(price),1);
        var promo=new ShoppingPlan.ScenarioPromotion(UUID.randomUUID(),name+" reward","REGISTER_REWARD",n("10"),1,n(threshold),basis,wc,List.of(line.id()),false,true,same,program,product,name,program,promotion);
        return new WeeklyDealPlanner.Candidate(name,name,"Walgreens",new ShoppingPlan.Scenario(List.of(line),List.of(),List.of(promo),n("0.09"),n("0"),n("0"),n("0"),null,"CASH",""));
    }
    static WeeklyDealPlanner.Reward rr(String id,String amount,String product,String program,String promotion,int days){return new WeeklyDealPlanner.Reward(id,id,"REGISTER_REWARD","Walgreens",n(amount),DAY.plusDays(days),"MANUFACTURER",product,product==null?"Other":product,program,promotion);}
    static Map<String,Object> suggest(WeeklyDealPlanner.Candidate candidate,List<WeeklyDealPlanner.Reward> rewards,String wc){return WeeklyDealPlanner.plan(List.of(candidate),rewards,n(wc),n("100"),"LOWEST_OOP",DAY);}
    @SuppressWarnings("unchecked") static WeeklyDealPlanner.Step step(Map<String,Object> result){return ((List<WeeklyDealPlanner.Step>)result.get("steps")).getFirst();}
    @Test void pampersRejectsConfiguredPgAndWalgreensCashFunding(){
        var pg=restricted("Pampers","35","30","BEFORE_COUPONS",true,false,"P&G","Pampers","PG30");
        var original=pg.scenario();var booster=new ShoppingPlan.ScenarioPromotion(UUID.randomUUID(),"Spend 35 booster","WALGREENS_CASH",n("7"),0,n("35"),"BEFORE_COUPONS",false,List.of(),true);
        var candidate=new WeeklyDealPlanner.Candidate(pg.id(),pg.name(),pg.store(),new ShoppingPlan.Scenario(original.items(),List.of(),List.of(original.promotions().getFirst(),booster),original.tax(),n("0"),n("0"),n("0"),null,"CASH",""));
        var result=suggest(candidate,List.of(rr("pg10","10","Tide","P&G","PG30",1)),"10");
        assertEquals(Set.of(),result.get("rewardsUsed"));assertEquals(n("35.09"),result.get("cash"));
        assertEquals(n("17.35"),step(result).estimate().get("potentialRewards"));
    }
    @Test void schickUsesEnteredSpendBasisAndPaymentRule(){
        var before=restricted("Schick","12","10","BEFORE_COUPONS",true,false,null,null,null);
        var after=restricted("Schick","12","10","AFTER_REWARDS_USED",true,false,null,null,null);
        var reward=rr("rr4","4","Other","Other","Other",1);
        assertEquals(Set.of("rr4"),suggest(before,List.of(reward),"5").get("rewardsUsed"));
        var result=suggest(after,List.of(reward),"5");assertEquals(Set.of(),result.get("rewardsUsed"));
        assertEquals(n("10.09"),result.get("cash"));assertEquals(n("2.00"),((BigDecimal)step(result).estimate().get("walgreensCashUsed")).setScale(2));
    }
    @Test void crestOriginControlsTideAndOralBProgramEligibility(){
        var candidate=restricted("Tide and Oral-B","35","30","BEFORE_COUPONS",true,false,"P&G",null,null);
        assertEquals(Set.of(),suggest(candidate,List.of(rr("crest6","6","Crest","P&G","different",1)),"20").get("rewardsUsed"));
        assertEquals(Set.of("crest6"),suggest(candidate,List.of(rr("crest6","6","Crest","Other","different",1)),"20").get("rewardsUsed"));
        assertEquals(Set.of(),suggest(candidate,List.of(rr("crest6","6","Crest",null,null,1)),"20").get("rewardsUsed"));
    }
    @Test void sameProductOrPromotionConflictsButUnrelatedRewardIsAllowed(){
        for(var product:List.of("Colgate","Tresemmé","AXE")){
            var candidate=restricted(product,"15","0","BEFORE_COUPONS",true,true,null,product,"offer1");
            assertEquals(Set.of(),suggest(candidate,List.of(rr("same","4",product,"Other","offer2",1)),"0").get("rewardsUsed"));
            assertEquals(Set.of(),suggest(candidate,List.of(rr("same","4","Other","Other","offer1",1)),"0").get("rewardsUsed"));
            assertEquals(Set.of("other"),suggest(candidate,List.of(rr("other","4","Other","Other","offer2",1)),"0").get("rewardsUsed"));
        }
    }
    @SuppressWarnings("unchecked") @Test void unknownOriginNeedsReviewAndExcludesExpectedEarning(){
        var candidate=restricted("Colgate","15","0","BEFORE_COUPONS",true,true,null,"Colgate","offer1");
        var scenario=WeeklyDealPlanner.funded(candidate.scenario(),List.of(rr("unknown","4",null,null,null,1).payment()));
        var report=scenario.estimate("Walgreens");var promo=((List<Map<String,Object>>)report.get("promotions")).getFirst();
        assertEquals("Needs Review",promo.get("paymentRuleStatus"));assertEquals(n("0"),promo.get("potentialReward"));
        var suggested=suggest(candidate,List.of(rr("unknown","4",null,null,null,1)),"0");
        assertEquals(Set.of(),suggested.get("rewardsUsed"));assertTrue(suggested.get("reviews").toString().contains("Needs Review"));
    }
    @Test void prohibitedEarlyRewardDoesNotHideAllowedLaterReward(){
        var candidate=restricted("Pampers","35","30","BEFORE_COUPONS",false,false,"P&G",null,null);
        assertEquals(Set.of("other"),suggest(candidate,List.of(rr("pg","10","Tide","P&G","PG30",1),rr("other","6","Colgate","Other","other",2)),"0").get("rewardsUsed"));
    }
    @Test void expirationBreaksTiesBetweenEligibleFundingChoices(){
        var result=suggest(deal("a","4","1"),List.of(rr("later","4",null,null,null,5),rr("early","4",null,null,null,1)),"0");
        assertEquals(Set.of("early"),result.get("rewardsUsed"));
    }
    @Test void fundingCannotExceedDueAndCheckoutEquationReconciles(){
        var candidate=restricted("Pampers","19","0","BEFORE_COUPONS",true,false,null,null,null);
        var result=suggest(candidate,List.of(rr("rr","10",null,null,null,1)),"10");var report=step(result).estimate();
        assertEquals(0,((BigDecimal)report.get("overFunding")).signum());
        var equation=((BigDecimal)report.get("merchandiseAfterCoupons")).subtract((BigDecimal)report.get("registerRewardsUsed")).add((BigDecimal)report.get("tax")).subtract((BigDecimal)report.get("walgreensCashUsed"));
        assertEquals(0,equation.compareTo((BigDecimal)report.get("calculatedOop")));assertTrue(equation.signum()>=0);
    }
    @Test void enteredRedemptionIncrementAndLimitAreRespected(){
        var candidate=deal("limited","19","1");var s=candidate.scenario();
        var limited=new ShoppingPlan.Scenario(s.items(),s.coupons(),s.promotions(),s.tax(),s.walgreensCashUsed(),s.registerRewardsUsed(),s.giftCardUsed(),null,"CASH","",null,null,null,n("5"),n("7"));
        var result=suggest(new WeeklyDealPlanner.Candidate("limited","limited","Walgreens",limited),List.of(),"20");
        assertEquals(0,n("5").compareTo((BigDecimal)step(result).estimate().get("walgreensCashUsed")));
    }
    @Test void unmetSpendDoesNotBecomeAnOptimizedReward(){
        var candidate=restricted("Schick","9","10","BEFORE_COUPONS",true,false,null,null,null);
        assertTrue(((List<?>)suggest(candidate,List.of(),"5").get("steps")).isEmpty());
    }
    static ShoppingPlan.Line product(String name,String price,int quantity){return new ShoppingPlan.Line(UUID.randomUUID(),name,null,n(price),n(price),quantity,0);}
    static ShoppingPlan.ScenarioCoupon off(ShoppingPlan.Line line,String value,int quantity){return new ShoppingPlan.ScenarioCoupon(UUID.randomUUID(),"Entered coupon",n(value),quantity,List.of(line.id()),null);}
    static ShoppingPlan.ScenarioPromotion offer(String name,String value,int quantity,String spend,String basis,boolean wc,List<UUID> ids,boolean same,String program,String product,String promotion){return new ShoppingPlan.ScenarioPromotion(UUID.randomUUID(),name,"REGISTER_REWARD",n(value),quantity,n(spend),basis,wc,ids,ids.isEmpty(),true,same,program,product,null,program,promotion);}
    static ShoppingPlan.Scenario entered(List<ShoppingPlan.Line> lines,List<ShoppingPlan.ScenarioCoupon> coupons,List<ShoppingPlan.ScenarioPromotion> promos,String tax){return new ShoppingPlan.Scenario(lines,coupons,promos,n(tax),n("0"),n("0"),n("0"),null,"CASH","");}
    @SuppressWarnings("unchecked") static Map<String,Object> promotion(Map<String,Object> report,String name){return ((List<Map<String,Object>>)report.get("promotions")).stream().filter(p->name.equals(p.get("description"))).findFirst().orElseThrow();}
    @Test void reproducesSuppliedPampersFundingAndRejectsBothPaymentConflicts(){
        var pampers=product("Pampers Diapers Unscented","11.00",3);var water=product("Nice! water","3.99",1);
        var buy=offer("Buy 3 Pampers","10",3,"0","BEFORE_COUPONS",true,List.of(pampers.id()),false,null,null,null);
        var pg=offer("PG30","10",0,"30","BEFORE_COUPONS",true,List.of(),false,"P&G",null,null);
        var booster=new ShoppingPlan.ScenarioPromotion(UUID.randomUUID(),"Spend35 WC","WALGREENS_CASH",n("7"),0,n("35"),"BEFORE_COUPONS",false,List.of(),true);
        var s=entered(List.of(pampers,water),List.of(off(pampers,"3",2),off(water,"2.50",1)),List.of(buy,pg,booster),"2.60");
        var payments=new ArrayList<ShoppingPlan.ScenarioPayment>();for(var value:List.of("6","5","10","3"))payments.add(rr("rr"+value,value,"Other",value.equals("10")?"P&G":"Other","other",1).payment());
        payments.add(new ShoppingPlan.ScenarioPayment(UUID.randomUUID(),"WALGREENS_CASH",n("10"),"Entered funding"));
        var report=WeeklyDealPlanner.funded(s,payments).estimate();
        assertEquals(n("31.49"),report.get("merchandiseAfterCoupons"));assertEquals(n("0.09"),report.get("calculatedOop"));
        assertEquals("Not Met",promotion(report,"PG30").get("paymentRuleStatus"));assertEquals("Not Met",promotion(report,"Spend35 WC").get("paymentRuleStatus"));
        assertEquals(n("0"),promotion(report,"PG30").get("potentialReward"));assertEquals(n("0"),promotion(report,"Spend35 WC").get("potentialReward"));
        var suggested=suggest(new WeeklyDealPlanner.Candidate("pampers","Pampers","Walgreens",s),List.of(rr("pg10","10","Tide","P&G","PG30",1),rr("other6","6","Other","Other","other",2)),"10");
        assertEquals(Set.of("other6"),suggested.get("rewardsUsed"));assertEquals(n("0"),step(suggested).estimate().get("walgreensCashUsed"));
    }
    @Test void reproducesSuppliedSchickArithmeticAndChecksBeforeAndAfterCouponThresholds(){
        var gel=product("SkinMate Sgel Sens Skin","4.99",1);var hydro=product("Hydro Silk 5 Ultra Sens Disp 2s","8.99",1);
        for(var basis:List.of("BEFORE_COUPONS","AFTER_COUPONS")){
            var promo=offer("Schick Spend10G5RR","5",0,"10",basis,true,List.of(),false,null,null,null);
            var s=entered(List.of(gel,hydro),List.of(off(gel,"2.50",1),off(hydro,"3",1)),List.of(promo),"0.70");
            var report=WeeklyDealPlanner.funded(s,List.of(rr("rr4","4",null,null,null,1).payment(),new ShoppingPlan.ScenarioPayment(UUID.randomUUID(),"WALGREENS_CASH",n("5"),"Entered funding"))).estimate();
            assertEquals(n("8.48"),report.get("merchandiseAfterCoupons"));assertEquals(n("0.18"),report.get("calculatedOop"));
            assertEquals(basis.equals("BEFORE_COUPONS"),promotion(report,"Schick Spend10G5RR").get("eligible"));
        }
    }
    @Test void reproducesSuppliedTideOralBArithmeticWithoutGuessingCrestOrigin(){
        var tide=product("Tide","21.99",1);var oral=product("Oral B","6.99",3);
        var s=entered(List.of(tide,oral),List.of(off(tide,"6",1),off(tide,"4",1),off(oral,"5.90",1),off(oral,"3",3)),List.of(offer("Oral B","6",3,"0","BEFORE_COUPONS",true,List.of(oral.id()),false,null,null,null),offer("PG30","10",0,"30","BEFORE_COUPONS",true,List.of(),false,"P&G",null,null)),"1.98");
        for(var program:Arrays.asList("P&G","Other",null)){
            var report=WeeklyDealPlanner.funded(s,List.of(rr("crest6","6","Crest",program,null,1).payment(),new ShoppingPlan.ScenarioPayment(UUID.randomUUID(),"WALGREENS_CASH",n("20"),"Entered funding"))).estimate();
            assertEquals(n("24.06"),report.get("merchandiseAfterCoupons"));assertEquals(n("0.04"),report.get("calculatedOop"));
            assertEquals(program==null?"Needs Review":program.equals("P&G")?"Not Met":"Met",promotion(report,"PG30").get("paymentRuleStatus"));
        }
    }
    @Test void rewardPreferenceMaximizesQualifyingEarningsAfterMinimizingOop(){
        var result=WeeklyDealPlanner.plan(List.of(deal("low","4","1"),deal("high","4","10")),List.of(),n("0"),n("4"),"REWARDS",DAY);
        assertEquals("high",step(result).id());
    }
    @Test void combinedRewardsCannotExceedMerchandiseEvenWhenTaxWouldAbsorbThem(){
        var s=entered(List.of(product("Item","9",1)),List.of(),List.of(),"2");
        var result=suggest(new WeeklyDealPlanner.Candidate("tax","tax","Walgreens",s),List.of(rr("six","6",null,null,null,1),rr("four","4",null,null,null,2)),"0");
        assertEquals(Set.of("six"),result.get("rewardsUsed"));
    }

    @Test void unknownRrClassificationNeedsReviewForStoreRrSpendBasis(){
        var candidate=restricted("Schick","12","10","BEFORE_COUPONS_AFTER_STORE_RR",true,false,null,null,null);
        var unknown=new WeeklyDealPlanner.Reward("unknown","Unknown type","REGISTER_REWARD","Walgreens",n("4"),DAY.plusDays(1));
        var report=WeeklyDealPlanner.funded(candidate.scenario(),List.of(unknown.payment())).estimate();
        assertEquals("Needs Review",promotion(report,"Schick reward").get("paymentRuleStatus"));
        assertEquals(Set.of(),suggest(candidate,List.of(unknown),"0").get("rewardsUsed"));
    }
    @Test void suppliedCashDenominationsAreEnforcedInBothCheckoutAndSuggestions(){
        var candidate=restricted("Tide and Oral-B","24.06","0","BEFORE_COUPONS",true,false,null,null,null);
        var s=candidate.scenario();
        for(var value:List.of("1","2","3","4","5","10","20")){
            var report=WeeklyDealPlanner.funded(s,List.of(new ShoppingPlan.ScenarioPayment(UUID.randomUUID(),"WALGREENS_CASH",n(value),""))).estimate("Walgreens");
            assertEquals("Met",report.get("fundingRuleStatus"),value);
        }
        for(var value:List.of("6.09","20.04","6","9","15")){
            var report=WeeklyDealPlanner.funded(s,List.of(new ShoppingPlan.ScenarioPayment(UUID.randomUUID(),"WALGREENS_CASH",n(value),""))).estimate("Walgreens");
            assertEquals("Not Met",report.get("fundingRuleStatus"),value);assertFalse((boolean)promotion(report,"Tide and Oral-B reward").get("eligible"));
        }
        var suggested=suggest(candidate,List.of(),"20.04");assertEquals(0,n("20").compareTo((BigDecimal)step(suggested).estimate().get("walgreensCashUsed")));
        var large=restricted("large","70","0","BEFORE_COUPONS",true,false,null,null,null);
        assertEquals("Met",WeeklyDealPlanner.funded(large.scenario(),List.of(new ShoppingPlan.ScenarioPayment(UUID.randomUUID(),"WALGREENS_CASH",n("50"),""))).estimate("Walgreens").get("fundingRuleStatus"));
    }
    @Test void pampersFourPhysicalItemsCannotAcceptFiveManufacturerRewardsAndExistingCoupons(){
        var pampers=product("Pampers","11",3);var water=product("Water","3.99",1);
        var coupons=List.of(off(pampers,"3",2),new ShoppingPlan.ScenarioCoupon(UUID.randomUUID(),"Nice store promo",n("2.50"),1,List.of(water.id()),null,"STORE_COUPON"));
        var s=entered(List.of(pampers,water),coupons,List.of(offer("Pampers","10",3,"0","BEFORE_COUPONS",true,List.of(pampers.id()),false,null,null,null)),"2.60");
        var rewards=List.of(rr("a","1","Other","Other","Other",1),rr("b","1","Other","Other","Other",2),rr("c","1","Other","Other","Other",3),rr("d","1","Other","Other","Other",4),rr("e","1","Other","Other","Other",5));
        var report=WeeklyDealPlanner.funded(s,rewards.stream().map(WeeklyDealPlanner.Reward::payment).toList()).estimate("Walgreens");
        assertEquals("Not Met",report.get("fundingRuleStatus"));assertEquals("Not Met",promotion(report,"Pampers").get("paymentRuleStatus"));assertEquals(0,((BigDecimal)report.get("potentialRewards")).signum());
        var result=suggest(new WeeklyDealPlanner.Candidate("pampers","Pampers","Walgreens",s),rewards,"0");assertEquals(3,((Set<?>)result.get("rewardsUsed")).size());
    }
    @Test void couponAllocationUsesLinkedItemsAndCanReassignOverlappingCoupons(){
        var a=product("A","10",1);var b=product("B","10",1);var c=product("C","10",1);
        var flexible=new ShoppingPlan.ScenarioCoupon(UUID.randomUUID(),"Flexible",n("1"),1,List.of(a.id(),b.id()),null);
        var narrow=off(a,"1",1);
        var s=entered(List.of(a,b,c),List.of(flexible,narrow),List.of(),"0");
        assertEquals("Met",WeeklyDealPlanner.funded(s,List.of(rr("one","1",null,null,null,1).payment())).estimate("Walgreens").get("fundingRuleStatus"));
        var overlapping=entered(List.of(a,b,c),List.of(off(a,"1",1),off(a,"1",1)),List.of(),"0");
        assertEquals("Not Met",overlapping.estimate("Walgreens").get("fundingRuleStatus"));
    }
    @Test void storeRewardsDoNotConsumeManufacturerItemSlotsButUnknownTypeNeedsReview(){
        var a=product("A","10",1);var s=entered(List.of(a),List.of(off(a,"1",1)),List.of(),"0");
        var store=new WeeklyDealPlanner.Reward("store","store RR","REGISTER_REWARD","Walgreens",n("1"),DAY.plusDays(1),"STORE",null,null,null,null);
        assertEquals("Met",WeeklyDealPlanner.funded(s,List.of(store.payment())).estimate("Walgreens").get("fundingRuleStatus"));
        var unknown=new WeeklyDealPlanner.Reward("unknown","unknown","REGISTER_REWARD","Walgreens",n("1"),null);
        assertEquals("Needs Review",WeeklyDealPlanner.funded(s,List.of(unknown.payment())).estimate("Walgreens").get("fundingRuleStatus"));
    }
    @Test void unreviewedRrRulesRemainBlockedAndExplicitWalgreensCashPermissionIsRespected(){
        var a=product("Tide","35",1);
        var legacyRR=new ShoppingPlan.ScenarioPromotion(UUID.randomUUID(),"PG30","REGISTER_REWARD",n("10"),0,n("30"),"BEFORE_COUPONS",true,List.of(a.id()));
        var legacyBooster=new ShoppingPlan.ScenarioPromotion(UUID.randomUUID(),"Spend35","WALGREENS_CASH",n("7"),0,n("35"),"BEFORE_COUPONS",true,List.of(a.id()));
        var s=entered(List.of(a),List.of(),List.of(legacyRR,legacyBooster),"0");
        var rrReport=WeeklyDealPlanner.funded(s,List.of(rr("PG","10",null,"P&G",null,1).payment())).estimate("Walgreens");
        assertEquals("Needs Review",promotion(rrReport,"PG30").get("paymentRuleStatus"));assertEquals(n("0"),promotion(rrReport,"PG30").get("potentialReward"));
        var wcReport=WeeklyDealPlanner.funded(s,List.of(new ShoppingPlan.ScenarioPayment(UUID.randomUUID(),"WALGREENS_CASH",n("10"),""))).estimate("Walgreens");
        assertEquals("Met",promotion(wcReport,"Spend35").get("paymentRuleStatus"));assertEquals(n("7"),promotion(wcReport,"Spend35").get("potentialReward"));
        var suggested=suggest(new WeeklyDealPlanner.Candidate("legacy","Legacy","Walgreens",s),List.of(),"10");
        assertFalse(suggested.get("reviews").toString().contains("Walgreens Cash"));
    }
    @Test void taxCannotIncreaseRewardRedemptionOrQualifyingSpend(){
        var candidate=restricted("Tax","9","10","BEFORE_COUPONS",true,false,null,null,null);var s=candidate.scenario();
        var tax=entered(s.items(),List.of(),s.promotions(),"10");
        var report=WeeklyDealPlanner.funded(tax,List.of(new ShoppingPlan.ScenarioPayment(UUID.randomUUID(),"WALGREENS_CASH",n("10"),""))).estimate("Walgreens");
        assertEquals("Not Met",report.get("fundingRuleStatus"));assertFalse((boolean)promotion(report,"Tax reward").get("spendMet"));
        assertEquals(n("0"),promotion(report,"Tax reward").get("potentialReward"));
    }

    @SuppressWarnings("unchecked") @Test void reviewWarningsIdentifyOriginFieldAndExactPromotion(){
        var candidate=restricted("Tide","35","30","BEFORE_COUPONS",true,false,"P&G",null,null);
        var result=suggest(candidate,List.of(rr("crest","6","Crest",null,null,1)),"0");
        var reviews=(List<Map<String,String>>)result.get("reviews");assertEquals("payment.originProgram",reviews.getFirst().get("field"));
        assertEquals(candidate.scenario().promotions().getFirst().id().toString(),reviews.getFirst().get("promotionId"));
    }
    @Test void emptySuggestionsDistinguishBudgetFromEligibility(){
        assertEquals("BUDGET",WeeklyDealPlanner.plan(List.of(deal("costly","30","1")),List.of(),n("0"),n("1"),"LOWEST_OOP",DAY).get("emptyReason"));
        var invalid=restricted("Schick","9","10","BEFORE_COUPONS",true,false,null,null,null);
        assertEquals("ELIGIBILITY",suggest(invalid,List.of(),"0").get("emptyReason"));
    }

    @SuppressWarnings("unchecked") @Test void automaticFundingKeepsUnknownOptionOutAndUsesTwoDistinctRewardsAcrossTransactions(){
        var first=deal("first","10","1");var second=deal("second","10","1");
        var known=List.of(rr("first6","6","Other","Other","other",1),rr("second6","6","Other","Other","other",2),new WeeklyDealPlanner.Reward("unknown","Unverified","REGISTER_REWARD","Walgreens",n("8"),null));
        var result=WeeklyDealPlanner.plan(List.of(first,second),known,n("8"),n("0"),"LOWEST_OOP",DAY);
        var steps=(List<WeeklyDealPlanner.Step>)result.get("steps");assertEquals(2,steps.size());assertEquals(Set.of("first6","second6"),result.get("rewardsUsed"));
        assertEquals(0,((BigDecimal)result.get("cash")).signum());assertEquals(0,n("8").compareTo(steps.stream().map(step->(BigDecimal)step.estimate().get("walgreensCashUsed")).reduce(BigDecimal.ZERO,BigDecimal::add)));
    }
    @Test void automaticCouponsCanLeaveOneUnusedToSatisfyRecordedAfterCouponSpend(){
        var item=product("Item","12",1);var coupon=off(item,"3",1);
        var promo=offer("Spend10","5",0,"10","AFTER_COUPONS",true,List.of(),false,null,null,null);
        var scenario=entered(List.of(item),List.of(coupon),List.of(promo),"0");
        var result=suggest(new WeeklyDealPlanner.Candidate("coupons","coupons","Walgreens",scenario),List.of(),"0");
        assertTrue(step(result).scenario().coupons().isEmpty());assertEquals(0,n("12").compareTo((BigDecimal)result.get("cash")));
    }

    @Test void sameBrandCannotFundAnRrEvenWhenOptionalRollingFlagIsOff(){
        for(String brand:List.of("Colgate","Tresemmé","AXE","P&G")){
            var candidate=restricted(brand,"15","0","BEFORE_COUPONS",true,false,null,"Different product","Different promotion");
            var payment=new WeeklyDealPlanner.Reward("matching","Recorded RR","REGISTER_REWARD","Walgreens",n("4"),DAY.plusDays(1),"MANUFACTURER","Other product",brand.equals("P&G")?"PG":brand,"Other program","Other promotion");
            var report=WeeklyDealPlanner.funded(candidate.scenario(),List.of(payment.payment())).estimate("Walgreens");
            assertEquals("Not Met",promotion(report,brand+" reward").get("paymentRuleStatus"));assertEquals(n("0"),promotion(report,brand+" reward").get("potentialReward"));
            assertEquals(Set.of(),suggest(candidate,List.of(payment),"0").get("rewardsUsed"));
        }
    }
    @Test void knownUnrelatedBrandRemainsAllowedAndMissingBrandNeedsReview(){
        var candidate=restricted("Colgate","15","0","BEFORE_COUPONS",true,false,null,"Colgate","Colgate offer");
        var unrelated=new WeeklyDealPlanner.Reward("other","Other","REGISTER_REWARD","Walgreens",n("4"),DAY.plusDays(1),"MANUFACTURER",null,"Softsoap",null,null);
        assertEquals(Set.of("other"),suggest(candidate,List.of(unrelated),"0").get("rewardsUsed"));
        var unknown=new WeeklyDealPlanner.Reward("unknown","Unknown","REGISTER_REWARD","Walgreens",n("4"),DAY.plusDays(1),"MANUFACTURER",null,null,null,null);
        var report=WeeklyDealPlanner.funded(candidate.scenario(),List.of(unknown.payment())).estimate("Walgreens");
        assertEquals("Needs Review",promotion(report,"Colgate reward").get("paymentRuleStatus"));assertEquals(n("0"),promotion(report,"Colgate reward").get("potentialReward"));
    }

    @Test void walgreensCashCheckboxAllowsFundingOnlyWhenRecordedPaymentSensitiveSpendStillQualifies(){
        var item=product("Qualifying products","40",1);
        for(boolean allowed:List.of(true,false)){
            var booster=new ShoppingPlan.ScenarioPromotion(UUID.randomUUID(),"Spend35 Earn7","WALGREENS_CASH",n("7"),0,n("35"),"AFTER_REWARDS_USED",allowed,List.of(item.id()));
            var scenario=entered(List.of(item),List.of(),List.of(booster),"0");
            var candidate=new WeeklyDealPlanner.Candidate("booster","booster","Walgreens",scenario);
            var selected=suggest(candidate,List.of(),"10");
            assertEquals(0,n(allowed?"5":"0").compareTo((BigDecimal)step(selected).estimate().get("walgreensCashUsed")));
            var report=WeeklyDealPlanner.funded(scenario,List.of(new ShoppingPlan.ScenarioPayment(UUID.randomUUID(),"WALGREENS_CASH",n("10"),""))).estimate("Walgreens");
            assertEquals(allowed?"Met":"Not Met",promotion(report,"Spend35 Earn7").get("paymentRuleStatus"));
            assertFalse((boolean)promotion(report,"Spend35 Earn7").get("spendMet"));assertEquals(n("0"),promotion(report,"Spend35 Earn7").get("potentialReward"));
        }
    }
    static WeeklyDealPlanner.RequiredItem must(WeeklyDealPlanner.Candidate candidate,int quantity){return new WeeklyDealPlanner.RequiredItem(candidate.id(),candidate.scenario().items().getFirst().id(),quantity);}
    static Map<String,Object> selected(List<WeeklyDealPlanner.Candidate> candidates,List<WeeklyDealPlanner.RequiredItem> required,boolean optional,List<WeeklyDealPlanner.Reward> rewards,String wc,String budget){return WeeklyDealPlanner.plan(candidates,rewards,n(wc),n(budget),"REWARDS",DAY,required,optional);}
    @Test void oneMustBuyIsKeptAtExactQuantityWithOriginalProductId(){
        var candidate=deal("must","4","1");var result=selected(List.of(candidate),List.of(must(candidate,3)),false,List.of(),"0","20");
        assertEquals(true,result.get("requirementsMet"));assertEquals(candidate.scenario().items().getFirst().id(),step(result).scenario().items().getFirst().id());assertEquals(3,step(result).scenario().items().getFirst().quantity());assertEquals(1,candidate.scenario().items().getFirst().quantity());assertEquals(0,n("12").compareTo((BigDecimal)result.get("cash")));
    }
    @SuppressWarnings("unchecked") @Test void allMustBuysShareIndividualRewardsAndWalletWithoutDoubleSpending(){
        var first=deal("first","10","1");var second=deal("second","10","1");
        var result=selected(List.of(first,second),List.of(must(first,1),must(second,1)),false,List.of(rr("one6","6","Other","Other","other",1),rr("two6","6","Other","Other","other",2)),"8","0");
        assertEquals(true,result.get("requirementsMet"));assertEquals(true,result.get("affordable"));assertEquals(Set.of("one6","two6"),result.get("rewardsUsed"));assertEquals(2,((List<WeeklyDealPlanner.Step>)result.get("steps")).size());assertEquals(0,((BigDecimal)result.get("cash")).signum());
    }
    @SuppressWarnings("unchecked") @Test void optionalDealsCanBeAddedOnlyAfterMustBuysAreSatisfied(){
        var must=deal("must","4","1");var optional=deal("optional","4","10");var required=List.of(must(must,1));
        var only=selected(List.of(optional,must),required,false,List.of(),"8","0");assertEquals(1,((List<?>)only.get("steps")).size());assertEquals("must",step(only).id());
        var extra=selected(List.of(optional,must),required,true,List.of(),"8","0");var steps=(List<WeeklyDealPlanner.Step>)extra.get("steps");assertEquals(List.of("must","optional"),steps.stream().map(WeeklyDealPlanner.Step::id).toList());
    }
    @Test void overBudgetMustBuyIsRetainedWithExactAdditionalCashAndNotReplacedByCheapDeal(){
        var required=deal("required","25","1");var cheap=deal("cheap","1","100");
        var result=selected(List.of(cheap,required),List.of(must(required,2)),true,List.of(),"0","10");
        assertEquals("required",step(result).id());assertEquals(2,step(result).scenario().items().getFirst().quantity());assertEquals(false,result.get("affordable"));assertEquals(true,result.get("requirementsMet"));assertEquals(0,n("50").compareTo((BigDecimal)result.get("cash")));assertEquals(0,n("40").compareTo((BigDecimal)result.get("additionalCashNeeded")));
    }
    @Test void blockedRequiredItemKeepsHumanReadableNameAndNoUuidInIssue(){
        var id=UUID.randomUUID();var blocked=new WeeklyDealPlanner.Candidate("plan","Edited trip","Walgreens",null);
        var result=selected(List.of(blocked),List.of(new WeeklyDealPlanner.RequiredItem("plan",id,2,"Jiffy Corn Muffin Mix")),false,List.of(),"0","20");assertEquals(false,result.get("requirementsMet"));assertTrue(result.get("issues").toString().contains("Jiffy Corn Muffin Mix"));assertFalse(result.get("issues").toString().contains(id.toString()));assertNull(result.get("cash"));
    }
    @Test void priceLabelsCannotBecomeVerifiedProducts(){
        var candidate=deal("Regular $4.99 Sale :","3.49","0");var item=candidate.scenario().items().getFirst();
        var result=selected(List.of(candidate),List.of(new WeeklyDealPlanner.RequiredItem(candidate.id(),item.id(),1,"Product name needs correction")),false,List.of(),"0","20");assertEquals(false,result.get("requirementsMet"));assertTrue(result.get("issues").toString().contains("price label"));assertNull(result.get("cash"));
    }
    @Test void missingMustBuyProductAndMissingPricesAreReportedWithoutSubstitution(){
        var available=deal("available","2","10");var missingId=UUID.randomUUID();
        var result=selected(List.of(available),List.of(new WeeklyDealPlanner.RequiredItem(available.id(),missingId,2)),true,List.of(),"0","20");assertEquals(false,result.get("requirementsMet"));assertNull(result.get("cash"));assertTrue(((List<?>)result.get("steps")).isEmpty());assertEquals(1,((List<?>)result.get("requiredItems")).size());
        var noPrice=new WeeklyDealPlanner.Candidate("unpriced","Unpriced","Walgreens",null);
        var unpriced=selected(List.of(noPrice),List.of(new WeeklyDealPlanner.RequiredItem("unpriced",missingId,1)),false,List.of(),"0","20");assertEquals(false,unpriced.get("requirementsMet"));assertTrue(unpriced.get("issues").toString().contains("pricing"));
    }
    @Test void requiredQuantityUsesAutomaticRegisterRewardAndValidWalgreensCashAmounts(){
        var item=deal("required","4","1");var result=selected(List.of(item),List.of(must(item,3)),false,List.of(rr("other4","4","Other","Other","other",1)),"8","3");
        assertEquals(Set.of("other4"),result.get("rewardsUsed"));assertEquals(0,n("5").compareTo((BigDecimal)step(result).estimate().get("walgreensCashUsed")));assertEquals(0,n("3").compareTo((BigDecimal)result.get("cash")));assertEquals(true,result.get("affordable"));
    }
    @Test void requiredPurchaseIsAllowedWithoutInventingUnmetCouponsOrPromotions(){
        var line=product("Item","10",1);var coupon=off(line,"3",2);var promo=offer("Buy3","5",3,"0","BEFORE_COUPONS",true,List.of(line.id()),false,null,null,null);
        var item=new WeeklyDealPlanner.Candidate("item","item","Walgreens",entered(List.of(line),List.of(coupon),List.of(promo),"0"));
        var result=selected(List.of(item),List.of(must(item,1)),false,List.of(),"0","20");assertEquals(true,result.get("requirementsMet"));assertEquals(0,n("10").compareTo((BigDecimal)result.get("cash")));assertEquals(n("0"),promotion(step(result).estimate(),"Buy3").get("potentialReward"));assertEquals(0,((BigDecimal)step(result).estimate().get("couponTotal")).signum());
    }
    @Test void changingBasketWithFixedTaxReportsUncertaintyInsteadOfScalingTax(){
        var line=product("Item","10",1);var item=new WeeklyDealPlanner.Candidate("item","item","Walgreens",entered(List.of(line),List.of(),List.of(),"0.80"));
        var result=selected(List.of(item),List.of(must(item,2)),false,List.of(),"0","20");assertEquals(false,result.get("requirementsMet"));assertNull(result.get("additionalCashNeeded"));assertTrue(result.get("issues").toString().contains("tax"));assertEquals(1,((List<?>)result.get("requiredItems")).size());
    }

    @Test void walgreensCannotUseEcbEvenWhenSourceWasIncorrectlyLabeledWalgreens(){
        var candidate=deal("walgreens","10","1");var ecb=new WeeklyDealPlanner.Reward("ecb","CVS ExtraBucks","ECB","Walgreens",n("5"),DAY.plusDays(1));
        var result=suggest(candidate,List.of(ecb),"0");assertEquals(Set.of(),result.get("rewardsUsed"));
        for(String type:List.of("ECB","WALMART_CASH")){
            var report=WeeklyDealPlanner.funded(candidate.scenario(),List.of(new ShoppingPlan.ScenarioPayment(UUID.randomUUID(),type,n("5"),""))).estimate("Walgreens");assertEquals("Not Met",report.get("fundingRuleStatus"));
        }
    }
    @Test void expiringFirstUsesEarliestEligibleRewardButNeverMakesAnAffordableMustBuyUnaffordable(){
        var candidate=deal("item","4","1");var early=rr("early","2","Other","Other","other",1);var later=rr("later","4","Other","Other","other",5);
        var rescue=WeeklyDealPlanner.plan(List.of(candidate),List.of(later,early),n("0"),n("3"),"EXPIRING_FIRST",DAY);assertEquals(Set.of("early"),rescue.get("rewardsUsed"));assertEquals(0,n("2").compareTo((BigDecimal)rescue.get("cash")));
        var tight=WeeklyDealPlanner.plan(List.of(candidate),List.of(later,early),n("0"),n("1"),"EXPIRING_FIRST",DAY,List.of(must(candidate,1)),false);assertEquals(Set.of("later"),tight.get("rewardsUsed"));assertEquals(true,tight.get("affordable"));
    }
    @Test void expiringFirstSkipsProhibitedEarlyRewards(){
        var candidate=restricted("Pampers","35","30","BEFORE_COUPONS",false,false,"P&G",null,null);
        var result=WeeklyDealPlanner.plan(List.of(candidate),List.of(rr("pg","10","Tide","P&G","PG30",1),rr("other","6","Colgate","Other","other",2)),n("0"),n("100"),"EXPIRING_FIRST",DAY);assertEquals(Set.of("other"),result.get("rewardsUsed"));
    }

    static WeeklyDealPlanner.Candidate simple(String name,String store,String price,List<ShoppingPlan.ScenarioPromotion> promotions){
        var line=new ShoppingPlan.Line(UUID.nameUUIDFromBytes(name.getBytes()),name,name,null,n(price),1);
        return new WeeklyDealPlanner.Candidate(name,name,store,new ShoppingPlan.Scenario(List.of(line),List.of(),promotions,n("0"),n("0"),n("0"),n("0"),null,"CASH",""));
    }
    static ShoppingPlan.ScenarioPromotion rollingPromotion(String type,Boolean allowed){return new ShoppingPlan.ScenarioPromotion(UUID.randomUUID(),"Colgate",type,n("5"),0,n("0"),"BEFORE_COUPONS",true,List.of(),true,true,false,null,"Colgate","Colgate",null,"colgate-origin",true,"COUNTS",allowed,"MANUFACTURER",true,true);}
    static List<WeeklyDealPlanner.RequiredItem> require(WeeklyDealPlanner.Candidate... deals){return Arrays.stream(deals).map(c->new WeeklyDealPlanner.RequiredItem(c.id(),c.scenario().items().getFirst().id(),1)).toList();}
    @Test void rollsOnlyVerifiedRewardsIntoLaterTransactionsForEachRetailer(){
        for(var entry:Map.of("Walgreens","REGISTER_REWARD","CVS","ECB","Walmart","WALMART_CASH").entrySet()){
            var a=simple("earn",entry.getKey(),"5",List.of(rollingPromotion(entry.getValue(),true)));var b=simple("spend",entry.getKey(),"5",List.of());
            var disabled=WeeklyDealPlanner.plan(List.of(a,b),List.of(),n("0"),n("5"),"LOWEST_OOP",DAY,require(a,b),false,new WeeklyDealPlanner.Options(false,false,false,null));
            var enabled=WeeklyDealPlanner.plan(List.of(a,b),List.of(),n("0"),n("5"),"LOWEST_OOP",DAY,require(a,b),false,new WeeklyDealPlanner.Options(true,false,false,null));
            assertEquals(n("10"),disabled.get("cash"));assertEquals(0,n("5").compareTo((BigDecimal)enabled.get("cash")));
            @SuppressWarnings("unchecked") var steps=(List<WeeklyDealPlanner.Step>)enabled.get("steps");assertTrue(steps.getFirst().scenario().payments().isEmpty());assertEquals(entry.getValue(),steps.get(1).scenario().payments().getFirst().type());assertTrue(steps.get(1).scenario().payments().getFirst().notes().contains("Expected"));
        }
    }
    @Test void rollsWalgreensCashSeparatelyAndDoesNotGuessMissingRollingPermission(){
        var a=simple("earn","Walgreens","5",List.of(rollingPromotion("WALGREENS_CASH",true)));var b=simple("spend","Walgreens","5",List.of());
        var result=WeeklyDealPlanner.plan(List.of(a,b),List.of(),n("0"),n("5"),"LOWEST_OOP",DAY,require(a,b),false,new WeeklyDealPlanner.Options(true,false,false,null));assertEquals(n("5"),result.get("cash"));
        a=simple("earn","Walgreens","5",List.of(rollingPromotion("REGISTER_REWARD",null)));
        result=WeeklyDealPlanner.plan(List.of(a,b),List.of(),n("0"),n("5"),"LOWEST_OOP",DAY,require(a,b),false,new WeeklyDealPlanner.Options(true,false,false,null));assertEquals(n("10"),result.get("cash"));
    }
    @Test void sameBrandExpectedRegisterRewardCannotRollIntoTheMatchingReward(){
        var a=simple("first","Walgreens","5",List.of(rollingPromotion("REGISTER_REWARD",true)));var b=simple("second","Walgreens","5",List.of(rollingPromotion("REGISTER_REWARD",true)));
        var result=WeeklyDealPlanner.plan(List.of(a,b),List.of(),n("0"),n("20"),"LOWEST_OOP",DAY,require(a,b),false,new WeeklyDealPlanner.Options(true,false,false,null));assertEquals(n("10"),result.get("cash"));
    }
    @Test void giftCardsAndWalmartCashAreRetailerSpecificPartialFundsAndNeverDoubleSpent(){
        var a=simple("a","Walmart","5",List.of());var b=simple("b","Walmart","5",List.of());var card=new WeeklyDealPlanner.Reward(UUID.randomUUID().toString(),"My card ending 1234","GIFT_CARD","Walmart",n("7"),null);
        var result=WeeklyDealPlanner.plan(List.of(a,b),List.of(card),n("999"),n("3"),"LOWEST_OOP",DAY,require(a,b),false,new WeeklyDealPlanner.Options(false,true,true,null));assertEquals(0,n("3").compareTo((BigDecimal)result.get("cash")));
        var unrelated=new WeeklyDealPlanner.Reward(UUID.randomUUID().toString(),"CVS only","ECB","CVS",n("5"),null);
        var wm=new WeeklyDealPlanner.Reward("wm","Walmart Cash","WALMART_CASH","Walmart",n("6.37"),null);var item=simple("wm-item","Walmart","7.11",List.of());
        assertEquals(n("0.74"),WeeklyDealPlanner.plan(List.of(item),List.of(wm,unrelated),n("100"),n("10"),"LOWEST_OOP",DAY).get("cash"));
        assertEquals(n("10"),WeeklyDealPlanner.plan(List.of(a,b),List.of(card),n("0"),n("20"),"LOWEST_OOP",DAY,require(a,b),false,new WeeklyDealPlanner.Options(false,false,false,null)).get("cash"));
    }
    @Test void walgreensCashFirstUsesOnlyPermittedAmountsBeforeRegisterRewards(){
        var a=simple("item","Walgreens","5",List.of());var reward=rr("early","5","Other",null,null,1);
        var result=WeeklyDealPlanner.plan(List.of(a),List.of(reward),n("5"),n("5"),"WC_FIRST",DAY);assertEquals(n("5"),step(result).estimate().get("walgreensCashUsed"));assertEquals(Set.of(),result.get("rewardsUsed"));
    }
    @Test void boosterTargetCombinesRecordedBasketsAndEnforcesFundingRules(){
        var a=simple("a","Walgreens","20",List.of());var b=simple("b","Walgreens","15",List.of());
        var p=new ShoppingPlan.ScenarioPromotion(UUID.randomUUID(),"Spend 35 Get 7","WALGREENS_CASH",n("7"),0,n("35"),"BEFORE_COUPONS",false,List.of(),true,true,false,null,null,null,null,null,true,"COUNTS",false,null,false,null);
        var target=new WeeklyDealPlanner.Target(p,"RECEIPT",Set.of(),Map.of());var result=WeeklyDealPlanner.plan(List.of(a,b),List.of(),n("100"),n("40"),"LOWEST_OOP",DAY,List.of(),true,new WeeklyDealPlanner.Options(false,false,false,target));
        assertEquals(true,result.get("boosterMet"));assertEquals(n("35"),result.get("cash"));assertEquals(n("7.35"),result.get("earnings"));assertEquals(n("0"),step(result).estimate().get("walgreensCashUsed"));assertEquals(2,step(result).scenario().items().size());
    }
    @Test void giftCardThresholdReductionCannotCreateAnUnqualifiedBoosterEarning(){
        var a=simple("a","Walgreens","35",List.of());var p=new ShoppingPlan.ScenarioPromotion(UUID.randomUUID(),"Spend 35 Get 7","WALGREENS_CASH",n("7"),0,n("35"),"BEFORE_COUPONS",false,List.of(),true,true,false,null,null,null,null,null,true,"REDUCES",false,null,true,null);
        var target=new WeeklyDealPlanner.Target(p,"RECEIPT",Set.of(),Map.of());var card=new WeeklyDealPlanner.Reward("gift","Gift","GIFT_CARD","Walgreens",n("10"),null);
        var result=WeeklyDealPlanner.plan(List.of(a),List.of(card),n("0"),n("40"),"LOWEST_OOP",DAY,List.of(),true,new WeeklyDealPlanner.Options(false,true,true,target));assertEquals(true,result.get("boosterMet"));assertEquals(n("35"),result.get("cash"));
    }

    @Test void laterEligibleGiftCardIsComparedAfterManySmallerCards(){
        var candidate=simple("basket","Walgreens","20",List.of());var funds=new ArrayList<WeeklyDealPlanner.Reward>();for(int i=0;i<8;i++)funds.add(new WeeklyDealPlanner.Reward("small-"+i,"Small card","GIFT_CARD","Walgreens",n("1"),null));funds.add(new WeeklyDealPlanner.Reward("z-large","Large card","GIFT_CARD","Walgreens",n("20"),null));
        var result=WeeklyDealPlanner.plan(List.of(candidate),funds,n("0"),n("20"),"LOWEST_OOP",DAY,List.of(),true,new WeeklyDealPlanner.Options(false,true,true,null));assertEquals(0,((BigDecimal)result.get("cash")).signum());
    }

    @Test void automaticCouponsReuseRecordedSkuDateStoreAndSingleRedemptionRules(){
        var terms=new coupons.promotions.CouponTerms(UUID.randomUUID(),coupons.promotions.CouponTerms.Source.DIGITAL_MANUFACTURER,coupons.promotions.CouponTerms.Discount.DOLLARS,"Brand","Walgreens",null,null,List.of("a","b"),null,null,DAY.minusDays(1),1,null,null,null);
        var coupon=new coupons.promotions.Coupon("Recorded coupon",n("2"),1,coupons.promotions.CouponType.MANUFACTURER,DAY.plusDays(2),terms);
        var clipped=new coupons.promotions.ClippedCoupon(UUID.randomUUID(),"Brand",null,coupon,coupons.promotions.CouponStatus.CLIPPED,DAY.minusDays(1),1);
        var a=simple("a","Walgreens","5",List.of());var b=simple("b","Walgreens","5",List.of());
        var result=WeeklyDealPlanner.plan(List.of(a,b),List.of(),n("0"),n("20"),"LOWEST_OOP",DAY,require(a,b),false,new WeeklyDealPlanner.Options(false,false,false,null,List.of(clipped)));
        assertEquals(n("8"),result.get("cash"));
        var cvs=simple("a","CVS","5",List.of());result=WeeklyDealPlanner.plan(List.of(cvs),List.of(),n("0"),n("20"),"LOWEST_OOP",DAY,List.of(),true,new WeeklyDealPlanner.Options(false,false,false,null,List.of(clipped)));assertEquals(n("5"),result.get("cash"));
    }
    @Test void boosterSearchCanLeaveAnOptionalCouponUnusedToMeetRecordedAfterCouponThreshold(){
        var a=simple("a","Walgreens","20",List.of());var source=a.scenario();var coupon=new ShoppingPlan.ScenarioCoupon(UUID.randomUUID(),"Optional coupon",n("5"),1,List.of(source.items().getFirst().id()),null);
        a=new WeeklyDealPlanner.Candidate(a.id(),a.name(),a.store(),new ShoppingPlan.Scenario(source.items(),List.of(coupon),List.of(),n("0"),n("0"),n("0"),n("0"),null,"CASH",""));var b=simple("b","Walgreens","15",List.of());
        var p=new ShoppingPlan.ScenarioPromotion(UUID.randomUUID(),"Spend 35 Get 7","WALGREENS_CASH",n("7"),0,n("35"),"AFTER_COUPONS",false,List.of(),true,true,false,null,null,null,null,null,true,"COUNTS",false,null,true,null);
        var result=WeeklyDealPlanner.plan(List.of(a,b),List.of(),n("0"),n("40"),"LOWEST_OOP",DAY,List.of(),true,new WeeklyDealPlanner.Options(false,false,false,new WeeklyDealPlanner.Target(p,"RECEIPT",Set.of(),Map.of())));
        assertEquals(true,result.get("boosterMet"));assertEquals(n("0"),step(result).estimate().get("couponTotal"));assertEquals(n("35"),result.get("cash"));
    }

    @Test void boosterRepeatUsesRespectTheRecordedRemainingLimit(){
        var a=simple("a","Walgreens","35",List.of());var b=simple("b","Walgreens","35",List.of());
        var p=new ShoppingPlan.ScenarioPromotion(UUID.randomUUID(),"Spend 35 Get 7","WALGREENS_CASH",n("7"),0,n("35"),"BEFORE_COUPONS",false,List.of(),true,true,false,null,null,null,null,null,true,"COUNTS",false,null,true,null);
        var once=new WeeklyDealPlanner.Target(p,"RECEIPT",Set.of(),Map.of(),Set.of(),Set.of(),1);var repeat=new WeeklyDealPlanner.Target(p,"RECEIPT",Set.of(),Map.of(),Set.of(),Set.of(),2);
        var single=WeeklyDealPlanner.plan(List.of(a,b),List.of(),n("0"),n("100"),"REWARDS",DAY,require(a,b),false,new WeeklyDealPlanner.Options(false,false,false,once));
        var twice=WeeklyDealPlanner.plan(List.of(a,b),List.of(),n("0"),n("100"),"REWARDS",DAY,require(a,b),false,new WeeklyDealPlanner.Options(false,false,false,repeat));
        assertEquals(n("7.70"),single.get("earnings"));assertEquals(n("14.70"),twice.get("earnings"));
    }
}
