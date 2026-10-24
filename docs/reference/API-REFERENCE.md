# Online API navigation reference

## Revision history

| Version | Date | Changes | Verification |
|---|---|---|---|
| 1.0 | 2026-10-21 | Original reference snapshot | Static inspection |
| 1.1 | 2026-10-22 | Planning reward progress, unknown-value semantics and current UPC policy | Source updated; final checks not run |
| 1.2 | 2026-10-22 | Receipt evidence/reconciliation, distinct purchase/scan and partial benefit values | See VERIFICATION.md |

## Revision 1.2 — receipt evidence and reconciliation

Receipt create/preview accepts optional printedTotalBasis/paymentEntryStatus and payments[].role for OTHER. Omitted metadata defaults UNKNOWN. These facts participate in reviewToken digest. Save acknowledgment uses the basis-aware result, with legacy fallback for UNKNOWN. No new endpoints.

Totals adds paymentReview, benefitObservations, benefitAdjustedCashSpending; savings adds known confirmed subtotal, nullable complete total/completeness and unvalued providers. Legacy fields remain unchanged. rewardOffers suggestions include form/sourceReceiptId/sourceReceiptNumber/sourceDate. EarningInput.requirement accepts nullable type/amount with category Purchase/ReceiptScan/Receipt/Other; numerical BUY/SPEND disallow categories.


## Revision 1.1 — current behavior and status

Response extension, no new endpoint: workspace plans[].pricing and checkout.summary expose dealProgress, knownPotentialRewards, rewardsComplete and reviewRequired. Existing expectedRewards means complete potential rewards and is null when any applicable value is unknown. DealProgress includes nullable amounts, RewardEstimateStatus, ThresholdStatus and ReviewStatus. The Java Json codec serializes nested records and enum names; lossless frontend decoding preserves numeric lexemes as strings. New automated assertions are written but not executed. Current DealInput accepts strictSkuMatching; omission is false; strict mode without codes is rejected.



Original revision 1.0 inspection: October 21, 2026 (America/Chicago). Base Git commit: `fa3b8debc070cb5de990cbe9eb420fe1e56b7c06`. This reference describes the working tree, including pre-existing uncommitted checkout changes; it does not prove what is deployed online. No application tests were run for this documentation-only task.

Reviewed routing source: ../../src/main/java/coupons/api/CouponApiService.java. Existing detailed design: [API.md](API-REFERENCE.md). Paths below omit the browser proxy prefix /api.

| Method | Path | Purpose |
|---|---|---|
| GET | /health | Health check |
| GET | /workspace | Aggregate workspace and canWrite flag |
| POST | /deals | Create advertised deal; no PUT deal route currently exposed |
| POST | /shopping-plans | Create plan |
| GET / PUT | /shopping-plans/{id} | Retrieve/update plan |
| POST | /shopping-plans/{id}/purchases | Add planned item |
| PUT | /shopping-plans/{id}/purchases/{itemId} | Update planned item |
| GET / PUT | /shopping-plans/{id}/checkout | Retrieve/save checkout |
| POST | /clipped-coupons | Create saved coupon copies |
| GET | /clipped-coupons?provider=... | Find provider coupons |
| GET | /clipped-coupons/{id} | Retrieve coupon |
| POST | /clipped-coupons/{id}/redeem | Record coupon redemption |
| POST | /receipts/preview | Preview receipt and possible duplicates |
| POST | /receipts | Create receipt with preview confirmation |
| GET | /receipts/{id} | Retrieve receipt |
| GET | /receipts/{id}/totals | Financial/qualification view |
| GET | /receipts/{id}/history | Receipt history |
| GET | /receipts/{id}/evidence/{evidenceId} | Retrieve evidence |
| POST | /receipts/{id}/earnings/preview | Preview added earning |
| POST | /receipts/{id}/earnings | Add earning |
| PUT | /receipts/{id}/earnings/{earningId}/associations | Change selected products/quantities |
| PUT | /receipts/{id}/earnings/{earningId}/status | Change lifecycle state |

## Contracts and safeguards

ApiClient.request supplies Authorization: Bearer and uses no-store. Non-health routes authorize requests. Write operations follow idempotency journaling; revisions are required where route code calls revision(e). Use Idempotency-Key for writes and If-Match-Revision for revision-controlled updates. Do not assume all IDs share one revision counter. See actual route for the required entity revision.

DealInput.read validates allowed keys, constructs DealTerms and creates revision-one provenance. CouponInput, ReceiptInput, EarningInput, ReceiptEarningAddition and ReceiptEarningStatusUpdate own their respective input contracts. Json handles API encoding. Read exact fields in MODEL-DECLARATIONS.md and parser source before forming a write request.

Typical errors include malformed input, authorization failure, revision conflicts, missing required revision, duplicate/uncertain writes and incomplete financial inputs. ApiClient reports network failure as uncertain outcome and supports retaining the same retry key. Never turn an unknown write result into an unrelated new write automatically.

No credentials or live API responses are reproduced in this reference. No endpoints were invoked to generate it.


