package coupons.repository;

import coupons.promotions.AdvertisedDeal;
import coupons.promotions.BenefitKind;
import coupons.receipts.CouponTransaction;
import coupons.receipts.PurchaseItem;
import coupons.promotions.RebateBenefit;
import coupons.promotions.RebateClaim;
import coupons.promotions.RebateStatus;
import coupons.receipts.ReceiptTotals;
import coupons.promotions.SourceArtifact;
import coupons.inventory.InventoryBalance;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.*;
import java.util.List;
import java.util.UUID;
import java.util.ConcurrentModificationException;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused Milestone 2 integration tests. These tests only run when all three
 * dedicated PostgreSQL test variables are configured and never use local data.
 */
class PostgresMilestone2RepositoryTest {
    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");
    private PostgresConnectionFactory connections;
    private PostgresSourceArtifactRepository artifacts;
    private PostgresInventoryRepository inventory;
    private PostgresDealRepository deals;
    private PostgresRebateRepository rebates;

    private PostgresTestDatabase testDatabase;

    @org.junit.jupiter.api.AfterEach void releaseTestDatabase() throws Exception {
        if (testDatabase != null) testDatabase.close();
    }

    @BeforeEach
    void setUp() throws Exception {
        testDatabase = new PostgresTestDatabase();
        connections = testDatabase.connections;
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        artifacts = new PostgresSourceArtifactRepository(connections);
        inventory = new PostgresInventoryRepository(connections, clock);
        deals = new PostgresDealRepository(connections, clock);
        rebates = new PostgresRebateRepository(connections, clock);
    }

