import { useEffect, useRef, useState } from 'react'
import type { ApiClient } from '../../api/client'
import type { RewardInstrument, Workspace, Receipt } from '../../types/platform'
import { Panel, Notice } from '../../components/ui'
import { date, money } from '../../api/format'

type Props={api?:ApiClient;writable:boolean;workspace?:Workspace}
/** Individually identified instruments. Legacy provider totals are never assigned by guesswork. */
export function RewardInstruments({api,writable,workspace}:Props){
 const [rows,setRows]=useState<RewardInstrument[]>([]),[selected,setSelected]=useState(''),[error,setError]=useState(''),[busy,setBusy]=useState(false),[needsRefresh,setNeedsRefresh]=useState(false)
 const [kind,setKind]=useState('ACCOUNT_REWARD'),[provider,setProvider]=useState(''),[nickname,setNickname]=useState(''),[last,setLast]=useState(''),[unit,setUnit]=useState('USD'),[opening,setOpening]=useState(''),[complete,setComplete]=useState(false),[openingDate,setOpeningDate]=useState(''),[reason,setReason]=useState(''),[replacementId,setReplacementId]=useState(''),[evidenceReference,setEvidenceReference]=useState('')
 const [action,setAction]=useState('CREDIT'),[amount,setAmount]=useState(''),[eventDate,setEventDate]=useState(''),[note,setNote]=useState(''),[correction,setCorrection]=useState(''),[sourceAccount,setSourceAccount]=useState(''),[confirmed,setConfirmed]=useState(false)
 const [cardProvider,setCardProvider]=useState(''),[cardValue,setCardValue]=useState(''),[cardName,setCardName]=useState(''),[sourceAmount,setSourceAmount]=useState('')
 const [editProvider,setEditProvider]=useState(''),[editOpening,setEditOpening]=useState(''),[editDate,setEditDate]=useState(''),[editComplete,setEditComplete]=useState(false),[changeOpening,setChangeOpening]=useState(false),[editName,setEditName]=useState(''),[editLast,setEditLast]=useState(''),[editReason,setEditReason]=useState(''),[editConfirmed,setEditConfirmed]=useState(false)
 const [receiptId,setReceiptId]=useState(''),[receiptEvent,setReceiptEvent]=useState(''),[receipt,setReceipt]=useState<Receipt|null>(null)
 const [hasPending,setHasPending]=useState(false)
 const pending=useRef<{key:string;path:string;body:Record<string,unknown>;revision?:string;method:'POST'|'PUT'}|null>(null)
 async function load(){if(api){const result=await api.rewardInstruments();setRows(result.instruments)}}
 useEffect(()=>{if(!api)return;const controller=new AbortController();api.rewardInstruments(controller.signal).then(r=>setRows(r.instruments)).catch(e=>{if(!controller.signal.aborted)setError((e as Error).message)});return()=>controller.abort()},[api])
 useEffect(()=>{if(!api||!receiptId)return;const controller=new AbortController();api.receipt(receiptId,controller.signal).then(setReceipt).catch(e=>{if(!controller.signal.aborted)setError((e as Error).message)});return()=>controller.abort()},[api,receiptId])
 const sourceReceipt=receipt?.transactionId===receiptId?receipt:null
 const current=rows.find(r=>r.id===selected),original=rows.find(r=>r.id===(sourceAccount||selected))
 const value=(amount:string|null,unit:string)=>amount==null?'Unknown':unit==='POINTS'?`${amount} points`:money(amount)
 async function send(path:string,body:Record<string,unknown>,revision?:string,method:'POST'|'PUT'='POST'){
  if(!api||!writable||needsRefresh)return
  const request=pending.current??{key:crypto.randomUUID(),path,body,revision,method};pending.current=request;setHasPending(true);setBusy(true);setError('')
  try{await api.mutate(request.path,request.method,request.body,request.key,request.revision);pending.current=null;setHasPending(false);setNeedsRefresh(true);await load();setNeedsRefresh(false);setConfirmed(false);setEditConfirmed(false);setCorrection('');setNote('');setAmount('');setReceiptEvent('');setReceiptId('')}
  catch(e){setError((e as Error).message);const status=(e as {status?:number}).status;if(status&&status<500&&!(status===409&&/uncertain/i.test((e as Error).message))){pending.current=null;setHasPending(false);await load().catch(()=>{})}}
  finally{setBusy(false)}
 }
 if(!api)return null
 const disabled=busy||!writable||hasPending||needsRefresh
 return <Panel className="reward-instruments-panel" title="Individual Accounts & Gift Cards" subtitle="Track each account and each issued card separately. Existing provider totals are not automatically assigned to these records." action={<button type="button" className="secondary" disabled={busy} onClick={()=>void load().then(()=>setError('')).catch(e=>setError((e as Error).message))}>Reload instrument balances</button>}>
  <div className="reward-instruments-content">
  {error&&<Notice>{error}</Notice>}{needsRefresh&&<button type="button" onClick={()=>void load().then(()=>setNeedsRefresh(false)).catch(e=>setError((e as Error).message))}>Reload saved instrument records</button>}
  {hasPending&&!busy&&<button type="button" onClick={()=>void send('',{})} disabled={!writable}>Retry the same instrument request</button>}
  <details><summary>Add an account or individual gift card</summary><form onSubmit={e=>{e.preventDefault();void send('/reward-instruments',{id:crypto.randomUUID(),kind,provider,nickname:nickname||null,lastFour:kind==='GIFT_CARD'?last||null:null,unit:kind==='GIFT_CARD'?'USD':unit,opening:opening||null,date:openingDate||null,historyComplete:complete,reason,...(kind==='GIFT_CARD'&&replacementId?{replacementId,evidenceReference}:{})})}}>
   <fieldset disabled={disabled}><div className="form-grid">
    <label>Instrument type<select value={kind} onChange={e=>setKind(e.target.value)}><option value="ACCOUNT_REWARD">Account-linked reward</option><option value="GIFT_CARD">Individual gift card</option></select></label>
    <label>Retailer or reward program<input required value={provider} onChange={e=>setProvider(e.target.value)} maxLength={200}/></label>
    <label>Account or card nickname<input value={nickname} onChange={e=>setNickname(e.target.value)} maxLength={200}/></label>
    {kind==='GIFT_CARD'?<label>Card last four digits (optional)<input inputMode="numeric" pattern="[0-9]{4}" maxLength={4} value={last} onChange={e=>setLast(e.target.value)}/></label>:<label>Native balance unit<select value={unit} onChange={e=>setUnit(e.target.value)}><option value="USD">USD</option><option value="POINTS">Points</option></select></label>}
    <label>Observed opening value (blank if unknown)<input inputMode="decimal" value={opening} onChange={e=>setOpening(e.target.value)}/></label>
    <label>Opening observation date (optional)<input type="date" value={openingDate} onChange={e=>setOpeningDate(e.target.value)}/></label>
    <label><input type="checkbox" checked={complete} onChange={e=>setComplete(e.target.checked)}/>Opening and subsequent history are sufficiently recorded</label>
    {kind==='GIFT_CARD'&&<><label>Replacement of an existing card (optional)<select value={replacementId} onChange={e=>setReplacementId(e.target.value)}><option value="">No recorded replacement</option>{rows.filter(r=>r.kind==='GIFT_CARD').map(r=><option key={r.id} value={r.id}>{r.provider} — {r.nickname||r.id}</option>)}</select></label>{replacementId&&<label>Replacement evidence reference<input required value={evidenceReference} onChange={e=>setEvidenceReference(e.target.value)}/></label>}<p>A replacement link transfers no value automatically. Enter an opening only when separately observed.</p></>}
    <label>Opening source or reason<input required value={reason} onChange={e=>setReason(e.target.value)} maxLength={1000}/></label>
   </div><p>Leave missing values blank. No retailer login or full gift-card credentials are requested.</p><button type="submit">Save account or card</button></fieldset>
  </form></details>
  <label>Select individual account or card<select value={selected} onChange={e=>{setSelected(e.target.value);setCorrection('');setSourceAccount('');setConfirmed(false);const row=rows.find(r=>r.id===e.target.value);setEditName(row?.nickname??'');setEditLast(row?.lastFour??'');setEditProvider(row?.provider??'');setEditOpening(row?.opening??'');setEditDate(row?.openingDate??'');setEditComplete(row?.historyComplete??false);setChangeOpening(false)}}><option value="">Choose an account or card</option>{rows.map(row=><option key={row.id} value={row.id}>{row.provider} — {row.nickname||row.id} ({row.kind==='GIFT_CARD'?'Gift card':'Account reward'})</option>)}</select></label>
  {!rows.length&&<p>No individual accounts or cards recorded.</p>}
  {current&&<>
   <p>{current.provider} · {current.kind==='GIFT_CARD'?'Individual gift card':'Account-linked reward'} · {current.unit}{current.lastFour?` · ending ${current.lastFour}`:''}</p>
   <div className="instrument-balances" aria-label="Individual balance comparison"><span>Calculated <strong>{value(current.calculated,current.unit)}</strong></span><span>Actual reported <strong>{value(current.reported,current.unit)}</strong></span><span>Difference <strong>{value(current.difference,current.unit)}</strong></span></div>
   <p>Opening date: {date(current.openingDate)}. Recorded activity through: {date(current.balanceDate)}. Reported observation: {date(current.reportedDate)}.</p>
   {current.events.some(e=>e.active&&e.sourceChanged)&&<Notice>A linked receipt has changed. Review its recorded activity and association; the calculated balance remains unknown until explicitly reconciled.</Notice>}
   {current.calculated==null&&<p>Calculated balance is unknown or incomplete. A reported observation does not create an opening or missing transaction.</p>}
   {current.reported!=null&&<p>{current.comparisonStatus==='SAME_RECORDED_DATE'?'Comparison uses recorded entries on the same recorded date; it does not prove completeness or event ordering.':'Date alignment is unresolved; no difference is inferred across different or unknown dates.'}</p>}
   {current.issuedFrom.map(link=><p key={link.eventId}>Issued from reward account {rows.find(r=>r.id===link.accountId)?.nickname||link.accountId}. Observed source redemption: {link.sourceAmount??'amount not recorded'}. This card issuance is not new reward earnings.</p>)}
   <form onSubmit={e=>{e.preventDefault();void send(`/reward-instruments/${current.id}/events`,{id:crypto.randomUUID(),kind:action,amount,date:eventDate||null,reason:note,...(receiptId&&receiptEvent?{sourceReceiptId:receiptId,sourceEventId:receiptEvent}:{}),...(correction?{supersedes:correction,confirmed,sourceRevision:original?.revision}:{})},current.revision)}}>
    <fieldset disabled={disabled}><legend>Record observed activity</legend><div className="form-grid">
     <label>Activity type<select aria-label="Activity type" value={action} onChange={e=>{setAction(e.target.value);setCorrection('');setReceiptEvent('')}}>{['CREDIT','REDEMPTION','ADJUSTMENT','REFUND','REVERSAL','RESTORATION','REPORTED_BALANCE',...(current.kind==='ACCOUNT_REWARD'?['WITHDRAWAL']:[])].map(v=><option key={v} value={v}>{v.replaceAll('_',' ')}</option>)}</select></label>
     <label>Observed activity amount ({current.unit})<input required inputMode="decimal" value={amount} onChange={e=>setAmount(e.target.value)}/></label>
     <label>Actual event date (optional)<input type="date" value={eventDate} onChange={e=>setEventDate(e.target.value)}/></label>
     <label>Evidence reference or reason<input required value={note} onChange={e=>setNote(e.target.value)} maxLength={1000}/></label>
    </div>
    {workspace&&['CREDIT','REDEMPTION'].includes(action)&&<details><summary>Associate existing receipt activity without changing its amounts</summary>
     <label>Recorded source receipt<select value={receiptId} onChange={e=>{setReceiptId(e.target.value);setReceiptEvent('')}}><option value="">Standalone observed activity</option>{workspace.receipts.filter(r=>r.status==='ACTIVE').map(r=><option key={r.transactionId} value={r.transactionId}>{r.metadata?.receiptNumber||r.transactionId}</option>)}</select></label>
     {receiptId&&<label>Recorded source activity<select required value={receiptEvent} onChange={e=>{setReceiptEvent(e.target.value);const credit=sourceReceipt?.contents.observations.earnings.find(v=>v.earningId===e.target.value);const payment=sourceReceipt?.contents.payments.find(v=>v.paymentId===e.target.value);setAmount(credit?.confirmed?.amount??payment?.amount??'')}}><option value="">Choose recorded activity</option>{action==='CREDIT'?sourceReceipt?.contents.observations.earnings.filter(v=>v.confirmed&&['CREDITED','PAID'].includes(v.status)).map(v=><option key={v.earningId} value={v.earningId}>{v.provider} — {v.offerReference||'Confirmed earning'} — {v.confirmed?.amount}</option>):sourceReceipt?.contents.payments.filter(v=>['GIFT_CARD','WALGREENS_CASH'].includes(v.paymentType)).map(v=><option key={v.paymentId} value={v.paymentId}>{v.paymentType} — {v.amount}</option>)}</select></label>}
     <p>The server checks the recorded amount, units and instrument type. Associating a recorded credit does not add another earning or change the receipt. Use an explicit source; no provider-level history is guessed.</p>
    </details>}
    <details><summary>Correct an existing activity or its account association</summary>
     <label>Original activity account<select value={sourceAccount||selected} onChange={e=>{setSourceAccount(e.target.value);setCorrection('')}}>{rows.filter(r=>r.kind===current.kind&&r.unit===current.unit).map(r=><option key={r.id} value={r.id}>{r.provider} — {r.nickname||r.id}</option>)}</select></label>
     <label>Activity being corrected<select value={correction} onChange={e=>setCorrection(e.target.value)}><option value="">Record new activity</option>{original?.events.filter(v=>v.active&&!v.exchangeId&&v.kind===action).map(v=><option key={v.id} value={v.id}>{v.kind} {v.amount} — {v.reason}</option>)}</select></label>
     {correction&&<label><input type="checkbox" required checked={confirmed} onChange={e=>setConfirmed(e.target.checked)}/>I confirm this correction; preserve the original activity</label>}
    </details><p>Withdrawals are completed observations only. Adjustments may be signed. Record a refund or restoration only when actually observed; a return does not automatically restore rewards.</p>
    <button type="submit">{correction?'Save activity correction':'Save observed activity'}</button></fieldset>
   </form>
   {current.kind==='ACCOUNT_REWARD'&&<details><summary>Record an observed exchange into a gift card</summary><form onSubmit={e=>{e.preventDefault();void send(`/reward-instruments/${current.id}/exchange`,{id:crypto.randomUUID(),cardId:crypto.randomUUID(),provider:cardProvider,nickname:cardName||null,sourceAmount:sourceAmount||null,cardValue,date:eventDate||null,reason:note},current.revision)}}><fieldset disabled={disabled}>
    <label>Issued card retailer<input required value={cardProvider} onChange={e=>setCardProvider(e.target.value)}/></label>
    <label>Issued card nickname<input value={cardName} onChange={e=>setCardName(e.target.value)}/></label>
    <label>Observed source redemption ({current.unit}, blank if unknown)<input inputMode="decimal" value={sourceAmount} onChange={e=>setSourceAmount(e.target.value)}/></label>
    <label>Issued gift-card value (USD)<input required inputMode="decimal" value={cardValue} onChange={e=>setCardValue(e.target.value)}/></label>
    <label>Exchange date (optional)<input type="date" value={eventDate} onChange={e=>setEventDate(e.target.value)}/></label>
    <label>Exchange evidence or reason<input required value={note} onChange={e=>setNote(e.target.value)}/></label>
    <p>Only record a card actually issued. No points conversion rate or additional reward earnings will be invented.</p><button type="submit">Save linked exchange</button></fieldset></form></details>}
   <details><summary>Correct account or card details</summary><form onSubmit={e=>{e.preventDefault();void send(`/reward-instruments/${current.id}`,{provider:editProvider,nickname:editName||null,...(changeOpening?{opening:editOpening||null,date:editDate||null,historyComplete:editComplete}:{}),lastFour:current.kind==='GIFT_CARD'?editLast||null:null,reason:editReason,confirmed:editConfirmed},current.revision,'PUT')}}><fieldset disabled={disabled}>
    <label>Corrected retailer or program<input required value={editProvider} onChange={e=>setEditProvider(e.target.value)}/></label><label>Corrected nickname<input value={editName} onChange={e=>setEditName(e.target.value)}/></label>
    {current.kind==='GIFT_CARD'&&<label>Corrected last four digits<input pattern="[0-9]{4}" maxLength={4} value={editLast} onChange={e=>setEditLast(e.target.value)}/></label>}
    <label><input type="checkbox" checked={changeOpening} onChange={e=>setChangeOpening(e.target.checked)}/>Explicitly correct opening facts or history completeness</label>
    {changeOpening&&<><label>Corrected observed opening (blank if unknown)<input inputMode="decimal" value={editOpening} onChange={e=>setEditOpening(e.target.value)}/></label><label>Corrected opening observation date<input type="date" value={editDate} onChange={e=>setEditDate(e.target.value)}/></label><label><input type="checkbox" checked={editComplete} onChange={e=>setEditComplete(e.target.checked)}/>Opening and subsequent history are sufficiently recorded</label><p>Record an actual opening fact; do not copy the latest reported balance to force agreement. Linked-exchange opening corrections require dependency review.</p></>}
    <label>Detail correction reason<input required value={editReason} onChange={e=>setEditReason(e.target.value)}/></label>
    <label><input type="checkbox" required checked={editConfirmed} onChange={e=>setEditConfirmed(e.target.checked)}/>Confirm detail correction and retain history</label><button type="submit">Save detail correction</button></fieldset></form></details>
   <details><summary>Recorded activity and correction history</summary>{current.events.map(event=><p key={event.id}>{event.active?'Current':'Corrected — original retained'}: {event.kind} {value(event.amount,current.unit)} · {date(event.date)} · {event.reason}{event.relatedInstrument&&` · Linked card ${event.relatedInstrument}`}</p>)}{current.history.map(row=><p key={row.revision}>Revision {row.revision}: {row.nickname||'No nickname'} · {row.reason} · {row.recordedBy}</p>)}</details>
  </>}
  </div>
 </Panel>
}
