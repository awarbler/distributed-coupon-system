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
