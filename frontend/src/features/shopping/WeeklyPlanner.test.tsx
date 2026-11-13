import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
import { ApiClient } from '../../api/client'
import { workspace, planningRow, receiptRow } from '../../api/fixtures.test-support'
import type { PlanScenario, ScenarioEstimate } from '../../types/platform'
import { WeeklyPlanner } from './WeeklyPlanner'
import { WrittenDeal } from './WrittenDeal'
import { PlanScenarioEditor } from '../../pages/WorkspacePages'

it('sends saved payment restrictions and recorded reward origins without inferring a program from its name',async()=>{
 const data=workspace(),api=new ApiClient('test'),user=userEvent.setup(),row=planningRow()
 const scenario:PlanScenario={items:[{id:'line',name:'Pampers',sku:null,regular:'11',sale:'11',quantity:'3'}],coupons:[],promotions:[{id:'pg',paymentRulesReviewed:true,description:'P&G',rewardType:'REGISTER_REWARD',rewardAmount:'10',requiredQuantity:'0',requiredSpend:'30',spendBasis:'BEFORE_COUPONS',allowsWalgreensCash:false,lineIds:['line'],prohibitedRegisterRewardProgram:'P&G'}],tax:'2.60',walgreensCashUsed:'0',registerRewardsUsed:'0',giftCardUsed:'0',manualOop:null,paymentMethod:'CASH',fundingNotes:'',payments:[{id:'payment',type:'REGISTER_REWARD',amount:'6',notes:'Crest',rewardSourceId:'crest',originProduct:'Crest',originProgram:'P&G',originPromotion:'B3G6',registerRewardType:'STORE'}],walgreensCashIncrement:'5',walgreensCashLimit:'10'}
 row.plan={...row.plan,planId:'pampers',name:'Pampers',retailer:'Walgreens',purchasedReceiptId:null,shoppingDate:'2026-11-06',scenario,organization:{stage:'TRIP',alternativeOf:null,checkoutOrder:0,rewardSources:[]}};data.plans=[row];data.receipts=[];data.receiptEarnings=[];data.rrMatches=[]
 vi.spyOn(api,'standaloneRewards').mockResolvedValue([{rewardId:'crest',description:'Crest',amount:'6',registerRewardType:'STORE',originProduct:'Crest',originProgram:'P&G',originPromotion:'B3G6',earnedDate:null,expirationDate:null,status:'UNUSED'},{rewardId:'unknown',description:'P&G named but origin unrecorded',amount:'10',earnedDate:null,expirationDate:null,status:'UNUSED'}])
 const mutate=vi.spyOn(api,'mutate').mockResolvedValue({steps:[],cash:'0',earnings:'0',remaining:'20',notice:'Bounded search',reviews:[{candidateId:'pampers',candidateName:'Pampers',rewardId:'unknown',rewardName:'Unknown RR',status:'Needs Review'}]})
 render(<WeeklyPlanner data={data} api={api} day="2026-11-06" from="2026-11-02" to="2026-11-08"/>);await user.click(screen.getByText('Plan my shopping trip'));await user.selectOptions(screen.getByLabelText('Shopping mode'),'MY_DEALS');await user.click(screen.getByRole('button',{name:'Suggest my shopping plan'}));await screen.findByText(/Unknown RR — reward origin needs review/)
 expect(mutate).toHaveBeenCalledWith('/shopping-plans/weekly-preview','POST',expect.objectContaining({candidates:[expect.objectContaining({scenario:expect.objectContaining({walgreensCashIncrement:'5',walgreensCashLimit:'10',promotions:[expect.objectContaining({allowsWalgreensCash:false,paymentRulesReviewed:true,prohibitedRegisterRewardProgram:'P&G'})]})})],rewards:expect.arrayContaining([expect.objectContaining({id:'crest',originProduct:'Crest',originProgram:'P&G',originPromotion:'B3G6',registerRewardType:'STORE'}),expect.objectContaining({id:'unknown',originProgram:undefined})])}),expect.any(String))
})