## October 23 saved receipt lifecycle and workspace updates

Existing authenticated workspace responses now include receiptPeriods (day/month,
legacy fixed-week and shoppingWeek:Sunday-date summary keys), ReceiptRow.sortTime,
and Savings.recordedCashCardPaid/recordedCashCardComplete. No money is summed in
the browser. Empty-period data is distinct from an older API missing the contract.

Owner-only receipt routes added:

| Method/path | Contract |
|---|---|
| POST /receipts/{id}/edit-preview | Requires If-Match-Revision. Validates correction fields and returns reviewToken/totals; no persistence. |
| PUT /receipts/{id} | Requires revision, Idempotency-Key, reason, matching reviewToken and acknowledgement for differences. Writes an audited revision. |
| DELETE /receipts/{id} | Requires revision, retry key, reason and confirmed=true. Soft-deletes, preserving history/evidence/reservations. |
| POST /receipts/{id}/restore | Requires revision, retry key, reason and confirmed=true. Restores the original receipt identity. |

Correction fields: retailer, receiptNumber, purchaseDate, printedTime,
purchaseChannel, printedTotalBasis, paymentEntryStatus, tax, reportedTotal, items,
payments, discounts, reason, reviewToken, acknowledgeDifference. Omitted editable
fields preserve previous values; untouched observations, evidence and financial
references are copied from the saved snapshot. Final repository reference checks
can still reject a previewed update. Existing redemptions cannot be reversed here.

Creation items.unitPrice and correction items.saleUnitPrice retain their existing
field names. Their UI label is now Unit price before listed discounts; no API/schema
rename occurred. Ordinary SALE_PROMOTION deductions remain separate total reductions.


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

## Revision 1.6 — account views and printed observations

`GET /accounts/activity?provider=Register%20Rewards&from=2026-10-01&to=2026-10-31` returns provider/from/to, beginning and ending Balance objects, known earned/spent USD and native points, usdActivityComplete and structured activity. Each activity has a movement with receiptId/eventId, readable receiptNumber/description, rewards[], before/after USD and points, orderKnown and valuationBasis. Unknown date activity remains visible but makes period USD activity incomplete. GET receipt adds rrUses[]; workspace adds accountActivity[].

`POST /accounts/openings` is owner-only and journaled with Idempotency-Key. Body: observationId UUID, provider, date (beginning of day), points/USD decimal strings or null (`points`, `usd`), valuationBasis, sourceNote, confirmedBeginningOfDay:true. At least one amount is required. This appends an AccountOpening observation, not an earning. An uncertain retry retains the original body/key; the durable journal may require reconciliation after a crash. No account-adjustment/transfer/redemption/reconciliation-write UI is implied.

Receipt preview/create/edit-preview/PUT accept optional `printedFooter`: status (PRINTED/NOT_PRINTED/UNKNOWN), myWalgreensSavings, storeCouponSavings, manufacturerCouponSavings, totalSavings, savingsValuePercent, wcOpening, wcEarned, wcUsed, wcClosing. Amounts are nonnegative decimal strings or null; blank is sent as null, zero as "0". Omitted fields preserve existing observations on edit. Unknown keys are rejected. Existing correction revision/token/reason/acknowledgement rules apply. Whole-receipt discount deletion uses omission from the complete replacement discounts array; partially filled rows remain invalid.

The account response also contains `conversions[]`: latest recorded ProviderRedemption events requested in the selected range, with originalUnitsUsed, sourceUsdValue, destinationAccount/destinationUsdValue, status and completion date. These are actual recorded redemption lifecycle observations, distinct from a point earning’s saved valuation. The UI marks requested reservations versus received fulfillment.


## Revision 1.7 — receipt RR correction and matching

GET /receipts/{id}/rr-matches returns use rows: eventId, amount, description, matched and options[]. Options include sourceId, receiptEarning, amount, promotion, sourceReceiptId/Number, earnedDate and expires. POST to the same route requires owner auth, Idempotency-Key, If-Match-Revision and {usageEventId,sourceId,receiptEarning,reason}. Matching validates availability under the receipt-state transaction lock and writes only RRUsageLink. Existing uses/earnings are not replayed. Unknown same-day order, future/expired/unprinted/wrong-value/consumed sources are excluded; missing dates must be recorded first.

Receipt edit-preview/PUT accept rrUses[] {eventId,amount,recordedDescription,notes}; omission preserves existing observations. One row per coupon. rrReclassifications[] {kind: PAYMENT|COUPON_APPLIED,id,targetEventId} explicitly moves a saved payment/discount to a new RR use, or identifies it as a duplicate of an existing use with exactly the same amount. The original entry must be removed. Corresponding printed-savings inclusion references are remapped; mismatches are rejected. Payments accept optional role FUNDING/CHECKOUT_DEDUCTION/UNRESOLVED and accountName; additional deductions require a named Other account. GET receipt adds paymentRoles. Pure receipt correction validation returns a useful field/domain message; SQL/repository errors remain generic.

