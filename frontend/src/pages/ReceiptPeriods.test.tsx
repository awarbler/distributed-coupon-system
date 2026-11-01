import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
import { WorkspacePages } from './WorkspacePages'
import { workspace, receiptRow } from '../api/fixtures.test-support'
import { ApiClient } from '../api/client'

it('defaults receipts to this month and switches week, year and all-time totals',async()=>{
 const today=new Date().toLocaleDateString('sv-SE',{timeZone:'America/Chicago'})
 const data=workspace(),api=new ApiClient('test'),user=userEvent.setup()
 data.receipts=[receiptRow('current',today),receiptRow('old','2020-01-01')]
 data.receiptPeriods={empty:data.summaries.month,[`month:${today.slice(0,7)}`]:data.summaries.month}
 const load=vi.spyOn(api,'receiptSummary').mockResolvedValue(data.summaries.all)
 render(<WorkspacePages data={data} api={api} page="receipts" writable onPage={vi.fn()} onReceipt={vi.fn()} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/> )
 expect(screen.getByRole('button',{name:'Month'})).toHaveAttribute('aria-pressed','true')
 expect(screen.getByText('Showing 1–1 of 1')).toBeInTheDocument()
 const month=new Intl.DateTimeFormat('en-US',{month:'long',year:'numeric'}).format(new Date(`${today}T12:00:00`))
 expect(screen.getByRole('heading',{name:`Period totals — ${month}`})).toBeInTheDocument()
 await user.click(screen.getByRole('button',{name:'Week'}))
 const start=new Date(`${today}T12:00:00Z`);start.setUTCDate(start.getUTCDate()-start.getUTCDay());const end=new Date(start);end.setUTCDate(end.getUTCDate()+6)
 await waitFor(()=>expect(load).toHaveBeenCalledWith(start.toISOString().slice(0,10),end.toISOString().slice(0,10),expect.any(AbortSignal)))
 await user.click(screen.getByRole('button',{name:'Year'}))
 await waitFor(()=>expect(load).toHaveBeenCalledWith(`${today.slice(0,4)}-01-01`,`${today.slice(0,4)}-12-31`,expect.any(AbortSignal)))
 await user.click(screen.getByRole('button',{name:'All Time'}))
 expect(screen.getByRole('heading',{name:'Period totals — All time'})).toBeInTheDocument()
 expect(screen.getByText('Showing 1–2 of 2')).toBeInTheDocument()
})
