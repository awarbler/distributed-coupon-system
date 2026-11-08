import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi } from 'vitest'
import { workspace, receiptRow } from '../../api/fixtures.test-support'
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

it('opens gift-card entry on the gift-card page and saves a separately tracked card',async()=>{
 const api=new ApiClient('test');vi.spyOn(api,'rewardInstruments').mockResolvedValue({instruments:[]});const mutate=vi.spyOn(api,'mutate').mockResolvedValue({});const user=userEvent.setup();
 render(<RewardInstruments api={api} writable giftCardsOnly/>);
 expect(screen.getByText('Add a gift card').closest('details')).not.toHaveAttribute('open');expect(screen.getByLabelText('Instrument type')).toHaveValue('GIFT_CARD');expect(screen.queryByRole('option',{name:'Account-linked reward'})).not.toBeInTheDocument();
 await user.click(screen.getByText('Add a gift card'));await user.type(screen.getByLabelText('Retailer or reward program'),'Target');await user.type(screen.getByLabelText('Observed opening value (blank if unknown)'),'25');await user.type(screen.getByLabelText('Card last four digits (optional)'),'1234');await user.type(screen.getByLabelText('Opening source or reason'),'New card');await user.click(screen.getByRole('button',{name:'Save account or card'}));
 expect(mutate).toHaveBeenCalledWith('/reward-instruments','POST',expect.objectContaining({kind:'GIFT_CARD',provider:'Target',opening:'25',lastFour:'1234'}),expect.any(String),undefined);
});

it('refreshes the active card list immediately after creation and clears the add form',async()=>{
 const card={...row('new-card','Kitchen card'),kind:'GIFT_CARD' as const,provider:'Walgreens',lastFour:'1234',calculated:'25.00',status:'ACTIVE' as const};
 const api=new ApiClient('test');vi.spyOn(api,'rewardInstruments').mockResolvedValueOnce({instruments:[]}).mockResolvedValue({instruments:[card]});vi.spyOn(api,'mutate').mockResolvedValue(card);const user=userEvent.setup();
 render(<RewardInstruments api={api} writable giftCardsOnly/>);await user.click(screen.getByText('Add a gift card'));await user.type(screen.getByLabelText('Retailer or reward program'),'Walgreens');await user.type(screen.getByLabelText('Account or card nickname'),'Kitchen card');await user.type(screen.getByLabelText('Opening source or reason'),'Observed opening');await user.click(screen.getByRole('button',{name:'Save account or card'}));
 const active=screen.getByRole('region',{name:'Current Active Gift Cards'});expect(await within(active).findByText('Walgreens · Kitchen card · ending 1234')).toBeInTheDocument();expect(within(active).getByText('$25.00')).toBeInTheDocument();expect(screen.getByLabelText('Account or card nickname')).toHaveValue('');expect(screen.getByLabelText('Retailer or reward program')).toHaveValue('');
});
it('edits a card and confirms duplicate removal by archiving while retaining its identity',async()=>{
 const card={...row('card','My card'),kind:'GIFT_CARD' as const,provider:'Walgreens',lastFour:'1234',status:'ACTIVE' as const};const edited={...card,nickname:'Updated card',revision:'2'};const archived={...edited,status:'ARCHIVED' as const,revision:'3'};
 const api=new ApiClient('test');vi.spyOn(api,'rewardInstruments').mockResolvedValueOnce({instruments:[card]}).mockResolvedValueOnce({instruments:[edited]}).mockResolvedValue({instruments:[archived]});const mutate=vi.spyOn(api,'mutate').mockResolvedValueOnce(edited).mockResolvedValue(archived);const user=userEvent.setup();vi.spyOn(window,'confirm').mockReturnValue(true);
 render(<RewardInstruments api={api} writable giftCardsOnly/>);await user.click(await screen.findByRole('button',{name:'Edit'}));expect(screen.getByLabelText('Corrected last four digits')).toHaveValue('1234');await user.clear(screen.getByLabelText('Corrected nickname'));await user.type(screen.getByLabelText('Corrected nickname'),'Updated card');await user.type(screen.getByLabelText('Detail correction reason'),'Correct label');await user.click(screen.getByLabelText('Confirm detail correction and retain history'));await user.click(screen.getByRole('button',{name:'Save detail correction'}));expect(mutate).toHaveBeenCalledWith('/reward-instruments/card','PUT',expect.objectContaining({nickname:'Updated card',kind:'GIFT_CARD',lastFour:'1234',confirmed:true}),expect.any(String),'1');
 await user.click(screen.getByRole('button',{name:'Delete'}));expect(mutate).toHaveBeenCalledWith('/reward-instruments/card','PUT',expect.objectContaining({status:'ARCHIVED',confirmed:true}),expect.any(String),'2');
 const active=screen.getByRole('region',{name:'Current Active Gift Cards'});expect(await within(active).findByText('No active gift cards recorded.')).toBeInTheDocument();await user.click(screen.getByLabelText('Show archived cards'));expect(within(active).getByText('ARCHIVED')).toBeInTheDocument();
});
it('shows an unknown balance separately from its observed opening',async()=>{
 const card={...row('unknown','Unverified'),kind:'GIFT_CARD' as const,provider:'CVS',opening:'50.00',calculated:null,historyComplete:false};const api=new ApiClient('test');vi.spyOn(api,'rewardInstruments').mockResolvedValue({instruments:[card]});render(<RewardInstruments api={api} writable giftCardsOnly/>);
 const active=screen.getByRole('region',{name:'Current Active Gift Cards'});expect(await within(active).findByText('Unknown')).toBeInTheDocument();expect(within(active).getByText(/Opening observation:.*50.00/)).toBeInTheDocument();
});

