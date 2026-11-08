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
  expect(screen.queryByRole('button',{name:'Receipt 1151',hidden:true})).not.toBeInTheDocument()
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
 expect(mutate.mock.calls[0]).toEqual(['/rewards/register-rewards','POST',{rewardId:expect.any(String),description:'Palmolive RR',registerRewardType:'UNKNOWN',amount:'2',earnedDate:null,expirationDate:null,confirmAvailable:true},expect.any(String)])
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

it('shows available, expected, earned and used rewards in the overview', async () => {
  const data=workspace(),user=userEvent.setup()
  data.rewardsOverview={totalUsed:'2',monthlyEarning:'3',asOf:data.asOf,available:'23',expected:'5',total:'28',knownAvailable:'23',knownExpected:'5',expectedCount:'1',missingBalances:[],missingValuations:[]}
  render(<RewardsAccounts data={data} writable onReceipt={vi.fn()}/>)
  const panel=screen.getByRole('heading',{name:'Rewards Overview'}).closest('section')!
  expect(within(panel).queryByText('Total Rewards')).not.toBeInTheDocument()
  expect(within(panel).getByText('$23.00')).toBeInTheDocument()
  expect(within(panel).getByText('$5.00')).toBeInTheDocument()
  expect(within(panel).getByText('Total Earned')).toBeInTheDocument();expect(within(panel).getByText('Total Used')).toBeInTheDocument();expect(within(panel).getByText('$2.00')).toBeInTheDocument()
  expect(within(panel).getByText('$3.00')).toBeInTheDocument()
  expect(screen.queryByText(/Balances and outstanding expected rewards through/)).not.toBeInTheDocument()
  await user.click(within(panel).getByRole('button',{name:'About Rewards Overview'}))
  expect(screen.getByRole('tooltip')).toHaveTextContent('Search filters account rows only.')
  await user.type(screen.getByLabelText('Search accounts'),'no match')
  expect(within(panel).queryByText('Total Rewards')).not.toBeInTheDocument()
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
 await user.type(screen.getByLabelText('Points Used (Optional)'),'5000');await user.selectOptions(screen.getByLabelText('How Paid (Optional)'),'GIFT_CARD');expect(screen.getByLabelText('Gift Card Last Four Digits (Optional)')).not.toBeRequired()
 await user.type(screen.getByLabelText('Gift Card Brand (Optional)'),'Target');await user.type(screen.getByLabelText('Gift Card Value'),'5');await user.click(screen.getByRole('button',{name:'Save Payout'}))
 expect(mutate).toHaveBeenCalledWith('/accounts/payouts','POST',expect.objectContaining({provider:'Fetch',points:'5000',amount:'5',destination:'GIFT_CARD',brand:'Target',number:null,status:'REQUESTED'}),expect.any(String));expect(saved).toHaveBeenCalled()
})


