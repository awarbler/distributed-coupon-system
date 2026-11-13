import type { Coupon, Workspace } from '../../types/platform'

const physicalSources = new Set(['PAPER_MANUFACTURER', 'PAPER_STORE', 'PRINTABLE', 'REGISTER_PRINTED'])
export const isPaperSource = (source?: string) => physicalSources.has(source || '')
export const isPaperCoupon = (coupon: Coupon) => isPaperSource(coupon.terms?.source)
const text = (value: unknown) => String(value ?? '').trim().replace(/\s+/g, ' ')
// Decimal strings stay exact; grouping must not round monetary values through Number.
const number = (value: unknown) => {
  const raw = text(value)
  if (!/^\d+(?:\.\d+)?$/.test(raw)) return raw
  const [whole, fraction = ''] = raw.split('.')
  const tail = fraction.replace(/0+$/, '')
  return whole.replace(/^0+(?=\d)/, '') + (tail ? `.${tail}` : '')
}
const provenance = /^(?:importKey|sourcePage|sourceDocument|sourceDocumentHash|sourceLayout|lastSeen.*|reviewStatus)$/
export const paperBrand = (c: Coupon) => text(c.terms?.offerDetails?.brand) || text(c.terms?.productOrBrand) || 'Brand not recorded'

/** Conservative display equivalence. It never merges or changes persisted copy identities. */
export function paperOfferKey(c: Coupon): string {
  const t = c.terms
  if (!isPaperCoupon(c) || !t) return JSON.stringify(['individual', c.couponId])
  const details = Object.entries(t.offerDetails || {}).filter(([key]) => !provenance.test(key)).sort(([a], [b]) => a.localeCompare(b)).map(([key, value]) => [key, text(value)])
  const evidence = !!(text(t.productOrBrand) || t.qualifyingSkus?.length || text(t.originalOfferText) || text(t.offerDetails?.qualifyingProducts))
  const complete = evidence && !!c.expirationDate && c.couponType !== 'OTHER' && t.discount !== 'UNKNOWN' && t.offerDetails?.reviewStatus !== 'NEEDS_REVIEW'
  return JSON.stringify([
    text(c.provider), text(t.issuingProvider), c.couponType, t.source, t.discount,
    number(c.discountAmount), number(c.requiredQuantity), number(t.minimumPurchase), number(t.usageLimit),
    c.expirationDate, t.startsOn || null, text(t.storeRestriction), text(t.accountRestriction),
    text(t.productOrBrand), [...new Set((t.qualifyingSkus || []).map(text))].sort(), text(t.sizeOrVariety),
    text(t.conditions), text(t.originalOfferText), text(c.description), c.externalId || null, details,
    // Missing qualifications/expiration are not proof that independently entered offers are identical.
    complete ? null : t.offerId || c.couponId,
  ])
}
export type PaperStatus = 'AVAILABLE' | 'ALL' | 'REDEEMED' | 'EXPIRED' | 'UPCOMING' | 'NOT_OWNED' | 'UNAVAILABLE'
export type PaperGrouping = 'BRAND' | 'OFFER' | 'EXPIRATION'
export function paperStatus(c: Coupon, today: string): Exclude<PaperStatus, 'ALL'> {
  if (c.status === 'APPLIED') return 'REDEEMED'
  if (c.expirationDate && c.expirationDate < today) return 'EXPIRED'
  if (c.status === 'AVAILABLE') return 'NOT_OWNED'
  if (c.status !== 'CLIPPED') return 'UNAVAILABLE'
  if (c.terms?.startsOn && c.terms.startsOn > today) return 'UPCOMING'
  return 'AVAILABLE'
}
export interface PaperOffer { key: string; copies: Coupon[]; available: number; total: number }
export interface PaperSection { key: string; title: string; offers: PaperOffer[] }
export function groupPaperCoupons(coupons: Coupon[], today: string, grouping: PaperGrouping, status: PaperStatus): PaperSection[] {
  const offers = new Map<string, Coupon[]>()
  for (const c of coupons.filter(isPaperCoupon)) {
    const key = paperOfferKey(c)
    offers.set(key, [...(offers.get(key) || []), c])
  }
  const sections = new Map<string, PaperSection>()
  for (const [key, copies] of offers) {
    if (status !== 'ALL' && !copies.some(c => paperStatus(c, today) === status)) continue
    const c = copies[0]
    const sectionKey = grouping === 'BRAND' ? paperBrand(c) : grouping === 'EXPIRATION' ? c.expirationDate || '9999-99-99' : key
    const title = grouping === 'BRAND' ? paperBrand(c) : grouping === 'EXPIRATION' ? c.expirationDate || 'Expiration not recorded' : c.description
    const section = sections.get(sectionKey) || { key: sectionKey, title, offers: [] }
    section.offers.push({ key, copies, available: copies.filter(c => paperStatus(c, today) === 'AVAILABLE').length, total: copies.length })
    sections.set(sectionKey, section)
  }
  return [...sections.values()].sort((a, b) => grouping === 'EXPIRATION' ? a.key.localeCompare(b.key) : a.title.localeCompare(b.title)).map(s => ({ ...s, offers: s.offers.sort((a, b) => a.copies[0].description.localeCompare(b.copies[0].description) || (a.copies[0].expirationDate || '9999').localeCompare(b.copies[0].expirationDate || '9999') || a.copies[0].couponId.localeCompare(b.copies[0].couponId)) }))
}
export const couponAssignments = (data: Workspace, id: string) => data.plans.filter(p => p.items.some(i => i.clippedCouponIds.includes(id)) || p.plan.scenario?.coupons.some(c => c.savedCouponId === id)).length
