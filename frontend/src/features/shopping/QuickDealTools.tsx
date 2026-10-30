import { InfoHelp } from '../../components/ui'
import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import type { ApiClient } from '../../api/client'
import { scenarioRequest } from '../../api/client'
import { money } from '../../api/format'
import type { PlanScenario, ScenarioEstimate, ScenarioLine, Workspace } from '../../types/platform'
import { EvidenceField } from '../../components/EvidenceField'
import type { Upload } from '../../components/EvidenceField'

interface ImportedNotes { retailer?:string|null; items: ScenarioLine[]; coupons: PlanScenario['coupons']; promotions: PlanScenario['promotions']; unresolved: string[]; source: PlanScenario['source']; notice: string }
export function QuickDealTools({ api, scenario, onChange, store, day, data, initialMode, onStoreChange }: {onStoreChange?:(store:string)=>void;initialMode?:'notes'|'screenshot'|null; api: ApiClient; scenario: PlanScenario; onChange: (value: PlanScenario) => void; store: string; day: string; data: Workspace }) {
  const [screenshotProduct,setScreenshotProduct]=useState('')
  const [text, setText] = useState(''), [mode, setMode] = useState<'notes' | 'screenshot' | null>(initialMode??null)
  const [file, setFile] = useState<Upload | null>(null), [busy, setBusy] = useState(false), [fileBusy, setFileBusy] = useState(false)
  const [sourceOpen,setSourceOpen]=useState(false)
  const [error, setError] = useState(''), [unresolved, setUnresolved] = useState<string[]>([]), [matchNotice, setMatchNotice] = useState('')
  const [importNotice,setImportNotice]=useState('')
  const latest = useRef(scenario)
  useLayoutEffect(() => { latest.current = scenario }, [scenario])
  const matchingKey = JSON.stringify({ items: scenario.items, store, day, unallocatedManufacturer:scenario.coupons.some(c=>c.wholeReceipt&&c.kind==='MANUFACTURER'&&!c.savedCouponId&&c.amount.trim()), coupons: data.coupons.map(c => [c.couponId, c.revision]) })
  useEffect(() => {
    let active = true
    const timer = setTimeout(() => {
      try {
        const body = scenarioRequest({ ...latest.current, source: null })
        if (!data.coupons.length) return
        if (latest.current.coupons.some(c=>c.wholeReceipt&&c.kind==='MANUFACTURER'&&!c.savedCouponId&&c.amount.trim())) {
          setMatchNotice('Allocate or replace the unlinked manufacturer coupon amounts before matching recorded coupons, so the same discount is not counted twice.')
          return
        }
        void api.mutate('/shopping-plans/match-offers', 'POST', { scenario: body, retailer: store, day: day || null, couponIds: data.coupons.filter(c => c.status === 'CLIPPED').slice(0,100).map(c => c.couponId) }, crypto.randomUUID()).then(value => {
          if (!active) return
          const matches = value as PlanScenario['coupons'], current = latest.current
          const additions = matches.filter(m => !current.coupons.some(c => c.savedCouponId === m.savedCouponId))
          if (additions.length) {
            // Link an advertised deduction of the same amount to the actual saved offer instead of deducting twice.
            const remaining = [...current.coupons]
            for (const match of additions) {
              const index = remaining.findIndex(c => !c.savedCouponId && (c.kind??'MANUFACTURER') === match.kind && c.requiredQuantity === match.requiredQuantity && c.lineIds.length === match.lineIds.length && c.lineIds.every(id => match.lineIds.includes(id)))
              if (index >= 0) remaining.splice(index, 1)
            }
            onChange({ ...current, coupons: [...remaining, ...additions] })
            setMatchNotice(`${additions.length} recorded coupon match(es) attached automatically. View coupon details below.`)
          }
        }).catch(e => { if (active) setMatchNotice(`Offer matching unavailable: ${(e as Error).message}`) })
      } catch { /* Incomplete inputs stay editable; no financial assumptions are invented. */ }
    }, 450)
    return () => { active = false; clearTimeout(timer) }
    // The request identity is based on product inputs and recorded coupon revisions, not callback identity.
  }, [api, matchingKey]) // eslint-disable-line react-hooks/exhaustive-deps
  async function readNotes(recover?: Upload) {
    setBusy(true); setError('')
    try {
      const result = await api.mutate('/shopping-plans/import-preview', 'POST', recover ? {...recover,text:null} : mode === 'screenshot' ? {...file,text:screenshotProduct.trim()?`Product: ${screenshotProduct.trim()}`:null} : { text }, crypto.randomUUID()) as ImportedNotes
      const current = latest.current
      const retained = current.items.filter(l => l.name.trim() || l.regular || l.sale)
      const reusingProduct=!!result.source?.contentBase64&&result.source.contentBase64===current.source?.contentBase64&&retained.length===1&&result.items.length===1
      if(recover&&!reusingProduct&&!(retained.length===0&&result.items.length===1))throw new Error('This screenshot cannot be safely linked to one existing product. Your draft is unchanged; review its offers manually.')
      const decimalKey=(value:string)=>{const [whole,fraction='']=value.trim().split('.');const tail=fraction.replace(/0+$/,'');return `${whole.replace(/^0+/,'')||'0'}${tail?'.'+tail:''}`}
      const linkedIds=(ids:string[])=>reusingProduct?ids.map(id=>id===result.items[0].id?retained[0].id:id):ids
      const importedCoupons=result.coupons.map(c=>({...c,lineIds:linkedIds(c.lineIds)})).filter(c=>!current.coupons.some(old=>(old.kind??'MANUFACTURER')===(c.kind??'MANUFACTURER')&&decimalKey(old.amount)===decimalKey(c.amount)&&String(old.requiredQuantity)===String(c.requiredQuantity)&&old.lineIds.length===c.lineIds.length&&old.lineIds.every(id=>c.lineIds.includes(id))))
      const importedPromotions=result.promotions.map(p=>({...p,lineIds:linkedIds(p.lineIds)})).filter(p=>!current.promotions.some(old=>old.rewardType===p.rewardType&&decimalKey(old.rewardAmount)===decimalKey(p.rewardAmount)&&old.requiredQuantity===p.requiredQuantity&&decimalKey(old.requiredSpend)===decimalKey(p.requiredSpend)&&old.lineIds.length===p.lineIds.length&&old.lineIds.every(id=>p.lineIds.includes(id))))

      onChange({ ...current, items: reusingProduct ? current.items : result.items.length ? [...retained, ...result.items.map(l => ({ ...l, regular: l.regular ?? '' }))] : current.items, coupons: [...current.coupons.filter(c => c.amount.trim()), ...importedCoupons], promotions: [...current.promotions.filter(p => p.rewardAmount.trim()), ...importedPromotions], source: { ...result.source!, text: [current.source?.text,...(result.source?.text===current.source?.text?[]:[result.source?.text])].filter(Boolean).join('\n\n'), earlier:[...(current.source?.earlier??[]).filter(s=>!result.source?.contentBase64||s.contentBase64!==result.source.contentBase64),...(current.source?.contentBase64&&current.source.contentBase64!==result.source?.contentBase64&&!(current.source.earlier??[]).some(s=>s.contentBase64===current.source?.contentBase64)?[{text:'',mediaType:current.source.mediaType,contentBase64:current.source.contentBase64}]:[])] } })
      if(result.retailer&&(!retained.length||result.retailer.toLowerCase()===store.toLowerCase()))onStoreChange?.(result.retailer)
      else if(result.retailer&&result.retailer.toLowerCase()!==store.toLowerCase())result.unresolved.push(`Source store: ${result.retailer}. Existing plan store: ${store}. Review which store this plan is for.`)
      if (!result.items.length) setError('No products were recognized. Your existing products are preserved; enter the products below and review the original notes.')
      setImportNotice(`${importedCoupons.length} coupon(s) and ${importedPromotions.length} expected reward/rebate(s) added.${reusingProduct?' Existing product details preserved.':''}`)
      setUnresolved(result.unresolved); setMode(null); setText(''); setFile(null)
    } catch(e) { setError((e as Error).message) } finally { setBusy(false) }
  }
  return <section className="quick-deal-tools" aria-label="Quick deal entry">
    <div className="product-entry-actions"><button type="button" className="secondary" onClick={() => setMode(mode === 'notes' ? null : 'notes')}>Paste deal</button><button type="button" className="secondary" onClick={() => setMode(mode === 'screenshot' ? null : 'screenshot')}>Add screenshot</button></div>
    {mode && <div className="quick-note-entry">{mode === 'notes' ? <label>Type or paste your deal notes<textarea rows={5} value={text} onChange={e => setText(e.target.value)} placeholder={'Colgate 6 x2\n5 off 2\nearn 5 RR'} /></label> : <><EvidenceField dropEnabled onChange={setFile} onBusy={setFileBusy} /><label>Product name if the screenshot is hard to read (optional)<input value={screenshotProduct} onChange={e=>setScreenshotProduct(e.target.value)} placeholder="For example, Poise pads"/></label></>}<button className="secondary" type="button" disabled={busy || fileBusy || (mode === 'notes' ? !text.trim() : !file)} onClick={() => void readNotes()}>{busy ? 'Reading…' : 'Add to this plan'}</button></div>}
    {scenario.source?.contentBase64&&scenario.source.mediaType&&<button type="button" className="secondary" disabled={busy||fileBusy} onClick={()=>void readNotes({mediaType:scenario.source!.mediaType!,contentBase64:scenario.source!.contentBase64!})}>{busy?'Reading…':'Re-read screenshot offers'}</button>}
    {importNotice&&<p role="status">{importNotice}</p>}
    {error && <p role="alert" className="error">{error}</p>}
    {unresolved.length > 0 && <details open><summary>Notes needing entry ({unresolved.length})</summary><p>These lines remain in your source notes. Enter any missing item, discount, tax or qualification below.</p><pre className="source-note">{unresolved.join('\n')}</pre></details>}
    {scenario.source && <details onToggle={e=>setSourceOpen(e.currentTarget.open)}><summary>Original deal notes / screenshot</summary><pre className="source-note">{scenario.source.text}</pre>{sourceOpen&&scenario.source.contentBase64 && <img className="deal-source-image" alt="Original deal screenshot" src={`data:${scenario.source.mediaType};base64,${scenario.source.contentBase64}`} />}{sourceOpen&&scenario.source.earlier?.filter(s=>s.contentBase64).map((source,i)=><img key={i} className="deal-source-image" alt={`Earlier deal screenshot ${i+1}`} src={`data:${source.mediaType};base64,${source.contentBase64}`}/>)}</details>}
    {matchNotice && <p role="status">{matchNotice}</p>}
    {scenario.source && <details><summary>Advertised deal / my available offers</summary><div className="table-wrap"><table><thead><tr><th>Advertised</th><th>My available offer</th></tr></thead><tbody>{scenario.coupons.filter(c=>c.savedCouponId).map(c=><tr key={c.id}><td>See original deal notes above</td><td>{c.description} · {money(c.amount)} · recorded clipped coupon</td></tr>)}<tr><td>Advertised rebates and point rewards</td><td>Your offer is unknown unless separately recorded. Enter your own amount in Expected earnings; it is not confirmed by the advertisement.</td></tr></tbody></table></div></details>}
    <p className="muted">Recorded SKU, store and date-qualified dollar coupons attach automatically. Other account offers are unknown until recorded; advertised earnings are estimates.</p>
  </section>
}
export function SaleButtons({ line, onChange, products=[] }: {products?:ScenarioLine[]; line: ScenarioLine; onChange: (value: ScenarioLine) => void }) {
  const options = [['SALE','Sale price'],['BOGO_FREE','BOGO free'],['BOGO_HALF','BOGO 50%'],['BUY_ONE_GET_TWO','Buy 1 get 2 free'],['TWO_FOR','2 for $___'],['OTHER','Other']] as const
  return <div className="sale-buttons"><div className="product-entry-actions" aria-label={`Sale pricing for ${line.name || 'new product'}`}>{options.map(([type, name]) => <button className="secondary" type="button" key={type} aria-pressed={type === 'SALE' ? !line.priceRule : line.priceRule?.type === type} onClick={() => onChange({ ...line, priceRule: type === 'SALE' || type === 'OTHER' ? null : { type, bundlePrice: line.priceRule?.bundlePrice ?? null } })}>{name}</button>)}</div>{line.priceRule?.type === 'TWO_FOR' && <label>Price for two — {line.name || 'new product'}<input inputMode="decimal" value={line.priceRule.bundlePrice ?? ''} onChange={e => onChange({ ...line, priceRule: { type: 'TWO_FOR', bundlePrice: e.target.value || null } })} /></label>}{line.priceRule && <InfoHelp>Complete groups only; remaining units keep their entered unit price.</InfoHelp>}{line.priceRule&&line.priceRule.type!=='TWO_FOR'&&products.length>1&&<details><summary>Qualifying products for this sale</summary><p>For a mixed-price group, the cheapest qualifying units receive the entered discount.</p>{products.map(product=><label className="check" key={product.id}><input type="checkbox" disabled={product.id===line.id} checked={product.id===line.id||!!line.priceRule?.lineIds?.includes(product.id)} onChange={e=>{const ids=line.priceRule?.lineIds??[line.id];onChange({...line,priceRule:{...line.priceRule!,lineIds:e.target.checked?[...ids,product.id]:ids.filter(id=>id!==product.id)}})}}/>{product.name||'New product'} · {product.quantity}</label>)}</details>}</div>
}
export function LiveDealSummary({ report, scenario }: { report: ScenarioEstimate | null; scenario: PlanScenario }) {
  return <aside className="live-deal-summary" aria-label="Live deal calculation" aria-live="polite"><strong>Deal calculation</strong>{report ? <>
    <div><span>Merchandise</span><b>{money(report.saleTotal)}</b></div>
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
  const used=new Set((data.rrMatches as {sourceId:string}[]).map(r=>r.sourceId))
  const receiptRewards=(data.receiptEarnings??[]).filter(row=>row.receiptStatus==='ACTIVE'&&row.earning.kind==='REGISTER_REWARD'&&row.earning.status==='PAID'&&!used.has(row.earning.earningId)&&!(data.unavailableRewardSources??[]).some(r=>r.sourceId===row.earning.earningId&&(!day||r.date<=day))).map(row=>({id:row.earning.earningId,name:row.earning.offerReference??'Printed Register Reward',amount:row.earning.confirmed?.amount??row.earning.expected?.amount,expires:row.earning.expirationDate,earned:data.receipts.find(r=>r.transactionId===row.receiptId)?.date}))
  const rewards=[...receiptRewards,...standalone.filter(r=>r.status==='UNUSED'||r.status==='UNAVAILABLE'&&(data.unavailableRewardSources??[]).some(m=>m.sourceId===r.rewardId&&!!day&&m.date>day)).map(r=>({id:r.rewardId,name:r.description,amount:r.amount,expires:r.expirationDate,earned:r.earnedDate}))].filter(r=>!(data.unavailableRewardSources??[]).some(m=>m.sourceId===r.id&&(!day||m.date<=day))&&r.amount!=null&&(!r.expires||!day||r.expires>=day)&&(!r.earned||!day||r.earned<=day))
  function select(id:string){const reward=rewards.find(r=>r.id===id);if(!reward?.amount)return;onChange({...scenario,payments:[...(scenario.payments??[]),{id:crypto.randomUUID(),type:'REGISTER_REWARD',amount:reward.amount,notes:`${reward.name} · ${reward.id} · expires ${reward.expires??'unknown'}`} ]})}
  const wc=data.balances.find(b=>b.provider==='Walgreens Cash')?.balance.endUsd
  return <details className="recorded-reward-picker" onToggle={e=>setOpen(e.currentTarget.open)}><summary>Use my existing rewards</summary>{error&&<p role="alert">{error}</p>}<p>Recorded Walgreens Cash: {money(wc)}. Amount available to redeem depends on the transaction’s recorded program rules.</p><label>Select a recorded RR<select value="" onChange={e=>select(e.target.value)}><option value="">Choose amount, source and expiration</option>{rewards.map(r=><option key={r.id} value={r.id} disabled={scenario.payments?.some(p=>p.notes.includes(r.id))}>{money(r.amount)} · {r.name} · expires {r.expires??'unknown'}</option>)}</select></label><small>Selection plans a use only. Current balances change through the existing receipt and reward-recording workflows.</small></details>
}
