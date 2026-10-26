import type { ReceiptDraftState } from './features/receipts/ReceiptCreate'
import { useCallback, useEffect, useState } from 'react'
import { ClipboardList, Coins, Download, LayoutDashboard, Leaf, LogOut, Menu, Package, ReceiptText, RefreshCw, ShieldCheck, ShoppingBag, Ticket, Wallet, X } from 'lucide-react'
import type { FormEvent } from 'react'
import { ApiClient } from './api/client'
import { Loading, Modal, Notice } from './components/ui'
import { EditDialog } from './features/shopping/EditDialog'
import type { Edit } from './features/shopping/EditDialog'
import { ReceiptCreate } from './features/receipts/ReceiptCreate'
import { ReceiptDetail } from './features/receipts/ReceiptDetail'
import { WorkspacePages } from './pages/WorkspacePages'
import type { Page } from './pages/WorkspacePages'
import type { Workspace } from './types/platform'
import './App.css'
import type { Plan, PlanReceiptDraft } from './types/platform'
import { CheckoutForm } from './features/shopping/CheckoutForm'

const navigation = [
  { id: 'dashboard', name: 'Overview', icon: LayoutDashboard }, { id: 'shopping', name: 'Shopping plans', icon: ClipboardList },
  { id: 'receipts', name: 'Receipts', icon: ReceiptText }, { id: 'coupons', name: 'Coupons', icon: Ticket },
  { id: 'deals', name: 'Deals & promotions', icon: ShoppingBag }, { id: 'rewards', name: 'Rewards & Accounts', icon: Wallet },
  { id: 'inventory', name: 'Inventory', icon: Package }, { id: 'rebates', name: 'Rebates', icon: Coins },
] as const
interface InstallEvent extends Event { prompt(): Promise<void>; userChoice: Promise<{ outcome: string }> }
function Brand() { return <div className="brand"><span className="brand-icon"><Leaf size={24} /></span><span>grocery<span>intelligence</span></span></div> }
function currentPage(): Page { const page = location.hash.slice(1); return navigation.some(n => n.id === page) ? page as Page : 'dashboard' }

