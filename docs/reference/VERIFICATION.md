# Verification, scope and maintenance

## Revision history

| Version | Date | Changes | Verification |
|---|---|---|---|
| 1.0 | 2026-10-21 | Original reference snapshot | Static inspection |
| 1.1 | 2026-10-22 | Planning reward progress, unknown-value semantics and current UPC policy | Source updated; final checks not run |
| 1.2 | 2026-10-22 | Receipt evidence/reconciliation, distinct purchase/scan and partial benefit values | See VERIFICATION.md |

## Revision 1.2 — receipt evidence and reconciliation

Receipt revision 1.2 automated results (2026-10-22): `mvn -q clean test` — 552 tests, 0 failures, 0 errors, 40 skipped. `npm test -- --reporter=dot` from frontend — 43 passed in 7 files. `npm run build` — TypeScript project compilation and production build passed. `git diff --check` passed. The Java suite covers both earlier planning work and current receipt changes; the older revision 1.1 no-run statement below is retained as historical evidence, not current suite status.

Old five-field ReceiptMeta bytes were produced by an isolated compiled legacy record and read by the new model; explicit new fields round-tripped. Real loopback HTTP preview/save verifies $25.96 before funding, $20 WC, $5.96 card and preserved legacy $20 comparison. Expected error-path test logging is not a test failure. Forty skipped tests, including environment-gated persistence tests, do not establish live PostgreSQL compatibility. No manual browser entry, deployment, live database migration or data rewrite occurred. Scope includes clean Java tests, frontend tests, type/build checks, exact split-funding HTTP preview/save and old serialized metadata fixture. PostgreSQL tests requiring disposable configuration may skip; live database/deployment and manual browser entry are not claimed.


## Revision 1.1 — current behavior and status

Revision 1.1 implementation edits and test coverage were added on 2026-10-22. At the user's request, final sanity checks, tests, typechecking, builds, live API/browser scenarios and database compatibility checks were NOT run for this change. Do not reuse earlier passing counts as evidence for this revision.

Acceptance includes all store/date states and inclusive endpoints; quantity/spend below/exactly/above thresholds; quantity rewards without prices; unknown spend rewards; known partial total; sale-only no-reward message; opt-in strict UPC exclusions; preselected Add another item; review questions and retained warnings; unchanged checkout due/no account credits. Remaining tax/stacking/allocation decisions stay open. Final verification and execution record pending.



Original revision 1.0 inspection: October 21, 2026 (America/Chicago). Base Git commit: `fa3b8debc070cb5de990cbe9eb420fe1e56b7c06`. This reference describes the working tree, including pre-existing uncommitted checkout changes; it does not prove what is deployed online. No application tests were run for this documentation-only task.

## What was done

Read project structure, key domain/API/repository/frontend implementations, migration definitions and existing documentation locations. Generated a project file manifest with SHA-256 values, lexical declaration/import/model indexes and a test-scenario index. Wrote reviewed explanations for promotion and checkout behavior and its CLI/online differences.

No application source edits, database mutations, schema application, service startup, builds, test runs or deployment occurred. Existing uncommitted application work was left in place. This snapshot cannot certify production deployment, real retailer eligibility, runtime test success or exhaustive semantic correctness of every indexed file.

## Available verification commands — not run by this task

| Scope | Command | Notes |
|---|---|---|
| Java suite | mvn test | PostgreSQL integration tests may require disposable test environment configuration |
| Frontend checks | npm run typecheck | Run from frontend |
| Frontend unit tests | npm test | Run from frontend |
| Frontend lint | npm run lint | Run from frontend |
| Browser tests | npm run test:e2e | Run from frontend; explicitly select disposable API/database for real write scenarios |

Existing tests are described by declared names in TEST-INDEX.md. Prior chat test counts are not verification evidence. For authoritative results record command, time, working-tree revision, environment and summary of passed/skipped/failed tests.

## Meaningful acceptance examples

- Two different linked products, quantity one each, combine to meet Buy 2.
- Current populated SKU list excludes missing codes; requested opt-in policy changes that behavior only when implemented.
- Strict matching, once implemented, excludes unlisted/missing codes and explains the reason.
- Different deal IDs do not combine quantities.
- Repeatability and limits cap groups correctly.
- Manual item prices can still qualify for quantity rewards.
- Promotion edits preserve deal ID, links, actor/reason and history; stale revisions fail atomically.
- Expected rewards never reduce current amount due.
- Actual tax overrides rate estimate; saved payment totals reconcile.
- RR and Walgreens Cash stay distinct when moved from plan expectations to actual receipt records.
- No plan-to-receipt conversion credits an earning twice.

These are a mix of existing behavior checks and proposed acceptance criteria; consult PROMOTIONS.md before treating proposed UPC/edit features as implemented.

## Maintaining the documentation

Update the relevant rule/data/API pages in the same change that alters the application. Refresh the file/symbol inventories after new files or renamed declarations. The CSV hashes detect changes relative to this snapshot; they are not signatures or deployment attestations. Generated indexes are lexical, so review any automated regeneration for missed multiline/package-private/arrow declarations.

Keep live credentials, account exports, receipts and runtime data outside documentation examples unless explicitly selected and redacted. Preserve existing docs; this reference supplements them. The installed language runtimes, dependency package source, target/, out/, node_modules/, dist/, local data, secrets and OS metadata are outside catalog scope. Binary images/PDF/DOCX assets are inventoried but not textually interpreted.


## October 23 receipt terminology and lifecycle verification

The latest receipt wording update renames Sale unit price to Unit price before
listed discounts in creation/editing, clarifies the separate SALE_PROMOTION total
reduction, and renames the displayed saleMerchandise label without changing stored
fields or arithmetic. 56 frontend tests, TypeScript/production build and lint passed.
No Java change was needed for this wording update. Receipt duplicate-count removal
and benefit-adjusted-spending explanation passed 23 targeted component tests in the
preceding update. The earlier lifecycle implementation passed 558 Java tests (518
passed, 40 database-dependent skipped) and four desktop/mobile fixture browser
checks. These are recorded run results, not evidence of a production deployment or
PostgreSQL integration for the new routes. Personal receipts were not modified.


October 23 regular-price/item-discount verification: clean Java build ran 560 tests,
520 passed and 40 database-dependent tests skipped, zero failures/errors. New
ReceiptCorrectionTest verifies unchanged checkout and savings when historical price
reductions become explicit attached draft discounts, stable draft IDs, preserved
source snapshots, and a $4.79 × 2 − $1.58 = $8.00 item promotion counted once.
57 frontend tests passed, including attached product-discount payloads and regular
price-only entry. TypeScript, build and lint passed. Four desktop/mobile Chromium
fixture checks passed for browsing and edit/delete/restore with item discounts.
PostgreSQL integration was not rerun without a disposable test configuration;
personal data was not changed. Restart the rebuilt API and reopen receipt details
to load the new edit projection before using regular-price corrections.

