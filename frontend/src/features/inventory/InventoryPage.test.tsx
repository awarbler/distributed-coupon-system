import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
import { ApiClient, ApiError } from '../../api/client'
import { workspace } from '../../api/fixtures.test-support'
import { WorkspacePages } from '../../pages/WorkspacePages'

it('imports receipt-backed inventory and saves confirmed stock with an optional blank location',async()=>{
 const data=workspace();data.inventory=[{inventoryId:'stock',productId:'product',revision:'2',productName:'Olay',sku:'00123',totalPurchased:'5',quantityOnHand:null,quantityConfirmed:false,location:null,sources:[{receiptId:'receipt',lineItemId:'line',revision:'1',quantity:'5',active:true,productName:'Olay',store:'Walgreens',receiptNumber:'R1',date:'2026-10-24'}]}]
 const api=new ApiClient('test'),mutate=vi.spyOn(api,'mutate').mockResolvedValue({saved:true}),changed=vi.fn(),user=userEvent.setup()
 render(<WorkspacePages page="inventory" data={data} api={api} writable onChanged={changed} onPage={vi.fn()} onReceipt={vi.fn()} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/>)
 expect(screen.getByText('00123')).toBeInTheDocument();expect(screen.queryByRole('columnheader',{name:'Quantity on hand'})).not.toBeInTheDocument();expect(screen.getByText('Needs cost review')).toBeInTheDocument()
 await user.click(screen.getByRole('button',{name:'View / update'}));expect(screen.getByLabelText('Location (optional)')).not.toBeRequired();await user.selectOptions(screen.getByLabelText('Inventory action'),'SET');await user.type(screen.getByLabelText('Quantity on hand'),'3');await user.click(screen.getByRole('button',{name:'Save inventory'}))
 expect(mutate).toHaveBeenCalledWith('/inventory/stock','PUT',expect.objectContaining({action:'SET',quantity:3,location:null}),expect.any(String),'2');expect(changed).toHaveBeenCalled()
})

it('collapses inventory sections without losing filters or stock form inputs',async()=>{
 const data=workspace();data.inventory=[{inventoryId:'stock',productId:'product',revision:'2',productName:'Olay',sku:'00123',totalPurchased:'5',quantityOnHand:null}];const user=userEvent.setup()
 render(<WorkspacePages page="inventory" data={data} api={new ApiClient('test')} writable onPage={vi.fn()} onReceipt={vi.fn()} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/>)
 const search=screen.getByLabelText('Search products');await user.type(search,'Olay')
 await user.click(screen.getByRole('button',{name:'Collapse Inventory filters'}));expect(search).not.toBeVisible();await user.click(screen.getByRole('button',{name:'Expand Inventory filters'}));expect(search).toHaveValue('Olay')
 await user.click(screen.getByRole('button',{name:'View / update'}));await user.selectOptions(screen.getByLabelText('Inventory action'),'SET');const quantity=screen.getByLabelText('Quantity on hand');await user.type(quantity,'3')
 await user.click(screen.getByText('Update stock',{exact:true}));expect(quantity).not.toBeVisible();await user.click(screen.getByText('Update stock',{exact:true}));expect(quantity).toHaveValue('3')
 expect(screen.getByText('Product summary')).toBeInTheDocument();expect(screen.getByText('Purchase history')).toBeInTheDocument();expect(screen.getByText('Stock history')).toBeInTheDocument()
})

