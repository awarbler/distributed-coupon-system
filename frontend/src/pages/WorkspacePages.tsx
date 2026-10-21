import { useState } from 'react'
import { ArrowUpRight, CalendarDays, Check, ClipboardList, Coins, Plus, Search, ShoppingBasket, Ticket, Wallet } from 'lucide-react'
import { date, decimal, label, money, shortId } from '../api/format'
import { rewardStatusLabel } from '../features/receipts/rewardStatus'
import { Badge, Empty, LinkButton, Notice, PageHeader, Panel, Unavailable } from '../components/ui'
import type { Edit } from '../features/shopping/EditDialog'
import type { Plan, PlanRow, Workspace } from '../types/platform'

export type Page = 'dashboard' | 'receipts' | 'shopping' | 'coupons' | 'deals' | 'rewards' | 'inventory' | 'rebates'
interface Props {
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
  const summary = data.summaries[period]
  const metrics = [
    { title: 'Regular value', value: money(summary.regularWithTax), icon: ShoppingBasket, description: 'Including recorded tax', color: 'mint' },
    { title: 'Due at checkout', value: money(summary.due), icon: Wallet, description: `${summary.receipts} recorded transactions`, color: 'peach' },
    { title: 'Future value earned', value: money(summary.newlyEarned), icon: Coins, description: 'Confirmed rewards and valued rebates', color: 'lilac' },
    { title: 'Total saved', value: money(summary.totalSaved), icon: Ticket, description: summary.percentSaved == null ? 'Needs complete recorded values' : `${decimal(summary.percentSaved)}% saved`, color: 'yellow' },
  ]
  return <>
    <PageHeader title="Make every trip count." subtitle="Your plans, purchases, and rewards. All in one place." action={<button className="primary" disabled={!writable} onClick={() => onEdit({ kind: 'plan' })}><Plus size={18} />New shopping plan</button>} />
    <div className="overview-bar"><div><strong>Your savings at a glance</strong><span>As of {date(data.asOf)}</span></div><div className="segmented" aria-label="Savings period">{(['day', 'week', 'month', 'all'] as const).map(p => <button key={p} className={period === p ? 'active' : ''} onClick={() => setPeriod(p)}>{p === 'all' ? 'All time' : label(p)}</button>)}</div></div>
    <div className="metrics">{metrics.map(m => <section className="metric" key={m.title}><div className="metric-top"><span>{m.title}</span><span className={`icon-tile ${m.color}`}><m.icon size={19} /></span></div><strong>{m.value}</strong><small>{m.description}</small></section>)}</div>
    {summary.issues.length > 0 && <Notice>{summary.issues.length} recorded information issue(s) affect these totals. Review receipt details for missing values.</Notice>}
    <div className="dashboard-grid"><Panel title="Your next shopping trip" subtitle="A little preparation goes a long way." action={<LinkButton onClick={() => onPage('shopping')}>All plans</LinkButton>}>
      {data.plans.length ? data.plans.slice(0, 3).map(row => <button className="plan-preview" key={row.plan.planId} onClick={() => onPage('shopping')}><span className="icon-tile mint"><ClipboardList size={21} /></span><span><strong>{row.plan.name}</strong><small>{row.plan.retailer || 'Store not selected'} · {row.items.length} items</small></span><span className="preview-date">{date(row.plan.shoppingDate)}<ArrowUpRight size={17} /></span></button>) : <Empty title="Start with a shopping plan">Gather the essentials before your next trip.<button disabled={!writable} className="text-button" onClick={() => onEdit({ kind: 'plan' })}>Create your first plan <Plus size={15} /></button></Empty>}
      <div className="tip"><span className="icon-tile yellow"><Check size={20} /></span><p><strong>Plan first. Shop with confidence.</strong><br />Keep product quantities, target prices, and coupon associations together.</p></div>
    </Panel><Panel title="Rewards to keep track of" subtitle="Calculated account balances" action={<LinkButton onClick={() => onPage('rewards')}>All accounts</LinkButton>}>
        {data.balances.length ? data.balances.slice(0, 4).map((b, i) => <div className="account-preview" key={b.provider}><span className={`account-avatar ${['mint', 'lilac', 'peach', 'yellow'][i % 4]}`}>{b.provider.slice(0, 1)}</span><span><strong>{b.provider}</strong><small>{b.balance.endPoints != null ? `${b.balance.endPoints} points` : 'Recorded account value'}</small></span><strong>{money(b.balance.endUsd)}</strong></div>) : <Empty title="No account records yet">Balances appear when openings and activity are recorded.</Empty>}
      </Panel></div>
    <Panel title="Recent receipts" subtitle="The details behind your grocery spending." action={<LinkButton onClick={() => onPage('receipts')}>View receipts</LinkButton>}>
      {data.receipts.length ? <ReceiptTable rows={data.receipts.slice(0, 4)} onReceipt={onReceipt} /> : <Empty title="No receipts in this workspace">Your local receipts remain separate until an explicitly approved database import.</Empty>}
    </Panel>
  </>
}
function ReceiptTable({ rows, onReceipt }: { rows: Workspace['receipts']; onReceipt: (id: string) => void }) {
  return <div className="table-wrap"><table><thead><tr><th>Store / receipt</th><th>Date & time</th><th>Due at register</th><th>Newly earned</th><th>Status</th><th><span className="sr-only">Details</span></th></tr></thead><tbody>{rows.map(r => <tr key={r.transactionId}><td><strong>{r.metadata?.retailer || 'Store not recorded'}</strong><small>{r.metadata?.receiptNumber || shortId(r.transactionId)}</small></td><td>{date(r.date)}<small>{r.time || 'Time not recorded'}</small></td><td>{money(r.totals.reportedDue)}</td><td>{money(r.totals.savings.newlyEarned)}</td><td><Badge>{label(r.status)}</Badge></td><td><button className="icon-button" aria-label={`Open receipt ${r.metadata?.receiptNumber || shortId(r.transactionId)}`} onClick={() => onReceipt(r.transactionId)}><ArrowUpRight size={19} /></button></td></tr>)}</tbody></table></div>
}
function Receipts({ data, onReceipt, writable, onAddReceipt }: Props) {
  const [search, setSearch] = useState(''); const [status, setStatus] = useState('ALL')
  const rows = data.receipts.filter(r => (status === 'ALL' || r.status === status) && `${r.metadata?.retailer} ${r.metadata?.receiptNumber} ${r.date}`.toLowerCase().includes(search.toLowerCase()))
  return <><PageHeader title="Your receipts" subtitle="Original records, item details, and the full revision trail." action={<button className="primary" disabled={!writable} onClick={onAddReceipt}><Plus size={16} />Add receipt</button>} /><div className="filters"><label className="search"><Search size={18} /><input aria-label="Search receipts" placeholder="Search store, receipt, or date" value={search} onChange={e => setSearch(e.target.value)} /></label><select aria-label="Receipt status" value={status} onChange={e => setStatus(e.target.value)}><option value="ALL">All statuses</option><option>ACTIVE</option><option>DELETED</option></select></div><Panel title={`${rows.length} receipt${rows.length === 1 ? '' : 's'}`}>{rows.length ? <ReceiptTable rows={rows} onReceipt={onReceipt} /> : <Empty title="No matching receipts">Try another search or status.</Empty>}</Panel><Unavailable>General editing of saved receipts is not available yet. Add receipt creates a new purchase; owner access can update an existing reward’s status in the Rewards tab.</Unavailable></>
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
          : `${row.plan.retailer || 'Store not selected'} · ${date(row.plan.shoppingDate)} · revision ${row.plan.revision}`
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
            Expected promotional rewards
            <strong>
              {money(row.pricing.expectedRewards)}
            </strong>
          </div>

          <div className="detail-row">
            Estimated cost after rewards
            <strong>
              {money(row.pricing.finalCost)}
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
        before tax. Expected rewards are not available
        balances.
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
              subtitle={`${row.plan.retailer || 'Store not selected'} · ${date(row.plan.shoppingDate)} · ${row.items.length} items`}
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
function Coupons({ data, writable, onEdit }: Props) {
  const [today] = useState(() => new Date().toISOString().slice(0, 10))
  const [search, setSearch] = useState(''); const [status, setStatus] = useState('ALL')
  const rows = data.coupons.filter(c => `${c.provider} ${c.description}`.toLowerCase().includes(search.toLowerCase()) && (status === 'ALL' || c.status === status))
  return <><PageHeader title="Coupons" subtitle="Keep your paper and digital coupons together." action={<button className="primary" disabled={!writable} onClick={() => onEdit({ kind: 'coupon' })}>Add coupon</button>} /><div className="filters"><label className="search"><Search size={18} /><input aria-label="Search coupons" placeholder="Search coupons or providers" value={search} onChange={e => setSearch(e.target.value)} /></label><select aria-label="Coupon status" value={status} onChange={e => setStatus(e.target.value)}><option value="ALL">All statuses</option>{[...new Set(data.coupons.map(c => c.status))].map(s => <option key={s}>{s}</option>)}</select></div><div className="card-grid">{rows.map(c => <article className="coupon-card" key={c.couponId}><div className="card-top"><span className="icon-tile lilac"><Ticket size={20} /></span><Badge>{c.expirationDate && c.expirationDate < today && c.status !== 'APPLIED' ? 'Expired' : label(c.status)}</Badge></div><span className="eyebrow">{c.provider}</span><h2>{c.terms?.discount === 'PERCENT' ? `${c.discountAmount}% off` : c.terms?.discount === 'FREE_ITEM' ? 'Free item' : money(c.discountAmount)}</h2><h3>{c.description}</h3><p>{label(c.couponType)} coupon · {label(c.terms?.source || 'Source unknown')}</p><p>{data.plans.filter(p => p.items.some(i => i.clippedCouponIds.includes(c.couponId))).length} plan(s) assigned · assignment is not redemption</p><div className="coupon-dates"><CalendarDays size={15} />Expires {date(c.expirationDate)}</div>{c.redeemedOn && <p>Used {date(c.redeemedOn)}</p>}<button className="secondary full-width" disabled={!writable || c.status !== 'CLIPPED'} onClick={() => onEdit({ kind: 'redeem', coupon: c })}>Record as used</button></article>)}</div>{!rows.length && <Panel title="Recorded coupons"><Empty title="No coupons to show">No matching coupons are recorded in this database.</Empty></Panel>}<Unavailable>Removing coupons is not exposed by the API. Text qualifications require review. Expiration and one-time use are enforced by the backend when recording redemption.</Unavailable></>
}
function Deals({ data }: Props) { return <><PageHeader title="Deals & promotions" subtitle="Source-backed offers and recorded rebate qualification programs." /><div className="card-grid">{data.deals.map(d => <article className="deal-card" key={d.dealId}><div className="card-top"><span className="icon-tile peach"><Ticket size={20} /></span><Badge>{d.retailer || 'Store not recorded'}</Badge></div><h2>{d.title}</h2><p>{d.interpretedTerms}</p><div className="coupon-dates"><CalendarDays size={15} />{date(d.validFrom)} – {date(d.validThrough)}</div><small>{d.sourceArtifactIds.length} source artifact(s) · revision {d.revision}</small></article>)}</div>{!data.deals.length && <Panel title="Advertised deals"><Empty title="No advertised deals recorded">Saved source-backed offers will appear here.</Empty></Panel>}<Panel title="Gift-card promotion progress" subtitle="Qualification records only. These amounts are not available gift-card balances.">{data.promotions.length ? data.promotions.map(p => <details className="promotion" key={p.id}><summary><span><strong>{p.name}</strong><small>{label(p.status)} · threshold {money(p.threshold)}</small></span><Badge>Potential gift card {money(p.giftCardValue)}</Badge></summary><p>{p.terms}</p>{Object.values(p.contributions).map(c => <div className="detail-row" key={c.receiptId}><div>Receipt {data.receipts.find(r => r.transactionId === c.receiptId)?.metadata?.receiptNumber || shortId(c.receiptId)}<small>Reviewed revision {c.reviewedRevision} · {c.note}</small></div><strong>Recorded contribution {money(c.reviewedAmount)}</strong></div>)}</details>) : <Empty title="No programs recorded">Promotion contributions appear after they are recorded.</Empty>}</Panel><Unavailable>Editing source-backed offers and confirming current promotion eligibility are not web operations yet. Recorded contributions are not presented as confirmed combined progress.</Unavailable></> }
function Rewards({ data }: Props) { return <><PageHeader title="Rewards & accounts" subtitle={`Calculated from recorded activity through ${date(data.asOf)}. Provider observations remain separate.`} /><Panel title="Calculated account balances"><div className="table-wrap"><table><thead><tr><th>Provider</th><th>Month start</th><th>Day start</th><th>Day close</th><th>Original points at close</th></tr></thead><tbody>{data.balances.map(b => <tr key={b.provider}><td><strong>{b.provider}</strong></td><td>{money(b.balance.monthUsd)}</td><td>{money(b.balance.startUsd)}</td><td>{money(b.balance.endUsd)}</td><td>{b.balance.endPoints || 'Not recorded'}</td></tr>)}</tbody></table></div>{!data.balances.length && <Empty title="No recorded account balances">Openings and activity are needed to calculate balances.</Empty>}</Panel><Panel title="Provider-reported observations"><div className="table-wrap"><table><thead><tr><th>Date / receipt</th><th>Reported opening</th><th>Earned</th><th>Used</th><th>Reported close</th></tr></thead><tbody>{data.reportedBalances.map(r => <tr key={r.transactionId}><td>{date(r.date)}<small>{r.receiptNumber || shortId(r.transactionId)} · {label(r.status)}</small></td><td>{money(r.values.startingBalance)}</td><td>{money(r.values.earned)}</td><td>{money(r.values.used)}</td><td>{money(r.values.endingBalance)}</td></tr>)}</tbody></table></div>{!data.reportedBalances.length && <Empty title="No printed observations">Reported receipt balances appear separately here.</Empty>}</Panel><Panel title="Account activity"><div className="table-wrap"><table><thead><tr><th>Date</th><th>Provider</th><th>Recorded USD movement</th><th>Original points</th><th>Source</th></tr></thead><tbody>{data.movements.map((m, i) => <tr key={`${m.source}-${i}`}><td>{date(m.date)}<small>{m.time}</small></td><td>{m.provider}</td><td>{money(m.usd)}</td><td>{m.points || '—'}</td><td className="wrap">{m.source}</td></tr>)}</tbody></table></div>{!data.movements.length && <Empty title="No recorded movements">Account activity will appear when recorded.</Empty>}</Panel><Unavailable>Entering openings, transfers, adjustments, and reconciliation approvals still requires dedicated account API workflows. No account balance can be edited here.</Unavailable></> }
function Inventory({ data }: Props) { return <><PageHeader title="Your inventory" subtitle="Recorded whole-package quantities, separate from receipt history." /><Panel title={`${data.inventory.length} inventory balances`}><div className="table-wrap"><table><thead><tr><th>Catalog product</th><th>Packages on hand</th><th>Revision</th><th>Inventory ID</th></tr></thead><tbody>{data.inventory.map(item => <tr key={item.inventoryId}><td><strong>Product {shortId(item.productId)}</strong><small>{item.productId}</small></td><td>{item.quantityOnHand}</td><td>{item.revision}</td><td><code>{shortId(item.inventoryId)}</code></td></tr>)}</tbody></table></div>{!data.inventory.length && <Empty title="No inventory balances recorded">Products are not automatically added from receipts.</Empty>}</Panel><Unavailable>Inventory adjustments require an authenticated approval workflow. The current API does not establish an independent approver, so this screen is read-only. Product names also require the catalog API.</Unavailable></> }
function Rebates({ data, onReceipt }: Props) { return <><PageHeader title="Rebates" subtitle="Track expected and confirmed benefits without counting them twice." /><Panel title="Receipt-linked rebates and points"><div className="stack">{data.receiptEarnings?.filter(row => row.earning.kind === 'THIRD_PARTY_REBATE').map(row => <div className="detail-row" key={`${row.receiptId}-${row.earning.earningId}`}><div><strong>{row.earning.provider} · {row.earning.offerReference || 'Receipt reward'}</strong><small>{rewardStatusLabel(row.earning)} · receipt {row.receiptNumber || shortId(row.receiptId)} · {label(row.receiptStatus)}</small><small>Expected {row.earning.expected?.amount} {row.earning.expected?.kind === 'POINTS' ? 'points' : row.earning.expected?.currency} · confirmed {row.earning.confirmed?.amount || 'not recorded'}</small>{row.earning.details?.payoutDestination && <small>Payout destination: {label(row.earning.details.payoutDestination)} (context only)</small>}</div><button className="text-button" onClick={() => onReceipt(row.receiptId)}>Open receipt</button></div>)}</div>{!data.receiptEarnings?.some(row => row.earning.kind === 'THIRD_PARTY_REBATE') && <p className="muted">No receipt-linked rebates yet. Open a receipt’s Rewards tab to add one.</p>}</Panel><div className="card-grid">{data.rebates.map(r => <article className="deal-card" key={r.claimId}><div className="card-top"><span className="icon-tile mint"><Coins size={20} /></span><Badge>{label(r.status)}</Badge></div><h2>{r.provider}</h2><p>{r.externalClaimRef || shortId(r.claimId)}</p><h3>Expected</h3>{r.expectedBenefits.map((b, i) => <p key={i}>{b.kind === 'POINTS' ? `${b.amount} points` : `${b.currency || ''} ${b.amount}`} · {label(b.kind)}</p>)}<h3>Confirmed</h3>{r.confirmedBenefits.length ? r.confirmedBenefits.map((b, i) => <p key={i}>{b.kind === 'POINTS' ? `${b.amount} points` : `${b.currency || ''} ${b.amount}`} · {label(b.kind)}</p>) : <p className="muted">Not confirmed</p>}<small>Receipt: {r.transactionId ? data.receipts.find(row => row.transactionId === r.transactionId)?.metadata?.receiptNumber || r.transactionId : 'Not linked'}</small><small>Submitted {date(r.submittedOn)} · fulfilled {date(r.fulfilledOn)}</small>{r.providerStatus && <p>{r.providerStatus}</p>}</article>)}</div>{!data.rebates.length && <Panel title="Rebate claims"><Empty title="No rebate claims recorded">Receipt earnings and formal rebate claims are separate records.</Empty></Panel>}<Unavailable>Receipt-linked earnings and their submission notes can be added on receipts. Standalone claim lifecycle management and external payout confirmation are not exposed here.</Unavailable></> }
