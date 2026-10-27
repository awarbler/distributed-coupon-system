-- Experimental V12: explicitly installed only after disposable-target verification.
-- Deliberately absent from PostgresSchema.apply and normal server startup.
CREATE TABLE receipt_financial_projection_config (
 singleton BOOLEAN PRIMARY KEY CHECK(singleton), marker TEXT NOT NULL,
 projection_version INTEGER NOT NULL CHECK(projection_version=1)
);
CREATE TABLE receipt_financial_revisions (
 transaction_id UUID NOT NULL, revision BIGINT NOT NULL CHECK(revision>0),
 status TEXT NOT NULL CHECK(status IN ('ACTIVE','DELETED')),
 projection_version INTEGER NOT NULL CHECK(projection_version=1),
 source_hash TEXT NOT NULL CHECK(length(source_hash)=64),
 formula_version TEXT NOT NULL,
 calculation_status TEXT NOT NULL CHECK(calculation_status IN ('CALCULATED','UNAVAILABLE')),
 calculation_note TEXT,
 source_context BYTEA NOT NULL,
 tax_amount NUMERIC NOT NULL, tax_amount_scale INTEGER NOT NULL, CHECK((tax_amount IS NULL)=(tax_amount_scale IS NULL)), CHECK(tax_amount IS NULL OR tax_amount NOT IN ('NaN'::numeric,'Infinity'::numeric,'-Infinity'::numeric)),
 retailer_reported_total NUMERIC NOT NULL, retailer_reported_total_scale INTEGER NOT NULL, CHECK((retailer_reported_total IS NULL)=(retailer_reported_total_scale IS NULL)), CHECK(retailer_reported_total IS NULL OR retailer_reported_total NOT IN ('NaN'::numeric,'Infinity'::numeric,'-Infinity'::numeric)),
 expected_total NUMERIC, expected_total_scale INTEGER, CHECK((expected_total IS NULL)=(expected_total_scale IS NULL)), CHECK(expected_total IS NULL OR expected_total NOT IN ('NaN'::numeric,'Infinity'::numeric,'-Infinity'::numeric)),
 difference NUMERIC, difference_scale INTEGER, CHECK((difference IS NULL)=(difference_scale IS NULL)), CHECK(difference IS NULL OR difference NOT IN ('NaN'::numeric,'Infinity'::numeric,'-Infinity'::numeric)),
 PRIMARY KEY(transaction_id,revision),
 FOREIGN KEY(transaction_id,revision) REFERENCES receipt_history(transaction_id,revision) ON DELETE RESTRICT,
 CHECK((calculation_status='UNAVAILABLE' AND expected_total IS NULL AND difference IS NULL) OR (calculation_status='CALCULATED' AND expected_total IS NOT NULL AND difference IS NOT NULL AND difference=retailer_reported_total-expected_total))
);
CREATE TABLE receipt_purchase_lines (
 transaction_id UUID NOT NULL, revision BIGINT NOT NULL CHECK(revision>0),
 line_item_id UUID NOT NULL,
 source_position INTEGER NOT NULL CHECK(source_position>=0),
 product_name TEXT NOT NULL,
 product_code TEXT,
 quantity INTEGER NOT NULL CHECK(quantity>0),
 regular_unit_price NUMERIC, regular_unit_price_scale INTEGER, CHECK((regular_unit_price IS NULL)=(regular_unit_price_scale IS NULL)), CHECK(regular_unit_price IS NULL OR regular_unit_price NOT IN ('NaN'::numeric,'Infinity'::numeric,'-Infinity'::numeric)),
 purchase_unit_price NUMERIC NOT NULL, purchase_unit_price_scale INTEGER NOT NULL, CHECK((purchase_unit_price IS NULL)=(purchase_unit_price_scale IS NULL)), CHECK(purchase_unit_price IS NULL OR purchase_unit_price NOT IN ('NaN'::numeric,'Infinity'::numeric,'-Infinity'::numeric)),
 source_context BYTEA NOT NULL,
 PRIMARY KEY(transaction_id,revision,line_item_id),
 UNIQUE(transaction_id,revision,source_position),
 FOREIGN KEY(transaction_id,revision) REFERENCES receipt_financial_revisions(transaction_id,revision) ON DELETE RESTRICT
);
CREATE TABLE receipt_coupon_applications (
 transaction_id UUID NOT NULL, revision BIGINT NOT NULL CHECK(revision>0),
 application_id UUID NOT NULL,
 source_position INTEGER NOT NULL CHECK(source_position>=0),
 printed_label TEXT NOT NULL,
 required_quantity INTEGER CHECK(required_quantity>0),
 whole_receipt BOOLEAN NOT NULL,
 applied_amount NUMERIC NOT NULL, applied_amount_scale INTEGER NOT NULL, CHECK((applied_amount IS NULL)=(applied_amount_scale IS NULL)), CHECK(applied_amount IS NULL OR applied_amount NOT IN ('NaN'::numeric,'Infinity'::numeric,'-Infinity'::numeric)),
 source_context BYTEA NOT NULL,
 PRIMARY KEY(transaction_id,revision,application_id),
 FOREIGN KEY(transaction_id,revision) REFERENCES receipt_financial_revisions(transaction_id,revision) ON DELETE RESTRICT
);
CREATE TABLE receipt_coupon_lines (
 transaction_id UUID NOT NULL, revision BIGINT NOT NULL CHECK(revision>0),
 application_id UUID NOT NULL,
 line_item_id UUID NOT NULL,
 source_position INTEGER NOT NULL CHECK(source_position>=0),
 qualifying_quantity INTEGER CHECK(qualifying_quantity>0),
 PRIMARY KEY(transaction_id,revision,application_id,line_item_id),
 FOREIGN KEY(transaction_id,revision,application_id) REFERENCES receipt_coupon_applications(transaction_id,revision,application_id) ON DELETE RESTRICT,
 FOREIGN KEY(transaction_id,revision,line_item_id) REFERENCES receipt_purchase_lines(transaction_id,revision,line_item_id) ON DELETE RESTRICT
);
CREATE TABLE receipt_coupon_offers (
 transaction_id UUID NOT NULL, revision BIGINT NOT NULL CHECK(revision>0),
 source_position INTEGER NOT NULL CHECK(source_position>=0),
 description TEXT NOT NULL,
 coupon_type TEXT NOT NULL,
 required_quantity INTEGER NOT NULL CHECK(required_quantity>0),
 expiration_date DATE,
 discount_amount NUMERIC NOT NULL, discount_amount_scale INTEGER NOT NULL, CHECK((discount_amount IS NULL)=(discount_amount_scale IS NULL)), CHECK(discount_amount IS NULL OR discount_amount NOT IN ('NaN'::numeric,'Infinity'::numeric,'-Infinity'::numeric)),
 source_context BYTEA NOT NULL,
 PRIMARY KEY(transaction_id,revision,source_position),
 FOREIGN KEY(transaction_id,revision) REFERENCES receipt_financial_revisions(transaction_id,revision) ON DELETE RESTRICT
);
CREATE TABLE receipt_payments (
 transaction_id UUID NOT NULL, revision BIGINT NOT NULL CHECK(revision>0),
 payment_id UUID NOT NULL,
 source_position INTEGER NOT NULL CHECK(source_position>=0),
 payment_type TEXT NOT NULL,
 account_name TEXT,
 notes TEXT,
 amount NUMERIC NOT NULL, amount_scale INTEGER NOT NULL, CHECK((amount IS NULL)=(amount_scale IS NULL)), CHECK(amount IS NULL OR amount NOT IN ('NaN'::numeric,'Infinity'::numeric,'-Infinity'::numeric)),
 source_context BYTEA NOT NULL,
 PRIMARY KEY(transaction_id,revision,payment_id),
 FOREIGN KEY(transaction_id,revision) REFERENCES receipt_financial_revisions(transaction_id,revision) ON DELETE RESTRICT
);
CREATE TABLE receipt_reward_redemptions (
 transaction_id UUID NOT NULL, revision BIGINT NOT NULL CHECK(revision>0),
 redemption_id UUID NOT NULL,
 reward_id UUID NOT NULL,
 source_position INTEGER NOT NULL CHECK(source_position>=0),
 amount NUMERIC NOT NULL, amount_scale INTEGER NOT NULL, CHECK((amount IS NULL)=(amount_scale IS NULL)), CHECK(amount IS NULL OR amount NOT IN ('NaN'::numeric,'Infinity'::numeric,'-Infinity'::numeric)),
 source_context BYTEA NOT NULL,
 PRIMARY KEY(transaction_id,revision,redemption_id),
 FOREIGN KEY(transaction_id,revision) REFERENCES receipt_financial_revisions(transaction_id,revision) ON DELETE RESTRICT
);
CREATE TABLE receipt_promotion_terms (
 transaction_id UUID NOT NULL, revision BIGINT NOT NULL CHECK(revision>0),
 source_position INTEGER NOT NULL CHECK(source_position>=0),
 description TEXT NOT NULL,
 promotion_type TEXT NOT NULL,
 threshold_unit TEXT NOT NULL CHECK(threshold_unit IN ('MONEY','QUANTITY')),
 reward_type TEXT NOT NULL,
 source_representation TEXT NOT NULL CHECK(source_representation='LEGACY_DOUBLE'),
 qualification_threshold NUMERIC NOT NULL, qualification_threshold_scale INTEGER NOT NULL, CHECK((qualification_threshold IS NULL)=(qualification_threshold_scale IS NULL)), CHECK(qualification_threshold IS NULL OR qualification_threshold NOT IN ('NaN'::numeric,'Infinity'::numeric,'-Infinity'::numeric)),
 reward_amount NUMERIC NOT NULL, reward_amount_scale INTEGER NOT NULL, CHECK((reward_amount IS NULL)=(reward_amount_scale IS NULL)), CHECK(reward_amount IS NULL OR reward_amount NOT IN ('NaN'::numeric,'Infinity'::numeric,'-Infinity'::numeric)),
 source_context BYTEA NOT NULL,
 PRIMARY KEY(transaction_id,revision,source_position),
 FOREIGN KEY(transaction_id,revision) REFERENCES receipt_financial_revisions(transaction_id,revision) ON DELETE RESTRICT
);
CREATE TABLE receipt_earning_benefits (
 transaction_id UUID NOT NULL, revision BIGINT NOT NULL CHECK(revision>0),
 earning_id UUID NOT NULL,
 source_position INTEGER NOT NULL CHECK(source_position>=0),
 benefit_role TEXT NOT NULL CHECK(benefit_role IN ('EXPECTED','CONFIRMED')),
 earning_status TEXT NOT NULL,
 benefit_kind TEXT NOT NULL,
 unit TEXT NOT NULL CHECK(unit IN ('MONEY','POINTS')),
 currency TEXT,
 program TEXT,
 amount NUMERIC NOT NULL, amount_scale INTEGER NOT NULL, CHECK((amount IS NULL)=(amount_scale IS NULL)), CHECK(amount IS NULL OR amount NOT IN ('NaN'::numeric,'Infinity'::numeric,'-Infinity'::numeric)),
 source_context BYTEA NOT NULL,
 CHECK((unit='POINTS' AND currency IS NULL AND program IS NOT NULL) OR (unit='MONEY' AND currency IS NOT NULL)),
 PRIMARY KEY(transaction_id,revision,earning_id,benefit_role),
 FOREIGN KEY(transaction_id,revision) REFERENCES receipt_financial_revisions(transaction_id,revision) ON DELETE RESTRICT
);
CREATE TABLE receipt_financial_amounts (
 transaction_id UUID NOT NULL, revision BIGINT NOT NULL CHECK(revision>0),
 source_path TEXT NOT NULL,
 field_name TEXT NOT NULL,
 unit TEXT NOT NULL,
 currency TEXT,
 program TEXT,
 source_representation TEXT NOT NULL CHECK(source_representation IN ('BIG_DECIMAL','LEGACY_DOUBLE','DERIVED')),
 source_text TEXT NOT NULL,
 amount NUMERIC NOT NULL, amount_scale INTEGER NOT NULL, CHECK((amount IS NULL)=(amount_scale IS NULL)), CHECK(amount IS NULL OR amount NOT IN ('NaN'::numeric,'Infinity'::numeric,'-Infinity'::numeric)),
 source_context BYTEA NOT NULL,
 PRIMARY KEY(transaction_id,revision,source_path,field_name),
 CHECK((field_name IN ('regular_merchandise_total','sale_merchandise_total','applied_coupon_total','after_coupon_total','register_rewards_redeemed','walgreens_cash_redeemed','reward_redemption_total','after_redemptions_total','valuation_amount','unidentified_rr_amount','wc_starting_balance','wc_used','wc_earned','wc_ending_balance','wc_available_balance','savings_reported_amount','savings_included_amount','reviewed_eligible_amount') AND unit IN ('MONEY')) OR (field_name IN ('receipt_saving_value_percent') AND unit IN ('RATE')) OR (field_name IN ('legacy_reward_amount','original_units') AND unit IN ('UNKNOWN_UNIT','POINTS')) OR (field_name IN ('requirement_amount') AND unit IN ('MONEY','QUANTITY'))),
 FOREIGN KEY(transaction_id,revision) REFERENCES receipt_financial_revisions(transaction_id,revision) ON DELETE RESTRICT
);
CREATE TABLE receipt_financial_backfill_runs (
 run_id UUID PRIMARY KEY, manifest_hash TEXT NOT NULL, total_revisions BIGINT NOT NULL,
 projected_revisions BIGINT NOT NULL, complete BOOLEAN NOT NULL,
 CHECK(total_revisions>=0 AND projected_revisions>=0 AND projected_revisions<=total_revisions),
 CHECK(NOT complete OR total_revisions=projected_revisions)
);
CREATE FUNCTION reject_receipt_projection_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Receipt financial projections are append-only'; END;
$$;
CREATE TRIGGER receipt_financial_revisions_immutable BEFORE UPDATE OR DELETE ON receipt_financial_revisions FOR EACH ROW EXECUTE FUNCTION reject_receipt_projection_change();
CREATE TRIGGER receipt_purchase_lines_immutable BEFORE UPDATE OR DELETE ON receipt_purchase_lines FOR EACH ROW EXECUTE FUNCTION reject_receipt_projection_change();
CREATE TRIGGER receipt_coupon_applications_immutable BEFORE UPDATE OR DELETE ON receipt_coupon_applications FOR EACH ROW EXECUTE FUNCTION reject_receipt_projection_change();
CREATE TRIGGER receipt_coupon_lines_immutable BEFORE UPDATE OR DELETE ON receipt_coupon_lines FOR EACH ROW EXECUTE FUNCTION reject_receipt_projection_change();
CREATE TRIGGER receipt_coupon_offers_immutable BEFORE UPDATE OR DELETE ON receipt_coupon_offers FOR EACH ROW EXECUTE FUNCTION reject_receipt_projection_change();
CREATE TRIGGER receipt_payments_immutable BEFORE UPDATE OR DELETE ON receipt_payments FOR EACH ROW EXECUTE FUNCTION reject_receipt_projection_change();
CREATE TRIGGER receipt_reward_redemptions_immutable BEFORE UPDATE OR DELETE ON receipt_reward_redemptions FOR EACH ROW EXECUTE FUNCTION reject_receipt_projection_change();
CREATE TRIGGER receipt_promotion_terms_immutable BEFORE UPDATE OR DELETE ON receipt_promotion_terms FOR EACH ROW EXECUTE FUNCTION reject_receipt_projection_change();
CREATE TRIGGER receipt_earning_benefits_immutable BEFORE UPDATE OR DELETE ON receipt_earning_benefits FOR EACH ROW EXECUTE FUNCTION reject_receipt_projection_change();
CREATE TRIGGER receipt_financial_amounts_immutable BEFORE UPDATE OR DELETE ON receipt_financial_amounts FOR EACH ROW EXECUTE FUNCTION reject_receipt_projection_change();
CREATE TRIGGER receipt_financial_projection_config_immutable BEFORE UPDATE OR DELETE ON receipt_financial_projection_config FOR EACH ROW EXECUTE FUNCTION reject_receipt_projection_change();

