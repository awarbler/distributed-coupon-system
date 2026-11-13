package coupons.receipts;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.*;

/** Reporting and allocation metadata; references existing financial events rather than creating amounts. */
public record ReceiptWorksheetData(Map<UUID,Group> earningGroups, Map<UUID,Use> uses, Integer dayOrder, Map<UUID,PackageDetails> packageDetails) implements Serializable {
    public ReceiptWorksheetData(Map<UUID,Group> groups,Map<UUID,Use> uses,Integer order){this(groups,uses,order,Map.of());}
    public record PackageDetails(BigDecimal size,String unit,Integer itemCount,String itemUnit,BigDecimal secondarySize,String secondaryUnit) implements Serializable {
        public PackageDetails {
            if(size!=null&&size.signum()<=0||secondarySize!=null&&secondarySize.signum()<=0||itemCount!=null&&itemCount<1)throw new IllegalArgumentException("Package sizes and item counts must be positive.");
            unit=normalize(unit);itemUnit=normalize(itemUnit);secondaryUnit=normalize(secondaryUnit);
            if(size!=null&&unit==null||secondarySize!=null&&secondaryUnit==null||itemCount!=null&&itemUnit==null)throw new IllegalArgumentException("Select the unit for each recorded package measure.");
        }
        private static String normalize(String value){
            if(value==null||value.isBlank())return null;
            var v=value.trim().toLowerCase(Locale.ROOT);
            if(v.length()>50)throw new IllegalArgumentException("Package or weight unit must be 50 characters or fewer.");
            return v;
        }
    }
    public enum Group { CASH, GIFT_CARD, STORE_REWARD, POINTS_VALUE }
    public record Use(List<UUID> lineIds, BigDecimal originalUnits, boolean checkoutDeduction) implements Serializable {
        public Use {
            lineIds=List.copyOf(lineIds);
            if(new HashSet<>(lineIds).size()!=lineIds.size() || originalUnits!=null && originalUnits.signum()<=0) throw new IllegalArgumentException("Choose distinct products and positive point units.");
        }
    }
    public ReceiptWorksheetData {
        packageDetails=packageDetails==null?Map.of():Map.copyOf(packageDetails);
        earningGroups=earningGroups==null?Map.of():Map.copyOf(earningGroups);
        uses=uses==null?Map.of():Map.copyOf(uses);
        if(dayOrder!=null && dayOrder<1) throw new IllegalArgumentException("Transaction order must be positive.");
    }
    public static ReceiptWorksheetData empty() { return new ReceiptWorksheetData(Map.of(),Map.of(),null); }
    public Group group(ReceiptObservations.Earning e) {
        if(earningGroups.containsKey(e.earningId())) return earningGroups.get(e.earningId());
        var b=e.confirmed()==null?e.expected():e.confirmed();
        return switch(b.kind()) { case CASH->Group.CASH; case GIFT_CARD->Group.GIFT_CARD; case POINTS->Group.POINTS_VALUE; default->Group.STORE_REWARD; };
    }
}
