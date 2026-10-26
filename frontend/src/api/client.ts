import { parse } from 'lossless-json'
import { integer } from './format'
import type {PlanScenario,Workspace,Receipt,History,Totals,PlanCheckout,} from '../types/platform'

export class ApiError extends Error {
  readonly status: number
  constructor(status: number, message: string) { super(message); this.status = status }
}
export function decode(text: string): unknown { return parse(text, null, { parseNumber: (lexeme) => lexeme }) }
export class ApiClient {
  private readonly token: string
  constructor(token: string) { this.token = token }
  private async request(path: string, init: RequestInit = {}): Promise<Response> {
    let response: Response
    try {
      response = await fetch(`${import.meta.env.VITE_API_BASE || '/api'}${path}`, {
        ...init, cache: 'no-store', credentials: 'omit',
        headers: { ...init.headers, Authorization: `Bearer ${this.token}` },
      })
    } catch (error) {
      if (error instanceof Error && error.name === 'AbortError') throw error
      throw new ApiError(0, 'Cannot reach the API. If you submitted a change, its outcome may be unknown. Retry the same request or refresh to reconcile.')
    }
    if (!response.ok) {
      let message = `Request failed (${response.status}).`
      try { const body = decode(await response.text()) as { error?: string }; if (body.error) message = body.error } catch { /* Non-JSON proxy error. */ }
      if (response.status === 404 && path === '/workspace') message = 'This API does not provide workspace views yet. Start the updated Java backend, then reconnect.'
      throw new ApiError(response.status, message)
    }
    return response
  }
  private async get<T>(path: string, signal?: AbortSignal): Promise<T> { return decode(await (await this.request(path, { signal })).text()) as T }
  payout(id:string){return this.get<{revision:string;payout:{status:string}}>(`/accounts/payouts/${encodeURIComponent(id)}`)}
  memberReward(id:string){return this.get<{amount:string;points:string|null;provider:string}>(`/accounts/member-rewards/${encodeURIComponent(id)}`)}
  async workspace(signal?: AbortSignal) {
    const value = await this.get<Workspace>('/workspace', signal)
    if (!value || typeof value.canWrite !== 'boolean' || !value.summaries ||
      !['receipts', 'plans', 'coupons', 'deals', 'rebates', 'inventory', 'balances', 'reportedBalances', 'movements', 'promotions'].every(key => Array.isArray(value[key as keyof Workspace])))
      throw new ApiError(502, 'The server returned an incompatible workspace contract. Start the updated Java API.')
    return value
  }
  checkout(
  planId: string,
  signal?: AbortSignal
): Promise<PlanCheckout> {
  return this.get<PlanCheckout>(
    `/shopping-plans/${encodeURIComponent(planId)}/checkout`,
    signal
  )
}

saveCheckout(
  planId: string,
  values: Record<string, string | null>,
  revision: string,
  key: string
): Promise<PlanCheckout> {
  return this.mutate(
    `/shopping-plans/${encodeURIComponent(planId)}/checkout`,
    'PUT',
    values,
    key,
    revision
  ) as Promise<PlanCheckout>
}
  workspaceAt(asOf: string, signal?: AbortSignal) { return this.get<Workspace>(`/workspace?asOf=${encodeURIComponent(asOf)}`, signal) }
  account(provider: string, from: string, to: string, signal?: AbortSignal) { return this.get<import('../types/platform').AccountReport>(`/accounts/activity?provider=${encodeURIComponent(provider)}&from=${from}&to=${to}`, signal) }
  async standaloneRewards(signal?: AbortSignal) { const value = await this.get<import('../types/platform').StandaloneRR[]>('/rewards/register-rewards',signal); if(!Array.isArray(value))throw new ApiError(502,'Restart the updated API to load rewards without receipts.'); return value }
  receiptReport(metric: string, from: string, to: string, search: string, sort: string, signal?: AbortSignal) { return this.get<import('../types/platform').ReceiptReport>(`/receipts/report?${new URLSearchParams({metric,from,to,search,sort})}`,signal) }
  receiptSummary(from: string, to: string, signal?: AbortSignal) { return this.get<import('../types/platform').Savings>(`/receipts/summary?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`, signal) }
  rrMatches(id: string, signal?: AbortSignal) { return this.get<import('../types/platform').RRMatchUse[]>(`/receipts/${encodeURIComponent(id)}/rr-matches`,signal) }
  receipt(id: string, signal?: AbortSignal) { return this.get<Receipt>(`/receipts/${encodeURIComponent(id)}`, signal) }
  history(id: string, signal?: AbortSignal) { return this.get<History[]>(`/receipts/${encodeURIComponent(id)}/history`, signal) }
  totals(id: string, signal?: AbortSignal) { return this.get<Totals>(`/receipts/${encodeURIComponent(id)}/totals`, signal) }
  async receiptDraft(id:string):Promise<{draftId:string;revision:string;payload:string}>{return decode(await (await this.request(`/receipt-drafts/${encodeURIComponent(id)}`)).text()) as {draftId:string;revision:string;payload:string}}
  async evidence(receipt: string, id: string) { return (await this.request(`/receipts/${encodeURIComponent(receipt)}/evidence/${encodeURIComponent(id)}`)).blob() }
  async mutate(path: string, method: 'POST' | 'PUT' | 'DELETE', body: unknown, key: string, revision?: string): Promise<unknown> {
    const response = await this.request(path, { method, body: JSON.stringify(body), headers: {
      'Content-Type': 'application/json', 'Idempotency-Key': key, ...(revision ? { 'If-Match-Revision': revision } : {}),
    } })
    return decode(await response.text())
  }
}

