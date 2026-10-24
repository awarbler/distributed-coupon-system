# Database migration reference

Inspected: October 21, 2026 (America/Chicago). Base Git commit: `fa3b8debc070cb5de990cbe9eb420fe1e56b7c06`. This reference describes the working tree, including pre-existing uncommitted checkout changes; it does not prove what is deployed online. No application tests were run for this documentation-only task.

Migrations are listed in filename order. These are source definitions, not an assertion that a live database has applied them. Serialized payload columns must be interpreted through their Java models; they are not ordinary JSON columns. PostgresSchema.java also manages schema setup; compare both when planning changes.

## V1__receipt_repository.sql

Source: [src/main/resources/db/migration/V1__receipt_repository.sql](../../src/main/resources/db/migration/V1__receipt_repository.sql)

```sql
CREATE TABLE IF NOT EXISTS schema_version (
    version INTEGER PRIMARY KEY,
    description TEXT NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS receipt_current (
    transaction_id UUID PRIMARY KEY,
    status VARCHAR(16) NOT NULL,
    revision BIGINT NOT NULL CHECK (revision > 0),
    contents BYTEA NOT NULL,
    metadata BYTEA,
    evidence_ids BYTEA NOT NULL
);

CREATE TABLE IF NOT EXISTS receipt_evidence (
    evidence_id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES receipt_current(transaction_id),
    media_type TEXT NOT NULL,
    content BYTEA NOT NULL
);

CREATE TABLE IF NOT EXISTS register_rewards (
    reward_id UUID PRIMARY KEY,
    earned_receipt_id UUID,
    payload BYTEA NOT NULL
);

CREATE TABLE IF NOT EXISTS register_reward_redemptions (
    redemption_id UUID PRIMARY KEY,
    reward_id UUID NOT NULL UNIQUE REFERENCES register_rewards(reward_id),
    receipt_id UUID NOT NULL REFERENCES receipt_current(transaction_id),
    payload BYTEA NOT NULL
);

CREATE TABLE IF NOT EXISTS receipt_history (
    history_id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES receipt_current(transaction_id),
    revision BIGINT NOT NULL,
    request_id UUID NOT NULL,
    payload BYTEA NOT NULL,
    UNIQUE (transaction_id, revision),
    UNIQUE (request_id)
);

CREATE TABLE IF NOT EXISTS receipt_write_requests (
    request_id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL,
    payload BYTEA NOT NULL,
    result BYTEA NOT NULL
);

CREATE INDEX IF NOT EXISTS receipt_current_status_idx
    ON receipt_current(status);

CREATE INDEX IF NOT EXISTS receipt_evidence_transaction_idx
    ON receipt_evidence(transaction_id);

CREATE INDEX IF NOT EXISTS receipt_history_transaction_idx
    ON receipt_history(transaction_id, revision);
```

## V2__catalog_deal_rebate_inventory.sql

Source: [src/main/resources/db/migration/V2__catalog_deal_rebate_inventory.sql](../../src/main/resources/db/migration/V2__catalog_deal_rebate_inventory.sql)

