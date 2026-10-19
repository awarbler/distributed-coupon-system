package coupons;

import coupons.ReceiptObservations.*;
import coupons.repository.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReceiptPresentationTest {
    private BigDecimal money(String value) { return new BigDecimal(value); }
    private Earning earning(EarningKind kind, String value, RebateStatus status, boolean confirmed) {
        var benefit = new RebateBenefit(kind == EarningKind.REGISTER_REWARD ? BenefitKind.STORE_COUPON : BenefitKind.STORE_REWARD,
                money(value), Currency.getInstance("USD"), "Walgreens");
        return new Earning(UUID.randomUUID(), kind, "Walgreens", null, Scope.RECEIPT, List.of(), benefit,
                confirmed ? benefit : null, status, null, null, null, List.of());
    }
    private String render(CouponTransaction tx, String retailer) {
        var bytes = new ByteArrayOutputStream();
        ReceiptReport.print(new ReceiptRecord(UUID.randomUUID(), ReceiptStatus.ACTIVE, 1, tx,
                new ReceiptMeta(retailer, "000123", null, null)), new PrintStream(bytes));
        return bytes.toString();
    }

    @Test void capitalizesDisplayAndTotalsRewardsWithoutChangingOriginalRecordsOrCountingTwice() {
        var tx = new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO, money("2.00")));
        var item = new PurchaseItem("lysol travel wipes", "ab00123", null, money("3.00"), 2);
        tx.addPurchaseItem(item);
        var coupon = new CouponApplied("Manufacturer coupon", money("4.00"), List.of(item.getLineItemId()));
        tx.addAppliedCoupon(coupon);
        tx.setObservations(new ReceiptObservations(null, "940 am", List.of(
                earning(EarningKind.REGISTER_REWARD, "7.00", RebateStatus.PAID, true),
                earning(EarningKind.REGISTER_REWARD, "5.00", RebateStatus.PAID, true),
                earning(EarningKind.REGISTER_REWARD, "20.00", RebateStatus.EXPECTED, false),
                earning(EarningKind.REGISTER_REWARD, "9.00", RebateStatus.REJECTED, true),
                earning(EarningKind.WALGREENS_CASH, "0.80", RebateStatus.CREDITED, true)),
                List.of(), new WalgreensCashAccount(money("10.00"), BigDecimal.ZERO, money("0.80"), money("10.80")),
                List.of(), Map.of(coupon.getApplicationId(), DiscountKind.MANUFACTURER)));
        var before = ReceiptSnapshot.from(tx);
        String output = render(tx, "walgreens");
        assertTrue(output.contains("\nWalgreens\n"), output);
        assertTrue(output.contains("Lysol Travel Wipes"));
        assertTrue(output.contains("Manufacturer Coupon"));
        assertTrue(output.contains("SKU / UPC: AB00123"));
        assertTrue(output.contains("Time: 9:40 AM"));
        assertTrue(output.replaceAll(" +", " ").contains("Register Rewards earned | 12.00 RR"));
        assertTrue(output.replaceAll(" +", " ").contains("Walgreens Cash earned | 0.80 WC"));
        assertEquals(before, ReceiptSnapshot.from(tx));
        assertEquals("lysol travel wipes", item.getProductName());
        assertEquals("ab00123", item.getProductCode());
        assertEquals("Manufacturer coupon", coupon.getPrintedLabel());
    }

    @Test void genericReceiptDoesNotAdvertiseWalgreensSpecificTotals() {
        var tx = new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO, money("5.00")));
        tx.addPurchaseItem(new PurchaseItem("milk", "0001", null, money("5.00"), 1));
        String output = render(tx, "h-e-b");
        assertTrue(output.contains("H-E-B"));
        assertFalse(output.contains("Walgreens"));
        assertFalse(output.contains("Register Rewards Earned"));
    }
    @Test void equivalentExpectedValueAndAccountWideWcAreNotPrintedAgain() {
        var tx = new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO, money("5.00")));
        tx.addPurchaseItem(new PurchaseItem("Item", "0001", null, money("5.00"), 1));
        var expected = new RebateBenefit(BenefitKind.STORE_COUPON, money("5"), Currency.getInstance("USD"), "Walgreens");
        var confirmed = new RebateBenefit(BenefitKind.STORE_COUPON, money("5.00"), Currency.getInstance("USD"), "Walgreens");
        var rr = new Earning(UUID.randomUUID(), EarningKind.REGISTER_REWARD, "Walgreens", null, Scope.RECEIPT,
                List.of(), expected, confirmed, RebateStatus.PAID, null, null, null, List.of());
        tx.setObservations(new ReceiptObservations(null, null, List.of(rr,
                earning(EarningKind.WALGREENS_CASH, "0.80", RebateStatus.CREDITED, true)), List.of(),
                new WalgreensCashAccount(money("10.00"), BigDecimal.ZERO, money("0.80"), money("10.80")), List.of(), Map.of()));
        var before = ReceiptSnapshot.from(tx);
        String output = render(tx, "Walgreens");
        assertFalse(output.contains("After Store Discounts"));
        assertFalse(output.contains("Expected:"));
        assertFalse(output.contains("Status: Register Reward Printed"));
        assertFalse(output.contains("Recorded Earned: 0.80 Walgreens Cash"));
        assertTrue(output.replaceAll(" +", " ").contains("Walgreens Cash earned | 0.80 WC"));
        assertFalse(output.contains("Attached Receipt Files: 0"));
        assertEquals(before, ReceiptSnapshot.from(tx));
    }
}
