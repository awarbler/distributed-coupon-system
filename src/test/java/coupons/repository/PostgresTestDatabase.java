package coupons.repository;

import java.util.UUID;
import org.junit.jupiter.api.Assumptions;

/** One private schema per test; never truncates shared tables. */
public final class PostgresTestDatabase implements AutoCloseable {
    private final PostgresConnectionFactory admin;
    private final String schema = "phase4_test_" + UUID.randomUUID().toString().replace("-", "");
    public final PostgresConnectionFactory connections;

    public PostgresTestDatabase() throws Exception {
        String url = System.getenv("COUPON_POSTGRES_TEST_URL");
        String user = System.getenv("COUPON_POSTGRES_TEST_USER");
        String password = System.getenv("COUPON_POSTGRES_TEST_PASSWORD");
        Assumptions.assumeTrue(url != null && user != null && password != null,
                "Dedicated disposable PostgreSQL test configuration is required.");
        admin = new PostgresConnectionFactory(url, user, password);
        try (var connection = target().open(admin); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT current_database()")) {
                rows.next();
                if (!"distributed_coupons_test".equals(rows.getString(1))) {
                    throw new IllegalStateException("Tests require distributed_coupons_test; no schema was changed.");
                }
            }
            statement.execute("CREATE SCHEMA " + schema);
        }
        connections = new PostgresConnectionFactory(url + (url.contains("?") ? "&" : "?")
                + "currentSchema=" + schema, user, password);
        try (var connection = target().open(connections)) {
            target().verifySchema(connection);
            PostgresSchema.apply(connection);
        } catch (Exception failure) {
            try { close(); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public DisposablePostgresTarget target() { return targetForSchema(schema); }

    public static DisposablePostgresTarget targetForSchema(String schema) {
        return new DisposablePostgresTarget(System.getenv("COUPON_POSTGRES_TEST_HOST"),
                Integer.parseInt(System.getenv("COUPON_POSTGRES_TEST_PORT")),
                System.getenv("COUPON_POSTGRES_TEST_MARKER"),
                System.getenv("COUPON_POSTGRES_TEST_USER"), schema);
    }

    @Override public void close() throws Exception {
        try (var connection = target().open(admin); var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }
}
