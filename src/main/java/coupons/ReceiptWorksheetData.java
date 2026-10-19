package coupons;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.*;

/** Reporting and allocation metadata; references existing financial events rather than creating amounts. */
public record ReceiptWorksheetData(Map<UUID,Group> earningGroups, Map<UUID,Use> uses, Integer dayOrder) implements Serializable {
    public enum Group { CASH, GIFT_CARD, STORE_REWARD, POINTS_VALUE }
    public record Use(List<UUID> lineIds, BigDecimal originalUnits, boolean checkoutDeduction) implements Serializable {
        public Use {
            lineIds=List.copyOf(lineIds);
            if(new HashSet<>(lineIds).size()!=lineIds.size() || originalUnits!=null && originalUnits.signum()<=0) throw new IllegalArgumentException("Choose distinct products and positive point units.");
        }
    }
    public ReceiptWorksheetData {
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
