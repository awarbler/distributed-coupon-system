import { parse } from 'lossless-json'
import type { Workspace, Receipt, History, Totals } from '../types/platform'

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
  async workspace(signal?: AbortSignal) {
    const value = await this.get<Workspace>('/workspace', signal)
    if (!value || typeof value.canWrite !== 'boolean' || !value.summaries ||
      !['receipts', 'plans', 'coupons', 'deals', 'rebates', 'inventory', 'balances', 'reportedBalances', 'movements', 'promotions'].every(key => Array.isArray(value[key as keyof Workspace])))
      throw new ApiError(502, 'The server returned an incompatible workspace contract. Start the updated Java API.')
    return value
  }
  receipt(id: string, signal?: AbortSignal) { return this.get<Receipt>(`/receipts/${encodeURIComponent(id)}`, signal) }
  history(id: string, signal?: AbortSignal) { return this.get<History[]>(`/receipts/${encodeURIComponent(id)}/history`, signal) }
  totals(id: string, signal?: AbortSignal) { return this.get<Totals>(`/receipts/${encodeURIComponent(id)}/totals`, signal) }
  async evidence(receipt: string, id: string) { return (await this.request(`/receipts/${encodeURIComponent(receipt)}/evidence/${encodeURIComponent(id)}`)).blob() }
  async mutate(path: string, method: 'POST' | 'PUT', body: unknown, key: string, revision?: string): Promise<unknown> {
    const response = await this.request(path, { method, body: JSON.stringify(body), headers: {
      'Content-Type': 'application/json', 'Idempotency-Key': key, ...(revision ? { 'If-Match-Revision': revision } : {}),
    } })
    return decode(await response.text())
  }
}
