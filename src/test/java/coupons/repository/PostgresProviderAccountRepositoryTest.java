package coupons.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

/** Focused provider-ledger integration coverage; never uses .coupon-data. */
class PostgresProviderAccountRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");
    private PostgresProviderAccountRepository repository;

    @BeforeEach void setUp() throws Exception {
        Assumptions.assumeTrue(System.getenv("COUPON_POSTGRES_TEST_URL") != null
                && System.getenv("COUPON_POSTGRES_TEST_USER") != null
                && System.getenv("COUPON_POSTGRES_TEST_PASSWORD") != null);
        var factory = new PostgresConnectionFactory(System.getenv("COUPON_POSTGRES_TEST_URL"),
                System.getenv("COUPON_POSTGRES_TEST_USER"), System.getenv("COUPON_POSTGRES_TEST_PASSWORD"));
        try (Connection c = factory.open()) {
            PostgresSchema.apply(c);
            c.createStatement().executeUpdate(
                    "TRUNCATE provider_redemptions_history, provider_redemptions_current, " +
                    "provider_account_adjustments, provider_account_openings");
        }
        repository = new PostgresProviderAccountRepository(factory);
    }

    @Test void preservesOpeningsSeparatelyFromAdjustmentsAndRejectsDuplicateIds() {
        UUID openingId = UUID.randomUUID();
        repository.saveOpening(new AccountOpening(openingId, LocalDate.of(2026, 10, 1), "Store",
                new BigDecimal("10"), new BigDecimal("5.00"), true, "statement",
                "reported opening", NOW));
        UUID adjustmentId = UUID.randomUUID();
        repository.saveAdjustment(new AccountAdjustment(adjustmentId, LocalDate.of(2026, 10, 2),
                "Store", new BigDecimal("-1"), null, "correction", "tester", NOW));
        assertEquals(1, repository.balanceState("Store").openings().size());
        assertEquals(1, repository.balanceState("Store").adjustments().size());
        assertThrows(IllegalArgumentException.class, () -> repository.saveOpening(
                new AccountOpening(openingId, LocalDate.of(2026, 10, 1), "Store",
                        new BigDecimal("10"), null, true, "statement", "duplicate", NOW)));
    }

    @Test void retainsRedemptionStatusRevisionsAndRejectsStaleWriters() {
        UUID id = UUID.randomUUID();
        ProviderRedemption requested = new ProviderRedemption(id, "Store",
                LocalDate.of(2026, 10, 2), null, new BigDecimal("2"), new BigDecimal("4"),
                ProviderRedemption.Destination.CASH, "Bank", BigDecimal.ZERO,
                ProviderRedemption.Status.REQUESTED, null, null, List.of(), "ref-1", "notes");
        repository.saveRedemption(requested);
        ProviderRedemption received = requested.complete(ProviderRedemption.Status.RECEIVED,
                LocalDate.of(2026, 10, 3), LocalTime.NOON);
        repository.updateRedemption(id, 1, received);
        assertEquals(requested, repository.findRedemptionRevision(id, 1).orElseThrow());
        assertEquals(received, repository.findRedemption(id).orElseThrow());
        assertEquals(2, repository.findRedemptionHistory("Store").size());
        assertThrows(ConcurrentModificationException.class,
                () -> repository.updateRedemption(id, 1, received));
    }
}
