import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiClient, ApiError } from '../../api/client'
import type { Earning } from '../../types/platform'
import { EarningStatusForm } from './EarningStatusForm'
import { rewardChoices, rewardStatusLabel } from './rewardStatus'
const earning: Earning = { earningId: 'earning-test', provider: 'Fetch', kind: 'THIRD_PARTY_REBATE', offerReference: null, scope: 'RECEIPT', lineItemIds: [], expected: { kind: 'POINTS', amount: '25.000', currency: null, program: 'Fetch' }, confirmed: null, status: 'EXPECTED', valuation: { amount: '0.025000', currency: 'USD' } }
function form(value = earning, enabled = true) {
  const api = new ApiClient('test'); const mutate = vi.spyOn(api, 'mutate').mockResolvedValue({}); const saved = vi.fn()
  render(<EarningStatusForm earning={value} receiptId="receipt-test" revision="7" api={api} enabled={enabled} onSaved={saved} onClose={vi.fn()} />)
  return { mutate, saved, user: userEvent.setup() }
}
describe('simplified reward statuses', () => {
  it('labels program-specific statuses without rewriting legacy codes', () => {
    expect(rewardChoices({ kind: 'REGISTER_REWARD' }).map(c => c.text)).toEqual(['Expected', 'Printed at register', 'Rejected'])
    expect(rewardChoices(earning).map(c => c.value)).toEqual(['EXPECTED', 'CREDITED', 'REJECTED'])
    expect(rewardStatusLabel({ kind: 'REGISTER_REWARD', status: 'PAID' })).toBe('Printed at register')
    expect(rewardStatusLabel({ ...earning, status: 'CREDITED' })).toBe('Added to program account')
  })
  it('marks the recorded amount received without duplicate confirmation fields', async () => {
    const { user, mutate } = form(); await user.selectOptions(screen.getByLabelText('Reward status'), 'CREDITED')
    expect(screen.queryByLabelText('Confirm received amount')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Confirm unit')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Save reward status' }))
    expect(mutate).toHaveBeenCalledWith('/receipts/receipt-test/earnings/earning-test/status', 'PUT', { status: 'CREDITED', amount: '25.000', unit: 'points', confirmAmountAndUnit: true }, expect.any(String), '7')
  })
  it('keeps legacy approval visible but unavailable as a new standard choice', () => {
    form({ ...earning, status: 'APPROVED' }); expect(screen.getByRole('option', { name: /Approved.*retained/ })).toBeDisabled(); expect(screen.getByRole('button', { name: 'Save reward status' })).toBeDisabled()
    expect(screen.queryByRole('option', { name: /^Submitted/ })).not.toBeInTheDocument()
  })
  it('submits an optional rejection reason without changing amounts', async () => {
    const { user, mutate } = form(); await user.selectOptions(screen.getByLabelText('Reward status'), 'REJECTED'); await user.type(screen.getByLabelText('Rejection reason (optional)'), 'Not issued')
    await user.click(screen.getByRole('button', { name: 'Save reward status' })); expect(mutate).toHaveBeenCalledWith(expect.any(String), 'PUT', { status: 'REJECTED', rejectionReason: 'Not issued' }, expect.any(String), '7')
  })
  it('retains identical request and revision for uncertain retries', async () => {
    const { user, mutate } = form(); mutate.mockRejectedValueOnce(new ApiError(0, 'Uncertain outcome'))
    await user.selectOptions(screen.getByLabelText('Reward status'), 'REJECTED'); await user.click(screen.getByRole('button', { name: 'Save reward status' })); await user.click(screen.getByRole('button', { name: 'Retry same request' }))
    expect(mutate.mock.calls[0]).toEqual(mutate.mock.calls[1])
  })
  it('disables editing when write access or connectivity is unavailable', () => { form(earning, false); expect(screen.getByLabelText('Reward status')).toBeDisabled() })
})
