import type { Decimal, ReceiptRow } from '../types/platform'
// Display formatting only: no financial calculations or conversion of money to Number.
export function decimal(value: Decimal | undefined, places = 2): string {
  if (value == null) return 'Not recorded'
  value = value.replace(/^(-?)\./, (_, sign: string) => `${sign}0.`)
  const match = /^(-?)(\d+)(?:\.(\d+))?$/.exec(value)
  if (!match) return value // Preserve scientific notation exactly rather than lose precision.
  const fraction = (match[3] || '').padEnd(places + 1, '0')
  let units = BigInt(match[2] + fraction.slice(0, places))
  if (fraction[places] >= '5') units += 1n
  const digits = units.toString().padStart(places + 1, '0')
  const whole = (places ? digits.slice(0, -places) : digits).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return `${match[1] && units !== 0n ? '−' : ''}${whole}${places ? `.${digits.slice(-places)}` : ''}`
}
export function money(value: Decimal | undefined): string { return value == null ? 'Not recorded' : `$${decimal(value)}` }
export function shortId(id: string): string { return id.slice(0, 8) }
export function date(value: string | null | undefined): string {
  if (!value) return 'Date not recorded'
  const parsed = new Date(value.length === 10 ? `${value}T12:00:00` : value)
  return Number.isNaN(parsed.getTime()) ? value : new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric', year: 'numeric' }).format(parsed)
}
export function label(value: string): string { return value.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, s => s.toUpperCase()) }
export function integer(value: string, min = 1): number {
  if (!/^\d+$/.test(value) || BigInt(value) < BigInt(min) || BigInt(value) > 2147483647n) throw new Error(`Enter a whole number from ${min} to 2,147,483,647.`)
  return Number(value)
}


export interface ReceiptFilters {
  search: string
  status: string
  day: string
  month: string
  week: string
  order: 'newest' | 'oldest'
}
export function matchesReceiptSearch(row: ReceiptRow, search: string): boolean {
  const normalize = (value: string) => value.normalize('NFKC').toLowerCase().replace(/[\p{Pd}\u2212]/gu, '-')
  const query = normalize(search).trim()
  if (!query) return true
  const receipt = normalize(row.metadata?.receiptNumber ?? '')
  const compactReceipt = receipt.replace(/[^a-z0-9]/g, '')
  const formattedDates = row.date ? [
    new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric', year: 'numeric' }).format(new Date(`${row.date}T12:00:00`)),
    new Intl.DateTimeFormat('en-US', { month: 'long', day: 'numeric', year: 'numeric' }).format(new Date(`${row.date}T12:00:00`)),
    `${Number(row.date.slice(5, 7))}/${Number(row.date.slice(8, 10))}/${row.date.slice(0, 4)}`,
  ] : []
  const text = normalize([row.metadata?.retailer, receipt, row.date, ...formattedDates].filter(Boolean).join(' '))
  const compactQuery = query.replace(/[^a-z0-9]/g, '')
  if (compactQuery && compactReceipt.includes(compactQuery)) return true
  return query.split(/\s+/).every(token => text.includes(token) || compactReceipt.includes(token.replace(/[^a-z0-9]/g, '')))

}
function timeKey(row: ReceiptRow): string | null {
  if (row.sortTime != null) return row.sortTime
  // Compatibility with older API responses; no financial arithmetic.
  const text = row.time?.trim() ?? ''
  const twelve = /^(\d{1,2})(?::?(\d{2}))?\s*([ap])\.?m\.?$/i.exec(text)
  const full = /^(\d{1,2}):?(\d{2})$/.exec(text)
  if (!twelve && !full) return null
  const hour = Number((twelve ?? full)![1])
  const minute = Number((twelve ?? full)![2] ?? 0)
  if (minute > 59 || (twelve ? hour < 1 || hour > 12 : hour > 23)) return null
  const normalized = twelve ? hour % 12 + (twelve[3].toLowerCase() === 'p' ? 12 : 0) : hour
  return `${String(normalized).padStart(2, '0')}:${String(minute).padStart(2, '0')}`
}
export function transactionTime(value: string | null | undefined): string {
  if (!value?.trim()) return 'Not recorded'
  const match=/^([01]?[0-9]|2[0-3]):([0-5][0-9])(?::[0-5][0-9](?:[.][0-9]+)?)?$/.exec(value.trim())
  if (!match) return value
  const hour=Number(match[1])
  return `${hour % 12 || 12}:${match[2]} ${hour >= 12 ? 'PM' : 'AM'}`
}
export function receiptTime(row: ReceiptRow): string {
  const time = timeKey(row)
  if (!time) return row.time || 'Time not recorded'
  const hour = Number(time.slice(0, 2))
  return `${hour % 12 || 12}:${time.slice(3, 5)} ${hour >= 12 ? 'PM' : 'AM'}`
}
export function browseReceipts(rows: ReceiptRow[], filters: ReceiptFilters): ReceiptRow[] {
  const direction = filters.order === 'newest' ? -1 : 1
  return rows.filter(row =>
    (filters.status === 'ALL' || row.status === filters.status) &&
    matchesReceiptSearch(row, filters.search) &&
    (!filters.day || row.date === filters.day) &&
    (!filters.month || row.date?.slice(0, 7) === filters.month) &&
    (!filters.week || (!!row.date && String(Math.ceil(Number(row.date.slice(8, 10)) / 7)) === filters.week)),
  ).sort((a, b) => {
    if (!a.date || !b.date) return a.date ? -1 : b.date ? 1 : a.transactionId.localeCompare(b.transactionId)
    const date = a.date.localeCompare(b.date)
    if (date) return direction * date
    const at = timeKey(a), bt = timeKey(b)
    if (!at || !bt) return at ? -1 : bt ? 1 : a.transactionId.localeCompare(b.transactionId)
    return direction * at.localeCompare(bt) || a.transactionId.localeCompare(b.transactionId)
  })
}


