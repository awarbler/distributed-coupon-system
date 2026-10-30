import { FormActions } from '../../components/ui'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { ApiClient, ApiError } from '../../api/client'
import type { Earning } from '../../types/platform'
import { Notice } from '../../components/ui'
import { rewardChoices, rewardStatusLabel } from './rewardStatus'

export function EarningStatusForm({ earning, receiptId, revision, api, enabled, onSaved, onClose }: { earning: Earning; receiptId: string; revision: string; api: ApiClient; enabled: boolean; onSaved: () => void; onClose: () => void }) {
  const [status, setStatus] = useState(earning.status)
  const [amount,setAmount]=useState((earning.confirmed||earning.expected)?.amount??'')
  const [correcting,setCorrecting]=useState(false)
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false), [error, setError] = useState('')
  const [pending, setPending] = useState<{ key: string; body: unknown; path?:string } | null>(null)
  const benefit = earning.confirmed || earning.expected
  const originalUnit = benefit?.kind === 'POINTS' ? 'points' : benefit?.currency || ''
  const receiving = status === 'CREDITED' || status === 'PAID'
  const choices = rewardChoices(earning)
  async function remove(){if(!enabled||!window.confirm('Delete this reward from current totals? Its history will be retained.'))return;const request={key:crypto.randomUUID(),body:{},path:'delete'};setPending(request);setBusy(true);setError('');try{await api.mutate(`/receipts/${receiptId}/earnings/${earning.earningId}/delete`,'PUT',request.body,request.key,revision);onSaved()}catch(e){setError((e as Error).message);if(e instanceof ApiError&&e.status>=400&&e.status<500&&e.status!==409)setPending(null)}finally{setBusy(false)}}
  async function save(event: FormEvent) {
    event.preventDefault(); if (!enabled) return
    setError(''); setBusy(true)
    const request: {key:string;body:unknown;path?:string} = pending || { key: crypto.randomUUID(), body: correcting?{amount,unit:originalUnit}:{ status, ...(receiving ? { amount: benefit?.amount, unit: originalUnit, confirmAmountAndUnit: true } : {}), ...(status === 'REJECTED' && reason.trim() ? { rejectionReason: reason.trim() } : {}) } }
    setPending(request)
    try { await api.mutate(`/receipts/${receiptId}/earnings/${earning.earningId}/${request.path??(correcting?'amount':'status')}`, 'PUT', request.body, request.key, revision); onSaved() }
    catch (e) { setError((e as Error).message); if (e instanceof ApiError && e.status >= 400 && e.status < 500 && e.status !== 409) setPending(null) }
    finally { setBusy(false) }
  }
  return <form className="earning-status-form" aria-label={`Update reward status for ${earning.provider}`} onSubmit={save}>
    <h3>Update reward status</h3>
    <p className="muted">{earning.kind === 'REGISTER_REWARD' ? 'Printing receives an RR coupon. Redeeming it later is a separate event; no Walgreens Cash is deposited.' : 'Record a provider account credit. External cash payouts are not handled here.'}</p>
    {earning.provider?.trim().toLowerCase()==='ibotta' && <p>Ibotta account credit is earned money held in Ibotta. Your withdrawal minimum is $20.00; a later withdrawal is a separate transfer.</p>}
    {error && <div className="error" role="alert">{error}</div>}
    {pending && !busy && <Notice>The outcome may be unresolved. Retry retains the same request and revision. Close and refresh to review before a different change.</Notice>}
    <fieldset disabled={!enabled || busy || !!pending}>
      <button type="button" className="secondary" onClick={()=>setCorrecting(!correcting)}>{correcting?'Edit Status Instead':'Edit Reward Amount'}</button>
      {correcting?<label>Reward amount ({originalUnit})<input aria-label="Reward amount" required inputMode="decimal" pattern="([0-9]+([.][0-9]+)?|[.][0-9]+)" value={amount} onChange={e=>setAmount(e.target.value)}/></label>:<label>Reward status<select aria-label="Reward status" value={status} onChange={e => setStatus(e.target.value)}>
        {!choices.some(c => c.value === earning.status) && <option value={earning.status} disabled>{rewardStatusLabel(earning)} — retained</option>}
        {choices.map(c => <option key={c.value} value={c.value}>{earning.provider?.trim().toLowerCase()==='ibotta' && c.value==='CREDITED' ? 'Added to Ibotta account' : c.text}</option>)}
      </select></label>}
      {receiving && <p>Received: {benefit?.amount} {originalUnit}. Saving uses this recorded amount; no re-entry is needed.</p>}
      {status === 'REJECTED' && <><label>Rejection reason (optional)<textarea maxLength={1000} value={reason} onChange={e => setReason(e.target.value)} /></label><p className="muted">Expected and previously recorded amounts remain in the record. Rejected rewards do not increase balances or savings.</p></>}
    </fieldset>
    <FormActions><button type="button" className="secondary" disabled={!enabled||busy||!!pending} onClick={()=>void remove()}>Delete Reward</button><button type="button" className="secondary" disabled={busy} onClick={onClose}>{pending ? 'Close and refresh' : 'Cancel'}</button><button className="primary" disabled={!enabled || busy || (!pending && (correcting?amount===(earning.confirmed||earning.expected)?.amount:status === earning.status))}>{busy ? 'Saving…' : pending ? 'Retry same request' : correcting?'Save Reward Amount':'Save reward status'}</button></FormActions>
  </form>
}