```sql
CREATE TABLE IF NOT EXISTS source_artifacts (
    artifact_id UUID PRIMARY KEY, label TEXT NOT NULL, media_type TEXT NOT NULL,
    source_reference TEXT, captured_at TIMESTAMPTZ NOT NULL, content BYTEA NOT NULL
);
CREATE TABLE IF NOT EXISTS inventory_current (
    inventory_id UUID PRIMARY KEY, product_id UUID NOT NULL UNIQUE,
    quantity_on_hand INTEGER NOT NULL CHECK (quantity_on_hand >= 0),
    revision BIGINT NOT NULL CHECK (revision > 0)
);
CREATE TABLE IF NOT EXISTS inventory_history (
    history_id UUID PRIMARY KEY, inventory_id UUID NOT NULL REFERENCES inventory_current(inventory_id),
    from_quantity INTEGER NOT NULL, to_quantity INTEGER NOT NULL,
    from_revision BIGINT NOT NULL, to_revision BIGINT NOT NULL,
    requested_by TEXT NOT NULL, reason TEXT NOT NULL, approved_by TEXT NOT NULL,
    approved_at TIMESTAMPTZ NOT NULL, committed_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE IF NOT EXISTS deal_current (
    deal_id UUID PRIMARY KEY, title TEXT NOT NULL, interpreted_terms TEXT NOT NULL,
    retailer TEXT, valid_from DATE, valid_through DATE,
    revision BIGINT NOT NULL CHECK (revision > 0), recorded_by TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS deal_sources (
    deal_id UUID NOT NULL REFERENCES deal_current(deal_id),
    artifact_id UUID NOT NULL REFERENCES source_artifacts(artifact_id),
    PRIMARY KEY (deal_id, artifact_id)
);
CREATE TABLE IF NOT EXISTS deal_history (
    deal_id UUID NOT NULL REFERENCES deal_current(deal_id), revision BIGINT NOT NULL,
    title TEXT NOT NULL, interpreted_terms TEXT NOT NULL, retailer TEXT,
    valid_from DATE, valid_through DATE, changed_by TEXT NOT NULL,
    reason TEXT NOT NULL, committed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (deal_id, revision)
);
CREATE TABLE IF NOT EXISTS deal_history_sources (
    deal_id UUID NOT NULL, revision BIGINT NOT NULL,
    artifact_id UUID NOT NULL REFERENCES source_artifacts(artifact_id),
    PRIMARY KEY (deal_id, revision, artifact_id),
    FOREIGN KEY (deal_id, revision) REFERENCES deal_history(deal_id, revision)
);
CREATE TABLE IF NOT EXISTS rebate_current (
    claim_id UUID PRIMARY KEY, provider TEXT NOT NULL, external_claim_ref TEXT,
    planned_purchase_id UUID, transaction_id UUID, status TEXT NOT NULL,
    provider_status TEXT, expected_benefits BYTEA NOT NULL, confirmed_benefits BYTEA NOT NULL,
    submitted_on DATE, fulfilled_on DATE, revision BIGINT NOT NULL CHECK (revision > 0),
    recorded_by TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS rebate_sources (
    claim_id UUID NOT NULL REFERENCES rebate_current(claim_id),
    artifact_id UUID NOT NULL REFERENCES source_artifacts(artifact_id),
    PRIMARY KEY (claim_id, artifact_id)
);
CREATE TABLE IF NOT EXISTS rebate_history (
    claim_id UUID NOT NULL REFERENCES rebate_current(claim_id), revision BIGINT NOT NULL,
    provider TEXT NOT NULL, external_claim_ref TEXT, planned_purchase_id UUID,
    transaction_id UUID, status TEXT NOT NULL, provider_status TEXT,
    expected_benefits BYTEA NOT NULL, confirmed_benefits BYTEA NOT NULL,
    submitted_on DATE, fulfilled_on DATE, changed_by TEXT NOT NULL,
    reason TEXT NOT NULL, committed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (claim_id, revision)
);
CREATE TABLE IF NOT EXISTS rebate_history_sources (
    claim_id UUID NOT NULL, revision BIGINT NOT NULL,
    artifact_id UUID NOT NULL REFERENCES source_artifacts(artifact_id),
    PRIMARY KEY (claim_id, revision, artifact_id),
    FOREIGN KEY (claim_id, revision) REFERENCES rebate_history(claim_id, revision)
);
```

## V3__provider_account_ledger.sql

Source: [src/main/resources/db/migration/V3__provider_account_ledger.sql](../../src/main/resources/db/migration/V3__provider_account_ledger.sql)

