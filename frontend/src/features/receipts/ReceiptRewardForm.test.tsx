import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiClient } from '../../api/client'
import { ReceiptRewardForm } from './ReceiptRewardForm'
import type { Receipt } from '../../types/platform'
import { RewardFields } from './RewardFields'
import { newReward, rewardBody } from './rewardStatus'
import { savings } from '../../api/fixtures.test-support'
const receipt:Receipt={transactionId:'receipt',revision:'3',status:'ACTIVE',metadata:null,evidenceIds:[],contents:{purchaseItems:[{lineItemId:'first',productName:'Shampoo',productCode:'same',quantity:'2',regularUnitPrice:'10',saleUnitPrice:'8'},{lineItemId:'second',productName:'Shampoo',productCode:'same',quantity:'1',regularUnitPrice:'10',saleUnitPrice:'7'}],appliedCoupons:[],payments:[],observations:{earnings:[]},receiptTotals:{taxAmount:'0',finalTotal:'23'}}}
describe('post-save receipt rebates',()=>{
  it('links exact quantities across lines and keeps P&G separate from PayPal',async()=>{
    const api=new ApiClient('owner');const mutate=vi.spyOn(api,'mutate').mockImplementation(async path=>path.endsWith('preview')?{totals:{reportedDue:'23',savings:{...savings(),newlyEarned:'0'}},qualification:{status:'NOT_RECORDED',reason:'No requirement',qualifyingQuantity:'0',qualifyingSpend:null}}:{});const saved=vi.fn();const user=userEvent.setup();render(<ReceiptRewardForm receipt={receipt} api={api} enabled onSaved={saved} onCancel={vi.fn()}/>);
    expect(screen.getByLabelText('Reward status 1')).toHaveValue('CREDITED');await user.selectOptions(screen.getByLabelText('Reward status 1'),'EXPECTED');await user.selectOptions(screen.getByLabelText('Reward program 1'),'P&G Good Everyday');await user.type(screen.getByLabelText('Reward amount 1 (points)'),'100.0000');await user.click(screen.getByLabelText('Reward products 1: line 1'));await user.click(screen.getByLabelText('Reward products 1: line 2'));await user.clear(screen.getByLabelText('Qualifying quantity for line 1'));await user.type(screen.getByLabelText('Qualifying quantity for line 1'),'1');await user.click(screen.getByText('Submission and payout destination (optional)'));await user.selectOptions(screen.getByLabelText('Payout destination'),'PAYPAL');await user.click(screen.getByRole('button',{name:'Review rebate or reward'}));
    await waitFor(()=>expect(screen.getByRole('button',{name:'Save rebate or reward'})).toBeEnabled());expect(saved).not.toHaveBeenCalled();await user.click(screen.getByRole('button',{name:'Save rebate or reward'}));const body=mutate.mock.calls[1][2] as {earning:{provider:string;amount:string;status:string;payoutDestination:string;lineItemIds:string[];qualifyingQuantities:Record<string,number>}};expect(body.earning.provider).toBe('P&G Good Everyday');expect(body.earning.payoutDestination).toBe('PAYPAL');expect(body.earning.amount).toBe('100.0000');expect(body.earning.status).toBe('EXPECTED');expect(body.earning.lineItemIds).toEqual(['first','second']);expect(body.earning.qualifyingQuantities).toEqual({first:1,second:1});expect(mutate.mock.calls[1][4]).toBe('3');expect(saved).toHaveBeenCalledOnce()
  })
})

it('preserves purchase versus scan requirements without numerical thresholds', () => {
  for (const [selection, category] of [['PURCHASE', 'Purchase'], ['RECEIPT_SCAN', 'ReceiptScan']]) {
    const body = rewardBody({ ...newReward(), requirementType: selection })
    expect(body.requirement).toEqual({ type: null, amount: null, category, spendBasis: 'UNKNOWN' })
  }
})
it('copies historical terms with the right form and clears previous confirmation and associations', async () => {
  const set = vi.fn(); const user = userEvent.setup()
  const value = { ...newReward(), status: 'CREDITED', confirmed: '25', confirm: true, reference: 'old', valuation: '0.025', quantities: { first: '1' } }
  render(<RewardFields value={value} onChange={set} lines={[]} offers={[{
    id: 'old', title: 'Receipt scan', kind: 'THIRD_PARTY_REBATE', provider: 'Fetch', amount: '25', form: 'POINTS', sourceReceiptNumber: '000123', sourceDate: '2026-10-19',
    requirement: { type: null, amount: null, category: 'ReceiptScan', spendBasis: 'UNKNOWN' },
  }]} />)
  await user.selectOptions(screen.getByLabelText('Copy terms from a previous receipt'), 'old')
  expect(set).toHaveBeenCalledWith(expect.objectContaining({ id: value.id, form: 'POINTS', requirementType: 'RECEIPT_SCAN', status: 'EXPECTED', confirmed: '', confirm: false, quantities: {}, valuation: '', reference: '' }))
})

it('offers purchase member rewards as receipt-wide Walgreens Cash without a qualifying threshold', async () => {
  const user=userEvent.setup(),changed=vi.fn(),reward={...newReward(),kind:'WALGREENS_CASH',form:'STORE_REWARD'}
  render(<RewardFields value={reward} onChange={changed} lines={[]} />)
  await user.selectOptions(screen.getByLabelText('Walgreens Cash reward type 1'),'MEMBER')
  const selected=changed.mock.calls[0][0]
  expect(selected.offer).toBe('Member rewards for purchase')
  expect(rewardBody(selected)).toMatchObject({kind:'WALGREENS_CASH',lineItemIds:[],requirement:{type:null,amount:null,category:'Purchase'}})
})

it.each(['PayPal','Venmo'])('offers %s as a dollar reward provider', async provider => {
  const changed=vi.fn(),user=userEvent.setup()
  render(<RewardFields value={newReward()} onChange={changed} lines={[]} />)
  await user.selectOptions(screen.getByLabelText('Reward program 1'),provider)
  expect(changed).toHaveBeenCalledWith(expect.objectContaining({provider,form:'CASH',status:'CREDITED'}))
})

it('lists supported programs once without duplicate common-provider controls',()=>{
 render(<RewardFields value={newReward()} onChange={vi.fn()} lines={[]}/>)
 const select=screen.getByLabelText('Reward program 1')
 for(const name of ['Fetch','P&G Brand','Ibotta','Walmart Cash','PayPal','Venmo','Swagbucks','MyPoints','Merryfield','Rakuten','Shopmium','Makeena'])expect(select).toHaveTextContent(name)
 expect(screen.queryByText('Common providers')).not.toBeInTheDocument();expect(screen.queryByLabelText('Provider 1')).not.toBeInTheDocument()
})

it('describes Walmart Cash as store credit for later use without changing checkout OOP',()=>{
 render(<RewardFields value={{...newReward(),provider:'Walmart Cash',form:'STORE_REWARD',amount:'5'}} onChange={vi.fn()} lines={[]}/>)
 expect(screen.getByRole('option',{name:'Added to Walmart Cash account'})).toBeInTheDocument()
 expect(screen.getByText('Walmart Cash — store credit')).toBeInTheDocument()
 expect(screen.getByText(/does not reduce Actual OOP or count as a cash rebate/)).toBeInTheDocument()
})
