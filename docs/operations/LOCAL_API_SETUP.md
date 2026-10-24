# Start your Grocery Intelligence Platform

Your project folder is:

```text
/Users/anitawoodford/Desktop/SCHOOL/ECE382N/MyCode/distributed-coupon-system
```

Use two terminal tabs: one for the Java backend and one for the React frontend.
Run each command separately. Leave both programs running while using the website.

## 1. Start Docker Desktop

Open Docker Desktop and wait until it is running.

## 2. Start your existing PostgreSQL container

In your first terminal:

```sh
docker start distributed-coupon-postgres
```

Check readiness:

```sh
docker exec distributed-coupon-postgres pg_isready -U coupon_user -d distributed_coupons
```

Continue when it says `accepting connections`. Do not create another database,
remove containers or volumes, or use this persistent database for automated tests.

## 3. Start the Java backend — terminal 1

If this terminal is already running Java, press Control + C first.

Go to the project folder:

```sh
cd '/Users/anitawoodford/Desktop/SCHOOL/ECE382N/MyCode/distributed-coupon-system'
```

Start the backend using your saved private settings:

```sh
./start-local-api.sh
```

Expected message:

```text
Grocery Intelligence Platform API listening on 127.0.0.1:8080
```

Leave this terminal running. Do not enter more shell commands in it while Java
is running. Startup performs existing schema migration checks; it does not import
local receipt files. Your CLI receipts are separate from PostgreSQL records.

## 4. Copy your access token — terminal 2

Open a NEW terminal tab. Go to the project folder, even if your prompt currently
says `frontend`:

```sh
cd '/Users/anitawoodford/Desktop/SCHOOL/ECE382N/MyCode/distributed-coupon-system'
```

Load the saved settings:

```sh
source ./.local-api.env
```

Copy your owner token to the Mac clipboard:

```sh
printf '%s' "$COUPON_API_ADMIN_TOKEN" | pbcopy
```

This command normally prints nothing. It copies the token so you can paste it.
The admin token allows saving changes. With this saved configuration,
COUPON_API_TOKEN is the read-only token.

## 5. Start the frontend — terminal 2

From the project folder:

```sh
cd frontend
```

Then:

```sh
npm run dev
```

Expected address:

```text
http://127.0.0.1:5173/
```

Leave this terminal running too. If Vite chooses a different port because 5173
is occupied, use the Local address printed by Vite.

## 6. Open the website

1. Open http://127.0.0.1:5173 (or the Local address printed by Vite).
2. Click the API access token field.
3. Paste with Command + V.
4. Click Open my workspace.

The browser keeps the token in memory only. After a reload or disconnect, you
may need to copy and paste it again. Reuse the saved token; do not generate a new
one each time.

### Browse receipts and read totals

Open Receipts. Search by store, receipt number or date; receipt numbers work with
or without hyphens. Five results display per page; use Previous/Next for the rest.
Choose Purchase day or Month, optionally Week of month (days 1–7, 8–14, 15–21,
22–28, or 29–month end). Sort newest/oldest by purchase date and time.

Actual out of pocket means cash/card only, excluding WC, RR used and gift cards.
Total saved is separate and includes confirmed valued benefits under the existing
Java formula. It appears in the list, each receipt's Totals tab, and summaries.
Period totals include every active receipt in that period across pages;
search/status filters affect only the table. Missing information stays visible.
See [Receipt totals and browsing](RECEIPT_TOTALS.md) for the worked example and
legacy-difference explanation. Restart the rebuilt API and refresh the browser
to load the new period-summary fields; this feature needs no database migration.

## 7. Stop when finished

Press Control + C in the frontend terminal and in the backend terminal.
Stopping these programs does not delete saved records. PostgreSQL can stay
running. No receipt migration is part of these instructions.

## Where your settings are saved

The hidden file `.local-api.env` in the PROJECT ROOT contains your database
settings and access tokens. It is excluded from Git and readable only by your
Mac user. Never share it, paste its contents into chat, or commit it.