it('renders every funding term and the server checkout result with promotion payment status',()=>{
 const report={items:[],coupons:[],promotions:[{id:'pg',paymentRulesReviewed:true,description:'P&G',eligible:false,paymentRuleStatus:'Needs Review',spendMet:true}],merchandiseAfterCoupons:'31.49',couponTotal:'5.50',registerRewardsUsed:'24',tax:'2.60',walgreensCashUsed:'10',ecbUsed:'0',giftCardUsed:'0',walmartCashUsed:'0',calculatedOop:'0.09',potentialRewards:'0'} as unknown as ScenarioEstimate
 render(<WrittenDeal report={report}/>);
 expect(screen.getByText('$31.49 − $24.00 RR + $2.60 tax − $10.00 Walgreens Cash = $0.09 cash/card')).toBeInTheDocument()
 expect(screen.getByText('P&G · Payment Rule: Needs Review · Spend: Met')).toBeInTheDocument()
})

it('groups reward warnings into one original reward editor and reevaluates after saving',async()=>{
 const data=workspace(),api=new ApiClient('test'),user=userEvent.setup(),onReview=vi.fn(),first=planningRow(),second=planningRow()
 const scenario:PlanScenario={items:[{id:'line',name:'Item',sku:null,regular:'10',sale:null,quantity:'1'}],coupons:[],promotions:[],tax:'0',walgreensCashUsed:'0',registerRewardsUsed:'0',giftCardUsed:'0',manualOop:null,paymentMethod:'CASH',fundingNotes:'',payments:[{id:'selected',type:'REGISTER_REWARD',amount:'6',notes:'Original',rewardSourceId:'source'}]}
 first.plan={...first.plan,planId:'a',name:'Pampers',shoppingDate:'2026-11-06',scenario};second.plan={...second.plan,planId:'b',name:'Tide',shoppingDate:'2026-11-06',scenario:{...scenario,payments:[]}}
 data.plans=[first,second];data.receipts=[];data.receiptEarnings=[];data.rrMatches=[]
 vi.spyOn(api,'standaloneRewards').mockResolvedValue([{rewardId:'source',description:'Crest $6',amount:'6',earnedDate:null,expirationDate:null,status:'UNUSED'}])
 const reviews=['a','b','a'].map(id=>({candidateId:id,candidateName:id==='a'?'Pampers':'Tide',rewardId:'source',rewardName:'Crest $6',status:'Needs Review',field:'payment.originProgram',message:'Record the originating program.'}))
 const mutate=vi.spyOn(api,'mutate').mockResolvedValueOnce({steps:[],cash:'0',earnings:'0',remaining:'20',notice:'Bounded search',emptyReason:'ELIGIBILITY',reviews}).mockResolvedValueOnce({steps:[],cash:'0',earnings:'0',remaining:'20',notice:'Bounded search',emptyReason:'ELIGIBILITY',reviews:[reviews[0]]})
 const view=render(<WeeklyPlanner data={data} api={api} day="2026-11-06" from="2026-11-02" to="2026-11-08" onReview={onReview}/>);await user.click(screen.getByText('Plan my shopping trip'));await user.click(screen.getByRole('button',{name:'Suggest my shopping plan'}))
 expect(await screen.findByText('Crest $6 — reward origin needs review.')).toBeInTheDocument();expect(screen.getAllByRole('button',{name:'Edit this reward'})).toHaveLength(1)
 await user.click(screen.getByRole('button',{name:'Edit this reward'}));await screen.findByRole('dialog');expect(screen.getByLabelText('Origin program')).toBeInTheDocument();expect(onReview).not.toHaveBeenCalled()
 view.rerender(<WeeklyPlanner data={{...data}} api={api} day="2026-11-06" from="2026-11-02" to="2026-11-08" onReview={onReview}/>);
 await waitFor(()=>expect(mutate).toHaveBeenCalledTimes(2));expect(screen.getAllByRole('button',{name:'Edit this reward'})).toHaveLength(1)
})

