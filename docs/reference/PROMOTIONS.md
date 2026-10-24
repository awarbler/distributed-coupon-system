# Promotion setup and eligibility rules

## Revision history

| Version | Date | Changes | Verification |
|---|---|---|---|
| 1.0 | 2026-10-21 | Original reference snapshot | Static inspection |
| 1.1 | 2026-10-22 | Planning reward progress, unknown-value semantics and current UPC policy | Source updated; final checks not run |
| 1.2 | 2026-10-22 | Receipt evidence/reconciliation, distinct purchase/scan and partial benefit values | See VERIFICATION.md |

## Revision 1.2 — receipt evidence and reconciliation

Receipt reward requirements distinguish Purchase (purchase-based), ReceiptScan (submission/scan), legacy Receipt, Other, BUY and SPEND. Nonnumeric categories return CANNOT_VERIFY with evidence guidance; they do not fabricate threshold success, submission or credit. Historical terms are copy suggestions with source receipt/date/form; equivalent suggestions may collapse without merging records. Copies reset Expected, product associations, submission, confirmation and valuation. Advertised-deal UPC matching remains opt-in and separate from coupon rules.


## Revision 1.1 — current behavior and status

Current advertised-deal UPC policy is opt-in: strictSkuMatching defaults false; code lists alone do not restrict linked products. When enabled, exact matches are required and an empty list is rejected. Coupon UPC policy remains separate.

Planning implementation revision 1.1: DealProgress distinguishes NOT_APPLICABLE, KNOWN and UNKNOWN rewards and independent threshold/review status. Quantity reward values do not require prices; spend rewards with missing prices are unknown. Known threshold-met rewards remain in potential totals with store/date notes/conflicts. Incomplete offers stay visible as opportunities outside that total. Dates are inclusive. Automatic sale/coupon eligibility remains separate. Final sanity checks have not been run.



Original revision 1.0 inspection: October 21, 2026 (America/Chicago). Base Git commit: `fa3b8debc070cb5de990cbe9eb420fe1e56b7c06`. This reference describes the working tree, including pre-existing uncommitted checkout changes; it does not prove what is deployed online. No application tests were run for this documentation-only task.

## Current shopping-plan behavior — reviewed

ShoppingEstimate.calculate(items, deals, coupons, date, retailer) is the estimator. For each deal it selects linked items by advertisedDealId, checks date/store, filters qualifyingSkus, sums eligible quantity/spend, then applies thresholds, repeatability and limits.

| Type | Example | Structured setup |
|---|---|---|
| SALE | $5 each | promotionalPrice 5; minimumQuantity normally 1 |
| MULTIBUY | 2 for $10 | minimumQuantity 2; promotionalPrice 10 for full bundle |
| BUY_GET | Buy 2 get 1 free | minimumQuantity 2; freeQuantity 1; promotionalPrice 0 |
| QUANTITY_REWARD | Buy 2 earn $5 | minimumQuantity 2; rewardAmount 5 |
| SPEND_REWARD | Spend $50 earn $10 | minimumSpend 50; rewardAmount 10; minimumQuantity still structurally ≥1 |

Plan reward types still do not specify whether the benefit is RR or Walgreens Cash. A title containing RR does not create a typed RR reward.

Eligibility is currently `!strictSkuMatching || (productCode != null && qualifyingSkus.contains(productCode))`. A partial list restricts linked products only when strictSkuMatching is explicitly enabled. Two different eligible products can combine quantities. Empty SKU lists allow explicit linking without codes. Brand/size/loyalty/exclusion text is for human review.

Quantity groups use integer division quantity/minimumQuantity. Spend groups use spend/minimumSpend rounded down. Non-repeatable deals cap groups at one; limit further caps them. Expected rewards do not create available balances. Spend rewards require eligible prices; fixed quantity rewards do not require prices to determine the reward amount. Manual priceSource does not exclude quantity-reward items.

Spend rewards use recorded merchandise before coupon savings; there is no plan spend-basis field. For automatic SALE/MULTIBUY/BUY_GET allocation, eligible items need DEAL priceSource. Buy/get requires cheapestItemsDiscounted for automatic allocation. Price deals and reward deals are separate types; one item has only one advertisedDealId.

## Receipt reward behavior — reviewed

ReceiptEntryApp asks for reward program, offer, BUY/SPEND requirement, selected receipt products and reward amount/status. Its examined spend question offers before-coupon or unknown basis. Online RewardFields also exposes after-coupon basis. PromotionQualification.evaluate performs receipt-line threshold arithmetic; QualifyingSpend.calculate handles sale reductions and rejects ambiguous allocations. RewardSpendCheck.evaluate specifically checks before-coupon spend and must not be treated as identical to PromotionQualification.

The CLI requires human choice of qualifying products; it is not a retailer catalog validator. No selected receipt lines means qualification cannot be automatically established. Crossing selected/unselected lines with deductions can require review. Online receipt EarningInput enforces program/form consistency. ReceiptEarningRules detects identical new observations; a second legitimate offer needs distinct offer/reference context.

## Confirmed Dove case from supplied response

Both items referenced the same deal, quantity 1 each. QUANTITY_REWARD required 2 and rewarded $5. The saved qualifyingSkus list held two codes while both productCode values were initially null, excluding both. After matching codes were entered, the supplied UI showed $7 total rewards ($5 Dove + $2 Cottonelle). This establishes the failure mechanism in that supplied snapshot, not a fresh live database inspection.

## Opt-in policy — implemented in source; persisted compatibility pending

Explicitly linked items count without UPC matching unless strictSkuMatching is true. The API and creation checkbox carry this opt-in flag; strict mode requires a nonempty code list. Existing saved-record compatibility still needs verification.

## Setup review checklist

Check actual offer evidence; type; store/date; products linked to the correct deal; quantity/spend; code-list policy; prices; repeatability and maximum groups; membership/exclusions; purchase channel; benefit program and expiration where relevant. The present shopping-plan model cannot express all receipt reward rules. Do not assume a promotion title fills missing structured fields.
