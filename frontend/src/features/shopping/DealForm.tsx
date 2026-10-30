import { FormActions } from '../../components/ui'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { EvidenceField } from '../../components/EvidenceField'
import type { Upload } from '../../components/EvidenceField'
import { ApiClient, ApiError } from '../../api/client'
import { integer } from '../../api/format'
import type { Deal } from '../../types/platform'

/** Inline form: parent plan fields stay mounted while an independent deal is saved. */
export function DealForm({ api, onSaved, onCancel }: { api: ApiClient; onSaved: (deal: Deal) => void; onCancel: () => void }) {
  const [fields, setFields] = useState<Record<string, string>>({ type: 'SALE', minimumQuantity: '1', freeQuantity: '1' })
  const [cheapest,setCheapest]=useState(false)
  const [strictSkuMatching, setStrictSkuMatching] = useState(false)
  const [repeatable, setRepeatable] = useState(false)
  const [fileBusy,setFileBusy]=useState(false)
  const [attachment, setAttachment] = useState<Upload | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [request, setRequest] = useState<{ body: unknown; key: string } | null>(null)
  const input = (key: string, name: string, required = false, type = 'text') => <label>{name}<input required={required} type={type} value={fields[key] || ''} onChange={e => setFields({ ...fields, [key]: e.target.value })} /></label>
  async function submit(e: FormEvent) {
    e.preventDefault()
    setError('')
    if (strictSkuMatching && !fields.skus?.split(',').some(sku => sku.trim().length > 0)) {
      setError('Enter at least one qualifying UPC/SKU before enabling strict matching.')
      return
    }
    setBusy(true)
    try {
      const nullable = (key: string) => fields[key]?.trim() || null
      const pending = request || { key: crypto.randomUUID(), body: { dealId: crypto.randomUUID(), attachment, title: fields.title, retailer: fields.retailer, interpretedTerms: fields.terms, validFrom: nullable('validFrom'), validThrough: nullable('validThrough'), pricing: { cheapestItemsDiscounted: cheapest, strictSkuMatching, type: fields.type, productDescription: fields.productDescription, regularUnitPrice: nullable('regularUnitPrice'), promotionalPrice: nullable('promotionalPrice'), minimumQuantity: integer(fields.minimumQuantity), minimumSpend: nullable('minimumSpend'), freeQuantity: fields.type === 'BUY_GET' ? integer(fields.freeQuantity) : 0, rewardAmount: nullable('rewardAmount'), repeatable, limit: fields.limit ? integer(fields.limit) : null, qualifyingSkus: fields.skus?.split(',').map(s => s.trim()).filter(Boolean) || [], qualifications: nullable('qualifications'), loyaltyRequirement: nullable('loyaltyRequirement'), exclusions: nullable('exclusions') } } }
      setRequest(pending)
      const deal = await api.mutate('/deals', 'POST', pending.body, pending.key)
      onSaved(deal as Deal)
    } catch (ex) { setError((ex as Error).message); if(ex instanceof ApiError && ex.status===400)setRequest(null) } finally { setBusy(false) }
  }
  return <form onSubmit={submit} aria-label="Create advertised deal"><h3>Add a deal</h3>{error && <p role="alert">{error}</p>}<fieldset disabled={busy || fileBusy || !!request}>
    {input('retailer', 'Store', true)}{input('title', 'Promotion name', true)}{input('productDescription', 'Qualifying product description', true)}{input('terms', 'Recorded offer terms', true)}
    <label>Promotion type<select value={fields.type} onChange={e => setFields({ ...fields, type: e.target.value })}><option value="SALE">Regular sale price</option><option value="MULTIBUY">Multi-buy bundle price</option><option value="BUY_GET">Buy X, get Y discounted or free</option><option value="SPEND_REWARD">Spend X, earn store rewards</option><option value="QUANTITY_REWARD">Buy X products, earn store rewards</option></select></label>
    <div className="form-grid">{input('regularUnitPrice', 'Regular unit price')}{input('promotionalPrice', fields.type === 'MULTIBUY' ? 'Price for the full bundle' : fields.type === 'BUY_GET' ? 'Price per discounted item (0 for free)' : 'Sale unit price')}{input('minimumQuantity', fields.type === 'MULTIBUY' ? 'Units per bundle' : 'Required quantity', true)}{fields.type === 'BUY_GET' && input('freeQuantity', 'Discounted quantity', true)}{input('minimumSpend', 'Required spend')}{input('rewardAmount', 'Expected store reward (USD)')}</div>
    {input('skus', 'Qualifying SKU / UPC (comma separated)')}
    <label className="check">
      <input type="checkbox" checked={strictSkuMatching} onChange={e => setStrictSkuMatching(e.target.checked)} />
      Require an exact UPC/SKU match for this promotion
    </label>
    {strictSkuMatching && <p className="muted">Only linked products with a UPC/SKU in the qualifying list will count toward this promotion. Products without a code will be excluded.</p>}
    {input('qualifications', 'Brands, sizes, categories and other qualifications')}{input('loyaltyRequirement', 'Loyalty account requirements')}{input('exclusions', 'Conditions and exclusions')}
    <div className="form-grid">{input('validFrom', 'Start date', false, 'date')}{input('validThrough', 'Expiration date', false, 'date')}{input('limit', 'Maximum qualifying bundles / rewards')}</div>
    {fields.type==='BUY_GET'&&<label className="check"><input type="checkbox" checked={cheapest} onChange={e=>setCheapest(e.target.checked)} />The offer states that the cheapest qualifying items receive the discount. Leave unchecked to retain manual prices and flag allocation for review.</label>}
    <label className="check"><input type="checkbox" checked={repeatable} onChange={e => setRepeatable(e.target.checked)} />Repeat for each qualifying group</label>
  <EvidenceField onBusy={setFileBusy} onChange={setAttachment} /></fieldset><p className="muted">Select this deal on every qualifying planned item. Linked planned items count toward this deal by default. Recorded product qualifications still require review.</p><FormActions><button type="button" className="secondary" onClick={onCancel} disabled={busy || fileBusy}>Back to planned item</button><button className="primary" disabled={busy || fileBusy}>{busy ? 'Saving…' : request ? 'Retry same deal' : 'Save and link deal'}</button></FormActions></form>
}
