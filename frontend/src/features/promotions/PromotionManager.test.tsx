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

it('adds, edits and archives Spend Boosters through the existing promotion workflow',async()=>{
 const booster={...promotion,planningRules:{kind:'SPEND_BOOSTER',store:'Walgreens',rewardType:'WALGREENS_CASH',spendBasis:'BEFORE_COUPONS',scope:'RECEIPT',wcAllowed:'NO',rrAllowed:'YES',usageLimit:'ONCE',verified:'true'},name:'Spend 35 Get 7',status:'ACTIVE',start:'2026-11-01',end:'2026-11-30'};
 const api=new ApiClient('test'),mutate=vi.spyOn(api,'mutate').mockResolvedValue({}),changed=vi.fn(),user=userEvent.setup();vi.spyOn(window,'confirm').mockReturnValue(true);
 render(<PromotionManager boostersOnly promotions={[booster,promotion]} api={api} writable onChanged={changed}/>);
 expect(screen.getByRole('heading',{name:'Spend Boosters'})).toBeInTheDocument();await user.selectOptions(screen.getByLabelText('Spend Booster'),'p');await user.click(screen.getByRole('button',{name:'Edit Spend Booster'}));
 expect(screen.getByLabelText('Walgreens Cash payment')).toHaveValue('NO');await user.selectOptions(screen.getByLabelText('Gift card payment / threshold rule'),'COUNTS');await user.click(screen.getByRole('button',{name:'Save Spend Booster'}));
 expect(mutate).toHaveBeenLastCalledWith('/rebate-programs/p','PUT',expect.objectContaining({planningRules:expect.objectContaining({kind:'SPEND_BOOSTER',giftCardRule:'COUNTS',wcAllowed:'NO',verified:'true'})}),expect.any(String));
 await user.click(screen.getByRole('button',{name:'Delete Spend Booster'}));expect(mutate).toHaveBeenLastCalledWith('/rebate-programs/p','DELETE',{expectedToken:'original-token'},expect.any(String));
 await user.click(screen.getByRole('button',{name:'Add Spend Booster'}));expect(screen.getByRole('dialog',{name:'Add Spend Booster'})).toBeInTheDocument();expect(screen.getByLabelText('Qualifying spend basis')).toHaveValue('');expect(screen.getByLabelText('Walgreens Cash payment')).toHaveValue('NO');
});