```sql
CREATE TABLE IF NOT EXISTS provider_account_openings (
    observation_id UUID PRIMARY KEY, provider TEXT NOT NULL, observed_date DATE NOT NULL,
    original_units NUMERIC, usd_value NUMERIC, month_start BOOLEAN NOT NULL,
    valuation_basis TEXT NOT NULL, source_note TEXT NOT NULL, recorded_at TIMESTAMPTZ NOT NULL,
    payload BYTEA NOT NULL
);
CREATE INDEX IF NOT EXISTS provider_account_openings_provider_date
    ON provider_account_openings(provider, observed_date);

CREATE TABLE IF NOT EXISTS provider_account_adjustments (
    event_id UUID PRIMARY KEY, provider TEXT NOT NULL, event_date DATE NOT NULL,
    unit_change NUMERIC, usd_change NUMERIC, reason TEXT NOT NULL,
    recorded_by TEXT NOT NULL, recorded_at TIMESTAMPTZ NOT NULL, payload BYTEA NOT NULL
);
CREATE INDEX IF NOT EXISTS provider_account_adjustments_provider_date
    ON provider_account_adjustments(provider, event_date);

CREATE TABLE IF NOT EXISTS provider_redemptions_current (
    redemption_id UUID PRIMARY KEY, provider TEXT NOT NULL, revision BIGINT NOT NULL CHECK (revision > 0),
    status TEXT NOT NULL, payload BYTEA NOT NULL, recorded_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE IF NOT EXISTS provider_redemptions_history (
    redemption_id UUID NOT NULL REFERENCES provider_redemptions_current(redemption_id),
    revision BIGINT NOT NULL CHECK (revision > 0), provider TEXT NOT NULL,
    status TEXT NOT NULL, payload BYTEA NOT NULL, recorded_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (redemption_id, revision)
);
CREATE INDEX IF NOT EXISTS provider_redemptions_provider
    ON provider_redemptions_current(provider);
```

## V4__shopping_plans_and_clipped_coupons.sql

Source: [src/main/resources/db/migration/V4__shopping_plans_and_clipped_coupons.sql](../../src/main/resources/db/migration/V4__shopping_plans_and_clipped_coupons.sql)

```sql
CREATE TABLE IF NOT EXISTS shopping_plan_current (
    plan_id UUID PRIMARY KEY, name TEXT NOT NULL, retailer TEXT,
    shopping_date DATE, revision BIGINT NOT NULL CHECK (revision > 0),
    payload BYTEA NOT NULL
);
CREATE TABLE IF NOT EXISTS shopping_plan_history (
    plan_id UUID NOT NULL REFERENCES shopping_plan_current(plan_id),
    revision BIGINT NOT NULL CHECK (revision > 0), name TEXT NOT NULL,
    changed_by TEXT NOT NULL, reason TEXT NOT NULL, payload BYTEA NOT NULL,
    PRIMARY KEY (plan_id, revision)
);
CREATE TABLE IF NOT EXISTS planned_purchase_current (
    purchase_id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES shopping_plan_current(plan_id),
    revision BIGINT NOT NULL CHECK (revision > 0), payload BYTEA NOT NULL
);
CREATE INDEX IF NOT EXISTS planned_purchase_plan ON planned_purchase_current(plan_id);
CREATE TABLE IF NOT EXISTS planned_purchase_history (
    purchase_id UUID NOT NULL REFERENCES planned_purchase_current(purchase_id),
    revision BIGINT NOT NULL CHECK (revision > 0), changed_by TEXT NOT NULL,
    reason TEXT NOT NULL, payload BYTEA NOT NULL,
    PRIMARY KEY (purchase_id, revision)
);
CREATE TABLE IF NOT EXISTS clipped_coupon_current (
    coupon_id UUID PRIMARY KEY, provider TEXT NOT NULL, external_id TEXT,
    status TEXT NOT NULL, revision BIGINT NOT NULL CHECK (revision > 0),
    payload BYTEA NOT NULL
);
CREATE INDEX IF NOT EXISTS clipped_coupon_provider ON clipped_coupon_current(provider);
CREATE TABLE IF NOT EXISTS clipped_coupon_history (
    coupon_id UUID NOT NULL REFERENCES clipped_coupon_current(coupon_id),
    revision BIGINT NOT NULL CHECK (revision > 0), changed_by TEXT NOT NULL,
    reason TEXT NOT NULL, payload BYTEA NOT NULL,
    PRIMARY KEY (coupon_id, revision)
);
CREATE TABLE IF NOT EXISTS clipped_coupon_redemptions (
    coupon_id UUID PRIMARY KEY REFERENCES clipped_coupon_current(coupon_id),
    transaction_id UUID NOT NULL UNIQUE, redeemed_on DATE NOT NULL
);
```

