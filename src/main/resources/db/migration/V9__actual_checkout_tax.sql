ALTER TABLE shopping_plan_checkout_current
ADD COLUMN IF NOT EXISTS actual_tax NUMERIC(14,2);

ALTER TABLE shopping_plan_checkout_history
ADD COLUMN IF NOT EXISTS actual_tax NUMERIC(14,2);