Each RR match-use response also includes `needsReview`. A conflicting later receipt correction can leave an existing source reserved while its association requires review; it must not appear as a newly available coupon or be represented as another deduction.


## Revision 1.8 — RR without an earning receipt

POST /receipts/{id}/rr-matches/unknown-source requires owner authorization, Idempotency-Key and If-Match-Revision. Body: usageEventId, stable rewardId, description, nullable earnedDate/expirationDate, reason and confirmRecordedUse:true. Face value is derived server-side from the existing positive unmatched RR use. ReceiptRRMatching.standalone validates active receipt/revision, existing unmatched use and any known dates; the adapter inserts RegisterReward (earned_receipt_id NULL) and RRUsageLink atomically. No earning event is posted. Unknown dates remain NULL. This explicit unknown-history workflow does not loosen automatic eligible-source filtering.


## Revision 1.10 — independent Rewards inventory

GET /rewards/register-rewards returns standalone coupon rows {rewardId,description,amount,earnedDate,expirationDate,status}; active non-voided sources without an earning receipt are listed. Status is UNUSED, MATCHED or REDEEMED and does not imply a current dollar-account balance. POST to the same route is owner-only, journaled with Idempotency-Key, and accepts {rewardId,description,amount,earnedDate,expirationDate,confirmAvailable:true}. Dates are optional/null, amount is a positive decimal string. No receipt/revision is required. Save under the shared state lock inserts a standalone RegisterReward only.

Receipt RR match options add historyUnknown. A standalone source with unknown earned date, or same-day history without an earning time, can be selected with confirmUnknownHistory:true in the existing revision-checked POST /receipts/{id}/rr-matches. Matching revalidates under lock and appends the availability confirmation to link reason. Receipt-based sources retain strict temporal eligibility; known date/expiry conflicts and reservations remain enforced.


## Revision 1.11 — visible recorded receipt calculations

Receipt totals add calculationChecks:{beforeFunding,afterRewards}. Each CalculationScenario has basis, calculatedTotal, purchaseDifference, knownFundingSubtotal, paymentDifference and verified. paymentDifference is arithmetic against the recorded funding subtotal, even when completeness is unknown; verified requires that scenario to match explicitly recorded basis plus COMPLETE entry and no unresolved payment roles. verified does not mean zero difference. paymentReview retains its existing strict null/status semantics and acknowledgement behavior. No request contract or stored metadata changes.


## Revision 1.12 — simplified receipt totals display

No API change. calculationChecks and strict paymentReview remain available in responses but the browser receipt totals/review hides the calculated-total rows and comparison table. Printed store coupon savings input/storage is retained while its saved Totals row is hidden.


## Revision 1.13 — reuse existing files and align CLI rebate display

No response/request contract change. The UI now renders existing savings.paidAfterRebates, preserving CLI payment/valuation availability rules; root benefitAdjustedCashSpending retains its stricter compatibility semantics and is no longer the primary displayed metric. Internal read DTO classes move to existing nested owners; their JSON fields are unchanged. Public receipt IDs, journal keys, revisions and persisted schemas are untouched.

### Optional RR matching note

The receipt RR matching form accepts an optional matching note. Selecting an eligible reward is sufficient; unknown-history availability confirmation is still required when applicable. POST `/receipts/{id}/rr-matches` accepts an omitted, null, empty or whitespace-only `reason`; the server records the neutral audit description “Matched recorded RR use to selected reward” when no note is supplied. A supplied note is retained after trimming. This does not create another reward or deduction. Existing receipt correction/deletion reasons and unknown-source confirmation rules are unchanged. Implementation: `frontend/src/features/receipts/ReceiptDetail.tsx`, `src/main/java/coupons/api/CouponApiService.java`; regression: existing `ReceiptCreate.test.tsx`.

### Receipt period reporting update — October 23, 2026

Receipts → Period totals displays Regular price including tax, Actual OOP, Total amount saved, Confirmed benefits, Total Paid after rebates, and Total % saved. Pending rebates are not shown or included. Existing Java CLI savings rules and saved points valuations apply; original receipt data and valuations are unchanged. Cash/card Actual OOP excludes rewards used and gift cards. Percent saved uses regular merchandise plus recorded tax as denominator; a zero/unknown denominator remains Not recorded. Unknown inputs retain existing null/completeness behavior.

Select Purchase day or Month, enter inclusive Start date and End date, or click Month to date (the current local calendar month through today). Selecting a day/month clears the range; entering a range clears day/month. Clear filters returns to All time. Totals cover all active latest receipt revisions across every page, regardless of text search or status filter. Search continues across dates/statuses; without search the receipt list follows the selected period. Date-range loading/errors never reuse the prior summary. Invalid or incomplete ranges require correction.

Read-only authenticated API: GET /receipts/summary?from=YYYY-MM-DD&to=YYYY-MM-DD returns the existing Savings shape. Both dates are required, start must not exceed end, and undated/deleted receipts are excluded. Range calculation runs in Java through SavingsReport.range and the existing savings projection; the browser performs no money aggregation. No database schema change.