## V5__api_retry_journal.sql

Source: [src/main/resources/db/migration/V5__api_retry_journal.sql](../../src/main/resources/db/migration/V5__api_retry_journal.sql)

```sql
-- One receipt can consume several different clipped coupons.
ALTER TABLE clipped_coupon_redemptions
    DROP CONSTRAINT IF EXISTS clipped_coupon_redemptions_transaction_id_key;

-- Separate API retry outcomes from receipt repository write requests.
-- NULL response columns mean the outcome needs reconciliation, not permission to retry.
CREATE TABLE IF NOT EXISTS api_write_requests (
    scope TEXT NOT NULL,
    request_key TEXT NOT NULL,
    signature TEXT NOT NULL,
    status_code INTEGER,
    response_body TEXT,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (scope, request_key),
    CHECK ((status_code IS NULL AND response_body IS NULL)
        OR (status_code BETWEEN 200 AND 599 AND response_body IS NOT NULL))
);
```

## V6__lossless_local_migration.sql

Source: [src/main/resources/db/migration/V6__lossless_local_migration.sql](../../src/main/resources/db/migration/V6__lossless_local_migration.sql)

```sql
CREATE TABLE IF NOT EXISTS gift_card_promotions (
    promotion_id UUID PRIMARY KEY,
    payload BYTEA NOT NULL
);
CREATE TABLE IF NOT EXISTS rr_usage_links (
    source_id UUID PRIMARY KEY,
    usage_event_id UUID NOT NULL UNIQUE,
    spent_receipt_id UUID NOT NULL REFERENCES receipt_current(transaction_id),
    payload BYTEA NOT NULL
);
CREATE TABLE IF NOT EXISTS reward_removals (
    reward_id UUID PRIMARY KEY REFERENCES register_rewards(reward_id),
    payload BYTEA NOT NULL
);
-- Preserve the original append order independently of timestamps and UUIDs.
ALTER TABLE provider_account_openings ADD COLUMN IF NOT EXISTS source_sequence BIGINT;
ALTER TABLE provider_account_adjustments ADD COLUMN IF NOT EXISTS source_sequence BIGINT;
ALTER TABLE provider_redemptions_history ADD COLUMN IF NOT EXISTS source_sequence BIGINT;
-- The local format did not record provider-redemption commit timestamps.
-- NULL means not recorded. The migration must not invent an original timestamp.
ALTER TABLE provider_redemptions_current ALTER COLUMN recorded_at DROP NOT NULL;
ALTER TABLE provider_redemptions_history ALTER COLUMN recorded_at DROP NOT NULL;
CREATE TABLE IF NOT EXISTS local_migrations (
    source_digest TEXT PRIMARY KEY,
    checkpoint BYTEA NOT NULL,
    manifest BYTEA NOT NULL,
    migrated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS local_migration_files (
    source_digest TEXT NOT NULL REFERENCES local_migrations(source_digest),
    relative_path TEXT NOT NULL,
    sha256 TEXT NOT NULL,
    content BYTEA NOT NULL,
    PRIMARY KEY (source_digest, relative_path)
);
```

## V7__offer_pricing.sql

Source: [src/main/resources/db/migration/V7__offer_pricing.sql](../../src/main/resources/db/migration/V7__offer_pricing.sql)

```sql
ALTER TABLE deal_current ADD COLUMN IF NOT EXISTS pricing BYTEA;
ALTER TABLE deal_history ADD COLUMN IF NOT EXISTS pricing BYTEA;
ALTER TABLE deal_current ADD COLUMN IF NOT EXISTS manual_fingerprint TEXT UNIQUE;
```

