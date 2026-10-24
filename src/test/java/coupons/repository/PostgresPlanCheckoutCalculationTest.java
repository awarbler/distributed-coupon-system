
package coupons.repository;

import coupons.repository.PostgresPlatformRepository.ReceiptRRMatching;

import coupons.ReceiptObservations.*;
import java.time.*;

import coupons.*;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import coupons.ShoppingEstimate;

class PostgresPlanCheckoutCalculationTest {

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private static Map<String, Object> checkout() {
        Map<String, Object> values = new LinkedHashMap<>();

        values.put("planId", UUID.randomUUID());
        values.put("revision", 0L);
        values.put("promoCode", null);
        values.put("promoDiscount", money("0.00"));
        values.put("taxRate", money("8.25"));
        values.put("taxableBase", null);
        values.put("walgreensCashUsed", money("0.00"));
        values.put("registerRewardsCoupon", money("0.00"));
        values.put("giftCardUsed", money("0.00"));
        values.put("cashPayment", money("0.00"));
        values.put("cardPayment", money("0.00"));
        values.put("otherPayment", money("0.00"));
        values.put("notes", null);

        return values;
    }

    private static ShoppingEstimate estimate() {
        return new ShoppingEstimate(
                money("44.96"),
                money("31.98"),
                money("12.98"),
                money("6.00"),
                money("2.00"),
                money("25.98"),
                money("23.98"),
                List.of()
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> summary(
            Map<String, Object> checkout) {

        return (Map<String, Object>) checkout.get("summary");
    }

    @Test
    void calculatesTaxOnMerchandiseAndKeepsExpectedRewardsSeparate() {
        Map<String, Object> result =
                PostgresPlanCheckoutRepository.calculateView(
                        checkout(), estimate());

        Map<String, Object> totals = summary(result);

        assertEquals(money("2.64"), totals.get("tax"));
        assertEquals(money("28.62"), totals.get("amountDue"));
        assertEquals(money("28.62"), totals.get("remainingPayment"));
        assertEquals(money("2.00"), totals.get("expectedRewards"));
        assertEquals(true, result.get("estimateComplete"));
    }

    @Test
    void subtractsRegisterRewardsAsCouponAndWalgreensCashAsPayment() {
        Map<String, Object> values = checkout();

        values.put("registerRewardsCoupon", money("3.00"));
        values.put("walgreensCashUsed", money("5.00"));

        Map<String, Object> totals = summary(
                PostgresPlanCheckoutRepository.calculateView(
                        values, estimate()));

        assertEquals(money("25.62"), totals.get("amountDue"));
        assertEquals(money("20.62"), totals.get("remainingPayment"));
    }

    @Test
    void subtractsAllPaymentMethods() {
        Map<String, Object> values = checkout();

        values.put("giftCardUsed", money("10.00"));
        values.put("cashPayment", money("5.00"));
        values.put("cardPayment", money("7.00"));
        values.put("otherPayment", money("2.00"));

        Map<String, Object> totals = summary(
                PostgresPlanCheckoutRepository.calculateView(
                        values, estimate()));

        assertEquals(money("28.62"), totals.get("amountDue"));
        assertEquals(money("4.62"), totals.get("remainingPayment"));
    }

    @Test
    void usesExplicitTaxableBaseWhenProvided() {
        Map<String, Object> values = checkout();

        values.put("taxableBase", money("20.00"));

        Map<String, Object> totals = summary(
                PostgresPlanCheckoutRepository.calculateView(
                        values, estimate()));

        assertEquals(money("1.65"), totals.get("tax"));
        assertEquals(money("27.63"), totals.get("amountDue"));
    }

    @Test
    void missingTaxRateKeepsFinalAmountsIncomplete() {
        Map<String, Object> values = checkout();

        values.put("taxRate", null);

        Map<String, Object> result =
                PostgresPlanCheckoutRepository.calculateView(
                        values, estimate());

        Map<String, Object> totals = summary(result);

        assertNull(totals.get("tax"));
        assertNull(totals.get("amountDue"));
        assertNull(totals.get("remainingPayment"));
        assertEquals(false, result.get("estimateComplete"));
    }

    @Test
    void warnsWhenPlannedPaymentsExceedAmountDue() {
        Map<String, Object> values = checkout();

        values.put("cashPayment", money("30.00"));

        Map<String, Object> totals = summary(
                PostgresPlanCheckoutRepository.calculateView(
                        values, estimate()));

        assertEquals(money("-1.38"), totals.get("remainingPayment"));

        assertTrue(
                ((List<?>) totals.get("issues")).stream()
                        .anyMatch(issue -> issue.toString()
                                .contains("payments exceed"))
        );
    }

    @Test
    void excessivePromotionsCannotMakeAmountDueNegative() {
        Map<String, Object> values = checkout();

        values.put("promoDiscount", money("40.00"));

        Map<String, Object> totals = summary(
                PostgresPlanCheckoutRepository.calculateView(
                        values, estimate()));

        assertEquals(money("2.64"), totals.get("amountDue"));

        assertTrue(
                ((List<?>) totals.get("issues")).stream()
                        .anyMatch(issue -> issue.toString()
                                .contains("discounts exceed"))
        );
    }

    @Test
    void incompleteMerchandiseEstimateCannotProduceAmountDue() {
        ShoppingEstimate incomplete = new ShoppingEstimate(
                null, null, null, null,
                money("0.00"), null, null, List.of());

        Map<String, Object> result =
                PostgresPlanCheckoutRepository.calculateView(
                        checkout(), incomplete);

        assertNull(summary(result).get("amountDue"));
        assertEquals(false, result.get("estimateComplete"));
    }

    @Test void unknownRewardsDoNotAlterAmountDueAndRemainIncomplete() {
        var pricing = new ShoppingEstimate(money("10"), money("10"), money("0"), money("0"),
                null, money("10"), null, List.of(), List.of(), money("5"), false, true);
        var result = PostgresPlanCheckoutRepository.calculateView(checkout(), pricing);
        var summary = (Map<?, ?>) result.get("summary");
        assertNull(summary.get("expectedRewards"));
        assertEquals(money("5"), summary.get("knownPotentialRewards"));
        assertEquals(false, summary.get("rewardsComplete"));
        assertEquals(true, summary.get("reviewRequired"));
        assertEquals(money("10.83"), summary.get("amountDue"));
        assertTrue(((List<?>) summary.get("issues")).size() >= 2);
    }
}

class ReceiptPeriodsTest {
    private ReceiptRecord receipt(String date, String paid, boolean payment) {
        var tx = new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO, new BigDecimal(paid)));
        tx.addPurchaseItem(new PurchaseItem("Synthetic", null, new BigDecimal("10"), new BigDecimal(paid), 1));
        if (payment) tx.addPayment(new Payment(PaymentType.CARD, new BigDecimal(paid)));
        tx.setObservations(new ReceiptObservations(LocalDate.parse(date), "940 am", List.of(), List.of(), null, List.of(), Map.of()));
        return new ReceiptRecord(UUID.randomUUID(), ReceiptStatus.ACTIVE, 1, tx, null);
    }
    @SuppressWarnings("unchecked")
    private Map<String, Object> summary(Map<String, Object> periods, String key) {
        return (Map<String, Object>) periods.get(key);
    }
    @Test void groupsAllReceiptsAcrossWeekMonthAndYearBoundariesWithoutPagination() {
        var records = new ArrayList<ReceiptRecord>();
        for (int i = 0; i < 6; i++) records.add(receipt("2026-10-07", "1.01", true));
        records.add(receipt("2026-10-08", "2", true));
        records.add(receipt("2026-10-29", "3", true));
        records.add(receipt("2026-11-01", "4", true));
        records.add(receipt("2027-01-01", "5", true));
        var deleted = receipt("2026-10-07", "100", true);
        records.add(deleted);
        records.add(new ReceiptRecord(deleted.transactionId(), ReceiptStatus.DELETED, 2, deleted.transaction(), null));
        var periods = PostgresPlatformRepository.receiptPeriods(records);
        assertEquals(6, summary(periods, "week:2026-10:1").get("receipts"));
        assertEquals(new BigDecimal("6.06"), summary(periods, "day:2026-10-07").get("recordedCashCardPaid"));
        assertEquals(1, summary(periods, "week:2026-10:2").get("receipts"));
        assertEquals(1, summary(periods, "week:2026-10:5").get("receipts"));
        assertEquals(8, summary(periods, "month:2026-10").get("receipts"));
        assertEquals(1, summary(periods, "month:2026-11").get("receipts"));
        assertEquals(1, summary(periods, "month:2027-01").get("receipts"));
        assertEquals(0, summary(periods, "empty").get("receipts"));
    }
    @Test void missingPaymentKeepsKnownSubtotalSeparateFromUnknownSavings() {
        var periods = PostgresPlatformRepository.receiptPeriods(List.of(receipt("2026-10-01", "2", false), receipt("2026-10-02", "1.23", true)));
        var totals = summary(periods, "month:2026-10");
        assertEquals(new BigDecimal("1.23"), totals.get("recordedCashCardPaid"));
        assertEquals(false, totals.get("recordedCashCardComplete"));
        assertNull(totals.get("totalSaved"));
        assertFalse(((List<?>) totals.get("issues")).isEmpty());
    }
    @Test void shoppingWeeksIncludeCrossMonthAndCrossYearDates() {
        var periods = PostgresPlatformRepository.receiptPeriods(List.of(
                receipt("2026-09-27", "1", true), receipt("2026-10-01", "2", true),
                receipt("2026-10-03", "3", true), receipt("2026-10-04", "4", true),
                receipt("2027-01-01", "5", true)));
        assertEquals(3, summary(periods, "shoppingWeek:2026-09-27").get("receipts"));
        assertEquals(new BigDecimal("6"), summary(periods, "shoppingWeek:2026-09-27").get("recordedCashCardPaid"));
        assertEquals(1, summary(periods, "shoppingWeek:2026-10-04").get("receipts"));
        assertEquals(1, summary(periods, "shoppingWeek:2026-12-27").get("receipts"));
    }

