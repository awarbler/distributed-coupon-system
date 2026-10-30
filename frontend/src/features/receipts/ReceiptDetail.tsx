import { ReceiptLineSelector } from './ReceiptLineSelector'
import { InfoHelp } from '../../components/ui'
import { FormActions } from '../../components/ui'
import { ECBUseFields, ReceiptDiscountSale, ReceiptProductTotals } from './ReceiptCreate'
import { RewardConnection } from '../../pages/WorkspacePages'
import { useEffect, useState } from 'react'
import { Download, Image as ImageIcon } from 'lucide-react'
import type { ApiClient } from '../../api/client'
import { date, integer, label, money, shortId, productDiscountLabel, isWalgreensStore } from '../../api/format'
import { AutoSizeTextArea, Badge, Loading, Modal, Notice, Unavailable } from '../../components/ui'
import { ReceiptFinancialReview, PrintedFooterView, PrintedFooterFields, ItemDiscountFields } from './ReceiptCreate'
import { rewardStatusLabel, footerDraft, footerBody } from './rewardStatus'
import { EarningLinksForm } from './EarningLinksForm'
import { ReceiptRewardForm } from './ReceiptRewardForm'
import { EarningStatusForm, RewardCreditSourceForm } from './EarningStatusForm'
import type { History, Receipt, Totals, RewardOffer, RRMatchUse } from '../../types/platform'

