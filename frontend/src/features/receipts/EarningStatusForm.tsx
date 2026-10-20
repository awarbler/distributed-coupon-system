import { useState } from 'react'
import type { FormEvent } from 'react'
import { ApiClient, ApiError } from '../../api/client'
import type { Earning } from '../../types/platform'
import { Notice } from '../../components/ui'
import { rewardChoices, rewardStatusLabel } from './rewardStatus'

export function EarningStatusForm({ earning, receiptId, revision, api, enabled, onSaved, onClose }: { earning: Earning; receiptId: string; revision: string; api: ApiClient; enabled: boolean; onSaved: () => void; onClose: () => void }) {
  const [status, setStatus] = useState(earning.status)
  const [amount, setAmount] = useState(''), [unit, setUnit] = useState(''), [confirm, setConfirm] = useState(false), [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false), [error, setError] = useState('')
  const [pending, setPending] = useState<{ key: string; body: unknown } | null>(null)
  const benefit = earning.confirmed || earning.expected
  const originalUnit = benefit?.kind === 'POINTS' ? 'points' : benefit?.currency || ''
  const receiving = status === 'CREDITED' || status === 'PAID'
  const choices = rewardChoices(earning)
  async function save(event: FormEvent) {
    event.preventDefault(); if (!enabled) return
    setError(''); setBusy(true)
    const request = pending || { key: crypto.randomUUID(), body: { status, ...(receiving ? { amount, unit, confirmAmountAndUnit: confirm } : {}), ...(status === 'REJECTED' && reason.trim() ? { rejectionReason: reason.trim() } : {}) } }
    setPending(request)
    try { await api.mutate(`/receipts/${receiptId}/earnings/${earning.earningId}/status`, 'PUT', request.body, request.key, revision); onSaved() }
    catch (e) { setError((e as Error).message); if (e instanceof ApiError && e.status >= 400 && e.status < 500 && e.status !== 409) setPending(null) }
    finally { setBusy(false) }
  }
  return <form className="earning-status-form" aria-label={`Update reward status for ${earning.provider}`} onSubmit={save}>
    <h3>Update reward status</h3>
    <p className="muted">{earning.kind === 'REGISTER_REWARD' ? 'Printing receives an RR coupon. Redeeming it later is a separate event; no Walgreens Cash is deposited.' : 'Record a provider account credit. External cash payouts are not handled here.'}</p>
    {error && <div className="error" role="alert">{error}</div>}
    {pending && !busy && <Notice>The outcome may be unresolved. Retry retains the same request and revision. Close and refresh to review before a different change.</Notice>}
    <fieldset disabled={!enabled || busy || !!pending}>
      <label>Reward status<select aria-label="Reward status" value={status} onChange={e => { setStatus(e.target.value); setAmount(''); setUnit(''); setConfirm(false) }}>
        {!choices.some(c => c.value === earning.status) && <option value={earning.status} disabled>{rewardStatusLabel(earning)} — retained</option>}
        {choices.map(c => <option key={c.value} value={c.value}>{c.text}</option>)}
      </select></label>
      {receiving && <><p>Recorded amount: {benefit?.amount} {originalUnit}. Original quantities and valuations will be preserved.</p><label>Confirm received amount<input required inputMode="decimal" pattern="[0-9]+(\.[0-9]+)?" value={amount} onChange={e => setAmount(e.target.value)} /></label><label>Confirm unit<select aria-label="Confirm unit" required value={unit} onChange={e => setUnit(e.target.value)}><option value="">Select the original unit</option><option value={originalUnit}>{originalUnit}</option></select></label><label className="check"><input type="checkbox" required checked={confirm} onChange={e => setConfirm(e.target.checked)} />I confirm this amount and unit were {earning.kind === 'REGISTER_REWARD' ? 'printed at the register' : 'added to the provider account'}.</label></>}
      {status === 'REJECTED' && <><label>Rejection reason (optional)<textarea maxLength={1000} value={reason} onChange={e => setReason(e.target.value)} /></label><p className="muted">Expected and previously recorded amounts remain in the record. Rejected rewards do not increase balances or savings.</p></>}
    </fieldset>
    <div className="form-actions"><button type="button" className="secondary" disabled={busy} onClick={onClose}>{pending ? 'Close and refresh' : 'Cancel'}</button><button className="primary" disabled={!enabled || busy || (!pending && status === earning.status)}>{busy ? 'Saving…' : pending ? 'Retry same request' : 'Save reward status'}</button></div>
  </form>
}
