package coupons.api;

import coupons.promotions.ClippedCoupon;
import coupons.promotions.CouponStatus;
import coupons.promotions.CouponTerms;
import coupons.shopping.ShoppingPlan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Only fully recorded SKU/date/store dollar coupons are eligible for automatic attachment. */
final class ShoppingOfferMatch {
    private ShoppingOfferMatch() {}
    static List<ShoppingPlan.ScenarioCoupon> match(ShoppingPlan.Scenario scenario,String store,LocalDate day,List<ClippedCoupon> coupons) {
        if(day==null)return List.of();
        var result=new ArrayList<ShoppingPlan.ScenarioCoupon>();var usedOffers=new HashSet<UUID>();
        var totals=scenario.estimate(store);
        @SuppressWarnings("unchecked") var lines=(List<Map<String,Object>>)totals.get("items");
        for(var clipped:coupons) {
            var c=clipped.coupon();var terms=c.getTerms();
            if(clipped.clippedOn()!=null&&day.isBefore(clipped.clippedOn())||clipped.status()!=CouponStatus.CLIPPED||terms==null||terms.discount()!=CouponTerms.Discount.DOLLARS||terms.qualifyingSkus().isEmpty()
                ||c.getExpirationDate()==null||day.isAfter(c.getExpirationDate())||terms.startsOn()!=null&&day.isBefore(terms.startsOn())
                ||present(terms.accountRestriction())||present(terms.conditions())||present(terms.sizeOrVariety())
                ||present(terms.storeRestriction())&&(store==null||!terms.storeRestriction().trim().equalsIgnoreCase(store.trim())))continue;
            var eligible=scenario.items().stream().filter(l->l.sku()!=null&&terms.qualifyingSkus().contains(l.sku())).toList();
            long quantity=eligible.stream().mapToLong(ShoppingPlan.Line::quantity).sum();
            var ids=eligible.stream().map(ShoppingPlan.Line::id).toList();
            BigDecimal available=lines.stream().filter(l->ids.contains(l.get("id"))).map(l->(BigDecimal)l.get("totalPrice")).reduce(BigDecimal.ZERO,BigDecimal::add);
            int required=c.getRequiredQuantity();
            if(quantity<required||available.compareTo(c.getDiscountAmount())<0||terms.minimumPurchase()!=null&&available.compareTo(terms.minimumPurchase())<0||terms.offerId()!=null&&!usedOffers.add(terms.offerId()))continue;
            result.add(new ShoppingPlan.ScenarioCoupon(UUID.nameUUIDFromBytes(("plan-match:"+clipped.couponId()).getBytes(java.nio.charset.StandardCharsets.UTF_8)),c.getCouponDescription(),c.getDiscountAmount(),required,ids,clipped.couponId(),c.getCouponType().name().equals("STORE")?"STORE_COUPON":"MANUFACTURER"));
        }
        return result;
    }
    private static boolean present(String text){return text!=null&&!text.isBlank();}
}