Existing implementation files changed: src/main/java/coupons/SavingsReport.java; src/main/java/coupons/repository/PostgresPlatformRepository.java; src/main/java/coupons/api/CouponApiService.java; frontend/src/api/client.ts; frontend/src/pages/WorkspacePages.tsx. Existing regression files: src/test/java/coupons/SavingsReportTest.java; frontend/src/features/receipts/ReceiptCreate.test.tsx. No new files. Layout uses the existing centered three-column receipt-period-totals grid (six metrics in two rows), collapsing to one column on narrow screens; existing typography/control tokens remain unchanged.

### Receipt entry breakdown and field errors — October 23, 2026

Add Receipt now provides a Review entered breakdown button near the top, in addition to the existing calculated review action. The Java preview review lists product descriptions, regular prices, quantities, recorded extended prices and individual attached discounts; shared/receipt-wide discounts appear separately with their selected products. Existing tax, tender, reward-used, earned-benefit and savings calculations remain in the financial review. No additional browser money calculations or financial events are introduced.

Receipt-entry invalid fields receive a red border and pale red background, aria-invalid, and a descriptive error. Validation scrolls to and focuses the first invalid control; collapsed containing details are opened. Received reward confirmations explain the original unit, compare exact decimal strings without Number conversion, and show an inline error if the confirmed amount differs. Equivalent values such as 5 and 5.00 remain valid. Correct Reward amount first when the actual received amount differs. Points and their saved USD valuation remain separate. New-earning API validation identifies the offer/provider and expected amount/unit rather than displaying status-only workflow wording. Existing saved-reward status restrictions remain unchanged.

The draft information message is explanatory, not an error: the receipt is unsaved until review and Save receipt; closing discards unsaved input. It now says this in plain language. Existing files edited: frontend/src/features/receipts/ReceiptCreate.tsx; frontend/src/features/receipts/RewardFields.tsx; frontend/src/index.css; src/main/java/coupons/api/EarningInput.java. Existing regressions: ReceiptCreate.test.tsx and src/test/java/coupons/api/ReceiptInputTest.java. No new files or database-schema changes.

### Multiple Register Rewards used during receipt creation — October 23, 2026

Add Receipt → Register Rewards used now offers Add RR used and removable individual rows, each with one dollar amount and optional description. Enter $5, $5 and $4 as three separate coupons; duplicate dollar values remain distinct through stable event IDs. Blank descriptions default to Register Rewards used. The review shows each coupon and the Java-calculated RR total. These receipt-wide deductions must not be entered again as discounts or payment tenders. Existing post-save matching offers a separate source match for each use; matching does not create another deduction. Edit Receipt already supports separate RR uses and is unchanged.

Receipt preview/create accept rrUses:[{eventId,amount,recordedDescription?}] with at most 100 positive entries and unique event IDs. The legacy rrUsed/rrUsageId request remains supported; a nonzero legacy amount alongside rrUses is rejected to prevent double counting. Existing UnidentifiedRR observations store the separate uses; no schema/model migration or new financial formula is introduced. Preview tokens and stable save retries retain the individual IDs.

Existing files updated: frontend/src/features/receipts/ReceiptCreate.tsx; src/main/java/coupons/api/ReceiptInput.java; frontend/src/features/receipts/ReceiptCreate.test.tsx; src/test/java/coupons/api/ReceiptInputTest.java. No new files. Regression coverage verifies $5+$5+$4 yields $14, equal coupons have different IDs, preview/save preserve identities, and zero/duplicate-ID/mixed legacy input is rejected.

### Simplified new reward entry and collapsible sections — October 23, 2026

New Fetch point earnings automatically receive the existing worksheet valuation of 1000 points = 1 USD in EarningInput when no explicit valuation is supplied. The UI hides Fetch manual valuation fields; 25 points retain the exact $0.025 saved value. This is a worksheet valuation, not a redemption or current market-rate guarantee. Existing explicit valuations and saved receipts are preserved. Other providers keep their existing valuation entry.

New reward entry records the entered amount once. Choosing Printed at register or Added to program account uses that same amount and its original USD/points unit in the existing confirmed payload. Duplicate amount/unit entry and the repeated confirmation checkbox are removed from RewardFields; EXPECTED remains unconfirmed, and backend saved-reward status protections remain unchanged. P&G promotion guidance appears under Rewards earned or expected: Other provider → P&G Brand, offer description, products and Buy/Spend requirement. Receipt-scan qualification messages explain that eligibility checks are separate from recording/valuing rewards, without misleading zero-quantity/unknown-dollar displays.

All Add Receipt sections use keyboard-accessible Show / Hide disclosures: receipt details, products, shared discounts, tax/rewards used/payments, printed footer, rewards earned/expected, and evidence. Draft values remain present while collapsed; validation opens every enclosing disclosure before focusing an invalid input. Existing files updated: ReceiptCreate.tsx, RewardFields.tsx, rewardStatus.ts, EarningInput.java, ReceiptCreate.test.tsx, ReceiptInputTest.java. No new files or database changes.