October 23 discount breakdown: added a Java grouping regression for SALE_PROMOTION,
MANUFACTURER, STORE/STORE_COUPON and OTHER/missing classifications, checking the sum
matches the existing calculator total. Added a frontend regression for separately
rendered categories and their combined subtotal. This change does not alter receipt
contents or infer missing classifications.


## Draft 1.4 frontend form readability — October 23, 2026

Revision 1.4 executed evidence, October 23: 58 frontend unit/component tests passed; TypeScript/production build and lint passed; six synthetic receipt browser checks passed across desktop WebKit, desktop Chromium and mobile Chromium emulation. Checks include 48px single-line heights, 16px value fonts, expanding long-description text fit and unchanged receipt lifecycle/browsing. DOCX package/XML/readability checks passed and existing media were retained. No Java/backend or live financial data changed; installed Safari/physical-device certification and full accessibility audit remain unclaimed.

Source changes: frontend/src/index.css, App.css, styles/tokens.css (new),
components/AutoSizeTextArea.tsx (new), features/receipts/ReceiptCreate.tsx,
ReceiptEdit.tsx, ItemDiscountFields.tsx, tests/browser.spec.ts and
playwright.config.ts. The design specification is FRONTEND-DESIGN-SYSTEM.md.
The Word SRS/SDD themselves were updated in place to Draft 1.4.

Draft 1.5 enlarged sizing: 60px controls, 18px value text, 15px labels and full-width
receipt grids. 58 frontend tests, six WebKit/desktop/mobile Chromium fixture checks,
build/typecheck and lint passed. SRS/SDD 1.5 packages reopen successfully.


## Revision 1.6 — executed receipt/account verification (October 23, 2026)

- `mvn -q clean test`: **566 tests; 526 passed; 40 database-dependent skipped; 0 failures/errors**.
- `npm --prefix frontend run test -- --run`: **61 tests passed**; final account-view focused regression also passed after presentation changes.
- Frontend production build/typecheck and lint passed. `git diff --check` passed.
- Six isolated receipt/account Playwright scenarios passed across desktop WebKit, desktop Chromium and mobile Chromium emulation. They cover reviewed edit/delete/restore, blank accidental receipt-wide discount removal/omission, printed-footer inputs, account navigation, no page overflow and 60px controls. Mobile account screenshot inspected.
- Word SRS/SDD Draft 1.6 packages and XML parts reopened successfully; existing embedded media preserved. No office layout renderer was available, so rendered Word pagination is not certified.

New Java regressions cover linked RR earning/use without duplicate movement, carry-forward, unknown opening, native unvalued points, nullable/zero printed fields, malformed values and create/correction preservation of money and earnings. New UI coverage opens an account, expands its RR source and follows the receipt link.

No personal receipt records were changed by verification. Live PostgreSQL persistence/retry integration for the new account endpoints remains unverified; the 40 database-dependent tests were skipped. No deployment or physical-device Safari certification is claimed. Restart the updated Java API and refresh the frontend before using the new contracts. Earlier verification entries remain historical.

## Revision 1.7 — executed receipt correction and RR matching checks

October 23, 2026:

- Full Java suite: **571 tests, 531 passed, 40 database-dependent skipped, no failures/errors**. Final focused receipt/API/matching checks also passed after adding reserved-match review status.
- Frontend: **64 unit/component tests passed**; production build/typecheck and lint passed.
- Six synthetic Playwright checks passed: receipt edit/delete/restore and RR matching/collapsible observation placement, each in desktop WebKit, desktop Chromium and mobile Chromium emulation. The matching fixture verifies the page refreshes after a source-only association, whose receipt revision remains unchanged.
- Read-only diagnosis against the local saved $0.79 receipt: unchanged full edit-preview returned 200. No save was submitted; the pasted labels do not identify the rejected unsaved value. The corrected editor/API now expose actionable field/domain validation.

The tests use isolated memory or browser fixtures. No personal receipt was changed or matched automatically. New PostgreSQL RR match writes were not exercised against a live database; the existing shared transaction-lock/repository path is reused. Physical-device Safari/deployment and rendered Word pagination remain unverified. Word package/XML integrity and media preservation are checked separately. Earlier revision evidence remains historical.


## Revision 1.8 — unknown-source RR

Revision 1.8 verification: Java 573 tests, 533 passed, 40 database-dependent skipped, zero failures/errors. Frontend 65 tests passed; build/typecheck and lint passed. Three isolated unknown-source browser workflows passed in WebKit, desktop Chromium and mobile Chromium emulation. Tests cover nullable source dates/receipt, unchanged financial movements, conflicting dates/stale revision/duplicate rejection, reader authorization and stable retries. Live PostgreSQL command integration, physical-device Safari, deployment and rendered Word pagination remain unverified. No personal data was changed.


## Revision 1.9 — Rewards points-account display

Revision 1.9: all 74 frontend tests passed; production build/typecheck, lint and git diff whitespace check passed. Nine new account-display cases cover all five approved providers and four other accounts with legacy points data. No Java/API/schema changes or personal data writes; Java and live PostgreSQL checks were not rerun for this presentation-only update.


## Revision 1.10 — independent Rewards inventory

Revision 1.10 verification: Java full suite 574 tests / 534 passed / 40 database-dependent skipped / zero failures or errors. Frontend 77 tests passed; production build/typecheck and lint passed. Three isolated end-to-end browser flows passed in WebKit, desktop Chromium and mobile Chromium emulation: save on Rewards with no earning receipt/dates, then match the saved source to a recorded use with availability confirmation. No personal data writes. Live PostgreSQL inventory/match command integration, deployment, physical-device Safari and Word pagination remain unverified.


## Revision 1.11 — recorded receipt calculations

Revision 1.11 verification: full Java suite 576 tests / 536 passed / 40 database-dependent skipped / zero failures or errors. Frontend 78 tests passed; production build/typecheck and lint passed. New domain/UI checks cover numeric historical scenario comparisons, unchanged metadata/source snapshots, provisional zero and complete/resolved versus unresolved entry. No personal data writes or backfill. Live PostgreSQL integration, new browser/device checks, deployment and rendered Word pagination were not performed for this scoped projection update.


## Revision 1.12 — simplified receipt totals display

Revision 1.12: 78 frontend tests passed; production build/typecheck, lint and whitespace check passed. The updated regression verifies requested UI removals and preserved cash/card/source values. No backend change, personal-data write or migration; Java/live database/browser-device checks were not rerun for this bounded display removal. Word package/XML and embedded-media preservation were checked; rendered pagination remains unverified.


