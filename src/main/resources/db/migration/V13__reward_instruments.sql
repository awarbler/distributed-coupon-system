-- Additive identities and observed activity. No legacy account or receipt data is rewritten.
CREATE TABLE IF NOT EXISTS reward_instruments (
 id UUID PRIMARY KEY, kind TEXT NOT NULL CHECK(kind IN ('ACCOUNT_REWARD','GIFT_CARD')),
 provider TEXT NOT NULL, nickname TEXT, last_four TEXT, unit TEXT NOT NULL CHECK(unit IN ('USD','POINTS')),
 opening NUMERIC, opening_date DATE, history_complete BOOLEAN NOT NULL, revision BIGINT NOT NULL CHECK(revision>0),
 replacement_id UUID REFERENCES reward_instruments(id), evidence_reference TEXT, reason TEXT NOT NULL,
 recorded_by TEXT NOT NULL, recorded_at TIMESTAMPTZ NOT NULL,
 CHECK(last_four IS NULL OR (kind='GIFT_CARD' AND last_four ~ '^[0-9]{4}$')),
 CHECK(kind<>'GIFT_CARD' OR unit='USD')
);
CREATE TABLE IF NOT EXISTS reward_instrument_history (LIKE reward_instruments INCLUDING CONSTRAINTS);
CREATE UNIQUE INDEX IF NOT EXISTS reward_instrument_history_identity ON reward_instrument_history(id,revision);
CREATE TABLE IF NOT EXISTS reward_instrument_events (
 id UUID PRIMARY KEY, instrument_id UUID NOT NULL REFERENCES reward_instruments(id),
 kind TEXT NOT NULL CHECK(kind IN ('CREDIT','REDEMPTION','WITHDRAWAL','ADJUSTMENT','REFUND','REVERSAL','RESTORATION','REPORTED_BALANCE','EXCHANGE')),
 amount NUMERIC, event_date DATE, reason TEXT NOT NULL, recorded_by TEXT NOT NULL, recorded_at TIMESTAMPTZ NOT NULL,
 supersedes UUID UNIQUE REFERENCES reward_instrument_events(id),
 related_instrument UUID REFERENCES reward_instruments(id), exchange_id UUID,
 source_receipt UUID REFERENCES receipt_current(transaction_id), source_event UUID, source_revision BIGINT,
 CHECK ((source_receipt IS NULL)=(source_event IS NULL)),
 CHECK(amount IS NOT NULL OR kind='EXCHANGE')
);
CREATE INDEX IF NOT EXISTS reward_instrument_event_account ON reward_instrument_events(instrument_id);

CREATE OR REPLACE FUNCTION protect_reward_instrument_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Reward instrument evidence and history are append-only'; END;
$$;
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgrelid='reward_instrument_events'::regclass AND tgname='reward_event_immutable') THEN
  CREATE TRIGGER reward_event_immutable BEFORE UPDATE OR DELETE ON reward_instrument_events FOR EACH ROW EXECUTE FUNCTION protect_reward_instrument_history();
 END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgrelid='reward_instrument_history'::regclass AND tgname='reward_history_immutable') THEN
  CREATE TRIGGER reward_history_immutable BEFORE UPDATE OR DELETE ON reward_instrument_history FOR EACH ROW EXECUTE FUNCTION protect_reward_instrument_history();
 END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgrelid='reward_instruments'::regclass AND tgname='reward_identity_no_delete') THEN
  CREATE TRIGGER reward_identity_no_delete BEFORE DELETE ON reward_instruments FOR EACH ROW EXECUTE FUNCTION protect_reward_instrument_history();
 END IF;
END $$;
