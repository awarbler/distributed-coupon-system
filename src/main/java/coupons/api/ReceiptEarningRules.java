package coupons.api;
import coupons.receipts.ReceiptObservations.Earning;
import java.util.*;
/** Reject identical new observations, without changing legacy historical earnings. */
final class ReceiptEarningRules {
    static void distinct(List<Earning> earnings) {
        Set<Object> seen=new HashSet<>();
        for(var e:earnings) {
            var amount=e.expected()==null?e.confirmed():e.expected();
            var signature=Arrays.asList(e.kind(),e.provider().trim().toLowerCase(Locale.ROOT),e.offerReference()==null?null:e.offerReference().trim().toLowerCase(Locale.ROOT),amount.kind(),amount.amount().stripTrailingZeros(),new HashSet<>(e.lineItemIds()),e.requirement(),e.details()==null?null:e.details().submissionReference());
            if(!seen.add(signature))throw new IllegalArgumentException("Duplicate earning: use a distinct offer/reference for a separate legitimate rebate.");
        }
    }
    static void additional(List<Earning> existing,Earning added){for(var old:existing)distinct(List.of(old,added));}
}
