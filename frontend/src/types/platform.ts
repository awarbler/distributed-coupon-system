// JSON numeric lexemes remain strings. Financial values never pass through JS Number.
export type Decimal = string | null
export type PrintedTotalBasis = 'BEFORE_PAYMENT_FUNDING' | 'AFTER_REWARD_REDEMPTIONS' | 'UNKNOWN'
export type PaymentEntryStatus = 'COMPLETE' | 'PARTIAL' | 'UNKNOWN'
export interface PaymentReview {
  printedTotal: Decimal
  calculatedPurchaseTotal: Decimal
  purchaseDifference: Decimal
  knownFundingSubtotal: Decimal
  recordedCashCardPaid: Decimal
  giftCardFunding: Decimal
  otherFunding: Decimal
  difference: Decimal
  status: 'BALANCED' | 'DIFFERENCE' | 'INCOMPLETE' | 'CANNOT_VERIFY'
  printedTotalBasis: PrintedTotalBasis
  paymentEntryStatus: PaymentEntryStatus
  unresolvedPaymentIds: string[]
  reason: string
  acknowledgementRequired: boolean
}
export interface Savings { recordedCashCardComplete?: boolean; recordedCashCardPaid?: Decimal; knownConfirmedBenefitValue?: Decimal; confirmedBenefitValue?: Decimal; confirmedBenefitsComplete?: boolean; unvaluedProviders?: string[]; receipts: string; regularWithTax: Decimal; due: Decimal; cash: Decimal; card: Decimal; newlyEarned: Decimal; paidAfterRebates: Decimal; totalSaved: Decimal; percentSaved: Decimal; issues: string[] }
export interface CalculationScenario {basis: PrintedTotalBasis;calculatedTotal:Decimal;purchaseDifference:Decimal;knownFundingSubtotal:Decimal;paymentDifference:Decimal;verified:boolean}
export interface Totals { calculationChecks?: {beforeFunding:CalculationScenario;afterRewards:CalculationScenario}; discountBreakdown?: { salePromotions: Decimal; manufacturerCoupons: Decimal; storeDiscounts: Decimal; unclassified: Decimal }; paymentReview?: PaymentReview; benefitAdjustedCashSpending?: Decimal; benefitObservations?: { earningId: string; provider: string; status: string; expected: Benefit | null; confirmed: Benefit | null; valuation: { amount: Decimal; currency: string; basis: string } | null }[]; lineTotals?:Record<string,string>; productGroups?: {identity:string;description:string;quantity:string;lineItemIds:string[]}[]; qualification?: {earningId:string;review:{status:string;qualifyingQuantity:string;qualifyingSpend:Decimal;reason:string}}[]; regularMerchandise: Decimal; saleMerchandise: Decimal; coupons: Decimal; rrUsed: Decimal; wcUsed: Decimal; tax: Decimal; calculatedDue: Decimal; reportedDue: Decimal; difference: Decimal; savings: Savings }
export interface Metadata { retailer: string; receiptNumber: string | null; purchasedAt: string | null; storeReference: string | null; purchaseChannel: string; printedTotalBasis?: PrintedTotalBasis; paymentEntryStatus?: PaymentEntryStatus }
export interface ReceiptRow { sortTime?: string | null; transactionId: string; revision: string; status: string; metadata: Metadata | null; date: string | null; time: string | null; totals: Totals }
export interface Purchase { regularUnitPrice?: Decimal; saleUnitPrice?: Decimal; priceSource?: string | null; plannedPurchaseId: string; planId: string; productName: string; productCode: string | null; quantity: string; targetUnitPrice: Decimal; advertisedDealId: string | null; clippedCouponIds: string[]; revision: string }
export interface Plan { planId: string; name: string; retailer: string | null; shoppingDate: string | null; plannedPurchaseIds: string[]; revision: string }
export type ReviewStatus = 'NOT_RECORDED' | 'MATCHES' | 'NEEDS_REVIEW'
export interface DealProgress {
  dealId: string
  qualifyingQuantity: string
  requiredQuantity: string | null
  quantityMissing: string | null
  qualifyingSpend: Decimal
  requiredSpend: Decimal
  spendMissing: Decimal
  thresholdStatus: 'NOT_APPLICABLE' | 'MET' | 'NOT_MET' | 'UNKNOWN'
  storeStatus: ReviewStatus
  dateStatus: ReviewStatus
  rewardStatus: 'NOT_APPLICABLE' | 'KNOWN' | 'UNKNOWN'
  potentialReward: Decimal
  rewardIfCompleted: Decimal
  messages: string[]
}
export interface RewardProjection {
  dealProgress?: DealProgress[]
  knownPotentialRewards?: Decimal
  rewardsComplete?: boolean
  reviewRequired?: boolean
}
export interface CheckoutSummary extends RewardProjection {
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
export interface PlanRow { pricing?: RewardProjection & { regularTotal: Decimal; saleTotal: Decimal; saleSavings: Decimal; couponSavings: Decimal; expectedRewards: Decimal; registerCost: Decimal; finalCost: Decimal; issues: string[] }; plan: Plan; items: Purchase[]; estimatedMerchandise: Decimal; estimateComplete: boolean }
export interface Coupon { requiredQuantity?:string|null; terms?: {source: string; discount: string} | null; couponId: string; provider: string; externalId: string | null; status: string; clippedOn: string | null; redeemedOn: string | null; redeemedTransactionId: string | null; revision: string; description: string; discountAmount: Decimal; couponType: string; expirationDate: string | null }
export interface Deal { pricing?: { type: string; promotionalPrice: Decimal } | null; dealId: string; title: string; interpretedTerms: string; retailer: string | null; validFrom: string | null; validThrough: string | null; sourceArtifactIds: string[]; revision: string }
export interface Benefit { kind: string; amount: Decimal; currency: string | null; program: string | null }
export interface Rebate { claimId: string; provider: string; externalClaimRef: string | null; transactionId: string | null; plannedPurchaseId: string | null; status: string; providerStatus: string | null; expectedBenefits: Benefit[]; confirmedBenefits: Benefit[]; submittedOn: string | null; fulfilledOn: string | null }
export interface Inventory { inventoryId: string; productId: string; quantityOnHand: string; revision: string }
export interface Balance { provider: string; balance: { monthUsd: Decimal; startUsd: Decimal; endUsd: Decimal; startPoints: Decimal; endPoints: Decimal } }
export interface ReportedBalance { transactionId: string; receiptNumber: string | null; date: string | null; status: string; values: { startingBalance: Decimal; used: Decimal; earned: Decimal; endingBalance: Decimal } }
export interface Movement { date: string | null; provider: string; points: Decimal; usd: Decimal; source: string; time: string | null }
export interface Promotion { id: string; name: string; threshold: Decimal; giftCardValue: Decimal; terms: string; status: string; contributions: Record<string, { receiptId: string; reviewedRevision: string; reviewedAmount: Decimal; note: string }> }
export interface RewardOffer { id: string; title: string | null; kind: string; provider: string; amount: string; form?: string; sourceReceiptId?: string; sourceReceiptNumber?: string | null; sourceDate?: string | null; requirement: { type: string | null; amount: Decimal; category?: string | null; spendBasis: string } }
export interface ProductLookup { sku: string; description: string; regularPrice: Decimal; purchaseDate: string | null; receiptNumber: string | null }
export interface RewardsOverview { asOf:string; available:Decimal; expected:Decimal; total:Decimal; knownAvailable:Decimal; knownExpected:Decimal; expectedCount:string; missingBalances:string[]; missingValuations:string[] }
export interface Workspace { rewardsOverview?: RewardsOverview; productLookup?: ProductLookup[]; accountActivity?: AccountActivity[]; receiptPeriods?: Record<string, Savings>; receiptEarnings?: {receiptId:string;receiptNumber:string|null;receiptStatus:string;earning:Earning}[]; rewardOffers?: RewardOffer[]; asOf: string; canWrite: boolean; receipts: ReceiptRow[]; plans: PlanRow[]; coupons: Coupon[]; deals: Deal[]; rebates: Rebate[]; inventory: Inventory[]; balances: Balance[]; reportedBalances: ReportedBalance[]; movements: Movement[]; promotions: Promotion[]; summaries: Record<'all'|'day'|'week'|'month',Savings>; accountHistory: { openings: unknown[]; adjustments: unknown[]; redemptionHistory: unknown[] }; rrMatches: unknown[]; rewardRemovals: unknown[] }
export interface Earning { expirationDate?:string|null; details?: {payoutDestination:string|null;submissionReference:string|null;qualifyingQuantities:Record<string,string>}|null; recordedLifecycle?: string | null; earningId: string; provider: string; kind: string; offerReference: string | null; scope: string; lineItemIds: string[]; expected: Benefit | null; confirmed: Benefit | null; status: string; valuation: { amount: Decimal; currency: string } | null }
export interface Receipt { paymentRoles?: Record<string,string>; rrUses?: RewardLink[]; preservePriceLineIds?: string[]; editDiscounts?: { applicationId: string; printedLabel: string; requiredQuantity?: string | null; appliedAmount: Decimal; kind: string; lineItemIds: string[]; wholeReceipt: boolean; qualifyingQuantities: Record<string, string> }[]; transactionId: string; revision: string; status: string; metadata: Metadata | null; evidenceIds: string[]; contents: { purchaseItems: { lineItemId: string; productName: string; productCode: string | null; regularUnitPrice: Decimal; saleUnitPrice: Decimal; quantity: string }[]; appliedCoupons: { applicationId: string; printedLabel: string; requiredQuantity?: string | null; appliedAmount: Decimal; lineItemIds: string[]; wholeReceipt: boolean }[]; payments: { paymentId: string; paymentType: string; amount: Decimal; notes: string | null; accountName?: string | null }[]; observations: { unidentifiedRR?: {eventId:string;amount:Decimal;recordedDescription:string;notes?:string|null}[]; footerStatus?: string; savings?: { categoryId: string; printedLabel: string; reportedAmount: Decimal }[]; receiptSavingValuePercent?: Decimal; walgreensCash?: { startingBalance: Decimal; earned: Decimal; used: Decimal; endingBalance: Decimal } | null; purchaseDate?: string | null; printedTime?: string | null; earnings: Earning[] }; receiptTotals: { taxAmount: Decimal; finalTotal: Decimal } } }
export interface History { historyId: string; request: { requestedAt: string }; result: { revision: string; action: string; committedAt: string }; changeCtx: { reason: string; approvedBy: string } | null; createCtx: { requestedBy: string } | null }
export interface RewardLink { products?: string[]; earningId: string; receiptId: string | null; receiptNumber: string | null; date: string | null; time: string | null; promotion: string; amount: Decimal; spentReceiptId: string; spentReceiptNumber: string | null; usedAmount: Decimal; status: string }
export interface AccountActivity { movement: Movement & {receiptId: string | null; eventId: string | null}; receiptNumber: string | null; description: string; rewards: RewardLink[]; beforeUsd: Decimal; afterUsd: Decimal; beforePoints: Decimal; afterPoints: Decimal; orderKnown: boolean; valuationBasis?: string | null }
export interface AccountReport { conversions?: {redemptionId:string;requestedDate:string;originalUnitsUsed:Decimal;sourceUsdValue:Decimal;destinationAccount:string;destinationUsdValue:Decimal;status:string;completedDate:string|null}[]; provider: string; from: string; to: string; beginning: Balance['balance'] & {opening: {date: string; sourceNote: string; valuationBasis: string} | null}; ending: Balance['balance']; earned: Decimal; spent: Decimal; earnedPoints: Decimal; spentPoints: Decimal; usdActivityComplete: boolean; activity: AccountActivity[] }

export interface RRMatchUse { eventId:string;amount:Decimal;description:string;matched:boolean;needsReview?:boolean;options:{sourceId:string;receiptEarning:boolean;historyUnknown?:boolean;amount:Decimal;promotion:string;sourceReceiptId:string|null;sourceReceiptNumber:string|null;earnedDate:string|null;expires:string|null}[] }
export interface StandaloneRR {rewardId:string;description:string;amount:Decimal;earnedDate:string|null;expirationDate:string|null;status:'UNUSED'|'MATCHED'|'REDEEMED'}

export interface ReceiptReport { unconfirmedTotal?: Decimal; metric: string; summary: Savings; matchedTotal: Decimal; matchedCash: Decimal; matchedCard: Decimal; rows: {revision?:string;earning?:Earning;group?:string;status?:string;nativeAmount?:Decimal;unit?:string;receiptId:string;receiptNumber:string|null;store:string|null;date:string|null;time:string|null;description:string;sku?:string|null;quantity?:string;unitPrice?:Decimal;amount:Decimal}[] }
