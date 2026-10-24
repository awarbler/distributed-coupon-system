# distributed-coupon-system

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


## Step 8 — create receipts in the web application

Use **Receipts → Add receipt** with an owner/write token. Enter store/date,
products and quantities, actual sale unit prices, tax and the reported register
total. Regular prices may remain unknown. Record each actual discount once;
choose an item or the whole receipt. Do not repeat a sale reduction already
included in the sale unit price.

Record payments separately from new earnings. RR use records an unmatched prior
coupon deduction; Walgreens Cash use is a separate redemption payment. Expected
rewards remain uncredited; printed RR and account credits require explicit amount
and unit confirmation. Point valuations are saved explicitly, never inferred.

**Review calculated totals** calls Java without saving. Verify differences and
partial-value warnings, then **Save receipt**. Saving writes one PostgreSQL
receipt plus evidence and audit history, and opens the saved receipt. A stale
preview or existing receipt UUID cannot overwrite saved data. Repeating an
uncertain save retains the same payload/key; refresh to reconcile before starting
another entry. No new transaction is written to local CLI storage.

Optional JPEG/PNG/WebP/PDF evidence is preserved as original bytes (four files,
5 MiB each, 10 MiB total). There is no OCR in this manual form. Drafts exist only
in memory, not offline storage. A zero-due receipt needs no invented zero payment.
Unknown regular prices, payments, or point valuations remain partial/unknown.

Scope limits: saved-receipt general editing, named-account redemption selection,
matching an RR use to its original coupon, external payouts, and duplicate overrides are not provided here. Possible
existing receipts must be reviewed before another entry is saved. Inventory,
future clipped coupons, advertised deals and standalone rebate claims are not
created automatically from a receipt. The legacy CLI remains separate/local and
was not restructured; use the web application for new authoritative transactions.

Restart the Java API after rebuilding and refresh the browser to use the new
operations. Step 8 verification: 502 Java tests, 25 frontend tests and eight
desktop/mobile browser checks passed; TypeScript, lint and production build passed.
Write tests used disposable PostgreSQL only. No historical migration was rerun.


## Step 9 — plans, coupons, promotions and receipt rebates

- **Shopping plans → Add item:** enter regular price, optional manual sale price and quantity. **Add a deal** saves an independent source-backed offer and links it without clearing the item. Select the same deal on every qualifying planned item. Java pools multi-buy quantities across those items and separates estimated register cost from future rewards.
- **Coupons → Add coupon**, or **Add coupon** inside a planned item: use the same form for paper, digital, printable, ordinary register-printed and other coupons. Physical copies have separate redeemable UUIDs. Linking a copy to a plan does not redeem it. Dollar, percentage and free-item estimates enforce recorded SKU, dates, quantity, minimum spending and copy limits.
- **Receipts → Add receipt:** use quantity or **Add another line for this product**. Matching SKU/UPC values group units without combining or rewriting the original lines. The shared product selector supports multiple lines and selected quantities for deductions, promotions and rebates.
- **Receipt → Rewards → Add rebate or reward:** choose/type the issuer, select qualifying lines, record an optional spend/quantity requirement, then review and save. This also works after saving a receipt. **Edit product associations** changes only its links. Submission references, supporting evidence and PayPal/Venmo/bank/gift-card destination notes remain separate from the issuing provider. Receipt-linked rebates also appear under **Rebates**.

**Accounting:** the existing Java receipt calculator, reward confirmation rules and repository validators remain authoritative. Expected rewards are not available credits, opening balances are not earnings, and printing RR is separate from redeeming it. Selecting a payout destination does not create a payout or a second earning. Post-save additions create a guarded receipt revision with unchanged register amounts and earlier history. Point quantities retain their original precision; USD values require an explicit saved valuation.

