import { ProductCouponPicker } from './CouponPicker'
import { useState } from 'react'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiClient } from '../../api/client'
import { workspace, planningRow } from '../../api/fixtures.test-support'
import type { PlanScenario } from '../../types/platform'
import { PlanScenarioEditor } from '../../pages/WorkspacePages'
import { EvidenceField } from '../../components/EvidenceField'
import { QuickDealTools, SaleButtons, RewardPicker } from './QuickDealTools'
import { CompareCosts, ShoppingWeek } from './ShoppingWeek'

const scenario=():PlanScenario=>({items:[{id:'line',name:'Colgate',sku:null,regular:'',sale:'6',quantity:'2'}],coupons:[],promotions:[],tax:'0',walgreensCashUsed:'0',registerRewardsUsed:'0',giftCardUsed:'0',manualOop:null,paymentMethod:'CASH',fundingNotes:''})
function row(id:string,name:string){const r=planningRow();r.plan={...r.plan,planId:id,name,scenario:scenario(),shoppingDate:'2026-10-19',organization:{stage:'IDEA',alternativeOf:null,checkoutOrder:null,rewardSources:[]}};return r}
describe('approved shopping notebook',()=>{
  it('edits and reparses notes, confirms replacement, preserves attachments and names, and recovers from failure',async()=>{
    const api=new ApiClient('test'),user=userEvent.setup(),name=vi.fn()
    const original='Addidas Gift Package 10.00\nBuy One Get One 50% Off\n$4.00 Off 2\n3 Buy 10 + (10/2) + 10=25\n25 - 4 = 21 + Tax\nI Can Use 10 Pg Rr , Use 6 Rr\n21 -10-6=5\nIbotta Get 9.00\nSpend 25 Get 5 Rr'
    const mutate=vi.spyOn(api,'mutate').mockImplementation(async(_path,_method,body)=>{
      const text=(body as {text:string}).text
      return {items:[{id:'adidas',name:'Addidas Gift Package',regular:'10.00',sale:'10.00',quantity:text.includes('4 Buy')?'4':'3',priceRule:{type:'BOGO_HALF'}}],coupons:[{id:'c',description:'Coupon',amount:text.includes('$5.00 Off')?'5.00':'4.00',requiredQuantity:'2',lineIds:['adidas']}],promotions:[],payments:[{id:'rr10',type:'REGISTER_REWARD',amount:'10',notes:'P&G',registerRewardType:'MANUFACTURER'},{id:'rr6',type:'REGISTER_REWARD',amount:'6',notes:'RR',registerRewardType:'MANUFACTURER'}],unresolved:[],source:{text}}
    })
    let saved:PlanScenario|undefined
    function Harness(){const [value,setValue]=useState<PlanScenario>({...scenario(),items:[],source:{text:'',mediaType:'image/png',contentBase64:'AQID'}});return <QuickDealTools api={api} scenario={value} data={workspace()} store="Walgreens" day="2026-10-29" initialMode="notes" onImportedName={name} onChange={next=>{saved=next;setValue(next)}}/>}
    const view=render(<Harness/>);fireEvent.change(screen.getByLabelText('Type or paste your deal notes'),{target:{value:original}})
    await user.click(screen.getByRole('button',{name:'Add to this plan'}));await screen.findByText(/Deal updated from notes/)
    expect(name).toHaveBeenCalledWith('Addidas');expect(saved?.items[0].quantity).toBe('3');expect(saved?.source?.text).toBe(original);expect(saved?.source?.contentBase64).toBe('AQID')
    const confirm=vi.spyOn(window,'confirm').mockReturnValue(false)
    fireEvent.change(screen.getByLabelText('Original deal notes'),{target:{value:original.replace('3 Buy','4 Buy').replace('$4.00 Off','$5.00 Off')}})
    await user.click(screen.getByRole('button',{name:'Update Deal From Notes'}));expect(mutate).toHaveBeenCalledTimes(1);expect(saved?.items[0].quantity).toBe('3')
    confirm.mockReturnValue(true);await user.click(screen.getByRole('button',{name:'Update Deal From Notes'}));await waitFor(()=>expect(saved?.items[0].quantity).toBe('4'));expect(saved?.coupons[0].amount).toBe('5.00');expect(saved?.payments).toHaveLength(2)
    mutate.mockRejectedValueOnce(new Error('Request failed (502)'));await user.click(screen.getByRole('button',{name:'Update Deal From Notes'}));await screen.findByRole('alert');expect(saved?.items[0].quantity).toBe('4');expect(screen.getByLabelText('Original deal notes')).toBeEnabled()
    const persisted=structuredClone(saved!);view.unmount();render(<QuickDealTools api={api} scenario={persisted} data={workspace()} store="Walgreens" day="2026-10-29" onChange={vi.fn()}/>);expect(screen.getByLabelText('Original deal notes')).toHaveValue(persisted.source!.text)
  })

  it.each(['Addidas Gift Package','Qtips','Pampers Diapers Unscented'])('names %s from its product and preserves a manually changed name without assuming tax',async productName=>{
    const api=new ApiClient('test'),user=userEvent.setup()
    vi.spyOn(api,'mutate').mockImplementation(async path=>{
      if(path==='/shopping-plans/import-preview')return {items:[{id:'product',name:productName,regular:'10',sale:null,quantity:'3'}],coupons:[],promotions:[],unresolved:[]}
      throw new Error('No calculation response needed for naming')
    })
    render(<PlanScenarioEditor plan={null} data={workspace()} api={api} writable onClose={vi.fn()} initialMode="notes" onReceipt={vi.fn()}/>)
    expect(screen.getByLabelText('Tax calculation')).toHaveValue('AMOUNT')
    fireEvent.change(screen.getByLabelText('Type or paste your deal notes'),{target:{value:'Here is my deal\nAddidas Gift Package 10.00'}})
    await user.click(screen.getByRole('button',{name:'Add to this plan'}));await waitFor(()=>expect(screen.getByLabelText('Plan name')).toHaveValue(productName.split(' ')[0]))
    fireEvent.change(screen.getByLabelText('Plan name'),{target:{value:'My gift deal'}})
    vi.spyOn(window,'confirm').mockReturnValue(true);await user.click(screen.getByRole('button',{name:'Update Deal From Notes'}));await screen.findByText(/Deal updated from notes/)
    expect(screen.getByLabelText('Plan name')).toHaveValue('My gift deal')
  })

  it('links an inventory reward to an imported RR payment without counting its value twice',async()=>{
    const api=new ApiClient('test'),user=userEvent.setup(),change=vi.fn(),input=scenario()
    input.payments=[{id:'imported10',type:'REGISTER_REWARD',amount:'10.00',notes:'P&G',registerRewardType:'MANUFACTURER'},{id:'imported6',type:'REGISTER_REWARD',amount:'6',notes:'RR',registerRewardType:'MANUFACTURER'}]
    vi.spyOn(api,'standaloneRewards').mockResolvedValue([{rewardId:'source10',description:'P&G RR',amount:'10',registerRewardType:'MANUFACTURER',earnedDate:'2026-10-01',expirationDate:'2026-11-01',status:'UNUSED'}])
    render(<RewardPicker api={api} data={workspace()} scenario={input} onChange={change} day="2026-10-19"/>)
    await user.click(screen.getByText('Choose a reward manually (optional)'));await screen.findByRole('option',{name:/P&G RR/})
    await user.selectOptions(screen.getByLabelText('Select a recorded RR'),'source10')
    const selected=change.mock.lastCall![0] as PlanScenario;expect(selected.payments).toHaveLength(2);expect(selected.payments![0]).toMatchObject({id:'imported10',amount:'10',registerRewardType:'MANUFACTURER',rewardSourceId:'source10'})
    expect(selected.payments![1]).toEqual(input.payments[1])
  })

  it('maps an online Q-tips import into one editable product, coupons, rewards, and channel',async()=>{
    const api=new ApiClient('test'),user=userEvent.setup()
    const text='Qtips Deal Regular Price 4. 99\nStore Promotion .99\nBuy 3 : 3 X 4 = 12.00\nManufacturer Coupon Buy 3 Get $5.00 Off\nWalgreens Coupon 1.00 Off X 3 = 3.00\nOnline Promo Code Wag10 -.40\nOop = 3.60\nEarn .12 Walgreens Cash\nFetch 2500 Points\nIbotta 4.00 Must Buy 3\n\nOnline Only'
    vi.spyOn(api,'mutate').mockImplementation(async path=>{
      if(path==='/shopping-plans/import-preview')return {items:[{id:'qtips',name:'Qtips',regular:'4.99',sale:'4',quantity:'3'}],coupons:[{id:'mfg',description:'Manufacturer coupon',amount:'5',requiredQuantity:'3',kind:'MANUFACTURER',lineIds:['qtips']},{id:'store',description:'Walgreens coupon',amount:'3',requiredQuantity:'3',kind:'STORE_COUPON',lineIds:['qtips']},{id:'code',description:'Online promo code WAG10',amount:'0.40',requiredQuantity:'1',kind:'STORE_COUPON',lineIds:[],wholeReceipt:true}],promotions:[{id:'fetch',description:'Fetch',rewardType:'FETCH',rewardAmount:'2500',requiredQuantity:'0',requiredSpend:'0',spendBasis:'BEFORE_COUPONS',allowsWalgreensCash:true,lineIds:[],wholeReceipt:true}],source:{text,mediaType:null,contentBase64:null,purchaseChannel:'ONLINE'},retailer:'Walgreens',unresolved:['Store Promotion .99 — clarify the promotion.']}
      throw new Error('Calculation is covered by Java regression tests')
    })
    render(<PlanScenarioEditor plan={null} data={workspace()} api={api} writable onClose={vi.fn()} initialMode="notes" onReceipt={vi.fn()}/>)
    fireEvent.change(screen.getByLabelText('Type or paste your deal notes'),{target:{value:text}});await user.click(screen.getByRole('button',{name:'Add to this plan'}))
    await waitFor(()=>expect(screen.getByLabelText('Plan name')).toHaveValue('Qtips'))
    expect(screen.getByLabelText('Purchase channel')).toHaveValue('ONLINE');expect(screen.getByLabelText('Original deal notes')).toHaveValue(text)
    expect(screen.getByLabelText('Coupon amount 1')).toHaveValue('5');expect(screen.getByLabelText('Coupon amount 2')).toHaveValue('3');expect(screen.getByLabelText('Coupon amount 3')).toHaveValue('0.40');expect(screen.getByLabelText('Rebate type 1')).toHaveValue('FETCH');expect(screen.getByLabelText('Rebate points 1')).toHaveValue('2500')
    expect(screen.getAllByRole('button',{name:/Remove product/})).toHaveLength(1)
  })

  it('does not read dropped attachments inside a disabled form',()=>{
    const change=vi.fn()
    render(<fieldset disabled><EvidenceField onChange={change}/></fieldset>)
    fireEvent.drop(screen.getByRole('group',{name:'Attachment drop area'}),{dataTransfer:{files:[new File(['a'],'proof.png',{type:'image/png'})]}})
    expect(change).not.toHaveBeenCalled();expect(screen.getByLabelText('Optional supporting image or PDF')).toBeDisabled()
  })

  it('recovers the product and coupon when an earlier screenshot import left only a blank row',async()=>{
    const api=new ApiClient('test'),user=userEvent.setup(),change=vi.fn()
    const source={text:'Grab\n1Febreze small space\nfebreze\nareas\n$3.49',mediaType:'image/png',contentBase64:'AQID'}
    vi.spyOn(api,'mutate').mockResolvedValue({items:[{id:'febreze',name:'Febreze small space',regular:null,sale:'3.49',quantity:'1'}],coupons:[{id:'coupon',description:'$2 off',amount:'2',requiredQuantity:'1',lineIds:['febreze'],savedCouponId:null}],promotions:[],source,unresolved:[]})
    render(<QuickDealTools api={api} scenario={{...scenario(),items:[{...scenario().items[0],name:'',sale:'',regular:''}],source}} data={workspace()} store="Walgreens" day="2026-10-29" onChange={change}/>)
    await user.click(screen.getByRole('button',{name:'Re-read screenshot offers'}))
    await screen.findByText(/1 coupon\(s\) and 0 expected/)
    const updated=change.mock.lastCall![0] as PlanScenario
    expect(updated.items).toHaveLength(1);expect(updated.items[0]).toMatchObject({id:'febreze',name:'Febreze small space',sale:'3.49'})
    expect(updated.coupons).toHaveLength(1);expect(updated.coupons[0].lineIds).toEqual(['febreze'])
  })

  it('restores skipped offers from the retained screenshot without duplicating products or offers',async()=>{
    const api=new ApiClient('test'),user=userEvent.setup()
    const imported={items:[{id:'ocr-product',name:'Product from screenshot — enter name',regular:null,sale:'7.00',quantity:'2'}],coupons:['5.00','2.50'].map((amount,i)=>({id:`c${i}`,description:`Poise coupon ${i}`,amount,requiredQuantity:'1',lineIds:['ocr-product'],savedCouponId:null,kind:'MANUFACTURER'})),promotions:[{id:'rebate',description:'Ibotta',rewardType:'IBOTTA',rewardAmount:'2.00',requiredQuantity:'0',requiredSpend:'0',spendBasis:'BEFORE_COUPONS',allowsWalgreensCash:true,lineIds:['ocr-product']}],source:{text:'OCR offers',mediaType:'image/png',contentBase64:'AQID'},unresolved:[]}
    vi.spyOn(api,'mutate').mockImplementation(async path=>{
      if(path==='/shopping-plans/import-preview')return structuredClone(imported)
      throw new Error('No other mutation expected')
    })
    let saved:PlanScenario|undefined
    function Harness(){const [value,setValue]=useState<PlanScenario>({...scenario(),items:[{...scenario().items[0],name:'Poise pads — my correction',sale:'6.99',quantity:'2'}],source:{text:'Old OCR missing offers',mediaType:'image/png',contentBase64:'AQID'}});return <QuickDealTools api={api} scenario={value} data={workspace()} store="Walgreens" day="2026-10-29" onChange={next=>{saved=next;setValue(next)}}/>}
    render(<Harness/>);await user.click(screen.getByRole('button',{name:'Re-read screenshot offers'}))
    await screen.findByText(/2 coupon\(s\) and 1 expected/)
    expect(saved?.items).toHaveLength(1);expect(saved?.items[0]).toMatchObject({id:'line',name:'Poise pads — my correction',sale:'6.99'})
    expect(saved?.coupons).toHaveLength(2);expect(saved?.promotions).toHaveLength(1)
    expect(saved?.coupons.every(c=>c.lineIds[0]==='line')).toBe(true);expect(saved?.promotions[0].lineIds).toEqual(['line'])
    imported.coupons[0].amount='5';imported.promotions[0].rewardAmount='2'
    await user.click(screen.getByRole('button',{name:'Re-read screenshot offers'}))
    await screen.findByText(/0 coupon\(s\) and 0 expected/)
    expect(saved?.items).toHaveLength(1);expect(saved?.coupons).toHaveLength(2);expect(saved?.promotions).toHaveLength(1)
  })

  it('keeps both Poise screenshot coupons and Ibotta visible in the plan editor',async()=>{
    const api=new ApiClient('test'),user=userEvent.setup()
    const coupons=['5.00','2.50'].map((amount,i)=>({id:`coupon-${i}`,description:`Poise $${amount} off 1`,amount,requiredQuantity:'1',lineIds:['poise'],savedCouponId:null,kind:'MANUFACTURER'}))
    const rebate={id:'ibotta',description:'Poise Ibotta rebate',rewardType:'IBOTTA',rewardAmount:'2.00',requiredQuantity:'0',requiredSpend:'0',spendBasis:'BEFORE_COUPONS',allowsWalgreensCash:true,lineIds:['poise']}
    vi.spyOn(api,'mutate').mockImplementation(async path=>{
      if(path==='/shopping-plans/import-preview')return {items:[{id:'poise',name:'Poise pads',regular:null,sale:'7.00',quantity:'2'}],coupons,promotions:[rebate],unresolved:[],source:{text:'Poise poster',mediaType:'image/png',contentBase64:'AQID'}}
      throw new Error('Preview not needed for this field-preservation test')
    })
    render(<PlanScenarioEditor plan={null} data={workspace()} api={api} writable onClose={vi.fn()} initialMode="screenshot" onReceipt={vi.fn()}/>)
    await user.upload(screen.getByLabelText('Deal photos — select companion images together'),new File([new Uint8Array([1,2,3])],'poise.png',{type:'image/png'}))
    await waitFor(()=>expect(screen.getByRole('button',{name:'Add to this plan'})).toBeEnabled())
    await user.click(screen.getByRole('button',{name:'Add to this plan'}))
    expect(await screen.findByLabelText('Coupon amount 1')).toHaveValue('5.00')
    expect(screen.getByLabelText('Coupon amount 2')).toHaveValue('2.50')
    expect(screen.getByLabelText('Rebate amount 1')).toHaveValue('2.00')
    expect(screen.getByLabelText('Rebate type 1')).toHaveValue('IBOTTA')
  })

  it('accepts a dropped screenshot using the existing file validation',async()=>{
    const change=vi.fn()
    render(<EvidenceField onChange={change}/>)
    const zone=screen.getByRole('group',{name:'Attachment drop area'})
    fireEvent.dragOver(zone,{dataTransfer:{dropEffect:'none'}})
    expect(zone).toHaveClass('dragging')
    fireEvent.drop(zone,{dataTransfer:{files:[new File([new Uint8Array([1,2,3])],'deal.png',{type:'image/png'})]}})
    expect(await screen.findByRole('status')).toHaveTextContent('Attached: deal.png')
    expect(change).toHaveBeenLastCalledWith({mediaType:'image/png',contentBase64:'AQID'})
    expect(zone).not.toHaveClass('dragging')
    expect(screen.getByLabelText('Optional supporting image or PDF')).toBeInTheDocument()
  })
  it('rejects unsupported and multiple dropped files without submitting them',async()=>{
    const change=vi.fn()
    render(<EvidenceField onChange={change}/>)
    const zone=screen.getByRole('group',{name:'Attachment drop area'})
    fireEvent.drop(zone,{dataTransfer:{files:[new File(['text'],'deal.txt',{type:'text/plain'})]}})
    expect(await screen.findByRole('alert')).toHaveTextContent('Select JPEG, PNG, WebP or PDF up to 5 MiB.')
    fireEvent.drop(zone,{dataTransfer:{files:[new File(['a'],'a.png',{type:'image/png'}),new File(['b'],'b.png',{type:'image/png'})]}})
    expect(screen.getByRole('alert')).toHaveTextContent('Drop one image or PDF at a time.')
    expect(change.mock.calls.every(([value])=>value===null)).toBe(true)
  })

  it('preserves an editable product when no pasted products are recognized',async()=>{
    const api=new ApiClient('test'),change=vi.fn(),user=userEvent.setup()
    const input={...scenario(),items:[{...scenario().items[0],name:'',sale:'',regular:''}]}
    vi.spyOn(api,'mutate').mockResolvedValue({items:[],coupons:[],promotions:[],unresolved:['Unrecognized note'],source:{text:'Unrecognized note'}})
    render(<QuickDealTools api={api} scenario={input} data={workspace()} onChange={change} store="Walgreens" day="2026-10-27" initialMode="notes"/>)
    await user.type(screen.getByLabelText('Type or paste your deal notes'),'Unrecognized note')
    await user.click(screen.getByRole('button',{name:'Add to this plan'}))
    expect(change).toHaveBeenCalledWith(expect.objectContaining({items:input.items,source:expect.objectContaining({text:'Unrecognized note'})}))
    expect(await screen.findByText(/Automatic reading failed.*No products could be read/)).toBeInTheDocument()
  })

  it('imports notes into the editable plan, retaining unrecognized terms and the original source',async()=>{
    const api=new ApiClient('test'),change=vi.fn(),user=userEvent.setup(),text='Colgate 6 x2\n5 off 2\nearn 5 RR\nCheck my offer'
    const mutate=vi.spyOn(api,'mutate').mockResolvedValue({items:scenario().items,coupons:[],promotions:[],unresolved:['Check my offer'],source:{text,mediaType:null,contentBase64:null}})
    render(<QuickDealTools api={api} scenario={{...scenario(),items:[]}} data={workspace()} onChange={change} store="Walgreens" day="2026-10-19" initialMode="notes"/>)
    await user.type(screen.getByLabelText('Type or paste your deal notes'),text)
    await user.click(screen.getByRole('button',{name:'Add to this plan'}))
    expect(mutate).toHaveBeenCalledWith('/shopping-plans/import-preview','POST',{text},expect.any(String))
    expect(change).toHaveBeenCalledWith(expect.objectContaining({source:expect.objectContaining({text}),items:expect.arrayContaining([expect.objectContaining({sale:'6',regular:''})])}))
    expect(await screen.findByText('Check my offer',{selector:'pre'})).toBeInTheDocument()
    expect(mutate.mock.calls.some(c=>c[0]==='/shopping-plans')).toBe(false)
  })
  it('retries the same screenshot without duplicating its bytes or source text and adopts its explicit store',async()=>{
    const api=new ApiClient('test'),change=vi.fn(),store=vi.fn(),user=userEvent.setup(),text='Original OCR text'
    const input={...scenario(),items:[],source:{text,mediaType:'image/png',contentBase64:'AQID',earlier:[]}}
    vi.spyOn(api,'mutate').mockResolvedValue({items:scenario().items,coupons:[],promotions:[],unresolved:[],source:input.source,retailer:'CVS'})
    render(<QuickDealTools api={api} scenario={input} data={workspace()} onChange={change} onStoreChange={store} store="Walgreens" day="2026-10-27" initialMode="screenshot"/>)
    await user.upload(screen.getByLabelText('Deal photos — select companion images together'),new File([new Uint8Array([1,2,3])],'poster.png',{type:'image/png'}))
    await waitFor(()=>expect(screen.getByRole('button',{name:'Add to this plan'})).toBeEnabled())
    await user.click(screen.getByRole('button',{name:'Add to this plan'}))
    expect(change).toHaveBeenCalledWith(expect.objectContaining({source:expect.objectContaining({text,contentBase64:'AQID',earlier:[]})}))
    expect(store).toHaveBeenCalledWith('CVS')
  })
  it('preserves an entered coupon instead of replacing it with a recorded match',async()=>{
    const api=new ApiClient('test'),data=workspace();data.coupons=[{couponId:'saved',provider:'Brand',externalId:null,status:'CLIPPED',clippedOn:'2026-10-01',redeemedOn:null,redeemedTransactionId:null,revision:'1',description:'My coupon',discountAmount:'3',couponType:'MANUFACTURER',expirationDate:'2026-10-31'}]
    const input=scenario();input.items[0].sku='123';input.coupons=[{id:'advertised',description:'Advertised coupon',amount:'5',requiredQuantity:'2',lineIds:['line'],savedCouponId:null,kind:'MANUFACTURER'}]
    const mutate=vi.spyOn(api,'mutate').mockResolvedValue([{id:'matched',description:'My coupon',amount:'3',requiredQuantity:'2',lineIds:['line'],savedCouponId:'saved',kind:'MANUFACTURER'}])
    let saved:PlanScenario|undefined
    function Harness(){const [value,setValue]=useState(input);return <QuickDealTools api={api} scenario={value} data={data} onChange={v=>{saved=v;setValue(v)}} store="Walgreens" day="2026-10-19"/>}
    render(<Harness/>);await new Promise(resolve=>setTimeout(resolve,500))
    expect(screen.queryByText(/attached automatically/)).not.toBeInTheDocument();expect(saved).toBeUndefined()
    expect(input.coupons[0].amount).toBe('5');expect(mutate.mock.calls.every(call=>call[0]==='/shopping-plans/match-offers')).toBe(true)
  })
  it('does not stack recorded coupons onto unallocated advertised manufacturer amounts',async()=>{
    const api=new ApiClient('test'),data=workspace(),input=scenario()
    data.coupons=[{couponId:'saved',provider:'Brand',externalId:null,status:'CLIPPED',clippedOn:'2026-10-01',redeemedOn:null,redeemedTransactionId:null,revision:'1',description:'My coupon',discountAmount:'3',couponType:'MANUFACTURER',expirationDate:'2026-10-31'}]
    input.coupons=[{id:'advertised',description:'Advertised manufacturer coupons',amount:'8',requiredQuantity:'1',lineIds:[],savedCouponId:null,kind:'MANUFACTURER',wholeReceipt:true}]
    const mutate=vi.spyOn(api,'mutate').mockResolvedValue([]),change=vi.fn(),user=userEvent.setup()
    render(<ProductCouponPicker product={input.items[0]} api={api} scenario={input} coupons={data.coupons} onChange={change} store="Walgreens" day="2026-10-27"/>)
    await user.click(screen.getByText(/^Find \/ Change Coupon/));await user.click(screen.getByLabelText('Search all my coupons'));await user.click(screen.getByRole('button',{name:'Select coupon'}));expect(await screen.findByText(/Unallocated advertised totals cannot be matched automatically/)).toBeInTheDocument();expect(change).not.toHaveBeenCalled()
    expect(mutate.mock.calls.every(call=>call[0]==='/shopping-plans/match-offers')).toBe(true)
  })
  it('sale buttons preserve the entered unit price and attach the rule for backend calculation',async()=>{
    const change=vi.fn(),user=userEvent.setup(),line=scenario().items[0]
    render(<SaleButtons line={line} onChange={change}/>);await user.click(screen.getByRole('button',{name:'BOGO free'}))
    expect(change).toHaveBeenCalledWith({...line,priceRule:{type:'BOGO_FREE',bundlePrice:null}})
  })
  it('shows approved A/B costs from the Java response without sending saved screenshot bytes',async()=>{
    const api=new ApiClient('test'),a=row('a','Option A'),b=row('b','Option B'),choose=vi.fn(),user=userEvent.setup()
    a.plan.scenario!.source={text:'Original',mediaType:'image/png',contentBase64:'private-image'}
    const cost=(overrides:object)=>({total:'24.13',couponTotal:'7',totalWithTax:'17.13',registerRewardsUsed:'15',walgreensCashUsed:'2',ecbUsed:'0',walmartCashUsed:'0',giftCardUsed:'0',calculatedOop:'0.13',selectedOop:'0.13',manualDifference:'0',potentialRewards:'10',overFunding:'0',hasEnteredEarnings:false,earnings:[],...overrides})
    const mutate=vi.spyOn(api,'mutate').mockResolvedValue({a:cost({}),b:cost({total:'34.18',couponTotal:'11.50',totalWithTax:'22.68',registerRewardsUsed:'20',calculatedOop:'0.68'}),cashDifference:'0.55',rrDifference:'5',addedItems:[{name:'Scott packages',quantity:'2'}]})
    render(<CompareCosts rows={[a,b]} api={api} onClose={vi.fn()} onChoose={choose}/>)
    expect(await screen.findByText('$0.68')).toBeInTheDocument();expect(screen.getByText('$0.13')).toBeInTheDocument();expect(screen.getByText('2 Scott packages')).toBeInTheDocument()
    expect(JSON.stringify(mutate.mock.calls[0][2])).not.toContain('private-image')
    await user.click(screen.getByRole('button',{name:'Choose B'}));expect(choose).toHaveBeenCalledWith(b.plan)
  })
  it('places an idea into the trip using its current revision and refreshes the workspace',async()=>{
    const api=new ApiClient('test'),data=workspace(),plan=row('a','Colgate + Listerine'),changed=vi.fn(),user=userEvent.setup();data.plans=[plan]
    vi.spyOn(api,'account').mockRejectedValue(new Error('No test opening'));vi.spyOn(api,'standaloneRewards').mockResolvedValue([])
    const mutate=vi.spyOn(api,'mutate').mockResolvedValue({})
    render(<ShoppingWeek data={data} api={api} writable onQuick={vi.fn()} onEdit={vi.fn()} onAlternative={vi.fn()} onReceipt={vi.fn()} onPage={vi.fn()} onChanged={changed}/>)
    expect(screen.queryByRole('button',{name:'Add to trip'})).not.toBeInTheDocument()
    await user.click(screen.getByRole('button',{name:'View'}))
    expect(screen.getByRole('dialog',{name:'Colgate + Listerine'})).toBeVisible()
    expect(screen.getByRole('cell',{name:'Colgate'})).toBeVisible()
    expect(screen.getByRole('columnheader',{name:'Quantity'})).toBeVisible()
    await user.click(screen.getByRole('button',{name:'Add to trip'}))
    await waitFor(()=>expect(changed).toHaveBeenCalledTimes(1))
    expect(mutate).toHaveBeenCalledWith('/shopping-plans/a','PUT',expect.objectContaining({organization:expect.objectContaining({stage:'TRIP',checkoutOrder:0})}),expect.any(String),'1')
  })
  it('removes a plan from the trip without deleting it',async()=>{
    const api=new ApiClient('test'),data=workspace(),plan=row('trip','PG deal'),changed=vi.fn(),user=userEvent.setup()
    plan.plan.organization={stage:'TRIP',alternativeOf:null,checkoutOrder:2,rewardSources:[]};data.plans=[plan]
    const mutate=vi.spyOn(api,'mutate').mockResolvedValue({})
    render(<ShoppingWeek data={data} api={api} writable onQuick={vi.fn()} onEdit={vi.fn()} onAlternative={vi.fn()} onReceipt={vi.fn()} onPage={vi.fn()} onChanged={changed}/>)
    await user.click(screen.getByRole('button',{name:'Remove from trip'}))
    await waitFor(()=>expect(changed).toHaveBeenCalledOnce())
    expect(mutate).toHaveBeenCalledWith('/shopping-plans/trip','PUT',expect.objectContaining({organization:expect.objectContaining({stage:'IDEA',checkoutOrder:null})}),expect.any(String),'1')
  })
  it('keeps follow-up while removing weekly balances and inventory from Shopping Plans',()=>{
    const api=new ApiClient('test'),data=workspace(),onPage=vi.fn()
    const account=vi.spyOn(api,'account'),instruments=vi.spyOn(api,'standaloneRewards')
    data.accountHistory.redemptionHistory=[{redemptionId:'cancelled',provider:'Cancelled program',requestedDate:'2026-10-19',status:'REQUESTED'},{redemptionId:'cancelled',provider:'Cancelled program',requestedDate:'2026-10-19',status:'CANCELLED'},{redemptionId:'pending',provider:'Ibotta',requestedDate:'2026-10-19',status:'REQUESTED',notes:'Pending transfer'}]
    render(<ShoppingWeek data={data} api={api} writable onQuick={vi.fn()} onEdit={vi.fn()} onAlternative={vi.fn()} onReceipt={vi.fn()} onPage={onPage}/>)
    expect(screen.getByRole('heading',{name:'Follow-up'})).toBeInTheDocument()
    expect(screen.getByText('Ibotta payout — Requested')).toBeInTheDocument()
    expect(screen.queryByText('Cancelled program')).not.toBeInTheDocument()
    expect(screen.queryByRole('heading',{name:'Weekly tracking'})).not.toBeInTheDocument()
    expect(screen.queryByText('Weekly reconciliation — Walgreens Cash')).not.toBeInTheDocument()
    expect(screen.queryByRole('button',{name:'Inventory'})).not.toBeInTheDocument()
    expect(account).not.toHaveBeenCalled();expect(instruments).not.toHaveBeenCalled()
    expect(screen.getByRole('button',{name:'All rebates and follow-up'})).toBeInTheDocument()
  })
})

