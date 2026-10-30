import { SectionHelp } from '../../components/ui'
import { useState } from 'react'
import { EarningLinksForm } from './EarningLinksForm'
import { RewardFields } from './RewardFields'
import { newReward } from './rewardStatus'
import { ReceiptEdit, ReceiptStatusAction, ReceiptRRMatch } from './ReceiptDetail'
import { WorkspacePages, RewardsAccounts, StandaloneRewardPanel, MemberRewardEntry, PayoutEntry, PlanScenarioEditor, ProductCouponSubtotal, ScenarioBreakdown } from '../../pages/WorkspacePages'
import { browseReceipts, matchesReceiptSearch, receiptTime, shoppingWeek } from '../../api/format'
import type { Receipt, AccountReport, Workspace, Totals, RewardOffer } from '../../types/platform'
import { fireEvent, render, screen, within, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiClient, ApiError, scenarioRequest } from '../../api/client'
import { workspace, receiptRow, planningRow } from '../../api/fixtures.test-support'
import type { ReceiptDraftState } from './ReceiptCreate'
import { ReceiptCreate, ReceiptFinancialReview, PrintedFooterView, ReceiptDiscountSale } from './ReceiptCreate'
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
  it('defaults RR copied into an actual receipt to printed and preserves an explicit waiting status',async()=>{
    const draft=planningFixture(),api=new ApiClient('test'),user=userEvent.setup(),mutate=vi.spyOn(api,'mutate').mockResolvedValue(result())
    render(<ReceiptCreate planning={draft} api={api} enabled onClose={vi.fn()} onSaved={vi.fn()} onReview={vi.fn()}/>)
    expect(screen.getByLabelText('Reward status 1')).toHaveValue('PAID')
    await user.selectOptions(screen.getByLabelText('Reward status 1'),'EXPECTED')
    await user.click(screen.getByRole('button',{name:'Review calculated totals'}))
    const body=mutate.mock.calls.find(c=>c[0]==='/receipts/preview')![2] as {earnings:{status:string;confirmedAmount?:string}[]}
    expect(body.earnings[0].status).toBe('EXPECTED');expect(body.earnings[0].confirmedAmount).toBeUndefined()
  })
  it('defaults copied RR terms to printed instead of an unmatchable legacy credited status',async()=>{
    const user=userEvent.setup(),change=vi.fn(),value={...newReward(),kind:'REGISTER_REWARD',provider:'Walgreens',form:'STORE_COUPON',status:'CREDITED'}
    const offer:RewardOffer={id:'rr-offer',kind:'REGISTER_REWARD',provider:'Walgreens',title:'Buy two earn RR',amount:'5',form:'STORE_COUPON',requirement:{type:'BUY',amount:'2',category:null,spendBasis:'SALE_BEFORE_COUPONS'},sourceReceiptId:'old',sourceReceiptNumber:'OLD',sourceDate:'2026-10-01'}
    render(<RewardFields value={value} onChange={change} lines={[]} offers={[offer]}/>)
    expect(screen.getByLabelText('Reward status 1')).toHaveValue('PAID')
    await user.selectOptions(screen.getByLabelText('Copy terms from a previous receipt'),'rr-offer')
    expect(change).toHaveBeenLastCalledWith(expect.objectContaining({kind:'REGISTER_REWARD',status:'PAID',amount:'5'}))
  })

  it('accepts multiple dropped receipt evidence files without uploading or saving them',async()=>{
    const {mutate}=setup()
    fireEvent.drop(screen.getByRole('group',{name:'Receipt evidence drop area'}),{dataTransfer:{files:[new File(['a'],'page-one.png',{type:'image/png'}),new File(['b'],'page-two.pdf',{type:'application/pdf'})]}})
    expect(await screen.findByText('page-one.png')).toBeInTheDocument();expect(screen.getByText('page-two.pdf')).toBeInTheDocument()
    expect(mutate).not.toHaveBeenCalled()
  })
  it('uses the receipt import preview for dropped files and never saves a receipt',async()=>{
    const {mutate}=setup()
    mutate.mockResolvedValue({sources:[{name:'receipt.pdf',rawText:'Synthetic',scanned:false,store:'Test store',number:'DROP',day:'2026-10-29',time:'',tax:'0',reported:'2',footer:{},wcEarned:'',warnings:[],items:[{id:'drop-line',description:'Soap',sku:'',regular:'2',sale:'',quantity:'1'}],deductions:[],payments:[]}],duplicates:[]})
    fireEvent.drop(screen.getByRole('group',{name:'Receipt import drop area'}),{dataTransfer:{files:[new File(['pdf'],'receipt.pdf',{type:'application/pdf'})]}})
    expect(await screen.findByRole('heading',{name:'Import Review'})).toBeInTheDocument()
    expect(screen.getByLabelText('Product description 1')).toHaveValue('Soap')
    expect(mutate).toHaveBeenCalledTimes(1);expect(mutate.mock.calls[0][0]).toBe('/receipts/import-preview')
  })
  it('blocks receipt drops for a read-only form and retains file-type validation',async()=>{
    const {mutate}=setup(false)
    const file=new File(['pdf'],'receipt.pdf',{type:'application/pdf'})
    fireEvent.drop(screen.getByRole('group',{name:'Receipt import drop area'}),{dataTransfer:{files:[file]}})
    fireEvent.drop(screen.getByRole('group',{name:'Receipt evidence drop area'}),{dataTransfer:{files:[file]}})
    expect(mutate).not.toHaveBeenCalled();expect(screen.queryByText('receipt.pdf')).not.toBeInTheDocument()
  })

  it('shows Java product totals separately from receipt-wide discounts',async()=>{
    const {user,mutate}=setup();await minimum(user)
    mutate.mockImplementation(async(path,_method,body)=>{
      const input=body as {scenario:{items:{id:string}[]}}
      return path==='/shopping-plans/scenario/preview'?{items:[{id:input.scenario.items[0].id,totalPrice:'18.73',itemOopBeforeTaxAndFunding:'13.73'}]}:result()
    })
    await user.click(screen.getByRole('button',{name:'Calculate product totals'}))
    expect(await screen.findByText('$18.73')).toBeInTheDocument();expect(screen.getByText('$13.73')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Regular price 1'),'1')
    expect(screen.queryByText('$13.73')).not.toBeInTheDocument()
  })

  it('reviews receipt sale buttons through Java and records the reduction once',async()=>{
    const {user,mutate}=setup();await minimum(user)
    await user.clear(screen.getByLabelText('Quantity 1'));await user.type(screen.getByLabelText('Quantity 1'),'2')
    mutate.mockImplementation(async(path,_method,body)=>{
      if(path==='/shopping-plans/scenario/preview'){
        const input=body as {scenario:{items:{id:string}[]}}
        return {items:[{id:input.scenario.items[0].id,saleReduction:'2.00000000000000000001'}]}
      }
      return path==='/receipts/preview'?result():{}
    })
    await user.click(screen.getByRole('button',{name:'BOGO free'}))
    await user.click(screen.getByRole('button',{name:'Review calculated totals'}))
    await screen.findByRole('dialog',{name:'Review new receipt'})
    const scenario=mutate.mock.calls.find(call=>call[0]==='/shopping-plans/scenario/preview')![2] as {scenario:{items:{priceRule:{type:string};sale:string;quantity:number}[]}}
    expect(scenario.scenario.items[0]).toMatchObject({priceRule:{type:'BOGO_FREE'},sale:'2.00000000000000000001',quantity:2})
    const receipt=mutate.mock.calls.find(call=>call[0]==='/receipts/preview')![2] as {items:{unitPrice:string}[];discounts:{amount:string;kind:string}[]}
    expect(receipt.items[0].unitPrice).toBe('2.00000000000000000001')
    expect(receipt.discounts).toHaveLength(1);expect(receipt.discounts[0]).toMatchObject({kind:'SALE_PROMOTION',amount:'2.00000000000000000001'})
    await user.click(screen.getByRole('button',{name:'Save receipt'}))
    expect(mutate.mock.calls.filter(call=>call[0]==='/shopping-plans/scenario/preview')).toHaveLength(1)
    expect((mutate.mock.calls.find(call=>call[0]==='/receipts')![2] as {discounts:unknown[]}).discounts).toEqual(receipt.discounts)
  })
  it('clears a receipt sale rule when returning to sale-price entry',async()=>{
    const {user,mutate}=setup();await minimum(user)
    await user.click(screen.getByRole('button',{name:'2 for $___'}))
    await user.type(screen.getByLabelText('Price for two — Soap'),'3')
    await user.click(screen.getByRole('button',{name:/^Sale price$/}))
    expect(screen.queryByLabelText('Price for two — Soap')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button',{name:'Review calculated totals'}))
    await screen.findByRole('dialog',{name:'Review new receipt'})
    expect(mutate.mock.calls.some(call=>call[0]==='/shopping-plans/scenario/preview')).toBe(false)
  })

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
it('sends reviewed total basis without asking or inventing payment completeness', async () => {
  const { user, mutate } = setup(); await minimum(user)
  await user.selectOptions(screen.getByLabelText('Printed total represents'), 'BEFORE_PAYMENT_FUNDING')
  expect(screen.queryByLabelText('Have you entered all payments shown on this receipt?')).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Review calculated totals' }))
  expect(mutate.mock.calls[0][2]).toMatchObject({ printedTotalBasis: 'BEFORE_PAYMENT_FUNDING', paymentEntryStatus: 'UNKNOWN' })
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


it('shows total before coupons and hides only the requested known-zero rows',()=>{
 const totals:Totals={...result().totals,regularMerchandise:'30.53',saleMerchandise:'30.53',merchandiseBeforeCoupons:'19.65',coupons:'10.88',rrUsed:'10.00',wcUsed:'0.000',discountBreakdown:{salePromotions:'10.88',manufacturerCoupons:'0',storeDiscounts:'0.00',unclassified:'-0.000'}}
 totals.paymentReview={printedTotal:'9.62',calculatedPurchaseTotal:null,purchaseDifference:null,knownFundingSubtotal:'9.62',recordedCashCardPaid:'9.62',giftCardFunding:'0.00',otherFunding:'0',difference:null,status:'CANNOT_VERIFY',printedTotalBasis:'UNKNOWN',paymentEntryStatus:'UNKNOWN',unresolvedPaymentIds:[],reason:'Basis unknown',acknowledgementRequired:false}
 render(<ReceiptFinancialReview totals={totals}/>)
 expect(screen.getByText('Total before coupons').parentElement).toHaveTextContent('$19.65')
 for(const label of ['Manufacturer coupons','Store discounts / coupons','Unclassified discounts','Walgreens Cash used','Recorded gift-card funding'])expect(screen.queryByText(label)).not.toBeInTheDocument()
 expect(screen.getByText('Register Rewards used').parentElement).toHaveTextContent('$10.00')
 expect(screen.queryByText('Total listed discounts (sum above)')).not.toBeInTheDocument()
 const rows=screen.getByText('Regular merchandise').closest('.key-values')!
 expect(Array.from(rows.querySelectorAll(':scope > div > span')).map(row=>row.textContent)).toEqual(['Regular merchandise','Merchandise before listed discounts','Sale/promotion reductions','Sales tax','Total before coupons','Register Rewards used','Recorded printed total (basis unknown)'])
})
it('retains nonzero and unknown optional receipt amounts',()=>{
 const totals={...result().totals,wcUsed:'0.000001',discountBreakdown:{salePromotions:'0',manufacturerCoupons:'2.50',storeDiscounts:null,unclassified:'0'}}
 render(<ReceiptFinancialReview totals={totals}/>)
 expect(screen.getByText('Manufacturer coupons').parentElement).toHaveTextContent('$2.50')
 expect(screen.getByText('Store discounts / coupons')).toBeInTheDocument()
 expect(screen.getByText('Walgreens Cash used')).toBeInTheDocument()
 expect(screen.getByText('Recorded gift-card funding')).toBeInTheDocument()
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
  await user.click(screen.getByRole('button', { name: 'Delete' }))
  await user.type(screen.getByLabelText('Reason for correction'), 'Remove accidental discount')
  await user.click(screen.getByRole('button', { name: 'Review updated totals' }))
  expect(mutate.mock.calls[0][2]).toMatchObject({ discounts: [{ applicationId: 'old-price-reduction' }] })
})
it('omits a wholly blank receipt-wide discount while saving printed zero and unknown values distinctly', async () => {
  const api = new ApiClient('test'), user = userEvent.setup()
  const mutate = vi.spyOn(api, 'mutate').mockResolvedValue({ reviewToken: 'reviewed', totals: receiptRow('test').totals })
  render(<ReceiptEdit receipt={receipt()} api={api} enabled onSaved={vi.fn()} onCancel={vi.fn()} />)
  await user.click(screen.getByRole('button', { name: 'Add receipt-wide discount' }))
  await user.type(screen.getByLabelText('Used This Visit'), '0')
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
 expect(await screen.findByRole('columnheader',{name:'Dollar Value'})).toBeInTheDocument()
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
  const { user, mutate } = setup(); await minimum(user);await user.clear(screen.getByLabelText('Store'));await user.type(screen.getByLabelText('Store'),'Walgreens')
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
 const time=screen.getByLabelText('Reward Time');expect(time).not.toBeRequired();expect((time as HTMLInputElement).value).toMatch(/^\d{2}:\d{2}$/);await user.clear(time);await user.type(time,'08:45')
 await user.type(screen.getByLabelText('Member reward amount'),'.98')
 await user.type(screen.getByLabelText('Member reward name / description'),'Member bonus')
 await user.type(screen.getByLabelText('ECB expiration date (optional)'),'2026-11-24')
 await user.click(screen.getByRole('button',{name:'Save member reward'}))
 expect(mutate).toHaveBeenCalledWith('/accounts/member-rewards','POST',expect.objectContaining({provider:'ECB',amount:'.98',description:'Member bonus',date:'2026-10-24',time:'08:45',expirationDate:'2026-11-24'}),expect.any(String))
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

function planningFixture():import('../../types/platform').PlanReceiptDraft {
 const scenario:import('../../types/platform').PlanScenario={items:[{id:'neut',name:'Neutrogena',sku:'07050111090',regular:'7.79',sale:'5.99',quantity:'2'},{id:'lysol',name:'Lysol',sku:null,regular:'3.29',sale:null,quantity:'2'}],coupons:[{id:'coupon',description:'Neutrogena $4 off 2',amount:'4',requiredQuantity:'2',lineIds:['neut'],savedCouponId:null}],promotions:[{id:'promo',description:'Buy 2 get 7 RR',rewardType:'REGISTER_REWARD',rewardAmount:'7',requiredQuantity:'2',requiredSpend:'0',spendBasis:'BEFORE_COUPONS',allowsWalgreensCash:true,lineIds:['neut']}],tax:'1.04',walgreensCashUsed:'10',registerRewardsUsed:'2',giftCardUsed:'0',manualOop:null,paymentMethod:'CASH',fundingNotes:'Palmolive RR earned September'}
 return {plan:{planId:'plan',name:'Walgreens deal',retailer:'Walgreens',shoppingDate:'2026-10-24',plannedPurchaseIds:[],revision:'3',scenario},estimate:{items:[{id:'neut',name:'Neutrogena',totalPrice:'11.98',saleReduction:'3.60',couponsApplied:'4',itemOopBeforeTaxAndFunding:'7.98',possibleRewards:'7'},{id:'lysol',name:'Lysol',totalPrice:'6.58',saleReduction:'0',couponsApplied:'0',itemOopBeforeTaxAndFunding:'6.58',possibleRewards:'0'}],coupons:[{id:'coupon',description:'Neutrogena $4 off 2',quantityMet:true,applied:true,amount:'4',reason:'Quantity met'}],promotions:[{id:'promo',description:'Buy 2 get 7 RR',quantityMet:true,spendMet:true,walgreensCashAllowed:true,eligible:true,qualifyingSpend:'11.98',potentialReward:'7'}],regularTotal:'22.16',saleTotal:'18.56',couponTotal:'4',merchandiseAfterCoupons:'14.56',tax:'1.04',registerRewardsUsed:'2',walgreensCashUsed:'10',giftCardUsed:'0',receiptTotalBeforeFunding:'13.60',calculatedOop:'3.60',selectedOop:'3.60',manualDifference:'0',overFunding:'0',potentialRewards:'7',afterPotentialRewards:'-3.40'}}
}
it('previews and saves an editable planning scenario without redeeming rewards',async()=>{
 const draft=planningFixture(),api=new ApiClient('test'),saved=vi.fn(),receipt=vi.fn(),user=userEvent.setup()
 const mutate=vi.spyOn(api,'mutate').mockImplementation(async(path,_method,body)=>path.endsWith('/preview')?draft.estimate:{...(body as object),revision:'4'})
 render(<PlanScenarioEditor plan={draft.plan} data={workspace()} api={api} writable onClose={vi.fn()} onChanged={saved} onReceipt={receipt}/>)
 await screen.findByRole('heading',{name:'Planning breakdown'})
 expect(screen.getByLabelText('Payment or reward 1')).toHaveValue('WALGREENS_CASH');expect(screen.getByLabelText('Payment amount 1')).toHaveValue('10')
 await user.click(screen.getByRole('button',{name:'Save scenario'}))
 expect(mutate).toHaveBeenCalledWith('/shopping-plans/plan','PUT',expect.objectContaining({scenario:expect.objectContaining({items:expect.arrayContaining([expect.objectContaining({quantity:2})]),registerRewardsUsed:'2'})}),expect.any(String),'3')
 expect(saved).toHaveBeenCalled();expect(mutate.mock.calls.some(c=>String(c[0]).includes('/accounts/'))).toBe(false)
 await user.click(screen.getByRole('button',{name:'Start receipt draft'}));expect(receipt).toHaveBeenCalledWith(expect.objectContaining({estimate:draft.estimate}))
})
it('copies a plan into an unsaved receipt with sale reductions once and expected rewards',async()=>{
 const draft=planningFixture(),api=new ApiClient('test'),mutate=vi.spyOn(api,'mutate').mockResolvedValue(result()),user=userEvent.setup()
 render(<ReceiptCreate planning={draft} api={api} enabled onClose={vi.fn()} onSaved={vi.fn()} onReview={vi.fn()}/>)
 expect(screen.getByLabelText('Store')).toHaveValue('Walgreens');expect(screen.getByLabelText('Payment amount 2')).toHaveValue('3.60')
 expect(screen.getByLabelText('Product 1 discount 1 amount')).toHaveValue('3.60')
 await user.click(screen.getByRole('button',{name:'Review calculated totals'}))
 const body=mutate.mock.calls[0][2] as {discounts:{amount:string}[];earnings:{status:string}[];rrUses:{amount:string}[];reportedTotal:string}
 expect(body.reportedTotal).toBe('13.60');expect(body.discounts.map(d=>d.amount)).toEqual(['3.60','4']);expect(body.earnings[0].status).toBe('PAID');expect(body.rrUses[0].amount).toBe('2')
 expect(mutate.mock.calls).toHaveLength(1)
})

it('confirms permanent plan deletion and sends the displayed revision',async()=>{
 const data=workspace(),row=planningRow();data.plans=[row];const api=new ApiClient('test'),changed=vi.fn(),user=userEvent.setup(),mutate=vi.spyOn(api,'mutate').mockResolvedValue({deleted:true}),confirm=vi.spyOn(window,'confirm').mockReturnValue(false)
 render(<WorkspacePages page="shopping" data={data} api={api} writable onChanged={changed} onPage={vi.fn()} onReceipt={vi.fn()} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/>)
 await user.click(screen.getByRole('button',{name:'Delete plan'}));expect(mutate).not.toHaveBeenCalled()
 confirm.mockReturnValue(true);await user.click(screen.getByRole('button',{name:'Delete plan'}))
 await waitFor(()=>expect(changed).toHaveBeenCalled())
 expect(mutate).toHaveBeenCalledWith(`/shopping-plans/${row.plan.planId}`,'DELETE',{},expect.any(String),row.plan.revision)
 expect(confirm).toHaveBeenCalledWith(expect.stringContaining('cannot be undone'))
})

it('transfers automatic scenario tax and Fetch points without converting points into a dollar earning',async()=>{
 const draft=planningFixture();draft.plan.scenario!.tax='0';draft.plan.scenario!.taxRate='8.25';draft.estimate.tax='1.65';draft.plan.scenario!.promotions[0].rewardType='FETCH';draft.plan.scenario!.promotions[0].rewardAmount='10000'
 const api=new ApiClient('test'),mutate=vi.spyOn(api,'mutate').mockResolvedValue(result()),user=userEvent.setup()
 render(<ReceiptCreate planning={draft} api={api} enabled onClose={vi.fn()} onSaved={vi.fn()} onReview={vi.fn()}/>)
 expect(screen.getByLabelText('Sales tax')).toHaveValue('1.65')
 await user.click(screen.getByRole('button',{name:'Review calculated totals'}))
 const body=mutate.mock.calls[0][2] as {earnings:{provider:string;benefit:{amount:string;unit:string}}[]}
 expect(body.earnings[0].provider).toBe('Fetch')
 expect(JSON.stringify(body.earnings[0])).toContain('10000')
})

it('provides an automatically attached blank coupon and excludes it from preview and save',async()=>{
 const api=new ApiClient('test'),draft=planningFixture(),user=userEvent.setup(),mutate=vi.spyOn(api,'mutate').mockImplementation(async(path,_method,body)=>path.endsWith('/preview')?draft.estimate:{...(body as object),revision:'1'})
 render(<PlanScenarioEditor plan={null} data={workspace()} api={api} writable onClose={vi.fn()} onChanged={vi.fn()} onReceipt={vi.fn()}/>)
 expect(await screen.findByLabelText('Coupon description 1')).toHaveValue('');expect(screen.getByLabelText('Coupon amount 1')).not.toBeRequired()
 await user.type(screen.getByLabelText('Plan name'),'Blank coupon');await user.type(screen.getByLabelText('Product 1',{exact:true}),'Olay');await user.type(screen.getByLabelText('Regular price 1'),'13.99')
 await screen.findByRole('heading',{name:'Planning breakdown'});await user.click(screen.getByRole('button',{name:'Save scenario'}))
 const saved=mutate.mock.calls.find(c=>c[0]==='/shopping-plans')![2] as {scenario:{coupons:unknown[];payments:unknown[]}}
 expect(saved.scenario.coupons).toEqual([]);expect(saved.scenario.payments).toEqual([])
 expect(screen.getByRole('button',{name:'Add another coupon for product 1'})).toBeVisible()
 expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
})
it('copies each planned payment and ECB description without replacing split cash and credit',()=>{
 const draft=planningFixture();draft.plan.scenario!.payments=[{id:'rr',type:'REGISTER_REWARD',amount:'2',notes:'Palmolive'},{id:'ecb',type:'ECB',amount:'5',notes:'Scott ECB'},{id:'cash',type:'CASH',amount:'1',notes:'Cash'},{id:'card',type:'CARD',amount:'2.60',notes:'Credit'}]
 render(<ReceiptCreate planning={draft} api={new ApiClient('test')} enabled onClose={vi.fn()} onSaved={vi.fn()} onReview={vi.fn()}/>)
 expect(screen.getByDisplayValue('Scott ECB')).toBeInTheDocument();expect(screen.getByDisplayValue('Palmolive')).toBeInTheDocument();expect(screen.getByDisplayValue('2.60')).toBeInTheDocument()
})

it('adds whole-receipt and product store-promotion discounts with distinct scopes',async()=>{
 const draft=planningFixture(),api=new ApiClient('test'),user=userEvent.setup(),mutate=vi.spyOn(api,'mutate').mockImplementation(async(path,_method,body)=>path.endsWith('/preview')?draft.estimate:{...(body as object),revision:'4'})
 render(<PlanScenarioEditor plan={draft.plan} data={workspace()} api={api} writable onClose={vi.fn()} onChanged={vi.fn()} onReceipt={vi.fn()}/>)
 await user.click(screen.getByRole('button',{name:'Add store promotion discount for product 1'}))
 const types=screen.getAllByLabelText(/Coupon type/);expect(types[1]).toHaveValue('SALE_PROMOTION')
 await user.click(screen.getByRole('button',{name:'Add whole-receipt coupon or promotion'}))
 const descriptions=screen.getAllByLabelText(/Coupon description/),amounts=screen.getAllByLabelText(/Coupon amount/)
 await user.type(descriptions[descriptions.length-1],'ONLINE5');await user.type(amounts[amounts.length-1],'5')
 await screen.findByRole('heading',{name:'Planning breakdown'});await user.click(screen.getByRole('button',{name:'Save scenario'}))
 const body=mutate.mock.calls.find(c=>c[0]==='/shopping-plans/plan')![2] as {scenario:{coupons:{description:string;wholeReceipt?:boolean;lineIds:string[]}[]}}
 expect(body.scenario.coupons.find(c=>c.description==='ONLINE5')).toMatchObject({wholeReceipt:true,lineIds:[]})
})
it('copies a whole-receipt promotion as one receipt-wide discount',async()=>{
 const draft=planningFixture();draft.plan.scenario!.coupons[0].wholeReceipt=true;draft.plan.scenario!.coupons[0].kind='SALE_PROMOTION';draft.plan.scenario!.coupons[0].lineIds=[]
 const api=new ApiClient('test'),mutate=vi.spyOn(api,'mutate').mockResolvedValue(result()),user=userEvent.setup()
 render(<ReceiptCreate planning={draft} api={api} enabled onClose={vi.fn()} onSaved={vi.fn()} onReview={vi.fn()}/>)
 await user.click(screen.getByRole('button',{name:'Review calculated totals'}))
 const body=mutate.mock.calls[0][2] as {discounts:{wholeReceipt:boolean;kind:string;amount:string}[]}
 expect(body.discounts).toEqual(expect.arrayContaining([expect.objectContaining({wholeReceipt:true,kind:'SALE_PROMOTION',amount:'4'})]))
})

it('shows the calculated total minus coupons under each planned product',async()=>{
 const draft=planningFixture(),api=new ApiClient('test');vi.spyOn(api,'mutate').mockResolvedValue(draft.estimate)
 render(<PlanScenarioEditor plan={draft.plan} data={workspace()} api={api} writable onClose={vi.fn()} onReceipt={vi.fn()}/>)
 await screen.findByRole('heading',{name:'Planning breakdown'})
 const totals=screen.getAllByText(/Total minus coupons:/)
 await waitFor(()=>expect(totals[0]).toHaveTextContent('$7.98'));await waitFor(()=>expect(totals[1]).toHaveTextContent('$6.58'))
})

it('updates a product subtotal independently of unfinished products, tax and rebates',async()=>{
 const draft=planningFixture(),api=new ApiClient('test'),mutate=vi.spyOn(api,'mutate').mockResolvedValue({items:[{id:draft.plan.scenario!.items[0].id,itemOopBeforeTaxAndFunding:'6.99'}]})
 const {rerender}=render(<ProductCouponSubtotal line={draft.plan.scenario!.items[0]} coupons={[draft.plan.scenario!.coupons[0]]} api={api}/>)
 await screen.findByText('$6.99')
 const body=mutate.mock.calls[0][2] as {scenario:{items:unknown[];promotions:unknown[];tax:string}}
 expect(body.scenario.items).toHaveLength(1);expect(body.scenario.promotions).toEqual([]);expect(body.scenario.tax).toBe('0')
 mutate.mockResolvedValue({items:[{id:draft.plan.scenario!.items[0].id,itemOopBeforeTaxAndFunding:'5.99'}]})
 rerender(<ProductCouponSubtotal line={draft.plan.scenario!.items[0]} coupons={[{...draft.plan.scenario!.coupons[0],amount:'5'}]} api={api}/>)
 expect(screen.queryByText('$6.99')).not.toBeInTheDocument();await screen.findByText('$5.99')
})

it('offers whole-receipt rebate scope and preserves it in a receipt draft',async()=>{
 const draft=planningFixture();draft.plan.scenario!.promotions[0].wholeReceipt=true;draft.plan.scenario!.promotions[0].lineIds=[]
 const api=new ApiClient('test'),mutate=vi.spyOn(api,'mutate').mockResolvedValue(result()),user=userEvent.setup()
 render(<ReceiptCreate planning={draft} api={api} enabled onClose={vi.fn()} onSaved={vi.fn()} onReview={vi.fn()}/>)
 await user.click(screen.getByRole('button',{name:'Review calculated totals'}))
 expect((mutate.mock.calls[0][2] as {earnings:{lineItemIds:string[]}[]}).earnings[0].lineItemIds).toEqual([])
})
it('lets a planned rebate switch between whole receipt and selected products',async()=>{
 const draft=planningFixture(),api=new ApiClient('test'),user=userEvent.setup();vi.spyOn(api,'mutate').mockResolvedValue(draft.estimate)
 render(<PlanScenarioEditor plan={draft.plan} data={workspace()} api={api} writable onClose={vi.fn()} onReceipt={vi.fn()}/>)
 const scope=screen.getByLabelText('Rebate applies to 1');expect(scope).toHaveValue('PRODUCTS');expect(screen.getByRole('checkbox',{name:/Neutrogena/})).toBeVisible()
 await user.selectOptions(scope,'WHOLE_RECEIPT');expect(scope).toHaveValue('WHOLE_RECEIPT');expect(screen.queryByRole('checkbox',{name:/Neutrogena/})).not.toBeInTheDocument()
 await user.selectOptions(scope,'PRODUCTS');expect(scope).toHaveValue('PRODUCTS')
})

it('labels checkout OOP after tax before subtracting earned rewards',()=>{
 const draft=planningFixture();render(<ScenarioBreakdown report={draft.estimate}/>)
 expect(screen.getByText('OOP After Rewards, After Tax').parentElement).toHaveTextContent('$3.60')
})

it('keeps copied product coupons under the product instead of suggesting another entry',()=>{
 const draft=planningFixture()
 render(<ReceiptCreate planning={draft} api={new ApiClient('test')} enabled onClose={vi.fn()} onSaved={vi.fn()} onReview={vi.fn()}/>)
 expect(screen.getByLabelText('Product 1 discount 2 amount')).toHaveValue('4')
 expect(screen.getByLabelText('Product 1 discount 2 description')).toHaveValue('Neutrogena $4 off 2')
 expect(screen.queryByLabelText('Discount label 1')).not.toBeInTheDocument()
 expect(screen.getByText(/Those discounts are already entered below/)).toBeInTheDocument()
})

it('includes the saved plan revision in receipt review for atomic purchased marking',async()=>{
 const draft=planningFixture(),api=new ApiClient('test'),mutate=vi.spyOn(api,'mutate').mockResolvedValue(result()),user=userEvent.setup()
 render(<ReceiptCreate planning={draft} api={api} enabled onClose={vi.fn()} onSaved={vi.fn()} onReview={vi.fn()}/>)
 await user.click(screen.getByRole('button',{name:'Review calculated totals'}))
 expect(mutate.mock.calls[0][2]).toMatchObject({shoppingPlanId:draft.plan.planId,shoppingPlanRevision:draft.plan.revision})
})
it('shows purchased status and opens its saved receipt without enabling checkout again',async()=>{
 const data=workspace(),row=planningRow();row.plan.purchasedReceiptId='saved-receipt';data.plans=[row];const open=vi.fn(),user=userEvent.setup()
 render(<WorkspacePages page="shopping" data={data} api={new ApiClient('test')} writable onPage={vi.fn()} onReceipt={open} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/>)
 expect(screen.getByText('Purchased')).toBeVisible();expect(screen.queryByRole('button',{name:/^Checkout$/})).not.toBeInTheDocument()
 await user.click(screen.getByRole('button',{name:'View receipt'}));expect(open).toHaveBeenCalledWith('saved-receipt')
})

it('transfers Walmart Cash as an expected store reward in dollars',async()=>{
 const draft=planningFixture();draft.plan.scenario!.promotions[0].rewardType='WALMART_CASH';const api=new ApiClient('test'),mutate=vi.spyOn(api,'mutate').mockResolvedValue(result()),user=userEvent.setup()
 render(<ReceiptCreate planning={draft} api={api} enabled onClose={vi.fn()} onSaved={vi.fn()} onReview={vi.fn()}/>)
 await user.click(screen.getByRole('button',{name:'Review calculated totals'}))
 expect((mutate.mock.calls[0][2] as {earnings:unknown[]}).earnings[0]).toMatchObject({provider:'Walmart Cash',form:'STORE_REWARD',status:'EXPECTED',amount:'7'})
})

it('shows only the retailer-appropriate reward-payment permission',async()=>{
 const draft=planningFixture(),api=new ApiClient('test'),user=userEvent.setup();vi.spyOn(api,'mutate').mockResolvedValue(draft.estimate)
 render(<PlanScenarioEditor plan={draft.plan} data={workspace()} api={api} writable onClose={vi.fn()} onReceipt={vi.fn()}/>)
 expect(screen.getByLabelText('This rebate permits paying with Walgreens Cash')).toBeInTheDocument();expect(screen.queryByLabelText('This rebate permits paying with Walmart Cash')).not.toBeInTheDocument()
 await user.clear(screen.getByLabelText('Store',{exact:true}));await user.type(screen.getByLabelText('Store',{exact:true}),'Walmart')
 expect(screen.queryByLabelText('This rebate permits paying with Walgreens Cash')).not.toBeInTheDocument();expect(screen.getByLabelText('This rebate permits paying with Walmart Cash')).toBeInTheDocument()
 await user.clear(screen.getByLabelText('Store',{exact:true}));await user.type(screen.getByLabelText('Store',{exact:true}),'CVS')
 expect(screen.queryByLabelText('This rebate permits paying with Walgreens Cash')).not.toBeInTheDocument();expect(screen.queryByLabelText('This rebate permits paying with Walmart Cash')).not.toBeInTheDocument()
})
it('transfers Walmart Cash used as funding rather than cash paid',()=>{
 const draft=planningFixture();draft.plan.scenario!.payments=[{id:'walmart',type:'WALMART_CASH',amount:'3',notes:'Walmart balance'}]
 render(<ReceiptCreate planning={draft} api={new ApiClient('test')} enabled onClose={vi.fn()} onSaved={vi.fn()} onReview={vi.fn()}/>)
 expect(screen.getByDisplayValue('Walmart balance')).toBeInTheDocument();expect(screen.getByLabelText('Other payment role')).toHaveValue('FUNDING')
})

it('normalizes a no-coupon Walmart deal with optional rebate thresholds and unused rows',()=>{
 const scenario=planningFixture().plan.scenario!
 scenario.coupons=[{id:'empty',description:'',amount:'',kind:'MANUFACTURER',requiredQuantity:'',lineIds:['neut'],savedCouponId:null},{id:'zero',description:'',amount:'0.00',kind:'MANUFACTURER',requiredQuantity:'0',lineIds:['neut'],savedCouponId:null}]
 scenario.promotions=[...scenario.promotions.map(p=>({...p,requiredQuantity:'',requiredSpend:''})),{...scenario.promotions[0],id:'unused',description:'',rewardAmount:''}]
 const body=scenarioRequest(scenario)
 expect(body.coupons).toEqual([]);expect(body.promotions).toHaveLength(1);expect(body.promotions[0]).toMatchObject({requiredQuantity:0,requiredSpend:'0'})
 expect(()=>scenarioRequest({...scenario,promotions:[{...scenario.promotions[0],description:'Ibotta',rewardAmount:''}]})).toThrow('Rebate 1 amount: enter a valid amount.')
})
it('allows blank coupon quantity fields and unused rebate slots when saving a no-coupon deal',async()=>{
 const draft=planningFixture();draft.plan.scenario!.coupons=[];draft.plan.scenario!.promotions=[]
 const api=new ApiClient('test'),user=userEvent.setup(),mutate=vi.spyOn(api,'mutate').mockImplementation(async(path,_method,body)=>path.endsWith('/preview')?draft.estimate:{...(body as object),revision:'4'})
 render(<PlanScenarioEditor plan={draft.plan} data={workspace()} api={api} writable onClose={vi.fn()} onReceipt={vi.fn()}/>)
 await user.click(screen.getByRole('button',{name:'Add another coupon for product 1'}));await user.clear(screen.getByLabelText('Required coupon quantity 1'));await user.click(screen.getByRole('button',{name:'Add another rebate'}))
 await screen.findByRole('heading',{name:'Planning breakdown'});await user.click(screen.getByRole('button',{name:'Save scenario'}))
 expect(mutate.mock.calls.find(c=>c[0]==='/shopping-plans/plan')?.[2]).toMatchObject({scenario:{coupons:[],promotions:[]}})
})

it('shows RR used only on Walgreens receipts and accepts a separate sale price',async()=>{
 const {user,mutate}=setup();await minimum(user)
 expect(screen.queryByRole('button',{name:'Add RR used'})).not.toBeInTheDocument()
 await user.type(screen.getByLabelText('Sale price 1 (optional)'),'1.25')
 await user.click(screen.getByRole('button',{name:'Review calculated totals'}))
 expect((mutate.mock.calls[0][2] as {items:{unitPrice:string}[]}).items[0].unitPrice).toBe('1.25')
})

it('defaults a new receipt to the local date/time and allows clearing its date',async()=>{
 vi.useFakeTimers();vi.setSystemTime(new Date(2026,9,25,14,7));const {user}=setup();vi.useRealTimers()
 expect(screen.getByLabelText('Purchase date')).toHaveValue('2026-10-25');expect(screen.getByLabelText('Purchase time (optional)')).toHaveValue('14:07')
 expect(screen.queryByRole('button',{name:'Clear date'})).not.toBeInTheDocument();await user.clear(screen.getByLabelText('Purchase date'));expect(screen.getByLabelText('Purchase date')).toHaveValue('')
})
it('ignores unused product discount amounts even with an automatically filled description',async()=>{
 const {user,mutate}=setup();await minimum(user);await user.click(screen.getByRole('button',{name:'Add discount to product 1'}))
 expect(screen.getByLabelText('Product 1 discount 1 amount')).not.toBeRequired()
 await user.click(screen.getByRole('button',{name:'Review calculated totals'}));expect((mutate.mock.calls[0][2] as {discounts:unknown[]}).discounts).toEqual([])
 await user.click(screen.getByRole('button',{name:'Back to edit'}));await user.type(screen.getByLabelText('Product 1 discount 1 amount'),'0')
 await user.click(screen.getByRole('button',{name:'Review calculated totals'}));expect((mutate.mock.calls[1][2] as {discounts:unknown[]}).discounts).toEqual([])
})
it('highlights and focuses an invalid discount amount instead of sending it to the API',async()=>{
 const {user,mutate}=setup();await minimum(user);await user.click(screen.getByRole('button',{name:'Add discount to product 1'}));const field=screen.getByLabelText('Product 1 discount 1 amount')
 await user.type(field,'-1');await user.click(screen.getByRole('button',{name:'Review calculated totals'}))
 expect(field).toHaveAttribute('aria-invalid','true');expect(field).toHaveFocus();expect(screen.getByRole('alert')).toHaveTextContent('Product 1 discount 1 amount');expect(mutate).not.toHaveBeenCalled()
})
it('moves focus to the visible error when the server rejects an otherwise valid form',async()=>{
 const {user,mutate}=setup();await minimum(user);mutate.mockRejectedValue(new ApiError(400,'Applied amount must be greater than zero.'))
 await user.click(screen.getByRole('button',{name:'Review calculated totals'}));const error=await screen.findByRole('alert');expect(error).toHaveTextContent('Applied amount must be greater than zero.');await waitFor(()=>expect(error).toHaveFocus())
})

it('imports receipt-backed inventory and saves confirmed stock with an optional blank location',async()=>{
 const data=workspace();data.inventory=[{inventoryId:'stock',productId:'product',revision:'2',productName:'Olay',sku:'00123',totalPurchased:'5',quantityOnHand:null,quantityConfirmed:false,location:null,sources:[{receiptId:'receipt',lineItemId:'line',revision:'1',quantity:'5',active:true,productName:'Olay',store:'Walgreens',receiptNumber:'R1',date:'2026-10-24'}]}]
 const api=new ApiClient('test'),mutate=vi.spyOn(api,'mutate').mockResolvedValue({saved:true}),changed=vi.fn(),user=userEvent.setup()
 render(<WorkspacePages page="inventory" data={data} api={api} writable onChanged={changed} onPage={vi.fn()} onReceipt={vi.fn()} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/>)
 expect(screen.getByText('00123')).toBeInTheDocument();expect(screen.queryByRole('columnheader',{name:'Quantity on hand'})).not.toBeInTheDocument();expect(screen.getByText('Needs cost review')).toBeInTheDocument()
 await user.click(screen.getByRole('button',{name:'View / update'}));expect(screen.getByLabelText('Location (optional)')).not.toBeRequired();await user.selectOptions(screen.getByLabelText('Inventory action'),'SET');await user.type(screen.getByLabelText('Quantity on hand'),'3');await user.click(screen.getByRole('button',{name:'Save inventory'}))
 expect(mutate).toHaveBeenCalledWith('/inventory/stock','PUT',expect.objectContaining({action:'SET',quantity:3,location:null}),expect.any(String),'2');expect(changed).toHaveBeenCalled()
})

it('collapses inventory sections without losing filters or stock form inputs',async()=>{
 const data=workspace();data.inventory=[{inventoryId:'stock',productId:'product',revision:'2',productName:'Olay',sku:'00123',totalPurchased:'5',quantityOnHand:null}];const user=userEvent.setup()
 render(<WorkspacePages page="inventory" data={data} api={new ApiClient('test')} writable onPage={vi.fn()} onReceipt={vi.fn()} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/>)
 const search=screen.getByLabelText('Search products');await user.type(search,'Olay')
 await user.click(screen.getByRole('button',{name:'Collapse Inventory filters'}));expect(search).not.toBeVisible();await user.click(screen.getByRole('button',{name:'Expand Inventory filters'}));expect(search).toHaveValue('Olay')
 await user.click(screen.getByRole('button',{name:'View / update'}));await user.selectOptions(screen.getByLabelText('Inventory action'),'SET');const quantity=screen.getByLabelText('Quantity on hand');await user.type(quantity,'3')
 await user.click(screen.getByText('Update stock',{exact:true}));expect(quantity).not.toBeVisible();await user.click(screen.getByText('Update stock',{exact:true}));expect(quantity).toHaveValue('3')
 expect(screen.getByText('Product summary')).toBeInTheDocument();expect(screen.getByText('Purchase history')).toBeInTheDocument();expect(screen.getByText('Stock history')).toBeInTheDocument()
})

it('saves and restores an incomplete receipt draft without reviewing or posting a purchase',async()=>{
 const api=new ApiClient('test'),mutate=vi.spyOn(api,'mutate').mockResolvedValue({revision:'1'}),user=userEvent.setup(),changed=vi.fn()
 const first=render(<ReceiptCreate api={api} enabled onClose={vi.fn()} onSaved={vi.fn()} onReview={vi.fn()} onDraftSaved={changed}/>)
 await user.type(screen.getByLabelText('Store'),'Walmart');await user.type(screen.getByLabelText('Product description 1'),'Unfinished product');await user.click(screen.getByRole('button',{name:'Save Draft'}))
 const call=mutate.mock.calls[0],state=(call[2] as {state:ReceiptDraftState}).state
 expect(call[0]).toMatch(/^\/receipt-drafts\//);expect(call[4]).toBe('0');expect(mutate).toHaveBeenCalledTimes(1);expect(changed).toHaveBeenCalled();expect(state.lines[0].regular).toBe('')
 first.unmount();render(<ReceiptCreate savedDraft={{state,revision:'1'}} api={api} enabled onClose={vi.fn()} onSaved={vi.fn()} onReview={vi.fn()}/>)
 expect(screen.getByLabelText('Store')).toHaveValue('Walmart');expect(screen.getByLabelText('Product description 1')).toHaveValue('Unfinished Product')
})
it('shows saved receipt drafts separately and resumes through the draft action',async()=>{
 const data=workspace();data.receiptDrafts=[{draftId:'draft',revision:'1',store:'Walmart',receiptNumber:null,updatedAt:'2026-10-25T15:00:00Z'}];const resume=vi.fn(),user=userEvent.setup()
 render(<WorkspacePages data={data} page="receipts" api={new ApiClient('test')} writable onResumeReceiptDraft={resume} onPage={vi.fn()} onReceipt={vi.fn()} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/>)
 await user.click(screen.getByRole('button',{name:'Resume draft'}));expect(resume).toHaveBeenCalledWith('draft')
})

it('requires a new count when switching inventory from packs to individual items',async()=>{
 const data=workspace();data.inventory=[{inventoryId:'stock',productId:'product',revision:'3',productName:'Bic Razors',sku:'070330714170',quantityOnHand:'1',totalPurchased:'1',unitsPerPackage:'4',unitName:'Razors',countUnit:'PACKAGE',individualPurchased:'4',individualOnHand:'4'}];const api=new ApiClient('test'),mutate=vi.spyOn(api,'mutate').mockResolvedValue({saved:true}),user=userEvent.setup()
 render(<WorkspacePages data={data} page="inventory" api={api} writable onPage={vi.fn()} onReceipt={vi.fn()} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/>)
 await user.click(screen.getByRole('button',{name:'View / update'}));await user.selectOptions(screen.getByLabelText('Track stock as'),'ITEM');expect(screen.getByLabelText('Quantity on hand')).toHaveValue('')
 await user.type(screen.getByLabelText('Quantity on hand'),'4');await user.click(screen.getByRole('button',{name:'Save inventory'}));expect(mutate.mock.calls[0][2]).toMatchObject({quantity:4,unitsPerPackage:4,unitName:'Razors',countUnit:'ITEM'})
})


it('sorts inventory headings in both directions using numeric quantities',async()=>{
 const data=workspace();data.inventory=[{inventoryId:'a',productId:'a',revision:'1',productName:'Alpha',sku:'002',totalPurchased:'10',quantityOnHand:'2',location:'Pantry',netCost:'10.00'},{inventoryId:'b',productId:'b',revision:'1',productName:'Beta',sku:'001',totalPurchased:'2',quantityOnHand:'10',location:'Bathroom',netCost:'2.00'}];const user=userEvent.setup()
 render(<WorkspacePages page="inventory" data={data} writable onPage={vi.fn()} onReceipt={vi.fn()} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/>)
 const first=()=>screen.getAllByRole('row')[1].textContent
 await user.click(screen.getByRole('button',{name:'Quantity purchased'}));expect(first()).toContain('Beta')
 await user.click(screen.getByRole('button',{name:'Quantity purchased'}));expect(first()).toContain('Alpha')
 expect(screen.getByRole('columnheader',{name:/Quantity purchased/})).toHaveAttribute('aria-sort','descending')
 await user.click(screen.getByRole('button',{name:'SKU / UPC'}));expect(first()).toContain('Beta')
 await user.click(screen.getByRole('button',{name:'Cost after discounts'}));expect(first()).toContain('Beta')
 await user.click(screen.getByRole('button',{name:'Cost after discounts'}));expect(first()).toContain('Alpha')
 expect(screen.queryByRole('columnheader',{name:'Location'})).not.toBeInTheDocument()
})


it('previews the latest five inventory additions and expands with an arrow',async()=>{
 const data=workspace();data.inventory=Array.from({length:7},(_,i)=>({inventoryId:`stock-${i}`,productId:`p-${i}`,revision:'1',productName:`Item ${i}`,quantityOnHand:'1',addedAt:`2026-10-${String(i+10).padStart(2,'0')}T12:00:00Z`}))
 const user=userEvent.setup();render(<WorkspacePages page="inventory" data={data} writable onPage={vi.fn()} onReceipt={vi.fn()} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/>)
 expect(screen.getAllByRole('row')).toHaveLength(6);expect(screen.getAllByRole('row')[1]).toHaveTextContent('Item 6');expect(screen.queryByText('Item 0')).not.toBeInTheDocument()
 const arrow=screen.getByRole('button',{name:'Show All Inventory Items'});expect(arrow).toHaveTextContent('');await user.click(arrow);expect(screen.getAllByRole('row')).toHaveLength(8)
 await user.click(screen.getByRole('button',{name:'Show Latest Five Inventory Items'}));expect(screen.getAllByRole('row')).toHaveLength(6)
})
it('shows a recurring Sunday receipt and rebate review on the dashboard',async()=>{
 const dateSpy=vi.spyOn(Date.prototype,'toLocaleDateString').mockReturnValue('2026-10-25');const data=workspace();data.asOf='2026-10-26';const onPage=vi.fn(),user=userEvent.setup()
 render(<WorkspacePages page="dashboard" data={data} writable onPage={onPage} onReceipt={vi.fn()} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/>)
 expect(screen.getByText('Sunday Receipt & Rebate Review')).toBeInTheDocument();expect(screen.getByText('Oct 19, 2026 – Oct 25, 2026 · Past Seven Days')).toBeInTheDocument()
 await user.click(screen.getByRole('button',{name:'Check Receipts'}));expect(onPage).toHaveBeenCalledWith('receipts');await user.click(screen.getByRole('button',{name:'Check Rebates'}));expect(onPage).toHaveBeenCalledWith('deals');dateSpy.mockRestore()
})


it('adds a P&G bonus as points without requiring a manual dollar conversion',async()=>{
 const api=new ApiClient('test'),saved=vi.fn(),mutate=vi.spyOn(api,'mutate').mockResolvedValue({}),user=userEvent.setup()
 render(<MemberRewardEntry provider="P&G Brand" api={api} writable day="2026-10-26" onSaved={saved}/>)
 await user.click(screen.getByRole('button',{name:'Add member reward without a receipt'}));expect(screen.queryByLabelText('Member reward amount')).not.toBeInTheDocument()
 await user.type(screen.getByLabelText('Bonus Reward Points'),'25');await user.type(screen.getByLabelText('Member reward name / description'),'Survey Bonus');await user.click(screen.getByRole('button',{name:'Save member reward'}))
 expect(mutate).toHaveBeenCalledWith('/accounts/member-rewards','POST',expect.objectContaining({provider:'P&G Brand',points:'25',amount:null,description:'Survey Bonus'}),expect.any(String));expect(saved).toHaveBeenCalled()
})
it('previews five account entries, expands, searches by day and sorts dollar values numerically',async()=>{
 const data=workspace(),api=new ApiClient('test'),user=userEvent.setup();data.asOf=new Date().toLocaleDateString('sv-SE');const report=pointsReport('P&G Brand',data.asOf)
 report.activity=Array.from({length:7},(_,i)=>({...report.activity[0],description:`Bonus ${i}`,movement:{...report.activity[0].movement,time:['00:00','12:00','18:40','21:36','08:00','10:00','13:00'][i],date:`${data.asOf.slice(0,7)}-${String(i+1).padStart(2,'0')}`,usd:String(i===0?100:i+1)}}))
 data.balances=[{provider:'P&G Brand',balance:report.ending}];vi.spyOn(api,'account').mockResolvedValue(report);vi.spyOn(api,'workspaceAt').mockResolvedValue(data)
 render(<RewardsAccounts data={data} api={api} writable onReceipt={vi.fn()}/>);await user.click(await screen.findByRole('button',{name:'P&G Brand'}));await screen.findByText('Bonus 6')
 expect(screen.queryByText('Bonus 0')).not.toBeInTheDocument()
 const showAll=screen.getByRole('button',{name:'Show All Account Activity'});expect(showAll).toHaveTextContent('Show all');expect(showAll).toHaveAttribute('aria-expanded','false')
 await user.click(showAll);expect(screen.getByText('Bonus 0')).toBeInTheDocument();expect(screen.getByText(/Showing 7 Of 7 Matching Entries/)).toBeInTheDocument()
 expect(screen.getByRole('cell',{name:'12:00 AM'})).toBeInTheDocument();expect(screen.getByRole('cell',{name:'12:00 PM'})).toBeInTheDocument();expect(screen.getByRole('cell',{name:'6:40 PM'})).toBeInTheDocument();expect(screen.getByRole('cell',{name:'9:36 PM'})).toBeInTheDocument()
 const latest=screen.getByRole('button',{name:'Show Latest Five Account Activity'});expect(latest).toHaveTextContent('Show latest 5');expect(latest).toHaveAttribute('aria-expanded','true')
 await user.click(latest);expect(screen.queryByText('Bonus 0')).not.toBeInTheDocument()
 await user.click(screen.getByRole('button',{name:'Show All Account Activity'}))
 await user.click(screen.getByRole('button',{name:/^Total$/}));expect(screen.getByRole('columnheader',{name:'Total ↑'})).toHaveAttribute('aria-sort','ascending')
 await user.type(screen.getByLabelText('Search Account By Day'),`${data.asOf.slice(0,7)}-01`);expect(screen.getByText('Bonus 0')).toBeInTheDocument();expect(screen.queryByText('Bonus 6')).not.toBeInTheDocument()
})


it('hides the Sunday reminder on Monday even when the workspace date is Sunday',()=>{
 const dateSpy=vi.spyOn(Date.prototype,'toLocaleDateString').mockReturnValue('2026-10-26');const data=workspace();data.asOf='2026-10-25'
 render(<WorkspacePages page="dashboard" data={data} writable onPage={vi.fn()} onReceipt={vi.fn()} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/>)
 expect(screen.queryByText('Sunday Receipt & Rebate Review')).not.toBeInTheDocument();dateSpy.mockRestore()
})
it('records a payout separately with optional gift-card details',async()=>{
 const api=new ApiClient('test'),saved=vi.fn(),mutate=vi.spyOn(api,'mutate').mockResolvedValue({}),user=userEvent.setup()
 render(<PayoutEntry provider="Fetch" pointsAccount api={api} writable day="2026-10-26" onSaved={saved}/>)
 await user.click(screen.getByRole('button',{name:'Record Payout / Use Rewards'}));expect(screen.getByLabelText('Description (Optional)')).not.toBeRequired()
 await user.type(screen.getByLabelText('Points Used (Optional)'),'5000');await user.selectOptions(screen.getByLabelText('How Paid (Optional)'),'GIFT_CARD');expect(screen.getByLabelText('Gift Card Number (Optional)')).not.toBeRequired()
 await user.type(screen.getByLabelText('Gift Card Brand (Optional)'),'Target');await user.click(screen.getByRole('button',{name:'Save Payout'}))
 expect(mutate).toHaveBeenCalledWith('/accounts/payouts','POST',expect.objectContaining({provider:'Fetch',points:'5000',amount:null,destination:'GIFT_CARD',brand:'Target',number:null,status:'REQUESTED'}),expect.any(String));expect(saved).toHaveBeenCalled()
})


it('focuses Rebates on follow-up and keeps all reward details accessible from Rewards',async()=>{
 const data=workspace();data.receiptEarnings=Array.from({length:7},(_,i)=>({receiptId:`r${i}`,receiptNumber:`R${i}`,receiptStatus:'ACTIVE',earning:{earningId:`e${i}`,kind:'THIRD_PARTY_REBATE',provider:'Ibotta',offerReference:`Offer ${i}`,scope:'RECEIPT',lineItemIds:[],status:'CREDITED',expected:null,confirmed:{kind:'CASH',amount:'1',currency:'USD',program:null},valuation:null}})) as Workspace['receiptEarnings']
 const {unmount}=render(<WorkspacePages page="rebates" data={data} writable onPage={vi.fn()} onReceipt={vi.fn()} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/>)
 expect(screen.queryByText(/Ibotta · Offer/)).not.toBeInTheDocument();await userEvent.setup().selectOptions(screen.getByLabelText('Submission Filter'),'ALL');expect(screen.getAllByText(/Ibotta · Offer/)).toHaveLength(5);await userEvent.setup().click(screen.getByRole('button',{name:'Show All Rebates'}));expect(screen.getAllByText(/Ibotta · Offer/)).toHaveLength(7);expect(screen.queryByText('Add Missing Rewards')).not.toBeInTheDocument();unmount()
 const api=new ApiClient('test');vi.spyOn(api,'workspaceAt').mockResolvedValue(data);const user=userEvent.setup()
 render(<RewardsAccounts data={data} api={api} writable onReceipt={vi.fn()}/>);await user.click(screen.getByRole('button',{name:'Add Missing Rewards'}));expect(screen.getByRole('heading',{name:'Add Missing Rewards'})).toBeInTheDocument();expect(screen.queryByRole('button',{name:'Add member reward without a receipt'})).not.toBeInTheDocument()
})


it('opens the Sunday receipt review with only the last seven days selected',async()=>{
 const dateSpy=vi.spyOn(Date.prototype,'toLocaleDateString').mockReturnValue('2026-10-25'),user=userEvent.setup(),data=workspace(),api=new ApiClient('test');vi.spyOn(api,'receiptSummary').mockResolvedValue(data.summaries.all)
 const props={data,api,writable:true,onReceipt:vi.fn(),onEdit:vi.fn(),onAddReceipt:vi.fn(),onCheckout:vi.fn()};let view:ReturnType<typeof render>
 const navigate=(page:'receipts'|'rebates'|'dashboard')=>view.rerender(<WorkspacePages {...props} page={page} onPage={p=>navigate(p as 'receipts'|'rebates'|'dashboard')}/>)
 view=render(<WorkspacePages {...props} page="dashboard" onPage={p=>navigate(p as 'receipts'|'rebates'|'dashboard')}/>);await user.click(screen.getByRole('button',{name:'Check Receipts'}));await waitFor(()=>expect(api.receiptSummary).toHaveBeenCalledWith('2026-10-19','2026-10-25',expect.anything()));dateSpy.mockRestore()
})


it('shows every reward account in the preferred order and resets sorting when reopened',async()=>{
 const data=workspace(),user=userEvent.setup();const names=['Rakuten','Fetch','Walgreens Cash','Ibotta','Register Rewards','ECB','Walmart Cash','Checkout 51','Venmo','P&G Brand','Swagbucks','MyPoints','Coupons DotCom','PayPal','Gift Cards','Returns','Target','Merryfield','Makeena','Shopmium'];const balance={monthUsd:'0',startUsd:'0',endUsd:'0',startPoints:null,endPoints:null}
 data.balances=names.map(provider=>({provider,balance}));render(<RewardsAccounts data={data} writable onReceipt={vi.fn()}/> )
 const accountRows=()=>within(document.querySelector('.account-balances-panel') as HTMLElement).getAllByRole('row').slice(1).map(row=>within(row).getByRole('button').textContent)
 expect(accountRows()).toEqual(['Walgreens Cash','Register Rewards','ECB','Walmart Cash','Checkout 51','Ibotta','Venmo','Fetch','P&G Brand','Swagbucks','MyPoints','Coupons DotCom','PayPal','Gift Cards','Returns','Target','Merryfield','Makeena','Shopmium','Rakuten'])
 await user.click(screen.getByRole('button',{name:/^Account$/}));expect(accountRows()[0]).toBe('Checkout 51')
 await user.click(screen.getByRole('button',{name:/^Fetch$/}));await user.click(screen.getByRole('button',{name:'← All Accounts'}));expect(accountRows()[0]).toBe('Walgreens Cash');expect(accountRows()).toHaveLength(20)
})


it('keeps checkout funding visible when coupon details collapse and hides unused detail rows',async()=>{
 const draft=planningFixture(),user=userEvent.setup();draft.estimate.regularTotal=draft.estimate.saleTotal;draft.estimate.totalWithTax='15.60';draft.estimate.promotions=[];draft.estimate.potentialRewards='0';draft.estimate.afterPotentialRewards='3.60';draft.estimate.afterCashRebates='3.60'
 render(<ScenarioBreakdown report={draft.estimate} scenario={draft.plan.scenario!} onEdit={vi.fn()}/>)
 expect(screen.getByText('Regular And Sale Prices Match')).toBeInTheDocument();expect(screen.queryByText('Sale Merchandise')).not.toBeInTheDocument()
 expect(screen.getByRole('columnheader',{name:'Quantity'})).toBeInTheDocument()
 await user.click(screen.getByRole('button',{name:'Collapse Coupons Applied'}));expect(screen.queryByText('Neutrogena $4 off 2')).not.toBeInTheDocument();expect(screen.getByText(/Register Rewards Used:.*2.00.*Walgreens Cash Used:.*10.00/)).toBeInTheDocument()
 expect(screen.getByText('No Rebates.')).toBeInTheDocument();expect(screen.queryByText('Gift Cards Used')).not.toBeInTheDocument();expect(screen.queryByText('Manual / Calculated Difference')).not.toBeInTheDocument();expect(screen.queryByText('Net Before Tax')).not.toBeInTheDocument()
 await user.click(screen.getByRole('button',{name:'Expand Coupons Applied'}));expect(screen.getByText('Neutrogena $4 off 2')).toBeInTheDocument()
})

it('retains nested shopping inputs while section arrows collapse independently',async()=>{
 const draft=planningFixture(),api=new ApiClient('test'),user=userEvent.setup();vi.spyOn(api,'mutate').mockResolvedValue(draft.estimate)
 render(<PlanScenarioEditor plan={draft.plan} data={workspace()} api={api} writable onClose={vi.fn()} onReceipt={vi.fn()}/>)
 const payment=screen.getByText('Payment Or Rewards').closest('summary')!,section=payment.parentElement!
 await user.click(payment);expect(section).not.toHaveAttribute('open');await user.click(payment);expect(section).toHaveAttribute('open')
 await screen.findByText('Adjust Eligibility / Rates');await user.click(screen.getByText('Adjust Eligibility / Rates'))
 expect(screen.getByLabelText('Member Reward Rate 1')).toHaveValue('1')
 await user.selectOptions(screen.getByLabelText('Member Reward Rate 1'),'5');expect(await screen.findByLabelText('Member Reward Rate 1')).toHaveValue('5')
})

it.each([['0','15.12',false],['5','15.12',true],['20.12','0',false]])('shows rebate categories only for a mixed cash/store reward total (%s / %s)',(cash,other,mixed)=>{
 const draft=planningFixture();draft.estimate.cashRebates=cash;draft.estimate.otherRebates=other;draft.estimate.potentialRewards=mixed?'20.12':cash==='0'?other:cash
 render(<ScenarioBreakdown report={draft.estimate}/>)
 expect(screen.getAllByText('Total Rebates')).toHaveLength(1)
 expect(!!screen.queryByText('Cash Rebates')).toBe(mixed);expect(!!screen.queryByText('Store Rewards & Points')).toBe(mixed)
 expect(screen.queryByText('Other Rebates')).not.toBeInTheDocument()
})

it('closes the scenario only after a successful save and keeps server errors visible',async()=>{
 const draft=planningFixture(),api=new ApiClient('test'),close=vi.fn(),user=userEvent.setup()
 const mutate=vi.spyOn(api,'mutate').mockImplementation(async(path)=>{if(path.endsWith('/preview'))return draft.estimate;throw new ApiError(400,'Shopping deal could not be saved.')})
 render(<PlanScenarioEditor plan={draft.plan} data={workspace()} api={api} writable onClose={close} onReceipt={vi.fn()}/>)
 await screen.findByRole('heading',{name:'Planning breakdown'});await user.click(screen.getByRole('button',{name:'Save scenario'}))
 expect(screen.getByRole('alert')).toHaveTextContent('Shopping deal could not be saved.');expect(close).not.toHaveBeenCalled()
 mutate.mockImplementation(async(path)=>path.endsWith('/preview')?draft.estimate:{...draft.plan,revision:'4'})
 await user.click(screen.getByRole('button',{name:'Save scenario'}));expect(close).toHaveBeenCalledOnce()
})

it('shows whole-receipt reward amounts separately and suppresses zero points and zero payment differences',()=>{
 const draft=planningFixture();draft.plan.scenario!.promotions[0].wholeReceipt=true;draft.estimate.receiptLevelRewards='15.12';draft.estimate.promotions[0].points='0';draft.estimate.manualDifference='0.00'
 render(<ScenarioBreakdown report={draft.estimate} scenario={draft.plan.scenario!}/>)
 expect(screen.getByRole('row',{name:/Whole-Receipt Rewards.*15.12/})).toBeInTheDocument();expect(screen.queryByText(/0 Points/)).not.toBeInTheDocument();expect(screen.queryByText('Manual / Calculated Difference')).not.toBeInTheDocument()
 expect(screen.getByText(/Applies To: Whole Receipt/)).toBeInTheDocument()
})

it('imports into editable fields without saving a receipt and lets the owner classify an RR once',async()=>{
 const api=new ApiClient('test'),user=userEvent.setup(),id='import-line'
 const mutate=vi.spyOn(api,'mutate').mockResolvedValue({sources:[{name:'receipt.pdf',rawText:'Synthetic Walgreens receipt',scanned:false,store:'Walgreens',storeReference:'Store 1',number:'RFN-TEST',day:'2026-10-08',time:'9:34 AM',tax:'0.27',reported:'3.50',footer:{},wcEarned:'',warnings:['Review regular price'],items:[{id,description:'Aquaphor',sku:'000123',regular:'13.49',sale:'',quantity:'1'}],deductions:[{id:'import-coupon',label:'MFG CPN',amount:'10.00',kind:'UNCLASSIFIED'}],payments:[]}],duplicates:[]})
 render(<ReceiptCreate api={api} enabled onClose={vi.fn()} onSaved={vi.fn()} onReview={vi.fn()}/>)
 await user.upload(screen.getByLabelText('Import Receipt Files'),new File(['pdf'],'receipt.pdf',{type:'application/pdf'}))
 expect(await screen.findByRole('heading',{name:'Import Review'})).toBeInTheDocument();expect(screen.getByLabelText('Product description 1')).toHaveValue('Aquaphor');expect(screen.getByLabelText('SKU / UPC 1 (optional)')).toHaveValue('000123')
 const classification=screen.getByLabelText('Classify Imported Deduction import-coupon')
 expect(classification).toHaveAttribute('aria-invalid','true');expect(classification.closest('label')).toHaveClass('import-deduction-needs-review')
 await user.click(screen.getByRole('button',{name:'Review entered breakdown'}))
 expect(screen.getByRole('alert')).toHaveClass('receipt-blocking-error');expect(screen.getByRole('alert')).toHaveTextContent('Action required')
 expect(classification).toHaveFocus();expect(mutate).toHaveBeenCalledTimes(1)
 await user.selectOptions(screen.getByLabelText('Classify Imported Deduction import-coupon'),'REGISTER_REWARD')
 expect(classification).not.toHaveAttribute('aria-invalid');expect(classification.closest('label')).not.toHaveClass('import-deduction-needs-review');expect(screen.queryByRole('alert')).not.toBeInTheDocument()
 expect(screen.getByLabelText('RR used amount 1')).toHaveValue('10.00');expect(mutate).toHaveBeenCalledTimes(1);expect(mutate.mock.calls[0][0]).toBe('/receipts/import-preview')
 await user.clear(screen.getByLabelText('Regular price 1'));await user.type(screen.getByLabelText('Regular price 1'),'14.00');expect(screen.getByLabelText('Regular price 1')).toHaveValue('14.00')
})

it('copies a completed shopping plan into a new editable scenario without editing the source',async()=>{
 const draft=planningFixture(),data=workspace(),api=new ApiClient('test'),user=userEvent.setup();draft.plan.purchasedReceiptId='saved-receipt';const row=planningRow();row.plan=draft.plan;row.scenarioEstimate=draft.estimate;data.plans=[row];const mutate=vi.spyOn(api,'mutate').mockImplementation(async(path)=>path.endsWith('/preview')?draft.estimate:{...draft.plan,planId:'new-plan',purchasedReceiptId:null,revision:'1'})
 render(<WorkspacePages page="shopping" data={data} api={api} writable onPage={vi.fn()} onReceipt={vi.fn()} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/>)
 expect(screen.getByText('Completed Shopping Plans')).toBeInTheDocument();expect(screen.getByText('Purchased')).toBeInTheDocument()
 await user.click(screen.getByRole('button',{name:'Copy Plan'}));expect(screen.getByLabelText('Plan name')).toHaveValue('Walgreens deal Copy')
 await screen.findByRole('heading',{name:'Planning breakdown'});await user.click(screen.getByRole('button',{name:'Save scenario'}))
 expect(mutate).toHaveBeenCalledWith('/shopping-plans','POST',expect.objectContaining({name:'Walgreens deal Copy'}),expect.any(String),undefined)
 expect(draft.plan.purchasedReceiptId).toBe('saved-receipt')
})

it('fills a Walgreens purchase member estimate once and keeps the amount editable',async()=>{
 const user=userEvent.setup(),estimate=vi.fn().mockResolvedValue('0.60')
 function Example(){const [value,setValue]=useState({...newReward(),kind:'WALGREENS_CASH',provider:'Walgreens',form:'STORE_REWARD'});return <RewardFields retailer="Walgreens" value={value} onChange={setValue} lines={[]} onMemberEstimate={estimate}/>}
 render(<Example/>);await user.selectOptions(screen.getByLabelText('Walgreens Cash reward type 1'),'MEMBER')
 await waitFor(()=>expect(screen.getByLabelText('Reward amount 1 (USD)')).toHaveValue('0.60'))
 await user.clear(screen.getByLabelText('Reward amount 1 (USD)'));await user.type(screen.getByLabelText('Reward amount 1 (USD)'),'.75');expect(screen.getByLabelText('Reward amount 1 (USD)')).toHaveValue('.75');expect(estimate).toHaveBeenCalledOnce()
})

it('offers Cancel at the top of the receipt draft and closes after confirming discard',async()=>{
 const user=userEvent.setup(),close=vi.fn(),confirm=vi.spyOn(window,'confirm').mockReturnValue(true)
 render(<ReceiptCreate api={new ApiClient('test')} enabled onClose={close} onSaved={vi.fn()} onReview={vi.fn()}/>)
 await user.click(screen.getByRole('button',{name:/^Cancel$/}));expect(confirm).toHaveBeenCalled();expect(close).toHaveBeenCalledOnce();confirm.mockRestore()
})

it('previews a manager adjustment without placeholder products and sends its original reward link',async()=>{
 const api=new ApiClient('test'),user=userEvent.setup(),data=workspace();data.receipts=[{...receiptRow('source','2026-10-25'),transactionId:'source',metadata:{...receiptRow('source','2026-10-25').metadata!,receiptNumber:'11513219089526102503'}}];data.receiptEarnings=[{receiptId:'source',receiptNumber:'11513219089526102503',receiptStatus:'ACTIVE',earning:{earningId:'missing',kind:'WALGREENS_CASH',provider:'Walgreens',offerReference:'Spend 5 Get 15',scope:'RECEIPT',lineItemIds:[],status:'REJECTED',expected:{kind:'STORE_REWARD',amount:'15',currency:'USD',program:'Walgreens'},confirmed:null,valuation:null}}]
 const mutate=vi.spyOn(api,'mutate').mockResolvedValue(result());render(<ReceiptCreate workspace={data} api={api} enabled onClose={vi.fn()} onSaved={vi.fn()} onReview={vi.fn()}/>)
 await user.selectOptions(screen.getByLabelText('Receipt Type'),'REWARD_ADJUSTMENT');await user.type(screen.getByLabelText('Store'),'Walgreens');await user.selectOptions(screen.getByLabelText('Original Receipt'),'source');await user.selectOptions(screen.getByLabelText('Original Walgreens Cash Reward'),'missing');await user.type(screen.getByLabelText('Reward amount 1 (USD)'),'10')
 expect(screen.queryByLabelText('Product description 1')).not.toBeInTheDocument();await user.click(screen.getByRole('button',{name:'Review calculated totals'}))
 expect(mutate).toHaveBeenCalledWith('/receipts/preview','POST',expect.objectContaining({receiptKind:'REWARD_ADJUSTMENT',items:[],payments:[],tax:'0',reportedTotal:'0',earnings:expect.arrayContaining([expect.objectContaining({amount:'10',originalReceiptId:'source',originalEarningId:'missing'})])}),expect.any(String))
})

it('preserves an unknown regular price and transfers a grouped sale discount once into the receipt draft',async()=>{
 const draft=planningFixture(),s=draft.plan.scenario!,line=s.items[0]
 s.items=[{...line,regular:'',sale:'6.0000',quantity:'2',priceRule:{type:'BOGO_FREE',bundlePrice:null}}];s.coupons=[];s.promotions=[];s.tax='0';s.taxRate=null;s.payments=[]
 draft.estimate={...draft.estimate,items:[{id:line.id,name:line.name,totalPrice:'6.0000',saleReduction:'6.0000',couponsApplied:'0',itemOopBeforeTaxAndFunding:'6.0000',possibleRewards:'0'}],coupons:[],promotions:[],regularTotal:null,saleTotal:'6.0000',couponTotal:'0',merchandiseAfterCoupons:'6.0000',tax:'0',registerRewardsUsed:'0',walgreensCashUsed:'0',selectedOop:'6.0000',calculatedOop:'6.0000',potentialRewards:'0'}
 const api=new ApiClient('test'),mutate=vi.spyOn(api,'mutate').mockResolvedValue(result()),user=userEvent.setup()
 render(<ReceiptCreate planning={draft} api={api} enabled onClose={vi.fn()} onSaved={vi.fn()} onReview={vi.fn()}/>)
 await user.click(screen.getByRole('button',{name:'Review calculated totals'}))
 const body=mutate.mock.calls.find(c=>c[0]==='/receipts/preview')![2] as {items:{regularUnitPrice:string|null;unitPrice:string;quantity:number}[];discounts:{amount:string}[]}
 expect(body.items[0].regularUnitPrice).toBeNull();expect(body.items[0].unitPrice).toBe('6.0000');expect(body.items[0].quantity).toBe(2)
 expect(body.discounts).toHaveLength(1);expect(body.discounts[0].amount).toBe('6.0000')
})


it('removes a planned coupon without recreating it after edits or adding another product',async()=>{
 const api=new ApiClient('test'),draft=planningFixture(),user=userEvent.setup()
 vi.spyOn(api,'mutate').mockImplementation(async()=>draft.estimate)
 render(<PlanScenarioEditor plan={null} data={workspace()} api={api} writable onClose={vi.fn()} onReceipt={vi.fn()}/>)
 await user.click(screen.getByRole('button',{name:/^Remove coupon 1$/}))
 expect(screen.queryByLabelText('Coupon description 1')).not.toBeInTheDocument()
 await user.type(screen.getByLabelText('Product 1',{exact:true}),'A&H Laundry')
 await user.type(screen.getByLabelText('Regular price 1'),'8.99')
 expect(screen.queryByLabelText('Coupon description 1')).not.toBeInTheDocument()
 await user.click(screen.getByRole('button',{name:/^Add product$/}))
 expect(screen.getAllByLabelText(/Coupon description/)).toHaveLength(1)
 const firstProduct=screen.getByLabelText('Product 1',{exact:true}).closest('details')!
 expect(within(firstProduct).queryByLabelText(/Coupon description/)).not.toBeInTheDocument()
 await user.click(within(firstProduct).getByRole('button',{name:'Add another coupon for product 1'}))
 expect(within(firstProduct).getByLabelText(/Coupon description/)).toBeInTheDocument()
})
it('reopens a saved no-coupon scenario without inventing a blank coupon row',()=>{
 const api=new ApiClient('test'),draft=planningFixture();draft.plan.scenario!.coupons=[]
 vi.spyOn(api,'mutate').mockResolvedValue(draft.estimate)
 render(<PlanScenarioEditor plan={draft.plan} data={workspace()} api={api} writable onClose={vi.fn()} onReceipt={vi.fn()}/>)
 expect(screen.queryByLabelText(/Coupon description/)).not.toBeInTheDocument()
 expect(screen.getByRole('button',{name:'Add another coupon for product 1'})).toBeInTheDocument()
})


describe('rebate program receipt connections',()=>{
 function rebateWorkspace(){const data=workspace();data.receipts=[{...receiptRow('receipt-id'),date:'2026-10-19'}];data.promotions=[{id:'pg-rebate',name:'PG October Rebate',threshold:'75',giftCardValue:'15',start:'2026-10-01',end:'2026-10-31',terms:'Qualifying purchases after coupons',status:'TRACKING',contributions:{}}];return data}
 it('orders linked receipts oldest first and edits the captured revision instead of current prices',async()=>{
  const data=rebateWorkspace(),api=new ApiClient('test'),user=userEvent.setup()
  const captured=receipt(),current={...receipt(),revision:'4',contents:{...receipt().contents,purchaseItems:[{...receipt().contents.purchaseItems[0],saleUnitPrice:'99'}]}}
  data.receipts.push({...receiptRow('earlier'),date:'2026-10-04'})
  data.promotions[0].contributions={'receipt-id':{receiptId:'receipt-id',reviewedRevision:'3',reviewedAmount:'4.00',lineIds:['original-line'],note:'',token:'saved',receiptDate:'2026-10-19'},earlier:{receiptId:'earlier',reviewedRevision:'1',reviewedAmount:'2',note:'',receiptDate:'2026-10-04'}}
  vi.spyOn(api,'receipt').mockResolvedValue(current)
  vi.spyOn(api,'history').mockResolvedValue([{historyId:'audit',request:{requestedAt:''},result:{revision:'3',action:'SAVE',committedAt:''},changeCtx:null,createCtx:null,after:captured}])
  const mutate=vi.spyOn(api,'mutate').mockResolvedValue({})
  render(<WorkspacePages page="deals" data={data} api={api} writable onPage={vi.fn()} onReceipt={vi.fn()} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/>)
  await user.click(screen.getByText('PG October Rebate'))
  const edits=screen.getAllByRole('button',{name:'Edit receipt link / submission'})
  expect(edits[0].closest('section')).toHaveTextContent('Oct 4, 2026');expect(edits[1].closest('section')).toHaveTextContent('Oct 19, 2026')
  await user.click(edits[1]);await screen.findByLabelText(/Soap · Quantity 1 · Unit price \$5.96/)
  await user.click(screen.getByLabelText('This receipt has been submitted for this rebate'));await user.click(screen.getByRole('button',{name:'Save rebate receipt'}))
  expect(mutate.mock.calls[0][4]).toBe('3');expect(mutate.mock.calls[0][2]).toMatchObject({netAmount:'4.00',submitted:true})
 })
 it('searches eligible receipts by formatted number, store and date without clearing a selected receipt',async()=>{
  const data=rebateWorkspace(),api=new ApiClient('test'),user=userEvent.setup()
  data.receipts[0].metadata={...receipt().metadata!,retailer:'Walgreens',receiptNumber:'1151-3712-3339'}
  data.receipts.push({...receiptRow('other'),date:'2026-10-20',metadata:{...receipt().metadata!,retailer:'Walmart',receiptNumber:'OTHER-123'}})
  vi.spyOn(api,'receipt').mockResolvedValue(receipt())
  render(<WorkspacePages page="deals" data={data} api={api} writable onPage={vi.fn()} onReceipt={vi.fn()} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/>)
  await user.click(screen.getByText('PG October Rebate'));await user.click(screen.getByRole('button',{name:'Connect receipt'}))
  expect(screen.getByRole('dialog',{name:'Connect receipt to PG October Rebate'})).toBeVisible()
  const search=screen.getByLabelText('Search receipts'),select=screen.getByLabelText('Receipt for this rebate')
  await user.type(search,'115137123339');expect(within(select).getAllByRole('option')).toHaveLength(2)
  expect(within(select).queryByText(/OTHER-123/)).not.toBeInTheDocument()
  await user.clear(search);await user.type(search,'walmart');expect(within(select).getByText(/OTHER-123/)).toBeInTheDocument()
  await user.clear(search);await user.type(search,'10/19/2026');await user.selectOptions(select,'receipt-id')
  await screen.findByLabelText(/Soap · Quantity/)
  await user.clear(search);await user.type(search,'not-found');expect(select).toHaveValue('receipt-id');expect(screen.getByText(/No matching receipts/)).toBeVisible()
  await user.clear(search);expect(within(select).getAllByRole('option')).toHaveLength(3)
  await user.click(screen.getByRole('button',{name:'Close dialog'}));expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  expect(screen.getByRole('button',{name:'Connect receipt'})).toHaveFocus()
 })
 it('links selected receipt products and submission details without editing the receipt, and retries the identical request',async()=>{
  const data=rebateWorkspace(),api=new ApiClient('test'),user=userEvent.setup(),saved=vi.fn()
  vi.spyOn(api,'receipt').mockResolvedValue(receipt())
  const mutate=vi.spyOn(api,'mutate').mockRejectedValueOnce(new ApiError(0,'Outcome uncertain')).mockResolvedValueOnce({})
  render(<WorkspacePages page="deals" data={data} api={api} writable onChanged={saved} onPage={vi.fn()} onReceipt={vi.fn()} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/>)
  await user.click(screen.getByText('PG October Rebate'));await user.click(screen.getByRole('button',{name:'Connect receipt'}))
  await user.selectOptions(screen.getByLabelText('Receipt for this rebate'),'receipt-id')
  await user.click(await screen.findByLabelText(/Soap · Quantity/));await user.type(screen.getByLabelText('Eligible amount after coupons'),'4.00125')
  await user.click(screen.getByLabelText('This receipt has been submitted for this rebate'))
  fireEvent.change(screen.getByLabelText('Submission date (optional)'),{target:{value:'2026-10-20'}})
  await user.type(screen.getByLabelText('Submission reference (optional)'),'PG-123')
  await user.click(screen.getByRole('button',{name:'Save rebate receipt'}));expect(saved).not.toHaveBeenCalled()
  await user.click(screen.getByRole('button',{name:'Retry same receipt link'}))
  expect(mutate.mock.calls[0]).toEqual(['/rebate-programs/pg-rebate/receipts/receipt-id','PUT',{lineIds:['original-line'],netAmount:'4.00125',note:null,submitted:true,submittedOn:'2026-10-20',submissionReference:'PG-123',expectedContribution:'new'},expect.any(String),'3'])
  expect(mutate.mock.calls[1]).toEqual(mutate.mock.calls[0]);expect(saved).toHaveBeenCalledOnce()
 })
 it('edits the linked amount and can unlink with a safe retry while retaining the receipt',async()=>{
  const data=rebateWorkspace(),api=new ApiClient('test'),user=userEvent.setup(),saved=vi.fn()
  data.promotions[0].contributions={'receipt-id':{receiptId:'receipt-id',reviewedRevision:'3',lineIds:['original-line'],reviewedAmount:'4.00',note:'After coupon',afterCoupons:true,submitted:false,token:'saved',quantity:'1',saleTotal:'5.96',couponTotal:'1.96'}}
  vi.spyOn(api,'receipt').mockResolvedValue(receipt())
  const mutate=vi.spyOn(api,'mutate').mockResolvedValueOnce({}).mockRejectedValueOnce(new ApiError(0,'Outcome uncertain')).mockResolvedValueOnce({})
  render(<WorkspacePages page="deals" data={data} api={api} writable onChanged={saved} onPage={vi.fn()} onReceipt={vi.fn()} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/>)
  await user.click(screen.getByText('PG October Rebate'));await user.click(screen.getByRole('button',{name:'Edit receipt link / submission'}))
  const amount=await screen.findByLabelText('Eligible amount after coupons');expect(amount).toHaveValue('4.00')
  expect(screen.getByText('Subtract Mfg Ecpn Used Off Of Promotion Price')).toBeVisible()
  await user.clear(amount);await user.type(amount,'3.25');await user.click(screen.getByRole('button',{name:'Save rebate receipt'}))
  expect(mutate.mock.calls[0][2]).toMatchObject({netAmount:'3.25',expectedContribution:'saved'})
  await user.click(screen.getByRole('button',{name:'Unlink receipt'}));await user.click(screen.getByRole('button',{name:'Retry unlink receipt'}))
  expect(mutate.mock.calls[1]).toEqual(['/rebate-programs/pg-rebate/receipts/receipt-id','DELETE',{expectedContribution:'saved'},expect.any(String)])
  expect(mutate.mock.calls[2]).toEqual(mutate.mock.calls[1]);expect(saved).toHaveBeenCalledTimes(2)
  expect(data.receipts).toHaveLength(1)
 })
 it('shows individual submission status and receipt links and prevents read-only changes',async()=>{
  const data=rebateWorkspace(),open=vi.fn(),user=userEvent.setup()
  data.promotions[0].contributions={'receipt-id':{receiptId:'receipt-id',reviewedRevision:'3',lineIds:['original-line'],reviewedAmount:'4.00',note:'After coupon',afterCoupons:true,submitted:true,submittedOn:'2026-10-20',submissionReference:'PG-123',token:'saved'}}
  render(<WorkspacePages page="deals" data={data} api={new ApiClient('test')} writable={false} onPage={vi.fn()} onReceipt={open} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/>)
  await user.click(screen.getByText('PG October Rebate'));expect(screen.getAllByText('Submitted').some(element=>element.tagName==='STRONG')).toBe(true);expect(screen.getByText(/PG-123/)).toBeVisible()
  await user.click(screen.getByRole('button',{name:/Receipt /}));expect(open).toHaveBeenCalledWith('receipt-id')
  expect(screen.getByRole('button',{name:'Connect receipt'})).toBeDisabled();expect(screen.getByRole('button',{name:'Edit receipt link / submission'})).toBeDisabled()
 })
})


it('calculates one shared sale discount across differently priced products through Java',async()=>{
 const user=userEvent.setup(),api=new ApiClient('test'),calculated=vi.fn()
 const mutate=vi.spyOn(api,'mutate').mockResolvedValue({saleReductionTotal:'4.495'})
 render(<ReceiptDiscountSale api={api} items={[{id:'a',name:'Dove one',sku:null,regular:'',sale:'8.99',quantity:'1'},{id:'b',name:'Dove two',sku:null,regular:'',sale:'8.99',quantity:'1'}]} onCalculated={calculated}/>)
 await user.click(screen.getByRole('button',{name:'BOGO 50%'}));await waitFor(()=>expect(calculated).toHaveBeenCalledWith('4.495','BOGO 50%'))
 expect(mutate.mock.calls[0][2]).toMatchObject({scenario:{items:[{id:'a',priceRule:{type:'BOGO_HALF',lineIds:['a','b']}},{id:'b',priceRule:null}]}})
})
it('records a Target gift card as payment funding rather than a received reward',async()=>{
 const {user,mutate}=setup();await minimum(user)
 await user.clear(screen.getByLabelText('Store'));await user.type(screen.getByLabelText('Store'),'Target')
 await user.click(screen.getByRole('button',{name:'Add payment'}))
 expect(within(screen.getByLabelText('Payment type 1')).getAllByRole('option').map(o=>o.textContent)).toEqual(['Cash','Card','Gift card payment','Target gift card','Target Circle','Other tender'])
 await user.selectOptions(screen.getByLabelText('Payment type 1'),'TARGET_GIFT_CARD')
 expect(screen.getByLabelText('Gift card account 1 (optional)')).toHaveValue('Target Gift Card')
 await user.type(screen.getByLabelText('Payment amount 1'),'2')
 await user.click(screen.getByRole('button',{name:'Review calculated totals'}))
 expect(mutate.mock.calls.find(c=>c[0]==='/receipts/preview')?.[2]).toMatchObject({payments:[{type:'GIFT_CARD',accountName:'Target Gift Card',amount:'2'}],earnings:[]})
})

it('hides zero RR usage and shows zero cash/card for a fully gift-card-funded purchase',()=>{
 const totals:Totals={...result().totals,rrUsed:'0.00',paymentReview:{printedTotal:'8.10',calculatedPurchaseTotal:'8.11',purchaseDifference:'-0.01',knownFundingSubtotal:'8.10',recordedCashCardPaid:'0',giftCardFunding:'8.10',otherFunding:'0',difference:null,status:'INCOMPLETE',printedTotalBasis:'BEFORE_PAYMENT_FUNDING',paymentEntryStatus:'UNKNOWN',unresolvedPaymentIds:[],reason:'Review difference',acknowledgementRequired:true}}
 render(<ReceiptFinancialReview totals={totals}/>);expect(screen.queryByText('Register Rewards used')).not.toBeInTheDocument();expect(screen.getByText('Actual cash/card paid').parentElement).toHaveTextContent('$0.00');expect(screen.getByText('Printed purchase total').parentElement).toHaveTextContent('$8.10')
})

it('records Target Circle spending with its account and funding role',async()=>{
 const {user,mutate}=setup();await minimum(user);await user.clear(screen.getByLabelText('Store'));await user.type(screen.getByLabelText('Store'),'Target')
 await user.click(screen.getByRole('button',{name:'Add payment'}));await user.selectOptions(screen.getByLabelText('Payment type 1'),'TARGET_CIRCLE');await user.type(screen.getByLabelText('Payment amount 1'),'2')
 expect(screen.queryByText(/Record rewards used once in their own section/)).not.toBeInTheDocument()
 await user.click(screen.getByRole('button',{name:'Review calculated totals'}))
 expect(mutate.mock.calls.find(c=>c[0]==='/receipts/preview')?.[2]).toMatchObject({payments:[{type:'OTHER',accountName:'Target Circle',role:'FUNDING',amount:'2'}]})
})

it('names the spent Target Gift Card account instead of generic gift-card funding',()=>{
 const totals:Totals={...result().totals,giftCardPayments:{'Target Gift Card':'8.10'}}
 render(<ReceiptFinancialReview totals={totals}/>);expect(screen.getByText('Target Gift Card').parentElement).toHaveTextContent('$8.10');expect(screen.queryByText('Recorded gift-card funding')).not.toBeInTheDocument()
})

it('automatically refreshes valid product totals without saving a receipt',async()=>{
 const api=new ApiClient('test'),user=userEvent.setup(),mutate=vi.spyOn(api,'mutate').mockResolvedValue(result())
 const preview=vi.spyOn(api,'previewScenario').mockImplementation(async input=>{const scenario=input as {items:{id:string}[]};return {items:[{id:scenario.items[0].id,totalPrice:'8.99',itemOopBeforeTaxAndFunding:'4.99'}]} as unknown as import('../../types/platform').ScenarioEstimate})
 render(<ReceiptCreate api={api} enabled onClose={vi.fn()} onReview={vi.fn()} onSaved={vi.fn()}/>)
 await user.type(screen.getByLabelText('Product description 1'),'Dove');await user.type(screen.getByLabelText('Regular price 1'),'8.99')
 await waitFor(()=>expect(screen.getByText('$4.99')).toBeVisible(),{timeout:2000});expect(preview).toHaveBeenCalled();expect(mutate).not.toHaveBeenCalled()
 expect(screen.getByRole('button',{name:'Calculate product totals'})).toBeVisible()
})
it('offers working draft actions at the top and bottom and deletes only the saved draft',async()=>{
 const api=new ApiClient('test'),user=userEvent.setup(),close=vi.fn(),mutate=vi.spyOn(api,'mutate').mockResolvedValue({revision:'1'}),confirm=vi.spyOn(window,'confirm').mockReturnValue(true)
 render(<ReceiptCreate api={api} enabled onClose={close} onReview={vi.fn()} onSaved={vi.fn()}/>)
 expect(screen.getByRole('button',{name:'Top: Review calculated totals'})).toBeVisible();expect(screen.getByRole('button',{name:'Top: Cancel'})).toBeVisible()
 await user.type(screen.getByLabelText('Store'),'Unfinished');await user.click(screen.getByRole('button',{name:'Top: Save Draft'}))
 const path=mutate.mock.calls[0][0];expect(path).toMatch(/^\/receipt-drafts\//)
 await user.click(screen.getByRole('button',{name:'Delete'}));expect(mutate.mock.calls[1]).toEqual([path,'DELETE',{},expect.any(String),'1']);expect(close).toHaveBeenCalledOnce();confirm.mockRestore()
})

it('submits the original receipt form from its top Review action',async()=>{
 const {user,mutate}=setup();await minimum(user)
 await user.click(screen.getByRole('button',{name:'Top: Review calculated totals'}))
 expect(mutate.mock.calls.filter(c=>c[0]==='/receipts/preview')).toHaveLength(1)
 expect(screen.getByRole('dialog',{name:'Review new receipt'})).toBeVisible()
})

it('keeps channel and total basis together and expands/collapses individual or all products',async()=>{
 const {user}=setup();await user.type(screen.getByLabelText('Product description 1'),'Dove');await user.click(screen.getByRole('button',{name:'Add product'}));await user.type(screen.getByLabelText('Product description 2'),'Soap')
 expect(screen.getByLabelText('Order type').closest('.receipt-channel-basis')).toContainElement(screen.getByLabelText('Printed total represents'))
 const first=screen.getByLabelText('Product description 1').closest('details')!,second=screen.getByLabelText('Product description 2').closest('details')!
 await user.click(screen.getByRole('button',{name:'Collapse all products'}));expect(first).not.toHaveAttribute('open');expect(second).not.toHaveAttribute('open')
 await user.click(first.querySelector('summary')!);expect(first).toHaveAttribute('open');expect(second).not.toHaveAttribute('open')
 await user.click(screen.getByRole('button',{name:'Expand all products'}));expect(first).toHaveAttribute('open');expect(second).toHaveAttribute('open')
})
it('opens product-total information beside the total and Escape closes only that popup',async()=>{
 const {user}=setup();await user.click(screen.getByRole('button',{name:'About product totals'}))
 const popup=screen.getByRole('tooltip',{name:'Product totals'});expect(popup).toBeVisible();expect(within(popup).getByText(/Product coupons only/)).toBeVisible()
 await user.keyboard('{Escape}');expect(screen.queryByRole('tooltip',{name:'Product totals'})).not.toBeInTheDocument();expect(screen.getByRole('dialog',{name:'Add receipt'})).toBeVisible()
 await user.click(screen.getByRole('button',{name:'About product totals'}));await user.keyboard('{Escape}');expect(screen.queryByRole('tooltip',{name:'Product totals'})).not.toBeInTheDocument()
})

it('explains Walgreens Cash filters and balances in a closable information popup',async()=>{
 const data=workspace(),user=userEvent.setup();data.balances=[{provider:'Walgreens Cash',balance:{monthUsd:'10',startUsd:'10',endUsd:'12',startPoints:null,endPoints:null}}]
 render(<RewardsAccounts data={data} writable onReceipt={vi.fn()}/>)
 await user.click(screen.getByRole('button',{name:'Walgreens Cash'}));await user.click(screen.getByRole('button',{name:'About Dates & Reward Search'}))
 const popup=screen.getByRole('tooltip',{name:'Dates & Reward Search'});expect(popup).toBeVisible();expect(within(popup).getByText(/This narrows the activity list/)).toBeVisible();expect(within(popup).getByText(/Filters narrow the list, not the balances/)).toBeVisible()
 await user.keyboard('{Escape}');expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
})

it('explains RR receipt matching without double deductions in a closable popup',async()=>{
 const data=workspace(),api=new ApiClient('test'),user=userEvent.setup();data.asOf=new Date().toLocaleDateString('sv-SE');data.balances=[{provider:'Register Rewards',balance:{monthUsd:'10',startUsd:'10',endUsd:'5',startPoints:null,endPoints:null}}]
 vi.spyOn(api,'workspaceAt').mockResolvedValue(data);vi.spyOn(api,'account').mockResolvedValue(pointsReport('Register Rewards',data.asOf));vi.spyOn(api,'standaloneRewards').mockResolvedValue([])
 render(<RewardsAccounts data={data} api={api} writable onReceipt={vi.fn()}/>)
 await user.click(await screen.findByRole('button',{name:'Register Rewards'}));await user.click(await screen.findByRole('button',{name:'About Account Activity'}))
 const popup=screen.getByRole('tooltip',{name:'Account Activity'});expect(popup).toBeVisible();expect(within(popup).getByText(/matching does not deduct the amount a second time/)).toBeVisible()
 await user.keyboard('{Escape}');expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
})

it('provides ECB date help and searches earned activity separately from spending and expiration',async()=>{
 const data=workspace(),api=new ApiClient('test'),user=userEvent.setup();data.asOf=new Date().toLocaleDateString('sv-SE');const report=pointsReport('ECB',data.asOf)
 const earned={...report.activity[0],description:'ECB toothpaste reward',movement:{...report.activity[0].movement,provider:'ECB',eventId:'earned',points:null,usd:'5'}}
 report.activity=[earned,{...earned,description:'Paid purchase',movement:{...earned.movement,eventId:'spent',usd:'-2'}},{...earned,description:'Expired ECB',movement:{...earned.movement,eventId:'expired',usd:'-1',source:'Adjustment id | Reward unavailable: EXPIRED'}}];report.unavailable='1';report.spent='2';data.balances=[{provider:'ECB',balance:report.ending}]
 vi.spyOn(api,'workspaceAt').mockResolvedValue(data);vi.spyOn(api,'account').mockResolvedValue(report)
 render(<RewardsAccounts data={data} api={api} writable onReceipt={vi.fn()}/>);await user.click(await screen.findByRole('button',{name:'ECB'}));await screen.findByText('ECB toothpaste reward')
 await user.click(screen.getByRole('button',{name:'About Dates & Reward Search'}));expect(screen.getByRole('tooltip',{name:'Dates & Reward Search'})).toHaveTextContent('not coupon expiration dates');await user.keyboard('{Escape}')
 await user.selectOptions(screen.getByLabelText('Activity Type'),'EARNED');expect(screen.getByText('ECB toothpaste reward')).toBeVisible();expect(screen.queryByText('Paid purchase')).not.toBeInTheDocument();expect(screen.queryByText('Expired ECB')).not.toBeInTheDocument()
 await user.type(screen.getByLabelText('Search Rewards'),'toothpaste');expect(screen.getByText('ECB toothpaste reward')).toBeVisible()
})
it('records lost points and a saved dollar valuation separately using a stable retry',async()=>{
 const data=workspace(),api=new ApiClient('test'),user=userEvent.setup();data.asOf=new Date().toLocaleDateString('sv-SE');const report=pointsReport('Fetch',data.asOf);data.balances=[{provider:'Fetch',balance:report.ending}]
 vi.spyOn(api,'workspaceAt').mockResolvedValue(data);vi.spyOn(api,'account').mockResolvedValue(report)
 const mutate=vi.spyOn(api,'mutate').mockRejectedValueOnce(new ApiError(0,'Uncertain')).mockResolvedValueOnce({})
 render(<RewardsAccounts data={data} api={api} writable onReceipt={vi.fn()}/>);await user.click(await screen.findByRole('button',{name:'Fetch'}));await user.click(screen.getByRole('button',{name:'Mark reward unavailable'}))
 const popup=screen.getByRole('dialog',{name:'Mark Fetch reward unavailable'});await user.selectOptions(within(popup).getByLabelText('Reason type'),'LOST');await user.type(within(popup).getByLabelText('Points removed'),'25');await user.type(within(popup).getByLabelText(/Dollar value removed/),'0.025');await user.type(within(popup).getByLabelText('Explanation'),'Provider removed these points')
 await user.click(within(popup).getByRole('button',{name:'Record unavailable reward'}));await user.click(within(popup).getByRole('button',{name:'Retry same unavailable reward'}))
 expect(mutate.mock.calls[0]).toEqual(['/accounts/unavailable-rewards','POST',expect.objectContaining({provider:'Fetch',category:'LOST',points:'25',amount:'0.025',sourceId:null}),expect.any(String)]);expect(mutate.mock.calls[1]).toEqual(mutate.mock.calls[0])
})

it('clears a copied full reward valuation when removing only some of its points',async()=>{
 const data=workspace(),api=new ApiClient('test'),user=userEvent.setup();data.asOf=new Date().toLocaleDateString('sv-SE');const report=pointsReport('Fetch',data.asOf);data.balances=[{provider:'Fetch',balance:report.ending}];data.accountActivity=[{...report.activity[0],movement:{...report.activity[0].movement,eventId:'earned-points',points:'1000',usd:'1'}}]
 vi.spyOn(api,'workspaceAt').mockResolvedValue(data);vi.spyOn(api,'account').mockResolvedValue(report)
 render(<RewardsAccounts data={data} api={api} writable onReceipt={vi.fn()}/>);await user.click(await screen.findByRole('button',{name:'Fetch'}));await user.click(screen.getByRole('button',{name:'Mark reward unavailable'}))
 const popup=screen.getByRole('dialog',{name:'Mark Fetch reward unavailable'});await user.selectOptions(within(popup).getByLabelText(/Reward source/),'earned-points');expect(within(popup).getByLabelText(/Dollar value removed/)).toHaveValue('1')
 await user.clear(within(popup).getByLabelText('Points removed'));await user.type(within(popup).getByLabelText('Points removed'),'250');expect(within(popup).getByLabelText(/Dollar value removed/)).toHaveValue('')
})

it('shows every activity entry for a selected day and only meaningful Walgreens observations',async()=>{
 const data=workspace(),api=new ApiClient('test'),user=userEvent.setup();data.asOf=new Date().toLocaleDateString('sv-SE');const report=pointsReport('Walgreens Cash',data.asOf);data.balances=[{provider:'Walgreens Cash',balance:report.ending}]
 report.activity=Array.from({length:7},(_,i)=>({...report.activity[0],description:`Day reward ${i+1}`,movement:{...report.activity[0].movement,eventId:`event-${i}`,points:null}}))
 data.receipts=[{...receiptRow('wg'),metadata:{...receipt().metadata!,retailer:'Walgreens'}},{...receiptRow('wm'),metadata:{...receipt().metadata!,retailer:'Walmart'}},{...receiptRow('empty'),metadata:{...receipt().metadata!,retailer:'Walgreens'}}]
 data.reportedBalances=[{transactionId:'wg',receiptNumber:'WG-OBS',date:data.asOf,status:'ACTIVE',values:{startingBalance:'1',earned:'0',used:'0',endingBalance:'1'}},{transactionId:'wm',receiptNumber:'WALMART-OBS',date:data.asOf,status:'ACTIVE',values:{startingBalance:'10',earned:'0',used:'0',endingBalance:'10'}},{transactionId:'empty',receiptNumber:'EMPTY-OBS',date:data.asOf,status:'ACTIVE',values:{startingBalance:null,earned:null,used:null,endingBalance:null}}]
 vi.spyOn(api,'workspaceAt').mockResolvedValue(data);vi.spyOn(api,'account').mockResolvedValue(report)
 render(<RewardsAccounts data={data} api={api} writable onReceipt={vi.fn()}/>);await user.click(await screen.findByRole('button',{name:'Walgreens Cash'}));await screen.findByText('Day reward 1');expect(screen.queryByText('Day reward 7')).not.toBeInTheDocument()
 fireEvent.change(screen.getByLabelText('Search Account By Day'),{target:{value:data.asOf}});expect(screen.getByText('Day reward 7')).toBeVisible();expect(screen.getByText('Account Activity').closest('section')).toHaveTextContent(/Showing 7 of 7 matching entries/i)
 await user.click(screen.getByText('Provider-Reported Observations — Show / Hide'));expect(screen.getByRole('button',{name:/WG-OBS/})).toBeVisible();expect(screen.queryByRole('button',{name:/WALMART-OBS/})).not.toBeInTheDocument();expect(screen.queryByRole('button',{name:/EMPTY-OBS/})).not.toBeInTheDocument()
 expect(screen.queryByRole('button',{name:'About Walgreens Cash account activity'})).not.toBeInTheDocument()
 for(const name of ['About Dates & Reward Search','About Account Balances','About Account Activity','About Provider-Reported Observations','About Conversions & Payouts','About Expired, Lost & Other Unavailable Rewards'])expect(screen.getByRole('button',{name})).toBeVisible()
})
it('provides separate rebate progress and submission information popups',async()=>{
 const user=userEvent.setup();render(<WorkspacePages page="deals" data={workspace()} writable onPage={vi.fn()} onReceipt={vi.fn()} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/>)
 await user.click(screen.getByRole('button',{name:'About Rebate Progress'}));expect(screen.getByRole('tooltip',{name:'Rebate Progress'})).toHaveTextContent('captured receipt revision');await user.keyboard('{Escape}')
 await user.click(screen.getByRole('button',{name:'About Rebates — Submission & Follow-Up'}));expect(screen.getByRole('tooltip',{name:'Rebates — Submission & Follow-Up'})).toHaveTextContent('does not mean paid');await user.keyboard('{Escape}');expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
})

it('shows help on hover or keyboard focus and hides on leave or Escape',async()=>{
 const user=userEvent.setup();render(<><SectionHelp title="Test section">An explanation.</SectionHelp><button>Outside</button></>)
 const icon=screen.getByRole('button',{name:'About Test section'});await user.hover(icon);expect(screen.getByRole('tooltip',{name:'Test section'})).toBeVisible()
 await user.unhover(icon);await waitFor(()=>expect(screen.queryByRole('tooltip')).not.toBeInTheDocument())
 await user.tab();expect(icon).toHaveFocus();expect(screen.getByRole('tooltip')).toBeVisible();await user.keyboard('{Escape}');expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
 await user.click(icon);await user.click(screen.getByRole('button',{name:'Outside'}));expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
})
it('moves account overview explanations into help and keeps unavailable actions inset',async()=>{
 const data=workspace(),user=userEvent.setup();data.balances=[{provider:'Walgreens Cash',balance:{monthUsd:'1',startUsd:'1',endUsd:'1',startPoints:null,endPoints:null}}]
 render(<RewardsAccounts data={data} writable onReceipt={vi.fn()}/>)
 expect(screen.queryByText(/Available uses positive recorded closing balances/)).not.toBeInTheDocument()
 await user.hover(screen.getByRole('button',{name:'About Rewards Overview'}));expect(screen.getByRole('tooltip')).toHaveTextContent('Available uses positive recorded closing balances')
 await user.unhover(screen.getByRole('button',{name:'About Rewards Overview'}));await waitFor(()=>expect(screen.queryByRole('tooltip')).not.toBeInTheDocument())
 await user.hover(screen.getByRole('button',{name:'About Calculated Account Balances'}));expect(screen.getByRole('tooltip')).toHaveTextContent('Month Start, Day Start and Day Close as of');await user.keyboard('{Escape}')
 await user.click(screen.getByRole('button',{name:'Walgreens Cash'}));expect(screen.getByRole('button',{name:'Mark reward unavailable'}).closest('.panel')).toHaveClass('reward-unavailable-panel')
})

it('edits a shared discount product selection and sends only supported API fields',async()=>{
 const r=receipt();r.editDiscounts=[{applicationId:'shared',printedLabel:'Shared coupon',appliedAmount:'1.00',kind:'MANUFACTURER',wholeReceipt:true,lineItemIds:[],qualifyingQuantities:{}}]
 const api=new ApiClient('test'),mutate=vi.spyOn(api,'mutate').mockResolvedValue({reviewToken:'reviewed',totals:receiptRow('test').totals}),user=userEvent.setup()
 render(<ReceiptEdit receipt={r} api={api} enabled onSaved={vi.fn()} onCancel={vi.fn()}/>)
 await user.click(screen.getByLabelText('Discount products 1: line 1'))
 expect(screen.getByLabelText('Qualifying quantity for line 1')).toHaveValue(1)
 await user.type(screen.getByLabelText('Reason for correction'),'Select qualifying product')
 await user.click(screen.getByRole('button',{name:'Review updated totals'}))
 const body=mutate.mock.calls.find(call=>call[0].endsWith('/edit-preview'))?.[2] as {discounts:Record<string,unknown>[]}
 expect(body.discounts[0]).toMatchObject({applicationId:'shared',wholeReceipt:false,lineItemIds:['original-line'],qualifyingQuantities:{'original-line':1}})
 expect(body.discounts[0]).not.toHaveProperty('shared')
})
