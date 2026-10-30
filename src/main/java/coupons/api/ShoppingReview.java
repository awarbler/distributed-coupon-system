package coupons.api;

import coupons.shopping.ShoppingPlan;
import java.math.BigDecimal;
import java.util.*;

/** Read-only comparisons of existing Java planning results. */
final class ShoppingReview {
    private ShoppingReview() {}
    static Map<String,Object> compare(ShoppingPlan.Scenario a,String storeA,ShoppingPlan.Scenario b,String storeB) {
        var left=a.estimate(storeA);var right=b.estimate(storeB);
        var result=new LinkedHashMap<String,Object>();result.put("a",costs(a,left));result.put("b",costs(b,right));
        result.put("cashDifference",amount(right,"calculatedOop").subtract(amount(left,"calculatedOop")));
        result.put("rrDifference",amount(right,"registerRewardsUsed").subtract(amount(left,"registerRewardsUsed")));
        var quantities=new LinkedHashMap<String,Integer>();var names=new HashMap<String,String>();
        for(var l:a.items())quantities.merge(identity(l),-l.quantity(),Integer::sum);
        for(var l:b.items()){quantities.merge(identity(l),l.quantity(),Integer::sum);names.put(identity(l),l.name());}
        result.put("addedItems",quantities.entrySet().stream().filter(e->e.getValue()>0).map(e->Map.of("name",names.get(e.getKey()),"quantity",e.getValue())).toList());
        return result;
    }
    private static String identity(ShoppingPlan.Line l){return l.sku()==null?"name:"+l.name().trim().toLowerCase(Locale.ROOT):"sku:"+l.sku();}
    private static BigDecimal amount(Map<String,Object> values,String key){return (BigDecimal)values.get(key);}
    private static Map<String,Object> costs(ShoppingPlan.Scenario s,Map<String,Object> estimate){
        var out=new LinkedHashMap<String,Object>();
        out.put("total",amount(estimate,"saleTotal").add(amount(estimate,"tax")));
        for(String key:List.of("couponTotal","totalWithTax","registerRewardsUsed","walgreensCashUsed","ecbUsed","walmartCashUsed","giftCardUsed","calculatedOop","selectedOop","manualDifference","potentialRewards","overFunding"))out.put(key,estimate.get(key));
        out.put("earnings",estimate.get("promotions"));out.put("hasEnteredEarnings",!s.promotions().isEmpty());return out;
    }
    static Map<String,Object> reconcile(BigDecimal beginning,BigDecimal earned,BigDecimal used,BigDecimal reported){
        var out=new LinkedHashMap<String,Object>();var ending=beginning==null||earned==null||used==null?null:beginning.add(earned).subtract(used);
        out.put("calculatedEnding",ending);out.put("reportedEnding",reported);out.put("difference",ending==null||reported==null?null:reported.subtract(ending));return out;
    }
}
