# Core data dictionary

## Revision history

| Version | Date | Changes | Verification |
|---|---|---|---|
| 1.0 | 2026-10-21 | Original reference snapshot | Static inspection |
| 1.1 | 2026-10-22 | Planning reward progress, unknown-value semantics and current UPC policy | Source updated; final checks not run |
| 1.2 | 2026-10-22 | Receipt evidence/reconciliation, distinct purchase/scan and partial benefit values | See VERIFICATION.md |

## Revision 1.2 — receipt evidence and reconciliation

ReceiptMeta adds printedTotalBasis BEFORE_PAYMENT_FUNDING/AFTER_REWARD_REDEMPTIONS/UNKNOWN and paymentEntryStatus COMPLETE/PARTIAL/UNKNOWN. Missing/legacy fields stay UNKNOWN. Original retailer/receipt/time/channel are preserved.

PaymentRole FUNDING/CHECKOUT_DEDUCTION/UNRESOLVED derives from PaymentType and explicit worksheet Use metadata. OTHER without role and legacy REGISTER_REWARD tender are unresolved. ReceiptInput role is optional; explicit OTHER role maps to existing worksheet storage.

PaymentReconciliation contains printedTotal, calculatedPurchaseTotal, purchaseDifference, knownFundingSubtotal, nullable recordedCashCardPaid, giftCardFunding, otherFunding, nullable difference, status BALANCED/DIFFERENCE/INCOMPLETE/CANNOT_VERIFY, basis, entry status, unresolved IDs, reason and acknowledgmentRequired. Known subtotals do not imply completeness.

OfferRequirement.category adds Purchase and ReceiptScan, preserving Receipt/Other. benefitObservations retains native expected/confirmed amounts, status and valuation. Savings projection adds knownConfirmedBenefitValue, confirmedBenefitValue, confirmedBenefitsComplete and unvaluedProviders; receipt projection adds benefitAdjustedCashSpending.


## Revision 1.1 — current behavior and status

ShoppingEstimate.DealProgress (nested derived record): dealId; qualifyingQuantity; nullable requiredQuantity/quantityMissing; nullable qualifyingSpend/requiredSpend/spendMissing; thresholdStatus NOT_APPLICABLE/MET/NOT_MET/UNKNOWN; storeStatus/dateStatus NOT_RECORDED/MATCHES/NEEDS_REVIEW; rewardStatus NOT_APPLICABLE/KNOWN/UNKNOWN; nullable potentialReward/rewardIfCompleted; messages. Known zero differs from unknown and not-applicable.

ShoppingEstimate adds dealProgress, knownPotentialRewards, rewardsComplete and reviewRequired. expectedRewards remains the complete potential total, null when unknown. finalCost is unknown if register cost or complete reward total is unknown. DealTerms.strictSkuMatching is boolean, defaults false in compatibility constructors/API omission, requires nonempty qualifyingSkus when true. Progress is not persisted and never credits rewards.



Original revision 1.0 inspection: October 21, 2026 (America/Chicago). Base Git commit: `fa3b8debc070cb5de990cbe9eb420fe1e56b7c06`. This reference describes the working tree, including pre-existing uncommitted checkout changes; it does not prove what is deployed online. No application tests were run for this documentation-only task.

This is the reviewed business-field reference. MODEL-DECLARATIONS.md supplies a broader declaration datasheet for records and frontend contracts. Names in different models may describe different stages of a purchase.

## Advertised deal: AdvertisedDeal

| Field | Meaning / constraint |
|---|---|
| dealId | Stable UUID; referenced by planned items |
| title / interpretedTerms | Display and recorded wording, not parsed arithmetic |
| retailer | Store checked against plan retailer, ignoring case |
| validFrom / validThrough | Inclusive advertised dates; absent dates trigger provisional warning in plan estimate |
| sourceArtifactIds | Supporting source artifact references |
| revision | Positive version; updates use expected revision + 1 |
| pricing | DealTerms structured arithmetic |

## DealTerms