/** The owning month is the month of the Sunday on which the shopping week starts. */
export function shoppingWeek(day: string): { start: string; end: string; month: string } | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(day)) return null
  const start = new Date(`${day}T12:00:00Z`)
  if (!Number.isFinite(start.getTime()) || start.toISOString().slice(0, 10) !== day) return null
  start.setUTCDate(start.getUTCDate() - start.getUTCDay())
  const end = new Date(start)
  end.setUTCDate(end.getUTCDate() + 6)
  const startDay = start.toISOString().slice(0, 10)
  return { start: startDay, end: end.toISOString().slice(0, 10), month: startDay.slice(0, 7) }
}
export function shoppingWeekLabel(day: string | null): string {
  const week = day ? shoppingWeek(day) : null
  if (!week) return 'Shopping week not recorded'
  const format = (value: string) => new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric', year: 'numeric', timeZone: 'UTC' }).format(new Date(`${value}T12:00:00Z`))
  return `Shopping week: ${format(week.start)} – ${format(week.end)} (Sunday–Saturday)`
}

export function titleWords(value: string): string { return value.replace(/(^|\s)(\p{L})/gu, (_, space: string, letter: string) => space + letter.toUpperCase()) }

export function productDiscountLabel(product: string, kind: string): string {
  const word = product.trim().split(/\s+/)[0] || ''
  const type = ({ SALE_PROMOTION: 'Sale Promotion', MANUFACTURER: 'Manufacturer Coupon', STORE_COUPON: 'Store Coupon' } as Record<string,string>)[kind] || 'Discount'
  return titleWords([word,type].filter(Boolean).join(' '))
}

export function isWalgreensStore(value: string | null | undefined): boolean { return /^walgreens(?:\s|#|$)/i.test(value?.trim() ?? '') }

// Exact recorded-value ordering shared by account tables and shopping presentation.
export function compareRewardValues(left:string|null|undefined,right:string|null|undefined,numeric=false):number {
 if(left==null||left==='')return right==null||right===''?0:1
 if(right==null||right==='')return -1
 if(numeric&&/^-?\d+(?:[.]\d+)?$/.test(left)&&/^-?\d+(?:[.]\d+)?$/.test(right)){
  const places=Math.max(left.split('.')[1]?.length??0,right.split('.')[1]?.length??0)
  const units=(value:string)=>{const [whole,fraction='']=value.replace('-','').split('.');return BigInt(whole+fraction.padEnd(places,'0'))*(value.startsWith('-')?-1n:1n)}
  const a=units(left),b=units(right);return a<b?-1:a>b?1:0
 }
 return left.localeCompare(right,undefined,{sensitivity:'base'})
}
