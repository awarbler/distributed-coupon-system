package coupons.api;

import coupons.*;
import java.time.*;
import java.util.*;
import static coupons.api.Json.*;

/** Explicit deal input, with immutable manual-entry provenance when no advertisement is supplied. */
record DealInput(AdvertisedDeal deal,SourceArtifact source) {
    static DealInput read(Map<String,Object> f) {
        known(f,"dealId","title","interpretedTerms","retailer","validFrom","validThrough","pricing","attachment");
        if(!(f.get("pricing") instanceof Map<?,?>))throw new IllegalArgumentException("Pricing terms are required.");
        @SuppressWarnings("unchecked") var p=(Map<String,Object>)f.get("pricing");
        known(p,"cheapestItemsDiscounted","type","productDescription","regularUnitPrice","promotionalPrice","minimumQuantity","minimumSpend","freeQuantity","rewardAmount","repeatable","limit","qualifyingSkus","qualifications","loyaltyRequirement","exclusions");
        var skus=new ArrayList<String>();if(p.get("qualifyingSkus") instanceof List<?> list)for(var sku:list)skus.add(asText(sku));
        var terms=new DealTerms(DealTerms.Type.valueOf(required(p,"type")),required(p,"productDescription"),ReceiptInput.decimal(p,"regularUnitPrice",true),ReceiptInput.decimal(p,"promotionalPrice",true),
                Math.toIntExact(number(p.get("minimumQuantity"))),ReceiptInput.decimal(p,"minimumSpend",true),p.get("freeQuantity")==null?0:Math.toIntExact(number(p.get("freeQuantity"))),ReceiptInput.decimal(p,"rewardAmount",true),Boolean.TRUE.equals(p.get("repeatable")),p.get("limit")==null?null:Math.toIntExact(number(p.get("limit"))),skus,optional(p,"qualifications"),optional(p,"loyaltyRequirement"),optional(p,"exclusions"),ReceiptInput.flag(p,"cheapestItemsDiscounted"));
        if(f.get("attachment")!=null){
            if(!(f.get("attachment") instanceof Map<?,?> a)||!Set.of("image/jpeg","image/png","image/webp","application/pdf").contains(a.get("mediaType"))||!(a.get("contentBase64") instanceof String content)||Base64.getDecoder().decode(content).length>5*1024*1024)throw new IllegalArgumentException("Invalid advertisement attachment.");
        }
        UUID id=UUID.fromString(required(f,"dealId")),source=UUID.randomUUID();
        // Canonical manual input excludes client-generated identity, so repeated identical entries conflict.
        var provenance=new LinkedHashMap<>(f);provenance.remove("dealId");
        var artifact=new SourceArtifact(source,"Manually recorded deal","application/json",null,Instant.now(),Json.encode(provenance).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new DealInput(new AdvertisedDeal(id,required(f,"title"),required(f,"interpretedTerms"),required(f,"retailer"),date(f,"validFrom"),date(f,"validThrough"),List.of(source),1,terms),artifact);
    }
    private static LocalDate date(Map<String,Object> f,String key){var value=optional(f,key);return value==null?null:LocalDate.parse(value);}
}
