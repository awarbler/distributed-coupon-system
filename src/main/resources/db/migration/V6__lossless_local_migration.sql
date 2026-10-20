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
