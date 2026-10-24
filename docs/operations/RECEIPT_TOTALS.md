# Receipt totals and browsing

Updated October 23, 2026. The October 23 update at the end supersedes the earlier
week-selector instructions: the selector is now hidden and Month uses a dropdown.
Restart the rebuilt Java API and refresh the browser to load the new workspace fields.
No database migration or historical receipt rewrite is part of this change.

## Receipt table

The columns are Store / receipt, Date & time, Recorded printed total,
Actual out of pocket after used rewards (cash/card), Confirmed benefit value,
Total saved, Status, and a button to open the original receipt details.

The receipt list shows five rows per page, newest first by purchase date and time.
Previous and Next navigate the matching results. The dashboard shows five recent
receipts. Recognized printed times display consistently; original stored text is
preserved and unrecognized text remains visible. Unknown dates/times sort last.

## Financial meanings

- Recorded printed total: the original receipt total, with its recorded basis.
- Actual out of pocket after used rewards (cash/card): cash and card payments,
  excluding Walgreens Cash, RR used, gift cards and newly earned benefits.
- Confirmed benefit value: confirmed earnings in USD or with explicit saved USD
  point valuations. Pending earnings are not included and no valuation is invented.
- Total saved: the existing Java formula, regular merchandise plus tax minus
  cash/card paid plus confirmed valued benefits. This includes benefit value and
  is separate from the amount paid. Prior rewards used are not added again as new
  earnings. It can exceed regular value when benefits exceed spending.

The receipt Totals tab keeps the purchase, payment and benefit sections, adds a
visible Total saved row, and retains the original figures in Legacy calculations.
The legacy reported/calculated difference is explained within that section rather
than displayed as the main current reconciliation warning.

## Worked example

| Measure | Amount |
|---|---:|
| Regular merchandise | $44.96 |
| Sale merchandise | $31.98 |
| Applied discounts | $8.00 |
| Tax | $1.98 |
| Printed purchase total before payment funding | $25.96 |
| Walgreens Cash used | $20.00 |
| Card paid / actual out of pocket | $5.96 |
| Confirmed valued benefits | $8.74 |
| Total saved under the existing formula | $49.72 |

$44.96 + $1.98 - $5.96 + $8.74 = $49.72. The $20 WC is already reflected in
cash/card spending and must not be added again. $20 WC + $5.96 card funds the
$25.96 purchase. The current purchase difference is $0; the legacy $20 difference
compares original checkout figures at different funding stages.

## Incomplete information

A recorded cash/card subtotal does not prove payment entry is complete. Original
unknown/partial payment status remains visible. A zero printed total does not
establish zero cash/card spending in the receipt payment-evidence display.
Legacy aggregate availability continues to use the existing SavingsReport rules;
it is not a new assertion of explicitly confirmed payment completeness.
Missing regular prices, payments or benefit valuations can leave savings unknown.
Review receipt details rather than substituting zero or changing historical facts.

## Search and filters

1. Open Receipts.
2. Search by store, printed receipt number (full or partial), or ISO purchase date.
   Receipt numbers match with or without spaces/hyphens; leading zeros are retained.
3. Select Purchase day for one date, or Month for a month and year.
4. After selecting Month, optionally select Week of month:
   week 1 = days 1–7; week 2 = 8–14; week 3 = 15–21;
   week 4 = 22–28; week 5 = days 29 through month end.
   These are fixed month segments, not Monday–Sunday calendar weeks. Nonexistent
   dates in shorter months do not contribute receipts.
5. Choose Newest first or Oldest first and an optional status filter.
6. Use Previous/Next. Changing a filter resets the page; Clear filters restores
   all-time browsing and newest-first order.

Day selection synchronizes the month and clears week selection. Month selection
clears day/week selection. Week selection clears the day. Filters run before
pagination, so searching can find receipts on any page.

## Period totals

The receipt page shows cash/card spending, confirmed benefit value and Total saved
for all active receipts in the selected day, month or week-of-month, across every
page. With no date selection it shows all-time totals. Search and status filters
only affect the table, not the labeled financial period totals. Deleted receipts
never contribute; latest revisions are used once. Undated receipts participate in
all-time totals but not dated periods. Empty dated periods have zero totals.