it('requires a new count when switching inventory from packs to individual items',async()=>{
 const data=workspace();data.inventory=[{inventoryId:'stock',productId:'product',revision:'3',productName:'Bic Razors',sku:'070330714170',quantityOnHand:'1',totalPurchased:'1',unitsPerPackage:'4',unitName:'Razors',countUnit:'PACKAGE',individualPurchased:'4',individualOnHand:'4'}];const api=new ApiClient('test'),mutate=vi.spyOn(api,'mutate').mockResolvedValue({saved:true}),user=userEvent.setup()
 render(<WorkspacePages data={data} page="inventory" api={api} writable onPage={vi.fn()} onReceipt={vi.fn()} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/>)
 await user.click(screen.getByRole('button',{name:'View / update'}));await user.selectOptions(screen.getByLabelText('Track stock as'),'ITEM');expect(screen.getByLabelText('Quantity on hand')).toHaveValue('')
 await user.type(screen.getByLabelText('Quantity on hand'),'4');await user.click(screen.getByRole('button',{name:'Save inventory'}));expect(mutate.mock.calls[0][2]).toMatchObject({quantity:4,unitsPerPackage:4,unitName:'Razors',countUnit:'ITEM'})
})


it('sorts inventory headings in both directions using numeric quantities',async()=>{
 const data=workspace();data.inventory=[{inventoryId:'a',productId:'a',revision:'1',productName:'Alpha',sku:'002',totalPurchased:'10',quantityOnHand:'2',location:'Pantry',netCost:'10.00'},{inventoryId:'b',productId:'b',revision:'1',productName:'Beta',sku:'001',totalPurchased:'2',quantityOnHand:'10',location:'Bathroom',netCost:'2.00'}];const user=userEvent.setup()
 render(<WorkspacePages page="inventory" data={data} writable onPage={vi.fn()} onReceipt={vi.fn()} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/>)
 const first=()=>screen.getAllByRole('row')[1].textContent
 await user.click(screen.getByRole('button',{name:'Quantity purchased'}));expect(first()).toContain('Beta')
 await user.click(screen.getByRole('button',{name:'Quantity purchased'}));expect(first()).toContain('Alpha')
 expect(screen.getByRole('columnheader',{name:/Quantity purchased/})).toHaveAttribute('aria-sort','descending')
 await user.click(screen.getByRole('button',{name:'SKU / UPC'}));expect(first()).toContain('Beta')
 await user.click(screen.getByRole('button',{name:'Cost after discounts'}));expect(first()).toContain('Beta')
 await user.click(screen.getByRole('button',{name:'Cost after discounts'}));expect(first()).toContain('Alpha')
 expect(screen.queryByRole('columnheader',{name:'Location'})).not.toBeInTheDocument()
})


it('shows all inventory products by default and offers an optional five-item preview',async()=>{
 const data=workspace();data.inventory=Array.from({length:7},(_,i)=>({inventoryId:`stock-${i}`,productId:`p-${i}`,revision:'1',productName:`Item ${i}`,quantityOnHand:'1',addedAt:`2026-10-${String(i+10).padStart(2,'0')}T12:00:00Z`}))
 const user=userEvent.setup();render(<WorkspacePages page="inventory" data={data} writable onPage={vi.fn()} onReceipt={vi.fn()} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/>)
 expect(screen.getAllByRole('row')).toHaveLength(8);await user.click(screen.getByRole('button',{name:'Show Latest Five Inventory Items'}));expect(screen.getAllByRole('row')).toHaveLength(6);expect(screen.getAllByRole('row')[1]).toHaveTextContent('Item 6');expect(screen.queryByText('Item 0')).not.toBeInTheDocument()
 const arrow=screen.getByRole('button',{name:'Show All Inventory Items'});expect(arrow).toHaveTextContent('Show all products');await user.click(arrow);expect(screen.getAllByRole('row')).toHaveLength(8)
 await user.click(screen.getByRole('button',{name:'Show Latest Five Inventory Items'}));expect(screen.getAllByRole('row')).toHaveLength(6)
})

