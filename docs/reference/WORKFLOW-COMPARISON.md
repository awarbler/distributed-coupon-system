# CLI, online receipts and shopping plans

## Revision history

| Version | Date | Changes | Verification |
|---|---|---|---|
| 1.0 | 2026-10-21 | Original reference snapshot | Static inspection |
| 1.1 | 2026-10-22 | Planning reward progress, unknown-value semantics and current UPC policy | Source updated; final checks not run |
| 1.2 | 2026-10-22 | Receipt evidence/reconciliation, distinct purchase/scan and partial benefit values | See VERIFICATION.md |

## Revision 1.2 — receipt evidence and reconciliation

Online receipts now expose explicit total basis/completeness, separate actual out of pocket and payment reconciliation, native points/known benefit subtotal and distinct Purchase/ReceiptScan reward conditions. CLI requirement choices add Purchase/Scan without renumbering Receipt/Other; correction metadata is preserved. CLI legacy flows remain UNKNOWN rather than inferring completeness. ReceiptReport displays recorded out-of-pocket evidence. Persisted drafts, plan conversion, removal, rebates and automatic Fetch remain outside this work.


## Revision 1.1 — current behavior and status

Revision 1.1 working-tree implementation: shopping plans expose derived promotion progress, potential totals, unknown/zero/not-applicable states, store/date review and Add another item using the existing editor. Checkout displays potential values and warnings. Store/date conflicts do not verify eligibility or authorize sale discounts. Advertised-deal strictSkuMatching is already opt-in; coupon rules remain independent. Promotion editing, reward-program/spend-basis/channel modeling and actual receipt lifecycle parity remain outside this change. Source edits are not runtime verification.



Original revision 1.0 inspection: October 21, 2026 (America/Chicago). Base Git commit: `fa3b8debc070cb5de990cbe9eb420fe1e56b7c06`. This reference describes the working tree, including pre-existing uncommitted checkout changes; it does not prove what is deployed online. No application tests were run for this documentation-only task.

This table documents inspected paths, not a claim of complete behavioral parity for every feature.

| Capability | CLI receipt path | Online receipt path | Online plan / checkout |
|---|---|---|---|
| Named RR / Walgreens Cash / provider | Yes | RewardFields | Missing in DealTerms |
| BUY/SPEND requirement | Yes | Yes | Generic quantity/spend reward types |
| Product association | Receipt-line selection | ReceiptLineSelector | One deal ID per item |
| Mandatory SKU list | Not required by examined reward selection | Selected receipt lines | Enforced when list populated |
| Spend basis | Before coupons or unknown in examined entry | Before or after coupons | Before coupon savings; no selector |
| Expected/received status | Recorded | Status form and confirmation | Estimate only |
| RR expiration | Reward observation/instrument context | Reward expiry field | No plan reward expiry field |
| Online RR restriction | CLI prevents earning/using RR online | Not fully audited here | No equivalent channel setting in DealTerms |
| Separate RR coupons used | CLI asks for each | Further audit required | One combined amount |
| Payments | Multiple records | Receipt inputs | Fixed amount by payment category |
| Printed total reconciliation | Actual receipt workflow | Preview/save receipt workflow | Not equivalent to receipt completion |
| Repeatability / limit | Not established equivalent | Not established equivalent | Explicit fields |
| Edit advertised deal | Different correction models | Different correction models | Repository method exists; web route/form absent |
| Reward count explanation | Review prompts / reports | Qualification response | Per-deal progress/status and strict-code exclusion messages; no general per-item result model |
| Rewards in checkout summary | Reward review is separate | Receipt views | Returned and displayed as potential rewards; completeness/review shown |

## Confirmed gaps

Shopping plans cannot fully represent reward program, spend basis or purchase channel. DealForm only POSTs creation; DealRepository.update exists but lacks an exposed deal-edit workflow. Eligibility mode is implicit in whether a SKU list is populated. The frontend Deal.pricing contract only describes a subset of backend fields. Checkout displays potential rewards and review status but still lacks the full financial breakdown and live recalculation preview. No reviewed connection establishes that saving a plan checkout creates a completed receipt with credited rewards.

## Proposed changes, not delivered here

Explicit UPC policy defaulting to linked products; revision-aware promotion editing; structured eligibility explanations; typed reward program/spend basis/channel; complete checkout review; deliberately designed plan-to-receipt conversion that avoids duplicate reward crediting. Verify serialized-model compatibility before introducing fields. PostgresDealRepository.update currently leaves manual_fingerprint unchanged; decide duplicate-detection semantics for corrections.

## Not yet established

Full parity for provider balances, instrument matching, third-party claims/payouts, gift-card lifecycle, inventory, every import path, soft deletion/restoration and distributed scenarios requires focused behavior-by-behavior audit. Their files are indexed here, but index coverage is not proof of parity. Do not implement gaps solely from naming similarities.

## Browser form readability — October 23, 2026

