import type { Inventory } from '../../types/platform'

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