it('filters and sorts by saved category, displays unit pricing, and saves the category with package details',async()=>{
 const data=workspace();data.inventory=[{inventoryId:'stock',productId:'product',revision:'2',productName:'Laundry',totalPurchased:'2',quantityOnHand:'1',netCost:'12.00',unitsPerPackage:'40',unitName:'Loads',category:'Household & Pet Essentials > Laundry Care'},{inventoryId:'other',productId:'other',revision:'1',productName:'Other',quantityOnHand:'1',category:'Beauty'}];
 const api=new ApiClient('test'),mutate=vi.spyOn(api,'mutate').mockResolvedValue({saved:true}),user=userEvent.setup();
 render(<WorkspacePages page="inventory" data={data} api={api} writable onPage={vi.fn()} onReceipt={vi.fn()} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/>);
 expect(screen.getByText('$0.1500 / load')).toBeInTheDocument();await user.selectOptions(screen.getByLabelText('Sort inventory'),'CATEGORY');expect(screen.getAllByRole('row')[1]).toHaveTextContent('Other');
 await user.selectOptions(screen.getByLabelText('Filter by category'),'Household & Pet Essentials > Laundry Care');expect(screen.getAllByRole('row')).toHaveLength(2);
 await user.click(screen.getByRole('button',{name:'View / update'}));expect(screen.getByLabelText('Category')).toHaveValue('Household & Pet Essentials > Laundry Care');await user.clear(screen.getByLabelText('Category'));await user.type(screen.getByLabelText('Category'),'Custom laundry');await user.click(screen.getByRole('button',{name:'Save inventory'}));
 expect(mutate).toHaveBeenCalledWith('/inventory/stock','PUT',expect.objectContaining({action:'DETAILS',category:'Custom laundry',unitsPerPackage:40,unitName:'Loads'}),expect.any(String),'2');
});

it('collapses the inventory product list with an arrow and restores all products',async()=>{
 const data=workspace();data.inventory=[{inventoryId:'a',productId:'a',revision:'1',productName:'Laundry',quantityOnHand:'1'}];const user=userEvent.setup();
 render(<WorkspacePages page="inventory" data={data} writable onPage={vi.fn()} onReceipt={vi.fn()} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/>);
 const arrow=screen.getByRole('button',{name:'Collapse Inventory products'});expect(arrow).toHaveAttribute('aria-expanded','true');expect(arrow).toHaveTextContent('▾');await user.click(arrow);expect(screen.getByText('Laundry')).not.toBeVisible();
 await user.click(screen.getByRole('button',{name:'Expand Inventory products'}));expect(screen.getByText('Laundry')).toBeVisible();
});

it('keeps inventory details editable after a rejected field and retries with a new request key',async()=>{
 const data=workspace();data.inventory=[{inventoryId:'stock',productId:'product',revision:'2',productName:'Tide',quantityOnHand:'1',unitsPerPackage:'80',unitName:'Loads'}];
 const api=new ApiClient('test'),mutate=vi.spyOn(api,'mutate').mockRejectedValueOnce(new ApiError(400,'Unknown request field.')).mockResolvedValueOnce({saved:true}),user=userEvent.setup();
 render(<WorkspacePages page="inventory" data={data} api={api} writable onPage={vi.fn()} onReceipt={vi.fn()} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/>);
 await user.click(screen.getByRole('button',{name:'View / update'}));await user.type(screen.getByLabelText('Category'),'Laundry');await user.click(screen.getByRole('button',{name:'Save inventory'}));
 expect(screen.getAllByText(/Your entries are still here/).length).toBeGreaterThan(0);expect(screen.getByLabelText('Category')).toBeEnabled();expect(screen.getByLabelText('Category')).toHaveValue('Laundry');
 await user.click(screen.getByRole('button',{name:'Save inventory'}));expect(mutate).toHaveBeenCalledTimes(2);expect(mutate.mock.calls[0][3]).not.toBe(mutate.mock.calls[1][3]);expect(mutate.mock.calls[1][2]).toMatchObject({category:'Laundry',unitsPerPackage:80,unitName:'Loads'});
});

