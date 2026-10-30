import { fireEvent, render, screen, within, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
import { ApiClient, ApiError } from '../../api/client'
import { workspace, receiptRow } from '../../api/fixtures.test-support'
import type { AccountReport, Workspace } from '../../types/platform'
import { WorkspacePages } from '../../pages/WorkspacePages'
import { RewardsAccounts, StandaloneRewardPanel, MemberRewardEntry, PayoutEntry } from './RewardsAccounts'

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


it('shows every reward account in the preferred order and resets sorting when reopened',async()=>{
 const data=workspace(),user=userEvent.setup();const names=['Rakuten','Fetch','Walgreens Cash','Ibotta','Register Rewards','ECB','Walmart Cash','Checkout 51','Venmo','P&G Brand','Swagbucks','MyPoints','Coupons DotCom','PayPal','Gift Cards','Returns','Target','Merryfield','Makeena','Shopmium'];const balance={monthUsd:'0',startUsd:'0',endUsd:'0',startPoints:null,endPoints:null}
 data.balances=names.map(provider=>({provider,balance}));render(<RewardsAccounts data={data} writable onReceipt={vi.fn()}/> )
 const accountRows=()=>within(document.querySelector('.account-balances-panel') as HTMLElement).getAllByRole('row').slice(1).map(row=>within(row).getByRole('button').textContent)
 expect(accountRows()).toEqual(['Walgreens Cash','Register Rewards','ECB','Walmart Cash','Checkout 51','Ibotta','Venmo','Fetch','P&G Brand','Swagbucks','MyPoints','Coupons DotCom','PayPal','Gift Cards','Returns','Target','Merryfield','Makeena','Shopmium','Rakuten'])
 await user.click(screen.getByRole('button',{name:/^Account$/}));expect(accountRows()[0]).toBe('Checkout 51')
 await user.click(screen.getByRole('button',{name:/^Fetch$/}));await user.click(screen.getByRole('button',{name:'← All Accounts'}));expect(accountRows()[0]).toBe('Walgreens Cash');expect(accountRows()).toHaveLength(20)
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
 data.receipts=[{...receiptRow('wg'),metadata:{...receiptRow('observation').metadata!,retailer:'Walgreens'}},{...receiptRow('wm'),metadata:{...receiptRow('observation').metadata!,retailer:'Walmart'}},{...receiptRow('empty'),metadata:{...receiptRow('observation').metadata!,retailer:'Walgreens'}}]
 data.reportedBalances=[{transactionId:'wg',receiptNumber:'WG-OBS',date:data.asOf,status:'ACTIVE',values:{startingBalance:'1',earned:'0',used:'0',endingBalance:'1'}},{transactionId:'wm',receiptNumber:'WALMART-OBS',date:data.asOf,status:'ACTIVE',values:{startingBalance:'10',earned:'0',used:'0',endingBalance:'10'}},{transactionId:'empty',receiptNumber:'EMPTY-OBS',date:data.asOf,status:'ACTIVE',values:{startingBalance:null,earned:null,used:null,endingBalance:null}}]
 vi.spyOn(api,'workspaceAt').mockResolvedValue(data);vi.spyOn(api,'account').mockResolvedValue(report)
 render(<RewardsAccounts data={data} api={api} writable onReceipt={vi.fn()}/>);await user.click(await screen.findByRole('button',{name:'Walgreens Cash'}));await screen.findByText('Day reward 1');expect(screen.queryByText('Day reward 7')).not.toBeInTheDocument()
 fireEvent.change(screen.getByLabelText('Search Account By Day'),{target:{value:data.asOf}});expect(screen.getByText('Day reward 7')).toBeVisible();expect(screen.getByText('Account Activity').closest('section')).toHaveTextContent(/Showing 7 of 7 matching entries/i)
 await user.click(screen.getByText('Provider-Reported Observations — Show / Hide'));expect(screen.getByRole('button',{name:/WG-OBS/})).toBeVisible();expect(screen.queryByRole('button',{name:/WALMART-OBS/})).not.toBeInTheDocument();expect(screen.queryByRole('button',{name:/EMPTY-OBS/})).not.toBeInTheDocument()
 expect(screen.queryByRole('button',{name:'About Walgreens Cash account activity'})).not.toBeInTheDocument()
 for(const name of ['About Dates & Reward Search','About Account Balances','About Account Activity','About Provider-Reported Observations','About Conversions & Payouts','About Expired, Lost & Other Unavailable Rewards'])expect(screen.getByRole('button',{name})).toBeVisible()
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