export function RewardAmountEditor({api,writable,receiptId,earningId,onSaved}:{api?:ApiClient;writable:boolean;receiptId:string;earningId:string;onSaved:()=>void}) {
 const [record,setRecord]=useState<{earning:Earning;revision:string}|null>(null),[error,setError]=useState(''),[busy,setBusy]=useState(false)
 async function open(){if(!api)return;setBusy(true);setError('');try{const r=await api.receipt(receiptId);const earning=r.contents.observations.earnings.find(e=>e.earningId===earningId);if(!earning)throw Error('Reward was not found. Refresh and try again.');setRecord({earning,revision:r.revision})}catch(e){setError((e as Error).message)}finally{setBusy(false)}}
 return <div>{error&&<div role="alert" className="error">{error}</div>}{!record?<button type="button" className="secondary" disabled={!api||!writable||busy} onClick={()=>void open()}>Edit / Delete Reward</button>:<EarningStatusForm earning={record.earning} receiptId={receiptId} revision={record.revision} api={api!} enabled={writable} onClose={()=>setRecord(null)} onSaved={()=>{setRecord(null);onSaved()}}/>}</div>
}


export function StandaloneAmountEditor({api,writable,path,original,onSaved}:{api?:ApiClient;writable:boolean;path:string;original:string;onSaved:()=>void}) {
 const [expected,setExpected]=useState(original),[expectedPoints,setExpectedPoints]=useState<string|null>(null),[points,setPoints]=useState(''),[provider,setProvider]=useState('')
 const [open,setOpen]=useState(false),[amount,setAmount]=useState(original),[busy,setBusy]=useState(false),[error,setError]=useState(''),[pending,setPending]=useState<{body:unknown;key:string;method:'PUT'|'DELETE'}|null>(null)
 async function send(deleting=false){if(!api)return;if(deleting&&!pending&&!window.confirm('Delete this reward? Its prior entry stays in history and its contribution is reversed.'))return;setBusy(true);setError('');const request=pending??{method:deleting?'DELETE' as const:'PUT' as const,key:crypto.randomUUID(),body:path.startsWith('/accounts/')?{amount,expectedAmount:expected,points:expectedPoints===null?null:points,expectedPoints,correctionId:crypto.randomUUID()}:deleting?{}:{amount,expectedAmount:expected}};setPending(request);try{await api.mutate(path,request.method,request.body,request.key);setPending(null);setOpen(false);onSaved()}catch(e){setError((e as Error).message);if(e instanceof ApiError&&e.status>=400&&e.status<500)setPending(null)}finally{setBusy(false)}}
 return <div>{error&&<div role="alert" className="error">{error}</div>}{!open?<button className="secondary" type="button" disabled={!api||!writable} onClick={()=>{if(path.startsWith('/accounts/')&&api){void api.memberReward(path.split('/').at(-1)!).then(r=>{setExpected(r.amount);setAmount(r.amount);setExpectedPoints(r.points);setPoints(r.points??'');setProvider(r.provider);setOpen(true)}).catch(e=>setError((e as Error).message))}else setOpen(true)}}>Edit / Delete Reward</button>:<form onSubmit={e=>{e.preventDefault();void send()}}>{expectedPoints!==null&&<label>Reward points<input required disabled={busy||!!pending} inputMode="decimal" value={points} onChange={e=>setPoints(e.target.value)}/></label>}{!['Fetch','P&G Brand'].includes(provider)&&<label>Reward amount<input required disabled={busy||!!pending} inputMode="decimal" pattern="([0-9]+([.][0-9]+)?|[.][0-9]+)" value={amount} onChange={e=>setAmount(e.target.value)}/></label>}<FormActions><button type="button" className="secondary" disabled={busy} onClick={()=>setOpen(false)}>Cancel</button><button type="button" className="secondary" disabled={busy||!writable||!!pending} onClick={()=>void send(true)}>Delete Reward</button><button className="primary" disabled={busy||!writable}>{pending?'Retry Same Request':'Save Reward Amount'}</button></FormActions></form>}</div>
}


