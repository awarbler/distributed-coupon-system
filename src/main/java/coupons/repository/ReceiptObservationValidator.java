package coupons.repository;

import coupons.promotions.Coupon;

import coupons.promotions.BenefitKind;
import coupons.receipts.ReceiptObservations.EventKind;
import coupons.receipts.ReceiptObservations.EventReference;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Checks captured relationships without reconstructing or recalculating history. */
public final class ReceiptObservationValidator {
    private static final Currency USD = Currency.getInstance("USD");

    /** Utility class. */
    private ReceiptObservationValidator() { }

    /** An actual event's denomination and recorded value, not a new financial event. */
    public record EventValue(BigDecimal amount, Currency currency) { }

    /**
     * Resolves existing events for display and structured savings explanations.
     * Points and unconfirmed earnings are excluded from monetary savings sources.
     * Existing transaction monetary values are USD under the current calculator.
     */
    public static Map<EventReference, EventValue> monetaryEvents(ReceiptSnapshot contents) {
        Map<EventReference, EventValue> events = new HashMap<>();
        contents.appliedCoupons().forEach(value -> events.put(
                new EventReference(EventKind.COUPON_APPLIED, value.getApplicationId()),
                new EventValue(value.getAppliedAmount(), USD)));
        contents.payments().forEach(value -> events.put(
                new EventReference(EventKind.PAYMENT, value.paymentId()), new EventValue(value.amount(), USD)));
        contents.registerRewardRedemptions().forEach(value -> events.put(
                new EventReference(EventKind.REGISTER_REWARD_REDEMPTION, value.getRedemptionId()),
                new EventValue(value.getRedeemedAmount(), USD)));
        contents.observations().unidentifiedRR().forEach(value -> events.put(
                new EventReference(EventKind.UNIDENTIFIED_RR, value.eventId()), new EventValue(value.amount(), USD)));
        contents.observations().earnings().forEach(value -> {
            if (value.confirmed() != null && value.confirmed().kind() != BenefitKind.POINTS) {
                events.put(new EventReference(EventKind.EARNING, value.earningId()),
                        new EventValue(value.confirmed().amount(), value.confirmed().currency()));
            }
        });
        return Map.copyOf(events);
    }

    /** Lists underlying event IDs, including points and unconfirmed earning observations. */
    public static Set<UUID> eventIds(ReceiptSnapshot contents) {
        Set<UUID> ids = new HashSet<>();
        contents.appliedCoupons().forEach(value -> ids.add(value.getApplicationId()));
        contents.payments().forEach(value -> ids.add(value.paymentId()));
        contents.registerRewardRedemptions().forEach(value -> ids.add(value.getRedemptionId()));
        contents.observations().unidentifiedRR().forEach(value -> ids.add(value.eventId()));
        contents.observations().earnings().forEach(value -> ids.add(value.earningId()));
        return Set.copyOf(ids);
    }