it('focuses Rebates on follow-up and keeps all reward details accessible from Rewards',async()=>{
 const data=workspace();data.receiptEarnings=Array.from({length:7},(_,i)=>({receiptId:`r${i}`,receiptNumber:`R${i}`,receiptStatus:'ACTIVE',earning:{earningId:`e${i}`,kind:'THIRD_PARTY_REBATE',provider:'Ibotta',offerReference:`Offer ${i}`,scope:'RECEIPT',lineItemIds:[],status:'CREDITED',expected:null,confirmed:{kind:'CASH',amount:'1',currency:'USD',program:null},valuation:null}})) as Workspace['receiptEarnings']
 const {unmount}=render(<WorkspacePages page="rebates" data={data} writable onPage={vi.fn()} onReceipt={vi.fn()} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/>)
 expect(screen.queryByText(/Ibotta · Offer/)).not.toBeInTheDocument();await userEvent.setup().selectOptions(screen.getByLabelText('Submission Filter'),'ALL');expect(screen.getAllByText(/Ibotta · Offer/)).toHaveLength(5);await userEvent.setup().click(screen.getByRole('button',{name:'Show All Rebates'}));expect(screen.getAllByText(/Ibotta · Offer/)).toHaveLength(7);expect(screen.queryByText('Add Missing Rewards')).not.toBeInTheDocument();unmount()
 data.balances=[{provider:'Ibotta',balance:{monthUsd:'0',startUsd:'0',endUsd:'0',startPoints:null,endPoints:null}}];
 const api=new ApiClient('test');vi.spyOn(api,'workspaceAt').mockResolvedValue(data);const user=userEvent.setup()
 render(<RewardsAccounts data={data} api={api} writable onReceipt={vi.fn()}/>);expect(screen.queryByRole('button',{name:'Add Missing Rewards'})).not.toBeInTheDocument();await user.click(await screen.findByRole('button',{name:'Ibotta'}));await user.click(screen.getByRole('button',{name:'Add Missing Rewards'}));expect(screen.getByRole('heading',{name:'Add Missing Rewards'})).toBeInTheDocument();expect(screen.queryByRole('button',{name:'Add member reward without a receipt'})).not.toBeInTheDocument()
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
 await user.click(screen.getByRole('button',{name:'Expand Provider-Reported Observations'}));expect(screen.getByRole('button',{name:/WG-OBS/})).toBeVisible();expect(screen.queryByRole('button',{name:/WALMART-OBS/})).not.toBeInTheDocument();expect(screen.queryByRole('button',{name:/EMPTY-OBS/})).not.toBeInTheDocument()
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



it('saves an expiration date for a missing Register Reward',async()=>{
 const api=new ApiClient('test'),user=userEvent.setup(),saved=vi.fn()
 const mutate=vi.spyOn(api,'mutate').mockResolvedValue({})
 render(<MemberRewardEntry provider="Register Rewards" api={api} writable day="2026-11-03" initialOpen onSaved={saved}/>)
 await user.type(screen.getByLabelText('Member reward amount'),'5')
 await user.type(screen.getByLabelText('Member reward name / description'),'Crest reward')
 const expiration=screen.getByLabelText('Register Reward expiration date (optional)')
 expect(expiration).toHaveAttribute('type','date')
 expect(expiration).toHaveAttribute('min','2026-11-03')
 fireEvent.change(expiration,{target:{value:'2026-11-17'}})
 await user.click(screen.getByRole('button',{name:'Save member reward'}))
 await waitFor(()=>expect(mutate).toHaveBeenCalledWith('/accounts/member-rewards','POST',expect.objectContaining({provider:'Register Rewards',amount:'5',expirationDate:'2026-11-17'}),expect.any(String)))
 expect(saved).toHaveBeenCalledOnce()
})

it('organizes RR inventory into aligned rows and keeps secondary workflows collapsed but reachable',async()=>{
 const data=workspace(),api=new ApiClient('test'),user=userEvent.setup();data.asOf=new Date().toLocaleDateString('sv-SE');const report=pointsReport('Register Rewards',data.asOf);report.ending.endUsd='34';data.balances=[{provider:'Register Rewards',balance:report.ending}]
 vi.spyOn(api,'workspaceAt').mockResolvedValue(data);vi.spyOn(api,'account').mockResolvedValue(report);vi.spyOn(api,'standaloneRewards').mockResolvedValue(['5','10','6','6','4','3'].map((amount,i)=>({rewardId:`reward-${i}`,description:`Reward ${i}`,amount,earnedDate:null,expirationDate:null,status:'UNUSED'})))
 render(<RewardsAccounts data={data} api={api} writable onReceipt={vi.fn()}/>);await user.click(await screen.findByRole('button',{name:'Register Rewards'}));await screen.findByRole('heading',{name:'Available Register Rewards'});
 const inventory=screen.getByRole('heading',{name:'Available Register Rewards'}).closest('section')!;const filters=screen.getByRole('heading',{name:'Dates & Reward Search'}).closest('section')!;expect(filters.compareDocumentPosition(inventory)&Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();await waitFor(()=>expect(within(inventory).getAllByRole('row')).toHaveLength(7));expect(inventory).toHaveTextContent('6 individual rewards');expect(inventory).toHaveTextContent('$34.00');expect(within(inventory).getAllByRole('columnheader')).toHaveLength(4)
 for(const row of within(inventory).getAllByRole('row').slice(1))expect(within(row).getAllByRole('cell')).toHaveLength(4)
 const activity=screen.getByRole('heading',{name:'Account Activity'}).closest('section')!;const columnCount=within(activity).getAllByRole('columnheader').length;for(const row of within(activity).getAllByRole('row').slice(1))expect(within(row).getAllByRole('cell')).toHaveLength(columnCount)
 const unavailable=screen.getByText('Expired, lost & unavailable rewards').closest('details')!;expect(unavailable).not.toHaveAttribute('open');expect(screen.getByText('Rewards without receipts').closest('details')).not.toHaveAttribute('open')
 await user.click(screen.getByRole('link',{name:'Expired Rewards'}));expect(unavailable).toHaveAttribute('open');expect(screen.getByRole('button',{name:'Mark reward unavailable'})).toBeVisible()
 const from=screen.getByLabelText('From',{exact:true});await user.clear(from);expect(screen.getByLabelText('From',{exact:true})).toBeVisible();await user.type(screen.getByLabelText('From',{exact:true}),report.from);expect(screen.getByLabelText('From',{exact:true})).toHaveValue(report.from)
})

it('uses the shared beginning/accrual/spending/ending account report for Gift Cards',async()=>{
 const data=workspace(),api=new ApiClient('test'),user=userEvent.setup();data.balances=[{provider:'Gift Cards',balance:{monthUsd:'0',startUsd:'0',endUsd:'113.44',startPoints:null,endPoints:null}}];
 vi.spyOn(api,'workspaceAt').mockResolvedValue(data);vi.spyOn(api,'account').mockImplementation(async(_provider,from,to)=>({provider:'Gift Cards',from,to,beginning:{opening:null,startUsd:'0',startPoints:null,monthUsd:'0',endUsd:'0',endPoints:null},ending:{endUsd:'113.44',startUsd:'0',monthUsd:'0',startPoints:null,endPoints:null},earned:'150',spent:'36.56',earnedPoints:null,spentPoints:null,usdActivityComplete:true,activity:[]}));
 vi.spyOn(api,'rewardInstruments').mockResolvedValue({instruments:[],cardSummary:{recordedOpenings:'150',recordedRedemptions:'36.56',knownRemaining:'113.44',unknownBalanceCount:0,activeCards:3}});render(<RewardsAccounts data={data} api={api} writable onReceipt={vi.fn()}/>);await user.click(screen.getByRole('button',{name:'Manage gift cards'}));
 await screen.findByRole('heading',{name:'Current Active Gift Cards'});expect(screen.getByRole('button',{name:'About Account Balances'})).toBeInTheDocument();
 const balance=screen.getByRole('heading',{name:'Account Balances'}),cards=screen.getByRole('heading',{name:'Current Active Gift Cards'});expect(balance.compareDocumentPosition(cards)&Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();expect((await screen.findAllByText('$113.44')).length).toBeGreaterThan(0);
 const summary=balance.closest('section')!;expect(summary).toHaveTextContent('Beginning Balance');expect(summary).toHaveTextContent('Earned / accrued');expect(summary).toHaveTextContent('Used / Spent');expect(summary).toHaveTextContent('Ending Balance');expect(summary).toHaveTextContent('$150.00');expect(summary).toHaveTextContent('$36.56');expect(summary).toHaveTextContent('$113.44');expect(screen.queryByRole('heading',{name:'Provider Ledger Balances',hidden:true})).not.toBeInTheDocument();
 const add=screen.getByRole('link',{name:'Add Gift Card'});expect(add).toHaveAttribute('href','#account-add-gift-card');const entry=screen.getByText('Add a gift card').closest('details');expect(entry).not.toHaveAttribute('open');await user.click(add);expect(entry).toHaveAttribute('open');expect(screen.getByLabelText('Retailer or reward program')).toHaveFocus();
});

it('returns to the account overview when Rewards & Accounts is selected again',async()=>{
 const data=workspace(),api=new ApiClient('test'),user=userEvent.setup();vi.spyOn(api,'workspaceAt').mockResolvedValue(data);vi.spyOn(api,'rewardInstruments').mockResolvedValue({instruments:[]});
 const props={data,api,writable:true,page:'rewards' as const,onPage:vi.fn(),onReceipt:vi.fn(),onEdit:vi.fn(),onAddReceipt:vi.fn(),onCheckout:vi.fn()};
 const view=render(<WorkspacePages {...props} rewardsNavigationKey={1}/>);await user.click(screen.getByRole('button',{name:'Manage gift cards'}));expect(screen.getByRole('heading',{name:'Gift Cards'})).toBeInTheDocument();view.rerender(<WorkspacePages {...props} rewardsNavigationKey={2}/>);expect(screen.getByRole('heading',{name:'Rewards & Accounts'})).toBeInTheDocument();
});
it('shows recording-time provenance without inventing actual transaction time',async()=>{
 const data=workspace(),api=new ApiClient('test'),user=userEvent.setup(),report=pointsReport('Ibotta',new Date().toLocaleDateString('sv-SE'));data.balances=[{provider:'Ibotta',balance:report.ending}];report.activity=[{movement:{date:report.to,time:null,provider:'Ibotta',usd:'2',points:null,source:'manual',receiptId:null,eventId:'manual'},recordedAt:'2026-11-07T22:35:00Z',receiptNumber:null,description:'Recorded bonus',beforeUsd:null,afterUsd:null,beforePoints:null,afterPoints:null,orderKnown:false,rewards:[]}];
 vi.spyOn(api,'workspaceAt').mockResolvedValue(data);vi.spyOn(api,'account').mockResolvedValue(report);vi.spyOn(api,'rewardInstruments').mockResolvedValue({instruments:[]});render(<RewardsAccounts data={data} api={api} writable onReceipt={vi.fn()}/>);await user.click(await screen.findByRole('button',{name:'Ibotta'}));expect(await screen.findByRole('cell',{name:'4:35 PM'})).toBeInTheDocument();expect(screen.queryByText(/transaction time unknown/)).not.toBeInTheDocument();await user.click(screen.getByRole('button',{name:'Recorded bonus'}));expect(screen.getByRole('dialog',{name:'Promotion details'})).toHaveTextContent('transaction time unknown');expect(screen.getByRole('dialog',{name:'Promotion details'})).toHaveTextContent('4:35 PM CST');expect(report.activity[0].movement.time).toBeNull();expect(report.activity[0].orderKnown).toBe(false);
});
it('edits and voids manually added rewards through their original audited record',async()=>{
 const data=workspace(),api=new ApiClient('test'),user=userEvent.setup(),report=pointsReport('Ibotta',new Date().toLocaleDateString('sv-SE'));data.balances=[{provider:'Ibotta',balance:report.ending}];data.manualRewards=[{id:'manual',provider:'Ibotta',description:'Manual bonus',amount:'5',date:'2026-11-07',expirationDate:null,status:'ACTIVE'}];vi.spyOn(api,'workspaceAt').mockResolvedValue(data);vi.spyOn(api,'account').mockResolvedValue(report);vi.spyOn(api,'rewardInstruments').mockResolvedValue({instruments:[]});vi.spyOn(api,'memberReward').mockResolvedValue({amount:'5',points:null,provider:'Ibotta',description:'Manual bonus',date:'2026-11-07',token:'original'});const mutate=vi.spyOn(api,'mutate').mockResolvedValue({});vi.spyOn(window,'confirm').mockReturnValue(true);render(<RewardsAccounts data={data} api={api} writable onReceipt={vi.fn()}/>);await user.click(await screen.findByRole('button',{name:'Ibotta'}));await user.click(await screen.findByRole('button',{name:'View / Edit / Void'}));await user.clear(await screen.findByLabelText('Reward name / description'));await user.type(screen.getByLabelText('Reward name / description'),'Corrected bonus');await user.click(screen.getByRole('button',{name:'Save reward correction'}));expect(mutate).toHaveBeenCalledWith('/accounts/member-rewards/manual','PUT',expect.objectContaining({expectedToken:'original',expectedAmount:'5',details:expect.objectContaining({description:'Corrected bonus'})}),expect.any(String));
 await user.click(screen.getByRole('button',{name:'View / Edit / Void'}));await screen.findByLabelText('Reward name / description');await user.click(screen.getByRole('button',{name:'Void reward'}));expect(mutate).toHaveBeenLastCalledWith('/accounts/member-rewards/manual','DELETE',expect.objectContaining({expectedToken:'original',correctionId:expect.any(String)}),expect.any(String));
});

it('records a received gift-card payout directly from a manually added reward',async()=>{
 const data=workspace(),api=new ApiClient('test'),user=userEvent.setup(),report=pointsReport('Fetch',new Date().toLocaleDateString('sv-SE'));data.balances=[{provider:'Fetch',balance:report.ending}];data.manualRewards=[{id:'bonus',provider:'Fetch',description:'Saved reward',amount:'60',date:'2026-11-07',expirationDate:null,status:'ACTIVE'}];vi.spyOn(api,'workspaceAt').mockResolvedValue(data);vi.spyOn(api,'account').mockResolvedValue(report);vi.spyOn(api,'rewardInstruments').mockResolvedValue({instruments:[]});vi.spyOn(api,'memberReward').mockResolvedValue({amount:'60',points:'60000',provider:'Fetch',description:'Saved reward',date:'2026-11-07',token:'original'});const mutate=vi.spyOn(api,'mutate').mockResolvedValue({});render(<RewardsAccounts data={data} api={api} writable onReceipt={vi.fn()}/>);await user.click(await screen.findByRole('button',{name:'Fetch'}));await user.click(await screen.findByRole('button',{name:'View / Edit / Void'}));await screen.findByLabelText('Reward name / description');await user.click(screen.getByRole('button',{name:'Record redemption / payout'}));await user.selectOptions(screen.getByLabelText('How Paid (Optional)'),'GIFT_CARD');await user.selectOptions(screen.getByLabelText('Payout Status'),'RECEIVED');await user.type(screen.getByLabelText('Gift Card Value'),'50');await user.type(screen.getByLabelText('Points Used (Optional)'),'55000');await user.type(screen.getByLabelText('Gift Card Brand (Optional)'),'Visa Prepaid Card');await user.type(screen.getByLabelText('Gift Card Last Four Digits (Optional)'),'4668');await user.click(screen.getByRole('button',{name:'Save Payout'}));expect(mutate).toHaveBeenCalledWith('/accounts/payouts','POST',expect.objectContaining({sourceRewardId:'bonus',provider:'Fetch',destination:'GIFT_CARD',status:'RECEIVED',amount:'50',points:'55000',brand:'Visa Prepaid Card',number:'4668'}),expect.any(String));
});

it.each(['Gift Cards','Walgreens Cash','ECB','Fetch'])('keeps %s activity rows compact and opens promotion actions in a dialog',async provider=>{
 const data=workspace(),api=new ApiClient('test'),user=userEvent.setup(),report=pointsReport(provider,new Date().toLocaleDateString('sv-SE'));data.balances=[{provider,balance:report.ending}];report.activity=[{movement:{date:report.to,time:null,provider,usd:'50',points:null,source:'Redemption payout-id | received',receiptId:null,eventId:null},recordedAt:'2026-11-05T20:58:00Z',receiptNumber:null,description:'Redemption | Received',beforeUsd:null,afterUsd:null,beforePoints:null,afterPoints:null,orderKnown:false,rewards:[]}];vi.spyOn(api,'account').mockResolvedValue(report);vi.spyOn(api,'workspaceAt').mockResolvedValue(data);vi.spyOn(api,'rewardInstruments').mockResolvedValue({instruments:[]});render(<RewardsAccounts data={data} api={api} writable onReceipt={vi.fn()}/>);if(provider==='Gift Cards')await user.click(screen.getByRole('button',{name:'Manage gift cards'}));else await user.click(await screen.findByRole('button',{name:provider}));expect(await screen.findByRole('cell',{name:'2:58 PM'})).toBeInTheDocument();expect(screen.queryByRole('button',{name:'View / Correct Payout'})).not.toBeInTheDocument();await user.click(screen.getByRole('button',{name:'Redemption | Received'}));const dialog=screen.getByRole('dialog',{name:'Promotion details'});expect(within(dialog).getByRole('button',{name:'View / Correct Payout'})).toBeInTheDocument();if(provider==='Gift Cards')expect(within(dialog).getByRole('button',{name:'View / Correct Card'})).toBeInTheDocument();expect(dialog).toHaveTextContent('transaction time unknown');await user.keyboard('{Escape}');expect(screen.queryByRole('dialog',{name:'Promotion details'})).not.toBeInTheDocument();
});
