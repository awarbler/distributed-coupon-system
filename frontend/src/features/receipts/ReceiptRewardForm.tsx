import { useState } from 'react'
import type { FormEvent } from 'react'
import { ApiClient, ApiError } from '../../api/client'
import { Notice } from '../../components/ui'
import { RewardFields } from './RewardFields'
import { newReward, rewardBody } from './rewardStatus'
import type { Receipt, Totals, RewardOffer } from '../../types/platform'
import { money } from '../../api/format'

/** Post-save additions share input and qualification logic with new receipt entry. */
export function ReceiptRewardForm({ receipt, api, enabled, onSaved, onCancel, offers=[], lineTotals }: {lineTotals?:Record<string,string>;offers?:RewardOffer[];receipt:Receipt;api:ApiClient;enabled:boolean;onSaved:()=>void;onCancel:()=>void}) {
  const [reward,setReward]=useState(()=>/^cvs/i.test(receipt.metadata?.retailer??'')?{...newReward(),provider:'ECB',form:'STORE_REWARD',requirementType:'PURCHASE'}:newReward())
  const [review,setReview]=useState<{body:unknown;totals:Totals;qualification:{status:string;reason:string;qualifyingQuantity:string;qualifyingSpend:string|null}}|null>(null)
  const [pending,setPending]=useState<{body:unknown;key:string}|null>(null)
  const [busy,setBusy]=useState(false),[error,setError]=useState('')
  const path=`/receipts/${receipt.transactionId}/earnings`
  async function preview(e:FormEvent){e.preventDefault();setBusy(true);setError('');try{const body={earning:rewardBody(reward),evidence:reward.attachment?[reward.attachment]:[]};const result=await api.mutate(`${path}/preview`,'POST',body,crypto.randomUUID(),receipt.revision) as Omit<NonNullable<typeof review>,'body'>;setReview({body,...result})}catch(ex){setError((ex as Error).message)}finally{setBusy(false)}}
  async function save(){if(!review)return;const request=pending||{body:review.body,key:crypto.randomUUID()};setPending(request);setBusy(true);setError('');try{await api.mutate(path,'POST',request.body,request.key,receipt.revision);onSaved()}catch(ex){setError((ex as Error).message);if(ex instanceof ApiError&&ex.status===400)setPending(null)}finally{setBusy(false)}}
  return <section className="receipt-input-row"><h3>Add rebate or reward</h3>{error&&<p role="alert">{error}</p>}{review?<>{reward.status === 'EXPECTED' && <p>Expected — waiting to receive. This reward does not increase confirmed benefits until you record it as received.</p>}<details><summary>Optional qualification review</summary><Notice>Qualification: {review.qualification.status} · {review.qualification.reason}. Selected quantity {review.qualification.qualifyingQuantity}; spend {money(review.qualification.qualifyingSpend)}.</Notice></details><p>Register total remains {money(review.totals.reportedDue)}. Total confirmed earned value after this addition: {money(review.totals.savings.newlyEarned)}.</p>{pending&&<Notice>The exact request is retained for retry. If its outcome is uncertain, refresh and check history before entering it again.</Notice>}<div className="form-actions"><button type="button" className="secondary" disabled={busy||!!pending} onClick={()=>setReview(null)}>Back to reward</button><button className="primary" type="button" disabled={busy||!enabled} onClick={()=>void save()}>{pending?'Retry same addition':'Save rebate or reward'}</button></div></>:<form onSubmit={preview}><fieldset disabled={busy||!enabled}><RewardFields retailer={receipt.metadata?.retailer??''} offers={offers} onFileBusy={setBusy} value={reward} onChange={setReward} lines={receipt.contents.purchaseItems.map(l=>({id:l.lineItemId,description:l.productName,sku:l.productCode,quantity:l.quantity,price:l.saleUnitPrice||'0',lineTotal:lineTotals?.[l.lineItemId]}))}/><div className="form-actions"><button type="button" className="secondary" onClick={onCancel}>Cancel reward</button><button className="primary">{busy?'Reviewing…':'Review rebate or reward'}</button></div></fieldset></form>}</section>
}