export default function App() {
  const [api, setApi] = useState<ApiClient | null>(null)
  const [data, setData] = useState<Workspace | null>(null)
  const [token, setToken] = useState('')
  const [connecting, setConnecting] = useState(false)
  const [error, setError] = useState('')
  const [refreshing, setRefreshing] = useState(false)
  const [page, setPage] = useState<Page>(currentPage)
  const [mobileMenu, setMobileMenu] = useState(false)
  const [edit, setEdit] = useState<Edit | null>(null)
  const [checkoutPlan, setCheckoutPlan] = useState<Plan | null>(null)
  const [parentPlan, setParentPlan] = useState<Plan | null>(null)
  const [creating, setCreating] = useState(false)
  const [savedReceiptDraft,setSavedReceiptDraft]=useState<{state:ReceiptDraftState;revision:string}|null>(null)
  const [planReceipt,setPlanReceipt]=useState<PlanReceiptDraft|null>(null)
  const [receipt, setReceipt] = useState<string | null>(null)
  const [settings, setSettings] = useState(false)
  const [online, setOnline] = useState(navigator.onLine)
  const [install, setInstall] = useState<InstallEvent | null>(null)
  const [message, setMessage] = useState('')
  useEffect(() => {
    const connection = () => setOnline(navigator.onLine)
    const hash = () => { setPage(currentPage()); setMobileMenu(false) }
    const prompt = (event: Event) => { event.preventDefault(); setInstall(event as InstallEvent) }
    window.addEventListener('online', connection); window.addEventListener('offline', connection)
    window.addEventListener('hashchange', hash); window.addEventListener('beforeinstallprompt', prompt)
    return () => { window.removeEventListener('online', connection); window.removeEventListener('offline', connection); window.removeEventListener('hashchange', hash); window.removeEventListener('beforeinstallprompt', prompt) }
  }, [])
  async function connect(event: FormEvent) {
    event.preventDefault(); setError(''); setConnecting(true)
    const client = new ApiClient(token.trim())
    try { const workspace = await client.workspace(); setApi(client); setData(workspace); setToken('') }
    catch (e) { setError((e as Error).message) } finally { setConnecting(false) }
  }
  const refresh = useCallback(async () => {
    if (!api) return
    setRefreshing(true); setError('')
    try { setData(await api.workspace()) } catch (e) { setError((e as Error).message) } finally { setRefreshing(false) }
  }, [api])
  function navigate(p: Page) { setPage(p); window.history.pushState(null, '', `#${p}`); setMobileMenu(false) }
  const closeEdit = useCallback(() => setEdit(null), [])
  function openNestedEditor(next: Edit) {
    if (edit?.kind === 'plan' && edit.plan) {
      setParentPlan(edit.plan)
    }
    setEdit(next)
  }
  function closeNestedEditor() {
    if (edit?.kind === 'purchase' && parentPlan) {
      setEdit({ kind: 'plan', plan: parentPlan })
      setParentPlan(null)
      return
    }
    setParentPlan(null)
    closeEdit()
  }
  const closeCreate = useCallback(() => {setCreating(false);setSavedReceiptDraft(null);setPlanReceipt(null)}, [])
  const reviewSaved = useCallback(() => { void refresh() }, [refresh])
  const closeReceipt = useCallback(() => setReceipt(null), [])
  const closeSettings = useCallback(() => setSettings(false), [])
  function logout() { setSavedReceiptDraft(null);setPlanReceipt(null);setApi(null); setData(null); setToken(''); setError(''); setEdit(null); setReceipt(null); setCreating(false); setSettings(false); setMessage('') ; setCheckoutPlan(null)}
  async function installApp() { if (install) { await install.prompt(); await install.userChoice; setInstall(null) } }
  if (!api || !data) return <main className="connect-page"><section className="connect-story"><Brand /><div><span className="eyebrow">Shop thoughtfully. Keep more.</span><h1>A clearer picture<br />of every grocery trip.</h1><p>Your shopping plans, receipts, coupons, and rewards — together in one personal workspace.</p><div className="story-icons"><span><ClipboardList />Plan your trip</span><span><ReceiptText />Know your spending</span><span><Wallet />Track your rewards</span></div></div><small>One workspace. Every little saving.</small></section><section className="connect-form"><span className="icon-tile mint"><ShieldCheck size={26} /></span><h2>Welcome to your workspace</h2><p>Connect to your Grocery Intelligence API to get started.</p>{!online && <Notice>You’re offline. Reconnect to load your records. Financial data is not stored for offline use.</Notice>}{error && <div className="error" role="alert">{error}</div>}<form onSubmit={connect}><label>API access token<input type="password" autoComplete="off" required value={token} onChange={e => setToken(e.target.value)} disabled={connecting} placeholder="Enter your access token" /></label><button className="primary full-width" disabled={connecting || !online}>{connecting ? 'Connecting…' : 'Open my workspace'}</button></form><p className="security-note"><ShieldCheck size={15} />Your token stays in memory. It is not saved on this device.</p><details><summary>Connection & installation help</summary><p>Start the Java API and use its configured access token. In development, Vite forwards API requests to the local backend.</p><p>To install on iPhone, use Share → Add to Home Screen. Installation requires HTTPS, or localhost for desktop development.</p></details></section></main>
  const writable = data.canWrite && online && !refreshing
  return <div className="app-shell">
    <a className="skip-link" href="#main-content" onClick={e => { e.preventDefault(); document.getElementById('main-content')?.focus() }}>Skip to content</a>
    <aside className={`sidebar ${mobileMenu ? 'is-open' : ''}`}><Brand /><button className="mobile-close icon-button" aria-label="Close navigation" onClick={() => setMobileMenu(false)}><X /></button><span className="nav-label">WORKSPACE</span><nav aria-label="Main navigation">{navigation.map(n => <a key={n.id} href={`#${n.id}`} aria-current={page === n.id ? 'page' : undefined} className={page === n.id ? 'selected' : ''} onClick={e => { e.preventDefault(); navigate(n.id) }}><n.icon size={19} /><span>{n.name}</span>{n.id === 'shopping' && data.plans.length > 0 && <span className="nav-count">{data.plans.length}</span>}</a>)}</nav><div className="sidebar-bottom"><div className="sidebar-note"><Leaf size={20} /><strong>Small savings add up.</strong><p>Keep your plans and recorded benefits in perspective.</p></div><button className="workspace-settings" onClick={() => setSettings(true)}><span className="owner-avatar">G</span><span><strong>Owner workspace</strong><small>{data.canWrite ? 'Owner access' : 'Read-only access'}</small></span><ShieldCheck size={17} /></button><button className="text-button logout" onClick={logout}><LogOut size={16} />Disconnect</button></div></aside>
    {mobileMenu && <button className="menu-shade" aria-label="Close navigation" onClick={() => setMobileMenu(false)} />}
    <div className="main-shell"><header className="topbar"><div className="breadcrumb"><button className="icon-button mobile-toggle" aria-label="Open navigation" onClick={() => setMobileMenu(true)}><Menu /></button><span>My workspace</span><span>/</span><strong>{navigation.find(n => n.id === page)?.name}</strong></div><div className="topbar-actions"><span className={`connection-dot ${online ? '' : 'disconnected'}`} /> <span className="connection-text">{online ? 'Connected workspace' : 'Offline'}</span><button className="icon-button" disabled={refreshing || !online} aria-label="Refresh workspace" onClick={() => void refresh()}><RefreshCw size={18} className={refreshing ? 'spinning' : ''} /></button><button className="owner-avatar" aria-label="Workspace settings" onClick={() => setSettings(true)}>G</button></div></header>
      <main id="main-content" tabIndex={-1} className="content">
        {!online && <Notice>You’re offline. Previously loaded records may be out of date; changes are disabled. Reconnect, then refresh.</Notice>}
        {!data.canWrite && <Notice>Read-only access. A write-authorized token is required to save changes.</Notice>}
        {error && <div className="error" role="alert">{error} Showing previously loaded data.<button className="text-button" onClick={() => void refresh()}>Try refresh</button></div>}
        {message && <div className="success" role="status">{message}<button className="icon-button" aria-label="Dismiss message" onClick={() => setMessage('')}><X size={16} /></button></div>}
        {refreshing && !data ? <Loading /> : <WorkspacePages
          data={data}
          api={api}
          onChanged={() => void refresh()}
          page={page}
          writable={writable}
          onPage={navigate}
          onReceipt={setReceipt}
          onEdit={setEdit}
          onAddReceipt={() => {setSavedReceiptDraft(null);setPlanReceipt(null);setCreating(true)}}
          onResumeReceiptDraft={async id=>{if(!api)return;try{const draft=await api.receiptDraft(id);setSavedReceiptDraft({state:JSON.parse(draft.payload) as ReceiptDraftState,revision:draft.revision});setPlanReceipt(null);setCreating(true)}catch(e){setMessage((e as Error).message)}}}
          onPlanReceipt={draft=>{setSavedReceiptDraft(null);setPlanReceipt(draft);setCreating(true)}}
          onCheckout={setCheckoutPlan}
        />}
        <footer className="page-footer"><Leaf size={14} /><span>Grocery Intelligence Platform</span><span>Financial values calculated by your Java backend.</span></footer>
      </main></div>
    {edit && <EditDialog key={
      edit.kind === 'plan' ? `plan-${edit.plan?.planId || 'new'}`
        : edit.kind === 'purchase' ? `purchase-${edit.item?.plannedPurchaseId || 'new'}`
          : edit.kind === 'redeem' ? `redeem-${edit.coupon.couponId}`
            : 'coupon-new'}
      edit={edit} api={api} data={data} onClose={closeNestedEditor} onSaved={() => { setMessage('Saved. Refreshing your recorded data.'); void refresh() }} onReview={() => { setMessage('Refreshing to check the uncertain save result.'); void refresh() }} onEdit={openNestedEditor} online={online} />}
    {checkoutPlan && (
      <CheckoutForm
        api={api}
        planId={checkoutPlan.planId}
        planName={checkoutPlan.name}
        writable={writable}
        onClose={() => setCheckoutPlan(null)}
        onSaved={() => {
          setMessage('Checkout saved to PostgreSQL.')
          void refresh()
        }}
      />
    )}
    {creating && <ReceiptCreate workspace={data??undefined} onOpenExisting={id=>{setCreating(false);setSavedReceiptDraft(null);setPlanReceipt(null);setReceipt(id)}} savedDraft={savedReceiptDraft??undefined} onDraftSaved={()=>void refresh()} planning={planReceipt??undefined} coupons={data?.coupons} products={data?.productLookup} offers={data?.rewardOffers} api={api} enabled={writable} onClose={closeCreate} onReview={reviewSaved} onSaved={id => { setCreating(false);setSavedReceiptDraft(null);setPlanReceipt(null); setReceipt(id); setMessage('Receipt saved.'); void refresh() }} />}
    {receipt && <ReceiptDetail offers={data?.rewardOffers} id={receipt} api={api} onClose={closeReceipt} writable={writable} onChanged={() => void refresh()} />}
    {settings && <Modal title="Your workspace" onClose={closeSettings}><p>This connection serves one owner’s database. The web app does not read or migrate local receipt files.</p><div className="key-values"><div><span>Access</span><strong>{data.canWrite ? 'Owner / write' : 'Read only'}</strong></div><div><span>Data storage</span><strong>Backend database</strong></div><div><span>Offline financial storage</span><strong>Disabled</strong></div></div>{install ? <button className="primary" onClick={() => void installApp()}><Download size={18} />Install application</button> : <p className="muted">Install using your browser’s app menu. On iPhone: Share → Add to Home Screen. HTTPS is required outside localhost.</p>}<div className="form-actions"><button className="secondary" onClick={logout}><LogOut size={17} />Disconnect and clear screen</button></div></Modal>}
  </div>
}
