import { describe, expect, it } from 'vitest'
import { groupInventory } from './groupInventory'
import type { Inventory } from '../../types/platform'
const item=(id:string,sku:string|null,quantity:string,cost:string|null):Inventory=>({inventoryId:id,productId:id,revision:'1',sku,totalPurchased:quantity,quantityOnHand:quantity,netCost:cost})
describe('inventory product grouping',()=>{
 it('groups UPC and GTIN without dropping purchases or modifying source records',()=>{
  const rows=[item('a','019560284600','2','4.20'),item('b','00019560284600','1','1.25')]
  const result=groupInventory(rows)
  expect(result).toHaveLength(1)
  expect(result[0]).toMatchObject({totalPurchased:'3',quantityOnHand:'3',netCost:'5.45'})
  expect(result[0].members).toEqual(rows)
  expect(rows[0].totalPurchased).toBe('2')
 })
 it('keeps distinct short store SKUs and unidentified products separate',()=>{
  expect(groupInventory([item('a','0123','1','1.00'),item('b','123','1','1.00'),item('c',null,'1',null),item('d',null,'1',null)])).toHaveLength(4)
 })
 it('retains missing costs and purchase evidence across grouped products',()=>{
  const a=item('a','019560284600','2','4.20'),b=item('b','00019560284600','1',null)
  a.sources=[{receiptId:'r',lineItemId:'l',revision:'1',quantity:'2',active:true,productName:'Plates',store:'Walgreens',receiptNumber:'R1',date:'2026-10-25'}]
  expect(groupInventory([a,b])[0]).toMatchObject({netCost:null,sources:a.sources})
 })
})
