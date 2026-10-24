# Checkout, rewards and savings calculation guide

## Revision history

| Version | Date | Changes | Verification |
|---|---|---|---|
| 1.0 | 2026-10-21 | Original reference snapshot | Static inspection |
| 1.1 | 2026-10-22 | Planning reward progress, unknown-value semantics and current UPC policy | Source updated; final checks not run |
| 1.2 | 2026-10-22 | Receipt evidence/reconciliation, distinct purchase/scan and partial benefit values | See VERIFICATION.md |

## Revision 1.2 — receipt evidence and reconciliation

Receipt is primary evidence; printed values are not overwritten by calculated checks. Legacy expected checkout/difference/savings retain exact meanings.

BEFORE_PAYMENT_FUNDING: calculated purchase = legacy expected checkout + WC redemption. Funding includes classified cash/card, WC, gift cards and other funding. RR is a separately recorded deduction, not funded twice. AFTER_REWARD_REDEMPTIONS: calculated amount = legacy expected checkout; WC is excluded from the funding comparison. Unknown basis leaves new differences unknown. COMPLETE plus resolved roles is required for a funding difference/BALANCED result.

Example: $31.98 merchandise − $8 discounts + $1.98 tax = $25.96 printed before funding; $20 WC + $5.96 card = $25.96; both new differences zero; legacy difference remains $20. Actual out of pocket / Actual cash/card paid is $5.96, not inferred from remainder. A negative benefit-adjusted amount is not payment. Missing points valuation preserves known benefits and native units but leaves dependent combined values unknown. Tax basis/stacking policy is unchanged.


## Revision 1.1 — current behavior and status

Revision 1.1: eligible fixed-quantity rewards are calculated without requiring prices. Spend rewards with incomplete prices are unknown. Sum known contributions as knownPotentialRewards; expectedRewards is null when any applicable reward is unknown. finalCost is null unless register cost and complete reward total are known. Store/date review affects conditional labeling, not whether known threshold-met potential rewards are included. Incomplete offers contribute known zero and expose rewardIfCompleted. Sale-only deals have no separate reward. Potential benefits never reduce checkout amount due. Automatic sale/coupon rules remain separate; tax/stacking/allocation policies are not resolved by this change.



Original revision 1.0 inspection: October 21, 2026 (America/Chicago). Base Git commit: `fa3b8debc070cb5de990cbe9eb420fe1e56b7c06`. This reference describes the working tree, including pre-existing uncommitted checkout changes; it does not prove what is deployed online. No application tests were run for this documentation-only task.

## Shopping plan

Regular merchandise = sum(item regular unit price × quantity), when known.
Initial estimated line price = quantity × first available(saleUnitPrice, regularUnitPrice, targetUnitPrice).
Supported automatic deal allocation can then alter line totals.

Sale total = sum(calculated line prices).
Sale savings = regular total − sale total.
Coupon savings = qualified linked coupon discounts, bounded by eligible merchandise and overall sale total.
Register cost = sale total − coupon savings.
Potential rewards = determinable plan reward groups × reward amounts; unknown applicable amounts make the complete total unknown.
Final cost = register cost − expected rewards, before tax and additional checkout discounts.

ShoppingEstimate checks coupon status, dates, store restrictions, quantities, optional SKU restrictions, offer usage limits and minimum spend. Percentage/free-item coupons have additional allocation logic. Consult the full method before changing stacking; a linked coupon is not automatically a valid discount.

## Saved plan checkout

PostgresPlanCheckoutRepository builds a view using the current ShoppingEstimate and stored checkout inputs.

Tax = actualTax when provided; otherwise taxableBase × taxRate/100 rounded to cents. If taxableBase is absent, the rate calculation defaults to sale merchandise. This is a recorded estimate rule, not a universal statement of tax law.

Amount due = max(0, register cost − promoDiscount − registerRewardsCoupon) + tax.
Payments = Walgreens Cash used + gift card used + cash + card + other.
Remaining payment = amount due − payments.

Missing tax configuration yields an issue and null displayed amountDue/remainingPayment. Excess payments and excessive deductions create issues. Monetary checkout calculations round to cents. Expected rewards are returned separately and never subtracted from the amount due.

The form displays the last-loaded summary while editing inputs; it saves and closes rather than showing a live backend preview. Its summary now also displays potential reward values, completeness and review warnings.

## Supplied example