The database password was copied from the Docker setup command you previously
supplied. If you changed that password afterward, update the saved value to
match. Editing this file does not change PostgreSQL's password.

Backend: http://127.0.0.1:8080
Health check: http://127.0.0.1:8080/health
Frontend default: http://127.0.0.1:5173
Database: distributed_coupons
Database user: coupon_user
Database address: 127.0.0.1:5432

## Fix: Invalid bearer token

This usually means the browser token does not match the token loaded by the
running Java backend.

1. Stop the Java backend with Control + C.
2. In that terminal, return to the project folder and run:

```sh
./start-local-api.sh
```

3. In a separate terminal, return to the project folder and run:

```sh
source ./.local-api.env
```

```sh
printf '%s' "$COUPON_API_ADMIN_TOKEN" | pbcopy
```

4. Refresh the website, clear its token field, paste with Command + V, and connect.
5. If the backend says Address already in use, another API process is running.
   Stop the old API in its terminal before restarting. Do not stop unrelated Java
   programs or change tokens repeatedly.

## Fix: zsh bad pattern / strange paste characters

If a command begins with `^[[200~` or ends with an extra `~`:

1. Press Control + C to clear the command.
2. Manually type the command without those extra characters.
3. Press Return. Paste only one command at a time.

## Fix: cd frontend says no such file or directory

You may already be inside frontend. Return to the project root first:

```sh
cd '/Users/anitawoodford/Desktop/SCHOOL/ECE382N/MyCode/distributed-coupon-system'
```

Then run `cd frontend` once.

## Fix: settings file not found / database environment missing

The settings file is in the project root, not frontend. Return to the project
folder using the full cd command above. Start Java with `./start-local-api.sh`
so it loads the saved settings. A newly opened terminal does not automatically
inherit exports from another terminal.

## Fix: missing compiled classes or dependencies

Only when a build is needed, stop Java and run from the project root:

```sh
mvn -q -DskipTests package dependency:copy-dependencies
```

Then restart with `./start-local-api.sh`.
If frontend dependencies are missing, run `npm ci` inside frontend before
`npm run dev`.

## Fix: database connection or password error

Check Docker Desktop and repeat the PostgreSQL readiness command in step 2.
If authentication fails, the password in `.local-api.env` must match the existing
database's password. Do not delete the database or volume to fix this.

## Using a phone later

These localhost instructions are for your Mac. Phone access and PWA installation
need a separately configured trusted HTTPS deployment. The Mac's 127.0.0.1
address cannot be used from a phone to reach the Mac.


### October 23: Safari month selector and shopping weeks

The receipt Month control is now a dropdown of full month names and years,
including all twelve months for years represented by receipts/the workspace date.
Choose October 2026 rather than typing Oct. This avoids Safari treating an
unsupported month input as unrestricted text and producing an invalid period key.
Period totals and their heading are centered in the card.

The week-of-month selector is removed from the receipt page. Existing fixed-week
calculation helpers and Java week summaries remain available internally for future
reports; no records or report data were removed. Day/month controls remain visible.

Shopping plans show their Sunday–Saturday week range. The owning month is the
month of the starting Sunday: October 1, 2026 belongs to September 27–October 3,
owned by September. October 4 begins October's first shopping week. Java also
supplies shoppingWeek:YYYY-MM-DD summaries keyed by that Sunday for future reports,
including cross-month/year receipts. Existing legacy CLI report conventions and
fixed week keys are preserved. A selectable shopping-week reporting UI is deferred.


## Saved receipt editing, deletion and restoration — October 23, 2026

Owner access can open any active receipt and choose Edit receipt or Delete receipt.
Read-only connections display disabled actions. An existing deleted receipt can
be opened using the Deleted/All status filter and restored before editing.

### Correct a saved receipt