export function RebateFollowUp({api,writable,receiptId,earningId,onSaved}:{api?:ApiClient;writable:boolean;receiptId:string;earningId:string;onSaved:()=>void}) {
 const [revision,setRevision]=useState<string|null>(null),[status,setStatus]=useState('NOT_SUBMITTED'),[submitted,setSubmitted]=useState(''),[reference,setReference]=useState(''),[due,setDue]=useState(''),[note,setNote]=useState(''),[error,setError]=useState(''),[busy,setBusy]=useState(false)
 const [pending,setPending]=useState<{key:string;body:unknown}|null>(null)
 async function open(){if(!api)return;setBusy(true);setError('');try{const r=await api.receipt(receiptId),d=r.contents.observations.earnings.find(e=>e.earningId===earningId)?.details;setRevision(r.revision);setStatus(d?.submissionStatus??(d?.submittedOn?'SUBMITTED':'NOT_SUBMITTED'));setSubmitted(d?.submittedOn??'');setReference(d?.submissionReference??'');setDue(d?.followUpOn??'');setNote(d?.followUpNote??'')}catch(e){setError((e as Error).message)}finally{setBusy(false)}}
 async function save(e:FormEvent){e.preventDefault();if(!api||!revision)return;setBusy(true);setError('');const request=pending??{key:crypto.randomUUID(),body:{submissionStatus:status,submittedOn:submitted||null,submissionReference:reference||null,followUpOn:due||null,followUpNote:note||null}};setPending(request);try{await api.mutate(`/receipts/${receiptId}/earnings/${earningId}/follow-up`,'PUT',request.body,request.key,revision);setPending(null);setRevision(null);onSaved()}catch(e){setError((e as Error).message);if(e instanceof ApiError&&e.status>=400&&e.status<500&&e.status!==409)setPending(null)}finally{setBusy(false)}}
 return <div>{error&&<div className="error" role="alert">{error}</div>}{!revision?<button type="button" className="secondary" disabled={!api||busy} onClick={()=>void open()}>Check Submission / Follow-Up</button>:<form onSubmit={save}><fieldset disabled={!writable||busy||!!pending}><label>Submission Status<select value={status} onChange={e=>setStatus(e.target.value)}>{['NOT_SUBMITTED','SUBMITTED','FOLLOW_UP','COMPLETE','NOT_REQUIRED'].map(v=><option key={v} value={v}>{v.replaceAll('_',' ')}</option>)}</select></label><label>Submitted On (Optional)<input type="date" value={submitted} onChange={e=>setSubmitted(e.target.value)}/></label><label>Submission Reference (Optional)<input value={reference} onChange={e=>setReference(e.target.value)}/></label><label>Follow-Up Date (Optional)<input type="date" value={due} onChange={e=>setDue(e.target.value)}/></label><label>Follow-Up Note (Optional)<textarea maxLength={2000} value={note} onChange={e=>setNote(e.target.value)}/></label></fieldset><FormActions><button type="button" className="secondary" disabled={busy} onClick={()=>{setRevision(null);setPending(null)}}>Close</button><button className="primary" disabled={!writable||busy}>{busy?'Saving…':pending?'Retry Same Request':'Save Follow-Up'}</button></FormActions></form>}</div>
}

