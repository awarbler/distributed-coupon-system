import { useId, useState } from 'react'

const stores = ['Walmart', 'Walgreens', 'H-E-B', 'Costco', 'CVS', 'Target', 'Kroger', "Sam's Club", 'Amazon', 'Dollar General']
const key = (value: string) => value.toLowerCase().replace(/[^a-z0-9]/g, '')

export function StoreInput({ value, onChange, required = false, label = 'Store' }: {value: string; onChange: (value: string) => void; required?: boolean; label?: string}) {
  const id = useId(), [focused, setFocused] = useState(false), [active, setActive] = useState(-1)
  const matches = key(value).length >= 2 ? stores.filter(store => key(store).startsWith(key(value)) && store !== value) : []
  const open = focused && matches.length > 0
  function choose(store: string) { onChange(store); setFocused(false); setActive(-1) }
  return <div className="store-autocomplete"><label htmlFor={id}>{label}</label><input id={id} required={required} maxLength={200} role="combobox" aria-autocomplete="list" aria-expanded={open} aria-controls={open ? `${id}-suggestions` : undefined} aria-activedescendant={open && active >= 0 ? `${id}-${active}` : undefined} autoComplete="off" value={value} onFocus={() => setFocused(true)} onBlur={() => setFocused(false)} onChange={e => {onChange(e.target.value); setFocused(true); setActive(-1)}} onKeyDown={e => {
    if(e.key === 'Escape') {setFocused(false); return}
    if(!open) return
    if(e.key === 'ArrowDown' || e.key === 'ArrowUp') {e.preventDefault(); setActive((active + (e.key === 'ArrowDown' ? 1 : -1) + matches.length) % matches.length)}
    if(e.key === 'Enter') {e.preventDefault(); choose(matches[active < 0 ? 0 : active])}
  }}/>{open && <div id={`${id}-suggestions`} role="listbox" aria-label="Store suggestions">{matches.map((store,i) => <button id={`${id}-${i}`} key={store} type="button" role="option" aria-selected={active === i} className="text-button" onMouseDown={e => e.preventDefault()} onClick={() => choose(store)}>{store}</button>)}</div>}</div>
}

export function formatReceiptNumber(store: string, value: string): string {
  if(!/^walgreens(?:\s|#|$)/i.test(store.trim())) return value
  const digits = value.replace(/\D/g, '')
  return digits.length === 20 ? digits.match(/.{4}/g)!.join('-') : value
}