| Field | Type / meaning | Calculation behavior |
|---|---|---|
| type | SALE, MULTIBUY, BUY_GET, SPEND_REWARD, QUANTITY_REWARD | Selects arithmetic |
| productDescription | Text | No automatic product-name eligibility matching |
| regularUnitPrice | Optional decimal | Recorded offer context; plan regular totals use item prices |
| promotionalPrice | Optional decimal; required for price deals | Unit, bundle or discounted-item price according to type |
| minimumQuantity | Integer ≥ 1 | Quantity threshold / bundle size |
| minimumSpend | Positive decimal for SPEND_REWARD | Spending threshold |
| freeQuantity | Nonnegative integer; positive for BUY_GET | Discounted units per buy/get bundle |
| rewardAmount | Positive decimal for reward deals | Expected reward per group |
| repeatable | Boolean | Allows additional qualifying groups |
| limit | Optional positive integer | Caps groups |
| qualifyingSkus | List of strings; null becomes empty | List restricts matching only when strictSkuMatching is true |
| qualifications / loyaltyRequirement / exclusions | Optional text | Requests human review; no semantic verification |
| cheapestItemsDiscounted | Boolean | Enables cheapest-item buy/get allocation |

Missing today: explicit reward program, spend basis, purchase channel and strict-matching opt-in in DealTerms. Do not document proposed fields as existing.

## PlannedPurchase

| Field | Meaning |
|---|---|
| plannedPurchaseId / planId | Item identity and owning plan |
| productName / productCode | Description and optional SKU/UPC |
| quantity | Positive purchased/planned unit count |
| advertisedDealId | One optional linked deal per planned item |
| clippedCouponIds | Distinct linked coupon IDs |
| regularUnitPrice / saleUnitPrice / targetUnitPrice | Unit-price inputs; estimate preference is sale → regular → target |
| priceSource | MANUAL or DEAL; DEAL requires a linked promotion |
| revision | Positive version for correction/concurrency |

ShoppingPlan supplies planId, name, retailer, shoppingDate, plannedPurchaseIds and revision. Repository loading of items is important: do not infer an empty plan just from plannedPurchaseIds in one response; the workspace returns its items separately.

## Receipt reward: ReceiptObservations.Earning

| Field | Meaning |
|---|---|
| earningId | Observation identity |
| kind | REGISTER_REWARD, WALGREENS_CASH or THIRD_PARTY_REBATE |
| provider / offerReference | Program and offer description |
| scope / lineItemIds | ITEM, MULTI_ITEM, RECEIPT or UNKNOWN and selected receipt lines |
| expected / confirmed | Benefit values at different lifecycle stages |
| status | Lifecycle status; valid transitions depend on program and received confirmation |
| requirement | OfferRequirement: type, amount, category, spendBasis |
| details | Qualifying quantities, submission/payout context; see EarningDetails |
| expirationDate | Optional expiry |
| valuation | Recorded monetary valuation, particularly for points; not a second earning |
| evidenceIds | Supporting evidence |
| instrumentId / claimId / recordedLifecycle | Links/context carried by the reward model |

OfferRequirement uses BUY/SPEND and SpendBasis SALE_BEFORE_COUPONS, SALE_AFTER_COUPONS or UNKNOWN. Selected quantities may not exceed purchased units. The receipt form distinguishes STORE_COUPON (RR) from STORE_REWARD (Walgreens Cash); other-provider forms include points, cashback and gift-card values.

## Checkout values

| Field | Meaning |
|---|---|
| promoCode | Label only; no automatic retailer-code lookup |
| promoDiscount | Manually recorded additional discount amount |
| taxRate / taxableBase | Inputs for calculated tax |
| actualTax | Receipt tax override; takes precedence over rate calculation |
| registerRewardsCoupon | Combined RR deduction before amount due |
| walgreensCashUsed / giftCardUsed | Funding the amount due |
| cashPayment / cardPayment / otherPayment | Other funding amounts |
| notes / revision | Context and concurrency version |
| summary.expectedRewards | Complete potential total, null when unknown; displayed with completeness/review in checkout |

## Other model families