export function ReceiptDetail({ id, api, onClose, writable = false, onChanged, offers=[] }: { offers?:RewardOffer[]; id: string; api: ApiClient; onClose: () => void; writable?: boolean; onChanged?: () => void }) {
  const [data, setData] = useState<{ receipt: Receipt; history: History[]; totals: Totals } | null>(null)
  const [addingReward,setAddingReward]=useState(false)
  const [receiptAction, setReceiptAction] = useState<'edit' | 'status' | null>(null)
  const [sourceReceipt, setSourceReceipt] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [linking,setLinking]=useState<string|null>(null)
  const [editing, setEditing] = useState<string | null>(null)
  function updated() { setReceiptAction(null); setAddingReward(false); setLinking(null); setEditing(null); setReload(v => v + 1); onChanged?.() }
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
      <div className="detail-meta"><span>{r.metadata?.retailer || 'Store not recorded'} · {date(r.metadata?.purchasedAt)}</span><Badge>{label(r.status)} · revision {r.revision}</Badge></div>{r.metadata?.storeReference&&<p>{r.metadata.storeReference}</p>}
      <FormActions>
        {r.status === 'ACTIVE' && <button className="secondary" disabled={!writable || !!receiptAction} onClick={() => setReceiptAction('edit')}>Edit receipt</button>}
        <button className="secondary" disabled={!writable || !!receiptAction} onClick={() => setReceiptAction('status')}>{r.status === 'DELETED' ? 'Restore receipt' : 'Delete receipt'}</button>
      </FormActions>
      {receiptAction === 'edit' && <ReceiptEdit receipt={r} api={api} enabled={writable} onSaved={updated} onCancel={() => setReceiptAction(null)} />}
      {receiptAction === 'status' && <ReceiptStatusAction receipt={r} api={api} enabled={writable} onSaved={updated} onCancel={() => setReceiptAction(null)} />}

      <div className="tabs" aria-label="Receipt sections">{['Items', 'Discounts', 'Payments', 'Rewards', 'Totals', 'Evidence', 'History'].map(t => <button key={t} className={t === tab ? 'active' : ''} onClick={() => setTab(t)}>{t}</button>)}</div>
      {tab === 'Items' && <><p className="muted">Matching SKU / UPC values group units across lines; original lines and individual prices remain unchanged.</p>{data.totals.productGroups?.map(g=><p key={g.identity}>{g.description}: {g.quantity} unit(s), {g.lineItemIds.length} receipt line(s)</p>)}<div className="table-wrap"><table><thead><tr><th>Line / item</th><th>Qty</th><th>Regular unit</th><th>Sale unit</th></tr></thead><tbody>{r.contents.purchaseItems.map((p, i) => <tr key={p.lineItemId}><td><strong>{i + 1}. {p.productName}</strong><small>{p.productCode || 'SKU not recorded'}</small></td><td>{p.quantity}</td><td>{money(p.regularUnitPrice)}</td><td>{money(p.saleUnitPrice)}</td></tr>)}</tbody></table></div></>}
      {tab === 'Discounts' && <><h3>Applied coupons and discounts</h3>{r.contents.appliedCoupons.length ? r.contents.appliedCoupons.map(c => <div className="detail-row" key={c.applicationId}><div><strong>{c.printedLabel}</strong>{c.requiredQuantity && <small>Requires buying {c.requiredQuantity} products</small>}<small>{c.wholeReceipt ? 'Whole receipt' : c.lineItemIds.length ? `Lines ${c.lineItemIds.map(id => r.contents.purchaseItems.findIndex(p => p.lineItemId === id) + 1).join(', ')}` : 'Products not recorded'}</small></div><strong>{money(c.appliedAmount)}</strong></div>) : <p className="muted">No applied coupons recorded.</p>}</>}
      {tab === 'Payments' && <><h3>Recorded payments</h3>{r.contents.payments.map(p => <div className="detail-row" key={p.paymentId}><div>{label(p.paymentType)}{p.notes && <small>{p.notes}</small>}</div><strong>{money(p.amount)}</strong></div>)}</>}
      {tab === 'Rewards' && <>{r.status === 'ACTIVE' && <button className="secondary" disabled={!writable || addingReward} onClick={()=>setAddingReward(true)}>Add rebate or reward</button>}{addingReward && <ReceiptRewardForm lineTotals={data.totals.lineTotals} offers={offers} receipt={r} api={api} enabled={writable} onSaved={updated} onCancel={()=>setAddingReward(false)} />}{r.contents.observations.earnings.length ? r.contents.observations.earnings.map(e => <section className="receipt-reward-card" key={e.earningId}>
        <div className="receipt-reward-layout"><div className="receipt-reward-info"><strong className="receipt-reward-program">{e.kind === 'REGISTER_REWARD' ? 'Register Rewards' : e.kind === 'WALGREENS_CASH' ? 'Walgreens Cash' : e.provider}</strong><h3 className="receipt-reward-title">{e.offerReference || label(e.kind)}</h3><p className="receipt-reward-meta"><span className="badge">{rewardStatusLabel(e)}</span><span> · {e.scope === 'RECEIPT' ? 'Whole receipt' : e.lineItemIds.length ? `Lines ${e.lineItemIds.map(id => r.contents.purchaseItems.findIndex(p => p.lineItemId === id) + 1).join(', ')}` : 'Products unknown'}</span></p>
        {e.expirationDate && <p className="muted">Expires {date(e.expirationDate)}</p>}
        {e.details?.payoutDestination && <small>Destination: {label(e.details.payoutDestination)} (context only)</small>}
        {e.details?.submissionReference && <small>Reference: {e.details.submissionReference}</small>}
        {data.totals.qualification?.filter(q=>q.earningId===e.earningId).map(q=><details key={q.earningId}><summary>Optional qualification details</summary><small>Qualification: {q.review.status} · {q.review.qualifyingQuantity} units · {money(q.review.qualifyingSpend)} · {q.review.reason}</small></details>)}
        {!e.confirmed && e.expected && <small>Expected: {e.expected.amount} {e.expected.kind === 'POINTS' ? 'points' : e.expected.currency}</small>}
        {e.recordedLifecycle && <small className="wrap">{e.recordedLifecycle}</small>}</div><div className="receipt-reward-value"><strong>{e.confirmed ? e.confirmed.kind==='POINTS' ? `${e.confirmed.amount} points` : e.confirmed.currency==='USD' ? money(e.confirmed.amount) : `${e.confirmed.amount} ${e.confirmed.currency}` : 'Not confirmed'}</strong>
        {data.totals.benefitObservations?.filter(b => b.earningId === e.earningId && b.valuation).map(b => <small key={b.earningId}>Dollar value: {money(b.valuation?.amount)}</small>)}
        {e.status === 'REJECTED' && <small>Excluded from earned value</small>}
        {r.status==='ACTIVE'&&<button className="secondary" disabled={!writable} onClick={()=>setLinking(e.earningId)}>Edit products / expiration</button>}
        {r.status === 'ACTIVE' && <button className="secondary" disabled={!writable} onClick={() => setEditing(e.earningId)}>Edit Amount / Status</button>}</div></div>
        {e.details?.originalReceiptId&&<p><button type="button" className="text-button" onClick={()=>setSourceReceipt(e.details!.originalReceiptId!)}>View Original Purchase / Offer</button></p>}
        {r.creditSettlements?.filter(v=>v.earningId===e.earningId).map(v=><section className="receipt-input-row" key={v.earningId}><strong>Manager Credits Received: {money(v.received)} Of {money(v.expected)}</strong><p>The Original Rejection Remains Recorded. These Separate Credits Are Counted Once On Their Own Receipts.</p>{v.credits.map(c=><p key={c.earningId}>{money(c.amount)} · <button type="button" className="text-button" onClick={()=>setSourceReceipt(c.receiptId)}>Receipt {c.receiptNumber||c.receiptId}</button></p>)}</section>)}
        {e.kind==='WALGREENS_CASH'&&e.status==='CREDITED'&&<RewardCreditSourceForm api={api} receiptId={r.transactionId} earning={e} revision={r.revision} writable={writable} onSaved={updated}/>}
        {linking===e.earningId&&<EarningLinksForm lineTotals={data.totals.lineTotals} earning={e} receipt={r} api={api} enabled={writable} onSaved={updated} onCancel={()=>setLinking(null)} />}
        {editing === e.earningId && <EarningStatusForm earning={e} receiptId={id} revision={r.revision} api={api} enabled={writable} onSaved={updated} onClose={updated} />}
      </section>) : <p className="muted">No separate earnings recorded.</p>}</>}
      {tab === 'Totals' && <>
        <ReceiptFinancialReview totals={data.totals} cvs={/^cvs/i.test(r.metadata?.retailer??'')} />{isWalgreensStore(r.metadata?.retailer) && <PrintedFooterView observations={r.contents.observations} />}
        {data.totals.savings.issues.map(issue => <p className="muted" key={issue}>{issue}</p>)}
      </>}
      {(tab === 'Rewards' || tab === 'Totals') && <>
        {r.status==='ACTIVE' && isWalgreensStore(r.metadata?.retailer??'') && <ReceiptRRMatch receipt={r} api={api} enabled={writable && !receiptAction} onSaved={updated} />}
        {r.rrUses?.map((reward,i)=><section key={`${reward.earningId}-${i}`}><p>Used {reward.promotion}: {money(reward.usedAmount)}</p><RewardConnection reward={reward} onReceipt={setSourceReceipt}/></section>)}
      </>}
      {tab === 'Evidence'  && <><p className="muted">Original attachments are loaded only when selected.</p><div className="evidence-list">{r.evidenceIds.map((e, i) => <button key={e} className="secondary" disabled={loadingEvidence} onClick={() => void evidence(e)}><ImageIcon size={17} />Attachment {i + 1}<Download size={14} /></button>)}</div>{!r.evidenceIds.length && <p>No attachments recorded.</p>}{image && <img className="receipt-image" src={image} alt="Original receipt attachment" />}<Unavailable>Attachments can be added while creating a receipt. Adding or replacing attachments on saved receipts is not supported yet.</Unavailable></>}
      {tab === 'History' && <ol className="history">{data.history.map(h => <li key={h.historyId}><span className="history-dot" /><div><strong>{label(h.result.action)} · revision {h.result.revision}</strong><small>{new Date(h.result.committedAt).toLocaleString()}</small><p>{h.changeCtx?.reason || `Created by ${h.createCtx?.requestedBy || 'recorded owner'}`}</p><code>{h.historyId}</code></div></li>)}</ol>}
    </>}
    {sourceReceipt && sourceReceipt !== id && <ReceiptDetail id={sourceReceipt} api={api} onClose={() => setSourceReceipt(null)} writable={writable} onChanged={onChanged} offers={offers} />}
  </Modal>
}


