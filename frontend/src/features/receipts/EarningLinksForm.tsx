import { useState } from 'react'
import { ApiClient } from '../../api/client'
import { integer } from '../../api/format'
import { ReceiptLineSelector } from './ReceiptLineSelector'
import type { Earning,Receipt } from '../../types/platform'
export function EarningLinksForm({ earning,receipt,api,onSaved,onCancel,enabled,lineTotals }: {lineTotals?:Record<string,string>;earning:Earning;receipt:Receipt;api:ApiClient;onSaved:()=>void;onCancel:()=>void;enabled:boolean}) {
  const [selection,setSelection]=useState<Record<string,string>>(()=>Object.fromEntries(earning.lineItemIds.map(id=>[id,earning.details?.qualifyingQuantities[id]||receipt.contents.purchaseItems.find(l=>l.lineItemId===id)?.quantity||'1'])))
  const [request,setRequest]=useState<{body:unknown;key:string}|null>(null),[error,setError]=useState(''),[busy,setBusy]=useState(false)
  async function save(){setBusy(true);setError('');try{const pending=request||{key:crypto.randomUUID(),body:{lineItemIds:Object.keys(selection),qualifyingQuantities:Object.fromEntries(Object.entries(selection).map(([id,q])=>[id,integer(q)]))}};setRequest(pending);await api.mutate(`/receipts/${receipt.transactionId}/earnings/${earning.earningId}/associations`,'PUT',pending.body,pending.key,receipt.revision);onSaved()}catch(ex){setError((ex as Error).message)}finally{setBusy(false)}}
  return <form onSubmit={e=>{e.preventDefault();void save()}}><fieldset disabled={!enabled||busy||!!request}><ReceiptLineSelector name="Earning product associations" lines={receipt.contents.purchaseItems.map(l=>({id:l.lineItemId,description:l.productName,sku:l.productCode,quantity:l.quantity,price:l.saleUnitPrice||'0',lineTotal:lineTotals?.[l.lineItemId]}))} value={selection} onChange={setSelection}/></fieldset>{error&&<p role="alert">{error}</p>}<p className="muted">This changes product associations only. Amount, status, payment and the original earning ID remain unchanged.</p><div className="form-actions"><button className="secondary" type="button" onClick={onCancel} disabled={busy}>Cancel associations</button><button className="primary" disabled={!enabled||busy}>{request?'Retry same associations':'Save associations'}</button></div></form>
}
