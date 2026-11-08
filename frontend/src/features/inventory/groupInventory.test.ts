import { describe, expect, it } from 'vitest'
import { groupInventory, inventoryUnitPrice } from './groupInventory'
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

it('calculates purchase cost per load and per actual roll without using stock on hand',()=>{
 expect(inventoryUnitPrice({...item('loads',null,'2','12.00'),unitsPerPackage:'40',unitName:'Loads',quantityOnHand:'1'})).toEqual({amount:'0.1500',unit:'load'});
 expect(inventoryUnitPrice({...item('rolls',null,'3','9.00'),unitsPerPackage:'12',unitName:'Rolls'})).toEqual({amount:'0.2500',unit:'roll'});
});
it('does not guess missing package counts, prices or incompatible grouped units',()=>{
 expect(inventoryUnitPrice(item('a',null,'1','5.00'))).toBeNull();
 expect(inventoryUnitPrice({...item('a',null,'0','0.00'),unitsPerPackage:'12',unitName:'Rolls'})).toBeNull();
 const a={...item('a','019560284600','1','4.00'),unitsPerPackage:'20',unitName:'Loads'},b={...item('b','00019560284600','1','2.00'),unitsPerPackage:'10',unitName:'Rolls'};
 expect(inventoryUnitPrice(groupInventory([a,b])[0])).toBeNull();b.unitName='Loads';
 expect(inventoryUnitPrice(groupInventory([a,b])[0])).toEqual({amount:'0.2000',unit:'load'});
 b.netCost=null;expect(inventoryUnitPrice(groupInventory([a,b])[0])).toBeNull();
});
it('keeps conflicting grouped categories visible instead of guessing',()=>{
 const a={...item('a','019560284600','1','4.00'),category:'Personal Care'},b={...item('b','00019560284600','1','2.00'),category:'Beauty'};
 expect(groupInventory([a,b])[0].category).toBe('Mixed categories');
});

it('compares decimal fluid-ounce package sizes using discounted purchase cost',()=>{
 expect(inventoryUnitPrice({...item('soap',null,'2','3.00'),comparisonSize:'7.5',comparisonUnit:'FL_OZ'})).toEqual({amount:'0.2000',unit:'fl oz'});
 expect(inventoryUnitPrice({...item('bars',null,'1','4.80'),comparisonSize:'19.2',comparisonUnit:'OZ'})).toEqual({amount:'0.2500',unit:'oz'});
});
it('does not combine fluid ounces with weight ounces or guess missing measurements',()=>{
 const a={...item('a','019560284600','1','3.00'),comparisonSize:'7.5',comparisonUnit:'FL_OZ'},b={...item('b','00019560284600','1','3.00'),comparisonSize:'7.5',comparisonUnit:'OZ'};
 expect(inventoryUnitPrice(groupInventory([a,b])[0])).toBeNull();
 expect(inventoryUnitPrice({...a,comparisonSize:null})).toBeNull();expect(inventoryUnitPrice({...a,comparisonSize:'0'})).toBeNull();expect(inventoryUnitPrice({...a,comparisonSize:'7.12345'})).toBeNull();
 b.comparisonUnit='FL_OZ';b.comparisonSize='12.5';expect(inventoryUnitPrice(groupInventory([a,b])[0])).toEqual({amount:'0.3000',unit:'fl oz'});
});

it('uses recorded package size or secondary loads without changing package or stock quantities',()=>{
 const value={...item('detergent',null,'2','16.00'),quantityOnHand:null,packageDetails:{size:'80',unit:'fl oz',secondarySize:'80',secondaryUnit:'loads'}};
 expect(inventoryUnitPrice(value)).toEqual({amount:'0.1000',unit:'fl oz'});expect(inventoryUnitPrice(value,'load')).toEqual({amount:'0.1000',unit:'load'});expect(value.quantityOnHand).toBeNull();expect(value.totalPurchased).toBe('2');
});
it('does not use one receipt package size for another receipt with missing size',()=>{
 const value={...item('soap',null,'2','8.00'),packageDetails:{size:'8',unit:'fl oz'},sources:[{receiptId:'one',lineItemId:'a',revision:'1',quantity:'1',active:true,productName:'Soap',store:null,receiptNumber:null,date:null,packageDetails:{size:'8',unit:'fl oz'}},{receiptId:'two',lineItemId:'b',revision:'1',quantity:'1',active:true,productName:'Soap',store:null,receiptNumber:null,date:null}]};
 expect(inventoryUnitPrice(value)).toBeNull();
});