it('keeps genuine insufficient-budget results distinct from editable eligibility reviews',async()=>{
 const data=workspace(),api=new ApiClient('test'),user=userEvent.setup();data.plans=[];data.receipts=[];data.receiptEarnings=[]
 vi.spyOn(api,'standaloneRewards').mockResolvedValue([]);vi.spyOn(api,'mutate').mockResolvedValue({steps:[],cash:'0',earnings:'0',remaining:'20',notice:'Bounded search',emptyReason:'BUDGET',reviews:[]})
 render(<WeeklyPlanner data={data} api={api} day="2026-11-06" from="2026-11-02" to="2026-11-08"/>);await user.click(screen.getByText('Plan my shopping trip'));await user.click(screen.getByRole('button',{name:'Suggest my shopping plan'}));expect(await screen.findByText(/No complete eligible deal fits the remaining cash budget/)).toBeInTheDocument();expect(screen.queryByRole('button',{name:/Review .* funding/})).not.toBeInTheDocument()
})

it('opens the existing exact promotion rule control without approving it',async()=>{
 const api=new ApiClient('test'),data=workspace(),row=planningRow()
 row.plan.scenario={items:[{id:'line',name:'Pampers',sku:null,regular:'11',sale:null,quantity:'3'}],coupons:[],promotions:[{id:'booster',description:'Spend 35 earn 7',rewardType:'WALGREENS_CASH',rewardAmount:'7',requiredQuantity:'0',requiredSpend:'35',spendBasis:'AFTER_REWARDS_USED',allowsWalgreensCash:true,lineIds:['line']}],tax:'0',walgreensCashUsed:'0',registerRewardsUsed:'0',giftCardUsed:'0',manualOop:null,paymentMethod:'CASH',fundingNotes:''}
 vi.spyOn(api,'mutate').mockResolvedValue({items:[],coupons:[],promotions:[]})
 render(<PlanScenarioEditor plan={row.plan} data={data} api={api} writable onClose={vi.fn()} onReceipt={vi.fn()} reviewTarget={{rewardId:'WALGREENS_CASH',promotionId:'booster',field:'promotion.paymentRulesReviewed',message:'Review payment-sensitive spend rules.'}}/>);
 const control=screen.getByLabelText('Payment rules reviewed 1');await waitFor(()=>expect(control).toHaveFocus());expect(control).not.toBeChecked();expect(control.closest('details')).toHaveAttribute('open')
})

it('selects a recorded Must Buy item, edits quantity and requests automatic funding without manual payments',async()=>{
 const data=workspace(),api=new ApiClient('test'),user=userEvent.setup(),row=planningRow()
 row.plan={...row.plan,planId:'pampers',name:'Pampers deal',shoppingDate:'2026-11-06',scenario:{items:[{id:'recorded-line',name:'Pampers',sku:'original-sku',regular:'11',sale:'10',quantity:'1'}],coupons:[],promotions:[],tax:'0',walgreensCashUsed:'0',registerRewardsUsed:'0',giftCardUsed:'0',manualOop:null,paymentMethod:'CASH',fundingNotes:''}}
 data.plans=[row];data.receipts=[];vi.spyOn(api,'standaloneRewards').mockResolvedValue([])
 const mutate=vi.spyOn(api,'mutate').mockResolvedValue({steps:[],cash:'30',earnings:'0',remaining:'0',affordable:false,requirementsMet:true,additionalCashNeeded:'10',requiredItems:[{candidateId:'pampers',lineId:'recorded-line',name:'Pampers',quantity:'3'}],notice:'Bounded search'})
 render(<WeeklyPlanner data={data} api={api} day="2026-11-06" from="2026-11-02" to="2026-11-08"/>);await user.click(screen.getByText('Plan my shopping trip'));await user.selectOptions(screen.getByLabelText('Shopping mode'),'MY_DEALS');await user.selectOptions(screen.getByLabelText('Add a required item'),JSON.stringify(['pampers','recorded-line']))
 const quantity=screen.getByLabelText('Required quantity Pampers');await user.clear(quantity);await user.type(quantity,'3');expect(screen.getByLabelText('Must Buy Pampers')).toBeChecked();expect(screen.getByLabelText('Include additional suggested deals')).not.toBeChecked()
 await user.click(screen.getByRole('button',{name:'Suggest my shopping plan'}));await screen.findByText(/Additional cash needed for these purchases/)
 expect(mutate).toHaveBeenCalledWith('/shopping-plans/weekly-preview','POST',expect.objectContaining({requiredItems:[{candidateId:'pampers',lineId:'recorded-line',quantity:3,name:'Pampers'}],includeOptional:false}),expect.any(String));expect(screen.getByRole('region',{name:'Required shopping list'})).toHaveTextContent('Pampers · Quantity 3');expect(screen.getByText('$10.00')).toBeInTheDocument()
 await user.click(screen.getByRole('button',{name:'Remove Pampers'}));expect(screen.queryByLabelText('Required quantity Pampers')).not.toBeInTheDocument();expect(screen.queryByRole('region',{name:'Required shopping list'})).not.toBeInTheDocument()
})

