# distributed-coupon-system

For step-by-step help with editing, Detailed breakdown settings, and transaction,
day, week, and month reports, see the
[CLI breakdown and reports guide](examples/CLI_BREAKDOWN_AND_REPORTS.md).

Manual receipt entry (Issue #13, Checkpoint 2):

```sh
mvn clean test
java -cp target/classes coupons.ReceiptEntryApp
```

For local image reading, choose **9 / IMPORT**, then **PHOTOS**. Use **BROWSE**
for the desktop photo picker or **LIST** for numbered images. Select one receipt's
photos in top-to-bottom order, confirm, review the prefilled receipt, add REBATES,
and SAVE to approve. Tesseract runs locally; no hosted OCR service is called.
Walgreens is the initial supported layout. Unreadable required values are asked;
OCR is not guaranteed to read every amount correctly.

Existing prepared imports remain supported through IMPORT > LIST or a
`.properties` path. Original images, extraction and approval evidence are saved
with the approved receipt. See [receipt import instructions](examples/RECEIPT_IMPORT.md).

At new-receipt review, **PAUSE** keeps an unfinished receipt without crediting
balances. **10 / RESUME** at the main menu reopens it after restart. Completed review
states, completed entry sections, and completed products are checkpointed locally.
SAVE approves, PAUSE keeps a draft, and CANCEL explicitly discards it. Exiting
local storage keeps the last completed checkpoint. In-progress answers and
saved-receipt correction proposals remain session-only.


Type a printed receipt number directly to start, or select NEW. Enter time
as printed, an optional date, retailer, and entrant, then each item's optional
SKU, price, quantity, store discount, and manufacturer coupon quantity/value.
Line totals are calculated automatically. Tax, actual unit prices, quantities,
and the printed checkout total are required. Extended-only or unsupported receipt
structures must not be approximated. Amounts are entered without currency signs.

Record RR used without inventing an instrument when only the receipt deduction
is known. Existing instruments may still be recorded with menu 5. Normal receipt entry asks
only for each RR amount and source; it does not link instruments automatically.
Enter item, multi-item, receipt-wide, or unknown-scope earnings separately from
checkout deductions. Cash, points, gift cards, RR, and Walgreens Cash retain
their original forms; credit and payout do not create extra earnings.

Optional sections preserve all four printed Walgreens Cash account observations,
printed savings categories, and user-confirmed links to existing events. Partial
or negative reconciliation differences remain visible. Original evidence files
may be attached and associated with earning observations.

Review and EDIT before SAVE. Retrieve by printed receipt number or #session
number, inspect history, and retry the unchanged original save. Entering a receipt
does not create a provider account balance, make expected rewards available to
spend, or submit rebate claims externally. Saved observations are preserved with
the receipt; separate claim repositories and live reward ledgers remain future work.

The CLI saves receipts locally in `.coupon-data/receipts.bin` under the working
directory. Exiting and reopening from the same project folder restores receipts,
evidence, history, and the last original save request for retries. Repository
default constructors used by isolated tests remain memory-only. The repository supports
approved, revision-aware updates with immutable before/after history and original-result
retries, including after later revisions and local restarts. Existing redemptions cannot
be removed or altered, and new evidence is appended atomically. Menu 3 updates saved receipts using the revision originally loaded; EDIT within new
receipt entry continues to change unsaved drafts only.
Soft deletion is available from menu 4 and preserves contents, redemptions and history. Restoration is not implemented. The console is a single-user manual tool,
not an authentication system or a receipt parser.

Personal verification remains separate from automated tests: enter a real
receipt, compare retrieved lines and totals against the original, view history,
then retry and verify that only one history entry exists.


### Required financial views

Receipt entry, provider-account tracking, and period reporting are three required
views of the shopping records. Provider-specific earnings are central financial
records; the original observations and their history remain preserved underneath.

- Receipt: metadata; products, quantities, known regular/sale prices; separately
  recorded discounts and tax; reward redemptions; actual cash/card/gift-card
  tenders; item and receipt earnings in original units; and calculated summaries.
  Unknown regular prices and item tax remain unknown. Do not allocate receipt tax
  to products without an explicit allocation rule. Count each coupon only once,
  even when displayed in both line and receipt summaries.
- Provider account: provider and benefit unit, opening balance, earnings,
  redemptions, actual conversions, payouts, adjustments, closing balance, and
  pending/available status. Printed balances are observations, not substitutes
  for account movements. Conversion and payout do not create new earnings.
- Period: date range, receipt count, receipt-line count and product-unit count,
  known regular-price total, checkout total, post-rebate total, savings and
  savings percentage. Missing inputs must be visible rather than treated as zero.
  Provider columns group the underlying events without duplicating them.

Due at checkout, amounts tendered, cost after cash rebates, and effective cost
including valued benefits are distinct results. Expected and confirmed benefits
must remain distinguishable. Points retain their units; any monetary valuation
must have a recorded basis. Spreadsheet Converted valuations are not necessarily
actual account conversions. No duplicate value is earned when an existing benefit
is credited, converted or paid out. Savings can exceed 100% and effective cost can
be negative when the explicitly chosen calculation supports that result.

Persistent account openings, receipt-derived balances, and explicit external adjustments are supported.
Dedicated transfer/conversion workflows and period reports remain unimplemented.
The receipt CLI now supports local cross-session storage. The current receipt snapshots and printed WC
account observations do not by themselves provide those features.

### PostgreSQL provider ledger

`PostgresProviderAccountRepository` implements the database-independent
`ProviderAccountRepository` contract for provider openings, explicit
adjustments, and redemption status revisions. Apply the additive
`V3__provider_account_ledger.sql` migration through `PostgresSchema`; it does
not read or migrate `.coupon-data`. Configure isolated integration tests with
`COUPON_POSTGRES_TEST_URL`, `COUPON_POSTGRES_TEST_USER`, and
`COUPON_POSTGRES_TEST_PASSWORD`. Reported openings remain separate from
movement events, and redemption revisions are retained immutably with
optimistic concurrency checks.

Receipt dates accept month/day/year and year/month/day with slash, hyphen or dot
separators, plus common English month names. Two-digit years mean 2000–2099;
short dates use month/day/year by default. Year-first input starts with a
four-digit year or a two-digit value above 12, such as 26/10/4. The interpreted
date is acknowledged without another selection. Invalid calendar dates are rejected. This is not an arbitrary
natural-language date parser.


### CLI balance check

Payment-method choices appear directly; there is no preliminary yes/no prompt.
Choose Cash, Card, Gift Card, Other with notes, or Not recorded.

Menu 6 (Account balances) records explicit month-start and day-start USD values by provider
and date. Choose Both only when the same starting value applies to both periods.
Prior starting observations remain in persistent opening history when revised.
Menu 6 provides the full provider balance table. Successful saves and corrections
also show affected accounts with day start, day close, and next-day opening;
unsaved review and ordinary retrieval do not publish account activity. Unknown
starting values and missing point valuations stay marked not entered. Totals are
marked partial unless all contributing provider values are known.

The day-end column is an estimate using distinct receipts for that date, credited
or received earnings, recorded RR/WC redemptions, and gift-card tenders. Repeated
review and save retries do not add activity again. Point earnings use their saved
USD valuation once; rates are not automatically inferred here. Expected, pending
and approved-but-not-credited benefits do not increase these balance estimates.
Explicit external adjustments can record payouts, expirations, and other movements
with a source and entrant. Dedicated conversion/transfer workflows remain future
work. Starting observations, external adjustments, and saved receipts survive CLI
restart in the local receipt file. Daily balances carry forward from the latest
applicable confirmed opening using known receipt and external activity.


### Local receipt storage and time entry

Run `java -cp target/classes coupons.ReceiptEntryApp` from this project directory.
The CLI prints the absolute storage location and number of restored receipts.
SAVE writes a versioned local checkpoint before reporting success; main-menu Exit
closes storage without removing saved records. Unsaved drafts are not persisted.
A custom storage path may be supplied as the first command-line argument.

The complete checkpoint retains original snapshots, evidence bytes, financial
IDs, history and committed request results. Writes use a forced temporary file
and atomic replacement; failed writes do not publish new in-memory state.
One running CLI owns the file lock. Unreadable or incompatible files are reported
rather than replaced with an empty store. This local binary format is separate
from future database storage; future schema changes may require file migration.
The data directory is excluded from Git. Provider balance starting settings now persist in the same checkpoint. Receipts from previously closed memory-only sessions cannot
be recovered from this new file.

Time entry accepts `940 am`, `9:40 am`, `940pm`, and 24-hour forms such as `21:40`.
The interpreted time is acknowledged and displayed as `9:40 AM` or `9:40 PM`.
Original entered time text is retained in the receipt snapshot. Invalid newly
entered times are re-prompted; historical unrecognized text remains readable.


### Required Walgreens receipt footer

After earnings entry, Walgreens receipts ask whether the savings/rewards footer
is printed (default Yes). No saves the footer as Not printed and skips its fields,
without changing entered earnings or payments. Yes asks for Receipt myWalgreens
Savings, Receipt MFG Coupon savings, Receipt Total Savings, and Receipt Saving
Value (the printed percentage; either 43 or 43% is accepted). Then the CLI asks
for Receipt Opening Balance, Receipt Earned This Visit, Receipt Redeemed This
Visit, and Receipt Closing Balance. Each field requires a valid numeric value;
enter 0 only when that is the printed amount. The footer question is asked once before savings; there is no separate account-used
confirmation question when the footer is present. The independent printed values, including discrepancies,
are retained in snapshots and local storage. Historical receipts without these
observations remain readable. The footer does not create additional earnings.

For pre-save corrections, use EDIT > SAVINGS or EDIT > ACCOUNT. Optional structured
savings explanations remain available under EDIT > SAVINGS LINKS. Editing financial
entries clears stale confirmations but preserves the printed amounts themselves.


### RR correction and menu navigation

Action menus show one numbered option per line. Enter the displayed number or
the original command, such as SELECT, EDIT, DONE, or BACK. Item field selection
keeps the selected product until you choose another. Done returns to receipt
review; Save is still required to persist edits. Balance settings place Opening
history at option 5 and Back at option 9.

Menu 5, option 3 removes a misentered separately registered RR from the active
coupon list after confirmation, reason, and confirmed-by entry. The original
instrument and immutable removal audit remain stored; receipt earnings are not
deleted. Coupons linked to a receipt or redemption cannot be removed through
this standalone correction. Voided coupons cannot be redeemed, and the removal
survives later saves and CLI restart.

BACK at the balance date/action menu returns to the main menu; inside an opening
or adjustment form it returns to the previous question. EXIT closes the CLI,
with confirmation when uncommitted answers would be lost. Navigation commands
are handled before date or numeric parsing. The main menu is separated
from the preceding output by a blank line. Receipt display omits zero-discount
repetitions and redundant printed-RR status text. A simple receipt-wide WC earning
already represented by the printed account earned amount is not repeated as a
separate block; detailed underlying records remain in the saved snapshot.


### Checkpoint 3: correct saved receipts

Choose menu 3, then a printed receipt number or #session number. The editor shows
the original revision and both saved and proposed receipt views. Supported sections
are DETAILS (retailer, number, date/time), ITEM (existing line description, SKU, unit
price and quantity), TOTALS (printed tax and final total), COUPONS (shared manufacturer coupons), EARNINGS, ACCOUNT, and
SAVINGS. Existing item discounts and regular prices are retained; review their
applicability after correcting an item. Payments, linked redemptions, individual-item coupons,
and evidence are preserved; this editor does not replace those records. Financial
corrections clear savings-link confirmations while preserving printed amounts.

SAVE approves and commits the correction as Local owner. Optional REASON records a correction reason before SAVE; no second approver prompt is shown.
A successful update displays the new revision and history. Failed writes retain
the proposed edits; SAVE retries the same captured request until another edit is
made. A revision conflict retains the proposed receipt for review and never
automatically overwrites or rebases the newer saved revision. CANCEL requires
confirmation before discarding the proposal; proposals are session-only and are
not recovered after closing the CLI. Menu 8 > RETRY repeats the original creation request.

Historical revisions retain original financial IDs, benefit units, point amounts,
and recorded valuations. Current RR listings use the latest receipt revision.
The later account extension adds persistent openings and automatic carry-forward.
Receipt deletion and restoration remain unimplemented.


### Manufacturer coupons covering multiple product lines

Enter a shared coupon once, rather than repeating its value on each product.
For example, enter the $1 Charmin coupon on its item line, but omit the $3 H&S
coupon from both individual H&S lines. After all products, type COUPONS at the
tax prompt, confirm Add a shared manufacturer coupon, and choose the shampoo and conditioner numbers
(e.g. 2,3). Enter the description, total deduction 3.00, and required products 2.
The tax prompt returns afterward; type COUPONS again for another shared coupon.
A single line containing multiple units continues to use the item coupon fields.

Use EDIT > COUPONS before saving, or menu 3 > COUPONS for a saved receipt.
Choose CORRECT, then an existing shared coupon number to correct its description, amount,
product links, or quantity requirement while retaining its financial event ID.
BACK at product selection returns to the previous question; CANCEL confirms cancellation of that coupon entry. Product numbers must be
distinct and valid, with at least two selected lines. Reviews list both linked
products and deduct the coupon once. Saved corrections retain the original
revision in history and clear stale savings-link confirmations.

The three-line October 4 example calculates 35.96 merchandise minus 4.00 product
coupons minus 7.00 RR used plus 1.73 tax = 26.69. Sale savings already reflected
in unit prices must not be entered again as separate deductions.


### Reward earnings covering different products

When adding a reward immediately after an item, Applies to offers: 1 Current
item, 2 Select multiple products after all items are entered, 3 Whole receipt,
and 4 Unknown. Choose 2 for an RR earned by buying both H&S shampoo and
conditioner. Enter that reward once. After the final product, the CLI names each
deferred earning and asks for the associated product numbers (for example 2,3).
Choose at least two different lines; 0 explicitly changes the scope to whole
receipt and U to unknown. VIEW marks unresolved associations as awaiting product
selection. BACK from the amount question lets you revise the scope choice.

The selected products share one earning ID and one benefit amount; this does not
reduce checkout or duplicate earnings. Associations survive save, history, retry,
and restart. Additional earnings and corrections after item entry continue to
use the existing product-number selection.


### Shared-coupon navigation and all-product earnings

When no shared coupons exist, the CLI asks whether to add one and then goes
directly to product selection. With existing coupons, choose ADD, CORRECT, or
BACK; only CORRECT asks for a coupon number. Product selection accepts numbers
such as 2,3. Earning product selection also accepts A for all currently listed
products, preserving explicit product IDs rather than an unassociated whole-
receipt scope. For the described P&G RR, record Spend 35, reward 10, select all
three eligible products, and include sale/promotion prices before coupons in
the offer/notes field. The sale-price total is 35.96. These are user-recorded
terms and observations, not automatic eligibility verification.

### Online gift-card promotion progress across receipts

Menu 7 (Gift-card promotions) provides NEW, VIEW, LINK, REVIEW, STATUS, and BACK. NEW
records the actual offer name, required spend, gift-card value, inclusive purchase
dates, spend basis, and offer terms/eligible-product notes. No P&G online offer
threshold or reward value is prefilled; use the terms of your particular offer.
Link during receipt entry with PROMOTIONS at the tax prompt or EDIT > PROMOTIONS,
or save a receipt first and use LINK in menu 7. Select eligible product numbers
or A if all listed products qualify. Add a note explaining eligibility. Link
additional receipts to accumulate progress. Linking the same receipt replaces
its previous contribution, so repeated review does not increase progress.

Sale/promotion prices before coupons are recomputed from the selected lines in
the latest saved receipt revision. For after-coupon or other rules, choose Reviewed
eligible amount and enter the amount calculated under the actual offer terms;
this avoids inventing an allocation for a shared coupon. These manually reviewed
amounts require re-linking after any receipt revision. Missing products, unknown
dates, and purchases outside the offer period are marked needs review and excluded
from known progress; totals are labelled partial when any contribution is unresolved.

VIEW shows receipt references, reviewed/current revisions, selected products,
notes, progress and remaining known spend. STATUS records Tracking, Submitted,
Approved or Received. It does not submit a claim, create additional earnings, or
credit an available gift-card balance. A full gift-card/provider account ledger
remains future work. Promotion settings and links use the same locked atomic local
checkpoint as receipts and survive restart; previous receipt files load with an
empty promotion collection. In-memory repositories remain session-only.


### Automatic reward spend requirement checks

When entering an earning with a Spend requirement, enter its threshold and
choose the qualifying spend basis. Sale/promotion prices before coupons enables
automatic arithmetic verification. Other/unknown keeps the requirement recorded
but displays Cannot verify; historical earnings without a saved basis also remain
unknown until explicitly corrected. No basis is inferred from offer notes.

Select the eligible product numbers (or A for all listed products). Whole-receipt
or unknown scope cannot verify product eligibility and displays a request to
select product lines. Review and retrieval show qualifying spend, required spend,
and either requirement met with the excess or not met with the shortfall. The
calculation includes selected line prices times quantities minus explicit
sale/promotion reductions, before store/manufacturer coupons and redemptions,
and excludes tax. Legacy combined store deductions require classification. It does not use regular prices.

For the P&G example, select Spend 35.00, sale/promotion prices before coupons,
and all three product lines, then record the 10.00 RR and its actual status.
21.96 + 7.00 + 7.00 = 35.96, so the spending requirement is met by 0.96. Receipt
corrections automatically recalculate the check while old snapshots retain their
original values and basis. A failed check does not erase an observed printed RR,
change its status, or create earnings. Product eligibility and other promotion
conditions remain user-confirmed; the check verifies the recorded spending rule.


### Persistent provider balances and receipt-side online rebate notes

Before tax, type PROMOTIONS to select an online offer, eligible products, and an
eligibility note. If no offers exist, the CLI lets you create one, then BACK to
link this draft. For P&G's online offer, select only Charmin if only that line
qualifies. Keep the separate in-store RR linked to all eligible P&G products,
and record brandSAVER points as a separate earning. Enter the actual online
terms and reviewed eligible amount; do not assume the RR's before-coupon basis.
Use EDIT > PROMOTIONS to correct draft eligibility, or menu 3 > PROMOTIONS for
a saved receipt. Financial draft edits invalidate manually reviewed eligible
amounts until PROMOTIONS is used again; the eligibility note is retained.

Receipt SAVE commits its eligibility observations and promotion contribution
in one atomic checkpoint, including history and retry data. Failed writes publish
neither receipt nor progress. Unchanged reviewed contributions require review
after a saved receipt correction; an explicit PROMOTIONS review updates the
contribution to the new revision. This records progress only, never gift-card
receipt/use or extra earnings. Eligibility appears in receipt review and history
snapshots and survives restart.

Menu 6 SETTINGS month/day/Both actions now persist USD observations. Action 6
adds an opening with original point count, independent USD valuation, valuation
basis and source. Leave unknown values blank; monetary accounts need no point
count. Month observations take effect at the beginning of the first day; day
observations take effect before that day's receipts. The latest applicable date
wins, with a daily observation taking precedence over a monthly one on the same
date. Observations replace a baseline rather than being added as earnings. Earlier
observations remain in history. A mismatch against earlier calculated carry is
shown as a reconciliation difference, including after backdated corrections.

Day start/end are derived from that baseline and latest committed receipt
revisions, with automatic carry-forward across days and months. Multiple receipts
are combined once; repeat views and write retries do not add activity. Original
point counts and exact USD valuations remain separate; rounding is for display.
Missing baselines, valuations, or undated activity stay unknown. Expected/pending
rewards and online promotion progress do not increase balances. Activity is
currently positioned by receipt purchase date, not a separately recorded credit
date; calculations cover only known receipts and external movements.

Action 7 records signed external point/USD adjustments with reason, source and
entrant; do not repeat a movement already captured by a receipt. Action 8 shows
source receipt IDs/revisions/event IDs and external adjustments. Action 9 shows
opening history. These settings use the same atomic local checkpoint. Existing
files without account settings load with empty settings; unknown openings are
never assumed to be zero. Spreadsheet figures have not been automatically imported.

Target Gift Card and Target Circle are separate account choices. Enter 15 and
20 respectively for the supplied opening, rather than also entering a combined
Target 35. When using gift-card or Other tender, name the redeemed account so
its balance decreases once. An unassigned gift-card tender stays in the generic
Gift Cards category; it is not guessed to be a Target card. Do not repeat a
named gift card under another opening. Full paired account conversions and
provider-statement import remain future workflows.


### Consistent CLI navigation and promotion details

Every input prompt recognizes BACK, CANCEL, and EXIT without treating them as
amounts, dates, product numbers, or names. BACK returns to the previous question
within a form and retains earlier answers. At the first question it returns to
the parent section/menu; at tax it can return to the existing product list for
ADD, EDIT, or DONE. Escape followed by Enter (or the literal ^[) also means BACK.
Existing item and earning question navigation remains available.

CANCEL asks for confirmation before abandoning the current operation. Completed
receipt sections remain in the draft when a subsection is cancelled. Cancelling
a whole receipt at review or abandoning proposed saved corrections requires
confirmation. EXIT asks before discarding an active receipt or uncommitted form.
Declining a discard leaves the current question and draft available. BACK/CANCEL
at a discard confirmation declines it. Returning from attachment entry retains
the receipt. Drafts still do not survive closing the CLI.

The promotion menu immediately shows qualifying spend / threshold, still needed,
and potential gift-card value for every offer. VIEW groups selected products by
printed receipt number and shows quantity and sale total for each product, the
reviewed eligible amount for that receipt's selected products, and its note.
A reviewed combined amount is not allocated to individual items. Repeated views
do not change progress. Missing/stale contributions show needs review and are
excluded from known progress; the summary is labelled partial until reviewed.
The list and detail use the same calculation from current saved receipt revisions.


### Sale-adjusted spend, combined reward blocks, and RR usage

New item entry distinguishes separately entered sale/promotion reductions from
store coupons. For a positive combined deduction, select Sale/promotion, Store
coupon, or Both and enter the sale portion. Do not enter a sale reduction again
when the unit price already includes it. Manufacturer coupons remain separate.
The spend check subtracts sale reductions once, including shared reductions, but
never subtracts store/manufacturer coupons, RR, WC, or tax for the before-coupon
basis. If a shared sale reduction crosses selected/unselected products, explicit
allocation is needed rather than guessed. Negative qualifying totals also need
review. Gift-card offers using the before-coupon basis share this calculation.

Existing combined STORE/unknown deductions are preserved. Use menu 3 > DISCOUNTS
to classify each deduction, then 0 to finish and SAVE with reason/confirmation.
Classification retains original coupon IDs, amounts, product links, and history.
For the described receipt, classify 8.00, 1.49 and 1.49 as sale reductions; leave
the 4.00 product manufacturer coupons classified as manufacturer coupons.
46.94 minus 10.98 = 35.96. A genuinely mixed deduction requires item correction
to split it. Drafts also support EDIT > DISCOUNTS. Classifications invalidate stale
savings confirmations. Existing manually reviewed rebate amounts are retained when
only ambiguous discount labels are classified and amounts/product links are unchanged.

Each spend reward now has one block: reward/program and offer, required spend,
basis, selected products, qualifying spend and result, recorded benefit, and
status. The duplicate separate spend-requirements section is removed. A spend
30 offer on this receipt is met by 5.96; spend 35 is met by 0.96.

After a successful receipt save or correction, an ACCOUNT BALANCES AFTER SAVE
panel shows affected accounts' day start/close and next-day opening. Corrections
include old and new accounts/dates where relevant. The view reuses current
committed receipts and confirmed openings and never adds movements. Missing
openings remain unknown with menu 6 instructions; separately confirmed next-day
openings override the derived carry and are identified for reconciliation.

Menu 5, option 1 shows total printed RR earned from saved receipts, total RR used
on saved receipts, printed coupons not linked as used, pending/other observations,
separate physical coupons, used coupons, and unmatched receipt-reported uses.
Separate existing instruments are not counted as newly earned receipt rewards.
The active list is unreconciled inventory, not a guarantee of spendability; verify
expiry and match unidentified uses before relying on individual coupon status.

Menu 5, option 4 links an already recorded RR deduction to the actual earlier
printed earning or separately registered coupon. Choose the spending receipt,
its deduction, the source coupon, and record a source confirmation, entrant and
explicit confirmation. Amounts must match exactly; matches are never inferred
from a name alone. A source or deduction cannot be matched twice. The coupon
then leaves active lists and appears USED with its spending receipt. Original
earning and deduction history remain. No second redemption or balance deduction
is created. Opening RR without a recorded instrument stays an unmatched use
until its source is explicitly identified; no instrument is invented.

Usage associations are atomically persisted and survive restart and other writes.
Original-result retries do not duplicate them. If later receipt corrections
change or remove their source/deduction, they remain reserved as USED but are
marked needs review; removed earnings remain visible through immutable history.
Reconciliation of an invalidated association requires review rather than silently
assigning a different coupon. Historical receipt data has not been rewritten.


### Promotion review and RR monthly summary

The main menu and RR action menu display one option per line. Menu 7 separates
promotion totals from linked purchases, prints each product once, and calculates
sale totals after explicit sale reductions. Manufacturer coupons tied only to
that product are shown separately; shared discounts are never allocated by guess.

Unrelated receipt corrections retain manually reviewed promotion contributions.
The comparison uses the immutable reviewed revision: selected product details,
quantities and prices, relevant discount amounts and product links, and purchase
date. Classifying an old ambiguous discount without changing its amount or links
does not discard the manually reviewed amount. Changing a previously explicit
sale/coupon classification requires confirmation. Changed eligibility displays
the previous amount, the reason, and REVIEW (select the promotion and receipt,
then confirm the products, eligible amount and note).

When saving a receipt correction, promotion confirmations appear before final
save approval. Changed eligible amounts are confirmed during that flow. The
final receipt approval commits those confirmations and receipt revision together;
there is no second approval in menu 7. Missing products or invalid purchase dates
remain unconfirmed and are identified with instructions to use PROMOTIONS.

Menu 5 > 1 starts with a table for each recorded purchase month: RR earned,
RR used and calculated closing balance. It also shows the available account
balance as of the latest dated receipt and a table of total RR used per receipt.
Openings and external adjustments affect balances; separate physical coupons
are not counted again as earnings. Unknown openings remain unknown. Unmatched
redemptions already reduce the balance, so matching a physical coupon never
subtracts them a second time. Coupon expiration is not inferred.


### Compact account and reward views

Account balances show only providers with nonzero dollar or point balances in
one of the displayed periods. Opening sources remain in opening history rather
than under each row. A combined Target opening is excluded from this summary
when Target Gift Card or Target Circle accounts have been recorded; the original
opening remains stored. Missing values on visible accounts show Missing data and
incomplete totals remain partial.

Receipt earnings are grouped as Register Rewards, Walgreens Cash, then Other
Rebates. RR summaries have table borders. Active coupons sort by purchase date
and parsed receipt time (unknown dates/times follow known values). Earnings on
the same receipt retain their order. Used coupon rows show their original source,
amount, status and spending receipt. Confirmation reasons, actors and products
remain stored for later research. Empty expected/separate coupon sections and
already-matched deduction detail are omitted. Promotion purchases use bordered
field/value tables with wrapped product names and notes.


### Owner actions and spreadsheet receipt totals

The main menu is now: 1 Enter, 2 Retrieve, 3 Update, 4 Delete, 5 RR,
6 Account balances / Savings reports, 7 Gift-card promotions, 8 History / Retry, 9 Import, 10 Resume unfinished receipt, 11 Exit.
Option 6 immediately displays balances for the latest recorded receipt date;
SETTINGS opens dated openings, adjustments, source activity and history.

In this local single-user CLI, SAVE approves and commits the reviewed receipt
correction without a separate approver name or yes/no question. Optional REASON
records an explanation before SAVE; otherwise the owner-save reason is recorded.
Changed promotion eligibility is resolved while editing or with PROMOTIONS before
SAVE. The repository still rejects stale revisions and preserves atomic retries.
This local owner convention does not add authentication or multi-user permissions.

Delete displays the selected receipt and requires the explicit DELETE action.
It changes status and appends history atomically without erasing receipt contents,
original redemptions or audit facts. Deleted receipts leave current balances and
promotion progress; history remains accessible in menu 8, including after restart.
A deleted receipt's recorded RR usage remains reserved for reconciliation.

Update > RR NOTES edits an unmatched redemption note without replacing its event
ID or creating another deduction. The notes appear in the unmatched RR table.
Monthly RR tables include the calculated start balance; missing month openings
remain unknown. Active RR columns are date, time, products, source, amount, status.

Receipt reward groups use bordered tables. The receipt totals section includes
regular and recorded merchandise, embedded/separate sale savings and store
coupons, manufacturer coupon applications/value, reward redemptions, tax,
checkout totals, cash/card/gift/other payments, cash rebates, gift-card/store
rebates, original provider points, saved dollar valuations, total newly earned
value, net cash/card cost and total savings. Expected-only and rejected benefits
are excluded from earned totals; original points are not added to dollars.
Missing valuations produce partial totals. Online rebate progress creates no
new earned value. Shared discounts are counted once; no per-item allocation is
guessed. Payment amounts remain independent recorded facts, not inferred tenders.

For the supplied first receipt, 12.00 RR + 0.80 WC + 0.025 Fetch value = 12.825,
displayed as 12.83. Net cash/card cost is 3.60 - 12.825 = -9.225 (display -9.23).
Regular merchandise plus tax is 23.20, giving 32.425 savings (display 32.43).
All sums retain original precision until display. Existing saved receipts are not
silently imported, reclassified or given inferred valuations.


### Enter monthly openings once; preserve point valuations

Monthly openings and their original point counts/USD observations persist in the
local receipt checkpoint. Future days and months derive their start from prior
saved activity until another confirmed opening replaces it. Re-entering an
identical opening does not append it again; repeating a simple USD opening also
retains original point counts already recorded for that same opening.

Points-based receipt entry requires a USD valuation. An explicitly saved value
for the same provider and exact point quantity can be reused by pressing Enter.
If saved values conflict, or the quantity differs, enter the actual valuation;
opening balances are never treated as exchange rates. No earning or balance is
credited a second time by reusing its valuation. Missing legacy valuations are
identified by provider and receipt/event rather than labelled Pending.

For an explicit spreadsheet reconciliation, prepare a private tab-separated file
under `.coupon-data/` with six fields: type (OPENING/EARNING), ISO date, exact
provider name, original points (blank for monetary openings), USD value, and
printed receipt number (blank for openings). OPENING dates are month firsts.
EARNING rows must match one existing receipt and one original point earning.
Preview with `java -cp target/classes coupons.SpreadsheetBalanceImport
.coupon-data/receipts.bin .coupon-data/observations.tsv`; add `--apply` to save.
Close the running CLI first. Apply creates a backup and appends receipt revision
history/opening observations; it does not create receipts or additional earnings.
Each receipt update and opening write is atomic; if a multi-receipt import stops,
rebuild its plan and repeat to finish only the remaining changes. Reapplying an
unchanged source makes no further corrections. Keep these private files out of Git.


### Compact RR labels and spreadsheet totals

RR offers use short product labels and recorded requirements (for example,
Charmin — buy 4, get $5 RR). Multi-brand P&G offers use their recorded P&G terms.
Display references are one-based receipt line numbers; original UUID associations,
full product names, quantities and offer text remain stored. Requirements are
never inferred from the quantity purchased; absent terms use RR — receipt lines.

Receipt Totals omit known displayed zero rows and retain unknown/partial values.
Regular merchandise sums explicit regular unit prices times quantities. Regular
total with tax adds tax once. After promos + tax subtracts store/promo reductions
from recorded line totals and includes tax. Before cash rebates additionally
subtracts manufacturer coupons and rewards used; cash/card payments are separate
observations. The supplied first receipt reconciles to 23.20, 19.60 and 3.60.

For new products, enter a price as regular/entered (for example 7.49/5.49) when
the entered unit price is already a sale price. A single unit price remains valid
but does not invent an unknown regular price. Menu 3 > REGULAR PRICES records
regular prices for existing lines while preserving sale prices and deductions.
The importer also supports REGULAR_PRICE rows: use the receipt line UUID in the
subject/provider column, blank points, and regular unit price in the USD column.
USD import values accept an explicit numerator/denominator formula. Division
uses DECIMAL128 precision and monetary rounding occurs at display; 250/150 P&G
points are not prematurely rounded to 1.67 before calculating net cost.


### Transaction, daily and monthly savings reports

Retrieved/reviewed receipts use a compact vertical RECEIPT TOTALS table with regular
price including tax, retailer-reported due, separate payments, new RR and WC,
other earned value, total future value earned, paid after earned benefits,
total saved and percent saved. A second wide Savings Summary is not printed.
Menu 6 shows balances; choose SAVINGS, then TRANSACTION, DAY, WEEK or MONTH for an
explicitly requested summary. DETAILS prints the selected report's item/provider breakdown.
Dates accept the existing receipt-date formats; months accept YYYY-MM or M/YYYY.
BACK works at each selection and Exit remains option 9 in the main menu.

The breakdown distinguishes explicit sale/promotion reductions, store coupons,
manufacturer coupons, classified coupons only, combined/unclassified discounts,
sales plus all coupons, prior RR/WC used, gift-card/other tender, new RR/WC earned,
and each app's saved USD earnings/point valuations. Legacy mixed store/promo
amounts stay in the unclassified row until corrected rather than being guessed
as coupons. Zero-valued known breakdown rows are omitted.

Paid after rebates equals cash/card tender minus newly earned value. Total
saved equals regular price plus tax minus that net cost. Prior balances spent
are shown separately from coupons and can affect the cash/card outlay metric.
Thus total saved may exceed the purchase's regular price. Missing tenders are
not inferred from reported totals. Point units remain separate in the receipt
and balances; only their saved dollar valuations enter savings arithmetic.

Only confirmed APPROVED, CREDITED or PAID earnings are included; expected,
submitted, pending and rejected benefits and unfinished online offer progress
are excluded. A printed WC earning aggregate is used only when its earning
components are absent, so it is not added twice. Missing regular prices or
valuations are identified by receipt; percentages are unavailable when inputs
are incomplete or the regular total is zero.

Daily/monthly reports select recorded purchase dates, count saved receipts and
exclude undated records (with a count). They use the latest active revision,
replace corrected activity, exclude deleted receipts and never create a financial
movement. Monthly percentages are calculated from aggregate amounts, not averaged
receipt percentages; precision is retained until the final display rounding.
The two saved example receipts aggregate to 71.87 regular with tax, 30.29 due,
-21.73 paid after earned value and 93.60 saved (130.23%). The spreadsheet's third
receipt must be entered before the app's day/month totals include that purchase.


### Detailed reward and payment reporting

Savings breakdowns show Register Rewards used, Walgreens Cash used, cash paid,
card paid, Register Rewards earned and Walgreens Cash earned separately.
Period price tables split redeemed amounts into RR used, WC used and Other used;
Other used means gift-card or named account balances. The receipt/account table
below identifies each such account. Rebate tables separate RR and WC earnings,
with receipt/provider details and original units alongside saved USD valuations.
Register Reward earnings are labeled Register Rewards rather than store coupons;
ordinary store coupons/IVC remain checkout deductions.

The Register Rewards submenu is: 1 View, 2 Add existing, 3 Delete misentered
separate coupon, 4 Match RR used to coupon, 5 Back to main menu. Used coupons
show the purchase date of the spending receipt, including deleted receipt history
when available; unknown dates are displayed explicitly rather than inferred from
when the association was confirmed.


Zero-total receipts: choose payment method 0 (No payment needed), or enter 0
in a payment amount field. No zero-value payment is created; existing rewards
used remain recorded. Enter 0 for the printed final total. A printed zero total
is a known no-payment checkout, not missing tender. A positive total with no
recorded tender still reports missing payment. Total due remains visible at $0.00.


Receipt product references in coupon/reward details, online eligibility notes and
REWARDS ALREADY ENTERED use one-based receipt line numbers. The numbered item list
retains full product names. Whole-receipt and unknown associations remain explicit;
selecting all individual products still shows their line numbers. Active RR labels
put these line references before their recorded offer terms. Used RR tables put
Date used first, using the spending receipt purchase date. These are display
changes only; original product UUID links and offer descriptions are retained.


### Simplified RR matching

Menu 5 > 4 first selects the receipt where the coupon was USED. Already-matched
deductions are hidden. With one unmatched deduction, the CLI goes directly to
unused matching-value source coupons; with several, choose the deduction first.
Candidates show their original earned date/time and source receipt/coupon. Choose
a coupon explicitly, then confirm the earned-to-used receipt summary. Matching
amounts never automatically establish a source. None of these, 0/Back and declining
confirmation save nothing. Local-owner confirmation and timestamp are retained
without additional reason or actor prompts. The existing deduction is not posted again.


### Change one field and finish editing

In receipt review choose EDIT and a section. DETAILS, ITEM, CHECKOUT, ACCOUNT,
SAVINGS, EARNINGS, COUPONS and DISCOUNTS offer individual fields. Choose a field,
enter its value, then DONE to return to receipt review with completed edits retained.
Saved-receipt correction uses the same field editors; TOTALS and REGULAR PRICES
also support individual changes. ALL opens the previous full-section form.

Example: EDIT > CHECKOUT > TAX > .08 > DONE > SAVE changes tax without clearing
or reentering RR, WC, payments or the printed total. CHECKOUT supports TAX, RR, WC,
PAYMENTS and PRINTED TOTAL. Select existing entries when changing amounts so IDs
remain unchanged. Zero removes an unsaved draft payment/deduction; saved receipt
corrections remain subject to the existing immutable-redemption rules.

DONE works during every edit section, including worksheet metadata, promotion
links, savings explanations and evidence entry. A partially answered field or
attachment is discarded; previously completed fields/sections remain in the draft.
BACK returns to an earlier question or field menu; CANCEL confirms discarding only
the active field/form. Use DONE followed by SAVE to approve all retained changes.
Nothing is persisted until SAVE. Changing point quantities clears the old valuation
until explicitly reentered through VALUE; it never invents a conversion rate.


### Compact receipt results and optional period reports

RECEIPT TOTALS uses a narrow label/amount table with right-aligned values and
blank lines between prices, payments, new earnings and final results. Known zero
optional rows and redundant intermediate totals are omitted. Retailer total due
is always visible, including $0.00. Amounts and percentages are calculated before
rounding; negative cost displays as -$3.24.

TOTAL FUTURE VALUE EARNED means new confirmed benefits generated by the receipt,
including saved point valuations. It is not the available account balance.
Expected/unconfirmed benefits have a separate row and do not reduce paid-after
cost. Missing valuations keep the relevant totals partial and percent saved N/A.
Previously earned rewards used on the receipt remain separate from new earnings.

No day/week/month report prints automatically under a receipt. Request those
through menu 6 > SAVINGS > DAY / WEEK / MONTH. Those summaries use the same compact
layout. Choose DETAILS only when the detailed savings breakdown, worksheet columns
and provider/item totals are wanted. Reports remain read-only and use the latest
active receipt revision once.

### Saving receipts with unresolved online rebates

An online rebate link whose reviewed eligible amount is missing no longer blocks
receipt saving. The receipt, history, and pending link save together; the link
remains excluded from confirmed progress until reviewed in menu 7. Missing amounts
are never substituted with zero. The save confirmation identifies pending reviews.

Menu 6 automatically displays daily balances, Sunday-based week-to-date account
movements, month-to-date account movements, and weekly/monthly savings summaries.
The reference day is the latest saved purchase date, or today when no dated
receipts exist. Periods stop at that day. Account Added/Used columns include
external movements; they are not automatically new earnings.

### Receipt issues and order type

Receipt review and retrieval show a dedicated RECEIPT ISSUES section. ISSUES lists
current concerns, affected fields, and correction actions. Fixed concerns disappear;
recovery provenance stays in evidence rather than appearing as an unresolved error.
SAVE approves non-blocking concerns without a second stale-note confirmation.

Edit > Receipt details > Order type records In store, Online, or Unknown. New
manual entry also accepts ONLINE at the retailer prompt before the store name.
Online orders cannot earn or redeem Register Rewards under the configured rule.
Both the CLI and repository enforce this; existing contradictory data is preserved
for correction and cannot be saved as an online order. Old order types default to
Unknown rather than being inferred. Import files accept receipt.channel.
# Optional PostgreSQL receipt repository

The CLI continues to use its existing local serialized receipt store by default.
PostgreSQL is opt-in and does not migrate, open, or modify files under
`.coupon-data`.

Milestone 1 provides a PostgreSQL adapter for the complete
`TransactionRepository` receipt contract, including receipt revisions, evidence,
Register Rewards and redemptions, history, and idempotent write requests. The
adapter currently rejects receipts containing gift-card promotion links until
that related aggregate can be persisted atomically as well.

The checked-in migration is:

```text
src/main/resources/db/migration/V1__receipt_repository.sql
```

Milestone 2 adds opt-in PostgreSQL implementations for source artifacts,
inventory balances, advertised deals, and rebate claims. It uses the additive
`V2__catalog_deal_rebate_inventory.sql` migration. Deal and rebate writes
validate referenced source artifacts; deal, rebate, and inventory corrections
use PostgreSQL transactions, immutable history, and optimistic revision checks.
The CLI and local repositories remain unchanged.

Milestone 3 adds additive PostgreSQL persistence for shopping plans, planned
purchase lines, and clipped provider coupons through
`PostgresShoppingPlanRepository`, `PostgresPlannedPurchaseRepository`, and
`PostgresClippedCouponRepository`. Plans and editable records retain revisions
and use optimistic concurrency. Coupon redemption checks expiration and is
protected by database uniqueness constraints against duplicate use. These
repositories do not change the CLI or local storage.

The additive migration is:

```text
src/main/resources/db/migration/V4__shopping_plans_and_clipped_coupons.sql
```

The adapter requires explicit configuration:

```text
COUPON_POSTGRES_URL
COUPON_POSTGRES_USER
COUPON_POSTGRES_PASSWORD
```

The integration tests are isolated and disabled unless a dedicated test
database is explicitly configured:

```text
COUPON_POSTGRES_TEST_URL
COUPON_POSTGRES_TEST_USER
COUPON_POSTGRES_TEST_PASSWORD
```

Use a disposable PostgreSQL database or container for those tests. Never point
the test variables at a database containing real receipt data.