type Props = { receipt: Receipt; api: ApiClient; enabled: boolean; onSaved: () => void; onCancel: () => void }
const nullable = (value: string) => value.trim() || null
export function ReceiptEdit({ receipt, api, enabled, onSaved, onCancel }: Props) {
  const m = receipt.metadata, contents = receipt.contents
  const pricingReady = receipt.editDiscounts !== undefined
  const [receiptKind,setReceiptKind]=useState(m?.receiptKind??'PURCHASE')
  const [retailer, setRetailer] = useState(m?.retailer ?? '')
  const [number, setNumber] = useState(m?.receiptNumber ?? '')
  const [storeReference,setStoreReference]=useState(m?.storeReference??'')
  const [day, setDay] = useState(contents.observations.purchaseDate ?? m?.purchasedAt?.slice(0, 10) ?? '')
  const [time, setTime] = useState(contents.observations.printedTime ?? m?.purchasedAt?.slice(11, 16) ?? '')
  const [channel, setChannel] = useState(m?.purchaseChannel ?? 'UNKNOWN')
  const [basis, setBasis] = useState(m?.printedTotalBasis ?? 'UNKNOWN')
  const [complete, setComplete] = useState(m?.paymentEntryStatus ?? 'UNKNOWN')
  const [footer, setFooter] = useState(() => footerDraft(contents.observations))
  const [tax, setTax] = useState(contents.receiptTotals.taxAmount ?? '')
  const [total, setTotal] = useState(contents.receiptTotals.finalTotal ?? '')
  const [focusName,setFocusName]=useState('')
  const [focusDiscount,setFocusDiscount]=useState('')
  function focusedDiscountId(){const id=crypto.randomUUID();setFocusDiscount(id);return id}
  const [items, setItems] = useState(contents.purchaseItems.map(item => ({ ...item, regularUnitPrice: item.regularUnitPrice ?? '', saleUnitPrice: item.saleUnitPrice ?? '', enteredSalePrice:'', productCode: item.productCode ?? '' })))
  const [payments, setPayments] = useState(contents.payments.map(payment => ({ ...payment, amount: payment.amount ?? '', notes: payment.notes ?? '', accountName: payment.accountName ?? '', role: receipt.paymentRoles?.[payment.paymentId] ?? (payment.paymentType === 'OTHER' || payment.paymentType === 'REGISTER_REWARD' ? 'UNRESOLVED' : 'FUNDING') })))
  const [discounts, setDiscounts] = useState((receipt.editDiscounts ?? contents.appliedCoupons.map(d => ({ ...d, kind: 'OTHER', qualifyingQuantities: {} }))).map(d => ({ ...d, shared: d.wholeReceipt || d.lineItemIds.length !== 1, appliedAmount: d.appliedAmount ?? '' })))
  const [rrUses, setRRUses] = useState((contents.observations.unidentifiedRR ?? []).map(r=>({...r,amount:r.amount??'',notes:r.notes??''})))
  const [rrTarget,setRRTarget] = useState('new')
  const [reclassifications,setReclassifications]=useState<{kind:string;id:string;targetEventId:string}[]>([])
  function moveToRR(kind: 'PAYMENT'|'COUPON_APPLIED', id: string, amount: string, description: string) {
    const target=rrTarget==='new'?crypto.randomUUID():rrTarget
    if(rrTarget==='new')setRRUses([...rrUses,{eventId:target,amount,recordedDescription:description||'Register Rewards used',notes:''}])
    const saved=kind==='PAYMENT'?contents.payments.some(p=>p.paymentId===id):contents.appliedCoupons.some(d=>d.applicationId===id)
    if(saved)setReclassifications([...reclassifications,{kind,id,targetEventId:target}])
    if(kind==='PAYMENT')setPayments(payments.filter(p=>p.paymentId!==id));else setDiscounts(discounts.filter(d=>d.applicationId!==id))
    setComplete('UNKNOWN')
  }
  const [reason, setReason] = useState(''), [error, setError] = useState(''), [busy, setBusy] = useState(false), [ack, setAck] = useState(false)
  const [review, setReview] = useState<{ body: Record<string, unknown>; token: string; totals: Totals } | null>(null)
  const [pending, setPending] = useState<{ body: unknown; key: string } | null>(null)
  function field(name: string, value: string, change: (value: string) => void, required = false, type = 'text', full = false) {
    return <label className={full ? 'field-full' : undefined}>{name}{full ? <AutoSizeTextArea autoFocus={focusName===name} aria-label={name} value={value} required={required} onChange={e => change(e.target.value)} /> : <input autoFocus={focusName===name} aria-label={name} value={value} type={type} required={required} onChange={e => change(e.target.value)} />}</label>
  }
  async function preview() {
    setBusy(true); setError('')
    try {
      payments.forEach((p,i)=>{if(!/^(?:[0-9]+(?:\.[0-9]+)?|\.[0-9]+)$/.test(p.amount.trim()) || /^(?:0+(?:\.0+)?|\.0+)$/.test(p.amount.trim()))throw new Error(`Payment ${i+1} amount must be greater than zero. Remove unused payment rows instead of leaving a zero amount.`)})
      const body = { receiptKind, rrUses: rrUses.map(r=>({...r,notes:nullable(r.notes)})), rrReclassifications: reclassifications, printedFooter: footerBody(footer), storeReference:nullable(storeReference), retailer, receiptNumber: nullable(number), purchaseDate: nullable(day), printedTime: nullable(time), purchaseChannel: channel, printedTotalBasis: basis, paymentEntryStatus: complete, tax, reportedTotal: total, reason,
        items: items.map(item => ({ lineItemId: item.lineItemId, productName: item.productName, productCode: nullable(item.productCode), regularUnitPrice: nullable(item.regularUnitPrice), quantity: integer(item.quantity), ...(item.enteredSalePrice.trim()?{saleUnitPrice:item.enteredSalePrice}:!receipt.editDiscounts ? { saleUnitPrice: item.saleUnitPrice } : receipt.preservePriceLineIds?.includes(item.lineItemId) ? { saleUnitPrice: item.saleUnitPrice } : {}) })),
        payments: payments.map(payment => ({ paymentId: payment.paymentId, paymentType: payment.paymentType, amount: payment.amount, notes: nullable(payment.notes), ...(payment.paymentType==='OTHER'?{role:payment.role,accountName:nullable(payment.accountName)}:payment.paymentType==='GIFT_CARD'?{accountName:nullable(payment.accountName)}:{}) })), discounts: discounts.filter(d => d.appliedAmount.trim()&&!/^(?:0+(?:[.]0*)?|[.]0+)$/.test(d.appliedAmount.trim())).map(({shared: _shared, ...d}) => ({ ...d, requiredQuantity: d.requiredQuantity ? integer(d.requiredQuantity) : null, qualifyingQuantities: Object.fromEntries(Object.entries(d.qualifyingQuantities).map(([id,q]) => [id, integer(String(q))])) })) }
      const result = await api.mutate(`/receipts/${receipt.transactionId}/edit-preview`, 'POST', body, crypto.randomUUID(), receipt.revision) as { reviewToken: string; totals: Totals }
      setReview({ body, token: result.reviewToken, totals: result.totals }); setAck(false)
    } catch (e) { setError((e as Error).message) } finally { setBusy(false) }
  }
  async function save() {
    if (!review) return
    setBusy(true); setError('')
    try {
      const request = pending ?? { body: { ...review.body, reviewToken: review.token, acknowledgeDifference: ack }, key: crypto.randomUUID() }
      setPending(request)
      await api.mutate(`/receipts/${receipt.transactionId}`, 'PUT', request.body, request.key, receipt.revision)
      onSaved()
    } catch (e) { setError((e as Error).message) } finally { setBusy(false) }
  }
  const close = () => { if (!pending || window.confirm('The save outcome may be uncertain. Close and refresh the receipt before making another correction?')) onCancel() }
  return <section className="receipt-editor"><h3>Edit receipt</h3>
    <p className="muted">Existing evidence, reward records and history are retained. Use Rewards for reward status and association changes. Existing reward redemptions remain protected.</p>
    {!pricingReady && <Notice>Restart the updated Java API, then close and reopen this receipt to prepare regular-price and item-discount editing.</Notice>}
    {error && <Notice>{error} If the revision changed, close and refresh before editing again.</Notice>}
    {!review ? <form onSubmit={e => { e.preventDefault(); void preview() }}><fieldset disabled={!enabled || busy || !pricingReady}>
      <div className="receipt-edit-toolbar"><label>Receipt Type<select value={receiptKind} onChange={e=>{const kind=e.target.value as typeof receiptKind;if(kind==='REWARD_ADJUSTMENT'){if(items.some(i=>Number(i.regularUnitPrice||i.saleUnitPrice)!==0)||payments.length||discounts.length||rrUses.length||Number(tax)!==0||Number(total)!==0){setError('Only a zero-purchase receipt can be converted to a reward-only adjustment.');return}if(!window.confirm('Remove the zero-value placeholder products? Reward records, Fetch and history will remain.'))return;setItems([]);setComplete('COMPLETE');setBasis('BEFORE_PAYMENT_FUNDING')}setReceiptKind(kind)}}><option value="PURCHASE">Purchase</option><option value="REWARD_ADJUSTMENT">Reward Credit / Adjustment — No Purchase</option></select></label><div className="form-actions"><button type="button" aria-label="Top: Cancel edit" className="secondary" onClick={close}>Cancel edit</button><button aria-label="Top: Review updated totals" className="primary">Review updated totals</button></div></div>
      <div className="receipt-fields-row">{field('Store', retailer, setRetailer, true)}{field('Receipt number', number, setNumber)}</div>{field('Store / Address Reference (Optional)',storeReference,setStoreReference)}<div className="receipt-fields-row receipt-fields-three">{field('Purchase date', day, setDay, false, 'date')}{field('Printed time', time, setTime)}
        <label>Purchase channel<select value={channel} onChange={e => setChannel(e.target.value)}>{['IN_STORE','ONLINE','UNKNOWN'].map(v => <option key={v}>{v}</option>)}</select></label></div><div className="receipt-fields-row receipt-fields-three">
        <label>Printed-total basis<select value={basis} onChange={e => setBasis(e.target.value as typeof basis)}>{[['UNKNOWN','Unknown'],['BEFORE_PAYMENT_FUNDING','Before funding'],['AFTER_REWARD_REDEMPTIONS','After reward use']].map(([v,text]) => <option key={v} value={v}>{text}</option>)}</select></label>
      {field('Sales tax', tax, setTax, true)}{field('Receipt total', total, setTotal, true)}
      </div>
      <h4>Products</h4>{items.map((item, i) => <div className="receipt-input-row" key={item.lineItemId}><div className="receipt-fields-row receipt-product-identity">
        {field(`Product ${i + 1}`, item.productName, value => setItems(items.map((r, j) => j === i ? { ...r, productName: value } : r)), true, 'text')}
        {field(`SKU ${i + 1}`, item.productCode, value => setItems(items.map((r, j) => j === i ? { ...r, productCode: value } : r)))}
        {field(`Quantity ${i + 1}`, item.quantity, value => setItems(items.map((r, j) => j === i ? { ...r, quantity: value } : r)), true)}
      </div><div className="receipt-fields-row">        {field(`Regular price ${i + 1}`, item.regularUnitPrice, value => setItems(items.map((r, j) => j === i ? { ...r, regularUnitPrice: value } : r)))}

{field(`Sale price ${i + 1} (optional)`,item.enteredSalePrice,value=>setItems(items.map((r,j)=>j===i?{...r,enteredSalePrice:value}:r)))}
      </div><InfoHelp>Leave sale price blank when its reduction is already listed below. Existing recorded prices and attached discounts are preserved until explicitly corrected.</InfoHelp>
      {discounts.filter(d => d.lineItemIds.length === 1 && d.lineItemIds[0] === item.lineItemId && !d.wholeReceipt && !d.shared).map((d, n) => <ItemDiscountFields key={d.applicationId} name={`Product ${i + 1} discount ${n + 1}`} autoFocus={focusDiscount===d.applicationId} productDescription={item.productName} discount={{ id: d.applicationId, label: d.printedLabel, amount: d.appliedAmount, kind: d.kind, requiredQuantity:d.requiredQuantity }} onChange={value => setDiscounts(discounts.map(row => row.applicationId === d.applicationId ? { ...row, printedLabel: value.label, appliedAmount: value.amount, kind: value.kind, requiredQuantity:value.requiredQuantity } : row))} onRemove={() => setDiscounts(discounts.filter(row => row.applicationId !== d.applicationId))} />)}
      {discounts.filter(d=>!d.wholeReceipt && d.lineItemIds.length===1 && d.lineItemIds[0]===item.lineItemId).map(d=><button key={d.applicationId} type="button" className="text-button" onClick={()=>moveToRR('COUPON_APPLIED',d.applicationId,d.appliedAmount,d.printedLabel)}>Move {d.printedLabel||'product discount'} to RR used</button>)}
      <ReceiptProductTotals api={api} line={{id:item.lineItemId,description:item.productName,sku:item.productCode,regular:item.regularUnitPrice,sale:item.enteredSalePrice.trim()||(!pricingReady||receipt.preservePriceLineIds?.includes(item.lineItemId)?item.saleUnitPrice:undefined),quantity:item.quantity}} discounts={discounts.filter(d=>!d.wholeReceipt&&d.lineItemIds.length===1&&d.lineItemIds[0]===item.lineItemId).map(d=>({id:d.applicationId,label:d.printedLabel,amount:d.appliedAmount,kind:d.kind,requiredQuantity:d.requiredQuantity,quantities:{[item.lineItemId]:item.quantity}}))}/>
      <button type="button" className="secondary" onClick={() => setDiscounts([...discounts, { applicationId: focusedDiscountId(), printedLabel: productDiscountLabel(item.productName, 'SALE_PROMOTION'), appliedAmount: '', kind: 'SALE_PROMOTION', lineItemIds: [item.lineItemId], wholeReceipt: false, shared: false, qualifyingQuantities: {} }])}>Add discount to product {i + 1}</button><button type="button" className="text-button" onClick={() => setItems(items.filter((_, j) => i !== j))}>Remove product {i + 1}</button></div>)}
      <button type="button" className="secondary" onClick={() => {setFocusName(`Product ${items.length+1}`);setItems([...items, { lineItemId: crypto.randomUUID(), productName: '', productCode: '', regularUnitPrice: '', saleUnitPrice: '', enteredSalePrice:'', quantity: '1' }]);}}>Add product</button>
      <h4>Shared or receipt-wide discounts</h4><div className="information-box" role="note">Applies to the selected products, or all products when Whole Receipt is selected. Recalculate after changing prices, quantities or selection. Do not also enter the same sale on a product.</div>{discounts.filter(d => d.wholeReceipt || d.lineItemIds.length !== 1 || d.shared).map((discount, i) => <div className="receipt-input-row" key={discount.applicationId}><ReceiptLineSelector name={`Discount products ${i+1}`} lines={items.map(l=>({id:l.lineItemId,description:l.productName,sku:l.productCode,quantity:l.quantity,price:l.enteredSalePrice.trim()||l.regularUnitPrice||l.saleUnitPrice}))} value={discount.wholeReceipt?{}:Object.fromEntries(discount.lineItemIds.map(id=>[id,String((discount.qualifyingQuantities as Record<string,string|number>)[id]||items.find(l=>l.lineItemId===id)?.quantity||'1')]))} onChange={quantities=>setDiscounts(current=>current.map(d=>d.applicationId===discount.applicationId?{...d,shared:true,wholeReceipt:!Object.keys(quantities).length,lineItemIds:Object.keys(quantities),qualifyingQuantities:quantities}:d))}/><div className="form-grid">
        {field(`Discount label ${i + 1}`, discount.printedLabel, value => setDiscounts(discounts.map(r => r.applicationId === discount.applicationId ? { ...r, printedLabel: value } : r)), !!discount.appliedAmount.trim())}
        <ReceiptDiscountSale api={api} items={items.filter(l=>discount.wholeReceipt||discount.lineItemIds.includes(l.lineItemId)).map(l=>({id:l.lineItemId,name:l.productName,sku:l.productCode,regular:'',sale:l.enteredSalePrice.trim()||l.regularUnitPrice||l.saleUnitPrice,quantity:(discount.qualifyingQuantities as Record<string,string>)[l.lineItemId]||l.quantity}))} onCalculated={(amount,name)=>setDiscounts(current=>current.map(d=>d.applicationId===discount.applicationId?{...d,appliedAmount:amount,kind:'SALE_PROMOTION',printedLabel:d.printedLabel||name}:d))}/>
        {field(`Discount amount ${i + 1}`, discount.appliedAmount, value => setDiscounts(discounts.map(r => r.applicationId === discount.applicationId ? { ...r, appliedAmount: value } : r)), !!discount.printedLabel.trim())}
      </div><button type="button" className="text-button" onClick={() => setDiscounts(discounts.filter(row => row.applicationId !== discount.applicationId))}>Delete</button><button type="button" className="text-button" onClick={()=>moveToRR('COUPON_APPLIED',discount.applicationId,discount.appliedAmount,discount.printedLabel)}>Move discount {i + 1} to RR used</button></div>)}
      <button type="button" className="secondary" onClick={() => {setFocusName(`Discount label ${discounts.filter(d=>d.wholeReceipt || d.lineItemIds.length!==1).length+1}`);setDiscounts([...discounts, { applicationId: focusedDiscountId(), printedLabel: '', appliedAmount: '', kind: 'OTHER', lineItemIds: [], wholeReceipt: true, shared: true, qualifyingQuantities: {} }]);}}>Add receipt-wide discount</button>
      {isWalgreensStore(retailer)&&<details open><summary>Register Rewards used · {rrUses.length} coupon{rrUses.length===1?'':'s'} — Show / Hide</summary><p className="muted">RR used is a checkout deduction, separate from cash/card and manufacturer coupons. Enter each coupon once. Save the receipt, then use the Rewards tab to select and match an available unused RR.</p>
      <label>When moving a discount/payment to RR used<select value={rrTarget} onChange={e=>setRRTarget(e.target.value)}><option value="new">Create a separate RR use</option>{rrUses.map((r,i)=><option key={r.eventId} value={r.eventId}>Already included in RR use {i+1}: {r.amount} — {r.recordedDescription}</option>)}</select></label><p className="muted">Choose an existing use only if the discount/payment is a duplicate record of that same coupon. Amounts must match; review the resulting totals.</p>
      {rrUses.map((r,i)=><div className="receipt-input-row" key={r.eventId}><div className="form-grid">{field(`RR used amount ${i+1}`,r.amount,value=>setRRUses(rrUses.map(row=>row.eventId===r.eventId?{...row,amount:value}:row)),true)}{field(`RR used description ${i+1}`,r.recordedDescription,value=>setRRUses(rrUses.map(row=>row.eventId===r.eventId?{...row,recordedDescription:value}:row)),true)}</div><button type="button" className="text-button" onClick={()=>{setRRUses(rrUses.filter(row=>row.eventId!==r.eventId));if(rrTarget===r.eventId)setRRTarget('new')}}>Remove RR use {i+1}</button></div>)}
      <button type="button" className="secondary" onClick={()=>setRRUses([...rrUses,{eventId:crypto.randomUUID(),amount:'',recordedDescription:'Register Rewards used',notes:''}])}>Add RR used</button></details>}
      {(/^cvs(?:\s|#|$)/i.test(retailer.trim()) || payments.some(p=>p.accountName==='ECB')) && <ECBUseFields rows={payments.filter(p=>p.accountName==='ECB').map(p=>({id:p.paymentId,amount:p.amount,notes:p.notes}))} onChange={rows=>{setComplete('UNKNOWN');setPayments([...payments.filter(p=>p.accountName!=='ECB'),...rows.map(r=>({paymentId:r.id,paymentType:'OTHER',amount:r.amount,notes:r.notes,accountName:'ECB',role:'CHECKOUT_DEDUCTION'}))])}} />}
      <h4>Recorded payments</h4>{payments.map((payment, i) => payment.accountName==='ECB'?null:<div className="receipt-input-row payment-entry" key={payment.paymentId}><div className="form-grid payment-fields">
        <label>Payment type {i + 1}<select value={payment.paymentType==='GIFT_CARD'&&payment.accountName==='Target Gift Card'?'TARGET_GIFT_CARD':payment.paymentType==='OTHER'&&payment.accountName==='Target Circle'?'TARGET_CIRCLE':payment.paymentType} onChange={e => setPayments(payments.map((r, j) => j === i ? { ...r, paymentType:e.target.value==='TARGET_GIFT_CARD'?'GIFT_CARD':e.target.value==='TARGET_CIRCLE'?'OTHER':e.target.value,accountName:e.target.value==='TARGET_GIFT_CARD'?'Target Gift Card':e.target.value==='TARGET_CIRCLE'?'Target Circle':'', role: e.target.value==='OTHER' || e.target.value==='REGISTER_REWARD'?'UNRESOLVED':'FUNDING' } : r))}>{(/^target(?:\s|#|$)/i.test(retailer.trim())?[...new Set(['CASH','CARD','GIFT_CARD','TARGET_GIFT_CARD','TARGET_CIRCLE','OTHER',...(!['CASH','CARD','GIFT_CARD','OTHER'].includes(payment.paymentType)?[payment.paymentType]:[])])]:['CASH','CARD','CREDIT_CARD','DEBIT_CARD','WALGREENS_CASH','GIFT_CARD','TARGET_GIFT_CARD','OTHER','REGISTER_REWARD']).filter(v=>!/^cvs/i.test(retailer)||!['WALGREENS_CASH','REGISTER_REWARD'].includes(v)||v===payment.paymentType).map(v => <option key={v} value={v}>{v==='TARGET_GIFT_CARD'?'Target gift card':v==='TARGET_CIRCLE'?'Target Circle':v==='GIFT_CARD'?'Gift card payment':v==='OTHER'?'Other tender':label(v)}</option>)}</select></label>
        {payment.paymentType==='GIFT_CARD'&&field(`Gift card account ${i+1} (optional)`,payment.accountName,value=>setPayments(payments.map((r,j)=>j===i?{...r,accountName:value}:r)))}
        {payment.paymentType==='OTHER'&&payment.accountName!=='Target Circle'&&<><label>Payment role {i+1}<select value={payment.role} onChange={e=>setPayments(payments.map((r,j)=>j===i?{...r,role:e.target.value}:r))}><option value="UNRESOLVED">Not sure — needs review</option><option value="FUNDING">Payment toward the printed total</option><option value="CHECKOUT_DEDUCTION">Other account deduction before the printed total</option></select></label>{field(`Payment account ${i+1}`,payment.accountName,value=>setPayments(payments.map((r,j)=>j===i?{...r,accountName:value}:r)),payment.role==='CHECKOUT_DEDUCTION')}</>}
        {field(`Payment amount ${i + 1}`, payment.amount, value => setPayments(payments.map((r, j) => j === i ? { ...r, amount: value } : r)), true)}
        {field(`Payment notes ${i + 1} (optional)`, payment.notes, value => setPayments(payments.map((r, j) => j === i ? { ...r, notes: value } : r)), false, 'text', true)}
      </div><button type="button" className="text-button" onClick={() => setPayments(payments.filter((_, j) => i !== j))}>Remove payment {i + 1}</button>{!/^cvs/i.test(retailer)&&(payment.paymentType==='OTHER'||payment.paymentType==='REGISTER_REWARD')&&<button type="button" className="text-button" onClick={()=>moveToRR('PAYMENT',payment.paymentId,payment.amount,payment.notes||'Register Rewards used')}>Move payment {i+1} to RR used</button>}</div>)}
      <button type="button" className="secondary" onClick={() => setPayments([...payments, { paymentId: crypto.randomUUID(), paymentType: 'CARD', amount: '', notes: '', accountName: '', role: 'FUNDING' }])}>Add payment</button>
      {isWalgreensStore(retailer) && <PrintedFooterFields value={footer} onChange={setFooter} />}
      {field('Reason for correction', reason, setReason, true)}
      <FormActions><button type="button" className="secondary" onClick={close}>Cancel edit</button><button className="primary">Review updated totals</button></FormActions>
    </fieldset></form> : <>
      <ReceiptFinancialReview totals={review.totals} cvs={/^cvs/i.test(retailer)} />
      <label className="check"><input type="checkbox" checked={ack} disabled={busy || !!pending} onChange={e => setAck(e.target.checked)} />I reviewed the amounts and acknowledge any recorded differences.</label>
      <FormActions><button className="secondary" disabled={busy} onClick={close}>Close edit</button><button className="secondary" disabled={busy || !!pending} onClick={() => setReview(null)}>Back to editing</button><button className="primary" disabled={!enabled || busy || !ack} onClick={() => void save()}>{pending ? 'Retry same update' : 'Save receipt changes'}</button></FormActions>
    </>}
  </section>
}

export function ReceiptStatusAction({ receipt, api, enabled, onSaved, onCancel }: Props) {
  const restore = receipt.status === 'DELETED'
  const [reason, setReason] = useState(''), [confirmed, setConfirmed] = useState(false), [busy, setBusy] = useState(false), [error, setError] = useState('')
  const [pending, setPending] = useState<{ body: unknown; key: string } | null>(null)
  async function save() {
    setBusy(true); setError('')
    try {
      const request = pending ?? { body: { reason, confirmed }, key: crypto.randomUUID() }; setPending(request)
      await api.mutate(`/receipts/${receipt.transactionId}${restore ? '/restore' : ''}`, restore ? 'POST' : 'DELETE', request.body, request.key, receipt.revision)
      onSaved()
    } catch (e) { setError((e as Error).message) } finally { setBusy(false) }
  }
  return <form className="receipt-editor" onSubmit={e => { e.preventDefault(); void save() }}><h3>{restore ? 'Restore receipt' : 'Delete receipt'}</h3>
    <p>{restore ? 'Restoring includes the receipt in active reports again.' : 'Deleting removes this receipt from active reports. The original contents, evidence, history and reward-redemption reservations are retained.'}</p>
    {error && <Notice>{error} Refresh after a revision conflict.</Notice>}
    <fieldset disabled={!enabled || busy || !!pending}><label>Reason<input required value={reason} onChange={e => setReason(e.target.value)} /></label><label className="check"><input type="checkbox" required checked={confirmed} onChange={e => setConfirmed(e.target.checked)} />I confirm this {restore ? 'restoration' : 'deletion'}.</label></fieldset>
    <FormActions><button type="button" className="secondary" disabled={busy} onClick={onCancel}>Cancel</button><button className="primary" disabled={!enabled || busy || !confirmed}>{pending ? 'Retry same action' : restore ? 'Confirm restore' : 'Confirm delete'}</button></FormActions>
  </form>
}

export function ReceiptRRMatch({receipt,api,enabled,onSaved}:{receipt:Receipt;api:ApiClient;enabled:boolean;onSaved:()=>void}) {
  const [uses,setUses]=useState<RRMatchUse[]>([]),[error,setError]=useState('')
  useEffect(()=>{const controller=new AbortController();api.rrMatches(receipt.transactionId,controller.signal).then(setUses).catch(e=>{if(e.name!=='AbortError')setError(e.message)});return()=>controller.abort()},[api,receipt])
  return <section><h3>Match Register Rewards Used</h3><p className="muted">Record RR used in Edit receipt, save, then select the unused coupon with the same amount. Matching connects the source coupon; it adds no new deduction. If its earning receipt is unavailable, add a source without an earning receipt below. Availability uses this saved receipt’s date and time.</p>{error&&<Notice>{error}</Notice>}{uses.map(use=><MatchRow key={use.eventId} use={use} receipt={receipt} api={api} enabled={enabled} onSaved={onSaved}/>)}{!uses.length&&!error&&<p>No separately recorded RR use. Add it in Edit receipt → Register Rewards used.</p>}</section>
}
function MatchRow({use,receipt,api,enabled,onSaved}:{use:RRMatchUse;receipt:Receipt;api:ApiClient;enabled:boolean;onSaved:()=>void}) {
  const [historyConfirmed,setHistoryConfirmed]=useState(false)
  const [selected,setSelected]=useState(''),[reason,setReason]=useState(''),[error,setError]=useState(''),[busy,setBusy]=useState(false)
  const [pending,setPending]=useState<{body:unknown;key:string}|null>(null)
  async function save(){const source=use.options.find(o=>o.sourceId===selected);if(!source)return;setBusy(true);setError('');const request=pending??{body:{usageEventId:use.eventId,sourceId:source.sourceId,receiptEarning:source.receiptEarning,reason,...(source.historyUnknown?{confirmUnknownHistory:historyConfirmed}:{})},key:crypto.randomUUID()};setPending(request);try{await api.mutate(`/receipts/${receipt.transactionId}/rr-matches`,'POST',request.body,request.key,receipt.revision);onSaved()}catch(e){setError((e as Error).message)}finally{setBusy(false)}}
  return <div className="receipt-input-row"><strong>{use.description} · {money(use.amount)}</strong>{use.matched?<p>{use.needsReview?'Existing RR match needs review after the receipt correction. Its source remains reserved; do not record another deduction.':'Matched to its original reward. See View Reward below.'}</p>:<form onSubmit={e=>{e.preventDefault();void save()}}>{error&&<Notice>{error}</Notice>}<fieldset disabled={!enabled||busy||!!pending}><label>Unused Register Reward<select required value={selected} onChange={e=>{setSelected(e.target.value);setHistoryConfirmed(false)}}><option value="">Select an available RR</option>{use.options.map(o=><option key={o.sourceId} value={o.sourceId}>{money(o.amount)} · {o.promotion} · Earned {o.earnedDate??'not recorded'} · Receipt {o.sourceReceiptNumber??'not recorded'} · Expires {o.expires??'not recorded'}</option>)}</select></label>{!use.options.length&&<p>No eligible unused RR of this amount is recorded. You can add an RR without an earning receipt below; unknown source dates can stay blank.</p>}{use.options.find(o=>o.sourceId===selected)?.historyUnknown&&<label className="check"><input type="checkbox" required checked={historyConfirmed} onChange={e=>setHistoryConfirmed(e.target.checked)}/>I confirm this RR was available for this purchase although its earning date or time is unknown.</label>}<label>Matching note (optional)<input value={reason} onChange={e=>setReason(e.target.value)}/></label></fieldset><FormActions><button className="secondary" disabled={!enabled||busy||!selected||(!pending&&!!use.options.find(o=>o.sourceId===selected)?.historyUnknown&&!historyConfirmed)}>{pending?'Retry Same Match':'Match RR'}</button></FormActions></form>}{!use.matched&&<UnknownRRSource use={use} receipt={receipt} api={api} enabled={enabled&&!busy&&!pending} onSaved={onSaved}/>}</div>
}

function UnknownRRSource({use,receipt,api,enabled,onSaved}:{use:RRMatchUse;receipt:Receipt;api:ApiClient;enabled:boolean;onSaved:()=>void}) {
  const [open,setOpen]=useState(false),[description,setDescription]=useState(use.description||'Unknown source RR'),[earned,setEarned]=useState(''),[expires,setExpires]=useState(''),[reason,setReason]=useState('Earning receipt/source not recorded'),[confirmed,setConfirmed]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState('')
  const [pending,setPending]=useState<{body:unknown;key:string}|null>(null)
  async function save(){setBusy(true);setError('');const request=pending??{key:crypto.randomUUID(),body:{usageEventId:use.eventId,rewardId:crypto.randomUUID(),description:description.trim(),earnedDate:earned||null,expirationDate:expires||null,reason:reason.trim(),confirmRecordedUse:confirmed}};setPending(request);try{await api.mutate(`/receipts/${receipt.transactionId}/rr-matches/unknown-source`,'POST',request.body,request.key,receipt.revision);onSaved()}catch(e){setError((e as Error).message);if((e as {status?:number}).status===400)setPending(null)}finally{setBusy(false)}}
  return <section><button type="button" className="secondary" disabled={!enabled||busy||!!pending} onClick={()=>setOpen(!open)}>{open?'Cancel source entry':'Add RR without an earning receipt'}</button>{open&&<form onSubmit={e=>{e.preventDefault();void save()}}><h4>RR With No Earning Receipt</h4><p>Amount: {money(use.amount)} — already recorded as used. This saves the source coupon and matches it; it does not add another earning or deduction.</p>{error&&<Notice>{error}</Notice>}<fieldset disabled={!enabled||busy||!!pending}><div className="form-grid"><label>RR description<input required value={description} onChange={e=>setDescription(e.target.value)}/></label><label>Earned date (optional)<input type="date" value={earned} onChange={e=>setEarned(e.target.value)}/></label><label>Expiration date (optional)<input type="date" value={expires} onChange={e=>setExpires(e.target.value)}/></label><label>Source note<input required value={reason} onChange={e=>setReason(e.target.value)}/></label></div><p className="muted">Leave unknown dates blank. No earning receipt, purchase or date will be invented.</p><label className="check"><input type="checkbox" required checked={confirmed} onChange={e=>setConfirmed(e.target.checked)}/>I confirm this RR was used on this receipt; its earning receipt or source may be unknown.</label></fieldset><FormActions><button className="primary" disabled={!enabled||busy||(!pending&&!confirmed)}>{pending?'Retry Same Source Entry':'Save RR and match'}</button></FormActions></form>}</section>
}
