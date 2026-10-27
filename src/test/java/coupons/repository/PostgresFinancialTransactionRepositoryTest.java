package coupons.repository;

/** Rerun the complete receipt contract with projection writes in the same transactions. */
class PostgresFinancialTransactionRepositoryTest extends PostgresTransactionRepositoryTest {
    @Override boolean financialsEnabled() { return true; }
}