| Step | Amount |
|---|---:|
| Merchandise | $31.98 |
| Coupons | −$6.00 |
| Plan register cost before tax | $25.98 |
| Additional saved WAG10 discount | −$2.00 |
| Recorded tax | +$1.98 |
| Checkout amount due | $25.96 |
| Walgreens Cash used | $20.00 |
| Card payment | $5.96 |
| Remaining payment | $0.00 |
| Expected new rewards | $7.00 |

The plan's $18.98 final-cost estimate is $25.98 − $7 and excludes checkout-specific discount/tax. It should not be equated to the card payment or account balance. Entering a promotion code label alone does not calculate its discount.

## Actual receipt calculations

TransactionCalculator contains calculateRegularMerchandiseTotal, calculateSaleMerchandiseTotal, calculateCouponTotal, calculateAfterCouponTotal, calculateWalgreensCashRedeemed, calculateRegisterRewardsRedeemed, calculateRewardRedemptionTotal, calculateAfterRewardRedemptions, calculateExpectedCheckoutAmount and calculateReceiptDifference. These operate on CouponTransaction; they are not interchangeable with plan estimate outputs. Printed receipt totals remain separate from calculations so real discrepancies can be reviewed.

Earnings, redemptions, cashback payouts, transfers, points valuations and printed savings are different accounting concepts. Do not sum them as new rewards merely because each has a currency value. See provider/reward model sources and tests for individual lifecycle rules.


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

## Revision 1.6 — observation and account boundaries

Printed savings/categories, percentage and Walgreens Cash footer values are observations only: they do not change Actual OOP, calculated Total Saved, credit earnings or create payment deductions. Actual OOP remains entered cash/card, irrespective of the all-payments completeness answer. COMPLETE/PARTIAL/UNKNOWN still controls strict reconciliation and benefit-adjusted spending.

Account range beginning uses ProviderBalanceSummary.balance(from).start; ending uses balance(to).end. A confirmed opening is a replacement baseline, never an addition. Earlier openings carry intervening activity forward; missing openings/valuations remain null. Activity earns/spends reuse existing movements; RR links add no movements. Running balances require known day order/time and starting value. Unknown ordering leaves per-event balances unavailable while daily balance rules remain unchanged. Points are native units; saved USD valuation is shown with its basis and is not evidence of conversion or payout. Period earned/spent are known subtotals when usdActivityComplete is false.


## Revision 1.7 — receipt RR correction and matching

RR used is a checkout deduction, not cash/card or a newly earned manufacturer coupon. Reclassifying a $5 coupon deduction into one $5 RR use preserves checkout arithmetic; identifying an already duplicated $5 RR removes the extra deduction after explicit reviewed correction. Matching only adds a source-use association. Other payment role must be recorded explicitly; no RR role is guessed. For regular merchandise $7.58 plus tax $0.21, $5 RR and $2 WC, the remaining total is $0.79. AFTER_REWARD_REDEMPTIONS identifies that printed basis; COMPLETE plus resolved payment roles permits strict reconciliation. Printed footer balances/savings do not confirm payment completeness.


## Revision 1.8 — RR without an earning receipt

Adding an unknown-source coupon for an existing $2 use leaves all receipt totals, Actual OOP, Total Saved and account movements unchanged. It creates source/inventory evidence and an association, not a $2 earning or another $2 use. Unknown earning dates and balances are not inferred.


## Revision 1.10 — independent Rewards inventory

Saving an unused standalone RR from Rewards records coupon inventory only. It does not increase calculated earned benefits, account balance or Total Saved, and does not create an RR-use deduction. A later match describes an already recorded equal-value use. Unknown earning history requires explicit availability confirmation instead of assigning a fictitious receipt/date; known conflicts remain invalid.


## Revision 1.11 — visible recorded receipt calculations

Receipt totals now show arithmetic from recorded entries even when historical printed-total basis or payment completeness is unknown. Unknown basis displays both before-funding and after-reward-use calculated totals, purchase differences, funding subtotals and payment differences. A known basis displays its scenario. Incomplete/unknown entry or unresolved roles are explicitly Provisional — recorded entries only. Zero arithmetic difference does not prove complete payment entry. Existing metadata, strict reconciliation status/differences, Actual OOP, benefit-adjusted spending rules, legacy figures and history are preserved; no receipts are automatically marked complete or verified. CalculationScenario.purchaseDifference = printed total − scenario calculated total; paymentDifference = printed total − scenario recorded funding subtotal. Both scenarios reuse reconcilePayments with a temporary basis and original payment-entry status. The existing purchase/payment formulas are not replaced. For $7.58 + $0.21 − $5 RR − $2 WC, after-reward total is $0.79; before WC funding is $2.79. If printed total is $0.79 and known cash/card is $0.79, after-reward differences are zero but remain provisional until basis/completeness are confirmed.


