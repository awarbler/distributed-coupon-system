package coupons.api;

import coupons.promotions.BenefitKind;
import coupons.promotions.Coupon;
import coupons.promotions.CouponApplied;
import coupons.receipts.CouponTransaction;
import coupons.receipts.Payment;
import coupons.receipts.PaymentType;
import coupons.promotions.PromotionQualification;
import coupons.rewards.ProviderBalanceSummary;
import coupons.receipts.PurchaseItem;
import coupons.promotions.RebateStatus;
import coupons.receipts.ReceiptObservations;
import coupons.receipts.ReceiptTotals;
import coupons.receipts.ReceiptWorksheetData;
import coupons.receipts.SavingsReport;
import coupons.receipts.TransactionCalculator;

import coupons.repository.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReceiptInputTest {
    private static Map<String,Object> printedCashDraft(String earned){
        var fields=draft(UUID.randomUUID());fields.put("retailer","Walgreens");fields.put("earnings",List.of());
        fields.put("printedFooter",Map.of("status","PRINTED","wcEarned",earned));return fields;
    }
    @Test void printedEarnedCashCreatesOneStableReceivedEarningAndAccountMovement(){
        var fields=printedCashDraft("0.6900");var a=read(fields);var b=read(fields);
        var cash=a.transaction().getObservations().earnings().stream().filter(e->e.kind()==ReceiptObservations.EarningKind.WALGREENS_CASH).toList();
        assertEquals(1,cash.size());assertEquals(new BigDecimal("0.6900"),cash.getFirst().confirmed().amount());assertEquals(RebateStatus.CREDITED,cash.getFirst().status());
        assertEquals(cash.getFirst().earningId(),b.transaction().getObservations().earnings().stream().filter(e->e.kind()==ReceiptObservations.EarningKind.WALGREENS_CASH).findFirst().orElseThrow().earningId());
        var movements=new ProviderBalanceSummary().movements(List.of(a.record()));
        var earned=movements.stream().filter(m->m.provider().equals("Walgreens Cash")).toList();assertEquals(1,earned.size());assertEquals(new BigDecimal("0.6900"),earned.getFirst().usd());
        assertEquals(1,a.transaction().getObservations().earnings().stream().filter(e->e.provider().equals("Fetch")).count());
    }
    @Test void explicitCashRewardOverridesThePrintedDefaultIncludingItsStatus(){
        var fields=printedCashDraft("2.66");UUID event=UUID.randomUUID();
        fields.put("earnings",List.of(Map.of("earningId",event,"kind","WALGREENS_CASH","provider","Walgreens","form","STORE_REWARD","amount","2.66","status","EXPECTED","offer","My reward")));
        var cash=read(fields).transaction().getObservations().earnings().stream().filter(e->e.kind()==ReceiptObservations.EarningKind.WALGREENS_CASH).toList();
        assertEquals(1,cash.size());assertEquals(event,cash.getFirst().earningId());assertEquals(RebateStatus.EXPECTED,cash.getFirst().status());assertNull(cash.getFirst().confirmed());
    }
    @Test void blankZeroAndOtherStoreFootersDoNotInventCash(){
        for(String amount:List.of("0","0.000"))assertTrue(read(printedCashDraft(amount)).transaction().getObservations().earnings().stream().noneMatch(e->e.kind()==ReceiptObservations.EarningKind.WALGREENS_CASH));
        var fields=printedCashDraft("0.69");fields.put("retailer","Walmart");assertTrue(read(fields).transaction().getObservations().earnings().stream().noneMatch(e->e.kind()==ReceiptObservations.EarningKind.WALGREENS_CASH));
        fields.put("retailer","Walgreens");fields.put("printedFooter",Map.of("status","PRINTED","wcOpening","100","wcClosing","101"));assertTrue(read(fields).transaction().getObservations().earnings().stream().noneMatch(e->e.kind()==ReceiptObservations.EarningKind.WALGREENS_CASH));
    }
    @Test void correctingPrintedEarnedCashUpdatesOnlyTheCurrentRevisionAndCanRemoveItsDefault(){
        var created=read(printedCashDraft("0.69"));var old=created.record();
        var corrected=ReceiptCorrection.read(old,Map.of("reason","Correct earned amount","printedFooter",Map.of("wcEarned","0.79"))).transaction();
        var cash=corrected.getObservations().earnings().stream().filter(e->e.kind()==ReceiptObservations.EarningKind.WALGREENS_CASH).findFirst().orElseThrow();assertEquals(new BigDecimal("0.79"),cash.confirmed().amount());
        assertEquals(new BigDecimal("0.69"),old.transaction().getObservations().walgreensCash().earned());assertEquals(ReceiptInput.printedCashId(old.transactionId()),cash.earningId());
        var cleared=ReceiptCorrection.read(old,Map.of("reason","Remove printed earned amount","printedFooter",Map.of("wcEarned","0"))).transaction();assertTrue(cleared.getObservations().earnings().stream().noneMatch(e->e.kind()==ReceiptObservations.EarningKind.WALGREENS_CASH));
    }
    @Test void addingAnExplicitCashRewardReplacesTheAutomaticDefaultWithoutDoubleCounting(){
        var old=read(printedCashDraft("0.69")).record();
        var addition=ReceiptEarningAddition.read(old,Map.of("earning",Map.of("earningId",UUID.randomUUID().toString(),"kind","WALGREENS_CASH","provider","Walgreens","form","STORE_REWARD","amount","0.69","status","CREDITED","confirmedAmount","0.69","unit","USD","confirmAmountAndUnit",true,"offer","Member earnings")));
        assertEquals(1,addition.transaction().getObservations().earnings().stream().filter(e->e.kind()==ReceiptObservations.EarningKind.WALGREENS_CASH).count());
        assertTrue(addition.transaction().getObservations().earnings().stream().noneMatch(e->e.earningId().equals(ReceiptInput.printedCashId(old.transactionId()))));
        assertEquals(new BigDecimal("0.69"),new ProviderBalanceSummary().movements(List.of(new ReceiptRecord(old.transactionId(),old.status(),2,addition.transaction(),old.metadata(),old.evidenceIds()))).stream().filter(m->m.provider().equals("Walgreens Cash")).map(ProviderBalanceSummary.Movement::usd).reduce(BigDecimal.ZERO,BigDecimal::add));
    }

    @Test void manufacturerPurchaseRequirementDoesNotMultiplyDeduction() {
        var fields=draft(UUID.randomUUID());
        fields.put("discounts",List.of(Map.of("applicationId",UUID.randomUUID(),"label","Buy 2 manufacturer coupon","amount","4","kind","MANUFACTURER","lineItemIds",List.of(),"wholeReceipt",true,"requiredQuantity",2)));
        var coupon=read(fields).transaction().getAppliedCoupons().getFirst();
        assertEquals(2,coupon.getRequiredQuantity());assertEquals(new BigDecimal("4"),coupon.getAppliedAmount());
        assertThrows(IllegalArgumentException.class,()->ReceiptInput.requiredQuantity(Map.of("requiredQuantity",0)));
    }

    @Test void namedEcbUsesPreserveSeparateDescriptionsAndAccount() {
        var fields=draft(UUID.randomUUID());fields.put("retailer","CVS");
        fields.put("payments",List.of(Map.of("paymentId",UUID.randomUUID(),"type","OTHER","amount","5","notes","Beauty ECB","accountName","ECB","role","CHECKOUT_DEDUCTION"),Map.of("paymentId",UUID.randomUUID(),"type","OTHER","amount","2","notes","Weekly ECB","accountName","ECB","role","CHECKOUT_DEDUCTION")));
        var payments=read(fields).transaction().getPayments();
        assertEquals(2,payments.size());assertEquals("ECB",payments.getFirst().getAccountName());assertEquals("Beauty ECB",payments.getFirst().getNotes());assertEquals("Weekly ECB",payments.getLast().getNotes());
    }

    static Map<String,Object> draft(UUID id) {
        UUID line=UUID.randomUUID();var input=new LinkedHashMap<String,Object>();
        input.put("transactionId",id.toString());input.put("retailer","Synthetic store");input.put("receiptNumber","web-"+id);input.put("purchaseDate","2026-10-19");input.put("printedTime","09:00");input.put("purchaseChannel","IN_STORE");input.put("tax","0.27");input.put("reportedTotal","7.28");
        input.put("items",List.of(Map.of("lineItemId",line,"description","Test soap","sku","0000123","regularUnitPrice","10.0000","unitPrice","10.000","quantity",1)));
        input.put("discounts",List.of(Map.of("applicationId",UUID.randomUUID(),"label","Store coupon","amount","2.000","kind","STORE_COUPON","lineItemIds",List.of(line),"wholeReceipt",false)));
        input.put("payments",List.of(Map.of("paymentId",UUID.randomUUID(),"type","CASH","amount","7.2800")));
        input.put("rrUsed","1.00");input.put("rrUsageId",UUID.randomUUID());
        input.put("earnings",List.of(Map.of("earningId",UUID.randomUUID(),"kind","REGISTER_REWARD","form","STORE_COUPON","amount","5.000","status","PAID","confirmedAmount","5","unit","USD","confirmAmountAndUnit",true),Map.of("earningId",UUID.randomUUID(),"kind","WALGREENS_CASH","form","STORE_REWARD","amount","9","status","EXPECTED")));
        input.put("evidence",List.of(Map.of("evidenceId",UUID.randomUUID(),"mediaType","image/png","contentBase64","AQID")));
        return input;
    }
    static ReceiptInput read(Map<String,Object> input){return ReceiptInput.read(Json.parseObject(Json.object(input)));}
    @Test void automaticFetchIsStableAndAnyExplicitFetchEntryReplacesIt() {
        var fields=draft(UUID.randomUUID());
        var a=read(fields).transaction().getObservations().earnings().stream().filter(e->"Fetch".equals(e.provider())).toList();
        var b=read(fields).transaction().getObservations().earnings().stream().filter(e->"Fetch".equals(e.provider())).toList();
        assertEquals(1,a.size()); assertEquals(a,b); assertEquals(new BigDecimal("25"),a.getFirst().confirmed().amount());
        assertEquals(RebateStatus.CREDITED,a.getFirst().status()); assertEquals(new BigDecimal("0.025"),a.getFirst().valuation().amount());
        for(var status:List.of("EXPECTED","REJECTED")) {
            fields.put("earnings",List.of(Map.of("earningId",UUID.randomUUID(),"kind","THIRD_PARTY_REBATE","provider","Fetch","form","POINTS","amount","100","status",status)));
            var explicit=read(fields).transaction().getObservations().earnings();assertEquals(1,explicit.size());assertEquals(status,explicit.getFirst().status().name());
        }
    }
    @Test void leadingDecimalAmountsAreAcceptedWithoutRounding() {
        assertEquals(new BigDecimal("0.98"),ReceiptInput.decimal(Map.of("amount",".98"),"amount",false));
        assertEquals(new BigDecimal("0.00001"),ReceiptInput.decimal(Map.of("amount",".00001"),"amount",false));
        for(var invalid:List.of(".","-.98","1..2")) assertThrows(IllegalArgumentException.class,()->ReceiptInput.decimal(Map.of("amount",invalid),"amount",false));
    }
    @Test void pgPointsAutomaticallyUseWorksheetRateForProviderAliases() {
        for(var provider:List.of("P&G Brand","P&G Good Everyday","PG","P&G brandSAVER")) {
            var input=new LinkedHashMap<String,Object>(); input.put("earningId",UUID.randomUUID().toString());
            input.put("kind","THIRD_PARTY_REBATE"); input.put("provider",provider); input.put("form","POINTS");
            input.put("amount","100"); input.put("status","CREDITED"); input.put("confirmedAmount","100"); input.put("unit","points"); input.put("confirmAmountAndUnit",true);
            var earning=EarningInput.read(input);
            assertEquals(0,earning.valuation().amount().compareTo(new BigDecimal("100").divide(new BigDecimal("150"),java.math.MathContext.DECIMAL128)));
            assertEquals(0,earning.confirmed().amount().compareTo(new BigDecimal("100")));
        }
    }
    @Test void fetchPointsUseWorksheetValuationWithoutManualConversion() {
        var input=new LinkedHashMap<String,Object>(); input.put("earningId",UUID.randomUUID().toString());
        input.put("kind","THIRD_PARTY_REBATE"); input.put("provider","Fetch"); input.put("form","POINTS");
        input.put("amount","25"); input.put("status","CREDITED"); input.put("confirmedAmount","25"); input.put("unit","points"); input.put("confirmAmountAndUnit",true);
        var earning=EarningInput.read(input);
        assertEquals(0,earning.valuation().amount().compareTo(new BigDecimal("0.025")));
        assertEquals(0,earning.confirmed().amount().compareTo(new BigDecimal("25")));
        input.put("valuation","0.04"); input.put("valuationBasis","Previously recorded valuation");
        assertEquals(0,EarningInput.read(input).valuation().amount().compareTo(new BigDecimal("0.04")));
    }
    @Test void separateRRUsesPreserveEqualCouponsAndRejectDoubleCounting() {
        var fields=draft(UUID.randomUUID()); fields.remove("rrUsed"); fields.remove("rrUsageId");
        var first=Map.<String,Object>of("eventId",UUID.randomUUID(),"amount","5.00","recordedDescription","First coupon");
        var second=Map.<String,Object>of("eventId",UUID.randomUUID(),"amount","5.00","recordedDescription","Second coupon");
        var third=Map.<String,Object>of("eventId",UUID.randomUUID(),"amount","4.00");
        fields.put("rrUses",List.of(first,second,third)); var result=read(fields);
        assertEquals(3,result.transaction().getObservations().unidentifiedRR().size());
        assertEquals(0,new TransactionCalculator().calculateRewardRedemptionTotal(result.transaction()).compareTo(new BigDecimal("14")));
        fields.put("rrUsed","14"); assertThrows(IllegalArgumentException.class,()->read(fields)); fields.remove("rrUsed");
        fields.put("rrUses",List.of(first,first)); assertThrows(IllegalArgumentException.class,()->read(fields));
        fields.put("rrUses",List.of(Map.of("eventId",UUID.randomUUID(),"amount","0"))); assertThrows(IllegalArgumentException.class,()->read(fields));
    }
    @Test void newRewardErrorsIdentifyTheOfferAndExpectedAmountAndUnit() {
        var input = new LinkedHashMap<String,Object>();
        input.put("earningId",UUID.randomUUID().toString()); input.put("kind","REGISTER_REWARD"); input.put("form","STORE_COUPON");
        input.put("offer","Gain promotion"); input.put("amount","5.00"); input.put("status","PAID");
        input.put("confirmedAmount","4"); input.put("unit","USD"); input.put("confirmAmountAndUnit",true);
        var error = assertThrows(IllegalArgumentException.class, () -> EarningInput.read(input));
        assertTrue(error.getMessage().contains("Gain promotion")); assertTrue(error.getMessage().contains("5.00 USD"));
        assertFalse(error.getMessage().contains("status-only"));
        input.put("confirmedAmount","5"); assertNotNull(EarningInput.read(input).confirmed());
    }
    @Test void buildsExistingModelsAndPreservesExactReportedDiscrepancyAndRewardSemantics() {
        var input=read(draft(UUID.randomUUID()));var tx=input.transaction();var calc=new TransactionCalculator();
        assertEquals(0,new BigDecimal("7.27").compareTo(calc.calculateExpectedCheckoutAmount(tx)));
        assertEquals(0,new BigDecimal("0.01").compareTo(calc.calculateReceiptDifference(tx)));
        assertEquals(new BigDecimal("10.0000"),tx.getPurchaseItems().getFirst().getRegularUnitPrice());
        assertEquals("0000123",tx.getPurchaseItems().getFirst().getProductCode());
        assertEquals(0,new BigDecimal("5.025").compareTo(SavingsReport.calculate(List.of(input.record())).newlyEarned()));
        assertEquals(1,tx.getObservations().unidentifiedRR().size());assertTrue(tx.getRegisterRewardRedemptions().isEmpty());
        assertArrayEquals(new byte[]{1,2,3},input.evidence().getFirst().getContent());assertEquals(input.id(),input.evidence().getFirst().getTransactionId());
    }
    @Test void reviewDigestIsStableButChangesWithFinancialValuesAndRetainsPrecision() {
        var fields=draft(UUID.randomUUID());var initial=read(fields);fields.put("reviewToken",initial.reviewToken());fields.put("acknowledgeDifference",true);
        assertEquals(initial.reviewToken(),read(fields).reviewToken());fields.put("tax","0.27000000000000000001");assertNotEquals(initial.reviewToken(),read(fields).reviewToken());
        assertEquals(new BigDecimal("0.27000000000000000001"),read(fields).transaction().getReceiptTotals().getTaxAmount());
    }
    @Test void validatesReferencesOnlineRestrictionsAmountsAndConfirmationThroughExistingRules() {
        var fields=draft(UUID.randomUUID());fields.put("purchaseChannel","ONLINE");var online=fields;assertThrows(IllegalArgumentException.class,()->read(online));
        fields=draft(UUID.randomUUID());fields.put("items",List.of());var empty=fields;assertThrows(IllegalArgumentException.class,()->read(empty));
        fields=draft(UUID.randomUUID());fields.put("tax",0.27);var numeric=fields;assertThrows(IllegalArgumentException.class,()->read(numeric));
        fields=draft(UUID.randomUUID());fields.put("earnings",List.of(Map.of("earningId",UUID.randomUUID(),"kind","REGISTER_REWARD","form","STORE_COUPON","amount","5","status","PAID")));var unconfirmed=fields;assertThrows(IllegalArgumentException.class,()->read(unconfirmed));
        fields=draft(UUID.randomUUID());fields.put("discounts",List.of(Map.of("applicationId",UUID.randomUUID(),"label","Coupon","amount","1","kind","MANUFACTURER","lineItemIds",List.of(UUID.randomUUID()),"wholeReceipt",false)));var unrelated=fields;assertThrows(IllegalArgumentException.class,()->read(unrelated));
    }
    @Test void evidenceAndUnitsAreBoundedAndRejectedValuesArePreserved() {
        var fields=draft(UUID.randomUUID());fields.put("evidence",List.of(Map.of("evidenceId",UUID.randomUUID(),"mediaType","text/html","contentBase64","AQID")));assertThrows(IllegalArgumentException.class,()->read(fields));
        fields.put("evidence",List.of());fields.put("earnings",List.of(Map.of("earningId",UUID.randomUUID(),"kind","THIRD_PARTY_REBATE","provider","Fetch","form","POINTS","amount","25.000","status","REJECTED","rejectionReason","Denied")));
        var e=read(fields).transaction().getObservations().earnings().getFirst();assertEquals(new BigDecimal("25.000"),e.expected().amount());assertNull(e.confirmed());assertEquals(RebateStatus.REJECTED,e.status());assertTrue(e.recordedLifecycle().contains("Denied"));
    }

    @Test void recordsPointQuantitiesAndSavedValuationsWithoutConvertingOrRoundingThem() {
        var fields=draft(UUID.randomUUID());fields.put("earnings",List.of(Map.ofEntries(Map.entry("earningId",UUID.randomUUID()),Map.entry("kind","THIRD_PARTY_REBATE"),Map.entry("provider","Fetch"),Map.entry("form","POINTS"),Map.entry("amount","200.000"),Map.entry("status","CREDITED"),Map.entry("confirmedAmount","200"),Map.entry("unit","points"),Map.entry("confirmAmountAndUnit",true),Map.entry("valuation","0.20000000000000000001"),Map.entry("valuationBasis","Original provider valuation"))));
        var input=read(fields);var e=input.transaction().getObservations().earnings().getFirst();
        assertEquals(new BigDecimal("200.000"),e.confirmed().amount());assertEquals(new BigDecimal("0.20000000000000000001"),e.valuation().amount());
        assertEquals(new BigDecimal("0.20000000000000000001"),SavingsReport.calculate(List.of(input.record())).newlyEarned());
    }

    @Test void preservesReviewContextAndInvalidatesDigestWhenItChanges() {
        var fields = draft(UUID.randomUUID()); var legacy = read(fields);
        assertEquals(ReceiptMeta.PrintedTotalBasis.UNKNOWN, legacy.metadata().printedTotalBasis());
        fields.put("printedTotalBasis", "BEFORE_PAYMENT_FUNDING"); fields.put("paymentEntryStatus", "COMPLETE");
        var revised = read(fields);
        assertEquals(ReceiptMeta.PrintedTotalBasis.BEFORE_PAYMENT_FUNDING, revised.metadata().printedTotalBasis());
        assertNotEquals(legacy.reviewToken(), revised.reviewToken());
        fields.put("paymentEntryStatus", "PARTIAL"); assertNotEquals(revised.reviewToken(), read(fields).reviewToken());
    }
    @Test void purchaseAndScanRequirementsRoundTripWithoutInventedThresholds() {
        for (String category : List.of("Purchase", "ReceiptScan", "Receipt", "Other")) {
            var input = new LinkedHashMap<String,Object>();
            input.put("earningId", UUID.randomUUID().toString()); input.put("kind", "THIRD_PARTY_REBATE");
            input.put("provider", "Fetch"); input.put("form", "POINTS"); input.put("amount", "25"); input.put("status", "EXPECTED");
            var requirement = new LinkedHashMap<String,Object>(); requirement.put("type", null); requirement.put("amount", null);
            requirement.put("category", category); requirement.put("spendBasis", "UNKNOWN"); input.put("requirement", requirement);
            var earning = EarningInput.read(Json.parseObject(Json.encode(input)));
            assertEquals(category, earning.requirement().category());
            assertEquals("CANNOT_VERIFY", PromotionQualification.evaluate(earning, read(draft(UUID.randomUUID())).transaction()).status());
            requirement.put("amount", "1");
            assertThrows(IllegalArgumentException.class, () -> EarningInput.read(Json.parseObject(Json.encode(input))));
        }
    }
    @Test void explicitOtherRoleIsRetainedAndUnknownRoleIsNotInvented() {
        var fields = draft(UUID.randomUUID()); UUID payment = UUID.randomUUID();
        fields.put("payments", List.of(Map.of("paymentId", payment, "type", "OTHER", "amount", "7.28", "role", "FUNDING")));
        assertFalse(read(fields).transaction().getObservations().worksheet().uses().get(payment).checkoutDeduction());
        fields.put("payments", List.of(Map.of("paymentId", payment, "type", "OTHER", "amount", "7.28")));
        var tx = read(fields).transaction();
        assertEquals(TransactionCalculator.PaymentRole.UNRESOLVED, new TransactionCalculator().paymentRole(tx, tx.getPayments().getFirst()));
    }


    @Test void historicalSuggestionsGroupTermsWithoutMergingEvidenceAndExposeBenefitForm() {
        var fields = draft(UUID.randomUUID()); var original = read(fields).record();
        var offers = PostgresPlatformRepository.historicalRewardOffers(List.of(original, original));
        assertEquals(0, offers.size()); // Fixture rewards have no recorded requirement.
        var e = new LinkedHashMap<String,Object>();
        e.put("earningId", UUID.randomUUID()); e.put("kind", "THIRD_PARTY_REBATE"); e.put("provider", "Fetch");
        e.put("form", "POINTS"); e.put("amount", "25"); e.put("status", "EXPECTED");
        e.put("requirement", Map.of("category", "ReceiptScan"));
        fields.put("earnings", List.of(e)); var record = read(fields).record();
        var suggestions = PostgresPlatformRepository.historicalRewardOffers(List.of(record, record));
        assertEquals(1, suggestions.size());
        var offer = (Map<?, ?>) suggestions.getFirst();
        assertEquals(BenefitKind.POINTS, offer.get("form"));
        assertEquals(record.transactionId(), offer.get("sourceReceiptId"));
        var changed = new LinkedHashMap<>(e); changed.put("amount", "50"); fields.put("earnings", List.of(changed));
        assertEquals(2, PostgresPlatformRepository.historicalRewardOffers(List.of(record, read(fields).record())).size());
        assertEquals(1, record.transaction().getObservations().earnings().size());
    }


    @Test void unknownPointsValuationPreservesKnownBenefitsAndRecordedPaymentEvidence() {
        var fields = draft(UUID.randomUUID());
        fields.put("paymentEntryStatus", "COMPLETE"); fields.put("printedTotalBasis", "AFTER_REWARD_REDEMPTIONS");
        fields.put("earnings", List.of(
            Map.of("earningId", UUID.randomUUID(), "kind", "WALGREENS_CASH", "form", "STORE_REWARD", "amount", "7", "status", "CREDITED", "confirmedAmount", "7", "unit", "USD", "confirmAmountAndUnit", true),
            Map.of("earningId", UUID.randomUUID(), "kind", "THIRD_PARTY_REBATE", "provider", "Merryfield", "form", "POINTS", "amount", "25", "status", "CREDITED", "confirmedAmount", "25", "unit", "points", "confirmAmountAndUnit", true)));
        var totals = PostgresPlatformRepository.totals(read(fields).record());
        var savings = (Map<?, ?>) totals.get("savings");
        assertEquals(0, ((BigDecimal) savings.get("knownConfirmedBenefitValue")).compareTo(new BigDecimal("7.025")));
        assertEquals(false, savings.get("confirmedBenefitsComplete"));
        assertNull(savings.get("confirmedBenefitValue")); assertNull(totals.get("benefitAdjustedCashSpending"));
        var payment = (TransactionCalculator.PaymentReconciliation) totals.get("paymentReview");
        assertEquals(0, payment.recordedCashCardPaid().compareTo(new BigDecimal("7.28")));
        assertEquals(3, ((List<?>) totals.get("benefitObservations")).size());
    }
}

