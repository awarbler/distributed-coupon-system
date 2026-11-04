import {test,expect} from '@playwright/test'
import {workspace,planningRow} from '../src/api/fixtures.test-support'
import type {PlanScenario,ScenarioEstimate} from '../src/types/platform'
test.use({serviceWorkers:'block'})
test('written deal guide fits a phone and narrow desktop',async({page},info)=>{
 if(info.project.name!=='mobile')await page.setViewportSize({width:980,height:1000})
 const data=workspace(),row=planningRow();data.asOf='2026-11-04';
 const scenario:PlanScenario={items:[{id:'neutrogena',name:'Neutrogena wipes',sku:null,regular:'7.79',sale:'5.99',quantity:'2'},{id:'lysol',name:'Lysol wipes',sku:null,regular:'3.29',sale:'3.29',quantity:'2'}],coupons:[{id:'coupon',description:'Neutrogena digital coupon',amount:'4',requiredQuantity:'2',lineIds:['neutrogena'],savedCouponId:null}],promotions:[{id:'rr',description:'Neutrogena RR',rewardType:'REGISTER_REWARD',rewardAmount:'7',requiredQuantity:'2',requiredSpend:'0',spendBasis:'BEFORE_COUPONS',allowsWalgreensCash:true,lineIds:['neutrogena']}],tax:'1.04',walgreensCashUsed:'0',registerRewardsUsed:'0',giftCardUsed:'0',manualOop:null,paymentMethod:'CASH',fundingNotes:'',payments:[{id:'palmolive',type:'REGISTER_REWARD',amount:'2',notes:'Palmolive earned in September · expires Nov 5'},{id:'wc',type:'WALGREENS_CASH',amount:'10',notes:''}]}
 const report:ScenarioEstimate={items:[{id:'neutrogena',name:'Neutrogena wipes',totalPrice:'11.98',couponsApplied:'4',itemOopBeforeTaxAndFunding:'7.98',possibleRewards:'7'},{id:'lysol',name:'Lysol wipes',totalPrice:'6.58',couponsApplied:'0',itemOopBeforeTaxAndFunding:'6.58',possibleRewards:'5'}],coupons:[{id:'coupon',description:'Neutrogena digital coupon',quantityMet:true,applied:true,amount:'4',reason:'Quantity met'}],promotions:[{id:'rr',description:'Neutrogena RR',quantityMet:true,spendMet:true,walgreensCashAllowed:true,eligible:true,qualifyingSpend:'11.98',potentialReward:'7'}],regularTotal:'22.16',saleTotal:'18.56',couponTotal:'4',merchandiseAfterCoupons:'14.56',tax:'1.04',registerRewardsUsed:'2',walgreensCashUsed:'10',giftCardUsed:'0',calculatedOop:'3.60',selectedOop:'3.60',manualDifference:'0',overFunding:'0',potentialRewards:'12',afterPotentialRewards:'-8.40'}
 row.plan={...row.plan,name:'Neutrogena + Lysol',retailer:'Walgreens',shoppingDate:'2026-11-04',scenario,organization:{stage:'IDEA',alternativeOf:null,checkoutOrder:null,rewardSources:[]}};row.scenarioEstimate=report;data.plans=[row]
 await page.route('**/api/**',r=>r.fulfill({json:[]}));await page.route('**/api/workspace**',r=>r.fulfill({json:data}))
 await page.goto('/');await page.getByLabel('API access token').fill('synthetic');await page.getByRole('button',{name:'Open my workspace',exact:true}).click()
 await expect(page.getByRole('heading',{name:'Make every trip count.'})).toBeVisible()
 const menu=page.getByRole('button',{name:'Open navigation',exact:true});if(await menu.isVisible())await menu.click()
 await page.getByRole('link',{name:/Shopping & Deals/}).click();await page.locator('.weekly-idea').getByRole('button',{name:'View',exact:true}).click()
 const dialog=page.getByRole('dialog',{name:'Neutrogena + Lysol'}),guide=dialog.getByRole('region',{name:'Shopping checkout guide'})
 await expect(guide.getByText('Neutrogena wipes — buy 2')).toBeVisible();await expect(guide.getByText(/Palmolive earned in September/)).toBeVisible()
 expect(await dialog.evaluate(e=>e.scrollWidth<=e.clientWidth+1)).toBe(true)
 await guide.getByText('Pay',{exact:false}).last().scrollIntoViewIfNeeded();await page.screenshot({path:info.outputPath('written-deal-guide.png')})
 await dialog.getByRole('button',{name:'Close dialog',exact:true}).click()
 await page.getByRole('button',{name:'Add screenshot',exact:true}).click()
 const upload=page.getByRole('dialog',{name:'Add screenshot',exact:true})
 await upload.getByRole('group',{name:'Attachment drop area'}).evaluate(node=>{
  const transfer=new DataTransfer();transfer.items.add(new File([new Uint8Array([1,2,3])],'dropped-photo.png',{type:'image/png'}))
  node.dispatchEvent(new DragEvent('dragenter',{bubbles:true,cancelable:true,dataTransfer:transfer}))
  node.dispatchEvent(new DragEvent('dragover',{bubbles:true,cancelable:true,dataTransfer:transfer}))
  node.dispatchEvent(new DragEvent('drop',{bubbles:true,cancelable:true,dataTransfer:transfer}))
 })
 await expect(upload.getByText('dropped-photo.png',{exact:false})).toBeVisible()
 expect(await upload.locator('input[type=file]').evaluate((input:HTMLInputElement)=>Array.from(input.files??[]).map(file=>file.name))).toEqual(['dropped-photo.png'])
 await expect(upload.getByRole('button',{name:'Add to this plan',exact:true})).toBeEnabled()

})