Java supplies these summaries in the same authenticated workspace response under
receiptPeriods: day:YYYY-MM-DD, month:YYYY-MM, week:YYYY-MM:N, and empty for an empty
period. The browser does not calculate money. There is no new summary endpoint.
If an older API omits receiptPeriods, selected-period totals request an API restart
instead of pretending to know the amount. Dashboard day/week/month summaries retain
their existing date basis; its Week remains the existing calendar-week calculation,
whereas the receipt selector explicitly says Week of month.

## Verification

Check exact headings, five-row pagination, compact receipt-number search, mixed
printed-time ordering, day/month/week boundaries, deleted/latest revisions,
all-page summaries, unknown values, and desktop/mobile layout. Run frontend unit
tests, typecheck, lint, build, and the fixture browser scenario. Run Java tests;
PostgreSQL integration tests require an explicitly configured disposable database.
Never use the persistent household database for tests.


### Verification recorded October 22, 2026

- Java: 554 tests, 514 passed, 40 skipped, zero failures/errors. Database-dependent
  tests were skipped without a disposable PostgreSQL configuration. New pure Java
  period tests cover six receipts in one day, week/month/year boundaries, latest
  deleted revisions, and missing-payment propagation.
- Frontend: 51 unit/component tests passed; TypeScript, lint and production build passed.
- New receipt browsing scenario passed in desktop and mobile Chromium emulation
  with synthetic workspace fixtures. It checks five-row pagination, compact receipt
  search, selected week totals independent of search, and page width.
- Browser runtime was downloaded to /private/tmp/coupon-receipt-browsers; if rerunning
  with that runtime, set PLAYWRIGHT_BROWSERS_PATH to that directory. No real database
  browser writes or production deployment were performed.

The repository currently ignores docs/ in Git. These operations/research documents
remain local under that policy; README.md and frontend/README.md are tracked.


October 23 update: Actual OOP period summaries expose the recorded cash/card
subtotal even when other payment amounts are missing, with a separate completeness
flag and visible note. This is not proof that all payments have been recorded.
An older API missing the field requests an API restart instead of showing a blank.
Search supports formatted dates, Unicode dash variants, and combined store/receipt
terms. While text search is active it covers all dates and statuses; selected
period summaries remain tied to their date selection. Clearing text search restores
the selected date/status filters.


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


October 23 item-level pricing follow-up: pricing guidance now sits inside each
product card beside its fields in creation and editing. Each card also shows its
linked discount labels and recorded totals. Shared multi-product discounts are
explicitly labeled as totals across selected products; the display does not invent
per-product dollar allocations. Receipt-wide discounts stay in Applied discounts.
The discount editor, stored links and all calculation rules are unchanged.


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


## Separate sale promotions and coupons in receipt totals — October 23, 2026

The shared financial review (saved receipt Totals and create/edit previews) now
shows Sale/promotion reductions, Manufacturer coupons, Store discounts / coupons,
Unclassified discounts, and Total listed discounts (sum above). The former Applied
discounts row no longer hides different categories in a single unexplained number.
The total is a subtotal of those rows, not another deduction.

Java supplies totals.discountBreakdown with salePromotions, manufacturerCoupons,
storeDiscounts and unclassified. It sums recorded CouponApplied amounts by explicit
ReceiptObservations.discountKinds: SALE_PROMOTION, MANUFACTURER, STORE/STORE_COUPON,
and OTHER/missing respectively. Descriptions are never used to infer a kind.
Unclassified historical entries remain unclassified until an explicit correction.
Implicit reductions already embedded in a legacy unit price remain represented by
the difference between regular merchandise and merchandise before listed discounts;
they are not deducted again in this listed-discount breakdown.

No saved financial value, subtotal formula, reward accounting or schema changed.
Restart the rebuilt Java API to load the added response field. Older APIs show a
restart notice and preserve the combined total rather than guessing category values.

## Receipt form readability — October 23, 2026

Text/date/time inputs and selections now share a 48px height with 16px value text.
Product and discount descriptions occupy the full row and expand to show long
text. Narrow dialogs stack their fields. Before funding / After reward use are
short captions for unchanged printed-total-basis values. Refresh to load styles.
The visual source of truth is ../reference/FRONTEND-DESIGN-SYSTEM.md.


