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
