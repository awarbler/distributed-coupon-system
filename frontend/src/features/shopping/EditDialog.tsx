import { useState } from 'react'
import type { FormEvent } from 'react'
import { ApiClient, ApiError } from '../../api/client'
import { CouponForm } from './CouponForm'
import { DealForm } from './DealForm'
import { integer } from '../../api/format'
import { Modal, Notice } from '../../components/ui'
import type { Deal, Coupon, Plan, PlanRow, Purchase, Workspace } from '../../types/platform'

export type Edit = { kind: 'plan'; plan?: Plan } | { kind: 'purchase'; row: PlanRow; item?: Purchase; initialDealId?: string } | { kind: 'redeem'; coupon: Coupon } | { kind: 'coupon' }
interface Request { path: string; method: 'POST' | 'PUT'; body: unknown; revision?: string; key: string }
export function EditDialog({ edit, api, data, onClose, onSaved, onReview, onEdit, online }:
  { edit: Edit; api: ApiClient; data: Workspace; onClose: () => void; onSaved: () => void; onReview?: () => void; onEdit?: (edit: Edit) => void; online: boolean }) {
  const plan = edit.kind === 'plan' ? edit.plan : undefined
  const item = edit.kind === 'purchase' ? edit.item : undefined
  const planRow = edit.kind === 'plan' && edit.plan ? data.plans.find(row => row.plan.planId === edit.plan?.planId) : undefined
  const [id] = useState(() => crypto.randomUUID())
  const [name, setName] = useState(plan?.name || item?.productName || '')
  const [retailer, setRetailer] = useState(plan?.retailer || '')
  const [when, setWhen] = useState(plan?.shoppingDate || '')
  const [sku, setSku] = useState(item?.productCode || '')
  const [quantity, setQuantity] = useState(item?.quantity || '1')
  const [price, setPrice] = useState(item?.regularUnitPrice || item?.targetUnitPrice || '')
  const [sale, setSale] = useState(item?.saleUnitPrice || '')
  const [priceSource, setPriceSource] = useState(item?.priceSource || 'MANUAL')
  const [addingCoupon, setAddingCoupon] = useState(edit.kind === 'coupon')
  const [createdCoupons, setCreatedCoupons] = useState<Coupon[]>([])
  const [addingDeal, setAddingDeal] = useState(false)
  const [createdDeals, setCreatedDeals] = useState<Deal[]>([])
  const [deal, setDeal] = useState(item?.advertisedDealId || (edit.kind === 'purchase' ? edit.initialDealId : '') || '')
  const [coupons, setCoupons] = useState<string[]>(item?.clippedCouponIds || [])
  const [receipt, setReceipt] = useState('')
  const [error, setError] = useState('')
  const [pending, setPending] = useState<Request | null>(null)
  const [busy, setBusy] = useState(false)
  const availableDeals = [...new Map([...data.deals, ...createdDeals].map(d => [d.dealId, d])).values()]
  const availableCoupons = [...new Map([...data.coupons, ...createdCoupons].map(c => [c.couponId, c])).values()]
  const title = edit.kind === 'coupon' ? 'Add coupon' : edit.kind === 'plan' ? plan ? 'Edit shopping plan' : 'Create a shopping plan' : edit.kind === 'purchase' ? item ? 'Edit planned item' : 'Add a planned item' : 'Record coupon redemption'
  function build(): Request {
    const key = crypto.randomUUID()
    if (edit.kind === 'plan') return { path: plan ? `/shopping-plans/${plan.planId}` : '/shopping-plans', method: plan ? 'PUT' : 'POST', key, revision: plan?.revision, body: { ...(plan ? {} : { planId: id }), name: name.trim(), retailer: retailer.trim() || null, shoppingDate: when || null } }
    if (edit.kind === 'purchase') {
      if (price && (!/^\d+(\.\d+)?$/.test(price) || price.length > 64)) throw new Error('Enter a nonnegative decimal target price, without currency symbols.')
      return { path: `/shopping-plans/${edit.row.plan.planId}/purchases${item ? `/${item.plannedPurchaseId}` : ''}`, method: item ? 'PUT' : 'POST', key, revision: item?.revision, body: { plannedPurchaseId: item?.plannedPurchaseId || id, productName: name.trim(), productCode: sku.trim() || null, quantity: integer(quantity), regularUnitPrice: price || null, saleUnitPrice: sale || null, priceSource, advertisedDealId: deal || null, clippedCouponIds: coupons } }
    }
    if (edit.kind !== 'redeem') throw new Error('Use the coupon form.')
    if (!receipt || !when) throw new Error('Select the receipt and the actual date this coupon was used.')
    return { path: `/clipped-coupons/${edit.coupon.couponId}/redeem`, method: 'POST', key, revision: edit.coupon.revision, body: { transactionId: receipt, redeemedOn: when } }
  }
  async function submit(event: FormEvent) {
    event.preventDefault(); setError(''); if (!online) { setError('Reconnect before submitting changes.'); return }
    let request: Request
    try { request = pending || build() } catch (e) { setError((e as Error).message); return }
    setPending(request); setBusy(true)
    try { await api.mutate(request.path, request.method, request.body, request.key, request.revision); onSaved(); onClose() }
    catch (e) {
      setError((e as Error).message)
      if (e instanceof ApiError && e.status >= 400 && e.status < 500 && e.status !== 409) setPending(null)
    } finally { setBusy(false) }
  }
  return <Modal title={title} onClose={onClose}>{addingCoupon ? <CouponForm api={api} onCancel={() => edit.kind === 'coupon' ? onClose() : setAddingCoupon(false)} onSaved={added => { if (edit.kind === 'coupon') { onSaved(); onClose() } else { setCreatedCoupons([...createdCoupons, ...added]); setCoupons([...coupons, added[0].couponId]); setAddingCoupon(false); onSaved() } }} /> : addingDeal ? <DealForm api={api} onCancel={() => setAddingDeal(false)} onSaved={d => { setCreatedDeals([...createdDeals, d]); setDeal(d.dealId); setPriceSource('DEAL'); if (d.pricing?.type === 'SALE') setSale(d.pricing.promotionalPrice || ''); setAddingDeal(false); onSaved() }} /> : <form onSubmit={submit}>
    {error && <div className="error" role="alert">{error}</div>}
    {pending && !busy && <Notice>The original request is retained. Retry sends the same key and values. Refresh and review the record before making a different change.</Notice>}
    <fieldset disabled={busy || !!pending || !online}>
      {edit.kind === 'plan' && (
  <label>
    Plan name
    <input
      required
      maxLength={200}
      value={name}
      onChange={e => setName(e.target.value)}
      placeholder="Weekly essentials"
    />
  </label>
)}
      {edit.kind === 'plan' && <div className="form-grid"><label>Store <span>(optional)</span><input value={retailer} onChange={e => setRetailer(e.target.value)} placeholder="Store name" /></label><label>Shopping date <span>(optional)</span><input type="date" value={when} onChange={e => setWhen(e.target.value)} /></label></div>}
      {edit.kind === 'purchase' && <>
        <div className="product-identity-grid">
          <label>Product description <span>(Required)</span>
            <input
              required maxLength={200} value={name}onChange={e => setName(e.target.value)}placeholder="Product name"/>
          </label>

          <label>
            SKU / UPC <span>(optional)</span><input
value={sku}
              onChange={e => setSku(e.target.value)}
              placeholder="Enter SKU or UPC"
            />
          </label>
        </div>

        <div className="product-price-grid">
          <label>
            Quantity<span>(Required)</span>
            <input
              required
              inputMode="numeric"
              pattern="[0-9]+"
              value={quantity}
              onChange={e => setQuantity(e.target.value)}
            />
          </label>

          <label>
            Regular price <span>(optional)</span>
            <input
              inputMode="decimal"
              value={price}
              onChange={e => setPrice(e.target.value)}
              placeholder="0.00"
            />
          </label>

          <label>
            Sale price <span>(optional)</span>
            <input
              inputMode="decimal"
              value={sale}
              onChange={e => {
                setSale(e.target.value)
                setPriceSource('MANUAL')
              }}
              placeholder="0.00"
            />
          </label>
        </div>
        <label>Advertised deal <span>(optional association)</span><select value={deal} onChange={e => { setDeal(e.target.value); setPriceSource(e.target.value ? 'DEAL' : 'MANUAL') }}><option value="">No deal linked</option>{availableDeals.map(d => <option key={d.dealId} value={d.dealId}>{d.title}</option>)}</select></label>
        <button type="button" className="text-button" onClick={() => setAddingDeal(true)}>Add a deal</button>
        <span className="field-label">Linked coupons</span><div className="check-list">{availableCoupons.length ? availableCoupons.map(c => <label key={c.couponId} className="check"><input type="checkbox" checked={coupons.includes(c.couponId)} onChange={e => setCoupons(e.target.checked ? [...coupons, c.couponId] : coupons.filter(id => id !== c.couponId))} />{c.provider}: {c.description}</label>) : <p>No recorded coupons to associate.</p>}</div>
        <button type="button" className="text-button" onClick={() => setAddingCoupon(true)}>Add coupon</button>
        <p className="muted">Java estimates merchandise, qualifying recorded offers and linked coupons. Tax is excluded; expected rewards are separate from register cost. Pricing source: {priceSource === 'DEAL' ? 'linked deal' : 'manually entered'}.</p>
      </>}
      {edit.kind === 'redeem' && (
        <>
          <p>
            {edit.coupon.provider} · {edit.coupon.description}
          </p>

          <Notice>
            This records the coupon as used.
            It does not add a deduction to the receipt.
          </Notice>

          <label>
            Receipt
            <select
              required
              value={receipt}
              onChange={e => setReceipt(e.target.value)}
            >
              <option value="">Select the spending receipt</option>
              {data.receipts
                .filter(r => r.status === 'ACTIVE')
                .map(r => (
                  <option
                    key={r.transactionId}
                    value={r.transactionId}
                  >
                    {r.date || 'Undated'} ·{' '}
                    {r.metadata?.receiptNumber || r.transactionId}
                  </option>
                ))}
            </select>
          </label>

          <label>
            Actual date used
            <input
              type="date"
              required
              value={when}
              onChange={e => setWhen(e.target.value)}
            />
          </label>
        </>
      )}

      {edit.kind === 'plan' && planRow && (
        <section className="plan-editor-products">
          <h3>Products in this plan</h3>

          <p className="muted">
            Product changes are saved individually.
            Save changes to the plan name, store, or date
            before opening a product editor.
          </p>

          {planRow.items.length === 0 ? (
            <p className="muted">
              No products have been added to this plan.
            </p>
          ) : (
            <div className="plan-editor-product-list">
              {planRow.items.map(product => (
                <div
                  className="plan-editor-product"
                  key={product.plannedPurchaseId}
                >
                  <div>
                    <strong>{product.productName}</strong>
                    <small>
                      Quantity: {product.quantity}
                      {' · '}
                      Unit price: $
                      {product.saleUnitPrice ||
                        product.regularUnitPrice ||
                        product.targetUnitPrice ||
                        'Not recorded'}
                    </small>
                  </div>

                  <button
                    type="button"
                    className="secondary"
                    disabled={!online || !onEdit || busy || !!pending}
                    onClick={() =>
                      onEdit?.({
                        kind: 'purchase',
                        row: planRow,
                        item: product
                      })
                    }
                  >
                    Edit item
                  </button>
                </div>
              ))}
            </div>
          )}

          <button
            type="button"
            className="secondary"
            disabled={!online || !onEdit || busy || !!pending}
            onClick={() =>
              onEdit?.({
                kind: 'purchase',
                row: planRow
              })
            }
          >
            + Add product
          </button>
        </section>
      )}
    </fieldset>

    <div className="form-actions">
      <button
        type="button"
        className="secondary"
        disabled={busy}
        onClick={() => {
          if (pending) onReview?.()
          onClose()
        }}
      >
        {pending ? 'Close and review current data' : 'Cancel'}
      </button>

      <button
        className="primary"
        disabled={busy || !online}
      >
        {busy
          ? 'Saving…'
          : pending
            ? 'Retry same request'
            : 'Save'}
      </button>
    </div>
  </form>}
  </Modal>
}
