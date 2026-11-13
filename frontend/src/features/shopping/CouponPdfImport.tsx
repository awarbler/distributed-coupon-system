import { useRef, useState } from 'react'
import { ApiClient, ApiError } from '../../api/client'
import { date, integer, label } from '../../api/format'
import { DisclosureSummary, Modal, Notice, SectionHelp } from '../../components/ui'
import type { Coupon } from '../../types/platform'

type Fields = Record<string, unknown> & { offerDetails?: Record<string, string> }
type Draft = Fields & {
  couponId: string; description: string; expectedRevision: string; target: string;
  matches: string[]; reasons: string[]; approved: boolean; skipped: boolean;
  protectedRecord: boolean; separateConfirmed: boolean; sourceText: string; processed?:string; failed?:boolean
}
type ImportResult={coupons:Coupon[];outcomes:{couponId:string;outcome:string}[];summary:{imported:number;updated:number;alreadySaved:number;needsReview:number;failed:number}}
type Report = { documentKind?:string; entries: Fields[]; layout?:string; extractedCount?: number; declaredCount?: number; offerTypes?: Record<string, number>; missingExpiration?: number; needsReview?: number; warnings?: string[]; ocrPages?: number }
const value = (v: unknown) => String(v ?? '').trim()
const numeric = (v: unknown) => /^(?:\d+(?:\.\d+)?|\.\d+)$/.test(value(v))
const positive = (v: unknown) => numeric(v) && /[1-9]/.test(value(v))
const quantity = (v: unknown, minimum = 1) => /^\d+$/.test(value(v)) && Number.isSafeInteger(Number(v)) && Number(v) >= minimum && Number(v) <= 2147483647
const validDate = (v: unknown) => /^\d{4}-\d{2}-\d{2}$/.test(value(v)) && !Number.isNaN(Date.parse(value(v))) && new Date(value(v)).toISOString().slice(0, 10) === value(v)
const immediate = (r: Fields) => ['DOLLARS', 'PERCENT'].includes(value(r.discount))
function issues(r: Draft): string[] {
  const catalogOnly=r.offerDetails?.qualificationStatus==='NEEDS_REVIEW'&&r.offerDetails?.sourceLayout==='WEB_CARDS'&&['WEBSITE_CLIP_CONTROL','WEBSITE_REMOVE_CONTROL'].includes(r.offerDetails?.statusEvidence||'')
  const errors: string[] = [], type = value(r.discount), details = r.offerDetails || {}
  if (!value(r.description)) errors.push('Enter the offer description.')
  if (!value(r.provider)) errors.push('Record the issuer / provider.')
  if (!validDate(r.expirationDate)&&!(catalogOnly&&!value(r.expirationDate)&&details.datePrecision==='MONTH_DAY_RANGE'&&details.validityText)) errors.push('Record a verified full expiration date.')
  if (r.status==='CLIPPED'&&!validDate(r.clippedOn)) errors.push('Record the clipped date.')
  if (value(r.startsOn) && (!validDate(r.startsOn) || value(r.startsOn) > value(r.expirationDate))) errors.push('Correct the validity dates.')
  if (!['MANUFACTURER', 'STORE', 'OTHER'].includes(value(r.couponType)) || value(r.couponType) === 'OTHER' && !catalogOnly && !(type === 'REWARD' && details.issuerType === 'SUGGESTED_FOR_YOU')) errors.push('Verify the issuer classification.')
  if (r.source !== `DIGITAL_${value(r.couponType)}` || value(r.storeRestriction).toLowerCase() !== 'walgreens') errors.push('Verify the Walgreens digital source and store.')
  if (!['CLIPPED','AVAILABLE'].includes(value(r.status))) errors.push('The document does not verify whether this offer is Available or Clipped.')
  if (!quantity(r.usageLimit)) errors.push('Record a valid usage limit.')
  if (!['DOLLARS', 'PERCENT', 'FREE_ITEM', 'BOGO', 'REBATE', 'REWARD'].includes(type)) errors.push('Verify the offer type.')
  if (immediate(r) && !positive(r.amount)) errors.push('Enter the evidenced immediate discount amount.')
  if (type === 'PERCENT' && positive(r.amount)) {
    const [whole, fraction = ''] = value(r.amount).split('.')
    if (BigInt(whole||'0') > 100n || BigInt(whole||'0') === 100n && /[1-9]/.test(fraction)) errors.push('Percentage cannot exceed 100.')
  }
  if (type !== 'REWARD' && !quantity(r.requiredQuantity)) errors.push('Record the qualifying quantity.')
  if (value(r.minimumPurchase) && !numeric(r.minimumPurchase)) errors.push('Correct the qualifying spend threshold.')
  if (type === 'REBATE' && !positive(details.rebateAmount)) errors.push('Record the rebate amount separately from checkout discounts.')
  if (type === 'REWARD') {
    if(value(r.requiredQuantity)&&!quantity(r.requiredQuantity,0))errors.push('Correct the qualifying quantity.')
    if (!positive(details.rewardAmount)) errors.push('Record the Walgreens Cash reward amount.')
    if (!quantity(r.requiredQuantity) && !positive(r.minimumPurchase)) errors.push('Record the qualifying spend or quantity for the reward.')
  }
  if (type === 'BOGO' && !quantity(details.freeQuantity)) errors.push('Record the free-item quantity.')
  if (type === 'BOGO' && value(details.maximumSavings) && !positive(details.maximumSavings)) errors.push('Correct the maximum savings amount.')
  if (!catalogOnly&&!value(r.conditions) && !value(details.qualifyingProducts) && !value(r.productOrBrand)) errors.push('Record the qualifying products or conditions.')
  if (r.target === 'UNDECIDED') errors.push('Decide whether this is an existing offer or a verified separate offer.')
  if (r.target === 'NEW' && r.reasons.some(s => s.includes('match') || s.includes('duplicate')) && !r.separateConfirmed) errors.push('Confirm that this is a separate offer before adding it.')
  return errors
}
function existingFields(c: Coupon): Fields {
  const t = c.terms
  return { couponId: c.couponId, provider: c.provider, description: c.description, amount: c.discountAmount, requiredQuantity: c.requiredQuantity, couponType: c.couponType, expirationDate: c.expirationDate, externalId: c.externalId, status:c.status,clippedOn:c.clippedOn,source: t?.source, discount: t?.discount, storeRestriction: t?.storeRestriction, accountRestriction: t?.accountRestriction, productOrBrand: t?.productOrBrand, qualifyingSkus: t?.qualifyingSkus || [], sizeOrVariety: t?.sizeOrVariety, minimumPurchase: t?.minimumPurchase, startsOn: t?.startsOn, usageLimit: t?.usageLimit, conditions: t?.conditions, originalOfferText: t?.originalOfferText, evidenceMediaType: t?.evidenceMediaType, evidenceBase64: t?.evidenceBase64, offerDetails: t?.offerDetails }
}
function matchingImportYear(c:Coupon,r:Fields) {
  if(r.offerDetails?.dateYearBasis!=='CURRENT_IMPORT_YEAR')return true
  return value(c.expirationDate||c.terms?.importedOn).slice(0,4)===value(r.expirationDate).slice(0,4)
}
function drafts(entries: Fields[], coupons: Coupon[]): Draft[] {
  return entries.map(r => {
    const key = r.offerDetails?.importKey
    const matching = coupons.filter(c => c.couponId === r.couponId || r.externalId && c.externalId === r.externalId || key && (c.terms?.offerDetails?.importKey === key||(c.terms?.offerDetails?.importKey === r.offerDetails?.previousImportKey&&matchingImportYear(c,r))) || !key && c.terms?.originalOfferText === r.originalOfferText)
    const verified = matching.filter(c => c.couponId === r.couponId || key && (c.terms?.offerDetails?.importKey === key||(c.terms?.offerDetails?.importKey === r.offerDetails?.previousImportKey&&matchingImportYear(c,r))))
    const exact = verified.length === 1 ? verified[0] : undefined
    const resolvedDates=exact&&!exact.expirationDate&&r.offerDetails?.dateYearBasis==='CURRENT_IMPORT_YEAR'?{expirationDate:r.expirationDate,startsOn:r.startsOn}:{}
    const reasons: string[] = []
    if (r.offerDetails?.reviewStatus !== 'PARSED') reasons.push('Source parsing or unsupported conditions need review.')
    if (entries.filter(other => other.originalOfferText && other.originalOfferText === r.originalOfferText || r.externalId && other.externalId === r.externalId).length > 1) reasons.push('Possible duplicate entries in this PDF.')
    if (matching.length && !exact) reasons.push('An existing / possible match needs a verified decision.')
    const row = { ...r, ...(exact ? {...existingFields(exact),status:exact.status==='AVAILABLE'&&r.status==='CLIPPED'?'CLIPPED':exact.status,clippedOn:exact.status==='AVAILABLE'&&r.status==='CLIPPED'?r.clippedOn:exact.clippedOn} : {}), ...resolvedDates, couponId: exact?.couponId || value(r.couponId), description: exact?.description || value(r.description), offerDetails: exact ? { ...r.offerDetails,...exact.terms?.offerDetails,...(r.offerDetails?.recordedClipLimit?{recordedClipLimit:r.offerDetails.recordedClipLimit}:{}), ...(Object.keys(resolvedDates).length?{datePrecision:r.offerDetails?.datePrecision||'',dateYearBasis:r.offerDetails?.dateYearBasis||'',dateNote:r.offerDetails?.dateNote||''}:{}), qualificationStatus:exact.terms?.offerDetails?.qualificationStatus==='VERIFIED'?'VERIFIED':r.offerDetails?.qualificationStatus||exact.terms?.offerDetails?.qualificationStatus||'',lastSeenInImport: value(r.importedOn) } : { ...r.offerDetails }, expectedRevision: exact?.revision || '0', target: exact?.couponId || (matching.length || reasons.some(s => s.includes('duplicate')) ? 'UNDECIDED' : 'NEW'), matches: matching.map(c => c.couponId), reasons, approved: false, skipped: false, protectedRecord: exact?.status === 'APPLIED' || exact?.status === 'NO_LONGER_AVAILABLE', separateConfirmed: false, sourceText: value(r.originalOfferText) } as Draft
    if (issues(row).length) row.reasons = [...reasons, 'Critical offer information needs correction.']
    return row
  })
}
const apiKeys = ['couponId', 'provider', 'description', 'amount', 'requiredQuantity', 'couponType', 'expirationDate', 'clippedOn', 'status', 'copies', 'source', 'discount', 'storeRestriction', 'accountRestriction', 'productOrBrand', 'qualifyingSkus', 'sizeOrVariety', 'minimumPurchase', 'startsOn', 'usageLimit', 'conditions', 'evidenceMediaType', 'evidenceBase64', 'externalId', 'originalOfferText', 'importedOn', 'importSource', 'offerDetails']
function entry(r: Draft): Fields {
  const payload: Fields = Object.fromEntries(apiKeys.filter(key => r[key] !== undefined).map(key => [key, r[key]]))
  return { ...payload, amount: immediate(r) ? r.amount : null, copies: 1, usageLimit: integer(value(r.usageLimit)), requiredQuantity: integer(value(r.requiredQuantity) || '0', 0), expectedRevision: integer(r.expectedRevision, 0), offerDetails: { ...r.offerDetails, ...(r.approved ? { reviewStatus: 'REVIEWED' } : {}) } }
}