    @Test void separatesPromotionsManufacturerStoreAndUnclassifiedWithoutChangingTotal() {
        var tx=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,BigDecimal.ZERO));
        var kinds=new HashMap<UUID,ReceiptObservations.DiscountKind>();
        var values=List.of(ReceiptObservations.DiscountKind.SALE_PROMOTION,ReceiptObservations.DiscountKind.MANUFACTURER,ReceiptObservations.DiscountKind.STORE_COUPON,ReceiptObservations.DiscountKind.STORE,ReceiptObservations.DiscountKind.OTHER);
        for(int i=0;i<6;i++) {
            UUID id=UUID.randomUUID();
            tx.addAppliedCoupon(new CouponApplied(id,"Do not infer manufacturer from this label",new BigDecimal("1.01"),List.of(),null,true));
            if(i<values.size()) kinds.put(id,values.get(i));
        }
        tx.setObservations(new ReceiptObservations(null,null,List.of(),List.of(),null,List.of(),kinds));
        var split=PostgresPlatformRepository.discountBreakdown(tx);
        assertEquals(new BigDecimal("1.01"),split.get("salePromotions"));
        assertEquals(new BigDecimal("1.01"),split.get("manufacturerCoupons"));
        assertEquals(new BigDecimal("2.02"),split.get("storeDiscounts"));
        assertEquals(new BigDecimal("2.02"),split.get("unclassified"));
        assertEquals(new TransactionCalculator().calculateCouponTotal(tx),split.values().stream().map(v->(BigDecimal)v).reduce(BigDecimal.ZERO,BigDecimal::add));
    }

}