it('submits the combined multi-retailer request with a quantity-three Must Buy and incomplete optional advertisement',async()=>{
 const data=workspace(),api=new ApiClient('test'),user=userEvent.setup(),row=planningRow()
 row.plan={...row.plan,planId:'trash',name:'Walgreens Trash Bag',retailer:'Walgreens',shoppingDate:'2026-11-12',scenario:{items:[{id:'trash-line',name:'Walgreens Trash Bag',sku:'trash-sku',regular:'9.99',sale:null,quantity:'1'}],coupons:[],promotions:[],tax:'0',walgreensCashUsed:'0',registerRewardsUsed:'0',giftCardUsed:'0',manualOop:null,paymentMethod:'CASH',fundingNotes:''}}
 data.plans=[row];data.receipts=[];data.deals=[{dealId:'bad-walmart-ad',title:'Incomplete Walmart offer',interpretedTerms:'Price missing',retailer:'Walmart',validFrom:'2026-11-08',validThrough:'2026-11-14',sourceArtifactIds:[],revision:'1',pricing:{type:'ADVERTISEMENT',promotionalPrice:'0',advertisement:{draftJson:JSON.stringify({items:[{id:'bad-line',name:'Unpriced item',sku:null,regular:'',sale:null,quantity:'1'}],coupons:[],promotions:[]}),fields:{},warnings:['Price missing']}}}]
 vi.spyOn(api,'standaloneRewards').mockResolvedValue([]);const mutate=vi.spyOn(api,'mutate').mockResolvedValue({steps:[{id:'trash',name:'Walgreens Trash Bag',store:'Walgreens',scenario:row.plan.scenario,estimate:{items:[],coupons:[],promotions:[],couponTotal:'0',merchandiseAfterCoupons:'29.97',registerRewardsUsed:'0',walgreensCashUsed:'0',giftCardUsed:'0',tax:'0',calculatedOop:'29.97',selectedOop:'29.97',manualDifference:'0',overFunding:'0',potentialRewards:'0',afterPotentialRewards:'29.97'}}],cash:'29.97',earnings:'0',remaining:'970.03',requirementsMet:true,requiredItems:[{candidateId:'trash',lineId:'trash-line',quantity:'3',name:'Walgreens Trash Bag'}],notice:'Bounded search'})
 render(<WeeklyPlanner data={data} api={api} day="2026-11-12" from="2026-11-08" to="2026-11-14"/>);await user.click(screen.getByText('Plan my shopping trip'));await user.selectOptions(screen.getByLabelText('Shopping mode'),'COMBINED');await user.selectOptions(screen.getByLabelText('Add a required item'),JSON.stringify(['trash','trash-line']));const quantity=screen.getByLabelText('Required quantity Walgreens Trash Bag');await user.clear(quantity);await user.type(quantity,'3');await user.click(screen.getByLabelText('Include additional suggested deals'));await user.clear(screen.getByLabelText('Weekly cash budget'));await user.type(screen.getByLabelText('Weekly cash budget'),'1000');await user.click(screen.getByLabelText('Allow Rewards From Earlier Transactions'));await user.click(screen.getByLabelText('Use eligible gift cards'));await user.click(screen.getByRole('button',{name:'Suggest my shopping plan'}))
 await waitFor(()=>expect(mutate).toHaveBeenCalledWith('/shopping-plans/weekly-preview','POST',expect.objectContaining({requiredItems:[{candidateId:'trash',lineId:'trash-line',quantity:3,name:'Walgreens Trash Bag'}],includeOptional:true,budget:'1000.00',allowRewardsFromEarlierTransactions:true,useGiftCards:true,candidates:expect.arrayContaining([expect.objectContaining({id:'trash',store:'Walgreens',scenario:expect.objectContaining({items:[expect.objectContaining({name:'Walgreens Trash Bag',regular:'9.99'})]})}),expect.objectContaining({id:'advertisement:bad-walmart-ad',store:'Walmart',scenario:null})])}),expect.any(String)))
 expect(await screen.findByRole('region',{name:'Required shopping list'})).toHaveTextContent('Walgreens Trash Bag · Quantity 3')
})

