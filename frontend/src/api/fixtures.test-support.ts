// Synthetic automated-test fixtures; never loaded by the running app.
import type { Savings, Workspace, PlanRow, DealProgress, ReceiptRow } from '../types/platform'
export function savings(): Savings { return { receipts: '0', regularWithTax: '0.00', due: '0.00', cash: '0.00', card: '0.00', newlyEarned: '0.00', paidAfterRebates: '0.00', totalSaved: '0.00', percentSaved: null, issues: [] } }
export function workspace(): Workspace { return { asOf: '2026-10-19', canWrite: true, receipts: [], plans: [], coupons: [], deals: [], rebates: [], inventory: [], balances: [], reportedBalances: [], movements: [], promotions: [], summaries: { all: savings(), day: savings(), week: savings(), month: savings() }, accountHistory: { openings: [], adjustments: [], redemptionHistory: [] }, rrMatches: [], rewardRemovals: [] } }

export function planningRow(overrides: Partial<DealProgress> = {}): PlanRow {
  const progress: DealProgress = {
    dealId: 'deal-progress', qualifyingQuantity: '1', requiredQuantity: '2', quantityMissing: '1',
    qualifyingSpend: '5', requiredSpend: null, spendMissing: null, thresholdStatus: 'NOT_MET',
    storeStatus: 'NEEDS_REVIEW', dateStatus: 'MATCHES', rewardStatus: 'KNOWN', potentialReward: '0',
    rewardIfCompleted: '5', messages: ['Is this the correct store?'], ...overrides,
  }
  return {
    plan: { planId: 'plan-progress', name: 'Progress trip', retailer: 'Target', shoppingDate: '2026-10-21', plannedPurchaseIds: [], revision: '1' },
    items: [{ plannedPurchaseId: 'item-progress', planId: 'plan-progress', productName: 'Dove', productCode: null, quantity: '1', targetUnitPrice: '5', saleUnitPrice: '5', regularUnitPrice: '6', advertisedDealId: 'deal-progress', clippedCouponIds: [], revision: '1' }],
    pricing: { regularTotal: '6', saleTotal: '5', saleSavings: '1', couponSavings: '0', registerCost: '5',
      expectedRewards: progress.potentialReward, finalCost: progress.rewardStatus === 'UNKNOWN' ? null : '5',
      knownPotentialRewards: progress.potentialReward ?? '0', rewardsComplete: progress.rewardStatus !== 'UNKNOWN',
      reviewRequired: true, dealProgress: [progress], issues: [] },
    estimatedMerchandise: '5', estimateComplete: true,
  }
}

export function receiptRow(id: string, day = '2026-10-19', time = '940 am'): ReceiptRow {
  return { transactionId: id, revision: '1', status: 'ACTIVE', date: day, time,
    metadata: { retailer: 'Walgreens', receiptNumber: `0001-${id}`, purchasedAt: null, storeReference: null, purchaseChannel: 'IN_STORE' },
    totals: { regularMerchandise: '44.96', saleMerchandise: '31.98', coupons: '8', rrUsed: '0', wcUsed: '20', tax: '1.98', calculatedDue: '5.96', reportedDue: '25.96', difference: '20', savings: { ...savings(), totalSaved: '49.72' } } }
}