CREATE TRIGGER receipt_financial_revisions_no_truncate BEFORE TRUNCATE ON receipt_financial_revisions FOR EACH STATEMENT EXECUTE FUNCTION reject_receipt_projection_change();
CREATE TRIGGER receipt_purchase_lines_no_truncate BEFORE TRUNCATE ON receipt_purchase_lines FOR EACH STATEMENT EXECUTE FUNCTION reject_receipt_projection_change();
CREATE TRIGGER receipt_coupon_applications_no_truncate BEFORE TRUNCATE ON receipt_coupon_applications FOR EACH STATEMENT EXECUTE FUNCTION reject_receipt_projection_change();
CREATE TRIGGER receipt_coupon_lines_no_truncate BEFORE TRUNCATE ON receipt_coupon_lines FOR EACH STATEMENT EXECUTE FUNCTION reject_receipt_projection_change();
CREATE TRIGGER receipt_coupon_offers_no_truncate BEFORE TRUNCATE ON receipt_coupon_offers FOR EACH STATEMENT EXECUTE FUNCTION reject_receipt_projection_change();
CREATE TRIGGER receipt_payments_no_truncate BEFORE TRUNCATE ON receipt_payments FOR EACH STATEMENT EXECUTE FUNCTION reject_receipt_projection_change();
CREATE TRIGGER receipt_reward_redemptions_no_truncate BEFORE TRUNCATE ON receipt_reward_redemptions FOR EACH STATEMENT EXECUTE FUNCTION reject_receipt_projection_change();
CREATE TRIGGER receipt_promotion_terms_no_truncate BEFORE TRUNCATE ON receipt_promotion_terms FOR EACH STATEMENT EXECUTE FUNCTION reject_receipt_projection_change();
CREATE TRIGGER receipt_earning_benefits_no_truncate BEFORE TRUNCATE ON receipt_earning_benefits FOR EACH STATEMENT EXECUTE FUNCTION reject_receipt_projection_change();
CREATE TRIGGER receipt_financial_amounts_no_truncate BEFORE TRUNCATE ON receipt_financial_amounts FOR EACH STATEMENT EXECUTE FUNCTION reject_receipt_projection_change();
CREATE TRIGGER receipt_financial_projection_config_no_truncate BEFORE TRUNCATE ON receipt_financial_projection_config FOR EACH STATEMENT EXECUTE FUNCTION reject_receipt_projection_change();