## Revision 1.12 — simplified receipt totals display

No formula or verification-rule change. The requested calculated-total/comparison UI is removed; backend RecordedChecks and strict reconciliation remain unchanged. Hiding Store Coupon Savings does not delete/reclassify a coupon or change receipt savings.


## Revision 1.13 — reuse existing files and align CLI rebate display

The visible receipt measure is Total Paid after rebates, sourced directly from savings.paidAfterRebates — the same SavingsReport value used by the CLI. Formula: recorded cash/card minus newly earned reward/rebate dollars, including saved USD point valuations. The frontend no longer uses the stricter benefitAdjustedCashSpending field for this spreadsheet measure; that compatibility field and its explicit-completeness semantics remain unchanged. Unknown historical payment-entry metadata alone does not hide the CLI value. Truly missing tender or dollar valuations still leave the CLI measure unknown, now explained as Cannot calculate yet. Actual OOP, reward use, Total Saved and stored facts are unchanged. Spreadsheet regression examples: $3.60 − $12.83 = −$9.23; $26.69 − $39.19 = −$12.50; $2.12 − $5.13 = −$3.01. Decimal arithmetic/rounding stay in existing Java/format helpers; negative values are calculated after-benefit cost, not additional cash received.

### Receipt period reporting update — October 23, 2026

Receipts → Period totals displays Regular price including tax, Actual OOP, Total amount saved, Confirmed benefits, Total Paid after rebates, and Total % saved. Pending rebates are not shown or included. Existing Java CLI savings rules and saved points valuations apply; original receipt data and valuations are unchanged. Cash/card Actual OOP excludes rewards used and gift cards. Percent saved uses regular merchandise plus recorded tax as denominator; a zero/unknown denominator remains Not recorded. Unknown inputs retain existing null/completeness behavior.

Select Purchase day or Month, enter inclusive Start date and End date, or click Month to date (the current local calendar month through today). Selecting a day/month clears the range; entering a range clears day/month. Clear filters returns to All time. Totals cover all active latest receipt revisions across every page, regardless of text search or status filter. Search continues across dates/statuses; without search the receipt list follows the selected period. Date-range loading/errors never reuse the prior summary. Invalid or incomplete ranges require correction.

Read-only authenticated API: GET /receipts/summary?from=YYYY-MM-DD&to=YYYY-MM-DD returns the existing Savings shape. Both dates are required, start must not exceed end, and undated/deleted receipts are excluded. Range calculation runs in Java through SavingsReport.range and the existing savings projection; the browser performs no money aggregation. No database schema change.

Existing implementation files changed: src/main/java/coupons/SavingsReport.java; src/main/java/coupons/repository/PostgresPlatformRepository.java; src/main/java/coupons/api/CouponApiService.java; frontend/src/api/client.ts; frontend/src/pages/WorkspacePages.tsx. Existing regression files: src/test/java/coupons/SavingsReportTest.java; frontend/src/features/receipts/ReceiptCreate.test.tsx. No new files. Layout uses the existing centered three-column receipt-period-totals grid (six metrics in two rows), collapsing to one column on narrow screens; existing typography/control tokens remain unchanged.

### Multiple Register Rewards used during receipt creation — October 23, 2026

Add Receipt → Register Rewards used now offers Add RR used and removable individual rows, each with one dollar amount and optional description. Enter $5, $5 and $4 as three separate coupons; duplicate dollar values remain distinct through stable event IDs. Blank descriptions default to Register Rewards used. The review shows each coupon and the Java-calculated RR total. These receipt-wide deductions must not be entered again as discounts or payment tenders. Existing post-save matching offers a separate source match for each use; matching does not create another deduction. Edit Receipt already supports separate RR uses and is unchanged.

Receipt preview/create accept rrUses:[{eventId,amount,recordedDescription?}] with at most 100 positive entries and unique event IDs. The legacy rrUsed/rrUsageId request remains supported; a nonzero legacy amount alongside rrUses is rejected to prevent double counting. Existing UnidentifiedRR observations store the separate uses; no schema/model migration or new financial formula is introduced. Preview tokens and stable save retries retain the individual IDs.

