# distributed-coupon-system

Manual receipt entry (Issue #13, Checkpoint 2):

```sh
mvn clean test
java -cp target/classes coupons.ReceiptEntryApp
```

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
default constructors used by isolated tests remain memory-only. Update, soft deletion, and restoration
are explicitly not implemented yet. The console is a single-user manual tool,
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

General provider-account ledgers and period reports remain unimplemented.
The receipt CLI now supports local cross-session storage. The current receipt snapshots and printed WC
account observations do not by themselves provide those features.

Receipt dates accept month/day/year and year/month/day with slash, hyphen or dot
separators, plus common English month names. Two-digit years mean 2000–2099;
short dates use month/day/year by default. Year-first input starts with a
four-digit year or a two-digit value above 12, such as 26/10/4. The interpreted
date is acknowledged without another selection. Invalid calendar dates are rejected. This is not an arbitrary
natural-language date parser.


### CLI balance check

Payment-method choices appear directly; there is no preliminary yes/no prompt.
Choose Cash, Card, Gift Card, Other with notes, or Not recorded.

Menu 7 (Balances) records explicit month-start and day-start USD values by provider
and date. Choose Both only when the same starting value applies to both periods.
Prior starting observations remain in the session's opening history when revised.
The provider balance table is available only through menu 7, View balances; it is
not printed automatically with receipt review, saving or retrieval. Unknown
starting values and missing point valuations stay marked not entered. Totals are
marked partial unless all contributing provider values are known.

The day-end column is an estimate using distinct receipts for that date, credited
or received earnings, recorded RR/WC redemptions, and gift-card tenders. Repeated
review and save retries do not add activity again. Point earnings use their saved
USD valuation once; rates are not automatically inferred here. Expected, pending
and approved-but-not-credited benefits do not increase these balance estimates.
External conversions, payouts, expirations and adjustments still need account-ledger
support. Starting-balance settings remain session-only; saved receipts now survive
CLI restart in the local receipt file.


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
The data directory is excluded from Git. Provider balance starting settings are
still session-only. Receipts from previously closed memory-only sessions cannot
be recovered from this new file.

Time entry accepts `940 am`, `9:40 am`, `940pm`, and 24-hour forms such as `21:40`.
The interpreted time is acknowledged and displayed as `9:40 AM` or `9:40 PM`.
Original entered time text is retained in the receipt snapshot. Invalid newly
entered times are re-prompted; historical unrecognized text remains readable.


### Required Walgreens receipt footer

After earnings entry, Walgreens receipts ask directly for Receipt myWalgreens
Savings, Receipt MFG Coupon savings, Receipt Total Savings, and Receipt Saving
Value (the printed percentage; either 43 or 43% is accepted). Then the CLI asks
for Receipt Opening Balance, Receipt Earned This Visit, Receipt Redeemed This
Visit, and Receipt Closing Balance. Each field requires a valid numeric value;
enter 0 only when that is the printed amount. There are no opt-in or account-used
confirmation questions. The independent printed values, including discrepancies,
are retained in snapshots and local storage. Historical receipts without these
observations remain readable. The footer does not create additional earnings.

For pre-save corrections, use EDIT > SAVINGS or EDIT > ACCOUNT. Optional structured
savings explanations remain available under EDIT > SAVINGS LINKS. Editing financial
entries clears stale confirmations but preserves the printed amounts themselves.


### RR correction and menu navigation

Menu 5, option 4 removes a misentered separately registered RR from the active
coupon list after confirmation, reason, and confirmed-by entry. The original
instrument and immutable removal audit remain stored; receipt earnings are not
deleted. Coupons linked to a receipt or redemption cannot be removed through
this standalone correction. Voided coupons cannot be redeemed, and the removal
survives later saves and CLI restart.

BACK returns from balance settings to the main menu. EXIT closes the CLI from
balance settings. Neither command is parsed as a date. The main menu is separated
from the preceding output by a blank line. Receipt display omits zero-discount
repetitions and redundant printed-RR status text. A simple receipt-wide WC earning
already represented by the printed account earned amount is not repeated as a
separate block; detailed underlying records remain in the saved snapshot.
