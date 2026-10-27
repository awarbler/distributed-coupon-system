package coupons.repository;

/** Original lossless-copy import regressions with explicitly enabled financial projections. */
class PostgresFinancialMigrationTest extends LocalToPostgresMigrationTest {
    @Override LocalToPostgresMigration.Report migrate() throws Exception {
        return LocalToPostgresMigration.migrateCopyWithFinancials(copy,original,db,
                PostgresTestDatabase.targetForSchema(schema));
    }
}
