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
}
