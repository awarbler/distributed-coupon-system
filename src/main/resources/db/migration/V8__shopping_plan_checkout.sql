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