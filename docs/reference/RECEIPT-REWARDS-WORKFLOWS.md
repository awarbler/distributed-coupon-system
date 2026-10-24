# Receipt & Reward Account Workflows

Revision 1.6 · October 23, 2026 · Implemented working-tree behavior

## Account balances and activity

Rewards & Accounts retains Calculated Account Balances. Click an account name, or choose an account, to open its detail view. Select From/Through dates. The page shows beginning balance, known earned and spent amounts, ending balance, native point balances and an activity table. Openings from earlier days are labeled Carried Forward; missing starting balances remain Not recorded. Accounts with known zero or recorded activity remain accessible.

Activity uses the same Java movements as CLI balances. Receipt numbers open receipts. Existing RR earning/use links are shown as readable promotion descriptions; View Reward reveals the source receipt, earning date/time, promotion/products/status, matched use and spending receipt. Earned rows expose where linked RR was used. Technical identifiers are collapsed under Technical Details. Missing/unavailable RR sources are not invented. Reading a link never creates an earning or another deduction.

Transaction balances require a known opening and reliable chronological order. Unknown receipt times or ambiguous cross-receipt ordering leave transaction balances unavailable. Daily/range closing calculations remain separate. Points retain original units and saved dollar valuation/basis. Conversions & Payouts shows recorded provider redemption events separately; Requested is a reservation and Received records fulfillment. Saved valuation alone is never a conversion.

Set Beginning Balance records an owner-confirmed balance **before** activity on the selected From day. Supply USD and/or original points, valuation basis and source/reason, then confirm beginning-of-day meaning. Earlier calculated closings carry forward automatically. Do not enter a day’s closing as that same day’s opening. Opening observations append to history and establish a baseline; they are not earned credits. Retry an uncertain save using the same request. Broader adjustment/transfer/redemption/reconciliation commands remain separate workflows.

## Receipt entry and correction

“Have you entered all payments shown on this receipt?” maps Yes to COMPLETE, Not finished to PARTIAL and Not sure to UNKNOWN. This asks about completeness, not whether the purchase was paid. Cash/card entered from the receipt already contributes to Actual OOP. Strict reconciliation and benefit-adjusted spending continue to require the appropriate completeness, payment roles and valuations. Existing receipts are not automatically marked complete.

Add Receipt and Edit Receipt include Printed Savings & Walgreens Cash. Each field can be skipped independently:

| Printed savings | Printed Walgreens Cash |
|---|---|
| myWalgreens Savings | Opening Balance |
| Store Coupon Savings | Earned This Visit |
| Manufacturer Coupon Savings | Used This Visit |
| Printed Total Savings | Closing Balance |
| Savings Value (%) | |

Blank means not recorded. Zero means an explicitly recorded zero. Printed/not printed/not sure is an independent observation. Saving printed savings does not overwrite calculated Total Saved; entering printed WC earned/used does not create an earning/payment; printed opening/closing does not replace confirmed account openings. Saved values appear in Receipt Totals. WC observations also appear in the account’s Provider-Reported Observations, retaining receipt status.

Optional printed data patches existing ReceiptObservations; unsubmitted fields/categories are preserved. Existing category identities are retained. Changing a category amount clears its now-stale explanation confirmation; unchanged explanation links survive. Corrections retain receipt revision/history, original evidence and protected reward identities.

An accidental whole-receipt discount can be removed using **Delete receipt-wide discount**, even when its required inputs are empty. Alternatively clear **both** description and amount: a wholly empty row is omitted from the replacement discount list at preview/save. A partly completed row must be completed or deleted. Enter a correction reason, review totals, acknowledge and save. Original historical versions remain available.

## Application files changed in this revision