Coupon/CouponTerms/ClippedCoupon describe offer conditions and saved usable copies; CouponApplied records receipt deductions. RegisterReward and RegisterRewardRedemption concern reward instruments and their use. Provider account openings/adjustments/redemptions establish account ledger events. GiftCardPromotion tracks eligible contributions across receipts. InventoryBalance and inventory adjustments concern stock, not reward credits. The declaration and file indexes link all these model definitions without conflating their roles.


## October 23 receipt terminology and read-model additions

- PurchaseLineSnap.saleUnitPrice / creation items.unitPrice: stored unit-price
  operand before separately listed deductions; UI label Unit price before listed
  discounts. Regular unit price remains the regular-value savings baseline.
- totals.saleMerchandise: sum of those unit prices times quantities, before listed
  ordinary discounts; UI label Merchandise before listed discounts.
- DiscountKind.SALE_PROMOTION: separate total additional reduction for selected
  lines/quantities, not a derived unit price. It must not repeat a reduction already
  represented by the unit-price operand.
- Savings.recordedCashCardPaid: known aggregate cash/card subtotal; the separate
  recordedCashCardComplete flag indicates existing aggregate completeness rules.
- Workspace.receiptPeriods: day/month/fixed-week keys plus shoppingWeek:Sunday-date
  summaries. The week selector is hidden; shopping weeks span Sunday–Saturday.
- ReceiptRow.sortTime: Java-parsed local time for ordering; original text preserved.
- Receipt status edits create revisions; deletion is soft and restoration retains
  the same identity. Refer to API-REFERENCE.md and operations/RECEIPT_TOTALS.md.


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

## Revision 1.6 — printed footer and account activity

printedFooter is an API input projection of existing ReceiptObservations. `myWalgreensSavings` → SavingsCategory “myWalgreens Savings”; storeCouponSavings → “Store coupon savings”; manufacturerCouponSavings → “Mfg coupon savings”; totalSavings → “Your total Savings”; savingsValuePercent → receiptSavingValuePercent. WC fields map to WalgreensCashAccount(startingBalance,used,earned,endingBalance). Null means unknown; explicit zero is retained. Existing category IDs and unchanged explanation links survive updates; a changed amount clears stale explanation confirmation. Unsubmitted categories remain intact.

Movement now exposes receiptId/eventId and parsed receipt time without changing financial deltas. AccountActivityView.Activity joins readable descriptions and RewardLink records with before/after USD/points, orderKnown and valuationBasis. RewardLink includes source earning/instrument, receipt/date/time, promotion/products/status, spent receipt and matched amount. These are read projections, not additional ledger events.


## Revision 1.7 — receipt RR correction and matching

ReceiptRRMatching.Use/Option are transient availability DTOs. rrUses correction input maps to existing ReceiptObservations.UnidentifiedRR. rrReclassifications expresses an explicit source event → existing/new UNIDENTIFIED_RR mapping and preserves related printed savings explanation references. Payment role/account edits patch existing worksheet uses; obsolete use IDs are removed when the corresponding payment/use is removed. RRUsageLink remains the persisted source-use association with actor, reason and confirmation time. Read response paymentRoles explains each existing payment’s classification.


## Revision 1.8 — RR without an earning receipt

Standalone unknown-source RegisterReward: earnedReceiptId=NULL; earnedDate/expirationDate independently nullable; sourceDescription retains entered description. Face value equals the already recorded RR deduction. RRUsageLink has receiptEarning=false and retains source note/actor/time. StandaloneMatch is a transient validation result (reward + link), not a new persisted record type. Frontend RewardLink.receiptId is nullable.


## Revision 1.9 — Rewards points-account display

No stored record or API response fields change. Rewards account display capabilities are restricted by provider name to MyPoints/Fetch/P&G Brand/Swagbucks/Merryfield. Existing points data on other providers stays stored but is hidden from these Rewards pages. Account opening UI sends null points for other accounts.


## Revision 1.10 — independent Rewards inventory

StandaloneRR is a transient inventory projection with rewardId, description, USD face amount, nullable earned/expiry dates and UNUSED/MATCHED/REDEEMED reservation status. ReceiptRRMatching.Option.historyUnknown distinguishes standalone sources whose earning order is not established. confirmUnknownHistory is explicit matching evidence retained in the match reason/API journal; it does not create a date or receipt. RegisterReward stored shape remains unchanged.


