package coupons.repository;

import coupons.promotions.BenefitKind;
import coupons.promotions.CouponApplied;
import coupons.receipts.CouponTransaction;
import coupons.receipts.Payment;
import coupons.receipts.PaymentType;
import coupons.receipts.PurchaseItem;
import coupons.promotions.RebateBenefit;
import coupons.promotions.RebateStatus;
import coupons.receipts.ReceiptObservations;
import coupons.receipts.ReceiptReport;
import coupons.receipts.ReceiptTotals;
import coupons.receipts.TransactionCalculator;

import coupons.receipts.ReceiptObservations.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Acceptance tests for structured receipt facts, persistence, and no double counting. */
class ReceiptObservationsTest {
    private static final Currency USD = Currency.getInstance("USD");
    private static final Instant CONFIRMED = Instant.parse("2026-10-17T12:00:00Z");

    /** Creates an exact decimal. */
    private BigDecimal money(String value) { return new BigDecimal(value); }

    /** Creates a USD benefit without implying that retailer rewards are spendable cash. */
    private RebateBenefit benefit(String amount) {
        return new RebateBenefit(BenefitKind.CASH, money(amount), USD, null);
    }

    /** Creates one confirmed earning, preserving an independent receipt identity. */
    private Earning earned(EarningKind kind, String provider, String amount, Scope scope, List<UUID> lines) {
        return new Earning(UUID.randomUUID(), kind, provider, null, scope, lines,
                null, benefit(amount), RebateStatus.CREDITED, null, null, null, List.of());
    }

    /** Records the entrant's included amount and confirmation, not another event. */
    private Inclusion include(EventKind kind, UUID id, String amount) {
        return new Inclusion(new EventReference(kind, id), money(amount), "receipt-owner", CONFIRMED);
    }

    /** Builds the confirmed Walgreens case, without inventing starting/ending balances. */
    private CouponTransaction walgreens() {
        var transaction = new CouponTransaction(new ReceiptTotals(money("1.04"), money("3.60")));
        var neutrogena = new PurchaseItem("Neutrogenta", "07050111090", null, money("7.79"), 2);
        var lysol = new PurchaseItem("Lysol travel wipes", "01920099717", null, money("3.29"), 2);
        transaction.addPurchaseItem(neutrogena);
        transaction.addPurchaseItem(lysol);
        var store = new CouponApplied("Store coupon", money("3.60"), List.of(neutrogena.getLineItemId()));
        var manufacturer = new CouponApplied("Manufacturer coupon", money("4.00"), List.of(neutrogena.getLineItemId()));
        transaction.addAppliedCoupon(store);
        transaction.addAppliedCoupon(manufacturer);
        var payment = new Payment(PaymentType.WALGREENS_CASH, money("10.00"));
        transaction.addPayment(payment);
        var rr = new UnidentifiedRR(UUID.randomUUID(), money("2.00"), "Printed RR used; instrument unknown");
        var first = earned(EarningKind.REGISTER_REWARD, "Walgreens", "7.00", Scope.ITEM, List.of(neutrogena.getLineItemId()));
        var second = earned(EarningKind.REGISTER_REWARD, "Walgreens", "5.00", Scope.ITEM, List.of(lysol.getLineItemId()));
        var cash = earned(EarningKind.WALGREENS_CASH, "Walgreens", "0.80", Scope.RECEIPT, List.of());
        var wcLinks = List.of(include(EventKind.PAYMENT, payment.getPaymentId(), "10.00"),
                include(EventKind.EARNING, cash.earningId(), "0.80"));
        var mfgLinks = List.of(include(EventKind.COUPON_APPLIED, manufacturer.getApplicationId(), "4.00"),
                include(EventKind.UNIDENTIFIED_RR, rr.eventId(), "2.00"));
        var totalLinks = new ArrayList<>(wcLinks);
        totalLinks.addAll(mfgLinks);
        transaction.setObservations(new ReceiptObservations(null, "9:40 am", List.of(first, second, cash),
                List.of(rr), new WalgreensCashAccount(null, money("10.00"), money("0.80"), null),
                List.of(new SavingsCategory(UUID.randomUUID(), "myWalgreens Savings", USD, money("10.80"), wcLinks, true),
                        new SavingsCategory(UUID.randomUUID(), "Mfg coupon savings", USD, money("6.00"), mfgLinks, true),
                        new SavingsCategory(UUID.randomUUID(), "Your total Savings", USD, money("16.80"), totalLinks, true)),
                Map.of(store.getApplicationId(), DiscountKind.STORE,
                        manufacturer.getApplicationId(), DiscountKind.MANUFACTURER)));
        return transaction;
    }

