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
