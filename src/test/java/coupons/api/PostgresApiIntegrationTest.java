package coupons.api;

import com.sun.net.httpserver.HttpServer;
import coupons.*;
import coupons.repository.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** A private schema in an explicitly configured disposable test database. */
class PostgresApiIntegrationTest {
    PostgresConnectionFactory admin,db;
    String schema,base;
    HttpServer server;
    HttpClient client;
    PostgresTransactionRepository receipts;
    PostgresShoppingPlanRepository plans;
    PostgresClippedCouponRepository coupons;
    @BeforeEach void setup() throws Exception {
        String url=System.getenv("COUPON_POSTGRES_TEST_URL");
        Assumptions.assumeTrue(url!=null && System.getenv("COUPON_POSTGRES_TEST_USER")!=null && System.getenv("COUPON_POSTGRES_TEST_PASSWORD")!=null);
        admin=new PostgresConnectionFactory(url,System.getenv("COUPON_POSTGRES_TEST_USER"),System.getenv("COUPON_POSTGRES_TEST_PASSWORD"));
        schema="api_test_"+UUID.randomUUID().toString().replace("-","");
        try(Connection c=admin.open()) { c.createStatement().execute("CREATE SCHEMA "+schema); }
        db=new PostgresConnectionFactory(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,System.getenv("COUPON_POSTGRES_TEST_USER"),System.getenv("COUPON_POSTGRES_TEST_PASSWORD"));
        try(Connection c=db.open()) { PostgresSchema.apply(c); }
        receipts=new PostgresTransactionRepository(db);plans=new PostgresShoppingPlanRepository(db);coupons=new PostgresClippedCouponRepository(db);
        client=HttpClient.newHttpClient();start();
    }
    void start() throws Exception {
        server=new CouponApiService(receipts,plans,coupons,"reader","owner",new PostgresApiRequestStore(db),new PostgresPlatformRepository(db)).start(0);
        base="http://127.0.0.1:"+server.getAddress().getPort();
    }
    @AfterEach void cleanup() throws Exception {
        if(server!=null)server.stop(0);if(client!=null)client.close();
        if(admin!=null && schema!=null)try(Connection c=admin.open()){c.createStatement().execute("DROP SCHEMA "+schema+" CASCADE");}
    }
    HttpResponse<String> call(String method,String path,String body,String key,String revision) throws Exception {
        var b=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(20)).header("Authorization","Bearer owner").header("Content-Type","application/json");
        if(key!=null)b.header("Idempotency-Key",key);if(revision!=null)b.header("If-Match-Revision",revision);
        return client.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    UUID receipt() {
        UUID id=UUID.randomUUID();
        var tx=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,BigDecimal.ONE));
        tx.addPurchaseItem(new PurchaseItem("Soap",null,BigDecimal.ONE,BigDecimal.ONE,1));
        receipts.save(id,tx,new ReceiptMeta("Test store","001",null,null),List.of(),new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptCreateCtx("test"));return id;
    }
    UUID coupon() {
        UUID id=UUID.randomUUID();
        coupons.save(new ClippedCoupon(id,"Test provider",null,new Coupon("Test",BigDecimal.ONE,1,CouponType.MANUFACTURER,LocalDate.of(2026,12,31)),CouponStatus.CLIPPED,LocalDate.of(2026,10,1),1),"test");return id;
    }
    @Test void newOperationalWritesRequireOwnerAuthorization() throws Exception {
        for(String path:List.of("/deals","/clipped-coupons","/receipts/"+UUID.randomUUID()+"/earnings")) {
            var request=HttpRequest.newBuilder(URI.create(base+path)).header("Authorization","Bearer reader").header("Content-Type","application/json").header("Idempotency-Key",UUID.randomUUID().toString()).POST(HttpRequest.BodyPublishers.ofString("{}")).build();assertEquals(403,client.send(request,HttpResponse.BodyHandlers.ofString()).statusCode());
        }
    }
    @Test void postSaveRebatesRetainIssuerUnitsDestinationsScopeAndRegisterTotals() throws Exception {
        UUID receipt=receipt();var original=receipts.findById(receipt).orElseThrow();UUID line=original.transaction().getPurchaseItems().getFirst().getLineItemId();
        long revision=1;
        for(String provider:List.of("Fetch","P&G Good Everyday","Ibotta","Custom provider")) {
            UUID earning=UUID.randomUUID();boolean points=provider.equals("Fetch")||provider.startsWith("P&G");var body=new LinkedHashMap<String,Object>();var e=new LinkedHashMap<String,Object>();
            e.put("earningId",earning);e.put("kind","THIRD_PARTY_REBATE");e.put("provider",provider);e.put("offer",provider+" distinct offer");e.put("form",points?"POINTS":"CASH");e.put("amount",points?"25.0000":"2.50000000000000000001");e.put("status","EXPECTED");e.put("lineItemIds",List.of(line));e.put("qualifyingQuantities",Map.of(line.toString(),1));e.put("payoutDestination",provider.equals("Ibotta")?"VENMO":"PAYPAL");e.put("submissionReference","reference-"+provider);body.put("earning",e);
            if(provider.equals("Ibotta")){var evidenceId=UUID.randomUUID();e.put("evidenceIds",List.of(evidenceId));body.put("evidence",List.of(Map.of("evidenceId",evidenceId,"mediaType","image/png","contentBase64","AQID")));}
            var preview=call("POST","/receipts/"+receipt+"/earnings/preview",Json.encode(body),null,""+revision);assertEquals(200,preview.statusCode(),preview.body());assertEquals(revision,receipts.findById(receipt).orElseThrow().revision());
            var result=call("POST","/receipts/"+receipt+"/earnings",Json.encode(body),"rebate-"+provider,""+revision);assertEquals(201,result.statusCode(),result.body());assertEquals(result.body(),call("POST","/receipts/"+receipt+"/earnings",Json.encode(body),"rebate-"+provider,""+revision).body());
            assertEquals(409,call("POST","/receipts/"+receipt+"/earnings",Json.encode(body),"stale-"+provider,""+revision).statusCode());revision++;
            e.put("earningId",UUID.randomUUID());assertEquals(400,call("POST","/receipts/"+receipt+"/earnings",Json.encode(body),"duplicate-"+provider,""+revision).statusCode());assertEquals(revision,receipts.findById(receipt).orElseThrow().revision());
        }
        var saved=receipts.findById(receipt).orElseThrow();assertEquals(4,saved.transaction().getObservations().earnings().size());assertEquals(1,saved.evidenceIds().size());assertArrayEquals(new byte[]{1,2,3},receipts.findEvidenceById(saved.evidenceIds().getFirst()).orElseThrow().getContent());assertEquals(5,receipts.findHistoryByReceiptId(receipt).size());assertEquals(original.transaction().getReceiptTotals().getFinalTotal(),saved.transaction().getReceiptTotals().getFinalTotal());assertEquals(BigDecimal.ZERO,SavingsReport.calculate(List.of(saved)).newlyEarned());
        var p=saved.transaction().getObservations().earnings().stream().filter(x->x.provider().equals("P&G Good Everyday")).findFirst().orElseThrow();assertEquals(new BigDecimal("25.0000"),p.expected().amount());assertEquals("PAYPAL",p.details().payoutDestination());assertNull(p.valuation());
        var associations=Json.encode(Map.of("lineItemIds",List.of(),"qualifyingQuantities",Map.of()));assertEquals(200,call("PUT","/receipts/"+receipt+"/earnings/"+p.earningId()+"/associations",associations,"scope",""+revision).statusCode());var updated=receipts.findById(receipt).orElseThrow().transaction().getObservations().earnings().stream().filter(x->x.earningId().equals(p.earningId())).findFirst().orElseThrow();assertEquals(ReceiptObservations.Scope.RECEIPT,updated.scope());assertEquals(p.expected(),updated.expected());assertEquals(p.details().payoutDestination(),updated.details().payoutDestination());
    }
    @Test void rewardAssociationsRejectExcessQuantitiesAndForeignEvidenceAtomically() throws Exception {
        UUID receipt=receipt();UUID line=receipts.findById(receipt).orElseThrow().transaction().getPurchaseItems().getFirst().getLineItemId();var e=new LinkedHashMap<String,Object>();e.put("earningId",UUID.randomUUID());e.put("kind","WALGREENS_CASH");e.put("form","STORE_REWARD");e.put("amount","5");e.put("status","CREDITED");e.put("confirmedAmount","5");e.put("unit","USD");e.put("confirmAmountAndUnit",true);e.put("lineItemIds",List.of(line));e.put("qualifyingQuantities",Map.of(line.toString(),2));
        assertEquals(400,call("POST","/receipts/"+receipt+"/earnings",Json.encode(Map.of("earning",e)),"overquantity","1").statusCode());assertEquals(1,receipts.findHistoryByReceiptId(receipt).size());
        e.put("qualifyingQuantities",Map.of(line.toString(),1));e.put("evidenceIds",List.of(UUID.randomUUID()));assertEquals(400,call("POST","/receipts/"+receipt+"/earnings",Json.encode(Map.of("earning",e)),"foreignEvidence","1").statusCode());assertEquals(1,receipts.findById(receipt).orElseThrow().revision());
    }
    @Test void paperCouponsHaveIndependentCopiesWithoutAccountAndAtomicRollback() throws Exception {
        var id=UUID.randomUUID();var body=new LinkedHashMap<String,Object>();body.put("couponId",id);body.put("provider","Manufacturer");body.put("description","Two dollars off soap");body.put("amount","2.0000");body.put("requiredQuantity",1);body.put("couponType","MANUFACTURER");body.put("source","PAPER_MANUFACTURER");body.put("discount","DOLLARS");body.put("copies",3);body.put("usageLimit",2);body.put("status","CLIPPED");body.put("clippedOn","2026-10-01");body.put("expirationDate","2026-10-31");
        var created=call("POST","/clipped-coupons",Json.encode(body),"paper",null);assertEquals(201,created.statusCode(),created.body());assertEquals(3,coupons.findByProvider("Manufacturer").size());assertNull(coupons.findById(id).orElseThrow().coupon().getTerms().accountRestriction());
        assertEquals(created.body(),call("POST","/clipped-coupons",Json.encode(body),"paper",null).body());assertEquals(3,coupons.findByProvider("Manufacturer").size());
        var receipt=receipt();assertEquals(200,call("POST","/clipped-coupons/"+id+"/redeem",Json.encode(Map.of("transactionId",receipt,"redeemedOn","2026-10-05")),"paperUsed","1").statusCode());
        var second=coupons.findByProvider("Manufacturer").stream().filter(c->!c.couponId().equals(id)).findFirst().orElseThrow();assertEquals(CouponStatus.CLIPPED,second.status());
        assertEquals(400,call("POST","/clipped-coupons/"+second.couponId()+"/redeem",Json.encode(Map.of("transactionId",receipt,"redeemedOn","2026-11-05")),"expired","1").statusCode());assertEquals(1,coupons.findById(second.couponId()).orElseThrow().revision());
    }
    @Test void structuredDealAndPlanPricingPersistWithoutReceiptWrites() throws Exception {
        var plan=UUID.randomUUID();assertEquals(201,call("POST","/shopping-plans",Json.encode(Map.of("planId",plan,"name","Shop","retailer","Store","shoppingDate","2026-10-19")),"plan9",null).statusCode());
        var body=new LinkedHashMap<String,Object>();body.put("dealId",UUID.randomUUID());body.put("title","Two soaps");body.put("retailer","Store");body.put("interpretedTerms","Buy 2 for $5");body.put("pricing",Map.of("type","MULTIBUY","productDescription","Soap","minimumQuantity",2,"promotionalPrice","5.00","repeatable",true));
        var result=call("POST","/deals",Json.encode(body),"deal9",null);assertEquals(201,result.statusCode(),result.body());
        assertEquals(result.body(),call("POST","/deals",Json.encode(body),"deal9",null).body());
        var d=new PostgresDealRepository(db).findById((UUID)body.get("dealId")).orElseThrow();assertEquals(DealTerms.Type.MULTIBUY,d.pricing().type());assertEquals(d,new PostgresDealRepository(db).findByRevision(d.dealId(),1).orElseThrow());
        body.put("dealId",UUID.randomUUID());assertEquals(400,call("POST","/deals",Json.encode(body),"duplicate9",null).statusCode());
        var purchase=Map.of("plannedPurchaseId",UUID.randomUUID(),"productName","Soap","quantity",3,"regularUnitPrice","3.00","priceSource","DEAL","advertisedDealId",d.dealId());
        assertEquals(201,call("POST","/shopping-plans/"+plan+"/purchases",Json.encode(purchase),"item9",null).statusCode());
        var workspace=call("GET","/workspace",null,null,null);assertEquals(200,workspace.statusCode(),workspace.body());assertTrue(workspace.body().contains("\"registerCost\":8.00"),workspace.body());
    }
    @Test void postgresHttpPlansPersistAndRetryAcrossServerRestart() throws Exception {
        UUID id=UUID.randomUUID();String body=Json.object(Map.of("planId",id,"name","Weekly, food"));
        var created=call("POST","/shopping-plans",body,"create",null);assertEquals(201,created.statusCode(),created.body());
        server.stop(0);start();
        assertEquals(created.body(),call("POST","/shopping-plans",body,"create",null).body());
        assertEquals(200,call("GET","/shopping-plans/"+id,null,null,null).statusCode());
        assertEquals(200,call("PUT","/shopping-plans/"+id,"{\"name\":\"Updated\"}","update","1").statusCode());
        assertEquals(409,call("PUT","/shopping-plans/"+id,"{\"name\":\"stale\"}","stale","1").statusCode());
        assertEquals("Updated",plans.findById(id).orElseThrow().name());
        assertEquals("Weekly, food",plans.findByRevision(id,1).orElseThrow().name());
    }
    @Test void multipleCouponsCanRedeemOnOneReceiptAndRetryWithoutAnotherRedemption() throws Exception {
        UUID receipt=receipt(),a=coupon(),b=coupon();
        String body=Json.object(Map.of("transactionId",receipt,"redeemedOn","2026-10-05"));
        var first=call("POST","/clipped-coupons/"+a+"/redeem",body,"a","1");assertEquals(200,first.statusCode(),first.body());
        assertEquals(200,call("POST","/clipped-coupons/"+b+"/redeem",body,"b","1").statusCode());
        server.stop(0);start();
        assertEquals(first.body(),call("POST","/clipped-coupons/"+a+"/redeem",body,"a","1").body());
        assertEquals(409,call("POST","/clipped-coupons/"+a+"/redeem",body,"again","2").statusCode());
        assertEquals(2,coupons.findById(a).orElseThrow().revision());
        var found=call("GET","/receipts/"+receipt,null,null,null);assertEquals(200,found.statusCode(),found.body());assertTrue(found.body().contains("Soap"));
        assertEquals(200,call("GET","/clipped-coupons?provider=Test%20provider",null,null,null).statusCode());
    }
    @Test void journalCoordinatesInstancesAndKeepsUncertainOutcomesReserved() throws Exception {
        var a=new PostgresApiRequestStore(db);var b=new PostgresApiRequestStore(db);
        var writes=new java.util.concurrent.atomic.AtomicInteger();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            var x=pool.submit(()->{start.await();return a.execute("owner","race","same",()->{writes.incrementAndGet();return new ApiRequestStore.Result(201,"{}");});});
            var y=pool.submit(()->{start.await();return b.execute("owner","race","same",()->{writes.incrementAndGet();return new ApiRequestStore.Result(201,"{}");});});
            start.countDown();assertEquals(x.get(10,TimeUnit.SECONDS),y.get(10,TimeUnit.SECONDS));assertEquals(1,writes.get());
        }
        assertThrows(ApiRequestStore.Conflict.class,()->b.execute("owner","race","changed",()->null));
        assertThrows(IllegalStateException.class,()->a.execute("owner","interrupted","same",()->{throw new IllegalStateException("simulated interruption");}));
        assertThrows(ApiRequestStore.Conflict.class,()->b.execute("owner","interrupted","same",()->{fail("Uncertain write must not execute again");return null;}));
    }
    @Test void workspaceAndPlannedItemsUseExistingJavaModelsAndExactEstimates() throws Exception {
        UUID plan=UUID.randomUUID(),item=UUID.randomUUID();
        assertEquals(201,call("POST","/shopping-plans",Json.object(Map.of("planId",plan,"name","Weekly")),"create-plan",null).statusCode());
        String body=Json.object(Map.of("plannedPurchaseId",item,"productName","Soap","quantity",3,"targetUnitPrice","0.10000000000000000001","clippedCouponIds",List.of()));
        assertEquals(201,call("POST","/shopping-plans/"+plan+"/purchases",body,"add-item",null).statusCode());
        var workspace=call("GET","/workspace",null,null,null);assertEquals(200,workspace.statusCode(),workspace.body());
        assertTrue(workspace.body().contains("0.30000000000000000003"));assertTrue(workspace.body().contains("\"canWrite\":true"));
        assertEquals(200,call("PUT","/shopping-plans/"+plan+"/purchases/"+item,body,"update-item","1").statusCode());
        assertEquals(409,call("PUT","/shopping-plans/"+plan+"/purchases/"+item,body,"stale-item","1").statusCode());
        assertEquals(2,new PostgresPlannedPurchaseRepository(db).findById(item).orElseThrow().revision());
        var reader=HttpRequest.newBuilder(URI.create(base+"/workspace")).header("Authorization","Bearer reader").GET().build();
        var read=client.send(reader,HttpResponse.BodyHandlers.ofString());assertEquals(200,read.statusCode());assertTrue(read.body().contains("\"canWrite\":false"));
    }
    @Test void inventoryRemainsReadOnlyWithoutAnAuthenticatedApprovalWorkflow() throws Exception {
        UUID id=UUID.randomUUID();var repo=new PostgresInventoryRepository(db);
        repo.save(new InventoryBalance(id,UUID.randomUUID(),5,1),"seed-owner");
        assertEquals(404,call("PUT","/inventory/"+id,"{\"quantity\":0,\"approvedBy\":\"owner\"}","unsafe-approval","1").statusCode());
        assertEquals(5,repo.findById(id).orElseThrow().quantityOnHand());
        try(var c=db.open();var rows=c.createStatement().executeQuery("SELECT count(*) FROM inventory_history")){rows.next();assertEquals(1,rows.getInt(1));}
    }
    @Test void receiptHistoryTotalsEvidenceAndCouponsAreStructuredAndOwnerScoped() throws Exception {
        UUID id=UUID.randomUUID(),evidenceId=UUID.randomUUID();var tx=new CouponTransaction(new ReceiptTotals(new BigDecimal("0.27"),new BigDecimal("4.28")));
        var item=new PurchaseItem("Soap",null,new BigDecimal("5.00"),new BigDecimal("5.00"),1);tx.addPurchaseItem(item);
        tx.addAppliedCoupon(new CouponApplied("Digital coupon",BigDecimal.ONE,List.of(item.getLineItemId())));
        receipts.save(id,tx,null,List.of(new ReceiptEvidence(evidenceId,id,"image/png",new byte[]{1,2,3})),new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptCreateCtx("owner"));
        var detail=call("GET","/receipts/"+id,null,null,null);assertTrue(detail.body().contains("\"printedLabel\":\"Digital coupon\""));
        assertEquals(200,call("GET","/receipts/"+id+"/history",null,null,null).statusCode());
        var totals=call("GET","/receipts/"+id+"/totals",null,null,null);assertEquals(200,totals.statusCode());assertTrue(totals.body().contains("0.01"));
        var artifact=call("GET","/receipts/"+id+"/evidence/"+evidenceId,null,null,null);assertEquals(200,artifact.statusCode());assertEquals("no-store",artifact.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals(404,call("GET","/receipts/"+UUID.randomUUID()+"/evidence/"+evidenceId,null,null,null).statusCode());
        receipts.softDelete(id,1,new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptChangeContext("owner","delete","owner",Instant.now()));
        assertEquals(200,call("GET","/receipts/"+id,null,null,null).statusCode());
    }


    @Test void rewardStatusUpdatesPreserveHistoryAndRetryWithoutDuplicateFinancialActivity()throws Exception {
        UUID id=UUID.randomUUID(),earningId=UUID.randomUUID();var tx=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,BigDecimal.ONE));
        tx.addPurchaseItem(new PurchaseItem("Test soap",null,BigDecimal.ONE,BigDecimal.ONE,1));
        var expected=new RebateBenefit(BenefitKind.POINTS,new BigDecimal("25.000"),null,"Fetch");
        var earning=new ReceiptObservations.Earning(earningId,ReceiptObservations.EarningKind.THIRD_PARTY_REBATE,"Fetch",null,ReceiptObservations.Scope.RECEIPT,List.of(),expected,null,RebateStatus.EXPECTED,null,null,null,List.of(),"Original note",null,new ReceiptObservations.RecordedValuation(new BigDecimal("0.025000"),Currency.getInstance("USD"),"Original valuation"));
        tx.setObservations(new ReceiptObservations(LocalDate.of(2026,10,19),"9:00 AM",List.of(earning),List.of(),null,List.of(),Map.of()));
        receipts.save(id,tx,new ReceiptMeta("Test","status-test",null,null),List.of(),new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptCreateCtx("test"));
        String path="/receipts/"+id+"/earnings/"+earningId+"/status";
        assertEquals(400,call("PUT",path,"{\"status\":\"CREDITED\"}","missing-confirmation","1").statusCode());
        assertEquals(400,call("PUT",path,"{\"status\":\"APPROVED\"}","legacy-assignment","1").statusCode());
        String body="{\"status\":\"CREDITED\",\"amount\":\"25\",\"unit\":\"points\",\"confirmAmountAndUnit\":true}";
        var saved=call("PUT",path,body,"confirm-credit","1");assertEquals(200,saved.statusCode(),saved.body());
        assertEquals(409,call("PUT",path,body,"stale-credit","1").statusCode());
        server.stop(0);start();assertEquals(saved.body(),call("PUT",path,body,"confirm-credit","1").body());
        var after=receipts.findById(id).orElseThrow();var updated=after.transaction().getObservations().earnings().getFirst();
        assertEquals(2,after.revision());assertEquals(earningId,updated.earningId());assertEquals(expected,updated.confirmed());assertEquals(earning.valuation(),updated.valuation());assertEquals(expected,updated.expected());
        assertEquals(2,receipts.findHistoryByReceiptId(id).size());
        assertEquals(earning,receipts.findHistoryByReceiptId(id).getFirst().after().contents().observations().earnings().getFirst());
        var moves=new ProviderBalanceSummary().movements(List.of(after));assertEquals(1,moves.stream().filter(m->m.source().contains("earning ")).count());
        var readonly=HttpRequest.newBuilder(URI.create(base+path)).header("Authorization","Bearer reader").header("Content-Type","application/json").header("Idempotency-Key","readonly").header("If-Match-Revision","2").PUT(HttpRequest.BodyPublishers.ofString(body)).build();
        assertEquals(403,client.send(readonly,HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(200,call("PUT",path,"{\"status\":\"REJECTED\",\"rejectionReason\":\"Provider correction\"}","reject","2").statusCode());
        var rejected=receipts.findById(id).orElseThrow();var rejectedEarning=rejected.transaction().getObservations().earnings().getFirst();
        assertEquals(expected,rejectedEarning.expected());assertEquals(expected,rejectedEarning.confirmed());assertTrue(rejectedEarning.recordedLifecycle().contains("Original note"));assertTrue(rejectedEarning.recordedLifecycle().contains("Provider correction"));
        assertEquals(0,new ProviderBalanceSummary().movements(List.of(rejected)).stream().filter(m->m.source().contains("earning ")).count());
        assertEquals(0,BigDecimal.ZERO.compareTo(SavingsReport.calculate(List.of(rejected)).newlyEarned()));
    }

    @Test void webReceiptPreviewAndCreationAreExactAtomicAndIdempotent()throws Exception {
        UUID id=UUID.randomUUID();var fields=ReceiptInputTest.draft(id);String body=Json.object(fields);
        var preview=call("POST","/receipts/preview",body,null,null);assertEquals(200,preview.statusCode(),preview.body());
        assertTrue(receipts.findById(id).isEmpty());var response=Json.parseObject(preview.body());assertTrue(preview.body().contains("7.270"));
        assertEquals(409,call("POST","/receipts",body,"unreviewed",null).statusCode());
        fields.put("reviewToken",response.get("reviewToken"));
        assertEquals(400,call("POST","/receipts",Json.object(fields),"unacknowledged-difference",null).statusCode());
        fields.put("acknowledgeDifference",true);String save=Json.object(fields);
        var created=call("POST","/receipts",save,"create-receipt",null);assertEquals(201,created.statusCode(),created.body());
        server.stop(0);start();assertEquals(created.body(),call("POST","/receipts",save,"create-receipt",null).body());
        assertEquals(409,call("POST","/receipts",save,"same-uuid-new-request",null).statusCode());
        var record=receipts.findById(id).orElseThrow();assertEquals(1,record.revision());assertEquals(1,receipts.findHistoryByReceiptId(id).size());assertEquals(1,receipts.findEvidenceByReceiptId(id).size());
        assertEquals(new BigDecimal("10.0000"),record.transaction().getPurchaseItems().getFirst().getRegularUnitPrice());
        assertEquals(0,new BigDecimal("0.01").compareTo(new TransactionCalculator().calculateReceiptDifference(record.transaction())));
        assertEquals(ReceiptInputTest.read(fields).transaction().getObservations(),record.transaction().getObservations());
        assertArrayEquals(new byte[]{1,2,3},receipts.findEvidenceByReceiptId(id).getFirst().getContent());
        assertEquals("api-owner",receipts.findHistoryByReceiptId(id).getFirst().createCtx().requestedBy());
        var changed=new LinkedHashMap<>(fields);changed.put("tax","1.00");changed.put("transactionId",UUID.randomUUID());
        assertEquals(409,call("POST","/receipts",Json.object(changed),"stale-preview",null).statusCode());
        assertEquals(1,receipts.findHistoryByReceiptId(id).size());
    }
    @Test void webReceiptFailuresLeaveNoPartialEvidenceOrAuditAndReadOnlyCannotWrite()throws Exception {
        UUID first=UUID.randomUUID();var fields=ReceiptInputTest.draft(first);var preview=Json.parseObject(call("POST","/receipts/preview",Json.object(fields),null,null).body());
        fields.put("reviewToken",preview.get("reviewToken"));fields.put("acknowledgeDifference",true);assertEquals(201,call("POST","/receipts",Json.object(fields),"first-create",null).statusCode());
        UUID second=UUID.randomUUID();fields.put("transactionId",second);fields.put("receiptNumber","another-receipt");fields.put("printedTime","10:00");
        var secondPreview=call("POST","/receipts/preview",Json.object(fields),null,null);assertEquals(200,secondPreview.statusCode());fields.put("reviewToken",Json.parseObject(secondPreview.body()).get("reviewToken"));
        // Same evidence UUID belongs to the first receipt: existing repository validation must reject the complete second write.
        assertEquals(400,call("POST","/receipts",Json.object(fields),"foreign-evidence",null).statusCode());
        assertTrue(receipts.findById(second).isEmpty());assertTrue(receipts.findHistoryByReceiptId(second).isEmpty());assertTrue(receipts.findEvidenceByReceiptId(second).isEmpty());
        assertEquals(1,receipts.findHistoryByReceiptId(first).size());assertEquals(1,receipts.findEvidenceByReceiptId(first).size());
        var denied=HttpRequest.newBuilder(URI.create(base+"/receipts")).header("Authorization","Bearer reader").header("Content-Type","application/json").header("Idempotency-Key","denied-create").POST(HttpRequest.BodyPublishers.ofString(Json.object(fields))).build();
        assertEquals(403,client.send(denied,HttpResponse.BodyHandlers.ofString()).statusCode());
    }
    @Test void webZeroDueReceiptNeedsNoInventedPaymentAndDuplicateMetadataIsFlagged()throws Exception {
        UUID id=UUID.randomUUID();var f=ReceiptInputTest.draft(id);f.put("tax","0.00");f.put("reportedTotal","0.00");f.put("rrUsed",null);f.put("discounts",List.of());f.put("payments",List.of());f.put("earnings",List.of());f.put("evidence",List.of());
        f.put("items",List.of(Map.of("lineItemId",UUID.randomUUID(),"description","Free item","regularUnitPrice","0.00","unitPrice","0.00","quantity",1)));
        var preview=call("POST","/receipts/preview",Json.object(f),null,null);assertEquals(200,preview.statusCode(),preview.body());f.put("reviewToken",Json.parseObject(preview.body()).get("reviewToken"));
        assertEquals(201,call("POST","/receipts",Json.object(f),"zero-due-create",null).statusCode());assertTrue(receipts.findById(id).orElseThrow().transaction().getPayments().isEmpty());
        UUID another=UUID.randomUUID();f.put("transactionId",another);var duplicate=call("POST","/receipts/preview",Json.object(f),null,null);assertEquals(200,duplicate.statusCode());assertTrue(duplicate.body().contains(id.toString()));
        f.put("reviewToken",Json.parseObject(duplicate.body()).get("reviewToken"));assertEquals(409,call("POST","/receipts",Json.object(f),"duplicate-metadata",null).statusCode());assertTrue(receipts.findById(another).isEmpty());
    }

    @Test void planningProgressRoundTripsThroughWorkspaceAndCheckoutWithoutReceiptCredit() throws Exception {
        UUID planId = UUID.randomUUID(), dealId = UUID.randomUUID();
        plans.save(new ShoppingPlan(planId, "Potential", null, null, List.of(), 1), "test");
        var terms = new DealTerms(DealTerms.Type.QUANTITY_REWARD, "Dove", null, null, 2, null,
                0, new BigDecimal("5"), false, null, List.of(), null, null, null);
        var source = new SourceArtifact(UUID.randomUUID(), "Test terms", "application/json", null,
                Instant.now(), "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        new PostgresDealRepository(db).save(new AdvertisedDeal(dealId, "Dove", "Buy two", "Walgreens",
                LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 24), List.of(source.getArtifactId()), 1, terms), "test", source);
        new PostgresPlatformRepository(db).savePurchase(planId, UUID.randomUUID(), "Dove", null, 2,
                new BigDecimal("6"), new BigDecimal("5"), "MANUAL", dealId, List.of(), 0, "test");
        var response = call("GET", "/workspace", null, null, null);
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"rewardStatus\":\"KNOWN\""));
        assertTrue(response.body().contains("\"storeStatus\":\"NOT_RECORDED\""));
        assertTrue(response.body().contains("\"potentialReward\":5"));
        var checkoutResponse = call("GET", "/shopping-plans/" + planId + "/checkout", null, null, null);
        assertEquals(200, checkoutResponse.statusCode(), checkoutResponse.body());
        assertTrue(checkoutResponse.body().contains("\"reviewRequired\":true"));
        assertTrue(checkoutResponse.body().contains("\"rewardsComplete\":true"));
        var workspace = Json.parseObject(response.body());
        assertTrue(((List<?>) workspace.get("receiptEarnings")).isEmpty());
    }
}