**Qualification limits:** estimates exclude tax. Text-only product, loyalty and exclusion conditions require review. Unallocated discounts across eligible/ineligible items or partial quantities produce **Cannot verify**, not a guessed allocation. Buy/get pricing discounts the cheapest eligible units only when that rule is explicitly recorded; otherwise manual prices remain and allocation is flagged. This is not retailer eligibility verification. External payout execution, standalone claim lifecycle management, offline synchronization and general receipt editing remain outside this step. Drafts are still in memory only.

**Database:** additive `V7__offer_pricing.sql` adds optional structured pricing to current/history deal rows and a nullable unique manual-entry fingerprint. Coupon terms, selected quantities and earning submission context use existing versioned payloads. Existing snapshots are read compatibly; no historical rows need rewriting. V7 is tested only in disposable PostgreSQL in this development step. Do not restart against permanent data to apply it until that schema change is approved. No historical import or local-data fallback is involved.

<details>
<summary>Step 9 implementation files and purposes</summary>

New Java domain files:

- `DealTerms.java`: structured sale, multi-buy, buy/get and reward offer terms.
- `CouponTerms.java`: coupon source, qualifications, discount type, copy group and evidence context.
- `EarningDetails.java`: selected quantities and submission/destination context, without a payout event.
- `ShoppingEstimate.java`: precise Java plan estimates, pooled offers and coupon qualifications.
- `PromotionQualification.java`: selected receipt quantity/spend reviews using recorded coupon basis.

New API files:

- `DealInput.java` and `CouponInput.java`: validated independent creation inputs, including manual provenance and physical copies.
- `EarningInput.java`: one shared parser for receipt-entry and post-save earnings.
- `ReceiptEarningRules.java`: duplicate earning checks without rewriting compatible legacy records.
- `ReceiptEarningAddition.java`: validated, audited additions through the existing receipt repository.

Existing Java files extended:

- `AdvertisedDeal.java`, `PlannedPurchase.java`, `Coupon.java`, `CouponApplied.java`, `ReceiptObservations.java`: optional structured terms/prices/quantity context with compatible existing constructors.
- `PostgresDealRepository.java`: atomic manual source/deal creation, immutable pricing revisions and duplicate fingerprints.
- `PostgresClippedCouponRepository.java`: atomic physical-copy creation and guarded date/qualification/copy-limit redemption.
- `PostgresPlatformRepository.java`: plan pricing, grouped units, line totals, promotion reviews and receipt-linked rebate read models.
- `ReceiptObservationValidator.java`: validates selected quantities and existing ownership/reference rules.
- `PostgresSchema.java`: registers V7; earlier migrations remain unchanged.
- `CouponApiService.java`: owner-only creation/addition/association routes with existing idempotency and revision protection.
- `ReceiptInput.java`, `ReceiptEarningStatusUpdate.java`, `Json.java`: reuse earning parsing, preserve new context on status changes and expose selected quantities.

New React files:

- `components/EvidenceField.tsx`: shared bounded supporting-file input.
- `features/shopping/DealForm.tsx`, `CouponForm.tsx`: independent inline deal creation and one reusable coupon form.
- `features/receipts/ReceiptLineSelector.tsx`: shared stable-line and quantity selection.
- `features/receipts/RewardFields.tsx`: shared provider, status, qualification and submission fields.
- `features/receipts/ReceiptRewardForm.tsx`, `EarningLinksForm.tsx`: reviewed post-save additions and guarded association edits.

Existing React files extended:

- `App.tsx`, `types/platform.ts`: connect the new workflows and typed API data.
- `features/shopping/EditDialog.tsx`: preserve item form state during inline creation and link new records.
- `features/receipts/ReceiptCreate.tsx`, `ReceiptDetail.tsx`, `rewardStatus.ts`: shared reward fields, grouping, qualification reviews, evidence and post-save actions.
- `pages/WorkspacePages.tsx`, `App.css`: estimates, coupon entry/status, receipt-linked rebates and responsive selectors.

Tests:

