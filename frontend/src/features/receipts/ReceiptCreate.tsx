import { AutoSizeTextArea } from '../../components/ui'
import { useCallback, useState, useRef } from 'react'
import type { FormEvent } from 'react'
import { Plus, Trash2 } from 'lucide-react'
import { ApiClient, ApiError } from '../../api/client'
import { integer, money, titleWords, productDiscountLabel, isWalgreensStore } from '../../api/format'
import { Modal, Notice } from '../../components/ui'
import type { Totals, RewardOffer, PrintedTotalBasis, PaymentEntryStatus, Receipt, ProductLookup } from '../../types/platform'
import { RewardFields } from './RewardFields'
import { newReward, rewardBody, footerDraft, footerBody, footerLabels } from './rewardStatus'
import type { RewardDraft, FooterDraft } from './rewardStatus'
import { ReceiptLineSelector } from './ReceiptLineSelector'

type Line = { id: string; description: string; sku: string; regular: string; quantity: string }
type Discount = { clippedCouponId?:string; requiredQuantity?: string | null; shared?: boolean; id: string; label: string; amount: string; kind: string; quantities: Record<string,string> }
type Tender = { id: string; type: string; amount: string; notes: string; accountName?: string; role?: string }
type Reward = RewardDraft
type Evidence = { evidenceId: string; mediaType: string; contentBase64: string; name: string; size: number }
type Preview = { reviewToken: string; totals: Totals; possibleDuplicates: { transactionId: string }[]; evidence: unknown[] }
const newLine = (): Line => ({ id: crypto.randomUUID(), description: '', sku: '', regular: '', quantity: '1' })
const nullable = (s: string) => s.trim() || null

/** Shared receipt evidence/projection display; all arithmetic comes from Java. */
export function ReceiptFinancialReview({ totals, cvs=false }: { totals: Totals; cvs?:boolean }) {
  const review = totals.paymentReview
  const savings = totals.savings
  return <>
    <div className="key-values">{Object.entries({
      'Regular merchandise': totals.regularMerchandise,
      'Merchandise before listed discounts': totals.saleMerchandise,
      'Sale/promotion reductions': totals.discountBreakdown?.salePromotions ?? null,
      'Manufacturer coupons': totals.discountBreakdown?.manufacturerCoupons ?? null,
      'Store discounts / coupons': totals.discountBreakdown?.storeDiscounts ?? null,
      'Unclassified discounts': totals.discountBreakdown?.unclassified ?? null,
      'Total listed discounts (sum above)': totals.coupons,
      'Sales tax': totals.tax,
      [review?.printedTotalBasis === 'BEFORE_PAYMENT_FUNDING' ? 'Printed purchase total' : review?.printedTotalBasis === 'AFTER_REWARD_REDEMPTIONS' ? 'Printed remaining total after redemptions' : 'Recorded printed total (basis unknown)']: review?.printedTotal ?? totals.reportedDue,
      ...(!cvs ? {'Walgreens Cash used': totals.wcUsed, 'Register Rewards used': totals.rrUsed} : {}),
      'Recorded gift-card funding': review?.giftCardFunding ?? null,
    }).map(([name, value]) => <div key={name}><span>{name}</span><strong>{money(value)}</strong></div>)}</div>
    {!totals.discountBreakdown && <Notice>Restart the updated Java API to load the discount breakdown. The combined discount total remains recorded above.</Notice>}
    <h3>Actual out of pocket</h3>
    <div className="detail-row"><span>Actual cash/card paid</span><strong>{money(review?.recordedCashCardPaid)}</strong></div>
    <p className="muted">From recorded cash and card payments. Excludes redeemed rewards, gift cards and newly earned benefits.</p>
    {review ? <>
      <p>Printed-total basis: {review.printedTotalBasis === 'BEFORE_PAYMENT_FUNDING' ? 'Before payment funding' : review.printedTotalBasis === 'AFTER_REWARD_REDEMPTIONS' ? 'After reward redemptions' : 'Unknown — legacy meaning preserved'}</p>
      <p>All receipt payments entered: {review.paymentEntryStatus === 'COMPLETE' ? 'Yes' : review.paymentEntryStatus === 'PARTIAL' ? 'Not finished' : 'Not sure'}. Reconciliation: {review.status.toLowerCase().replaceAll('_', ' ')}.</p>
      <p>{review.reason}</p>
      {review.unresolvedPaymentIds.length > 0 && <Notice>Some payment entries need a role. In Edit receipt, classify Other as funding or an account deduction; move any RR payment into Register Rewards used. Review the receipt total basis and confirm all payments are entered.</Notice>}
    </> : <Notice>Payment completeness and printed-total basis were not supplied by this server.</Notice>}
    <h3>Benefits — separate from payment</h3>
    <div className="detail-row"><span>Known confirmed dollar-benefit subtotal</span><strong>{money(savings.knownConfirmedBenefitValue ?? savings.newlyEarned)}</strong></div>
    {savings.confirmedBenefitsComplete === false && <Notice>Combined benefit value is incomplete. Native points remain recorded; no USD conversion is invented.</Notice>}
    {totals.benefitObservations?.map(e => <p key={e.earningId}>{e.provider} — {e.status}: {e.confirmed ? `recorded confirmed amount ${e.confirmed.amount} ${e.confirmed.kind === 'POINTS' ? 'points' : e.confirmed.currency}` : `expected ${e.expected?.amount ?? 'unknown'} ${e.expected?.kind === 'POINTS' ? 'points' : e.expected?.currency ?? ''}`}
      {(e.confirmed || e.expected)?.kind === 'POINTS' && (e.valuation ? `; saved USD valuation ${money(e.valuation.amount)} (${e.valuation.basis})` : '; USD valuation not recorded')}
    </p>)}
    <div className="detail-row"><span>Total Paid after rebates</span><strong>{savings.paidAfterRebates == null ? 'Cannot calculate yet' : money(savings.paidAfterRebates)}</strong></div>
    <p className="muted">Cash/card paid minus newly earned reward and rebate value, using the same calculation as the CLI and your spreadsheet. Includes saved dollar valuations of points. Rewards used are not subtracted again; Actual OOP remains the cash/card amount paid.</p>
    {savings.paidAfterRebates == null && <Notice>{savings.recordedCashCardComplete === false ? 'Cannot calculate yet: payment amounts are missing. Record the actual tenders, or a genuine zero checkout.' : savings.confirmedBenefitsComplete === false ? 'Cannot calculate yet: a confirmed benefit is missing its dollar valuation. Keep native points recorded and add their saved valuation when known.' : 'Cannot calculate yet: recorded payment amounts or benefit valuations are missing.'}</Notice>}
    <h3>Total saved</h3>
    <div className="detail-row"><span>Total saved — including confirmed benefits</span><strong>{money(savings.totalSaved)}</strong></div>
    <p className="muted">Regular merchandise plus recorded tax, minus cash/card paid, plus confirmed valued benefits. Separate from actual out of pocket; rewards used are not added again as earnings.</p>
    {savings.totalSaved == null && <Notice>Savings inputs are incomplete. Review regular prices, payment information and benefit valuations.</Notice>}
    <details><summary>Legacy calculations — original meanings preserved</summary>
      {totals.difference && !/^-?0+(\.0+)?$/.test(totals.difference) && <p className="muted">The legacy reported/calculated difference of {money(totals.difference)} compares original checkout figures. It is separate from the purchase-total and payment reconciliation above.</p>}
      <div className="key-values">{Object.entries({ 'Legacy calculated checkout': totals.calculatedDue, 'Recorded printed total': totals.reportedDue,
        'Legacy reported/calculated difference': totals.difference, 'Legacy paid after benefits': savings.paidAfterRebates, 'Legacy total saved': savings.totalSaved,
      }).map(([name, value]) => <div key={name}><span>{name}</span><strong>{money(value)}</strong></div>)}</div>
    </details>
  </>
}

