import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiClient } from '../../api/client'
import { ReceiptRewardForm } from './ReceiptRewardForm'
import type { Receipt } from '../../types/platform'
import { savings } from '../../api/fixtures.test-support'
const receipt:Receipt={transactionId:'receipt',revision:'3',status:'ACTIVE',metadata:null,evidenceIds:[],contents:{purchaseItems:[{lineItemId:'first',productName:'Shampoo',productCode:'same',quantity:'2',regularUnitPrice:'10',saleUnitPrice:'8'},{lineItemId:'second',productName:'Shampoo',productCode:'same',quantity:'1',regularUnitPrice:'10',saleUnitPrice:'7'}],appliedCoupons:[],payments:[],observations:{earnings:[]},receiptTotals:{taxAmount:'0',finalTotal:'23'}}}
describe('post-save receipt rebates',()=>{
  it('links exact quantities across lines and keeps P&G separate from PayPal',async()=>{
    const api=new ApiClient('owner');const mutate=vi.spyOn(api,'mutate').mockImplementation(async path=>path.endsWith('preview')?{totals:{reportedDue:'23',savings:{...savings(),newlyEarned:'0'}},qualification:{status:'NOT_RECORDED',reason:'No requirement',qualifyingQuantity:'0',qualifyingSpend:null}}:{});const saved=vi.fn();const user=userEvent.setup();render(<ReceiptRewardForm receipt={receipt} api={api} enabled onSaved={saved} onCancel={vi.fn()}/>);
    await user.type(screen.getByLabelText('Provider 1'),'P&G Good Everyday');await user.type(screen.getByLabelText('Reward amount 1 (points)'),'100.0000');await user.click(screen.getByLabelText('Reward products 1: line 1'));await user.click(screen.getByLabelText('Reward products 1: line 2'));await user.clear(screen.getByLabelText('Qualifying quantity for line 1'));await user.type(screen.getByLabelText('Qualifying quantity for line 1'),'1');await user.click(screen.getByText('Submission and payout destination (optional)'));await user.selectOptions(screen.getByLabelText('Payout destination'),'PAYPAL');await user.click(screen.getByRole('button',{name:'Review rebate or reward'}));
    await waitFor(()=>expect(screen.getByRole('button',{name:'Save rebate or reward'})).toBeEnabled());expect(saved).not.toHaveBeenCalled();await user.click(screen.getByRole('button',{name:'Save rebate or reward'}));const body=mutate.mock.calls[1][2] as {earning:{provider:string;amount:string;status:string;payoutDestination:string;lineItemIds:string[];qualifyingQuantities:Record<string,number>}};expect(body.earning.provider).toBe('P&G Good Everyday');expect(body.earning.payoutDestination).toBe('PAYPAL');expect(body.earning.amount).toBe('100.0000');expect(body.earning.status).toBe('EXPECTED');expect(body.earning.lineItemIds).toEqual(['first','second']);expect(body.earning.qualifyingQuantities).toEqual({first:1,second:1});expect(mutate.mock.calls[1][4]).toBe('3');expect(saved).toHaveBeenCalledOnce()
  })
})