it('loads the source image even when reading fails, preserving products for manual entry',async()=>{
 const api=new ApiClient('test'),user=userEvent.setup(),input=scenario()
 const mutate=vi.spyOn(api,'mutate').mockRejectedValue(new Error('Screenshot could not be read.'))
 let saved:PlanScenario|undefined
 function Harness(){const [value,setValue]=useState(input);return <QuickDealTools api={api} scenario={value} data={workspace()} onChange={next=>{saved=next;setValue(next)}} store="Walgreens" day="2026-11-04" initialMode="screenshot"/>}
 render(<Harness/>)
 await user.upload(screen.getByLabelText('Deal photos — select companion images together'),new File(['photo'],'image.png',{type:'image/png'}))
 await user.click(screen.getByRole('button',{name:'Add to this plan'}))
 expect(await screen.findByText(/Source attached.*Screenshot could not be read/)).toBeInTheDocument()
 expect(screen.queryByRole('dialog',{name:'Add screenshot'})).not.toBeInTheDocument()
 expect(screen.getByRole('img',{name:'Original deal screenshot'})).toHaveAttribute('src',`data:image/png;base64,${btoa('photo')}`)
 expect(saved?.items).toEqual(input.items)
 expect(mutate).toHaveBeenCalledWith('/shopping-plans/import-preview','POST',{mediaType:'image/png',contentBase64:btoa('photo'),text:null},expect.any(String),undefined,expect.any(AbortSignal))
})