Existing files updated: frontend/src/features/receipts/ReceiptCreate.tsx; src/main/java/coupons/api/ReceiptInput.java; frontend/src/features/receipts/ReceiptCreate.test.tsx; src/test/java/coupons/api/ReceiptInputTest.java. No new files. Regression coverage verifies $5+$5+$4 yields $14, equal coupons have different IDs, preview/save preserve identities, and zero/duplicate-ID/mixed legacy input is rejected.

### Simplified new reward entry and collapsible sections — October 23, 2026

New Fetch point earnings automatically receive the existing worksheet valuation of 1000 points = 1 USD in EarningInput when no explicit valuation is supplied. The UI hides Fetch manual valuation fields; 25 points retain the exact $0.025 saved value. This is a worksheet valuation, not a redemption or current market-rate guarantee. Existing explicit valuations and saved receipts are preserved. Other providers keep their existing valuation entry.

New reward entry records the entered amount once. Choosing Printed at register or Added to program account uses that same amount and its original USD/points unit in the existing confirmed payload. Duplicate amount/unit entry and the repeated confirmation checkbox are removed from RewardFields; EXPECTED remains unconfirmed, and backend saved-reward status protections remain unchanged. P&G promotion guidance appears under Rewards earned or expected: Other provider → P&G Brand, offer description, products and Buy/Spend requirement. Receipt-scan qualification messages explain that eligibility checks are separate from recording/valuing rewards, without misleading zero-quantity/unknown-dollar displays.

All Add Receipt sections use keyboard-accessible Show / Hide disclosures: receipt details, products, shared discounts, tax/rewards used/payments, printed footer, rewards earned/expected, and evidence. Draft values remain present while collapsed; validation opens every enclosing disclosure before focusing an invalid input. Existing files updated: ReceiptCreate.tsx, RewardFields.tsx, rewardStatus.ts, EarningInput.java, ReceiptCreate.test.tsx, ReceiptInputTest.java. No new files or database changes.

Automatic P&G/Fetch point valuation: new API reward entries calculate Fetch at 1000 points = 1 USD and P&G Brand/P&G Good Everyday/PG at 250 points = 1 USD, using existing worksheet rates. Native points remain stored separately; 100 P&G points = $0.40 and 25 Fetch points = $0.025 before display rounding. Expected rewards remain excluded from confirmed savings. RewardFields hides manual conversion for both programs; rewardBody sends no hidden stale valuation for those point providers. EarningInput calculates the USD valuation and records its basis. Previously saved explicit valuations are not overwritten. No payout is generated. Existing EarningInput.java, RewardFields.tsx, rewardStatus.ts and ReceiptInputTest.java edited; no new files.

Existing points valuation repair: period totals, savings reports, account movements and receipt benefit projections now use SavingsReport.effectiveValuation. It preserves explicit historical valuations and derives a read-only worksheet value for missing Fetch (1000 points/$1) or P&G (250 points/$1) valuations. Other unknown providers remain incomplete. This fixes the existing 100-point P&G entry blocking all-time savings: it contributes $0.40 without rewriting receipt history or creating earnings. Expected/rejected status rules remain unchanged. Receipt Rewards displays the projected dollar value beside points. Existing SavingsReport.java, ProviderBalanceSummary.java, PostgresPlatformRepository.java, ReceiptDetail.tsx and SavingsReportTest.java updated; no new files.

Ibotta account credit: RewardFields and EarningStatusForm label CREDITED as Added to Ibotta account, separate from Expected (waiting for credit). Typing Ibotta as the provider selects the cash benefit form. Account credit is earned value held in Ibotta; the user's $20 withdrawal minimum is displayed as context, not an external eligibility guarantee. Rewards → Ibotta has an Account Credit & Withdrawals section with the recorded period-end balance and minimum. A withdrawal is a separate transfer/payout and must not generate another earning. Existing conversion records remain unchanged; this update does not initiate withdrawals or introduce a payout command. Existing RewardFields.tsx, EarningStatusForm.tsx and WorkspacePages.tsx edited; no new files or financial-rule changes.

Automatic Fetch receipt default (explicitly approved): new web receipt preview/create adds one received/CREDITED 25-point Fetch earning valued at $0.025 when no explicit Fetch entry is present. Any explicit Fetch amount/status replaces this default. Event identity is deterministic per receipt UUID, so repeated previews and idempotent saves do not duplicate it. Preview shows the benefit; only saving persists it. Existing receipts, edits and restores are not backfilled. Existing ReceiptInput.java, ReceiptCreate.tsx and ReceiptInputTest.java updated; no new files.