1. Open the receipt and choose Edit receipt.
2. Correct retailer, receipt number, purchase date/time/channel, printed-total basis,
   payment completeness, product names/SKUs/prices/quantities, ordinary discounts,
   tax, printed total, or payment types/amounts/notes. Products, payments and ordinary
   discounts may be added/removed where existing reference rules permit it. Newly
   added ordinary discounts are receipt-wide; existing discount links are retained.
3. Enter a reason, then Review updated totals. Java validates the candidate and
   recalculates its financial view without saving.
4. Review Actual OOP, benefits, savings and any differences. Confirm review, then
   Save receipt changes. This advances the same receipt ID by one revision.
5. The details and workspace refresh. Earlier values remain in History.

Unedited observations, footer values, evidence, earnings, redemption IDs, coupon
terms and payment account names are preserved from the saved snapshot. Reward
statuses and product associations remain in the existing Rewards workflows.
This editor does not replace evidence or independently reverse issued-instrument
redemptions. Removing a referenced product/payment or changing a protected
financial event may be rejected by existing repository rules; do not work around
that by creating a duplicate receipt. Review the linked records first.

### Delete or restore

Choose Delete receipt, enter a reason, explicitly confirm deletion, then Confirm
delete. This is a soft deletion: original contents, evidence, history and reserved
reward redemptions remain. The receipt is excluded from active financial reports.
Dependencies can prevent deletion and are reported rather than silently removed.
For restoration, find the deleted receipt via its status filter (or search), open
it, choose Restore receipt, enter a reason, and confirm. Restoration re-includes it
in active reports without creating a second purchase.

### Concurrency, retries and implementation

Writes require owner authentication, If-Match-Revision and Idempotency-Key.
POST /receipts/{id}/edit-preview validates fields and returns a revision-bound
reviewToken plus financial totals. PUT /receipts/{id} requires that token and
acknowledges differences. DELETE /receipts/{id} and POST /receipts/{id}/restore
require reason and confirmed=true. Existing persistent API request journaling and
repository transactions preserve retry behavior and audit history. No new schema
migration is needed. Preview is validation/review, not a successful persistence
check against every linked record; final repository checks still apply atomically.

An uncertain write retains its exact payload/key/revision for Retry same update or
Retry same action. After a revision conflict close the form, refresh, and review
the latest receipt rather than overwriting it. Restart the rebuilt Java API and
refresh the frontend to expose these routes.

Verification: 558 Java tests, 518 passed, 40 database-dependent skipped, zero
failures/errors; 56 frontend tests passed; typecheck/build/lint passed. Four
Chromium desktop/mobile checks passed for browsing and edit/delete/restore with
synthetic network fixtures. New HTTP tests use in-memory repositories. PostgreSQL
integration for these endpoints was not run without a disposable test database;
no personal receipt records were modified during verification.


## Receipt presentation and pricing clarification — October 23, 2026

The receipt-results card no longer repeats a heading such as “16 receipts”.
Pagination remains the count: “Showing 1–5 of 16”, with Previous/Next. The table
retains its column headings and accessible Receipt results region.

Benefit-adjusted cash spending is cash/card paid minus confirmed valued benefits;
it does not change Actual OOP. It is not an additional payment or an actual payout.
For $5.96 paid and $8.74 confirmed benefits the arithmetic is -$2.78, but this
strict financial projection stays unknown until payment entry is explicitly COMPLETE,
all payment roles are resolved, and benefit valuations are available. It can remain
unknown while the legacy Total saved calculation is available because those views
use different completeness rules. The visible explanation now states these reasons.
Do not mark payment entry complete merely to make this result appear.

Current product calculation: sale unit price times quantity gives the merchandise
line total. Regular unit price times quantity supplies the regular-value baseline.
For regular $4.79, actual sale unit $4.00 and quantity 2: regular line $9.58,
sale line $8.00, built-in sale reduction $1.58. Separately entered ordinary
coupon/discount amounts are subtracted after sale merchandise; tax is added and
reward deductions/funding are reconciled in their existing roles. Do not record
the same $1.58 promotion again when the sale price already includes it.

