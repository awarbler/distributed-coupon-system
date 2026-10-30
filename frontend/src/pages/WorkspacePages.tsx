import { FormActions } from '../components/ui'
import { QuickDealTools, SaleButtons, LiveDealSummary, RewardPicker } from '../features/shopping/QuickDealTools'
import { ShoppingWeek } from '../features/shopping/ShoppingWeek'
import { scenarioRequest, activeScenarioCoupons, zeroScenarioAmount } from '../api/client'
import { CouponForm } from '../features/shopping/CouponForm'
import type { PlanScenario, ScenarioEstimate, PlanReceiptDraft } from '../types/platform'
import { EarningStatusForm, RewardAmountEditor, StandaloneAmountEditor, RebateFollowUp } from '../features/receipts/EarningStatusForm'
import type { ApiClient } from '../api/client'
import { useEffect, useState, useCallback } from 'react'
import { browseReceipts, matchesReceiptSearch, receiptTime, transactionTime, shoppingWeekLabel } from '../api/format'
import { ArrowUpRight, ChevronDown, ChevronUp, CalendarDays, Check, ClipboardList, Coins, Plus, Search, ShoppingBasket, Ticket, Wallet } from 'lucide-react'
import { date, decimal, integer, label, money, shortId, isWalgreensStore } from '../api/format'
import { rewardStatusLabel } from '../features/receipts/rewardStatus'
import { AutoSizeTextArea, Badge, Disclosure, Empty, LinkButton, Notice, PageHeader, Panel, Unavailable, Modal } from '../components/ui'
import type { Edit } from '../features/shopping/EditDialog'
import type { Inventory as InventoryItem, Plan, PlanRow, Workspace, RewardProjection, AccountReport, RewardLink, StandaloneRR, Savings, ReceiptReport, Promotion, Receipt } from '../types/platform'

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
  onPlanReceipt?: (draft:PlanReceiptDraft)=>void
  reviewRange?:{from:string;to:string}
  onWeeklyReview?:(page: 'receipts'|'rebates',from:string,to:string)=>void
  onResumeReceiptDraft?:(id:string)=>void
}
export function WorkspacePages(props: Props) {
  const { page } = props
  const [weeklyRange,setWeeklyRange]=useState<{page:Page;from:string;to:string}|null>(null)
  const [rangePage,setRangePage]=useState(page)
  if(rangePage!==page){setRangePage(page);if(weeklyRange&&page!==weeklyRange.page)setWeeklyRange(null)}
  const scoped={...props,reviewRange:weeklyRange?.page===page?weeklyRange:undefined}
  if (page === 'dashboard') return <Dashboard {...props} onWeeklyReview={(page,from,to)=>{const target=page==='rebates'?'deals':page;setWeeklyRange({page:target,from,to});props.onPage(target)}} />
  if (page === 'receipts') return <Receipts {...scoped} />
  if (page === 'shopping') return <Shopping {...props} onWeeklyReview={(page,from,to)=>{const target=page==='rebates'?'deals':page;setWeeklyRange({page:target,from,to});props.onPage(target)}} />
  if (page === 'coupons') return <Coupons {...props} />
  if (page === 'rewards') return <Rewards {...props} />
  if (page === 'inventory') return <Inventory {...props} />
  if (page === 'rebates') return <Deals {...scoped} />
  return <Deals {...scoped} />
}
function Dashboard({ data, writable, onPage, onEdit, onReceipt,onWeeklyReview }: Props) {
  const [period, setPeriod] = useState<'month' | 'week' | 'day' | 'all'>('month')
  const [collapsed,setCollapsed]=useState<Record<string,boolean>>({})
  const sectionTitle=(key:string,text:string)=><span className="dashboard-section-title"><span>{text}</span><button type="button" className="icon-button" aria-label={`${collapsed[key]?'Expand':'Collapse'} ${text}`} aria-expanded={!collapsed[key]} aria-controls={`dashboard-${key}`} onClick={()=>setCollapsed({...collapsed,[key]:!collapsed[key]})}>{collapsed[key]?<ChevronDown size={20}/>:<ChevronUp size={20}/>}</button></span>
  const [reviewToday,setReviewToday]=useState(()=>new Date().toLocaleDateString('sv-SE',{timeZone:'America/Chicago'}))
  useEffect(()=>{const update=()=>setReviewToday(new Date().toLocaleDateString('sv-SE',{timeZone:'America/Chicago'}));const timer=setInterval(update,60000);document.addEventListener('visibilitychange',update);return()=>{clearInterval(timer);document.removeEventListener('visibilitychange',update)}},[])
  const reviewDate=new Date(`${reviewToday}T12:00:00`),reviewStart=new Date(reviewDate);reviewStart.setDate(reviewStart.getDate()-6)
  const reviewFrom=`${reviewStart.getFullYear()}-${String(reviewStart.getMonth()+1).padStart(2,'0')}-${String(reviewStart.getDate()).padStart(2,'0')}`
  const reviewReceipts=data.receipts.filter(r=>r.status==='ACTIVE'&&r.date&&r.date>=reviewFrom&&r.date<=reviewToday)
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
    {reviewDate.getDay()===0&&<Panel className="sunday-review-card" title={sectionTitle('review','Sunday Receipt & Rebate Review')} subtitle={`${date(reviewFrom)} – ${date(reviewToday)} · Past Seven Days`}><div id="dashboard-review" hidden={!!collapsed.review}><p>Today Is Review Day. {reviewReceipts.length} Active Receipts In The Past Seven Days. Check That Every Eligible Receipt Was Scanned, Received Points Were Recorded, And Rebates Were Submitted And Added To Their Accounts.</p><p className="muted">Keep Rewards You Are Still Waiting For Marked Expected. A Saved Receipt Does Not Prove It Was Scanned.</p><FormActions><button className="secondary" onClick={()=>onWeeklyReview?onWeeklyReview('receipts',reviewFrom,reviewToday):onPage('receipts')}>Check Receipts</button><button className="secondary" onClick={()=>onWeeklyReview?onWeeklyReview('rebates',reviewFrom,reviewToday):onPage('rebates')}>Check Rebates</button></FormActions></div></Panel>}
    <div className="dashboard-grid"><Panel className={`dashboard-card${collapsed.plans ? ' is-collapsed' : ''}`} title={sectionTitle('plans','Your Next Shopping Trip')} subtitle="A little preparation goes a long way." action={<div className="dashboard-section-actions"><LinkButton onClick={() => onPage('shopping')}>All plans</LinkButton></div>}><div id="dashboard-plans" hidden={!!collapsed.plans}>
      {data.plans.some(row=>!row.plan.purchasedReceiptId) ? data.plans.filter(row=>!row.plan.purchasedReceiptId).slice(0, 3).map(row => <button className="plan-preview" key={row.plan.planId} onClick={() => onPage('shopping')}><span className="icon-tile mint"><ClipboardList size={21} /></span><span><strong>{row.plan.name}</strong><small>{row.plan.retailer || 'Store not selected'} · {row.plan.scenario?.items.length??row.items.length} items</small></span><span className="preview-date">{date(row.plan.shoppingDate)}<ArrowUpRight size={17} /></span></button>) : <Empty title="Start with a shopping plan">Gather the essentials before your next trip.<button disabled={!writable} className="text-button" onClick={() => onEdit({ kind: 'plan' })}>Create your first plan <Plus size={15} /></button></Empty>}
      <div className="tip"><span className="icon-tile yellow"><Check size={20} /></span><p><strong>Plan first. Shop with confidence.</strong><br />Keep product quantities, target prices, and coupon associations together.</p></div>
     </div></Panel><Panel className={`dashboard-card${collapsed.accounts ? ' is-collapsed' : ''}`} title={sectionTitle('accounts','Rewards')} subtitle="Calculated account balances" action={<div className="dashboard-section-actions"><LinkButton onClick={() => onPage('rewards')}>All accounts</LinkButton></div>}><div id="dashboard-accounts" hidden={!!collapsed.accounts}>
        <div className="dashboard-rewards-total"><span>Total Available — All Accounts</span><strong>{money(data.rewardsOverview?.available)}</strong>{data.rewardsOverview?.available==null&&<small>Some account balances are not recorded.</small>}</div>
        {data.balances.length ? data.balances.slice(0, 4).map((b, i) => <div className="account-preview" key={b.provider}><span className={`account-avatar ${['mint', 'lilac', 'peach', 'yellow'][i % 4]}`}>{b.provider.slice(0, 1)}</span><span><strong>{b.provider}</strong><small>{b.balance.endPoints != null ? `${b.balance.endPoints} points` : 'Recorded account value'}</small></span><strong>{money(b.balance.endUsd)}</strong></div>) : <Empty title="No account records yet">Balances appear when openings and activity are recorded.</Empty>}
       </div></Panel></div>
    <Panel className={`dashboard-card${collapsed.receipts ? ' is-collapsed' : ''}`} title={sectionTitle('receipts','Recent Receipts')} subtitle="The details behind your grocery spending." action={<div className="dashboard-section-actions"><LinkButton onClick={() => onPage('receipts')}>View receipts</LinkButton></div>}><div id="dashboard-receipts" hidden={!!collapsed.receipts}>
      {data.receipts.length ? <ReceiptTable rows={data.receipts.slice(0, 5)} onReceipt={onReceipt} /> : <Empty title="No receipts in this workspace">Your local receipts remain separate until an explicitly approved database import.</Empty>}
     </div></Panel>
  </>
}
function ReceiptTable({ rows, onReceipt }: { rows: Workspace['receipts']; onReceipt: (id: string) => void }) {
  return <div className="table-wrap"><table className="receipt-table"><thead><tr><th>Store / receipt</th><th>Date & time</th><th className="receipt-money">Receipt total</th><th className="receipt-money">Actual OOP</th><th className="receipt-money">Confirmed benefit value</th><th className="receipt-money">Total saved</th><th>Status</th><th><span className="sr-only">Details</span></th></tr></thead><tbody>{rows.map(r => <tr key={r.transactionId}><td><strong>{r.metadata?.retailer || 'Store not recorded'}</strong><small>{r.metadata?.receiptNumber || shortId(r.transactionId)}</small></td><td>{date(r.date)}<small>{receiptTime(r)}</small></td><td className="receipt-money"><span className="receipt-value">{money(r.totals.reportedDue)}</span></td><td className="receipt-money"><span className="receipt-value">{money(r.totals.paymentReview?.recordedCashCardPaid)}</span>{r.totals.paymentReview && r.totals.paymentReview.paymentEntryStatus !== 'COMPLETE' && <small>Payment details incomplete</small>}</td><td className="receipt-money"><span className="receipt-value">{money(r.totals.savings.newlyEarned)}</span></td><td className="receipt-money"><span className="receipt-value">{money(r.totals.savings.totalSaved)}</span>{r.totals.savings.totalSaved == null && <small>Savings inputs incomplete</small>}</td><td><Badge>{label(r.status)}</Badge></td><td><button className="icon-button" aria-label={`Open receipt ${r.metadata?.receiptNumber || shortId(r.transactionId)}`} onClick={() => onReceipt(r.transactionId)}><ArrowUpRight size={19} /></button></td></tr>)}</tbody></table></div>
}
function SavedReceiptDrafts({data,api,writable,onChanged,onResume}:{data:Workspace;api?:ApiClient;writable:boolean;onChanged?:()=>void;onResume?:(id:string)=>void}) {
 const [error,setError]=useState(''),[busy,setBusy]=useState(false)
 async function remove(id:string,revision:string){if(!api||!window.confirm('Delete this saved draft? No saved receipt or purchase will be deleted.'))return;setBusy(true);setError('');try{await api.mutate(`/receipt-drafts/${id}`,'DELETE',{},crypto.randomUUID(),revision);onChanged?.()}catch(e){setError((e as Error).message)}finally{setBusy(false)}}
 if(!writable||!data.receiptDrafts?.length)return null
 return <Panel title="Saved receipt drafts"><details open><summary>{data.receiptDrafts.length} saved draft(s) — Show / Hide</summary>{error&&<Notice>{error}</Notice>}{data.receiptDrafts.map(d=><section className="receipt-input-row" key={d.draftId}><strong>{d.store||'Untitled receipt'} · {d.receiptNumber||'Receipt number not entered'}</strong><p>Saved {new Date(d.updatedAt).toLocaleString()}</p><FormActions><button className="secondary" disabled={busy||!onResume} onClick={()=>onResume?.(d.draftId)}>Resume draft</button><button className="secondary" disabled={busy||!api} onClick={()=>void remove(d.draftId,d.revision)}>Delete draft</button></FormActions></section>)}</details></Panel>
}
function Receipts({ data, api, onChanged, onReceipt, writable, onAddReceipt, onResumeReceiptDraft,reviewRange }: Props) {
  const [metric,setMetric]=useState('')
  const closeMetric=useCallback(()=>setMetric(''),[])
  const [search, setSearch] = useState(''), [status, setStatus] = useState('ALL')
  const [day, setDay] = useState(''), [month, setMonth] = useState('')
  const [from, setFrom] = useState(reviewRange?.from??''), [to, setTo] = useState(reviewRange?.to??'')
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
    <SavedReceiptDrafts data={data} api={api} writable={writable} onChanged={onChanged} onResume={onResumeReceiptDraft}/>
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

function Shopping({ data, api, onChanged, writable, onEdit, onCheckout, onPlanReceipt, onReceipt, onPage, onWeeklyReview }: Props) {
  const [copyPlan,setCopyPlan]=useState<Plan|null>(null)
  const [followUpTarget,setFollowUpTarget]=useState<HTMLDivElement|null>(null)
  const [quickMode,setQuickMode]=useState<'notes'|'screenshot'|null>(null)
  const [search, setSearch] = useState('')
  const [store, setStore] = useState('ALL')
  const [dateFilter, setDateFilter] = useState('ALL')
  const [selectedDate, setSelectedDate] = useState('')
  const [dateFrom, setDateFrom] = useState('')
  const [dateTo, setDateTo] = useState('')
  const [sort, setSort] = useState('NEWEST')
  const [openPlanIds, setOpenPlanIds] = useState<string[]>([])
  const [scenarioPlan,setScenarioPlan]=useState<Plan|null|undefined>(undefined)
  const [deleteError,setDeleteError]=useState('')
  const [deletingPlan,setDeletingPlan]=useState<string|null>(null)
  async function deletePlan(plan:Plan) {
    if(!api||!window.confirm(`Permanently delete “${plan.name}” and its products, scenario, checkout drafts and plan history? This cannot be undone. Saved receipts and coupon/reward records are kept.`))return
    setDeleteError('');setDeletingPlan(plan.planId)
    try{await api.mutate(`/shopping-plans/${plan.planId}`,'DELETE',{},crypto.randomUUID(),plan.revision);onChanged?.()}
    catch(e){setDeleteError((e as Error).message)}finally{setDeletingPlan(null)}
  }

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
      {scenarioPlan!==undefined&&api&&<PlanScenarioEditor initialMode={quickMode} key={scenarioPlan?.planId||'new'} plan={scenarioPlan} copyFrom={copyPlan} data={data} api={api} writable={writable} onClose={()=>{setScenarioPlan(undefined);setCopyPlan(null)}} onChanged={onChanged} onReceipt={draft=>{setScenarioPlan(undefined);onPlanReceipt?.(draft)}}/>}
      <PageHeader
        title="Shopping & Deals"
        subtitle="Search, organize, and manage your shopping trips."
        action={
          <button
            className="primary"
            disabled={!writable}
            onClick={() => {setCopyPlan(null);setScenarioPlan(null)}}
          >
            <Plus size={18} />
            New plan
          </button>
        }
      />

      {deleteError&&<Notice>{deleteError}</Notice>}
      <Panel>
        <div className="filters shopping-plan-filters">
          <label className="shopping-plan-search-field">
            Search plans
            <span className="search">
            <Search size={18} />
            <input
              aria-label="Search shopping plans"
              placeholder="Search plan, store, or product"
              value={search}
              onChange={e => setSearch(e.target.value)}
            />
            </span>
          </label>

          <label>Store<select
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
          </select></label>

          <label>Date<select
            aria-label="Filter by date"
            value={dateFilter}
            onChange={e => setDateFilter(e.target.value)}
          >
            <option value="ALL">All dates</option>
            <option value="SPECIFIC">Specific date</option>
            <option value="RANGE">Date range</option>
          </select></label>

          <label>Sort plans<select
            aria-label="Sort shopping plans"
            value={sort}
            onChange={e => setSort(e.target.value)}
          >
            <option value="NEWEST">Newest first</option>
            <option value="OLDEST">Oldest first</option>
            <option value="NAME">Plan name</option>
            <option value="STORE">Store name</option>
          </select></label>
        </div>

        {dateFilter === 'SPECIFIC' && (
          <div className="shopping-plan-date-fields"><label>
            Shopping date
            <input
              type="date"
              value={selectedDate}
              onChange={e => setSelectedDate(e.target.value)}
            />
          </label></div>
        )}

        {dateFilter === 'RANGE' && (
          <div className="form-grid shopping-plan-date-fields">
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

        <div className="panel-bottom shopping-plan-filter-footer">
          <strong> {filteredPlans.length} of {data.plans.length} plans</strong>
          <button
            type="button"
            className="secondary"
            onClick={resetFilters}
          >Clear filters</button>
        </div>
      </Panel>

      <ShoppingWeek followUpTarget={followUpTarget} data={data} api={api} writable={writable} onPage={onPage} onReceipt={onReceipt} onPlanReceipt={onPlanReceipt} onChanged={onChanged} onTransactions={(from,to)=>onWeeklyReview?onWeeklyReview('receipts',from,to):onPage('receipts')}
        onQuick={mode=>{setQuickMode(mode??null);setCopyPlan(null);setScenarioPlan(null)}}
        onEdit={plan=>{setCopyPlan(null);setScenarioPlan(plan)}}
        onAlternative={plan=>{setCopyPlan(plan);setScenarioPlan(null)}} />

      {[false,true].map(completed=><Disclosure key={String(completed)} title={completed?'Completed Shopping Plans':'Active Shopping Plans'}><div className="stack">{filteredPlans.filter(row=>!!row.plan.purchasedReceiptId===completed).map(row => {
        const planId = row.plan.planId
        const isOpen = openPlanIds.includes(planId)
        return (
          <div key={planId} className="shopping-plan-entry">
            <Panel title={<>{row.plan.name}{row.plan.purchasedReceiptId&&<> — <Badge>Purchased</Badge></>}</>}
              subtitle={<>{row.plan.retailer || 'Store not selected'} · {date(row.plan.shoppingDate)} · {row.plan.scenario?.items.length??row.items.length} Items<br/>{shoppingWeekLabel(row.plan.shoppingDate).replace(/\s*\(Sunday–Saturday\)/,'')}</>}
              action={<div className="shopping-card-actions">
                <button
                  type="button"
                  className="secondary"
                  aria-expanded={isOpen}
                  onClick={() => togglePlan(planId)}
                > {isOpen ? 'Hide details' : 'View'}
                </button>

                {row.plan.purchasedReceiptId&&<button type="button" className="secondary" onClick={()=>onReceipt(row.plan.purchasedReceiptId!)}>View receipt</button>}
                {!row.plan.purchasedReceiptId&&<button type="button" className="secondary" onClick={()=>row.plan.scenario?setScenarioPlan(row.plan):onCheckout(row.plan)}>Checkout</button>}
                <button type="button" className="secondary" disabled={!writable||!api} aria-label="Copy Plan" onClick={()=>{setCopyPlan(row.plan);setScenarioPlan(null)}}>Copy</button>
                <button type="button" className="secondary" disabled={!writable||!api||!!deletingPlan} aria-label="Delete plan" onClick={()=>void deletePlan(row.plan)}>{deletingPlan===planId?'Deleting…':'Delete'}</button>
              </div>
              }
            >
              {!row.plan.purchasedReceiptId&&<p className="shopping-plan-status"><Badge>Planned</Badge></p>}
            </Panel>

            {isOpen && (
              <div className="shopping-plan-expanded">
                {row.plan.scenario && <>{row.scenarioEstimate?<ScenarioBreakdown report={row.scenarioEstimate} scenario={row.plan.scenario} subtitle={`${row.plan.retailer||'Store Not Selected'} · ${date(row.plan.shoppingDate)}`} onEdit={writable&&!row.plan.purchasedReceiptId?()=>setScenarioPlan(row.plan):undefined}/>:<button className="secondary" disabled={!writable||!api} onClick={()=>setScenarioPlan(row.plan)}>Edit Scenario</button>}</>}
                {!row.plan.scenario && <><FormActions><button className="secondary" disabled={!writable} onClick={()=>onEdit({kind:'plan',plan:row.plan})}>Edit Plan</button><button className="secondary" disabled={!writable||!api} onClick={()=>setScenarioPlan(row.plan)}>Edit Scenario</button></FormActions><PlanCard
                  row={row}
                  data={data}
                  writable={writable}
                  onEdit={onEdit}
                  embedded /></>}
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
      </div></Disclosure>)}

      <div className="shopping-week" ref={setFollowUpTarget} />
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
function RebateProgram({program,data,api,writable,onReceipt,onChanged}:{program:Promotion;data:Workspace;api?:ApiClient;writable:boolean;onReceipt:(id:string)=>void;onChanged?:()=>void}){
 const [editing,setEditing]=useState<string|null>(null),[unlinking,setUnlinking]=useState(false),[unlinkError,setUnlinkError]=useState('')
 const [pendingUnlink,setPendingUnlink]=useState<{receiptId:string;token:string;key:string}|null>(null)
 async function unlink(receiptId:string){if(!api||!writable)return;const request=pendingUnlink??{receiptId,token:program.contributions[receiptId].token??'',key:crypto.randomUUID()};setPendingUnlink(request);setUnlinking(true);setUnlinkError('');try{await api.mutate(`/rebate-programs/${program.id}/receipts/${request.receiptId}`,'DELETE',{expectedContribution:request.token},request.key);setPendingUnlink(null);onChanged?.()}catch(e){setUnlinkError((e as Error).message);if([400,403,404,409,412].includes((e as {status:number}).status))setPendingUnlink(null)}finally{setUnlinking(false)}}
 return <details className="promotion"><summary><span><strong>{program.name}</strong><small>{label(program.status)} · threshold {money(program.threshold)}</small></span><Badge>Expected rebate value {money(program.giftCardValue)}</Badge></summary><p>{program.terms}</p>{program.start&&<p>Eligible purchases: {date(program.start)} – {date(program.end??null)}</p>}
 <p>Linked eligible amount: <strong>{money(program.eligibleTotal??(Object.keys(program.contributions).length===0?'0':null))}</strong>{program.progressComplete===false?' · Some receipts need review.':''}</p>
 {unlinkError&&<p className="error" role="alert">{unlinkError}</p>}
 {Object.values(program.contributions).sort((a,b)=>(a.receiptDate??data.receipts.find(r=>r.transactionId===a.receiptId)?.date??'9999').localeCompare(b.receiptDate??data.receipts.find(r=>r.transactionId===b.receiptId)?.date??'9999')||a.receiptId.localeCompare(b.receiptId)).map(c=><section className="receipt-input-row" key={c.receiptId}><div className="rebate-receipt-summary"><button type="button" className="text-button" onClick={()=>onReceipt(c.receiptId)}>Receipt {data.receipts.find(r=>r.transactionId===c.receiptId)?.metadata?.receiptNumber||shortId(c.receiptId)}</button><span>Date: {date(c.receiptDate??data.receipts.find(r=>r.transactionId===c.receiptId)?.date??null)}</span><span>Recorded eligible amount: <strong>{money(c.reviewedAmount??c.eligibleAmount??null)}</strong></span><strong>{c.submitted?'Submitted':'Not submitted'}</strong>{c.quantity!=null&&<span>Quantity: ×{c.quantity}</span>}{c.saleTotal!=null&&<span>Sale price total: {money(c.saleTotal)}</span>}<span>Minus coupons: {c.couponTotal==null?'Review receipt':money(c.couponTotal)}</span></div>{c.submittedOn&&<p>Submitted: {date(c.submittedOn)}{c.submissionReference?` · ${c.submissionReference}`:''}</p>}{!c.submittedOn&&c.submissionReference&&<p>{c.submissionReference}</p>}{c.note&&<p>{c.note}</p>}{c.needsReview&&<p className="error">The linked receipt snapshot is unavailable. Your recorded amount is preserved.</p>}<FormActions><button type="button" className="secondary" disabled={!api||!writable||editing!==null||!!pendingUnlink} onClick={()=>setEditing(c.receiptId)}>Edit receipt link / submission</button><button type="button" className="secondary" disabled={!api||!writable||editing!==null||unlinking||!!pendingUnlink&&pendingUnlink.receiptId!==c.receiptId} onClick={()=>void unlink(c.receiptId)}>{pendingUnlink?.receiptId===c.receiptId?'Retry unlink receipt':'Unlink receipt'}</button></FormActions></section>)}
 <p className="muted">Unlink removes only this rebate connection. Your saved receipt remains available.</p>
 <button type="button" className="secondary" disabled={!api||!writable||editing!==null||!!pendingUnlink} onClick={()=>setEditing('')}>Connect receipt</button>
 {editing!==null&&api&&<RebateReceiptEditor key={editing} program={program} initialReceipt={editing} data={data} api={api} writable={writable} onCancel={()=>setEditing(null)} onSaved={()=>{setEditing(null);onChanged?.()}}/>}</details>
}
function RebateReceiptEditor({program,initialReceipt,data,api,writable,onCancel,onSaved}:{program:Promotion;initialReceipt:string;data:Workspace;api:ApiClient;writable:boolean;onCancel:()=>void;onSaved:()=>void}){
 const [search,setSearch]=useState('')
 const existing=program.contributions[initialReceipt]
 const [receiptId,setReceiptId]=useState(initialReceipt),[receipt,setReceipt]=useState<Receipt|null>(null),[lines,setLines]=useState(existing?.lineIds??[]),[amount,setAmount]=useState(existing?.reviewedAmount??''),[note,setNote]=useState(existing?.note??''),[submitted,setSubmitted]=useState(!!existing?.submitted),[submittedOn,setSubmittedOn]=useState(existing?.submittedOn??''),[reference,setReference]=useState(existing?.submissionReference??''),[error,setError]=useState(''),[loading,setLoading]=useState(!!initialReceipt),[busy,setBusy]=useState(false)
 const [pending,setPending]=useState<{body:unknown;key:string;revision:string;receiptId:string}|null>(null)
 useEffect(()=>{if(!receiptId)return;const controller=new AbortController();api.receipt(receiptId,controller.signal).then(async value=>{const pinned=program.contributions[receiptId];if(pinned&&String(pinned.reviewedRevision)!==String(value.revision)){const history=await api.history(receiptId,controller.signal);const captured=history.find(h=>h.after&&String(h.after.revision)===String(pinned.reviewedRevision))?.after;if(!captured)throw new Error('Linked receipt snapshot is unavailable. Your recorded amount is preserved.');value=captured}if(controller.signal.aborted)return;setReceipt(value);setLines(current=>current.filter(id=>value.contents.purchaseItems.some(line=>line.lineItemId===id)))}).catch(e=>{if(e.name!=='AbortError')setError(e.message)}).finally(()=>{if(!controller.signal.aborted)setLoading(false)});return()=>controller.abort()},[api,receiptId,program.contributions])
 function choose(id:string){setReceiptId(id);setReceipt(null);setLoading(!!id);setError('');const old=program.contributions[id];setLines(old?.lineIds??[]);setAmount(old?.reviewedAmount??'');setNote(old?.note??'');setSubmitted(!!old?.submitted);setSubmittedOn(old?.submittedOn??'');setReference(old?.submissionReference??'')}
 async function save(e:React.FormEvent){e.preventDefault();if(!writable||!receipt||receipt.transactionId!==receiptId)return;setBusy(true);setError('');try{const request=pending??{receiptId,key:crypto.randomUUID(),revision:receipt.revision,body:{lineIds:lines,netAmount:amount,note:note||null,submitted,submittedOn:submitted?submittedOn||null:null,submissionReference:submitted?reference||null:null,expectedContribution:program.contributions[receiptId]?.token??'new'}};setPending(request);await api.mutate(`/rebate-programs/${program.id}/receipts/${request.receiptId}`,'PUT',request.body,request.key,request.revision);setPending(null);onSaved()}catch(e){setError((e as Error).message);if((e as {status?:number}).status&&[400,404,409,412].includes((e as {status:number}).status))setPending(null)}finally{setBusy(false)}}
 return <Modal title={`Connect receipt to ${program.name}`} onClose={()=>{if(!busy&&!pending)onCancel()}}><form className="receipt-input-row rebate-receipt-form" onSubmit={save}>{error&&<p className="error" role="alert">{error}</p>}<fieldset disabled={!writable||busy||!!pending}><label>Search receipts<input type="search" placeholder="Receipt number, store or date" value={search} onChange={e=>setSearch(e.target.value)}/></label><label>Receipt for this rebate<select required value={receiptId} onChange={e=>choose(e.target.value)}><option value="">Choose a saved receipt</option>{data.receipts.filter(r=>r.status==='ACTIVE'&&(!program.start||!!r.date&&r.date>=program.start)&&(!program.end||!!r.date&&r.date<=program.end)&&(r.transactionId===receiptId||matchesReceiptSearch(r,search))).map(r=><option key={r.transactionId} value={r.transactionId}>{date(r.date)} · {r.metadata?.retailer} · {r.metadata?.receiptNumber||shortId(r.transactionId)}</option>)}</select></label>{search.trim()&&!data.receipts.some(r=>r.status==='ACTIVE'&&(!program.start||!!r.date&&r.date>=program.start)&&(!program.end||!!r.date&&r.date<=program.end)&&matchesReceiptSearch(r,search))&&<p role="status">No matching receipts. Try another receipt number, store or date.</p>}{loading&&<p role="status">Loading receipt…</p>}{receipt?.transactionId===receiptId&&<><p>Select only the products that qualify for this rebate.</p>{receipt.contents.purchaseItems.map(l=><label className="check" key={l.lineItemId}><input type="checkbox" checked={lines.includes(l.lineItemId)} onChange={e=>setLines(e.target.checked?[...lines,l.lineItemId]:lines.filter(id=>id!==l.lineItemId))}/>{l.productName} · Quantity {l.quantity} · Unit price {money(l.saleUnitPrice)}</label>)}<details><summary>Original receipt coupons and discounts</summary>{receipt.contents.appliedCoupons.length?receipt.contents.appliedCoupons.map(c=><p key={c.applicationId}>{c.printedLabel}: {money(c.appliedAmount)}</p>):<p>No recorded coupons or discounts.</p>}</details><label>Subtract Mfg Ecpn Used Off Of Promotion Price<input aria-label="Eligible amount after coupons" required inputMode="decimal" pattern="(?:[0-9]+(?:[.][0-9]+)?|[.][0-9]+)" value={amount} onChange={e=>setAmount(e.target.value)}/></label><p className="muted">Enter the qualifying products’ amount after their coupons and discounts. Exclude tax. Do not subtract payments made with RR, Walgreens Cash or gift cards. This adjusts rebate tracking only; the saved receipt stays unchanged.</p><label>Rebate receipt note (optional)<input value={note} onChange={e=>setNote(e.target.value)}/></label><label className="check"><input type="checkbox" checked={submitted} onChange={e=>setSubmitted(e.target.checked)}/>This receipt has been submitted for this rebate</label>{submitted&&<><label>Submission date (optional)<input type="date" value={submittedOn} onChange={e=>setSubmittedOn(e.target.value)}/></label><label>Submission reference (optional)<input value={reference} onChange={e=>setReference(e.target.value)}/></label></>}</>}</fieldset><FormActions><button type="button" className="secondary" disabled={busy||!!pending} onClick={onCancel}>Cancel link</button><button type="submit" className="primary" disabled={!writable||busy||loading||!receipt||receipt.transactionId!==receiptId||!lines.length}>{busy?'Saving…':pending?'Retry same receipt link':'Save rebate receipt'}</button></FormActions></form></Modal>
}

function Deals(props: Props) { const {data,api,writable,onChanged}=props; return <><PageHeader title="Promotions & Rebates" subtitle="Deals, promotion progress, and rebate submission and follow-up." />{!!data.deals.length&&<Panel title="Advertised deals" help={<p>These are advertised offers and source terms. They do not confirm your account eligibility, coupon availability or rebate payment.</p>}><div className="card-grid">{data.deals.map(d => <article className="deal-card" key={d.dealId}><div className="card-top"><span className="icon-tile peach"><Ticket size={20} /></span><Badge>{d.retailer || 'Store not recorded'}</Badge></div><h2>{d.title}</h2><p>{d.interpretedTerms}</p><div className="coupon-dates"><CalendarDays size={15} />{date(d.validFrom)} – {date(d.validThrough)}</div><small>{d.sourceArtifactIds.length} source artifact(s) · revision {d.revision}</small><CatalogDelete api={api} writable={writable} path={`/deals/${d.dealId}`} revision={d.revision} name={d.title} onChanged={onChanged}/></article>)}</div></Panel>}{!data.deals.length && <Panel title="Advertised deals" help={<p>These are saved advertised offers and source terms, not confirmation that your account qualifies. Review your available coupons and rebates before buying.</p>}><Empty title="No advertised deals recorded">Saved source-backed offers will appear here.</Empty></Panel>}<Panel title="Rebate Progress" help={<p>Connect qualifying receipts, select eligible products and record the amount you submitted after coupons. Links retain their captured receipt revision. Submission tracking does not confirm payment, change receipt totals or issue another earning.</p>} subtitle="Link qualifying receipts, record amounts after coupons, and track which receipts you submitted. Submission does not record a payout.">{data.promotions.length?data.promotions.map(p=><RebateProgram key={p.id} program={p} data={data} api={api} writable={writable} onReceipt={props.onReceipt} onChanged={onChanged}/>):<Empty title="No rebate programs recorded">Saved rebate programs appear here.</Empty>}</Panel><Rebates {...props} embedded/><Unavailable>Source-backed offer editing is not available here yet. Rebate receipt links and submission tracking do not confirm payment.</Unavailable></> }
function Rewards(props: Props) { return <RewardsAccounts data={props.data} api={props.api} writable={props.writable} onReceipt={props.onReceipt} onChanged={props.onChanged} /> }

function Inventory({data,api,writable,onChanged,onReceipt}:Props) {
 const [collapsed,setCollapsed]=useState<Record<string,boolean>>({})
 const [showAll,setShowAll]=useState(false)
 const collapseControl=(key:string)=><button type="button" className="icon-button" aria-label={`${collapsed[key]?'Expand':'Collapse'} Inventory ${key}`} aria-expanded={!collapsed[key]} aria-controls={`inventory-${key}`} onClick={()=>setCollapsed({...collapsed,[key]:!collapsed[key]})}>{collapsed[key]?<ChevronDown size={20}/>:<ChevronUp size={20}/>}</button>
 const [search,setSearch]=useState(''),[sort,setSort]=useState('ADDED'),[reviewOnly,setReviewOnly]=useState(false),[error,setError]=useState(''),[message,setMessage]=useState(''),[busy,setBusy]=useState(false),[editing,setEditing]=useState<InventoryItem|null>(null)
 const [action,setAction]=useState('SET'),[quantity,setQuantity]=useState(''),[location,setLocation]=useState(''),[packSize,setPackSize]=useState(''),[unitName,setUnitName]=useState(''),[countUnit,setCountUnit]=useState('PACKAGE'),[lowStock,setLowStock]=useState(''),[note,setNote]=useState(''),[pending,setPending]=useState<{path:string;method:'POST'|'PUT';body:unknown;key:string;revision?:string}|null>(null)
 const [descending,setDescending]=useState(true)
 const columns=[['NAME','Product name'],['SKU','SKU / UPC'],['PURCHASED','Quantity purchased'],['ON_HAND','Quantity on hand'],['LOCATION','Location'],['DATE','Last purchased']]
 function sortColumn(key:string){if(key===sort)setDescending(value=>!value);else{setSort(key);setDescending(false)}}
 const rows=data.inventory.filter(item=>`${item.productName??''} ${item.sku??''} ${item.location??''}`.toLowerCase().includes(search.toLowerCase())&&(!reviewOnly||item.needsProductReview)).sort((a,b)=>{
  const value=(item:InventoryItem)=>sort==='PURCHASED'?item.totalPurchased:sort==='ON_HAND'?item.quantityOnHand:sort==='ADDED'?item.addedAt??item.lastPurchased:sort==='DATE'?item.lastPurchased:sort==='SKU'?item.sku:sort==='LOCATION'?item.location:item.productName??item.productId
  const left=value(a),right=value(b)
  if(left==null||left==='')return right==null||right===''?0:1
  if(right==null||right==='')return -1
  const comparison=sort==='PURCHASED'||sort==='ON_HAND'?Number(left)-Number(right):String(left).localeCompare(String(right),undefined,{sensitivity:'base'})
  return (descending?-comparison:comparison)||a.productId.localeCompare(b.productId)
 })
 const visibleRows=showAll?rows:rows.slice(0,5)
 async function send(request:{path:string;method:'POST'|'PUT';body:unknown;key:string;revision?:string}) {if(!api)return;setBusy(true);setError('');setMessage('');setPending(request);try{const result=await api.mutate(request.path,request.method,request.body,request.key,request.revision) as {products?:string;receiptLines?:string;purchasedPackages?:string};setPending(null);setEditing(null);setMessage(request.method==='POST'?`Receipt purchases synchronized: ${result.products} products, ${result.receiptLines} receipt lines, ${result.purchasedPackages} packages. Stock is tracked from purchases; counting is optional.`:'Inventory updated.');onChanged?.()}catch(e){setError((e as Error).message);if([400,404,409,412].includes((e as {status?:number}).status??0))setPending(null)}finally{setBusy(false)}}
 function edit(item:InventoryItem){setEditing(item);setAction('DETAILS');setQuantity(item.quantityOnHand??'');setLocation(item.location??'');setPackSize(item.unitsPerPackage??'');setUnitName(item.unitName??'');setCountUnit(item.countUnit??'PACKAGE');setLowStock(item.lowStockLevel??'');setNote('');setError('')}
 return <><PageHeader title="Household inventory" subtitle="Stock tracked automatically from receipt purchases." action={<button type="button" className="primary" disabled={!api||!writable||busy||!!pending} onClick={()=>void send({path:'/inventory/import-receipts',method:'POST',body:{},key:crypto.randomUUID()})}>{busy?'Synchronizing…':'Import / sync receipts'}</button>}/>{error&&<div className="error" role="alert">{error}</div>}{message&&<Notice>{message}</Notice>}{pending&&!busy&&<button type="button" className="secondary" onClick={()=>void send(pending)}>Retry same inventory request</button>}<Panel title="Find inventory" action={collapseControl('filters')}><div id="inventory-filters" hidden={!!collapsed.filters}><div className="inventory-filters"><label>Search products<input type="search" value={search} onChange={e=>setSearch(e.target.value)} placeholder="Product, SKU or location"/></label><label>Sort inventory<select value={sort} onChange={e=>{setSort(e.target.value);setDescending(e.target.value==='ADDED'||e.target.value==='DATE'||e.target.value==='PURCHASED')}}><option value="ADDED">Recently added</option>{columns.map(([key,name])=><option key={key} value={key}>{name}</option>)}</select></label><label className="check"><input type="checkbox" checked={reviewOnly} onChange={e=>setReviewOnly(e.target.checked)}/>Needs product review</label></div></div></Panel><Panel title={`${rows.length} inventory products`} action={<button type="button" className="icon-button" aria-label={showAll?'Show Latest Five Inventory Items':'Show All Inventory Items'} aria-expanded={showAll} aria-controls="inventory-products" onClick={()=>setShowAll(!showAll)}>{showAll?<ChevronUp size={20}/>:<ChevronDown size={20}/>}</button>}><div id="inventory-products"><p className="inventory-explanation">Quantity purchased is supported by active receipts. Quantity on hand starts from purchased amounts and updates with receipts and recorded usage. A physical count is optional and can be entered later. Location is optional. Matching SKU / UPC values preserve leading zeros; missing SKUs stay separate, and conflicting names are flagged for review.</p><div className="table-wrap"><table><thead><tr>{columns.map(([key,name])=><th key={key} aria-sort={sort===key?(descending?'descending':'ascending'):'none'}><button type="button" className="text-button" onClick={()=>sortColumn(key)}>{name}{sort===key?<span aria-hidden="true"> {descending?'↓':'↑'}</span>:null}</button></th>)}<th>Details</th></tr></thead><tbody>{visibleRows.map(item=><tr key={item.inventoryId}><td><strong>{item.productName??`Product ${shortId(item.productId)}`}</strong>{item.needsProductReview&&<small>Review SKU / product naming</small>}{item.lowStockLevel!=null&&item.quantityOnHand!=null&&Number(item.quantityOnHand)<=Number(item.lowStockLevel)&&<small>Low stock</small>}</td><td><span className="raw-value">{item.sku??'Not recorded'}</span></td><td>{item.totalPurchased??'Not recorded'} package(s){item.individualPurchased!=null&&<small>{item.individualPurchased} {item.unitName||'items'}</small>}</td><td>{item.quantityOnHand??'Needs confirmation'}{item.quantityConfirmed===false&&<small>Purchase-based estimate</small>}{item.quantityOnHand!=null&&<small>{item.countUnit==='ITEM'?item.unitName||'items':'Packages'}</small>}{item.countUnit!=='ITEM'&&item.individualOnHand!=null&&<small>{item.individualOnHand} {item.unitName||'items'} in full packs</small>}</td><td>{item.location||'Not recorded'}</td><td>{date(item.lastPurchased)}</td><td><button type="button" className="secondary" disabled={!!pending} onClick={()=>edit(item)}>View / update</button></td></tr>)}</tbody></table></div><p className="muted">Showing {Math.min(visibleRows.length,rows.length)} of {rows.length} inventory items.</p>{!rows.length&&<Empty title="No inventory products">Import receipt purchases to begin, or change your search.</Empty>}</div></Panel>{editing&&<Modal title={editing.productName??'Inventory item'} onClose={()=>{if(!busy&&!pending)setEditing(null)}}><details open><summary>Product summary</summary><p>SKU / UPC: <span className="raw-value">{editing.sku??'Not recorded'}</span> · Quantity purchased: {editing.totalPurchased??'Not recorded'} · Quantity on hand: {editing.quantityOnHand??'Needs confirmation'}</p></details>{error&&<div className="error" role="alert">{error}</div>}<details open><summary>Update stock</summary><p>Receipt quantities are purchased packages. Enter items per package to show their contents. You do not need to count now. Purchased package contents appear automatically; an individual-item count can be entered later.</p><form onSubmit={e=>{e.preventDefault();if(!api)return;void send(pending??{path:`/inventory/${editing.inventoryId}`,method:'PUT',key:crypto.randomUUID(),revision:editing.revision,body:{action,quantity:action==='DETAILS'?0:integer(quantity,action==='SET'?0:1),location:location.trim()||null,note:note.trim()||null,lowStockLevel:lowStock.trim()?integer(lowStock,0):null,unitsPerPackage:packSize.trim()?integer(packSize):null,unitName:unitName.trim()||null,countUnit}})}}><fieldset disabled={!writable||!api||busy||!!pending||!editing.productName}><div className="form-grid"><label>Inventory action<select aria-label="Inventory action" value={action} onChange={e=>{setAction(e.target.value);setQuantity(e.target.value==='SET'?editing.quantityOnHand??'':'1')}}><option value="SET">Count / correct quantity on hand (optional)</option><option value="DETAILS">Update package details / location only</option>{['USE','RETURN','DONATE','DISCARD'].map(v=><option key={v} value={v} disabled={editing.quantityOnHand==null}>{label(v)}</option>)}</select></label><label>{action==='SET'?'Quantity on hand':countUnit==='ITEM'?'Items removed':'Packages removed'}<input required={action!=='DETAILS'} disabled={action==='DETAILS'} inputMode="numeric" pattern={action==='SET'?'[0-9]+':action==='DETAILS'?undefined:'[1-9][0-9]*'} value={quantity} onChange={e=>setQuantity(e.target.value)}/></label><label>Items per package {countUnit==='ITEM'?'(required)':'(optional)'}<input inputMode="numeric" required={countUnit==='ITEM'} pattern="[1-9][0-9]*" value={packSize} onChange={e=>setPackSize(e.target.value)}/></label><label>Item unit name (optional)<input maxLength={80} placeholder="Razors, rolls, bottles" value={unitName} onChange={e=>setUnitName(e.target.value)}/></label><label>Track stock as<select aria-label="Track stock as" value={countUnit} onChange={e=>{setCountUnit(e.target.value);setAction('SET');setQuantity('')}}><option value="PACKAGE">Packages</option><option value="ITEM">Individual items</option></select></label><label>Location (optional)<input maxLength={200} value={location} onChange={e=>setLocation(e.target.value)}/></label><label>Low-stock alert level (optional)<input inputMode="numeric" pattern="[0-9]+" value={lowStock} onChange={e=>setLowStock(e.target.value)}/></label><label>Adjustment note (optional)<input value={note} onChange={e=>setNote(e.target.value)}/></label></div></fieldset><FormActions><button type="submit" className="primary" disabled={!writable||!api||busy||!editing.productName}>{busy?'Saving…':pending?'Retry same adjustment':'Save inventory'}</button></FormActions></form></details><details open><summary>Purchase evidence</summary>{editing.sources?.map(source=><p key={`${source.receiptId}-${source.lineItemId}`}><button className="text-button" onClick={()=>{setEditing(null);onReceipt(source.receiptId)}}>{source.receiptNumber||shortId(source.receiptId)}</button> · {source.store||'Store not recorded'} · {date(source.date)} · {source.quantity} package(s) · Revision {source.revision} · {source.active?'Active purchase':'Removed / deleted purchase'}</p>)}</details><details><summary>Stock history</summary>{editing.history?.map((entry,i)=><p key={i}>{new Date(entry.at).toLocaleString()} · {entry.before} → {entry.after} · {entry.reason}</p>)}</details></Modal>}</>
}
function Rebates({data,onReceipt,api,writable,onChanged,reviewRange,embedded=false}:Omit<Props,'page'|'onPage'|'onEdit'|'onAddReceipt'|'onCheckout'> & {embedded?:boolean}){
 const [search,setSearch]=useState(''),[filter,setFilter]=useState('OPEN'),[all,setAll]=useState(false)
 const rows=(data.receiptEarnings?.filter(row=>{const day=data.receipts.find(r=>r.transactionId===row.receiptId)?.date;return row.receiptStatus==='ACTIVE'&&row.earning.kind==='THIRD_PARTY_REBATE'&&(filter==='ALL'||filter==='OPEN'&&!['COMPLETE','NOT_REQUIRED'].includes(row.earning.details?.submissionStatus??'')&&!['CREDITED','PAID','REJECTED'].includes(row.earning.status)|| (row.earning.details?.submissionStatus??(row.earning.details?.submittedOn?'SUBMITTED':'NOT_SUBMITTED'))===filter)&&`${row.earning.provider} ${row.earning.offerReference??''} ${row.receiptNumber??''}`.toLowerCase().includes(search.toLowerCase())&&(!reviewRange||!!day&&day>=reviewRange.from&&day<=reviewRange.to)}).sort((a,b)=>(data.receipts.find(r=>r.transactionId===b.receiptId)?.date??'').localeCompare(data.receipts.find(r=>r.transactionId===a.receiptId)?.date??'')))??[]
 return <>{!embedded&&<PageHeader title="Rebate Submission & Follow-Up" subtitle="Check submission and follow-up details. Account balances are in Rewards & Accounts." />}<Panel className="account-detail-panel rebate-followup-panel" title="Rebates — Submission & Follow-Up" help={<p>Search and filter submission records here. Submitted means sent to the provider; it does not mean paid. Expected, confirmed and received amounts remain separate. Record receipt of a benefit through its reward controls without adding the same reward twice.</p>} action={<button type="button" className="text-button" aria-label={all?"Show Latest Five Rebates":"Show All Rebates"} aria-expanded={all} onClick={()=>setAll(!all)}>{all?<ChevronUp size={18}/>:<ChevronDown size={18}/>}</button>}><p>Submission tracking does not confirm payment or change balances. Edit the reward to record receipt of the benefit.</p><div className="form-grid"><label>Search Rebates<input type="search" value={search} onChange={e=>setSearch(e.target.value)} placeholder="Provider, offer or receipt number"/></label><label>Submission Filter<select value={filter} onChange={e=>setFilter(e.target.value)}><option value="ALL">All</option><option value="OPEN">Needs Submission / Follow-Up</option><option value="NOT_SUBMITTED">Not Submitted</option><option value="SUBMITTED">Submitted</option><option value="FOLLOW_UP">Follow-Up</option><option value="COMPLETE">Complete</option><option value="NOT_REQUIRED">Not Required</option></select></label></div><div className="stack">{rows.slice(0,all?undefined:5).map(row => <div className="detail-row" key={`${row.receiptId}-${row.earning.earningId}`}><div><strong>{row.earning.provider} · {row.earning.offerReference || 'Receipt reward'}</strong><small>{rewardStatusLabel(row.earning)} · receipt {row.receiptNumber || shortId(row.receiptId)} · {label(row.receiptStatus)}</small><small>Submission: {label(row.earning.details?.submissionStatus??(row.earning.details?.submittedOn?'SUBMITTED':'NOT_SUBMITTED'))} · Submitted {date(row.earning.details?.submittedOn??null)} · Follow-Up {date(row.earning.details?.followUpOn??null)}</small>{row.earning.details?.followUpNote&&<p>{row.earning.details.followUpNote}</p>}<small>Expected {row.earning.expected?.amount} {row.earning.expected?.kind === 'POINTS' ? 'points' : row.earning.expected?.currency} · confirmed {row.earning.confirmed?.amount || 'not recorded'}</small>{row.earning.details?.payoutDestination && <small>Payout destination: {label(row.earning.details.payoutDestination)} (context only)</small>}</div><div><button className="text-button" onClick={() => onReceipt(row.receiptId)}>Open receipt</button><RebateFollowUp api={api} writable={writable&&row.receiptStatus==='ACTIVE'} receiptId={row.receiptId} earningId={row.earning.earningId} onSaved={()=>onChanged?.()}/><RewardAmountEditor api={api} writable={writable} receiptId={row.receiptId} earningId={row.earning.earningId} onSaved={()=>onChanged?.()}/></div></div>)}</div>{!rows.length&&<p className="muted">No Matching Rebates. Choose All To Include Received Rewards.</p>}</Panel>{!!data.rebates.length&&<Panel title="Recorded Rebate Claims" help={<p>Claims keep expected and confirmed benefits and submission/fulfillment dates. A submitted claim is not proof of payment, and expected points are not cash received.</p>}><div className="card-grid">{data.rebates.filter(r=>!reviewRange||!!r.submittedOn&&r.submittedOn>=reviewRange.from&&r.submittedOn<=reviewRange.to).map(r => <article className="deal-card" key={r.claimId}><div className="card-top"><span className="icon-tile mint"><Coins size={20} /></span><Badge>{label(r.status)}</Badge></div><h2>{r.provider}</h2><p>{r.externalClaimRef || shortId(r.claimId)}</p><h3>Expected</h3>{r.expectedBenefits.map((b, i) => <p key={i}>{b.kind === 'POINTS' ? `${b.amount} points` : `${b.currency || ''} ${b.amount}`} · {label(b.kind)}</p>)}<h3>Confirmed</h3>{r.confirmedBenefits.length ? r.confirmedBenefits.map((b, i) => <p key={i}>{b.kind === 'POINTS' ? `${b.amount} points` : `${b.currency || ''} ${b.amount}`} · {label(b.kind)}</p>) : <p className="muted">Not confirmed</p>}<small>Receipt: {r.transactionId ? data.receipts.find(row => row.transactionId === r.transactionId)?.metadata?.receiptNumber || r.transactionId : 'Not linked'}</small><small>Submitted {date(r.submittedOn)} · fulfilled {date(r.fulfilledOn)}</small>{r.providerStatus && <p>{r.providerStatus}</p>}</article>)}</div></Panel>}</> }


export function RewardConnection({ reward, onReceipt }: { reward: RewardLink; onReceipt?: (id: string) => void }) {
  return <details><summary>View Reward</summary><p>{reward.promotion} · {money(reward.amount)} · {reward.status}</p>{reward.products?.map((p,i)=><p key={i}>{p}</p>)}<p>Earned {date(reward.date)} · {reward.time || 'Time not recorded'}<br />{onReceipt && reward.receiptId ? <button className="text-button" onClick={() => onReceipt(reward.receiptId!)}>Receipt {reward.receiptNumber || 'Not recorded'}</button> : <>{reward.receiptNumber ? `Receipt ${reward.receiptNumber}` : 'Earning receipt not recorded'}</>}</p><p>Used {money(reward.usedAmount)} on {onReceipt ? <button className="text-button" onClick={() => onReceipt(reward.spentReceiptId)}>Receipt {reward.spentReceiptNumber || 'Not recorded'}</button> : <>Receipt {reward.spentReceiptNumber}</>}</p><details><summary>Technical Details</summary><code>{reward.earningId}</code></details></details>
}
const title = (name: string) => ['PayPal','myWalgreens','P&G','MyPoints','ECB'].includes(name) ? name : name.replace(/\b[a-z]/g, c => c.toUpperCase())
// Presentation policy for Rewards only; stored native units and Java calculations remain intact.
const hasPoints = (provider: string) => ['mypoints','mypoint','fetch','pgbrand','pg','pggoodeveryday','swagbucks','swagbuck','swag','merryfield'].includes(provider.toLowerCase().replace(/[^a-z0-9]/g,''))
const preferredRewardAccounts=['walgreenscash','registerrewards','ecb','walmartcash','checkout51','ibotta','venmo','fetch','pgbrand','swagbucks','mypoints','couponsdotcom','paypal','giftcards','returns','target','merryfield','makeena','shopmium','rakuten']
function rewardAccountRank(name:string):number {
 const key=name.toLowerCase().replace(/[^a-z0-9]/g,'')
 const aliases:Record<string,string>={registrrewards:'registerrewards',swag:'swagbucks',mypoint:'mypoints',couponcom:'couponsdotcom',couponscom:'couponsdotcom',makenna:'makeena',mckenna:'makeena',shop:'shopmium',pg:'pgbrand',pggoodeveryday:'pgbrand',pgbrandsaver:'pgbrand',targetgiftcard:'target',targetcircle:'target'}
 const rank=preferredRewardAccounts.indexOf(aliases[key]??key)
 return rank<0?preferredRewardAccounts.length:rank
}
function compareRewardValues(left:string|null|undefined,right:string|null|undefined,numeric=false):number {
 if(left==null||left==='')return right==null||right===''?0:1
 if(right==null||right==='')return -1
 if(numeric&&/^-?\d+(?:[.]\d+)?$/.test(left)&&/^-?\d+(?:[.]\d+)?$/.test(right)){
  const places=Math.max(left.split('.')[1]?.length??0,right.split('.')[1]?.length??0)
  const units=(value:string)=>{const [whole,fraction='']=value.replace('-','').split('.');return BigInt(whole+fraction.padEnd(places,'0'))*(value.startsWith('-')?-1n:1n)}
  const a=units(left),b=units(right);return a<b?-1:a>b?1:0
 }
 return left.localeCompare(right,undefined,{sensitivity:'base'})
}
function RewardUnavailableEntry({data,api,provider,writable,day,onSaved}:{data:Workspace;api?:ApiClient;provider:string;writable:boolean;day:string;onSaved:()=>void}){
 const [open,setOpen]=useState(false),[search,setSearch]=useState(''),[source,setSource]=useState(''),[dateValue,setDate]=useState(day),[category,setCategory]=useState('EXPIRED'),[amount,setAmount]=useState(''),[points,setPoints]=useState(''),[reason,setReason]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState('')
 const [pending,setPending]=useState<{key:string;body:unknown}|null>(null)
 const [standalone,setStandalone]=useState<StandaloneRR[]>([])
 useEffect(()=>{if(!open||!api||provider!=='Register Rewards')return;let active=true;void api.standaloneRewards().then(rows=>{if(active)setStandalone(rows.filter(r=>r.status==='UNUSED'))}).catch(e=>{if(active)setError((e as Error).message)});return()=>{active=false}},[open,api,provider])
 const removed=new Set(data.unavailableRewardSources?.map(r=>r.sourceId)??[])
 const spentSources=new Set((data.rrMatches as {sourceId:string}[]).map(r=>r.sourceId))
 const earned=(data.accountActivity??[]).filter(a=>a.movement.provider===provider&&a.movement.eventId&&!removed.has(a.movement.eventId)&&!spentSources.has(a.movement.eventId)&&!((a.movement.usd??a.movement.points??'0').startsWith('-'))&&`${a.description} ${a.receiptNumber??''} ${a.movement.date??''}`.toLowerCase().includes(search.toLowerCase()))
 async function save(e:React.FormEvent){e.preventDefault();if(!api||!writable)return;setBusy(true);setError('');const request=pending??{key:crypto.randomUUID(),body:{eventId:crypto.randomUUID(),provider,date:dateValue,category,amount:amount.trim()||null,points:points.trim()||null,sourceId:source||null,reason:reason.trim()}};setPending(request);try{await api.mutate('/accounts/unavailable-rewards','POST',request.body,request.key);setPending(null);setOpen(false);setSource('');setAmount('');setPoints('');setReason('');onSaved()}catch(e){setError((e as Error).message);if([400,403,404,409].includes((e as {status:number}).status))setPending(null)}finally{setBusy(false)}}
 return <Panel className="reward-unavailable-panel" title="Expired, Lost & Other Unavailable Rewards" help={<p>Record an actual expired, lost or otherwise unavailable amount with a date and explanation. It reduces availability separately from Used / Spent and preserves the original receipt and earning. Record each loss once. Keep native points and any known dollar valuation distinct.</p>} subtitle="Record a balance reduction without recording a purchase or payout."><button type="button" className="secondary" disabled={!api||!writable} onClick={()=>setOpen(true)}>Mark reward unavailable</button>{open&&<Modal title={`Mark ${provider} reward unavailable`} onClose={()=>{if(!busy&&!pending)setOpen(false)}}><p>This preserves the original earning and receipt. The amount is removed from available balance on the chosen date, separately from Used / Spent. Do not record a reward already spent or removed.</p>{error&&<p role="alert" className="error">{error}</p>}<form onSubmit={save}><fieldset disabled={!writable||busy||!!pending}><label>Search earned rewards<input type="search" value={search} onChange={e=>setSearch(e.target.value)}/></label><label>Reward source {provider==='Register Rewards'?'':'(optional)'}<select required={provider==='Register Rewards'} value={source} onChange={e=>{setSource(e.target.value);const selected=(data.accountActivity??[]).find(a=>a.movement.eventId===e.target.value);if(selected){setAmount(selected.movement.usd??'');setPoints(selected.movement.points??'')}else{const coupon=standalone.find(r=>r.rewardId===e.target.value);setAmount(coupon?.amount??'');setPoints('')}}}><option value="">{provider==='Register Rewards'?'Choose the unused RR coupon':'Account balance adjustment'}</option>{standalone.filter(r=>!removed.has(r.rewardId)&&`${r.description} ${r.earnedDate??''}`.toLowerCase().includes(search.toLowerCase())).map(r=><option key={r.rewardId} value={r.rewardId}>{r.earnedDate??'Unknown date'} · {r.description} · {r.amount}</option>)}{(data.accountActivity??[]).filter(a=>a.movement.eventId===source&&!earned.includes(a)).concat(earned).map(a=><option key={a.movement.eventId!} value={a.movement.eventId!}>{a.movement.date??'Unknown date'} · {a.description} · {a.receiptNumber??''} · {a.movement.usd??'No dollar valuation'}</option>)}</select></label><label>Unavailable date<input required type="date" value={dateValue} onChange={e=>setDate(e.target.value)}/></label><label>Reason type<select value={category} onChange={e=>setCategory(e.target.value)}><option value="EXPIRED">Expired</option><option value="LOST">Lost</option><option value="OTHER">Other unavailable</option></select></label>{hasPoints(provider)&&<label>Points removed<input required inputMode="decimal" pattern="[0-9]+([.][0-9]+)?" value={points} onChange={e=>{setPoints(e.target.value);if(source)setAmount('')}}/></label>}<label>Dollar value removed {hasPoints(provider)?'(optional recorded valuation)':''}<input required={!hasPoints(provider)} inputMode="decimal" pattern="[0-9]+([.][0-9]+)?" value={amount} onChange={e=>setAmount(e.target.value)}/></label>{hasPoints(provider)&&<p className="muted">Use the recorded dollar valuation for the points removed, or leave it blank if unknown. An estimated valuation is not a cash payout.</p>}<label>Explanation<input required maxLength={2000} value={reason} onChange={e=>setReason(e.target.value)}/></label></fieldset><FormActions><button type="button" className="secondary" disabled={busy||!!pending} onClick={()=>setOpen(false)}>Cancel</button><button className="primary" disabled={!writable||busy}>{pending?'Retry same unavailable reward':'Record unavailable reward'}</button></FormActions></form></Modal>}</Panel>
}

export function RewardsAccounts({ data, api, writable, onReceipt, onChanged }: {data: Workspace; api?: ApiClient; writable: boolean; onReceipt: (id: string) => void; onChanged?: () => void}) {
  const today = () => { const now = new Date(); return `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}` }
  const [balanceDay, setBalanceDay] = useState(() => api ? today() : data.asOf), [accountSearch, setAccountSearch] = useState('')
  const [dated, setDated] = useState<{ day: string; data: Workspace; value?: Workspace; error?: string } | null>(null)

  const [provider, setProvider] = useState(''), [from, setFrom] = useState(balanceDay.slice(0,7) + '-01'), [to, setTo] = useState(balanceDay)
  const [report,setReport]=useState<AccountReport|null>(null),[error,setError]=useState(''),[refresh,setRefresh]=useState(0)
  useEffect(() => {
    if (!api || !balanceDay || provider) return
    const controller = new AbortController()
    api.workspaceAt(balanceDay,controller.signal).then(value => setDated({day:balanceDay,data,value})).catch(e => { if(e.name!=='AbortError') setDated({day:balanceDay,data,error:e.message}) })
    return () => controller.abort()
  }, [api,balanceDay,data,provider,refresh])
  const [missingOpen,setMissingOpen]=useState(false),[missingProvider,setMissingProvider]=useState('Fetch')
  const [opening,setOpening]=useState(false),[usd,setUsd]=useState(''),[points,setPoints]=useState(''),[basis,setBasis]=useState(''),[note,setNote]=useState(''),[confirmed,setConfirmed]=useState(false),[busy,setBusy]=useState(false)
  const [pending,setPending]=useState<{body:unknown;key:string}|null>(null)
  useEffect(()=>{ if(!provider || !api)return; const controller=new AbortController(); api.account(provider,from,to,controller.signal).then(value=>{setReport(value);setError('')}).catch(e=>{if(e.name!=='AbortError')setError(e.message)});return()=>controller.abort() },[provider,from,to,api,refresh])
  const [expanded,setExpanded]=useState<Record<string,boolean>>({}),[activityDay,setActivityDay]=useState(''),[activitySearch,setActivitySearch]=useState(''),[activityType,setActivityType]=useState('ALL'),[activitySort,setActivitySort]=useState('Transaction Date'),[activityDesc,setActivityDesc]=useState(true),[balanceSort,setBalanceSort]=useState('DEFAULT'),[balanceDesc,setBalanceDesc]=useState(false)
  const arrow=(key:string,text:string)=><button type="button" className="icon-button" aria-label={`${expanded[key]?'Show Latest Five':'Show All'} ${text}`} aria-expanded={!!expanded[key]} onClick={()=>setExpanded({...expanded,[key]:!expanded[key]})}>{expanded[key]?<ChevronUp size={20}/>:<ChevronDown size={20}/>}</button>
  const activityValue=(a:AccountReport['activity'][number],column:string)=>({'Transaction Date':a.movement.date,'Transaction Time':a.movement.time,'RFN / Order Number':a.receiptNumber,'Promotion Details':a.description,'Total':a.movement.usd,'Earned / Spent':a.movement.source.includes(' | Reward unavailable: ')?'Expired / Unavailable':(a.movement.usd?.startsWith('-')||a.movement.points?.startsWith('-'))?'Spent':'Earned / Added','Balance Before':a.beforeUsd,'Balance After':a.afterUsd,'Points':a.movement.points,'Points Before':a.beforePoints,'Points After':a.afterPoints} as Record<string,string|null|undefined>)[column]
  const unavailableActivity=(a:AccountReport['activity'][number])=>a.movement.source.includes(' | Reward unavailable: ')
  const earnedActivity=(a:AccountReport['activity'][number])=>!unavailableActivity(a)&&!(a.movement.usd?.startsWith('-')||a.movement.points?.startsWith('-'))
  const activityRows=(report?.activity??[]).filter(a=>(activityType==='ALL'||activityType==='EARNED'&&earnedActivity(a)||activityType==='SPENT'&&!unavailableActivity(a)&&!earnedActivity(a)||activityType==='UNAVAILABLE'&&unavailableActivity(a))&&(!activityDay||a.movement.date===activityDay)&&`${a.description} ${a.receiptNumber??''} ${a.movement.date??''} ${a.movement.usd??''} ${a.movement.points??''}`.toLowerCase().includes(activitySearch.toLowerCase())).sort((a,b)=>{
   const left=activityValue(a,activitySort),right=activityValue(b,activitySort);if(left==null||right==null)return compareRewardValues(left,right)
   const comparison=compareRewardValues(left,right,['Total','Balance Before','Balance After','Points','Points Before','Points After'].includes(activitySort))
   return (activityDesc?-comparison:comparison)||compareRewardValues(a.movement.time,b.movement.time)*(activityDesc?-1:1)
  })
  const visibleActivity=expanded.activity||activityDay?activityRows:activityRows.slice(0,5)
  const pointsAccount = hasPoints(provider)
  const showPointsColumn = data.balances.some(b=>hasPoints(b.provider))
  async function saveOpening() {
    if(!api)return;setBusy(true);setError('')
    const request=pending??{key:crypto.randomUUID(),body:{observationId:crypto.randomUUID(),provider,date:from,usd:usd||null,points:pointsAccount?(points||null):null,valuationBasis:basis,sourceNote:note,confirmedBeginningOfDay:confirmed}};setPending(request)
    try {await api.mutate('/accounts/openings','POST',request.body,request.key);setOpening(false);setPending(null);setRefresh(x=>x+1);onChanged?.()}catch(e){setError((e as Error).message);if((e as {status?:number}).status === 400)setPending(null)}finally{setBusy(false)}
  }
  const selected = dated?.day===balanceDay && dated.data===data ? dated : null
  const rewardTotals = api ? selected?.value?.rewardsOverview : data.rewardsOverview
  const balances = (api ? selected?.value?.balances ?? [] : data.balances).filter(b=>b.provider.toLowerCase().includes(accountSearch.trim().toLowerCase())).sort((a,b)=>{if(balanceSort==='DEFAULT')return rewardAccountRank(a.provider)-rewardAccountRank(b.provider)||a.provider.localeCompare(b.provider);const value=(b:Workspace['balances'][number])=>balanceSort==='Account'?b.provider:balanceSort==='Month Start'?b.balance.monthUsd:balanceSort==='Day Start'?b.balance.startUsd:balanceSort==='Day Close'?b.balance.endUsd:b.balance.endPoints;const left=value(a),right=value(b);return (left==null||right==null?compareRewardValues(left,right):compareRewardValues(left,right,balanceSort!=='Account')*(balanceDesc?-1:1))})
  if(!provider) return <><PageHeader title="Rewards & Accounts" subtitle={`Month to date through ${date(balanceDay)}. Day Close includes recorded activity up to this date.`} /><Panel title="Account Search & Balance Date" help={<p>Search accounts by name. Balance Through selects the date used by the overview and account balances. Month to Date resets it to today. Add Missing Rewards records a received credit that is not already entered; do not repeat a reward already saved on a receipt. These filters do not edit recorded rewards.</p>}><div className="form-grid rewards-account-filters"><label>Search accounts<input type="search" value={accountSearch} onChange={e=>setAccountSearch(e.target.value)} placeholder="Account name" /></label><label>Balance through<input type="date" required value={balanceDay} onChange={e=>{setBalanceDay(e.target.value);setFrom(e.target.value.slice(0,7)+'-01');setTo(e.target.value)}} /></label><button className="secondary" onClick={()=>{const day=today();setBalanceDay(day);setFrom(day.slice(0,7)+'-01');setTo(day)}}>Month to date</button><button className="secondary" onClick={()=>setMissingOpen(v=>!v)}>Add Missing Rewards</button></div></Panel><Panel className="rewards-overview" title="Rewards Overview" help={<><p>Available balances are received recorded amounts through the selected date. Missing openings or valuations make the totals incomplete.</p><p>Available uses positive recorded closing balances. Expected rewards are separate and are not yet available to spend. Rejected or expired expected rewards are excluded; already-spent amounts are not added back.</p></>}><p>Balances and outstanding expected rewards through {date(balanceDay)}. Search filters account rows only.</p>{rewardTotals?<><div className="receipt-period-totals"><div><span>Total Rewards</span><strong>{money(rewardTotals.total)}</strong></div><div><span>Available Balances</span><strong>{money(rewardTotals.available)}</strong></div><div><span>Expected / Unconfirmed</span><strong>{money(rewardTotals.expected)}</strong></div></div>{(rewardTotals.missingBalances.length>0||rewardTotals.missingValuations.length>0)&&<Notice>Some balances or dollar valuations are missing. Known available subtotal: {money(rewardTotals.knownAvailable)}. Known expected subtotal: {money(rewardTotals.knownExpected)}. Review {Array.from(new Set([...rewardTotals.missingBalances,...rewardTotals.missingValuations])).join(', ')}.</Notice>}</>:<p>{selected?.error||'Loading reward totals…'}</p>}</Panel><Panel className="account-balances-panel" title="Calculated Account Balances" help={<><p>Month Start, Day Start and Day Close as of {date(balanceDay)}. Click an account to view its month-to-date activity.</p><p>Native points and recorded dollar values are separate; a valuation is not a payout.</p></>}>{selected?.error && <Notice>{selected.error}</Notice>}{api&&!selected && <p>Loading selected-date balances…</p>}<div className="table-wrap"><table><thead><tr>{['Account','Month Start','Day Start','Day Close',...(showPointsColumn?['Original Points At Close']:[])].map(t=><th key={t} aria-sort={balanceSort===t?(balanceDesc?'descending':'ascending'):'none'}><button className="text-button" onClick={()=>{setBalanceDesc(balanceSort===t?!balanceDesc:false);setBalanceSort(t)}}>{t}{balanceSort===t?(balanceDesc?' ↓':' ↑'):''}</button></th>)}</tr></thead><tbody>{balances.map(b=><tr key={b.provider}><td><button className="text-button" onClick={()=>{setFrom(balanceDay.slice(0,7)+'-01');setTo(balanceDay);setActivityDay('');setActivitySearch('');setExpanded({});setProvider(b.provider)}}>{title(b.provider)}</button></td><td>{money(b.balance.monthUsd)}</td><td>{money(b.balance.startUsd)}</td><td>{money(b.balance.endUsd)}</td>{showPointsColumn&&<td>{hasPoints(b.provider)?b.balance.endPoints??'Not recorded':'—'}</td>}</tr>)}</tbody></table></div>{!balances.length&&(!api||selected?.value)&&<p>No matching accounts.</p>}</Panel>{missingOpen&&<><label>Reward Account<select value={missingProvider} onChange={e=>setMissingProvider(e.target.value)}>{data.balances.map(b=><option key={b.provider}>{b.provider}</option>)}</select></label><MemberRewardEntry key={missingProvider} provider={missingProvider} api={api} writable={writable} day={balanceDay} initialOpen hideTrigger onSaved={()=>{setRefresh(v=>v+1);onChanged?.()}}/></>}</>

  return <><button className="text-button" onClick={()=>{setProvider('');setOpening(false);setBalanceSort('DEFAULT');setBalanceDesc(false);setAccountSearch('')}}>← All Accounts</button><PageHeader title={title(provider)} subtitle="Recorded account activity and receipt connections." /><Panel title="Dates & Reward Search" help={<><p>From / Through are the first and last activity dates included in account totals, not coupon expiration dates.</p><p>Search By Day shows every matching entry for one day within the period. This narrows the activity list without changing period balances.</p><p>Search Rewards finds descriptions, receipt numbers, dates and amounts. Choose Earned / Added under Activity Type to search only received rewards. Filters narrow the list, not the balances.</p><p>Current Month resets the dates through today and clears activity searches.</p></>}><div className="form-grid"><label>From<input type="date" required value={from} onChange={e=>setFrom(e.target.value)} /></label><label>Through<input type="date" required value={to} onChange={e=>setTo(e.target.value)} /></label><label>Search By Day<input aria-label="Search Account By Day" type="date" min={from} max={to} value={activityDay} onChange={e=>setActivityDay(e.target.value)}/></label><label>Search Rewards<input type="search" value={activitySearch} onChange={e=>setActivitySearch(e.target.value)} placeholder="Promotion, receipt number or date"/></label><label>Activity Type<select value={activityType} onChange={e=>setActivityType(e.target.value)}><option value="ALL">All activity</option><option value="EARNED">Earned / Added</option><option value="SPENT">Used / Spent</option><option value="UNAVAILABLE">Expired / Lost / Other unavailable</option></select></label><button type="button" className="secondary" onClick={()=>{const day=today();setFrom(day.slice(0,7)+'-01');setTo(day);setActivityDay('');setActivitySearch('');setActivityType('ALL')}}>Current Month</button><button type="button" className="secondary" onClick={()=>setMissingOpen(v=>!v)}>Add Missing Rewards</button></div></Panel>{error&&<Notice>{error}</Notice>}{!api&&<Notice>Reconnect to the updated API to load account details.</Notice>}
  {report && report.provider === provider && report.from === from && report.to === to && <><Panel className="account-detail-panel" title="Account Balances" help={<p>Beginning balance + received earnings − used/spent − expired or unavailable amounts gives ending balance when the necessary records are known. Expected rewards are not available cash. Missing values remain unknown; point valuations are not cash payouts.</p>}>{pointsAccount?<div className="table-wrap points-balance-summary"><table><thead><tr><th>Account Balances</th><th>Points</th><th>Dollar Value</th></tr></thead><tbody>{[['Beginning Balance',report.beginning.startPoints,report.beginning.startUsd],['Earned',report.earnedPoints,report.earned],['Used / Spent',report.spentPoints,report.spent],...(report.unavailable!=null&&!zeroScenarioAmount(report.unavailable)||report.unavailablePoints!=null&&!zeroScenarioAmount(report.unavailablePoints)?[['Expired / Unavailable',report.unavailablePoints??null,report.unavailable??null]]:[]),['Ending Balance',report.ending.endPoints,report.ending.endUsd]].map(([text,points,usd])=><tr key={text}><td>{text}</td><td>{points??'Not recorded'}</td><td>{money(usd)}</td></tr>)}</tbody></table></div>:<div className="key-values">{Object.entries({'Beginning Balance':report.beginning.startUsd,'Earned':report.earned,'Used / Spent':report.spent,...(report.unavailable!=null&&!zeroScenarioAmount(report.unavailable)?{'Expired / Unavailable':report.unavailable}:{}),'Ending Balance':report.ending.endUsd}).map(([text,value])=><div key={text}><span>{text}</span><strong>{money(value)}</strong></div>)}</div>}<p>{report.beginning.opening ? `${report.beginning.opening.date === from ? 'Recorded Opening' : 'Carried Forward'} from ${date(report.beginning.opening.date)}: ${report.beginning.opening.sourceNote}` : 'Beginning Balance Not Recorded'}</p>{!report.usdActivityComplete&&<Notice>Earned and spent dollar amounts are known subtotals; some activity lacks a saved dollar valuation.</Notice>}<p className="muted">Transaction Balances Require Recorded Order And A Known Opening.</p><button className="secondary" disabled={!writable} onClick={()=>setOpening(v=>!v)}>Set Beginning Balance</button></Panel>

  <Panel className="account-activity-panel" title="Account Activity" help={<p>Lists recorded earnings, spending and unavailable adjustments in the selected period. A selected day shows all matching entries. Matching a Register Reward connects its source coupon to an existing deduction; matching does not deduct the amount a second time. Transaction balances need a known opening and order. Open a receipt connection to check its original facts.</p>} action={activityDay?undefined:<button type="button" className="secondary" aria-label={`${expanded.activity?'Show Latest Five':'Show All'} Account Activity`} aria-expanded={!!expanded.activity} onClick={()=>setExpanded(current=>({...current,activity:!current.activity}))}>{expanded.activity?'Show latest 5':'Show all'}</button>}><p className="muted">Showing {Math.min(visibleActivity.length,activityRows.length)} Of {activityRows.length} Matching Entries. Balances Above Cover The Full Selected Period.</p><div className="table-wrap"><table><thead><tr>{['Transaction Date','Transaction Time','RFN / Order Number','Promotion Details','Total','Earned / Spent','Balance Before','Balance After',...(pointsAccount?['Points','Points Before','Points After']:[])].map(t=><th key={t} aria-sort={activitySort===t?(activityDesc?'descending':'ascending'):'none'}><button type="button" className="text-button" onClick={()=>{setActivityDesc(activitySort===t?!activityDesc:false);setActivitySort(t)}}>{t}{activitySort===t?(activityDesc?' ↓':' ↑'):''}</button></th>)}</tr></thead><tbody>{visibleActivity.map((a,i)=><tr key={i}><td>{date(a.movement.date)}</td><td>{transactionTime(a.movement.time)}</td><td>{a.movement.receiptId?<button className="text-button" onClick={()=>onReceipt(a.movement.receiptId!)}>{a.receiptNumber||'Receipt'}</button>:'External Activity'}</td><td className="wrap"><details><summary><span>{a.description}</span> — Show / Hide</summary>{pointsAccount && a.valuationBasis && <small>Saved Dollar Valuation: {money(a.movement.usd)} · {a.valuationBasis}; valuation is not a payout.</small>}{a.rewards.map(r=><RewardConnection key={`${r.earningId}-${r.spentReceiptId}`} reward={r} onReceipt={onReceipt}/>)}{!a.orderKnown&&<small>Order not confirmed; transaction balances unavailable.</small>}<details><summary>Technical Details</summary><code>{a.movement.source}</code></details></details>{a.movement.receiptId&&data.receiptEarnings?.filter(row=>row.receiptId===a.movement.receiptId&&(a.movement.eventId===row.earning.earningId||a.movement.source.includes(row.earning.earningId))).map(row=><RewardAmountEditor key={row.earning.earningId} api={api} writable={writable&&row.receiptStatus==='ACTIVE'} receiptId={row.receiptId} earningId={row.earning.earningId} onSaved={()=>{setRefresh(v=>v+1);onChanged?.()}}/>)}{!a.movement.receiptId&&a.movement.source.includes(' | Member reward:')&&<StandaloneAmountEditor api={api} writable={writable} path={`/accounts/member-rewards/${a.movement.source.split(' ')[1]}`} original={a.movement.usd??'0'} onSaved={()=>{setRefresh(v=>v+1);onChanged?.()}}/>}</td><td>{money(a.movement.usd)}</td><td>{a.movement.source.includes(' | Reward unavailable: ')?'Expired / Unavailable':(a.movement.usd?.startsWith('-')||a.movement.points?.startsWith('-'))?'Spent':'Earned / Added'}</td><td>{money(a.beforeUsd)}</td><td>{money(a.afterUsd)}</td>{pointsAccount&&<><td>{a.movement.points??'—'}</td><td>{a.beforePoints??'Not recorded'}</td><td>{a.afterPoints??'Not recorded'}</td></>}</tr>)}</tbody></table></div>{!activityRows.length&&<p>No Matching Activity In This Period.</p>}</Panel></>}
  <RewardUnavailableEntry data={data} api={api} provider={provider} writable={writable} day={to} onSaved={()=>{setRefresh(x=>x+1);onChanged?.()}}/>
  {opening&&<Panel className="account-detail-panel" title={`Beginning-of-Day Balance — ${date(from)}`} help={<p>Record a confirmed balance before the first activity of this date. A receipt closing balance belongs after that transaction, not before it. This opening establishes a baseline; it is not a newly earned reward. Keep original points and any saved dollar valuation separate.</p>}><form onSubmit={e=>{e.preventDefault();void saveOpening()}}><fieldset disabled={busy||!!pending}><p>Use a confirmed balance before this day’s activity. Closing balances belong to the following day.</p><div className="form-grid"><label>Dollar Balance<input inputMode="decimal" value={usd} onChange={e=>setUsd(e.target.value)}/></label><>{pointsAccount&&<label>Original Points<input inputMode="decimal" value={points} onChange={e=>setPoints(e.target.value)}/></label>}</><label>{pointsAccount?'Valuation Basis':'Balance Basis'}<input required value={basis} onChange={e=>setBasis(e.target.value)}/></label><label>Source / Reason<input required value={note} onChange={e=>setNote(e.target.value)}/></label></div><label className="check"><input type="checkbox" required checked={confirmed} onChange={e=>setConfirmed(e.target.checked)}/>This is the beginning balance before activity on {date(from)}.</label></fieldset><FormActions><button className="primary" disabled={busy||!writable||(!pending&&(!confirmed||!usd&&(!pointsAccount||!points)))}>{pending?'Retry Same Opening':'Save Beginning Balance'}</button></FormActions></form></Panel>}
  {missingOpen&&<MemberRewardEntry initialOpen hideTrigger key={provider} provider={provider} api={api} writable={writable} day={to} onSaved={()=>{setRefresh(v=>v+1);onChanged?.()}}/>}
  {provider==='Walgreens Cash'&&<Panel className="account-detail-panel" title="Provider-Reported Observations" help={<p>Only recorded Walgreens Cash observations from Walgreens receipts appear here. Opening, earned, used and closing amounts are printed source observations. They do not replace recorded account balances or create extra rewards or payments. Known zero remains zero; unknown amounts stay Not Recorded.</p>} action={arrow('observations','Provider Observations')}><details className="provider-observations"><summary>Provider-Reported Observations — Show / Hide</summary>{data.reportedBalances.filter(r=>{const store=data.receipts.find(receipt=>receipt.transactionId===r.transactionId)?.metadata?.retailer;return (!store||isWalgreensStore(store))&&Object.values(r.values).some(value=>value!=null)&&r.date&&r.date>=from&&r.date<=to&&(!activityDay||r.date===activityDay)}).sort((a,b)=>(b.date??'').localeCompare(a.date??'')).slice(0,activityDay||expanded.observations?undefined:5).map(r=><section key={r.transactionId}><button className="text-button" onClick={()=>onReceipt(r.transactionId)}>{date(r.date)} · {r.receiptNumber} · {r.status}</button><p>Opening {money(r.values.startingBalance)} · Earned {money(r.values.earned)} · Used {money(r.values.used)} · Closing {money(r.values.endingBalance)}</p></section>)}<p className="muted">Printed observations do not replace confirmed openings or create account movements.</p></details></Panel>}<>{provider==='Register Rewards'&&<StandaloneRewardPanel api={api} writable={writable} onChanged={()=>{setRefresh(v=>v+1);onChanged?.()}}/>}</>{report&&report.provider===provider&&report.from===from&&report.to===to&&<><Panel className="account-detail-panel" title="Conversions &amp; Payouts" help={<p>Record transfers or uses of received rewards here. Requested payouts reserve their amounts; Received confirms fulfillment; cancelling a request releases its reservation. A transfer is not another earning. Recorded point valuations remain separate from cash received.</p>} action={arrow('conversions','Conversions')}>{provider.trim().toLowerCase()==='ibotta' && <Panel title="Ibotta Account Credit &amp; Withdrawals" help={<p>Received Ibotta credit is earned even before withdrawal. Withdrawal is a separate transfer, subject to the account minimum; it does not earn the rebate a second time.</p>}><div className="detail-row"><span>Recorded Ibotta balance at period end</span><strong>{money(report.ending.endUsd)}</strong></div><div className="detail-row"><span>Your withdrawal minimum</span><strong>$20.00</strong></div><p>Rebates marked Added to Ibotta account count as earned even while the money stays in Ibotta. A withdrawal to your bank, PayPal or gift card is a separate transfer; it must not count as another rebate. Compare this recorded balance with Ibotta before withdrawing.</p></Panel>}<PayoutEntry provider={provider} pointsAccount={pointsAccount} api={api} writable={writable} day={today()} onSaved={()=>{setRefresh(v=>v+1);onChanged?.()}}/><p className="muted">These are recorded redemption events{pointsAccount?', separate from saved points valuations':''}. Requested events are reserved; only Received confirms fulfillment.</p>{report.conversions?.length ? report.conversions.slice().sort((a,b)=>b.requestedDate.localeCompare(a.requestedDate)).slice(0,expanded.conversions?undefined:5).map(c=><p key={c.redemptionId}>{date(c.requestedDate)} · {pointsAccount&&<>{c.originalUnitsUsed??'Not recorded'} points · </>}Source value {money(c.sourceUsdValue)} → {c.destinationAccount}: {money(c.destinationUsdValue)} · {c.status}{c.completedDate ? ` · ${date(c.completedDate)}` : ''}{c.notes&&<details><summary>Payout Details</summary><span className="raw-value">{c.notes}</span></details>}{c.status==='REQUESTED'&&<PayoutCompletion api={api} writable={writable} id={c.redemptionId} day={today()} onSaved={()=>{setRefresh(v=>v+1);onChanged?.()}}/>}</p>):<p>No recorded conversions or payouts requested in this period.</p>}</Panel></>}</>
}


/** Coupon inventory entry; saving does not post an earning or require any receipt. */
export function StandaloneRewardPanel({api,writable,onChanged}:{api?:ApiClient;writable:boolean;onChanged?:()=>void}) {
 const [rows,setRows]=useState<StandaloneRR[]>([]),[refresh,setRefresh]=useState(0),[open,setOpen]=useState(false),[description,setDescription]=useState(''),[amount,setAmount]=useState(''),[earned,setEarned]=useState(''),[expires,setExpires]=useState(''),[confirmed,setConfirmed]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState('')
 const [showAll,setShowAll]=useState(false),[sort,setSort]=useState('Earned Date'),[desc,setDesc]=useState(true),[day,setDay]=useState('')
 const [now]=useState(()=>new Date());const month=`${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}`
 const [from,setFrom]=useState(month+'-01'),[through,setThrough]=useState(`${month}-${String(now.getDate()).padStart(2,'0')}`)
 const filtered=rows.filter(r=>(!r.earnedDate||r.earnedDate>=from&&r.earnedDate<=through)&&(!day||r.earnedDate===day)).sort((a,b)=>{const value=(r:StandaloneRR)=>sort==='Reward'?r.description:sort==='Amount'?r.amount:sort==='Earned Date'?r.earnedDate:sort==='Expiration'?r.expirationDate:r.status;const left=value(a),right=value(b);return compareRewardValues(left,right,sort==='Amount')*(left==null||right==null?1:desc?-1:1)})
 const [pending,setPending]=useState<{body:unknown;key:string}|null>(null)
 useEffect(()=>{if(!api)return;const controller=new AbortController();api.standaloneRewards(controller.signal).then(setRows).catch(e=>{if(e.name!=='AbortError')setError(e.message)});return()=>controller.abort()},[api,refresh])
 async function save(){if(!api)return;setBusy(true);setError('');const request=pending??{key:crypto.randomUUID(),body:{rewardId:crypto.randomUUID(),description:description.trim(),amount:amount.trim(),earnedDate:earned||null,expirationDate:expires||null,confirmAvailable:confirmed}};setPending(request);try{await api.mutate('/rewards/register-rewards','POST',request.body,request.key);setPending(null);setOpen(false);setDescription('');setAmount('');setEarned('');setExpires('');setConfirmed(false);setRefresh(v=>v+1);onChanged?.()}catch(e){setError((e as Error).message);if((e as {status?:number}).status===400)setPending(null)}finally{setBusy(false)}}
 return <Panel className="account-detail-panel" title="Rewards Without Receipts" help={<p>Record an existing unused paper RR when its earning receipt is unavailable. This creates source evidence, not another earning or deduction. Matching links an existing use. Unavailable coupons cannot be selected for a later purchase.</p>} action={<FormActions><button type="button" className="icon-button" aria-label={showAll?'Show Latest Five Standalone Rewards':'Show All Standalone Rewards'} aria-expanded={showAll} onClick={()=>setShowAll(!showAll)}>{showAll?<ChevronUp size={20}/>:<ChevronDown size={20}/>}</button><button className="secondary" disabled={!api||!writable||busy||!!pending} onClick={()=>setOpen(!open)}>{open?'Cancel reward entry':'Add reward without a receipt'}</button></FormActions>}>
  {error&&<Notice>{error}</Notice>}
  <p>Save an unused Register Reward here, then select it when matching RR used on a receipt. Unknown earning receipt and dates can stay unrecorded. Saving a coupon does not add an earning or deduction.</p>
  {open&&<form onSubmit={e=>{e.preventDefault();void save()}}><p><strong>Reward type: Register Rewards</strong></p><fieldset disabled={!writable||busy||!!pending}><div className="form-grid"><label className="field-full">Reward description<AutoSizeTextArea required value={description} onChange={e=>setDescription(e.target.value)}/></label><label>Reward amount<input required inputMode="decimal" pattern="([0-9]+([.][0-9]+)?|[.][0-9]+)" value={amount} onChange={e=>setAmount(e.target.value)}/></label><label>Earned date (optional)<input type="date" value={earned} onChange={e=>setEarned(e.target.value)}/></label><label>Expiration date (optional)<input type="date" value={expires} onChange={e=>setExpires(e.target.value)}/></label></div><label className="check"><input type="checkbox" required checked={confirmed} onChange={e=>setConfirmed(e.target.checked)}/>I confirm this is an unused RR coupon available to use.</label></fieldset><FormActions><button className="primary" disabled={!writable||busy||(!pending&&!confirmed)}>{pending?'Retry same reward':'Save reward'}</button></FormActions></form>}
  <div className="form-grid"><label>Rewards From<input type="date" value={from} onChange={e=>setFrom(e.target.value)}/></label><label>Rewards Through<input type="date" value={through} onChange={e=>setThrough(e.target.value)}/></label><label>Search Reward Day<input type="date" value={day} onChange={e=>setDay(e.target.value)}/></label></div><div className="table-wrap"><table><thead><tr>{['Reward','Amount','Earned Date','Expiration','Status'].map(text=><th key={text} aria-sort={sort===text?(desc?'descending':'ascending'):'none'}><button type="button" className="text-button" onClick={()=>{setDesc(sort===text?!desc:false);setSort(text)}}>{text}{sort===text?(desc?' ↓':' ↑'):''}</button></th>)}</tr></thead><tbody>{filtered.slice(0,showAll?undefined:5).map(r=><tr key={r.rewardId}><td>{r.description}</td><td>{money(r.amount)}</td><td>{date(r.earnedDate)}</td><td>{date(r.expirationDate)}</td><td>{label(r.status)}{r.status==='UNUSED'&&<StandaloneAmountEditor api={api} writable={writable} path={`/rewards/register-rewards/${r.rewardId}`} original={r.amount??'0'} onSaved={()=>{setRefresh(v=>v+1);onChanged?.()}}/>}</td></tr>)}</tbody></table></div>{!filtered.length&&<p>No rewards without receipts recorded yet.</p>}
 </Panel>
}

/** Non-receipt account credits reuse the audited external movement ledger. */
export function MemberRewardEntry({provider,api,writable,day,onSaved,initialOpen=false,hideTrigger=false}:{provider:string;api?:ApiClient;writable:boolean;day:string;onSaved:()=>void;initialOpen?:boolean;hideTrigger?:boolean}) {
 const [open,setOpen]=useState(initialOpen),[date,setDate]=useState(day),[time,setTime]=useState(()=>new Intl.DateTimeFormat('en-GB',{timeZone:'America/Chicago',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).format(new Date())),[amount,setAmount]=useState(''),[rewardPoints,setRewardPoints]=useState(''),[description,setDescription]=useState(''),[expiration,setExpiration]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState('')
 const [pending,setPending]=useState<{key:string;body:unknown}|null>(null)
 async function save(e?:React.FormEvent){e?.preventDefault();if(!api)return;setBusy(true);setError('');const request=pending??{key:crypto.randomUUID(),body:{eventId:crypto.randomUUID(),provider,date,time:time||null,amount:amount||null,...(hasPoints(provider)?{points:rewardPoints||null}:{}),description,...(provider==='ECB'&&expiration?{expirationDate:expiration}:{})}};setPending(request);try{await api.mutate('/accounts/member-rewards','POST',request.body,request.key);setPending(null);setAmount('');setRewardPoints('');setDescription('');setExpiration('');setOpen(false);onSaved()}catch(ex){setError((ex as Error).message);if((ex as {status?:number}).status===400)setPending(null)}finally{setBusy(false)}}
 return <Panel className="account-detail-panel" title="Add Missing Rewards">{!hideTrigger&&<button type="button" className="secondary" disabled={!writable||!api} onClick={()=>{if(!open)setTime(new Intl.DateTimeFormat('en-GB',{timeZone:'America/Chicago',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).format(new Date()));setOpen(!open)}}>Add member reward without a receipt</button>}<p>Record a reward already received in {provider}. It increases the account balance without creating a receipt or changing receipt savings. Do not enter the same earning again on a receipt.</p>{provider==='Register Rewards'&&<p>For a coupon you need to match to a receipt, also use Rewards Without Receipts below. That coupon inventory entry does not add another account earning.</p>}{open&&<form onSubmit={save}>{error&&<Notice>{error}</Notice>}<fieldset disabled={busy||!!pending}><div className="form-grid"><label>Reward date<input type="date" required value={date} onChange={e=>setDate(e.target.value)}/></label><label>Reward Time<input aria-label="Reward Time" type="time" value={time} onChange={e=>setTime(e.target.value)}/></label>{hasPoints(provider)&&<label>Bonus Reward Points<input aria-label="Bonus Reward Points" required inputMode="decimal" value={rewardPoints} onChange={e=>setRewardPoints(e.target.value)}/></label>}{!['Fetch','P&G Brand'].includes(provider)&&<label>Member reward amount<input required inputMode="decimal" pattern="([0-9]+([.][0-9]+)?|[.][0-9]+)" value={amount} onChange={e=>setAmount(e.target.value)}/></label>}{provider==='ECB'&&<label>ECB expiration date (optional)<input type="date" min={date||undefined} value={expiration} onChange={e=>setExpiration(e.target.value)}/></label>}<label className="field-full">Member reward name / description<input required value={description} onChange={e=>setDescription(e.target.value)}/></label></div></fieldset><FormActions><button type="submit" className="primary" disabled={busy||!writable}>{pending?'Retry same reward':'Save member reward'}</button></FormActions></form>}</Panel>
}

export function PayoutEntry({provider,pointsAccount,api,writable,day,onSaved}:{provider:string;pointsAccount:boolean;api?:ApiClient;writable:boolean;day:string;onSaved:()=>void}) {
 const [open,setOpen]=useState(false),[date,setDate]=useState(day),[description,setDescription]=useState(''),[points,setPoints]=useState(''),[amount,setAmount]=useState(''),[destination,setDestination]=useState(''),[brand,setBrand]=useState(''),[number,setNumber]=useState(''),[status,setStatus]=useState('REQUESTED'),[busy,setBusy]=useState(false),[error,setError]=useState(''),[pending,setPending]=useState<{key:string;body:unknown}|null>(null)
 async function save(e:React.FormEvent){e.preventDefault();if(!api)return;setBusy(true);setError('');const request=pending??{key:crypto.randomUUID(),body:{payoutId:crypto.randomUUID(),provider,date:date||null,description:description||null,points:pointsAccount?points||null:null,amount:amount||null,destination:destination||null,brand:destination==='GIFT_CARD'||destination==='CREDIT_CARD'?brand||null:null,number:destination==='GIFT_CARD'||destination==='CREDIT_CARD'?number||null:null,status}};setPending(request);try{await api.mutate('/accounts/payouts','POST',request.body,request.key);setPending(null);setOpen(false);setPoints('');setAmount('');setDescription('');setBrand('');setNumber('');onSaved()}catch(ex){setError((ex as Error).message);if((ex as {status?:number}).status&&((ex as {status:number}).status<500))setPending(null)}finally{setBusy(false)}}
 return <section className="payout-entry"><button type="button" className="secondary" disabled={!api||!writable||busy||!!pending} onClick={()=>setOpen(!open)}>{open?'Close Payout Entry':'Record Payout / Use Rewards'}</button>{open&&<form onSubmit={save}>{error&&<div className="error" role="alert">{error}</div>}<p>Record A Transfer You Requested Or Received. This Does Not Send Money Or Create A Receipt Rebate.</p><fieldset disabled={busy||!!pending||!writable}><div className="form-grid"><label>Payout Date (Optional)<input type="date" value={date} onChange={e=>setDate(e.target.value)}/></label><label>Description (Optional)<input value={description} maxLength={1000} onChange={e=>setDescription(e.target.value)}/></label>{pointsAccount&&<label>Points Used (Optional)<input inputMode="decimal" value={points} onChange={e=>setPoints(e.target.value)}/></label>}<label>Payout Amount (Optional If Points Determine Value)<input inputMode="decimal" value={amount} onChange={e=>setAmount(e.target.value)}/></label><label>How Paid (Optional)<select value={destination} onChange={e=>{setDestination(e.target.value);setBrand('');setNumber('')}}><option value="">Choose — Defaults To Cash</option><option value="CASH">Cash</option><option value="GIFT_CARD">Gift Card</option><option value="CREDIT_CARD">Credit Card</option></select></label><label>Payout Status<select value={status} onChange={e=>setStatus(e.target.value)}><option value="REQUESTED">Requested — Waiting</option><option value="RECEIVED">Received</option></select></label>{(destination==='GIFT_CARD'||destination==='CREDIT_CARD')&&<><label>{destination==='GIFT_CARD'?'Gift Card Brand (Optional)':'Credit Card Brand (Optional)'}<input maxLength={120} value={brand} onChange={e=>setBrand(e.target.value)}/></label><label>{destination==='GIFT_CARD'?'Gift Card Number (Optional)':'Credit Card Last Four Digits (Optional)'}<input className="raw-value" maxLength={destination==='CREDIT_CARD'?4:100} value={number} onChange={e=>setNumber(e.target.value)}/></label></>}</div></fieldset><FormActions><button className="primary" disabled={busy||!writable}>{pending?'Retry Same Payout':'Save Payout'}</button></FormActions></form>}</section>
}
function PayoutCompletion({api,writable,id,day,onSaved}:{api?:ApiClient;writable:boolean;id:string;day:string;onSaved:()=>void}){
 const [date,setDate]=useState(day),[busy,setBusy]=useState(false),[error,setError]=useState(''),[pending,setPending]=useState<{key:string;body:unknown;revision:string}|null>(null)
 async function finish(status:string){if(!api)return;setBusy(true);setError('');try{const request=pending??{key:crypto.randomUUID(),revision:(await api.payout(id)).revision,body:{status,date}};setPending(request);await api.mutate(`/accounts/payouts/${id}`,'PUT',request.body,request.key,request.revision);setPending(null);onSaved()}catch(ex){setError((ex as Error).message);if((ex as {status?:number}).status&&((ex as {status:number}).status<500))setPending(null)}finally{setBusy(false)}}
 return <span className="payout-completion">{error&&<span role="alert">{error}</span>}<label>Completion Date<input type="date" value={date} disabled={busy||!!pending} onChange={e=>setDate(e.target.value)}/></label><button className="secondary" disabled={!writable||busy} onClick={()=>void finish('RECEIVED')}>{pending?'Retry Same Update':'Mark Received'}</button><button className="secondary" disabled={!writable||busy||!!pending} onClick={()=>void finish('CANCELLED')}>Cancel Payout</button></span>
}

function CatalogDelete({api,writable,path,revision,name,onChanged}:{api?:ApiClient;writable:boolean;path:string;revision:string;name:string;onChanged?:()=>void}) {
 const [busy,setBusy]=useState(false),[error,setError]=useState('')
 async function remove(){if(!api||!window.confirm(`Permanently delete ${name}? Its current plan associations will be removed.`))return;setBusy(true);setError('');try{await api.mutate(path,'DELETE',{},crypto.randomUUID(),revision);onChanged?.()}catch(ex){setError((ex as Error).message)}finally{setBusy(false)}}
 return <div className="catalog-delete">{error&&<Notice>{error}</Notice>}<button type="button" className="secondary" disabled={!api||!writable||busy} onClick={()=>void remove()}>Delete {path.startsWith('/deals/')?'deal':'coupon'}</button></div>
}

const scenarioLine=()=>({id:crypto.randomUUID(),name:'',sku:null as string|null,regular:'',sale:null as string|null,quantity:'1'})
function completeScenarioSlots(current:PlanScenario,seedCoupons=true):PlanScenario {
   const missing=seedCoupons?current.items.filter(l=>!current.coupons.some(c=>!c.wholeReceipt&&c.lineIds.length===1&&c.lineIds[0]===l.id)):[];
   if(!missing.length&&current.payments!=null)return current;
   const rows=current.payments??[...([['WALGREENS_CASH',current.walgreensCashUsed],['REGISTER_REWARD',current.registerRewardsUsed],['GIFT_CARD',current.giftCardUsed]] as const).filter(([,a])=>!/^0+(?:[.]0+)?$/.test(a)).map(([type,amount])=>({id:crypto.randomUUID(),type,amount,notes:current.fundingNotes})),{id:crypto.randomUUID(),type:current.paymentMethod,amount:current.manualOop??'',notes:''}];
   return {...current,payments:rows,coupons:[...current.coupons,...missing.map(l=>({id:crypto.randomUUID(),description:'',amount:'',requiredQuantity:'1',kind:'MANUFACTURER',lineIds:[l.id],savedCouponId:null}))]}
}
export function ProductCouponSubtotal({line,coupons,api}:{line:PlanScenario['items'][number];coupons:PlanScenario['coupons'];api:ApiClient}) {
 const key=JSON.stringify({line,coupons}),[result,setResult]=useState<{key:string;amount:string}|null>(null)
 useEffect(()=>{let active=true;const timer=setTimeout(()=>{try{
  const value=JSON.parse(key) as {line:PlanScenario['items'][number];coupons:PlanScenario['coupons']}
  const scenario={items:[{...value.line,sku:value.line.sku||null,sale:value.line.sale||null,quantity:integer(String(value.line.quantity))}],coupons:activeScenarioCoupons(value.coupons).map(c=>({...c,requiredQuantity:integer(String(c.requiredQuantity))})),promotions:[],tax:'0',walgreensCashUsed:'0',registerRewardsUsed:'0',giftCardUsed:'0',manualOop:null,paymentMethod:'CASH',fundingNotes:''}
  void api.mutate('/shopping-plans/scenario/preview','POST',{scenario},crypto.randomUUID()).then(value=>{const row=(value as ScenarioEstimate).items.find(x=>x.id===line.id);if(active&&row)setResult({key,amount:row.itemOopBeforeTaxAndFunding})}).catch(()=>{if(active)setResult(null)})
 }catch{if(active)setResult(null)}},250);return()=>{active=false;clearTimeout(timer)}},[api,key,line.id])
 return <p className="planning-calculated" role="status">Total minus coupons: <strong>{result?.key===key?money(result.amount):'—'}</strong><small>Product coupons only · before tax and whole-receipt discounts</small></p>
}
export function PlanScenarioEditor({plan,copyFrom,data,api,writable,onClose,onChanged,onReceipt,initialMode}:{initialMode?:'notes'|'screenshot'|null;copyFrom?:Plan|null;plan:Plan|null;data:Workspace;api:ApiClient;writable:boolean;onClose:()=>void;onChanged?:()=>void;onReceipt:(draft:PlanReceiptDraft)=>void}) {
 const [saved,setSaved]=useState<Plan|null>(plan),[id]=useState(()=>plan?.planId??crypto.randomUUID()),[name,setName]=useState(plan?.name??(copyFrom?`${copyFrom.name} Copy`:'')),[store,setStore]=useState(plan?.retailer??copyFrom?.retailer??'Walgreens'),[day,setDay]=useState(()=>plan?(plan.shoppingDate??''):new Date().toLocaleDateString('sv-SE',{timeZone:'America/Chicago'}))
 const [scenario,setScenarioState]=useState<PlanScenario>(()=>completeScenarioSlots(plan?.scenario??(copyFrom?.scenario?{...structuredClone(copyFrom.scenario),coupons:copyFrom.scenario.coupons.map(c=>({...c,savedCouponId:null}))}:undefined)??{items:data.plans.find(r=>r.plan.planId===(plan??copyFrom)?.planId)?.items.map(p=>({id:p.plannedPurchaseId,name:p.productName,sku:p.productCode,regular:p.regularUnitPrice??p.targetUnitPrice??'',sale:p.saleUnitPrice??null,quantity:p.quantity})).filter(Boolean).length?data.plans.find(r=>r.plan.planId===(plan??copyFrom)?.planId)!.items.map(p=>({id:p.plannedPurchaseId,name:p.productName,sku:p.productCode,regular:p.regularUnitPrice??p.targetUnitPrice??'',sale:p.saleUnitPrice??null,quantity:p.quantity})):[scenarioLine()],coupons:[],promotions:[],tax:'0',taxRate:'8.25',walgreensCashUsed:'0',registerRewardsUsed:'0',giftCardUsed:'0',manualOop:null,paymentMethod:'CASH',fundingNotes:''},!plan?.scenario&&!copyFrom?.scenario))
 const [focus,setFocus]=useState('')
 const [rewardSources,setRewardSources]=useState<string[]>(plan?.organization?.rewardSources??[])
 const [preview,setPreview]=useState<{key:string;value:ScenarioEstimate}|null>(null),[previewError,setPreviewError]=useState(''),[error,setError]=useState(''),[busy,setBusy]=useState(false),[pending,setPending]=useState<{key:string;body:unknown;path:string;method:'POST'|'PUT';revision?:string}|null>(null),[couponForm,setCouponForm]=useState<string|null>(null)
 function setScenario(value:PlanScenario){setScenarioState(current=>{
   const existingIds=new Set(current.items.map(line=>line.id));
   const addedLines=value.items.filter(line=>!existingIds.has(line.id)&&!value.coupons.some(c=>!c.wholeReceipt&&c.lineIds.includes(line.id)));
   return {...value,coupons:[...value.coupons,...addedLines.map(line=>({id:crypto.randomUUID(),description:'',amount:'',requiredQuantity:'1',kind:'MANUFACTURER',lineIds:[line.id],savedCouponId:null}))]};
 })}
 function cleanScenario(value:PlanScenario):PlanScenario{return {...value,paymentMethod:value.payments?.find(p=>['CASH','CARD'].includes(p.type))?.type??value.paymentMethod,coupons:activeScenarioCoupons(value.coupons),promotions:value.promotions.filter(p=>(p.description.trim()||p.rewardAmount.trim())&&!zeroScenarioAmount(p.rewardAmount)).map(p=>({...p,requiredQuantity:p.requiredQuantity||'0',requiredSpend:p.requiredSpend.trim()||'0'})),payments:value.payments?.filter(p=>p.amount.trim())}}
 const raw=JSON.stringify({...scenario,source:null}),report=preview?.key===`${store}:${raw}`?preview.value:null
 function payload(){return scenarioRequest({...JSON.parse(raw) as PlanScenario,source:scenario.source})}
 useEffect(()=>{let active=true;const timer=setTimeout(()=>{try{const body=scenarioRequest({...JSON.parse(raw) as PlanScenario,source:null});void api.mutate('/shopping-plans/scenario/preview','POST',{scenario:body,retailer:store},crypto.randomUUID()).then(r=>{if(active){setPreview({key:`${store}:${raw}`,value:r as ScenarioEstimate});setPreviewError('')}}).catch(e=>{if(active)setPreviewError((e as Error).message)})}catch(e){if(active)setPreviewError((e as Error).message)}},350);return()=>{active=false;clearTimeout(timer)}},[api,raw,store])

 function changeLine(id:string,key:string,value:string){setScenario({...scenario,items:scenario.items.map(x=>x.id===id?{...x,[key]:value}:x)})}
 const amount=(label:string,value:string|null,change:(v:string)=>void,required=true)=><label>{label}<input required={required} inputMode="decimal" pattern="([0-9]+([.][0-9]+)?|[.][0-9]+)" value={value??''} onChange={e=>change(e.target.value)}/></label>
 const selectProducts=(ids:string[],set:(ids:string[])=>void)=><div className="check-list">{scenario.items.map((l,i)=><label className="check" key={l.id}><input type="checkbox" checked={ids.includes(l.id)} onChange={e=>set(e.target.checked?[...ids,l.id]:ids.filter(id=>id!==l.id))}/>{i+1}. {l.name||'New product'} · quantity {l.quantity}</label>)}</div>
 function addCoupon(lineId?:string,kind='MANUFACTURER',wholeReceipt=false){const nextId=crypto.randomUUID();setFocus(nextId);setScenario({...scenario,coupons:[...scenario.coupons,{id:nextId,description:'',amount:'',requiredQuantity:'1',kind,wholeReceipt,lineIds:wholeReceipt?[]:lineId?[lineId]:scenario.items.map(x=>x.id),savedCouponId:null}]})}
 function addPromotion(lineId?:string){const nextId=crypto.randomUUID();setFocus(nextId);setScenario({...scenario,promotions:[...scenario.promotions,{id:nextId,description:'',rewardType:'REGISTER_REWARD',rewardAmount:'',requiredQuantity:'1',requiredSpend:'0',spendBasis:'BEFORE_COUPONS',allowsWalgreensCash:true,allowsWalmartCash:true,wholeReceipt:!lineId,lineIds:lineId?[lineId]:[]}]})}
 async function save(e?:React.FormEvent){e?.preventDefault();setError('');if(!report){setError('Complete the inputs and wait for the current Java breakdown before saving.');return}setBusy(true);try{const request=pending??{key:crypto.randomUUID(),method:saved?'PUT' as const:'POST' as const,path:saved?`/shopping-plans/${id}`:'/shopping-plans',revision:saved?.revision,body:{planId:id,name:name.trim(),retailer:store.trim()||null,shoppingDate:day||null,scenario:payload(),organization:{...(saved?.organization??{stage:'IDEA',alternativeOf:copyFrom?.planId??null,checkoutOrder:null}),checkoutOrder:saved?.organization?.checkoutOrder==null?null:integer(String(saved.organization.checkoutOrder),0),rewardSources}}};setPending(request);const result=await api.mutate(request.path,request.method,request.body,request.key,request.revision) as Plan;setSaved(result);setPending(null);onChanged?.();return result}catch(e){setError((e as Error).message);const status=(e as {status?:number}).status;if(status&&status>=400&&status<500&&status!==409)setPending(null)}finally{setBusy(false)}}
 async function copyReceipt(id:string){if(!id)return;setError('');try{const receipt=await api.receipt(id);if(scenario.items.some(l=>l.name)&&!window.confirm('Replace this draft’s products and remove its coupon/promotion associations?'))return;setScenario({...scenario,items:receipt.contents.purchaseItems.map(l=>({id:crypto.randomUUID(),name:l.productName,sku:l.productCode,regular:l.regularUnitPrice??'',sale:null,quantity:l.quantity})),coupons:[],promotions:[]});setStore(receipt.metadata?.retailer||store)}catch(e){setError((e as Error).message)}}
 function couponFields(c:PlanScenario['coupons'][number]) {
  const i=scenario.coupons.findIndex(x=>x.id===c.id),filled=!!(c.description.trim()||c.amount.trim())&&!zeroScenarioAmount(c.amount);
  const change=(key:string,value:string)=>setScenario({...scenario,coupons:scenario.coupons.map(x=>x.id===c.id?{...x,[key]:value}:x)});
  return <Disclosure className="scenario-coupon" key={c.id} title={`Coupon ${i+1}`}><div className="scenario-row"><label>Coupon description {i+1}<input autoFocus={focus===c.id} required={filled} value={c.description} onChange={e=>change('description',e.target.value)}/></label><label>Coupon type {i+1}<select aria-label={`Coupon type ${i+1}`} value={c.kind??'MANUFACTURER'} onChange={e=>change('kind',e.target.value)}><option value="MANUFACTURER">Mfg</option><option value="STORE_COUPON">Store coupon</option><option value="SALE_PROMOTION">Store promotion discount</option></select></label></div><div className="scenario-row">{amount(`Coupon amount ${i+1}`,c.amount,v=>change('amount',v),filled)}<label>Required coupon quantity {i+1}<input required={filled} pattern={filled?'[1-9][0-9]*':undefined} value={c.requiredQuantity} onChange={e=>change('requiredQuantity',e.target.value)}/></label></div><div className="product-entry-actions"><button type="button" className="secondary" disabled={c.kind==='SALE_PROMOTION'||!!c.savedCouponId||!c.description||!c.amount} onClick={()=>setCouponForm(c.id)}>{c.savedCouponId?'Saved to Coupons':'Add to Coupons (optional)'}</button><button type="button" className="secondary" onClick={()=>setScenario({...scenario,coupons:scenario.coupons.filter(x=>x.id!==c.id)})}>Remove coupon {i+1}</button></div></Disclosure>
 }
 const selectedCoupon=scenario.coupons.find(c=>c.id===couponForm)
 if(selectedCoupon)return <Modal title="Add planned coupon to Coupons" onClose={()=>setCouponForm(null)}><CouponForm api={api} initialValues={{description:selectedCoupon.description,amount:selectedCoupon.amount,requiredQuantity:selectedCoupon.requiredQuantity,provider:scenario.items.find(l=>selectedCoupon.lineIds.includes(l.id))?.name.split(' ')[0]||store,source:selectedCoupon.kind==='STORE_COUPON'?'PAPER_STORE':'PAPER_MANUFACTURER',couponType:selectedCoupon.kind==='STORE_COUPON'?'STORE':'MANUFACTURER'}} onCancel={()=>setCouponForm(null)} onSaved={rows=>{setScenario({...scenario,coupons:scenario.coupons.map(c=>c.id===selectedCoupon.id?{...c,savedCouponId:rows[0].couponId}:c)});setCouponForm(null);onChanged?.()}}/></Modal>
 return <Modal title={saved?'Edit deal scenario':'Plan a shopping deal'} onClose={onClose}><p>Try coupons, tax, WC and RR without spending rewards or creating a receipt. Rewards below are possible, not earned. Copying an older plan brings its products; review and add its coupon/promotion terms here.</p>{error&&<div className="error" role="alert">{error}</div>}<form className="deal-scenario-form" onSubmit={async e=>{if(await save(e))onClose()}}><fieldset disabled={!writable||busy||!!pending}><QuickDealTools onStoreChange={setStore} initialMode={initialMode} api={api} scenario={scenario} onChange={setScenario} store={store} day={day} data={data}/><Disclosure title="Plan Details"><div className="scenario-row"><label>Plan name<input required value={name} onChange={e=>setName(e.target.value)}/></label><label>Store<input required value={store} onChange={e=>setStore(e.target.value)}/></label><label>Shopping date<input type="date" value={day} onChange={e=>setDay(e.target.value)}/></label><label>Uses rewards from an earlier transaction (optional)<select multiple value={rewardSources} onChange={e=>setRewardSources(Array.from(e.target.selectedOptions,o=>o.value))}>{data.plans.filter(r=>r.plan.planId!==id&&r.plan.organization?.stage==='TRIP'&&r.plan.shoppingDate===day).map(r=><option key={r.plan.planId} value={r.plan.planId}>{r.plan.name}</option>)}</select></label><label>Copy products from receipt<select value="" onChange={e=>void copyReceipt(e.target.value)}><option value="">Choose a receipt (optional)</option>{data.receipts.filter(r=>r.status==='ACTIVE').map(r=><option value={r.transactionId} key={r.transactionId}>{r.date} · {r.metadata?.retailer} · {r.metadata?.receiptNumber}</option>)}</select></label></div></Disclosure>
 <Disclosure title="Products">{scenario.items.map((l,i)=><details key={l.id} className="receipt-input-row" open><summary>Product {i+1} · {l.name||'New product'} <ChevronDown size={18} aria-hidden="true"/></summary><div className="scenario-row"><label>Product {i+1}<input autoFocus={focus===l.id} required value={l.name} onChange={e=>changeLine(l.id,'name',e.target.value)}/></label></div><div className="scenario-row">{amount(`Regular price ${i+1}`,l.regular,v=>changeLine(l.id,'regular',v),false)}{amount(`Sale price ${i+1} (optional)`,l.sale,v=>changeLine(l.id,'sale',v),false)}</div><details className="product-more-details"><summary>More details — Product {i+1}</summary><p>SKU is optional. When sale price is blank, the regular price above is used.</p><div className="scenario-row"><label>SKU {i+1}<input value={l.sku??''} onChange={e=>{const match=data.productLookup?.find(p=>p.sku===e.target.value);setScenario({...scenario,items:scenario.items.map(x=>x.id===l.id?{...x,sku:e.target.value,...(match?{name:match.description,regular:match.regularPrice??x.regular}:{})}:x)})}}/></label></div></details><div className="scenario-row"><label>Quantity {i+1}<input required inputMode="numeric" pattern="[1-9][0-9]*" value={l.quantity} onChange={e=>changeLine(l.id,'quantity',e.target.value)}/></label><div className="planning-calculated">Total: <strong>{report?.items.find(x=>x.id===l.id)?money(report.items.find(x=>x.id===l.id)!.totalPrice):'Calculated when entries are complete'}</strong></div></div><SaleButtons line={l} products={scenario.items} onChange={line=>setScenario({...scenario,items:scenario.items.map(x=>x.id===l.id?line:x)})}/><Disclosure title="Coupons"><p className="muted">Optional. Leave blank when no coupon applies.</p>{scenario.coupons.filter(c=>!c.wholeReceipt&&c.lineIds.length===1&&c.lineIds[0]===l.id).map(c=>couponFields(c))}</Disclosure><ProductCouponSubtotal line={l} coupons={scenario.coupons.filter(c=>!c.wholeReceipt&&c.lineIds.length===1&&c.lineIds[0]===l.id)} api={api}/><div className="product-entry-actions"><button type="button" className="secondary" onClick={()=>addCoupon(l.id)}>Add another coupon for product {i+1}</button><button type="button" className="secondary" onClick={()=>addCoupon(l.id,'SALE_PROMOTION')}>Add store promotion discount for product {i+1}</button><button type="button" className="secondary" onClick={()=>addPromotion(l.id)}>Add promotion for product {i+1}</button><button type="button" className="secondary" disabled={scenario.items.length===1} onClick={()=>setScenario({...scenario,items:scenario.items.filter(x=>x.id!==l.id),coupons:scenario.coupons.map(c=>({...c,lineIds:c.lineIds.filter(id=>id!==l.id)})).filter(c=>c.wholeReceipt||c.lineIds.length),promotions:scenario.promotions.map(p=>({...p,lineIds:p.lineIds.filter(id=>id!==l.id)})).filter(p=>p.wholeReceipt||p.lineIds.length)})}>Remove product {i+1}</button></div></details>)}<button className="secondary" type="button" onClick={()=>{const line=scenarioLine();setFocus(line.id);setScenario({...scenario,items:[...scenario.items,line]})}}>Add product</button></Disclosure>
 {scenario.coupons.some(c=>!c.wholeReceipt&&c.lineIds.length>1)&&<details className="receipt-input-row"><summary>Existing shared coupons</summary>{scenario.coupons.filter(c=>!c.wholeReceipt&&c.lineIds.length>1).map(c=>couponFields(c))}</details>}
 <Disclosure className="receipt-input-row" title="Whole-Receipt Discounts"><p>For online coupon codes, order-wide coupons and store promotions. Each amount is deducted once from the whole purchase, including products added later. Leave blank rows empty when unused.</p>{scenario.coupons.filter(c=>c.wholeReceipt).map(c=>couponFields(c))}<button type="button" className="secondary" onClick={()=>addCoupon(undefined,'STORE_COUPON',true)}>Add whole-receipt coupon or promotion</button></Disclosure>
 <Disclosure className="receipt-input-row" title="Tax"><div className="scenario-row scenario-three"><label>Tax calculation<select aria-label="Tax calculation" value={scenario.taxRate==null?'AMOUNT':'RATE'} onChange={e=>setScenario({...scenario,taxRate:e.target.value==='RATE'?'8.25':null})}><option value="RATE">Calculate</option><option value="AMOUNT">Enter amount</option></select></label>{scenario.taxRate!=null?amount('Tax rate (%)',scenario.taxRate,taxRate=>setScenario({...scenario,taxRate})):amount('Tax amount',scenario.tax,tax=>setScenario({...scenario,tax}))}<div className="planning-calculated"><span>Tax amount</span><strong>{report?money(report.tax):'—'}</strong></div></div><p className="planning-calculated">Total with tax <strong>{report?money(report.totalWithTax??report.receiptTotalBeforeFunding??report.calculatedOop):'—'}</strong></p></Disclosure>
 <Disclosure className="receipt-input-row" title="Payment Or Rewards"><RewardPicker api={api} data={data} scenario={scenario} onChange={setScenario} day={day}/><p className="muted">Cash or credit left blank uses the calculated remaining amount. Enter amounts to plan specific payments.</p>{(scenario.payments??[]).map((p,i)=><Disclosure className="scenario-payment" key={p.id} title={`Payment ${i+1}`}><div className="scenario-row scenario-three"><label>Payment or reward {i+1}<select aria-label={`Payment or reward ${i+1}`} value={p.type} onChange={e=>setScenario({...scenario,payments:scenario.payments!.map(x=>x.id===p.id?{...x,type:e.target.value}:x)})}>{[['WALGREENS_CASH','Walgreens Cash'],...(/^walmart(?:\s|#|$)/i.test(store.trim())||p.type==='WALMART_CASH'?[['WALMART_CASH','Walmart Cash']]:[]),['REGISTER_REWARD','Register Rewards'],['ECB','ECB'],['GIFT_CARD','Gift Cards'],['CASH','Cash'],['CARD','Credit']].map(([v,t])=><option key={v} value={v}>{t}</option>)}</select></label>{amount(`Payment amount ${i+1}`,p.amount,v=>setScenario({...scenario,payments:scenario.payments!.map(x=>x.id===p.id?{...x,amount:v}:x)}),false)}<label>Payment description {i+1}<input value={p.notes} onChange={e=>setScenario({...scenario,payments:scenario.payments!.map(x=>x.id===p.id?{...x,notes:e.target.value}:x)})}/></label></div><button type="button" className="secondary" onClick={()=>setScenario({...scenario,payments:scenario.payments!.filter(x=>x.id!==p.id)})}>Remove payment {i+1}</button></Disclosure>)}<button type="button" className="secondary" onClick={()=>setScenario({...scenario,payments:[...(scenario.payments??[]),{id:crypto.randomUUID(),type:'CASH',amount:'',notes:''}]})}>Add another payment or reward</button></Disclosure>
 <details open className="receipt-input-row"><summary>Rebates <ChevronDown size={18} aria-hidden="true"/></summary><p>Enter each expected rebate once. Fetch points convert automatically at 1,000 points = $1; P&amp;G Brand uses 150 points = $1. Cash rebates include Ibotta, PayPal, Venmo and Swagbucks dollar values even before withdrawal limits are reached.</p>{scenario.promotions.map((p,i)=><Disclosure className="receipt-input-row" key={p.id} title={`Rebate ${i+1} · ${p.description||'New Rebate'}`}><div className="scenario-row scenario-rebate-main"><label>Rebate type {i+1}<select aria-label={`Rebate type ${i+1}`} value={p.rewardType} onChange={e=>setScenario({...scenario,promotions:scenario.promotions.map(x=>x.id===p.id?{...x,rewardType:e.target.value,rewardAmount:''}:x)})}>{[['FETCH','Fetch'],['REGISTER_REWARD','Register Reward'],['WALGREENS_CASH','Walgreens Cash'],['WALMART_CASH','Walmart Cash'],['IBOTTA','Ibotta'],['PAYPAL','PayPal'],['VENMO','Venmo'],['SWAGBUCKS','Swagbucks (dollar value)'],['PG_BRAND','P&G Brand'],['ECB','CVS ExtraBucks'],['CASH','Other cash rebate']].map(([v,t])=><option value={v} key={v}>{t}</option>)}</select></label><label>Rebate description {i+1}<input autoFocus={focus===p.id} required={!!(p.description.trim()||p.rewardAmount.trim())&&!zeroScenarioAmount(p.rewardAmount)} value={p.description} onChange={e=>setScenario({...scenario,promotions:scenario.promotions.map(x=>x.id===p.id?{...x,description:e.target.value}:x)})}/></label>{amount(`${['FETCH','PG_BRAND'].includes(p.rewardType)?'Rebate points':'Rebate amount'} ${i+1}`,p.rewardAmount,v=>setScenario({...scenario,promotions:scenario.promotions.map(x=>x.id===p.id?{...x,rewardAmount:v}:x)}),!!(p.description.trim()||p.rewardAmount.trim())&&!zeroScenarioAmount(p.rewardAmount))}{['FETCH','PG_BRAND'].includes(p.rewardType)&&<div className="planning-calculated"><span>Converted USD {i+1}</span><strong>{report?.promotions.find(x=>x.id===p.id)?.usdValue!=null?money(report.promotions.find(x=>x.id===p.id)!.usdValue!):'Calculated when entries are complete'}</strong></div>}</div><div className="scenario-row scenario-three"><label>Required promotion quantity {i+1}<input pattern="[0-9]+" value={p.requiredQuantity} onChange={e=>setScenario({...scenario,promotions:scenario.promotions.map(x=>x.id===p.id?{...x,requiredQuantity:e.target.value}:x)})}/></label>{amount(`Required spend ${i+1}`,p.requiredSpend,v=>setScenario({...scenario,promotions:scenario.promotions.map(x=>x.id===p.id?{...x,requiredSpend:v}:x)}),false)}<label>Spend basis {i+1}<select value={p.spendBasis} onChange={e=>setScenario({...scenario,promotions:scenario.promotions.map(x=>x.id===p.id?{...x,spendBasis:e.target.value}:x)})}><option value="BEFORE_COUPONS">Sale prices before coupons</option><option value="AFTER_COUPONS">After coupons</option><option value="AFTER_REWARDS_USED">After coupons and WC/RR used (conservative)</option></select></label></div><label>Rebate applies to {i+1}<select aria-label={`Rebate applies to ${i+1}`} value={p.wholeReceipt?'WHOLE_RECEIPT':'PRODUCTS'} onChange={e=>setScenario({...scenario,promotions:scenario.promotions.map(x=>x.id===p.id?{...x,wholeReceipt:e.target.value==='WHOLE_RECEIPT',lineIds:e.target.value==='WHOLE_RECEIPT'?[]:scenario.items.map(l=>l.id)}:x)})}><option value="WHOLE_RECEIPT">Whole receipt</option><option value="PRODUCTS">Selected products</option></select></label>{p.wholeReceipt&&<p className="muted">This Rebate Applies To The Whole Receipt, Including Products Added Later.</p>}{!p.wholeReceipt&&selectProducts(p.lineIds,lineIds=>setScenario({...scenario,promotions:scenario.promotions.map(x=>x.id===p.id?{...x,lineIds}:x)}))}<details><summary>Payment Rules (Optional)</summary>{/^walgreens(?:\s|#|$)/i.test(store.trim())&&<label className="check"><input type="checkbox" checked={p.allowsWalgreensCash} onChange={e=>setScenario({...scenario,promotions:scenario.promotions.map(x=>x.id===p.id?{...x,allowsWalgreensCash:e.target.checked}:x)})}/>This rebate permits paying with Walgreens Cash</label>}{/^walmart(?:\s|#|$)/i.test(store.trim())&&<label className="check"><input type="checkbox" checked={p.allowsWalmartCash??true} onChange={e=>setScenario({...scenario,promotions:scenario.promotions.map(x=>x.id===p.id?{...x,allowsWalmartCash:e.target.checked}:x)})}/>This rebate permits paying with Walmart Cash</label>}</details><button className="secondary" type="button" onClick={()=>setScenario({...scenario,promotions:scenario.promotions.filter(x=>x.id!==p.id)})}>Remove rebate {i+1}</button></Disclosure>)}<button className="secondary" type="button" onClick={()=>addPromotion()}>Add another rebate</button></details>
</fieldset>
 <LiveDealSummary report={report} scenario={scenario}/>{previewError&&!report&&<div className="error" role="alert">{previewError}</div>}{report?<ScenarioBreakdown report={report} scenario={scenario} memberRateControls={/^walgreens(?:\s|#|$)/i.test(store.trim())?<fieldset disabled={!writable||busy||!!pending}><Disclosure title="Adjust Eligibility / Rates" open={false}><div className="form-grid">{scenario.items.map((l,i)=><label key={l.id}>{l.name||`Product ${i+1}`}<select aria-label={`Member Reward Rate ${i+1}`} value={l.memberRewardRate??(/^(walg|walgreens)\b/i.test(l.name)?5:1)} onChange={e=>setScenario({...scenario,items:scenario.items.map(x=>x.id===l.id?{...x,memberRewardRate:Number(e.target.value)}:x)})}><option value={1}>1% — Eligible Purchase</option><option value={5}>5% — Eligible Walgreens Brand</option><option value={0}>0% — Excluded Item</option></select></label>)}</div></Disclosure></fieldset>:undefined}/>:<p role="status">Complete product prices for a Java-calculated breakdown.</p>}
 <FormActions><button className="secondary" type="button" disabled={busy} onClick={onClose}>Close scenario</button><button className="secondary" type="button" disabled={!report||busy||!!pending||!name||!writable||!!saved?.purchasedReceiptId} onClick={async()=>{const source=await save();if(source&&report)onReceipt({plan:{...source,scenario:cleanScenario(scenario)},estimate:report})}}>Start receipt draft</button><button className="primary" disabled={!writable||busy||!report}>{busy?'Saving…':pending?'Retry same plan':'Save scenario'}</button></FormActions>{saved&&<p className="muted">Saved plan revision {saved.revision}. Further edits need Save scenario.</p>}</form></Modal>
}
export function ScenarioBreakdown({report:r,scenario,subtitle,onEdit,memberRateControls}:{report:ScenarioEstimate;scenario?:PlanScenario;subtitle?:string;onEdit?:()=>void;memberRateControls?:React.ReactNode}) {
 const [couponsOpen,setCouponsOpen]=useState(true)
 const positive=(v:string|undefined)=>v!=null&&compareRewardValues(v,'0',true)>0
 const funding=[['Register Rewards Used',r.registerRewardsUsed],['Walgreens Cash Used',r.walgreensCashUsed],['Walmart Cash Used',r.walmartCashUsed],['ECB Used',r.ecbUsed],['Gift Cards Used',r.giftCardUsed]].filter(([,v])=>positive(v))
 const quantity=(id:string)=>scenario?.items.find(i=>i.id===id)?.quantity
 const totalQuantity=scenario?.items.every(i=>/^\d+$/.test(i.quantity))?scenario.items.reduce((n,i)=>n+BigInt(i.quantity),0n).toString():'—'
 const samePrice=r.regularTotal!=null&&compareRewardValues(r.regularTotal,r.saleTotal,true)===0
 const rows=(values:[string,string|null|undefined][])=>values.map(([name,value])=><tr key={name}><td>{name}</td><td>{money(value)}</td></tr>)
 return <section className="scenario-breakdown">
  <div className="scenario-view-heading">{onEdit&&<button type="button" className="secondary" onClick={onEdit}>Edit Scenario</button>}</div><Disclosure title={<h3>Planning breakdown</h3>}>
  {subtitle&&<p className="muted">{subtitle}</p>}
  <div className="table-wrap"><table><thead><tr>{['Product','Quantity','Total Price','Coupons Applied','Item OOP¹','Expected Rewards'].map(t=><th key={t}>{t}</th>)}</tr></thead><tbody>{r.items.map(l=><tr key={l.id}><td>{l.name}</td><td>{quantity(l.id)??'—'}</td><td>{money(l.totalPrice)}</td><td>{money(l.couponsApplied)}</td><td>{money(l.itemOopBeforeTaxAndFunding)}</td><td>{money(l.possibleRewards)}</td></tr>)}{positive(r.receiptLevelRewards)&&<tr><td colSpan={5}>Whole-Receipt Rewards</td><td>{money(r.receiptLevelRewards)}</td></tr>}</tbody><tfoot><tr><th>Total</th><td>{totalQuantity}</td><td>{money(r.saleTotal)}</td><td>{money(r.couponTotal)}</td><td>{money(r.merchandiseAfterCoupons)}</td><td>{money(r.potentialRewards)}</td></tr></tfoot></table></div>
  <p className="muted">¹ Before Receipt-Level Tax And Payment Funding. Whole-receipt rewards are shown separately and counted once. Shared selected-product offers are counted once on their first selected line.</p>
  <h4 className="scenario-view-heading">Coupons Applied<button className="icon-button" type="button" aria-label={couponsOpen?'Collapse Coupons Applied':'Expand Coupons Applied'} aria-expanded={couponsOpen} onClick={()=>setCouponsOpen(!couponsOpen)}>{couponsOpen?<ChevronUp size={20}/>:<ChevronDown size={20}/>}</button></h4>
  {couponsOpen&&<div className="scenario-offer-lines">{r.coupons.length?r.coupons.map(c=><p key={c.id}><strong>{c.description}</strong> · Type: {label(scenario?.coupons.find(v=>v.id===c.id)?.kind??'Not Recorded')} · Amount: <strong>{money(c.amount)}</strong> · Qualifies: <strong>{c.applied?'Yes':'No'}</strong> · Quantity: {c.quantityMet?'Met':'Not Met'} · {c.reason}</p>):<p>No Coupons.</p>}</div>}
  {funding.length>0&&<p className="scenario-funding-summary"><strong>Checkout Funding:</strong> {funding.map(([name,v])=>`${name}: ${money(v)}`).join(' · ')}</p>}
  <section className="planning-calculated scenario-oop-heading"><h4>OOP After Rewards, After Tax</h4><strong>{money(r.calculatedOop)}</strong><small>After Coupons, Tax And Rewards Used At Checkout; Before Newly Earned Rewards And Rebates.</small></section>
  <Disclosure title="Purchase Breakdown"><div className="table-wrap"><table className="scenario-summary-table"><thead><tr><th>Purchase Breakdown</th><th>Amount</th></tr></thead><tbody>{samePrice?<tr><td>Merchandise<small>Regular And Sale Prices Match</small></td><td>{money(r.regularTotal)}</td></tr>:rows([['Regular Merchandise',r.regularTotal],['Sale Merchandise',r.saleTotal]])}{rows([['Coupons Applied',`-${r.couponTotal}`],['After Coupons, Before Tax',r.merchandiseAfterCoupons],['Sales Tax',r.tax],['Total With Sales Tax',r.totalWithTax]])}</tbody></table></div></Disclosure>
  <Disclosure title="Payments & Rewards Used"><div className="table-wrap"><table className="scenario-summary-table"><thead><tr><th>Funding</th><th>Amount</th></tr></thead><tbody>{rows([...funding as [string,string][],['Cash / Card OOP',r.selectedOop],...(compareRewardValues(r.manualDifference,'0',true)!==0?[['Manual / Calculated Difference',r.manualDifference] as [string,string]]:[]),...(positive(r.overFunding)?[['Funding Above Purchase Total',r.overFunding] as [string,string]]:[])])}</tbody></table></div></Disclosure>
  <Disclosure title="Potential Rewards & Rebates"><div className="scenario-offer-lines">{r.promotions.length?r.promotions.map(p=><p key={p.id}><strong>{p.description}</strong> · {label(scenario?.promotions.find(v=>v.id===p.id)?.rewardType??'Reward')} · <strong>{money(p.potentialReward)}</strong>{positive(p.points)&&` · ${p.points} Points`} · Applies To: {scenario?.promotions.find(v=>v.id===p.id)?.wholeReceipt?'Whole Receipt':'Selected Products'} · Quantity: {p.quantityMet?'Met':'Not Met'} · Spend: {p.spendMet?'Met':'Not Met'} · Payment Rule: {p.walgreensCashAllowed?'Met':'Not Met'}</p>):!r.memberRewardLines?.length?<p>No Rebates.</p>:null}</div>
  {positive(r.potentialRewards)&&<div className="key-values"><div><span>Total Rebates</span><strong>{money(r.potentialRewards)}</strong></div>{positive(r.cashRebates)&&positive(r.otherRebates)&&<><div><span>Cash Rebates</span><strong>{money(r.cashRebates)}</strong></div><div><span>Store Rewards &amp; Points</span><strong>{money(r.otherRebates)}</strong></div></>}</div>}
  </Disclosure><Disclosure title="Final Deal Totals"><div className="table-wrap"><table className="scenario-summary-table"><thead><tr><th>Measure</th><th>Amount</th></tr></thead><tbody>{rows([['Total After Cash Rebates, Including Tax',r.afterCashRebates??r.calculatedOop],['Net Including Tax',r.afterPotentialRewards]])}</tbody></table></div></Disclosure>
  {(!!r.memberRewardLines?.length||memberRateControls)&&<Disclosure title="Walgreens Cash — Member Rewards"><p><strong>Estimated Members Reward For Purchase: {money(r.memberRewardEstimate)}</strong></p><p className="muted">Plan Total · Automatic 1% / 5% Estimate. Excludes Tax And Redeemed Funding.</p>{memberRateControls}</Disclosure>}
  <p className="muted">Quantity and spend checks use entered terms, not a guarantee of store eligibility. Possible rewards never reduce the amount due at checkout.</p>
 </Disclosure></section>
}
