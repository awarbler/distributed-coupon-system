package coupons.api;

import coupons.shopping.ShoppingPlan;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DealImportTest {
    @SuppressWarnings("unchecked") @Test void axePastePreservesExplicitPricesCouponAndOneReward(){
        var imported=ShoppingNoteInput.parse("""
            ***AXE BODY WASH***
            ***BUY 1 GET 1 50% OFF***
            ***BUY 2 GET $2 REGISTER REWARD***
            Buy (2) Axe Body Wash ($5.99, $2.99)
            Total comes to $8.98
            Use the $5/2 Axe Body Wash digital coupon
            Pay $3.98 & get a $2 RR
            Final cost is $1.98 for both or 99¢ each
            ***You can pay w/ Walgreens Cash on this deal
            """,null);
        var items=(List<ShoppingPlan.Line>)imported.get("items");var coupons=(List<ShoppingPlan.ScenarioCoupon>)imported.get("coupons");var rewards=(List<ShoppingPlan.ScenarioPromotion>)imported.get("promotions");
        assertEquals(2,items.size());assertEquals(1,coupons.size());assertEquals(1,rewards.size());assertEquals(2,coupons.getFirst().lineIds().size());assertEquals(2,rewards.getFirst().requiredQuantity());
        var scenario=new ShoppingPlan.Scenario(items,coupons,rewards,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,null,"CASH","");var estimate=scenario.estimate();
        assertEquals(new BigDecimal("8.98"),estimate.get("saleTotal"));assertEquals(new BigDecimal("3.98"),estimate.get("calculatedOop"));assertEquals(new BigDecimal("1.98"),estimate.get("afterPotentialRewards"));
    }
}
