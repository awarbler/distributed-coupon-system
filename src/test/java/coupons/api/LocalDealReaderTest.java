package coupons.api;
import coupons.shopping.ShoppingPlan;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LocalDealReaderTest {
    static Map<String,Object> product(){var p=new LinkedHashMap<String,Object>();p.put("name","AXE Body Wash");p.put("quantity",2);p.put("regularPrice",null);p.put("unitPrices",List.of("5.99","2.99"));return p;}
    static Map<String,Object> extracted(){return new LinkedHashMap<>(Map.of("products",List.of(product()),"coupons",List.of(Map.of("description","AXE digital coupon","amount","5","requiredQuantity",2,"productIndexes",List.of(0),"wholeReceipt",false)),"rewards",List.of(Map.of("description","AXE RR","type","REGISTER_REWARD","amount","2","requiredQuantity",2,"requiredSpend","0","spendBasis","BEFORE_COUPONS","allowsWalgreensCash",true,"productIndexes",List.of(0),"wholeReceipt",false)),"store","Walgreens","productTotal","8.98","afterCoupons","3.98","issues",List.of()));}
    @SuppressWarnings("unchecked") @Test void checksAxeMathWithoutReparsingTextPatterns(){var result=LocalDealReader.convert(extracted(),new ShoppingPlan.PlanningSource("source",null,null));var items=(List<ShoppingPlan.Line>)result.get("items");var coupons=(List<ShoppingPlan.ScenarioCoupon>)result.get("coupons");var rewards=(List<ShoppingPlan.ScenarioPromotion>)result.get("promotions");var z=BigDecimal.ZERO;var estimate=new ShoppingPlan.Scenario(items,coupons,rewards,z,z,z,z,null,"CASH","").estimate();assertEquals(new BigDecimal("3.98"),estimate.get("calculatedOop"));assertEquals(new BigDecimal("1.98"),estimate.get("afterPotentialRewards"));assertFalse(result.containsKey("payments"));}
    @Test void flagsConflictingTotals(){var input=extracted();input.put("productTotal","9.98");assertTrue(((List<?>)LocalDealReader.convert(input,new ShoppingPlan.PlanningSource("",null,null)).get("unresolved")).stream().anyMatch(v->v.toString().contains("Amount conflict")));}
    @Test void rejectsMissingProductLinks(){var input=extracted();input.put("coupons",List.of(Map.of("description","wrong","amount","5","requiredQuantity",2,"productIndexes",List.of(99),"wholeReceipt",false)));assertThrows(IllegalArgumentException.class,()->LocalDealReader.convert(input,new ShoppingPlan.PlanningSource("",null,null)));}
}
