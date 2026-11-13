package coupons.api;

import coupons.shopping.ShoppingPlan;
import coupons.shopping.ShoppingEstimate;
import java.math.BigDecimal;
import java.util.*;

/** Read-only bridge for purchase rows not yet incorporated into a saved scenario. */
final class ShoppingPlanViews {
    private ShoppingPlanViews() {}
    @SuppressWarnings("unchecked") static Map<String,Object> workspace(Map<String,Object> workspace) {
        if(!(workspace.get("plans") instanceof List<?> plans))return workspace;
        var deals=new HashMap<String,Map<String,Object>>();
        if(workspace.get("deals") instanceof List<?> list)for(var raw:list){var deal=object(raw);deals.put(deal.get("dealId").toString(),deal);}
        var result=new LinkedHashMap<>(workspace);result.put("plans",plans.stream().map(row->removed(project(object(row),deals),new HashSet<>(ids(workspace.get("deletedWeeklyDealIds"))))).toList());return result;
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> removed(Map<String,Object> row,Set<String> deleted){
        var plan=map(row.get("plan"));if(plan.get("purchasedReceiptId")!=null||deleted.isEmpty())return row;
        var references=new LinkedHashSet<String>();var scenario=plan.get("scenario") instanceof Map<?,?>?map(plan.get("scenario")):null;
        if(scenario!=null&&scenario.get("source") instanceof Map<?,?> source)references.addAll(ids(source.get("advertisedDealIds")));
        var processed=ids(plan.get("plannedPurchaseIds"));for(var item:rows(row,"items"))if(item.get("advertisedDealId")!=null&&(scenario==null||!processed.contains(item.get("plannedPurchaseId").toString())))references.add(item.get("advertisedDealId").toString());
        references.retainAll(deleted);if(references.isEmpty())return row;
        plan.put("removedWeeklyDealIds",List.copyOf(references));row.put("plan",plan);row.put("estimateComplete",false);row.put("scenarioEstimate",null);row.put("estimatedMerchandise",null);
        row.put("pricing",new ShoppingEstimate(null,null,null,null,null,null,null,List.of("The source Weekly Deals were removed as incorrect. Review entered prices, coupons and promotion terms before using this plan.")));return row;
    }
    static Map<String,Object> object(Object value){return Json.parseObject(Json.encode(value));}
    @SuppressWarnings("unchecked") static Map<String,Object> project(Map<String,Object> row,Map<String,Map<String,Object>> deals) {
        var plan=map(row.get("plan"));if(!(plan.get("scenario") instanceof Map<?,?>)||plan.get("purchasedReceiptId")!=null)return row;
        var scenario=map(plan.get("scenario"));var lines=rows(scenario,"items");
        var incorporated=new HashSet<>(ids(plan.get("plannedPurchaseIds")));for(var line:lines)incorporated.add(line.get("id").toString());
        var pending=rows(row,"items").stream().filter(p->!incorporated.contains(p.get("plannedPurchaseId").toString())).toList();
        if(!pending.isEmpty())plan.put("pendingPurchaseIds",pending.stream().map(p->p.get("plannedPurchaseId")).toList());
        if(pending.isEmpty()){row.put("items",displayItems(plan,scenario,rows(row,"items")));return row;}
        var coupons=new ArrayList<>(rows(scenario,"coupons"));var rewards=new ArrayList<>(rows(scenario,"promotions"));
        var source=scenario.get("source")==null?new LinkedHashMap<String,Object>():map(scenario.get("source"));
        var sourceIds=new LinkedHashSet<>(ids(source.get("advertisedDealIds")));
        for(var purchase:pending){
            var line=new LinkedHashMap<String,Object>();line.put("id",purchase.get("plannedPurchaseId"));line.put("name",purchase.get("productName"));line.put("sku",purchase.get("productCode"));line.put("quantity",purchase.get("quantity"));
            line.put("regular",purchase.get("regularUnitPrice"));line.put("sale",purchase.get("saleUnitPrice"));
            if(line.get("regular")==null&&line.get("sale")==null)line.put("regular",purchase.get("targetUnitPrice"));
            lines.add(line);
        }
        var byDeal=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(var purchase:rows(row,"items"))if(purchase.get("advertisedDealId")!=null)byDeal.computeIfAbsent(purchase.get("advertisedDealId").toString(),k->new ArrayList<>()).add(purchase);
        for(var group:byDeal.entrySet()) {
            if(pending.stream().noneMatch(p->group.getKey().equals(p.get("advertisedDealId"))))continue;
            var deal=deals.get(group.getKey());if(deal==null||!(deal.get("pricing") instanceof Map<?,?> pricing)||!(pricing.get("advertisement") instanceof Map<?,?> ad))continue;
            var draft=Json.parseObject(ad.get("draftJson").toString());var draftLines=rows(draft,"items");var links=new HashMap<String,String>();
            for(var advertised:draftLines){
                var matches=group.getValue().stream().filter(p->sameProduct(p,advertised)).toList();
                if(matches.size()!=1&&draftLines.size()==1&&group.getValue().size()==1)matches=group.getValue();
                if(matches.size()!=1)continue;
                String id=matches.getFirst().get("plannedPurchaseId").toString();links.put(advertised.get("id").toString(),id);
            }
            for(var advertised:draftLines) {
                String id=links.get(advertised.get("id").toString());if(id==null)continue;
                var purchase=group.getValue().stream().filter(p->id.equals(p.get("plannedPurchaseId"))).findFirst().orElseThrow();
                for(var line:lines)if(id.equals(line.get("id"))) {
                    if(advertised.get("memberRewardRate")!=null)line.putIfAbsent("memberRewardRate",advertised.get("memberRewardRate"));
                    if(!"MANUAL".equals(purchase.get("priceSource"))&&!line.containsKey("priceRule")&&advertised.get("priceRule") instanceof Map<?,?> rule) {
                        var copied=new LinkedHashMap<>(map(rule));var linked=ids(copied.get("lineIds"));
                        if(links.keySet().containsAll(linked)){copied.put("lineIds",linked.stream().map(links::get).toList());line.put("priceRule",copied);}
                    }
                }
            }
            copyBenefits(plan.get("planId").toString(),group.getKey(),rows(draft,"coupons"),coupons,links);
            copyBenefits(plan.get("planId").toString(),group.getKey(),rows(draft,"promotions"),rewards,links);
            if(!sourceIds.contains(group.getKey()))source.put("text",Objects.toString(source.get("text"),"")+"\n\n"+deal.get("title")+"\n"+deal.get("interpretedTerms"));
            sourceIds.add(group.getKey());
        }
        source.putIfAbsent("text","");source.putIfAbsent("mediaType",null);source.putIfAbsent("contentBase64",null);source.put("advertisedDealIds",new ArrayList<>(sourceIds));
        scenario.put("items",lines);scenario.put("coupons",coupons);scenario.put("promotions",rewards);scenario.put("source",source);plan.put("scenario",scenario);row.put("plan",plan);row.put("items",displayItems(plan,scenario,rows(row,"items")));
        try {
            var valid=CouponApiService.parseScenario(input(scenario));var estimate=valid.estimate((String)plan.get("retailer"));row.put("scenarioEstimate",estimate);
            BigDecimal regular=(BigDecimal)estimate.get("regularTotal"),sale=(BigDecimal)estimate.get("saleTotal"),discount=(BigDecimal)estimate.get("couponTotal"),reward=(BigDecimal)estimate.get("potentialRewards"),cost=(BigDecimal)estimate.get("merchandiseAfterCoupons");
            row.put("pricing",new ShoppingEstimate(regular,sale,regular==null?null:regular.subtract(sale),discount,reward,cost,cost.subtract(reward),List.of()));row.put("estimatedMerchandise",sale);row.put("estimateComplete",true);
        }catch(IllegalArgumentException|CouponApiService.ApiError failure){
            String issue="Complete prices and promotion terms for all shopping-plan products in Edit Deal Scenario. "+failure.getMessage();
            row.put("scenarioEstimate",null);row.put("estimatedMerchandise",null);row.put("estimateComplete",false);row.put("pricing",new ShoppingEstimate(null,null,null,null,null,null,null,List.of(issue)));
        }
        return row;
    }
    static boolean sameProduct(Map<String,Object> purchase,Map<String,Object> advertised) {
        String code=Objects.toString(purchase.get("productCode"),"");String sku=Objects.toString(advertised.get("sku"),"");
        return !code.isBlank()&&!sku.isBlank()?code.equals(sku):Objects.toString(purchase.get("productName"),"").strip().equalsIgnoreCase(Objects.toString(advertised.get("name"),"").strip());
    }
    private static void copyBenefits(String plan,String deal,List<Map<String,Object>> source,List<Map<String,Object>> target,Map<String,String> links) {
        for(var benefit:source){var old=ids(benefit.get("lineIds"));if(!Boolean.TRUE.equals(benefit.get("wholeReceipt"))&&(old.isEmpty()||!links.keySet().containsAll(old)))continue;
            var copy=new LinkedHashMap<>(benefit);copy.put("id",UUID.nameUUIDFromBytes((plan+":"+deal+":"+benefit.get("id")).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());
            copy.put("lineIds",old.stream().map(links::get).toList());if(target.stream().noneMatch(b->b.get("id").equals(copy.get("id"))||b.get("id").equals(benefit.get("id"))))target.add(copy);
        }
    }
    static Map<String,Object> input(Map<String,Object> draft) {
        var copy=object(draft);
        for(String key:List.of("tax","walgreensCashUsed","registerRewardsUsed","giftCardUsed","manualOop","taxRate","walgreensCashIncrement","walgreensCashLimit"))textAmount(copy,key);
        for(var line:rows(copy,"items")){textAmount(line,"regular");textAmount(line,"sale");integer(line,"quantity");integer(line,"memberRewardRate");if(line.get("priceRule") instanceof Map<?,?>)textAmount(map(line.get("priceRule")),"bundlePrice");}
        for(var coupon:rows(copy,"coupons")){textAmount(coupon,"amount");integer(coupon,"requiredQuantity");}
        for(var reward:rows(copy,"promotions")){textAmount(reward,"rewardAmount");textAmount(reward,"requiredSpend");integer(reward,"requiredQuantity");}
        for(var payment:rows(copy,"payments"))textAmount(payment,"amount");return copy;
    }
    private static List<Map<String,Object>> displayItems(Map<String,Object> plan,Map<String,Object> scenario,List<Map<String,Object>> purchases) {
        var result=new ArrayList<Map<String,Object>>();
        for(var line:rows(scenario,"items")){
            var item=new LinkedHashMap<String,Object>(purchases.stream().filter(p->p.get("plannedPurchaseId").equals(line.get("id"))).findFirst().orElse(Map.of()));
            item.put("plannedPurchaseId",line.get("id"));item.put("planId",plan.get("planId"));item.put("productName",line.get("name"));item.put("productCode",line.get("sku"));item.put("quantity",line.get("quantity"));item.put("regularUnitPrice",line.get("regular"));item.put("saleUnitPrice",line.get("sale"));item.put("targetUnitPrice",line.get("sale")==null?line.get("regular"):line.get("sale"));item.putIfAbsent("revision",BigDecimal.ONE);item.putIfAbsent("advertisedDealId",null);item.putIfAbsent("clippedCouponIds",List.of());result.add(item);
        }return result;
    }
    private static void integer(Map<String,Object> row,String key){if(row.get(key) instanceof String text&&!text.isBlank())row.put(key,new BigDecimal(text));}
    private static void textAmount(Map<String,Object> row,String key){if(row.get(key)!=null)row.put(key,row.get(key).toString());}
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value){return (Map<String,Object>)value;}
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> rows(Map<String,Object> value,String key){return value.get(key) instanceof List<?> list?new ArrayList<>((List<Map<String,Object>>)list):new ArrayList<>();}
    private static List<String> ids(Object value){return value instanceof List<?> list?list.stream().map(Object::toString).toList():List.of();}
}