it('opens the exact original receipt directly from an inventory row and from purchase history',async()=>{
 const data=workspace();data.inventory=[{inventoryId:'a',productId:'a',revision:'1',productName:'Tide',quantityOnHand:'1',sources:[{receiptId:'receipt-original',receiptNumber:'R123',lineItemId:'line',revision:'1',quantity:'1',active:true,productName:'Tide',store:'Walgreens',date:'2026-11-06'}]}];
 const onReceipt=vi.fn(),user=userEvent.setup();render(<WorkspacePages page="inventory" data={data} writable onPage={vi.fn()} onReceipt={onReceipt} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/>);
 await user.click(screen.getByRole('button',{name:'View receipt R123 for Tide'}));expect(onReceipt).toHaveBeenLastCalledWith('receipt-original');
 await user.click(screen.getByRole('button',{name:'View / update'}));await user.click(screen.getByRole('button',{name:'View receipt · R123'}));expect(onReceipt).toHaveBeenLastCalledWith('receipt-original');
});
it('lists each linked receipt once for grouped inventory products',async()=>{
 const source={receiptId:'r1',receiptNumber:'R1',lineItemId:'line',revision:'1',quantity:'1',active:true,productName:'Tide',store:'Walgreens',date:'2026-11-06'};
 const data=workspace();data.inventory=[{inventoryId:'a',productId:'a',sku:'019560284600',revision:'1',productName:'Tide',quantityOnHand:'1',sources:[source,{...source,lineItemId:'another'}]},{inventoryId:'b',productId:'b',sku:'00019560284600',revision:'1',productName:'Tide',quantityOnHand:'1',sources:[{...source,receiptId:'r2',receiptNumber:'R2'}]}];
 const onReceipt=vi.fn(),user=userEvent.setup();render(<WorkspacePages page="inventory" data={data} writable onPage={vi.fn()} onReceipt={onReceipt} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/>);
 await user.click(screen.getByText('View receipts (2)'));expect(screen.getAllByRole('button',{name:/View receipt R1 for Tide/})).toHaveLength(1);await user.click(screen.getByRole('button',{name:'View receipt R2 for Tide'}));expect(onReceipt).toHaveBeenCalledWith('r2');
});

it('compares like soap products per fluid ounce and saves decimal package size separately from stock',async()=>{
 const data=workspace();data.inventory=[{inventoryId:'soap',productId:'soap',revision:'2',productName:'Liquid Soap',totalPurchased:'2',quantityOnHand:'2',netCost:'3.00',comparisonSize:'7.5',comparisonUnit:'FL_OZ'},{inventoryId:'bar',productId:'bar',revision:'1',productName:'Bar Soap',totalPurchased:'1',quantityOnHand:'1',netCost:'4.80',comparisonSize:'19.2',comparisonUnit:'OZ'}];
 const api=new ApiClient('test'),mutate=vi.spyOn(api,'mutate').mockResolvedValue({saved:true}),user=userEvent.setup();render(<WorkspacePages page="inventory" data={data} api={api} writable onPage={vi.fn()} onReceipt={vi.fn()} onEdit={vi.fn()} onAddReceipt={vi.fn()} onCheckout={vi.fn()}/>);
 expect(screen.getByText('$0.2000 / fl oz')).toBeInTheDocument();expect(screen.getByText('$0.2500 / oz')).toBeInTheDocument();
 await user.selectOptions(screen.getByLabelText('Compare by unit'),'fl oz');expect(screen.getAllByRole('row')).toHaveLength(2);expect(screen.queryByText('Bar Soap')).not.toBeInTheDocument();
 await user.click(screen.getByRole('button',{name:'View / update'}));expect(screen.getByLabelText('Compare price by')).toHaveValue('FL_OZ');await user.clear(screen.getByLabelText('Total ounces per purchased package'));await user.type(screen.getByLabelText('Total ounces per purchased package'),'12.5');await user.click(screen.getByRole('button',{name:'Save inventory'}));
 expect(mutate).toHaveBeenCalledWith('/inventory/soap','PUT',expect.objectContaining({action:'DETAILS',quantity:0,countUnit:'PACKAGE',comparisonSize:'12.5',comparisonUnit:'FL_OZ'}),expect.any(String),'2');
});