it('labels recorded receipt spending as historical budget usage before planning',async()=>{
 const data=workspace(),api=new ApiClient('test'),user=userEvent.setup(),receipt=receiptRow('spent','2026-11-12');receipt.totals.savings={...receipt.totals.savings,recordedCashCardComplete:true,recordedCashCardPaid:'59.41'};data.receipts=[receipt]
 render(<WeeklyPlanner data={data} api={api} day="2026-11-12" from="2026-11-08" to="2026-11-14"/>);await user.click(screen.getByText('Plan my shopping trip'));expect(screen.getByText(/Already recorded cash\/card spending for this shopping week:/)).toHaveTextContent('$59.41');expect(screen.getByText(/This amount comes only from saved receipts/)).toBeInTheDocument()
})

it('offers the four distinct shopping strategies',async()=>{
 const api=new ApiClient('test'),user=userEvent.setup()
 render(<WeeklyPlanner data={workspace()} api={api} day="2026-11-06" from="2026-11-02" to="2026-11-08"/>);await user.click(screen.getByText('Plan my shopping trip'));const modes=screen.getByLabelText('Shopping mode');expect(modes).toHaveTextContent('Shop My Deals');expect(modes).toHaveTextContent('Find Best Deals');expect(modes).toHaveTextContent('Shop My Deals + Find Best Deals');expect(modes).toHaveTextContent('Maximize Register Rewards — Lowest Cash OOP')
})

it('sends every eligible imported weekly advertisement to Find Best Deals without copying it into a plan',async()=>{
 const data=workspace(),api=new ApiClient('test'),user=userEvent.setup();data.plans=[];data.receipts=[]
 const draft={items:[{id:'ad-line',name:'Advertised Tide',sku:null,regular:'8',sale:'6',quantity:'1'}],coupons:[],promotions:[]}
 data.deals=[{dealId:'weekly-tide',title:'Weekly Tide',interpretedTerms:'Sale $6',retailer:'Walgreens',validFrom:'2026-11-02',validThrough:'2026-11-08',sourceArtifactIds:[],revision:'1',pricing:{type:'ADVERTISEMENT',promotionalPrice:'6',advertisement:{draftJson:JSON.stringify(draft),fields:{},warnings:[]}}}]
 vi.spyOn(api,'standaloneRewards').mockResolvedValue([]);const mutate=vi.spyOn(api,'mutate').mockResolvedValue({steps:[],cash:'0',earnings:'0',remaining:'20',notice:'Bounded search',reviews:[]})
 render(<WeeklyPlanner data={data} api={api} day="2026-11-06" from="2026-11-02" to="2026-11-08"/>);await user.click(screen.getByText('Plan my shopping trip'));await user.click(screen.getByRole('button',{name:'Suggest my shopping plan'}))
 await waitFor(()=>expect(mutate).toHaveBeenCalledWith('/shopping-plans/weekly-preview','POST',expect.objectContaining({candidates:[expect.objectContaining({id:'advertisement:weekly-tide',name:'Weekly Tide',scenario:expect.objectContaining({items:[expect.objectContaining({name:'Advertised Tide',sale:'6'})]})})]}),expect.any(String)))
})