| File | Change |
|---|---|
| `src/main/java/coupons/ProviderBalanceSummary.java` | New structured activity/RR projection; source/use links, promotion/products, ordered balances and valuation basis. |
| `src/main/java/coupons/ProviderBalanceSummary.java` | Movements expose receipt/event IDs and parsed receipt times; same amount/sign semantics and compatible constructors. |
| `src/main/java/coupons/RegisterRewardLabel.java` | Shared readable offer label available to the projection. |
| `src/main/java/coupons/repository/PostgresPlatformRepository.java` | Provider range reads, existing RR links, printed observation projection, visible known-zero/activity accounts, confirmed opening persistence delegation. |
| `src/main/java/coupons/api/CouponApiService.java` | Account GET and owner opening POST; receipt rrUses response. |
| `src/main/java/coupons/api/ReceiptInput.java` | New validated optional receipt-observation patch. |
| `src/main/java/coupons/api/ReceiptInput.java` | Creation accepts independent optional printed observations. |
| `src/main/java/coupons/api/ReceiptInput.java` | Guarded corrections update submitted footer values while retaining other receipt facts. |
| `frontend/src/pages/WorkspacePages.tsx` | Clickable accounts, dated balance/activity view, reward detail links, original points/valuation, conversions and beginning-balance form. |
| `frontend/src/features/receipts/ReceiptCreate.tsx` | Shared optional controls and saved observation display. |
| `frontend/src/features/receipts/rewardStatus.ts` | Typed draft mapping; exact decimal strings and null versus zero. |
| `frontend/src/features/receipts/ReceiptCreate.tsx` | Printed fields, completeness wording, remove/blank receipt-wide discounts. |
| `frontend/src/features/receipts/ReceiptDetail.tsx` | Same correction controls with reviewed save/history. |
| `frontend/src/features/receipts/ReceiptDetail.tsx` | Totals shows printed observations and expandable used-RR connection; source receipts open on demand. |
| `frontend/src/types/platform.ts` | Account report/activity, RR source/use and footer contracts. |
| `frontend/src/api/client.ts` | Authenticated no-store account range query. |
| `frontend/src/pages/WorkspacePages.tsx` | Account feature integration. |
| `frontend/src/App.css` | Account detail card content insets using existing layout sizing. |
| `frontend/src/App.tsx` | Authenticated client/refresh callbacks and account navigation heading. |

No third-party dependency or SQL migration is added. Styling inherits the existing [Frontend Design System](FRONTEND-DESIGN-SYSTEM.md): 60px minimum controls, 18px values, full-width receipt editor fields, expanding descriptions and horizontal activity-table scrolling.

## Tests and documentation

New regressions: `AccountActivityViewTest`, `PrintedFooterTest`, `RewardsAccounts.test.tsx`. Updated regressions: `ReceiptEdit.test.tsx`, `ReceiptCreate.test.tsx`, `App.test.tsx`, `frontend/tests/browser.spec.ts`. See [verification](VERIFICATION.md) for executed results and limitations.

The actual Word SRS and SDD are revised to Draft 1.6. The API Reference, Calculations, Data Dictionary, Database Schema, System Overview, Dependency Index, Function Index, Model Declarations, Test Index, Verification, Workflow Comparison, Frontend Design System, File Catalog, reference README, root/frontend READMEs, receipt operations and local API setup guides, and research notes record the applicable changes. Old revision-specific evidence remains historical.


## Revision 1.7 — recording and matching RR used

1. Edit the receipt and enter each used RR coupon under **Register Rewards used**. Enter its face value and description, one coupon per row. Enter Walgreens Cash and cash/card as payments. Do not add a second manufacturer-coupon deduction for that same RR.
2. If a saved listed discount or Other/legacy RR payment is actually RR used, select whether to create a separate RR use or reuse an existing same-amount use, then click **Move … to RR used**. Reuse only when the old entry is a duplicate representation of the same coupon. Java verifies exact amounts and removes the original payment/discount in the reviewed revision; no silent conversion occurs.
3. Resolve **Other** tender as payment funding, a named-account checkout deduction, or Not sure. A legacy RR payment must move into RR used to become a matchable RR deduction. Cash/card payments are ordinary funding. Payment rows must be positive; remove unused zero/blank rows. A zero-out-of-pocket receipt needs no zero cash/card payment row.
4. Select **After reward use** only if the printed receipt total is the remaining amount after rewards; otherwise select the appropriate basis. Choose **Yes** for all-payments-entered only when every tender has been entered. Unknown basis, unknown roles or incomplete payments explain the unavailable reconciliation measures. Printed savings/footer fields do not supply this confirmation.
5. Review totals, enter the correction reason and save. In **Rewards**, or at the bottom of **Totals**, select each available unused RR with matching value, enter a matching note, and click **Match RR**. This separate command connects existing records without adding money. Source options are revalidated under lock and receipt revision check. Receipt time/date must establish earlier earning; future, expired, unprinted, consumed, linked and different-value coupons are excluded. Unknown expiry is shown as not recorded rather than guaranteed valid.
6. **Printed Savings & Walgreens Cash** now follows all payment rows and supports Show / Hide in both edit and saved views. Used promotion descriptions and **View Reward** are at the bottom of Totals/Rewards, after printed observations.

For the reported example, $7.58 merchandise + $0.21 tax − $5 RR − $2 WC = **$0.79** cash/card. With a printed remaining total, confirmed complete payment entry and resolved roles, reconciliation can compare these values. Printed manufacturer savings may include the RR in the store’s footer; that observation is not a second deduction. Benefit-adjusted spending is cash/card minus confirmed valued benefits; it is separate from cash/card paid.

