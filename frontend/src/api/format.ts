import type { Decimal } from '../types/platform'
// Display formatting only: no financial calculations or conversion of money to Number.
export function decimal(value: Decimal | undefined, places = 2): string {
  if (value == null) return 'Not recorded'
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