## Revision 1.13 — consolidation and CLI display parity

Revision 1.13 verification: clean Java suite 577 tests, 537 passed, 40 database-dependent skipped, zero failures/errors. Frontend 82 tests passed across the seven existing test files; all earlier cases retained and four rebate-display regressions added. Build/typecheck, lint and whitespace check passed. Eighteen existing isolated browser cases passed across WebKit and desktop/mobile Chromium, covering receipt browsing/lifecycle, account views, RR matching, unknown-source entry, standalone inventory and visible negative Total Paid after rebates. No personal data writes. Live PostgreSQL command integration, deployment and physical-device Safari remain unverified. DOCX XML/media integrity checked; rendered Word pagination unverified. All modifications use existing source/test/docs files; retired helpers are removed.

### Receipt period reporting update — October 23, 2026

Receipts → Period totals displays Regular price including tax, Actual OOP, Total amount saved, Confirmed benefits, Total Paid after rebates, and Total % saved. Pending rebates are not shown or included. Existing Java CLI savings rules and saved points valuations apply; original receipt data and valuations are unchanged. Cash/card Actual OOP excludes rewards used and gift cards. Percent saved uses regular merchandise plus recorded tax as denominator; a zero/unknown denominator remains Not recorded. Unknown inputs retain existing null/completeness behavior.

Select Purchase day or Month, enter inclusive Start date and End date, or click Month to date (the current local calendar month through today). Selecting a day/month clears the range; entering a range clears day/month. Clear filters returns to All time. Totals cover all active latest receipt revisions across every page, regardless of text search or status filter. Search continues across dates/statuses; without search the receipt list follows the selected period. Date-range loading/errors never reuse the prior summary. Invalid or incomplete ranges require correction.

Read-only authenticated API: GET /receipts/summary?from=YYYY-MM-DD&to=YYYY-MM-DD returns the existing Savings shape. Both dates are required, start must not exceed end, and undated/deleted receipts are excluded. Range calculation runs in Java through SavingsReport.range and the existing savings projection; the browser performs no money aggregation. No database schema change.

Existing implementation files changed: src/main/java/coupons/SavingsReport.java; src/main/java/coupons/repository/PostgresPlatformRepository.java; src/main/java/coupons/api/CouponApiService.java; frontend/src/api/client.ts; frontend/src/pages/WorkspacePages.tsx. Existing regression files: src/test/java/coupons/SavingsReportTest.java; frontend/src/features/receipts/ReceiptCreate.test.tsx. No new files. Layout uses the existing centered three-column receipt-period-totals grid (six metrics in two rows), collapsing to one column on narrow screens; existing typography/control tokens remain unchanged.

Period reporting verification: 83 frontend tests passed; 25 focused Java savings/API tests passed; frontend build and lint passed; git diff --check passed. No live receipt records changed.

Date-range troubleshooting: a Java API process started before the /receipts/summary route was added interprets summary as a receipt UUID and returns HTTP 400. Rebuild and restart the Java process; browser refresh alone cannot load new backend classes. The period panel now shows a single error notice rather than a duplicate unavailable notice. Existing WorkspacePages.tsx updated; no receipt data changed.

### Receipt entry breakdown and field errors — October 23, 2026

Add Receipt now provides a Review entered breakdown button near the top, in addition to the existing calculated review action. The Java preview review lists product descriptions, regular prices, quantities, recorded extended prices and individual attached discounts; shared/receipt-wide discounts appear separately with their selected products. Existing tax, tender, reward-used, earned-benefit and savings calculations remain in the financial review. No additional browser money calculations or financial events are introduced.

Receipt-entry invalid fields receive a red border and pale red background, aria-invalid, and a descriptive error. Validation scrolls to and focuses the first invalid control; collapsed containing details are opened. Received reward confirmations explain the original unit, compare exact decimal strings without Number conversion, and show an inline error if the confirmed amount differs. Equivalent values such as 5 and 5.00 remain valid. Correct Reward amount first when the actual received amount differs. Points and their saved USD valuation remain separate. New-earning API validation identifies the offer/provider and expected amount/unit rather than displaying status-only workflow wording. Existing saved-reward status restrictions remain unchanged.

The draft information message is explanatory, not an error: the receipt is unsaved until review and Save receipt; closing discards unsaved input. It now says this in plain language. Existing files edited: frontend/src/features/receipts/ReceiptCreate.tsx; frontend/src/features/receipts/RewardFields.tsx; frontend/src/index.css; src/main/java/coupons/api/EarningInput.java. Existing regressions: ReceiptCreate.test.tsx and src/test/java/coupons/api/ReceiptInputTest.java. No new files or database-schema changes.

Receipt-entry validation checks: 85 frontend tests and 15 focused Java input/status tests passed; frontend build, lint and diff whitespace checks passed. Regression coverage includes focus on the first missing field and exact-decimal reward amount confirmation.

### Multiple Register Rewards used during receipt creation — October 23, 2026

Add Receipt → Register Rewards used now offers Add RR used and removable individual rows, each with one dollar amount and optional description. Enter $5, $5 and $4 as three separate coupons; duplicate dollar values remain distinct through stable event IDs. Blank descriptions default to Register Rewards used. The review shows each coupon and the Java-calculated RR total. These receipt-wide deductions must not be entered again as discounts or payment tenders. Existing post-save matching offers a separate source match for each use; matching does not create another deduction. Edit Receipt already supports separate RR uses and is unchanged.

Receipt preview/create accept rrUses:[{eventId,amount,recordedDescription?}] with at most 100 positive entries and unique event IDs. The legacy rrUsed/rrUsageId request remains supported; a nonzero legacy amount alongside rrUses is rejected to prevent double counting. Existing UnidentifiedRR observations store the separate uses; no schema/model migration or new financial formula is introduced. Preview tokens and stable save retries retain the individual IDs.

Existing files updated: frontend/src/features/receipts/ReceiptCreate.tsx; src/main/java/coupons/api/ReceiptInput.java; frontend/src/features/receipts/ReceiptCreate.test.tsx; src/test/java/coupons/api/ReceiptInputTest.java. No new files. Regression coverage verifies $5+$5+$4 yields $14, equal coupons have different IDs, preview/save preserve identities, and zero/duplicate-ID/mixed legacy input is rejected.

Product entry disclosure: Add Receipt product cards now use native keyboard-accessible Show / Hide disclosures, initially expanded. Each summary keeps the product number, description, quantity and regular price visible. Collapsing retains all inputs and attached discounts; validation opens a collapsed invalid product before focusing its field. Existing ReceiptCreate.tsx updated; no new files or financial changes.