Received reward entry simplification: manually entered new rewards now default to received (CREDITED for account rewards, PAID/printed for RR); Expected remains an explicit option for rewards still awaited. Copying historical terms still defaults to Expected and does not copy receipt confirmation. Updating a saved reward status uses its existing recorded amount/unit automatically without duplicate input or checkbox; the existing revision-checked API payload still carries those exact values and explicit confirmation. No historical rewards are automatically reclassified. Receipt qualification diagnostics are collapsed under Optional qualification details; unknown eligibility does not negate a recorded receipt of rewards. Existing files: rewardStatus.ts, RewardFields.tsx, EarningStatusForm.tsx, EarningStatusForm.test.tsx, ReceiptDetail.tsx. No new files or backend financial-rule changes.

Automatic P&G/Fetch point valuation: new API reward entries calculate Fetch at 1000 points = 1 USD and P&G Brand/P&G Good Everyday/PG at 250 points = 1 USD, using existing worksheet rates. Native points remain stored separately; 100 P&G points = $0.40 and 25 Fetch points = $0.025 before display rounding. Expected rewards remain excluded from confirmed savings. RewardFields hides manual conversion for both programs; rewardBody sends no hidden stale valuation for those point providers. EarningInput calculates the USD valuation and records its basis. Previously saved explicit valuations are not overwritten. No payout is generated. Existing EarningInput.java, RewardFields.tsx, rewardStatus.ts and ReceiptInputTest.java edited; no new files.

Existing points valuation repair: period totals, savings reports, account movements and receipt benefit projections now use SavingsReport.effectiveValuation. It preserves explicit historical valuations and derives a read-only worksheet value for missing Fetch (1000 points/$1) or P&G (250 points/$1) valuations. Other unknown providers remain incomplete. This fixes the existing 100-point P&G entry blocking all-time savings: it contributes $0.40 without rewriting receipt history or creating earnings. Expected/rejected status rules remain unchanged. Receipt Rewards displays the projected dollar value beside points. Existing SavingsReport.java, ProviderBalanceSummary.java, PostgresPlatformRepository.java, ReceiptDetail.tsx and SavingsReportTest.java updated; no new files.

Decimal entry accepts a leading decimal point: .98 is equivalent to 0.98 for receipt tax, prices, discounts, RR, payments, footer observations and API decimal amounts. Exact BigDecimal/string precision is retained; currency display supplies the leading zero. Whole-number quantity rules remain unchanged; lone dots, negative nonnegative amounts and malformed decimals are rejected. Existing ReceiptCreate.tsx, ReceiptDetail.tsx, WorkspacePages.tsx, api/format.ts, ReceiptInput.java, ReceiptEarningStatusUpdate.java and existing receipt tests edited. No new files or database changes.

Automatic Fetch receipt default (explicitly approved): new web receipt preview/create adds one received/CREDITED 25-point Fetch earning valued at $0.025 when no explicit Fetch entry is present. Any explicit Fetch amount/status replaces this default. Event identity is deterministic per receipt UUID, so repeated previews and idempotent saves do not duplicate it. Preview shows the benefit; only saving persists it. Existing receipts, edits and restores are not backfilled. Existing ReceiptInput.java, ReceiptCreate.tsx and ReceiptInputTest.java updated; no new files.

Rewards date browsing: Rewards & Accounts adds account-name search, Balance through date, and Month to date (current local date). The main table's Month Start, Day Start, Day Close and allowed points columns are recalculated in Java for the selected date; future activity is excluded from those balances. Clicking an account opens the selected month's first day through that date. The redundant Open Account/Choose Account dropdown is removed; names remain clickable. GET /workspace?asOf=YYYY-MM-DD returns existing workspace balance projections for the chosen date; omitted asOf preserves existing latest-receipt behavior. Search filters account rows only. Unknown openings remain unknown. Existing PostgresPlatformRepository.java, CouponApiService.java, api/client.ts, WorkspacePages.tsx and ReceiptCreate.test.tsx updated; no new files or stored-data changes.

Receipt product lookup: existing active receipt history populates workspace.productLookup with SKU, description, most recent regular price, purchase date and source receipt. Latest revisions are deduplicated before filtering; deleted receipts are excluded. SKU leading zeros are preserved, and conflicting descriptions for one SKU remain separate selectable choices. Add Receipt suggests up to eight prefix matches as SKU is typed. Use this product fills description/SKU/last regular price and quantity 1; no historical promotions, coupons or rewards are copied. The user reviews the current price and discounts. This is a read-only product reference, not an import into on-hand inventory; historical purchases do not prove remaining stock. Existing PostgresPlatformRepository.java, types/platform.ts, App.tsx, ReceiptCreate.tsx, ReceiptCreate.test.tsx and SavingsReportTest.java updated. No new files or database tables.

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