The saved $0.79 receipt passed a no-change API edit preview during diagnosis. The pasted labels do not identify which unsaved value caused the original failure. Correction validation now reports the specific field/domain problem; zero payments are also caught with a row number in the editor. No personal receipt was changed by diagnosis or tests.

### Additional changed files

New: `src/main/java/coupons/repository/PostgresPlatformRepository.java`, `frontend/src/features/receipts/ReceiptDetail.tsx`, `src/test/java/coupons/repository/PostgresPlanCheckoutCalculationTest.java`, `frontend/src/features/receipts/ReceiptCreate.test.tsx`.

Modified: `src/main/java/coupons/repository/PostgresReceiptSupplementRepository.java`, `src/main/java/coupons/repository/PostgresPlatformRepository.java`, `src/main/java/coupons/api/CouponApiService.java`, `src/main/java/coupons/api/ReceiptInput.java`, `frontend/src/features/receipts/ReceiptDetail.tsx`, `frontend/src/features/receipts/ReceiptCreate.tsx`, `frontend/src/features/receipts/ReceiptCreate.tsx`, `frontend/src/features/receipts/ReceiptDetail.tsx`, `frontend/src/types/platform.ts`, `frontend/src/api/client.ts`, `frontend/src/App.css`, corresponding correction/API/editor tests and `frontend/tests/browser.spec.ts`.

Documentation: root/frontend READMEs, actual Word SRS/SDD Draft 1.7, this workflow/file map, reference README, API/calculations/data/schema/system/dependency/function/model/design/test/verification references, file catalog, receipt operations/local API setup and research notes. No new dependency or database migration.


## Revision 1.8 — RR without an earning receipt

For the recorded Palmolive $2 use, choose **Add RR without an earning receipt**. Keep or update the description, leave unknown earned/expiration dates blank, enter a source note, confirm the RR was used and click **Save RR and match**. The amount is taken from that existing use. A coupon record and source/use link are saved together; no receipt, earned benefit, guessed date or second deduction is created. The source remains reserved after matching. Known conflicting dates and stale/matched uses are rejected. Unknown-source entry is explicit; automatic source-list eligibility remains unchanged.

Application files changed: `src/main/java/coupons/repository/PostgresPlatformRepository.java`, `src/main/java/coupons/repository/PostgresReceiptSupplementRepository.java`, `src/main/java/coupons/repository/PostgresPlatformRepository.java`, `src/main/java/coupons/api/CouponApiService.java`, `frontend/src/features/receipts/ReceiptDetail.tsx`, `frontend/src/pages/WorkspacePages.tsx`, `frontend/src/types/platform.ts`. Tests: `src/test/java/coupons/repository/PostgresPlanCheckoutCalculationTest.java`, `src/test/java/coupons/api/CouponApiServiceTest.java`, `frontend/src/features/receipts/ReceiptCreate.test.tsx`, `frontend/tests/browser.spec.ts`. No dependency, database migration or automatic personal-data correction.

SRS/SDD Draft 1.8 and affected API/data/schema/calculation/design/workflow/operation/test references document this behavior.


## Revision 1.9 — Rewards points-account display

On the Rewards pages, only MyPoints, Fetch, P&G Brand (PGBRAND), Swagbucks (SWAGBUCK) and Merryfield show points information. Other account detail pages hide native-point balances, activity columns, point-valuation text, converted point quantities and the Original Points opening field, even if older records contain native units. Non-points opening forms use Balance Basis and require a dollar amount; new requests send points:null. The mixed account list shows a points column only when an allowed account is listed, with a dash for other accounts. This is Rewards presentation policy; receipt/CLI behavior, stored units and Java financial calculations remain intact.

Application changes: `frontend/src/pages/WorkspacePages.tsx` and `frontend/src/features/receipts/ReceiptCreate.test.tsx`. Documentation: root/frontend READMEs, System Overview, Workflow Comparison, Frontend Design System, Data Dictionary, Dependency/Function/Test indexes, Verification, File Catalog/reference README and actual SRS/SDD Draft 1.9. Documentation is maintained with each application change.


## Revision 1.10 — add on Rewards, match later

1. Open **Rewards & Accounts** (or its Register Rewards account page).
2. Click **Add reward without a receipt**. Enter the unused RR description/face value; leave unknown earned/expiration dates blank. Confirm unused and **Save reward**. No earning or spending receipt is required.
3. The coupon appears under **Rewards Without Receipts** as unused. This inventory action does not post earned dollars, an opening balance or a spending deduction.
4. Later record/save the RR use on the spending receipt and choose the saved source in **Match Register Rewards Used**. If history is unknown, explicitly confirm it was available for this purchase. Known future/expired/consumed sources remain invalid. Matching creates the source/use association only; the inventory source becomes reserved/matched.