Separate RR input compatibility: Unknown request field from an older running API can mean rrUses is unsupported. Restart the updated backend without refreshing the browser; the in-memory draft remains available for another preview. ReceiptCreate.tsx now gives this recovery instruction for the generic unknown-field preview error. No receipt is saved by preview.

### Simplified new reward entry and collapsible sections — October 23, 2026

New Fetch point earnings automatically receive the existing worksheet valuation of 1000 points = 1 USD in EarningInput when no explicit valuation is supplied. The UI hides Fetch manual valuation fields; 25 points retain the exact $0.025 saved value. This is a worksheet valuation, not a redemption or current market-rate guarantee. Existing explicit valuations and saved receipts are preserved. Other providers keep their existing valuation entry.

New reward entry records the entered amount once. Choosing Printed at register or Added to program account uses that same amount and its original USD/points unit in the existing confirmed payload. Duplicate amount/unit entry and the repeated confirmation checkbox are removed from RewardFields; EXPECTED remains unconfirmed, and backend saved-reward status protections remain unchanged. P&G promotion guidance appears under Rewards earned or expected: Other provider → P&G Brand, offer description, products and Buy/Spend requirement. Receipt-scan qualification messages explain that eligibility checks are separate from recording/valuing rewards, without misleading zero-quantity/unknown-dollar displays.

All Add Receipt sections use keyboard-accessible Show / Hide disclosures: receipt details, products, shared discounts, tax/rewards used/payments, printed footer, rewards earned/expected, and evidence. Draft values remain present while collapsed; validation opens every enclosing disclosure before focusing an invalid input. Existing files updated: ReceiptCreate.tsx, RewardFields.tsx, rewardStatus.ts, EarningInput.java, ReceiptCreate.test.tsx, ReceiptInputTest.java. No new files or database changes.

Simplified reward verification: 86 frontend tests passed; full Maven tests passed (database-dependent tests retain their normal skips); frontend build/lint and diff checks passed. Existing frontend/tests/browser.spec.ts was updated to use the single-entry reward workflow. Local API restarted to load automatic Fetch valuation. No saved receipt data changed.

Expected rewards quick entry: RewardFields now explains that provider and amount with Expected — waiting to receive are sufficient. Product selections, promotion rules, valuation, evidence and submission details sit in a closed Optional reward details disclosure; historical copying has its own optional disclosure. ReceiptRewardForm places qualification diagnostics in Optional qualification review, and explains that expected rewards do not increase confirmed benefits. No fields or backend validation requirements were added. Existing entered optional values are preserved. Existing RewardFields.tsx, ReceiptRewardForm.tsx and browser.spec.ts updated; no new files.

Received reward entry simplification: manually entered new rewards now default to received (CREDITED for account rewards, PAID/printed for RR); Expected remains an explicit option for rewards still awaited. Copying historical terms still defaults to Expected and does not copy receipt confirmation. Updating a saved reward status uses its existing recorded amount/unit automatically without duplicate input or checkbox; the existing revision-checked API payload still carries those exact values and explicit confirmation. No historical rewards are automatically reclassified. Receipt qualification diagnostics are collapsed under Optional qualification details; unknown eligibility does not negate a recorded receipt of rewards. Existing files: rewardStatus.ts, RewardFields.tsx, EarningStatusForm.tsx, EarningStatusForm.test.tsx, ReceiptDetail.tsx. No new files or backend financial-rule changes.

Received-default regression coverage also updates existing ReceiptRewardForm.test.tsx (explicit Expected selection remains available) and browser.spec.ts (status updates no longer require duplicate amount/unit controls).

Automatic P&G/Fetch point valuation: new API reward entries calculate Fetch at 1000 points = 1 USD and P&G Brand/P&G Good Everyday/PG at 250 points = 1 USD, using existing worksheet rates. Native points remain stored separately; 100 P&G points = $0.40 and 25 Fetch points = $0.025 before display rounding. Expected rewards remain excluded from confirmed savings. RewardFields hides manual conversion for both programs; rewardBody sends no hidden stale valuation for those point providers. EarningInput calculates the USD valuation and records its basis. Previously saved explicit valuations are not overwritten. No payout is generated. Existing EarningInput.java, RewardFields.tsx, rewardStatus.ts and ReceiptInputTest.java edited; no new files.

Existing points valuation repair: period totals, savings reports, account movements and receipt benefit projections now use SavingsReport.effectiveValuation. It preserves explicit historical valuations and derives a read-only worksheet value for missing Fetch (1000 points/$1) or P&G (250 points/$1) valuations. Other unknown providers remain incomplete. This fixes the existing 100-point P&G entry blocking all-time savings: it contributes $0.40 without rewriting receipt history or creating earnings. Expected/rejected status rules remain unchanged. Receipt Rewards displays the projected dollar value beside points. Existing SavingsReport.java, ProviderBalanceSummary.java, PostgresPlatformRepository.java, ReceiptDetail.tsx and SavingsReportTest.java updated; no new files.

Existing-point fallback verified: full Maven suite passed (583 tests, 40 database-dependent skips), 86 frontend tests passed, frontend build/lint passed. Existing missing-valuation regression fixtures now use Merryfield to preserve unknown-provider checks; known Fetch/P&G rates have explicit coverage. API restarted and all-time summary verified with no missing-valuation issues. No saved receipt records changed. Additional existing test files updated: ProviderBalanceSummaryTest.java and OwnerReceiptActionsTest.java.

Ibotta account credit: RewardFields and EarningStatusForm label CREDITED as Added to Ibotta account, separate from Expected (waiting for credit). Typing Ibotta as the provider selects the cash benefit form. Account credit is earned value held in Ibotta; the user's $20 withdrawal minimum is displayed as context, not an external eligibility guarantee. Rewards → Ibotta has an Account Credit & Withdrawals section with the recorded period-end balance and minimum. A withdrawal is a separate transfer/payout and must not generate another earning. Existing conversion records remain unchanged; this update does not initiate withdrawals or introduce a payout command. Existing RewardFields.tsx, EarningStatusForm.tsx and WorkspacePages.tsx edited; no new files or financial-rule changes.

Decimal entry accepts a leading decimal point: .98 is equivalent to 0.98 for receipt tax, prices, discounts, RR, payments, footer observations and API decimal amounts. Exact BigDecimal/string precision is retained; currency display supplies the leading zero. Whole-number quantity rules remain unchanged; lone dots, negative nonnegative amounts and malformed decimals are rejected. Existing ReceiptCreate.tsx, ReceiptDetail.tsx, WorkspacePages.tsx, api/format.ts, ReceiptInput.java, ReceiptEarningStatusUpdate.java and existing receipt tests edited. No new files or database changes.