    @Test void weeklyArchiveIsAtomicAuditedAndPreservesEditedPlanAndSource()throws Exception {
        var source=artifact();
        var ad=new coupons.promotions.DealTerms.WeeklyAdvertisement("{\"items\":[],\"coupons\":[],\"promotions\":[]}",java.util.Map.of("sourceName","incorrect.pdf"),List.of());
        var terms=new coupons.promotions.DealTerms(coupons.promotions.DealTerms.Type.ADVERTISEMENT,"Product",null,null,1,null,0,null,false,null,List.of(),null,null,null,false,false,ad);
        var deal=new AdvertisedDeal(UUID.randomUUID(),"Incorrect import","Recorded terms","Walgreens",null,null,List.of(source.getArtifactId()),1,terms);deals.save(deal,"test",source);
        var manual=new AdvertisedDeal(UUID.randomUUID(),"Manual offer","Owner terms","Walgreens",null,null,List.of(source.getArtifactId()),1);deals.save(manual,"test");
        var plans=new PostgresShoppingPlanRepository(connections);var plan=new coupons.shopping.ShoppingPlan(UUID.randomUUID(),"Owner edited plan","Walgreens",LocalDate.of(2026,11,10),List.of(),1);plans.save(plan,"test");
        var platform=new PostgresPlatformRepository(connections);var purchase=platform.saveWeeklyPurchase(plan.planId(),UUID.randomUUID(),"line","Owner product",null,3,new BigDecimal("12.34"),null,"MANUAL",deal.dealId(),List.of(),"test");
        var before=plans.findById(plan.planId()).orElseThrow();var archive=new WeeklyDealArchive(connections);
        assertThrows(ConcurrentModificationException.class,()->archive.delete(java.util.Map.of(deal.dealId(),2L),"test","Incorrect imports"));assertTrue(deals.findById(deal.dealId()).isPresent());
        assertThrows(IllegalArgumentException.class,()->archive.delete(java.util.Map.of(deal.dealId(),1L,manual.dealId(),1L),"test","Incorrect imports"));assertTrue(deals.findById(deal.dealId()).isPresent());
        var result=archive.delete(java.util.Map.of(deal.dealId(),1L),"test","Incorrect imports");assertEquals(1,result.get("deleted"));assertTrue(deals.findById(deal.dealId()).isEmpty());assertTrue(deals.findById(manual.dealId()).isPresent());
        assertEquals(before,plans.findById(plan.planId()).orElseThrow());assertEquals(List.of(purchase),platform.planPurchases(plan.planId()));assertTrue(artifacts.findById(source.getArtifactId()).isPresent());
        try(var c=connections.open();var q=c.prepareStatement("SELECT payload,source_ids FROM deleted_weekly_deals WHERE deal_id=?")){q.setObject(1,deal.dealId());try(var r=q.executeQuery()){assertTrue(r.next());assertTrue(r.getBytes(1).length>0);assertEquals(1,((Object[])r.getArray(2).getArray()).length);assertTrue(WeeklyDealArchive.deletedIds(c).contains(deal.dealId()));}}
        assertEquals(0,archive.delete(java.util.Map.of(deal.dealId(),1L),"test","Retry").get("deleted"));deals.save(deal,"verified reimport");assertTrue(deals.findById(deal.dealId()).isPresent());try(var c=connections.open()){assertFalse(WeeklyDealArchive.deletedIds(c).contains(deal.dealId()));}
    }
    @Test void weeklyPurchaseRetriesKeepOneLinkAndAdvancePlanRevisionOnce()throws Exception {
        var planRepo=new PostgresShoppingPlanRepository(connections);UUID planId=UUID.randomUUID();
        var plan=new coupons.shopping.ShoppingPlan(planId,"Weekly integration","Walgreens",LocalDate.of(2026,11,9),List.of(),1);planRepo.save(plan,"test");
        var source=artifact();artifacts.save(source);UUID dealId=UUID.randomUUID();
        var ad=new AdvertisedDeal(dealId,"CoverGirl","CoverGirl printed offer","Walgreens",null,null,List.of(source.getArtifactId()),1);deals.save(ad,"test");
        var platform=new PostgresPlatformRepository(connections);
        var first=platform.saveWeeklyPurchase(planId,UUID.randomUUID(),"source-line","CoverGirl",null,1,null,null,null,dealId,List.of(),"test");
        var repeat=platform.saveWeeklyPurchase(planId,UUID.randomUUID(),"source-line","CoverGirl",null,1,null,null,null,dealId,List.of(),"test");
        assertEquals(first.plannedPurchaseId(),repeat.plannedPurchaseId());assertEquals(1,platform.planPurchases(planId).size());assertEquals(2,planRepo.findById(planId).orElseThrow().revision());
        var stale=new coupons.shopping.ShoppingPlan(planId,"Stale editor","Walgreens",plan.shoppingDate(),List.of(),2);assertThrows(ConcurrentModificationException.class,()->planRepo.update(stale,1,"test","Stale save"));
        var second=platform.saveWeeklyPurchase(planId,UUID.randomUUID(),"other-source-line","Another product",null,2,new BigDecimal("3"),null,"DEAL",dealId,List.of(),"test");assertNotEquals(first.plannedPurchaseId(),second.plannedPurchaseId());assertEquals(2,platform.planPurchases(planId).size());
    }
    @Test void weeklyDealsShareOriginalAndPreserveCombinedTermsAcrossRevisions()throws Exception {
        var source=artifact();
        var ad=new coupons.promotions.DealTerms.WeeklyAdvertisement("{\"items\":[],\"coupons\":[],\"promotions\":[]}",java.util.Map.of("originalText","BOGO 50% plus spend 45 earn 15 WC"),List.of("Missing price"));
        var terms=new coupons.promotions.DealTerms(coupons.promotions.DealTerms.Type.ADVERTISEMENT,"CeraVe",null,null,1,null,0,null,false,null,List.of(),null,null,null,false,false,ad);
        var first=new AdvertisedDeal(UUID.randomUUID(),"CeraVe","BOGO 50% plus spend 45 earn 15 WC","Walgreens",LocalDate.of(2026,11,8),LocalDate.of(2026,11,14),List.of(source.getArtifactId()),1,terms);
        deals.save(first,"test",source);
        var second=new AdvertisedDeal(UUID.randomUUID(),"Another weekly offer","Other printed terms","Walgreens",first.validFrom(),first.validThrough(),first.sourceArtifactIds(),1,terms);deals.save(second,"test");
        assertEquals(2,deals.findBySourceId(source.getArtifactId()).size());assertEquals(source,artifacts.findById(source.getArtifactId()).orElseThrow());
        assertEquals(ad,deals.findById(first.dealId()).orElseThrow().pricing().advertisement());
        var corrected=new AdvertisedDeal(first.dealId(),"CeraVe corrected",first.interpretedTerms(),first.retailer(),first.validFrom(),first.validThrough(),first.sourceArtifactIds(),2,terms);
        deals.update(corrected,1,"test","Review correction");assertEquals(first,deals.findByRevision(first.dealId(),1).orElseThrow());assertEquals(corrected,deals.findById(first.dealId()).orElseThrow());
    }
    @Test
    void sourceArtifactsRoundTripExactlyAndRejectConflictingReuse() {
        SourceArtifact original = artifact();
        artifacts.save(original);
        assertEquals(original, artifacts.findById(original.getArtifactId()).orElseThrow());
        SourceArtifact conflicting = new SourceArtifact(original.getArtifactId(), "changed",
                original.getMediaType(), original.getSourceReference(), NOW, new byte[]{9});
        assertThrows(IllegalArgumentException.class, () -> artifacts.save(conflicting));
        assertEquals(original, artifacts.findById(original.getArtifactId()).orElseThrow());
    }