/** An unsaved web form. All totals and financial decisions come from the Java preview. */
export function ReceiptCreate({ api, enabled, onClose, onSaved, onReview, offers=[], products=[], coupons=[] }: { coupons?: import('../../types/platform').Coupon[]; products?:ProductLookup[]; offers?:RewardOffer[]; api: ApiClient; enabled: boolean; onClose: () => void; onSaved: (id: string) => void; onReview: () => void }) {
  const [id] = useState(() => crypto.randomUUID())
  const [focusId, setFocusId] = useState('')
  function focusedId() { const id=crypto.randomUUID(); setFocusId(id); return id }
  function focusedLine() { const line=newLine(); setFocusId(line.id); return line }
  const [store, setStore] = useState(''), [number, setNumber] = useState(''), [day, setDay] = useState(''), [time, setTime] = useState(''), [channel, setChannel] = useState('IN_STORE')
  const [printedTotalBasis, setPrintedTotalBasis] = useState<PrintedTotalBasis>('UNKNOWN')
  const [paymentEntryStatus, setPaymentEntryStatus] = useState<PaymentEntryStatus>('UNKNOWN')
  const [footer, setFooter] = useState(() => footerDraft())
  const [tax, setTax] = useState(''), [reported, setReported] = useState('')
  const [rrUses, setRRUses] = useState<{ eventId: string; amount: string; recordedDescription: string }[]>([])
  const [lines, setLines] = useState<Line[]>(() => [newLine()]), [discounts, setDiscounts] = useState<Discount[]>([]), [payments, setPayments] = useState<Tender[]>([]), [rewards, setRewards] = useState<Reward[]>([]), [evidence, setEvidence] = useState<Evidence[]>([])
  const [error, setError] = useState(''), [busy, setBusy] = useState(false), [ack, setAck] = useState(false)
  const formRef = useRef<HTMLFormElement>(null)
  const [review, setReview] = useState<{ body: Record<string, unknown>; preview: Preview } | null>(null)
  const [pending, setPending] = useState<{ body: Record<string, unknown>; key: string } | null>(null)
  const close = useCallback(() => {
    if (busy) return
    if (window.confirm(pending ? 'The save outcome may be uncertain. Close and refresh saved receipts before entering it again?' : 'Discard this unsaved receipt?')) { if (pending) onReview(); onClose() }
  }, [busy, pending, onClose, onReview])
  function change<T extends { id: string }>(rows: T[], id: string, field: keyof T, value: T[keyof T], set: (rows: T[]) => void) { setPaymentEntryStatus('UNKNOWN'); set(rows.map(row => row.id === id ? { ...row, [field]: value } : row)) }
  function amount(label: string, value: string, onChange: (s: string) => void, required = true) { return <label>{label}<input aria-label={label} required={required} inputMode="decimal" pattern="([0-9]+([.][0-9]+)?|[.][0-9]+)" value={value} onChange={e => onChange(e.target.value)} /></label> }
  function removeLine(id: string) {
    if ([...discounts, ...rewards].some(row => id in row.quantities)) { setError('Reassign or remove linked discounts/rewards before deleting this product.'); return }
    setLines(lines.filter(l => l.id !== id))
  }
  function couponSelector(name:string,line?:Line) {
    return <label>{name}<select aria-label={name} value="" onChange={e=>{const c=coupons.find(c=>c.couponId===e.target.value);if(!c)return;setDiscounts([...discounts,{id:c.couponId,clippedCouponId:c.couponId,label:c.description,amount:c.terms?.discount==='PERCENT'?'':c.discountAmount??'',kind:c.couponType==='MANUFACTURER'?'MANUFACTURER':'STORE_COUPON',requiredQuantity:c.requiredQuantity==null?null:String(c.requiredQuantity),shared:!line,quantities:line?{[line.id]:line.quantity}:{}}]);setPaymentEntryStatus('UNKNOWN')}}><option value="">Choose a saved coupon (optional)</option>{coupons.filter(c=>c.status==='CLIPPED'&&!discounts.some(d=>d.clippedCouponId===c.couponId)&&(!day||!c.expirationDate||c.expirationDate>=day)).map(c=><option key={c.couponId} value={c.couponId}>{c.provider} · {c.description} · {c.terms?.discount==='PERCENT'?`${c.discountAmount}%`:money(c.discountAmount)}</option>)}</select><small>Selection adds one editable discount. Do not enter it again manually. Saving the receipt marks this coupon used; preview does not. For percentage coupons, enter the actual deduction printed on the receipt.</small></label>
  }
  function body(): Record<string, unknown> {
    return { clippedCouponIds: discounts.flatMap(d=>d.clippedCouponId?[d.clippedCouponId]:[]), printedFooter: isWalgreensStore(store) ? footerBody(footer) : undefined, transactionId: id, retailer: store.trim(), receiptNumber: nullable(number), purchaseDate: day, printedTime: nullable(time), purchaseChannel: channel, printedTotalBasis, paymentEntryStatus, tax, reportedTotal: reported,
      items: lines.map(l => ({ lineItemId: l.id, description: l.description.trim(), sku: nullable(l.sku), regularUnitPrice: nullable(l.regular), unitPrice: l.regular, quantity: integer(l.quantity) })),
      discounts: discounts.filter(d => d.label.trim() || d.amount.trim()).map(d => ({ applicationId: d.id, label: d.label.trim(), amount: d.amount, kind: d.kind, requiredQuantity: d.requiredQuantity ? integer(d.requiredQuantity) : null, lineItemIds: Object.keys(d.quantities), qualifyingQuantities: Object.fromEntries(Object.entries(d.quantities).map(([id,q])=>[id,integer(q)])), wholeReceipt: !Object.keys(d.quantities).length })),
      payments: payments.map(p => ({ paymentId: p.id, type: p.type, amount: p.amount, notes: nullable(p.notes), ...(p.accountName ? {accountName:p.accountName} : {}), ...(p.type === 'OTHER' ? { role: p.role || 'UNRESOLVED' } : {}) })), rrUses: rrUses.map(r => ({ ...r, recordedDescription: r.recordedDescription.trim() || 'Register Rewards used' })),
      earnings: rewards.map(r => rewardBody(r)),
      evidence: [...evidence.map(({ evidenceId, mediaType, contentBase64 }) => ({ evidenceId, mediaType, contentBase64 })),...rewards.flatMap(r=>r.attachment?[r.attachment]:[])] }
  }
  async function preview(event: FormEvent) {
    event.preventDefault(); if (!enabled) return
    if (formRef.current && !formRef.current.checkValidity()) {
      const first = Array.from(formRef.current.elements).find((node): node is HTMLInputElement => ['INPUT', 'SELECT', 'TEXTAREA'].includes(node.tagName) && !(node as HTMLInputElement).validity.valid)
      for (let parent = first?.parentElement; parent; parent = parent.parentElement) { if (parent.tagName === 'DETAILS') parent.setAttribute('open', '') }
      first?.scrollIntoView?.({ block: 'center', behavior: 'smooth' }); first?.focus()
      return
    }
    setError(''); setBusy(true)
    try { const input = body(); const result = await api.mutate('/receipts/preview', 'POST', input, crypto.randomUUID()) as Preview; setReview({ body: input, preview: result }); setAck(false) }
    catch (e) { const message = (e as Error).message; setError(message === 'Unknown request field.' ? 'The running API does not recognize this receipt form. Restart the updated Java API, then select Review entered breakdown again. Keep this draft open; do not refresh.' : message) } finally { setBusy(false) }
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
  const different = review?.preview.totals.paymentReview?.acknowledgementRequired ?? (!!difference && !/^-?0+([.]0+)?$/.test(difference))
  return <Modal title={review ? 'Review new receipt' : 'Add receipt'} onClose={close}>
    <p className="muted">Your receipt is not saved yet. Review the breakdown, then select Save receipt. Closing this draft discards unsaved entries.</p>
    {error && <div className="error" role="alert">{error}</div>}
    {!enabled && <Notice>Reconnect with write access before continuing.</Notice>}
    {review ? <>
      <p><strong>{store}</strong> · {day} · {number || 'No printed number'}</p>
      <ReceiptFinancialReview totals={review.preview.totals} cvs={/^cvs/i.test(store.trim())} />
      <div className="table-wrap"><table><thead><tr><th>Product</th><th>Regular price</th><th>Quantity</th><th>Regular extended total</th><th>Attached discounts</th></tr></thead><tbody>{lines.map(l=><tr key={l.id}><td>{l.description}</td><td>{money(l.regular)}</td><td>{l.quantity}</td><td>{money(review.preview.totals.lineTotals?.[l.id]||null)}</td><td>{discounts.filter(d => !d.shared && Object.keys(d.quantities).length === 1 && l.id in d.quantities).map(d => <p key={d.id}>{d.label} · {money(d.amount)}</p>)}</td></tr>)}</tbody></table></div>
      {payments.some(p=>p.accountName==='ECB') && <><h3>ExtraBucks (ECB) used — Whole receipt</h3>{payments.filter(p=>p.accountName==='ECB').map(p=><p key={p.id}>{p.notes || 'ECB used'} · {money(p.amount)}</p>)}</>}
      {(!/^cvs/i.test(store.trim())||rrUses.length>0)&&<h3>Register Rewards used breakdown</h3>}{rrUses.map((r, i) => <p key={r.eventId}>RR {i+1} · {r.recordedDescription || 'Register Rewards used'} · {money(r.amount)}</p>)}
      <h3>Shared or receipt-wide discount breakdown</h3>
      {discounts.filter(d => (d.shared || Object.keys(d.quantities).length !== 1)).map(d => <p key={d.id}>{d.label} · {money(d.amount)} · {Object.keys(d.quantities).length ? lines.filter(l => l.id in d.quantities).map(l => l.description).join(', ') : 'Whole receipt'}</p>)}
      {review.preview.totals.qualification?.map(q=><Notice key={q.earningId}>{q.review.status === 'CANNOT_VERIFY' ? `Promotion review: ${q.review.reason} This check does not prevent recording the reward or valuing Fetch points.` : `Promotion qualification: ${q.review.status} · ${q.review.qualifyingQuantity} qualifying units · ${money(q.review.qualifyingSpend)} · ${q.review.reason}`} </Notice>)}
      <p>{lines.length} product lines · {discounts.length} deductions · {rewards.length} reward observations · {review.preview.evidence.length} attachments</p>
      {review.preview.totals.savings.issues.map(issue => <Notice key={issue}>{issue}</Notice>)}
      {!number && !time && <Notice>Without a printed number or time, duplicate lookup is limited.</Notice>}
      {review.preview.possibleDuplicates.length > 0 && <Notice>A possible existing receipt was found. Close this draft and review saved receipts before adding another.</Notice>}
      {different && <Notice>A recorded purchase/payment difference requires review. Printed values will be preserved.<label className="check"><input type="checkbox" checked={ack} disabled={busy || !!pending} onChange={e => setAck(e.target.checked)} />I reviewed the receipt evidence and want to preserve the reported difference.</label></Notice>}
      {pending && !busy && <Notice>Save outcome may be unresolved. Retry sends the exact same request. Close and refresh to reconcile before making another entry.</Notice>}
      <div className="form-actions"><button className="secondary" disabled={busy || !!pending} onClick={() => setReview(null)}>Back to edit</button><button className="primary" disabled={!enabled || busy || review.preview.possibleDuplicates.length > 0 || (different && !ack)} onClick={() => void save()}>{busy ? 'Saving…' : pending ? 'Retry same save' : 'Save receipt'}</button></div>
    </> : <form noValidate ref={formRef} onInvalidCapture={event => {
      event.preventDefault()
      const field = event.target as HTMLInputElement
      field.setAttribute('aria-invalid', 'true')
      const first = Array.from(formRef.current?.elements ?? []).find((node): node is HTMLInputElement => ['INPUT', 'SELECT', 'TEXTAREA'].includes(node.tagName) && !(node as HTMLInputElement).validity.valid)
      if (first === field) {
        setError(`${field.labels?.[0]?.textContent || field.getAttribute('aria-label') || 'Required field'}: ${field.validationMessage}`)
        for (let parent = field.parentElement; parent; parent = parent.parentElement) { if (parent.tagName === 'DETAILS') parent.setAttribute('open', '') }
        field.scrollIntoView?.({ block: 'center', behavior: 'smooth' }); field.focus()
      }
    }} onInputCapture={event => { const field = event.target as HTMLInputElement; if (field.validity?.valid) field.removeAttribute('aria-invalid') }} onSubmit={preview}><button type="submit" className="secondary">Review entered breakdown</button><fieldset disabled={!enabled || busy}>
      <details open><summary>Receipt details — Show / Hide</summary><div className="form-grid"><label>Store<input required maxLength={200} value={store} onChange={e => setStore(e.target.value)} /></label><label>Printed receipt number (optional)<input value={number} onChange={e => setNumber(e.target.value)} /></label><label>Purchase date<input type="date" required value={day} onChange={e => setDay(e.target.value)} /></label><label>Purchase time (optional)<input type="time" value={time} onChange={e => setTime(e.target.value)} /></label><label>Order type<select aria-label="Order type" value={channel} onChange={e => setChannel(e.target.value)}><option value="IN_STORE">In store</option><option value="ONLINE">Online</option><option value="UNKNOWN">Unknown</option></select></label></div>
      </details><details open><summary>Purchased products — Show / Hide</summary>
      {lines.map((l, i) => <details className="receipt-input-row" key={l.id} open><summary>Product {i + 1} · {l.description.trim() || 'New product'} · Quantity {l.quantity || 'not entered'} · Regular price {money(l.regular || null)} — Show / Hide</summary><div className="form-grid"><label className="field-full">Product description {i + 1}<AutoSizeTextArea aria-label={`Product description ${i + 1}`} autoFocus={focusId===l.id} required value={l.description} onBlur={() => change(lines, l.id, 'description', titleWords(l.description), setLines)} onChange={e => change(lines, l.id, 'description', e.target.value, setLines)} /></label><label>SKU / UPC {i + 1} (optional)<input value={l.sku} onChange={e => change(lines, l.id, 'sku', e.target.value, setLines)} /></label>{l.sku.trim() && <div className="field-full"><strong>Previously purchased products</strong>{products.filter(p=>p.sku.toLowerCase().startsWith(l.sku.trim().toLowerCase())).slice(0,8).map((p,n)=><div className="receipt-input-row" key={`${p.sku}-${p.description}-${n}`}><span>{p.description} · SKU {p.sku} · Last regular price {money(p.regularPrice)}</span><small>Last purchased {p.purchaseDate || 'date not recorded'} · Receipt {p.receiptNumber || 'not recorded'}</small><button type="button" className="secondary" onClick={()=>{setPaymentEntryStatus('UNKNOWN');setLines(lines.map(row=>row.id===l.id?{...row,description:p.description,sku:p.sku,regular:p.regularPrice??'',quantity:'1'}:row))}}>Use this product: {p.description}</button></div>)}{!products.some(p=>p.sku.toLowerCase().startsWith(l.sku.trim().toLowerCase()))&&<p className="muted">No previous product with this SKU. Enter its details below.</p>}<small>Previous prices are a reference. Review price and quantity; enter this purchase’s discounts separately.</small></div>}<label>Quantity {i + 1}<input required inputMode="numeric" pattern="[0-9]+" value={l.quantity} onChange={e => change(lines, l.id, 'quantity', e.target.value, setLines)} /></label>{amount(`Regular price ${i + 1}`, l.regular, v => change(lines, l.id, 'regular', v, setLines))}</div><p className="muted product-calculation-note">Regular price × quantity, minus this product’s discounts.<br />Enter each promotion once.</p>
      {couponSelector(`Use a clipped coupon for product ${i+1}`,l)}
      {discounts.filter(d => !d.shared && Object.keys(d.quantities).length === 1 && l.id in d.quantities).map((d, n) => <ItemDiscountFields key={d.id} name={`Product ${i + 1} discount ${n + 1}`} autoFocus={focusId===d.id} productDescription={l.description} discount={d} onChange={value => { setPaymentEntryStatus('UNKNOWN'); setDiscounts(discounts.map(row => row.id === d.id ? { ...row, ...value } : row)) }} onRemove={() => setDiscounts(discounts.filter(row => row.id !== d.id))} />)}
      <div className="product-entry-actions"><button type="button" className="secondary" onClick={() => setDiscounts([...discounts, { id: focusedId(), label: productDiscountLabel(l.description, 'SALE_PROMOTION'), amount: '', kind: 'SALE_PROMOTION', quantities: { [l.id]: l.quantity } }])}>Add discount to product {i + 1}</button><button className="secondary" type="button" onClick={() => setLines([...lines,{...l,id:focusedId()}])}>Add another line for this product</button><button className="secondary product-remove-action" type="button" disabled={lines.length === 1} onClick={() => removeLine(l.id)}><Trash2 size={14} />Remove product {i + 1}</button></div></details>)}
      <button className="secondary" type="button" onClick={() => setLines([...lines, focusedLine()])}><Plus size={15} />Add product</button>
      </details><details open><summary>Shared or receipt-wide discounts — Show / Hide</summary>{couponSelector('Use a clipped coupon across products or the whole receipt')}<p className="muted">These amounts are subtracted from the product prices above. A sale/promotion reduction is the total additional deduction for the selected products and quantities, not a final unit price. Enter each reduction once; do not repeat one already included in a product price.</p>
      {discounts.filter(d => (d.shared || Object.keys(d.quantities).length !== 1)).map((d, i) => <section className="receipt-input-row" key={d.id}><label>Discount label {i + 1}<input required={!!d.amount.trim()} autoFocus={focusId===d.id} value={d.label} onChange={e => change(discounts, d.id, 'label', e.target.value, setDiscounts)} /></label><div className="form-grid"><label>Discount type<select aria-label={`Discount type ${i + 1}`} value={d.kind} onChange={e => change(discounts, d.id, 'kind', e.target.value, setDiscounts)}><option value="MANUFACTURER">Manufacturer coupon</option><option value="STORE_COUPON">Store / IVC / online coupon</option><option value="SALE_PROMOTION">Sale/promotion reduction from prices above</option></select></label>{amount(`Discount amount ${i + 1}`, d.amount, v => change(discounts, d.id, 'amount', v, setDiscounts), !!d.label.trim())}<ReceiptLineSelector lines={lines.map(l => ({ ...l, price: l.regular }))} value={d.quantities} onChange={v => change(discounts,d.id,'quantities',v,setDiscounts)} name={`Discount products ${i+1}`} /></div><button className="text-button" type="button" onClick={() => setDiscounts(discounts.filter(row => row.id !== d.id))}>Delete receipt-wide discount {i + 1}</button></section>)}
      <button className="secondary" type="button" onClick={() => setDiscounts([...discounts, { id: focusedId(), shared: true, label: '', amount: '', kind: 'MANUFACTURER', quantities: {} }])}><Plus size={15} />Add shared or receipt-wide discount</button>
      </details><details open><summary>Tax, rewards used, and payment — Show / Hide</summary><div className="form-grid">{amount('Sales tax', tax, setTax)}{amount('Reported total due at register', reported, setReported)}</div>
      {(!/^cvs/i.test(store.trim())||rrUses.length>0)&&<details open><summary>Register Rewards used · {rrUses.length} coupon{rrUses.length===1?'':'s'} — Show / Hide</summary><p className="muted">Enter each coupon separately, even when two have the same value. For $5, $5 and $4, add three rows. Each use can be matched to its original reward after saving. Do not repeat these amounts as discounts or payments.</p>
      {rrUses.map((r, i) => <section className="receipt-input-row" key={r.eventId}><div className="form-grid">{amount(`RR used amount ${i+1}`, r.amount, value => { setPaymentEntryStatus('UNKNOWN'); setRRUses(rrUses.map(row => row.eventId === r.eventId ? { ...row, amount: value } : row)) })}<label>RR used description {i+1} (optional)<input value={r.recordedDescription} onChange={e => setRRUses(rrUses.map(row => row.eventId === r.eventId ? { ...row, recordedDescription: e.target.value } : row))} /></label></div><button type="button" className="text-button" onClick={() => { setPaymentEntryStatus('UNKNOWN'); setRRUses(rrUses.filter(row => row.eventId !== r.eventId)) }}>Remove RR use {i+1}</button></section>)}
      <button type="button" className="secondary" disabled={rrUses.length >= 100} onClick={() => { setPaymentEntryStatus('UNKNOWN'); setRRUses([...rrUses, { eventId: crypto.randomUUID(), amount: '', recordedDescription: '' }]) }}>Add RR used</button></details>}
      <p className="muted">Enter the printed total exactly. Before funding means before reward and other payment funding; After reward use means the remaining total after reward redemptions. Choose Unknown if the printed meaning is unclear.</p>
      <label>Printed total represents<select value={printedTotalBasis} onChange={e => setPrintedTotalBasis(e.target.value as PrintedTotalBasis)}>
        <option value="UNKNOWN">Unknown</option>
        <option value="BEFORE_PAYMENT_FUNDING">Before funding</option>
        <option value="AFTER_REWARD_REDEMPTIONS">Remaining total after reward redemptions</option>
      </select></label>
      <label>Have you entered all payments shown on this receipt?<select value={paymentEntryStatus} onChange={e => setPaymentEntryStatus(e.target.value as PaymentEntryStatus)}>
        <option value="UNKNOWN">Not sure</option><option value="PARTIAL">Not finished</option>
        <option value="COMPLETE">Yes</option>
      </select></label><p className="muted">Record rewards used once in their own section.  For zero due, no cash/card payment is required. If payment is unknown, leave it absent; savings will be marked partial.</p>
      {(/^cvs(?:\s|#|$)/i.test(store.trim()) || payments.some(p=>p.accountName==='ECB')) && <ECBUseFields rows={payments.filter(p=>p.accountName==='ECB')} onChange={rows=>{setPaymentEntryStatus('UNKNOWN');setPayments([...payments.filter(p=>p.accountName!=='ECB'),...rows.map(r=>({...r,type:'OTHER',role:'CHECKOUT_DEDUCTION',accountName:'ECB'}))])}} />}
      {payments.filter(p=>p.accountName!=='ECB').map((p, i) => <section className="receipt-input-row" key={p.id}><div className="form-grid"><label>Payment type<select aria-label={`Payment type ${i + 1}`} value={p.type} onChange={e => change(payments, p.id, 'type', e.target.value, setPayments)}><option value="CASH">Cash</option><option value="CARD">Card</option><option value="GIFT_CARD">Gift card payment</option><option value="OTHER">Other tender</option>{(!/^cvs/i.test(store.trim())||p.type==='WALGREENS_CASH')&&<option value="WALGREENS_CASH">Walgreens Cash used</option>}</select></label>{p.type === 'OTHER' && <label>Other payment role<select value={p.role || 'UNRESOLVED'} onChange={e => change(payments, p.id, 'role', e.target.value, setPayments)}><option value="UNRESOLVED">Unknown / needs review</option><option value="FUNDING">Funds the printed total</option><option value="CHECKOUT_DEDUCTION">Deducted before the printed total</option></select></label>}{amount(`Payment amount ${i + 1}`, p.amount, v => change(payments, p.id, 'amount', v, setPayments))}<label>Payment notes {i + 1} (optional)<input value={p.notes} onChange={e => change(payments, p.id, 'notes', e.target.value, setPayments)} /></label></div><button className="text-button" type="button" onClick={() => { setPaymentEntryStatus('UNKNOWN'); setPayments(payments.filter(row => row.id !== p.id)) }}>Remove payment {i + 1}</button></section>)}
      <button className="secondary" type="button" onClick={() => { setPaymentEntryStatus('UNKNOWN'); setPayments([...payments, { id: crypto.randomUUID(), type: 'CASH', amount: '', notes: '' }]) }}><Plus size={15} />Add payment</button>
      </details>{isWalgreensStore(store) && <PrintedFooterFields value={footer} onChange={setFooter} />}<details open><summary>Rewards earned or expected — Show / Hide</summary><p className="muted">25 received Fetch points are included automatically when saved. Enter a Fetch reward yourself to replace the default amount or status.</p><p className="muted">For a P&amp;G rebate, choose Other provider → P&amp;G Brand, enter the offer description and reward amount, then choose the qualifying products and promotion requirement (Buy or Spend).</p>
      {rewards.map((r,i) => <section className="receipt-input-row" key={r.id}><RewardFields retailer={store} onFileBusy={setBusy} offers={offers} value={r} onChange={next => setRewards(rewards.map(old => old.id===r.id?next:old))} lines={lines.map(l=>({...l,price:l.regular}))} index={i+1} /><button className="text-button" type="button" onClick={()=>setRewards(rewards.filter(row=>row.id!==r.id))}>Remove reward {i+1}</button></section>)}
      <button className="secondary" type="button" onClick={()=>setRewards([...rewards,/^cvs/i.test(store.trim())?{...newReward(),provider:'ECB',form:'STORE_REWARD',requirementType:'PURCHASE'}:newReward()])}><Plus size={15} />Add rebate or reward</button>
      </details><details open><summary>Original evidence (optional) — Show / Hide</summary><p className="muted">Attach up to four JPEG, PNG, WebP or PDF files (5 MiB each, 10 MiB total). This form does not run OCR.</p><label>Receipt files<input type="file" multiple accept="image/jpeg,image/png,image/webp,application/pdf" onChange={e => { void files(e.target.files); e.target.value = '' }} /></label>{evidence.map(file => <div className="detail-row" key={file.evidenceId}>{file.name}<button type="button" className="text-button" onClick={() => setEvidence(evidence.filter(row => row.evidenceId !== file.evidenceId))}>Remove attachment</button></div>)}
      </details><div className="form-actions"><button className="primary" type="submit">{busy ? 'Calculating…' : 'Review calculated totals'}</button></div>
    </fieldset></form>}
  </Modal>
}

export interface ItemDiscountDraft { requiredQuantity?: string | null; id: string; label: string; amount: string; kind: string }
export function ItemDiscountFields({ discount, name, productDescription='', autoFocus=false, onChange, onRemove }: { autoFocus?: boolean; productDescription?: string; discount: ItemDiscountDraft; name: string; onChange: (value: ItemDiscountDraft) => void; onRemove: () => void }) {
  return <div className="item-discount-fields"><div className="form-grid">
    <label className="field-full">{name} description<AutoSizeTextArea aria-label={`${name} description`} autoFocus={autoFocus} required value={discount.label} onBlur={() => { const word=productDescription.trim().split(/\s+/)[0]; const text=discount.label.trim(); onChange({...discount,label:titleWords(word && text && text.split(/\s+/)[0].toLowerCase()!==word.toLowerCase()?`${word} ${text}`:text)}) }} onChange={e => onChange({ ...discount, label: e.target.value })} /></label>
    <label>{name} type<select value={discount.kind} onChange={e => onChange({ ...discount, kind: e.target.value, label: !discount.label.trim() || discount.label === productDiscountLabel(productDescription,discount.kind) ? productDiscountLabel(productDescription,e.target.value) : discount.label })}>{[['SALE_PROMOTION','Sale promotion'],['MANUFACTURER','Manufacturer'],['STORE_COUPON','Store coupon'],...(discount.kind === 'STORE' ? [['STORE','Store discount']] : []),...(discount.kind === 'OTHER' ? [['OTHER','Other / unclassified']] : [])].map(([v,text]) => <option key={v} value={v}>{text}</option>)}</select></label>
    {discount.kind==='MANUFACTURER' && <label>{name} required purchase quantity (optional)<input inputMode="numeric" pattern="[1-9][0-9]*" placeholder="For example, 2 for buy 2" value={discount.requiredQuantity??''} onChange={e=>onChange({...discount,requiredQuantity:e.target.value})}/><small>Number of products required by this coupon, not the number of coupons. The amount below is the total deduction once.</small></label>}
    <label>{name} amount<input required inputMode="decimal" pattern="([0-9]+([.][0-9]+)?|[.][0-9]+)" value={discount.amount} onChange={e => onChange({ ...discount, amount: e.target.value })} /></label>
  </div><small>Enter the total deduction for the selected quantity, not the final price.</small><button type="button" className="text-button" onClick={onRemove}>Remove {name.toLowerCase()}</button></div>
}

export function PrintedFooterFields({ value, onChange }: { value: FooterDraft; onChange: (v: FooterDraft) => void }) {
  return <details className="printed-footer" open><summary>Printed Savings &amp; Walgreens Cash — Show / Hide</summary><p className="muted">Optional receipt observations. Leave absent information blank; enter 0 only when printed. These fields do not create payments, rewards or account openings.</p><div className="form-grid"><label>Is this information printed on the receipt?<select value={value.status} onChange={e => onChange({ ...value, status: e.target.value })}><option value="UNKNOWN">Not sure</option><option value="PRINTED">Yes</option><option value="NOT_PRINTED">Not printed</option></select></label>{Object.entries(footerLabels).map(([key,text]) => <label key={key}>{text}<input inputMode="decimal" pattern="([0-9]+([.][0-9]+)?|[.][0-9]+)" value={value[key as keyof typeof footerLabels]} onChange={e => onChange({ ...value, [key]: e.target.value })} /></label>)}</div></details>
}
export function PrintedFooterView({ observations }: { observations: Receipt['contents']['observations'] }) {
  const draft = footerDraft(observations)
  return <details className="printed-footer" open><summary>Printed Savings &amp; Walgreens Cash — Show / Hide</summary><p>Printed information: {draft.status === 'PRINTED' ? 'Yes' : draft.status === 'NOT_PRINTED' ? 'Not printed' : 'Not sure'}</p><div className="key-values">{Object.entries(footerLabels).filter(([key])=>key!=='storeCouponSavings').map(([key,text]) => <div key={key}><span>{text}</span><strong>{key === 'savingsValuePercent' ? draft[key] ? `${draft[key]}%` : 'Not recorded' : money(draft[key as keyof typeof footerLabels] || null)}</strong></div>)}</div><p className="muted">Printed observations are separate from calculated Total Saved and account balances.</p></details>
}

/** Receipt-wide ECB uses reuse named account deductions; never product coupons. */
export function ECBUseFields({rows,onChange}:{rows:{id:string;amount:string;notes:string}[];onChange:(rows:{id:string;amount:string;notes:string}[])=>void}) {
 return <details open className="receipt-input-row"><summary>ExtraBucks (ECB) used · {rows.length} coupon{rows.length===1?'':'s'} — Show / Hide</summary><p className="muted product-calculation-note">Enter each ECB separately. These apply to the whole receipt. Describe each coupon so you can identify it later; do not repeat it as a discount or payment.</p>{rows.map((r,i)=><section className="receipt-input-row" key={r.id}><div className="form-grid"><label>ECB used amount {i+1}<input required inputMode="decimal" pattern="([0-9]+([.][0-9]+)?|[.][0-9]+)" value={r.amount} onChange={e=>onChange(rows.map(x=>x.id===r.id?{...x,amount:e.target.value}:x))}/></label><label>ECB used description {i+1}<input value={r.notes} onChange={e=>onChange(rows.map(x=>x.id===r.id?{...x,notes:e.target.value}:x))}/></label></div><button type="button" className="secondary" onClick={()=>onChange(rows.filter(x=>x.id!==r.id))}>Remove ECB use {i+1}</button></section>)}<button type="button" className="secondary" onClick={()=>onChange([...rows,{id:crypto.randomUUID(),amount:'',notes:''}])}>Add ECB used</button></details>
}
