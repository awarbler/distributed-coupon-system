package coupons.api;

import coupons.promotions.ClippedCoupon;
import coupons.promotions.Coupon;
import coupons.promotions.CouponStatus;
import coupons.promotions.CouponTerms;
import coupons.promotions.CouponType;

import java.time.LocalDate;
import java.util.*;
import static coupons.api.Json.*;

/** One shared manual coupon workflow; copies are independent instruments, never RR account deposits. */
final class CouponInput {
    static List<ClippedCoupon> read(Map<String,Object> f) {
        known(f,"couponId","provider","description","amount","requiredQuantity","couponType","expirationDate","clippedOn","status","copies","source","discount","storeRestriction","accountRestriction","productOrBrand","qualifyingSkus","sizeOrVariety","minimumPurchase","startsOn","usageLimit","conditions","evidenceMediaType","evidenceBase64","externalId","originalOfferText","importedOn","importSource","offerDetails");
        int copies=Math.toIntExact(number(f.get("copies")));if(copies<1||copies>100)throw new IllegalArgumentException("Record 1 to 100 physical copies.");
        var source=CouponTerms.Source.valueOf(required(f,"source"));
        if(source.name().startsWith("DIGITAL")&&copies!=1)throw new IllegalArgumentException("Digital coupons have one available identity.");
        var classification=CouponType.valueOf(required(f,"couponType"));
        if(source.name().endsWith("MANUFACTURER")&&classification!=CouponType.MANUFACTURER||source.name().endsWith("STORE")&&classification!=CouponType.STORE)throw new IllegalArgumentException("Coupon source and classification disagree.");
        var status=CouponStatus.valueOf(required(f,"status"));if(status!=CouponStatus.CLIPPED&&status!=CouponStatus.AVAILABLE)throw new IllegalArgumentException("New coupons must be saved or available.");
        var skus=new ArrayList<String>();if(f.get("qualifyingSkus") instanceof List<?> list)for(var sku:list)skus.add(asText(sku));
        String evidence=optional(f,"evidenceBase64"),media=optional(f,"evidenceMediaType");
        if(evidence!=null && (!Set.of("image/jpeg","image/png","image/webp","application/pdf").contains(media==null?"":media)||Base64.getDecoder().decode(evidence).length>5*1024*1024))throw new IllegalArgumentException("Invalid coupon evidence.");
        var terms=new CouponTerms(UUID.fromString(required(f,"couponId")),source,CouponTerms.Discount.valueOf(required(f,"discount")),required(f,"provider"),optional(f,"storeRestriction"),optional(f,"accountRestriction"),optional(f,"productOrBrand"),skus,optional(f,"sizeOrVariety"),ReceiptInput.decimal(f,"minimumPurchase",true),date(f,"startsOn"),Math.toIntExact(number(f.get("usageLimit"))),optional(f,"conditions"),media,evidence,optional(f,"originalOfferText"),date(f,"importedOn"),optional(f,"importSource"),details(f));
        var coupon=new Coupon(required(f,"description"),(terms.discount()==CouponTerms.Discount.FREE_ITEM||!terms.checkoutDiscount())?null:ReceiptInput.decimal(f,"amount",false),f.get("requiredQuantity")==null&&!terms.checkoutDiscount()?0:Math.toIntExact(number(f.get("requiredQuantity"))),classification,date(f,"expirationDate"),terms);
        var id=UUID.fromString(required(f,"couponId"));List<ClippedCoupon> out=new ArrayList<>();
        for(int n=0;n<copies;n++)out.add(new ClippedCoupon(n==0?id:UUID.nameUUIDFromBytes((id+":"+n).getBytes(java.nio.charset.StandardCharsets.UTF_8)),required(f,"provider"),optional(f,"externalId"),coupon,status,date(f,"clippedOn"),1));
        return List.copyOf(out);
    }
    static ClippedCoupon addCopy(ClippedCoupon original,UUID id,LocalDate observedOn) {
        var terms=original.coupon().getTerms();
        if(terms==null||!Set.of(CouponTerms.Source.PAPER_MANUFACTURER,CouponTerms.Source.PAPER_STORE,CouponTerms.Source.PRINTABLE,CouponTerms.Source.REGISTER_PRINTED).contains(terms.source()))throw new IllegalArgumentException("Add Copy is only for recorded physical coupons.");
        if(id.equals(original.couponId()))throw new IllegalArgumentException("A new physical copy requires its own coupon ID.");
        Objects.requireNonNull(observedOn,"Enter the date this physical copy was recorded.");
        return new ClippedCoupon(id,original.provider(),original.externalId(),original.coupon(),CouponStatus.CLIPPED,observedOn,1);
    }
    static ClippedCoupon edit(ClippedCoupon old, Map<String,Object> fields) {
        if(old.status()==CouponStatus.APPLIED || old.redeemedTransactionId()!=null)
            throw new IllegalArgumentException("Redeemed coupons are protected. Correct the linked receipt using its recorded correction workflow.");
        var corrected=new LinkedHashMap<String,Object>(fields);
        boolean removed="NO_LONGER_AVAILABLE".equals(optional(fields,"status"))||"NOT_APPLIED".equals(optional(fields,"status"));
        if(removed)corrected.put("status","AVAILABLE");
        var revised=read(corrected);
        if(revised.size()!=1 || !revised.get(0).couponId().equals(old.couponId()))throw new IllegalArgumentException("Editing must preserve coupon identity and one copy.");
        var x=revised.get(0);
        if(!Objects.equals(x.externalId(),old.externalId()))throw new IllegalArgumentException("Editing must preserve the original external offer identifier.");
        var t=x.coupon().getTerms();var prior=old.coupon().getTerms();
        var terms=new CouponTerms(prior==null?t.offerId():prior.offerId(),t.source(),t.discount(),t.issuingProvider(),t.storeRestriction(),t.accountRestriction(),t.productOrBrand(),t.qualifyingSkus(),t.sizeOrVariety(),t.minimumPurchase(),t.startsOn(),t.usageLimit(),t.conditions(),t.evidenceMediaType(),t.evidenceBase64(),t.originalOfferText(),t.importedOn(),t.importSource(),t.offerDetails());
        var coupon=new Coupon(x.coupon().getCouponDescription(),x.coupon().getDiscountAmount(),x.coupon().getRequiredQuantity(),x.coupon().getCouponType(),x.coupon().getExpirationDate(),terms);
        return new ClippedCoupon(old.couponId(),x.provider(),old.externalId(),coupon,removed?CouponStatus.valueOf(required(fields,"status")):x.status(),x.clippedOn(),old.revision()+1);
    }
    static ClippedCoupon refreshSeen(ClippedCoupon old,ClippedCoupon observed) {
        var t=old.coupon().getTerms();var incoming=observed.coupon().getTerms();var metadata=new LinkedHashMap<>(t.offerDetails());
        if(incoming.offerDetails().containsKey("recordedClipLimit"))metadata.put("recordedClipLimit",incoming.offerDetails().get("recordedClipLimit"));
        boolean resolveDate=old.coupon().getExpirationDate()==null&&observed.coupon().getExpirationDate()!=null&&"CURRENT_IMPORT_YEAR".equals(incoming.offerDetails().get("dateYearBasis"));
        if(resolveDate)for(String key:List.of("datePrecision","dateYearBasis","dateNote","importKey","previousImportKey"))if(incoming.offerDetails().containsKey(key))metadata.put(key,incoming.offerDetails().get(key));
        metadata.put("lastSeenInImport",String.valueOf(incoming.importedOn()));metadata.put("lastSeenSourceDocument",String.valueOf(incoming.importSource()));
        var terms=new CouponTerms(t.offerId(),t.source(),t.discount(),t.issuingProvider(),t.storeRestriction(),t.accountRestriction(),t.productOrBrand(),t.qualifyingSkus(),t.sizeOrVariety(),t.minimumPurchase(),resolveDate?incoming.startsOn():t.startsOn(),t.usageLimit(),t.conditions(),t.evidenceMediaType(),t.evidenceBase64(),t.originalOfferText(),incoming.importedOn(),incoming.importSource(),metadata);
        var coupon=new Coupon(old.coupon().getCouponDescription(),old.coupon().getDiscountAmount(),old.coupon().getRequiredQuantity(),old.coupon().getCouponType(),resolveDate?observed.coupon().getExpirationDate():old.coupon().getExpirationDate(),terms);
        return new ClippedCoupon(old.couponId(),old.provider(),old.externalId(),coupon,old.status(),old.clippedOn(),old.revision()+1);
    }
    private static Map<String,String> details(Map<String,Object> f) {
        var result=new LinkedHashMap<String,String>();
        if(f.get("offerDetails") instanceof Map<?,?> values)values.forEach((k,v)->{if(v!=null)result.put(asText(k),asText(v));});
        return result;
    }
    private static LocalDate date(Map<String,Object> f,String key){var value=optional(f,key);return value==null?null:LocalDate.parse(value);}
}
