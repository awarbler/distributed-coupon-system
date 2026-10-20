import { render, screen, within, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiClient, ApiError } from '../../api/client'
import { workspace } from '../../api/fixtures.test-support'
import { ReceiptCreate } from './ReceiptCreate'
const result = () => ({ reviewToken: 'server-reviewed', possibleDuplicates: [], evidence: [], totals: { regularMerchandise: '99.13', saleMerchandise: '2', coupons: '0', rrUsed: '0', wcUsed: '0', tax: '0', calculatedDue: '2', reportedDue: '2', difference: '0', savings: { ...workspace().summaries.all, newlyEarned: '0', paidAfterRebates: '2', totalSaved: '97.13' } } })
function setup(enabled = true) {
  const api = new ApiClient('test'); const mutate = vi.spyOn(api, 'mutate').mockImplementation(async path => path.endsWith('/preview') ? result() : {})
  const saved = vi.fn(); render(<ReceiptCreate api={api} enabled={enabled} onClose={vi.fn()} onReview={vi.fn()} onSaved={saved} />)
  return { user: userEvent.setup(), mutate, saved }
}
async function minimum(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('Store'), 'Test store'); await user.type(screen.getByLabelText('Purchase date'), '2026-10-19')
  await user.type(screen.getByLabelText('Product description 1'), 'Soap'); await user.type(screen.getByLabelText('Sale unit price 1'), '2.00000000000000000001')
  await user.type(screen.getByLabelText('Sales tax'), '0'); await user.type(screen.getByLabelText('Reported total due at register'), '2')
}
describe('web receipt creation', () => {
  it('reviews Java totals before creating, retaining exact decimal strings and stable identifiers', async () => {
    const { user, mutate, saved } = setup(); await minimum(user); await user.click(screen.getByRole('button', { name: 'Review calculated totals' }))
    expect(screen.getByRole('dialog', { name: 'Review new receipt' })).toBeInTheDocument(); expect(screen.getByText('$99.13')).toBeInTheDocument(); expect(saved).not.toHaveBeenCalled()
    const body = mutate.mock.calls[0][2] as { transactionId: string; items: { unitPrice: string; quantity: number; lineItemId: string }[] }
    expect(body.items[0].unitPrice).toBe('2.00000000000000000001'); expect(body.items[0].quantity).toBe(1)
    await user.click(screen.getByRole('button', { name: 'Save receipt' })); expect(mutate.mock.calls[1][2]).toEqual({ ...body, reviewToken: 'server-reviewed', acknowledgeDifference: false }); expect(saved).toHaveBeenCalledWith(body.transactionId)
  })
  it('keeps edits local until preview and requires discrepancy acknowledgment', async () => {
    const { user, mutate } = setup(); mutate.mockResolvedValueOnce({ ...result(), totals: { ...result().totals, difference: '0.01' } })
    await minimum(user); expect(mutate).not.toHaveBeenCalled(); await user.click(screen.getByRole('button', { name: 'Review calculated totals' })); expect(screen.getByRole('button', { name: 'Save receipt' })).toBeDisabled()
    await user.click(screen.getByRole('checkbox')); expect(screen.getByRole('button', { name: 'Save receipt' })).toBeEnabled()
    await user.click(screen.getByRole('button', { name: 'Back to edit' })); await user.type(screen.getByLabelText('Sales tax'), '1'); expect(screen.queryByRole('button', { name: 'Save receipt' })).not.toBeInTheDocument()
  })
  it('preserves the exact request for uncertain retries and prevents editing it', async () => {
    const { user, mutate } = setup(); await minimum(user); await user.click(screen.getByRole('button', { name: 'Review calculated totals' })); mutate.mockRejectedValueOnce(new ApiError(0, 'Uncertain result'))
    await user.click(screen.getByRole('button', { name: 'Save receipt' })); expect(screen.getByRole('button', { name: 'Back to edit' })).toBeDisabled(); await user.click(screen.getByRole('button', { name: 'Retry same save' })); expect(mutate.mock.calls[1]).toEqual(mutate.mock.calls[2])
  })
  it('blocks a possible duplicate instead of creating another historical record', async () => {
    const { user, mutate } = setup(); mutate.mockResolvedValueOnce({ ...result(), possibleDuplicates: [{ transactionId: 'existing' }] }); await minimum(user); await user.click(screen.getByRole('button', { name: 'Review calculated totals' })); expect(screen.getByRole('button', { name: 'Save receipt' })).toBeDisabled()
  })
  it('uses the simplified reward choices and sends explicit confirmation and original evidence bytes', async () => {
    const { user, mutate } = setup(); await minimum(user); await user.click(screen.getByRole('button', { name: 'Add rebate or reward' })); await user.selectOptions(screen.getByLabelText('Reward program 1'), 'REGISTER_REWARD')
    expect(within(screen.getByLabelText('Reward status 1')).queryByRole('option', { name: /Approved/ })).not.toBeInTheDocument()
    await user.type(screen.getByLabelText('Reward amount 1 (USD)'), '5'); await user.selectOptions(screen.getByLabelText('Reward status 1'), 'PAID'); await user.type(screen.getByLabelText('Confirm received reward amount 1'), '5'); await user.selectOptions(screen.getByLabelText('Confirm reward unit 1'), 'USD'); await user.click(screen.getByRole('checkbox', {name: /I confirm reward/}))
    await user.upload(screen.getByLabelText('Receipt files'), new File([new Uint8Array([1, 2, 3])], 'test.png', { type: 'image/png' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Review calculated totals' })).toBeEnabled())
    await user.click(screen.getByRole('button', { name: 'Review calculated totals' })); const body = mutate.mock.calls[0][2] as { earnings: { status: string; confirmAmountAndUnit: boolean }[]; evidence: { contentBase64: string }[] }
    expect(body.earnings[0].status).toBe('PAID'); expect(body.earnings[0].confirmAmountAndUnit).toBe(true); expect(body.evidence[0].contentBase64).toBe('AQID')
  })
  it('does not permit submission without connectivity/write access', () => { setup(false); expect(screen.getByLabelText('Store')).toBeDisabled(); expect(screen.getByRole('button', { name: 'Review calculated totals' })).toBeDisabled() })
})
