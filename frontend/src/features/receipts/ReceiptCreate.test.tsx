import { EarningLinksForm } from './EarningLinksForm'
import { RewardFields } from './RewardFields'
import { newReward } from './rewardStatus'
import { ReceiptEdit, ReceiptStatusAction, ReceiptRRMatch } from './ReceiptDetail'
import { WorkspacePages, RewardsAccounts, StandaloneRewardPanel, MemberRewardEntry } from '../../pages/WorkspacePages'
import { browseReceipts, matchesReceiptSearch, receiptTime, shoppingWeek } from '../../api/format'
import type { Receipt, AccountReport } from '../../types/platform'
import { render, screen, within, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiClient, ApiError } from '../../api/client'
import { workspace, receiptRow } from '../../api/fixtures.test-support'
import { ReceiptCreate, ReceiptFinancialReview, PrintedFooterView } from './ReceiptCreate'
const result = () => ({ reviewToken: 'server-reviewed', possibleDuplicates: [], evidence: [], totals: { regularMerchandise: '99.13', saleMerchandise: '2', coupons: '0', rrUsed: '0', wcUsed: '0', tax: '0', calculatedDue: '2', reportedDue: '2', difference: '0', savings: { ...workspace().summaries.all, newlyEarned: '0', paidAfterRebates: '2', totalSaved: '97.13' } } })
function setup(enabled = true) {
  const api = new ApiClient('test'); const mutate = vi.spyOn(api, 'mutate').mockImplementation(async path => path.endsWith('/preview') ? result() : {})
  const saved = vi.fn(); render(<ReceiptCreate api={api} enabled={enabled} onClose={vi.fn()} onReview={vi.fn()} onSaved={saved} />)
  return { user: userEvent.setup(), mutate, saved }
}
async function minimum(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('Store'), 'Test store'); await user.type(screen.getByLabelText('Purchase date'), '2026-10-19')
  await user.type(screen.getByLabelText('Product description 1'), 'Soap'); await user.type(screen.getByLabelText('Regular price 1'), '2.00000000000000000001')
  await user.type(screen.getByLabelText('Sales tax'), '0'); await user.type(screen.getByLabelText('Reported total due at register'), '2')
}
describe('web receipt creation', () => {
  it('reviews Java totals before creating, retaining exact decimal strings and stable identifiers', async () => {
    const { user, mutate, saved } = setup(); await minimum(user); await user.click(screen.getByRole('button', { name: 'Review calculated totals' }))
    expect(screen.getByRole('dialog', { name: 'Review new receipt' })).toBeInTheDocument(); expect(screen.getByText('$99.13')).toBeInTheDocument(); expect(saved).not.toHaveBeenCalled()
    const body = mutate.mock.calls[0][2] as { transactionId: string; items: { unitPrice: string; quantity: number; lineItemId: string }[] }
    expect(body.items[0].unitPrice).toBe('2.00000000000000000001'); expect(body.items[0].quantity).toBe(1)
    await user.click(screen.getByRole('button', { name: 'Save receipt' })); expect(mutate.mock.calls[1][2]).toEqual({ ...body, reviewToken: 'server-reviewed', acknowledgeDifference: false }); expect(saved).toHaveBeenCalledWith(body.transactionId)
  })
  it('keeps edits local until preview and requires discrepancy acknowledgment', async () => {
    const { user, mutate } = setup(); mutate.mockResolvedValueOnce({ ...result(), totals: { ...result().totals, difference: '0.01' } })
    await minimum(user); expect(mutate).not.toHaveBeenCalled(); await user.click(screen.getByRole('button', { name: 'Review calculated totals' })); expect(screen.getByRole('button', { name: 'Save receipt' })).toBeDisabled()
    await user.click(screen.getByRole('checkbox')); expect(screen.getByRole('button', { name: 'Save receipt' })).toBeEnabled()
    await user.click(screen.getByRole('button', { name: 'Back to edit' })); await user.type(screen.getByLabelText('Sales tax'), '1'); expect(screen.queryByRole('button', { name: 'Save receipt' })).not.toBeInTheDocument()
  })
  it('preserves the exact request for uncertain retries and prevents editing it', async () => {
    const { user, mutate } = setup(); await minimum(user); await user.click(screen.getByRole('button', { name: 'Review calculated totals' })); mutate.mockRejectedValueOnce(new ApiError(0, 'Uncertain result'))
    await user.click(screen.getByRole('button', { name: 'Save receipt' })); expect(screen.getByRole('button', { name: 'Back to edit' })).toBeDisabled(); await user.click(screen.getByRole('button', { name: 'Retry same save' })); expect(mutate.mock.calls[1]).toEqual(mutate.mock.calls[2])
  })
  it('blocks a possible duplicate instead of creating another historical record', async () => {
    const { user, mutate } = setup(); mutate.mockResolvedValueOnce({ ...result(), possibleDuplicates: [{ transactionId: 'existing' }] }); await minimum(user); await user.click(screen.getByRole('button', { name: 'Review calculated totals' })); expect(screen.getByRole('button', { name: 'Save receipt' })).toBeDisabled()
  })
  it('uses the simplified reward choices and sends explicit confirmation and original evidence bytes', async () => {
    const { user, mutate } = setup(); await minimum(user); await user.click(screen.getByRole('button', { name: 'Add rebate or reward' })); await user.selectOptions(screen.getByLabelText('Reward program 1'), 'REGISTER_REWARD')
    expect(within(screen.getByLabelText('Reward status 1')).queryByRole('option', { name: /Approved/ })).not.toBeInTheDocument()
    await user.type(screen.getByLabelText('Reward amount 1 (USD)'), '5'); await user.selectOptions(screen.getByLabelText('Reward status 1'), 'PAID')
    await user.upload(screen.getByLabelText('Receipt files'), new File([new Uint8Array([1, 2, 3])], 'test.png', { type: 'image/png' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Review calculated totals' })).toBeEnabled())
    await user.click(screen.getByRole('button', { name: 'Review calculated totals' })); const body = mutate.mock.calls[0][2] as { earnings: { status: string; confirmAmountAndUnit: boolean }[]; evidence: { contentBase64: string }[] }
    expect(body.earnings[0].status).toBe('PAID'); expect(body.earnings[0].confirmAmountAndUnit).toBe(true); expect(body.evidence[0].contentBase64).toBe('AQID')
  })
  it('does not permit submission without connectivity/write access', () => { setup(false); expect(screen.getByLabelText('Store')).toBeDisabled(); expect(screen.getByRole('button', { name: 'Review calculated totals' })).toBeDisabled() })
})

it('shows receipt facts and cash/card out of pocket separately from legacy comparisons', () => {
  const totals = { ...result().totals, paymentReview: {
    printedTotal: '25.96', calculatedPurchaseTotal: '25.96', purchaseDifference: '0', knownFundingSubtotal: '25.96',
    recordedCashCardPaid: '5.96', giftCardFunding: '0', otherFunding: '0', difference: '0',
    status: 'BALANCED' as const, printedTotalBasis: 'BEFORE_PAYMENT_FUNDING' as const, paymentEntryStatus: 'COMPLETE' as const,
    unresolvedPaymentIds: [], reason: 'Evidence reconciles.', acknowledgementRequired: false,
  }, wcUsed: '20', reportedDue: '25.96', difference: '20', benefitAdjustedCashSpending: null,
  savings: { ...result().totals.savings, knownConfirmedBenefitValue: '7', confirmedBenefitsComplete: false },
  benefitObservations: [{ earningId: 'fetch', provider: 'Fetch', status: 'CREDITED', expected: null,
    confirmed: { kind: 'POINTS', amount: '25', currency: null, program: 'Fetch' }, valuation: null }],
  }
  render(<ReceiptFinancialReview totals={totals} />)
  expect(screen.getByRole('heading', { name: 'Actual out of pocket' })).toBeInTheDocument()
  expect(screen.getByText('Actual cash/card paid').parentElement).toHaveTextContent('$5.96')
  expect(screen.getByText('Printed purchase total').parentElement).toHaveTextContent('$25.96')
  expect(screen.getByText(/Fetch — CREDITED/)).toHaveTextContent('25 points; USD valuation not recorded')
  expect(screen.getByText(/Combined benefit value is incomplete/)).toBeInTheDocument()
  expect(screen.getByText('Legacy reported/calculated difference').parentElement).toHaveTextContent('$20.00')
})
it('sends explicit total basis and payment completeness as reviewed facts', async () => {
  const { user, mutate } = setup(); await minimum(user)
  await user.selectOptions(screen.getByLabelText('Printed total represents'), 'BEFORE_PAYMENT_FUNDING')
  await user.selectOptions(screen.getByLabelText('Have you entered all payments shown on this receipt?'), 'COMPLETE')
  await user.click(screen.getByRole('button', { name: 'Review calculated totals' }))
  expect(mutate.mock.calls[0][2]).toMatchObject({ printedTotalBasis: 'BEFORE_PAYMENT_FUNDING', paymentEntryStatus: 'COMPLETE' })
})

it('shows total saved outside legacy calculations while preserving the cash/card amount', () => {
  const totals = result().totals
  totals.savings.totalSaved = '49.72'
  render(<ReceiptFinancialReview totals={totals} />)
  const saved = screen.getByText('Total saved — including confirmed benefits').parentElement!
  expect(saved).toHaveTextContent('$49.72')
  expect(saved.closest('details')).toBeNull()
})
it('keeps savings unknown when inputs are incomplete', () => {
  const totals = { ...result().totals, savings: { ...result().totals.savings, totalSaved: null } }
  render(<ReceiptFinancialReview totals={totals} />)
  expect(screen.getByText('Total saved — including confirmed benefits').parentElement).toHaveTextContent('Not recorded')
  expect(screen.getByText(/Savings inputs are incomplete/)).toBeInTheDocument()
})

it('uses regular price and attaches a sale promotion inside its product card', async () => {
  const { user, mutate } = setup()
  await minimum(user)
  expect(screen.queryByLabelText(/Unit price before listed discounts/)).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Add discount to product 1' }))
  await user.type(screen.getByLabelText('Product 1 discount 1 description'), 'Printed sale promotion')
  await user.type(screen.getByLabelText('Product 1 discount 1 amount'), '0.50')
  expect(screen.getByLabelText('Product 1 discount 1 amount').closest('.receipt-input-row')).toContainElement(screen.getByLabelText('Regular price 1'))
  await user.click(screen.getByRole('button', { name: 'Review calculated totals' }))
  const body = mutate.mock.calls[0][2] as { items: { lineItemId: string; regularUnitPrice: string; unitPrice: string }[]; discounts: { amount: string; kind: string; lineItemIds: string[]; wholeReceipt: boolean }[] }
  expect(body.items[0].unitPrice).toBe(body.items[0].regularUnitPrice)
  expect(body.discounts).toEqual([expect.objectContaining({ amount: '0.50', kind: 'SALE_PROMOTION', lineItemIds: [body.items[0].lineItemId], wholeReceipt: false })])
})

it('shows sale promotions separately from manufacturer coupons in the financial review', () => {
  const totals = { ...result().totals, coupons: '11.08', discountBreakdown: { salePromotions: '3.08', manufacturerCoupons: '8.00', storeDiscounts: '0.00', unclassified: '0.00' } }
  render(<ReceiptFinancialReview totals={totals} />)
  expect(screen.getByText('Sale/promotion reductions').parentElement).toHaveTextContent('$3.08')
  expect(screen.getByText('Manufacturer coupons').parentElement).toHaveTextContent('$8.00')
  expect(screen.getByText('Total listed discounts (sum above)').parentElement).toHaveTextContent('$11.08')
})

it('hides calculation checks and store coupon observations while retaining actual spending and saved receipt data',()=>{
 const totals=receiptRow('historical').totals
 totals.calculationChecks={beforeFunding:{basis:'BEFORE_PAYMENT_FUNDING',calculatedTotal:'25.96',purchaseDifference:'0',knownFundingSubtotal:'25.96',paymentDifference:'0',verified:false},afterRewards:{basis:'AFTER_REWARD_REDEMPTIONS',calculatedTotal:'5.96',purchaseDifference:'20',knownFundingSubtotal:'5.96',paymentDifference:'20',verified:false}}
 totals.paymentReview={printedTotal:'25.96',calculatedPurchaseTotal:null,purchaseDifference:null,knownFundingSubtotal:'25.96',recordedCashCardPaid:'5.96',giftCardFunding:'0',otherFunding:'0',difference:null,status:'CANNOT_VERIFY',printedTotalBasis:'UNKNOWN',paymentEntryStatus:'UNKNOWN',unresolvedPaymentIds:[],reason:'Printed total meaning unknown',acknowledgementRequired:true}
 const observations={earnings:[],savings:[{categoryId:'store',printedLabel:'Store coupon savings',reportedAmount:'5'}]}
 render(<><ReceiptFinancialReview totals={totals}/><PrintedFooterView observations={observations}/></>)
 expect(screen.queryByText('Calculated total on recorded basis')).not.toBeInTheDocument()
 expect(screen.queryByText('Calculated total before WC funding')).not.toBeInTheDocument()
 expect(screen.queryByText('Calculated remaining total after rewards')).not.toBeInTheDocument()
 expect(screen.queryByRole('heading',{name:'Receipt calculation checks'})).not.toBeInTheDocument()
 expect(screen.queryByRole('columnheader',{name:'Payment difference'})).not.toBeInTheDocument()
 expect(screen.queryByText('Store Coupon Savings')).not.toBeInTheDocument()
 expect(screen.getByText('Actual cash/card paid').parentElement).toHaveTextContent('$5.96')
 expect(observations.savings[0].reportedAmount).toBe('5')
 expect(totals.calculationChecks.beforeFunding.calculatedTotal).toBe('25.96')
})

function receipt(): Receipt {
  return { editDiscounts: [{ applicationId: 'old-price-reduction', printedLabel: 'Previously recorded price reduction', appliedAmount: '4.04', kind: 'SALE_PROMOTION', lineItemIds: ['original-line'], wholeReceipt: false, qualifyingQuantities: { 'original-line': '1' } }], transactionId: 'receipt-id', revision: '3', status: 'ACTIVE', evidenceIds: ['original-evidence'],
    metadata: { retailer: 'Walgreens', receiptNumber: '000123', purchasedAt: '2026-10-19T17:09', storeReference: 'original-store', purchaseChannel: 'IN_STORE' },
    contents: { purchaseItems: [{ lineItemId: 'original-line', productName: 'Soap', productCode: null, regularUnitPrice: '10', saleUnitPrice: '5.96', quantity: '1' }], appliedCoupons: [], payments: [{ paymentId: 'original-payment', paymentType: 'CARD', amount: '5.96', notes: null }], observations: { purchaseDate: '2026-10-19', printedTime: '17:09', earnings: [] }, receiptTotals: { taxAmount: '0', finalTotal: '5.96' } } }
}
it('reviews edits and retries the identical update with original line/payment IDs and revision', async () => {
  const api = new ApiClient('test'), saved = vi.fn(), user = userEvent.setup()
  const mutate = vi.spyOn(api, 'mutate').mockResolvedValueOnce({ reviewToken: 'reviewed', totals: receiptRow('test').totals }).mockRejectedValueOnce(new ApiError(0, 'Outcome uncertain')).mockResolvedValueOnce({})
  render(<ReceiptEdit receipt={receipt()} api={api} enabled onSaved={saved} onCancel={vi.fn()} />)
  await user.clear(screen.getByLabelText('Regular price 1')); await user.type(screen.getByLabelText('Regular price 1'), '9.00')
  await user.type(screen.getByLabelText('Reason for correction'), 'Correct price')
  await user.click(screen.getByRole('button', { name: 'Review updated totals' }))
  expect(mutate.mock.calls[0][2]).toMatchObject({ items: [{ lineItemId: 'original-line', regularUnitPrice: '9.00' }], payments: [{ paymentId: 'original-payment' }] })
  expect(saved).not.toHaveBeenCalled()
  await user.click(screen.getByLabelText(/I reviewed the amounts/))
  await user.click(screen.getByRole('button', { name: 'Save receipt changes' }))
  await user.click(screen.getByRole('button', { name: 'Retry same update' }))
  expect(mutate.mock.calls[1]).toEqual(mutate.mock.calls[2])
  expect(mutate.mock.calls[1]).toEqual([ '/receipts/receipt-id', 'PUT', expect.objectContaining({ reviewToken: 'reviewed', acknowledgeDifference: true }), expect.any(String), '3' ])
  expect(saved).toHaveBeenCalledOnce()
})
it('requires reason and confirmation before soft deletion', async () => {
  const api = new ApiClient('test'), user = userEvent.setup(), saved = vi.fn()
  const mutate = vi.spyOn(api, 'mutate').mockResolvedValue({})
  render(<ReceiptStatusAction receipt={receipt()} api={api} enabled onSaved={saved} onCancel={vi.fn()} />)
  expect(screen.getByRole('button', { name: 'Confirm delete' })).toBeDisabled()
  await user.type(screen.getByLabelText('Reason'), 'Duplicate entry')
  await user.click(screen.getByLabelText('I confirm this deletion.'))
  await user.click(screen.getByRole('button', { name: 'Confirm delete' }))
  expect(mutate).toHaveBeenCalledWith('/receipts/receipt-id', 'DELETE', { reason: 'Duplicate entry', confirmed: true }, expect.any(String), '3')
  expect(saved).toHaveBeenCalledOnce()
})
it('offers restoration for deleted receipts and disables read-only changes', () => {
  render(<ReceiptStatusAction receipt={{ ...receipt(), status: 'DELETED' }} api={new ApiClient('test')} enabled={false} onSaved={vi.fn()} onCancel={vi.fn()} />)
  expect(screen.getByRole('heading', { name: 'Restore receipt' })).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Confirm restore' })).toBeDisabled()
})
it('deletes a blank accidental receipt-wide discount without browser validation blocking review', async () => {
  const api = new ApiClient('test'), user = userEvent.setup()
  const mutate = vi.spyOn(api, 'mutate').mockResolvedValue({ reviewToken: 'reviewed', totals: receiptRow('test').totals })
  render(<ReceiptEdit receipt={receipt()} api={api} enabled onSaved={vi.fn()} onCancel={vi.fn()} />)
  await user.click(screen.getByRole('button', { name: 'Add receipt-wide discount' }))
  await user.click(screen.getByRole('button', { name: 'Delete receipt-wide discount 1' }))
  await user.type(screen.getByLabelText('Reason for correction'), 'Remove accidental discount')
  await user.click(screen.getByRole('button', { name: 'Review updated totals' }))
  expect(mutate.mock.calls[0][2]).toMatchObject({ discounts: [{ applicationId: 'old-price-reduction' }] })
})
it('omits a wholly blank receipt-wide discount while saving printed zero and unknown values distinctly', async () => {
  const api = new ApiClient('test'), user = userEvent.setup()
  const mutate = vi.spyOn(api, 'mutate').mockResolvedValue({ reviewToken: 'reviewed', totals: receiptRow('test').totals })
  render(<ReceiptEdit receipt={receipt()} api={api} enabled onSaved={vi.fn()} onCancel={vi.fn()} />)
  await user.click(screen.getByRole('button', { name: 'Add receipt-wide discount' }))
  await user.type(screen.getByLabelText('Walgreens Cash Used This Visit'), '0')
  await user.type(screen.getByLabelText('Reason for correction'), 'Printed footer')
  await user.click(screen.getByRole('button', { name: 'Review updated totals' }))
  expect(mutate.mock.calls[0][2]).toMatchObject({ printedFooter: { wcUsed: '0', wcEarned: null }, discounts: [{ applicationId: 'old-price-reduction' }] })
})
it('records RR separately, resolves an Other payment role and moves printed fields below payments', async () => {
  const api=new ApiClient('test'),user=userEvent.setup(),r=receipt()
  r.contents.payments[0].paymentType='OTHER'
  const mutate=vi.spyOn(api,'mutate').mockResolvedValue({reviewToken:'reviewed',totals:receiptRow('test').totals})
  render(<ReceiptEdit receipt={r} api={api} enabled onSaved={vi.fn()} onCancel={vi.fn()}/> )
  await user.selectOptions(screen.getByLabelText('Payment role 1'),'FUNDING')
  await user.click(screen.getByRole('button',{name:'Add RR used'}))
  await user.type(screen.getByLabelText('RR used amount 1'),'5')
  await user.type(screen.getByLabelText('Reason for correction'),'Record RR')
  expect(screen.getByLabelText('Printed Total Savings').compareDocumentPosition(screen.getByLabelText('Payment amount 1')) & Node.DOCUMENT_POSITION_PRECEDING).toBeTruthy()
  await user.click(screen.getByText('Printed Savings & Walgreens Cash — Show / Hide'))
  expect(screen.getByLabelText('Printed Total Savings')).not.toBeVisible()
  await user.click(screen.getByRole('button',{name:'Review updated totals'}))
  expect(mutate.mock.calls[0][2]).toMatchObject({rrUses:[{amount:'5'}],payments:[{role:'FUNDING',paymentType:'OTHER'}]})
})
it('uses a field-specific message for a zero payment instead of calling the API', async () => {
  const api=new ApiClient('test'),user=userEvent.setup(),r=receipt();r.contents.payments[0].amount='0'
  const mutate=vi.spyOn(api,'mutate')
  render(<ReceiptEdit receipt={r} api={api} enabled onSaved={vi.fn()} onCancel={vi.fn()}/> )
  await user.type(screen.getByLabelText('Reason for correction'),'Review payment')
  await user.click(screen.getByRole('button',{name:'Review updated totals'}))
  expect(screen.getByText(/Payment 1 amount must be greater than zero/)).toBeInTheDocument()
  expect(mutate).not.toHaveBeenCalled()
})

it('selects an available RR and retries the same association without another deduction',async()=>{
 const api=new ApiClient('test'),user=userEvent.setup(),saved=vi.fn()
 vi.spyOn(api,'rrMatches').mockResolvedValue([{eventId:'used',amount:'5',description:'Lysol RR',matched:false,options:[{sourceId:'earned',receiptEarning:true,amount:'5',promotion:'Lysol — Buy 2, Get $5 RR',sourceReceiptId:'source',sourceReceiptNumber:'1151',earnedDate:'2026-10-04',expires:'2026-10-18'}]}])
 const mutate=vi.spyOn(api,'mutate').mockRejectedValueOnce(new Error('Outcome unknown')).mockResolvedValueOnce({})
 render(<ReceiptRRMatch receipt={{transactionId:'spent',revision:'3'} as Receipt} api={api} enabled onSaved={saved}/> )
 await screen.findByRole('option',{name:/Lysol — Buy 2/})
 await user.selectOptions(screen.getByLabelText('Unused Register Reward'),'earned')
 await user.type(screen.getByLabelText('Matching note (optional)'),'Matched printed coupon')
 await user.click(screen.getByRole('button',{name:'Match RR'}))
 await user.click(screen.getByRole('button',{name:'Retry Same Match'}))
 expect(mutate.mock.calls[0]).toEqual(mutate.mock.calls[1])
 expect(mutate.mock.calls[0]).toEqual(['/receipts/spent/rr-matches','POST',{usageEventId:'used',sourceId:'earned',receiptEarning:true,reason:'Matched printed coupon'},expect.any(String),'3'])
 expect(saved).toHaveBeenCalledOnce()
})
it('adds an unknown-source RR without an earning receipt or invented dates and retries the same request',async()=>{
 const api=new ApiClient('test'),user=userEvent.setup(),saved=vi.fn()
 vi.spyOn(api,'rrMatches').mockResolvedValue([{eventId:'palmolive-use',amount:'2',description:'Palmolive',matched:false,options:[]}])
 const mutate=vi.spyOn(api,'mutate').mockRejectedValueOnce(new Error('Outcome unknown')).mockResolvedValueOnce({})
 render(<ReceiptRRMatch receipt={{transactionId:'spent',revision:'1'} as Receipt} api={api} enabled onSaved={saved}/> )
 await user.click(await screen.findByRole('button',{name:'Add RR without an earning receipt'}))
 expect(screen.getByLabelText('Earned date (optional)')).toHaveValue('')
 expect(screen.getByLabelText('Expiration date (optional)')).toHaveValue('')
 expect(screen.getByRole('button',{name:'Save RR and match'})).toBeDisabled()
 await user.click(screen.getByLabelText(/I confirm this RR was used/))
 await user.click(screen.getByRole('button',{name:'Save RR and match'}))
 await user.click(screen.getByRole('button',{name:'Retry Same Source Entry'}))
 expect(mutate.mock.calls[0]).toEqual(mutate.mock.calls[1])
 expect(mutate.mock.calls[0]).toEqual(['/receipts/spent/rr-matches/unknown-source','POST',{usageEventId:'palmolive-use',rewardId:expect.any(String),description:'Palmolive',earnedDate:null,expirationDate:null,reason:'Earning receipt/source not recorded',confirmRecordedUse:true},expect.any(String),'1'])
 expect(saved).toHaveBeenCalledOnce()
})
it('requires explicit availability confirmation to match a saved reward with unknown earning history',async()=>{
 const api=new ApiClient('test'),user=userEvent.setup()
 vi.spyOn(api,'rrMatches').mockResolvedValue([{eventId:'use',amount:'2',description:'Palmolive',matched:false,options:[{sourceId:'source',receiptEarning:false,historyUnknown:true,amount:'2',promotion:'Palmolive RR',sourceReceiptId:null,sourceReceiptNumber:null,earnedDate:null,expires:null}]}])
 const mutate=vi.spyOn(api,'mutate').mockResolvedValue({})
 render(<ReceiptRRMatch receipt={{transactionId:'spent',revision:'1'} as Receipt} api={api} enabled onSaved={vi.fn()}/>)
 await screen.findByRole('option',{name:/Palmolive RR/});await user.selectOptions(screen.getByLabelText('Unused Register Reward'),'source')
 expect(screen.getByLabelText('Matching note (optional)')).not.toBeRequired()
 expect(screen.getByRole('button',{name:'Match RR'})).toBeDisabled()
 await user.click(screen.getByLabelText(/I confirm this RR was available for this purchase/))
 await user.click(screen.getByRole('button',{name:'Match RR'}))
 expect(mutate.mock.calls[0][2]).toMatchObject({sourceId:'source',receiptEarning:false,confirmUnknownHistory:true,reason:''})
})


const filters = { search: '', status: 'ALL', day: '', month: '', week: '', order: 'newest' as const }
describe('receipt browsing', () => {
  it('finds receipts by full month, abbreviation, and month/year', () => {
    const october = receiptRow('oct', '2026-10-19')
    const november = receiptRow('nov', '2026-11-01')
    for (const search of ['october', 'October', 'OCTOBER', 'Oct', 'October 2026']) {
      expect(browseReceipts([november, october], { ...filters, search }).map(row => row.transactionId)).toEqual(['oct'])
    }
  })

  it('finds the requested receipt with missing or partial hyphen separators', () => {
    const row=receiptRow('lookup'); row.metadata={...row.metadata!,receiptNumber:'1151-3213-7477-2610-0803'}
    for(const search of ['11513213747726100803','1151-3213747726100803','1151-3213-7477-2610-0803','1151 3213 7477 2610 0803']) {
      expect(browseReceipts([row],{...filters,search}).map(r=>r.transactionId)).toEqual([row.transactionId])
    }
    expect(matchesReceiptSearch(row,'11513213747726100804')).toBe(false)
  })
  it('searches receipt numbers with or without separators and preserves leading zeros', () => {
    const row = receiptRow('1234')
    expect(matchesReceiptSearch(row, '0001-1234')).toBe(true)
    expect(matchesReceiptSearch(row, '00011234')).toBe(true)
    expect(matchesReceiptSearch(row, '0001 1234')).toBe(true)
    expect(matchesReceiptSearch(row, 'walgreens')).toBe(true)
    expect(matchesReceiptSearch(row, '2026-10-19')).toBe(true)
    expect(matchesReceiptSearch(row, 'missing')).toBe(false)
    expect(matchesReceiptSearch(row, 'Walgreens 0001-1234')).toBe(true)
    expect(matchesReceiptSearch(row, '0001–1234')).toBe(true)
    expect(matchesReceiptSearch(row, 'Oct 19, 2026')).toBe(true)
    expect(matchesReceiptSearch(row, '10/19/2026')).toBe(true)
  })
  it('sorts mixed printed times chronologically without mutating source rows', () => {
    const rows = [receiptRow('1', undefined, '940 am'), receiptRow('2', undefined, '04:23PM'), receiptRow('3', undefined, '17:09')]
    expect(browseReceipts(rows, filters).map(r => r.transactionId)).toEqual(['3', '2', '1'])
    expect(browseReceipts(rows, { ...filters, order: 'oldest' }).map(r => r.transactionId)).toEqual(['1', '2', '3'])
    expect(rows[0].transactionId).toBe('1')
    expect(receiptTime(rows[0])).toBe('9:40 AM')
  })
  it('filters month and week boundaries and keeps unknown dates last', () => {
    const rows = ['2026-10-07', '2026-10-08', '2026-10-28', '2026-10-29', '2026-11-01'].map((day, i) => receiptRow(String(i), day))
    expect(browseReceipts(rows, { ...filters, month: '2026-10', week: '1' }).map(r => r.date)).toEqual(['2026-10-07'])
    expect(browseReceipts(rows, { ...filters, month: '2026-10', week: '5' }).map(r => r.date)).toEqual(['2026-10-29'])
    expect(browseReceipts(rows, { ...filters, day: '2026-11-01' })).toHaveLength(1)
    const undated = { ...receiptRow('unknown'), date: null }
    expect(browseReceipts([undated, ...rows], filters).at(-1)?.transactionId).toBe('unknown')
    expect(browseReceipts([undated, ...rows], { ...filters, order: 'oldest' }).at(-1)?.transactionId).toBe('unknown')
  })
})


it('assigns shopping weeks Sunday through Saturday to their starting month', () => {
  expect(shoppingWeek('2026-10-01')).toEqual({ start: '2026-09-27', end: '2026-10-03', month: '2026-09' })
  expect(shoppingWeek('2026-10-03')?.start).toBe('2026-09-27')
  expect(shoppingWeek('2026-10-04')).toEqual({ start: '2026-10-04', end: '2026-10-10', month: '2026-10' })
  expect(shoppingWeek('2027-01-01')).toEqual({ start: '2026-12-27', end: '2027-01-02', month: '2026-12' })
})

it('opens account balances and reveals existing RR connections only on request', async () => {
  const data=workspace(),api=new ApiClient('test'),onReceipt=vi.fn(),user=userEvent.setup()
  data.asOf=new Date().toLocaleDateString('sv-SE');data.balances=[{provider:'Register Rewards',balance:{monthUsd:'2',startUsd:'2',endUsd:'2',startPoints:null,endPoints:null}}]
  const balance={monthUsd:'2',startUsd:'2',endUsd:'2',startPoints:null,endPoints:null}
  const report:AccountReport={provider:'Register Rewards',from:data.asOf.slice(0,7)+'-01',to:data.asOf,beginning:{...balance,opening:{date:'2026-09-30',sourceNote:'Confirmed account opening',valuationBasis:'USD'}},ending:balance,earned:'7',spent:'7',earnedPoints:'0',spentPoints:'0',usdActivityComplete:true,activity:[{movement:{date:'2026-10-05',time:'10:13',provider:'Register Rewards',usd:'-7',points:null,source:'technical ids',receiptId:'spent',eventId:'use'},receiptNumber:'0476',description:'Used Neutrogena — Buy 2, Get $7 RR',beforeUsd:'9',afterUsd:'2',beforePoints:null,afterPoints:null,orderKnown:true,rewards:[{earningId:'earning',receiptId:'earned',receiptNumber:'1151',date:'2026-10-05',time:'8:17 AM',promotion:'Neutrogena — Buy 2, Get $7 RR',amount:'7',spentReceiptId:'spent',spentReceiptNumber:'0476',usedAmount:'7',status:'PAID'}]}]}
  vi.spyOn(api,'account').mockResolvedValue(report)
  vi.spyOn(api,'workspaceAt').mockResolvedValue(data)
 render(<RewardsAccounts data={data} api={api} writable onReceipt={onReceipt}/>)
  await user.click(await screen.findByRole('button',{name:'Register Rewards'}))
  expect(await screen.findByText(/Carried Forward/)).toBeInTheDocument()
  expect(screen.getByText('Used Neutrogena — Buy 2, Get $7 RR')).toBeInTheDocument()
  expect(screen.getByRole('button',{name:'Receipt 1151',hidden:true})).not.toBeVisible()
  await user.click(screen.getByText('Used Neutrogena — Buy 2, Get $7 RR'))
  await user.click(screen.getByText('View Reward'))
  await user.click(screen.getByRole('button',{name:'Receipt 1151'}))
  expect(onReceipt).toHaveBeenCalledWith('earned')
})
function pointsReport(provider:string,asOf:string):AccountReport {
 const balance={monthUsd:'1',startUsd:'1',endUsd:'2',startPoints:'100',endPoints:'125'}
 return {provider,from:asOf.slice(0,7)+'-01',to:asOf,beginning:{...balance,opening:null},ending:balance,earned:'1',spent:'0',earnedPoints:'25',spentPoints:'0',usdActivityComplete:true,activity:[{movement:{date:asOf,time:'09:00',provider,usd:'1',points:'25',source:'Recorded reward',receiptId:null,eventId:null},receiptNumber:null,description:'Recorded reward',beforeUsd:'1',afterUsd:'2',beforePoints:'100',afterPoints:'125',orderKnown:true,rewards:[],valuationBasis:'Saved points valuation'}],conversions:[{redemptionId:'conversion',requestedDate:asOf,originalUnitsUsed:'500',sourceUsdValue:'5',destinationAccount:'PayPal',destinationUsdValue:'5',status:'RECEIVED',completedDate:asOf}]}
}
it.each(['MyPoints','Fetch','P&G Brand','Swagbucks','Merryfield'])('shows points balances, activity and entry only for approved points account %s',async provider=>{
 const data=workspace(),api=new ApiClient('test'),user=userEvent.setup();data.asOf=new Date().toLocaleDateString('sv-SE');const report=pointsReport(provider,data.asOf)
 data.balances=[{provider,balance:report.ending}];vi.spyOn(api,'account').mockResolvedValue(report)
 vi.spyOn(api,'workspaceAt').mockResolvedValue(data)
 render(<RewardsAccounts data={data} api={api} writable onReceipt={vi.fn()}/> )
 await user.click(await screen.findByRole('button',{name:provider}))
 expect(await screen.findByText('Beginning Points')).toBeInTheDocument()
 expect(screen.getByRole('columnheader',{name:'Points Before'})).toBeInTheDocument()
 await user.click(screen.getByRole('button',{name:'Set Beginning Balance'}))
 expect(screen.getByLabelText('Original Points')).toBeInTheDocument()
})
it.each(['Walgreens Cash','Register Rewards','PayPal','Ibotta'])('hides points information for %s even when old data contains native units',async provider=>{
 const data=workspace(),api=new ApiClient('test'),user=userEvent.setup();data.asOf=new Date().toLocaleDateString('sv-SE');const report=pointsReport(provider,data.asOf)
 data.balances=[{provider,balance:report.ending}];vi.spyOn(api,'account').mockResolvedValue(report)
 vi.spyOn(api,'workspaceAt').mockResolvedValue(data)
 render(<RewardsAccounts data={data} api={api} writable onReceipt={vi.fn()}/> )
 expect(screen.queryByRole('columnheader',{name:'Original Points At Close'})).not.toBeInTheDocument()
 await user.click(await screen.findByRole('button',{name:provider}))
 await screen.findByRole('heading',{name:'Account Balances'})
 expect(screen.queryByText(/points/i)).not.toBeInTheDocument()
 expect(screen.queryByRole('columnheader',{name:'Points Before'})).not.toBeInTheDocument()
 await user.click(screen.getByRole('button',{name:'Set Beginning Balance'}))
 expect(screen.queryByLabelText('Original Points')).not.toBeInTheDocument()
 expect(screen.getByLabelText('Balance Basis')).toBeInTheDocument()
})

it('saves an unused reward from Rewards without any receipt and retains the same request on retry',async()=>{
 const api=new ApiClient('test'),user=userEvent.setup(),changed=vi.fn()
 vi.spyOn(api,'standaloneRewards').mockResolvedValueOnce([]).mockResolvedValue([{rewardId:'reward',description:'Palmolive RR',amount:'2',earnedDate:null,expirationDate:null,status:'UNUSED'}])
 const mutate=vi.spyOn(api,'mutate').mockRejectedValueOnce(new Error('Outcome unknown')).mockResolvedValueOnce({})
 render(<StandaloneRewardPanel api={api} writable onChanged={changed}/>)
 await user.click(screen.getByRole('button',{name:'Add reward without a receipt'}))
 await user.type(screen.getByLabelText('Reward description'),'Palmolive RR')
 await user.type(screen.getByLabelText('Reward amount'),'2')
 await user.click(screen.getByLabelText('I confirm this is an unused RR coupon available to use.'))
 await user.click(screen.getByRole('button',{name:'Save reward'}))
 await user.click(screen.getByRole('button',{name:'Retry same reward'}))
 expect(mutate.mock.calls[0]).toEqual(mutate.mock.calls[1])
 expect(mutate.mock.calls[0]).toEqual(['/rewards/register-rewards','POST',{rewardId:expect.any(String),description:'Palmolive RR',amount:'2',earnedDate:null,expirationDate:null,confirmAvailable:true},expect.any(String)])
 expect(await screen.findByText('Palmolive RR')).toBeInTheDocument();expect(changed).toHaveBeenCalledOnce()
})
it('disables reward registration for read-only access',()=>{
 const api=new ApiClient('test');vi.spyOn(api,'standaloneRewards').mockResolvedValue([])
 render(<StandaloneRewardPanel api={api} writable={false}/>)
 expect(screen.getByRole('button',{name:'Add reward without a receipt'})).toBeDisabled()
})

// Regression: display the existing CLI metric even when the stricter completeness metric is null.
it.each([['3.60','-9.23','$−9.23'],['26.69','-12.50','$−12.50'],['2.12','-3.01','$−3.01']])('shows spreadsheet Total Paid after rebates for recorded cash/card %s', (paid,after,display)=>{
 const totals=receiptRow('spreadsheet').totals
 totals.savings.paidAfterRebates=after;totals.benefitAdjustedCashSpending=null
 totals.paymentReview={printedTotal:paid,calculatedPurchaseTotal:null,purchaseDifference:null,knownFundingSubtotal:paid,recordedCashCardPaid:paid,giftCardFunding:'0',otherFunding:'0',difference:null,status:'CANNOT_VERIFY',printedTotalBasis:'UNKNOWN',paymentEntryStatus:'UNKNOWN',unresolvedPaymentIds:[],reason:'Historical metadata unknown',acknowledgementRequired:false}
 render(<ReceiptFinancialReview totals={totals}/>)
 expect(screen.getByText('Total Paid after rebates').parentElement).toHaveTextContent(display)
 expect(screen.getByText('Actual cash/card paid').parentElement).toHaveTextContent(`$${paid}`)
 expect(screen.queryByText('Benefit-adjusted cash spending')).not.toBeInTheDocument()
})
it('explains genuinely missing rebate valuation instead of showing a made-up after-rebate total',()=>{
 const totals=receiptRow('missing-valuation').totals
 totals.savings.paidAfterRebates=null;totals.savings.recordedCashCardComplete=true;totals.savings.confirmedBenefitsComplete=false
 render(<ReceiptFinancialReview totals={totals}/>)
 expect(screen.getByText('Total Paid after rebates').parentElement).toHaveTextContent('Cannot calculate yet')
 expect(screen.getByText(/a confirmed benefit is missing its dollar valuation/)).toBeInTheDocument()
})

it('shows all six period metrics and loads inclusive date-range totals without pending rebates', async () => {
  const api = new ApiClient('test'), user = userEvent.setup(), data = workspace()
  const summary = { ...data.summaries.all, regularWithTax: '211.23', recordedCashCardPaid: '83.49', totalSaved: '258.99', newlyEarned: '131.25', paidAfterRebates: '-47.76', percentSaved: '122.61' }
  const load = vi.spyOn(api, 'receiptSummary').mockResolvedValue(summary)
  render(<WorkspacePages api={api} data={data} page="receipts" writable onPage={vi.fn()} onReceipt={vi.fn()} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()} />)
  await user.type(screen.getByLabelText('Start date'), '2026-10-05')
  await user.type(screen.getByLabelText('End date'), '2026-10-05')
  await waitFor(() => expect(load).toHaveBeenCalledWith('2026-10-05', '2026-10-05', expect.any(AbortSignal)))
  const panel = screen.getByText('Regular price including tax').closest<HTMLElement>('.receipt-period-totals')!
  await waitFor(() => expect(within(panel).getByText('$211.23')).toBeInTheDocument())
  expect(within(panel).getByText('$83.49')).toBeInTheDocument()
  expect(within(panel).getByText('$258.99')).toBeInTheDocument()
  expect(within(panel).getByText('$131.25')).toBeInTheDocument()
  expect(within(panel).getByText('$−47.76')).toBeInTheDocument()
  expect(within(panel).getByText('122.61%')).toBeInTheDocument()
  expect(screen.queryByText('Pending Rebates')).not.toBeInTheDocument()
})

it('uses a single received reward amount and hides manual Fetch valuation fields', () => {
  const reward = { ...newReward(), provider: 'Fetch', amount: '25', status: 'CREDITED' }
  render(<RewardFields value={reward} onChange={vi.fn()} lines={[]} />)
  expect(screen.queryByLabelText('Confirm received reward amount 1')).not.toBeInTheDocument()
  expect(screen.queryByLabelText('Saved USD valuation 1 (optional)')).not.toBeInTheDocument()
  expect(screen.getByText(/1,000 points = \$1/)).toBeInTheDocument()
})
it('marks and focuses the first missing receipt field when requesting a breakdown', async () => {
  const { user, mutate } = setup()
  await user.click(screen.getByRole('button', { name: 'Review entered breakdown' }))
  expect(screen.getByLabelText('Store')).toHaveFocus()
  expect(screen.getByLabelText('Store')).toHaveAttribute('aria-invalid', 'true')
  expect(screen.getByRole('alert')).toHaveTextContent('Store')
  expect(mutate).not.toHaveBeenCalled()
})

it('keeps repeated-value RR coupons separate through preview and save', async () => {
  const { user, mutate } = setup(); await minimum(user)
  for (const [i, amount] of ['5.00','5.00','4.00'].entries()) {
    await user.click(screen.getByRole('button', { name: 'Add RR used' }))
    await user.type(screen.getByLabelText(`RR used amount ${i+1}`), amount)
  }
  await user.click(screen.getByRole('button', { name: 'Review calculated totals' }))
  const body = mutate.mock.calls[0][2] as { rrUses: { eventId: string; amount: string; recordedDescription: string }[] }
  expect(body.rrUses.map(r => r.amount)).toEqual(['5.00','5.00','4.00'])
  expect(new Set(body.rrUses.map(r => r.eventId)).size).toBe(3)
  expect(body).not.toHaveProperty('rrUsed')
  await user.click(screen.getByRole('button', { name: 'Save receipt' }))
  expect((mutate.mock.calls[1][2] as typeof body).rrUses).toEqual(body.rrUses)
})

it('accepts a tax amount without a leading zero', async () => {
  const { user, mutate } = setup(); await minimum(user)
  await user.clear(screen.getByLabelText('Sales tax')); await user.type(screen.getByLabelText('Sales tax'), '.98')
  expect(screen.getByLabelText('Sales tax')).toBeValid()
  await user.click(screen.getByRole('button', {name:'Review calculated totals'}))
  expect((mutate.mock.calls[0][2] as {tax:string}).tax).toBe('.98')
})

it('searches accounts and opens activity through the selected balance date', async () => {
  const data=workspace(),api=new ApiClient('test'),user=userEvent.setup()
  data.balances=[{provider:'Fetch',balance:pointsReport('Fetch','2026-10-11').ending}]
  const load=vi.spyOn(api,'workspaceAt').mockResolvedValue(data)
  const account=vi.spyOn(api,'account').mockResolvedValue(pointsReport('Fetch','2026-10-11'))
  render(<RewardsAccounts data={data} api={api} writable onReceipt={vi.fn()}/>)
  await screen.findByRole('button',{name:'Fetch'})
  expect(screen.queryByLabelText('Open Account')).not.toBeInTheDocument()
  await user.type(screen.getByLabelText('Search accounts'),'missing');expect(screen.queryByRole('button',{name:'Fetch'})).not.toBeInTheDocument()
  await user.clear(screen.getByLabelText('Search accounts'))
  await user.clear(screen.getByLabelText('Balance through'));await user.type(screen.getByLabelText('Balance through'),'2026-10-11')
  await waitFor(()=>expect(load).toHaveBeenCalledWith('2026-10-11',expect.any(AbortSignal)))
  await user.click(await screen.findByRole('button',{name:'Fetch'}))
  await waitFor(()=>expect(account).toHaveBeenCalledWith('Fetch','2026-10-01','2026-10-11',expect.any(AbortSignal)))
})

it('fills a saved SKU product without copying historical discounts or purchased quantity', async () => {
  const user=userEvent.setup(),api=new ApiClient('test')
  render(<ReceiptCreate api={api} enabled onClose={vi.fn()} onSaved={vi.fn()} onReview={vi.fn()} products={[{sku:'0012345',description:'Charmin Strong Mega',regularPrice:'7.49',purchaseDate:'2026-10-08',receiptNumber:'prior'}]}/>)
  await user.type(screen.getByLabelText('SKU / UPC 1 (optional)'),'00123')
  await user.click(screen.getByRole('button',{name:'Use this product: Charmin Strong Mega'}))
  expect(screen.getByLabelText('Product description 1')).toHaveValue('Charmin Strong Mega')
  expect(screen.getByLabelText('SKU / UPC 1 (optional)')).toHaveValue('0012345')
  expect(screen.getByLabelText('Regular price 1')).toHaveValue('7.49')
  expect(screen.getByLabelText('Quantity 1')).toHaveValue('1')
  expect(screen.queryByLabelText('Product 1 discount 1 amount')).not.toBeInTheDocument()
})

it('focuses newly added product and discount description fields', async () => {
  const {user}=setup()
  await user.click(screen.getByRole('button',{name:'Add product'}))
  expect(screen.getByLabelText('Product description 2')).toHaveFocus()
  await user.click(screen.getByRole('button',{name:'Add discount to product 2'}))
  expect(screen.getByLabelText('Product 2 discount 1 description')).toHaveFocus()
  await user.click(screen.getByRole('button',{name:'Add shared or receipt-wide discount'}))
  expect(screen.getByLabelText('Discount label 1')).toHaveFocus()
})

it('keeps shared discount product selection available after selecting its first item', async () => {
  const {user,mutate}=setup();await minimum(user)
  await user.click(screen.getByRole('button',{name:'Add product'}))
  await user.type(screen.getByLabelText('Product description 2'),'Second product')
  await user.type(screen.getByLabelText('Regular price 2'),'3')
  await user.click(screen.getByRole('button',{name:'Add shared or receipt-wide discount'}))
  await user.type(screen.getByLabelText('Discount label 1'),'Shared coupon')
  await user.type(screen.getByLabelText('Discount amount 1'),'1')
  await user.click(screen.getByLabelText('Discount products 1: line 1'))
  expect(screen.getByLabelText('Discount products 1: line 2')).toBeInTheDocument()
  expect(screen.getByLabelText('Discount label 1')).toHaveValue('Shared coupon')
  await user.click(screen.getByLabelText('Discount products 1: line 2'))
  expect(screen.getByLabelText('Discount products 1: line 1')).toBeChecked()
  expect(screen.getByLabelText('Discount products 1: line 2')).toBeChecked()
  await user.click(screen.getByRole('button',{name:'Review calculated totals'}))
  const body=mutate.mock.calls[0][2] as {discounts:{lineItemIds:string[];wholeReceipt:boolean;amount:string}[]}
  expect(body.discounts[0].lineItemIds).toHaveLength(2)
  expect(body.discounts[0]).toMatchObject({wholeReceipt:false,amount:'1'})
})

it('opens OOP detail records with cash/card totals and searchable sortable receipt links', async () => {
  const api=new ApiClient('test'),user=userEvent.setup(),data=workspace(),open=vi.fn()
  const summary={...data.summaries.all,recordedCashCardPaid:'3.60',cash:'1.10',card:'2.50'}
  const load=vi.spyOn(api,'receiptReport').mockResolvedValue({metric:'oop',summary,matchedTotal:'3.60',matchedCash:'1.10',matchedCard:'2.50',rows:[{receiptId:'source',receiptNumber:'000123',store:'Walgreens',date:'2026-10-05',time:'8:17 AM',description:'Card',amount:'2.50'}]})
  render(<WorkspacePages api={api} data={data} page="receipts" writable onPage={vi.fn()} onReceipt={open} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/>)
  await user.click(screen.getByRole('button',{name:'View Actual OOP'}))
  const dialog=await screen.findByRole('dialog',{name:'Actual OOP — All time'})
  await within(dialog).findByText('Period cash paid')
  expect(within(dialog).getByText('Period card paid')).toBeInTheDocument()
  await user.type(within(dialog).getByLabelText('Search detail records'),'Walgreens')
  await user.selectOptions(within(dialog).getByLabelText('Sort detail records'),'store')
  await waitFor(()=>expect(load).toHaveBeenLastCalledWith('oop','','','Walgreens','store',expect.any(AbortSignal)))
  await user.click(await within(dialog).findByRole('button',{name:'000123'}))
  expect(open).toHaveBeenCalledWith('source')
})

it('opens benefits with separate waiting amounts and confirms using the recorded reward amount', async () => {
  const api=new ApiClient('test'),user=userEvent.setup(),data=workspace()
  const earning={earningId:'waiting',provider:'Walgreens',kind:'WALGREENS_CASH',status:'EXPECTED',scope:'RECEIPT',lineItemIds:[],expected:{kind:'STORE_REWARD',amount:'3.40',currency:'USD',program:'Walgreens'},confirmed:null,valuation:null,offerReference:'Member rewards'} as Receipt['contents']['observations']['earnings'][number]
  vi.spyOn(api,'receiptReport').mockResolvedValue({metric:'benefits',summary:data.summaries.all,matchedTotal:'5',unconfirmedTotal:'3.40',matchedCash:'0',matchedCard:'0',rows:[{receiptId:'source',revision:'2',receiptNumber:'000123',store:'Walgreens',date:'2026-10-05',time:'8:17 AM',description:'Member rewards',amount:'3.40',nativeAmount:'3.40',unit:'USD',group:'Unconfirmed',status:'EXPECTED',earning}]})
  const save=vi.spyOn(api,'mutate').mockResolvedValue({})
  render(<WorkspacePages api={api} data={data} page="receipts" writable onPage={vi.fn()} onReceipt={vi.fn()} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/>)
  await user.click(screen.getByRole('button',{name:'View Confirmed benefits'}))
  await user.click(await screen.findByRole('button',{name:'Confirm / update status'}))
  expect(screen.getByText('Matching unconfirmed benefits')).toBeInTheDocument()
  await user.selectOptions(screen.getByLabelText('Reward status'),'CREDITED')
  await user.click(screen.getByRole('button',{name:'Save reward status'}))
  expect(save).toHaveBeenCalledWith('/receipts/source/earnings/waiting/status','PUT',{status:'CREDITED',amount:'3.40',unit:'USD',confirmAmountAndUnit:true},expect.any(String),'2')
})

it('shows available and expected rewards separately from their combined total', async () => {
  const data=workspace(),user=userEvent.setup()
  data.rewardsOverview={asOf:data.asOf,available:'23',expected:'5',total:'28',knownAvailable:'23',knownExpected:'5',expectedCount:'1',missingBalances:[],missingValuations:[]}
  render(<RewardsAccounts data={data} writable onReceipt={vi.fn()}/>)
  const panel=screen.getByRole('heading',{name:'Rewards Overview'}).closest('section')!
  expect(within(panel).getByText('$28.00')).toBeInTheDocument()
  expect(within(panel).getByText('$23.00')).toBeInTheDocument()
  expect(within(panel).getByText('$5.00')).toBeInTheDocument()
  await user.type(screen.getByLabelText('Search accounts'),'no match')
  expect(within(panel).getByText('$28.00')).toBeInTheDocument()
})

it('shows printed Walgreens fields only when the entered store is Walgreens', async () => {
  const {user}=setup()
  expect(screen.queryByText('Printed Savings & Walgreens Cash — Show / Hide')).not.toBeInTheDocument()
  await user.type(screen.getByLabelText('Store'),'walgreens')
  expect(screen.getByText('Printed Savings & Walgreens Cash — Show / Hide')).toBeInTheDocument()
  await user.clear(screen.getByLabelText('Store'));await user.type(screen.getByLabelText('Store'),'CVS')
  expect(screen.queryByText('Printed Savings & Walgreens Cash — Show / Hide')).not.toBeInTheDocument()
})

it('adds separate receipt-wide ECB uses for CVS with descriptions', async()=>{
 const {user}=setup()
 await user.type(screen.getByLabelText('Store'),'CVS')
 await user.click(screen.getByRole('button',{name:'Add ECB used'}))
 await user.type(screen.getByLabelText('ECB used amount 1'),'.98')
 await user.type(screen.getByLabelText('ECB used description 1'),'Beauty reward')
 await user.click(screen.getByRole('button',{name:'Add ECB used'}))
 expect(screen.getByLabelText('ECB used amount 1')).toHaveValue('.98')
 expect(screen.getByLabelText('ECB used description 1')).toHaveValue('Beauty reward')
 expect(screen.getByLabelText('ECB used amount 2')).toBeInTheDocument()
 await user.click(screen.getByRole('button',{name:'Remove ECB use 2'}))
 expect(screen.queryByLabelText('ECB used amount 2')).not.toBeInTheDocument()
})

it('records the buy quantity separately from a manufacturer coupon deduction',async()=>{
 const {user}=setup()
 await user.click(screen.getByRole('button',{name:'Add discount to product 1'}))
 await user.selectOptions(screen.getByLabelText('Product 1 discount 1 type'),'MANUFACTURER')
 await user.type(screen.getByLabelText(/Product 1 discount 1 required purchase quantity/),'2')
 await user.type(screen.getByLabelText('Product 1 discount 1 amount'),'4')
 expect(screen.getByLabelText(/Product 1 discount 1 required purchase quantity/)).toHaveValue('2')
 expect(screen.getByLabelText('Product 1 discount 1 amount')).toHaveValue('4')
})

it('defaults CVS earned rewards to named ECB and hides Walgreens entry controls',async()=>{
 const {user}=setup()
 await user.type(screen.getByLabelText('Store'),'CVS')
 expect(screen.queryByRole('button',{name:'Add RR used'})).not.toBeInTheDocument()
 expect(screen.queryByText('Printed Savings & Walgreens Cash — Show / Hide')).not.toBeInTheDocument()
 await user.click(screen.getByRole('button',{name:'Add rebate or reward'}))
 expect(screen.getByLabelText('Reward program 1')).toHaveValue('ECB')
 expect(screen.queryByRole('option',{name:'Walgreens Cash'})).not.toBeInTheDocument()
 await user.type(screen.getByLabelText('Reward amount 1 (USD)'),'2')
 await user.type(screen.getByLabelText('ECB reward name 1'),'Buy 2 earn 2')
 expect(screen.getByLabelText('ECB reward name 1')).toHaveValue('Buy 2 earn 2')
 await user.selectOptions(screen.getByLabelText('CVS reward type'),'PURCHASE')
 expect(screen.getByLabelText('ECB reward name 1')).toHaveValue('2% purchase earnings')
 await user.selectOptions(screen.getByLabelText('Reward status 1'),'EXPECTED')
 expect(screen.getByLabelText('Reward status 1')).toHaveValue('EXPECTED')
})

it('saves a member account credit without creating a receipt',async()=>{
 const api=new ApiClient('test'),saved=vi.fn(),mutate=vi.spyOn(api,'mutate').mockResolvedValue({}),user=userEvent.setup()
 render(<MemberRewardEntry provider="ECB" api={api} writable day="2026-10-24" onSaved={saved}/>)
 await user.click(screen.getByRole('button',{name:'Add member reward without a receipt'}))
 await user.type(screen.getByLabelText('Member reward amount'),'.98')
 await user.type(screen.getByLabelText('Member reward name / description'),'Member bonus')
 await user.type(screen.getByLabelText('ECB expiration date (optional)'),'2026-11-24')
 await user.click(screen.getByRole('button',{name:'Save member reward'}))
 expect(mutate).toHaveBeenCalledWith('/accounts/member-rewards','POST',expect.objectContaining({provider:'ECB',amount:'.98',description:'Member bonus',date:'2026-10-24',expirationDate:'2026-11-24'}),expect.any(String))
 expect(saved).toHaveBeenCalled()
})

it('selects a clipped coupon once and sends its link with the receipt discount',async()=>{
 const api=new ApiClient('test'),mutate=vi.spyOn(api,'mutate').mockResolvedValue(result()),user=userEvent.setup(),id=crypto.randomUUID()
 render(<ReceiptCreate coupons={[{couponId:id,provider:'Test',description:'Two off',discountAmount:'2',requiredQuantity:'1',status:'CLIPPED',couponType:'MANUFACTURER',expirationDate:'2026-10-31',externalId:null,clippedOn:null,redeemedOn:null,redeemedTransactionId:null,revision:'1'}]} api={api} enabled onClose={vi.fn()} onReview={vi.fn()} onSaved={vi.fn()}/>)
 await minimum(user)
 await user.selectOptions(screen.getByLabelText('Use a clipped coupon for product 1'),id)
 expect(screen.getByLabelText('Product 1 discount 1 amount')).toHaveValue('2')
 expect(screen.queryByRole('option',{name:/Test · Two off/})).not.toBeInTheDocument()
 await user.click(screen.getByRole('button',{name:'Review calculated totals'}))
 expect(mutate.mock.calls[0][2]).toEqual(expect.objectContaining({clippedCouponIds:[id],discounts:[expect.objectContaining({applicationId:id,amount:'2',kind:'MANUFACTURER'})]}))
})

it('saves an existing reward expiration without resubmitting amount or status',async()=>{
 const r=receipt(),api=new ApiClient('test'),mutate=vi.spyOn(api,'mutate').mockResolvedValue({}),saved=vi.fn(),user=userEvent.setup()
 const earning={earningId:'ecb',provider:'ECB',kind:'THIRD_PARTY_REBATE',status:'CREDITED',scope:'ITEM',lineItemIds:['original-line'],expected:null,confirmed:{kind:'STORE_REWARD',amount:'10',currency:'USD',program:'ECB'},valuation:null,offerReference:'Scott Spend 20 Get 10',expirationDate:null} as Receipt['contents']['observations']['earnings'][number]
 render(<EarningLinksForm receipt={r} earning={earning} api={api} enabled onSaved={saved} onCancel={vi.fn()}/>)
 await user.type(screen.getByLabelText('Reward expiration date (optional)'),'2026-11-19')
 await user.click(screen.getByRole('button',{name:'Save reward details'}))
 expect(mutate).toHaveBeenCalledWith('/receipts/receipt-id/earnings/ecb/associations','PUT',{expirationDate:'2026-11-19',lineItemIds:['original-line'],qualifyingQuantities:{'original-line':1}},expect.any(String),'3')
 expect(saved).toHaveBeenCalled()
})
