import { describe, expect, it, vi } from 'vitest'
import { ApiClient, ApiError, decode } from './client'
import { decimal, integer, money } from './format'
import { workspace } from './fixtures.test-support'
describe('exact API boundary', () => {
  it('retains decimal and large revision lexemes', () => {
    expect(decode('{"price":0.10000000000000000001,"revision":9007199254740993}')).toEqual({ price: '0.10000000000000000001', revision: '9007199254740993' })
    expect(money('9007199254740993.125')).toBe('$9,007,199,254,740,993.13'); expect(decimal('-0.005')).toBe('−0.01'); expect(money(null)).toBe('Not recorded'); expect(money('0.00')).toBe('$0.00')
  })
  it('validates quantities', () => { expect(integer('3')).toBe(3); expect(() => integer('0')).toThrow(); expect(() => integer('2.5')).toThrow(); expect(() => integer('2147483648')).toThrow() })
  it('sends authentication, revision and retry key without saving credentials', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response('{}')); vi.stubGlobal('fetch', fetch)
    await new ApiClient('private-token').mutate('/shopping-plans/id', 'PUT', { name: 'Trip' }, 'original-key', '9007199254740993')
    expect(fetch).toHaveBeenCalledWith('/api/shopping-plans/id', expect.objectContaining({ cache: 'no-store', credentials: 'omit', headers: expect.objectContaining({ Authorization: 'Bearer private-token', 'If-Match-Revision': '9007199254740993', 'Idempotency-Key': 'original-key' }) }))
    expect(localStorage.length).toBe(0); expect(sessionStorage.length).toBe(0)
  })
  it('surfaces conflicts without automatic retry', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response('{"error":"Revision changed"}', { status: 409 })); vi.stubGlobal('fetch', fetch)
    await expect(new ApiClient('token').mutate('/shopping-plans/id', 'PUT', {}, 'key', '1')).rejects.toMatchObject({ status: 409, message: 'Revision changed' }); expect(fetch).toHaveBeenCalledTimes(1)
  })
  it('rejects incompatible responses and reports transport failures', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(new Response('{}')).mockRejectedValueOnce(new TypeError('offline')))
    await expect(new ApiClient('token').workspace()).rejects.toMatchObject({ status: 502 }); await expect(new ApiClient('token').workspace()).rejects.toBeInstanceOf(ApiError)
  })
  it('loads a workspace envelope', async () => { vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(workspace())))); expect((await new ApiClient('token').workspace()).canWrite).toBe(true) })
})
