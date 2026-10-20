package coupons;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Manual coupon qualifications; a physical copy remains a separately redeemable coupon identity. */
public record CouponTerms(java.util.UUID offerId, Source source, Discount discount, String issuingProvider,
        String storeRestriction, String accountRestriction, String productOrBrand,
        List<String> qualifyingSkus, String sizeOrVariety, BigDecimal minimumPurchase,
        LocalDate startsOn, int usageLimit, String conditions, String evidenceMediaType,
        String evidenceBase64) implements Serializable {
    public enum Source { DIGITAL_MANUFACTURER, DIGITAL_STORE, PAPER_MANUFACTURER, PAPER_STORE, PRINTABLE, REGISTER_PRINTED, OTHER }
    public enum Discount { DOLLARS, PERCENT, FREE_ITEM }
    public CouponTerms {
        if(source==null||discount==null||usageLimit<1)throw new IllegalArgumentException("Invalid coupon terms.");
        qualifyingSkus=qualifyingSkus==null?List.of():List.copyOf(qualifyingSkus);
        if(minimumPurchase!=null&&minimumPurchase.signum()<0)throw new IllegalArgumentException("Minimum purchase cannot be negative.");
        if((evidenceMediaType==null)!=(evidenceBase64==null))throw new IllegalArgumentException("Coupon evidence requires its media type and content.");
    }
}
