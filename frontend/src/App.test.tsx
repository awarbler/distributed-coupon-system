import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import App from './App'
import { WorkspacePages } from './pages/WorkspacePages'
import { ApiClient, ApiError } from './api/client'
import { workspace, planningRow, receiptRow } from './api/fixtures.test-support'
import { EditDialog } from './features/shopping/EditDialog'
async function connect(readOnly = false) {
  const data = workspace(); data.canWrite = !readOnly
  const fetch = vi.fn().mockImplementation((url: string) => Promise.resolve(new Response(JSON.stringify(url.endsWith('/rewards/register-rewards') ? [] : data)))); vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup(); render(<App />)
  await user.type(screen.getByLabelText('API access token'), 'test-only-token'); await user.click(screen.getByRole('button', { name: 'Open my workspace' })); await screen.findByRole('heading', { name: 'Make every trip count.' })
  return { user, fetch }
}
describe('workspace UI', () => {
  it('paginates five receipts and searches all rows by compact receipt number', async () => {
    const data = workspace()
    data.receipts = Array.from({ length: 7 }, (_, i) => receiptRow(String(i), '2026-10-19', `9:0${i} AM`))
    const user = userEvent.setup()
    render(<WorkspacePages data={data} page="receipts" writable onPage={vi.fn()} onReceipt={vi.fn()} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()} />)
    expect(within(screen.getByRole('region', {name:'Receipt results'})).getAllByRole('row')).toHaveLength(6)
    expect(screen.getByText('Showing 1–5 of 7')).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Actual OOP' })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Total saved' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Next' }))
    expect(screen.getByText('Showing 6–7 of 7')).toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('Month', { exact: true }), '2026-01')
    expect(screen.getByText('No matching receipts', { selector: 'h3' })).toBeInTheDocument()
    await user.type(screen.getByLabelText('Search store, receipt number, or date'), '00016')
    expect(screen.getByText('Showing 1–1 of 1')).toBeInTheDocument()
    expect(screen.getByText('0001-6')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()
  })
  it('shows Java period totals independently of search and pagination', async () => {
    const data = workspace()
    data.receipts = [receiptRow('1')]
    data.receiptPeriods = { empty: data.summaries.all, 'month:2026-10': { ...data.summaries.all, totalSaved: '49.72', recordedCashCardPaid: '5.96', newlyEarned: '8.74' } }
    const user = userEvent.setup()
    render(<WorkspacePages data={data} page="receipts" writable onPage={vi.fn()} onReceipt={vi.fn()} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()} />)
    await user.selectOptions(screen.getByLabelText('Month', { exact: true }), '2026-10')
    expect(screen.queryByLabelText('Week of month')).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Period totals — October 2026' })).toBeInTheDocument()
    await user.type(screen.getByLabelText('Search store, receipt number, or date'), 'missing')
    expect(screen.getByText('No matching receipts', { selector: 'h3' })).toBeInTheDocument()
    expect(screen.getByText('$49.72')).toBeInTheDocument()
    expect(screen.getByText('$5.96')).toBeInTheDocument()
  })

  it('shows cash/card spending separately from printed receipt totals', () => {
    const data = workspace()
    data.summaries.month.due = '25.96'
    data.summaries.month.recordedCashCardPaid = '5.96'
    render(<WorkspacePages data={data} page="dashboard" writable onPage={vi.fn()} onReceipt={vi.fn()} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()} />)
    const metric = screen.getByText('Actual out of pocket').closest('section')!
    expect(within(metric).getByText('$5.96')).toBeInTheDocument()
    expect(within(metric).queryByText('$25.96')).not.toBeInTheDocument()
  })

  it('requires a connection and presents no demo values', () => { render(<App />); expect(screen.getByRole('heading', { name: 'Welcome to your workspace' })).toBeInTheDocument(); expect(screen.queryByText('Total saved')).not.toBeInTheDocument() })
  it('renders every grocery screen and approval limitations', async () => {
    const { user } = await connect()
    for (const [link, heading] of [['Receipts', 'Your receipts'], ['Shopping & Deals', 'Shopping & Deals'], ['Coupons', 'Coupons'], ['Promotions & Rebates', 'Promotions & Rebates'], ['Rewards & Accounts', 'Rewards & Accounts'], ['Inventory', 'Household inventory']]) { await user.click(screen.getByRole('link', { name: link })); expect(
  screen.getByRole('heading', { name: heading, level: 1 })
).toBeInTheDocument() }
    await user.click(screen.getByRole('link',{name:'Promotions & Rebates'}))
    expect(screen.getByRole('heading',{name:'Rebates — Submission & Follow-Up'})).toBeInTheDocument()
    expect(screen.getByLabelText('Submission Filter')).toBeInTheDocument()
    expect(screen.queryByRole('link',{name:/^Rebates$/})).not.toBeInTheDocument()
    await user.click(screen.getByRole('link',{name:'Rewards & Accounts'}))
    expect(screen.queryByLabelText('Submission Filter')).not.toBeInTheDocument()
    await user.click(screen.getByRole('link', { name: 'Inventory' })); expect(screen.getByRole('button', { name: 'Import / sync receipts' })).toBeInTheDocument()
  })
  it('disables read-only writes and clears the workspace on disconnect', async () => {
    const { user } = await connect(true); expect(screen.getByRole('button', { name: 'New shopping plan' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Disconnect' })); expect(screen.getByLabelText('API access token')).toHaveValue(''); expect(screen.queryByText('Actual out of pocket')).not.toBeInTheDocument(); expect(localStorage.length).toBe(0)
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

describe('potential promotion progress', () => {
  async function showProgress(overrides: Parameters<typeof planningRow>[0] = {}) {
    const data = workspace(); const row = planningRow(overrides); data.plans = [row]
    data.deals = [{ dealId: 'deal-progress', title: 'Dove reward', interpretedTerms: 'Buy two', retailer: 'Walgreens', validFrom: '2026-10-18', validThrough: '2026-10-24', sourceArtifactIds: [], revision: '1' }]
    const onEdit = vi.fn(); const user = userEvent.setup()
    render(<WorkspacePages data={data} page="shopping" writable onPage={vi.fn()} onReceipt={vi.fn()} onEdit={onEdit} onAddReceipt={vi.fn()} onCheckout={vi.fn()} />)
    await user.click(screen.getByRole('button', { name: 'View' }))
    return { user, onEdit, row }
  }
  it('retains an incomplete offer and preselects its deal when adding an item', async () => {
    const { user, onEdit, row } = await showProgress()
    expect(screen.getByText(/1 of 2 qualifying products planned/)).toBeInTheDocument()
    expect(screen.getByText(/Add 1 more qualifying item/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Add another item' }))
    expect(onEdit).toHaveBeenCalledWith({ kind: 'purchase', row, initialDealId: 'deal-progress' })
    await user.click(screen.getByRole('button', { name: 'Edit shopping plan' }))
    expect(onEdit).toHaveBeenCalledWith({ kind: 'plan', plan: row.plan })
    await user.click(screen.getByRole('button', { name: 'Keep for planning' }))
    expect(screen.getByText(/Kept for planning. Store\/date review is still required/)).toBeInTheDocument()
  })
  it('distinguishes unknown reward totals from known zero', async () => {
    await showProgress({ rewardStatus: 'UNKNOWN', thresholdStatus: 'UNKNOWN', potentialReward: null, rewardIfCompleted: null })
    expect(screen.getByText(/Potential reward cannot yet be calculated/)).toBeInTheDocument()
    expect(screen.getByText(/Known potential promotional rewards/)).toHaveTextContent('total incomplete')
    expect(screen.getByText(/Incomplete/)).toBeInTheDocument()
  })
  it('labels sale-only offers without implying an unknown reward', async () => {
    await showProgress({ rewardStatus: 'NOT_APPLICABLE', thresholdStatus: 'NOT_APPLICABLE', requiredQuantity: null, potentialReward: null })
    expect(screen.getByText('No separate promotional reward.')).toBeInTheDocument()
    expect(screen.getByText('No separate promotional rewards')).toBeInTheDocument()
    expect(screen.queryByText(/Potential reward cannot yet/)).not.toBeInTheDocument()
  })
})

it('independently collapses and expands dashboard sections',async()=>{
 const user=userEvent.setup()
 render(<WorkspacePages data={workspace()} page="dashboard" writable onPage={vi.fn()} onReceipt={vi.fn()} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/>)
 const buttons=screen.getAllByRole('button',{name:/^Collapse /})
 expect(screen.queryByRole('button',{name:'Collapse'})).not.toBeInTheDocument()
 for(const button of buttons)expect(button.closest('h2')).not.toBeNull()
 expect(buttons).toHaveLength(new Date().toLocaleDateString('en-US',{timeZone:'America/Chicago',weekday:'long'})==='Sunday'?4:3)
 for(const button of buttons){
  const body=document.getElementById(button.getAttribute('aria-controls')!)!
  await user.click(button);expect(body).not.toBeVisible();expect(button).toHaveAttribute('aria-expanded','false')
  await user.click(button);expect(body).toBeVisible();expect(button).toHaveAttribute('aria-expanded','true')
 }
})

it('deletes unused catalog entries using their current revisions',async()=>{
 const data=workspace(),api=new ApiClient('test'),changed=vi.fn(),user=userEvent.setup(),confirm=vi.spyOn(window,'confirm').mockReturnValue(true),mutate=vi.spyOn(api,'mutate').mockResolvedValue({deleted:true})
 data.coupons=[{couponId:'coupon-test',provider:'Test',description:'Test coupon',discountAmount:'2',status:'CLIPPED',couponType:'MANUFACTURER',externalId:null,clippedOn:'2026-10-19',expirationDate:null,redeemedOn:null,redeemedTransactionId:null,revision:'3'}]
 render(<WorkspacePages data={data} api={api} onChanged={changed} page="coupons" writable onPage={vi.fn()} onReceipt={vi.fn()} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/>)
 await user.click(screen.getByRole('button',{name:'Delete coupon'}))
 expect(mutate).toHaveBeenCalledWith('/clipped-coupons/coupon-test','DELETE',{},expect.any(String),'3')
 expect(changed).toHaveBeenCalled();confirm.mockRestore()
})

it('shows the available total across all accounts without changing the four-account preview',()=>{
 const data=workspace();data.rewardsOverview={asOf:data.asOf,available:'459.03',expected:'4',total:'463.03',knownAvailable:'459.03',knownExpected:'4',expectedCount:'1',missingBalances:[],missingValuations:[]}
 render(<WorkspacePages data={data} page="dashboard" writable onPage={vi.fn()} onReceipt={vi.fn()} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/>)
 const total=screen.getByText('Total Available — All Accounts').parentElement!
 expect(within(total).getByText('$459.03')).toBeInTheDocument();expect(within(total).queryByText('$463.03')).not.toBeInTheDocument()
 expect(document.querySelectorAll('#dashboard-accounts .account-preview')).toHaveLength(Math.min(4,data.balances.length))
})

it('keeps the welcome screen original with one workspace button and its visible token label',()=>{
 render(<App/>);expect(screen.getAllByRole('button',{name:'Open my workspace'})).toHaveLength(1)
 expect(screen.queryByRole('group',{name:'Top form actions'})).not.toBeInTheDocument()
 expect(screen.getByLabelText('API access token').closest('label')).not.toHaveAttribute('data-placeholder-field')
})

it('keeps Shopping & Deals and its plan form in their original label and action layout',async()=>{
 const {user}=await connect();await user.click(screen.getByRole('link',{name:'Shopping & Deals'}))
 expect(screen.getByRole('heading',{name:'Shopping & Deals'})).toBeVisible()
 expect(screen.getByLabelText('Shopping week').closest('label')).not.toHaveAttribute('data-placeholder-field')
 expect(screen.getByLabelText('Weekly store').closest('label')).not.toHaveAttribute('data-placeholder-field')
 await user.click(screen.getByRole('button',{name:'New plan'}))
 expect(screen.queryByRole('group',{name:'Top form actions'})).not.toBeInTheDocument()
 expect(screen.getByLabelText('Plan name').closest('label')).not.toHaveAttribute('data-placeholder-field')
})