The earlier standalone-date exclusion is superseded only for explicitly confirmed unknown-history standalone sources. Receipt-earned source ordering retains its existing checks. A historical unused-status inventory record is not a guarantee of unexpired availability today; matching checks expiry against the selected purchase date.

New application/test files: `frontend/src/pages/WorkspacePages.tsx`, `frontend/src/features/receipts/ReceiptCreate.test.tsx`.
Modified: `frontend/src/pages/WorkspacePages.tsx`, `frontend/src/features/receipts/ReceiptDetail.tsx`, `frontend/src/api/client.ts`, `frontend/src/types/platform.ts`, `frontend/src/App.test.tsx`, `frontend/src/features/receipts/ReceiptCreate.test.tsx`, `frontend/tests/browser.spec.ts`, `src/main/java/coupons/api/CouponApiService.java`, `src/main/java/coupons/repository/PostgresPlatformRepository.java`, `src/main/java/coupons/repository/PostgresReceiptSupplementRepository.java`, `src/main/java/coupons/repository/PostgresPlatformRepository.java`, `src/test/java/coupons/repository/PostgresPlanCheckoutCalculationTest.java`, `src/test/java/coupons/api/CouponApiServiceTest.java`.

The actual SRS/SDD and affected API/data/schema/calculation/design/system/workflow/dependency/function/model/test/verification/catalog/operations references and READMEs are updated with the code. No migration, dependency or automatic personal data correction.


## Revision 1.11 — calculations for existing receipts

Receipt totals now show arithmetic from recorded entries even when historical printed-total basis or payment completeness is unknown. Unknown basis displays both before-funding and after-reward-use calculated totals, purchase differences, funding subtotals and payment differences. A known basis displays its scenario. Incomplete/unknown entry or unresolved roles are explicitly Provisional — recorded entries only. Zero arithmetic difference does not prove complete payment entry. Existing metadata, strict reconciliation status/differences, Actual OOP, benefit-adjusted spending rules, legacy figures and history are preserved; no receipts are automatically marked complete or verified.

The values are computed on read; no owner data backfill or new financial event is needed. Restart the API/refresh the browser. A provisional zero is an arithmetic result only; resolve the actual receipt meaning/payment facts separately when known. Benefit-adjusted spending still requires its existing completeness/valuation rules.

New: `frontend/src/features/receipts/ReceiptCalculationChecks.tsx`, `frontend/src/features/receipts/ReceiptCreate.test.tsx`, `src/test/java/coupons/TransactionCalculatorTest.java`. Modified: `src/main/java/coupons/TransactionCalculator.java`, `src/main/java/coupons/repository/PostgresPlatformRepository.java`, `frontend/src/features/receipts/ReceiptCreate.tsx`, `frontend/src/types/platform.ts`. Actual SRS/SDD Draft 1.11 and affected API/data/schema/calculation/design/system/workflow/dependency/function/model/test/verification/catalog/operation references and READMEs are updated.


## Revision 1.12 — simplified receipt totals display

Per user request, receipt totals/review no longer displays Calculated total on recorded basis, its before/after-reward alternatives, or the Receipt calculation checks table. Saved printed observations also hide Store Coupon Savings. Store coupon input remains available in Add/Edit Receipt; existing saved observations, API calculationChecks, Java formulas, strict validation, Actual OOP, Total Saved, legacy details and history remain unchanged. This supersedes revision 1.11 UI visibility only.

Application changes: `frontend/src/features/receipts/ReceiptCreate.tsx`, `frontend/src/features/receipts/ReceiptCreate.tsx`, `frontend/src/features/receipts/ReceiptCreate.test.tsx`; removed `frontend/src/features/receipts/ReceiptCalculationChecks.tsx`. Root/frontend READMEs, current reference/operations documentation and actual SRS/SDD Draft 1.12 are updated. Earlier version notes remain historical.


## Revision 1.13 — current file ownership and CLI metric

Owner development constraint: edit and reuse existing files. Discuss any proposed new application, test or documentation file with the owner before creating it. This revision consolidates the earlier added helpers/components/tests into pre-existing files and does not add source or documentation files.

The visible receipt measure is Total Paid after rebates, sourced directly from savings.paidAfterRebates — the same SavingsReport value used by the CLI. Formula: recorded cash/card minus newly earned reward/rebate dollars, including saved USD point valuations. The frontend no longer uses the stricter benefitAdjustedCashSpending field for this spreadsheet measure; that compatibility field and its explicit-completeness semantics remain unchanged. Unknown historical payment-entry metadata alone does not hide the CLI value. Truly missing tender or dollar valuations still leave the CLI measure unknown, now explained as Cannot calculate yet. Actual OOP, reward use, Total Saved and stored facts are unchanged.

Earlier revision references now point at consolidated owners. Original helper paths below are retired, not additional files to create.

