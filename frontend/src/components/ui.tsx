import { useEffect, useRef, useLayoutEffect } from 'react'
import type { ReactNode, TextareaHTMLAttributes } from 'react'
import { ArrowRight, ChevronDown, CircleAlert, Inbox, X } from 'lucide-react'

export function Empty({ title, children, action }: { title: string; children?: ReactNode; action?: ReactNode }) {
  return <div className="empty"><span className="empty-icon"><Inbox size={25} /></span><h3>{title}</h3><p>{children}</p>{action}</div>
}
export function Notice({ children }: { children: ReactNode }) { return <div className="notice"><CircleAlert size={18} /><div>{children}</div></div> }
export function Unavailable({ children }: { children: ReactNode }) { return <div className="unavailable"><span className="eyebrow">Not available yet</span><p>{children}</p></div> }
export function Panel({ title, subtitle, action, children, className = '' }: { className?: string; title?: ReactNode; subtitle?: ReactNode; action?: ReactNode; children?: ReactNode }) {
  return <section className={`panel ${className}`}>{(title||subtitle||action)&&<div className="panel-head"><div>{title&&<h2>{title}</h2>}{subtitle && <p>{subtitle}</p>}</div>{action}</div>}{children}</section>
}
export function PageHeader({ title, subtitle, action }: { title: string; subtitle: string; action?: ReactNode }) {
  return <div className="page-heading"><div><span className="eyebrow">Your grocery workspace</span><h1>{title}</h1><p>{subtitle}</p></div>{action}</div>
}
export function Badge({ children }: { children: ReactNode }) { return <span className="badge">{children}</span> }
export function LinkButton({ children, onClick }: { children: ReactNode; onClick: () => void }) { return <button className="text-button" onClick={onClick}>{children}<ArrowRight size={15} /></button> }
export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null)
  const closeRef=useRef(onClose)
  useLayoutEffect(()=>{closeRef.current=onClose},[onClose])
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    const root = ref.current
    const controls = () => Array.from(root?.querySelectorAll<HTMLElement>('button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),a[href],[tabindex="0"]') || [])
    controls()[0]?.focus()
    function key(e: KeyboardEvent) {
      if (e.key === 'Escape') { e.preventDefault(); closeRef.current() }
      if (e.key === 'Tab') { const list = controls(); const first = list[0]; const last = list.at(-1); if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last?.focus() } else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first?.focus() } }
    }
    document.addEventListener('keydown', key)
    const overflow = document.body.style.overflow; document.body.style.overflow = 'hidden'
    return () => { document.removeEventListener('keydown', key); document.body.style.overflow = overflow; previous?.focus() }
  }, [])
  return <div className="modal-shade"><div ref={ref} className="modal" role="dialog" aria-modal="true" aria-label={title}><div className="modal-heading"><h2>{title}</h2><button className="icon-button" aria-label="Close dialog" onClick={onClose}><X size={20} /></button></div>{children}</div></div>
}
export function Loading() { return <div className="loading" role="status"><span className="spinner" />Loading your workspace…</div> }


/** Starts at the standard control height and grows to reveal the complete value. */
export function AutoSizeTextArea(props: TextareaHTMLAttributes<HTMLTextAreaElement> & { value: string }) {
  const ref = useRef<HTMLTextAreaElement>(null)
  useLayoutEffect(() => {
    const node = ref.current
    if (!node) return
    const resize = () => {
      node.style.height = 'auto'
      const css = getComputedStyle(node)
      const border = parseFloat(css.borderTopWidth) + parseFloat(css.borderBottomWidth)
      node.style.height = `${Math.max(parseFloat(css.minHeight) || 0, node.scrollHeight + border)}px`
    }
    resize()
    if (typeof ResizeObserver === 'undefined') return
    let width = node.getBoundingClientRect().width
    const observer = new ResizeObserver(() => {
      const next = node.getBoundingClientRect().width
      if (next !== width) { width = next; resize() }
    })
    observer.observe(node)
    return () => observer.disconnect()
  }, [props.value])
  return <textarea {...props} ref={ref} rows={1} className={`autosize-input ${props.className ?? ''}`} />
}


export function Disclosure({title,children,className='',open=true}:{title:ReactNode;children:ReactNode;className?:string;open?:boolean}) {
 return <details open={open} className={`section-disclosure ${className}`}><summary><div className="disclosure-title">{title}</div><ChevronDown size={18} aria-hidden="true"/></summary><div className="disclosure-body">{children}</div></details>
}
