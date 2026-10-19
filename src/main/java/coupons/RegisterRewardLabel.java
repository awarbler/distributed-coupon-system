package coupons;

import coupons.ReceiptObservations.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/** Short display labels only; original terms, product names and UUID associations remain unchanged. */
final class RegisterRewardLabel {
    private RegisterRewardLabel() { }
    static String lines(CouponTransaction tx, Earning earning) {
        if (earning.scope() == Scope.RECEIPT) return "Whole receipt";
        if (earning.scope() == Scope.UNKNOWN || earning.lineItemIds().isEmpty()) return "Unknown";
        var numbers = new ArrayList<String>();
        for (int i=0;i<tx.getPurchaseItems().size();i++)
            if (earning.lineItemIds().contains(tx.getPurchaseItems().get(i).getLineItemId())) numbers.add(Integer.toString(i+1));
        return numbers.size()==earning.lineItemIds().size() ? String.join(", ",numbers) : String.join(", ",numbers)+" (missing line)";
    }
    static String products(CouponTransaction tx, List<UUID> ids) {
        if(ids.isEmpty()) return "Products unknown";
        var numbers=new ArrayList<String>();
        for(int i=0;i<tx.getPurchaseItems().size();i++)
            if(ids.contains(tx.getPurchaseItems().get(i).getLineItemId())) numbers.add(Integer.toString(i+1));
        return "Receipt lines " + String.join(", ",numbers)
                + (numbers.size()==ids.size()?"":" (missing line)");
    }
    static String products(CouponTransaction tx, Earning earning) {
        return switch(earning.scope()) {
            case RECEIPT -> "Whole receipt";
            case UNKNOWN -> "Products unknown";
            default -> products(tx,earning.lineItemIds());
        };
    }
    static String couponSummary(CouponTransaction tx,Earning earning) {
        String lines=lines(tx,earning);
        if(earning.scope()==Scope.RECEIPT||earning.scope()==Scope.UNKNOWN) return lines+" — "+offer(tx,earning);
        String offer=offer(tx,earning);
        if(earning.requirement()==null||earning.requirement().type()==null) return products(tx,earning);
        if(offer.startsWith("RR — ")) offer=offer.substring(5);
        return "Lines "+lines.replace(", ",",")+" — "+offer.replace(" — "," ");
    }
    static String offer(CouponTransaction tx, Earning earning) {
        var terms=earning.requirement();
        if (terms==null || terms.type()==null || terms.amount()==null) return "RR — receipt lines " + lines(tx,earning);
        var benefit=earning.confirmed()==null?earning.expected():earning.confirmed();
        if (benefit==null) return "RR — receipt lines " + lines(tx,earning);
        var brands=tx.getPurchaseItems().stream().filter(i->earning.lineItemIds().contains(i.getLineItemId()))
                .map(i->i.getProductName().trim().split("\\s+")[0]).map(ReceiptReport::title).collect(Collectors.toCollection(LinkedHashSet::new));
        String brand=brands.size()==1?brands.iterator().next():"RR";
        if (earning.offerReference()!=null && earning.offerReference().toLowerCase(Locale.ROOT).matches(".*\\bp\\s*&?\\s*g\\b.*")) brand="P&G";
        String required=number(terms.amount());
        String action=terms.type()==PromotionType.BUY?"buy "+required:terms.type()==PromotionType.SPEND?"spend $"+required:null;
        if (action==null) return "RR — receipt lines "+lines(tx,earning);
        return brand+" — "+action+", get $"+number(benefit.amount())+" RR";
    }
    private static String number(BigDecimal number) { return number.stripTrailingZeros().toPlainString(); }
}