| Retired added file | Existing owner |
|---|---|
| `frontend/src/components/AutoSizeTextArea.tsx` | `frontend/src/components/ui.tsx` |
| `frontend/src/features/receipts/ItemDiscountFields.tsx` | `frontend/src/features/receipts/ReceiptCreate.tsx` |
| `frontend/src/features/receipts/PrintedFooterFields.tsx` | `frontend/src/features/receipts/ReceiptCreate.tsx` |
| `frontend/src/features/receipts/printedFooter.ts` | `frontend/src/features/receipts/rewardStatus.ts` |
| `frontend/src/features/receipts/receiptBrowse.ts` | `frontend/src/api/format.ts` |
| `frontend/src/features/receipts/ReceiptEdit.tsx` | `frontend/src/features/receipts/ReceiptDetail.tsx` |
| `frontend/src/features/receipts/ReceiptRRMatch.tsx` | `frontend/src/features/receipts/ReceiptDetail.tsx` |
| `frontend/src/features/rewards/RewardsAccounts.tsx` | `frontend/src/pages/WorkspacePages.tsx` |
| `frontend/src/features/rewards/StandaloneRewardPanel.tsx` | `frontend/src/pages/WorkspacePages.tsx` |
| `frontend/src/styles/tokens.css` | `frontend/src/index.css` |
| `src/main/java/coupons/AccountActivityView.java` | `src/main/java/coupons/ProviderBalanceSummary.java` |
| `src/main/java/coupons/api/PrintedFooter.java` | `src/main/java/coupons/api/ReceiptInput.java` |
| `src/main/java/coupons/api/ReceiptCorrection.java` | `src/main/java/coupons/api/ReceiptInput.java` |
| `src/main/java/coupons/repository/ReceiptRRMatching.java` | `src/main/java/coupons/repository/PostgresPlatformRepository.java` |
| `src/test/java/coupons/AccountActivityViewTest.java` | `src/test/java/coupons/ProviderBalanceSummaryTest.java` |
| `src/test/java/coupons/RecordedCalculationChecksTest.java` | `src/test/java/coupons/TransactionCalculatorTest.java` |
| `src/test/java/coupons/api/PrintedFooterTest.java` | `src/test/java/coupons/api/ReceiptInputTest.java` |
| `src/test/java/coupons/api/ReceiptCorrectionTest.java` | `src/test/java/coupons/api/ReceiptInputTest.java` |
| `src/test/java/coupons/repository/ReceiptPeriodsTest.java` | `src/test/java/coupons/repository/PostgresPlanCheckoutCalculationTest.java` |
| `src/test/java/coupons/repository/ReceiptRRMatchingTest.java` | `src/test/java/coupons/repository/PostgresPlanCheckoutCalculationTest.java` |
| `frontend/src/features/receipts/ReceiptCalculationChecks.test.tsx` | `frontend/src/features/receipts/ReceiptCreate.test.tsx` |
| `frontend/src/features/receipts/ReceiptEdit.test.tsx` | `frontend/src/features/receipts/ReceiptCreate.test.tsx` |
| `frontend/src/features/receipts/ReceiptRRMatch.test.tsx` | `frontend/src/features/receipts/ReceiptCreate.test.tsx` |
| `frontend/src/features/receipts/receiptBrowse.test.ts` | `frontend/src/features/receipts/ReceiptCreate.test.tsx` |
| `frontend/src/features/rewards/RewardsAccounts.test.tsx` | `frontend/src/features/receipts/ReceiptCreate.test.tsx` |
| `frontend/src/features/rewards/StandaloneRewardPanel.test.tsx` | `frontend/src/features/receipts/ReceiptCreate.test.tsx` |

Existing application/test files also updated: ReceiptCreate.tsx/test, ReceiptDetail.tsx, WorkspacePages.tsx, components/ui.tsx, api/format.ts, rewardStatus.ts, index.css, ProviderBalanceSummary.java, PostgresPlatformRepository.java, PostgresReceiptSupplementRepository.java, ReceiptInput.java, associated existing Java tests, SavingsReportTest.java and existing browser.spec.ts. Actual SRS/SDD and affected reference/operations/README documents are edited in place. No new application/test/document files are introduced.

### Optional RR matching note

The receipt RR matching form accepts an optional matching note. Selecting an eligible reward is sufficient; unknown-history availability confirmation is still required when applicable. POST `/receipts/{id}/rr-matches` accepts an omitted, null, empty or whitespace-only `reason`; the server records the neutral audit description “Matched recorded RR use to selected reward” when no note is supplied. A supplied note is retained after trimming. This does not create another reward or deduction. Existing receipt correction/deletion reasons and unknown-source confirmation rules are unchanged. Implementation: `frontend/src/features/receipts/ReceiptDetail.tsx`, `src/main/java/coupons/api/CouponApiService.java`; regression: existing `ReceiptCreate.test.tsx`.