Receipt creation and correction share 48px controls, 16px value text and expanding
full-row product/discount descriptions. Narrow dialogs stack fields based on their
own content width; dropdown values use concise captions without changing stored
enums. CLI input, calculations and repository workflows are unchanged. See
FRONTEND-DESIGN-SYSTEM.md and SDD 5.2 for visual rules and component responsibilities.


October 23 sizing increase: controls enlarged from 48px to 60px and value fonts
from 16px to 18px. Modal/filter labels are 15px; horizontal control padding is
16px. Dialog maximum width is 1040px. Receipt editor/product fields are full-width
single-column even on desktop. Long descriptions expand. Current specification:
docs/reference/FRONTEND-DESIGN-SYSTEM.md; executable values:
frontend/src/index.css. SRS/SDD internal revision 1.5 supersedes 1.4 sizing.

## Revision 1.6 — CLI and browser parity

Browser now shares confirmed opening/carry-forward calculations with the CLI and exposes earned/used RR source relationships through expandable View Reward details. Beginning-of-day openings can be appended in the browser with account/date/source/valuation/confirmation. Other account commands (adjustments, provider redemption/payout and reconciliation approvals) remain CLI/repository workflows. Browser printed receipt observations are independently optional; unlike an all-or-nothing CLI footer prompt, any printed amount may be skipped. Store and manufacturer savings stay separate. Both preserve original units/valuations and do not treat printed observations as a second earning or payment.


## Revision 1.7 — receipt RR correction and matching

Browser now exposes CLI-style record-RR-use and match-unused-source operations. Save receipt RR entries first; then choose a confirmed printed unused coupon matching each face value. Native instrument reservations/history are retained. The browser can explicitly move a misclassified discount/payment into RR used or reuse an existing same-value RR entry when removing a duplicate. Matching is not another earning/deduction. Other tender roles and named-account deductions can now be corrected without CLI.


## Revision 1.8 — RR without an earning receipt

Browser now supports the pre-existing standalone physical RR model without requiring an earning receipt. For an unmatched recorded use, Add RR without an earning receipt records/matches that coupon; dates may be unknown. Unlike an earned-receipt entry, this does not assert when/on which purchase the RR was earned or add a receipt earning.


## Revision 1.9 — Rewards points-account display

Browser Rewards uses the five-account points display policy. CLI and receipt-specific point/valuation reporting remain unchanged. On the Rewards pages, only MyPoints, Fetch, P&G Brand (PGBRAND), Swagbucks (SWAGBUCK) and Merryfield show points information. Other account detail pages hide native-point balances, activity columns, point-valuation text, converted point quantities and the Original Points opening field, even if older records contain native units. Non-points opening forms use Balance Basis and require a dollar amount; new requests send points:null. The mixed account list shows a points column only when an allowed account is listed, with a dash for other accounts. This is Rewards presentation policy; receipt/CLI behavior, stored units and Java financial calculations remain intact.


## Revision 1.10 — independent Rewards inventory

The browser can now register standalone RR inventory independently, like CLI issuedRewards storage, rather than requiring an existing spending receipt for the Add action. The receipt-level add-and-match workflow remains. Save on Rewards first, then select/match from the spending receipt after its RR-use deduction is recorded. The Rewards overview and Register Rewards detail page expose Add reward without a receipt. The owner can save an unused standalone Register Reward by description and positive amount, with optional earned/expiration dates and no receipt of any kind required. A coupon inventory table shows UNUSED, MATCHED or REDEEMED status. Saving inventory does not add a receipt earning, account credit, payment, deduction or immediate match. Later, an equal-value recorded receipt use can select that coupon. Standalone coupons with unknown earning date/time are offered with historyUnknown=true and require explicit confirmation they were available for that purchase; known future/expired/consumed sources remain excluded. Matched sources remain reserved.


## Revision 1.11 — visible recorded receipt calculations

Browser receipt views now expose recorded arithmetic even for legacy receipts lacking the newer basis/completeness fields. Both candidate bases are visible rather than silently inferred. Strict reconciliation, CLI calculations and financial measure definitions remain unchanged. Users can inspect numbers before deciding whether/how to confirm receipt metadata.


## Revision 1.12 — simplified receipt totals display

Browser totals are simplified by removing the calculated-total/check table and printed Store Coupon Savings row. Entry/correction workflows, CLI output, backend calculations and stored observations remain unchanged.


## Revision 1.13 — reuse existing files and align CLI rebate display

The visible receipt measure is Total Paid after rebates, sourced directly from savings.paidAfterRebates — the same SavingsReport value used by the CLI. Formula: recorded cash/card minus newly earned reward/rebate dollars, including saved USD point valuations. The frontend no longer uses the stricter benefitAdjustedCashSpending field for this spreadsheet measure; that compatibility field and its explicit-completeness semantics remain unchanged. Unknown historical payment-entry metadata alone does not hide the CLI value. Truly missing tender or dollar valuations still leave the CLI measure unknown, now explained as Cannot calculate yet. Actual OOP, reward use, Total Saved and stored facts are unchanged. Browser receipt output now follows the existing CLI/spreadsheet value even for legacy metadata. Genuine unknown tender/valuations still block that measure. Receipt/reward/edit/matching workflows remain available after consolidation.
