import { useState } from 'react'
import type { ApiClient } from '../../api/client'
import type { Promotion } from '../../types/platform'
import { FormActions, Modal, Notice, Panel } from '../../components/ui'

export function PromotionManager({promotions,api,writable,onChanged}:{promotions:Promotion[];api?:ApiClient;writable:boolean;onChanged?:()=>void}) {
  const [selected,setSelected]=useState(''),[editing,setEditing]=useState<Promotion|null|undefined>(undefined)
  const [error,setError]=useState(''),[busy,setBusy]=useState(false)
  const [deletion,setDeletion]=useState<{id:string;key:string;body:unknown}|null>(null)
  const promotion=promotions.find(p=>p.id===selected)
  async function remove() {
    if(!api||!promotion||!writable)return
    if(!deletion&&!window.confirm(`Delete ${promotion.name}? Receipt records and rewards will be retained.`))return
    setBusy(true);setError('')
    const request=deletion??{id:promotion.id,key:crypto.randomUUID(),body:{expectedToken:promotion.token}}
    setDeletion(request)
    try{await api.mutate(`/rebate-programs/${request.id}`,'DELETE',request.body,request.key);setDeletion(null);setSelected('');onChanged?.()}
    catch(e){setError((e as Error).message);if([400,404,409,412].includes((e as {status?:number}).status??0))setDeletion(null)}finally{setBusy(false)}
  }
  return <Panel className="promotion-management" title="Manage promotions">{error&&<Notice>{error}</Notice>}<div className="form-grid"><label>Promotion<select disabled={busy||!!deletion} value={selected} onChange={e=>setSelected(e.target.value)}><option value="">Select a promotion</option>{promotions.map(p=><option key={p.id} value={p.id}>{p.name} — {p.status}</option>)}</select></label><div className="form-actions"><button className="primary" disabled={!api||!writable||busy||!!deletion} onClick={()=>setEditing(null)}>Add promotion</button><button className="secondary" disabled={!api||!writable||!promotion||busy||!!deletion} onClick={()=>setEditing(promotion)}>Edit</button><button className="secondary" disabled={!api||!writable||!promotion||busy} onClick={()=>void remove()}>{deletion?'Retry delete':'Delete'}</button></div></div>{editing!==undefined&&api&&<PromotionEditor initial={editing} api={api} onClose={()=>setEditing(undefined)} onSaved={()=>{setEditing(undefined);onChanged?.()}}/>}</Panel>
}

function PromotionEditor({initial,api,onClose,onSaved}:{initial:Promotion|null;api:ApiClient;onClose:()=>void;onSaved:()=>void}) {
  const [name,setName]=useState(initial?.name??''),[terms,setTerms]=useState(initial?.terms??'')
  const [threshold,setThreshold]=useState(initial?.threshold??''),[value,setValue]=useState(initial?.giftCardValue??'')
  const [start,setStart]=useState(initial?.start??''),[end,setEnd]=useState(initial?.end??'')
  const [status,setStatus]=useState(initial?.status??'TRACKING'),[busy,setBusy]=useState(false),[error,setError]=useState('')
  const [pending,setPending]=useState<{id:string;body:unknown;key:string}|null>(null)
  async function save(e:React.FormEvent) {
    e.preventDefault();setBusy(true);setError('')
    const request=pending??{id:initial?.id??crypto.randomUUID(),key:crypto.randomUUID(),body:{name,terms,threshold,giftCardValue:value,start,end,status,expectedToken:initial?.token??'new'}}
    setPending(request)
    try{await api.mutate(`/rebate-programs/${request.id}`,'PUT',request.body,request.key);setPending(null);onSaved()}
    catch(e){setError((e as Error).message);if([400,404,409,412].includes((e as {status?:number}).status??0))setPending(null)}finally{setBusy(false)}
  }
  return <Modal title={initial?'Edit promotion':'Add promotion'} onClose={()=>{if(!busy&&!pending)onClose()}}><form onSubmit={save}>{error&&<Notice>{error}</Notice>}<fieldset disabled={busy||!!pending}><div className="form-grid"><label className="field-full">Promotion name<input required maxLength={200} value={name} onChange={e=>setName(e.target.value)}/></label><label>Required qualifying spend<input required inputMode="decimal" pattern="[0-9]+([.][0-9]{1,2})?" value={threshold} onChange={e=>setThreshold(e.target.value)}/></label><label>Reward value (USD)<input required inputMode="decimal" pattern="[0-9]+([.][0-9]{1,2})?" value={value} onChange={e=>setValue(e.target.value)}/></label><label>Start date<input required type="date" value={start} onChange={e=>setStart(e.target.value)}/></label><label>End date<input required type="date" min={start} value={end} onChange={e=>setEnd(e.target.value)}/></label><label className="field-full">Offer terms<textarea required maxLength={4000} value={terms} onChange={e=>setTerms(e.target.value)}/></label><label>Tracking status<select value={status} onChange={e=>setStatus(e.target.value)}>{['TRACKING','SUBMITTED','APPROVED','RECEIVED'].map(s=><option key={s}>{s}</option>)}</select></label></div><p className="muted">Track eligible spend after coupons. Status changes do not create a reward or account payment.</p></fieldset><FormActions><button type="button" className="secondary" disabled={busy||!!pending} onClick={onClose}>Cancel</button><button className="primary" disabled={busy}>{busy?'Saving…':pending?'Retry same promotion':'Save promotion'}</button></FormActions></form></Modal>
}