### Receipt period reporting update — October 23, 2026

Receipts → Period totals displays Regular price including tax, Actual OOP, Total amount saved, Confirmed benefits, Total Paid after rebates, and Total % saved. Pending rebates are not shown or included. Existing Java CLI savings rules and saved points valuations apply; original receipt data and valuations are unchanged. Cash/card Actual OOP excludes rewards used and gift cards. Percent saved uses regular merchandise plus recorded tax as denominator; a zero/unknown denominator remains Not recorded. Unknown inputs retain existing null/completeness behavior.

Select Purchase day or Month, enter inclusive Start date and End date, or click Month to date (the current local calendar month through today). Selecting a day/month clears the range; entering a range clears day/month. Clear filters returns to All time. Totals cover all active latest receipt revisions across every page, regardless of text search or status filter. Search continues across dates/statuses; without search the receipt list follows the selected period. Date-range loading/errors never reuse the prior summary. Invalid or incomplete ranges require correction.

Read-only authenticated API: GET /receipts/summary?from=YYYY-MM-DD&to=YYYY-MM-DD returns the existing Savings shape. Both dates are required, start must not exceed end, and undated/deleted receipts are excluded. Range calculation runs in Java through SavingsReport.range and the existing savings projection; the browser performs no money aggregation. No database schema change.

Existing implementation files changed: src/main/java/coupons/SavingsReport.java; src/main/java/coupons/repository/PostgresPlatformRepository.java; src/main/java/coupons/api/CouponApiService.java; frontend/src/api/client.ts; frontend/src/pages/WorkspacePages.tsx. Existing regression files: src/test/java/coupons/SavingsReportTest.java; frontend/src/features/receipts/ReceiptCreate.test.tsx. No new files. Layout uses the existing centered three-column receipt-period-totals grid (six metrics in two rows), collapsing to one column on narrow screens; existing typography/control tokens remain unchanged.

Date-range troubleshooting: a Java API process started before the /receipts/summary route was added interprets summary as a receipt UUID and returns HTTP 400. Rebuild and restart the Java process; browser refresh alone cannot load new backend classes. The period panel now shows a single error notice rather than a duplicate unavailable notice. Existing WorkspacePages.tsx updated; no receipt data changed.

### Receipt entry breakdown and field errors — October 23, 2026

Add Receipt now provides a Review entered breakdown button near the top, in addition to the existing calculated review action. The Java preview review lists product descriptions, regular prices, quantities, recorded extended prices and individual attached discounts; shared/receipt-wide discounts appear separately with their selected products. Existing tax, tender, reward-used, earned-benefit and savings calculations remain in the financial review. No additional browser money calculations or financial events are introduced.

Receipt-entry invalid fields receive a red border and pale red background, aria-invalid, and a descriptive error. Validation scrolls to and focuses the first invalid control; collapsed containing details are opened. Received reward confirmations explain the original unit, compare exact decimal strings without Number conversion, and show an inline error if the confirmed amount differs. Equivalent values such as 5 and 5.00 remain valid. Correct Reward amount first when the actual received amount differs. Points and their saved USD valuation remain separate. New-earning API validation identifies the offer/provider and expected amount/unit rather than displaying status-only workflow wording. Existing saved-reward status restrictions remain unchanged.

The draft information message is explanatory, not an error: the receipt is unsaved until review and Save receipt; closing discards unsaved input. It now says this in plain language. Existing files edited: frontend/src/features/receipts/ReceiptCreate.tsx; frontend/src/features/receipts/RewardFields.tsx; frontend/src/index.css; src/main/java/coupons/api/EarningInput.java. Existing regressions: ReceiptCreate.test.tsx and src/test/java/coupons/api/ReceiptInputTest.java. No new files or database-schema changes.

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

Expected rewards quick entry: RewardFields now explains that provider and amount with Expected — waiting to receive are sufficient. Product selections, promotion rules, valuation, evidence and submission details sit in a closed Optional reward details disclosure; historical copying has its own optional disclosure. ReceiptRewardForm places qualification diagnostics in Optional qualification review, and explains that expected rewards do not increase confirmed benefits. No fields or backend validation requirements were added. Existing entered optional values are preserved. Existing RewardFields.tsx, ReceiptRewardForm.tsx and browser.spec.ts updated; no new files.

