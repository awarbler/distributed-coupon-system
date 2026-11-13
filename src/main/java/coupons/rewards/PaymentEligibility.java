package coupons.rewards;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Shared payment acceptance facts. No retailer policy is inferred for open-network/promotional cards. */
public final class PaymentEligibility {
    private PaymentEligibility() {}
    public record Rule(UUID instrumentId,List<String> acceptedRetailers,List<String> excludedRetailers,
            String network,LocalDate expiresOn,LocalDate evaluatedOn,boolean verified,BigDecimal available) implements Serializable {
        public Rule {acceptedRetailers=acceptedRetailers==null?List.of():List.copyOf(acceptedRetailers);excludedRetailers=excludedRetailers==null?List.of():List.copyOf(excludedRetailers);}
    }
    public static String store(String value){if(value==null)return "";String key=value.strip().toUpperCase(Locale.ROOT);for(String name:List.of("WALGREENS","CVS","WALMART"))if(key.equals(name)||key.startsWith(name+" ")||key.startsWith(name+"#"))return name;return key;}
    public static String check(String retailer,String type,Rule rule,BigDecimal amount,LocalDate day){
        if(amount==null||amount.signum()==0||Set.of("CASH","CARD").contains(type))return "Met";
        String target=store(retailer);String required=switch(type){case "REGISTER_REWARD","WALGREENS_CASH"->"WALGREENS";case "ECB"->"CVS";case "WALMART_CASH"->"WALMART";default->"";};
        if(!required.isEmpty())return target.isEmpty()?"Needs Review":required.equals(target)?"Met":"Not Met";
        if(!type.equals("GIFT_CARD"))return "Met";
        if(rule==null||target.isBlank())return "Needs Review";
        if(rule.excludedRetailers().stream().map(PaymentEligibility::store).anyMatch(target::equals))return "Not Met";
        if(rule.expiresOn()!=null&&(day==null||day.isAfter(rule.expiresOn())))return day==null?"Needs Review":"Not Met";
        if(rule.available()!=null&&amount.compareTo(rule.available())>0)return "Not Met";
        if(!rule.verified()||rule.available()==null)return "Needs Review";
        return rule.acceptedRetailers().stream().map(PaymentEligibility::store).anyMatch(target::equals)?"Met":"Not Met";
    }
    public static Rule fromInstrument(Map<String,Object> card,LocalDate day){
        String merchant=store(Objects.toString(card.get("provider"),""));var accepted=strings(card.get("acceptedRetailers"));boolean explicit=Boolean.TRUE.equals(card.get("eligibilityVerified"));
        if(accepted.isEmpty()&&Set.of("WALGREENS","WALMART","CVS").contains(merchant)){accepted=List.of(merchant);explicit=true;}
        BigDecimal available=Boolean.TRUE.equals(card.get("historyComplete"))&&"ACTIVE".equals(card.get("status"))&&card.get("calculated") instanceof BigDecimal value?value:null;
        if(card.get("openingDate") instanceof LocalDate date&&day!=null&&date.isAfter(day)||card.get("balanceDate") instanceof LocalDate balanceDate&&day!=null&&balanceDate.isAfter(day))available=null;
        UUID id=card.get("id") instanceof UUID value?value:UUID.fromString(card.get("id").toString());
        return new Rule(id,accepted,strings(card.get("excludedRetailers")),Objects.toString(card.get("paymentNetwork"),null),(LocalDate)card.get("expiresOn"),day,explicit,available);
    }
    public static List<String> strings(Object raw){if(raw instanceof List<?> values)return values.stream().map(Object::toString).toList();if(raw instanceof String[] values)return List.of(values);return List.of();}
}