    /**
     * Validates local IDs, scope references, evidence ownership, and link denominations.
     * Arithmetic disagreements remain observations for review, not validation failures.
     * @param contents captured immutable transaction
     * @param evidenceIds evidence belonging to the enclosing receipt revision
     */
    public static void validate(ReceiptSnapshot contents, List<UUID> evidenceIds) {
        var worksheet=contents.observations().worksheet();
        var earningIds=contents.observations().earnings().stream().map(coupons.receipts.ReceiptObservations.Earning::earningId).collect(java.util.stream.Collectors.toSet());
        if(!earningIds.containsAll(worksheet.earningGroups().keySet())) throw new IllegalArgumentException("Worksheet references a missing earning.");
        var useIds=new HashSet<UUID>(); contents.payments().forEach(p->useIds.add(p.paymentId()));
        contents.registerRewardRedemptions().forEach(r->useIds.add(r.getRedemptionId())); contents.observations().unidentifiedRR().forEach(r->useIds.add(r.eventId()));
        var itemIds=contents.purchaseItems().stream().map(PurchaseLineSnap::lineItemId).collect(java.util.stream.Collectors.toSet());
        for(var entry:worksheet.uses().entrySet()) {
            if(!useIds.contains(entry.getKey()) || !itemIds.containsAll(entry.getValue().lineIds())) throw new IllegalArgumentException("Worksheet usage references a missing payment or product.");
            var payment=contents.payments().stream().filter(p->p.paymentId().equals(entry.getKey())).findFirst();
            if(entry.getValue().originalUnits()!=null && (payment.isEmpty() || payment.orElseThrow().accountName()==null)) throw new IllegalArgumentException("Point usage requires a named provider payment.");
            if(entry.getValue().checkoutDeduction() && (payment.isEmpty() || payment.orElseThrow().paymentType()!=coupons.receipts.PaymentType.OTHER || payment.orElseThrow().accountName()==null)) throw new IllegalArgumentException("Additional checkout deduction requires a named Other payment.");
        }
        var ids = new HashSet<UUID>();
        var lines = new HashSet<UUID>();
        contents.purchaseItems().forEach(value -> lines.add(value.lineItemId()));
        var coupons = new HashSet<UUID>();
        contents.appliedCoupons().forEach(value -> {
            unique(ids, value.getApplicationId());
            coupons.add(value.getApplicationId());
            for(var selection:value.getQualifyingQuantities().entrySet()) {
                var line=contents.purchaseItems().stream().filter(l->l.lineItemId().equals(selection.getKey())).findFirst().orElseThrow(()->new IllegalArgumentException("Coupon selection references an unknown line."));
                if(selection.getValue()>line.quantity())throw new IllegalArgumentException("Coupon qualifying quantity exceeds purchased quantity.");
            }
        });
        contents.payments().forEach(value -> unique(ids, value.paymentId()));
        contents.registerRewardRedemptions().forEach(value -> unique(ids, value.getRedemptionId()));
        contents.observations().unidentifiedRR().forEach(value -> unique(ids, value.eventId()));
        var instruments = new HashSet<UUID>();
        var claims = new HashSet<UUID>();
        for (var earning : contents.observations().earnings()) {
            unique(ids, earning.earningId());
            if (!lines.containsAll(earning.lineItemIds())) {
                throw new IllegalArgumentException("Earning references an unknown receipt line.");
            }
            if(earning.details()!=null)for(var selection:earning.details().qualifyingQuantities().entrySet()) {
                var line=contents.purchaseItems().stream().filter(l->l.lineItemId().equals(selection.getKey())).findFirst().orElseThrow(()->new IllegalArgumentException("Selected line missing."));
                if(selection.getValue()>line.quantity())throw new IllegalArgumentException("Qualifying quantity exceeds purchased quantity.");
            }
            if (!evidenceIds.containsAll(earning.evidenceIds())) {
                throw new IllegalArgumentException("Earning evidence is not attached to this receipt.");
            }
            if (earning.instrumentId() != null && !instruments.add(earning.instrumentId())
                    || earning.claimId() != null && !claims.add(earning.claimId())) {
                throw new IllegalArgumentException("The same instrument or claim cannot be counted as two earnings.");
            }
        }
        if (!coupons.containsAll(contents.observations().discountKinds().keySet())) {
            throw new IllegalArgumentException("Discount classification references an unknown deduction.");
        }
        var events = monetaryEvents(contents);
        for (var category : contents.observations().savings()) {
            for (var inclusion : category.inclusions()) {
                EventValue value = events.get(inclusion.event());
                if (value == null || !category.currency().equals(value.currency())) {
                    throw new IllegalArgumentException(
                            "Savings link requires an existing confirmed monetary event in the same currency.");
                }
            }
        }
    }

    /** Prevents separate entries from accidentally reusing the same event identity. */
    private static void unique(HashSet<UUID> ids, UUID id) {
        if (!ids.add(id)) {
            throw new IllegalArgumentException("Duplicate underlying financial event ID.");
        }
    }
}
