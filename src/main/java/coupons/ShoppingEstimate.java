package coupons;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Plan-only estimates. Never posts rewards or changes a receipt or account balance. */
public record ShoppingEstimate(BigDecimal regularTotal, BigDecimal saleTotal, BigDecimal saleSavings,
        BigDecimal couponSavings, BigDecimal expectedRewards, BigDecimal registerCost,
        BigDecimal finalCost, List<String> issues) {
    public static ShoppingEstimate calculate(List<PlannedPurchase> items, Map<UUID,AdvertisedDeal> deals,
            Map<UUID,ClippedCoupon> coupons, LocalDate date, String retailer) {
        BigDecimal regular=BigDecimal.ZERO,sale=BigDecimal.ZERO,rewards=BigDecimal.ZERO,discount=BigDecimal.ZERO;
        boolean known=true,regularKnown=true;
        List<String> issues=new ArrayList<>();
        Map<UUID,BigDecimal> linePrices=new HashMap<>();
        for(var item:items) {
            var r=item.regularUnitPrice();
            if(r==null){regularKnown=false;issues.add("Regular price missing: "+item.productName());}else regular=regular.add(r.multiply(BigDecimal.valueOf(item.quantity())));
            var unit=item.saleUnitPrice()!=null?item.saleUnitPrice():item.regularUnitPrice()!=null?item.regularUnitPrice():item.targetUnitPrice();
            if(unit==null){known=false;issues.add("Price missing: "+item.productName());}else linePrices.put(item.plannedPurchaseId(),unit.multiply(BigDecimal.valueOf(item.quantity())));
        }
        for(var entry:deals.entrySet()) {
            var d=entry.getValue();var terms=d.pricing();if(terms==null)continue;
            var linked=items.stream().filter(i->entry.getKey().equals(i.advertisedDealId())).toList();if(linked.isEmpty())continue;
            boolean valid=within(date,d.validFrom(),d.validThrough())&&(d.retailer()==null||retailer!=null&&d.retailer().equalsIgnoreCase(retailer));
            if(d.validFrom()==null||d.validThrough()==null)issues.add("Deal validity dates incomplete; arithmetic is provisional: "+d.title());
            if(terms.loyaltyRequirement()!=null||terms.qualifications()!=null||terms.exclusions()!=null)issues.add("Review recorded qualifications, loyalty terms and exclusions: "+d.title());
            if(!valid){issues.add("Deal date/store not qualified: "+d.title());continue;}
            var eligible=linked.stream().filter(i->terms.qualifyingSkus().isEmpty()||(i.productCode()!=null&&terms.qualifyingSkus().contains(i.productCode()))).toList();
            long quantity=eligible.stream().mapToLong(PlannedPurchase::quantity).sum();
            var spend=eligible.stream().map(i->linePrices.get(i.plannedPurchaseId())).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);
            long count=quantity/terms.minimumQuantity();
            if(terms.type()==DealTerms.Type.SPEND_REWARD){
                var groups=spend.divideToIntegralValue(terms.minimumSpend());if(!terms.repeatable())groups=groups.min(BigDecimal.ONE);if(terms.limit()!=null)groups=groups.min(BigDecimal.valueOf(terms.limit()));
                if(groups.signum()==0)issues.add("Threshold not met: "+d.title());
                if(eligible.stream().allMatch(i->linePrices.containsKey(i.plannedPurchaseId())))rewards=rewards.add(terms.rewardAmount().multiply(groups));else issues.add("Cannot estimate qualifying spend: "+d.title());continue;
            }
            if(!terms.repeatable())count=Math.min(count,1);if(terms.limit()!=null)count=Math.min(count,terms.limit());
            if(count<1){issues.add("Threshold not met: "+d.title());continue;}
            if(terms.type()==DealTerms.Type.SPEND_REWARD||terms.type()==DealTerms.Type.QUANTITY_REWARD) {
                if(eligible.stream().allMatch(i->linePrices.containsKey(i.plannedPurchaseId())))rewards=rewards.add(terms.rewardAmount().multiply(BigDecimal.valueOf(count)));
                else issues.add("Cannot estimate qualifying spend: "+d.title());
                continue;
            }
            if(terms.type()==DealTerms.Type.BUY_GET&&!terms.cheapestItemsDiscounted()){issues.add("Buy/get discounted product allocation is not recorded; using manual prices: "+d.title());continue;}
            var priced=eligible.stream().filter(i->"DEAL".equals(i.priceSource())).toList();
            if(priced.size()!=eligible.size()){issues.add("Manual pricing retained for deal: "+d.title());continue;}
            if(terms.type()==DealTerms.Type.SALE) {
                for(var i:priced)linePrices.put(i.plannedPurchaseId(),terms.promotionalPrice().multiply(BigDecimal.valueOf(i.quantity())));
            } else {
                // Pool linked units across all products. Full bundles only; remainder keeps each unit's recorded price.
                List<Map.Entry<BigDecimal,Integer>> units=new ArrayList<>();
                for(var i:priced){var unit=i.regularUnitPrice()!=null?i.regularUnitPrice():i.targetUnitPrice();if(unit==null){known=false;continue;}units.add(Map.entry(unit,i.quantity()));}
                if(units.size()!=priced.size())continue;
                BigDecimal total=units.stream().map(u->u.getKey().multiply(BigDecimal.valueOf(u.getValue()))).reduce(BigDecimal.ZERO,BigDecimal::add);
                if(terms.type()==DealTerms.Type.MULTIBUY) {
                    units.sort(Map.Entry.<BigDecimal,Integer>comparingByKey().reversed());long remaining=Math.multiplyExact(count,terms.minimumQuantity());
                    for(var unit:units){long n=Math.min(remaining,unit.getValue());total=total.subtract(unit.getKey().multiply(BigDecimal.valueOf(n)));remaining-=n;}
                    total=total.add(terms.promotionalPrice().multiply(BigDecimal.valueOf(count)));
                }else {
                    long bundles=quantity/Math.addExact((long)terms.minimumQuantity(),terms.freeQuantity());if(!terms.repeatable())bundles=Math.min(bundles,1);if(terms.limit()!=null)bundles=Math.min(bundles,terms.limit());
                    units.sort(Map.Entry.comparingByKey());long remaining=Math.multiplyExact(bundles,terms.freeQuantity());
                    for(var unit:units){long n=Math.min(remaining,unit.getValue());total=total.subtract(unit.getKey().subtract(terms.promotionalPrice().min(unit.getKey())).multiply(BigDecimal.valueOf(n)));remaining-=n;}
                }
                for(var i:priced)linePrices.put(i.plannedPurchaseId(),BigDecimal.ZERO);
                linePrices.put(priced.getFirst().plannedPurchaseId(),total);
            }
        }
        sale=linePrices.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add);
        Set<UUID> used=new HashSet<>();Map<UUID,Integer> couponUses=new HashMap<>();
        for(var item:items)for(var id:item.clippedCouponIds())if(used.add(id)) {
            var coupon=coupons.get(id);
            if(coupon==null||coupon.status()!=CouponStatus.CLIPPED||!within(date,coupon.clippedOn(),coupon.coupon().getExpirationDate())){issues.add("Coupon unavailable or expired: "+id);continue;}
            var terms=coupon.coupon().getTerms();
            if(terms!=null && (!within(date,terms.startsOn(),coupon.coupon().getExpirationDate()) || terms.storeRestriction()!=null&&(retailer==null||!terms.storeRestriction().equalsIgnoreCase(retailer)))) {issues.add("Coupon date/store restriction not met: "+coupon.coupon().getCouponDescription());continue;}
            if(terms!=null && terms.offerId()!=null && couponUses.getOrDefault(terms.offerId(),0)>=terms.usageLimit()){issues.add("Coupon usage limit reached: "+coupon.coupon().getCouponDescription());continue;}
            var eligible=items.stream().filter(i->i.clippedCouponIds().contains(id) && (terms==null||terms.qualifyingSkus().isEmpty()||(i.productCode()!=null&&terms.qualifyingSkus().contains(i.productCode())))).toList();
            long quantity=eligible.stream().mapToLong(PlannedPurchase::quantity).sum();
            if(quantity<coupon.coupon().getRequiredQuantity()){issues.add("Coupon quantity not met: "+coupon.coupon().getCouponDescription());continue;}
            var eligiblePrice=eligible.stream().map(i->linePrices.getOrDefault(i.plannedPurchaseId(),BigDecimal.ZERO)).reduce(BigDecimal.ZERO,BigDecimal::add);
            if(terms!=null && terms.minimumPurchase()!=null && eligiblePrice.compareTo(terms.minimumPurchase())<0){issues.add("Coupon minimum spend not met: "+coupon.coupon().getCouponDescription());continue;}
            var value=coupon.coupon().getDiscountAmount();
            if(terms!=null && terms.discount()==CouponTerms.Discount.PERCENT)value=eligiblePrice.multiply(value).movePointLeft(2);
            if(terms!=null && terms.discount()==CouponTerms.Discount.FREE_ITEM) {
                if(eligible.stream().anyMatch(i->"DEAL".equals(i.priceSource())&&i.advertisedDealId()!=null&&deals.containsKey(i.advertisedDealId())&&deals.get(i.advertisedDealId()).pricing()!=null&&Set.of(DealTerms.Type.MULTIBUY,DealTerms.Type.BUY_GET).contains(deals.get(i.advertisedDealId()).pricing().type()))){issues.add("Free-item coupon allocation with bundle pricing needs review: "+coupon.coupon().getCouponDescription());continue;}
                value=eligible.stream().map(i->linePrices.getOrDefault(i.plannedPurchaseId(),BigDecimal.ZERO).divide(BigDecimal.valueOf(i.quantity()))).min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
            }
            discount=discount.add(value.min(eligiblePrice));if(terms!=null&&terms.offerId()!=null)couponUses.merge(terms.offerId(),1,Integer::sum);
        }
        discount=discount.min(sale);var register=sale.subtract(discount);
        return new ShoppingEstimate(regularKnown?regular:null,known?sale:null,regularKnown&&known?regular.subtract(sale):null,known?discount:null,rewards,known?register:null,known?register.subtract(rewards):null,List.copyOf(issues));
    }
    private static boolean within(LocalDate date,LocalDate from,LocalDate through){return date!=null&&(from==null||!date.isBefore(from))&&(through==null||!date.isAfter(through));}
}
