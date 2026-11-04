package coupons.api;

import coupons.shopping.ShoppingPlan;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeeklyDealPlannerTest {
    static BigDecimal n(String n){return new BigDecimal(n);}
    static WeeklyDealPlanner.Candidate deal(String id,String price,String earning){var item=new ShoppingPlan.Line(UUID.randomUUID(),id,null,null,n(price),1);var reward=new ShoppingPlan.ScenarioPromotion(UUID.randomUUID(),"Expected reward","REGISTER_REWARD",n(earning),1,BigDecimal.ZERO,"BEFORE_COUPONS",true,List.of(item.id()));return new WeeklyDealPlanner.Candidate(id,id,"Walgreens",new ShoppingPlan.Scenario(List.of(item),List.of(),List.of(reward),n("0"),n("0"),n("0"),n("0"),null,"CASH",""));}
    @SuppressWarnings("unchecked") @Test void respectsBudgetAndDoesNotSpendSameRewardTwice(){
        var rr=new WeeklyDealPlanner.Reward("rr","Palmolive","REGISTER_REWARD","Walgreens",n("2"),LocalDate.parse("2026-11-05"));
        var result=WeeklyDealPlanner.plan(List.of(deal("a","4","3"),deal("b","4","3")),List.of(rr),n("0"),n("5"),"REWARDS",LocalDate.parse("2026-11-04"));
        assertEquals(1,((List<?>)result.get("steps")).size());assertEquals(n("2"),result.get("cash"));assertEquals(Set.of("rr"),result.get("rewardsUsed"));
    }
    @Test void expiredRewardIsNotSpendableAndNewEarningsDoNotFundPurchase(){var rr=new WeeklyDealPlanner.Reward("old","Old","REGISTER_REWARD","Walgreens",n("10"),LocalDate.parse("2026-11-01"));var result=WeeklyDealPlanner.plan(List.of(deal("a","25","100")),List.of(rr),n("0"),n("20"),"REWARDS",LocalDate.parse("2026-11-04"));assertTrue(((List<?>)result.get("steps")).isEmpty());}
    @Test void earliestExpiryIsUsedBeforeLargerLaterReward(){var early=new WeeklyDealPlanner.Reward("early","Early","REGISTER_REWARD","Walgreens",n("2"),LocalDate.parse("2026-11-05"));var later=new WeeklyDealPlanner.Reward("later","Later","REGISTER_REWARD","Walgreens",n("4"),LocalDate.parse("2026-11-20"));var result=WeeklyDealPlanner.plan(List.of(deal("a","4","1")),List.of(later,early),n("0"),n("20"),"LOWEST_OOP",LocalDate.parse("2026-11-04"));assertEquals(Set.of("early"),result.get("rewardsUsed"));}
    @Test void modesChooseDifferentDeals(){var candidates=List.of(deal("cheap","2","0"),deal("earner","8","10"));var low=WeeklyDealPlanner.plan(candidates,List.of(),n("0"),n("8"),"LOWEST_OOP",LocalDate.now());var rewards=WeeklyDealPlanner.plan(candidates,List.of(),n("0"),n("8"),"REWARDS",LocalDate.now());assertEquals(n("2"),low.get("cash"));assertEquals(n("8"),rewards.get("cash"));}
}
