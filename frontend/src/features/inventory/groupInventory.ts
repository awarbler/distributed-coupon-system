import type { Inventory, PackageDetails } from '../../types/platform'

/** UPC-A, EAN and GTIN representations share a padded comparison key; SKUs remain exact. */
export function inventoryProductKey(item: Inventory): string {
  const sku = item.sku?.trim()
  return sku ? /^\d{12,14}$/.test(sku) ? `GTIN:${sku.padStart(14, '0')}` : `SKU:${sku}` : `PRODUCT:${item.productId}`
}

export interface InventoryGroup extends Inventory { members: Inventory[] }

export function groupInventory(items: Inventory[]): InventoryGroup[] {
  const groups = new Map<string, InventoryGroup>()
  for (const item of items) {
    const key = inventoryProductKey(item)
    const group = groups.get(key)
    if (!group) { groups.set(key, { ...item, members: [item] }); continue }
    group.members.push(item)
    const categories=new Set(group.members.map(row=>row.category?.trim()||''))
    group.category=categories.size===1?[...categories][0]||null:'Mixed categories'
    group.sources = [...(group.sources ?? []), ...(item.sources ?? [])]
    group.history = [...(group.history ?? []), ...(item.history ?? [])].sort((a,b)=>b.at.localeCompare(a.at))
    group.totalPurchased = String(group.members.reduce((total, row)=>total + BigInt(row.totalPurchased ?? '0'), 0n))
    group.quantityOnHand = group.members.some(row=>row.quantityOnHand == null) ? null : String(group.members.reduce((total,row)=>total+BigInt(row.quantityOnHand!),0n))
    group.individualPurchased = group.members.every(row=>row.individualPurchased!=null) ? String(group.members.reduce((total,row)=>total+BigInt(row.individualPurchased!),0n)) : null
    group.quantityConfirmed = group.members.every(row=>row.quantityConfirmed)
    group.needsProductReview = group.members.some(row=>row.needsProductReview)
    if ((item.lastPurchased ?? '') > (group.lastPurchased ?? '')) group.lastPurchased = item.lastPurchased
    group.netCost = group.members.some(row=>row.netCost == null) ? null : sumMoney(group.members.map(row=>row.netCost!))
  }
  return [...groups.values()]
}

function sumMoney(amounts: string[]): string {
  const cents = amounts.reduce((total,amount)=>{const [whole,fraction='']=amount.split('.');return total+BigInt(whole)*100n+BigInt(fraction.padEnd(2,'0'))},0n)
  return `${cents/100n}.${String(cents%100n).padStart(2,'0')}`
}

/** Keep liquid volume, weight and counts separate when comparing products. */
export function normalizeInventoryUnit(unit:string):string {
 const value=unit.trim().toLowerCase().replace(/[.]/g,'').replace(/\s+/g,' ')
 if(['fl oz','fluid ounce','fluid ounces'].includes(value))return 'fl oz'
 if(['oz','ounce','ounces'].includes(value))return 'oz'
 if(value==='toothbrushes')return 'toothbrush'
 return value.replace(/s$/, '')
}

/** User-recorded package contents only; stock consumption does not change purchase unit cost. */
export function inventoryUnitPrice(item: Inventory,requestedUnit?:string): {amount:string;unit:string}|null {
 const members='members' in item?(item as InventoryGroup).members:[item]
 const measure=(row:Inventory,details:PackageDetails|null|undefined=row.packageDetails)=>{
  const choices=[...(row.comparisonUnit?[{size:row.comparisonSize,unit:row.comparisonUnit==='FL_OZ'?'fl oz':row.comparisonUnit==='OZ'?'oz':''}]:[]),...(row.unitsPerPackage?[{size:row.unitsPerPackage,unit:normalizeInventoryUnit(row.unitName??'')}]:[]),...(details?[{size:details.size,unit:normalizeInventoryUnit(details.unit??'')},{size:details.itemCount,unit:normalizeInventoryUnit(details.itemUnit??'')},{size:details.secondarySize,unit:normalizeInventoryUnit(details.secondaryUnit??'')}]:[])];
  return choices.find(value=>value.size&&value.unit&&(!requestedUnit||value.unit===normalizeInventoryUnit(requestedUnit)));
 }
 const unit=members[0]?measure(members[0])?.unit:null
 if(!unit||item.netCost==null||!/^\d+(?:\.\d{1,2})?$/.test(item.netCost))return null
 let count=0n
 for(const row of members){
  const sources=row.sources?.filter(source=>source.active);
  const portions=!row.comparisonUnit&&!row.unitsPerPackage&&sources?.length?sources.map(source=>({measurement:measure(row,source.packageDetails??null),quantity:source.quantity})):[{measurement:measure(row),quantity:row.totalPurchased}];
  for(const portion of portions){const size=portion.measurement?.size;
   if(portion.measurement?.unit!==unit||!/^\d+(?:\.\d{1,4})?$/.test(size??'')||!/^\d+$/.test(portion.quantity??''))return null
   const [whole,fraction='']=size!.split('.')
   const scaledSize=BigInt(whole)*10000n+BigInt(fraction.padEnd(4,'0'))
   if(scaledSize<=0n)return null
   count+=scaledSize*BigInt(portion.quantity!)
  }
 }
 if(count<=0n)return null
 const [whole,fraction='']=item.netCost.split('.')
 const cents=BigInt(whole)*100n+BigInt(fraction.padEnd(2,'0'))
 const scaled=(cents*1000000n+count/2n)/count
 return {amount:`${scaled/10000n}.${String(scaled%10000n).padStart(4,'0')}`,unit}
}

// Walgreens department names and household paths; assignments remain user controlled.
// Source: https://www.walgreens.com/store/c/paper-and-plastic/ID=20000941-tier2general
export const inventoryCategories=[
 'Household & Pet Essentials',
 'Household & Pet Essentials > Laundry Care',
 'Household & Pet Essentials > Paper & Plastic > Toilet Paper',
 'Household & Pet Essentials > Paper & Plastic > Paper Towels',
 'Household & Pet Essentials > Paper & Plastic > Facial Tissue',
 'Grocery & Beverages','Beauty','Personal Care','Baby & Kids',
 'Medicines & Treatments','Vitamins & Supplements','Home Health Care Solutions',
 'Fitness & Nutrition','First Aid','Incontinence','Sexual Wellness',
 'Contact Lenses','Toys, Games & Books','Electronics & Office','Home Goods','Pet','Gift Cards'
]
