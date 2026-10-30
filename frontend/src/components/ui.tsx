import { createPortal } from 'react-dom'
import { useEffect, useRef, useLayoutEffect, useState, useId, Children, isValidElement, cloneElement } from 'react'
import type { ReactNode, TextareaHTMLAttributes } from 'react'
import { ArrowRight, ChevronDown, CircleAlert, Inbox, X } from 'lucide-react'

export function InfoHelp({children}:{children:ReactNode}) {
 return <SectionHelp title="More information" label="More information">{children}</SectionHelp>
}

/** The same live form commands are accessible at either end of a long form. */
export function FormActions({children}:{children:ReactNode}) {
 const ref=useRef<HTMLDivElement>(null),[target,setTarget]=useState<HTMLDivElement|null>(null)
 useLayoutEffect(()=>{const node=ref.current,form=node?.closest('form');if(!node||!form||node.closest('[data-original-shopping="true"]'))return
  const groups=Array.from(form.querySelectorAll('.form-actions:not(.form-actions-top)')).filter(n=>n.closest('form')===form)
  if(groups.at(-1)!==node)return
  const host=document.createElement('div');host.className='form-actions form-actions-top';host.setAttribute('role','group');host.setAttribute('aria-label','Top form actions')
  const parent=node.closest('fieldset')??form;parent.insertBefore(host,parent.firstChild);setTarget(host)
  return()=>host.remove()
 },[])
 function top(nodes:ReactNode):ReactNode{return Children.map(nodes,node=>{if(!isValidElement<{children?:ReactNode;'aria-label'?:string;id?:string}>(node))return node
  if(node.type==='button'){const text=Children.toArray(node.props.children).filter(c=>typeof c==='string'||typeof c==='number').join(' ').trim();return cloneElement(node,{'aria-label':`Top: ${node.props['aria-label']??text??'Form action'}`,id:undefined})}
  return node.props.children?cloneElement(node,{},top(node.props.children)):node
 })}
 return <>{target&&createPortal(top(children),target)}<div ref={ref} className="form-actions">{children}</div></>
}

export function Empty({ title, children, action }: { title: string; children?: ReactNode; action?: ReactNode }) {
  return <div className="empty"><span className="empty-icon"><Inbox size={25} /></span><h3>{title}</h3><p>{children}</p>{action}</div>
}
export function Notice({ children }: { children: ReactNode }) { return <div className="notice"><CircleAlert size={18} /><div>{children}</div></div> }
export function Unavailable({ children }: { children: ReactNode }) { return <div className="unavailable"><span className="eyebrow">Not available yet</span><p>{children}</p></div> }
export function SectionHelp({title,children,label}:{title:string;children:ReactNode;label?:string}){
 const [open,setOpen]=useState(false),[position,setPosition]=useState({top:0,left:0,width:360})
 const button=useRef<HTMLButtonElement>(null),tip=useRef<HTMLDivElement>(null),pointer=useRef(''),keyboard=useRef(false),timer=useRef<ReturnType<typeof setTimeout>|null>(null),id=useId()
 const clear=()=>{if(timer.current)clearTimeout(timer.current)}
 useEffect(()=>()=>clear(),[])
 useLayoutEffect(()=>{if(!open)return
  const place=()=>{const rect=button.current?.getBoundingClientRect();if(!rect)return;const width=Math.min(400,window.innerWidth-24),height=tip.current?.offsetHeight??0,left=Math.max(12,Math.min(rect.right-width,window.innerWidth-width-12));const top=rect.bottom+8+height<=window.innerHeight-12?rect.bottom+8:Math.max(12,rect.top-height-8);setPosition(old=>old.top===top&&old.left===left&&old.width===width?old:{top,left,width})}
  place();window.addEventListener('resize',place);return()=>window.removeEventListener('resize',place)
 },[open,children])
 useEffect(()=>{if(!open)return
  const outside=(e:PointerEvent)=>{if(!button.current?.contains(e.target as Node)&&!tip.current?.contains(e.target as Node)){keyboard.current=false;setOpen(false)}}
  const escape=(e:KeyboardEvent)=>{if(e.key==='Escape'){e.preventDefault();e.stopPropagation();keyboard.current=false;setOpen(false)}}
  document.addEventListener('pointerdown',outside);document.addEventListener('keydown',escape)
  return()=>{document.removeEventListener('pointerdown',outside);document.removeEventListener('keydown',escape)}
 },[open])
 const leave=()=>{clear();timer.current=setTimeout(()=>{if(!keyboard.current)setOpen(false)},100)}
 return <><button ref={button} type="button" className="icon-button help-trigger" aria-label={label??`About ${title}`} aria-describedby={open?id:undefined} aria-expanded={open} onPointerDown={e=>{pointer.current=e.pointerType||'mouse';keyboard.current=false}} onPointerEnter={e=>{if(e.pointerType==='mouse'){clear();setOpen(true)}}} onPointerLeave={e=>{if(e.pointerType==='mouse')leave()}} onFocus={()=>{if(!pointer.current){keyboard.current=true;clear();setOpen(true)}}} onBlur={()=>{keyboard.current=false;setOpen(false)}} onClick={()=>{clear();if(pointer.current==='touch')setOpen(value=>!value);else setOpen(true);pointer.current=''}}>ⓘ</button>{open&&createPortal(<div ref={tip} id={id} role="tooltip" aria-label={title} data-help-visible="true" className="help-tooltip" style={position} onPointerEnter={clear} onPointerLeave={e=>{if(e.pointerType==='mouse')leave()}}><strong>{title}</strong><div>{children}</div></div>,document.body)}</>
}
export function Panel({ title, subtitle, action, children, className = '',help }: {help?:ReactNode; className?: string; title?: ReactNode; subtitle?: ReactNode; action?: ReactNode; children?: ReactNode }) {
  return <section className={`panel ${className}`}>{(title||subtitle||action||help)&&<div className="panel-head"><div>{title&&<h2>{title}</h2>}{subtitle && <p>{subtitle}</p>}</div><div className="panel-actions">{help&&<SectionHelp title={typeof title==='string'?title:'This section'}>{help}</SectionHelp>}{action}</div></div>}{children}</section>
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
      if(Array.from(document.querySelectorAll('[role="dialog"]')).at(-1)!==root)return
      if (e.key === 'Escape') { if(document.querySelector('[role="tooltip"][data-help-visible]'))return;e.preventDefault(); closeRef.current() }
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
