import { render,screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect,it,vi } from 'vitest'
import { PromotionManager } from './PromotionManager'
import { ApiClient,ApiError } from '../../api/client'
import type { Promotion } from '../../types/platform'
const promotion:Promotion={id:'p',name:'P&G promotion',threshold:'75',giftCardValue:'15',start:'2026-10-01',end:'2026-10-31',terms:'Selected products',status:'TRACKING',contributions:{},token:'original-token'}
it('preserves the original promotion update request across an uncertain outcome',async()=>{
 const api=new ApiClient('test'),mutate=vi.spyOn(api,'mutate').mockRejectedValueOnce(new ApiError(0,'Outcome uncertain')).mockResolvedValueOnce({}),changed=vi.fn(),user=userEvent.setup()
 render(<PromotionManager promotions={[promotion]} api={api} writable onChanged={changed}/>)
 await user.selectOptions(screen.getByLabelText('Promotion'),'p');await user.click(screen.getByRole('button',{name:'Edit'}))
 await user.clear(screen.getByLabelText('Promotion name'));await user.type(screen.getByLabelText('Promotion name'),'Updated P&G promotion')
 await user.click(screen.getByRole('button',{name:'Save promotion'}))
 expect(mutate.mock.calls[0]).toEqual(['/rebate-programs/p','PUT',expect.objectContaining({name:'Updated P&G promotion',expectedToken:'original-token'}),expect.any(String)])
 await user.click(screen.getByRole('button',{name:'Retry same promotion'}))
 expect(mutate.mock.calls[1]).toEqual(mutate.mock.calls[0]);expect(changed).toHaveBeenCalledOnce()
})
it('disables management controls in read-only mode',()=>{
 render(<PromotionManager promotions={[promotion]} api={new ApiClient('test')} writable={false}/>)
 expect(screen.getByRole('button',{name:'Add promotion'})).toBeDisabled()
 expect(screen.getByRole('button',{name:'Edit'})).toBeDisabled()
 expect(screen.getByRole('button',{name:'Delete'})).toBeDisabled()
})
