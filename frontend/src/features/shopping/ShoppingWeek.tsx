import { FormActions } from '../../components/ui'
import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import type { ComponentProps } from 'react'
import type { ApiClient } from '../../api/client'
import { scenarioRequest } from '../../api/client'
import { money, shoppingWeek, shoppingWeekLabel } from '../../api/format'
import { Modal, Panel } from '../../components/ui'
import type { Plan, PlanRow, PlanReceiptDraft, Workspace } from '../../types/platform'
import type { Page } from '../../pages/WorkspacePages'

function WeekPanel({children,...props}:ComponentProps<typeof Panel>) {
  return <Panel {...props}><div className="weekly-panel-body">{children}</div></Panel>
}

interface Cost { total:string; couponTotal:string; totalWithTax:string; registerRewardsUsed:string; walgreensCashUsed:string; ecbUsed:string; walmartCashUsed:string; giftCardUsed:string; calculatedOop:string; selectedOop:string; manualDifference:string; potentialRewards:string; overFunding:string; hasEnteredEarnings:boolean; earnings:{id:string;description:string;rewardType:string;potentialReward:string;points?:string;eligible:boolean}[] }
interface Comparison {a:Cost;b:Cost;cashDifference:string;rrDifference:string;addedItems:{name:string;quantity:string}[]}
const rewardName=(type:string)=>({REGISTER_REWARD:'RR',WALGREENS_CASH:'WC',PG_BRAND:'P&G Brand',FETCH:'Fetch',WALMART_CASH:'Walmart Cash'}[type]??type.replaceAll('_',' '))
const withoutSource = (plan: Plan) => scenarioRequest({ ...plan.scenario!, source:null })
const inRange=(day:string|null|undefined,from:string,to:string)=>!!day&&day>=from&&day<=to
export function CompareCosts({ rows, api, onClose, onChoose }: {rows:PlanRow[];api:ApiClient;onClose:()=>void;onChoose:(plan:Plan)=>void}) {
  const [a,setA]=useState(rows[0]?.plan.planId??''),[b,setB]=useState(rows[1]?.plan.planId??'')
  const [resolved,setResolved]=useState<{key:string;value:Comparison}|null>(null),[failure,setFailure]=useState<{key:string;message:string}|null>(null)
  const left=rows.find(r=>r.plan.planId===a),right=rows.find(r=>r.plan.planId===b)
  const key=`${a}:${left?.plan.revision}:${b}:${right?.plan.revision}`
  const result=resolved?.key===key?resolved.value:null,error=failure?.key===key?failure.message:''
  useEffect(()=>{let active=true;if(!left?.plan.scenario||!right?.plan.scenario)return
    void Promise.resolve().then(()=>api.mutate('/shopping-plans/compare','POST',{a:withoutSource(left.plan),b:withoutSource(right.plan),storeA:left.plan.retailer,storeB:right.plan.retailer},crypto.randomUUID())).then(value=>{if(active)setResolved({key,value:value as Comparison})}).catch(e=>{if(active)setFailure({key,message:(e as Error).message})})
    return()=>{active=false}
  },[api,left,right,key])
  const choose=(label:string,value:string,set:(v:string)=>void)=><label>{label}<select aria-label={label} value={value} onChange={e=>set(e.target.value)}><option value="">Choose a saved scenario</option>{rows.map(r=><option key={r.plan.planId} value={r.plan.planId}>{r.plan.name}</option>)}</select></label>
  return <Modal title="Compare Costs — Option A vs. Option B" onClose={onClose}><div className="form-grid">{choose('Option A',a,setA)}{choose('Option B',b,setB)}</div>{error&&<p className="error" role="alert">{error}</p>}{result?<>
    <div className="table-wrap"><table className="cost-comparison-table"><thead><tr><th>Cost breakdown</th><th>Option A</th><th>Option B</th></tr></thead><tbody>{([
      ['Total including entered tax','total',false],['Coupons','couponTotal',true],['Before reward use','totalWithTax',false],['RR used','registerRewardsUsed',true],['WC used','walgreensCashUsed',true],['Cash / card OOP','calculatedOop',false],
      ...(['ecbUsed','walmartCashUsed','giftCardUsed'] as const).filter(k=>[result.a[k],result.b[k]].some(v=>!/^0+(?:[.]0+)?$/.test(v))).map(k=>[k==='ecbUsed'?'ECB used':k==='walmartCashUsed'?'Walmart Cash used':'Gift cards used',k,true])
    ] as [string,keyof Cost,boolean][]).map(([name,key,deduct])=><tr key={key}><th>{name}</th>{[result.a,result.b].map((v,i)=><td key={i}>{deduct?'−':''}{money(v[key] as string)}</td>)}</tr>)}<tr><th>Expected earnings</th>{[result.a,result.b].map((v,i)=><td key={i}>{v.hasEnteredEarnings?v.earnings.filter(e=>e.eligible).map(e=><div key={e.id}>{['FETCH','PG_BRAND'].includes(e.rewardType)?`${e.points} points`:money(e.potentialReward)} {rewardName(e.rewardType)}</div>):'Not entered'}</td>)}</tr></tbody></table></div>
    <h3>Option B adds</h3>{[result.a,result.b].map((cost,i)=><div key={i}>{!/^0+(?:[.]0+)?$/.test(cost.manualDifference)&&<p>Option {i===0?'A':'B'} has entered cash/card of {money(cost.selectedOop)}, different from its calculated OOP. Both values are preserved.</p>}{!/^0+(?:[.]0+)?$/.test(cost.overFunding)&&<p>Option {i===0?'A':'B'} selects {money(cost.overFunding)} more funding than its amount due.</p>}</div>)}{result.addedItems.length?<ul>{result.addedItems.map(item=><li key={item.name}>{item.quantity} {item.name}</li>)}</ul>:<p>No additional product quantities.</p>}
    <p>Compared with Option A: {money(result.cashDifference)} change in cash/card · {money(result.rrDifference)} change in existing RR spent.</p>
    <p className="muted">Negative changes mean less spent. Expected rewards are not cash received. Choosing an option adds only that option to your trip.</p>
    <FormActions><button type="button" className="secondary" onClick={()=>left&&onChoose(left.plan)}>Choose A</button><button type="button" className="primary" onClick={()=>right&&onChoose(right.plan)}>Choose B</button></FormActions>
  </>:<p role="status">Choose two scenarios for a Java-calculated comparison.</p>}</Modal>
}

