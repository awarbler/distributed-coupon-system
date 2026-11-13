import { useState } from 'react'
import { ChevronDown, ChevronRight } from 'lucide-react'
import { ApiClient, ApiError } from '../../api/client'
import { date, label, shortId } from '../../api/format'
import { DisclosureSummary, Modal, Notice, SectionHelp } from '../../components/ui'
import type { Coupon, Workspace } from '../../types/platform'
import type { Edit } from './EditDialog'
import { couponAssignments, groupPaperCoupons, paperStatus } from './paperCouponGroups'
import type { PaperGrouping, PaperOffer, PaperStatus } from './paperCouponGroups'

type Action = 'Edit' | 'Use' | 'Delete'
export function PaperCoupons({ coupons, data, api, writable, onChanged, onEdit, onReceipt, today, grouping, status, allLibrary = false }: {
  coupons: Coupon[]; data: Workspace; api?: ApiClient; writable: boolean; onChanged?: () => void;
  onEdit: (edit: Edit) => void; onReceipt: (id: string) => void; today: string; grouping: PaperGrouping; status: PaperStatus; allLibrary?: boolean
}) {
  const [collapsed, setCollapsed] = useState<string[]>([])
  const [open, setOpen] = useState<string[]>([]), [error, setError] = useState(''), [busy, setBusy] = useState(false)
  const [choice, setChoice] = useState<{ action: Action; copies: Coupon[]; selected: string } | null>(null)
  const [pending, setPending] = useState<{ path: string; method: 'POST' | 'DELETE'; body: unknown; key: string; revision: string } | null>(null)
  const sections = groupPaperCoupons(coupons, today, grouping, allLibrary ? 'ALL' : status)
  async function send(request: NonNullable<typeof pending>) {
    if (!api || !writable) return
    setPending(request); setBusy(true); setError('')
    try { await api.mutate(request.path, request.method, request.body, request.key, request.revision); setPending(null); onChanged?.() }
    catch (ex) { setError((ex as Error).message); if (ex instanceof ApiError && [400, 403, 404, 409, 412].includes(ex.status)) setPending(null) }
    finally { setBusy(false) }
  }
  function add(c: Coupon) {
    void send({ path: `/clipped-coupons/${c.couponId}/copies`, method: 'POST', body: { couponId: crypto.randomUUID(), clippedOn: today }, key: crypto.randomUUID(), revision: c.revision })
  }
  function act(action: Action, c: Coupon) {
    setChoice(null)
    if (action === 'Use') onEdit({ kind: 'redeem', coupon: c })
    else if (action === 'Edit') onEdit({ kind: 'coupon', coupon: c })
    else if (window.confirm(`Permanently delete only physical copy ${shortId(c.couponId)} of “${c.description}”? Its own plan assignments will be detached. Other copies and redeemed history remain.`))
      void send({ path: `/clipped-coupons/${c.couponId}`, method: 'DELETE', body: {}, key: crypto.randomUUID(), revision: c.revision })
  }
  function choose(action: Action, offer: PaperOffer) {
    const candidates = offer.copies.filter(c => action === 'Use' ? c.status === 'CLIPPED' : c.status !== 'APPLIED')
      .sort((a, b) => couponAssignments(data, a.couponId) - couponAssignments(data, b.couponId) || a.couponId.localeCompare(b.couponId))
    if (candidates.length === 1) act(action, candidates[0])
    else if (candidates.length) setChoice({ action, copies: candidates, selected: candidates[0].couponId })
  }
  return <section aria-label="Paper coupons">
    {error && <p role="alert">{error}</p>}{pending && !busy && <Notice>The outcome may be unresolved. <button onClick={() => void send(pending)}>Retry same change</button> or refresh before making another change.</Notice>}
    {sections.map(section => {const sectionKey=grouping+section.key,sectionOpen=!collapsed.includes(sectionKey),sectionId=`paper-section-${section.offers[0].copies[0].couponId}`;return <section className="paper-coupon-section" key={section.key} aria-label={section.title}><h2><button className="paper-section-toggle" aria-expanded={sectionOpen} aria-controls={sectionId} onClick={()=>setCollapsed(sectionOpen?[...collapsed,sectionKey]:collapsed.filter(key=>key!==sectionKey))}>{sectionOpen?<ChevronDown size={18} aria-hidden="true"/>:<ChevronRight size={18} aria-hidden="true"/>}{grouping === 'EXPIRATION' && section.title !== 'Expiration not recorded' ? date(section.title) : section.title}</button></h2><div id={sectionId} hidden={!sectionOpen} className="coupon-list">{section.offers.map(offer => {
      const c = offer.copies[0], expanded = open.includes(offer.key), control = `paper-copy-${c.couponId}`, usable = offer.copies.some(x => x.status === 'CLIPPED'), editable = offer.copies.some(x => x.status !== 'APPLIED'), disabled = !writable || busy || !!pending
      return <article className="coupon-row" key={offer.key}><div className="coupon-row-heading"><strong>{c.description} <span className="paper-copy-count">· {offer.available} available {offer.available === 1 ? 'copy' : 'copies'} · {offer.total} recorded</span><span className="paper-copy-count"> · Expires {date(c.expirationDate)}</span></strong><div className="coupon-row-actions">
        <button disabled={disabled || !api || !!c.expirationDate && c.expirationDate < today} onClick={() => add(c)}>Add Copy</button>
        <button disabled={disabled || !editable} onClick={() => choose('Edit', offer)}>Edit</button><button disabled={disabled || !usable} onClick={() => choose('Use', offer)}>Use</button><button disabled={disabled || !api || !editable} onClick={() => choose('Delete', offer)}>Delete</button>
        <SectionHelp title={c.description} label={`About paper offer ${c.description}`}><p>{c.provider} · {label(c.couponType)} · {label(c.terms?.source||'Unknown')} · Expires {date(c.expirationDate)}</p><p>Products: {c.terms?.productOrBrand||'Not recorded'} · Quantity: {c.requiredQuantity||'Not recorded'} · Minimum spend: {c.terms?.minimumPurchase||'Not recorded'}</p><p>{c.terms?.conditions||'No additional terms recorded.'}</p></SectionHelp>
        <button aria-label={`${expanded ? 'Collapse' : 'Expand'} paper offer ${c.description}`} aria-expanded={expanded} aria-controls={control} onClick={() => setOpen(expanded ? open.filter(key => key !== offer.key) : [...open, offer.key])}>{expanded ? <ChevronDown size={18}/> : <ChevronRight size={18}/>}</button>
      </div></div>{expanded && <div id={control} className="coupon-row-details"><details><DisclosureSummary>Offer terms</DisclosureSummary><p>Size / variety: {c.terms?.sizeOrVariety||'Not recorded'} · Store: {c.terms?.storeRestriction||'Not recorded'} · Account: {c.terms?.accountRestriction||'Not recorded'}</p><p>Qualifying SKU / UPC: {c.terms?.qualifyingSkus?.join(', ')||'Not recorded'} · Maximum uses: {c.terms?.usageLimit||'Not recorded'}</p>{c.terms?.originalOfferText&&<p className="coupon-original">{c.terms.originalOfferText}</p>}</details>
        <ul className="paper-copy-list">{offer.copies.map(copy => <li key={copy.couponId}><div><strong>Copy {shortId(copy.couponId)}</strong> · {label(paperStatus(copy, today))}<p>{couponAssignments(data, copy.couponId)} plan(s) assigned.</p>{copy.redeemedOn && <p>Redeemed {date(copy.redeemedOn)}</p>}</div><div className="coupon-row-actions">{copy.redeemedTransactionId ? <button onClick={() => onReceipt(copy.redeemedTransactionId!)}>View receipt</button> : <><button disabled={disabled} onClick={() => act('Edit', copy)}>Edit copy</button><button disabled={disabled || copy.status !== 'CLIPPED'} onClick={() => act('Use', copy)}>Use copy</button><button disabled={disabled || !api} onClick={() => act('Delete', copy)}>Delete copy</button></>}</div></li>)}</ul>
      </div>}</article>
    })}</div></section>})}
    {!sections.length && <p>No paper coupons match this status.</p>}
    {choice && <Modal title={`${choice.action} one physical copy`} onClose={() => setChoice(null)}><p>Choose the copy to {choice.action.toLowerCase()}. Each copy keeps its own assignments and history.</p><label>Physical copy<select value={choice.selected} onChange={e => setChoice({ ...choice, selected: e.target.value })}>{choice.copies.map(c => <option key={c.couponId} value={c.couponId}>Copy {shortId(c.couponId)} · {label(paperStatus(c, today))} · {couponAssignments(data, c.couponId)} plan(s) assigned</option>)}</select></label><div className="button-row"><button onClick={() => setChoice(null)}>Cancel</button><button className="primary" onClick={() => { const selected = choice.copies.find(c => c.couponId === choice.selected); if (selected) act(choice.action, selected) }}>Continue</button></div></Modal>}
  </section>
}