// Normalize optional deal inputs without performing financial arithmetic.
export const zeroScenarioAmount=(amount:string)=>/^(?:0+(?:[.]0*)?|[.]0+)$/.test(amount.trim())
export const activeScenarioCoupons=(coupons:PlanScenario['coupons'])=>coupons.filter(c=>(c.description.trim()||c.amount.trim())&&!zeroScenarioAmount(c.amount))
export function scenarioRequest(value:PlanScenario) {
 const decimal=/^(?:[0-9]+(?:[.][0-9]+)?|[.][0-9]+)$/
 const moneyField=(amount:string,label:string)=>{if(!decimal.test(amount.trim()))throw new Error(`${label}: enter a valid amount.`);return amount.trim()}
 return {...value,paymentMethod:value.payments?.find(p=>['CASH','CARD'].includes(p.type))?.type??value.paymentMethod,items:value.items.map((x,i)=>{if(!x.name.trim())throw new Error(`Product ${i+1}: enter its description.`);return {...x,sku:x.sku||null,sale:x.sale?moneyField(x.sale,`Product ${i+1} sale price`):null,regular:moneyField(x.regular,`Product ${i+1} regular price`),quantity:integer(String(x.quantity))}}),coupons:activeScenarioCoupons(value.coupons).map((c,i)=>{if(!c.description.trim())throw new Error(`Coupon ${i+1}: enter its description or clear the amount to leave it unused.`);return {...c,amount:moneyField(c.amount,`Coupon ${i+1} amount`),requiredQuantity:integer(String(c.requiredQuantity))}}),promotions:value.promotions.filter(p=>(p.description.trim()||p.rewardAmount.trim())&&!zeroScenarioAmount(p.rewardAmount)).map((p,i)=>{if(!p.description.trim())throw new Error(`Rebate ${i+1}: enter its description or clear the amount to leave it unused.`);return {...p,rewardAmount:moneyField(p.rewardAmount,`Rebate ${i+1} amount`),requiredQuantity:integer(String(p.requiredQuantity).trim()||'0',0),requiredSpend:moneyField(p.requiredSpend.trim()||'0',`Rebate ${i+1} required spend`)}}),payments:value.payments?.filter(p=>p.amount.trim()).map((p,i)=>({...p,amount:moneyField(p.amount,`Payment ${i+1} amount`)})),tax:value.taxRate==null?moneyField(value.tax,'Tax amount'):value.tax,taxRate:value.taxRate==null?null:moneyField(value.taxRate,'Tax rate'),manualOop:value.manualOop||null}
}