## V8__shopping_plan_checkout.sql

Source: [src/main/resources/db/migration/V8__shopping_plan_checkout.sql](../../src/main/resources/db/migration/V8__shopping_plan_checkout.sql)

```sql
CREATE TABLE IF NOT EXISTS shopping_plan_checkout_current (
    plan_id UUID PRIMARY KEY
        REFERENCES shopping_plan_current(plan_id),
    revision BIGINT NOT NULL CHECK (revision >= 1),
    promo_code TEXT,
    promo_discount NUMERIC(14,2) NOT NULL DEFAULT 0,
    tax_rate NUMERIC(9,4),
    taxable_base NUMERIC(14,2),
    walgreens_cash_used NUMERIC(14,2) NOT NULL DEFAULT 0,
    register_rewards_coupon NUMERIC(14,2) NOT NULL DEFAULT 0,
    gift_card_used NUMERIC(14,2) NOT NULL DEFAULT 0,
    cash_payment NUMERIC(14,2) NOT NULL DEFAULT 0,
    card_payment NUMERIC(14,2) NOT NULL DEFAULT 0,
    other_payment NUMERIC(14,2) NOT NULL DEFAULT 0,
    notes TEXT
);

CREATE TABLE IF NOT EXISTS shopping_plan_checkout_history (
    plan_id UUID NOT NULL
        REFERENCES shopping_plan_current(plan_id),
    revision BIGINT NOT NULL CHECK (revision >= 1),
    changed_by TEXT NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    promo_code TEXT,
    promo_discount NUMERIC(14,2) NOT NULL,
    tax_rate NUMERIC(9,4),
    taxable_base NUMERIC(14,2),
    walgreens_cash_used NUMERIC(14,2) NOT NULL,
    register_rewards_coupon NUMERIC(14,2) NOT NULL,
    gift_card_used NUMERIC(14,2) NOT NULL,
    cash_payment NUMERIC(14,2) NOT NULL,
    card_payment NUMERIC(14,2) NOT NULL,
    other_payment NUMERIC(14,2) NOT NULL,
    notes TEXT,
    PRIMARY KEY (plan_id, revision)
);
```

## V9__actual_checkout_tax.sql

Source: [src/main/resources/db/migration/V9__actual_checkout_tax.sql](../../src/main/resources/db/migration/V9__actual_checkout_tax.sql)

```sql
ALTER TABLE shopping_plan_checkout_current
ADD COLUMN IF NOT EXISTS actual_tax NUMERIC(14,2);

ALTER TABLE shopping_plan_checkout_history
ADD COLUMN IF NOT EXISTS actual_tax NUMERIC(14,2);
```



## October 23 implementation note

Receipt browsing summaries, UI pricing terminology and saved-receipt edit/delete/
restore routes reuse existing receipt-current/history, evidence and API-request
journal storage. No migration, table/column rename or historical rewrite was added.
Existing snapshot field saleUnitPrice remains unchanged; the new UI label clarifies
that separate listed discounts are deducted afterward. Live schema state was not
changed by this work. See API-REFERENCE.md and CALCULATIONS.md for current behavior.

October 23 regular-price entry: new editDiscounts/preservePriceLineIds fields are
API projections, not new database columns. Explicit line-attached promotions use
existing applied-coupon snapshot fields and DiscountKind.SALE_PROMOTION. Normalized
values are persisted only as a reviewed receipt revision; no batch rewrite or
migration was added. Historical unit-price fields remain compatible.

## Revision 1.6 — existing storage reused

No DDL or migration is required. Receipt footer observations persist in existing serialized receipt_current contents and immutable receipt_history snapshots through guarded correction. Beginning-of-day account observations append to provider_account_openings through PostgresProviderAccountRepository; API retry outcomes use api_write_requests. Existing rr_usage_links, receipt earnings and register_rewards supply display associations; displaying a relationship writes nothing. Existing receipt evidence and historical versions are retained.