    /** Verifies complete save/retrieve/history/retry preservation and exact checkout. */
    @Test
    void walgreensAcceptanceSurvivesPersistenceWithoutDoubleCounting() {
        var repo = new InMemoryTransactionRepository();
        var transaction = walgreens();
        var original = ReceiptSnapshot.from(transaction);
        UUID id = UUID.randomUUID();
        var request = new ReceiptWriteRequest(UUID.randomUUID(), CONFIRMED);
        var actor = new ReceiptCreateCtx("receipt-owner");
        var metadata = new ReceiptMeta("Walgreens", "1115-13212274126100403", null, null);
        var result = repo.save(id, transaction, metadata, List.of(), request, actor);
        var restored = repo.findById(id).orElseThrow();
        assertEquals(original, ReceiptSnapshot.from(restored.transaction()));
        assertEquals(original, repo.history().findByRevision(id, 1).orElseThrow().after().contents());
        assertEquals(result, repo.save(id, original.toTransaction(), metadata, List.of(), request, actor));
        assertEquals(1, repo.history().findByReceiptId(id).size());
        assertTrue(repo.redemptions().findByReceiptId(id).isEmpty());
        assertNull(restored.transaction().getObservations().purchaseDate());
        assertEquals("9:40 am", restored.transaction().getObservations().printedTime());
        assertEquals(3, restored.transaction().getObservations().earnings().size());
        var calculator = new TransactionCalculator();
        assertEquals(money("3.60"), calculator.calculateExpectedCheckoutAmount(restored.transaction()));
        assertEquals(0, calculator.calculateReceiptDifference(restored.transaction()).signum());
        for (var category : restored.transaction().getObservations().savings()) {
            assertEquals(0, category.difference().signum());
            assertTrue(category.explanationComplete());
        }
        assertEquals(transaction.getPayments().get(0).getPaymentId(),
                restored.transaction().getPayments().get(0).getPaymentId());
    }