- New `ShoppingEstimateTest.java`, `PromotionQualificationTest.java`, `EditDialog.test.tsx`, `ReceiptRewardForm.test.tsx` cover pricing, qualification, inline-state preservation and exact rebate associations.
- Extended `PostgresApiIntegrationTest.java`, `ReceiptCreate.test.tsx`, `tests/browser.spec.ts` cover real persistence, retries, rollback, authorization, evidence, and desktop/mobile workflows. Attachment tests wait for file reading before submission.

</details>


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

Receipt Totals now separates sale promotions, manufacturer coupons, store deductions
and unclassified discounts, with a combined subtotal. Classifications come from
saved kinds rather than guessed labels; calculations and saved amounts are unchanged.

Frontend visual specifications are maintained in the
[Frontend Design System & Component Style Guide](docs/reference/FRONTEND-DESIGN-SYSTEM.md).
Core tokens live in frontend/src/index.css; global controls in index.css
and component layouts in App.css.

## Receipt and reward account updates — October 23, 2026

Rewards & Accounts keeps the balance list and opens a dated account activity view from each account name. Receipt numbers open their records; View Reward expands existing RR earning/use connections without posting another movement. Add/Edit Receipt includes optional Printed Savings & Walgreens Cash observations. Payment completeness asks “Have you entered all payments shown on this receipt?” (Yes / Not finished / Not sure). Delete an accidental receipt-wide discount, or clear its description and amount; fully blank rows are omitted from the reviewed save. Restart the Java API and refresh the frontend to load both updated contracts. See [receipt and reward workflows](docs/reference/RECEIPT-REWARDS-WORKFLOWS.md).


## Revision 1.7 — receipt RR correction and matching