export function CouponPdfImport({ open, onClose, api, coupons, onSaved }: { open: boolean; onClose: () => void; api: ApiClient; coupons: Coupon[]; onSaved: (keepOpen?:boolean) => void }) {
  const [importProvider,setImportProvider]=useState('WALGREENS'),[documentFormat,setDocumentFormat]=useState('AUTO')
  const supported=importProvider==='WALGREENS'
  const [rows, setRows] = useState<Draft[]>([]), [report, setReport] = useState<Report | null>(null), [source, setSource] = useState('')
  const [filter, setFilter] = useState('ALL'), [expanded, setExpanded] = useState<number[]>([]), [error, setError] = useState(''), [busy, setBusy] = useState(false)
  const [sourceError,setSourceError]=useState(false),[saving,setSaving]=useState(false)
  const previewGeneration=useRef(0)
  const [pending, setPending] = useState<{ body: unknown; key: string } | null>(null)
  function update(index: number, fields: Partial<Draft>) { setRows(old => old.map((r, i) => i === index ? { ...r, ...fields, approved: false,failed:false } : r)) }
  const conflicts = (r: Draft,list:Draft[]=rows) => list.some(other => other !== r && !other.skipped && !other.protectedRecord&&!other.processed && (other.couponId === r.couponId || !!r.externalId && other.externalId === r.externalId)) || !!r.externalId && coupons.some(c=>c.externalId===r.externalId&&c.couponId!==r.couponId)
  const ready = (r: Draft,list:Draft[]=rows) => !r.skipped && !r.protectedRecord&&!r.processed && !conflicts(r,list) && !issues(r).length && (!r.reasons.length || r.approved)
  const status = (r: Draft) => r.processed?'SAVED':r.failed?'FAILED':r.skipped||r.protectedRecord?'EXCLUDED':ready(r)?'READY':'REVIEW'
  const approved = rows.filter(r=>ready(r)), needsReview = rows.filter(r => status(r) === 'REVIEW'), excluded = rows.filter(r=>!ready(r)&&!r.processed).length
  const absent = coupons.filter(c => c.status === 'CLIPPED' && !rows.some(r => r.couponId === c.couponId || r.offerDetails?.importKey && r.offerDetails.importKey === c.terms?.offerDetails?.importKey || r.externalId && r.externalId === c.externalId || r.originalOfferText && r.originalOfferText === c.terms?.originalOfferText))
  async function read(file: File) {
    const generation=++previewGeneration.current
    setBusy(true); setError('');setSourceError(false)
    try {
      if(!supported)throw new Error('Only Walgreens PDF imports are supported. Paper, CVS, Target and Costco PDF importers are not available.')
      if (file.size > 8 * 1024 * 1024) throw new Error('Choose a PDF no larger than 8 MB.')
      let binary = ''; for (const byte of new Uint8Array(await file.arrayBuffer())) binary += String.fromCharCode(byte)
      const result = await api.mutate('/clipped-coupons/walgreens/preview', 'POST', { source: file.name, contentBase64: btoa(binary), documentFormat }, crypto.randomUUID()) as Report
      if(generation!==previewGeneration.current)return
      const next=drafts(result.entries,coupons);setRows(next); setReport(result); setSource(file.name); setExpanded([]); setFilter('ALL')
    } catch (ex) { if(generation===previewGeneration.current){setError((ex as Error).message);setSourceError(true)} } finally { if(generation===previewGeneration.current)setBusy(false) }
  }
  async function commit(batch:Draft[],keepReview:boolean) {
    setBusy(true);setSaving(true);setError('')
    try {
      const request=pending||{key:crypto.randomUUID(),body:{entries:batch.map(entry),...(keepReview?{summary:true}:{})}};setPending(request)
      const response=await api.mutate('/clipped-coupons/walgreens/import','POST',request.body,request.key) as ImportResult;
      setPending(null)
      if(keepReview){
        const outcomes=new Map(response.outcomes.map(o=>[o.couponId,o.outcome]));
        setRows(old=>old.map(r=>outcomes.has(r.couponId)?{...r,processed:outcomes.get(r.couponId),failed:false}:r));onSaved(true)
      }else{setRows([]);setReport(null);onSaved()}
    }catch(ex){
      setError((ex as Error).message)
      if(ex instanceof ApiError&&[400,403,409,412].includes(ex.status)){setPending(null);const ids=new Set(batch.map(r=>r.couponId));setRows(old=>old.map(r=>ids.has(r.couponId)?{...r,failed:true}:r))}
    }finally{setBusy(false);setSaving(false)}
  }
  async function save() {
    if(!supported||sourceError||!approved.length&&!pending)return
    await commit(approved,report?.documentKind==='AVAILABLE')
  }
  function target(index: number, selected: string) {
    const c = coupons.find(c => c.couponId === selected), row = rows[index]
    update(index, { ...(c ? {...existingFields(c),status:c.status==='AVAILABLE'&&row.status==='CLIPPED'?'CLIPPED':c.status,clippedOn:c.status==='AVAILABLE'&&row.status==='CLIPPED'?row.clippedOn:c.clippedOn} : {}), couponId: c?.couponId || (row.target === 'NEW' ? row.couponId : crypto.randomUUID()), target: selected, expectedRevision: c?.revision || '0', protectedRecord: c?.status === 'APPLIED' || c?.status === 'NO_LONGER_AVAILABLE', separateConfirmed: selected === 'NEW', offerDetails: c ? { ...c.terms?.offerDetails } : row.offerDetails })
  }
  function cancel() {
    if(saving)return
    ++previewGeneration.current
    if(!pending){setRows([]);setReport(null);setError('');setSourceError(false);setExpanded([]);setFilter('ALL');setBusy(false)}
    onClose()
  }
  if (!open) return null
  return <Modal title="Import Coupons" onClose={()=>{if(!saving)onClose()}}><div className="coupon-import-actions"><button disabled={saving} onClick={cancel}>{pending||rows.some(r=>r.processed)?'Close import review':'Cancel import'}</button></div><div className="coupon-import-source-controls"><label>Import source<select value={importProvider} disabled={busy||!!pending} onChange={e=>setImportProvider(e.target.value)}><option value="WALGREENS">Walgreens — supported</option>{['Paper','CVS','Target','Costco'].map(name=><option key={name} value={name}>{name} — PDF import not supported</option>)}</select></label><label>PDF format<select value={documentFormat} disabled={!supported||busy||!!pending} onChange={e=>setDocumentFormat(e.target.value)}><option value="AUTO">Automatic Detection</option><option value="AVAILABLE">Available Coupons</option><option value="CLIPPED">Clipped Coupons</option><option value="WEB_CARDS">Website offer export</option></select></label></div>{!supported&&<p role="status">{importProvider} PDF import is not supported. Choose Walgreens for supported PDFs, or use Add Coupon for manual entry.</p>}<div className="coupon-import-toolbar"><label>Walgreens text PDF<input type="file" accept="application/pdf" disabled={!supported||busy || !!pending} onChange={e => { if (e.target.files?.[0]) void read(e.target.files[0]) }}/></label><SectionHelp title="Walgreens PDF import" label="About Walgreens import review"><p>Only Walgreens clipped-list printouts and Walgreens website offer exports are supported. Status comes from the document: a clipped-list heading or Remove control confirms Clipped; a Clip control confirms Available. Missing controls remain unresolved. Existing Redeemed and Removed history is preserved; an Available listing does not unclip an already saved Clipped coupon. The current tab never determines status. Paper, CVS, Target and Costco PDF formats are not supported. This is a manual weekly refresh, not retailer synchronization. Upload previews offers without saving them. Import all ready offers with one button, or cancel without saving. New Available offers remain separate from clipped offers when their verified offer details or expiration dates differ. Importing does not use a clip slot in your Walgreens account. Month/day dates use the current import year; incomplete website terms stay unknown for shopping eligibility. Other valid parsed offers can be imported together; flagged entries need an explicit review decision. Missing offers are never marked redeemed. Cancel discards the preview. A submitted request cannot be undone by closing the review.</p>{report && <><p>{source} · Detected format: {report.layout||'not recorded'} · Reported: {report.declaredCount ?? 'not recorded'} · OCR pages: {report.ocrPages || 0} · Missing full expirations: {report.missingExpiration || 0}</p><p>{Object.entries(report.offerTypes || {}).map(([type, count]) => `${label(type)}: ${count}`).join(' · ')}</p>{report.warnings?.map((w, i) => <p key={i}>{w}</p>)}{!!absent.length && <p>{absent.length} clipped coupon(s) absent or unverified in this PDF. Their saved states remain unchanged.</p>}</>}</SectionHelp></div>
    {error && <p role="alert">{error}</p>}{sourceError&&report&&<button onClick={()=>{setSourceError(false);setError('')}}>Resume previous review</button>}{busy && !report && <p role="status">Reading PDF…</p>}
    {report&&supported && <>{!rows.some(r=>r.processed)&&<p>{rows.length} offers found · {approved.length} Ready to Import. Nothing is saved until you select Import.</p>}{report.documentKind==='AVAILABLE'?pending&&error?<p role="status">Import outcome unconfirmed. Retry the same request to obtain confirmed counts.</p>:<div className="coupon-import-summary" aria-label="Import processing results"><strong>{rows.filter(r=>r.processed==='IMPORTED').length} Imported</strong><strong>{rows.filter(r=>r.processed==='UPDATED').length} Updated</strong><strong>{rows.filter(r=>r.processed==='ALREADY_SAVED'||r.protectedRecord).length} Already Saved</strong><strong>{needsReview.length} Needs Review</strong><strong>{rows.filter(r=>r.failed).length} Failed</strong></div>:<div className="coupon-import-summary" aria-label="Import summary"><strong>{rows.length} Extracted</strong><strong>{approved.length} Ready to Import</strong><strong>{needsReview.length} Needs Review</strong></div>}<nav className="coupon-tabs" aria-label="Import review filters">{[['ALL', 'All'], ['REVIEW', 'Needs Review'], ['READY', 'Ready'], ['SAVED','Saved'],['FAILED','Failed'],['EXCLUDED', 'Excluded']].map(([key, name]) => <button key={key} aria-pressed={filter === key} onClick={() => setFilter(key)}>{name}{key === 'REVIEW' ? ` (${needsReview.length})` : ''}</button>)}</nav>
      <fieldset disabled={busy || !!pending} className="coupon-import-list">{rows.map((r, index) => {
        const current = status(r), show = expanded.includes(index), id = `import-details-${index}`, errors = issues(r)
        if (filter !== 'ALL' && current !== filter) return null
        const input = (key: string, title: string, type = 'text') => <label key={key}>{title}<input type={type} value={value(r[key])} onChange={e => update(index, { [key]: e.target.value || null })}/></label>
        const detail = (key: string, title: string) => <label key={key}>{title}<input value={r.offerDetails?.[key] || ''} onChange={e => update(index, { offerDetails: { ...r.offerDetails, [key]: e.target.value } })}/></label>
        return <article className="coupon-import-entry" key={index}><div className="coupon-import-row-summary"><strong>{r.description || 'Unrecognized offer'}</strong><span>{r.expirationDate?date(value(r.expirationDate)):r.offerDetails?.validityText||'Date not recorded'}{!r.expirationDate&&r.offerDetails?.datePrecision==='MONTH_DAY_RANGE'&&<small>Year not printed</small>}<small className="coupon-import-account-state">{r.status==='CLIPPED'?'Clipped':r.status==='AVAILABLE'?(r.target==='NEW'?'New · Available to clip':'Available'):'Account status unknown'}</small></span><span className={`coupon-import-status ${current.toLowerCase()}`}>{r.processed?label(r.processed):r.failed?'Failed':r.protectedRecord ? 'History preserved' : current === 'READY' ? 'Ready' : current === 'REVIEW' ? 'Needs Review' : 'Excluded'}</span><button aria-label={`Details: ${r.description || 'entry ' + (index + 1)}`} aria-expanded={show} aria-controls={id} onClick={() => setExpanded(show ? expanded.filter(i => i !== index) : [...expanded, index])}>Details</button></div>
          {show && <div id={id} className="coupon-import-details"><div className="coupon-review-reasons">{r.processed?<p>{label(r.processed)}. Its saved identity and history are preserved.</p>:r.protectedRecord ? <p>Redeemed or removed record preserved; no import change.</p> : <><ul>{[...new Set([...(r.approved?[]:r.reasons), ...errors, ...(conflicts(r) ? ['Two entries target the same saved coupon. Exclude one or verify separate identities.'] : [])])].map(reason => <li key={reason}>{reason}</li>)}</ul></>}</div>
            {!r.protectedRecord&&!r.processed && <><div className="coupon-import-fields">{input('description', 'Description')}{input('expirationDate', 'Expiration date', 'date')}
              <label>Offer type<select value={value(r.discount)} onChange={e => update(index, { discount: e.target.value, offerDetails: { ...r.offerDetails, offerType: e.target.value } })}>{['DOLLARS', 'PERCENT', 'FREE_ITEM', 'BOGO', 'REBATE', 'REWARD', 'UNKNOWN'].map(t => <option key={t} value={t}>{label(t)}</option>)}</select></label>
              {immediate(r) && input('amount', r.discount === 'PERCENT' ? 'Percent off' : 'Discount amount')}
              {(r.discount !== 'REWARD' || value(r.requiredQuantity) || !value(r.minimumPurchase)) && input('requiredQuantity', r.discount === 'BOGO' ? 'Purchase quantity' : 'Required quantity')}
              {(r.discount === 'REWARD' || value(r.minimumPurchase)) && input('minimumPurchase', 'Minimum spend')}
              {r.discount === 'BOGO' && <>{detail('freeQuantity', 'Free quantity')}{detail('maximumSavings', 'Maximum savings')}</>}
              {r.discount === 'REBATE' && detail('rebateAmount', 'Rebate amount')}{r.discount === 'REWARD' && detail('rewardAmount', 'Walgreens Cash reward amount')}
              {input('productOrBrand', 'Qualifying products')}{input('conditions', 'Restrictions / terms')}
              <label>Classification<select value={value(r.couponType)} onChange={e => update(index, { couponType: e.target.value, source: `DIGITAL_${e.target.value}` })}><option value="">Not recorded</option><option value="MANUFACTURER">Manufacturer</option><option value="STORE">Store</option><option value="OTHER">Other / not recorded</option></select></label>
              {r.offerDetails?.channelRestriction && detail('channelRestriction', 'Channel restriction')}
              {!!(r.matches.length || r.reasons.some(s => s.includes('duplicate'))) && <label>Verified destination<select value={r.target} onChange={e => target(index, e.target.value)}><option value="UNDECIDED">Choose after verifying the source</option><option value="NEW">New separate offer — verified</option>{coupons.filter(c => r.matches.includes(c.couponId) || c.couponId === r.target).map(c => <option key={c.couponId} value={c.couponId}>{c.description} · {c.status === 'APPLIED' ? 'Redeemed — preserved' : label(c.status)}</option>)}</select></label>}
            </div><details className="coupon-import-advanced"><DisclosureSummary>Advanced fields & original PDF text</DisclosureSummary><div className="coupon-import-fields">{input('provider', 'Issuer / provider')}{r.status==='CLIPPED'&&input('clippedOn', 'Clipped date', 'date')}{input('startsOn', 'Valid from', 'date')}{input('usageLimit', 'Recorded maximum uses per transaction')}{input('sizeOrVariety', 'Size / variety')}{input('accountRestriction', 'Account restriction')}{input('storeRestriction', 'Store restriction')}{detail('category', 'Category')}{detail('exclusions', 'Exclusions')}{detail('usageRestriction', 'Usage restriction')}</div><p>Source page: {r.offerDetails?.sourcePage || 'Not recorded'}</p><pre className="coupon-original">{r.sourceText}</pre></details>
              <div className="coupon-review-actions">{!r.skipped && <button disabled={!!errors.length || conflicts(r)} onClick={() => setRows(old => old.map((row, i) => i === index ? { ...row, approved: true } : row))}>{r.approved ? 'Approved' : 'Approve corrections'}</button>}<button onClick={() => update(index, { skipped: !r.skipped })}>{r.skipped ? 'Return to review' : 'Exclude entry'}</button></div></>}
          </div>}
        </article>
      })}</fieldset>
      <div className="coupon-import-footer"><span>{excluded} {excluded === 1 ? 'entry' : 'entries'} excluded (unresolved, skipped or preserved).</span><button className="primary" aria-label={pending ? 'Retry same import' : 'Import approved offers'} disabled={!supported||sourceError||busy || !pending && !approved.length} onClick={() => void save()}>{busy ? 'Importing…' : pending ? 'Retry same import' : `Import ${approved.length} ready offers`}</button>{report.documentKind==='AVAILABLE'&&rows.some(r=>r.processed)&&<button disabled={saving} onClick={onClose}>Done</button>}</div>
      {pending && !busy && <Notice>Retry sends the exact same request. Close and refresh to reconcile before starting a different import.</Notice>}
    </>}
  </Modal>
}