    /** Verifies distinct cash offers, expected versus confirmed, and projected item cost. */
    @Test
    void coverGirlAcceptanceKeepsRebatesOutOfCheckout() {
        var repo = new InMemoryTransactionRepository();
        var transaction = new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO, money("7.29")));
        var item = new PurchaseItem("covergirl", "61630453095", null, money("9.29"), 1);
        transaction.addPurchaseItem(item);
        var coupon = new CouponApplied("Manufacturer coupon", money("2.00"), List.of(item.getLineItemId()));
        transaction.addAppliedCoupon(coupon);
        List<Earning> earnings = new ArrayList<>();
        for (String[] offer : List.of(new String[]{"Ibotta", "3.05"}, new String[]{"PayPal separate promotion", "1.90"})) {
            earnings.add(new Earning(UUID.randomUUID(), EarningKind.THIRD_PARTY_REBATE, offer[0], "Distinct offer",
                    Scope.ITEM, List.of(item.getLineItemId()), benefit(offer[1]), null,
                    RebateStatus.EXPECTED, null, null, null, List.of()));
        }
        transaction.setObservations(new ReceiptObservations(null, null, earnings, List.of(), null, List.of(),
                Map.of(coupon.getApplicationId(), DiscountKind.MANUFACTURER)));
        UUID id = UUID.randomUUID();
        repo.save(id, transaction, null, List.of(), new ReceiptWriteRequest(UUID.randomUUID(), CONFIRMED), new ReceiptCreateCtx("owner"));
        var receipt = repo.findById(id).orElseThrow();
        assertEquals(money("7.29"), new TransactionCalculator().calculateExpectedCheckoutAmount(receipt.transaction()));
        assertEquals(ReceiptSnapshot.from(transaction), ReceiptSnapshot.from(receipt.transaction()));
        var output = new ByteArrayOutputStream();
        ReceiptReport.print(receipt, new PrintStream(output));
        assertTrue(output.toString().replaceAll(" +", " ").contains("Cash Rebates 4.95"));
        assertTrue(output.toString().replaceAll(" +", " ").contains("After Cash Rebates, Before Tax 2.34"));
        assertTrue(output.toString().contains("Expected, not confirmed"));
        var first = earnings.get(0);
        earnings.set(0, new Earning(first.earningId(), first.kind(), first.provider(), first.offerReference(), first.scope(),
                first.lineItemIds(), first.expected(), benefit("3.05"), RebateStatus.CREDITED, null, null, null, List.of()));
        var previous = transaction.getObservations();
        transaction.setObservations(new ReceiptObservations(null, null, earnings, List.of(), null, List.of(), previous.discountKinds()));
        output.reset();
        ReceiptReport.print(new ReceiptRecord(UUID.randomUUID(), ReceiptStatus.ACTIVE, 1, transaction), new PrintStream(output));
        assertTrue(output.toString().replaceAll(" +", " ").contains("Cash Rebates 4.95"));
    }

    /** Verifies preserved partial/overexplained categories and unknown account values. */
    @Test
    void preservesDifferencesWithoutInventingEvents() {
        var event = include(EventKind.PAYMENT, UUID.randomUUID(), "10.00");
        var partial = new SavingsCategory(UUID.randomUUID(), "myWalgreens Savings", USD, money("12.80"), List.of(event), false);
        assertEquals(money("2.80"), partial.difference());
        assertFalse(partial.explanationComplete());
        var excessive = new SavingsCategory(UUID.randomUUID(), "Recorded category", USD, money("8.00"), List.of(event), true);
        assertEquals(money("-2.00"), excessive.difference());
        var balances = new WalgreensCashAccount(money("15.00"), money("10.00"), money("0.80"), money("5.90"));
        assertEquals(money("5.80"), balances.calculatedEnding().orElseThrow());
        assertEquals(money("0.10"), balances.difference().orElseThrow());
        assertTrue(new WalgreensCashAccount(null, money("10.00"), money("0.80"), null).difference().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new SavingsCategory(UUID.randomUUID(), "Category", USD,
                money("20.00"), List.of(event, event), false));
    }

    /** Verifies reference validation is atomic and changes participate in retry comparison. */
    @Test
    void invalidLinksAndChangedObservationsCannotSilentlySave() {
        var store = new InMemoryStore();
        var repo = new InMemoryTransactionRepository(store, java.time.Clock.systemUTC());
        var transaction = walgreens();
        var facts = transaction.getObservations();
        UUID id = UUID.randomUUID();
        var request = new ReceiptWriteRequest(UUID.randomUUID(), CONFIRMED);
        var actor = new ReceiptCreateCtx("owner");
        var invalid = new SavingsCategory(UUID.randomUUID(), "Category", USD, money("1.00"),
                List.of(include(EventKind.PAYMENT, UUID.randomUUID(), "1.00")), false);
        transaction.setObservations(new ReceiptObservations(facts.purchaseDate(), facts.printedTime(), facts.earnings(),
                facts.unidentifiedRR(), facts.walgreensCash(), List.of(invalid), facts.discountKinds()));
        var before = store.state;
        assertThrows(IllegalArgumentException.class, () -> repo.save(id, transaction, null, List.of(), request, actor));
        assertSame(before, store.state);
        transaction.setObservations(facts);
        repo.save(id, transaction, null, List.of(), request, actor);
        before = store.state;
        transaction.setObservations(new ReceiptObservations(null, "9:41 am", facts.earnings(), facts.unidentifiedRR(),
                facts.walgreensCash(), facts.savings(), facts.discountKinds()));
        assertThrows(IllegalArgumentException.class, () -> repo.save(id, transaction, null, List.of(), request, actor));
        assertSame(before, store.state);
    }

    /** Verifies scopes and original gift-card/point units survive working restoration. */
    @Test
    void preservesPointGiftCardAndMultiItemEarnings() {
        var transaction = walgreens();
        var facts = transaction.getObservations();
        var earningList = new ArrayList<>(facts.earnings());
        var points = new RebateBenefit(BenefitKind.POINTS, money("25"), null, "Fetch");
        var gift = new RebateBenefit(BenefitKind.GIFT_CARD, money("5.00"), USD, "Recorded merchant");
        earningList.add(new Earning(UUID.randomUUID(), EarningKind.THIRD_PARTY_REBATE, "Fetch", null,
                Scope.RECEIPT, List.of(), points, null, RebateStatus.EXPECTED, null, null, null, List.of()));
        earningList.add(new Earning(UUID.randomUUID(), EarningKind.THIRD_PARTY_REBATE, "Provider", null,
                Scope.MULTI_ITEM, transaction.getPurchaseItems().stream().map(PurchaseItem::getLineItemId).toList(),
                gift, gift, RebateStatus.PAID, null, null, null, List.of()));
        transaction.setObservations(new ReceiptObservations(null, facts.printedTime(), earningList, facts.unidentifiedRR(),
                facts.walgreensCash(), facts.savings(), facts.discountKinds()));
        var snapshot = ReceiptSnapshot.from(transaction);
        assertEquals(snapshot, ReceiptSnapshot.from(snapshot.toTransaction()));
        var events = ReceiptObservationValidator.monetaryEvents(snapshot);
        assertFalse(events.containsKey(new EventReference(EventKind.EARNING, earningList.get(3).earningId())));
        assertEquals(money("5.00"), events.get(new EventReference(EventKind.EARNING, earningList.get(4).earningId())).amount());
    }

    /** Verifies scope and evidence references are validated on immutable captured contents. */
    @Test
    void rejectsMissingEarningLinesAndEvidence() {
        var repo = new InMemoryTransactionRepository();
        var transaction = walgreens();
        var facts = transaction.getObservations();
        var bad = earned(EarningKind.WALGREENS_CASH, "Walgreens", "0.80", Scope.ITEM, List.of(UUID.randomUUID()));
        transaction.setObservations(new ReceiptObservations(null, null, List.of(bad), List.of(), null, List.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> repo.save(UUID.randomUUID(), transaction, null, List.of(),
                new ReceiptWriteRequest(UUID.randomUUID(), CONFIRMED), new ReceiptCreateCtx("owner")));
        var existing = facts.earnings().get(0);
        var missingEvidence = new Earning(existing.earningId(), existing.kind(), existing.provider(), null,
                existing.scope(), existing.lineItemIds(), existing.expected(), existing.confirmed(), existing.status(),
                null, null, null, List.of(UUID.randomUUID()));
        transaction.setObservations(new ReceiptObservations(null, null, List.of(missingEvidence), List.of(), null, List.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> repo.save(UUID.randomUUID(), transaction, null, List.of(),
                new ReceiptWriteRequest(UUID.randomUUID(), CONFIRMED), new ReceiptCreateCtx("owner")));
    }

    /** Verifies one payment identity cannot be accidentally recorded on two receipts. */
    @Test
    void financialEventsCannotBeReusedAcrossReceipts() {
        var repo = new InMemoryTransactionRepository();
        var first = new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO, money("1.00")));
        var payment = new Payment(PaymentType.WALGREENS_CASH, money("1.00"));
        first.addPayment(payment);
        repo.save(UUID.randomUUID(), first, null, List.of(),
                new ReceiptWriteRequest(UUID.randomUUID(), CONFIRMED), new ReceiptCreateCtx("owner"));
        var second = new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO, money("1.00")));
        second.addPayment(new Payment(payment.getPaymentId(), payment.getPaymentType(), payment.getAmount()));
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> repo.save(id, second, null, List.of(),
                new ReceiptWriteRequest(UUID.randomUUID(), CONFIRMED), new ReceiptCreateCtx("owner")));
        assertTrue(repo.findById(id).isEmpty());
    }

    /** Verifies point balances cannot be linked as dollar savings or silently converted. */
    @Test
    void pointsCannotBecomeDollarSavingsLinks() {
        var repo = new InMemoryTransactionRepository();
        var transaction = walgreens();
        var points = new RebateBenefit(BenefitKind.POINTS, money("25"), null, "Fetch");
        var earning = new Earning(UUID.randomUUID(), EarningKind.THIRD_PARTY_REBATE, "Fetch", null,
                Scope.RECEIPT, List.of(), points, points, RebateStatus.CREDITED, null, null, null, List.of());
        var category = new SavingsCategory(UUID.randomUUID(), "Reported category", USD, money("25"),
                List.of(include(EventKind.EARNING, earning.earningId(), "25")), true);
        transaction.setObservations(new ReceiptObservations(null, null, List.of(earning), List.of(),
                null, List.of(category), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> repo.save(UUID.randomUUID(), transaction,
                null, List.of(), new ReceiptWriteRequest(UUID.randomUUID(), CONFIRMED), new ReceiptCreateCtx("owner")));
    }
}
