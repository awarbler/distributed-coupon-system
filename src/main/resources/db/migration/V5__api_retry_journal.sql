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
