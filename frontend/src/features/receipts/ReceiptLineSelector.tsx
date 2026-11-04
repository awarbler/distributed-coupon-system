import { useState } from 'react'
import { money } from '../../api/format'
import { ProductSelectionList, ReceiptScopeChoices } from '../../components/ProductSelection'
export interface SelectableLine { id: string; description: string; sku?: string | null; quantity: string; price: string; lineTotal?: string | null }
/** Shared stable-line and quantity selector for discounts, rebates and reward promotions. */
export function ReceiptLineSelector({ lines, value, onChange, name }: { lines: SelectableLine[]; value: Record<string, string>; onChange: (value: Record<string, string>) => void; name: string }) {
 const [choosingProducts,setChoosingProducts]=useState(Object.keys(value).length>0)
 const wholeReceipt=!choosingProducts&&!Object.keys(value).length
 return <fieldset className="receipt-selection receipt-selection-compact"><legend>{name}</legend><ReceiptScopeChoices name={`${name}: applies to`} wholeReceipt={wholeReceipt} onChange={whole=>{setChoosingProducts(!whole);if(whole)onChange({})}}/>{!wholeReceipt&&<ProductSelectionList requireSelection products={lines.map((line,i)=>({id:line.id,name:line.description,quantity:line.quantity,ariaLabel:`${name}: line ${i+1}`,detail:<>{line.sku||'SKU not recorded'} · Unit {money(line.price)}{line.lineTotal?` · Total ${money(line.lineTotal)}`:''}</>,extra:line.id in value?<label>Qualifying quantity<input aria-label={`Qualifying quantity for line ${i+1}`} required type="number" min="1" max={line.quantity} step="1" value={value[line.id]} onChange={e=>onChange({...value,[line.id]:e.target.value})}/></label>:undefined}))} selected={Object.keys(value)} onToggle={(id,checked)=>{const next={...value};if(checked)next[id]=lines.find(l=>l.id===id)!.quantity;else delete next[id];onChange(next)}}/>}</fieldset>
}
