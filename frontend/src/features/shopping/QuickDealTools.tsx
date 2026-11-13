import { availableRegisterRewards } from '../rewards/availableRegisterRewards'
import { ProductSelectionList } from '../../components/ProductSelection'
import { DisclosureSummary, Modal, InfoHelp } from '../../components/ui'
import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import type { ApiClient } from '../../api/client'
import { zeroScenarioAmount } from '../../api/client'
import { money } from '../../api/format'
import type { PlanScenario, ScenarioEstimate, ScenarioLine, Workspace } from '../../types/platform'
import { DealImages } from './DealImages'
import type { Upload } from '../../components/EvidenceField'

interface ImportedNotes { payments?:PlanScenario['payments']; reader?:string; advertisedBundle?:boolean; walgreensCashUsed?:string|null; retailer?:string|null; items: ScenarioLine[]; coupons: PlanScenario['coupons']; promotions: PlanScenario['promotions']; unresolved: string[]; source: PlanScenario['source']; notice: string }
export function QuickDealTools({ api, scenario, onChange, store, initialMode, onStoreChange, onImportedName }: {onImportedName?:(name:string)=>void;onStoreChange?:(store:string)=>void;initialMode?:'notes'|'screenshot'|null; api: ApiClient; scenario: PlanScenario; onChange: (value: PlanScenario) => void; store: string; day: string; data: Workspace }) {
  const [screenshotProduct,setScreenshotProduct]=useState('')
  const [text, setText] = useState(''), [mode, setMode] = useState<'notes' | 'screenshot' | null>(initialMode??null)
  const [files, setFiles] = useState<Upload[]>([]), [busy, setBusy] = useState(false), [fileBusy, setFileBusy] = useState(false)
  const [sourceOpen,setSourceOpen]=useState(false)
  const [error, setError] = useState(''), [unresolved, setUnresolved] = useState<string[]>([])
  const [importNotice,setImportNotice]=useState('')
  const reading=useRef<AbortController|null>(null)
  useEffect(()=>()=>reading.current?.abort(),[])
  const latest = useRef(scenario)
  useLayoutEffect(() => { latest.current = scenario }, [scenario])
  const structuredKey=(value:PlanScenario)=>JSON.stringify({...value,source:null})
  async function readTextNotes(notes:string,replace=false) {
    const before=latest.current
    if(replace&&before.items.some(l=>l.name.trim()||l.regular||l.sale)&&!window.confirm('Update deal from notes? This replaces the current products, coupons, promotions and payments, including manual corrections. Tax and plan name are preserved.'))return
    const source={advertisedDealIds:before.source?.advertisedDealIds,purchaseChannel:before.source?.purchaseChannel,text:replace?notes:[before.source?.text,notes].filter(Boolean).join('\n\n'),mediaType:before.source?.mediaType??null,contentBase64:before.source?.contentBase64??null,earlier:before.source?.earlier??[]}
    const attached={...before,source}
    latest.current=attached;onChange(attached);setSourceOpen(true);setMode(null);setBusy(true);setError('');setImportNotice('')
    const key=structuredKey(attached)
    try {
      const result=await api.mutate('/shopping-plans/import-preview','POST',{text:notes},crypto.randomUUID()) as ImportedNotes
      if(!result.items.length)throw new Error('No products could be read. Edit the notes or enter products below.')
      const current=latest.current
      if(structuredKey(current)!==key||current.source?.text!==source.text)throw new Error('Your draft changed while reading. Updates were preserved; retry when ready.')
      const updatedSource={...source,purchaseChannel:result.source?.purchaseChannel??source.purchaseChannel}
      const retained=replace?[]:current.items.filter(l=>l.name.trim()||l.regular||l.sale)
      const next={...current,items:[...retained,...result.items.map(l=>({...l,regular:l.regular??''}))],coupons:[...(replace?[]:current.coupons.filter(c=>c.amount.trim())),...result.coupons],promotions:[...(replace?[]:current.promotions.filter(p=>p.rewardAmount.trim())),...result.promotions],payments:[...(replace?[]:current.payments??[]),...(result.payments??[])],source:updatedSource}
      latest.current=next;onChange(next)
      onImportedName?.(result.items[0].name.trim().split(/\s+/)[0])
      if(result.retailer&&!retained.length)onStoreChange?.(result.retailer)
      setUnresolved(result.unresolved??[]);setImportNotice('Deal updated from notes. Expected rewards remain estimates.');setText('')
    }catch(e){setError(`Automatic reading failed: ${(e as Error).message} Your notes and fields are preserved.`)}
    finally{setBusy(false)}
  }
  async function readNotes(recover?: Upload) {
    if(!recover&&mode==='notes')return readTextNotes(text)
    setBusy(true); setError(''); setImportNotice('')
    const controller=new AbortController();reading.current=controller
    const deadline=setTimeout(()=>controller.abort(),120000)
    const originalSource=latest.current.source
    let attached=false
    try {
      const incoming=recover?[recover,...(originalSource?.earlier??[]).filter(s=>s.contentBase64).map(s=>({mediaType:s.mediaType!,contentBase64:s.contentBase64!}))]:mode==='screenshot'?files:[]
      const first=incoming[0]
      const attachedEarlier=[...incoming.slice(1).map(image=>({...image,text:''})),...(originalSource?.earlier??[]),...(originalSource?.contentBase64&&originalSource.contentBase64!==first?.contentBase64?[originalSource]:[])]
      const source={advertisedDealIds:originalSource?.advertisedDealIds,purchaseChannel:originalSource?.purchaseChannel,text:[originalSource?.text,mode==='notes'?text:''].filter(Boolean).join('\n\n'),mediaType:first?.mediaType??originalSource?.mediaType??null,contentBase64:first?.contentBase64??originalSource?.contentBase64??null,earlier:[...new Map(attachedEarlier.filter(s=>s.contentBase64&&s.contentBase64!==first?.contentBase64).map(s=>[s.contentBase64,s])).values()]}
      if(source.earlier.length>20||[source,...source.earlier].reduce((n,s)=>n+(s.contentBase64?.length??0),0)>7*1024*1024)throw new Error('The image exceeds this plan’s attachment limit. Use a smaller image or a separate deal.')
      onChange({...latest.current,source});attached=true;setSourceOpen(true);setMode(null)

      const result = await api.mutate('/shopping-plans/import-preview', 'POST', recover ? {...recover,files:scenario.source?.earlier?.filter(s=>s.contentBase64).map(s=>({mediaType:s.mediaType,contentBase64:s.contentBase64})),text:null} : mode === 'screenshot' ? {...(files.length===1?files[0]:{files}),text:screenshotProduct.trim()?`Product: ${screenshotProduct.trim()}`:null} : { text }, crypto.randomUUID(),undefined,controller.signal) as ImportedNotes
      if(controller.signal.aborted)return
      if (!result.items.length) {setUnresolved(result.unresolved);throw new Error('No products could be read from this source.')}
      const current = latest.current
      const retained = current.items.filter(l => l.name.trim() || l.regular || l.sale)
      const decimalKey=(value:string)=>{const [whole,fraction='']=value.trim().split('.');const tail=fraction.replace(/0+$/,'');return `${whole.replace(/^0+/,'')||'0'}${tail?'.'+tail:''}`}
      const sameSource=!!result.source?.contentBase64&&result.source.contentBase64===originalSource?.contentBase64
      const links=new Map<string,string>()
      if(sameSource&&retained.length===1&&result.items.length===1)links.set(result.items[0].id,retained[0].id)
      else if(sameSource&&retained.length===result.items.length){
        const unused=[...retained]
        for(const item of result.items){const index=unused.findIndex(old=>old.name.toLowerCase()===item.name.toLowerCase()&&String(old.quantity)===String(item.quantity)&&decimalKey(old.sale||old.regular)===decimalKey(item.sale||item.regular||''));if(index>=0)links.set(item.id,unused.splice(index,1)[0].id)}
      }
      const reusingProduct=sameSource&&links.size===result.items.length
      if((recover||sameSource)&&!reusingProduct&&retained.length>0)throw new Error('This screenshot cannot be safely linked to existing products. Your draft is unchanged; review its offers manually.')
      const linkedIds=(ids:string[])=>ids.map(id=>links.get(id)??id)
      const importedCoupons=result.coupons.map(c=>({...c,lineIds:linkedIds(c.lineIds)})).filter(c=>!current.coupons.some(old=>(old.kind??'MANUFACTURER')===(c.kind??'MANUFACTURER')&&decimalKey(old.amount)===decimalKey(c.amount)&&String(old.requiredQuantity)===String(c.requiredQuantity)&&old.lineIds.length===c.lineIds.length&&old.lineIds.every(id=>c.lineIds.includes(id))))
      const importedPromotions=result.promotions.map(p=>({...p,lineIds:linkedIds(p.lineIds)})).filter(p=>!current.promotions.some(old=>old.rewardType===p.rewardType&&decimalKey(old.rewardAmount)===decimalKey(p.rewardAmount)&&old.requiredQuantity===p.requiredQuantity&&decimalKey(old.requiredSpend)===decimalKey(p.requiredSpend)&&old.lineIds.length===p.lineIds.length&&old.lineIds.every(id=>p.lineIds.includes(id))))

      if(result.walgreensCashUsed&&current.walgreensCashUsed&&!zeroScenarioAmount(current.walgreensCashUsed)&&decimalKey(current.walgreensCashUsed)!==decimalKey(result.walgreensCashUsed))result.unresolved.push(`Source uses $${result.walgreensCashUsed} Walgreens Cash; your existing WC amount was preserved.`)
      const earlier=[...(result.source?.earlier??[]),...(current.source?.earlier??[]),...(current.source?.contentBase64&&current.source.contentBase64!==result.source?.contentBase64?[{text:'',mediaType:current.source.mediaType,contentBase64:current.source.contentBase64}]:[])]
      const importedSource={...result.source!,advertisedDealIds:current.source?.advertisedDealIds,purchaseChannel:result.source?.purchaseChannel??current.source?.purchaseChannel,text:[current.source?.text,...(result.source?.text===current.source?.text?[]:[result.source?.text])].filter(Boolean).join('\n\n'),earlier:[...new Map(earlier.filter(s=>s.contentBase64&&s.contentBase64!==result.source?.contentBase64).map(s=>[s.contentBase64,s])).values()]}
      if(importedSource.earlier.length>20||[importedSource,...importedSource.earlier].reduce((n,s)=>n+(s.contentBase64?.length??0),0)>7*1024*1024)throw new Error('These screenshots exceed the saved plan attachment limit. Split them into separate deals.')
      onChange({ ...current, items: reusingProduct ? current.items : result.items.length ? [...retained, ...result.items.map(l => ({ ...l, regular: l.regular ?? '' }))] : current.items, coupons: [...current.coupons.filter(c => c.amount.trim()), ...importedCoupons], promotions: [...current.promotions.filter(p => p.rewardAmount.trim()), ...importedPromotions], source: importedSource })
      onImportedName?.(result.items[0].name.trim().split(/\s+/)[0])
      if(result.retailer&&(!retained.length||result.retailer.toLowerCase()===store.toLowerCase()))onStoreChange?.(result.retailer)
      else if(result.retailer&&result.retailer.toLowerCase()!==store.toLowerCase())result.unresolved.push(`Source store: ${result.retailer}. Existing plan store: ${store}. Review which store this plan is for.`)
      setImportNotice(`${result.reader==='local-ai'?'Local AI draft: ':result.reader==='local'?'Local reading: ':''}${result.advertisedBundle?'Advertised bundle estimate added. Review product details and offer requirements below. ':''}${importedCoupons.length} coupon(s) and ${importedPromotions.length} expected reward/rebate(s) added.${reusingProduct?' Existing product details preserved.':''}`)
      setUnresolved(result.unresolved); setMode(null); setText(''); setFiles([])
    } catch(e) {
      if(reading.current!==controller)return
      if(controller.signal.aborted){setImportNotice('Reading stopped. Your screenshot is attached; retry or enter the deal details below.');return}
      if(attached){setImportNotice(`Source attached. Automatic reading could not fill the deal: ${(e as Error).message} Enter or correct the details below.`);setMode(null);setFiles([]);setText('')}
      else setError((e as Error).message)
    } finally { clearTimeout(deadline);if(reading.current===controller){reading.current=null;setBusy(false)} }
  }
  return <section className="quick-deal-tools" aria-label="Quick deal entry">
    <div className="product-entry-actions"><button type="button" className="secondary" disabled={busy} onClick={() => setMode(mode === 'notes' ? null : 'notes')}>Paste deal</button><button type="button" className="secondary" disabled={busy} onClick={() => setMode(mode === 'screenshot' ? null : 'screenshot')}>Add screenshot</button></div>
    {mode && <Modal title={mode==='notes'?'Paste deal':'Add screenshot'} onClose={()=>{if(!busy&&!fileBusy)setMode(null)}}><div className="quick-note-entry">{error&&<p role="alert" className="error">{error}</p>}{busy&&<p role="status">Reading your image locally. This may take a minute; your draft will appear here when reading finishes.</p>}{mode === 'notes' ? <label>Type or paste your deal notes<textarea rows={5} value={text} onChange={e => setText(e.target.value)} placeholder={'Colgate 6 x2\n5 off 2\nearn 5 RR'} /></label> : <><DealImages api={api} onChange={setFiles} onBusy={setFileBusy} /><label>Product name if the screenshot is hard to read (optional)<input value={screenshotProduct} onChange={e=>setScreenshotProduct(e.target.value)} placeholder="For example, Poise pads"/></label></>}<p className="muted">Photos are read locally on your computer. No paid API or key is required.</p><button className="secondary" type="button" disabled={busy || fileBusy || (mode === 'notes' ? !text.trim() : !files.length)} onClick={() => void readNotes()}>{busy ? 'Reading…' : 'Add to this plan'}</button></div></Modal>}
    {scenario.source?.contentBase64&&scenario.source.mediaType&&<button type="button" className="secondary" disabled={busy||fileBusy} onClick={()=>void readNotes({mediaType:scenario.source!.mediaType!,contentBase64:scenario.source!.contentBase64!})}>{busy?'Reading…':'Re-read screenshot offers'}</button>}
    {busy&&<p role="status">Your source is attached. Reading deal details locally… <button type="button" className="text-button" onClick={()=>{reading.current?.abort();setBusy(false)}}>Cancel reading</button></p>}{importNotice&&<p role="status">{importNotice}</p>}
    {!mode&&error && <p role="alert" className="error">{error}</p>}
    {unresolved.length > 0 && <details open><DisclosureSummary>Notes needing entry ({unresolved.length})</DisclosureSummary><p>These lines remain in your source notes. Enter any missing item, discount, tax or qualification below.</p><pre className="source-note">{unresolved.join('\n')}</pre></details>}
    {scenario.source && <details open={sourceOpen} onToggle={e=>setSourceOpen(e.currentTarget.open)}><DisclosureSummary>Original deal notes / screenshot</DisclosureSummary><label>Purchase channel<select value={scenario.source.purchaseChannel??'UNKNOWN'} onChange={e=>{const next={...latest.current,source:{...latest.current.source!,purchaseChannel:e.target.value}};latest.current=next;onChange(next)}}><option value="UNKNOWN">Unknown / not recorded</option><option value="ONLINE">Online</option><option value="IN_STORE">In store</option></select></label><label>Original deal notes<textarea rows={8} value={scenario.source.text} onChange={e=>{const next={...latest.current,source:{...latest.current.source!,text:e.target.value}};latest.current=next;onChange(next)}} /></label><button type="button" className="secondary" disabled={busy||!scenario.source.text.trim()} onClick={()=>void readTextNotes(latest.current.source!.text,true)}>Update Deal From Notes</button>{sourceOpen&&scenario.source.contentBase64 && <img className="deal-source-image" alt="Original deal screenshot" src={`data:${scenario.source.mediaType};base64,${scenario.source.contentBase64}`} />}{sourceOpen&&scenario.source.earlier?.filter(s=>s.contentBase64).map((source,i)=><img key={i} className="deal-source-image" alt={`Earlier deal screenshot ${i+1}`} src={`data:${source.mediaType};base64,${source.contentBase64}`}/>)}</details>}
    {scenario.source && <details><DisclosureSummary>Advertised deal / my available offers</DisclosureSummary><div className="table-wrap"><table><thead><tr><th>Advertised</th><th>My available offer</th></tr></thead><tbody>{scenario.coupons.filter(c=>c.savedCouponId).map(c=><tr key={c.id}><td>See original deal notes above</td><td>{c.description} · {money(c.amount)} · recorded clipped coupon</td></tr>)}<tr><td>Advertised rebates and point rewards</td><td>Imported offers are your selected assumptions. Review unreadable conditions; expected earnings are not available checkout funds.</td></tr></tbody></table></div></details>}
    <p className="muted">Imported coupons are treated as available. Check unclear details against your source. Your recorded rewards fund checkout; advertised earnings remain estimates.</p>
  </section>
}
export function SaleButtons({ line, onChange, products=[], baseLabel='Sale price' }: {baseLabel?:string;products?:ScenarioLine[]; line: ScenarioLine; onChange: (value: ScenarioLine) => void }) {
  const options = [['SALE',baseLabel],['BOGO_FREE','BOGO free'],['BOGO_HALF','BOGO 50%'],['BUY_ONE_GET_TWO','Buy 1 get 2 free'],['TWO_FOR','2 for $___'],['OTHER','Other']] as const
  return <div className="sale-buttons"><div className="product-entry-actions" aria-label={`Sale pricing for ${line.name || 'new product'}`}>{options.map(([type, name]) => <button className="secondary" type="button" key={type} aria-pressed={type === 'SALE' ? !line.priceRule : line.priceRule?.type === type} onClick={() => onChange({ ...line, priceRule: type === 'SALE' || type === 'OTHER' ? null : { type, bundlePrice: line.priceRule?.bundlePrice ?? null } })}>{name}</button>)}</div>{line.priceRule?.type === 'TWO_FOR' && <label>Price for two — {line.name || 'new product'}<input inputMode="decimal" value={line.priceRule.bundlePrice ?? ''} onChange={e => onChange({ ...line, priceRule: { type: 'TWO_FOR', bundlePrice: e.target.value || null } })} /></label>}{line.priceRule && <InfoHelp>Complete groups only; remaining units keep their entered unit price.</InfoHelp>}{line.priceRule&&line.priceRule.type!=='TWO_FOR'&&products.length>1&&<details><DisclosureSummary>Qualifying products for this sale</DisclosureSummary><p>For a mixed-price group, the cheapest qualifying units receive the entered discount.</p><ProductSelectionList products={products.map(product=>({id:product.id,name:product.name,quantity:product.quantity,disabled:product.id===line.id}))} selected={[...new Set([line.id,...(line.priceRule.lineIds??[])])]} onToggle={(id,checked)=>{const ids=line.priceRule?.lineIds??[line.id];onChange({...line,priceRule:{...line.priceRule!,lineIds:checked?[...ids,id]:ids.filter(value=>value!==id)}})}}/></details>}</div>
}
export function LiveDealSummary({ report, scenario }: { report: ScenarioEstimate | null; scenario: PlanScenario }) {
  return <aside className="live-deal-summary" aria-label="Live deal calculation" aria-live="polite"><strong>Deal calculation</strong>{report ? <>
    <div><span>Merchandise</span><b>{money(report.saleTotal)}</b></div>
    {report.storePromotions?.length? <div><span>Store Promotion (included in sale prices)</span><b>−{money(report.storePromotionTotal)}</b></div>:null}
    <div><span>Coupons / discounts</span><b>−{money(report.couponTotal)}</b></div>
    <div><span>Tax</span><b>{money(report.tax)}</b></div>
    <div><span>Before reward use</span><b>{money(report.totalWithTax)}</b></div>
    <div><span>RR used</span><b>−{money(report.registerRewardsUsed)}</b></div>
    <div><span>WC used</span><b>−{money(report.walgreensCashUsed)}</b></div>
    <div className="oop-highlight"><span>Estimated cash / card OOP</span><b>{money(report.calculatedOop)}</b></div>
    <div><span>Expected earnings</span><b>{money(report.potentialRewards)} estimated value</b></div>
    <small>Expected rewards are not available funds. Gift cards, ECB and other funding remain in the detailed breakdown.</small>
    {scenario.promotions.map(p => <small key={p.id}>{p.description}: {['FETCH','PG_BRAND'].includes(p.rewardType) ? `${p.rewardAmount} points` : money(p.rewardAmount)} {({REGISTER_REWARD:'RR',WALGREENS_CASH:'WC',PG_BRAND:'P&G Brand',FETCH:'Fetch'}[p.rewardType]??p.rewardType.replaceAll('_',' '))}</small>)}
  </> : <p>Enter a product, quantity and purchase price to calculate.</p>}</aside>
}

