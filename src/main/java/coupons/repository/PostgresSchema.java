package coupons.repository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/** Applies checked-in PostgreSQL migrations to an explicitly supplied database. */
public final class PostgresSchema {
    private static final String V1 = "/db/migration/V1__receipt_repository.sql";
    private static final String V2 = "/db/migration/V2__catalog_deal_rebate_inventory.sql";
    private static final String V3 = "/db/migration/V3__provider_account_ledger.sql";
    private static final String V4 = "/db/migration/V4__shopping_plans_and_clipped_coupons.sql";

    private PostgresSchema() {
    }

    /**
     * Applies the receipt-repository schema to the supplied connection.
     *
     * <p>This method never opens local receipt files and does not migrate data.
     */
    public static void apply(Connection connection) throws SQLException, IOException {
        Objects.requireNonNull(connection, "Connection cannot be null.");
        applyMigration(connection, V1, 1, "receipt repository");
        applyMigration(connection, V2, 2, "catalog, deal, rebate, and inventory repositories");
        applyMigration(connection, V3, 3, "provider account ledger");
        applyMigration(connection, V4, 4, "shopping plans, planned purchases, and clipped coupons");
        applyMigration(connection, "/db/migration/V5__api_retry_journal.sql", 5, "API retry journal and multiple coupon receipt use");
        applyMigration(connection, "/db/migration/V6__lossless_local_migration.sql", 6, "lossless local migration and receipt supplements");
        applyMigration(connection, "/db/migration/V7__offer_pricing.sql", 7, "structured advertised offer pricing");
        applyMigration(connection, "/db/migration/V8__shopping_plan_checkout.sql", 8,  "revisioned shopping plan checkout estimates"); }

    private static void applyMigration(Connection connection, String resource, int version,
            String description) throws SQLException, IOException {
        try (InputStream input = PostgresSchema.class.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing PostgreSQL migration resource: " + resource);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            try (Statement statement = connection.createStatement()) {
                for (String command : sql.split(";")) {
                    if (!command.isBlank()) statement.execute(command);
                }
                statement.executeUpdate("INSERT INTO schema_version(version, description) VALUES ("
                        + version + ", '" + description + "') ON CONFLICT (version) DO NOTHING");
            }
        }
    }
}