Requested future entry preference: enter regular price then a promotion reduction
and derive the sale price. Before implementing, establish whether the reduction
is per unit ($0.79 here) or whole line ($1.58 here). This entry change remains
pending user clarification; no new promotion arithmetic or receipt rewrite was
introduced in this presentation update.


## Receipt pricing terminology correction — October 23, 2026

The UI now says **Unit price before listed discounts**, replacing **Sale unit
price** in receipt entry/editing. This is a label correction, not a change to stored
prices or arithmetic. The totals display says **Merchandise before listed discounts**
for the existing saleMerchandise field. A separately entered SALE_PROMOTION is an
additional deduction from those prices, applied with the other ordinary discounts.
It is a total deduction for the selected products/quantities, not the final price
of one product. Existing receipt edits retain discount classifications and links.

Two valid ways to record the same example (quantity 2):

| Method | Regular unit baseline | Unit price before listed discounts | Separate sale-promotion deduction | Result before other discounts/tax |
|---|---:|---:|---:|---:|
| Regular price with listed promotion | $4.79 | $4.79 | $1.58 | $8.00 |
| Price already includes that promotion | $4.79 | $4.00 | None for that same promotion | $8.00 |

If that same promotion is both included in $4.00 and deducted again as $1.58, the
result would be $6.42. That is only an error if it repeats the same reduction;
a genuinely additional promotion can legitimately reduce the price further.
No saved receipt has been reinterpreted or changed. The earlier pending per-unit
versus whole-line question is superseded: the existing separate-discount workflow
already records the total deduction. No new promotion-entry calculation is needed
for this terminology fix.


## Regular-price and item-attached discount entry — October 23, 2026

This supersedes the earlier Unit price before listed discounts UI wording.
Receipt creation/editing now shows a single Regular price field per product, its
quantity, and Add discount to product. A discount has a description, type and total
amount for that product line. For regular $4.79 × 2 with attached sale promotion
$1.58, Java calculates $8.00 before other discounts/tax. The separate starting-price
field is no longer shown. Shared or receipt-wide discounts retain their distinct
section; they are not copied onto every product or allocated without a rule.

Creation sends the regular price as both regular baseline and merchandise unit
operand, with each attached discount recorded once under its selected line ID.
Item promotions are SALE_PROMOTION; manufacturer/store coupons keep their types.
No financial arithmetic was moved to the browser.

GET /receipts/{id} supplies editDiscounts as an editable draft projection. For a
saved line whose known regular price exceeds its old merchandise unit operand,
Java prepares an explicit attached Previously recorded price reduction equal to
(regular minus old unit operand) × quantity. Its deterministic draft identity and
classification preserve the original checkout/savings when regular price becomes
the merchandise operand. Existing separately recorded discounts remain separate.
Nothing is persisted until preview and an explicitly approved save. Review these
rows: removing one intentionally changes the proposed cost. This does not claim
that earlier recorded deductions were correct or automatically repair duplicates.

Correction items can omit saleUnitPrice: a supplied regular price is then the
merchandise operand. Existing unknown regular prices preserve their old operand
when left unknown; new products require a regular price. A preservePriceLineIds
projection identifies unusual historical lines whose regular price is below their
recorded operand; their pricing is preserved rather than converted into an invalid
negative promotion. Older API responses without editDiscounts request a restart
before regular-price editing is enabled.

Correction discounts now support kind, lineItemIds, wholeReceipt and
qualifyingQuantities in addition to their stable IDs, labels and total amounts.
Existing discount requirements and unedited observations/references/evidence are
retained. The editor converts quantity lexemes to validated whole numbers at the
API boundary; no selected discount is duplicated in a separate editor.

No schema migration or historical batch rewrite is required. Internal snapshot
saleUnitPrice and creation unitPrice names remain compatible with older callers.

## Account/receipt contract update

