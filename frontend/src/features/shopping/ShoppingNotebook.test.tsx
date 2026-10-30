import { useState } from 'react'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiClient } from '../../api/client'
import { workspace, planningRow } from '../../api/fixtures.test-support'
import type { PlanScenario } from '../../types/platform'
import { PlanScenarioEditor } from '../../pages/WorkspacePages'
import { EvidenceField } from '../../components/EvidenceField'
import { QuickDealTools, SaleButtons } from './QuickDealTools'
import { CompareCosts, ShoppingWeek } from './ShoppingWeek'

const scenario=():PlanScenario=>({items:[{id:'line',name:'Colgate',sku:null,regular:'',sale:'6',quantity:'2'}],coupons:[],promotions:[],tax:'0',walgreensCashUsed:'0',registerRewardsUsed:'0',giftCardUsed:'0',manualOop:null,paymentMethod:'CASH',fundingNotes:''})
function row(id:string,name:string){const r=planningRow();r.plan={...r.plan,planId:id,name,scenario:scenario(),shoppingDate:'2026-10-19',organization:{stage:'IDEA',alternativeOf:null,checkoutOrder:null,rewardSources:[]}};return r}
describe('approved shopping notebook',()=>{
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
    const updated=change.mock.calls[0][0] as PlanScenario
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
    await user.upload(screen.getByLabelText('Optional supporting image or PDF'),new File([new Uint8Array([1,2,3])],'poise.png',{type:'image/png'}))
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
    expect(change).toHaveBeenCalledWith(expect.objectContaining({items:input.items}))
    expect(await screen.findByText(/No products were recognized/)).toBeInTheDocument()
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
    await user.upload(screen.getByLabelText('Optional supporting image or PDF'),new File([new Uint8Array([1,2,3])],'poster.png',{type:'image/png'}))
    await waitFor(()=>expect(screen.getByRole('button',{name:'Add to this plan'})).toBeEnabled())
    await user.click(screen.getByRole('button',{name:'Add to this plan'}))
    expect(change).toHaveBeenCalledWith(expect.objectContaining({source:expect.objectContaining({text,contentBase64:'AQID',earlier:[]})}))
    expect(store).toHaveBeenCalledWith('CVS')
  })
  it('attaches a recorded match once and replaces the advertised deduction instead of stacking it',async()=>{
    const api=new ApiClient('test'),data=workspace();data.coupons=[{couponId:'saved',provider:'Brand',externalId:null,status:'CLIPPED',clippedOn:'2026-10-01',redeemedOn:null,redeemedTransactionId:null,revision:'1',description:'My coupon',discountAmount:'3',couponType:'MANUFACTURER',expirationDate:'2026-10-31'}]
    const input=scenario();input.items[0].sku='123';input.coupons=[{id:'advertised',description:'Advertised coupon',amount:'5',requiredQuantity:'2',lineIds:['line'],savedCouponId:null,kind:'MANUFACTURER'}]
    const mutate=vi.spyOn(api,'mutate').mockResolvedValue([{id:'matched',description:'My coupon',amount:'3',requiredQuantity:'2',lineIds:['line'],savedCouponId:'saved',kind:'MANUFACTURER'}])
    let saved:PlanScenario|undefined
    function Harness(){const [value,setValue]=useState(input);return <QuickDealTools api={api} scenario={value} data={data} onChange={v=>{saved=v;setValue(v)}} store="Walgreens" day="2026-10-19"/>}
    render(<Harness/>);await screen.findByText(/attached automatically/)
    expect(saved?.coupons).toHaveLength(1);expect(saved?.coupons[0].amount).toBe('3');expect(saved?.coupons[0].savedCouponId).toBe('saved')
    expect(mutate).toHaveBeenCalledTimes(1)
  })
  it('does not stack recorded coupons onto unallocated advertised manufacturer amounts',async()=>{
    const api=new ApiClient('test'),data=workspace(),input=scenario()
    data.coupons=[{couponId:'saved',provider:'Brand',externalId:null,status:'CLIPPED',clippedOn:'2026-10-01',redeemedOn:null,redeemedTransactionId:null,revision:'1',description:'My coupon',discountAmount:'3',couponType:'MANUFACTURER',expirationDate:'2026-10-31'}]
    input.coupons=[{id:'advertised',description:'Advertised manufacturer coupons',amount:'8',requiredQuantity:'1',lineIds:[],savedCouponId:null,kind:'MANUFACTURER',wholeReceipt:true}]
    const mutate=vi.spyOn(api,'mutate')
    render(<QuickDealTools api={api} scenario={input} data={data} onChange={vi.fn()} store="CVS" day="2026-10-27"/>)
    expect(await screen.findByText(/same discount is not counted twice/)).toBeInTheDocument()
    expect(mutate).not.toHaveBeenCalled()
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
    await user.click(screen.getByRole('button',{name:'Add to trip'}))
    await waitFor(()=>expect(changed).toHaveBeenCalledTimes(1))
    expect(mutate).toHaveBeenCalledWith('/shopping-plans/a','PUT',expect.objectContaining({organization:expect.objectContaining({stage:'TRIP',checkoutOrder:0})}),expect.any(String),'1')
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
