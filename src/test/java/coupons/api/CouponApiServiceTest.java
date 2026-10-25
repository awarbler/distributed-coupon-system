package coupons.api;

import com.sun.net.httpserver.HttpServer;
import coupons.*;
import coupons.repository.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP requests over loopback; isolated repositories, never local receipt files. */
class CouponApiServiceTest {
    final InMemoryTransactionRepository receipts = new InMemoryTransactionRepository();
    final Plans plans = new Plans();
    final Coupons coupons = new Coupons();
    HttpServer server;
    final HttpClient client = HttpClient.newHttpClient();
    String base;
    @BeforeEach void start() throws Exception {
        server = new CouponApiService(receipts,plans,coupons,"reader","owner").start(0);
        base="http://127.0.0.1:"+server.getAddress().getPort();
    }
    @AfterEach void stop() throws Exception { server.stop(0); client.close(); receipts.close(); }
    HttpResponse<String> call(String method,String path,String token,String body,String key,String revision) throws Exception {
        var b=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(10));
        if(token!=null)b.header("Authorization","Bearer "+token);
        if(key!=null)b.header("Idempotency-Key",key);
        if(revision!=null)b.header("If-Match-Revision",revision);
        b.header("Content-Type","application/json");
        b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
        return client.send(b.build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void editsEcbExpirationWithoutChangingMoneyOrStatusAndCanClearIt() throws Exception {
        UUID id=UUID.randomUUID(),earningId=UUID.randomUUID();
        var earning=EarningInput.read(Map.of("earningId",earningId.toString(),"kind","THIRD_PARTY_REBATE","provider","ECB","form","STORE_REWARD","amount","10","status","CREDITED","confirmedAmount","10","unit","USD","confirmAmountAndUnit",true));
        var tx=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,BigDecimal.ONE));tx.addPurchaseItem(new PurchaseItem("Synthetic",null,BigDecimal.ONE,BigDecimal.ONE,1));
        tx.setObservations(new ReceiptObservations(LocalDate.of(2026,10,4),null,List.of(earning),List.of(),null,List.of(),Map.of()));
        receipts.save(id,tx,new ReceiptMeta("CVS","Synthetic-expiry",null,null),List.of(),new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptCreateCtx("test"));
        String path="/receipts/"+id+"/earnings/"+earningId+"/associations";
        var result=call("PUT",path,"owner","{\"expirationDate\":\"2026-11-04\"}","expiry-save","1");assertEquals(200,result.statusCode(),result.body());
        var changed=receipts.findById(id).orElseThrow().transaction().getObservations().earnings().getFirst();
        assertEquals(LocalDate.of(2026,11,4),changed.expirationDate());assertEquals(earning.confirmed(),changed.confirmed());assertEquals(earning.status(),changed.status());assertEquals(earning.earningId(),changed.earningId());
        assertEquals(400,call("PUT",path,"owner","{\"expirationDate\":\"2026-10-03\"}","expiry-invalid","2").statusCode());
        assertEquals(200,call("PUT",path,"owner","{\"expirationDate\":null}","expiry-clear","2").statusCode());
        assertNull(receipts.findById(id).orElseThrow().transaction().getObservations().earnings().getFirst().expirationDate());
    }

    @Test void scenarioPreviewAndPlanSaveAreSeparateFromReceiptsAndBalances()throws Exception{
        var line=Map.of("id",UUID.randomUUID().toString(),"name","Scott","regular","5.00","quantity",2);
        var scenario=Map.of("items",List.of(line),"coupons",List.of(),"promotions",List.of(),"tax","0","walgreensCashUsed","0","registerRewardsUsed","0","giftCardUsed","0","paymentMethod","CASH");
        var preview=call("POST","/shopping-plans/scenario/preview","owner",Json.object(Map.of("scenario",scenario)),null,null);assertEquals(200,preview.statusCode(),preview.body());assertEquals(new BigDecimal("10.00"),Json.parseObject(preview.body()).get("calculatedOop"));
        var paid=new java.util.LinkedHashMap<String,Object>(scenario);paid.put("payments",List.of(Map.of("id",UUID.randomUUID().toString(),"type","ECB","amount","2","notes","Scott")));
        var paidPreview=call("POST","/shopping-plans/scenario/preview","owner",Json.object(Map.of("scenario",paid)),null,null);assertEquals(200,paidPreview.statusCode(),paidPreview.body());assertEquals(new BigDecimal("8.00"),Json.parseObject(paidPreview.body()).get("calculatedOop"));
        var whole=new java.util.LinkedHashMap<String,Object>(scenario);whole.put("coupons",List.of(Map.of("id",UUID.randomUUID().toString(),"description","ONLINE2","amount","2","requiredQuantity",1,"lineIds",List.of(),"kind","SALE_PROMOTION","wholeReceipt",true)));
        var wholePreview=call("POST","/shopping-plans/scenario/preview","owner",Json.object(Map.of("scenario",whole)),null,null);assertEquals(200,wholePreview.statusCode(),wholePreview.body());assertEquals(new BigDecimal("8.00"),Json.parseObject(wholePreview.body()).get("calculatedOop"));
        var id=UUID.randomUUID();var body=Map.of("planId",id.toString(),"name","Synthetic scenario","retailer","Walgreens","scenario",scenario);
        var created=call("POST","/shopping-plans","owner",Json.object(body),"scenario-create",null);assertEquals(201,created.statusCode(),created.body());assertEquals(2,plans.findById(id).orElseThrow().scenario().items().getFirst().quantity());
        var edited=call("PUT","/shopping-plans/"+id,"owner",Json.object(Map.of("name","Renamed")),"scenario-metadata","1");assertEquals(200,edited.statusCode(),edited.body());assertNotNull(plans.findById(id).orElseThrow().scenario());
        assertTrue(receipts.findById(id).isEmpty());
    }

    @Test void clearedPurchaseDateRemainsUnknownWhilePrintedTimeIsPreserved() {
        var fields=new java.util.LinkedHashMap<String,Object>();fields.put("transactionId",UUID.randomUUID().toString());fields.put("retailer","Store");fields.put("purchaseDate",null);fields.put("printedTime","14:07");fields.put("purchaseChannel","IN_STORE");fields.put("tax","0");fields.put("reportedTotal","1");fields.put("items",List.of(Map.of("lineItemId",UUID.randomUUID().toString(),"description","Product","regularUnitPrice","1","unitPrice","1","quantity",1)));
        var input=ReceiptInput.read(Json.parseObject(Json.object(fields)));assertNull(input.metadata().purchasedAt());assertNull(input.transaction().getObservations().purchaseDate());assertEquals("14:07",input.transaction().getObservations().printedTime());
    }

    @Test void draftHttpEndpointsAreOwnerOnlyAndIdempotent() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("COUPON_POSTGRES_TEST_URL")!=null);
        var connections=new PostgresConnectionFactory(System.getenv("COUPON_POSTGRES_TEST_URL"),System.getenv("COUPON_POSTGRES_TEST_USER"),System.getenv("COUPON_POSTGRES_TEST_PASSWORD"));
        try(var c=connections.open()){PostgresSchema.apply(c);}
        var repository=new PostgresTransactionRepository(connections);
        var platform=new PostgresPlatformRepository(connections);
        var service=new coupons.api.CouponApiService(repository,new PostgresShoppingPlanRepository(connections),new PostgresClippedCouponRepository(connections),"reader","owner",new PostgresApiRequestStore(connections),platform);
        var server=service.start(0);try{
            var id=UUID.randomUUID();var uri=java.net.URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/receipt-drafts/"+id);
            var client=java.net.http.HttpClient.newHttpClient();String body="{\"state\":{\"id\":\""+id+"\",\"store\":\"Walmart\",\"lines\":[]}}";
            var put=java.net.http.HttpRequest.newBuilder(uri).header("Authorization","Bearer owner").header("Content-Type","application/json").header("Idempotency-Key","draft-http").header("If-Match-Revision","0").PUT(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build();
            var first=client.send(put,java.net.http.HttpResponse.BodyHandlers.ofString());assertEquals(200,first.statusCode(),first.body());assertEquals(first.body(),client.send(put,java.net.http.HttpResponse.BodyHandlers.ofString()).body());
            var reader=java.net.http.HttpRequest.newBuilder(uri).header("Authorization","Bearer reader").GET().build();assertEquals(403,client.send(reader,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
            var get=java.net.http.HttpRequest.newBuilder(uri).header("Authorization","Bearer owner").GET().build();assertEquals(200,client.send(get,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());assertTrue(repository.findById(id).isEmpty());
            var del=java.net.http.HttpRequest.newBuilder(uri).header("Authorization","Bearer owner").header("Content-Type","application/json").header("Idempotency-Key","draft-delete").header("If-Match-Revision","1").method("DELETE",java.net.http.HttpRequest.BodyPublishers.ofString("{}")).build();assertEquals(200,client.send(del,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());assertEquals(404,client.send(get,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
        }finally{server.stop(0);}
    }

    String planBody(UUID id,String name) { return Json.object(Map.of("planId",id,"name",name)); }
    UUID receipt() {
        UUID id=UUID.randomUUID();
        CouponTransaction tx=new CouponTransaction(new ReceiptTotals(new BigDecimal("0.01"),new BigDecimal("1.02")));
        tx.addPurchaseItem(new PurchaseItem("Soap",null,new BigDecimal("1.00"),new BigDecimal("1.00"),1));
        receipts.save(id,tx,new ReceiptMeta("Store","000123",LocalDateTime.of(2026,10,4,9,0),null),List.of(),
                new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptCreateCtx("test"));
        return id;
    }
    @Test void healthIsPublicAndOtherEndpointsRequireAuthentication() throws Exception {
        assertEquals(200,call("GET","/health",null,null,null,null).statusCode());
        assertEquals(405,call("POST","/health",null,null,null,null).statusCode());
        assertEquals(401,call("GET","/receipts/"+UUID.randomUUID(),null,null,null,null).statusCode());
        assertEquals(401,call("GET","/shopping-plans/"+UUID.randomUUID(),"wrong",null,null,null).statusCode());
        assertEquals(403,call("POST","/shopping-plans","reader",planBody(UUID.randomUUID(),"x"),"key",null).statusCode());
        assertEquals(0,plans.writes);
    }
    @Test void receiptCorrectionExplainsZeroPaymentAndRRMatchingRequiresOwner() throws Exception {
        UUID id=receipt();
        var body=Json.object(Map.of("reason","Check payment","payments",List.of(Map.of("paymentId",UUID.randomUUID(),"paymentType","CASH","amount","0"))));
        var result=call("POST","/receipts/"+id+"/edit-preview","owner",body,null,"1");
        assertEquals(400,result.statusCode());assertTrue(result.body().contains("greater than zero"));
        assertEquals(403,call("POST","/receipts/"+id+"/rr-matches","reader","{}","match","1").statusCode());
        assertEquals(403,call("POST","/receipts/"+id+"/rr-matches/unknown-source","reader","{}","unknown","1").statusCode());
        assertEquals(403,call("POST","/rewards/register-rewards","reader","{}","standalone",null).statusCode());
    }
    @Test void configurationRejectsMissingOrAmbiguousCredentials() {
        assertThrows(IllegalArgumentException.class,()->new CouponApiService(receipts,plans,coupons," ",null));
        assertThrows(IllegalArgumentException.class,()->new CouponApiService(receipts,plans,coupons,"same","same"));
    }
    @Test void receiptReturnsStructuredValuesWithoutRecalculatingReportedTotal() throws Exception {
        UUID id=receipt();
        var r=call("GET","/receipts/"+id,"reader",null,null,null);
        assertEquals(200,r.statusCode());
        Map<String,Object> f=Json.parseObject(r.body());
        assertEquals("000123",((Map<?,?>)f.get("metadata")).get("receiptNumber"));
        assertTrue(r.body().contains("\"finalTotal\":1.02"));
        assertTrue(r.body().contains("Soap"));
        assertEquals("\"1\"",r.headers().firstValue("ETag").orElseThrow());
        assertEquals(404,call("GET","/receipts/"+UUID.randomUUID(),"reader",null,null,null).statusCode());
    }
    @Test void plansHandleEscapesNullsAndRepeatedUpdatesWithoutLosingLinks() throws Exception {
        UUID id=UUID.randomUUID(); String name="Trip, \"weekly\"\nfood";
        var created=call("POST","/shopping-plans","owner",planBody(id,name),"create",null);
        assertEquals(201,created.statusCode(),created.body());
        assertEquals(name,Json.parseObject(created.body()).get("name"));
        assertNull(Json.parseObject(created.body()).get("retailer"));
        UUID line=UUID.randomUUID(); ShoppingPlan p=plans.data.get(id);
        plans.data.put(id,new ShoppingPlan(id,p.name(),"Store",null,List.of(line),1));
        var updated=call("PUT","/shopping-plans/"+id,"owner","{\"name\":\"changed\"}","update","1");
        assertEquals(200,updated.statusCode(),updated.body());
        assertEquals(List.of(line),plans.data.get(id).plannedPurchaseIds());
        assertEquals("Store",plans.data.get(id).retailer());
        assertEquals(200,call("PUT","/shopping-plans/"+id,"owner","{\"name\":\"again\"}","update2","2").statusCode());
        assertEquals(3,plans.data.get(id).revision());
    }
    @Test void updateRequiresRevisionAndRejectsStaleOrMismatchedInputs() throws Exception {
        UUID id=UUID.randomUUID();call("POST","/shopping-plans","owner",planBody(id,"trip"),"c",null);
        assertEquals(428,call("PUT","/shopping-plans/"+id,"owner","{\"name\":\"x\"}","a",null).statusCode());
        assertEquals(409,call("PUT","/shopping-plans/"+id,"owner","{\"name\":\"x\"}","b","2").statusCode());
        assertEquals(400,call("PUT","/shopping-plans/"+id,"owner",planBody(UUID.randomUUID(),"x"),"d","1").statusCode());
        assertEquals(400,call("PUT","/shopping-plans/"+id,"owner","{\"name\":\"x\",\"plannedPurchaseIds\":[\""+UUID.randomUUID()+"\"]}","e","1").statusCode());
        assertEquals(1,plans.data.get(id).revision());
    }
    @Test void retryUsesExactRequestRouteAndRevisionAndReturnsOriginalResult() throws Exception {
        UUID id=UUID.randomUUID();String body=planBody(id,"trip");
        var first=call("POST","/shopping-plans","owner",body,"key",null);
        var again=call("POST","/shopping-plans","owner",body,"key",null);
        assertEquals(201,again.statusCode());assertEquals(first.body(),again.body());assertEquals(1,plans.writes);
        assertEquals(409,call("POST","/shopping-plans","owner",planBody(id,"other"),"key",null).statusCode());
        assertEquals(409,call("PUT","/shopping-plans/"+id,"owner",body,"key","1").statusCode());
        String change="{\"name\":\"v2\"}";
        var v2=call("PUT","/shopping-plans/"+id,"owner",change,"v2","1");
        call("PUT","/shopping-plans/"+id,"owner","{\"name\":\"v3\"}","v3","2");
        assertEquals(v2.body(),call("PUT","/shopping-plans/"+id,"owner",change,"v2","1").body());
        assertEquals(3,plans.data.get(id).revision());
    }
    @Test void simultaneousRetriesExecuteOnlyOneWrite() throws Exception {
        UUID id=UUID.randomUUID();String body=planBody(id,"trip");
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Callable<HttpResponse<String>> work=()->{start.await();return call("POST","/shopping-plans","owner",body,"same",null);};
            var a=pool.submit(work);var b=pool.submit(work);start.countDown();
            assertEquals(201,a.get(10,TimeUnit.SECONDS).statusCode());assertEquals(201,b.get(10,TimeUnit.SECONDS).statusCode());
            assertEquals(1,plans.writes);
        }
    }
    @Test void couponsExposeNullsAndRedemptionIsValidatedAndReplayable() throws Exception {
        UUID couponId=UUID.randomUUID(), receiptId=receipt();
        coupons.data.put(couponId,new ClippedCoupon(couponId,"Store",null,
                new Coupon("Save",BigDecimal.ONE,1,CouponType.MANUFACTURER,null),CouponStatus.CLIPPED,LocalDate.of(2026,10,1),1));
        var listing=call("GET","/clipped-coupons?provider=Store","reader",null,null,null);
        assertEquals(200,listing.statusCode());assertTrue(listing.body().startsWith("[{"));assertTrue(listing.body().contains("\"externalId\":null"));
        String path="/clipped-coupons/"+couponId+"/redeem";
        String body=Json.object(Map.of("transactionId",receiptId,"redeemedOn","2026-10-04"));
        var first=call("POST",path,"owner",body,"redeem","1");
        assertEquals(200,first.statusCode(),first.body());
        assertEquals(first.body(),call("POST",path,"owner",body,"redeem","1").body());
        assertEquals(1,coupons.redemptions);
        assertEquals(409,call("POST",path,"owner",body,"new-key","2").statusCode());
        assertEquals(404,call("POST",path,"owner",Json.object(Map.of("transactionId",UUID.randomUUID(),"redeemedOn","2026-10-04")),"missing","2").statusCode());
    }
    @Test void malformedOversizedUnknownAndMissingKeyRequestsDoNotWrite() throws Exception {
        for(String body:List.of("{", "{\"name\":\"a\",\"name\":\"b\"}", "{\"name\":\"a\",}", "{} trailing"))
            assertEquals(400,call("POST","/shopping-plans","owner",body,UUID.randomUUID().toString(),null).statusCode());
        assertEquals(400,call("POST","/shopping-plans","owner",planBody(UUID.randomUUID(),"x"),null,null).statusCode());
        assertEquals(413,call("POST","/shopping-plans","owner"," ".repeat(65537),"large",null).statusCode());
        assertEquals(0,plans.writes);
    }
    @Test void internalErrorsDoNotExposeStorageDetails() throws Exception {
        plans.fail=true;
        var r=call("GET","/shopping-plans/"+UUID.randomUUID(),"reader",null,null,null);
        assertEquals(500,r.statusCode());assertFalse(r.body().contains("secret"));
    }
    static class Plans implements ShoppingPlanRepository {
        final Map<UUID,ShoppingPlan> data=new ConcurrentHashMap<>();int writes;boolean fail;
        public synchronized void save(ShoppingPlan p,String actor){if(data.putIfAbsent(p.planId(),p)!=null)throw new IllegalArgumentException();writes++;}
        public Optional<ShoppingPlan> findById(UUID id){if(fail)throw new IllegalStateException("secret DB password and path");return Optional.ofNullable(data.get(id));}
        public Optional<ShoppingPlan> findByRevision(UUID id,long rev){return findById(id).filter(p->p.revision()==rev);}
        public synchronized ShoppingPlan update(ShoppingPlan p,long expected,String actor,String reason){if(data.get(p.planId()).revision()!=expected)throw new ConcurrentModificationException();data.put(p.planId(),p);writes++;return p;}
    }
    static class Coupons implements ClippedCouponRepository {
        final Map<UUID,ClippedCoupon> data=new ConcurrentHashMap<>();int redemptions;
        public void save(ClippedCoupon c,String actor){data.put(c.couponId(),c);}
        public Optional<ClippedCoupon> findById(UUID id){return Optional.ofNullable(data.get(id));}
        public List<ClippedCoupon> findByProvider(String p){return data.values().stream().filter(c->c.provider().equals(p)).toList();}
        public ClippedCoupon update(ClippedCoupon c,long expected,String actor,String reason){throw new UnsupportedOperationException();}
        public synchronized ClippedCoupon redeem(UUID id,UUID receipt,LocalDate date,long expected,String actor){var old=data.get(id);if(old.revision()!=expected)throw new ConcurrentModificationException();if(old.status()==CouponStatus.APPLIED)throw new IllegalStateException();var next=new ClippedCoupon(id,old.provider(),old.externalId(),old.coupon(),CouponStatus.APPLIED,old.clippedOn(),date,receipt,expected+1);data.put(id,next);redemptions++;return next;}
    }

    @Test void beforeFundingReceiptSavesWithoutFalseWalgreensCashDiscrepancy() throws Exception {
        var fields = ReceiptInputTest.draft(UUID.randomUUID());
        fields.put("retailer", "Walgreens"); fields.put("reportedTotal", "25.96"); fields.put("tax", "1.98");
        fields.put("printedTotalBasis", "BEFORE_PAYMENT_FUNDING"); fields.put("paymentEntryStatus", "COMPLETE");
        fields.put("items", List.of(Map.of("lineItemId", UUID.randomUUID(), "description", "Basket", "unitPrice", "31.98", "quantity", 1)));
        fields.put("discounts", List.of(Map.of("applicationId", UUID.randomUUID(), "label", "Recorded discounts", "amount", "8", "kind", "STORE_COUPON", "wholeReceipt", true)));
        fields.remove("rrUsed"); fields.put("payments", List.of(
                Map.of("paymentId", UUID.randomUUID(), "type", "WALGREENS_CASH", "amount", "20"),
                Map.of("paymentId", UUID.randomUUID(), "type", "CARD", "amount", "5.96")));
        fields.put("earnings", List.of()); fields.put("evidence", List.of());
        var preview = call("POST", "/receipts/preview", "owner", Json.object(fields), null, null);
        assertEquals(200, preview.statusCode(), preview.body());
        var response = Json.parseObject(preview.body());
        var totals = (Map<?, ?>) response.get("totals");
        var review = (Map<?, ?>) totals.get("paymentReview");
        assertEquals("BALANCED", review.get("status"));
        assertEquals(false, review.get("acknowledgementRequired"));
        assertEquals(0, ((BigDecimal) totals.get("difference")).compareTo(new BigDecimal("20")));
        fields.put("reviewToken", response.get("reviewToken"));
        var created = call("POST", "/receipts", "owner", Json.object(fields), "split-funding", null);
        assertEquals(201, created.statusCode(), created.body());
        var saved = receipts.findById(UUID.fromString(fields.get("transactionId").toString())).orElseThrow();
        assertEquals(ReceiptMeta.PrintedTotalBasis.BEFORE_PAYMENT_FUNDING, saved.metadata().printedTotalBasis());
        assertEquals(0, saved.transaction().getReceiptTotals().getFinalTotal().compareTo(new BigDecimal("25.96")));
    }

    @Test void receiptCorrectionsRequirePreviewRevisionAndOwnerAndRetainHistory() throws Exception {
        UUID id = receipt();
        var body = new LinkedHashMap<String,Object>();
        body.put("retailer","Corrected Store"); body.put("reason","Correct store name");
        String payload = Json.object(body);
        assertEquals(403,call("POST","/receipts/"+id+"/edit-preview","reader",payload,"preview","1").statusCode());
        assertEquals(409,call("PUT","/receipts/"+id,"owner",payload,"no-preview","1").statusCode());
        var preview = call("POST","/receipts/"+id+"/edit-preview","owner",payload,"preview","1");
        assertEquals(200,preview.statusCode(),preview.body());
        body.put("reviewToken",Json.parseObject(preview.body()).get("reviewToken"));
        body.put("acknowledgeDifference",true);
        var before = ReceiptSnapshot.from(receipts.findById(id).orElseThrow().transaction());
        var saved = call("PUT","/receipts/"+id,"owner",Json.object(body),"correct","1");
        assertEquals(200,saved.statusCode(),saved.body());
        assertEquals(before,ReceiptSnapshot.from(receipts.findById(id).orElseThrow().transaction()));
        assertEquals("Corrected Store",receipts.findById(id).orElseThrow().metadata().retailer());
        assertEquals(2,receipts.findById(id).orElseThrow().revision());
        assertEquals(saved.body(),call("PUT","/receipts/"+id,"owner",Json.object(body),"correct","1").body());
        assertEquals(409,call("PUT","/receipts/"+id,"owner",Json.object(body),"stale","1").statusCode());
        assertEquals("Store",receipts.creationHistory().getFirst().after().metadata().retailer());
    }
    @Test void receiptDeletionAndRestorationRequireConfirmationAndPreserveContents() throws Exception {
        UUID id = receipt(); var before = ReceiptSnapshot.from(receipts.findById(id).orElseThrow().transaction());
        String payload = Json.object(Map.of("reason","Duplicate entry","confirmed",true));
        assertEquals(403,call("DELETE","/receipts/"+id,"reader",payload,"delete-reader","1").statusCode());
        assertEquals(400,call("DELETE","/receipts/"+id,"owner","{\"reason\":\"Duplicate\"}","unconfirmed","1").statusCode());
        var deleted = call("DELETE","/receipts/"+id,"owner",payload,"delete","1");
        assertEquals(200,deleted.statusCode(),deleted.body());
        assertTrue(receipts.findById(id).isEmpty());
        assertEquals(before,ReceiptSnapshot.from(receipts.findDeletedById(id).orElseThrow().transaction()));
        assertEquals(deleted.body(),call("DELETE","/receipts/"+id,"owner",payload,"delete","1").body());
        assertEquals(200,call("POST","/receipts/"+id+"/restore","owner",payload,"restore","2").statusCode());
        assertEquals(3,receipts.findById(id).orElseThrow().revision());
        assertEquals(before,ReceiptSnapshot.from(receipts.findById(id).orElseThrow().transaction()));
    }

    @Test void correctedProductsAndPaymentsRetainStableIdsAndRejectChangedPreview() throws Exception {
        UUID id = receipt(); var old = receipts.findById(id).orElseThrow();
        UUID line = old.transaction().getPurchaseItems().getFirst().getLineItemId(), payment = UUID.randomUUID();
        var f = new LinkedHashMap<String,Object>();
        f.put("reason","Correct product price and record card payment"); f.put("reportedTotal","0.81");
        f.put("items",List.of(Map.of("lineItemId",line.toString(),"productName","Soap","regularUnitPrice","1.00","saleUnitPrice","0.80","quantity",1)));
        f.put("payments",List.of(Map.of("paymentId",payment.toString(),"paymentType","CARD","amount","0.81")));
        var preview = call("POST","/receipts/"+id+"/edit-preview","owner",Json.object(f),"price-preview","1");
        assertEquals(200,preview.statusCode(),preview.body());
        f.put("reviewToken",Json.parseObject(preview.body()).get("reviewToken")); f.put("acknowledgeDifference",true);
        f.put("reportedTotal","9.99");
        assertEquals(409,call("PUT","/receipts/"+id,"owner",Json.object(f),"altered-preview","1").statusCode());
        assertEquals(1,receipts.findById(id).orElseThrow().revision());
        f.put("reportedTotal","0.81");
        var saved = call("PUT","/receipts/"+id,"owner",Json.object(f),"price-save","1");
        assertEquals(200,saved.statusCode(),saved.body());
        var tx = receipts.findById(id).orElseThrow().transaction();
        assertEquals(line,tx.getPurchaseItems().getFirst().getLineItemId());
        assertEquals(new BigDecimal("0.80"),tx.getPurchaseItems().getFirst().getSaleUnitPrice());
        assertEquals(payment,tx.getPayments().getFirst().getPaymentId());
        assertEquals(old.transaction().getObservations(),tx.getObservations());
    }

}