Restart ./start-local-api.sh after these Java changes, then refresh/reconnect the browser. The UI uses GET /accounts/activity and POST /accounts/openings plus optional printedFooter in receipt commands. Existing storage is reused; no manual migration or rewrite of historical receipt data is needed. Verification uses synthetic browser fixtures and does not edit personal receipt data.


## Revision 1.7 — receipt RR correction and matching

Restart the Java API to enable /receipts/{id}/rr-matches and the new correction input/validation response. Refresh the frontend. A running older backend may reject new correction fields; no schema migration or personal-data rewrite is required.


## Revision 1.8 — RR without an earning receipt

Restart the updated Java API and refresh the browser to enable POST /receipts/{id}/rr-matches/unknown-source. Existing storage supports nullable source receipt/dates; no migration or personal-data rewrite is required.


## Revision 1.10 — independent Rewards inventory

Restart the Java API and refresh the frontend for GET/POST /rewards/register-rewards and confirmUnknownHistory in receipt matching. Existing storage is reused; no migration or personal-data rewrite is required.


## Revision 1.11 — visible recorded receipt calculations

Restart the updated Java API and refresh the browser to load calculationChecks for existing receipts. No migration, data backfill or manual receipt edit is needed to display the calculations. Confirmation of actual receipt facts remains a separate owner correction.


## Revision 1.13 — reuse existing files and align CLI rebate display

Restart ./start-local-api.sh after the internal Java helper consolidation, then refresh the browser. No migration or personal-data rewrite. The UI consumes the existing savings.paidAfterRebates field and displays Total Paid after rebates; source-file relocation changes no HTTP contract.


## Documentation revision 1.16 — material relocated from the root README (2026-10-24)

The following former README sections preserve prior operating guidance/change history. They are historical source material; current procedures above and the current API reference take precedence where earlier feature limitations or migration versions differ.

## Start the PostgreSQL API

The optional API uses PostgreSQL; the existing receipt CLI still uses local storage.
No existing receipts are migrated automatically.

1. Run `mvn clean package` with Java 21.
2. Configure `COUPON_POSTGRES_URL`, `COUPON_POSTGRES_USER`, and
   `COUPON_POSTGRES_PASSWORD` for your application database.
3. Set `COUPON_API_TOKEN`. Optionally set a different `COUPON_API_ADMIN_TOKEN`
   to restrict the first token to read access. Keep credentials outside Git.
4. Start:

```sh
java -cp 'target/classes:target/dependency/*' coupons.api.CouponApiServer
```

The server listens on `127.0.0.1:8080` (override with `COUPON_API_PORT`).
`curl http://127.0.0.1:8080/health` checks process liveness. Startup applies
additive migrations V1–V6 to the configured database, never `.coupon-data`.

Authenticated routes provide receipt lookup, shopping-plan creation/read/metadata
updates, clipped-coupon lookup, and redemption. Writes require `Idempotency-Key`;
updates/redemptions also require `If-Match-Revision`. Successful retry responses
survive API restarts. Interrupted outcomes are reserved for reconciliation rather
than replayed blindly. The API is single-owner with read/write roles, not a shared
multi-user platform. Planned-purchase editing, coupon creation, receipt upload,
native mobile UI, and offline synchronization are not HTTP features yet.

Local reference notes: API.md and DEPLOYMENT-BACKUP.md. These Markdown notes remain
local under the existing Git exclusion policy; README.md is the tracked guide.


## Web and mobile browser interface (Step 6)

The responsive React + TypeScript application is in `frontend/`. It uses the
Java API and PostgreSQL, never `.coupon-data`. Financial calculations remain in
Java; decimal amounts retain their original precision across the API boundary.

1. Configure the existing backend environment (`COUPON_POSTGRES_URL`,
   `COUPON_POSTGRES_USER`, `COUPON_POSTGRES_PASSWORD`, `COUPON_API_TOKEN`, and
   optionally a separate `COUPON_API_ADMIN_TOKEN`). Keep secrets outside Git.