## Revision 1.11 — visible recorded receipt calculations

TransactionCalculator.CalculationScenario and RecordedChecks are transient projections of existing receipt inputs. They expose always-available arithmetic without replacing PaymentReconciliation or persisting scenario metadata. verified means recorded basis and payment-entry completeness/roles support the scenario, not that its differences are zero.


## Revision 1.12 — simplified receipt totals display

No stored value/schema change. Store coupon SavingsCategory data and editable footer mapping remain; only its saved Totals presentation is hidden. CalculationScenario/RecordedChecks remain transient API projections without the browser comparison renderer.


## Revision 1.13 — reuse existing files and align CLI rebate display

Total Paid after rebates maps to SavingsReport.Totals.paidAfterRebates and existing savings.paidAfterRebates. Do not confuse this CLI/spreadsheet measure with root benefitAdjustedCashSpending, which still requires explicit complete/resolved entry. No new fields, fabricated zero values or point conversions. Current ownership: receipt create/review/shared printed fields and item discounts → ReceiptCreate.tsx; receipt editing/status/matching → ReceiptDetail.tsx; Rewards/account/standalone inventory and reward connection → WorkspacePages.tsx; browse/date/search helpers → api/format.ts; printed draft helpers → rewardStatus.ts; expanding textarea → components/ui.tsx; font/color/size tokens → index.css. AccountActivityView is nested in ProviderBalanceSummary.java; ReceiptRRMatching is nested in PostgresPlatformRepository.java; package-private correction/footer parsers share ReceiptInput.java. All earlier helper test cases remain in existing receipt/calculator/provider/repository test files.

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

Receipt product lookup: existing active receipt history populates workspace.productLookup with SKU, description, most recent regular price, purchase date and source receipt. Latest revisions are deduplicated before filtering; deleted receipts are excluded. SKU leading zeros are preserved, and conflicting descriptions for one SKU remain separate selectable choices. Add Receipt suggests up to eight prefix matches as SKU is typed. Use this product fills description/SKU/last regular price and quantity 1; no historical promotions, coupons or rewards are copied. The user reviews the current price and discounts. This is a read-only product reference, not an import into on-hand inventory; historical purchases do not prove remaining stock. Existing PostgresPlatformRepository.java, types/platform.ts, App.tsx, ReceiptCreate.tsx, ReceiptCreate.test.tsx and SavingsReportTest.java updated. No new files or database tables.

### Receipt total detail reports — October 24, 2026

Receipts period totals Regular price including tax, Actual OOP, Total amount saved and Total Paid after rebates are clickable. Each opens an existing-style modal with all active records in the selected day/month/inclusive range or all time, independent of receipt-list search/status/page. Reports support item/SKU/store/receipt/date/time/payment-type text search and date/time newest/oldest, store A–Z and item/payment-type A–Z sorting. Receipt links open source details. Regular price shows individual product regular extended values plus separate sales-tax rows. OOP shows individual cash/card tenders with period cash/card totals and matching cash/card subtotals; WC/RR/gift cards are excluded. Saved/net views show one contribution per receipt with its product descriptions. Period total remains unchanged by report search; Matching subtotal follows visible rows. Unknown contributions remain unknown.

Read-only authenticated GET /receipts/report?metric=regular|oop|saved|net&from=&to=&search=&sort= returns metric, existing Savings summary, rows, matchedTotal, matchedCash and matchedCard. Bounds are inclusive and optional; latest active revisions only. All money multiplication/aggregation uses Java BigDecimal and existing SavingsReport rules; the browser formats exact decimal strings. This introduces no saved records, schema or formula changes.

Existing files updated: src/main/java/coupons/repository/PostgresPlatformRepository.java; src/main/java/coupons/api/CouponApiService.java; frontend/src/api/client.ts; frontend/src/types/platform.ts; frontend/src/pages/WorkspacePages.tsx; frontend/src/App.css; src/test/java/coupons/SavingsReportTest.java; frontend/src/features/receipts/ReceiptCreate.test.tsx. No new files.


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
