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
        applyMigration(connection, "/db/migration/V8__shopping_plan_checkout.sql", 8,  "revisioned shopping plan checkout estimates");
        applyMigration(connection, "/db/migration/V9__actual_checkout_tax.sql", 9, "actual receipt sales tax for shopping plan checkout");
        // Kept inline in this existing file under the owner's no-new-files constraint.
        try(var statement=connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS inventory_receipt_settings (singleton BOOLEAN PRIMARY KEY CHECK(singleton), enabled BOOLEAN NOT NULL)");
            statement.execute("INSERT INTO inventory_receipt_settings VALUES(true,false) ON CONFLICT DO NOTHING");
            statement.execute("CREATE TABLE IF NOT EXISTS inventory_receipt_products (product_id UUID PRIMARY KEY REFERENCES inventory_current(product_id), product_key TEXT NOT NULL UNIQUE, product_name TEXT NOT NULL, sku TEXT, location TEXT, quantity_confirmed BOOLEAN NOT NULL DEFAULT false)");
            statement.execute("CREATE TABLE IF NOT EXISTS inventory_receipt_lines (transaction_id UUID NOT NULL, line_item_id UUID NOT NULL, product_id UUID NOT NULL REFERENCES inventory_receipt_products(product_id), receipt_revision BIGINT NOT NULL, quantity INTEGER NOT NULL CHECK(quantity>0), active BOOLEAN NOT NULL, product_name TEXT NOT NULL, retailer TEXT, receipt_number TEXT, purchase_date DATE, PRIMARY KEY(transaction_id,line_item_id))");
            statement.execute("ALTER TABLE inventory_receipt_products ADD COLUMN IF NOT EXISTS low_stock_level INTEGER CHECK(low_stock_level>=0)");
            statement.execute("CREATE INDEX IF NOT EXISTS inventory_receipt_product ON inventory_receipt_lines(product_id)");
            statement.execute("INSERT INTO schema_version(version,description) VALUES(10,'receipt-backed inventory and optional locations') ON CONFLICT DO NOTHING");
            statement.execute("ALTER TABLE inventory_receipt_products ADD COLUMN IF NOT EXISTS units_per_package INTEGER CHECK(units_per_package>0)");
            statement.execute("ALTER TABLE inventory_receipt_products ADD COLUMN IF NOT EXISTS unit_name TEXT");
            statement.execute("ALTER TABLE inventory_receipt_products ADD COLUMN IF NOT EXISTS count_unit TEXT NOT NULL DEFAULT 'PACKAGE' CHECK(count_unit IN ('PACKAGE','ITEM'))");
            statement.execute("CREATE TABLE IF NOT EXISTS receipt_drafts(draft_id UUID PRIMARY KEY,revision BIGINT NOT NULL CHECK(revision>0),owner_id TEXT NOT NULL,store TEXT,receipt_number TEXT,updated_at TIMESTAMPTZ NOT NULL,payload TEXT NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS receipt_draft_history(draft_id UUID NOT NULL REFERENCES receipt_drafts(draft_id),revision BIGINT NOT NULL,updated_at TIMESTAMPTZ NOT NULL,payload TEXT NOT NULL,PRIMARY KEY(draft_id,revision))");
            statement.execute("INSERT INTO schema_version(version,description) VALUES(11,'saved receipt drafts and inventory package contents') ON CONFLICT DO NOTHING");
        }

    }

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
