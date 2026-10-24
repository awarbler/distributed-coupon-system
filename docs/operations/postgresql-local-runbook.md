# PostgreSQL Local Setup and Troubleshooting Runbook

**Project:** Distributed Coupon System / Grocery Intelligence Platform  
**Document status:** Working operational notes  
**Observed:** October 20, 2026  
**Scope:** Local macOS development; does not define production database configuration.

## 1. Verified observations

| Topic | Observation |
|---|---|
| PostgreSQL tools | Postgres.app tools found at `/Applications/Postgres.app/Contents/Versions/latest/bin` |
| Client version | `psql (PostgreSQL) 18.6 (Postgres.app)` |
| Shell PATH | `psql` and `pg_isready` are not currently available as bare commands |
| TCP port 5432 | `pg_isready -h localhost -p 5432` previously returned `accepting connections` |
| TCP authentication | `psql -h localhost -p 5432 -d postgres` attempted role `anitawoodford`; password authentication failed |
| TCP address | One attempt reported IPv6 `::1` connection refused, while IPv4 `127.0.0.1` returned a PostgreSQL authentication error |
| Default socket | `psql -d postgres` failed because `/tmp/.s.PGSQL.5432` was not found |
| Running processes | `ps -axo pid,user,command | grep '[p]ostgres'` showed no matching processes at last check |
| Startup script | A grep for `PGUSER`, `POSTGRES_USER`, `DB_USER`, `DATABASE_URL`, `JDBC_URL`, `PGDATABASE`, `DB_NAME` in `start-local-api.sh` returned no matches |

**Not yet established:** Which PostgreSQL server was responding, whether it is currently running, server version, application database name, application database role, password/authentication method, data directory, migration history, and backup/restore verification. Do not assume the server is PostgreSQL 18 just because the client is version 18.6.

## 2. Safe read-only diagnostics

Run from any terminal directory. These commands do not intentionally change database records.

```bash
# Verify installed command-line tools
/Applications/Postgres.app/Contents/Versions/latest/bin/psql --version

# Check whether TCP port 5432 currently responds
/Applications/Postgres.app/Contents/Versions/latest/bin/pg_isready -h 127.0.0.1 -p 5432

# Identify a listener and its owning process (if visible)
lsof -nP -iTCP:5432 -sTCP:LISTEN

# Inspect running PostgreSQL processes
ps -axo pid,user,command | grep '[p]ostgres'

# Check whether Docker is installed and available (does not start it)
command -v docker
```

If a server is confirmed, determine the **existing** application's connection settings from project configuration or an authorized local configuration source. Do not guess credentials or paste secrets into documentation, terminal output shared with others, or chat.

## 3. Authentication and connection rules

- A macOS username is **not necessarily** a PostgreSQL role.
- A PostgreSQL password is **not necessarily** the Mac login password.
- `pg_isready` confirms server responsiveness, **not** successful application authentication or the correct database.
- A missing `/tmp` socket does **not** prove there is no TCP server; Postgres.app may use another socket directory, or a different service may own port 5432.
- `psql -h localhost` and `psql` without `-h` can follow different connection paths and authentication rules.
- Never store plaintext passwords in this runbook, source control, screenshots, or logs.

## 4. Do not do during discovery

- Do not reset passwords, create replacement users/databases, or reinstall PostgreSQL merely to resolve an unknown credential.
- Do not execute migrations or integration tests against the household's real grocery database until the target database and test isolation are verified.
- Do not remove database files or run destructive Git cleanup.
- Do not assume an existing SQL dump has been successfully restored until restoration has been tested separately.

## 5. Project verification baseline

- Git branch `main`, commit `6d1bacf53b1fca79ff206d52061aabdc3ca378c4`; substantial modified and untracked application code remains.
- Java Maven tests: **517 total; 478 passed; 39 skipped; 0 failures/errors**. PostgreSQL-related tests were among those skipped.
- React/Vite production build: **passed**.
- Vitest: **28 passed, 0 failed, 6 test files**.
- PostgreSQL integration and migration state: **not yet verified**.

## 6. Next investigation checkpoint

Run:

```bash
lsof -nP -iTCP:5432 -sTCP:LISTEN
```

Record whether a process is listening and, if present, its process name/PID. If there is no listener, inspect Postgres.app status and local project configuration before starting any service. Once the correct server and application database are identified, use read-only queries to inspect `current_user`, `current_database`, server version, and migration metadata.

## 7. Documentation maintenance

Suggested repository location: `docs/operations/postgresql-local-runbook.md`. Keep this runbook with the project, but review `.gitignore` and repository privacy before committing any operational notes. Update the **observed date**, verified facts, and next checkpoint after each troubleshooting session. Never record secrets.


## Documentation revision 1.16 — material relocated from the root README (2026-10-24)

The following former README sections preserve prior operating guidance/change history. They are historical source material; current procedures above and the current API reference take precedence where earlier feature limitations or migration versions differ.

## Lossless local-to-PostgreSQL migration

`coupons.repository.LocalToPostgresMigration` reads an independent **copy** of
local receipt storage. It never opens the original CLI store or creates source
locks. Existing CLI behavior remains local. No migration runs automatically.

The default is an offline inspection:

