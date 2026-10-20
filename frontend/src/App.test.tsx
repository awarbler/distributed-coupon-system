import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import App from './App'
import { ApiClient, ApiError } from './api/client'
import { workspace } from './api/fixtures.test-support'
import { EditDialog } from './features/shopping/EditDialog'
async function connect(readOnly = false) {
  const data = workspace(); data.canWrite = !readOnly
  const fetch = vi.fn().mockImplementation(() => Promise.resolve(new Response(JSON.stringify(data)))); vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup(); render(<App />)
  await user.type(screen.getByLabelText('API access token'), 'test-only-token'); await user.click(screen.getByRole('button', { name: 'Open my workspace' })); await screen.findByRole('heading', { name: 'Make every trip count.' })
  return { user, fetch }
}
describe('workspace UI', () => {
  it('requires a connection and presents no demo values', () => { render(<App />); expect(screen.getByRole('heading', { name: 'Welcome to your workspace' })).toBeInTheDocument(); expect(screen.queryByText('Total saved')).not.toBeInTheDocument() })
  it('renders every grocery screen and approval limitations', async () => {
    const { user } = await connect()
    for (const [link, heading] of [['Receipts', 'Your receipts'], ['Shopping plans', 'Shopping plans'], ['Coupons', 'Coupons'], ['Deals & promotions', 'Deals & promotions'], ['Rewards & accounts', 'Rewards & accounts'], ['Inventory', 'Your inventory'], ['Rebates', 'Rebates']]) { await user.click(screen.getByRole('link', { name: link })); expect(
  screen.getByRole('heading', { name: heading, level: 1 })
).toBeInTheDocument() }
    await user.click(screen.getByRole('link', { name: 'Inventory' })); expect(screen.getByText(/authenticated approval workflow/)).toBeInTheDocument(); expect(screen.queryByRole('button', { name: /adjust/i })).not.toBeInTheDocument()
  })
  it('disables read-only writes and clears the workspace on disconnect', async () => {
    const { user } = await connect(true); expect(screen.getByRole('button', { name: 'New shopping plan' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Disconnect' })); expect(screen.getByLabelText('API access token')).toHaveValue(''); expect(screen.queryByText('Due at checkout')).not.toBeInTheDocument(); expect(localStorage.length).toBe(0)
  })
  it('creates a plan with the API contract', async () => {
    const { user, fetch } = await connect(); await user.click(screen.getByRole('button', { name: 'New shopping plan' }))
    const dialog = screen.getByRole('dialog', { name: 'Create a shopping plan' }); await user.type(within(dialog).getByLabelText('Plan name'), 'My grocery trip'); await user.click(within(dialog).getByRole('button', { name: 'Save' }))
    expect(fetch).toHaveBeenCalledWith('/api/shopping-plans', expect.objectContaining({ method: 'POST', body: expect.stringContaining('My grocery trip'), headers: expect.objectContaining({ 'Idempotency-Key': expect.any(String) }) })); expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
  it('retries uncertain writes with the identical payload and key', async () => {
    const api = new ApiClient('test'); const mutate = vi.spyOn(api, 'mutate').mockRejectedValueOnce(new ApiError(0, 'Outcome uncertain')).mockResolvedValueOnce({}); const saved = vi.fn(); const user = userEvent.setup()
    render(<EditDialog edit={{ kind: 'plan' }} api={api} data={workspace()} onClose={vi.fn()} onSaved={saved} online />)
    await user.type(screen.getByLabelText('Plan name'), 'Trip'); await user.click(screen.getByRole('button', { name: 'Save' })); expect(screen.getByRole('alert')).toHaveTextContent('Outcome uncertain'); expect(screen.getByLabelText('Plan name')).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Retry same request' })); expect(mutate.mock.calls[0]).toEqual(mutate.mock.calls[1]); expect(saved).toHaveBeenCalledTimes(1)
  })
  it('reviews uncertain results without claiming a successful save', async () => {
    const api = new ApiClient('test'); vi.spyOn(api, 'mutate').mockRejectedValue(new ApiError(0, 'Outcome uncertain')); const saved = vi.fn(); const review = vi.fn(); const user = userEvent.setup()
    render(<EditDialog edit={{ kind: 'plan' }} api={api} data={workspace()} onClose={vi.fn()} onSaved={saved} onReview={review} online />)
    await user.type(screen.getByLabelText('Plan name'), 'Trip'); await user.click(screen.getByRole('button', { name: 'Save' })); await user.click(screen.getByRole('button', { name: 'Close and review current data' })); expect(saved).not.toHaveBeenCalled(); expect(review).toHaveBeenCalledOnce()
  })
  it('does not send offline writes', () => {
    const api = new ApiClient('test'); const mutate = vi.spyOn(api, 'mutate'); render(<EditDialog edit={{ kind: 'plan' }} api={api} data={workspace()} onClose={vi.fn()} onSaved={vi.fn()} online={false} />); expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled(); expect(mutate).not.toHaveBeenCalled()
  })
})
