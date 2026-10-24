import { EarningStatusForm } from '../features/receipts/EarningStatusForm'
import type { ApiClient } from '../api/client'
import { useEffect, useState, useCallback } from 'react'
import { browseReceipts, receiptTime, shoppingWeekLabel } from '../api/format'
import { ArrowUpRight, CalendarDays, Check, ClipboardList, Coins, Plus, Search, ShoppingBasket, Ticket, Wallet } from 'lucide-react'
import { date, decimal, label, money, shortId } from '../api/format'
import { rewardStatusLabel } from '../features/receipts/rewardStatus'
import { AutoSizeTextArea, Badge, Empty, LinkButton, Notice, PageHeader, Panel, Unavailable, Modal } from '../components/ui'
import type { Edit } from '../features/shopping/EditDialog'
import type { Plan, PlanRow, Workspace, RewardProjection, AccountReport, RewardLink, StandaloneRR, Savings, ReceiptReport } from '../types/platform'

export type Page = 'dashboard' | 'receipts' | 'shopping' | 'coupons' | 'deals' | 'rewards' | 'inventory' | 'rebates'
interface Props {
  api?: ApiClient
  onChanged?: () => void
  data: Workspace
  page: Page
  writable: boolean
  onPage: (p: Page) => void
  onReceipt: (id: string) => void
  onEdit: (edit: Edit) => void
  onAddReceipt: () => void
  onCheckout: (plan: Plan) => void
}
export function WorkspacePages(props: Props) {
  const { page } = props
  if (page === 'dashboard') return <Dashboard {...props} />
  if (page === 'receipts') return <Receipts {...props} />
  if (page === 'shopping') return <Shopping {...props} />
  if (page === 'coupons') return <Coupons {...props} />
  if (page === 'rewards') return <Rewards {...props} />
  if (page === 'inventory') return <Inventory {...props} />
  if (page === 'rebates') return <Rebates {...props} />
  return <Deals {...props} />
}
function Dashboard({ data, writable, onPage, onEdit, onReceipt }: Props) {
  const [period, setPeriod] = useState<'month' | 'week' | 'day' | 'all'>('month')
  const [collapsed,setCollapsed]=useState<Record<string,boolean>>({})
  const toggle=(key:string)=><button type="button" className="secondary" aria-expanded={!collapsed[key]} aria-controls={`dashboard-${key}`} onClick={()=>setCollapsed({...collapsed,[key]:!collapsed[key]})}>{collapsed[key]?'Expand':'Collapse'}</button>
  const summary = data.summaries[period]
  const metrics = [
    { title: 'Regular value', value: money(summary.regularWithTax), icon: ShoppingBasket, description: 'Including recorded tax', color: 'mint' },
    { title: 'Actual out of pocket', value: money(summary.recordedCashCardPaid), icon: Wallet, description: `Recorded cash and card payments · ${summary.receipts} transactions`, color: 'peach' },
    { title: 'Confirmed benefit value', value: money(summary.newlyEarned), icon: Coins, description: 'Earned from receipts in this period; separate from account balances', color: 'lilac' },
    { title: 'Total saved', value: money(summary.totalSaved), icon: Ticket, description: summary.percentSaved == null ? 'Incomplete valuations or payment observations' : `${decimal(summary.percentSaved)}% saved, including confirmed benefits`, color: 'yellow' },
  ]
  return <>
    <PageHeader title="Make every trip count." subtitle="Your plans, purchases, and rewards. All in one place." action={<button className="primary" disabled={!writable} onClick={() => onEdit({ kind: 'plan' })}><Plus size={18} />New shopping plan</button>} />
    <div className="overview-bar"><div><strong>Your savings at a glance</strong><span>As of {date(data.asOf)}</span></div><div className="segmented" aria-label="Savings period">{(['day', 'week', 'month', 'all'] as const).map(p => <button key={p} className={period === p ? 'active' : ''} onClick={() => setPeriod(p)}>{p === 'all' ? 'All time' : label(p)}</button>)}</div></div>
    <div className="metrics">{metrics.map(m => <section className="metric" key={m.title}><div className="metric-top"><span>{m.title}</span><span className={`icon-tile ${m.color}`}><m.icon size={19} /></span></div><strong>{m.value}</strong><small>{m.description}</small></section>)}</div>
    {summary.issues.length > 0 && <Notice>{summary.issues.length} recorded information issue(s) affect these totals. Review receipt details for missing values.</Notice>}
    <div className="dashboard-grid"><Panel className={`dashboard-card${collapsed.plans ? ' is-collapsed' : ''}`} title="Your next shopping trip" subtitle="A little preparation goes a long way." action={<div className="dashboard-section-actions">{toggle('plans')}<LinkButton onClick={() => onPage('shopping')}>All plans</LinkButton></div>}><div id="dashboard-plans" hidden={!!collapsed.plans}>
      {data.plans.length ? data.plans.slice(0, 3).map(row => <button className="plan-preview" key={row.plan.planId} onClick={() => onPage('shopping')}><span className="icon-tile mint"><ClipboardList size={21} /></span><span><strong>{row.plan.name}</strong><small>{row.plan.retailer || 'Store not selected'} · {row.items.length} items</small></span><span className="preview-date">{date(row.plan.shoppingDate)}<ArrowUpRight size={17} /></span></button>) : <Empty title="Start with a shopping plan">Gather the essentials before your next trip.<button disabled={!writable} className="text-button" onClick={() => onEdit({ kind: 'plan' })}>Create your first plan <Plus size={15} /></button></Empty>}
      <div className="tip"><span className="icon-tile yellow"><Check size={20} /></span><p><strong>Plan first. Shop with confidence.</strong><br />Keep product quantities, target prices, and coupon associations together.</p></div>
     </div></Panel><Panel className={`dashboard-card${collapsed.accounts ? ' is-collapsed' : ''}`} title="Rewards to keep track of" subtitle="Calculated account balances" action={<div className="dashboard-section-actions">{toggle('accounts')}<LinkButton onClick={() => onPage('rewards')}>All accounts</LinkButton></div>}><div id="dashboard-accounts" hidden={!!collapsed.accounts}>
        {data.balances.length ? data.balances.slice(0, 4).map((b, i) => <div className="account-preview" key={b.provider}><span className={`account-avatar ${['mint', 'lilac', 'peach', 'yellow'][i % 4]}`}>{b.provider.slice(0, 1)}</span><span><strong>{b.provider}</strong><small>{b.balance.endPoints != null ? `${b.balance.endPoints} points` : 'Recorded account value'}</small></span><strong>{money(b.balance.endUsd)}</strong></div>) : <Empty title="No account records yet">Balances appear when openings and activity are recorded.</Empty>}
       </div></Panel></div>
    <Panel className={`dashboard-card${collapsed.receipts ? ' is-collapsed' : ''}`} title="Recent receipts" subtitle="The details behind your grocery spending." action={<div className="dashboard-section-actions">{toggle('receipts')}<LinkButton onClick={() => onPage('receipts')}>View receipts</LinkButton></div>}><div id="dashboard-receipts" hidden={!!collapsed.receipts}>
      {data.receipts.length ? <ReceiptTable rows={data.receipts.slice(0, 5)} onReceipt={onReceipt} /> : <Empty title="No receipts in this workspace">Your local receipts remain separate until an explicitly approved database import.</Empty>}
     </div></Panel>
  </>
}
function ReceiptTable({ rows, onReceipt }: { rows: Workspace['receipts']; onReceipt: (id: string) => void }) {
  return <div className="table-wrap"><table className="receipt-table"><thead><tr><th>Store / receipt</th><th>Date & time</th><th className="receipt-money">Receipt total</th><th className="receipt-money">Actual OOP</th><th className="receipt-money">Confirmed benefit value</th><th className="receipt-money">Total saved</th><th>Status</th><th><span className="sr-only">Details</span></th></tr></thead><tbody>{rows.map(r => <tr key={r.transactionId}><td><strong>{r.metadata?.retailer || 'Store not recorded'}</strong><small>{r.metadata?.receiptNumber || shortId(r.transactionId)}</small></td><td>{date(r.date)}<small>{receiptTime(r)}</small></td><td className="receipt-money"><span className="receipt-value">{money(r.totals.reportedDue)}</span></td><td className="receipt-money"><span className="receipt-value">{money(r.totals.paymentReview?.recordedCashCardPaid)}</span>{r.totals.paymentReview && r.totals.paymentReview.paymentEntryStatus !== 'COMPLETE' && <small>Payment details incomplete</small>}</td><td className="receipt-money"><span className="receipt-value">{money(r.totals.savings.newlyEarned)}</span></td><td className="receipt-money"><span className="receipt-value">{money(r.totals.savings.totalSaved)}</span>{r.totals.savings.totalSaved == null && <small>Savings inputs incomplete</small>}</td><td><Badge>{label(r.status)}</Badge></td><td><button className="icon-button" aria-label={`Open receipt ${r.metadata?.receiptNumber || shortId(r.transactionId)}`} onClick={() => onReceipt(r.transactionId)}><ArrowUpRight size={19} /></button></td></tr>)}</tbody></table></div>
}
function Receipts({ data, api, onChanged, onReceipt, writable, onAddReceipt }: Props) {
  const [metric,setMetric]=useState('')
  const closeMetric=useCallback(()=>setMetric(''),[])
  const [search, setSearch] = useState(''), [status, setStatus] = useState('ALL')
  const [day, setDay] = useState(''), [month, setMonth] = useState('')
  const [from, setFrom] = useState(''), [to, setTo] = useState('')
  const [rangeResult, setRangeResult] = useState<{ from: string; to: string; data: Workspace; summary: Savings | null; error: string } | null>(null)
  const rangeActive = !!(from || to)
  const validRange = !!from && !!to && from <= to
  const currentRange = rangeResult?.from === from && rangeResult?.to === to && rangeResult?.data === data ? rangeResult : null
  const rangeSummary = currentRange?.summary
  const rangeError = rangeActive && !validRange ? 'Choose a start and end date, with the start on or before the end.' : rangeActive && !api ? 'Reconnect to load date-range totals.' : currentRange?.error
  useEffect(() => {
    if (!rangeActive || !validRange || !api) return
    const controller = new AbortController()
    api.receiptSummary(from, to, controller.signal)
      .then(summary => setRangeResult({ from, to, data, summary, error: '' }))
      .catch(e => { if (e.name !== 'AbortError') setRangeResult({ from, to, data, summary: null, error: e.message }) })
    return () => controller.abort()
  }, [api, from, to, rangeActive, validRange, data])
  const [order, setOrder] = useState<'newest' | 'oldest'>('newest'), [page, setPage] = useState(0)
  const rows = browseReceipts(data.receipts.filter(r => !!search.trim() || !rangeActive || (!!r.date && (!from || r.date >= from) && (!to || r.date <= to))), { search, status: search.trim() ? 'ALL' : status, day: search.trim() ? '' : day, month: search.trim() ? '' : month, week: '', order })
  const pageSize = 5, current = Math.min(page, Math.max(0, Math.ceil(rows.length / pageSize) - 1))
  const visible = rows.slice(current * pageSize, (current + 1) * pageSize)
  const periodKey = day ? `day:${day}` : month ? `month:${month}` : null
  const summary = rangeActive ? rangeSummary : periodKey ? data.receiptPeriods?.[periodKey] ?? data.receiptPeriods?.empty : data.summaries.all
  const monthLabel = (value: string) => new Intl.DateTimeFormat('en-US', { month: 'long', year: 'numeric' }).format(new Date(`${value}-01T12:00:00`))
  const years = [...new Set([data.asOf, ...data.receipts.map(row => row.date), day].filter((value): value is string => !!value && /^\d{4}-\d{2}-\d{2}$/.test(value)).map(value => value.slice(0, 4)))].sort().reverse()
  const months = years.flatMap(year => Array.from({ length: 12 }, (_, i) => `${year}-${String(i + 1).padStart(2, '0')}`))
  const periodLabel = rangeActive ? `${date(from)} – ${date(to)}` : day ? date(day) : month ? monthLabel(month) : 'All time'
  return <>
    <PageHeader title="Your receipts" subtitle="Original records, item details, and the full revision trail." action={<button className="primary" disabled={!writable} onClick={onAddReceipt}><Plus size={16} />Add receipt</button>} />
    <div className="filters receipt-filters">
      <label className="receipt-search-label"><span>Search receipts</span><span className="search"><Search size={18} aria-hidden="true" /><input aria-label="Search store, receipt number, or date" placeholder="Store, receipt number, or date" value={search} onChange={e => { setSearch(e.target.value); setPage(0) }} /></span></label>
      <details className="receipt-more-filters"><summary>More filters — Show / Hide</summary><div className="receipt-extra-filters">
      <label>Purchase day<input type="date" value={day} onChange={e => { setFrom(''); setTo(''); setDay(e.target.value); setMonth(e.target.value.slice(0, 7)); setPage(0) }} /></label>
      <label>Month<select aria-label="Month" value={month} onChange={e => { setFrom(''); setTo(''); setMonth(e.target.value); setDay(''); setPage(0) }}><option value="">All months</option>{months.map(value => <option key={value} value={value}>{monthLabel(value)}</option>)}</select></label>
      <label>Start date<input type="date" value={from} onChange={e => { setFrom(e.target.value); setDay(''); setMonth(''); setPage(0) }} /></label>
      <label>End date<input type="date" min={from || undefined} value={to} onChange={e => { setTo(e.target.value); setDay(''); setMonth(''); setPage(0) }} /></label>
      <button className="secondary" onClick={() => { const now = new Date(); const today = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}`; setFrom(today.slice(0,7)+'-01'); setTo(today); setDay(''); setMonth(''); setPage(0) }}>Month to date</button>
      <label>Sort receipts<select aria-label="Sort receipts" value={order} onChange={e => { setOrder(e.target.value as 'newest' | 'oldest'); setPage(0) }}><option value="newest">Newest first</option><option value="oldest">Oldest first</option></select></label>
      <label>Receipt status<select aria-label="Receipt status" value={status} onChange={e => { setStatus(e.target.value); setPage(0) }}><option value="ALL">All statuses</option><option>ACTIVE</option><option>DELETED</option></select></label>
      <button className="secondary" onClick={() => { setSearch(''); setStatus('ALL'); setFrom(''); setTo(''); setDay(''); setMonth(''); setOrder('newest'); setPage(0) }}>Clear filters</button>
      </div></details>
    </div>
    <Panel className="receipt-period-panel" title={`Period totals — ${periodLabel}`} subtitle="All active receipts in this period, across every page. Search and status filters affect the list only.">
      <div className="receipt-summary-body">
      {rangeError && <Notice>{rangeError}</Notice>}
      {summary ? <div className="receipt-period-totals">
        <div><button className="text-button" aria-label="View Regular price including tax" onClick={()=>setMetric('regular')}><span>Regular price including tax</span><strong>{money(summary.regularWithTax)}</strong></button></div>
        <div><button className="text-button" aria-label="View Actual OOP" onClick={()=>setMetric('oop')}><span>Actual OOP</span><strong>{summary.recordedCashCardPaid == null ? 'Restart API to load total' : money(summary.recordedCashCardPaid)}</strong></button>{summary.recordedCashCardComplete === false && <small>Recorded cash/card subtotal; some payment amounts are missing.</small>}</div>
        <div><button className="text-button" aria-label="View Total amount saved" onClick={()=>setMetric('saved')}><span>Total amount saved</span><strong>{money(summary.totalSaved)}</strong></button></div>
        <div><button className="text-button" aria-label="View Confirmed benefits" onClick={()=>setMetric('benefits')}><span>Confirmed benefits</span><strong>{money(summary.newlyEarned)}</strong></button></div>
        <div><button className="text-button" aria-label="View Total Paid after rebates" onClick={()=>setMetric('net')}><span>Total Paid after rebates</span><strong>{money(summary.paidAfterRebates)}</strong></button></div>
        <div><span>Total % saved</span><strong>{summary.percentSaved == null ? 'Not recorded' : `${decimal(summary.percentSaved)}%`}</strong></div>
      </div> : !rangeError && <Notice>{rangeActive ? 'Loading date-range totals…' : 'Restart the updated API to load selected-period totals.'}</Notice>}
      <p className="muted">Total saved includes confirmed valued benefits. Cash/card spending excludes Walgreens Cash, RR used and gift cards.</p>
      {!!summary?.issues.length && <Notice>Recorded information is incomplete for this period. Missing inputs can leave totals unknown; review receipt details.</Notice>}
      </div>
    </Panel>
    {search.trim() && <p className="muted">Search covers all dates and statuses. Period totals still describe the selected period.</p>}
    <section className="panel" aria-label="Receipt results">
      {rows.length ? <ReceiptTable rows={visible} onReceipt={onReceipt} /> : <Empty title="No matching receipts">Try another search or period.</Empty>}
      <nav className="receipt-pagination" aria-label="Receipt pages">
        <button className="secondary" disabled={current === 0} onClick={() => setPage(current - 1)}>Previous</button>
        <span aria-live="polite">{rows.length ? `Showing ${current * pageSize + 1}–${Math.min((current + 1) * pageSize, rows.length)} of ${rows.length}` : 'No matching receipts'}</span>
        <button className="secondary" disabled={(current + 1) * pageSize >= rows.length} onClick={() => setPage(current + 1)}>Next</button>
      </nav>
    </section>
    {metric && <ReceiptMetricReport writable={writable} onChanged={onChanged} api={api} metric={metric} from={rangeActive?from:day|| (month?month+'-01':'')} to={rangeActive?to:day|| (month?new Date(Number(month.slice(0,4)),Number(month.slice(5,7)),0,12).toLocaleDateString('sv-SE'):'')} periodLabel={periodLabel} onClose={closeMetric} onReceipt={id=>{closeMetric();onReceipt(id)}} />}
    <p className="muted">Open a receipt to edit, delete, or restore it with owner access. Changes retain prior revisions and evidence; deleted receipts are excluded from active totals.</p>
    <StandaloneRewardPanel api={api} writable={writable} onChanged={onChanged}/>
  </>
}

function ReceiptMetricReport({api,metric,from,to,periodLabel,onClose,onReceipt,writable,onChanged}:{writable:boolean;onChanged?:()=>void;api?:ApiClient;metric:string;from:string;to:string;periodLabel:string;onClose:()=>void;onReceipt:(id:string)=>void}) {
  const [search,setSearch]=useState(''),[sort,setSort]=useState('newest')
  const [editingBenefit,setEditingBenefit]=useState<ReceiptReport['rows'][number]|null>(null),[refresh,setRefresh]=useState(0)
  const [result,setResult]=useState<{key:string;value?:ReceiptReport;error?:string}|null>(null)
  const key=JSON.stringify([metric,from,to,search,sort,refresh])
  useEffect(()=>{if(!api)return;const controller=new AbortController();api.receiptReport(metric,from,to,search,sort,controller.signal).then(value=>setResult({key,value})).catch(e=>{if(e.name!=='AbortError')setResult({key,error:e.message})});return()=>controller.abort()},[api,metric,from,to,search,sort,key])
  const current=result?.key===key?result:null, report=current?.value
  const title=({regular:'Regular price including tax',oop:'Actual OOP',saved:'Total amount saved',net:'Total Paid after rebates',benefits:'Confirmed and unconfirmed benefits'} as Record<string,string>)[metric]
  const total=report?({regular:report.summary.regularWithTax,oop:report.summary.recordedCashCardPaid,saved:report.summary.totalSaved,net:report.summary.paidAfterRebates,benefits:report.summary.newlyEarned} as Record<string,string|null|undefined>)[metric]:null
  return <Modal title={`${title} — ${periodLabel}`} onClose={onClose}><p>All active receipts in the selected period. Search changes the matching subtotal, not the period total.</p><div className="form-grid"><label>Search detail records<input type="search" value={search} onChange={e=>setSearch(e.target.value)} placeholder="Item, SKU, store, receipt, date or payment type" /></label><label>Sort detail records<select value={sort} onChange={e=>setSort(e.target.value)}><option value="newest">Date / time — newest first</option><option value="oldest">Date / time — oldest first</option><option value="store">Store A–Z</option><option value="item">{metric==='oop'?'Payment type A–Z':'Item A–Z'}</option></select></label></div>{!api&&<Notice>Reconnect to load detail records.</Notice>}{current?.error&&<Notice>{current.error}</Notice>}{api&&!current&&<p role="status">Loading detail records…</p>}{report&&<><div className="key-values"><div><span>Period total</span><strong>{money(total)}</strong></div><div><span>{metric==='benefits'?'Matching confirmed benefits':'Matching subtotal'}</span><strong>{money(report.matchedTotal)}</strong></div>{metric==='benefits'&&<div><span>Matching unconfirmed benefits</span><strong>{money(report.unconfirmedTotal)}</strong></div>}{metric==='oop'&&<><div><span>Period cash paid</span><strong>{money(report.summary.cash)}</strong></div><div><span>Period card paid</span><strong>{money(report.summary.card)}</strong></div><div><span>Matching cash paid</span><strong>{money(report.matchedCash)}</strong></div><div><span>Matching card paid</span><strong>{money(report.matchedCard)}</strong></div></>}</div>{metric==='regular'&&<p>Regular price × quantity before discounts. Sales tax appears as a separate row so these amounts reconcile to the total.</p>}{metric==='oop'&&<p>Cash and card payments only. Walgreens Cash, RR and gift-card funding are excluded.</p>}{metric==='saved'&&<p>Regular price including tax minus cash/card paid plus confirmed valued benefits, per receipt.</p>}{metric==='net'&&<p>Cash/card paid minus confirmed valued benefits, per receipt.</p>}<div className="table-wrap"><table><thead><tr><th>Date &amp; time</th><th>Store / receipt</th><th>{metric==='oop'?'Payment type':metric==='benefits'?'Program / promotion':'Item / receipt products'}</th>{metric==='regular'&&<><th>SKU</th><th>Quantity</th><th>Regular price each</th></>}{metric==='benefits'&&<><th>Recorded amount</th><th>Status</th><th>Action</th></>}<th>{metric==='benefits'?'Dollar value':title}</th></tr></thead><tbody>{report.rows.map((r,i)=><tr key={`${r.receiptId}-${i}`}><td>{date(r.date)}<small>{r.time||'Time not recorded'}</small></td><td>{r.store||'Store not recorded'}<button className="text-button" onClick={()=>onReceipt(r.receiptId)}>{r.receiptNumber||shortId(r.receiptId)}</button></td><td className="wrap">{r.description}</td>{metric==='regular'&&<><td>{r.sku||'—'}</td><td>{r.quantity||'—'}</td><td>{r.unitPrice==null?'—':money(r.unitPrice)}</td></>}{metric==='benefits'&&<><td>{r.nativeAmount??'Not recorded'} {r.unit}</td><td>{r.group} · {r.earning?rewardStatusLabel(r.earning):r.status&&label(r.status)}</td><td>{r.earning?<button className="secondary" disabled={!writable||!api||!r.revision} onClick={()=>setEditingBenefit(r)}>{r.group==='Confirmed'?'Update status':'Confirm / update status'}</button>:<button className="secondary" onClick={()=>onReceipt(r.receiptId)}>Review receipt footer</button>}</td></>}<td>{money(r.amount)}</td></tr>)}</tbody></table></div>{!report.rows.length&&<p>No matching detail records.</p>}{metric==='benefits'&&<p>Unconfirmed rewards remain separate and do not increase confirmed savings. Points stay in their original units alongside their dollar value. Rejected entries are excluded from both totals.</p>}{editingBenefit?.earning&&api&&<section ref={node=>{if(node){node.scrollIntoView?.({block:'center',behavior:'smooth'});node.querySelector('select')?.focus()}}}><EarningStatusForm earning={editingBenefit.earning} receiptId={editingBenefit.receiptId} revision={editingBenefit.revision!} api={api} enabled={writable} onClose={()=>setEditingBenefit(null)} onSaved={()=>{setEditingBenefit(null);setRefresh(v=>v+1);onChanged?.()}}/></section>}{!!report.summary.issues.length&&<Notice>Some recorded inputs are incomplete. Unknown totals remain Not recorded.</Notice>}</>}</Modal>
}


function PotentialRewards({ projection, total }: {
  projection: RewardProjection
  total: string | null
}) {
  const deals = projection.dealProgress
  if (deals && deals.every(deal => deal.rewardStatus === 'NOT_APPLICABLE')) {
    return <span>No separate promotional rewards</span>
  }
  return <span>
    {projection.rewardsComplete === false
      ? <>Known potential promotional rewards: {money(projection.knownPotentialRewards ?? null)} — total incomplete</>
      : <>Potential promotional rewards: {money(total)}</>}
    {projection.reviewRequired && ' — store/date review needed'}
  </span>
}

function PlanCard({
  row,
  data,
  writable,
  onEdit,
  embedded = false,
}: {
  row: PlanRow
  data: Workspace
  writable: boolean
  onEdit: Props['onEdit']
  embedded?: boolean
}) {
  const [keptForPlanning, setKeptForPlanning] = useState<string[]>([])
  // Group existing planned purchases by advertised promotion.
  // Items without a linked promotion remain individually visible.
  const groups = new Map<string, typeof row.items>()

  for (const item of row.items) {
    const key =
      item.advertisedDealId || `item:${item.plannedPurchaseId}`
    const existing = groups.get(key) || []
    existing.push(item)
    groups.set(key, existing)
  }

  const dealGroups = [...groups.entries()].map(
    ([key, items], index) => {
      const deal = data.deals.find(d => d.dealId === key)
      const merchandiseSubtotal = items.reduce((total, item) => {
        const unitPrice = Number(
          item.saleUnitPrice ??
          item.regularUnitPrice ??
          item.targetUnitPrice)
        const quantity = Number(item.quantity)

        if (!Number.isFinite(unitPrice) || !Number.isFinite(quantity)) { return total }
        return total + unitPrice * quantity
      }, 0)
      return {
        key, items, number: index + 1, merchandiseSubtotal,
        progress: row.pricing?.dealProgress?.find(progress => progress.dealId === key),
        title: deal?.title || (items.length === 1 ? items[0].
          productName : 'Unlinked products'),
        terms: deal?.interpretedTerms || '',
        linked: Boolean(deal),
      }
    })

  return (
    <Panel
      title={embedded ? '' : row.plan.name}
      subtitle={
        embedded
          ? undefined
          : `${row.plan.retailer || 'Store not selected'} · ${date(row.plan.shoppingDate)} · ${shoppingWeekLabel(row.plan.shoppingDate)} · revision ${row.plan.revision}`
      }
      action={embedded ? undefined : (
        <button
          className="secondary"
          disabled={!writable}
          onClick={() => onEdit({ kind: 'plan', plan: row.plan })}
        > Edit plan
        </button>)}
    >
      {/* DEAL GROUPS */}
      {dealGroups.length > 0 ? (
        <div
          className="stack"
          style={{ gap: 16, padding: '16px', }}>
          {dealGroups.map(group => (
            <section
              key={group.key}
              style={{ border: '1px solid #dce5dc', borderRadius: 12, padding: 18, background: '#fff', }}
            >
              {/* DEAL HEADER */}
              <div style={{ marginBottom: 14 }}>
                <h3
                  style={{
                    margin: '0 0 5px',
                    fontSize: 16,
                  }}
                >
                  Deal #{group.number} — {group.title}
                </h3>

                {group.terms && (
                  <p className="muted" style={{ margin: 0 }}>{group.terms}</p>)}

                {!group.linked && (
                  <p className="muted" style={{ margin: 0 }} > No advertised promotion linked yet.</p>)}
              </div>

              {group.progress && (
                <div className={`promotion-progress ${group.progress.thresholdStatus === 'MET' && group.progress.storeStatus === 'MATCHES' && group.progress.dateStatus === 'MATCHES' ? 'promotion-progress-complete' : 'promotion-progress-review'}`}>
                  {group.progress.requiredQuantity !== null && <p>{group.progress.qualifyingQuantity} of {group.progress.requiredQuantity} qualifying products planned.</p>}
                  {group.progress.requiredSpend !== null && <p>Qualifying spend: {group.progress.qualifyingSpend === null ? 'Unknown' : money(group.progress.qualifyingSpend)} / {money(group.progress.requiredSpend)} required.</p>}
                  {group.progress.rewardStatus === 'NOT_APPLICABLE'
                    ? <p>No separate promotional reward.</p>
                    : group.progress.rewardStatus === 'UNKNOWN'
                      ? <p>Potential reward cannot yet be calculated — enter qualifying prices.</p>
                      : <p>Potential reward: {money(group.progress.potentialReward)}</p>}
                  {group.progress.thresholdStatus === 'NOT_MET' && <p>
                    {group.progress.quantityMissing !== null
                      ? `Add ${group.progress.quantityMissing} more qualifying item(s)`
                      : `Add ${money(group.progress.spendMissing)} in qualifying spend`}
                    {' '}to potentially earn {money(group.progress.rewardIfCompleted)}.
                  </p>}
                  {group.progress.messages.map((message, index) => <p key={index}>{message}</p>)}
                  {(group.progress.storeStatus !== 'MATCHES' || group.progress.dateStatus !== 'MATCHES') && <>
                    {keptForPlanning.includes(group.key) && <p>Kept for planning. Store/date review is still required.</p>}
                    <button type="button" className="secondary" disabled={!writable}
                      onClick={() => onEdit({ kind: 'plan', plan: row.plan })}>Edit shopping plan</button>
                    {' '}<button type="button" className="secondary"
                      onClick={() => setKeptForPlanning(current => current.includes(group.key) ? current : [...current, group.key])}>Keep for planning</button>
                  </>}
                  {group.progress.thresholdStatus === 'NOT_MET' && <button type="button" className="secondary" disabled={!writable}
                    onClick={() => onEdit({ kind: 'purchase', row, initialDealId: group.key })}>Add another item</button>}
                </div>
              )}

              {/* PRODUCTS IN THIS DEAL */}
              {group.items.map(item => (
                <div
                  key={item.plannedPurchaseId}
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    flexWrap: 'wrap',
                    gap: 12,
                    padding: '12px 0',
                    borderTop: '1px solid #edf0eb',
                  }}
                >
                  {/* PRODUCT DESCRIPTION */}
                  <div
                    style={{
                      flex: '1 1 240px',
                      minWidth: 0,
                    }}
                  >
                    <strong>{item.productName}</strong>

                    <small
                      style={{
                        display: 'block',
                        marginTop: 5,
                      }}
                    >
                      Qty: {item.quantity}
                      {' · '}
                      {item.clippedCouponIds.length}{' '}
                      linked coupon(s)
                    </small>

                    {item.productCode && (
                      <small
                        style={{
                          display: 'block',
                          marginTop: 3,
                        }}
                      >
                        SKU: {item.productCode}
                      </small>
                    )}
                  </div>

                  {/* UNIT PRICES */}
                  <div
                    style={{
                      textAlign: 'right',
                      minWidth: 125,
                    }}
                  >
                    <strong>
                      {money(
                        item.saleUnitPrice ??
                        item.regularUnitPrice ??
                        item.targetUnitPrice
                      )}
                    </strong>

                    <small
                      style={{
                        display: 'block',
                        marginTop: 4,
                      }}
                    >
                      Sale unit price
                    </small>

                    <small
                      style={{
                        display: 'block',
                        marginTop: 3,
                      }}
                    >
                      Regular:{' '}
                      {money(
                        item.regularUnitPrice ??
                        item.targetUnitPrice
                      )}
                    </small>
                  </div>

                  {/* EDIT PRODUCT */}
                  <button
                    className="secondary"
                    disabled={!writable}
                    onClick={() =>
                      onEdit({
                        kind: 'purchase',
                        row,
                        item,
                      })
                    }
                  >
                    Edit
                  </button>
                </div>
              ))}
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  paddingTop: 14,
                  marginTop: 6,
                  borderTop: '2px solid #dce5dc',
                }}
              >
                <strong>Merchandise subtotal</strong>
                <strong>{money(group.merchandiseSubtotal.toFixed(2))}</strong>
              </div>
            </section>
          ))}
        </div>
      ) : (
        <Empty title="What’s on your list?">
          Add products and quantities to prepare
          this shopping trip.
        </Empty>
      )}

      {/* ADD ITEM AND MERCHANDISE ESTIMATE */}
      <div className="panel-bottom">
        <button
          className="secondary"
          disabled={!writable}
          onClick={() =>
            onEdit({
              kind: 'purchase',
              row,
            })
          }
        >
          <Plus size={16} />
          Add item
        </button>

        <span>
          Merchandise estimate{' '}
          <strong>
            {money(row.pricing?.saleTotal ?? row.estimatedMerchandise)}
          </strong>
        </span>
      </div>

      {/* SHOPPING TRIP FINANCIAL SUMMARY */}
      {row.pricing && (
        <div className="plan-totals">
          <div className="detail-row">
            Regular total
            <strong>
              {money(row.pricing.regularTotal)}
            </strong>
          </div>

          <div className="detail-row">
            Sale total / savings
            <strong>
              {money(row.pricing.saleTotal)}
              {' / '}
              {money(row.pricing.saleSavings)}
            </strong>
          </div>

          <div className="detail-row">
            Estimated coupon savings
            <strong>
              {money(row.pricing.couponSavings)}
            </strong>
          </div>

          <div className="detail-row">
            Estimated register cost (before tax)
            <strong>
              {money(row.pricing.registerCost)}
            </strong>
          </div>

          <div className="detail-row">
            <PotentialRewards projection={row.pricing} total={row.pricing.expectedRewards} />
          </div>

          <div className="detail-row">
            Estimated cost after potential rewards
            <strong>
              {row.pricing.finalCost === null ? 'Incomplete' : money(row.pricing.finalCost)}
              {row.pricing.reviewRequired && ' — conditional; store/date review needed'}
            </strong>
          </div>

          {/* BACKEND CALCULATION WARNINGS */}
          {row.pricing.issues.map((issue, index) => (
            <p className="panel-note" key={index}>
              {issue}
            </p>
          ))}
        </div>
      )}

      <p className="panel-note">
        Estimates use recorded qualifications and dates,
        before tax. Potential rewards are not earned or available balances.
      </p>
    </Panel>
  )
}

function Shopping({ data, writable, onEdit, onCheckout }: Props) {
  const [search, setSearch] = useState('')
  const [store, setStore] = useState('ALL')
  const [dateFilter, setDateFilter] = useState('ALL')
  const [selectedDate, setSelectedDate] = useState('')
  const [dateFrom, setDateFrom] = useState('')
  const [dateTo, setDateTo] = useState('')
  const [sort, setSort] = useState('NEWEST')
  const [openPlanIds, setOpenPlanIds] = useState<string[]>([])
  const stores = [...new Set(
    data.plans
      .map(row => row.plan.retailer)
      .filter((value): value is string => Boolean(value))
  )].sort()

  const filteredPlans = data.plans
    .filter(row => {
      const plan = row.plan
      const query = search.trim().toLowerCase()

      const matchesSearch =
        !query ||
        plan.name.toLowerCase().includes(query) ||
        (plan.retailer || '').toLowerCase().includes(query) ||
        row.items.some(item =>
          item.productName.toLowerCase().includes(query)
        )

      const matchesStore =
        store === 'ALL' || plan.retailer === store

      const matchesDate =
        dateFilter === 'ALL' ||
        (dateFilter === 'SPECIFIC' &&
          selectedDate !== '' &&
          plan.shoppingDate === selectedDate) ||
        (dateFilter === 'RANGE' &&
          (!dateFrom || (plan.shoppingDate != null &&
            plan.shoppingDate >= dateFrom)) &&
          (!dateTo || (plan.shoppingDate != null &&
            plan.shoppingDate <= dateTo)) &&
          plan.shoppingDate != null)

      return matchesSearch && matchesStore && matchesDate
    })
    .sort((a, b) => {
      const first = a.plan.shoppingDate || ''
      const second = b.plan.shoppingDate || ''

      if (sort === 'NAME') {
        return a.plan.name.localeCompare(b.plan.name)
      }

      if (sort === 'STORE') {
        return (a.plan.retailer || '').localeCompare(
          b.plan.retailer || ''
        )
      }

      return sort === 'OLDEST'
        ? first.localeCompare(second)
        : second.localeCompare(first)
    })

  const resetFilters = () => {
    setSearch('')
    setStore('ALL')
    setDateFilter('ALL')
    setSelectedDate('')
    setDateFrom('')
    setDateTo('')
    setSort('NEWEST')
  }

  const togglePlan = (planId: string) => {
    setOpenPlanIds(current =>
      current.includes(planId)
        ? current.filter(id => id !== planId)
        : [...current, planId]
    )
  }

  return (
    <>
      <PageHeader
        title="Shopping plans"
        subtitle="Search, organize, and manage your shopping trips."
        action={
          <button
            className="primary"
            disabled={!writable}
            onClick={() => onEdit({ kind: 'plan' })}
          >
            <Plus size={18} />
            New plan
          </button>
        }
      />

      <Panel title="Find shopping plans">
        <div className="filters">
          <label className="search">
            <Search size={18} />
            <input
              aria-label="Search shopping plans"
              placeholder="Search plan, store, or product"
              value={search}
              onChange={e => setSearch(e.target.value)}
            />
          </label>

          <select
            aria-label="Filter by store"
            value={store}
            onChange={e => setStore(e.target.value)}
          >
            <option value="ALL">All stores</option>
            {stores.map(value => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </select>

          <select
            aria-label="Filter by date"
            value={dateFilter}
            onChange={e => setDateFilter(e.target.value)}
          >
            <option value="ALL">All dates</option>
            <option value="SPECIFIC">Specific date</option>
            <option value="RANGE">Date range</option>
          </select>

          <select
            aria-label="Sort shopping plans"
            value={sort}
            onChange={e => setSort(e.target.value)}
          >
            <option value="NEWEST">Newest first</option>
            <option value="OLDEST">Oldest first</option>
            <option value="NAME">Plan name</option>
            <option value="STORE">Store name</option>
          </select>
        </div>

        {dateFilter === 'SPECIFIC' && (
          <label>
            Shopping date
            <input
              type="date"
              value={selectedDate}
              onChange={e => setSelectedDate(e.target.value)}
            />
          </label>
        )}

        {dateFilter === 'RANGE' && (
          <div className="form-grid">
            <label>
              From
              <input
                type="date"
                value={dateFrom}
                max={dateTo || undefined}
                onChange={e => setDateFrom(e.target.value)}
              />
            </label>

            <label>
              To
              <input
                type="date"
                value={dateTo}
                min={dateFrom || undefined}
                onChange={e => setDateTo(e.target.value)}
              />
            </label>
          </div>
        )}

        <div className="panel-bottom">
          <strong> {filteredPlans.length} of {data.plans.length} plans</strong>
          <button
            type="button"
            className="secondary"
            onClick={resetFilters}
          >Clear filters</button>
        </div>
      </Panel>

      <div className="stack">{filteredPlans.map(row => {
        const planId = row.plan.planId
        const isOpen = openPlanIds.includes(planId)
        return (
          <div key={planId} className="shopping-plan-entry">
            <Panel title={row.plan.name}
              subtitle={`${row.plan.retailer || 'Store not selected'} · ${date(row.plan.shoppingDate)} · ${shoppingWeekLabel(row.plan.shoppingDate)} · ${row.items.length} items`}
              action={<div className="form-actions">
                <button
                  type="button"
                  className="secondary"
                  aria-expanded={isOpen}
                  onClick={() => togglePlan(planId)}
                > {isOpen ? 'Hide details' : 'View'}
                </button>

                <button
                  type="button"
                  className="secondary"
                  disabled={!writable}
                  onClick={() => onEdit({ kind: 'plan', plan: row.plan })
                  } > Edit
                </button>
                <button
                  type="button"
                  className="secondary"
                  onClick={() => onCheckout(row.plan)}
                > Checkout </button>
              </div>
              }
            >
              <div className="shopping-plan-meta">
                Shopping date: {date(row.plan.shoppingDate)} {' · '}  Revision {row.plan.revision}
              </div>
            </Panel>

            {isOpen && (
              <div className="shopping-plan-expanded">
                <PlanCard
                  row={row}
                  data={data}
                  writable={writable}
                  onEdit={onEdit}
                  embedded />
              </div>)}</div>)})}

        {filteredPlans.length === 0 && (
          <Panel title="Shopping plans">
            <Empty title="No matching shopping plans">
              {data.plans.length
                ? 'Try another search, store, or date.'
                : 'Create your first shopping plan to get started.'}
            </Empty>
          </Panel>
        )}
      </div>

      <Unavailable>
        Item deletion and offline changes are not available yet.
        Promotion qualifications and loyalty eligibility require review.
      </Unavailable>
    </>
  )
}
function Coupons({ data, api, onChanged, writable, onEdit, onReceipt }: Props) {
  const [today] = useState(() => new Date().toISOString().slice(0, 10))
  const [search, setSearch] = useState(''); const [status, setStatus] = useState('ALL')
  const rows = data.coupons.filter(c => `${c.provider} ${c.description}`.toLowerCase().includes(search.toLowerCase()) && (status === 'ALL' || c.status === status))
  return <><PageHeader title="Coupons" subtitle="Keep your paper and digital coupons together." action={<button className="primary" disabled={!writable} onClick={() => onEdit({ kind: 'coupon' })}>Add coupon</button>} /><div className="filters"><label className="search"><Search size={18} /><input aria-label="Search coupons" placeholder="Search coupons or providers" value={search} onChange={e => setSearch(e.target.value)} /></label><select aria-label="Coupon status" value={status} onChange={e => setStatus(e.target.value)}><option value="ALL">All statuses</option>{[...new Set(data.coupons.map(c => c.status))].map(s => <option key={s}>{s}</option>)}</select></div><div className="card-grid">{rows.map(c => <article className="coupon-card" key={c.couponId}><div className="card-top"><span className="icon-tile lilac"><Ticket size={20} /></span><Badge>{c.expirationDate && c.expirationDate < today && c.status !== 'APPLIED' ? 'Expired' : c.status==='APPLIED'?'Used':label(c.status)}</Badge></div><span className="eyebrow">{c.provider}</span><h2>{c.terms?.discount === 'PERCENT' ? `${c.discountAmount}% off` : c.terms?.discount === 'FREE_ITEM' ? 'Free item' : money(c.discountAmount)}</h2><h3>{c.description}</h3><p>{label(c.couponType)} coupon · {label(c.terms?.source || 'Source unknown')}</p><p>{data.plans.filter(p => p.items.some(i => i.clippedCouponIds.includes(c.couponId))).length} plan(s) assigned · assignment is not redemption</p><div className="coupon-dates"><CalendarDays size={15} />Expires {date(c.expirationDate)}</div>{c.redeemedOn && <p>Used {date(c.redeemedOn)}{c.redeemedTransactionId&&<button className="text-button" onClick={()=>onReceipt(c.redeemedTransactionId!)}>View receipt</button>}</p>}<CatalogDelete api={api} writable={writable && c.status==='CLIPPED'} path={`/clipped-coupons/${c.couponId}`} revision={c.revision} name={c.description} onChanged={onChanged}/><button className="secondary full-width" disabled={!writable || c.status !== 'CLIPPED'} onClick={() => onEdit({ kind: 'redeem', coupon: c })}>Record as used</button></article>)}</div>{!rows.length && <Panel title="Recorded coupons"><Empty title="No coupons to show">No matching coupons are recorded in this database.</Empty></Panel>}<Unavailable>Unused coupons can be permanently deleted. Used coupons remain protected. Select clipped coupons while entering a receipt to record their deductions and mark them used together. Text qualifications require review. Expiration and one-time use are enforced by the backend when recording redemption.</Unavailable></>
}
function Deals({ data, api, writable, onChanged }: Props) { return <><PageHeader title="Deals & promotions" subtitle="Source-backed offers and recorded rebate qualification programs." /><div className="card-grid">{data.deals.map(d => <article className="deal-card" key={d.dealId}><div className="card-top"><span className="icon-tile peach"><Ticket size={20} /></span><Badge>{d.retailer || 'Store not recorded'}</Badge></div><h2>{d.title}</h2><p>{d.interpretedTerms}</p><div className="coupon-dates"><CalendarDays size={15} />{date(d.validFrom)} – {date(d.validThrough)}</div><small>{d.sourceArtifactIds.length} source artifact(s) · revision {d.revision}</small><CatalogDelete api={api} writable={writable} path={`/deals/${d.dealId}`} revision={d.revision} name={d.title} onChanged={onChanged}/></article>)}</div>{!data.deals.length && <Panel title="Advertised deals"><Empty title="No advertised deals recorded">Saved source-backed offers will appear here.</Empty></Panel>}<Panel title="Gift-card promotion progress" subtitle="Qualification records only. These amounts are not available gift-card balances.">{data.promotions.length ? data.promotions.map(p => <details className="promotion" key={p.id}><summary><span><strong>{p.name}</strong><small>{label(p.status)} · threshold {money(p.threshold)}</small></span><Badge>Potential gift card {money(p.giftCardValue)}</Badge></summary><p>{p.terms}</p>{Object.values(p.contributions).map(c => <div className="detail-row" key={c.receiptId}><div>Receipt {data.receipts.find(r => r.transactionId === c.receiptId)?.metadata?.receiptNumber || shortId(c.receiptId)}<small>Reviewed revision {c.reviewedRevision} · {c.note}</small></div><strong>Recorded contribution {money(c.reviewedAmount)}</strong></div>)}</details>) : <Empty title="No programs recorded">Promotion contributions appear after they are recorded.</Empty>}</Panel><Unavailable>Editing source-backed offers and confirming current promotion eligibility are not web operations yet. Recorded contributions are not presented as confirmed combined progress.</Unavailable></> }
function Rewards(props: Props) { return <RewardsAccounts data={props.data} api={props.api} writable={props.writable} onReceipt={props.onReceipt} onChanged={props.onChanged} /> }

function Inventory({ data }: Props) { return <><PageHeader title="Your inventory" subtitle="Recorded whole-package quantities, separate from receipt history." /><Panel title={`${data.inventory.length} inventory balances`}><div className="table-wrap"><table><thead><tr><th>Catalog product</th><th>Packages on hand</th><th>Revision</th><th>Inventory ID</th></tr></thead><tbody>{data.inventory.map(item => <tr key={item.inventoryId}><td><strong>Product {shortId(item.productId)}</strong><small>{item.productId}</small></td><td>{item.quantityOnHand}</td><td>{item.revision}</td><td><code>{shortId(item.inventoryId)}</code></td></tr>)}</tbody></table></div>{!data.inventory.length && <Empty title="No inventory balances recorded">Products are not automatically added from receipts.</Empty>}</Panel><Unavailable>Inventory adjustments require an authenticated approval workflow. The current API does not establish an independent approver, so this screen is read-only. Product names also require the catalog API.</Unavailable></> }
function Rebates({ data, onReceipt }: Props) { return <><PageHeader title="Rebates" subtitle="Track expected and confirmed benefits without counting them twice." /><Panel title="Receipt-linked rebates and points"><div className="stack">{data.receiptEarnings?.filter(row => row.earning.kind === 'THIRD_PARTY_REBATE').map(row => <div className="detail-row" key={`${row.receiptId}-${row.earning.earningId}`}><div><strong>{row.earning.provider} · {row.earning.offerReference || 'Receipt reward'}</strong><small>{rewardStatusLabel(row.earning)} · receipt {row.receiptNumber || shortId(row.receiptId)} · {label(row.receiptStatus)}</small><small>Expected {row.earning.expected?.amount} {row.earning.expected?.kind === 'POINTS' ? 'points' : row.earning.expected?.currency} · confirmed {row.earning.confirmed?.amount || 'not recorded'}</small>{row.earning.details?.payoutDestination && <small>Payout destination: {label(row.earning.details.payoutDestination)} (context only)</small>}</div><button className="text-button" onClick={() => onReceipt(row.receiptId)}>Open receipt</button></div>)}</div>{!data.receiptEarnings?.some(row => row.earning.kind === 'THIRD_PARTY_REBATE') && <p className="muted">No receipt-linked rebates yet. Open a receipt’s Rewards tab to add one.</p>}</Panel><div className="card-grid">{data.rebates.map(r => <article className="deal-card" key={r.claimId}><div className="card-top"><span className="icon-tile mint"><Coins size={20} /></span><Badge>{label(r.status)}</Badge></div><h2>{r.provider}</h2><p>{r.externalClaimRef || shortId(r.claimId)}</p><h3>Expected</h3>{r.expectedBenefits.map((b, i) => <p key={i}>{b.kind === 'POINTS' ? `${b.amount} points` : `${b.currency || ''} ${b.amount}`} · {label(b.kind)}</p>)}<h3>Confirmed</h3>{r.confirmedBenefits.length ? r.confirmedBenefits.map((b, i) => <p key={i}>{b.kind === 'POINTS' ? `${b.amount} points` : `${b.currency || ''} ${b.amount}`} · {label(b.kind)}</p>) : <p className="muted">Not confirmed</p>}<small>Receipt: {r.transactionId ? data.receipts.find(row => row.transactionId === r.transactionId)?.metadata?.receiptNumber || r.transactionId : 'Not linked'}</small><small>Submitted {date(r.submittedOn)} · fulfilled {date(r.fulfilledOn)}</small>{r.providerStatus && <p>{r.providerStatus}</p>}</article>)}</div>{!data.rebates.length && <Panel title="Rebate claims"><Empty title="No rebate claims recorded">Receipt earnings and formal rebate claims are separate records.</Empty></Panel>}<Unavailable>Receipt-linked earnings and their submission notes can be added on receipts. Standalone claim lifecycle management and external payout confirmation are not exposed here.</Unavailable></> }


export function RewardConnection({ reward, onReceipt }: { reward: RewardLink; onReceipt?: (id: string) => void }) {
  return <details><summary>View Reward</summary><p>{reward.promotion} · {money(reward.amount)} · {reward.status}</p>{reward.products?.map((p,i)=><p key={i}>{p}</p>)}<p>Earned {date(reward.date)} · {reward.time || 'Time not recorded'}<br />{onReceipt && reward.receiptId ? <button className="text-button" onClick={() => onReceipt(reward.receiptId!)}>Receipt {reward.receiptNumber || 'Not recorded'}</button> : <>{reward.receiptNumber ? `Receipt ${reward.receiptNumber}` : 'Earning receipt not recorded'}</>}</p><p>Used {money(reward.usedAmount)} on {onReceipt ? <button className="text-button" onClick={() => onReceipt(reward.spentReceiptId)}>Receipt {reward.spentReceiptNumber || 'Not recorded'}</button> : <>Receipt {reward.spentReceiptNumber}</>}</p><details><summary>Technical Details</summary><code>{reward.earningId}</code></details></details>
}
const title = (name: string) => ['PayPal','myWalgreens','P&G','MyPoints','ECB'].includes(name) ? name : name.replace(/\b[a-z]/g, c => c.toUpperCase())
// Presentation policy for Rewards only; stored native units and Java calculations remain intact.
const hasPoints = (provider: string) => ['mypoints','mypoint','fetch','pgbrand','pg','pggoodeveryday','swagbucks','swagbuck','swag','merryfield'].includes(provider.toLowerCase().replace(/[^a-z0-9]/g,''))
export function RewardsAccounts({ data, api, writable, onReceipt, onChanged }: {data: Workspace; api?: ApiClient; writable: boolean; onReceipt: (id: string) => void; onChanged?: () => void}) {
  const today = () => { const now = new Date(); return `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}` }
  const [balanceDay, setBalanceDay] = useState(() => api ? today() : data.asOf), [accountSearch, setAccountSearch] = useState('')
  const [dated, setDated] = useState<{ day: string; data: Workspace; value?: Workspace; error?: string } | null>(null)

  const [provider, setProvider] = useState(''), [from, setFrom] = useState(data.asOf.slice(0,7) + '-01'), [to, setTo] = useState(data.asOf)
  const [report,setReport]=useState<AccountReport|null>(null),[error,setError]=useState(''),[refresh,setRefresh]=useState(0)
  useEffect(() => {
    if (!api || !balanceDay || provider) return
    const controller = new AbortController()
    api.workspaceAt(balanceDay,controller.signal).then(value => setDated({day:balanceDay,data,value})).catch(e => { if(e.name!=='AbortError') setDated({day:balanceDay,data,error:e.message}) })
    return () => controller.abort()
  }, [api,balanceDay,data,provider,refresh])
  const [opening,setOpening]=useState(false),[usd,setUsd]=useState(''),[points,setPoints]=useState(''),[basis,setBasis]=useState(''),[note,setNote]=useState(''),[confirmed,setConfirmed]=useState(false),[busy,setBusy]=useState(false)
  const [pending,setPending]=useState<{body:unknown;key:string}|null>(null)
  useEffect(()=>{ if(!provider || !api)return; const controller=new AbortController(); api.account(provider,from,to,controller.signal).then(value=>{setReport(value);setError('')}).catch(e=>{if(e.name!=='AbortError')setError(e.message)});return()=>controller.abort() },[provider,from,to,api,refresh])
  const pointsAccount = hasPoints(provider)
  const showPointsColumn = data.balances.some(b=>hasPoints(b.provider))
  async function saveOpening() {
    if(!api)return;setBusy(true);setError('')
    const request=pending??{key:crypto.randomUUID(),body:{observationId:crypto.randomUUID(),provider,date:from,usd:usd||null,points:pointsAccount?(points||null):null,valuationBasis:basis,sourceNote:note,confirmedBeginningOfDay:confirmed}};setPending(request)
    try {await api.mutate('/accounts/openings','POST',request.body,request.key);setOpening(false);setPending(null);setRefresh(x=>x+1);onChanged?.()}catch(e){setError((e as Error).message);if((e as {status?:number}).status === 400)setPending(null)}finally{setBusy(false)}
  }
  const selected = dated?.day===balanceDay && dated.data===data ? dated : null
  const rewardTotals = api ? selected?.value?.rewardsOverview : data.rewardsOverview
  const balances = (api ? selected?.value?.balances ?? [] : data.balances).filter(b=>b.provider.toLowerCase().includes(accountSearch.trim().toLowerCase()))
  if(!provider) return <><PageHeader title="Rewards & Accounts" subtitle={`Month to date through ${date(balanceDay)}. Day Close includes recorded activity up to this date.`} /><div className="form-grid rewards-account-filters"><label>Search accounts<input type="search" value={accountSearch} onChange={e=>setAccountSearch(e.target.value)} placeholder="Account name" /></label><label>Balance through<input type="date" required value={balanceDay} onChange={e=>{setBalanceDay(e.target.value);setFrom(e.target.value.slice(0,7)+'-01');setTo(e.target.value)}} /></label><button className="secondary" onClick={()=>{const day=today();setBalanceDay(day);setFrom(day.slice(0,7)+'-01');setTo(day)}}>Month to date</button></div><Panel className="rewards-overview" title="Rewards Overview"><p>Balances and outstanding expected rewards through {date(balanceDay)}. Search filters account rows only.</p>{rewardTotals?<><div className="receipt-period-totals"><div><span>Total Rewards</span><strong>{money(rewardTotals.total)}</strong></div><div><span>Available Balances</span><strong>{money(rewardTotals.available)}</strong></div><div><span>Expected / Unconfirmed</span><strong>{money(rewardTotals.expected)}</strong></div></div><p>Available uses positive recorded closing balances. Expected rewards are separate and are not yet available to spend. Rejected or expired expected rewards are excluded; already-spent amounts are not added back.</p>{(rewardTotals.missingBalances.length>0||rewardTotals.missingValuations.length>0)&&<Notice>Some balances or dollar valuations are missing. Known available subtotal: {money(rewardTotals.knownAvailable)}. Known expected subtotal: {money(rewardTotals.knownExpected)}. Review {Array.from(new Set([...rewardTotals.missingBalances,...rewardTotals.missingValuations])).join(', ')}.</Notice>}</>:<p>{selected?.error||'Loading reward totals…'}</p>}</Panel><Panel className="account-balances-panel" title="Calculated Account Balances"><p>Month Start, Day Start and Day Close as of {date(balanceDay)}. Click an account to view its month-to-date activity.</p>{selected?.error && <Notice>{selected.error}</Notice>}{api&&!selected && <p>Loading selected-date balances…</p>}<div className="table-wrap"><table><thead><tr>{['Account','Month Start','Day Start','Day Close',...(showPointsColumn?['Original Points At Close']:[])].map(t=><th key={t}>{t}</th>)}</tr></thead><tbody>{balances.map(b=><tr key={b.provider}><td><button className="text-button" onClick={()=>{setFrom(balanceDay.slice(0,7)+'-01');setTo(balanceDay);setProvider(b.provider)}}>{title(b.provider)}</button></td><td>{money(b.balance.monthUsd)}</td><td>{money(b.balance.startUsd)}</td><td>{money(b.balance.endUsd)}</td>{showPointsColumn&&<td>{hasPoints(b.provider)?b.balance.endPoints??'Not recorded':'—'}</td>}</tr>)}</tbody></table></div>{!balances.length&&(!api||selected?.value)&&<p>No matching accounts.</p>}</Panel></>

  return <><button className="text-button" onClick={()=>{setProvider('');setOpening(false)}}>← All Accounts</button><PageHeader title={title(provider)} subtitle="Recorded account activity and receipt connections." /><div className="form-grid"><label>From<input type="date" required value={from} onChange={e=>setFrom(e.target.value)} /></label><label>Through<input type="date" required value={to} onChange={e=>setTo(e.target.value)} /></label></div>{error&&<Notice>{error}</Notice>}{!api&&<Notice>Reconnect to the updated API to load account details.</Notice>}
  {report && report.provider === provider && report.from === from && report.to === to && <><Panel className="account-detail-panel" title="Account Balances"><div className="key-values">{Object.entries({'Beginning Balance':report.beginning.startUsd,'Earned':report.earned,'Used / Spent':report.spent,'Ending Balance':report.ending.endUsd}).map(([text,value])=><div key={text}><span>{text}</span><strong>{money(value)}</strong></div>)}</div><p>{report.beginning.opening ? `${report.beginning.opening.date === from ? 'Recorded Opening' : 'Carried Forward'} from ${date(report.beginning.opening.date)}: ${report.beginning.opening.sourceNote}` : 'Beginning Balance Not Recorded'}</p>{!report.usdActivityComplete&&<Notice>Earned and spent dollar amounts are known subtotals; some activity lacks a saved dollar valuation.</Notice>}{pointsAccount && <div className="key-values">{Object.entries({'Beginning Points':report.beginning.startPoints,'Points Earned':report.earnedPoints,'Points Spent':report.spentPoints,'Ending Points':report.ending.endPoints}).map(([text,value])=><div key={text}><span>{text}</span><strong>{value??'Not recorded'}</strong></div>)}</div>}<p className="muted">{pointsAccount&&<>Dollar valuations of points are separate from actual conversions or payouts. </>}Transaction balances require recorded order and a known opening.</p><button className="secondary" disabled={!writable} onClick={()=>setOpening(v=>!v)}>Set Beginning Balance</button></Panel>
  {provider.trim().toLowerCase()==='ibotta' && <Panel title="Ibotta Account Credit &amp; Withdrawals"><div className="detail-row"><span>Recorded Ibotta balance at period end</span><strong>{money(report.ending.endUsd)}</strong></div><div className="detail-row"><span>Your withdrawal minimum</span><strong>$20.00</strong></div><p>Rebates marked Added to Ibotta account count as earned even while the money stays in Ibotta. A withdrawal to your bank, PayPal or gift card is a separate transfer; it must not count as another rebate. Compare this recorded balance with Ibotta before withdrawing.</p></Panel>}
  <Panel className="account-detail-panel" title="Conversions &amp; Payouts"><p className="muted">These are recorded redemption events{pointsAccount?', separate from saved points valuations':''}. Requested events are reserved; only Received confirms fulfillment.</p>{report.conversions?.length ? report.conversions.map(c=><p key={c.redemptionId}>{date(c.requestedDate)} · {pointsAccount&&<>{c.originalUnitsUsed??'Not recorded'} points · </>}Source value {money(c.sourceUsdValue)} → {c.destinationAccount}: {money(c.destinationUsdValue)} · {c.status}{c.completedDate ? ` · ${date(c.completedDate)}` : ''}</p>):<p>No recorded conversions or payouts requested in this period.</p>}</Panel><Panel className="account-activity-panel" title="Account Activity"><details open><summary>Account Activity — Show / Hide</summary><div className="table-wrap"><table><thead><tr>{['Transaction Date','Transaction Time','RFN / Order Number','Promotion Details','Total','Earned / Spent','Balance Before','Balance After',...(pointsAccount?['Points','Points Before','Points After']:[])].map(t=><th key={t}>{t}</th>)}</tr></thead><tbody>{report.activity.map((a,i)=><tr key={i}><td>{date(a.movement.date)}</td><td>{a.movement.time??'Not recorded'}</td><td>{a.movement.receiptId?<button className="text-button" onClick={()=>onReceipt(a.movement.receiptId!)}>{a.receiptNumber||'Receipt'}</button>:'External Activity'}</td><td className="wrap"><details><summary><span>{a.description}</span> — Show / Hide</summary>{pointsAccount && a.valuationBasis && <small>Saved Dollar Valuation: {money(a.movement.usd)} · {a.valuationBasis}; valuation is not a payout.</small>}{a.rewards.map(r=><RewardConnection key={`${r.earningId}-${r.spentReceiptId}`} reward={r} onReceipt={onReceipt}/>)}{!a.orderKnown&&<small>Order not confirmed; transaction balances unavailable.</small>}<details><summary>Technical Details</summary><code>{a.movement.source}</code></details></details></td><td>{money(a.movement.usd)}</td><td>{(a.movement.usd??a.movement.points??'').startsWith('-')?'Spent':'Earned / Added'}</td><td>{money(a.beforeUsd)}</td><td>{money(a.afterUsd)}</td>{pointsAccount&&<><td>{a.movement.points??'—'}</td><td>{a.beforePoints??'Not recorded'}</td><td>{a.afterPoints??'Not recorded'}</td></>}</tr>)}</tbody></table></div>{!report.activity.length&&<p>No recorded activity in this period.</p>}</details></Panel></>}
  {opening&&<Panel className="account-detail-panel" title={`Beginning-of-Day Balance — ${date(from)}`}><form onSubmit={e=>{e.preventDefault();void saveOpening()}}><fieldset disabled={busy||!!pending}><p>Use a confirmed balance before this day’s activity. Closing balances belong to the following day.</p><div className="form-grid"><label>Dollar Balance<input inputMode="decimal" value={usd} onChange={e=>setUsd(e.target.value)}/></label><>{pointsAccount&&<label>Original Points<input inputMode="decimal" value={points} onChange={e=>setPoints(e.target.value)}/></label>}</><label>{pointsAccount?'Valuation Basis':'Balance Basis'}<input required value={basis} onChange={e=>setBasis(e.target.value)}/></label><label>Source / Reason<input required value={note} onChange={e=>setNote(e.target.value)}/></label></div><label className="check"><input type="checkbox" required checked={confirmed} onChange={e=>setConfirmed(e.target.checked)}/>This is the beginning balance before activity on {date(from)}.</label></fieldset><button className="primary" disabled={busy||!writable||(!pending&&(!confirmed||!usd&&(!pointsAccount||!points)))}>{pending?'Retry Same Opening':'Save Beginning Balance'}</button></form></Panel>}
  {['Walgreens Cash','Register Rewards','ECB','Walmart Cash'].includes(provider)&&<MemberRewardEntry key={provider} provider={provider} api={api} writable={writable} day={to} onSaved={()=>{setRefresh(v=>v+1);onChanged?.()}}/>}
  {provider==='Walgreens Cash'&&<Panel className="account-detail-panel" title="Provider-Reported Observations"><details className="provider-observations"><summary>Provider-Reported Observations — Show / Hide</summary>{data.reportedBalances.filter(r=>r.date&&r.date>=from&&r.date<=to).map(r=><section key={r.transactionId}><button className="text-button" onClick={()=>onReceipt(r.transactionId)}>{date(r.date)} · {r.receiptNumber} · {r.status}</button><p>Opening {money(r.values.startingBalance)} · Earned {money(r.values.earned)} · Used {money(r.values.used)} · Closing {money(r.values.endingBalance)}</p></section>)}<p className="muted">Printed observations do not replace confirmed openings or create account movements.</p></details></Panel>}<>{provider==='Register Rewards'&&<StandaloneRewardPanel api={api} writable={writable} onChanged={()=>{setRefresh(v=>v+1);onChanged?.()}}/>}</></>
}


/** Coupon inventory entry; saving does not post an earning or require any receipt. */
export function StandaloneRewardPanel({api,writable,onChanged}:{api?:ApiClient;writable:boolean;onChanged?:()=>void}) {
 const [rows,setRows]=useState<StandaloneRR[]>([]),[refresh,setRefresh]=useState(0),[open,setOpen]=useState(false),[description,setDescription]=useState(''),[amount,setAmount]=useState(''),[earned,setEarned]=useState(''),[expires,setExpires]=useState(''),[confirmed,setConfirmed]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState('')
 const [pending,setPending]=useState<{body:unknown;key:string}|null>(null)
 useEffect(()=>{if(!api)return;const controller=new AbortController();api.standaloneRewards(controller.signal).then(setRows).catch(e=>{if(e.name!=='AbortError')setError(e.message)});return()=>controller.abort()},[api,refresh])
 async function save(){if(!api)return;setBusy(true);setError('');const request=pending??{key:crypto.randomUUID(),body:{rewardId:crypto.randomUUID(),description:description.trim(),amount:amount.trim(),earnedDate:earned||null,expirationDate:expires||null,confirmAvailable:confirmed}};setPending(request);try{await api.mutate('/rewards/register-rewards','POST',request.body,request.key);setPending(null);setOpen(false);setDescription('');setAmount('');setEarned('');setExpires('');setConfirmed(false);setRefresh(v=>v+1);onChanged?.()}catch(e){setError((e as Error).message);if((e as {status?:number}).status===400)setPending(null)}finally{setBusy(false)}}
 return <Panel className="account-detail-panel" title="Rewards Without Receipts" action={<button className="secondary" disabled={!api||!writable||busy||!!pending} onClick={()=>setOpen(!open)}>{open?'Cancel reward entry':'Add reward without a receipt'}</button>}>
  {error&&<Notice>{error}</Notice>}
  <p>Save an unused Register Reward here, then select it when matching RR used on a receipt. Unknown earning receipt and dates can stay unrecorded. Saving a coupon does not add an earning or deduction.</p>
  {open&&<form onSubmit={e=>{e.preventDefault();void save()}}><p><strong>Reward type: Register Rewards</strong></p><fieldset disabled={!writable||busy||!!pending}><div className="form-grid"><label className="field-full">Reward description<AutoSizeTextArea required value={description} onChange={e=>setDescription(e.target.value)}/></label><label>Reward amount<input required inputMode="decimal" pattern="([0-9]+([.][0-9]+)?|[.][0-9]+)" value={amount} onChange={e=>setAmount(e.target.value)}/></label><label>Earned date (optional)<input type="date" value={earned} onChange={e=>setEarned(e.target.value)}/></label><label>Expiration date (optional)<input type="date" value={expires} onChange={e=>setExpires(e.target.value)}/></label></div><label className="check"><input type="checkbox" required checked={confirmed} onChange={e=>setConfirmed(e.target.checked)}/>I confirm this is an unused RR coupon available to use.</label></fieldset><button className="primary" disabled={!writable||busy||(!pending&&!confirmed)}>{pending?'Retry same reward':'Save reward'}</button></form>}
  <div className="table-wrap"><table><thead><tr>{['Reward','Amount','Earned Date','Expiration','Status'].map(text=><th key={text}>{text}</th>)}</tr></thead><tbody>{rows.map(r=><tr key={r.rewardId}><td>{r.description}</td><td>{money(r.amount)}</td><td>{date(r.earnedDate)}</td><td>{date(r.expirationDate)}</td><td>{label(r.status)}</td></tr>)}</tbody></table></div>{!rows.length&&<p>No rewards without receipts recorded yet.</p>}
 </Panel>
}

/** Non-receipt account credits reuse the audited external movement ledger. */
export function MemberRewardEntry({provider,api,writable,day,onSaved}:{provider:string;api?:ApiClient;writable:boolean;day:string;onSaved:()=>void}) {
 const [open,setOpen]=useState(false),[date,setDate]=useState(day),[amount,setAmount]=useState(''),[description,setDescription]=useState(''),[expiration,setExpiration]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState('')
 const [pending,setPending]=useState<{key:string;body:unknown}|null>(null)
 async function save(e:React.FormEvent){e.preventDefault();if(!api)return;setBusy(true);setError('');const request=pending??{key:crypto.randomUUID(),body:{eventId:crypto.randomUUID(),provider,date,amount,description,...(provider==='ECB'&&expiration?{expirationDate:expiration}:{})}};setPending(request);try{await api.mutate('/accounts/member-rewards','POST',request.body,request.key);setPending(null);setAmount('');setDescription('');setExpiration('');setOpen(false);onSaved()}catch(ex){setError((ex as Error).message);if((ex as {status?:number}).status===400)setPending(null)}finally{setBusy(false)}}
 return <Panel className="account-detail-panel" title="Member Rewards Without Receipts"><button type="button" className="secondary" disabled={!writable||!api} onClick={()=>setOpen(!open)}>Add member reward without a receipt</button><p>Record a reward already received in {provider}. It increases the account balance without creating a receipt or changing receipt savings. Do not enter the same earning again on a receipt.</p>{provider==='Register Rewards'&&<p>For a coupon you need to match to a receipt, also use Rewards Without Receipts below. That coupon inventory entry does not add another account earning.</p>}{open&&<form onSubmit={save}>{error&&<Notice>{error}</Notice>}<fieldset disabled={busy||!!pending}><div className="form-grid"><label>Reward date<input type="date" required value={date} onChange={e=>setDate(e.target.value)}/></label><label>Member reward amount<input required inputMode="decimal" pattern="([0-9]+([.][0-9]+)?|[.][0-9]+)" value={amount} onChange={e=>setAmount(e.target.value)}/></label>{provider==='ECB'&&<label>ECB expiration date (optional)<input type="date" min={date||undefined} value={expiration} onChange={e=>setExpiration(e.target.value)}/></label>}<label className="field-full">Member reward name / description<input required value={description} onChange={e=>setDescription(e.target.value)}/></label></div></fieldset><div className="form-actions"><button type="submit" className="primary" disabled={busy||!writable}>{pending?'Retry same reward':'Save member reward'}</button></div></form>}</Panel>
}

function CatalogDelete({api,writable,path,revision,name,onChanged}:{api?:ApiClient;writable:boolean;path:string;revision:string;name:string;onChanged?:()=>void}) {
 const [busy,setBusy]=useState(false),[error,setError]=useState('')
 async function remove(){if(!api||!window.confirm(`Permanently delete ${name}? Its current plan associations will be removed.`))return;setBusy(true);setError('');try{await api.mutate(path,'DELETE',{},crypto.randomUUID(),revision);onChanged?.()}catch(ex){setError((ex as Error).message)}finally{setBusy(false)}}
 return <div className="catalog-delete">{error&&<Notice>{error}</Notice>}<button type="button" className="secondary" disabled={!api||!writable||busy} onClick={()=>void remove()}>Delete {path.startsWith('/deals/')?'deal':'coupon'}</button></div>
}