class ReceiptRRMatchingTest {
    final InMemoryStore store=new InMemoryStore();
    final InMemoryTransactionRepository repo=new InMemoryTransactionRepository(store,Clock.systemUTC());
    final LocalDate day=LocalDate.of(2026,10,5);
    UUID save(LocalDate date,String time,List<Earning> earnings,List<UnidentifiedRR> uses) {
        UUID id=UUID.randomUUID();var tx=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,BigDecimal.ZERO));
        tx.setObservations(new ReceiptObservations(date,time,earnings,uses,null,List.of(),Map.of()));
        repo.save(id,tx,new ReceiptMeta("Walgreens",id.toString(),null,null),List.of(),new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptCreateCtx("test"));return id;
    }
    Earning earning(String amount,LocalDate expiry,RebateStatus status) {
        return new Earning(UUID.randomUUID(),EarningKind.REGISTER_REWARD,"Walgreens","Lysol — Buy 2, Get $5 RR",Scope.RECEIPT,List.of(),null,new RebateBenefit(BenefitKind.STORE_COUPON,new BigDecimal(amount),Currency.getInstance("USD"),"Walgreens"),status,expiry,null,null,List.of());
    }
    @Test void listsOnlyUnusedPrintedUnexpiredEqualValueEarlierRewardsAndDoesNotWrite() {
        var valid=earning("5",day.plusDays(1),RebateStatus.PAID);save(day.minusDays(1),"9:56 AM",List.of(valid),List.of());
        save(day.minusDays(1),"9 AM",List.of(earning("7",null,RebateStatus.PAID),earning("5",day.minusDays(1),RebateStatus.PAID),earning("5",null,RebateStatus.CREDITED)),List.of());
        save(day,"5 PM",List.of(earning("5",null,RebateStatus.PAID)),List.of());
        var use=new UnidentifiedRR(UUID.randomUUID(),new BigDecimal("5"),"Lysol");var spent=save(day,"4:23 PM",List.of(),List.of(use));
        var state=store.state;var options=ReceiptRRMatching.options(state,spent);
        assertSame(state,store.state);assertTrue(repo.rrUsageLinks().isEmpty());assertEquals(1,options.getFirst().options().size());assertEquals(valid.earningId(),options.getFirst().options().getFirst().sourceId());
        repo.linkRRUsage(new RRUsageLink(valid.earningId(),true,use.eventId(),spent,use.amount(),"Matched","owner",Instant.now()));
        var after=ReceiptRRMatching.options(store.state,spent).getFirst();assertTrue(after.matched());assertTrue(after.options().isEmpty());
        var next=save(day,"6 PM",List.of(),List.of(new UnidentifiedRR(UUID.randomUUID(),new BigDecimal("5"),"Another RR")));
        assertTrue(ReceiptRRMatching.options(store.state,next).getFirst().options().stream().noneMatch(o->o.sourceId().equals(valid.earningId())));
    }
    @Test void permitsEligibleStandaloneCouponWithoutInventingAnEarningReceipt() {
        var reward=new RegisterReward("Standalone Lysol RR",new BigDecimal("5"),day.minusDays(1),day.plusDays(3),null);repo.issuedRewards().save(reward);
        var id=save(day,"4 PM",List.of(),List.of(new UnidentifiedRR(UUID.randomUUID(),new BigDecimal("5"),"Printed RR")));
        var option=ReceiptRRMatching.options(store.state,id).getFirst().options().getFirst();
        assertFalse(option.receiptEarning());assertEquals(reward.getRewardId(),option.sourceId());assertNull(option.sourceReceiptNumber());
    }
    @Test void recordsUnknownSourceWithoutReceiptDateOrAnotherMoneyMovement() {
        var use=new UnidentifiedRR(UUID.randomUUID(),new BigDecimal("2"),"Palmolive");var receipt=save(day,"4 PM",List.of(),List.of(use));
        var before=store.state;var movements=new ProviderBalanceSummary(repo).movements(List.of(repo.findById(receipt).orElseThrow()));
        var result=ReceiptRRMatching.standalone(before,receipt,1,use.eventId(),UUID.randomUUID(),"Palmolive RR",null,null,"Earning source unknown","owner");
        assertSame(before,store.state);assertNull(result.reward().getEarnedReceiptId());assertNull(result.reward().getEarnedDate());assertNull(result.reward().getExpirationDate());
        assertEquals(use.amount(),result.reward().getFaceValue());assertFalse(result.link().receiptEarning());
        repo.issuedRewards().save(result.reward());repo.linkRRUsage(result.link());
        assertEquals(movements,new ProviderBalanceSummary(repo).movements(List.of(repo.findById(receipt).orElseThrow())));
        assertTrue(repo.findById(receipt).orElseThrow().transaction().getObservations().earnings().isEmpty());
        assertThrows(IllegalArgumentException.class,()->ReceiptRRMatching.standalone(store.state,receipt,1,use.eventId(),UUID.randomUUID(),"Duplicate",null,null,"Unknown","owner"));
    }
    @Test void unknownSourceStillRejectsKnownConflictingDatesAndStaleRevision() {
        var use=new UnidentifiedRR(UUID.randomUUID(),new BigDecimal("2"),"Palmolive");var receipt=save(day,"4 PM",List.of(),List.of(use));var before=store.state;
        assertThrows(IllegalArgumentException.class,()->ReceiptRRMatching.standalone(before,receipt,1,use.eventId(),UUID.randomUUID(),"Palmolive",day.plusDays(1),null,"Unknown receipt","owner"));
        assertThrows(IllegalArgumentException.class,()->ReceiptRRMatching.standalone(before,receipt,1,use.eventId(),UUID.randomUUID(),"Palmolive",null,day.minusDays(1),"Unknown receipt","owner"));
        assertThrows(ConcurrentModificationException.class,()->ReceiptRRMatching.standalone(before,receipt,2,use.eventId(),UUID.randomUUID(),"Palmolive",null,null,"Unknown receipt","owner"));
        assertSame(before,store.state);
    }
    @Test void unusedRewardCanBeSavedBeforeAnyReceiptAndMatchedLaterWithUnknownHistoryConfirmation() {
        var reward=new RegisterReward("Palmolive RR",new BigDecimal("2"),null,null,null);repo.issuedRewards().save(reward);
        assertTrue(store.state.receipts().isEmpty());assertTrue(repo.rrUsageLinks().isEmpty());
        var use=new UnidentifiedRR(UUID.randomUUID(),new BigDecimal("2"),"Palmolive");var receipt=save(day,"4 PM",List.of(),List.of(use));
        var before=store.state;var option=ReceiptRRMatching.options(before,receipt).getFirst().options().getFirst();assertTrue(option.historyUnknown());
        assertThrows(IllegalArgumentException.class,()->ReceiptRRMatching.select(before,receipt,use.eventId(),reward.getRewardId(),false,false));
        assertEquals(reward.getRewardId(),ReceiptRRMatching.select(before,receipt,use.eventId(),reward.getRewardId(),false,true).sourceId());assertSame(before,store.state);
        repo.linkRRUsage(new RRUsageLink(reward.getRewardId(),false,use.eventId(),receipt,use.amount(),"Confirmed available, date unknown","owner",Instant.now()));
        assertTrue(ReceiptRRMatching.options(store.state,receipt).getFirst().matched());assertTrue(repo.findById(receipt).orElseThrow().transaction().getObservations().earnings().isEmpty());
    }
}