Receipt correction now supports separate RR-use rows, explicit conversion of misclassified discounts/payments, and saved-receipt matching to available unused RR. Printed Savings & Walgreens Cash is below payments and collapsible. See [workflow instructions](docs/reference/RECEIPT-REWARDS-WORKFLOWS.md#revision-17--recording-and-matching-rr-used).


## Revision 1.8 — RR without an earning receipt

Add RR without an earning receipt lets the owner record a standalone source coupon and match an already recorded RR use in one operation. Earning receipt, earned date and expiration may remain unknown. Amount comes from the existing use; no earning, payment, receipt or second deduction is created. A source note and confirmation that the coupon was used are required. Known conflicting dates, stale revisions and already matched uses are rejected. Both instrument and link persist under the shared receipt-state lock in one transaction with the existing API retry journal. Use Match Register Rewards Used → Add RR without an earning receipt → Save RR and match. Restart the API and refresh the browser.


## Revision 1.9 — Rewards points-account display

On the Rewards pages, only MyPoints, Fetch, P&G Brand (PGBRAND), Swagbucks (SWAGBUCK) and Merryfield show points information. Other account detail pages hide native-point balances, activity columns, point-valuation text, converted point quantities and the Original Points opening field, even if older records contain native units. Non-points opening forms use Balance Basis and require a dollar amount; new requests send points:null. The mixed account list shows a points column only when an allowed account is listed, with a dash for other accounts. This is Rewards presentation policy; receipt/CLI behavior, stored units and Java financial calculations remain intact.


## Revision 1.10 — independent Rewards inventory

The Rewards overview and Register Rewards detail page expose Add reward without a receipt. The owner can save an unused standalone Register Reward by description and positive amount, with optional earned/expiration dates and no receipt of any kind required. A coupon inventory table shows UNUSED, MATCHED or REDEEMED status. Saving inventory does not add a receipt earning, account credit, payment, deduction or immediate match. Later, an equal-value recorded receipt use can select that coupon. Standalone coupons with unknown earning date/time are offered with historyUnknown=true and require explicit confirmation they were available for that purchase; known future/expired/consumed sources remain excluded. Matched sources remain reserved. Restart the Java API and refresh the frontend to use the new inventory routes.


## Revision 1.11 — visible recorded receipt calculations

Receipt totals now show arithmetic from recorded entries even when historical printed-total basis or payment completeness is unknown. Unknown basis displays both before-funding and after-reward-use calculated totals, purchase differences, funding subtotals and payment differences. A known basis displays its scenario. Incomplete/unknown entry or unresolved roles are explicitly Provisional — recorded entries only. Zero arithmetic difference does not prove complete payment entry. Existing metadata, strict reconciliation status/differences, Actual OOP, benefit-adjusted spending rules, legacy figures and history are preserved; no receipts are automatically marked complete or verified. Restart the Java API and refresh the browser to load the computed checks for saved receipts.


## Revision 1.12 — simplified receipt totals display

Per user request, receipt totals/review no longer displays Calculated total on recorded basis, its before/after-reward alternatives, or the Receipt calculation checks table. Saved printed observations also hide Store Coupon Savings. Store coupon input remains available in Add/Edit Receipt; existing saved observations, API calculationChecks, Java formulas, strict validation, Actual OOP, Total Saved, legacy details and history remain unchanged. This supersedes revision 1.11 UI visibility only. Refresh the browser for this frontend-only display change.


## Revision 1.13 — reuse existing files and align CLI rebate display

Owner development constraint: edit and reuse existing files. Discuss any proposed new application, test or documentation file with the owner before creating it. This revision consolidates the earlier added helpers/components/tests into pre-existing files and does not add source or documentation files.

The visible receipt measure is Total Paid after rebates, sourced directly from savings.paidAfterRebates — the same SavingsReport value used by the CLI. Formula: recorded cash/card minus newly earned reward/rebate dollars, including saved USD point valuations. The frontend no longer uses the stricter benefitAdjustedCashSpending field for this spreadsheet measure; that compatibility field and its explicit-completeness semantics remain unchanged. Unknown historical payment-entry metadata alone does not hide the CLI value. Truly missing tender or dollar valuations still leave the CLI measure unknown, now explained as Cannot calculate yet. Actual OOP, reward use, Total Saved and stored facts are unchanged.

Current ownership: receipt create/review/shared printed fields and item discounts → ReceiptCreate.tsx; receipt editing/status/matching → ReceiptDetail.tsx; Rewards/account/standalone inventory and reward connection → WorkspacePages.tsx; browse/date/search helpers → api/format.ts; printed draft helpers → rewardStatus.ts; expanding textarea → components/ui.tsx; font/color/size tokens → index.css. AccountActivityView is nested in ProviderBalanceSummary.java; ReceiptRRMatching is nested in PostgresPlatformRepository.java; package-private correction/footer parsers share ReceiptInput.java. All earlier helper test cases remain in existing receipt/calculator/provider/repository test files.

### Receipt period reporting update — October 23, 2026

Receipts → Period totals displays Regular price including tax, Actual OOP, Total amount saved, Confirmed benefits, Total Paid after rebates, and Total % saved. Pending rebates are not shown or included. Existing Java CLI savings rules and saved points valuations apply; original receipt data and valuations are unchanged. Cash/card Actual OOP excludes rewards used and gift cards. Percent saved uses regular merchandise plus recorded tax as denominator; a zero/unknown denominator remains Not recorded. Unknown inputs retain existing null/completeness behavior.

Select Purchase day or Month, enter inclusive Start date and End date, or click Month to date (the current local calendar month through today). Selecting a day/month clears the range; entering a range clears day/month. Clear filters returns to All time. Totals cover all active latest receipt revisions across every page, regardless of text search or status filter. Search continues across dates/statuses; without search the receipt list follows the selected period. Date-range loading/errors never reuse the prior summary. Invalid or incomplete ranges require correction.

Read-only authenticated API: GET /receipts/summary?from=YYYY-MM-DD&to=YYYY-MM-DD returns the existing Savings shape. Both dates are required, start must not exceed end, and undated/deleted receipts are excluded. Range calculation runs in Java through SavingsReport.range and the existing savings projection; the browser performs no money aggregation. No database schema change.

Existing implementation files changed: src/main/java/coupons/SavingsReport.java; src/main/java/coupons/repository/PostgresPlatformRepository.java; src/main/java/coupons/api/CouponApiService.java; frontend/src/api/client.ts; frontend/src/pages/WorkspacePages.tsx. Existing regression files: src/test/java/coupons/SavingsReportTest.java; frontend/src/features/receipts/ReceiptCreate.test.tsx. No new files. Layout uses the existing centered three-column receipt-period-totals grid (six metrics in two rows), collapsing to one column on narrow screens; existing typography/control tokens remain unchanged.

Receipt product lookup: existing active receipt history populates workspace.productLookup with SKU, description, most recent regular price, purchase date and source receipt. Latest revisions are deduplicated before filtering; deleted receipts are excluded. SKU leading zeros are preserved, and conflicting descriptions for one SKU remain separate selectable choices. Add Receipt suggests up to eight prefix matches as SKU is typed. Use this product fills description/SKU/last regular price and quantity 1; no historical promotions, coupons or rewards are copied. The user reviews the current price and discounts. This is a read-only product reference, not an import into on-hand inventory; historical purchases do not prove remaining stock. Existing PostgresPlatformRepository.java, types/platform.ts, App.tsx, ReceiptCreate.tsx, ReceiptCreate.test.tsx and SavingsReportTest.java updated. No new files or database tables.

### Receipt total detail reports — October 24, 2026

Receipts period totals Regular price including tax, Actual OOP, Total amount saved and Total Paid after rebates are clickable. Each opens an existing-style modal with all active records in the selected day/month/inclusive range or all time, independent of receipt-list search/status/page. Reports support item/SKU/store/receipt/date/time/payment-type text search and date/time newest/oldest, store A–Z and item/payment-type A–Z sorting. Receipt links open source details. Regular price shows individual product regular extended values plus separate sales-tax rows. OOP shows individual cash/card tenders with period cash/card totals and matching cash/card subtotals; WC/RR/gift cards are excluded. Saved/net views show one contribution per receipt with its product descriptions. Period total remains unchanged by report search; Matching subtotal follows visible rows. Unknown contributions remain unknown.

Read-only authenticated GET /receipts/report?metric=regular|oop|saved|net&from=&to=&search=&sort= returns metric, existing Savings summary, rows, matchedTotal, matchedCash and matchedCard. Bounds are inclusive and optional; latest active revisions only. All money multiplication/aggregation uses Java BigDecimal and existing SavingsReport rules; the browser formats exact decimal strings. This introduces no saved records, schema or formula changes.

Existing files updated: src/main/java/coupons/repository/PostgresPlatformRepository.java; src/main/java/coupons/api/CouponApiService.java; frontend/src/api/client.ts; frontend/src/types/platform.ts; frontend/src/pages/WorkspacePages.tsx; frontend/src/App.css; src/test/java/coupons/SavingsReportTest.java; frontend/src/features/receipts/ReceiptCreate.test.tsx. No new files.


Clipped coupons and catalog cleanup: Deals & promotions and Coupons now expose permanent Delete actions with write access and revision checks. DELETE /deals/{id} and DELETE /clipped-coupons/{id} remove the catalog record and its own revision history, detach current plan associations while retaining product prices, and remove deal source artifacts only when not shared. Normal plan revision history remains; no deletion backup is created. Redeemed coupons cannot be deleted. Receipt entry accepts clippedCouponIds and offers saved coupon selection per product and in shared/receipt-wide discounts. Selection creates one editable discount with the coupon UUID, description, type and buy requirement; use the shared selector for a coupon covering several products. Percentage/free-item deductions use the actual recorded receipt amount. Selecting does not consume a coupon; receipt creation and all selected coupon redemptions commit atomically in PostgreSQL. Expired, already-used, or ineligible coupons cause rollback. The Coupons page then shows Applied and the receipt connection. Do not manually repeat a selected deduction. Existing receipt corrections retain their separate workflow; editing a discount does not automatically release a previously redeemed coupon. No new schema migration or files are introduced.