Automatic Fetch receipt default (explicitly approved): new web receipt preview/create adds one received/CREDITED 25-point Fetch earning valued at $0.025 when no explicit Fetch entry is present. Any explicit Fetch amount/status replaces this default. Event identity is deterministic per receipt UUID, so repeated previews and idempotent saves do not duplicate it. Preview shows the benefit; only saving persists it. Existing receipts, edits and restores are not backfilled. Existing ReceiptInput.java, ReceiptCreate.tsx and ReceiptInputTest.java updated; no new files.

Rewards date browsing: Rewards & Accounts adds account-name search, Balance through date, and Month to date (current local date). The main table's Month Start, Day Start, Day Close and allowed points columns are recalculated in Java for the selected date; future activity is excluded from those balances. Clicking an account opens the selected month's first day through that date. The redundant Open Account/Choose Account dropdown is removed; names remain clickable. GET /workspace?asOf=YYYY-MM-DD returns existing workspace balance projections for the chosen date; omitted asOf preserves existing latest-receipt behavior. Search filters account rows only. Unknown openings remain unknown. Existing PostgresPlatformRepository.java, CouponApiService.java, api/client.ts, WorkspacePages.tsx and ReceiptCreate.test.tsx updated; no new files or stored-data changes.

Receipt search exact-example verification: existing separator-insensitive matching finds receipt 1151-3213-7477-2610-0803 using 11513213747726100803, 1151-3213747726100803, fully hyphenated or space-separated input. Leading zeros remain significant; a different final digit does not match. Regression added in existing ReceiptCreate.test.tsx; search implementation and saved data unchanged. Use Search receipts, not Search accounts.

Walgreens Cash quick option: RewardFields now offers Walgreens Cash reward type → Member rewards for purchase, alongside Other promotion / reward. Selecting it fills the offer description, sets whole-receipt scope and a purchase-based requirement without a spend/quantity threshold. The user enters the amount once; status selection and existing credit calculations remain unchanged. Available in both Add Receipt and post-save Add rebate or reward. Existing RewardFields.tsx and ReceiptRewardForm.test.tsx edited; no new files or database changes.

Receipt product lookup: existing active receipt history populates workspace.productLookup with SKU, description, most recent regular price, purchase date and source receipt. Latest revisions are deduplicated before filtering; deleted receipts are excluded. SKU leading zeros are preserved, and conflicting descriptions for one SKU remain separate selectable choices. Add Receipt suggests up to eight prefix matches as SKU is typed. Use this product fills description/SKU/last regular price and quantity 1; no historical promotions, coupons or rewards are copied. The user reviews the current price and discounts. This is a read-only product reference, not an import into on-hand inventory; historical purchases do not prove remaining stock. Existing PostgresPlatformRepository.java, types/platform.ts, App.tsx, ReceiptCreate.tsx, ReceiptCreate.test.tsx and SavingsReportTest.java updated. No new files or database tables.

Product discount descriptions: new item-attached discounts start with the product description's first word, e.g. Ven Sale Promotion. Selecting Manufacturer or Store Coupon updates the generated label; custom text remains editable. On leaving a discount description, the product prefix is supplied if missing and each word's initial letter is capitalized. Add Receipt product descriptions also capitalize word initials on blur without changing cursor position during typing. Existing historical records are not bulk rewritten. Existing api/format.ts, ReceiptCreate.tsx and ReceiptDetail.tsx updated; no new files or calculation changes.

New-entry focus: Add Product and Add Another Line place the cursor in the new product description. Adding a product discount focuses its new description; adding a shared/receipt-wide discount focuses its label. Add Receipt and Edit Receipt support this, using focus state and autofocus on newly mounted inputs, without moving focus during ordinary typing. Existing ReceiptCreate.tsx, ReceiptDetail.tsx and ReceiptCreate.test.tsx edited; no new files or financial changes.

Shared discount selector correction: Add Receipt retains the original shared-entry presentation even when only one product has been selected so far. Previously, selecting the first item reclassified the form row into the item section and hid the remaining product checkboxes. A draft-only shared marker keeps the selector available; selecting multiple items submits their stable IDs and quantities once, with wholeReceipt false. Item-created discounts remain with their item. The draft marker is not sent to the API and does not affect financial calculations. Existing ReceiptCreate.tsx and ReceiptCreate.test.tsx updated; no new files or saved-data changes.

PayPal and Venmo reward options: both appear in Common providers and the provider suggestions when adding a rebate/reward. Selecting or typing either uses a CASH/USD benefit rather than points; received is the default and the amount is entered once (e.g. 1.90). This records a new rebate paid through that provider only when saved. An existing rebate transferred from Ibotta or another account should remain a separate transfer, not another earning. Existing RewardFields.tsx and ReceiptRewardForm.test.tsx updated; no new files or personal receipt changes.

### Receipt total detail reports — October 24, 2026

Receipts period totals Regular price including tax, Actual OOP, Total amount saved and Total Paid after rebates are clickable. Each opens an existing-style modal with all active records in the selected day/month/inclusive range or all time, independent of receipt-list search/status/page. Reports support item/SKU/store/receipt/date/time/payment-type text search and date/time newest/oldest, store A–Z and item/payment-type A–Z sorting. Receipt links open source details. Regular price shows individual product regular extended values plus separate sales-tax rows. OOP shows individual cash/card tenders with period cash/card totals and matching cash/card subtotals; WC/RR/gift cards are excluded. Saved/net views show one contribution per receipt with its product descriptions. Period total remains unchanged by report search; Matching subtotal follows visible rows. Unknown contributions remain unknown.

Read-only authenticated GET /receipts/report?metric=regular|oop|saved|net&from=&to=&search=&sort= returns metric, existing Savings summary, rows, matchedTotal, matchedCash and matchedCard. Bounds are inclusive and optional; latest active revisions only. All money multiplication/aggregation uses Java BigDecimal and existing SavingsReport rules; the browser formats exact decimal strings. This introduces no saved records, schema or formula changes.

Existing files updated: src/main/java/coupons/repository/PostgresPlatformRepository.java; src/main/java/coupons/api/CouponApiService.java; frontend/src/api/client.ts; frontend/src/types/platform.ts; frontend/src/pages/WorkspacePages.tsx; frontend/src/App.css; src/test/java/coupons/SavingsReportTest.java; frontend/src/features/receipts/ReceiptCreate.test.tsx. No new files.

Receipt detail report validation: 96 frontend tests passed; full Maven suite passed with normal database-dependent skips; frontend build/lint and diff checks passed. Read-only live verification confirms each of the four unfiltered report subtotals equals its period summary, cash plus card equals Actual OOP, and date-range/item search works. API restarted. No personal records changed.

