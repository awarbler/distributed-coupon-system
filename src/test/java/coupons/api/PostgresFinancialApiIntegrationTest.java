package coupons.api;

/** Existing receipt/reward/plan HTTP regressions with financial projections explicitly enabled. */
class PostgresFinancialApiIntegrationTest extends PostgresApiIntegrationTest {
    @Override boolean financialsEnabled() { return true; }
}
