import { useCallback, useState } from 'react'
import type { FormEvent } from 'react'
import { Plus, Trash2 } from 'lucide-react'
import { ApiClient, ApiError } from '../../api/client'
import { integer, money } from '../../api/format'
import { Modal, Notice } from '../../components/ui'
import type { Totals, RewardOffer } from '../../types/platform'
import { RewardFields } from './RewardFields'
import { newReward, rewardBody } from './rewardStatus'
import type { RewardDraft } from './rewardStatus'
import { ReceiptLineSelector } from './ReceiptLineSelector'

type Line = { id: string; description: string; sku: string; regular: string; price: string; quantity: string }
type Discount = { id: string; label: string; amount: string; kind: string; quantities: Record<string,string> }
type Tender = { id: string; type: string; amount: string; notes: string }
type Reward = RewardDraft
type Evidence = { evidenceId: string; mediaType: string; contentBase64: string; name: string; size: number }
type Preview = { reviewToken: string; totals: Totals; possibleDuplicates: { transactionId: string }[]; evidence: unknown[] }
const newLine = (): Line => ({ id: crypto.randomUUID(), description: '', sku: '', regular: '', price: '', quantity: '1' })
const nullable = (s: string) => s.trim() || null

/** An unsaved web form. All totals and financial decisions come from the Java preview. */
export function ReceiptCreate({ api, enabled, onClose, onSaved, onReview, offers=[] }: { offers?:RewardOffer[]; api: ApiClient; enabled: boolean; onClose: () => void; onSaved: (id: string) => void; onReview: () => void }) {
  const [id] = useState(() => crypto.randomUUID()), [rrId] = useState(() => crypto.randomUUID())
  const [store, setStore] = useState(''), [number, setNumber] = useState(''), [day, setDay] = useState(''), [time, setTime] = useState(''), [channel, setChannel] = useState('IN_STORE')
  const [tax, setTax] = useState(''), [reported, setReported] = useState(''), [rrUsed, setRrUsed] = useState('')
  const [lines, setLines] = useState<Line[]>(() => [newLine()]), [discounts, setDiscounts] = useState<Discount[]>([]), [payments, setPayments] = useState<Tender[]>([]), [rewards, setRewards] = useState<Reward[]>([]), [evidence, setEvidence] = useState<Evidence[]>([])
  const [error, setError] = useState(''), [busy, setBusy] = useState(false), [ack, setAck] = useState(false)
  const [review, setReview] = useState<{ body: Record<string, unknown>; preview: Preview } | null>(null)
  const [pending, setPending] = useState<{ body: Record<string, unknown>; key: string } | null>(null)
  const close = useCallback(() => {
    if (busy) return
    if (window.confirm(pending ? 'The save outcome may be uncertain. Close and refresh saved receipts before entering it again?' : 'Discard this unsaved receipt?')) { if (pending) onReview(); onClose() }
  }, [busy, pending, onClose, onReview])
  function change<T extends { id: string }>(rows: T[], id: string, field: keyof T, value: T[keyof T], set: (rows: T[]) => void) { set(rows.map(row => row.id === id ? { ...row, [field]: value } : row)) }
  function amount(label: string, value: string, onChange: (s: string) => void, required = true) { return <label>{label}<input aria-label={label} required={required} inputMode="decimal" pattern="[0-9]+([.][0-9]+)?" value={value} onChange={e => onChange(e.target.value)} /></label> }
  function removeLine(id: string) {
    if ([...discounts, ...rewards].some(row => id in row.quantities)) { setError('Reassign or remove linked discounts/rewards before deleting this product.'); return }
    setLines(lines.filter(l => l.id !== id))
  }
  function body(): Record<string, unknown> {
    return { transactionId: id, retailer: store.trim(), receiptNumber: nullable(number), purchaseDate: day, printedTime: nullable(time), purchaseChannel: channel, tax, reportedTotal: reported,
      items: lines.map(l => ({ lineItemId: l.id, description: l.description.trim(), sku: nullable(l.sku), regularUnitPrice: nullable(l.regular), unitPrice: l.price, quantity: integer(l.quantity) })),
      discounts: discounts.map(d => ({ applicationId: d.id, label: d.label.trim(), amount: d.amount, kind: d.kind, lineItemIds: Object.keys(d.quantities), qualifyingQuantities: Object.fromEntries(Object.entries(d.quantities).map(([id,q])=>[id,integer(q)])), wholeReceipt: !Object.keys(d.quantities).length })),
      payments: payments.map(p => ({ paymentId: p.id, type: p.type, amount: p.amount, notes: nullable(p.notes) })), rrUsed: nullable(rrUsed), rrUsageId: rrId,
      earnings: rewards.map(r => rewardBody(r)),
      evidence: [...evidence.map(({ evidenceId, mediaType, contentBase64 }) => ({ evidenceId, mediaType, contentBase64 })),...rewards.flatMap(r=>r.attachment?[r.attachment]:[])] }
  }
  async function preview(event: FormEvent) {
    event.preventDefault(); if (!enabled) return
    setError(''); setBusy(true)
    try { const input = body(); const result = await api.mutate('/receipts/preview', 'POST', input, crypto.randomUUID()) as Preview; setReview({ body: input, preview: result }); setAck(false) }
    catch (e) { setError((e as Error).message) } finally { setBusy(false) }
  }
  async function save() {
    if (!enabled || !review) return
    setError(''); setBusy(true)
    const request = pending || { key: crypto.randomUUID(), body: { ...review.body, reviewToken: review.preview.reviewToken, acknowledgeDifference: ack } }
    setPending(request)
    try { await api.mutate('/receipts', 'POST', request.body, request.key); onSaved(id) }
    catch (e) { setError((e as Error).message); if (e instanceof ApiError && e.status >= 400 && e.status < 500 && e.status !== 409) setPending(null) }
    finally { setBusy(false) }
  }
  async function files(selected: FileList | null) {
    if (!selected) return
    setError(''); setBusy(true)
    try {
      const added = [...selected]
      if (evidence.length + added.length > 4 || [...evidence.map(e => e.size), ...added.map(f => f.size)].reduce((a, b) => a + b, 0) > 10 * 1024 * 1024) throw new Error('Attach up to four files, 10 MiB total.')
      const rows = await Promise.all(added.map(async file => {
        if (file.size > 5 * 1024 * 1024 || !['image/jpeg', 'image/png', 'image/webp', 'application/pdf'].includes(file.type)) throw new Error('Use JPEG, PNG, WebP or PDF, up to 5 MiB each.')
        const content = await new Promise<string>((resolve, reject) => { const reader = new FileReader(); reader.onerror = () => reject(new Error('Could not read the selected attachment.')); reader.onload = () => resolve(String(reader.result).split(',')[1]); reader.readAsDataURL(file) })
        return { evidenceId: crypto.randomUUID(), name: file.name, mediaType: file.type, contentBase64: content, size: file.size }
      }))
      setEvidence([...evidence, ...rows])
    } catch (e) { setError((e as Error).message) } finally { setBusy(false) }
  }
  const difference = review?.preview.totals.difference
  const different = !!difference && !/^-?0+([.]0+)?$/.test(difference)
  return <Modal title={review ? 'Review new receipt' : 'Add receipt'} onClose={close}>
    <p className="muted">This draft is held in memory only. Saving creates one PostgreSQL receipt with audit history.</p>
    {error && <div className="error" role="alert">{error}</div>}
    {!enabled && <Notice>Reconnect with write access before continuing.</Notice>}
    {review ? <>
      <p><strong>{store}</strong> · {day} · {number || 'No printed number'}</p>
      <div className="key-values">{Object.entries({ 'Regular merchandise': review.preview.totals.regularMerchandise, 'Sales tax': review.preview.totals.tax, 'Register Rewards used': review.preview.totals.rrUsed, 'Walgreens Cash used': review.preview.totals.wcUsed, 'Calculated checkout': review.preview.totals.calculatedDue, 'Reported checkout': review.preview.totals.reportedDue, 'Newly earned value': review.preview.totals.savings.newlyEarned, 'Paid after earned benefits': review.preview.totals.savings.paidAfterRebates, 'Total saved': review.preview.totals.savings.totalSaved }).map(([name, value]) => <div key={name}><span>{name}</span><strong>{money(value)}</strong></div>)}</div>
      <div className="table-wrap"><table><thead><tr><th>Product</th><th>Quantity</th><th>Recorded extended total</th></tr></thead><tbody>{lines.map(l=><tr key={l.id}><td>{l.description}</td><td>{l.quantity}</td><td>{money(review.preview.totals.lineTotals?.[l.id]||null)}</td></tr>)}</tbody></table></div>
      {review.preview.totals.qualification?.map(q=><Notice key={q.earningId}>Promotion qualification: {q.review.status} · {q.review.qualifyingQuantity} qualifying units · {money(q.review.qualifyingSpend)} · {q.review.reason}</Notice>)}
      <p>{lines.length} product lines · {discounts.length} deductions · {rewards.length} reward observations · {review.preview.evidence.length} attachments</p>
      {review.preview.totals.savings.issues.map(issue => <Notice key={issue}>{issue}</Notice>)}
      {!number && !time && <Notice>Without a printed number or time, duplicate lookup is limited.</Notice>}
      {review.preview.possibleDuplicates.length > 0 && <Notice>A possible existing receipt was found. Close this draft and review saved receipts before adding another.</Notice>}
      {different && <Notice>Reported checkout differs by {money(difference)}. Both amounts will be preserved.<label className="check"><input type="checkbox" checked={ack} disabled={busy || !!pending} onChange={e => setAck(e.target.checked)} />I verified the printed total and want to preserve this difference.</label></Notice>}
      {pending && !busy && <Notice>Save outcome may be unresolved. Retry sends the exact same request. Close and refresh to reconcile before making another entry.</Notice>}
      <div className="form-actions"><button className="secondary" disabled={busy || !!pending} onClick={() => setReview(null)}>Back to edit</button><button className="primary" disabled={!enabled || busy || review.preview.possibleDuplicates.length > 0 || (different && !ack)} onClick={() => void save()}>{busy ? 'Saving…' : pending ? 'Retry same save' : 'Save receipt'}</button></div>
    </> : <form onSubmit={preview}><fieldset disabled={!enabled || busy}>
      <h3>Receipt details</h3><div className="form-grid"><label>Store<input required maxLength={200} value={store} onChange={e => setStore(e.target.value)} /></label><label>Printed receipt number (optional)<input value={number} onChange={e => setNumber(e.target.value)} /></label><label>Purchase date<input type="date" required value={day} onChange={e => setDay(e.target.value)} /></label><label>Purchase time (optional)<input type="time" value={time} onChange={e => setTime(e.target.value)} /></label><label>Order type<select aria-label="Order type" value={channel} onChange={e => setChannel(e.target.value)}><option value="IN_STORE">In store</option><option value="ONLINE">Online</option><option value="UNKNOWN">Unknown</option></select></label></div>
      <h3>Purchased products</h3><p className="muted">Use the actual sale unit price before additional coupons. Do not enter a sale reduction again if it is already reflected in that price.</p>
      {lines.map((l, i) => <section className="receipt-input-row" key={l.id}><div className="form-grid"><label>Product description {i + 1}<input required value={l.description} onChange={e => change(lines, l.id, 'description', e.target.value, setLines)} /></label><label>SKU / UPC {i + 1} (optional)<input value={l.sku} onChange={e => change(lines, l.id, 'sku', e.target.value, setLines)} /></label><label>Quantity {i + 1}<input required inputMode="numeric" pattern="[0-9]+" value={l.quantity} onChange={e => change(lines, l.id, 'quantity', e.target.value, setLines)} /></label>{amount(`Regular unit price ${i + 1} (optional)`, l.regular, v => change(lines, l.id, 'regular', v, setLines), false)}{amount(`Sale unit price ${i + 1}`, l.price, v => change(lines, l.id, 'price', v, setLines))}</div><button className="text-button" type="button" onClick={() => setLines([...lines,{...l,id:crypto.randomUUID()}])}>Add another line for this product</button><button className="text-button" type="button" disabled={lines.length === 1} onClick={() => removeLine(l.id)}><Trash2 size={14} />Remove product {i + 1}</button></section>)}
      <button className="secondary" type="button" onClick={() => setLines([...lines, newLine()])}><Plus size={15} />Add product</button>
      <h3>Applied discounts</h3><p className="muted">Record actual deduction totals, once each. These are not future clipped coupons.</p>
      {discounts.map((d, i) => <section className="receipt-input-row" key={d.id}><label>Discount label {i + 1}<input required value={d.label} onChange={e => change(discounts, d.id, 'label', e.target.value, setDiscounts)} /></label><div className="form-grid"><label>Discount type<select aria-label={`Discount type ${i + 1}`} value={d.kind} onChange={e => change(discounts, d.id, 'kind', e.target.value, setDiscounts)}><option value="MANUFACTURER">Manufacturer coupon</option><option value="STORE_COUPON">Store / IVC / online coupon</option><option value="SALE_PROMOTION">Additional sale/promotion reduction</option></select></label>{amount(`Discount amount ${i + 1}`, d.amount, v => change(discounts, d.id, 'amount', v, setDiscounts))}<ReceiptLineSelector lines={lines} value={d.quantities} onChange={v => change(discounts,d.id,'quantities',v,setDiscounts)} name={`Discount products ${i+1}`} /></div><button className="text-button" type="button" onClick={() => setDiscounts(discounts.filter(row => row.id !== d.id))}>Remove discount {i + 1}</button></section>)}
      <button className="secondary" type="button" onClick={() => setDiscounts([...discounts, { id: crypto.randomUUID(), label: '', amount: '', kind: 'MANUFACTURER', quantities: {} }])}><Plus size={15} />Add discount</button>
      <h3>Tax, rewards used, and payment</h3><div className="form-grid">{amount('Sales tax', tax, setTax)}{amount('Reported total due at register', reported, setReported)}{amount('Register Rewards used (optional)', rrUsed, setRrUsed, false)}</div><p className="muted">RR use is a separate deduction; this form does not match its original coupon. Add Walgreens Cash used as a payment below. For zero due, no cash/card payment is required. If payment is unknown, leave it absent; savings will be marked partial.</p>
      {payments.map((p, i) => <section className="receipt-input-row" key={p.id}><div className="form-grid"><label>Payment type<select aria-label={`Payment type ${i + 1}`} value={p.type} onChange={e => change(payments, p.id, 'type', e.target.value, setPayments)}><option value="CASH">Cash</option><option value="CARD">Card</option><option value="GIFT_CARD">Gift card payment</option><option value="OTHER">Other tender</option><option value="WALGREENS_CASH">Walgreens Cash used</option></select></label>{amount(`Payment amount ${i + 1}`, p.amount, v => change(payments, p.id, 'amount', v, setPayments))}<label>Payment notes {i + 1} (optional)<input value={p.notes} onChange={e => change(payments, p.id, 'notes', e.target.value, setPayments)} /></label></div><button className="text-button" type="button" onClick={() => setPayments(payments.filter(row => row.id !== p.id))}>Remove payment {i + 1}</button></section>)}
      <button className="secondary" type="button" onClick={() => setPayments([...payments, { id: crypto.randomUUID(), type: 'CASH', amount: '', notes: '' }])}><Plus size={15} />Add payment</button>
      <h3>Rewards earned or expected</h3>
      {rewards.map((r,i) => <section className="receipt-input-row" key={r.id}><RewardFields onFileBusy={setBusy} offers={offers} value={r} onChange={next => setRewards(rewards.map(old => old.id===r.id?next:old))} lines={lines.map(l=>({...l,price:l.price}))} index={i+1} /><button className="text-button" type="button" onClick={()=>setRewards(rewards.filter(row=>row.id!==r.id))}>Remove reward {i+1}</button></section>)}
      <button className="secondary" type="button" onClick={()=>setRewards([...rewards,newReward()])}><Plus size={15} />Add rebate or reward</button>
      <h3>Original evidence (optional)</h3><p className="muted">Attach up to four JPEG, PNG, WebP or PDF files (5 MiB each, 10 MiB total). This form does not run OCR.</p><label>Receipt files<input type="file" multiple accept="image/jpeg,image/png,image/webp,application/pdf" onChange={e => { void files(e.target.files); e.target.value = '' }} /></label>{evidence.map(file => <div className="detail-row" key={file.evidenceId}>{file.name}<button type="button" className="text-button" onClick={() => setEvidence(evidence.filter(row => row.evidenceId !== file.evidenceId))}>Remove attachment</button></div>)}
      <div className="form-actions"><button className="primary" type="submit">{busy ? 'Calculating…' : 'Review calculated totals'}</button></div>
    </fieldset></form>}
  </Modal>
}
