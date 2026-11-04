import { useId } from 'react'
import type { ReactNode } from 'react'

export function ReceiptScopeChoices({wholeReceipt,onChange,name='Applies to'}:{wholeReceipt:boolean;onChange:(whole:boolean)=>void;name?:string}) {
 const group=useId()
 return <fieldset className="product-scope" aria-label={name}><legend>Applies to</legend><div className="product-scope-options"><label><input type="radio" name={group} checked={wholeReceipt} onChange={()=>onChange(true)}/>Entire receipt</label><label><input type="radio" name={group} checked={!wholeReceipt} onChange={()=>onChange(false)}/>Choose products</label></div></fieldset>
}
export interface ProductChoice {id:string;name:string;quantity:string;detail?:ReactNode;extra?:ReactNode;ariaLabel?:string;disabled?:boolean}
export function ProductSelectionList({products,selected,onToggle,requireSelection=false}:{products:ProductChoice[];selected:string[];onToggle:(id:string,checked:boolean)=>void;requireSelection?:boolean}) {
 const count=products.filter(p=>selected.includes(p.id)).length
 return <div className="product-selection"><div className="product-selection-list">{products.map((p,i)=><div className="product-choice" key={p.id}><label className="product-choice-label"><input type="checkbox" aria-label={p.ariaLabel} checked={selected.includes(p.id)} disabled={p.disabled} required={requireSelection&&count===0&&i===0} onChange={e=>onToggle(p.id,e.target.checked)}/><span className="product-choice-copy"><span className="product-choice-name">{p.name||'Unnamed product'}</span>{p.detail&&<small>{p.detail}</small>}</span><span className="product-quantity-badge">Qty {p.quantity}</span></label>{p.extra&&<div className="product-choice-extra">{p.extra}</div>}</div>)}</div>{!products.length&&<p className="muted">Add a product to make a selection.</p>}<small className="product-selection-count" aria-live="polite">{count} {count===1?'product':'products'} selected</small></div>
}