Register Rewards used disclosure: Add Receipt and Edit Receipt give the RR-used subsection its own initially expanded Show / Hide control, displaying the entered coupon-row count. Users can collapse all RR rows after entry and reopen them without losing amounts, descriptions or match identities. No financial or storage changes. Existing ReceiptCreate.tsx and ReceiptDetail.tsx updated; no new files.

Confirmed benefits detail: clicking Confirmed benefits opens the selected-period searchable/sortable report of all receipt earnings with native amounts/units, dollar values, confirmed/unconfirmed/rejected grouping and source receipt links. Confirmed and unconfirmed dollar subtotals are separate; rejected rewards contribute to neither. Unknown point valuations remain unknown rather than zero. New metric=benefits in GET /receipts/report returns original earning and receipt revision for existing status commands. Confirm / update status opens the existing EarningStatusForm and scrolls/focuses its control; it uses recorded amount/unit once, revision checks and stable retries. Saving refreshes report and main workspace totals. Read-only users cannot change status. Existing WorkspacePages.tsx, types/platform.ts, PostgresPlatformRepository.java, ReceiptCreate.test.tsx and SavingsReportTest.java updated; no new files or new financial formulas.

Benefits reconciliation includes clearly labeled printed Walgreens Cash footer fallback rows already counted by SavingsReport when no explicit WC earning exists. These rows link to Review receipt footer rather than creating a fictional earning/status action. No footer contribution is added when any explicit WC earning exists.

Benefits detail preserves existing SavingsReport footer fallback: when no explicit WC earning exists, printed WC earnings already counted by the total appear as labeled receipt-footer contributions with a Review receipt footer link. They are not invented earning records and cannot be status-confirmed. Explicit reward entries use the existing status form. Confirmed report subtotal verified against the live main summary; 97 frontend tests and focused Java report/API tests passed, plus build/lint. No personal statuses or records changed.

Rewards Without Receipts placement: the existing standalone RR inventory panel now appears at the bottom of the main Receipts page, after the receipt list/pagination and editing guidance. It is removed from the Rewards main balance screen; it remains accessible in Register Rewards account details. Add reward without a receipt and the reward inventory table retain their existing behavior. Existing WorkspacePages.tsx and browser.spec.ts updated; no new files, backend changes or saved-data edits.

Rewards Overview (approved definition): Total Rewards = Available Balances + Expected / Unconfirmed, shown separately on the Rewards main page for Balance through. Available sums positive recorded account closing balances after recorded spending/transfers; confirmed earnings are not added again. Expected includes outstanding active-receipt rewards dated on/before the selected day, excluding received, rejected and expired expected entries. Prior-month outstanding rewards remain included. Native points use saved/automatic USD valuations. Missing balance/valuation leaves the affected full total unknown and shows known subtotals/providers for review. Account search does not change these overview totals. GET /workspace adds rewardsOverview with available, expected, total, known subtotals and completeness context. Existing PostgresPlatformRepository.java, types/platform.ts, WorkspacePages.tsx and SavingsReportTest.java updated; no new files or personal record changes.

Rewards Overview verification: 98 frontend tests and 31 focused Java savings/API tests passed, with build/lint/diff checks. Read-only live API confirms Total Rewards equals Available plus Expected when all inputs are known. Existing ReceiptCreate.test.tsx also verifies the three cards remain stable during account search. No personal record changes; API restarted.

Walgreens-only printed footer: Printed Savings & Walgreens Cash appears only for Walgreens store names, case-insensitively with surrounding spaces and optional store-number suffixes supported. Add Receipt, Edit Receipt and saved receipt Totals use the same isWalgreensStore check. New non-Walgreens receipts omit any stale Walgreens footer draft from submission. Historical observations are preserved rather than deleted when hidden. Existing api/format.ts, ReceiptCreate.tsx, ReceiptDetail.tsx and ReceiptCreate.test.tsx updated; no new files or financial changes.


Rewards page layout: Search accounts, Balance through and Month to date share one aligned row on wider screens and stack on narrow screens. Rewards overview explanatory text has interior spacing; Total Rewards, Available Balances and Expected / Unconfirmed are centered. Individual account pages show From/Through followed by Account Balances before activity. Account Activity can expand/collapse, and each activity description expands to reveal reward connections, valuation notes and technical details while its transaction amounts remain visible. Register Rewards places Rewards Without Receipts at the bottom, after other account content. Successful receipt creation displays “Receipt saved.” Financial calculations and saved records are unchanged.


Rewards filter alignment correction: reset the Search accounts and Balance through labels’ bottom margins within rewards-account-filters. The Month to date button uses the shared form-control-height token and aligns to the bottom of the same grid row, keeping its top and bottom level with the two inputs. Narrow-screen stacking remains supported.


Receipt browsing: Search receipts stays visible. Purchase day, Month, Start date, End date, Month to date, Sort receipts, Receipt status and Clear filters are grouped in a More filters — Show / Hide disclosure, collapsed initially. Expanding reveals the existing responsive filter grid; collapsing preserves selections and their effect on receipt results and period totals.


Receipt entry product action formatting: the calculation note uses two readable lines with spacing. Add discount, Add another line for this product and Remove product appear as separate bordered buttons with a 12px gap and at least 44px height. Actions wrap on wider screens and stack at full width on narrow screens; removal uses a distinct text color. Existing input focus, duplication, discount and removal behavior remains unchanged.


CVS receipt entry and correction now provide a collapsible ExtraBucks (ECB) used section. Each coupon has a separate amount and description, plus add/remove actions. Uses apply to the whole receipt, never individual products. Existing OTHER payment records with accountName ECB and explicit CHECKOUT_DEDUCTION roles are reused, retaining stable payment IDs and notes; no new schema or endpoint is introduced. Create input accepts the existing Payment accountName field. ECB rows are excluded from the generic payment editor to prevent duplicate entry and are listed in the create review breakdown. ECB uses are separate from Register Rewards, manufacturer coupons and cash/card Actual OOP. This change records descriptions, not source-earning matching.


Manufacturer product coupons now have an optional Required purchase quantity field in Add and Edit Receipt, for terms such as buy 2. It records the coupon’s required product count, not coupon count or purchased quantity. The total deduction remains entered once and is not multiplied by the requirement. Positive whole numbers are accepted; blank means not recorded. The existing CouponApplied.requiredQuantity model and serialized field are reused, exposed in create/correction requests and edit projections, and shown on saved receipt discount details. Existing requirements are retained when omitted by API callers; explicit null clears one. No database schema or new files are needed.


