import { FormActions } from '../../components/ui'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { EvidenceField } from '../../components/EvidenceField'
import type { Upload } from '../../components/EvidenceField'
import { ApiClient, ApiError } from '../../api/client'
import { integer } from '../../api/format'
import type { Coupon } from '../../types/platform'

/** Shared by Coupons and inline shopping-plan entry. */
export function CouponForm({ api, onSaved, onCancel, initialValues={} }: { initialValues?:Record<string,string>; api: ApiClient; onSaved: (coupons: Coupon[]) => void; onCancel: () => void }) {
  const [f, setF] = useState<Record<string, string>>(() => ({ source: 'PAPER_MANUFACTURER', couponType: 'MANUFACTURER', discount: 'DOLLARS', copies: '1', requiredQuantity: '1', usageLimit: '1', status: 'CLIPPED', clippedOn: new Date().toISOString().slice(0, 10), ...initialValues }))
  const [fileBusy,setFileBusy]=useState(false)
  const [attachment, setAttachment] = useState<Upload | null>(null)
  const [error, setError] = useState(''); const [busy, setBusy] = useState(false)
  const [pending, setPending] = useState<{ body: unknown; key: string } | null>(null)
  const input = (key: string, name: string, required = false, type = 'text') => <label>{name}<input type={type} required={required} value={f[key] || ''} onChange={e => setF({ ...f, [key]: e.target.value })} /></label>
  async function submit(e: FormEvent) {
    e.preventDefault(); setError(''); setBusy(true)
    try {
      const optional = (key: string) => f[key]?.trim() || null
      const request = pending || { key: crypto.randomUUID(), body: { couponId: crypto.randomUUID(), evidenceMediaType: attachment?.mediaType || null, evidenceBase64: attachment?.contentBase64 || null, provider: f.provider, description: f.description, amount: f.discount === 'FREE_ITEM' ? null : f.amount, requiredQuantity: integer(f.requiredQuantity), couponType: f.couponType, expirationDate: optional('expirationDate'), clippedOn: optional('clippedOn'), status: f.status, copies: integer(f.copies), source: f.source, discount: f.discount, storeRestriction: optional('storeRestriction'), accountRestriction: optional('accountRestriction'), productOrBrand: optional('productOrBrand'), qualifyingSkus: f.skus?.split(',').map(s => s.trim()).filter(Boolean) || [], sizeOrVariety: optional('sizeOrVariety'), minimumPurchase: optional('minimumPurchase'), startsOn: optional('startsOn'), usageLimit: integer(f.usageLimit), conditions: optional('conditions') } }
      setPending(request); const result = await api.mutate('/clipped-coupons', 'POST', request.body, request.key); onSaved(result as Coupon[])
    } catch (ex) { setError((ex as Error).message); if (ex instanceof ApiError && ex.status === 400) setPending(null) } finally { setBusy(false) }
  }
  return <form onSubmit={submit} aria-label="Add coupon"><h3>Add coupon</h3>{error && <p role="alert">{error}</p>}<fieldset disabled={busy || fileBusy || !!pending}>
    {input('description', 'Coupon description', true)}{input('provider', 'Issuing manufacturer, store or provider', true)}
    <div className="form-grid"><label>Coupon source<select value={f.source} onChange={e => setF({ ...f, source: e.target.value, couponType: e.target.value.endsWith('STORE') ? 'STORE' : e.target.value.endsWith('MANUFACTURER') ? 'MANUFACTURER' : f.couponType })}>{['DIGITAL_MANUFACTURER', 'DIGITAL_STORE', 'PAPER_MANUFACTURER', 'PAPER_STORE', 'PRINTABLE', 'REGISTER_PRINTED', 'OTHER'].map(s => <option key={s} value={s}>{s.replaceAll('_', ' ').toLowerCase()}</option>)}</select></label><label>Classification<select value={f.couponType} onChange={e => setF({ ...f, couponType: e.target.value })}><option value="MANUFACTURER">Manufacturer coupon</option><option value="STORE">Store coupon</option></select></label></div>
    <label>Discount type<select value={f.discount} onChange={e => setF({ ...f, discount: e.target.value })}><option value="DOLLARS">Dollars off</option><option value="PERCENT">Percentage off</option><option value="FREE_ITEM">Free item</option></select></label>
    {f.discount !== 'FREE_ITEM' && input('amount', f.discount === 'PERCENT' ? 'Percent off' : 'Dollar amount', true)}
    <div className="form-grid">{input('requiredQuantity', 'Minimum qualifying quantity', true)}{input('minimumPurchase', 'Minimum qualifying purchase (optional)')}{input('copies', 'Number of available copies', true)}{input('usageLimit', 'Maximum copies per transaction', true)}</div>
    {input('productOrBrand', 'Product or brand')}{input('skus', 'Qualifying SKU / UPC (comma separated)')}{input('sizeOrVariety', 'Required size or variety')}{input('storeRestriction', 'Restricted to store (optional)')}{input('accountRestriction', 'Account requirement (optional)')}
    <div className="form-grid">{input('startsOn', 'Valid from', false, 'date')}{input('expirationDate', 'Expiration date', false, 'date')}{input('clippedOn', 'Saved / clipped date', true, 'date')}</div>
    <label>Initial status<select value={f.status} onChange={e => setF({ ...f, status: e.target.value })}><option value="CLIPPED">Saved / clipped</option><option value="AVAILABLE">Available, not yet saved</option></select></label>{input('conditions', 'Additional requirements or exclusions')}
  <EvidenceField onBusy={setFileBusy} onChange={setAttachment} /></fieldset><p className="muted">Paper coupons do not require a digital account. Ordinary register-printed coupons are separate from Register Reward instruments.</p><FormActions><button type="button" className="secondary" disabled={busy || fileBusy} onClick={onCancel}>Cancel coupon</button><button className="primary" disabled={busy || fileBusy}>{busy ? 'Saving…' : pending ? 'Retry same coupon' : 'Save coupon'}</button></FormActions></form>
}