export function RewardPicker({api,data,scenario,onChange,day}:{api:ApiClient;data:Workspace;scenario:PlanScenario;onChange:(value:PlanScenario)=>void;day:string}) {
  const [open,setOpen]=useState(false)
  const [standalone,setStandalone]=useState<import('../../types/platform').StandaloneRR[]>([]),[error,setError]=useState('')
  useEffect(()=>{let active=true;if(!open)return;void api.standaloneRewards().then(value=>{if(active)setStandalone(value)}).catch(e=>{if(active)setError((e as Error).message)});return()=>{active=false}},[api,open])
  const rewards=availableRegisterRewards(data,standalone,day)
  function select(id:string){
    const reward=rewards.find(r=>r.id===id);if(!reward?.amount||scenario.payments?.some(p=>p.rewardSourceId===id))return
    const amountKey=(amount:string)=>amount.replace(/^0+(?=[0-9])/,'').replace(/(\.[0-9]*?)0+$/,'$1').replace(/\.$/,'')
    const payments=scenario.payments??[]
    const existing=payments.find(p=>p.type==='REGISTER_REWARD'&&!p.rewardSourceId&&amountKey(p.amount)===amountKey(reward.amount!)&&(!p.registerRewardType||p.registerRewardType==='UNKNOWN'||p.registerRewardType===reward.registerRewardType))
    const selected={id:existing?.id??crypto.randomUUID(),type:'REGISTER_REWARD',amount:reward.amount,registerRewardType:reward.registerRewardType,originProduct:reward.originProduct,originBrand:reward.originBrand,originProgram:reward.originProgram,originPromotion:reward.originPromotion,rewardSourceId:reward.id,notes:`${reward.name} · ${reward.id} · expires ${reward.expires??'unknown'}`}
    onChange({...scenario,payments:existing?payments.map(p=>p.id===existing.id?selected:p):[...payments,selected]})
  }
  const wc=data.balances.find(b=>b.provider==='Walgreens Cash')?.balance.endUsd
  return <details className="recorded-reward-picker" onToggle={e=>setOpen(e.currentTarget.open)}><DisclosureSummary>Choose a reward manually (optional)</DisclosureSummary>{error&&<p role="alert">{error}</p>}<p>Walgreens Cash balance: {money(wc)}. You do not need to select rewards here for automatic suggestions.</p><label>Select a recorded RR<select aria-label="Select a recorded RR" value="" onChange={e=>select(e.target.value)}><option value="">Choose a reward for a manual estimate</option>{rewards.map(r=><option key={r.id} value={r.id} disabled={scenario.payments?.some(p=>p.rewardSourceId===r.id||p.notes.includes(r.id))}>{money(r.amount)} · {r.name} · expires {r.expires??'not recorded'}{r.registerRewardType==='UNKNOWN'?' · type not recorded':` · ${r.registerRewardType==='MANUFACTURER'?'manufacturer':'store'} reward`}</option>)}</select></label><label>Recorded gift card (optional)<select aria-label="Recorded gift card (optional)" value="" onChange={e=>{const card=data.rewardInstruments?.find(card=>card.id===e.target.value);if(card&&!scenario.payments?.some(p=>p.rewardSourceId===card.id))onChange({...scenario,payments:[...(scenario.payments??[]),{id:crypto.randomUUID(),type:'GIFT_CARD',amount:'0',notes:card.nickname||card.provider,rewardSourceId:card.id}]})}}><option value="">Choose a card, then enter a planned amount below</option>{data.rewardInstruments?.filter(card=>card.kind==='GIFT_CARD'&&card.status!=='ARCHIVED').map(card=><option key={card.id} value={card.id}>{card.nickname||card.provider} · {money(card.calculated)}{!card.historyComplete?' · Balance needs review':''}</option>)}</select></label><small>Choosing a reward here only changes this estimate. It does not spend the reward.</small></details>
}
