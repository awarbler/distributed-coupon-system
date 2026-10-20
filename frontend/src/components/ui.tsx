import { useEffect, useRef } from 'react'
import type { ReactNode } from 'react'
import { ArrowRight, CircleAlert, Inbox, X } from 'lucide-react'

export function Empty({ title, children, action }: { title: string; children?: ReactNode; action?: ReactNode }) {
  return <div className="empty"><span className="empty-icon"><Inbox size={25} /></span><h3>{title}</h3><p>{children}</p>{action}</div>
}
export function Notice({ children }: { children: ReactNode }) { return <div className="notice"><CircleAlert size={18} /><div>{children}</div></div> }
export function Unavailable({ children }: { children: ReactNode }) { return <div className="unavailable"><span className="eyebrow">Not available yet</span><p>{children}</p></div> }
export function Panel({ title, subtitle, action, children }: { title: string; subtitle?: string; action?: ReactNode; children: ReactNode }) {
  return <section className="panel"><div className="panel-head"><div><h2>{title}</h2>{subtitle && <p>{subtitle}</p>}</div>{action}</div>{children}</section>
}
export function PageHeader({ title, subtitle, action }: { title: string; subtitle: string; action?: ReactNode }) {
  return <div className="page-heading"><div><span className="eyebrow">Your grocery workspace</span><h1>{title}</h1><p>{subtitle}</p></div>{action}</div>
}
export function Badge({ children }: { children: ReactNode }) { return <span className="badge">{children}</span> }
export function LinkButton({ children, onClick }: { children: ReactNode; onClick: () => void }) { return <button className="text-button" onClick={onClick}>{children}<ArrowRight size={15} /></button> }
export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    const root = ref.current
    const controls = () => Array.from(root?.querySelectorAll<HTMLElement>('button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),a[href],[tabindex="0"]') || [])
    controls()[0]?.focus()
    function key(e: KeyboardEvent) {
      if (e.key === 'Escape') { e.preventDefault(); onClose() }
      if (e.key === 'Tab') { const list = controls(); const first = list[0]; const last = list.at(-1); if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last?.focus() } else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first?.focus() } }
    }
    document.addEventListener('keydown', key)
    const overflow = document.body.style.overflow; document.body.style.overflow = 'hidden'
    return () => { document.removeEventListener('keydown', key); document.body.style.overflow = overflow; previous?.focus() }
  }, [onClose])
  return <div className="modal-shade"><div ref={ref} className="modal" role="dialog" aria-modal="true" aria-label={title}><div className="modal-heading"><h2>{title}</h2><button className="icon-button" aria-label="Close dialog" onClick={onClose}><X size={20} /></button></div>{children}</div></div>
}
export function Loading() { return <div className="loading" role="status"><span className="spinner" />Loading your workspace…</div> }