class PrintedFooterTest {
    @Test void preservesUnknownAndZeroWithoutCreatingMoney() {
        var old=ReceiptObservations.empty();
        var f=new LinkedHashMap<String,Object>();f.put("status","PRINTED");f.put("wcAvailable","287.05");f.put("wcUsed","0");f.put("wcEarned",null);f.put("totalSavings","12.34");f.put("manufacturerCouponSavings","4.00");f.put("storeCouponSavings","2.00");
        var result=PrintedFooter.read(Map.of("printedFooter",f),old);
        assertEquals(new BigDecimal("287.05"),result.walgreensCash().availableBalance());assertNull(result.walgreensCash().endingBalance());assertEquals(BigDecimal.ZERO,result.walgreensCash().used());assertNull(result.walgreensCash().earned());
        assertEquals(3,result.savings().size()); assertTrue(result.earnings().isEmpty());assertTrue(result.unidentifiedRR().isEmpty());
        assertEquals(ReceiptObservations.FooterStatus.PRINTED,result.footerStatus());
        var edited=PrintedFooter.read(Map.of("printedFooter",Map.of("totalSavings","13")),result);
        assertEquals(result.savings().stream().filter(c->c.printedLabel().equals("Your total Savings")).findFirst().orElseThrow().categoryId(),edited.savings().stream().filter(c->c.printedLabel().equals("Your total Savings")).findFirst().orElseThrow().categoryId());
        assertEquals(result.walgreensCash(),edited.walgreensCash());assertSame(old,PrintedFooter.read(Map.of(),old));
    }
    @Test void createAndCorrectionPreserveMoneyWhileSavingPrintedFields() {
        var fields=ReceiptInputTest.draft(UUID.randomUUID());
        var before=ReceiptInputTest.read(fields);
        fields.put("printedFooter",Map.of("status","PRINTED","wcOpening","100","wcEarned","99","wcUsed","25","wcClosing","174","totalSavings","200"));
        var created=ReceiptInputTest.read(fields);
        var calc=new TransactionCalculator();
        assertEquals(calc.calculateExpectedCheckoutAmount(before.transaction()),calc.calculateExpectedCheckoutAmount(created.transaction()));
        assertEquals(before.transaction().getPayments().size(),created.transaction().getPayments().size());
        assertEquals(before.transaction().getObservations().earnings(),created.transaction().getObservations().earnings());
        var f=new LinkedHashMap<String,Object>();f.put("wcUsed",null);f.put("wcEarned","0");
        var correction=ReceiptCorrection.read(created.record(),Map.of("reason","Correct footer","printedFooter",f));
        assertNull(correction.transaction().getObservations().walgreensCash().used());
        assertEquals(BigDecimal.ZERO,correction.transaction().getObservations().walgreensCash().earned());
        assertEquals(new BigDecimal("100"),correction.transaction().getObservations().walgreensCash().startingBalance());
        assertEquals(created.transaction().getObservations().savings(),correction.transaction().getObservations().savings());
        assertEquals(created.transaction().getObservations().earnings(),correction.transaction().getObservations().earnings());
    }
    @Test void rejectsNegativeAndMalformedObservations() {
        for(String value:List.of("-1","NaN","12%")) assertThrows(IllegalArgumentException.class,()->PrintedFooter.read(Map.of("printedFooter",Map.of("wcEarned",value)),ReceiptObservations.empty()));
    }
}

