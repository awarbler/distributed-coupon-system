// JSON numeric lexemes remain strings. Financial values never pass through JS Number.
export type Decimal = string | null
export interface Savings { receipts: string; regularWithTax: Decimal; due: Decimal; cash: Decimal; card: Decimal; newlyEarned: Decimal; paidAfterRebates: Decimal; totalSaved: Decimal; percentSaved: Decimal; issues: string[] }
export interface Totals { lineTotals?:Record<string,string>; productGroups?: {identity:string;description:string;quantity:string;lineItemIds:string[]}[]; qualification?: {earningId:string;review:{status:string;qualifyingQuantity:string;qualifyingSpend:Decimal;reason:string}}[]; regularMerchandise: Decimal; saleMerchandise: Decimal; coupons: Decimal; rrUsed: Decimal; wcUsed: Decimal; tax: Decimal; calculatedDue: Decimal; reportedDue: Decimal; difference: Decimal; savings: Savings }
export interface Metadata { retailer: string; receiptNumber: string | null; purchasedAt: string | null; storeReference: string | null; purchaseChannel: string }
export interface ReceiptRow { transactionId: string; revision: string; status: string; metadata: Metadata | null; date: string | null; time: string | null; totals: Totals }
export interface Purchase { regularUnitPrice?: Decimal; saleUnitPrice?: Decimal; priceSource?: string | null; plannedPurchaseId: string; planId: string; productName: string; productCode: string | null; quantity: string; targetUnitPrice: Decimal; advertisedDealId: string | null; clippedCouponIds: string[]; revision: string }
export interface Plan { planId: string; name: string; retailer: string | null; shoppingDate: string | null; plannedPurchaseIds: string[]; revision: string }
export interface CheckoutSummary {
  tax: Decimal | null
  amountDue: Decimal | null
  remainingPayment: Decimal | null
  expectedRewards: Decimal | null
issues: string[]}
export interface PlanCheckout {
  planId: string
  revision: string
  promoCode: string | null
  promoDiscount: Decimal
  taxRate: Decimal
  taxableBase: Decimal
  actualTax: Decimal
  walgreensCashUsed: Decimal
  registerRewardsCoupon: Decimal
  giftCardUsed: Decimal
  cashPayment: Decimal
  cardPayment: Decimal
  otherPayment: Decimal
  notes: string | null
  summary: CheckoutSummary
  estimateComplete: boolean
}
export interface PlanRow { pricing?: { regularTotal: Decimal; saleTotal: Decimal; saleSavings: Decimal; couponSavings: Decimal; expectedRewards: Decimal; registerCost: Decimal; finalCost: Decimal; issues: string[] }; plan: Plan; items: Purchase[]; estimatedMerchandise: Decimal; estimateComplete: boolean }
export interface Coupon { terms?: {source: string; discount: string} | null; couponId: string; provider: string; externalId: string | null; status: string; clippedOn: string | null; redeemedOn: string | null; redeemedTransactionId: string | null; revision: string; description: string; discountAmount: Decimal; couponType: string; expirationDate: string | null }
export interface Deal { pricing?: { type: string; promotionalPrice: Decimal } | null; dealId: string; title: string; interpretedTerms: string; retailer: string | null; validFrom: string | null; validThrough: string | null; sourceArtifactIds: string[]; revision: string }
export interface Benefit { kind: string; amount: Decimal; currency: string | null; program: string | null }
export interface Rebate { claimId: string; provider: string; externalClaimRef: string | null; transactionId: string | null; plannedPurchaseId: string | null; status: string; providerStatus: string | null; expectedBenefits: Benefit[]; confirmedBenefits: Benefit[]; submittedOn: string | null; fulfilledOn: string | null }
export interface Inventory { inventoryId: string; productId: string; quantityOnHand: string; revision: string }
export interface Balance { provider: string; balance: { monthUsd: Decimal; startUsd: Decimal; endUsd: Decimal; startPoints: Decimal; endPoints: Decimal } }
export interface ReportedBalance { transactionId: string; receiptNumber: string | null; date: string | null; status: string; values: { startingBalance: Decimal; used: Decimal; earned: Decimal; endingBalance: Decimal } }
export interface Movement { date: string | null; provider: string; points: Decimal; usd: Decimal; source: string; time: string | null }
export interface Promotion { id: string; name: string; threshold: Decimal; giftCardValue: Decimal; terms: string; status: string; contributions: Record<string, { receiptId: string; reviewedRevision: string; reviewedAmount: Decimal; note: string }> }
export interface RewardOffer {id:string;title:string|null;kind:string;provider:string;amount:string;requirement:{type:string;amount:string;spendBasis:string}}
export interface Workspace { receiptEarnings?: {receiptId:string;receiptNumber:string|null;receiptStatus:string;earning:Earning}[]; rewardOffers?: RewardOffer[]; asOf: string; canWrite: boolean; receipts: ReceiptRow[]; plans: PlanRow[]; coupons: Coupon[]; deals: Deal[]; rebates: Rebate[]; inventory: Inventory[]; balances: Balance[]; reportedBalances: ReportedBalance[]; movements: Movement[]; promotions: Promotion[]; summaries: Record<'all'|'day'|'week'|'month',Savings>; accountHistory: { openings: unknown[]; adjustments: unknown[]; redemptionHistory: unknown[] }; rrMatches: unknown[]; rewardRemovals: unknown[] }
export interface Earning { details?: {payoutDestination:string|null;submissionReference:string|null;qualifyingQuantities:Record<string,string>}|null; recordedLifecycle?: string | null; earningId: string; provider: string; kind: string; offerReference: string | null; scope: string; lineItemIds: string[]; expected: Benefit | null; confirmed: Benefit | null; status: string; valuation: { amount: Decimal; currency: string } | null }
export interface Receipt { transactionId: string; revision: string; status: string; metadata: Metadata | null; evidenceIds: string[]; contents: { purchaseItems: { lineItemId: string; productName: string; productCode: string | null; regularUnitPrice: Decimal; saleUnitPrice: Decimal; quantity: string }[]; appliedCoupons: { applicationId: string; printedLabel: string; appliedAmount: Decimal; lineItemIds: string[]; wholeReceipt: boolean }[]; payments: { paymentId: string; paymentType: string; amount: Decimal; notes: string | null }[]; observations: { earnings: Earning[] }; receiptTotals: { taxAmount: Decimal; finalTotal: Decimal } } }
export interface History { historyId: string; request: { requestedAt: string }; result: { revision: string; action: string; committedAt: string }; changeCtx: { reason: string; approvedBy: string } | null; createCtx: { requestedBy: string } | null }
