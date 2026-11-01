package coupons.api;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Draft 1.69 acceptance examples, using the guarded disposable API fixture. */
class ReceiptAuthorityIntegrationTest {
    private final PostgresApiIntegrationTest fixture = new PostgresApiIntegrationTest();
    @BeforeEach void setup() throws Exception { fixture.setup(); }
    @AfterEach void cleanup() throws Exception { fixture.cleanup(); }

    @Test void authoritative2716SurvivesPreviewSaveRestartReportAndExplicitCorrection() throws Exception {
        UUID id=UUID.randomUUID(), line=UUID.randomUUID();
        var fields=ReceiptInputTest.draft(id);
        fields.remove("rrUsed");fields.remove("rrUsageId");fields.put("earnings",List.of());
        fields.put("items",List.of(Map.of("lineItemId",line,"description","Synthetic authority fixture","regularUnitPrice","30.00","unitPrice","30.00","quantity",1)));
        fields.put("discounts",List.of(Map.of("applicationId",UUID.randomUUID(),"label","Recorded discount","amount","5.00","kind","STORE_COUPON","wholeReceipt",true)));
        fields.put("tax","2.06");fields.put("reportedTotal","27.16");
        fields.put("payments",List.of(Map.of("paymentId",UUID.randomUUID(),"type","CASH","amount","27.16")));
        fields.put("printedTotalBasis","BEFORE_PAYMENT_FUNDING");fields.put("paymentEntryStatus","COMPLETE");
        String original=Json.object(fields);
        var preview=fixture.call("POST","/receipts/preview",original,null,null);
        assertEquals(200,preview.statusCode(),preview.body());
        assertTrue(fixture.receipts.findById(id).isEmpty());
        assertEquals(original,Json.object(fields));
        var parsed=Json.parseObject(preview.body());
        @SuppressWarnings("unchecked") var totals=(Map<String,Object>)parsed.get("totals");
        decimal("27.16",totals.get("reportedDue"));decimal("27.06",totals.get("calculatedDue"));decimal("0.10",totals.get("difference"));
        fields.put("reviewToken",parsed.get("reviewToken"));fields.put("acknowledgeDifference",true);
        var save=fixture.call("POST","/receipts",Json.object(fields),"authority-create",null);
        assertEquals(201,save.statusCode(),save.body());
        fixture.server.stop(0);fixture.start();
        var record=fixture.receipts.findById(id).orElseThrow();
        decimal("0.10",new coupons.receipts.TransactionCalculator().calculateReceiptDifference(record.transaction()));
        var detail=fixture.call("GET","/receipts/"+id,null,null,null);
        assertEquals(200,detail.statusCode(),detail.body());
        @SuppressWarnings("unchecked") var contents=(Map<String,Object>)Json.parseObject(detail.body()).get("contents");
        @SuppressWarnings("unchecked") var recordedTotals=(Map<String,Object>)contents.get("receiptTotals");
        decimal("27.16",recordedTotals.get("finalTotal"));decimal("2.06",recordedTotals.get("taxAmount"));
        @SuppressWarnings("unchecked") var item=(Map<String,Object>)((List<?>)contents.get("purchaseItems")).getFirst();
        decimal("30.00",item.get("regularUnitPrice"));decimal("30.00",item.get("saleUnitPrice"));decimal("1",item.get("quantity"));
        @SuppressWarnings("unchecked") var discount=(Map<String,Object>)((List<?>)contents.get("appliedCoupons")).getFirst();
        decimal("5.00",discount.get("appliedAmount"));
        @SuppressWarnings("unchecked") var payment=(Map<String,Object>)((List<?>)contents.get("payments")).getFirst();
        assertEquals("CASH",payment.get("paymentType"));decimal("27.16",payment.get("amount"));
        var calculated=fixture.call("GET","/receipts/"+id+"/totals",null,null,null);
        assertEquals(200,calculated.statusCode(),calculated.body());
        var loadedTotals=Json.parseObject(calculated.body());
        decimal("27.16",loadedTotals.get("reportedDue"));decimal("27.06",loadedTotals.get("calculatedDue"));decimal("0.10",loadedTotals.get("difference"));
        var report=fixture.call("GET","/workspace",null,null,null);
        assertEquals(200,report.statusCode(),report.body());
        @SuppressWarnings("unchecked") var reportRow=(Map<String,Object>)((List<?>)Json.parseObject(report.body()).get("receipts")).getFirst();
        @SuppressWarnings("unchecked") var reportTotals=(Map<String,Object>)reportRow.get("totals");
        decimal("27.16",reportTotals.get("reportedDue"));decimal("27.06",reportTotals.get("calculatedDue"));decimal("0.10",reportTotals.get("difference"));
        byte[] evidence=fixture.receipts.findEvidenceByReceiptId(id).getFirst().getContent();
        var edit=new LinkedHashMap<String,Object>(Map.of("reason","Explicit synthetic correction","reportedTotal","27.17"));
        var review=fixture.call("POST","/receipts/"+id+"/edit-preview",Json.object(edit),null,"1");
        assertEquals(200,review.statusCode(),review.body());
        edit.put("reviewToken",Json.parseObject(review.body()).get("reviewToken"));edit.put("acknowledgeDifference",true);
        var corrected=fixture.call("PUT","/receipts/"+id,Json.object(edit),"authority-correction","1");
        assertEquals(200,corrected.statusCode(),corrected.body());
        assertEquals(2,fixture.receipts.findHistoryByReceiptId(id).size());
        assertArrayEquals(evidence,fixture.receipts.findEvidenceByReceiptId(id).getFirst().getContent());
        try(var connection=fixture.db.open();var statement=connection.prepareStatement("SELECT revision,difference FROM receipt_financial_revisions WHERE transaction_id=? ORDER BY revision")) {
            statement.setObject(1,id);
            try(var rows=statement.executeQuery()) {
                assertTrue(rows.next());assertEquals(1,rows.getLong(1));decimal("0.10",rows.getBigDecimal(2));
                assertTrue(rows.next());assertEquals(2,rows.getLong(1));decimal("0.11",rows.getBigDecimal(2));assertFalse(rows.next());
            }
        }
        decimal("0.10",new coupons.receipts.TransactionCalculator().calculateReceiptDifference(record.transaction()));
    }
    private static void decimal(String expected,Object actual) {
        assertNotNull(actual);assertEquals(0,new BigDecimal(expected).compareTo(new BigDecimal(actual.toString())));
    }
}