it('opens selected card history directly and retains its original receipt link',async()=>{
 const data=workspace(),receipt=receiptRow('linked-receipt','2026-11-07');receipt.metadata={retailer:'Walgreens',receiptNumber:'R-123',purchasedAt:null,storeReference:null,purchaseChannel:'IN_STORE'};data.receipts=[receipt];
 const card={...row('history-card','Tracked card'),kind:'GIFT_CARD' as const,events:[{id:'event',kind:'REDEMPTION',amount:'5',date:'2026-11-07',reason:'Recorded purchase',active:true,supersedes:null,relatedInstrument:null,exchangeId:null,recordedAt:'2026-11-07T12:00:00Z',recordedBy:'test',sourceReceiptId:'linked-receipt'}]};
 const api=new ApiClient('test');vi.spyOn(api,'rewardInstruments').mockResolvedValue({instruments:[card]});const onReceipt=vi.fn(),user=userEvent.setup();render(<RewardInstruments api={api} writable giftCardsOnly workspace={data} onReceipt={onReceipt}/>);
 await user.click(await screen.findByRole('button',{name:'View'}));expect(screen.getByText('Recorded activity and correction history').closest('details')).toHaveAttribute('open');await user.click(screen.getByRole('button',{name:'View receipt R-123'}));expect(onReceipt).toHaveBeenCalledWith('linked-receipt');
});

it('links a distinct received Visa payout only after explicit confirmation and creates no extra earning',async()=>{
 const data=workspace(),api=new ApiClient('test'),user=userEvent.setup();data.accountHistory.redemptionHistory=[{redemptionId:'visa-payout',provider:'Fetch',destination:'GIFT_CARD',destinationAccount:'Gift Cards',destinationUsdValue:'50',status:'RECEIVED',completedDate:'2026-11-05',notes:'Brand: Visa Prepaid Card | Number: 4204**** **** 4668'}];
 vi.spyOn(api,'rewardInstruments').mockResolvedValue({instruments:[]});vi.spyOn(api,'payout').mockResolvedValue({revision:'2',payout:{status:'RECEIVED'}});const mutate=vi.spyOn(api,'mutate').mockResolvedValue({});
 render(<RewardInstruments api={api} writable giftCardsOnly workspace={data}/>);await user.click(screen.getByRole('button',{name:'Track / link this card'}));expect(mutate).not.toHaveBeenCalled();expect(screen.getByLabelText('Issued card last four digits')).toHaveValue('4668');await user.type(screen.getByLabelText('Link evidence or reason'),'Verified received payout');await user.click(screen.getByLabelText('I confirm this payout belongs to the selected or newly recorded card.'));await user.click(screen.getByRole('button',{name:'Confirm payout link'}));
 expect(mutate).toHaveBeenCalledWith('/accounts/payouts/visa-payout/gift-card','POST',expect.objectContaining({provider:'Visa Prepaid Card',lastFour:'4668',existingInstrumentId:null,confirmed:true}),expect.any(String),'2');expect(mutate).not.toHaveBeenCalledWith('/accounts/member-rewards',expect.anything(),expect.anything(),expect.anything());
});

it('keeps incomplete card totals in tracking help without creating a competing account summary',async()=>{
 const api=new ApiClient('test');vi.spyOn(api,'rewardInstruments').mockResolvedValue({instruments:[],cardSummary:{recordedOpenings:'55',recordedRedemptions:'0',knownRemaining:'5',unknownBalanceCount:1,activeCards:2}});render(<RewardInstruments api={api} writable giftCardsOnly/>);expect(screen.queryByRole('heading',{name:'Account Balances'})).not.toBeInTheDocument();await userEvent.setup().hover(screen.getByRole('button',{name:'About Gift Card Tracking'}));expect(await screen.findByRole('tooltip')).toHaveTextContent('Known remaining subtotal: $5.00');expect(screen.getByRole('tooltip')).toHaveTextContent('Cards with unknown balances: 1');
});
