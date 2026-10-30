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