2. From the project root, build and start the API:

   ```sh
   mvn -q -DskipTests package dependency:copy-dependencies
   java -cp 'target/classes:target/dependency/*' coupons.api.CouponApiServer
   ```

   Backend startup applies the existing additive migrations. It does not import
   local receipts. Choose your database deliberately; automated tests require a
   separate disposable database.
3. In another terminal:

   ```sh
   cd frontend
   npm ci
   npm run dev
   ```

4. Open `http://127.0.0.1:5173` and enter your API token. The default development
   proxy connects to `http://127.0.0.1:8080`; set `COUPON_API_TARGET` before
   starting Vite to use another backend address. Tokens stay in memory and must
   be entered again after reloading. A read-only token cannot submit changes.

Available screens: dashboard, receipts, shopping plans, coupons, deals and
promotion contributions, rewards/accounts, inventory, and rebates. Receipt
views include products, deductions, payments, rewards, original observations,
reported/calculated discrepancies, evidence downloads, and revision history.
Savings summaries cover day, Sunday–Saturday week, month, and all recorded data.
Deleted receipts remain distinguishable and are excluded from active summaries.
Reported provider observations remain separate from calculated balances.

Shopping plans support creation and metadata updates, plus adding/updating
product lines, quantities, decimal target prices, advertised-deal references,
and coupon references. Java calculates merchandise estimates only; these are
not checkout quotes or eligibility approvals. Coupon redemption uses the
existing duplicate-use validation and does not invent a receipt deduction.
Writes retain idempotency keys during uncertain retries and send revision
preconditions; conflicts require reviewing the current server state.

Inventory is intentionally read-only: the current HTTP authorization contract
cannot represent independent inventory approval. No requester is silently made
an approver. General editing of saved receipts, coupon creation/removal, inventory changes,
provider-ledger edits/reconciliation, rebate submission/payout, automatic offer
qualification, and shopping-item deletion are not implemented in this frontend.
Unavailable workflows are labelled rather than represented by working buttons.
Catalog names are not invented where only product IDs are available.

### Build, PWA, and checks

```sh
cd frontend
npm run typecheck
npm run lint
npm test
npm run build
npm run preview
```

The production build includes an installable manifest, icons, and service worker.
Only public application-shell assets are cached. API responses, evidence,
tokens, and private financial records are not cached. Offline writes and draft
synchronization are not supported. Installation on a phone requires a trusted
HTTPS deployment; localhost preview alone does not expose the app to a phone.
Serve `frontend/dist` through a trusted same-origin HTTPS reverse proxy forwarding
`/api/` to the loopback Java service (strip `/api`). Vite's development/preview
proxy is not a production deployment configuration. No native mobile app or
multi-user login is included.

Browser verification: install Playwright Chromium with
`npx playwright install chromium`, then run `npm run test:e2e` after building.
The real-API browser scenarios run only when `COUPON_BROWSER_TEST_TOKEN` and
`COUPON_API_TARGET` explicitly select an API backed by a **disposable** database.
They create synthetic shopping plans; never point them at personal data.
The remaining browser scenarios use synthetic network fixtures. Browser checks
cover desktop and mobile Chromium emulation, not physical-device installation.

Step 6 verification (2026-10-19): 481 Java tests passed with no failures,
errors, or skips against an isolated PostgreSQL 16 database; 13 frontend tests
passed; TypeScript, lint, and production build passed; four desktop/mobile
Chromium browser scenarios passed, including real API plan/item writes and
private-cache exclusions/offline shell reload. The disposable database was
removed afterward. Physical phone installation and a production TLS deployment
have not been verified. The CLI and personal receipt storage were not changed.




Frontend maintenance reference (revision 1.16): npm scripts include dev, build, typecheck, lint, test and test:e2e. Playwright requires the matching browser binaries. The default development API proxy targets localhost:8080 and can be overridden with COUPON_API_TARGET. Use the current frontend/package.json for script definitions. The obsolete Vite template README was replaced with the application frontend overview.