Received reward entry simplification: manually entered new rewards now default to received (CREDITED for account rewards, PAID/printed for RR); Expected remains an explicit option for rewards still awaited. Copying historical terms still defaults to Expected and does not copy receipt confirmation. Updating a saved reward status uses its existing recorded amount/unit automatically without duplicate input or checkbox; the existing revision-checked API payload still carries those exact values and explicit confirmation. No historical rewards are automatically reclassified. Receipt qualification diagnostics are collapsed under Optional qualification details; unknown eligibility does not negate a recorded receipt of rewards. Existing files: rewardStatus.ts, RewardFields.tsx, EarningStatusForm.tsx, EarningStatusForm.test.tsx, ReceiptDetail.tsx. No new files or backend financial-rule changes.

Automatic P&G/Fetch point valuation: new API reward entries calculate Fetch at 1000 points = 1 USD and P&G Brand/P&G Good Everyday/PG at 250 points = 1 USD, using existing worksheet rates. Native points remain stored separately; 100 P&G points = $0.40 and 25 Fetch points = $0.025 before display rounding. Expected rewards remain excluded from confirmed savings. RewardFields hides manual conversion for both programs; rewardBody sends no hidden stale valuation for those point providers. EarningInput calculates the USD valuation and records its basis. Previously saved explicit valuations are not overwritten. No payout is generated. Existing EarningInput.java, RewardFields.tsx, rewardStatus.ts and ReceiptInputTest.java edited; no new files.

Existing points valuation repair: period totals, savings reports, account movements and receipt benefit projections now use SavingsReport.effectiveValuation. It preserves explicit historical valuations and derives a read-only worksheet value for missing Fetch (1000 points/$1) or P&G (250 points/$1) valuations. Other unknown providers remain incomplete. This fixes the existing 100-point P&G entry blocking all-time savings: it contributes $0.40 without rewriting receipt history or creating earnings. Expected/rejected status rules remain unchanged. Receipt Rewards displays the projected dollar value beside points. Existing SavingsReport.java, ProviderBalanceSummary.java, PostgresPlatformRepository.java, ReceiptDetail.tsx and SavingsReportTest.java updated; no new files.

Ibotta account credit: RewardFields and EarningStatusForm label CREDITED as Added to Ibotta account, separate from Expected (waiting for credit). Typing Ibotta as the provider selects the cash benefit form. Account credit is earned value held in Ibotta; the user's $20 withdrawal minimum is displayed as context, not an external eligibility guarantee. Rewards → Ibotta has an Account Credit & Withdrawals section with the recorded period-end balance and minimum. A withdrawal is a separate transfer/payout and must not generate another earning. Existing conversion records remain unchanged; this update does not initiate withdrawals or introduce a payout command. Existing RewardFields.tsx, EarningStatusForm.tsx and WorkspacePages.tsx edited; no new files or financial-rule changes.

Decimal entry accepts a leading decimal point: .98 is equivalent to 0.98 for receipt tax, prices, discounts, RR, payments, footer observations and API decimal amounts. Exact BigDecimal/string precision is retained; currency display supplies the leading zero. Whole-number quantity rules remain unchanged; lone dots, negative nonnegative amounts and malformed decimals are rejected. Existing ReceiptCreate.tsx, ReceiptDetail.tsx, WorkspacePages.tsx, api/format.ts, ReceiptInput.java, ReceiptEarningStatusUpdate.java and existing receipt tests edited. No new files or database changes.

Automatic Fetch receipt default (explicitly approved): new web receipt preview/create adds one received/CREDITED 25-point Fetch earning valued at $0.025 when no explicit Fetch entry is present. Any explicit Fetch amount/status replaces this default. Event identity is deterministic per receipt UUID, so repeated previews and idempotent saves do not duplicate it. Preview shows the benefit; only saving persists it. Existing receipts, edits and restores are not backfilled. Existing ReceiptInput.java, ReceiptCreate.tsx and ReceiptInputTest.java updated; no new files.

Rewards date browsing: Rewards & Accounts adds account-name search, Balance through date, and Month to date (current local date). The main table's Month Start, Day Start, Day Close and allowed points columns are recalculated in Java for the selected date; future activity is excluded from those balances. Clicking an account opens the selected month's first day through that date. The redundant Open Account/Choose Account dropdown is removed; names remain clickable. GET /workspace?asOf=YYYY-MM-DD returns existing workspace balance projections for the chosen date; omitted asOf preserves existing latest-receipt behavior. Search filters account rows only. Unknown openings remain unknown. Existing PostgresPlatformRepository.java, CouponApiService.java, api/client.ts, WorkspacePages.tsx and ReceiptCreate.test.tsx updated; no new files or stored-data changes.

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

Register Rewards used disclosure: Add Receipt and Edit Receipt give the RR-used subsection its own initially expanded Show / Hide control, displaying the entered coupon-row count. Users can collapse all RR rows after entry and reopen them without losing amounts, descriptions or match identities. No financial or storage changes. Existing ReceiptCreate.tsx and ReceiptDetail.tsx updated; no new files.