October 23 sizing increase: controls enlarged from 48px to 60px and value fonts
from 16px to 18px. Modal/filter labels are 15px; horizontal control padding is
16px. Dialog maximum width is 1040px. Receipt editor/product fields are full-width
single-column even on desktop. Long descriptions expand. Current specification:
docs/reference/FRONTEND-DESIGN-SYSTEM.md; executable values:
frontend/src/index.css. SRS/SDD internal revision 1.5 supersedes 1.4 sizing.

## Printed footer, completeness and discount correction

Use Add/Edit Receipt → Printed Savings & Walgreens Cash for optional printed categories/percentage and WC opening/earned/used/closing. Blank means absent and zero means printed zero. These observations do not overwrite calculated savings or create account/payment events. Answer whether all receipt payments have been entered; entered cash/card already counts as Actual OOP. To remove an accidental receipt-wide discount, click Delete receipt-wide discount or clear both description and amount, review totals, give a correction reason and save the reviewed change. Partial discount entries must be completed. Receipt Totals includes saved footer observations and expandable linked RR details.


## Revision 1.7 — receipt RR correction and matching

To enter RR used: Edit receipt → Register Rewards used → Add RR used, one coupon amount per row. Add cash/card and WC under Recorded payments, choose printed-total basis and answer whether all payments are entered. Save the reviewed correction, then Rewards (or bottom of Totals) → Match Register Rewards Used → select the unused source coupon → matching note → Match RR. If $5 currently appears as a listed discount/Other or RR payment, use Move to RR used. Select “Already included in RR use …” first only when it is a duplicate of that exact recorded coupon. Amounts must match. Printed footer is below payments and can be shown/hidden. Never enter RR used again as a manufacturer coupon or cash/card payment.


## Revision 1.8 — RR without an earning receipt

If no eligible RR is listed and the earning receipt is unavailable: open Match Register Rewards Used, click Add RR without an earning receipt, enter the description/source note, leave unknown dates blank, confirm it was used on this receipt and click Save RR and match. Do not add the same amount again under payments, discounts or earnings.


## Revision 1.10 — independent Rewards inventory

To register first: Rewards → Add reward without a receipt → enter RR description/amount, leave unknown dates blank, confirm unused → Save reward. Later open the spending receipt, record each RR used once and save it; select the saved coupon in Match Register Rewards Used. If earning history is unknown, confirm it was available for that purchase. Match only: do not add its amount again as earnings or deductions.


## Revision 1.11 — visible recorded receipt calculations

Missing historical basis/completeness no longer hides arithmetic. Receipt calculation checks shows calculated totals and purchase/payment differences, with both scenarios if basis is unknown. To verify a scenario, Edit receipt → choose actual printed-total meaning → resolve Other/legacy RR payments → enter all tenders → confirm completeness → review/save. Do not enter fake payments or assert completeness just to make a zero appear.


## Revision 1.12 — simplified receipt totals display

Per user request, receipt totals/review no longer displays Calculated total on recorded basis, its before/after-reward alternatives, or the Receipt calculation checks table. Saved printed observations also hide Store Coupon Savings. Store coupon input remains available in Add/Edit Receipt; existing saved observations, API calculationChecks, Java formulas, strict validation, Actual OOP, Total Saved, legacy details and history remain unchanged. This supersedes revision 1.11 UI visibility only.


## Revision 1.13 — reuse existing files and align CLI rebate display

The visible receipt measure is Total Paid after rebates, sourced directly from savings.paidAfterRebates — the same SavingsReport value used by the CLI. Formula: recorded cash/card minus newly earned reward/rebate dollars, including saved USD point valuations. The frontend no longer uses the stricter benefitAdjustedCashSpending field for this spreadsheet measure; that compatibility field and its explicit-completeness semantics remain unchanged. Unknown historical payment-entry metadata alone does not hide the CLI value. Truly missing tender or dollar valuations still leave the CLI measure unknown, now explained as Cannot calculate yet. Actual OOP, reward use, Total Saved and stored facts are unchanged. Restart the Java API after source consolidation and refresh the frontend. No existing receipt needs to be marked Complete merely to display this CLI measure. Missing actual tender/valuation must still be entered when known.
