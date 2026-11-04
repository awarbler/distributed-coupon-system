import {test,expect} from '@playwright/test'
import {workspace} from '../src/api/fixtures.test-support'
test.use({serviceWorkers:'block'})
test('receipt text and discount rows stay readable',async({page},info)=>{
 await page.route('**/api/**',r=>r.fulfill({json:[]}))
 const data=workspace();data.receiptDrafts=[{draftId:'draft',revision:'1',store:'Walgreens',receiptNumber:null,updatedAt:'2026-11-03T12:00:00Z'}]
 await page.route('**/api/workspace**',r=>r.fulfill({json:data}))
 await page.goto('/');await page.getByLabel('API access token').fill('synthetic');await page.getByRole('button',{name:'Open my workspace',exact:true}).click()
 await expect(page.getByRole('heading',{name:'Make every trip count.'})).toBeVisible()
 const menu=page.getByRole('button',{name:'Open navigation',exact:true});if(await menu.isVisible())await menu.click()
 await page.getByRole('link',{name:'Receipts',exact:true}).click()
 await expect(page.getByRole('heading',{name:'Saved receipt drafts',exact:true})).toHaveCount(1)
 const draftY=(await page.getByRole('heading',{name:'Saved receipt drafts',exact:true}).boundingBox())!.y
 expect(draftY).toBeGreaterThan((await page.locator('.receipt-period-panel').boundingBox())!.y+(await page.locator('.receipt-period-panel').boundingBox())!.height)
 await page.getByRole('button',{name:'Add receipt',exact:true}).click()
 const dialog=page.getByRole('dialog',{name:'Add receipt'})
 await expect(dialog.getByRole('group',{name:'Top form actions'}).getByRole('button')).toHaveCount(2)
 const receiptType=await dialog.getByLabel('Receipt Type',{exact:true}).evaluate(e=>({height:e.getBoundingClientRect().height,padding:getComputedStyle(e).paddingTop}))
 expect(receiptType.height).toBeGreaterThanOrEqual(48);expect(receiptType.padding).toBe('0px')
 await dialog.getByLabel('Product description 1').fill('Synthetic Crest toothpaste')
 await dialog.getByLabel('Regular price 1',{exact:true}).fill('6.29')
 await dialog.getByLabel('Store promotion 1',{exact:true}).fill('2.58')
 for(const field of [dialog.getByLabel('Regular price 1',{exact:true}),dialog.getByLabel('Product 1 discount 1 amount')]){
  const metrics=await field.evaluate(e=>({font:parseFloat(getComputedStyle(e).fontSize),height:e.getBoundingClientRect().height}));expect(metrics.font).toBeGreaterThanOrEqual(16);expect(metrics.height).toBeGreaterThanOrEqual(44)
 }
 if(info.project.name==='desktop'){
  const buttons=dialog.locator('.receipt-sale-controls .product-entry-actions button')
  expect(await buttons.count()).toBe(6)
  const tops=await buttons.evaluateAll(nodes=>nodes.map(node=>Math.round(node.getBoundingClientRect().top)))
  expect(new Set(tops).size).toBe(1)
  expect(Math.round((await dialog.boundingBox())!.width)).toBe(1088)
  const y=await dialog.locator('.item-discount-fields .form-grid > label').evaluateAll(labels=>labels.map(l=>Math.round(l.getBoundingClientRect().bottom)))
  expect(new Set(y).size).toBe(1)
 }
 expect(await dialog.evaluate(e=>e.scrollWidth<=e.clientWidth+1)).toBe(true)
 await dialog.getByLabel('Regular price 1',{exact:true}).scrollIntoViewIfNeeded()
 await page.screenshot({path:info.outputPath('receipt-readable.png')})
 await page.route('**/api/receipts/import-preview',r=>r.fulfill({json:{sources:[{name:'synthetic.png',rawText:'Synthetic receipt',scanned:true,store:'Walgreens',number:'TEST',day:'2026-11-03',time:'',tax:'0',reported:'2',footer:{},wcEarned:'',warnings:[],items:[{id:'line',description:'Synthetic Crest',sku:'00123456789',regular:'2',sale:'',quantity:'1'}],deductions:[{id:'coupon',label:'MFG ECPN -4.00',amount:'4',kind:'MANUFACTURER'}],payments:[]}],duplicates:[]}}))
 page.once('dialog',prompt=>prompt.accept())
 await dialog.getByLabel('Import Receipt Files').setInputFiles({name:'synthetic.png',mimeType:'image/png',buffer:Buffer.from('synthetic')})
 const classification=dialog.getByLabel('Classify Imported Deduction coupon')
 await expect(classification).toHaveValue('MANUFACTURER')
 const control=await classification.evaluate(e=>({height:e.getBoundingClientRect().height,padding:getComputedStyle(e).paddingTop,font:parseFloat(getComputedStyle(e).fontSize)}))
 expect(control.height).toBeGreaterThanOrEqual(48);expect(control.padding).toBe('0px');expect(control.font).toBeGreaterThanOrEqual(16)
 await classification.scrollIntoViewIfNeeded()
 await page.screenshot({path:info.outputPath('receipt-import-readable.png')})
 await dialog.getByLabel('Earned This Visit',{exact:true}).fill('1.90')
 await dialog.getByLabel('Reward program 1',{exact:true}).selectOption('REGISTER_REWARD')
 await dialog.getByLabel('Reward program 1',{exact:true}).press('Enter')
 await expect(dialog.getByLabel('Reward amount 1 (USD)',{exact:true})).toBeFocused()
 if(info.project.name!=='mobile'){
  const bottom=async(label:string)=>Math.round((await dialog.getByLabel(label,{exact:true}).boundingBox())!.y)
  expect(await bottom('Reward program 1')).toBe(await bottom('Reward amount 1 (USD)'))
  expect(await bottom('Reward status 1')).toBe(await bottom('Offer description 1 (optional)'))
  expect(await bottom('Reward status 1')).toBe(await bottom('Reward expiration date 1 (optional)'))
 }
 await dialog.getByLabel('Reward status 1',{exact:true}).scrollIntoViewIfNeeded()
 await page.screenshot({path:info.outputPath('receipt-rewards-layout.png')})


})
