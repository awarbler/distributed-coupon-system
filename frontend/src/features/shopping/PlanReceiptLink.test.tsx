import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
import { ApiClient } from '../../api/client'
import { planningRow, receiptRow, workspace } from '../../api/fixtures.test-support'
import { PlanReceiptLink } from './PlanReceiptLink'

it.each(['11513233013826110203','1151-3233-0138-2611-0203','1151 3233 0138 2611 0203','32330138'])('finds a saved receipt using %s',async query=>{
 const data=workspace(),user=userEvent.setup(),receipt=receiptRow('matching')
 receipt.metadata={...receipt.metadata!,receiptNumber:'1151-3233-0138-2611-0203'}
 data.receipts=[receipt,receiptRow('other')]
 render(<PlanReceiptLink plan={planningRow().plan} data={data} api={new ApiClient('test')} writable onClose={vi.fn()} onSaved={vi.fn()}/>)
 await user.type(screen.getByLabelText('Search saved receipts'),query)
 expect(screen.getByRole('option',{name:/1151-3233-0138-2611-0203/})).toBeInTheDocument()
 expect(screen.getAllByRole('option')).toHaveLength(2)
})

it('connects an existing receipt without posting a new receipt and retains the request on retry',async()=>{
 const data=workspace(),plan=planningRow().plan,api=new ApiClient('test'),saved=vi.fn(),user=userEvent.setup()
 data.receipts=[receiptRow('existing'),{...receiptRow('deleted'),status:'DELETED'}]
 const mutate=vi.spyOn(api,'mutate').mockRejectedValueOnce(new Error('Outcome unknown')).mockResolvedValueOnce({})
 render(<PlanReceiptLink plan={plan} data={data} api={api} writable onClose={vi.fn()} onSaved={saved}/>)
 expect(screen.queryByRole('option',{name:/0001-deleted/})).not.toBeInTheDocument()
 await user.selectOptions(screen.getByLabelText('Completed deal receipt'),'existing')
 await user.click(screen.getByRole('button',{name:'Connect receipt & complete deal'}))
 await user.click(screen.getByRole('button',{name:'Retry same receipt connection'}))
 await waitFor(()=>expect(saved).toHaveBeenCalledOnce())
 expect(mutate.mock.calls[0]).toEqual(mutate.mock.calls[1]);expect(mutate.mock.calls[0]).toEqual([`/shopping-plans/${plan.planId}/receipt-link`,'POST',{receiptId:'existing',receiptRevision:'1'},expect.any(String),plan.revision])
})
