import { useEffect, useState } from 'react'
import { Download, Image as ImageIcon } from 'lucide-react'
import type { ApiClient } from '../../api/client'
import { date, label, money, shortId } from '../../api/format'
import { Badge, Loading, Modal, Notice, Unavailable } from '../../components/ui'
import { rewardStatusLabel } from './rewardStatus'
import { EarningLinksForm } from './EarningLinksForm'
import { ReceiptRewardForm } from './ReceiptRewardForm'
import { EarningStatusForm } from './EarningStatusForm'
import type { History, Receipt, Totals, RewardOffer } from '../../types/platform'

export function ReceiptDetail({ id, api, onClose, writable = false, onChanged, offers=[] }: { offers?:RewardOffer[]; id: string; api: ApiClient; onClose: () => void; writable?: boolean; onChanged?: () => void }) {
  const [data, setData] = useState<{ receipt: Receipt; history: History[]; totals: Totals } | null>(null)
  const [addingReward,setAddingReward]=useState(false)
  const [reload, setReload] = useState(0)
  const [linking,setLinking]=useState<string|null>(null)
  const [editing, setEditing] = useState<string | null>(null)
  function updated() { setAddingReward(false); setLinking(null); setEditing(null); setReload(v => v + 1); onChanged?.() }
  const [error, setError] = useState('')
  const [tab, setTab] = useState('Items')
  const [image, setImage] = useState('')
  const [loadingEvidence, setLoadingEvidence] = useState(false)
  useEffect(() => {
    const controller = new AbortController()
    Promise.all([api.receipt(id, controller.signal), api.history(id, controller.signal), api.totals(id, controller.signal)])
      .then(([receipt, history, totals]) => setData({ receipt, history, totals }))
      .catch(e => { if (e.name !== 'AbortError') setError(e.message) })
    return () => controller.abort()
  }, [id, api, reload])
  useEffect(() => () => { if (image) URL.revokeObjectURL(image) }, [image])
  async function evidence(evidenceId: string) {
    setLoadingEvidence(true); setError('')
    try {
      const blob = await api.evidence(id, evidenceId); const url = URL.createObjectURL(blob)
      if (blob.type.startsWith('image/')) setImage(url)
      else { const a = document.createElement('a'); a.href = url; a.download = `receipt-${shortId(id)}-${shortId(evidenceId)}${blob.type === 'application/pdf' ? '.pdf' : '.bin'}`; a.click(); setTimeout(() => URL.revokeObjectURL(url), 10000) }
    } catch (e) { setError((e as Error).message) } finally { setLoadingEvidence(false) }
  }
  const r = data?.receipt
  return <Modal title={r?.metadata?.receiptNumber ? `Receipt ${r.metadata.receiptNumber}` : 'Receipt details'} onClose={onClose}>
    {error && <div className="error" role="alert">{error}</div>}
    {!data && !error && <Loading />}
    {data && r && <>
      {r.status === 'DELETED' && <Notice>Historical transaction values are shown. Deleted receipts are excluded from period savings and calculated balances.</Notice>}
      <div className="detail-meta"><span>{r.metadata?.retailer || 'Store not recorded'} · {date(r.metadata?.purchasedAt)}</span><Badge>{label(r.status)} · revision {r.revision}</Badge></div>
      <div className="tabs" aria-label="Receipt sections">{['Items', 'Discounts', 'Rewards', 'Totals', 'Evidence', 'History'].map(t => <button key={t} className={t === tab ? 'active' : ''} onClick={() => setTab(t)}>{t}</button>)}</div>
      {tab === 'Items' && <><p className="muted">Matching SKU / UPC values group units across lines; original lines and individual prices remain unchanged.</p>{data.totals.productGroups?.map(g=><p key={g.identity}>{g.description}: {g.quantity} unit(s), {g.lineItemIds.length} receipt line(s)</p>)}<div className="table-wrap"><table><thead><tr><th>Line / item</th><th>Qty</th><th>Regular unit</th><th>Sale unit</th></tr></thead><tbody>{r.contents.purchaseItems.map((p, i) => <tr key={p.lineItemId}><td><strong>{i + 1}. {p.productName}</strong><small>{p.productCode || 'SKU not recorded'}</small></td><td>{p.quantity}</td><td>{money(p.regularUnitPrice)}</td><td>{money(p.saleUnitPrice)}</td></tr>)}</tbody></table></div></>}
      {tab === 'Discounts' && <><h3>Applied coupons and discounts</h3>{r.contents.appliedCoupons.length ? r.contents.appliedCoupons.map(c => <div className="detail-row" key={c.applicationId}><div><strong>{c.printedLabel}</strong><small>{c.wholeReceipt ? 'Whole receipt' : c.lineItemIds.length ? `Lines ${c.lineItemIds.map(id => r.contents.purchaseItems.findIndex(p => p.lineItemId === id) + 1).join(', ')}` : 'Products not recorded'}</small></div><strong>{money(c.appliedAmount)}</strong></div>) : <p className="muted">No applied coupons recorded.</p>}<h3>Recorded payments</h3>{r.contents.payments.map(p => <div className="detail-row" key={p.paymentId}><div>{label(p.paymentType)}{p.notes && <small>{p.notes}</small>}</div><strong>{money(p.amount)}</strong></div>)}</>}
      {tab === 'Rewards' && <>{r.status === 'ACTIVE' && <button className="secondary" disabled={!writable || addingReward} onClick={()=>setAddingReward(true)}>Add rebate or reward</button>}{addingReward && <ReceiptRewardForm lineTotals={data.totals.lineTotals} offers={offers} receipt={r} api={api} enabled={writable} onSaved={updated} onCancel={()=>setAddingReward(false)} />}{r.contents.observations.earnings.length ? r.contents.observations.earnings.map(e => <section key={e.earningId}>
        <div className="detail-row"><div><strong>{e.kind === 'REGISTER_REWARD' ? 'Register Rewards' : e.kind === 'WALGREENS_CASH' ? 'Walgreens Cash' : e.provider}</strong><small>{e.offerReference || label(e.kind)}</small><small>{rewardStatusLabel(e)} · {e.scope === 'RECEIPT' ? 'Whole receipt' : e.lineItemIds.length ? `Lines ${e.lineItemIds.map(id => r.contents.purchaseItems.findIndex(p => p.lineItemId === id) + 1).join(', ')}` : 'Products unknown'}</small>
        {e.details?.payoutDestination && <small>Destination: {label(e.details.payoutDestination)} (context only)</small>}
        {e.details?.submissionReference && <small>Reference: {e.details.submissionReference}</small>}
        {data.totals.qualification?.filter(q=>q.earningId===e.earningId).map(q=><small key={q.earningId}>Qualification: {q.review.status} · {q.review.qualifyingQuantity} units · {money(q.review.qualifyingSpend)} · {q.review.reason}</small>)}
        {e.expected && <small>Expected: {e.expected.amount} {e.expected.kind === 'POINTS' ? 'points' : e.expected.currency}</small>}
        {e.recordedLifecycle && <small className="wrap">{e.recordedLifecycle}</small>}</div><div><strong>{e.confirmed ? `${e.confirmed.amount} ${e.confirmed.kind === 'POINTS' ? 'points' : e.confirmed.currency}` : 'Not confirmed'}</strong>
        {e.status === 'REJECTED' && <small>Excluded from earned value</small>}
        {r.status==='ACTIVE'&&<button className="text-button" disabled={!writable} onClick={()=>setLinking(e.earningId)}>Edit product associations</button>}
        {r.status === 'ACTIVE' && !(e.kind !== 'REGISTER_REWARD' && e.status === 'PAID') && <button className="text-button" disabled={!writable} onClick={() => setEditing(e.earningId)}>Update status</button>}</div></div>
        {linking===e.earningId&&<EarningLinksForm lineTotals={data.totals.lineTotals} earning={e} receipt={r} api={api} enabled={writable} onSaved={updated} onCancel={()=>setLinking(null)} />}
        {editing === e.earningId && <EarningStatusForm earning={e} receiptId={id} revision={r.revision} api={api} enabled={writable} onSaved={updated} onClose={updated} />}
      </section>) : <p className="muted">No separate earnings recorded.</p>}</>}
      {tab === 'Totals' && <>
        {data.totals.difference && !/^-?0+(\.0+)?$/.test(data.totals.difference) && <Notice>A difference of {money(data.totals.difference)} is preserved between the reported and calculated checkout totals.</Notice>}
        <div className="key-values">{Object.entries({ 'Regular merchandise': data.totals.regularMerchandise, 'Sales tax': data.totals.tax, 'Coupons': data.totals.coupons, 'Register Rewards used': data.totals.rrUsed, 'Walgreens Cash used': data.totals.wcUsed, 'Calculated checkout': data.totals.calculatedDue, 'Reported checkout': data.totals.reportedDue, 'Newly earned value': data.totals.savings.newlyEarned, 'Paid after rebates': data.totals.savings.paidAfterRebates, 'Total saved': data.totals.savings.totalSaved }).map(([name, amount]) => <div key={name}><span>{name}</span><strong>{money(amount)}</strong></div>)}</div>
        {data.totals.savings.issues.map(issue => <p className="muted" key={issue}>{issue}</p>)}
      </>}
      {tab === 'Evidence' && <><p className="muted">Original attachments are loaded only when selected.</p><div className="evidence-list">{r.evidenceIds.map((e, i) => <button key={e} className="secondary" disabled={loadingEvidence} onClick={() => void evidence(e)}><ImageIcon size={17} />Attachment {i + 1}<Download size={14} /></button>)}</div>{!r.evidenceIds.length && <p>No attachments recorded.</p>}{image && <img className="receipt-image" src={image} alt="Original receipt attachment" />}<Unavailable>Attachments can be added while creating a receipt. Adding or replacing attachments on saved receipts is not supported yet.</Unavailable></>}
      {tab === 'History' && <ol className="history">{data.history.map(h => <li key={h.historyId}><span className="history-dot" /><div><strong>{label(h.result.action)} · revision {h.result.revision}</strong><small>{new Date(h.result.committedAt).toLocaleString()}</small><p>{h.changeCtx?.reason || `Created by ${h.createCtx?.requestedBy || 'recorded owner'}`}</p><code>{h.historyId}</code></div></li>)}</ol>}
    </>}
  </Modal>
}