CVS earned rewards: Add Receipt and the saved receipt Add rebate or reward form default to CVS ExtraBucks (ECB), stored as THIRD_PARTY_REBATE / STORE_REWARD with provider ECB so earned and used activity share the ECB account. Enter the earned USD amount and required reward name once. The CVS reward type selector includes 2% purchase earnings; record the CVS-reported amount when known, mark Expected while awaiting credit, or add it later via the saved receipt Rewards tab. No automatic 2% calculation or invented posting date is applied. New ECB entries default to whole receipt with optional associations available. CVS receipt entry, editing and totals hide unused Walgreens-specific RR/WC controls and footer; existing unusual saved entries are retained for correction. Shared financial wording uses redeemed rewards rather than Walgreens-specific terminology.


Receipt Rewards tab presentation: each earned/expected reward is a bordered, padded card with a muted program label, prominent promotion name, separate status badge and product scope, and a prominent formatted dollar amount (USD) or native points. Product association and status actions use separate spaced buttons. Qualification details remain collapsible. Expected amount is shown only when there is no confirmed amount, avoiding the repeated expected/received amount on confirmed cards. Cards stack on narrow screens; financial values and saved records are unchanged.


Member rewards without receipts: Walgreens Cash, Register Rewards, ECB and Walmart Cash individual account pages provide Add member reward without a receipt with reward date, positive USD amount and description. POST /accounts/member-rewards is an authenticated idempotent write using the existing AccountAdjustment and provider_account_adjustments ledger, with actor/time and Member reward description. It increases account activity/closing balance and overview availability; it does not create receipts, change receipt savings, or invent openings. Existing ledger beginning balances/order rules still apply. Register Rewards coupon inventory remains a separate non-earning record for matching; avoid counting the same earning again. Walgreens Cash Provider-Reported Observations is initially collapsed and supports Show / Hide without changing printed observations.


Dashboard sections Your next shopping trip, Rewards to keep track of and Recent receipts each have independent accessible Collapse / Expand buttons with aria-expanded and aria-controls. Sections start expanded, preserve content and retain navigation actions when collapsed. Confirmed benefit value explanatory text explicitly identifies receipt earnings in the selected period, separate from account closing balances. Dashboard financial formulas and personal receipt/account records are unchanged. Spreadsheet comparisons must compare the same date range and distinguish opening balances plus net account activity from receipt earnings; account balances are not substituted into receipt savings.


Clipped coupons and catalog cleanup: Deals & promotions and Coupons now expose permanent Delete actions with write access and revision checks. DELETE /deals/{id} and DELETE /clipped-coupons/{id} remove the catalog record and its own revision history, detach current plan associations while retaining product prices, and remove deal source artifacts only when not shared. Normal plan revision history remains; no deletion backup is created. Redeemed coupons cannot be deleted. Receipt entry accepts clippedCouponIds and offers saved coupon selection per product and in shared/receipt-wide discounts. Selection creates one editable discount with the coupon UUID, description, type and buy requirement; use the shared selector for a coupon covering several products. Percentage/free-item deductions use the actual recorded receipt amount. Selecting does not consume a coupon; receipt creation and all selected coupon redemptions commit atomically in PostgreSQL. Expired, already-used, or ineligible coupons cause rollback. The Coupons page then shows Applied and the receipt connection. Do not manually repeat a selected deduction. Existing receipt corrections retain their separate workflow; editing a discount does not automatically release a previously redeemed coupon. No new schema migration or files are introduced.

Changed application and test files: frontend/src/App.tsx, frontend/src/pages/WorkspacePages.tsx, frontend/src/features/receipts/ReceiptCreate.tsx, frontend/src/types/platform.ts, frontend/src/App.css, frontend/src/features/receipts/ReceiptCreate.test.tsx, src/main/java/coupons/api/CouponApiService.java, src/main/java/coupons/api/ReceiptInput.java, src/main/java/coupons/repository/PostgresPlatformRepository.java, src/main/java/coupons/repository/PostgresTransactionRepository.java, src/main/java/coupons/repository/PostgresClippedCouponRepository.java, src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java.


Catalog cleanup and clipped-coupon receipt verification (2026-10-24): 106 frontend tests passed; frontend build/lint and diff whitespace checks passed. Full Java suite: 594 tests, 0 failures/errors, 42 database-dependent skips in the ordinary run. Separately, all 5 PostgresTransactionRepositoryTest integration tests passed in an isolated temporary PostgreSQL schema, including atomic coupon redemption, expiration rollback, used-coupon delete protection, plan detachment and unshared source deletion; the schema was dropped afterward. Live checks confirmed deletion of the three user-named test deals and two unused test coupons, cleared current plan links, and unchanged receipt records, savings summaries and account balances. No personal receipt or account adjustment was made. frontend/src/App.test.tsx also verifies revision-aware catalog deletion.


User-authorized test-plan cleanup (2026-10-24): permanently removed the two explicitly identified test plans, “Test A Default matching” and “Pick Up Order,” with their five planned items, associated plan/item histories and checkout drafts. Checked for linked rebate records before deletion and performed the cleanup in a single transaction. Read-only before/after workspace comparisons verified that saved receipts, account balances and savings summaries were unchanged. No application behavior or schema changed and no cleanup backup was created.


ECB non-receipt member rewards now support optional expirationDate in POST /accounts/member-rewards and an ECB expiration date input. Dates before the reward date are rejected. AccountAdjustment retains the date in its existing serialized payload; its legacy eight-argument constructor and older records default to unknown expiration. No new SQL column or migration is required. Account activity displays Expires YYYY-MM-DD. The date is informational: it does not automatically debit balances or infer remaining coupon value after spending. Existing saved financial amounts are unchanged.
Changed files: WorkspacePages.tsx, CouponApiService.java, AccountAdjustment.java, ProviderBalanceSummary.java, ReceiptCreate.test.tsx and ProviderBalanceSummaryTest.java.


ECB expiration verification: 106 frontend tests passed, including submission of an optional expiration date; build and lint passed. Focused ProviderBalanceSummary/API tests passed, covering expiration serialization, legacy constructor compatibility, invalid date rejection and activity display. Five pasted worksheets were compared read-only against live records; the detailed findings are appended to docs/grocery-remaining-research.txt. No financial corrections were applied.


Receipt-earned ECB expiration: new CVS ExtraBucks/store reward entries expose Reward expiration date in the main reward form. Existing receipt reward cards display the saved expiration and offer Edit products / expiration. The existing EarningLinksForm includes an optional expiration date, prefilled from the saved earning; blank clears it. PUT /receipts/{receiptId}/earnings/{earningId}/associations accepts optional expirationDate, preserves it when omitted, and rejects dates before the recorded purchase day. Omitted product selections retain their prior values. Saving preserves earning ID, expected/confirmed amounts, valuation, status, payments and evidence, and records a receipt revision. This reuses the existing Earning.expirationDate field and existing endpoint; no schema migration or new files. Expiration entry does not automatically create an account debit.
Changed files: RewardFields.tsx, EarningLinksForm.tsx, ReceiptDetail.tsx, platform.ts, CouponApiService.java, ReceiptCreate.test.tsx and CouponApiServiceTest.java.