Clipped coupons and catalog cleanup: Deals & promotions and Coupons now expose permanent Delete actions with write access and revision checks. DELETE /deals/{id} and DELETE /clipped-coupons/{id} remove the catalog record and its own revision history, detach current plan associations while retaining product prices, and remove deal source artifacts only when not shared. Normal plan revision history remains; no deletion backup is created. Redeemed coupons cannot be deleted. Receipt entry accepts clippedCouponIds and offers saved coupon selection per product and in shared/receipt-wide discounts. Selection creates one editable discount with the coupon UUID, description, type and buy requirement; use the shared selector for a coupon covering several products. Percentage/free-item deductions use the actual recorded receipt amount. Selecting does not consume a coupon; receipt creation and all selected coupon redemptions commit atomically in PostgreSQL. Expired, already-used, or ineligible coupons cause rollback. The Coupons page then shows Applied and the receipt connection. Do not manually repeat a selected deduction. Existing receipt corrections retain their separate workflow; editing a discount does not automatically release a previously redeemed coupon. No new schema migration or files are introduced.

Changed application and test files: frontend/src/App.tsx, frontend/src/pages/WorkspacePages.tsx, frontend/src/features/receipts/ReceiptCreate.tsx, frontend/src/types/platform.ts, frontend/src/App.css, frontend/src/features/receipts/ReceiptCreate.test.tsx, src/main/java/coupons/api/CouponApiService.java, src/main/java/coupons/api/ReceiptInput.java, src/main/java/coupons/repository/PostgresPlatformRepository.java, src/main/java/coupons/repository/PostgresTransactionRepository.java, src/main/java/coupons/repository/PostgresClippedCouponRepository.java, src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java.


ECB non-receipt member rewards now support optional expirationDate in POST /accounts/member-rewards and an ECB expiration date input. Dates before the reward date are rejected. AccountAdjustment retains the date in its existing serialized payload; its legacy eight-argument constructor and older records default to unknown expiration. No new SQL column or migration is required. Account activity displays Expires YYYY-MM-DD. The date is informational: it does not automatically debit balances or infer remaining coupon value after spending. Existing saved financial amounts are unchanged.
Changed files: WorkspacePages.tsx, CouponApiService.java, AccountAdjustment.java, ProviderBalanceSummary.java, ReceiptCreate.test.tsx and ProviderBalanceSummaryTest.java.


Receipt-earned ECB expiration: new CVS ExtraBucks/store reward entries expose Reward expiration date in the main reward form. Existing receipt reward cards display the saved expiration and offer Edit products / expiration. The existing EarningLinksForm includes an optional expiration date, prefilled from the saved earning; blank clears it. PUT /receipts/{receiptId}/earnings/{earningId}/associations accepts optional expirationDate, preserves it when omitted, and rejects dates before the recorded purchase day. Omitted product selections retain their prior values. Saving preserves earning ID, expected/confirmed amounts, valuation, status, payments and evidence, and records a receipt revision. This reuses the existing Earning.expirationDate field and existing endpoint; no schema migration or new files. Expiration entry does not automatically create an account debit.
Changed files: RewardFields.tsx, EarningLinksForm.tsx, ReceiptDetail.tsx, platform.ts, CouponApiService.java, ReceiptCreate.test.tsx and CouponApiServiceTest.java.


## Revision 1.15 — consolidated guide sources (2026-10-24)

This is the single canonical guide for this purpose. Historical text below preserves provenance; it does not override newer endpoint/authentication or deployment behavior. Current API identity comes from authenticated tokens, not a caller-supplied X-Actor; PostgreSQL retry journaling supersedes the early in-process-only description. Current migrations are V1–V9.

### Incorporated historical source: docs/design/API.md

# Grocery Intelligence Platform API

This Java 21 JDK HTTP server uses the existing PostgreSQL repositories. It does
not change the CLI, import local receipts, or provide a native mobile application.
Start instructions are in README.md.

## Authentication and authorization

The service serves **one owner's database**. All configured tokens belong to that
owner. It does not implement multiple users or row-level ownership. Use a separate
database and process for another owner; do not deploy this as a shared multi-user
service. No signup, password login, or token-refresh workflow is implemented.

Every endpoint except GET /health requires `Authorization: Bearer <token>`.
`COUPON_API_TOKEN` is required. If `COUPON_API_ADMIN_TOKEN` is set to a different
value, the first token is read-only and the admin token permits writes. If it is
absent, the required token has owner access. Tokens must be configured securely.
Audit actors are assigned by the server; `X-Actor` cannot impersonate another user.
The server binds to 127.0.0.1; use a trusted TLS proxy for remote access.

## Routes

| Method | Route | Behavior |
| --- | --- | --- |
| GET | /health | Process liveness, not database readiness |
| GET | /receipts/{uuid} | Active receipt, metadata, evidence IDs, full structured snapshot; no recalculation |
| POST | /shopping-plans | Create a revision-1 plan |
| GET | /shopping-plans/{uuid} | Retrieve current plan |
| PUT | /shopping-plans/{uuid} | Update plan name/date/retailer using expected revision |
| GET | /clipped-coupons?provider={name} | Coupons for the exact provider name |
| GET | /clipped-coupons/{uuid} | Coupon details, value, type, expiration and redemption status |
| POST | /clipped-coupons/{uuid}/redeem | Redeem against an existing active receipt |

