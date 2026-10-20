// Synthetic automated-test fixtures; never loaded by the running app.
import type { Savings, Workspace } from '../types/platform'
export function savings(): Savings { return { receipts: '0', regularWithTax: '0.00', due: '0.00', cash: '0.00', card: '0.00', newlyEarned: '0.00', paidAfterRebates: '0.00', totalSaved: '0.00', percentSaved: null, issues: [] } }
export function workspace(): Workspace { return { asOf: '2026-10-19', canWrite: true, receipts: [], plans: [], coupons: [], deals: [], rebates: [], inventory: [], balances: [], reportedBalances: [], movements: [], promotions: [], summaries: { all: savings(), day: savings(), week: savings(), month: savings() }, accountHistory: { openings: [], adjustments: [], redemptionHistory: [] }, rrMatches: [], rewardRemovals: [] } }