it('puts the collapsed picker inside product Coupons and keeps existing manual fields unchanged',async()=>{
 const user=userEvent.setup(),data=workspace(),api=new ApiClient('test'),saved=row('saved-idea','Venus idea');saved.plan.retailer='Walgreens';saved.plan.scenario!.items[0].name='Venus Razor';saved.plan.scenario!.coupons=[{id:'manual',description:'My entered coupon',amount:'4',requiredQuantity:'1',lineIds:['line'],savedCouponId:null,kind:'MANUFACTURER'}];data.coupons=[{couponId:'venus-offer',provider:'Walgreens',description:'Gillette or Venus $4 off 1',discountAmount:'4',requiredQuantity:'1',couponType:'MANUFACTURER',status:'AVAILABLE',expirationDate:'2099-11-30',externalId:null,clippedOn:null,redeemedOn:null,redeemedTransactionId:null,revision:'1',terms:{source:'DIGITAL_MANUFACTURER',discount:'DOLLARS',storeRestriction:'Walgreens',productOrBrand:'Venus'}}];vi.spyOn(api,'mutate').mockImplementation(async path=>path.endsWith('/match-offers')?[]:{items:[],coupons:[],promotions:[],selectedOop:'0',potentialRewards:'0'});render(<PlanScenarioEditor plan={saved.plan} data={data} api={api} writable onClose={vi.fn()} onReceipt={vi.fn()}/>);expect(document.querySelector('.quick-deal-tools .coupon-picker')).toBeNull();expect(screen.getByLabelText('Coupon description 1')).toHaveValue('My entered coupon');expect(screen.getByLabelText('Coupon amount 1')).toHaveValue('4');expect(screen.queryByText('Gillette or Venus $4 off 1')).not.toBeInTheDocument();const picker=screen.getByLabelText('Coupons for Venus Razor');await user.click(within(picker).getByText(/^Find \/ Change Coupon/));expect(within(picker).getByText('Gillette or Venus $4 off 1')).toBeInTheDocument();await user.click(within(picker).getByText(/^Find \/ Change Coupon/));expect(screen.getByLabelText('Coupon description 1')).toHaveValue('My entered coupon');expect(screen.getByLabelText('Coupon amount 1')).toHaveValue('4');expect(screen.queryByLabelText('Search coupons')).not.toBeInTheDocument();
})