it('uses the shared optimizer for the Register Reward strategy and reports checkout and reward change separately',async()=>{
 const data=workspace(),api=new ApiClient('test'),user=userEvent.setup();data.receipts=[];vi.spyOn(api,'standaloneRewards').mockResolvedValue([])
 const mutate=vi.spyOn(api,'mutate').mockResolvedValue({steps:[],cash:'1',earnings:'4',remaining:'19',requirementsMet:true,registerRewardsUsedValue:'5',expectedRegisterRewards:'4',netRegisterRewardChange:'-1',notice:'Bounded search',reviews:[]})
 render(<WeeklyPlanner data={data} api={api} day="2026-11-06" from="2026-11-02" to="2026-11-08"/>);await user.click(screen.getByText('Plan my shopping trip'));await user.selectOptions(screen.getByLabelText('Shopping mode'),'MAX_REGISTER_REWARDS');await user.click(screen.getByRole('button',{name:'Suggest my shopping plan'}))
 await waitFor(()=>expect(mutate).toHaveBeenCalledWith('/shopping-plans/weekly-preview','POST',expect.objectContaining({mode:'MAX_REGISTER_REWARDS'}),expect.any(String)));expect(screen.getByRole('region',{name:'Register Reward strategy'})).toHaveTextContent('Register Rewards used: $5.00');expect(screen.getByRole('region',{name:'Register Reward strategy'})).toHaveTextContent('Expected Register Rewards earned: $4.00');expect(screen.getByRole('region',{name:'Register Reward strategy'})).toHaveTextContent('Net Register Reward change: $−1.00')
})

it('does not display an old suggestion after the user changes a Must Buy quantity',async()=>{
 const data=workspace(),row=planningRow(),api=new ApiClient('test'),user=userEvent.setup()
 row.plan={...row.plan,planId:'item',shoppingDate:'2026-11-06',scenario:{items:[{id:'line',name:'Item',sku:null,regular:'10',sale:null,quantity:'1'}],coupons:[],promotions:[],tax:'0',walgreensCashUsed:'0',registerRewardsUsed:'0',giftCardUsed:'0',manualOop:null,paymentMethod:'CASH',fundingNotes:''}};data.plans=[row];data.receipts=[]
 vi.spyOn(api,'standaloneRewards').mockResolvedValue([]);let finish:(value:unknown)=>void=()=>{};const mutate=vi.spyOn(api,'mutate').mockImplementation(()=>new Promise(resolve=>{finish=resolve}))
 render(<WeeklyPlanner data={data} api={api} day="2026-11-06" from="2026-11-02" to="2026-11-08"/>);await user.click(screen.getByText('Plan my shopping trip'));await user.selectOptions(screen.getByLabelText('Shopping mode'),'MY_DEALS');await user.selectOptions(screen.getByLabelText('Add a required item'),JSON.stringify(['item','line']));await user.click(screen.getByRole('button',{name:'Suggest my shopping plan'}));await waitFor(()=>expect(mutate).toHaveBeenCalledOnce());
 const quantity=screen.getByLabelText('Required quantity Item');await user.clear(quantity);await user.type(quantity,'2');await act(async()=>finish({steps:[],cash:'10',earnings:'0',remaining:'10',requirementsMet:true,requiredItems:[{candidateId:'item',lineId:'line',quantity:'1',name:'Item'}],notice:'Old result'}));
 expect(screen.queryByRole('region',{name:'Required shopping list'})).not.toBeInTheDocument();expect(quantity).toHaveValue(2);expect(screen.queryByText(/Old result/)).not.toBeInTheDocument()
})

