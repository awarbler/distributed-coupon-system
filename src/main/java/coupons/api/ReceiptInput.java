package coupons.api;

import coupons.*;
import coupons.ReceiptObservations.*;
import coupons.repository.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import static coupons.api.Json.*;

/** Web request boundary only: builds existing receipt models; no persistence or financial formula. */
record ReceiptInput(UUID id,CouponTransaction transaction,ReceiptMeta metadata,List<ReceiptEvidence> evidence,
                    String reviewToken,String suppliedReviewToken,boolean acknowledgeDifference) {
    ReceiptRecord record(){return new ReceiptRecord(id,ReceiptStatus.ACTIVE,1,transaction,metadata,evidence.stream().map(ReceiptEvidence::getEvidenceId).toList());}
    static ReceiptInput read(Map<String,Object> fields) {
        known(fields,"transactionId","retailer","receiptNumber","purchaseDate","printedTime","purchaseChannel","tax","reportedTotal","items","discounts","payments","rrUsed","rrUsageId","earnings","evidence","reviewToken","acknowledgeDifference");
        UUID id=uuid(fields,"transactionId");LocalDate day=LocalDate.parse(required(fields,"purchaseDate"));
        String time=optional(fields,"printedTime");LocalTime parsed=time==null?null:ReceiptTimeInput.parse(time).orElseThrow(()->new IllegalArgumentException("Use a valid receipt time."));
        var metadata=new ReceiptMeta(required(fields,"retailer"),optional(fields,"receiptNumber"),parsed==null?null:day.atTime(parsed),null,ReceiptMeta.PurchaseChannel.valueOf(required(fields,"purchaseChannel")));
        var tx=new CouponTransaction(new ReceiptTotals(decimal(fields,"tax",false),decimal(fields,"reportedTotal",false)));
        var itemRows=rows(fields,"items",100);if(itemRows.isEmpty())throw new IllegalArgumentException("At least one purchased product is required.");
        for(var item:itemRows) {
            known(item,"lineItemId","description","sku","regularUnitPrice","unitPrice","quantity");long quantity=number(item.get("quantity"));if(quantity<1||quantity>Integer.MAX_VALUE)throw new IllegalArgumentException("Quantity must be a positive whole number.");
            tx.addPurchaseItem(new PurchaseItem(uuid(item,"lineItemId"),required(item,"description"),optional(item,"sku"),decimal(item,"regularUnitPrice",true),decimal(item,"unitPrice",false),(int)quantity));
        }
        Map<UUID,DiscountKind> kinds=new HashMap<>();
        for(var d:rows(fields,"discounts",100)) {
            known(d,"applicationId","label","amount","kind","lineItemIds","wholeReceipt","qualifyingQuantities");var kind=DiscountKind.valueOf(required(d,"kind"));
            if(!Set.of(DiscountKind.SALE_PROMOTION,DiscountKind.STORE_COUPON,DiscountKind.MANUFACTURER).contains(kind))throw new IllegalArgumentException("Choose a supported discount type.");
            boolean whole=flag(d,"wholeReceipt");var lines=ids(d,"lineItemIds");if(!whole&&lines.isEmpty())throw new IllegalArgumentException("Select discount products or Whole receipt.");
            var coupon=new CouponApplied(uuid(d,"applicationId"),required(d,"label"),decimal(d,"amount",false),lines,null,whole,quantities(d));tx.addAppliedCoupon(coupon);kinds.put(coupon.getApplicationId(),kind);
        }
        for(var p:rows(fields,"payments",20)) {
            known(p,"paymentId","type","amount","notes");var type=PaymentType.valueOf(required(p,"type"));
            if(!Set.of(PaymentType.CASH,PaymentType.CARD,PaymentType.GIFT_CARD,PaymentType.OTHER,PaymentType.WALGREENS_CASH).contains(type))throw new IllegalArgumentException("Unsupported payment type; record RR use separately.");
            tx.addPayment(new Payment(uuid(p,"paymentId"),type,decimal(p,"amount",false),optional(p,"notes")));
        }
        var rr=decimal(fields,"rrUsed",true);var uses=rr==null||rr.signum()==0?List.<UnidentifiedRR>of():List.of(new UnidentifiedRR(uuid(fields,"rrUsageId"),rr,"Recorded RR use; original printed coupon not yet matched"));
        List<Earning> earnings=new ArrayList<>();
        for(var e:rows(fields,"earnings",100))earnings.add(EarningInput.read(e));
        ReceiptEarningRules.distinct(earnings);
        var evidence=evidence(fields,id);
        tx.setObservations(new ReceiptObservations(day,time,earnings,uses,null,List.of(),kinds));
        var result=new ReceiptInput(id,tx,metadata,List.copyOf(evidence),digest(fields),optional(fields,"reviewToken"),flag(fields,"acknowledgeDifference"));ReceiptValidator.validate(result.record());return result;
    }
    static List<ReceiptEvidence> evidence(Map<String,Object> fields,UUID id) {
        var evidence=new ArrayList<ReceiptEvidence>();int bytes=0;
        for(var e:rows(fields,"evidence",4)) {
            known(e,"evidenceId","mediaType","contentBase64");String type=required(e,"mediaType");if(!Set.of("image/jpeg","image/png","image/webp","application/pdf").contains(type))throw new IllegalArgumentException("Evidence must be JPEG, PNG, WebP or PDF.");
            byte[] content=Base64.getDecoder().decode(required(e,"contentBase64"));bytes+=content.length;if(content.length>5*1024*1024||bytes>10*1024*1024)throw new IllegalArgumentException("Evidence limit: 5 MiB each, 10 MiB total.");
            evidence.add(new ReceiptEvidence(uuid(e,"evidenceId"),id,type,content));
        }
        return List.copyOf(evidence);
    }
    static Map<UUID,Integer> quantities(Map<String,Object> f) {
        var out=new HashMap<UUID,Integer>();if(f.get("qualifyingQuantities")==null)return out;
        if(!(f.get("qualifyingQuantities") instanceof Map<?,?> q))throw new IllegalArgumentException("Quantities must map line IDs to whole numbers.");
        for(var entry:q.entrySet())out.put(UUID.fromString(asText(entry.getKey())),Math.toIntExact(number(entry.getValue())));return out;
    }
    static UUID uuid(Map<String,Object> m,String name){return UUID.fromString(required(m,name));}
    static BigDecimal decimal(Map<String,Object> m,String name,boolean nullable){String text=optional(m,name);if(text==null&&nullable)return null;if(text==null||text.length()>80||!text.matches("[0-9]+(\\.[0-9]+)?"))throw new IllegalArgumentException(name+" must be a nonnegative decimal string.");return new BigDecimal(text);}
    static boolean flag(Map<String,Object> m,String name){var value=m.get(name);if(value!=null&&!(value instanceof Boolean))throw new IllegalArgumentException(name+" must be a boolean.");return Boolean.TRUE.equals(value);}
    static List<UUID> ids(Map<String,Object> m,String name){var v=m.get(name);if(v==null)return List.of();if(!(v instanceof List<?> l))throw new IllegalArgumentException(name+" must be a list.");return l.stream().map(x->UUID.fromString(asText(x))).toList();}
    static List<Map<String,Object>> rows(Map<String,Object> m,String name,int max){var v=m.get(name);if(v==null)return List.of();if(!(v instanceof List<?> l)||l.size()>max)throw new IllegalArgumentException(name+" has an invalid number of rows.");var out=new ArrayList<Map<String,Object>>();for(var x:l){if(!(x instanceof Map<?,?>))throw new IllegalArgumentException(name+" rows must be objects.");@SuppressWarnings("unchecked") var row=(Map<String,Object>)x;out.add(row);}return out;}
    private static String digest(Map<String,Object> m){var copy=new LinkedHashMap<>(m);copy.remove("reviewToken");copy.remove("acknowledgeDifference");try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Json.object(copy).getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
}
