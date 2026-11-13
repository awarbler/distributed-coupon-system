package coupons.api;

import coupons.shopping.ShoppingPlan;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeeklyDealCorrectionsTest {
    @Test void pepsiArtifactHasUnknownPricesAndGeneralBuyTwoGetTwo() {
        String source="Buy 2 get ∞ 2 FREE 12-Pack Pepsi Products with myWalgreens Shop now";
        var result=WeeklyAdvertisementImport.interpret(List.of(new WeeklyAdvertisementImport.Page(1,source)),Map.of("retailer","Walgreens"),"Availables.pdf");
        var offer=ReceiptInput.rows(result,"offers",10).getFirst();
        var fields=ShoppingPlanViews.object(offer.get("fields"));
        assertEquals("12-Pack",fields.get("sizes"));assertEquals(source,fields.get("originalText"));assertEquals("myWalgreens",fields.get("loyalty"));
        assertNull(offer.get("validFrom"));assertNull(offer.get("validThrough"));
        var draft=ShoppingPlanViews.object(offer.get("draft"));var item=ReceiptInput.rows(draft,"items",10).getFirst();
        assertEquals("12-Pack Pepsi Products",item.get("name"));assertNull(item.get("regular"));assertNull(item.get("sale"));
        var rule=ShoppingPlanViews.object(item.get("priceRule"));assertEquals("BUY_GET",rule.get("type"));assertEquals(2,((Number)rule.get("buyQuantity")).intValue());assertEquals(2,((Number)rule.get("freeQuantity")).intValue());assertEquals(4,((Number)item.get("quantity")).intValue());
        var pricing=new LinkedHashMap<String,Object>();pricing.put("type","ADVERTISEMENT");pricing.put("productDescription",offer.get("title"));pricing.put("minimumQuantity",1);pricing.put("advertisement",Map.of("draftJson",Json.encode(draft),"fields",fields,"warnings",List.of()));
        var body=new LinkedHashMap<>(offer);body.keySet().removeAll(Set.of("fields","warnings","draft"));body.put("pricing",pricing);
        var saved=DealInput.read(Json.parseObject(Json.encode(body))).deal();assertNull(saved.pricing().regularUnitPrice());assertEquals(source,saved.pricing().advertisement().fields().get("originalText"));
        item.put("name","Pepsi Products — 12-pack");item.put("regular","8.99");pricing.put("advertisement",Map.of("draftJson",Json.encode(draft),"fields",fields,"warnings",List.of()));
        var corrected=DealInput.read(Json.parseObject(Json.encode(body))).deal();var reloaded=Json.parseObject(corrected.pricing().advertisement().draftJson());assertEquals("Pepsi Products — 12-pack",ReceiptInput.rows(reloaded,"items",10).getFirst().get("name"));assertEquals(source,corrected.interpretedTerms());
    }
    @Test void generalizedFreeQuantitiesPreservePartialGroupsAndRejectInvalidCounts() {
        var rule=new ShoppingPlan.PriceRule("BUY_GET",null,List.of(),2,2);
        assertEquals(new BigDecimal("20"),rule.total(new BigDecimal("10"),4));
        assertEquals(new BigDecimal("30"),rule.total(new BigDecimal("10"),5));
        assertEquals(new BigDecimal("40"),rule.total(new BigDecimal("10"),8));
        assertThrows(IllegalArgumentException.class,()->new ShoppingPlan.PriceRule("BUY_GET",null,List.of(),0,2));
        assertThrows(IllegalArgumentException.class,()->new ShoppingPlan.PriceRule("BUY_GET",null,List.of(),2,null));
    }
    @Test void generalizedPriceRuleSurvivesSerialization() throws Exception {
        var original=new ShoppingPlan.PriceRule("BUY_GET",null,List.of(UUID.randomUUID()),3,2);
        var bytes=new java.io.ByteArrayOutputStream();try(var out=coupons.serialization.LegacyObjectStreams.output(bytes)){out.writeObject(original);}
        try(var in=coupons.serialization.LegacyObjectStreams.input(new java.io.ByteArrayInputStream(bytes.toByteArray()))){assertEquals(original,in.readObject());}
    }
}
