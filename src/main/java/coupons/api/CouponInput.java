package coupons.api;

import coupons.*;
import java.time.LocalDate;
import java.util.*;
import static coupons.api.Json.*;

/** One shared manual coupon workflow; copies are independent instruments, never RR account deposits. */
final class CouponInput {
    static List<ClippedCoupon> read(Map<String,Object> f) {
        known(f,"couponId","provider","description","amount","requiredQuantity","couponType","expirationDate","clippedOn","status","copies","source","discount","storeRestriction","accountRestriction","productOrBrand","qualifyingSkus","sizeOrVariety","minimumPurchase","startsOn","usageLimit","conditions","evidenceMediaType","evidenceBase64");
        int copies=Math.toIntExact(number(f.get("copies")));if(copies<1||copies>100)throw new IllegalArgumentException("Record 1 to 100 physical copies.");
        var source=CouponTerms.Source.valueOf(required(f,"source"));
        if(source.name().startsWith("DIGITAL")&&copies!=1)throw new IllegalArgumentException("Digital coupons have one available identity.");
        var classification=CouponType.valueOf(required(f,"couponType"));
        if(source.name().endsWith("MANUFACTURER")&&classification!=CouponType.MANUFACTURER||source.name().endsWith("STORE")&&classification!=CouponType.STORE)throw new IllegalArgumentException("Coupon source and classification disagree.");
        var status=CouponStatus.valueOf(required(f,"status"));if(status!=CouponStatus.CLIPPED&&status!=CouponStatus.AVAILABLE)throw new IllegalArgumentException("New coupons must be saved or available.");
        var skus=new ArrayList<String>();if(f.get("qualifyingSkus") instanceof List<?> list)for(var sku:list)skus.add(asText(sku));
        String evidence=optional(f,"evidenceBase64"),media=optional(f,"evidenceMediaType");
        if(evidence!=null && (!Set.of("image/jpeg","image/png","image/webp","application/pdf").contains(media==null?"":media)||Base64.getDecoder().decode(evidence).length>5*1024*1024))throw new IllegalArgumentException("Invalid coupon evidence.");
        var terms=new CouponTerms(UUID.fromString(required(f,"couponId")),source,CouponTerms.Discount.valueOf(required(f,"discount")),required(f,"provider"),optional(f,"storeRestriction"),optional(f,"accountRestriction"),optional(f,"productOrBrand"),skus,optional(f,"sizeOrVariety"),ReceiptInput.decimal(f,"minimumPurchase",true),date(f,"startsOn"),Math.toIntExact(number(f.get("usageLimit"))),optional(f,"conditions"),media,evidence);
        var coupon=new Coupon(required(f,"description"),terms.discount()==CouponTerms.Discount.FREE_ITEM?null:ReceiptInput.decimal(f,"amount",false),Math.toIntExact(number(f.get("requiredQuantity"))),classification,date(f,"expirationDate"),terms);
        var id=UUID.fromString(required(f,"couponId"));List<ClippedCoupon> out=new ArrayList<>();
        for(int n=0;n<copies;n++)out.add(new ClippedCoupon(n==0?id:UUID.nameUUIDFromBytes((id+":"+n).getBytes(java.nio.charset.StandardCharsets.UTF_8)),required(f,"provider"),null,coupon,status,date(f,"clippedOn"),1));
        return List.copyOf(out);
    }
    private static LocalDate date(Map<String,Object> f,String key){var value=optional(f,key);return value==null?null:LocalDate.parse(value);}
}
