
package coupons.repository;

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
}
