package coupons.api;

import coupons.*;
import coupons.repository.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReceiptInputTest {
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
    @Test void buildsExistingModelsAndPreservesExactReportedDiscrepancyAndRewardSemantics() {
        var input=read(draft(UUID.randomUUID()));var tx=input.transaction();var calc=new TransactionCalculator();
        assertEquals(0,new BigDecimal("7.27").compareTo(calc.calculateExpectedCheckoutAmount(tx)));
        assertEquals(0,new BigDecimal("0.01").compareTo(calc.calculateReceiptDifference(tx)));
        assertEquals(new BigDecimal("10.0000"),tx.getPurchaseItems().getFirst().getRegularUnitPrice());
        assertEquals("0000123",tx.getPurchaseItems().getFirst().getProductCode());
        assertEquals(0,new BigDecimal("5.000").compareTo(SavingsReport.calculate(List.of(input.record())).newlyEarned()));
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
}
