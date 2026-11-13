package coupons.promotions;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Manual coupon qualifications; a physical copy remains a separately redeemable coupon identity. */
public record CouponTerms(java.util.UUID offerId, Source source, Discount discount, String issuingProvider,
        String storeRestriction, String accountRestriction, String productOrBrand,
        List<String> qualifyingSkus, String sizeOrVariety, BigDecimal minimumPurchase,
        LocalDate startsOn, int usageLimit, String conditions, String evidenceMediaType,
        String evidenceBase64, String originalOfferText, LocalDate importedOn, String importSource, java.util.Map<String,String> offerDetails) implements Serializable {
    public CouponTerms(java.util.UUID offerId, Source source, Discount discount, String issuingProvider, String storeRestriction, String accountRestriction, String productOrBrand, List<String> qualifyingSkus, String sizeOrVariety, BigDecimal minimumPurchase, LocalDate startsOn, int usageLimit, String conditions, String evidenceMediaType, String evidenceBase64, String originalOfferText, LocalDate importedOn, String importSource) {
        this(offerId,source,discount,issuingProvider,storeRestriction,accountRestriction,productOrBrand,qualifyingSkus,sizeOrVariety,minimumPurchase,startsOn,usageLimit,conditions,evidenceMediaType,evidenceBase64,originalOfferText,importedOn,importSource,null);
    }
    public CouponTerms(java.util.UUID offerId, Source source, Discount discount, String issuingProvider,
            String storeRestriction, String accountRestriction, String productOrBrand, List<String> qualifyingSkus,
            String sizeOrVariety, BigDecimal minimumPurchase, LocalDate startsOn, int usageLimit,
            String conditions, String evidenceMediaType, String evidenceBase64) {
        this(offerId, source, discount, issuingProvider, storeRestriction, accountRestriction, productOrBrand, qualifyingSkus,
            sizeOrVariety, minimumPurchase, startsOn, usageLimit, conditions, evidenceMediaType, evidenceBase64, null, null, null);
    }
    public enum Source { DIGITAL_MANUFACTURER, DIGITAL_STORE, PAPER_MANUFACTURER, PAPER_STORE, PRINTABLE, REGISTER_PRINTED, DIGITAL_OTHER, OTHER }
    public enum Discount { DOLLARS, PERCENT, FREE_ITEM, REBATE, BOGO, REWARD, UNKNOWN }
    public boolean qualificationsNeedReview(){return "NEEDS_REVIEW".equals(offerDetails.get("qualificationStatus"));}
    public boolean checkoutDiscount(){return discount==Discount.DOLLARS||discount==Discount.PERCENT||discount==Discount.FREE_ITEM;}
    public CouponTerms {
        offerDetails=offerDetails==null?java.util.Map.of():java.util.Map.copyOf(offerDetails);
        if(source==null||discount==null||usageLimit<1)throw new IllegalArgumentException("Invalid coupon terms.");
        qualifyingSkus=qualifyingSkus==null?List.of():List.copyOf(qualifyingSkus);
        if(minimumPurchase!=null&&minimumPurchase.signum()<0)throw new IllegalArgumentException("Minimum purchase cannot be negative.");
        if((evidenceMediaType==null)!=(evidenceBase64==null))throw new IllegalArgumentException("Coupon evidence requires its media type and content.");
    }
}
