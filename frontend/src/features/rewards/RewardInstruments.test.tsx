import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi } from 'vitest'
import { ApiClient, ApiError } from '../../api/client'
import type { RewardInstrument } from '../../types/platform'
import { RewardInstruments } from './RewardInstruments'
function row(id='account-one',nickname='My Target'):RewardInstrument{return {id,nickname,kind:'ACCOUNT_REWARD',provider:'Target',unit:'USD',lastFour:null,revision:'1',opening:'12.00',openingDate:'2026-10-30',historyComplete:true,calculated:'12.00',reported:'11.00',reportedDate:'2026-10-30',balanceDate:'2026-10-30',difference:'1.00',comparisonStatus:'SAME_RECORDED_DATE',newEarnings:'0',events:[],history:[],issuedFrom:[]}}
function setup(rows:RewardInstrument[]=[row()],writable=true){const api=new ApiClient('test');vi.spyOn(api,'rewardInstruments').mockResolvedValue({instruments:rows});const mutate=vi.spyOn(api,'mutate').mockResolvedValue({});render(<RewardInstruments api={api} writable={writable}/>);return {api,mutate,user:userEvent.setup()}}
describe('individual reward accounts and gift cards',()=>{
 it('shows independent balance values and separates multiple accounts from individual cards',async()=>{
  const card={...row('card','Issued card'),kind:'GIFT_CARD' as const,lastFour:'1234',reported:null,difference:null};const {user}=setup([row(),row('account-two',"Son's Target"),card]);
  await user.selectOptions(await screen.findByLabelText('Select individual account or card'),'account-one');
  expect(screen.getByRole('combobox',{name:/^Activity type$/})).toBeInTheDocument();const balance=screen.getByLabelText('Individual balance comparison');expect(balance).toHaveTextContent('Calculated $12.00');expect(balance).toHaveTextContent('Actual reported $11.00');expect(balance).toHaveTextContent('Difference $1.00');
  expect(screen.queryByLabelText('Corrected last four digits')).not.toBeInTheDocument();
  await user.selectOptions(screen.getByLabelText('Select individual account or card'),'card');expect(screen.getByLabelText('Corrected last four digits')).toHaveValue('1234');
 })
 it('preserves missing opening and date fields without credentials or a guessed zero',async()=>{
  const {user,mutate}=setup([]);await user.click(screen.getByText('Add an account or individual gift card'));await user.type(screen.getByLabelText('Retailer or reward program'),'Target');await user.type(screen.getByLabelText('Opening source or reason'),'History not yet recorded');await user.click(screen.getByRole('button',{name:'Save account or card'}));
  expect(mutate).toHaveBeenCalledWith('/reward-instruments','POST',expect.objectContaining({kind:'ACCOUNT_REWARD',opening:null,date:null,historyComplete:false,lastFour:null}),expect.any(String),undefined);
  expect(JSON.stringify(mutate.mock.calls[0][2])).not.toMatch(/password|credentials|pin/i);
 })
 it('retries the identical uncertain write rather than generating another event',async()=>{
  const {user,mutate}=setup();mutate.mockRejectedValueOnce(new ApiError(0,'Connection lost'));
  await user.selectOptions(await screen.findByLabelText('Select individual account or card'),'account-one');await user.type(screen.getByLabelText('Observed activity amount (USD)'),'5.00');await user.type(screen.getByLabelText('Evidence reference or reason'),'Observed credit');await user.click(screen.getByRole('button',{name:'Save observed activity'}));
  await user.click(await screen.findByRole('button',{name:'Retry the same instrument request'}));expect(mutate.mock.calls[1]).toEqual(mutate.mock.calls[0]);
 })
 it('records an observed exchange with no invented points amount or conversion rate',async()=>{
  const points={...row(),provider:'Fetch',unit:'POINTS' as const,calculated:null,reported:null,difference:null};const {user,mutate}=setup([points]);await user.selectOptions(await screen.findByLabelText('Select individual account or card'),'account-one');
  expect(screen.getByLabelText('Individual balance comparison')).toHaveTextContent('Calculated Unknown');await user.click(screen.getByText('Record an observed exchange into a gift card'));
  await user.type(screen.getByLabelText('Issued card retailer'),'Target');await user.type(screen.getByLabelText('Issued gift-card value (USD)'),'10.00');await user.type(screen.getByLabelText('Exchange evidence or reason'),'Issued card observed');await user.click(screen.getByRole('button',{name:'Save linked exchange'}));
  expect(mutate).toHaveBeenCalledWith('/reward-instruments/account-one/exchange','POST',expect.objectContaining({sourceAmount:null,cardValue:'10.00',date:null,provider:'Target'}),expect.any(String),'1');
 })
 it('disables all financial writes for a read-only session',async()=>{
  const {user,mutate}=setup([row()],false);await user.selectOptions(await screen.findByLabelText('Select individual account or card'),'account-one');expect(screen.getByRole('button',{name:'Save observed activity'})).toBeDisabled();expect(mutate).not.toHaveBeenCalled();
  expect(within(screen.getByLabelText('Individual balance comparison')).getByText('$1.00')).toBeInTheDocument();
 })
 it('keeps an uncertain journal outcome blocked instead of allowing a new write key',async()=>{
  const {user,mutate}=setup();mutate.mockRejectedValueOnce(new ApiError(0,'Connection lost')).mockRejectedValueOnce(new ApiError(409,'Previous request outcome is uncertain. Retrieve the resource and reconcile.'));
  await user.selectOptions(await screen.findByLabelText('Select individual account or card'),'account-one');await user.type(screen.getByLabelText('Observed activity amount (USD)'),'5');await user.type(screen.getByLabelText('Evidence reference or reason'),'Observed credit');await user.click(screen.getByRole('button',{name:'Save observed activity'}));await user.click(await screen.findByRole('button',{name:'Retry the same instrument request'}));
  expect(screen.getByRole('button',{name:'Save observed activity'})).toBeDisabled();expect(screen.getByRole('button',{name:'Retry the same instrument request'})).toBeEnabled();expect(mutate.mock.calls[1]).toEqual(mutate.mock.calls[0]);
 })
})