### Receipt total detail reports — October 24, 2026

Receipts period totals Regular price including tax, Actual OOP, Total amount saved and Total Paid after rebates are clickable. Each opens an existing-style modal with all active records in the selected day/month/inclusive range or all time, independent of receipt-list search/status/page. Reports support item/SKU/store/receipt/date/time/payment-type text search and date/time newest/oldest, store A–Z and item/payment-type A–Z sorting. Receipt links open source details. Regular price shows individual product regular extended values plus separate sales-tax rows. OOP shows individual cash/card tenders with period cash/card totals and matching cash/card subtotals; WC/RR/gift cards are excluded. Saved/net views show one contribution per receipt with its product descriptions. Period total remains unchanged by report search; Matching subtotal follows visible rows. Unknown contributions remain unknown.

Read-only authenticated GET /receipts/report?metric=regular|oop|saved|net&from=&to=&search=&sort= returns metric, existing Savings summary, rows, matchedTotal, matchedCash and matchedCard. Bounds are inclusive and optional; latest active revisions only. All money multiplication/aggregation uses Java BigDecimal and existing SavingsReport rules; the browser formats exact decimal strings. This introduces no saved records, schema or formula changes.

Existing files updated: src/main/java/coupons/repository/PostgresPlatformRepository.java; src/main/java/coupons/api/CouponApiService.java; frontend/src/api/client.ts; frontend/src/types/platform.ts; frontend/src/pages/WorkspacePages.tsx; frontend/src/App.css; src/test/java/coupons/SavingsReportTest.java; frontend/src/features/receipts/ReceiptCreate.test.tsx. No new files.

Confirmed benefits detail: clicking Confirmed benefits opens the selected-period searchable/sortable report of all receipt earnings with native amounts/units, dollar values, confirmed/unconfirmed/rejected grouping and source receipt links. Confirmed and unconfirmed dollar subtotals are separate; rejected rewards contribute to neither. Unknown point valuations remain unknown rather than zero. New metric=benefits in GET /receipts/report returns original earning and receipt revision for existing status commands. Confirm / update status opens the existing EarningStatusForm and scrolls/focuses its control; it uses recorded amount/unit once, revision checks and stable retries. Saving refreshes report and main workspace totals. Read-only users cannot change status. Existing WorkspacePages.tsx, types/platform.ts, PostgresPlatformRepository.java, ReceiptCreate.test.tsx and SavingsReportTest.java updated; no new files or new financial formulas.

Rewards Overview (approved definition): Total Rewards = Available Balances + Expected / Unconfirmed, shown separately on the Rewards main page for Balance through. Available sums positive recorded account closing balances after recorded spending/transfers; confirmed earnings are not added again. Expected includes outstanding active-receipt rewards dated on/before the selected day, excluding received, rejected and expired expected entries. Prior-month outstanding rewards remain included. Native points use saved/automatic USD valuations. Missing balance/valuation leaves the affected full total unknown and shows known subtotals/providers for review. Account search does not change these overview totals. GET /workspace adds rewardsOverview with available, expected, total, known subtotals and completeness context. Existing PostgresPlatformRepository.java, types/platform.ts, WorkspacePages.tsx and SavingsReportTest.java updated; no new files or personal record changes.


CVS receipt entry and correction now provide a collapsible ExtraBucks (ECB) used section. Each coupon has a separate amount and description, plus add/remove actions. Uses apply to the whole receipt, never individual products. Existing OTHER payment records with accountName ECB and explicit CHECKOUT_DEDUCTION roles are reused, retaining stable payment IDs and notes; no new schema or endpoint is introduced. Create input accepts the existing Payment accountName field. ECB rows are excluded from the generic payment editor to prevent duplicate entry and are listed in the create review breakdown. ECB uses are separate from Register Rewards, manufacturer coupons and cash/card Actual OOP. This change records descriptions, not source-earning matching.


Manufacturer product coupons now have an optional Required purchase quantity field in Add and Edit Receipt, for terms such as buy 2. It records the coupon’s required product count, not coupon count or purchased quantity. The total deduction remains entered once and is not multiplied by the requirement. Positive whole numbers are accepted; blank means not recorded. The existing CouponApplied.requiredQuantity model and serialized field are reused, exposed in create/correction requests and edit projections, and shown on saved receipt discount details. Existing requirements are retained when omitted by API callers; explicit null clears one. No database schema or new files are needed.