class ReceiptCorrectionTest {
    @Test void regularPriceWithAttachedHistoricalPromotionPreservesCostAndSource() {
        UUID receiptId=UUID.randomUUID(), lineId=UUID.randomUUID(), couponId=UUID.randomUUID();
        var tx = new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,new BigDecimal("7.50")));
        tx.addPurchaseItem(new PurchaseItem(lineId,"Mouthwash",null,new BigDecimal("4.79"),new BigDecimal("4.00"),2));
        tx.addAppliedCoupon(new CouponApplied(couponId,"Additional coupon",new BigDecimal("0.50"),List.of(lineId)));
        tx.addPayment(new Payment(PaymentType.CARD,new BigDecimal("7.50")));
        tx.setObservations(new ReceiptObservations(null,null,List.of(),List.of(),null,List.of(),Map.of(couponId,ReceiptObservations.DiscountKind.MANUFACTURER)));
        var old = new ReceiptRecord(receiptId,ReceiptStatus.ACTIVE,1,tx,new ReceiptMeta("Store","001",null,null));
        var original=ReceiptSnapshot.from(tx);
        var drafts=ReceiptCorrection.editDiscounts(old);
        assertEquals(2,drafts.size());
        assertEquals(new BigDecimal("1.58"),drafts.get(1).get("appliedAmount"));
        assertEquals(List.of(lineId),drafts.get(1).get("lineItemIds"));
        assertEquals(drafts.get(1).get("applicationId"),ReceiptCorrection.editDiscounts(old).get(1).get("applicationId"));
        var discounts=new ArrayList<Map<String,Object>>();
        for(var d:drafts) { var copy=new LinkedHashMap<>(d); copy.put("appliedAmount",d.get("appliedAmount").toString()); discounts.add(copy); }
        // Transport UUIDs through the same JSON boundary as the browser.
        var f=Json.parseObject(Json.object(Map.of("reason","Review regular pricing", "items",List.of(Map.of("lineItemId",lineId.toString(),"productName","Mouthwash","regularUnitPrice","4.79","quantity",2)),"discounts",discounts)));
        var correction=ReceiptCorrection.read(old,f);
        var calculator=new TransactionCalculator();
        assertEquals(calculator.calculateExpectedCheckoutAmount(tx),calculator.calculateExpectedCheckoutAmount(correction.transaction()));
        assertEquals(new BigDecimal("4.79"),correction.transaction().getPurchaseItems().getFirst().getSaleUnitPrice());
        assertEquals(original,ReceiptSnapshot.from(tx));
        assertEquals(original.observations().earnings(),correction.transaction().getObservations().earnings());
        assertEquals(2,ReceiptSnapshot.from(correction.transaction()).appliedCoupons().size());
        assertEquals(SavingsReport.calculate(List.of(old)).totalSaved(),SavingsReport.calculate(List.of(new ReceiptRecord(receiptId,ReceiptStatus.ACTIVE,2,correction.transaction(),correction.metadata()))).totalSaved());
    }
    @Test void itemDiscountRemainsAttachedAndIsCountedOnce() {
        var tx=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,new BigDecimal("9.58")));
        UUID line=UUID.randomUUID(); tx.addPurchaseItem(new PurchaseItem(line,"Product",null,new BigDecimal("4.79"),new BigDecimal("4.79"),2));
        var old=new ReceiptRecord(UUID.randomUUID(),ReceiptStatus.ACTIVE,1,tx,new ReceiptMeta("Store",null,null,null));
        var f=Json.parseObject(Json.object(Map.of("reason","Add product promotion","discounts",List.of(Map.of("applicationId",UUID.randomUUID().toString(),"printedLabel","Sale promotion","appliedAmount","1.58","kind","SALE_PROMOTION","lineItemIds",List.of(line.toString()),"wholeReceipt",false,"qualifyingQuantities",Map.of(line.toString(),2))))));
        var corrected=ReceiptCorrection.read(old,f).transaction();
        assertEquals(new BigDecimal("8.00"),new TransactionCalculator().calculateAfterCouponTotal(corrected));
        assertEquals(List.of(line),corrected.getAppliedCoupons().getFirst().getLineItemIds());
        assertEquals(ReceiptObservations.DiscountKind.SALE_PROMOTION,corrected.getObservations().discountKinds().get(corrected.getAppliedCoupons().getFirst().getApplicationId()));
    }
    @Test void resolvesOtherFundingAndMovesMisclassifiedCouponToRRExactlyOnce() {
        UUID line=UUID.randomUUID(),coupon=UUID.randomUUID(),payment=UUID.randomUUID(),rr=UUID.randomUUID();
        var tx=new CouponTransaction(new ReceiptTotals(new BigDecimal("0.21"),new BigDecimal("0.79")));
        tx.addPurchaseItem(new PurchaseItem(line,"Nivea",null,new BigDecimal("3.79"),new BigDecimal("3.79"),2));
        tx.addAppliedCoupon(new CouponApplied(coupon,"Lysol RR",new BigDecimal("5"),List.of()));
        tx.addPayment(new Payment(payment,PaymentType.OTHER,new BigDecimal("0.79")));
        var old=new ReceiptRecord(UUID.randomUUID(),ReceiptStatus.ACTIVE,1,tx,new ReceiptMeta("Walgreens",null,null,null));
        var input=Json.parseObject(Json.object(Map.of("reason","Classify printed RR","discounts",List.of(),"rrUses",List.of(Map.of("eventId",rr,"amount","5","recordedDescription","Lysol RR")),"rrReclassifications",List.of(Map.of("kind","COUPON_APPLIED","id",coupon,"targetEventId",rr)),"payments",List.of(Map.of("paymentId",payment,"paymentType","OTHER","amount","0.79","role","FUNDING")))));
        var correction=ReceiptCorrection.read(old,input).transaction();
        assertTrue(correction.getAppliedCoupons().isEmpty());assertEquals(1,correction.getObservations().unidentifiedRR().size());
        assertEquals(TransactionCalculator.PaymentRole.FUNDING,new TransactionCalculator().paymentRole(correction,correction.getPayments().getFirst()));
        assertEquals(new TransactionCalculator().calculateExpectedCheckoutAmount(tx),new TransactionCalculator().calculateExpectedCheckoutAmount(correction));
        var bad=Json.parseObject(Json.object(input));
        @SuppressWarnings("unchecked") var rows=(List<Map<String,Object>>)bad.get("rrUses");rows.getFirst().put("amount","7");
        assertThrows(ReceiptCorrection.InvalidFields.class,()->ReceiptCorrection.read(old,bad));
    }
    @Test void reportsZeroPaymentAndCleansRemovedWorksheetReferences() {
        UUID payment=UUID.randomUUID();var tx=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,BigDecimal.ONE));
        tx.addPurchaseItem(new PurchaseItem("Item",null,BigDecimal.ONE,BigDecimal.ONE,1));tx.addPayment(new Payment(payment,PaymentType.OTHER,BigDecimal.ONE));
        tx.setObservations(new ReceiptObservations(null,null,List.of(),List.of(),null,List.of(),Map.of(),null,List.of(),new ReceiptWorksheetData(Map.of(),Map.of(payment,new ReceiptWorksheetData.Use(List.of(),null,false)),null),ReceiptObservations.FooterStatus.UNKNOWN));
        var old=new ReceiptRecord(UUID.randomUUID(),ReceiptStatus.ACTIVE,1,tx,new ReceiptMeta("Store",null,null,null));
        var error=assertThrows(ReceiptCorrection.InvalidFields.class,()->ReceiptCorrection.read(old,Json.parseObject(Json.object(Map.of("reason","Check payment","payments",List.of(Map.of("paymentId",payment,"paymentType","OTHER","amount","0")))))));
        assertTrue(error.getMessage().contains("greater than zero"));
        assertTrue(ReceiptCorrection.read(old,Map.of("reason","Remove payment","payments",List.of())).transaction().getObservations().worksheet().uses().isEmpty());
    }

    @Test void importedPaperDeductionIsCountedOnceAndRrLabelsStayUnclassified() {
        var text="Walgreens\n10/08/2026 9:34 AM\nBUY 1 GET 1 50% OFF\nAQUAPHOR BALM\n07214003127 A 6.74\nREGULAR PRICE 13.49\nMYWALGREENS SAVINGS 6.75\nRETURN VALUE 8.24\nNIVEA LOTION\n07214002421 A 14.49\nRETURN VALUE 0.00\nMFG CPN -10.00\nMFG CPN -4.00\nMFG ECPN -2.00\nMFG ECPN -2.00\nSUBTOTAL 3.23\nSALES TAX A=8.25% 0.27\nTOTAL 3.50\nVISA ACCT 0254 3.50\nMYWALGREENS SAVINGS 6.75\nMFG COUPON SAVINGS 18.00\nRFN# 0476-1231-0521-2610-0803\nTOTAL SAVINGS 24.75\nSAVINGS VALUE 88%\nOPENING BALANCE 274.95\nEARNED THIS VISIT 12.10\nCLOSING BALANCE 287.05";
        var result=ReceiptImport.parse("paper",text,false);
        assertEquals("3.50",result.get("reported"));assertEquals("2026-10-08",result.get("day"));
        assertEquals("0476-1231-0521-2610-0803",ReceiptImport.parse("ocr",text.replace("RFN#","REN#").replace("2610-0803","26 10-0803"),true).get("number"));
        @SuppressWarnings("unchecked") var items=(List<Map<String,String>>)result.get("items");assertEquals(2,items.size());assertEquals("13.49",items.getFirst().get("regular"));assertEquals("",items.getFirst().get("sale"));assertEquals("",items.get(1).get("regular"));
        @SuppressWarnings("unchecked") var ds=(List<Map<String,String>>)result.get("deductions");assertEquals(5,ds.size());assertEquals(2,ds.stream().filter(d->d.get("kind").equals("UNCLASSIFIED")).count());assertEquals(1,ds.stream().filter(d->d.get("kind").equals("SALE_PROMOTION")).count());
    }
    @Test void walgreensRxIdentifiersAndExplicitPricesImportWithoutProductUpcGuessing() {
        var result=ReceiptImport.parse("rx","Walgreens\n10/25/2026 4:23 PM\nFSA-RX 1509950 15.00\nFSA RX 1502600 45,00\nTOTAL 60.00\nVISA ACCT 0254 60.00\nRFN# 1151-3418-6519-2610-2503",false);
        @SuppressWarnings("unchecked") var items=(List<Map<String,String>>)result.get("items");assertEquals(2,items.size());assertEquals("[FSA/HSA] RX",items.getFirst().get("description"));assertEquals("1509950",items.getFirst().get("sku"));assertEquals("15.00",items.getFirst().get("regular"));assertEquals("45.00",items.get(1).get("regular"));
    }
    @Test void suppliedWebsitePdfExtractsAnEditableDraftWithoutWrites()throws Exception {
        var path=java.nio.file.Path.of("/Users/anitawoodford/Downloads/nivea and aquaphor.pdf");org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.exists(path));
        var result=ReceiptImport.extract("website","application/pdf",java.nio.file.Files.readAllBytes(path));
        @SuppressWarnings("unchecked") var items=(List<Map<String,String>>)result.get("items");
        assertEquals(2,items.size(),result.toString());assertEquals("3.50",result.get("reported"));assertEquals("0476-1231-0521-2610-0803",result.get("number"));
        assertTrue(items.stream().allMatch(i->i.get("regular").isBlank()));
        assertTrue(items.stream().allMatch(i->!i.get("sale").isBlank()),items.toString());
        assertEquals("14.49",items.getFirst().get("sale"));assertEquals("6.74",items.get(1).get("sale"));
    }
    @Test void correctsRewardAdjustmentTimeWithoutInventingProductsOrChangingRewards() {
        var fields=ReceiptInputTest.draft(UUID.randomUUID());
        fields.remove("rrUsed");fields.remove("rrUsageId");
        fields.put("receiptKind","REWARD_ADJUSTMENT");fields.put("items",List.of());fields.put("discounts",List.of());fields.put("payments",List.of());fields.put("tax","0");fields.put("reportedTotal","0");
        fields.put("earnings",List.of(Map.of("earningId",UUID.randomUUID(),"kind","WALGREENS_CASH","provider","Walgreens","form","STORE_REWARD","amount","5","status","CREDITED","confirmedAmount","5","unit","USD","confirmAmountAndUnit",true)));
        var input=ReceiptInput.read(Json.parseObject(Json.object(fields)));var original=input.record();
        var correction=ReceiptCorrection.read(original,Map.of("reason","Correct printed time","printedTime","16:35","items",List.of()));
        assertEquals(java.time.LocalTime.of(16,35),correction.metadata().purchasedAt().toLocalTime());
        assertEquals(ReceiptMeta.ReceiptKind.REWARD_ADJUSTMENT,correction.metadata().receiptKind());
        assertTrue(correction.transaction().getPurchaseItems().isEmpty());
        assertEquals(original.transaction().getObservations().earnings(),correction.transaction().getObservations().earnings());
        assertThrows(ReceiptCorrection.InvalidFields.class,()->ReceiptCorrection.read(original,Map.of("reason","Invalid purchase conversion","receiptKind","PURCHASE","items",List.of())));
    }

}