```sh
java -cp 'target/classes:target/dependency/*' coupons.repository.LocalToPostgresMigration \
  --source-copy /path/to/independent-copy \
  --protected-source "$PWD/.coupon-data"
```

The source must contain receipts.bin. Original/overlapping directories, symbolic
links and hard-linked files are rejected. The working directory's .coupon-data
is protected independently of the supplied protected-source path. Use your
existing backup as the source of an independent working copy; this command does
not create another backup or edit the selected copy.

Database import requires explicit `--execute`, `--expected-database NAME`, and
separate `COUPON_MIGRATION_URL`, `COUPON_MIGRATION_USER`,
`COUPON_MIGRATION_PASSWORD` configuration. Ordinary API credentials are never
used as migration defaults. First rehearse on a disposable test database; do not
point integration-test or migration variables at a persistent database without
explicit authorization. No import into distributed_coupons has been performed.

The tool applies additive V1–V6 schema changes and imports all records in one
PostgreSQL transaction. It refuses a nonempty destination instead of merging or
overwriting records. It preserves receipt UUIDs, revisions, status, every history
ID/context/timestamp, exact decimal values and observations, evidence bytes/IDs,
RR instruments/redemptions/matching/removals, gift-card progress, provider records,
and original receipt request results. Original retry inputs are translated into
PostgreSQL's payload classes; matching retries return their original results even
after later revisions. Equality checks compare structured values, not incidental
Java map serialization order.

Before commit, validation compares all source/destination aggregate records,
financial snapshots, provider ordering, evidence SHA-256 hashes, original
checkpoint bytes, and archived file bytes/hashes. Any failure rolls back both
schema changes and imported rows. Independent concurrent migration attempts are
serialized. Repeating a completed import of the exact same source verifies its
archive and returns alreadyImported=true without overwriting later changes.
`--validate` instead compares the *current* destination against the source and
reports subsequent differences. It performs no writes or schema changes.

The source format has limits that are explicitly retained:

- Provider-redemption history has event dates/times but no original commit
  timestamps or revision numbers. Event timestamps remain exact; missing commit
  timestamps remain NULL. Database revision ordinals follow original append order.
- Original nanosecond timestamps and decimal scales remain in serialized values;
  PostgreSQL timestamp indexes have PostgreSQL's normal microsecond precision.
- All source files are archived verbatim with relative paths, hashes and recorded
  modification times. Drafts, prepared imports, images and other auxiliary files
  remain archives, not additional committed receipts or active resumable drafts.
  References to files outside the selected copy are not followed or imported.
- Existing stale promotion reviews and RR confirmations remain stale/reserved;
  migration does not manufacture approvals, earnings or available gift cards.

`PostgresReceiptSupplementRepository` provides the corresponding promotion,
RR-confirmation and reward-removal operations using existing local validation.
Migration is additive to the PostgreSQL adapters; financial calculators and the
receipt CLI are unchanged.


## Step 7 — read-only historical migration preview

`coupons.repository.LocalMigrationPreview` compares an independent local receipt
copy with an existing PostgreSQL V1–V6 schema. It uses an explicitly read-only,
repeatable-read transaction and does not apply migrations or import records.
Arguments: `SOURCE_COPY PROTECTED_ORIGINAL EXPECTED_DATABASE`; connection settings
are the existing `COUPON_POSTGRES_*` environment variables. Run only against your
intended database and retain the original directory as the protected path.

The preview reports source/destination counts, exact existing identities, new
candidates, conflicting identities, possible printed-receipt/evidence duplicates,
missing information, exact financial values and evidence SHA-256 hashes. Deleted
receipts participate in comparisons. It distinguishes embedded receipt deductions
and earnings from standalone clipped coupons, rebate claims, inventory and plans.
No absent domain record is invented to fill a dashboard.

“New” means an identity-level candidate, not approval to import. The existing
lossless importer accepts an empty target or a previously imported identical
source; merging into a populated target remains unsupported. No reset or overwrite
is proposed. Review the preview and obtain explicit approval before an import.
Private historical results and the proposed procedure are kept locally in the
Git-excluded `step7-migration-review/` directory.

### Simplified receipt reward status updates

Open a receipt, select **Rewards**, then **Update status** (owner access required).
Register Rewards offer **Expected / Printed at register / Rejected**. Provider
account rewards offer **Expected / Added to program account / Rejected**.
Legacy statuses stay visible and unchanged until an explicit supported update.

To confirm receipt, re-enter the recorded amount, select its original unit, and
confirm that it was received. Save retains the earning UUID, expected amount,
original quantities/valuations and earlier history, creating a new receipt
revision. This workflow cannot correct amounts or process external cash payouts.
Rejection allows an optional reason and retains recorded amounts; rejected
benefits do not increase earned savings or balances. Printing RR does not redeem
it or deposit Walgreens Cash. APPROVED is excluded from newly received savings.
Requests use revision protection and stable retry keys; refresh after a conflict.
No new schema or historical import is required. Credit movements retain the
receipt purchase date; separate credit/payout dates need a future workflow.

Reward-status verification (2026-10-19): 494 Java tests passed with no failures,
errors or skips; 19 frontend tests and six desktop/mobile browser checks passed.
TypeScript, lint and the production build passed. Browser tests used generated
receipts in a disposable PostgreSQL database. The historical-copy projection
remains $243.15 newly earned; no historical statuses were rewritten or imported.