CVS earned rewards: Add Receipt and the saved receipt Add rebate or reward form default to CVS ExtraBucks (ECB), stored as THIRD_PARTY_REBATE / STORE_REWARD with provider ECB so earned and used activity share the ECB account. Enter the earned USD amount and required reward name once. The CVS reward type selector includes 2% purchase earnings; record the CVS-reported amount when known, mark Expected while awaiting credit, or add it later via the saved receipt Rewards tab. No automatic 2% calculation or invented posting date is applied. New ECB entries default to whole receipt with optional associations available. CVS receipt entry, editing and totals hide unused Walgreens-specific RR/WC controls and footer; existing unusual saved entries are retained for correction. Shared financial wording uses redeemed rewards rather than Walgreens-specific terminology.


Member rewards without receipts: Walgreens Cash, Register Rewards, ECB and Walmart Cash individual account pages provide Add member reward without a receipt with reward date, positive USD amount and description. POST /accounts/member-rewards is an authenticated idempotent write using the existing AccountAdjustment and provider_account_adjustments ledger, with actor/time and Member reward description. It increases account activity/closing balance and overview availability; it does not create receipts, change receipt savings, or invent openings. Existing ledger beginning balances/order rules still apply. Register Rewards coupon inventory remains a separate non-earning record for matching; avoid counting the same earning again. Walgreens Cash Provider-Reported Observations is initially collapsed and supports Show / Hide without changing printed observations.


Dashboard sections Your next shopping trip, Rewards to keep track of and Recent receipts each have independent accessible Collapse / Expand buttons with aria-expanded and aria-controls. Sections start expanded, preserve content and retain navigation actions when collapsed. Confirmed benefit value explanatory text explicitly identifies receipt earnings in the selected period, separate from account closing balances. Dashboard financial formulas and personal receipt/account records are unchanged. Spreadsheet comparisons must compare the same date range and distinguish opening balances plus net account activity from receipt earnings; account balances are not substituted into receipt savings.


Clipped coupons and catalog cleanup: Deals & promotions and Coupons now expose permanent Delete actions with write access and revision checks. DELETE /deals/{id} and DELETE /clipped-coupons/{id} remove the catalog record and its own revision history, detach current plan associations while retaining product prices, and remove deal source artifacts only when not shared. Normal plan revision history remains; no deletion backup is created. Redeemed coupons cannot be deleted. Receipt entry accepts clippedCouponIds and offers saved coupon selection per product and in shared/receipt-wide discounts. Selection creates one editable discount with the coupon UUID, description, type and buy requirement; use the shared selector for a coupon covering several products. Percentage/free-item deductions use the actual recorded receipt amount. Selecting does not consume a coupon; receipt creation and all selected coupon redemptions commit atomically in PostgreSQL. Expired, already-used, or ineligible coupons cause rollback. The Coupons page then shows Applied and the receipt connection. Do not manually repeat a selected deduction. Existing receipt corrections retain their separate workflow; editing a discount does not automatically release a previously redeemed coupon. No new schema migration or files are introduced.

Changed application and test files: frontend/src/App.tsx, frontend/src/pages/WorkspacePages.tsx, frontend/src/features/receipts/ReceiptCreate.tsx, frontend/src/types/platform.ts, frontend/src/App.css, frontend/src/features/receipts/ReceiptCreate.test.tsx, src/main/java/coupons/api/CouponApiService.java, src/main/java/coupons/api/ReceiptInput.java, src/main/java/coupons/repository/PostgresPlatformRepository.java, src/main/java/coupons/repository/PostgresTransactionRepository.java, src/main/java/coupons/repository/PostgresClippedCouponRepository.java, src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java.


ECB non-receipt member rewards now support optional expirationDate in POST /accounts/member-rewards and an ECB expiration date input. Dates before the reward date are rejected. AccountAdjustment retains the date in its existing serialized payload; its legacy eight-argument constructor and older records default to unknown expiration. No new SQL column or migration is required. Account activity displays Expires YYYY-MM-DD. The date is informational: it does not automatically debit balances or infer remaining coupon value after spending. Existing saved financial amounts are unchanged.
Changed files: WorkspacePages.tsx, CouponApiService.java, AccountAdjustment.java, ProviderBalanceSummary.java, ReceiptCreate.test.tsx and ProviderBalanceSummaryTest.java.