Confirmed benefits detail: clicking Confirmed benefits opens the selected-period searchable/sortable report of all receipt earnings with native amounts/units, dollar values, confirmed/unconfirmed/rejected grouping and source receipt links. Confirmed and unconfirmed dollar subtotals are separate; rejected rewards contribute to neither. Unknown point valuations remain unknown rather than zero. New metric=benefits in GET /receipts/report returns original earning and receipt revision for existing status commands. Confirm / update status opens the existing EarningStatusForm and scrolls/focuses its control; it uses recorded amount/unit once, revision checks and stable retries. Saving refreshes report and main workspace totals. Read-only users cannot change status. Existing WorkspacePages.tsx, types/platform.ts, PostgresPlatformRepository.java, ReceiptCreate.test.tsx and SavingsReportTest.java updated; no new files or new financial formulas.

Rewards Without Receipts placement: the existing standalone RR inventory panel now appears at the bottom of the main Receipts page, after the receipt list/pagination and editing guidance. It is removed from the Rewards main balance screen; it remains accessible in Register Rewards account details. Add reward without a receipt and the reward inventory table retain their existing behavior. Existing WorkspacePages.tsx and browser.spec.ts updated; no new files, backend changes or saved-data edits.

Rewards Overview (approved definition): Total Rewards = Available Balances + Expected / Unconfirmed, shown separately on the Rewards main page for Balance through. Available sums positive recorded account closing balances after recorded spending/transfers; confirmed earnings are not added again. Expected includes outstanding active-receipt rewards dated on/before the selected day, excluding received, rejected and expired expected entries. Prior-month outstanding rewards remain included. Native points use saved/automatic USD valuations. Missing balance/valuation leaves the affected full total unknown and shows known subtotals/providers for review. Account search does not change these overview totals. GET /workspace adds rewardsOverview with available, expected, total, known subtotals and completeness context. Existing PostgresPlatformRepository.java, types/platform.ts, WorkspacePages.tsx and SavingsReportTest.java updated; no new files or personal record changes.

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


ECB non-receipt member rewards now support optional expirationDate in POST /accounts/member-rewards and an ECB expiration date input. Dates before the reward date are rejected. AccountAdjustment retains the date in its existing serialized payload; its legacy eight-argument constructor and older records default to unknown expiration. No new SQL column or migration is required. Account activity displays Expires YYYY-MM-DD. The date is informational: it does not automatically debit balances or infer remaining coupon value after spending. Existing saved financial amounts are unchanged.
Changed files: WorkspacePages.tsx, CouponApiService.java, AccountAdjustment.java, ProviderBalanceSummary.java, ReceiptCreate.test.tsx and ProviderBalanceSummaryTest.java.


Receipt-earned ECB expiration: new CVS ExtraBucks/store reward entries expose Reward expiration date in the main reward form. Existing receipt reward cards display the saved expiration and offer Edit products / expiration. The existing EarningLinksForm includes an optional expiration date, prefilled from the saved earning; blank clears it. PUT /receipts/{receiptId}/earnings/{earningId}/associations accepts optional expirationDate, preserves it when omitted, and rejects dates before the recorded purchase day. Omitted product selections retain their prior values. Saving preserves earning ID, expected/confirmed amounts, valuation, status, payments and evidence, and records a receipt revision. This reuses the existing Earning.expirationDate field and existing endpoint; no schema migration or new files. Expiration entry does not automatically create an account debit.
Changed files: RewardFields.tsx, EarningLinksForm.tsx, ReceiptDetail.tsx, platform.ts, CouponApiService.java, ReceiptCreate.test.tsx and CouponApiServiceTest.java.


Dashboard whole-card collapse correction: dashboard grid items align to the start rather than stretching to the height of the adjacent expanded card. All three dashboard cards have an explicit collapsed class and hidden bodies take no layout space. A collapsed card shrinks to its header, retaining its title/subtitle, Expand control and navigation link; expanding restores the full content. Header actions wrap with clear spacing. Existing browser.spec.ts verifies actual card/header heights and independent expand/collapse at desktop, Safari and mobile viewport sizes.


Calculated Account Balances card spacing: explanatory/loading/empty text uses 23px horizontal insets and 18px bottom spacing with 1.7 line height. On screens up to 600px, horizontal insets are 18px to align with the card heading. Notices use the same insets; account tables retain their existing scrolling layout. Changed WorkspacePages.tsx and App.css; no balance calculations or saved records changed.


## Documentation revision 1.16 — material relocated from the root README (2026-10-24)

The following former README sections preserve prior operating guidance/change history. They are historical source material; current procedures above and the current API reference take precedence where earlier feature limitations or migration versions differ.

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


