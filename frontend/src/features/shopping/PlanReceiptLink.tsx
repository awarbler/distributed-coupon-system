import { useState } from 'react'
import type { ApiClient } from '../../api/client'
import type { Plan, Workspace } from '../../types/platform'
import { date, money } from '../../api/format'
import { FormActions, Modal } from '../../components/ui'

export function PlanReceiptLink({plan,data,api,writable,onClose,onSaved}:{plan:Plan;data:Workspace;api:ApiClient;writable:boolean;onClose:()=>void;onSaved:()=>void}) {
 const [search,setSearch]=useState(''),[receiptId,setReceiptId]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState('')
 const [pending,setPending]=useState<{body:{receiptId:string;receiptRevision:string};key:string;revision:string}|null>(null)
 const receipts=data.receipts.filter(r=>r.status==='ACTIVE')
 const query=search.trim().toLowerCase()
 const receiptQuery=query.replace(/[\s\-‐‑‒–—−]/g,'')
 const matchingReceipts=receipts.filter(r=>r.transactionId===receiptId
  || `${r.metadata?.receiptNumber??''} ${r.metadata?.retailer??''} ${r.date??''}`.toLowerCase().includes(query)
  || (receiptQuery.length>0&&(r.metadata?.receiptNumber??'').toLowerCase().replace(/[\s\-‐‑‒–—−]/g,'').includes(receiptQuery)))
 const selected=receipts.find(r=>r.transactionId===receiptId)
 async function save(e:React.FormEvent){e.preventDefault();if(!writable||!selected)return;setBusy(true);setError('');const request=pending??{body:{receiptId:selected.transactionId,receiptRevision:selected.revision},key:crypto.randomUUID(),revision:plan.revision};setPending(request)
  try{await api.mutate(`/shopping-plans/${plan.planId}/receipt-link`,'POST',request.body,request.key,request.revision);setPending(null);onSaved()}catch(e){setError((e as Error).message);if([400,403,404,409].includes((e as {status:number}).status))setPending(null)}finally{setBusy(false)}
 }
 return <Modal title={`Connect receipt to ${plan.name}`} onClose={()=>{if(!busy&&!pending)onClose()}}><p>Choose the receipt for this completed deal. Connecting moves the plan to Completed Shopping Plans. It does not change the receipt, payments, savings or earned rewards.</p><form onSubmit={save}>{error&&<p role="alert" className="error">{error}</p>}<fieldset disabled={!writable||busy||!!pending}><label>Search saved receipts<input type="search" value={search} onChange={e=>setSearch(e.target.value)}/></label><label>Completed deal receipt<select required value={receiptId} onChange={e=>setReceiptId(e.target.value)}><option value="">Choose an existing receipt</option>{matchingReceipts.map(r=><option key={r.transactionId} value={r.transactionId}>{date(r.date)} · {r.metadata?.retailer||'Store not recorded'} · {r.metadata?.receiptNumber||'Receipt number not recorded'} · {money(r.totals.reportedDue)}</option>)}</select></label>{selected&&<p>Receipt total: <strong>{money(selected.totals.reportedDue)}</strong> · {selected.metadata?.retailer} · {date(selected.date)}</p>}{!receipts.length&&<p>Enter the receipt first, then return here to connect it.</p>}</fieldset><FormActions><button type="button" className="secondary" disabled={busy||!!pending} onClick={onClose}>Cancel</button><button className="primary" disabled={!writable||busy||!selected}>{pending?'Retry same receipt connection':'Connect receipt & complete deal'}</button></FormActions></form></Modal>
}
