CREATE TABLE IF NOT EXISTS deleted_weekly_deals (
 deal_id UUID PRIMARY KEY, deleted_revision BIGINT NOT NULL,
 deleted_by TEXT NOT NULL, reason TEXT NOT NULL, deleted_at TIMESTAMPTZ NOT NULL,
 source_ids UUID[] NOT NULL, payload BYTEA NOT NULL
);
ALTER TABLE reward_instruments ADD COLUMN IF NOT EXISTS payment_network TEXT;
ALTER TABLE reward_instruments ADD COLUMN IF NOT EXISTS accepted_retailers TEXT[] NOT NULL DEFAULT '{}';
ALTER TABLE reward_instruments ADD COLUMN IF NOT EXISTS excluded_retailers TEXT[] NOT NULL DEFAULT '{}';
ALTER TABLE reward_instruments ADD COLUMN IF NOT EXISTS expires_on DATE;
ALTER TABLE reward_instruments ADD COLUMN IF NOT EXISTS eligibility_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE reward_instruments ADD COLUMN IF NOT EXISTS payment_terms TEXT;
ALTER TABLE reward_instrument_history ADD COLUMN IF NOT EXISTS payment_network TEXT;
ALTER TABLE reward_instrument_history ADD COLUMN IF NOT EXISTS accepted_retailers TEXT[] NOT NULL DEFAULT '{}';
ALTER TABLE reward_instrument_history ADD COLUMN IF NOT EXISTS excluded_retailers TEXT[] NOT NULL DEFAULT '{}';
ALTER TABLE reward_instrument_history ADD COLUMN IF NOT EXISTS expires_on DATE;
ALTER TABLE reward_instrument_history ADD COLUMN IF NOT EXISTS eligibility_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE reward_instrument_history ADD COLUMN IF NOT EXISTS payment_terms TEXT;