Dashboard whole-card collapse correction: dashboard grid items align to the start rather than stretching to the height of the adjacent expanded card. All three dashboard cards have an explicit collapsed class and hidden bodies take no layout space. A collapsed card shrinks to its header, retaining its title/subtitle, Expand control and navigation link; expanding restores the full content. Header actions wrap with clear spacing. Existing browser.spec.ts verifies actual card/header heights and independent expand/collapse at desktop, Safari and mobile viewport sizes.


Whole-card collapse validation: 107 frontend tests, build and lint passed. Actual card bounding-box checks passed for all three dashboard sections at 1440px and 390px using the cached Chromium executable and synthetic API fixtures. The configured Playwright Safari/Chromium run could not launch because its required browser versions are not installed; Safari was not visually verified. No browser downloads, new project files or financial data changes were made.


Calculated Account Balances card spacing: explanatory/loading/empty text uses 23px horizontal insets and 18px bottom spacing with 1.7 line height. On screens up to 600px, horizontal insets are 18px to align with the card heading. Notices use the same insets; account tables retain their existing scrolling layout. Changed WorkspacePages.tsx and App.css; no balance calculations or saved records changed.


Documentation audit 1.14 (2026-10-24): current code baseline 3b0f681 already matched origin/main. Exactly one SRS and one SDD were found, so neither required merging with a competing specification. Both were updated from internal 1.13 to 1.14 with a dated revision row, core metadata and a consolidated REV14-001–012 traceability section. The duplicate supporting requirements inventory was byte-identical and removed from docs/reference; docs/requirements retains its single copy. Historical design plans were labeled as provenance. Missing Word table grids were repaired for structural consistency. Narrow .gitignore exceptions now make only the canonical Word pair and reference README eligible for Git; private data remains ignored. No commit/push or financial write performed. Remaining known limits are explicitly listed in both Word documents.

Audit 1.14 final checks: 107 frontend tests passed; frontend build/lint and diff checks passed. Full Java run: 596 tests, 0 failures, 0 errors, 42 skipped. Word XML, table grids, cover version, metadata, revision row and all 12 REV14 mappings validated. Exactly one canonical SRS and SDD remain. Browser visual verification was not rerun for this documentation-only audit.


## Revision 1.15 — documentation consolidation audit (2026-10-24)

Merged all unique blocks from the historical requirements inventory into the SRS and both earlier Word design/combined plans into the SDD, then removed those separate Word copies. Source text/table preservation was checked before removal. The SRS and SDD are the only two application Word documents; both covers, metadata and revision tables identify 1.15.

Merged docs/design/API.md and docs/archive/api/API 2.md into API-REFERENCE.md, and the archived duplicate deployment guide into docs/operations/DEPLOYMENT-BACKUP.md. Older contracts are retained as clearly historical source sections, with current token identity, persistent replay journal and V1–V9 migrations taking precedence. Combined the October 20/21 FILE_INDEX.md inventories into one index and their ALL_DECLARATIONS.tsv rows into one TSV with an as_of column, preserving every row. This is not a fresh source inventory.

Updated the canonical-document map and merged-target Markdown links. Unique subject guides remain separate. Receipt evidence, migration source copies, distinct receipt.properties records and generated third-party legal notices were deliberately unchanged. No application source or financial records changed, no new files were created, and nothing was committed/pushed.

Revision 1.15 final checks: exactly two application Word files, both version 1.15; revision rows and OOXML validated. No duplicate guide basenames remain; five receipt.properties files are separate evidence records. Combined inventory row count: 10437. Application tests were not rerun because this task changed documentation only.


## Documentation revision 1.16 — material relocated from the root README (2026-10-24)

The following former README sections preserve prior operating guidance/change history. They are historical source material; current procedures above and the current API reference take precedence where earlier feature limitations or migration versions differ.

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
[Frontend Design System & Component Style Guide](FRONTEND-DESIGN-SYSTEM.md).
Core tokens live in frontend/src/index.css; global controls in index.css
and component layouts in App.css.


## Receipt and reward account updates — October 23, 2026

Rewards & Accounts keeps the balance list and opens a dated account activity view from each account name. Receipt numbers open their records; View Reward expands existing RR earning/use connections without posting another movement. Add/Edit Receipt includes optional Printed Savings & Walgreens Cash observations. Payment completeness asks “Have you entered all payments shown on this receipt?” (Yes / Not finished / Not sure). Delete an accidental receipt-wide discount, or clear its description and amount; fully blank rows are omitted from the reviewed save. Restart the Java API and refresh the frontend to load both updated contracts. See [receipt and reward workflows](RECEIPT-REWARDS-WORKFLOWS.md).



## Revision 1.7 — receipt RR correction and matching

Receipt correction now supports separate RR-use rows, explicit conversion of misclassified discounts/payments, and saved-receipt matching to available unused RR. Printed Savings & Walgreens Cash is below payments and collapsible. See [workflow instructions](RECEIPT-REWARDS-WORKFLOWS.md#revision-17--recording-and-matching-rr-used).



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

Canonical specifications (documentation revision 1.15, October 24, 2026):
[Software Requirements Specification](Distributed_Coupon_System_SRS_Draft_1_0.docx) and
[Software Design Description](Distributed_Coupon_System_SDD_Draft_1_0.docx).
Maintain these two files in place, increment their internal version, and append a dated revision-history entry for each change set. Filename Draft_1_0 is retained for stable links; the current internal version is 1.15. See [document control](README.md).


## Revision 1.16 — README restructuring (2026-10-24)

Root README rewritten as a GitHub repository overview with purpose, status, capabilities, stack, structure, documentation navigation, support/maintainer and actual license status. Setup/manual and chronological change sections were relocated by subject to existing operating/workflow/verification documents as historical source material; current guide procedures take precedence. Frontend README now describes this application rather than generic Vite template configuration. Canonical guides linked from the README/index are eligible for Git so repository navigation can resolve when the reviewed docs are committed; private research, receipt evidence, source snapshots, credentials and generated artifacts remain ignored. SRS/SDD covers, internal metadata and revision tables updated to 1.16. This was documentation-only; no application tests rerun and no financial changes, new files, commit or push.

Reference used: GitHub Docs, About the repository README file (https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-readmes). GitHub provides guidance rather than a mandatory fixed README template. The owner explicitly requested manual procedures remain in separate guides.