Plan create body: `{"planId":"<uuid>","name":"Weekly shopping","retailer":null,"shoppingDate":"2026-10-25"}`.
Update body: `{"name":"Updated shopping","shoppingDate":"2026-10-26"}`.
Omitted optional update fields and existing planned-purchase links are preserved.
If supplied, `revision` must equal the next revision and `planId` must match the URL.
A date can explicitly be null. Changing purchase links through this endpoint is
rejected because the existing plan and purchase repositories do not perform an
atomic linked update. Purchase-line CRUD, coupon creation/editing, and receipt
creation/editing are not exposed by this API yet. There is no plan-list repository
contract; retrieve plans using their UUIDs.

Redemption body: `{"transactionId":"<receipt-uuid>","redeemedOn":"2026-10-25"}`.
Expiration, clipped status, revision and one-time use are checked by the existing
repository. Multiple different coupons may be redeemed on the same receipt.
This records the coupon lifecycle; it does not add a financial receipt deduction.
Receipt corrections and coupon redemption are separate repository transactions.

## Writes and retries

All writes require `Content-Type: application/json` and `Idempotency-Key` (1–200
characters). Update/redemption also require `If-Match-Revision` with a positive
integer. JSON bodies are limited to 64 KiB and nesting to 32 levels.

The production server journals successful retry results in PostgreSQL V5. Reuse
the same key, method, URI, revision header and exact body after a connection loss.
The original result is returned even after later revisions or server restart.
Changed content or route under the same key returns 409. Independent server
instances coordinate using PostgreSQL advisory locks. Keep keys for their full
retry lifetime; the implementation does not automatically prune the journal.

The journal and domain repositories use separate transactions. If a process or
connection fails between reservation and recording the result, the durable marker
remains **uncertain**. A retry returns 409 and never silently repeats the write.
Retrieve the resource and inspect history to reconcile it before issuing a new
write. This is safe duplicate prevention, not an atomic exactly-once API guarantee.
Known rejected writes can also leave an uncertain marker; fix the request and use
a new key after checking state. The public test constructor without a journal
provides bounded process-local retries only; production uses the durable journal.

Errors: 400 invalid fields, 401 missing/invalid token, 403 insufficient role,
404 missing resource, 405 method unsupported, 409 stale revision/retry conflict,
413 oversized body, 415 unsupported media type, 428 missing revision, 500 internal
failure. Internal errors never expose SQL, database secrets or device paths.

## Tests

`mvn -Dtest=CouponApiServiceTest test` runs real HTTP tests with isolated in-memory
fixtures. `PostgresApiIntegrationTest` additionally requires the dedicated
`COUPON_POSTGRES_TEST_URL`, `_USER`, `_PASSWORD` variables. It creates and removes
random private schemas in that database; use only a disposable test database.

## Step 6 workspace operations

- `GET /workspace`: authenticated read model containing receipt summaries,
  Java-calculated savings, plans/items, coupons, deals, rebates, inventory,
  calculated balances, separately reported observations, movements and promotion
  contributions. `canWrite` reflects the authenticated token's existing role.
- `GET /receipts/{id}/totals`: calculated totals and original reported values;
  discrepancies are retained. Deleted receipts have historical detail totals but
  remain excluded from active aggregate summaries.
- `GET /receipts/{id}/history`: immutable revision history.
- `GET /receipts/{id}/evidence/{evidenceId}`: binary, no-store attachment;
  evidence ownership must match the requested receipt.
- `POST /shopping-plans/{planId}/purchases`: add a planned item.
- `PUT /shopping-plans/{planId}/purchases/{purchaseId}`: replace editable item
  fields with an expected-revision precondition and idempotency key.

Planned-item fields: `plannedPurchaseId` on creation, `productName`, optional `productCode`, positive integer
`quantity`, optional decimal-string `targetUnitPrice`, optional `advertisedDealId`, and
`clippedCouponIds` UUID array. Item revisions are independent of plan metadata
revisions. Membership is resolved by the existing purchase `planId` lookup;
legacy plan ID arrays are not rewritten. Estimates cover merchandise only.
Financial numbers retain existing JSON wire compatibility; the frontend parses
numeric lexemes losslessly and submits monetary input as decimal strings.

There is no inventory-adjustment HTTP operation: independent approval cannot be
inferred from owner-token access. Existing inventory validators are unchanged.
This remains a single-owner API, not a multi-user authorization system.

## Existing receipt earning status update

`PUT /receipts/{receiptId}/earnings/{earningId}/status` requires owner access,
`Idempotency-Key`, and `If-Match-Revision` for the receipt revision. It uses the
existing receipt update repository/validators and writes a new audited receipt
revision. The server records the authenticated single owner's explicit Save as
approval of this receipt change; this does not establish independent inventory
approval or permit inventory edits.

Standard `status` choices are EXPECTED, REJECTED, PAID for REGISTER_REWARD, and
CREDITED for provider-account rewards. PAID means Printed at register for RR;
CREDITED means Added to program account. SUBMITTED, PENDING and APPROVED remain
stored/readable but are not assignable in this simplified endpoint.