    @Test
    void inventoryCategoriesPersistClearAndSurviveLegacyAdjustments() throws Exception {
        UUID id=UUID.randomUUID(),product=UUID.randomUUID();
        inventory.save(new InventoryBalance(id,product,2,1),"tester");
        try(var c=connections.open();var enable=c.prepareStatement("UPDATE inventory_receipt_settings SET enabled=true WHERE singleton=true");var p=c.prepareStatement("INSERT INTO inventory_receipt_products(product_id,product_key,product_name,quantity_confirmed) VALUES(?,?,?,true)")){
            enable.executeUpdate();p.setObject(1,product);p.setString(2,"test:"+product);p.setString(3,"Laundry");p.executeUpdate();
        }
        inventory.adjust(id,1,"DETAILS",0,null,null,"tester",null,40,"Loads","PACKAGE","Household & Pet Essentials > Laundry Care");
        try(var c=connections.open()){
            var rows=PostgresInventoryRepository.receiptInventory(c);
            var row=(java.util.Map<?,?>)rows.getFirst();
            assertEquals("Household & Pet Essentials > Laundry Care",row.get("category"));
            assertEquals(40,row.get("unitsPerPackage"));assertEquals("Loads",row.get("unitName"));assertEquals(2,row.get("quantityOnHand"));
        }
        inventory.adjust(id,2,"DETAILS",0,null,null,"tester");
        try(var c=connections.open()){assertEquals("Household & Pet Essentials > Laundry Care",((java.util.Map<?,?>)PostgresInventoryRepository.receiptInventory(c).getFirst()).get("category"));}
        assertThrows(ConcurrentModificationException.class,()->inventory.adjust(id,2,"DETAILS",0,null,null,"tester",null,40,"Loads","PACKAGE","Other"));
        inventory.adjust(id,3,"DETAILS",0,null,null,"tester",null,40,"Loads","PACKAGE","");
        try(var c=connections.open()){assertNull(((java.util.Map<?,?>)PostgresInventoryRepository.receiptInventory(c).getFirst()).get("category"));}
        inventory.adjust(id,4,"DETAILS",0,null,null,"tester",null,40,"Loads","PACKAGE",null,new BigDecimal("7.5"),"FL_OZ");
        try(var c=connections.open()){
            var row=(java.util.Map<?,?>)PostgresInventoryRepository.receiptInventory(c).getFirst();
            assertEquals(0,new BigDecimal("7.5").compareTo((BigDecimal)row.get("comparisonSize")));assertEquals("FL_OZ",row.get("comparisonUnit"));assertEquals(2,row.get("quantityOnHand"));assertEquals(40,row.get("unitsPerPackage"));
        }
        inventory.adjust(id,5,"DETAILS",0,null,null,"tester");
        try(var c=connections.open()){assertEquals("FL_OZ",((java.util.Map<?,?>)PostgresInventoryRepository.receiptInventory(c).getFirst()).get("comparisonUnit"));}
        inventory.adjust(id,6,"DETAILS",0,null,null,"tester",null,40,"Loads","PACKAGE",null,null,"");
        try(var c=connections.open()){var row=(java.util.Map<?,?>)PostgresInventoryRepository.receiptInventory(c).getFirst();assertNull(row.get("comparisonUnit"));assertNull(row.get("comparisonSize"));}

    }