it('cancels screenshot reading, preserves evidence, and ignores a late reader response',async()=>{
 const api=new ApiClient('test'),user=userEvent.setup();let finish!:(value:unknown)=>void
 const mutate=vi.spyOn(api,'mutate').mockImplementation(()=>new Promise(resolve=>{finish=resolve}))
 let saved:PlanScenario|undefined
 function Harness(){const [value,setValue]=useState<PlanScenario>({...scenario(),items:[]});return <QuickDealTools api={api} scenario={value} data={workspace()} store="Walgreens" day="2026-11-09" initialMode="screenshot" onChange={next=>{saved=next;setValue(next)}}/>}
 render(<Harness/>);const photo=new File(['photo'],'deal.png',{type:'image/png'});await user.upload(screen.getByLabelText('Deal photos — select companion images together'),photo);await screen.findByText(/Attached: deal.png/);await user.click(screen.getByRole('button',{name:'Add to this plan'}));await screen.findByRole('button',{name:'Cancel reading'});await user.click(screen.getByRole('button',{name:'Cancel reading'}));
 const args=mutate.mock.calls[0];expect((args[5] as AbortSignal).aborted).toBe(true);expect(saved?.source?.contentBase64).toBe(btoa('photo'));expect(saved?.items).toEqual([])
 finish({items:[{id:'late',name:'Late item',regular:'99',sale:null,quantity:'1'}],coupons:[],promotions:[],unresolved:[],source:{text:'',mediaType:'image/png',contentBase64:btoa('photo')}})
 await waitFor(()=>expect(screen.getByRole('button',{name:'Re-read screenshot offers'})).toBeEnabled());expect(saved?.items).toEqual([])
})