export function RewardCreditSourceForm({api,receiptId,earning,revision,writable,onSaved}:{api:ApiClient;receiptId:string;earning:Earning;revision:string;writable:boolean;onSaved:()=>void}){
 const [data,setData]=useState<import('../../types/platform').Workspace|null>(null),[search,setSearch]=useState(''),[source,setSource]=useState(earning.details?.originalReceiptId??''),[reward,setReward]=useState(earning.details?.originalEarningId??''),[error,setError]=useState(''),[busy,setBusy]=useState(false),[pending,setPending]=useState<{key:string;body:unknown}|null>(null)
 async function open(){setBusy(true);setError('');try{setData(await api.workspace())}catch(e){setError((e as Error).message)}finally{setBusy(false)}}
 async function save(e:FormEvent){e.preventDefault();setBusy(true);setError('');const request=pending??{key:crypto.randomUUID(),body:{originalReceiptId:source||null,originalEarningId:reward||null}};setPending(request);try{await api.mutate(`/receipts/${receiptId}/earnings/${earning.earningId}/source`,'PUT',request.body,request.key,revision);onSaved()}catch(e){setError((e as Error).message);if(e instanceof ApiError&&e.status>=400&&e.status<500&&e.status!==409)setPending(null)}finally{setBusy(false)}}
 return <div>{error&&<p className="error" role="alert">{error}</p>}{!data?<button type="button" className="secondary" disabled={!writable||busy} onClick={()=>void open()}>Link / Change Original Reward</button>:<form onSubmit={save}><p>Connect This Received Credit To Its Original Unpaid Or Rejected Offer. Linking Adds No Money And Keeps Fetch Unchanged.</p><fieldset disabled={busy||!!pending||!writable}><label>Search Original Receipt<input value={search} onChange={e=>setSearch(e.target.value)}/></label><label>Original Receipt<select value={source} onChange={e=>{setSource(e.target.value);setReward('')}}><option value="">Not Linked</option>{data.receipts.filter(r=>r.transactionId!==receiptId&&r.status==='ACTIVE'&&`${r.metadata?.receiptNumber??''} ${r.metadata?.retailer??''}`.replace(/[^a-z0-9]/gi,'').toLowerCase().includes(search.replace(/[^a-z0-9]/gi,'').toLowerCase())).map(r=><option value={r.transactionId} key={r.transactionId}>{r.metadata?.receiptNumber??r.transactionId}</option>)}</select></label>{source&&<label>Original Reward<select required value={reward} onChange={e=>setReward(e.target.value)}><option value="">Choose The Missing Reward</option>{data.receiptEarnings?.filter(r=>r.receiptId===source&&r.earning.kind==='WALGREENS_CASH'&&!['CREDITED','PAID'].includes(r.earning.status)).map(r=><option key={r.earning.earningId} value={r.earning.earningId}>{r.earning.offerReference} · {r.earning.expected?.amount} USD · {r.earning.status}</option>)}</select></label>}</fieldset><FormActions><button type="button" className="secondary" disabled={busy} onClick={()=>{setData(null);setPending(null)}}>Cancel Link</button><button className="primary" disabled={!writable||busy}>{pending?'Retry Same Link':'Save Credit Link'}</button></FormActions></form>}</div>
}