    @Test
    void inventoryPersistsHistoryAndRejectsStaleRevision() throws Exception {
        UUID id = UUID.randomUUID();
        inventory.save(new InventoryBalance(id, UUID.randomUUID(), 5, 1), "tester");
        InventoryAdjustCtx context = new InventoryAdjustCtx("requester", "count correction",
                "approver", NOW);
        assertEquals(2, inventory.updateQuantity(id, 1, 4, context).revision());
        assertEquals(4, inventory.findById(id).orElseThrow().quantityOnHand());
        assertThrows(ConcurrentModificationException.class,
                () -> inventory.updateQuantity(id, 1, 3, context));
        try (Connection c = connections.open();
             var statement = c.prepareStatement(
                     "SELECT count(*) FROM inventory_history WHERE inventory_id=?")) {
            statement.setObject(1, id);
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(2, rows.getLong(1));
            }
        }
    }

    @Test
    void dealsValidateSourcesPreserveRevisionHistoryAndRejectStaleUpdate() {
        UUID sourceId = artifact().getArtifactId();
        SourceArtifact source = new SourceArtifact(sourceId, "deal.txt", "text/plain",
                null, NOW, new byte[]{1});
        artifacts.save(source);
        UUID dealId = UUID.randomUUID();
        AdvertisedDeal first = deal(dealId, "Buy one", List.of(sourceId), 1);
        deals.save(first, "tester");
        AdvertisedDeal second = deal(dealId, "Buy two", List.of(sourceId), 2);
        assertEquals(second, deals.update(second, 1, "reviewer", "corrected terms"));
        assertEquals("Buy one", deals.findByRevision(dealId, 1).orElseThrow().title());
        assertEquals("Buy two", deals.findById(dealId).orElseThrow().title());
        assertThrows(ConcurrentModificationException.class,
                () -> deals.update(deal(dealId, "stale", List.of(sourceId), 2),
                        1, "reviewer", "stale correction"));
        assertThrows(IllegalArgumentException.class, () -> deals.save(
                deal(UUID.randomUUID(), "missing source", List.of(UUID.randomUUID()), 1),
                "tester"));
    }

    @Test
    void rebatesValidateSourcesAndCompletedReceiptReference() {
        SourceArtifact source = artifact();
        artifacts.save(source);
        UUID receiptId = UUID.randomUUID();
        new PostgresTransactionRepository(connections, Clock.fixed(NOW, ZoneOffset.UTC))
                .save(receiptId, transaction(), null, List.of(),
                        new ReceiptWriteRequest(UUID.randomUUID(), NOW),
                        new ReceiptCreateCtx("tester"));
        RebateClaim claim = new RebateClaim(UUID.randomUUID(), "Provider", "claim-1", null,
                receiptId, RebateStatus.SUBMITTED, "received",
                List.of(new RebateBenefit(BenefitKind.CASH, new BigDecimal("2.00"),
                        java.util.Currency.getInstance("USD"), null)),
                List.of(), List.of(source.getArtifactId()), LocalDate.of(2026, 10, 19), null, 1);
        rebates.save(claim, "tester");
        assertEquals(claim, rebates.findById(claim.claimId()).orElseThrow());
        assertEquals(List.of(claim), rebates.findByReceiptId(receiptId));
        assertThrows(IllegalArgumentException.class, () -> rebates.save(
                new RebateClaim(UUID.randomUUID(), "Provider", null, null, receiptId,
                        RebateStatus.EXPECTED, null, List.of(), List.of(),
                        List.of(UUID.randomUUID()), null, null, 1), "tester"));
    }

    private static SourceArtifact artifact() {
        return new SourceArtifact(UUID.randomUUID(), "source.bin", "application/octet-stream",
                "test-source", NOW, new byte[]{1, 2, 3});
    }

    private static AdvertisedDeal deal(UUID id, String title, List<UUID> sourceIds, long revision) {
        return new AdvertisedDeal(id, title, "recorded terms", "Retailer",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), sourceIds, revision);
    }

    private static CouponTransaction transaction() {
        CouponTransaction transaction = new CouponTransaction(
                new ReceiptTotals(new BigDecimal("0.00"), new BigDecimal("1.00")));
        transaction.addPurchaseItem(new PurchaseItem("Product", null, null,
                new BigDecimal("1.00"), 1));
        return transaction;
    }
}