Receiving requires `amount` as a decimal string, `unit` as the original currency
code or `points`, and `confirmAmountAndUnit: true`. The amount must equal the
existing recorded amount. Its original decimal scale, quantity, benefit form,
valuation, earning UUID, expected amount, links and prior audit snapshots are
preserved. Correcting quantities/values is outside this endpoint.

REJECTED accepts optional `rejectionReason` (maximum 1000 characters), appended
to the existing `recordedLifecycle` note; it never replaces an original note or
requires a new schema. Expected and previously confirmed amounts remain recorded,
but rejected benefits contribute no available balance or newly earned savings.
Confirmation fields are not accepted for Expected/Rejected.

The endpoint does not create RR redemptions or provider-redemption transactions.
RR earnings remain Register Rewards, not Walgreens Cash. External cashback
payouts cannot be recorded here, and existing non-RR PAID records require a
separate reconciliation workflow. Receipt dates remain original purchase dates;
this narrow operation does not add an independent account-credit effective date.

SavingsReport now excludes APPROVED from newly received value. A printed WC
footer does not override an explicit uncredited/rejected WC earning; fallback
remains for receipts with no separate WC earning observation. Account openings
remain excluded from earnings/savings. Existing code retains compatible status
codes internally; no historical status conversion or database migration is run.


## Web receipt creation

- `POST /receipts/preview`: owner-authenticated, non-persisting validation and
  Java calculation. Returns `reviewToken`, `totals`, `possibleDuplicates`, and
  evidence descriptors. It does not create a draft or audit record.
- `POST /receipts`: owner-authenticated creation with `Idempotency-Key` and the
  returned `reviewToken`. Returns 201 and the existing ReceiptWriteResult.
  Changed preview values, existing UUIDs, and possible existing receipts return
  409. Retries return their original result. Existing records are never updated
  through this route; revision-protected status updates remain separate.

The JSON request contains a stable transactionId, retailer, optional receiptNumber,
purchaseDate, optional printedTime, purchaseChannel, tax and reportedTotal;
items (lineItemId, description, optional sku/regularUnitPrice, unitPrice, integer
quantity); discounts (applicationId, label, amount, kind, lineItemIds, wholeReceipt);
payments (paymentId, type, amount, optional notes); optional rrUsed/rrUsageId;
earnings; and optional evidence. Monetary/point values are decimal strings.
The input boundary builds the existing domain models and invokes ReceiptValidator.

Earnings use stable earningId, kind, form, amount, status, optional provider/offer,
lineItemIds, expirationDate and saved valuation/valuationBasis. Credited or printed
benefits require confirmedAmount, unit and confirmAmountAndUnit=true, reusing the
existing simplified reward-status validator. Rejection may include rejectionReason.
No instrument, available coupon, inventory or separate claim is inferred.

Evidence uses evidenceId, mediaType and contentBase64. Receipt ownership is assigned
from transactionId and validated using the existing receipt/evidence repositories.
Limits: four attachments, 5 MiB each, 10 MiB total; supported types JPEG, PNG,
WebP and PDF. These two receipt routes allow a bounded 16 MiB JSON body; other
routes retain their 64 KiB limit. All creation records, evidence, financial snapshots
and audit history are committed atomically by the existing receipt repository.

If reported checkout differs from the Java calculation, creation additionally
requires acknowledgeDifference=true. Both values are retained. Review hashes
bind submitted values; they are not credentials or persistent draft tokens.
Explicit preview does not invent payment, valuation, inventory or account records.


### Incorporated historical source: docs/archive/api/API 2.md

# Coupon API contract

The optional `coupons.api.CouponApiServer` is a single-process HTTP adapter over
the existing PostgreSQL repositories. It is not a mobile app or a microservice.
All endpoints except `/health` require `Authorization: Bearer $COUPON_API_TOKEN`.
When `COUPON_API_ADMIN_TOKEN` is set, writes require that token. Clients should
send `X-Actor` and writes should include an `Idempotency-Key`.

* `GET /health`
* `GET /receipts/{transactionId}`
* `GET /shopping-plans/{planId}`
* `POST /shopping-plans` with `{planId,name,retailer?,shoppingDate?}`
* `PUT /shopping-plans/{planId}` with `name`, optional `retailer` and
  `If-Match-Revision`
* `GET /clipped-coupons?provider=Walgreens`
* `POST /clipped-coupons/{couponId}/redeem` with
  `{transactionId,redeemedOn?}` and `If-Match-Revision`

Successful responses are explicit JSON objects containing stable IDs and
revisions. A stale revision returns `409`; malformed requests return `400`.
Idempotency keys are replayed only when the request body matches. The current
minimal implementation keeps that replay cache in process; PostgreSQL remains
the source of truth for domain records.

Example mobile-client request (any HTTP-capable SDK):

```text
GET https://host/receipts/00000000-0000-0000-0000-000000000000
Authorization: Bearer <token>
```

This is an API contract/example client only; no native mobile SDK is claimed.

