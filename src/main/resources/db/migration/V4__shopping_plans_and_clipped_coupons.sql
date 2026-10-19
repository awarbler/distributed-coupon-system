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