export function ShoppingWeek({data,api,writable,onQuick,onEdit,onAlternative,onReceipt,onPage,onPlanReceipt,onChanged,onTransactions,followUpTarget}:{followUpTarget?:HTMLElement|null;onTransactions?:(from:string,to:string)=>void;onChanged?:()=>void;data:Workspace;api?:ApiClient;writable:boolean;onQuick:(mode?:'notes'|'screenshot')=>void;onEdit:(p:Plan)=>void;onAlternative:(p:Plan)=>void;onReceipt:(id:string)=>void;onPage:(p:Page)=>void;onPlanReceipt?:(draft:PlanReceiptDraft)=>void}) {
  const [day,setDay]=useState(data.asOf),[store,setStore]=useState('ALL')
  const [compare,setCompare]=useState(false),[shopping,setShopping]=useState<PlanRow|null>(null),[checked,setChecked]=useState<string[]>([])
  const [error,setError]=useState(''),[busy,setBusy]=useState(false)
  const range=shoppingWeek(day),from=range?.start??day,to=range?.end??day
  const active=data.plans.filter(r=>!r.plan.purchasedReceiptId&&inRange(r.plan.shoppingDate,from,to)&&(store==='ALL'||r.plan.retailer===store))
  const trip=active.filter(r=>r.plan.organization?.stage==='TRIP').sort((a,b)=>Number(a.plan.organization?.checkoutOrder??0)-Number(b.plan.organization?.checkoutOrder??0))
  const ideas=active.filter(r=>r.plan.organization?.stage!=='TRIP')
  const earnings=(data.receiptEarnings??[]).filter(r=>r.receiptStatus==='ACTIVE'&&inRange(data.receipts.find(receipt=>receipt.transactionId===r.receiptId)?.date,from,to))
  const needsFollowUp=(status:string|null|undefined)=>!['COMPLETE','NOT_REQUIRED'].includes(status??'')
  const missing=earnings.filter(r=>r.earning.kind==='REGISTER_REWARD'&&['EXPECTED','REJECTED'].includes(r.earning.status)&&needsFollowUp(r.earning.details?.submissionStatus))
  const pending=earnings.filter(r=>r.earning.kind==='THIRD_PARTY_REBATE'&&['EXPECTED','SUBMITTED','PENDING','APPROVED'].includes(r.earning.status)&&needsFollowUp(r.earning.details?.submissionStatus))
  const payoutHistory=data.accountHistory.redemptionHistory as {redemptionId:string;provider:string;status:string;requestedDate:string;notes?:string}[]
  const payouts=[...new Map(payoutHistory.map(p=>[p.redemptionId,p])).values()].filter(p=>p.status==='REQUESTED'&&inRange(p.requestedDate,from,to))
  async function addToTrip(plan:Plan) {
    if(!api||!writable)return false
    setBusy(true);setError('')
    try{await api.mutate(`/shopping-plans/${plan.planId}`,'PUT',{name:plan.name,organization:{...plan.organization,stage:'TRIP',alternativeOf:plan.organization?.alternativeOf??null,checkoutOrder:Math.max(-1,...data.plans.filter(r=>inRange(r.plan.shoppingDate,from,to)).map(r=>Number(r.plan.organization?.checkoutOrder??-1)))+1,rewardSources:plan.organization?.rewardSources??[]}},crypto.randomUUID(),plan.revision);onChanged?.();return true}
    catch(e){setError((e as Error).message);return false}finally{setBusy(false)}
  }
  // The parent owns refresh so account and receipt views always come from one workspace response.
  async function choose(plan:Plan){if(await addToTrip(plan)){setCompare(false)}}
  const followUp=(<WeekPanel title="Follow-up" subtitle={`${missing.length} missing RR · ${pending.length} rebate${pending.length===1?'':'s'} pending · ${payouts.length} payout${payouts.length===1?'':'s'} requested`}>      <><p>Expected, submitted and pending benefits remain separate from received rewards.</p>{[...missing,...pending].map(row=><div className="weekly-deal" key={row.earning.earningId}><strong>{row.earning.offerReference??row.earning.provider}</strong><p>{row.earning.status} · {row.earning.details?.followUpNote}</p><button className="text-button" onClick={()=>onReceipt(row.receiptId)}>Open receipt / record update</button></div>)}{payouts.map(p=><div className="weekly-deal" key={p.redemptionId}><strong>{p.provider} payout — Requested</strong><p>{p.notes}</p><button className="text-button" onClick={()=>onPage('rewards')}>Open Conversions &amp; Payouts</button></div>)}<button className="text-button" onClick={()=>onPage('rebates')}>All rebates and follow-up</button><button className="text-button" onClick={()=>onPage('rewards')}>Conversions &amp; Payouts</button></>
    </WeekPanel>)
  return <section className="shopping-week" aria-label="My shopping week">
    <div className="shopping-week-overview">
    <WeekPanel title="My shopping week" subtitle={shoppingWeekLabel(day)}><div className="form-grid shopping-week-controls"><label>Shopping week<input className="shopping-week-date" type="date" value={day} onChange={e=>setDay(e.target.value)}/></label><label>Weekly store<select value={store} onChange={e=>setStore(e.target.value)}><option value="ALL">All stores</option>{[...new Set(data.plans.map(r=>r.plan.retailer).filter(Boolean))].map(name=><option key={name!} value={name!}>{name}</option>)}</select></label></div><div className="product-entry-actions"><button className="primary" disabled={!writable} onClick={()=>onQuick()}>+ Quick deal</button><button className="secondary" disabled={!writable} onClick={()=>onQuick('notes')}>Paste deal</button><button className="secondary" disabled={!writable} onClick={()=>onQuick('screenshot')}>Add screenshot</button><button className="secondary" onClick={()=>onTransactions?onTransactions(from,to):onPage('receipts')}>View transactions</button></div></WeekPanel>
    <WeekPanel title="Ideas">{ideas.length?ideas.map(row=><article className="weekly-deal" key={row.plan.planId}><h3>{row.plan.name}</h3>{row.plan.organization?.alternativeOf&&<small>Alternative to {data.plans.find(r=>r.plan.planId===row.plan.organization?.alternativeOf)?.plan.name??'another plan'}</small>}<p>Before reward use: {money(row.scenarioEstimate?.totalWithTax)} · Estimated cash/card: {money(row.scenarioEstimate?.calculatedOop)} · Expected earnings: {money(row.scenarioEstimate?.potentialRewards)} estimated value</p><div className="product-entry-actions"><button className="secondary" disabled={!writable} onClick={()=>onEdit(row.plan)}>Try rewards</button><button className="secondary" disabled={!writable||busy} onClick={()=>void choose(row.plan)}>Add to trip</button><button className="secondary" disabled={!writable} onClick={()=>onAlternative(row.plan)}>Create alternative</button><button className="secondary" disabled={active.filter(r=>r.plan.scenario).length<2} onClick={()=>setCompare(true)}>Compare costs</button></div></article>):<p>No ideas recorded for this week. Add a quick deal or browse existing plans below.</p>}</WeekPanel>
    </div>
    {error&&<p role="alert" className="error">{error}</p>}
    <WeekPanel title="Planned trip">{trip.length>0&&<ol className="checkout-order">{trip.map(row=><li key={row.plan.planId}><strong>{row.plan.name}</strong><p>Expected earnings: {money(row.scenarioEstimate?.potentialRewards)} estimated value</p>{row.plan.organization?.rewardSources.map(id=>{const source=data.plans.find(r=>r.plan.planId===id);return <p className="notice" key={id}>Depends on rewards from {source?.plan.name??'a missing plan'}. Confirm the reward was received and is still available before using it.</p>})}<div className="product-entry-actions trip-actions"><button className="secondary" onClick={()=>{setChecked([]);setShopping(row)}}>View shopping list</button><button className="text-button" disabled={!writable} onClick={()=>onEdit(row.plan)}>Edit transaction</button></div></li>)}</ol>}{!trip.length&&<p>Add an idea to your trip to set its checkout order.</p>}</WeekPanel>
    {followUpTarget?createPortal(followUp,followUpTarget):followUp}
    {compare&&api&&<CompareCosts rows={active.filter(r=>r.plan.scenario)} api={api} onClose={()=>setCompare(false)} onChoose={p=>void choose(p)}/>}
    {shopping&&<Modal title={`${shopping.plan.retailer??'Shopping'} — ${shopping.plan.name}`} onClose={()=>setShopping(null)}><div className="store-shopping-list">{(shopping.plan.scenario?.items??[]).map(line=><label className="check" key={line.id}><input type="checkbox" checked={checked.includes(line.id)} onChange={e=>setChecked(e.target.checked?[...checked,line.id]:checked.filter(id=>id!==line.id))}/>{line.name} · {line.quantity} items · {money(line.sale||line.regular)} each</label>)}</div><p>Estimated cash/card: {money(shopping.scenarioEstimate?.calculatedOop)}</p><p>Expected earnings: {money(shopping.scenarioEstimate?.potentialRewards)} estimated value</p><div className="product-entry-actions"><button className="secondary" disabled={!writable} onClick={()=>{onEdit(shopping.plan);setShopping(null)}}>Change price / add item</button><button className="secondary" disabled>Scan item — future</button><button className="primary" disabled={!writable||!shopping.scenarioEstimate} onClick={()=>{if(shopping.scenarioEstimate){onPlanReceipt?.({plan:shopping.plan,estimate:shopping.scenarioEstimate});setShopping(null)}}}>Purchased — attach receipt</button></div></Modal>}
  </section>
}