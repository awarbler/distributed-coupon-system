# API deployment and backup

Build with Java 21: `mvn clean package`.

Configure `COUPON_POSTGRES_URL`, `COUPON_POSTGRES_USER`,
`COUPON_POSTGRES_PASSWORD`, `COUPON_API_TOKEN`, and optionally
`COUPON_API_ADMIN_TOKEN` and `COUPON_API_PORT` (default 8080).
Then run:

```sh
java -cp 'target/classes:target/dependency/*' coupons.api.CouponApiServer
```

Startup applies additive database migrations V1–V6 to the explicitly configured
database. It does not open or migrate `.coupon-data`. The existing CLI remains
`java -cp 'target/classes:target/dependency/*' coupons.ReceiptEntryApp` and uses
local storage. The API and CLI currently have separate data stores.

Use secret injection or a private environment file outside version control. Do
not put real tokens/passwords in tracked scripts or shell command arguments.
Expose the loopback listener only through a trusted TLS proxy. This server is
single-owner, not a multi-user authorization system. The migration runner remains
simple resource execution; checksum validation and a formal migration runner are
still future work. A migration-role account and a separate restricted runtime
role need deployment work before production hosting.

## Backup and restore

Use PostgreSQL client tools matching the deployment. Supply credentials through a
protected password file or secret manager. Set PGHOST, PGPORT, PGUSER and PGDATABASE
for the intended database. Stop application writes for a coordinated recovery.

```sh
pg_dump --format=custom --file=/secure/backup/coupons.dump "$PGDATABASE"
createdb coupons_restore_check
pg_restore --exit-on-error --no-owner --dbname=coupons_restore_check /secure/backup/coupons.dump
```

Run restore checks against the separate restored database, never over the live
one. Verify schema_version, receipt snapshots/evidence/history, RR reservations,
provider observations, plans, coupons, and API retry outcomes. Preserve the retry
journal with domain data. A backup alone is not a verified recovery procedure;
automated restore drills are not implemented here.

## Mobile integration limits

Clients can read receipts/coupons and edit plan metadata with bearer tokens,
revision headers and stable idempotency keys. Handle 409 by retrieving current
state; uncertain requests require reconciliation rather than an automatic new
key. A native app, login/token lifecycle, offline synchronization, purchase-line
editing endpoints and receipt upload remain unimplemented.


## Revision 1.15 — consolidated guide sources (2026-10-24)

This is the single canonical guide for this purpose. Historical text below preserves provenance; it does not override newer endpoint/authentication or deployment behavior. Current API identity comes from authenticated tokens, not a caller-supplied X-Actor; PostgreSQL retry journaling supersedes the early in-process-only description. Current migrations are V1–V9.

### Incorporated historical source: docs/archive/operations/DEPLOYMENT-BACKUP 2.md

# API deployment and backup

Run the API entry point with the same Java 21 runtime as the CLI:

```text
COUPON_POSTGRES_URL=jdbc:postgresql://db/coupons \
COUPON_POSTGRES_USER=coupons COUPON_POSTGRES_PASSWORD='...' \
COUPON_API_TOKEN='...' COUPON_API_PORT=8080 \
java -cp target/classes:target/dependency/* coupons.api.CouponApiServer
```

Keep tokens in the deployment secret store, never in source control. Put TLS
in a trusted reverse proxy or load balancer; `HttpServer` is deliberately a
small application foundation, not a TLS termination or production gateway.
Restrict database network access to the API and backup jobs.

Back up PostgreSQL with the standard `pg_dump`/`pg_restore` tools and test
restores regularly. Include the migration version table and all receipt,
history, evidence, plan, and coupon tables. The local `.coupon-data` directory
is not read, modified, uploaded, or used by this API.

The CLI remains the supported local workflow. Start the API separately only
when remote clients need the documented contract; no additional microservice
is required.