it('sends retailer, booster, rolling and optional gift-card preferences through the shared optimizer',async()=>{
 const data=workspace(),api=new ApiClient('test'),user=userEvent.setup();data.receipts=[];data.promotions=[{id:'booster',name:'Spend 35 Get 7',threshold:'35',giftCardValue:'7',terms:'Recorded',status:'ACTIVE',start:'2026-11-01',end:'2026-11-30',contributions:{},planningRules:{kind:'SPEND_BOOSTER'}}];
 vi.spyOn(api,'standaloneRewards').mockResolvedValue([]);const mutate=vi.spyOn(api,'mutate').mockResolvedValue({steps:[],cash:'0',earnings:'0',remaining:'20',notice:'Bounded search'});
 render(<WeeklyPlanner data={data} api={api} day="2026-11-07" from="2026-11-02" to="2026-11-08"/>);await user.click(screen.getByText('Plan my shopping trip'));await user.selectOptions(screen.getByLabelText('Walgreens Spend Booster target (optional)'),'booster');await user.selectOptions(screen.getByLabelText('Shopping preference'),'WC_FIRST');await user.click(screen.getByLabelText('Allow Rewards From Earlier Transactions'));await user.click(screen.getByLabelText('Use eligible gift cards'));await user.click(screen.getByLabelText('Prefer gift cards before rewards and cash/credit'));await user.click(screen.getByRole('button',{name:'Suggest my shopping plan'}));
 await waitFor(()=>expect(mutate).toHaveBeenCalledWith('/shopping-plans/weekly-preview','POST',expect.objectContaining({boosterId:'booster',mode:'WC_FIRST',allowRewardsFromEarlierTransactions:true,useGiftCards:true,giftCardFirst:true,walmartCash:'0'}),expect.any(String)));
});

it('accepts blank preference and requires a particular retailer for the fifth reward mode',async()=>{
 const data=workspace(),api=new ApiClient('test'),user=userEvent.setup();data.receipts=[];data.plans=[];data.deals=[];vi.spyOn(api,'standaloneRewards').mockResolvedValue([]);const mutate=vi.spyOn(api,'mutate').mockResolvedValue({steps:[],cash:'0',earnings:'0',remaining:'20',notice:'test'})
 render(<WeeklyPlanner data={data} api={api} day="2026-11-09" from="2026-11-08" to="2026-11-14"/>);await user.click(screen.getByText('Plan my shopping trip'))
 expect(screen.getByLabelText('Shopping preference')).toHaveValue('');await user.click(screen.getByRole('button',{name:'Suggest my shopping plan'}));await waitFor(()=>expect(mutate).toHaveBeenCalledWith('/shopping-plans/weekly-preview','POST',expect.objectContaining({mode:'AUTO'}),expect.any(String)))
 await user.selectOptions(screen.getByLabelText('Shopping mode'),'MAX_REWARDS');await user.click(screen.getByRole('button',{name:'Suggest my shopping plan'}));expect(await screen.findByRole('alert')).toHaveTextContent('Select a particular retailer');expect(mutate).toHaveBeenCalledTimes(1)
 await user.selectOptions(screen.getByLabelText('Retailers'),'Walgreens');await user.click(screen.getByRole('button',{name:'Suggest my shopping plan'}));await waitFor(()=>expect(mutate).toHaveBeenCalledWith('/shopping-plans/weekly-preview','POST',expect.objectContaining({mode:'MAX_REWARDS',retailer:'Walgreens'}),expect.any(String)))
 expect(screen.getByRole('option',{name:'Maximize Register Rewards — Lowest Cash OOP'})).toBeInTheDocument();expect(screen.getByRole('option',{name:'Shop My Deals'})).toBeInTheDocument()
})