## Revision 1.7 — receipt RR correction and matching

No migration is required. RR entry/conversion remains inside the existing receipt correction/current/history transaction. Matching rechecks receipt revision and eligible source under PostgresReceiptState.lock, then inserts into existing rr_usage_links. API journal retains stable-key outcomes. Matching does not change receipt revision or add an earning/payment; its separate RRUsageLink retains actor/reason/time. Consumed/linked instruments remain reserved, including historical/deleted use scenarios.


## Revision 1.8 — RR without an earning receipt

No schema migration. Unknown-source entry atomically inserts into existing register_rewards (nullable earned_receipt_id, nullable dates in payload) and rr_usage_links. Failed validation/insert rolls back both. The existing api_write_requests journal handles stable retries; no receipt financial snapshot or earning is added.


## Revision 1.10 — independent Rewards inventory

No migration. Independent inventory registration inserts into register_rewards with earned_receipt_id NULL and nullable dates in the existing payload, using shared transaction lock and durable API retry journal. Later matching inserts rr_usage_links only. The initial save has no receipt transaction/history entry or financial movement to duplicate.


## Revision 1.11 — visible recorded receipt calculations

No migration, serialized model change or historical rewrite. calculationChecks is computed on receipt/preview/workspace reads and is not stored as a payment, observation, revision or financial event.


## Revision 1.13 — reuse existing files and align CLI rebate display

No schema migration, persisted class signature change or data backfill. The consolidated nested classes are transient read DTO/helpers, not persisted receipt snapshots. Package-private receipt input parsers retain their class identities. The display uses an existing API/CLI measure; no financial event is added or altered.


Clipped coupons and catalog cleanup: Deals & promotions and Coupons now expose permanent Delete actions with write access and revision checks. DELETE /deals/{id} and DELETE /clipped-coupons/{id} remove the catalog record and its own revision history, detach current plan associations while retaining product prices, and remove deal source artifacts only when not shared. Normal plan revision history remains; no deletion backup is created. Redeemed coupons cannot be deleted. Receipt entry accepts clippedCouponIds and offers saved coupon selection per product and in shared/receipt-wide discounts. Selection creates one editable discount with the coupon UUID, description, type and buy requirement; use the shared selector for a coupon covering several products. Percentage/free-item deductions use the actual recorded receipt amount. Selecting does not consume a coupon; receipt creation and all selected coupon redemptions commit atomically in PostgreSQL. Expired, already-used, or ineligible coupons cause rollback. The Coupons page then shows Applied and the receipt connection. Do not manually repeat a selected deduction. Existing receipt corrections retain their separate workflow; editing a discount does not automatically release a previously redeemed coupon. No new schema migration or files are introduced.

Changed application and test files: frontend/src/App.tsx, frontend/src/pages/WorkspacePages.tsx, frontend/src/features/receipts/ReceiptCreate.tsx, frontend/src/types/platform.ts, frontend/src/App.css, frontend/src/features/receipts/ReceiptCreate.test.tsx, src/main/java/coupons/api/CouponApiService.java, src/main/java/coupons/api/ReceiptInput.java, src/main/java/coupons/repository/PostgresPlatformRepository.java, src/main/java/coupons/repository/PostgresTransactionRepository.java, src/main/java/coupons/repository/PostgresClippedCouponRepository.java, src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java.


ECB non-receipt member rewards now support optional expirationDate in POST /accounts/member-rewards and an ECB expiration date input. Dates before the reward date are rejected. AccountAdjustment retains the date in its existing serialized payload; its legacy eight-argument constructor and older records default to unknown expiration. No new SQL column or migration is required. Account activity displays Expires YYYY-MM-DD. The date is informational: it does not automatically debit balances or infer remaining coupon value after spending. Existing saved financial amounts are unchanged.
Changed files: WorkspacePages.tsx, CouponApiService.java, AccountAdjustment.java, ProviderBalanceSummary.java, ReceiptCreate.test.tsx and ProviderBalanceSummaryTest.java.
