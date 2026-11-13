import { test, expect } from '@playwright/test'
import { workspace, receiptRow, planningRow } from '../src/api/fixtures.test-support'

test('responsive navigation, installation assets, and private-response caching', async ({ page, context }, info) => {
  let calls = 0
  page.on('request',request=>{if(new URL(request.url()).pathname==='/api/workspace')calls++})
  await page.goto('/')
  await page.getByLabel('API access token').fill(process.env.COUPON_BROWSER_TEST_TOKEN!)
  await page.getByRole('button', { name: 'Open my workspace', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Make every trip count.' })).toBeVisible()
  expect(await page.evaluate(() => innerWidth)).toBe(page.viewportSize()!.width)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: info.outputPath('dashboard.png'), fullPage: true })
  const menu = page.getByRole('button', { name: 'Open navigation', exact: true }); if (await menu.isVisible()) await menu.click()
  await page.getByRole('link', { name: 'Inventory', exact: true }).click()
  await expect(page.getByRole('button', { name: /Import \/ sync receipts/i })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.getByRole('button', { name: 'Refresh workspace', exact: true }).click()
  await expect.poll(() => calls).toBeGreaterThanOrEqual(2)
  const manifest = await (await page.request.get('/manifest.webmanifest')).json()
  expect(manifest.display).toBe('standalone'); expect(manifest.icons).toHaveLength(2)
  await page.evaluate(async () => { await navigator.serviceWorker.ready; if (!navigator.serviceWorker.controller) await new Promise<void>(resolve => navigator.serviceWorker.addEventListener('controllerchange', () => resolve(), { once: true })) })
  const cached = await page.evaluate(async () => {
    const keys = await caches.keys(); const urls: string[] = []
    for (const key of keys) for (const request of await (await caches.open(key)).keys()) urls.push(request.url)
    return urls
  })
  expect(cached.some(url => url.includes('/assets/'))).toBe(true)
  expect(cached.some(url => url.includes('/api/'))).toBe(false)
  expect(await page.evaluate(() => localStorage.length + sessionStorage.length)).toBe(0)
  await context.setOffline(true); await page.reload()
  await expect(page.getByText(/Reconnect to load your records/)).toBeVisible()
  await expect(page.getByLabel('API access token')).toHaveValue('')
  await context.setOffline(false)
})

test('shopping-plan and item editing through the real disposable Java API', async ({ page }, info) => {
  test.skip(!process.env.COUPON_BROWSER_TEST_TOKEN, 'Requires an explicitly configured disposable Java API; never use a persistent workspace.')
  const planName = `Browser verification ${info.project.name} ${Date.now()}`
  await page.goto('/')
  await page.getByLabel('API access token').fill(process.env.COUPON_BROWSER_TEST_TOKEN!)
  await page.getByRole('button', { name: 'Open my workspace', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Make every trip count.' })).toBeVisible()
  expect(await page.evaluate(() => innerWidth)).toBe(page.viewportSize()!.width)
  await page.getByRole('button', { name: 'New shopping plan', exact: true }).click()
  await page.getByLabel('Plan name').fill(planName)
  await page.getByRole('dialog').getByRole('button', { name: 'Save', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  const menu = page.getByRole('button', { name: 'Open navigation', exact: true }); if (await menu.isVisible()) await menu.click()
  await page.getByRole('link', { name: /^Shopping & Deals(?: \d+)?$/ }).click()
  const panel = page.locator('.shopping-plan-entry').filter({ has: page.getByRole('heading', { name: planName, exact: true }) })
  await expect(panel).toBeVisible()
  await panel.getByRole('button', { name: 'View', exact: true }).click()
  await panel.getByRole('button', { name: 'Add item', exact: true }).click()
  await page.getByLabel('Product description').fill('Disposable test soap')
  await page.getByRole('textbox', { name: /^Quantity/ }).fill('3')
  await page.getByLabel('Regular price').fill('1.25')
  await page.getByRole('dialog').getByRole('button', { name: 'Save', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(panel.locator('.panel-bottom').getByText('$3.75', { exact: true })).toBeVisible()
  await panel.getByRole('button', { name: 'Edit', exact: true }).click()
  await page.getByRole('textbox', { name: /^Quantity/ }).fill('2')
  await page.getByRole('dialog').getByRole('button', { name: 'Save', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(panel.locator('.panel-bottom').getByText('$2.50', { exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: info.outputPath('shopping-plan.png'), fullPage: true })
})

test('reward receipt confirmation and rejection through the disposable Java API', async ({ page }, info) => {
  test.skip(!process.env.COUPON_BROWSER_REWARD_RECEIPT || !process.env.COUPON_BROWSER_TEST_TOKEN, 'Requires generated reward fixtures in an explicitly disposable database.')
  await page.goto('/')
  await page.getByLabel('API access token').fill(process.env.COUPON_BROWSER_TEST_TOKEN!)
  await page.getByRole('button', { name: 'Open my workspace', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Make every trip count.' })).toBeVisible()
  expect(await page.evaluate(() => innerWidth)).toBe(page.viewportSize()!.width)
  const menu = page.getByRole('button', { name: 'Open navigation', exact: true }); if (await menu.isVisible()) await menu.click()
  await page.getByRole('link', { name: 'Receipts', exact: true }).click()
  await page.getByRole('button', { name: `Open receipt ${process.env.COUPON_BROWSER_REWARD_RECEIPT}` }).click()
  await page.getByRole('button', { name: 'Rewards', exact: true }).click()
  for (const [offer, target] of [['RR test offer', 'PAID', '5', 'USD'], ['Fetch test offer', 'CREDITED', '25', 'points']]) {
    const section = page.getByRole('dialog').locator('section').filter({ has: page.getByText(offer, { exact: true }) })
    await expect(section.getByRole('button', { name: 'Edit Amount / Status', exact: true })).toBeEnabled()
    await section.getByRole('button', { name: 'Edit Amount / Status', exact: true }).click()
    if (await section.getByLabel('Reward status', { exact: true }).inputValue() !== 'EXPECTED') {
      await section.getByLabel('Reward status', { exact: true }).selectOption('EXPECTED')
      await section.getByRole('button', { name: 'Save reward status', exact: true }).click()
      await expect(section.getByRole('form')).toHaveCount(0)
      await expect(section.getByRole('button', { name: 'Edit Amount / Status', exact: true })).toBeEnabled()
      await section.getByRole('button', { name: 'Edit Amount / Status', exact: true }).click()
    }
    await section.getByLabel('Reward status', { exact: true }).selectOption(target)
    await section.getByRole('button', { name: 'Save reward status', exact: true }).click()
    await expect(section.getByRole('form')).toHaveCount(0)
    await expect(section.getByText(target === 'PAID' ? /Printed at register/ : /Added to program account/)).toBeVisible()
  }
  const response = await page.request.get('/api/workspace', { headers: { Authorization: `Bearer ${process.env.COUPON_BROWSER_TEST_TOKEN}` } })
  expect(response.ok()).toBe(true)
  const data = await response.json()
  const rr = data.movements.filter((m: { source: string; provider: string }) => m.source.includes(`Receipt ${process.env.COUPON_BROWSER_REWARD_RECEIPT}`) && m.provider === 'Register Rewards')
  expect(rr).toHaveLength(1); expect(String(rr[0].usd)).toBe('5')
  const wc = data.movements.filter((m: { source: string; provider: string }) => m.source.includes(`Receipt ${process.env.COUPON_BROWSER_REWARD_RECEIPT}`) && m.provider === 'Walgreens Cash')
  expect(wc).toHaveLength(0)
  const section = page.getByRole('dialog').locator('section').filter({ has: page.getByText('RR test offer', { exact: true }) })
  await expect(section.getByRole('button', { name: 'Edit Amount / Status', exact: true })).toBeEnabled()
  await section.getByRole('button', { name: 'Edit Amount / Status', exact: true }).click()
  await section.getByLabel('Reward status', { exact: true }).selectOption('REJECTED')
  await section.getByLabel('Rejection reason (optional)').fill(`Not issued — ${info.project.name}`)
  await section.getByRole('button', { name: 'Save reward status', exact: true }).click()
  await expect(section.getByText(/Excluded from earned value/)).toBeVisible()
  await expect(section.getByText('$5.00',{exact:true})).toBeVisible()
  const savedRewardReceipt=data.receipts.find((row:{metadata:{receiptNumber:string}})=>row.metadata.receiptNumber===process.env.COUPON_BROWSER_REWARD_RECEIPT)
  const rejected=await (await page.request.get(`/api/receipts/${savedRewardReceipt.transactionId}`,{headers:{Authorization:`Bearer ${process.env.COUPON_BROWSER_TEST_TOKEN}`}})).json()
  const retained=rejected.contents.observations.earnings.find((earning:{offerReference:string})=>earning.offerReference==='RR test offer')
  expect(retained.status).toBe('REJECTED');expect(retained.expected.amount).toBe(5);expect(retained.confirmed.amount).toBe(5)
  await expect(section.getByText(new RegExp(`Rejection reason: Not issued — ${info.project.name}`))).toBeVisible()
})

test('create and review a complete receipt through the disposable API', async ({ page }, info) => {
  test.skip(!process.env.COUPON_BROWSER_TEST_TOKEN, 'Requires an explicitly configured disposable API; never run write tests against personal records.')
  const receiptNumber = `web-create-${info.project.name}-${Date.now()}`
  await page.goto('/')
  await page.getByLabel('API access token').fill(process.env.COUPON_BROWSER_TEST_TOKEN!)
  await page.getByRole('button', { name: 'Open my workspace', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Make every trip count.' })).toBeVisible()
  const menu = page.getByRole('button', { name: 'Open navigation', exact: true }); if (await menu.isVisible()) await menu.click()
  await page.getByRole('link', { name: 'Receipts', exact: true }).click()
  await page.getByRole('button', { name: 'Add receipt', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: 'Add receipt' })
  await dialog.getByLabel('Store', { exact: true }).fill('Walgreens')
  await dialog.getByLabel('Printed receipt number (optional)').fill(receiptNumber)
  await dialog.getByLabel('Purchase date', { exact: true }).fill('2026-10-19')
  await dialog.getByLabel('Product description 1').fill('Soap')
  await dialog.getByLabel('Quantity 1', { exact: true }).fill('2')
  await dialog.getByLabel('Regular price 1', { exact: true }).fill('10.00')
  await dialog.getByRole('button', { name: 'Add discount to product 1', exact: true }).click()
  await dialog.getByLabel('Product 1 discount 1 description').fill('Sale promotion')
  await dialog.getByLabel('Product 1 discount 1 amount').fill('4.00')
  await dialog.getByRole('button', { name: 'Add shared or receipt-wide discount', exact: true }).click()
  await dialog.getByLabel('Discount label 1').fill('Manufacturer coupon')
  await dialog.getByLabel('Discount amount 1', { exact: true }).fill('2.00')
  await dialog.getByLabel('Sales tax', { exact: true }).fill('0.50')
  await dialog.getByLabel('Reported total due at register', { exact: true }).fill('8.50')
  await dialog.getByRole('button',{name:'Add RR used',exact:true}).click()
  await dialog.getByLabel('RR used amount 1',{exact:true}).fill('1.00')
  await dialog.getByRole('button', { name: 'Add payment', exact: true }).click()
  await dialog.getByLabel('Payment amount 1', { exact: true }).fill('8.50')
  await dialog.getByRole('button', { name: 'Add payment', exact: true }).click()
  await dialog.getByLabel('Payment type 2', { exact: true }).selectOption('WALGREENS_CASH')
  await dialog.getByLabel('Payment amount 2', { exact: true }).fill('5.00')
  await dialog.getByRole('button', { name: 'Add rebate or reward', exact: true }).click()
  await dialog.getByLabel('Reward program 1', { exact: true }).selectOption('REGISTER_REWARD')
  await dialog.getByLabel('Reward amount 1 (USD)', { exact: true }).fill('5.00')
  await dialog.getByLabel('Reward status 1', { exact: true }).selectOption('PAID')
  await dialog.getByRole('button', { name: 'Add rebate or reward', exact: true }).click()
  await dialog.getByLabel('Reward program 2', { exact: true }).selectOption('WALGREENS_CASH')
  await dialog.getByLabel('Reward amount 2 (USD)', { exact: true }).fill('10.00')
  await dialog.getByLabel('Reward status 2',{exact:true}).selectOption('EXPECTED')
  const image = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl6hWQAAAAASUVORK5CYII=', 'base64')
  await dialog.getByLabel('Receipt files',{exact:true}).setInputFiles({ name: 'synthetic-receipt.png', mimeType: 'image/png', buffer: image })
  await dialog.getByRole('button', { name: 'Review calculated totals', exact: true }).click()
  const review = page.getByRole('dialog', { name: 'Review new receipt' })
  await expect(review.getByText('Regular merchandise',{exact:true}).locator('..')).toContainText('$20.00')
  await expect(review.getByText('Actual cash/card paid',{exact:true}).locator('..')).toContainText('$8.50')
  await expect(review.getByText('Walgreens Cash used',{exact:true}).locator('..')).toContainText('$5.00')
  await expect(review.getByText('Known confirmed dollar-benefit subtotal',{exact:true}).locator('..')).toContainText('$5.03')
  await review.getByRole('button', { name: 'Save receipt', exact: true }).click()
  const saved = page.getByRole('dialog', { name: `Receipt ${receiptNumber}` })
  await expect(saved).toBeVisible()
  await expect(saved.getByText('1. Soap', { exact: true })).toBeVisible()
  await saved.getByRole('button', { name: 'Rewards', exact: true }).click()
  await expect(saved.getByText(/Printed at register/)).toBeVisible()
  await expect(saved.getByText(/^Expected ·/)).toBeVisible()
  await saved.getByRole('button', { name: 'Evidence', exact: true }).click()
  await expect(saved.getByRole('button', { name: /Attachment 1/ })).toBeVisible()
  await saved.getByRole('button', { name: 'History', exact: true }).click()
  await expect(saved.locator('ol.history li')).toHaveCount(1)
  await saved.getByRole('button', { name: 'Close dialog', exact: true }).click()
  // Receipt date/time determines ordering; new records need not be on the first page.
  await page.getByLabel('Search store, receipt number, or date').fill(receiptNumber)
  await expect(page.getByRole('button', { name: `Open receipt ${receiptNumber}`, exact: true })).toBeVisible()
  const response = await page.request.get('/api/workspace', { headers: { Authorization: `Bearer ${process.env.COUPON_BROWSER_TEST_TOKEN}` } })
  expect(response.ok()).toBe(true)
  const data = await response.json(); const row = data.receipts.find((r: { metadata: { receiptNumber: string } }) => r.metadata.receiptNumber === receiptNumber)
  expect(row.totals.calculatedDue).toBe(8.5); expect(row.totals.savings.newlyEarned).toBe(5.025)
  const moves = data.movements.filter((m: { source: string }) => m.source.includes(receiptNumber))
  expect(moves.filter((m: { provider: string; usd: number }) => m.provider === 'Walgreens Cash').map((m: { usd: number }) => m.usd)).toEqual([-5])
  expect(moves.filter((m: { provider: string }) => m.provider === 'Register Rewards')).toHaveLength(2) // one earning and one separate use
})

test('inline deals and physical coupons preserve a planned item', async ({ page }, info) => {
  test.skip(!process.env.COUPON_BROWSER_TEST_TOKEN, 'Disposable API required.')
  const headers={Authorization:`Bearer ${process.env.COUPON_BROWSER_TEST_TOKEN}`,'Idempotency-Key':crypto.randomUUID()}
  const planId=crypto.randomUUID(),name=`Inline trip ${info.project.name} ${Date.now()}`
  expect((await page.request.post('/api/shopping-plans',{headers,data:{planId,name,retailer:'Synthetic store',shoppingDate:'2026-10-19'}})).status()).toBe(201)
  await page.goto('/');await page.getByLabel('API access token').fill(process.env.COUPON_BROWSER_TEST_TOKEN!);await page.getByRole('button', { name: 'Open my workspace', exact: true }).click();await expect(page.getByRole('heading',{name:'Make every trip count.'})).toBeVisible()
  const menu=page.getByRole('button', { name: 'Open navigation', exact: true });if(await menu.isVisible())await menu.click();await page.getByRole('link',{name:/^Shopping & Deals(?: \d+)?$/}).click()
  const panel=page.locator('.shopping-plan-entry').filter({has:page.getByRole('heading',{name,exact:true})});await panel.getByRole('button',{name:'View',exact:true}).click();await panel.getByRole('button',{name:'Add item',exact:true}).click()
  const dialog=page.getByRole('dialog');await dialog.getByLabel(/^Product description/).fill('Shampoo');await dialog.getByLabel(/^Regular price/).fill('3.00');await dialog.getByLabel(/^Quantity/).fill('2')
  await dialog.getByRole('button',{name:'Add a deal',exact:true}).click();await dialog.getByLabel('Store',{exact:true}).fill('Synthetic store');await dialog.getByLabel('Promotion name').fill(`Shampoo bundle ${info.project.name} ${Date.now()}`);await dialog.getByLabel('Qualifying product description').fill('Shampoo');await dialog.getByLabel('Recorded offer terms').fill('Two for five dollars');await dialog.getByLabel('Promotion type').selectOption('MULTIBUY');await dialog.getByLabel('Price for the full bundle').fill('5.00');await dialog.getByLabel('Units per bundle').fill('2');await dialog.getByLabel('Start date',{exact:true}).fill('2026-10-01');await dialog.getByLabel('Expiration date',{exact:true}).fill('2026-10-31');await dialog.getByRole('button', { name: 'Save and link deal', exact: true }).click()
  await expect(dialog.getByLabel(/^Product description/)).toHaveValue('Shampoo');await dialog.getByRole('button',{name:'Add coupon',exact:true}).click();await dialog.getByLabel('Coupon description').fill('One dollar on shampoo');await dialog.getByLabel('Issuing manufacturer, store or provider').fill('Synthetic maker');await dialog.getByLabel('Dollar amount',{exact:true}).fill('1.00');await dialog.getByLabel('Number of available copies').fill('2');await dialog.getByLabel('Saved / clipped date').fill('2026-10-01');await dialog.getByLabel('Expiration date',{exact:true}).fill('2026-10-31');await dialog.getByRole('button',{name:'Save coupon',exact:true}).click()
  await expect(dialog.getByLabel(/^Product description/)).toHaveValue('Shampoo');await expect(dialog.getByLabel(/^Quantity/)).toHaveValue('2');await dialog.getByRole('button',{name:'Save',exact:true}).click();await expect(dialog).toHaveCount(0)
  await expect(panel.getByText('$4.00',{exact:true})).toHaveCount(2)
  const data=await (await page.request.get('/api/workspace',{headers})).json();const row=data.plans.find((p:{plan:{planId:string}})=>p.plan.planId===planId);expect(row.pricing.saleTotal).toBe(5);expect(row.pricing.registerCost).toBe(4);expect(row.items[0].clippedCouponIds).toHaveLength(1);expect(data.coupons.filter((c:{description:string})=>c.description==='One dollar on shampoo').every((c:{status:string})=>c.status==='CLIPPED')).toBe(true)
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
})

test('post-save promotion and rebate use selected units without changing checkout', async ({page},info)=>{
  test.skip(!process.env.COUPON_BROWSER_TEST_TOKEN,'Disposable API required.')
  const headers={Authorization:`Bearer ${process.env.COUPON_BROWSER_TEST_TOKEN}`,'Idempotency-Key':crypto.randomUUID()}
  const id=crypto.randomUUID(),a=crypto.randomUUID(),b=crypto.randomUUID(),number=`Associations ${info.project.name} ${Date.now()}`
  const draft={transactionId:id,retailer:`Synthetic store ${id}`,receiptNumber:number,purchaseDate:'2026-10-19',printedTime:'12:34',purchaseChannel:'IN_STORE',tax:'0',reportedTotal:'30',items:[{lineItemId:a,description:'Shampoo',sku:'same-product',regularUnitPrice:'10',unitPrice:'10',quantity:2},{lineItemId:b,description:'Shampoo',sku:'same-product',regularUnitPrice:'10',unitPrice:'10',quantity:1}],payments:[{paymentId:crypto.randomUUID(),type:'CARD',amount:'30'}]}
  const preview=await page.request.post('/api/receipts/preview',{headers,data:draft});expect(preview.ok()).toBe(true);const review=await preview.json();expect((await page.request.post('/api/receipts',{headers,data:{...draft,reviewToken:review.reviewToken}})).status()).toBe(201)
  await page.goto('/');await page.getByLabel('API access token').fill(process.env.COUPON_BROWSER_TEST_TOKEN!);await page.getByRole('button', { name: 'Open my workspace', exact: true }).click();await expect(page.getByRole('heading',{name:'Make every trip count.'})).toBeVisible()
  const menu=page.getByRole('button', { name: 'Open navigation', exact: true });if(await menu.isVisible())await menu.click();await page.getByRole('link',{name:'Receipts',exact:true}).click();await page.getByLabel('Search store, receipt number, or date').fill(number);await page.getByRole('button',{name:`Open receipt ${number}`,exact:true}).click();const dialog=page.getByRole('dialog');await expect(dialog.getByText('Shampoo: 3 unit(s), 2 receipt line(s)',{exact:true})).toBeVisible();await dialog.getByRole('button',{name:'Rewards',exact:true}).click()
  await dialog.getByRole('button',{name:'Add rebate or reward',exact:true}).click();await dialog.getByLabel('Reward program 1').selectOption('WALGREENS_CASH');await dialog.getByLabel('Reward amount 1 (USD)').fill('5');await dialog.getByLabel('Reward status 1',{exact:true}).selectOption('EXPECTED');await dialog.getByText('Optional reward details — products, promotion, valuation and evidence',{exact:true}).click();await dialog.getByLabel('Reward products 1: line 1',{exact:true}).check();await dialog.getByLabel('Reward products 1: line 2',{exact:true}).check();await dialog.getByRole('combobox',{name:'Promotion requirement',exact:true}).selectOption('SPEND');await dialog.getByLabel('Required qualifying spend',{exact:true}).fill('20');await dialog.getByLabel('Offer description 1 (optional)').fill('Spend 20 earn 5');await dialog.getByRole('button',{name:'Review rebate or reward',exact:true}).click();await dialog.getByText('Optional qualification review',{exact:true}).click();await expect(dialog.getByText(/Qualification: MET/)).toBeVisible();await dialog.getByRole('button',{name:'Save rebate or reward',exact:true}).click();await expect(dialog.getByText('Spend 20 earn 5',{exact:true})).toBeVisible();await expect(dialog.getByRole('button',{name:'Add rebate or reward',exact:true})).toBeEnabled()
  await dialog.getByRole('button',{name:'Add rebate or reward',exact:true}).click();await dialog.getByLabel('Reward program 1',{exact:true}).selectOption('P&G Good Everyday');await dialog.getByLabel('Reward amount 1 (points)').fill('100');await dialog.getByLabel('Reward status 1',{exact:true}).selectOption('EXPECTED');await dialog.getByText('Optional reward details — products, promotion, valuation and evidence',{exact:true}).click();await dialog.getByLabel('Reward products 1: line 1',{exact:true}).check();await dialog.getByLabel('Reward products 1: line 2',{exact:true}).check();await dialog.getByText('Submission and payout destination (optional)',{exact:true}).click();await dialog.getByRole('combobox',{name:'Payout destination',exact:true}).selectOption('PAYPAL');await dialog.getByRole('button',{name:'Review rebate or reward',exact:true}).click();await dialog.getByRole('button',{name:'Save rebate or reward',exact:true}).click();await expect(dialog.getByText('P&G Good Everyday',{exact:true})).toBeVisible();await dialog.getByRole('button',{name:'History',exact:true}).click();await expect(dialog.locator('ol.history li')).toHaveCount(3)
  const saved=await (await page.request.get(`/api/receipts/${id}`,{headers})).json();expect(saved.contents.receiptTotals.finalTotal).toBe(30);expect(saved.contents.observations.earnings).toHaveLength(3);expect(saved.contents.observations.earnings.find((e:{provider:string})=>e.provider==='P&G Good Everyday').details.payoutDestination).toBe('PAYPAL');expect(saved.contents.observations.earnings.filter((e:{provider:string})=>e.provider!=='Fetch').every((e:{status:string})=>e.status==='EXPECTED')).toBe(true)
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
})


test.describe('receipt fixture workflows', () => {
  test.use({ serviceWorkers: 'block' })

test('receipt search, five-row pages and selected-period savings', async ({ page }, info) => {
  const data = workspace()
  data.receipts = Array.from({ length: 7 }, (_, i) => receiptRow(String(i), '2026-10-19', `9:0${i} AM`))
  data.receiptPeriods = { empty: data.summaries.all, 'month:2026-10': { ...data.summaries.all, totalSaved: '349.72', recordedCashCardPaid: '35.96', newlyEarned: '18.74' } }
  await page.route('**/api/workspace**', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify(data) }))
  await page.goto('/')
  await page.getByLabel('API access token').fill('synthetic-browser-token')
  await page.getByRole('button', { name: 'Open my workspace', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Make every trip count.' })).toBeVisible()
  const menu = page.getByRole('button', { name: 'Open navigation', exact: true })
  if (await menu.isVisible()) await menu.click()
  await page.getByRole('link', { name: 'Receipts', exact: true }).click()
  const currentMonth=page.getByRole('button',{name:'Month',exact:true})
  await expect(currentMonth).toHaveAttribute('aria-pressed','true')
  await currentMonth.click()
  const periodStyle=await currentMonth.evaluate(node=>({border:getComputedStyle(node).borderTopWidth,outline:getComputedStyle(node).outlineStyle}))
  expect(periodStyle.border).toBe('0px');expect(periodStyle.outline).toBe('none')
  await page.getByRole('button',{name:'All Time',exact:true}).click()
  await expect(page.locator('.receipt-table').getByRole('row')).toHaveCount(6)
  await page.getByText('More filters — Show / Hide',{exact:true}).click()
  const controls = await Promise.all(['Purchase day', 'Month', 'Start date'].map(label => page.getByLabel(label, { exact: true }).boundingBox()))
  for (const box of controls) expect(box!.height).toBe(44)
  if (page.viewportSize()!.width >= 1200) {
    expect(controls[0]!.y).toBe(controls[1]!.y)
    expect(controls[1]!.y).toBe(controls[2]!.y)
  } else {
    expect(controls[1]!.y).toBeGreaterThan(controls[0]!.y + controls[0]!.height)
    expect(controls[2]!.y).toBeGreaterThan(controls[1]!.y + controls[1]!.height)
  }

  await expect(page.getByRole('columnheader', { name: 'Total saved', exact: true })).toBeAttached()
  const money = await page.locator('.receipt-table tbody tr').first().locator('.receipt-value').evaluateAll(nodes => nodes.map(node => node.getBoundingClientRect().top))
  expect(new Set(money).size).toBe(1)
  await page.screenshot({ path: `/private/tmp/coupon-receipt-alignment-${info.project.name}.png`, fullPage: true })

  await page.getByRole('button', { name: 'Next', exact: true }).click()
  await expect(page.getByText('Showing 6–7 of 7')).toBeVisible()
  await page.getByLabel('Search store, receipt number, or date').fill('october')
  await expect(page.getByText('Showing 1–5 of 7')).toBeVisible()
  await page.getByLabel('Search store, receipt number, or date').fill('00016')
  await expect(page.getByText('Showing 1–1 of 1')).toBeVisible()
  await page.getByLabel('Month', { exact: true }).selectOption('2026-10')
  await expect(page.getByLabel('Week of month')).toHaveCount(0)
  await expect(page.getByRole('heading', { name: 'Period totals — October 2026' })).toBeVisible()
  expect(await page.locator('.receipt-period-panel .panel-head').evaluate(node => getComputedStyle(node).textAlign)).toBe('center')
  expect(await page.locator('.receipt-summary-body').evaluate(node => getComputedStyle(node).textAlign)).toBe('center')
  await expect(page.getByText('$349.72', { exact: true })).toBeVisible()
  await page.getByLabel('Search store, receipt number, or date').fill('missing')
  await expect(page.getByRole('heading', { name: 'No matching receipts', exact:true })).toBeVisible()
  await expect(page.getByText('$349.72', { exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

test('saved receipts can be edited, deleted and restored with review', async ({ page }, info) => {
  const data = workspace(), row = receiptRow('11111111-1111-1111-1111-111111111111')
  data.receipts = [row]
  const receipt = { editDiscounts: [], transactionId: row.transactionId, revision: '1', status: 'ACTIVE', metadata: row.metadata!, evidenceIds: [], contents: {
    purchaseItems: [{ lineItemId: '22222222-2222-2222-2222-222222222222', productName: 'Soap', productCode: null, regularUnitPrice: '10', saleUnitPrice: '5.96', quantity: '1' }],
    appliedCoupons: [], payments: [{ paymentId: '33333333-3333-3333-3333-333333333333', paymentType: 'CARD', amount: '5.96', notes: null }],
    observations: { purchaseDate: '2026-10-19', printedTime: '17:09', earnings: [] }, receiptTotals: { taxAmount: '0', finalTotal: '5.96' },
  } }
  let edits = 0, deletes = 0, restores = 0
  await page.route('**/api/**', async route => {
    const request = route.request(), path = new URL(request.url()).pathname
    let result: unknown = {}
    if (path.endsWith('/workspace')) result = data
    else if (path.endsWith('/edit-preview')) result = { reviewToken: 'reviewed', totals: row.totals }
    else if (path.endsWith('/history')) result = []
    else if (path.endsWith('/totals')) result = row.totals
    else if (path.endsWith('/restore')) { restores++; receipt.status = row.status = 'ACTIVE'; receipt.revision = '4' }
    else if (request.method() === 'PUT') {
      const body = request.postDataJSON()
      expect(body.reviewToken).toBe('reviewed'); expect(body.reason).toBe('Correct store spelling')
      expect(body.items[0].lineItemId).toBe(receipt.contents.purchaseItems[0].lineItemId)
      expect(body.items[0].saleUnitPrice).toBeUndefined()
      expect(body.discounts[0]).toMatchObject({ kind: 'SALE_PROMOTION', appliedAmount: '1.58', lineItemIds: [body.items[0].lineItemId], wholeReceipt: false })
      receipt.metadata.retailer = row.metadata!.retailer = body.retailer; receipt.revision = '2'; edits++
    } else if (request.method() === 'DELETE') {
      expect(request.postDataJSON().confirmed).toBe(true)
      receipt.status = row.status = 'DELETED'; receipt.revision = '3'; deletes++
    } else result = receipt
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify(result) })
  })
  await page.goto('/')
  await page.getByLabel('API access token').fill('synthetic-browser-token')
  await page.getByRole('button', { name: 'Open my workspace', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Make every trip count.' })).toBeVisible()
  await page.getByRole('button', { name: `Open receipt ${row.metadata!.receiptNumber}` }).click()
  await page.getByRole('button', { name: 'Edit receipt', exact: true }).click()
  await expect(page.getByLabel('Unit price before listed discounts 1')).toHaveCount(0)
  await page.getByLabel('Product 1', { exact: true }).fill('Neutrogena Fragrance Free Makeup Remover Cleansing Towelettes with a long recorded product description')
  await page.getByRole('button', { name: 'Add discount to product 1', exact: true }).click()
  await page.getByLabel('Product 1 discount 1 description').fill('Attached sale promotion')
  await page.getByLabel('Product 1 discount 1 amount').fill('1.58')
  await page.getByRole('button', { name: 'Add receipt-wide discount', exact: true }).click()
  await page.getByRole('button', { name: 'Delete', exact: true }).click()
  await page.getByRole('button', { name: 'Add receipt-wide discount', exact: true }).click()
  await page.getByLabel('Discount label 1').fill('');await page.getByLabel('Discount amount 1',{exact:true}).fill('')
  const dimensions = await Promise.all(['Store', 'Purchase date', 'Printed time', 'Regular price 1', 'Quantity 1', 'Purchase channel', 'Printed-total basis', 'Product 1 discount 1 description', 'Product 1 discount 1 type', 'Product 1 discount 1 amount'].map(name => page.getByLabel(name, { exact: !['Purchase channel','Printed-total basis','Product 1 discount 1 type'].includes(name) }).boundingBox()))
  for (const [index, box] of dimensions.entries()) { if (index === 7) expect(box!.height).toBeGreaterThanOrEqual(44); else expect(box!.height).toBe(44) }
  const fonts = await page.locator('.receipt-editor input:not([type=checkbox]), .receipt-editor select, .receipt-editor textarea').evaluateAll(nodes => nodes.map(node => getComputedStyle(node).fontSize))
  expect(fonts.every(font => font === '16px')).toBe(true)
  const textFits = await page.getByLabel('Product 1', { exact: true }).evaluate(node => node.scrollHeight <= node.clientHeight + 1)
  expect(textFits).toBe(true)
  const description = dimensions[7]!, selection = dimensions[8]!
  expect(description.width).toBeGreaterThanOrEqual(selection.width)
  expect(selection.y).toBeGreaterThan(description.y)
  await page.screenshot({ path: `/private/tmp/coupon-control-sizing-${info.project.name}.png`, fullPage: true })

  await page.getByLabel('Store', { exact: true }).fill('Corrected store')
  await page.getByLabel('Reason for correction').fill('Correct store spelling')
  await page.getByRole('button', { name: 'Review updated totals', exact: true }).click()
  await page.getByLabel(/I reviewed the amounts/).check()
  await page.getByRole('button', { name: 'Save receipt changes', exact: true }).click()
  await expect(page.getByText('Active · revision 2', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Delete receipt', exact: true }).click()
  await page.getByLabel('Reason', { exact: true }).fill('Duplicate entry')
  await page.getByLabel('I confirm this deletion.').check()
  await page.getByRole('button', { name: 'Confirm delete', exact: true }).click()
  await expect(page.getByText('Deleted · revision 3', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Restore receipt', exact: true }).click()
  await page.getByLabel('Reason', { exact: true }).fill('Keep receipt')
  await page.getByLabel('I confirm this restoration.').check()
  await page.getByRole('button', { name: 'Confirm restore', exact: true }).click()
  await expect(page.getByText('Active · revision 4', { exact: true })).toBeVisible()
  expect({ edits, deletes, restores }).toEqual({ edits: 1, deletes: 1, restores: 1 })
})

})

test.describe('account detail fixture workflow', () => {
  test.use({ serviceWorkers: 'block' })
  test('account activity and printed receipt controls remain readable', async ({ page }, info) => {
    const data=workspace()
    data.balances=[{provider:'Register Rewards',balance:{monthUsd:'2',startUsd:'2',endUsd:'2',startPoints:null,endPoints:null}}]
    await page.route('**/api/workspace**',route=>route.fulfill({json:data}))
    await page.route('**/api/accounts/activity?**',route=>route.fulfill({json:{provider:'Register Rewards',from:new URL(route.request().url()).searchParams.get('from'),to:new URL(route.request().url()).searchParams.get('to'),beginning:{startUsd:'2',startPoints:null,opening:{date:'2026-09-30',sourceNote:'Confirmed opening'}},ending:{endUsd:'2',endPoints:null},earned:'7',spent:'7',earnedPoints:'0',spentPoints:'0',usdActivityComplete:true,activity:[]}}))
    await page.goto('/')
    await page.getByLabel('API access token').fill('synthetic-account-token')
    await page.getByRole('button', { name: 'Open my workspace', exact: true }).click()
    await expect(page.getByRole('heading',{name:'Make every trip count.'})).toBeVisible()
    const menu=page.getByRole('button', { name: 'Open navigation', exact: true });if(await menu.isVisible())await menu.click()
    await page.getByRole('link',{name:'Rewards & Accounts',exact:true}).click()
    await page.getByRole('button',{name:'Register Rewards',exact:true}).click()
    await expect(page.getByRole('heading',{name:'Account Balances',exact:true})).toBeVisible()
    await page.getByRole('button', { name: 'Set Beginning Balance', exact: true }).click()
    await expect(page.getByLabel('Dollar Balance')).toBeVisible()
    expect(await page.getByLabel('Dollar Balance').evaluate(el=>el.getBoundingClientRect().height)).toBeGreaterThanOrEqual(44)
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
    await page.screenshot({path:info.outputPath('account-details.png'),fullPage:true})
  })
})

test.describe('RR matching fixture workflow', () => {
  test.use({serviceWorkers:'block'})
  test('matches an unused RR and keeps printed observations collapsible above used details', async ({page})=>{
    const data=workspace(), row=receiptRow('rr-spent'), id=row.transactionId
    row.totals.savings.paidAfterRebates='-3.01';row.totals.benefitAdjustedCashSpending=null
    data.receipts=[row];let matched=false,posts=0
    const reward={earningId:'rr-earned',receiptId:'source-receipt',receiptNumber:'1151-source',date:'2026-10-04',time:'9:56 AM',promotion:'Lysol Travel Wipes — Buy 2, Get $5 RR',amount:'5',spentReceiptId:id,spentReceiptNumber:row.metadata!.receiptNumber,usedAmount:'5',status:'PAID',products:['Lysol wipes']}
    await page.route('**/api/workspace**',route=>route.fulfill({json:data}))
    await page.route(`**/api/receipts/${id}`,route=>route.fulfill({json:{transactionId:id,revision:'1',status:'ACTIVE',metadata:row.metadata,evidenceIds:[],rrUses:matched?[reward]:[],contents:{purchaseItems:[],payments:[],appliedCoupons:[],observations:{earnings:[],footerStatus:'PRINTED'},receiptTotals:{taxAmount:'0',finalTotal:'0'}}}}))
    await page.route(`**/api/receipts/${id}/history`,route=>route.fulfill({json:[]}))
    await page.route(`**/api/receipts/${id}/totals`,route=>route.fulfill({json:row.totals}))
    await page.route(`**/api/receipts/${id}/rr-matches`,async route=>{
      if(route.request().method()==='POST'){posts++;expect(route.request().postDataJSON()).toMatchObject({usageEventId:'use',sourceId:'rr-earned',receiptEarning:true});expect(route.request().headers()['if-match-revision']).toBe('1');matched=true;await route.fulfill({status:201,json:{}})}
      else await route.fulfill({json:[{eventId:'use',amount:'5',description:'Lysol RR',matched,options:matched?[]:[{sourceId:'rr-earned',receiptEarning:true,amount:'5',promotion:reward.promotion,sourceReceiptId:'source-receipt',sourceReceiptNumber:'1151-source',earnedDate:'2026-10-04',expires:'2026-10-18'}]}]})
    })
    await page.goto('/')
    await page.getByLabel('API access token').fill('synthetic-rr-token')
    await page.getByRole('button', { name: 'Open my workspace', exact: true }).click()
    await page.getByRole('button',{name:`Open receipt ${row.metadata!.receiptNumber}`}).click()
    await page.getByRole('button',{name:'Totals',exact:true}).click()
    await expect(page.getByText('Total Paid after rebates').locator('..')).toContainText('$−3.01')
    await page.getByLabel('Unused Register Reward').selectOption('rr-earned')
    await page.getByLabel('Matching note').fill('Matched Lysol printed coupon')
    await page.getByRole('button',{name:'Match RR',exact:true}).click()
    await expect(page.getByText('Matched to its original reward. See View Reward below.')).toBeVisible()
    expect(posts).toBe(1)
    const printed=page.getByText('Printed Savings & Walgreens Cash'),used=page.getByText('Used Lysol Travel Wipes — Buy 2, Get $5 RR: $5.00')
    await expect(used).toBeVisible()
    expect((await used.boundingBox())!.y).toBeGreaterThan((await printed.boundingBox())!.y)
    await printed.click()
    await expect(page.getByText('Printed information: Yes')).not.toBeVisible()
  })
})

test.describe('unknown RR source fixture workflow',()=>{
 test.use({serviceWorkers:'block'})
 test('adds a used RR without an earning receipt or dates',async({page})=>{
  const data=workspace(),row=receiptRow('unknown-rr'),id=row.transactionId;data.receipts=[row];let matched=false,posts=0
  await page.route('**/api/workspace**',r=>r.fulfill({json:data}))
  await page.route(`**/api/receipts/${id}`,r=>r.fulfill({json:{transactionId:id,revision:'1',status:'ACTIVE',metadata:row.metadata,evidenceIds:[],contents:{purchaseItems:[],payments:[],appliedCoupons:[],observations:{earnings:[]},receiptTotals:{taxAmount:'0',finalTotal:'0'}}}}))
  await page.route(`**/api/receipts/${id}/history`,r=>r.fulfill({json:[]}))
  await page.route(`**/api/receipts/${id}/totals`,r=>r.fulfill({json:row.totals}))
  await page.route(`**/api/receipts/${id}/rr-matches`,r=>r.fulfill({json:[{eventId:'use',amount:'2',description:'Palmolive',matched,options:[]}]}))
  await page.route(`**/api/receipts/${id}/rr-matches/unknown-source`,async r=>{posts++;expect(r.request().postDataJSON()).toMatchObject({usageEventId:'use',description:'Palmolive',earnedDate:null,expirationDate:null,confirmRecordedUse:true});matched=true;await r.fulfill({status:201,json:{}})})
  await page.goto('/');await page.getByLabel('API access token').fill('synthetic-unknown-rr');await page.getByRole('button', { name: 'Open my workspace', exact: true }).click()
  await page.getByRole('button',{name:`Open receipt ${row.metadata!.receiptNumber}`}).click()
  await page.getByRole('button',{name:'Rewards',exact:true}).click()
  await page.getByRole('button', { name: 'Add RR without an earning receipt', exact: true }).click()
  await expect(page.getByLabel('Earned date (optional)')).toHaveValue('')
  await page.getByLabel('I confirm this RR was used on this receipt; its earning receipt or source may be unknown.').check()
  await page.getByRole('button', { name: 'Save RR and match', exact: true }).click()
  await expect(page.getByText('Matched to its original reward. See View Reward below.')).toBeVisible();expect(posts).toBe(1)
 })
})

test.describe('Rewards standalone inventory fixture',()=>{
 test.use({serviceWorkers:'block'})
 test('adds a reward at the bottom of Receipts and matches it later without an earning receipt',async({page})=>{
  const data=workspace(),row=receiptRow('standalone-spent'),id=row.transactionId;data.receipts=[row]
  let rewardId='',matched=false,created=0,links=0
  await page.route('**/api/workspace**',r=>r.fulfill({json:data}))
  await page.route('**/api/rewards/register-rewards',async r=>{
   if(r.request().method()==='POST'){const b=r.request().postDataJSON();expect(b).toMatchObject({description:'Palmolive RR',amount:'2',earnedDate:null,expirationDate:null,confirmAvailable:true});expect(b.receiptId).toBeUndefined();rewardId=b.rewardId;created++;await r.fulfill({status:201,json:{rewardId,status:'UNUSED'}})}
   else await r.fulfill({json:rewardId?[{rewardId,description:'Palmolive RR',amount:'2',earnedDate:null,expirationDate:null,status:matched?'MATCHED':'UNUSED'}]:[]})
  })
  await page.route(`**/api/receipts/${id}`,r=>r.fulfill({json:{transactionId:id,revision:'1',status:'ACTIVE',metadata:row.metadata,evidenceIds:[],contents:{purchaseItems:[],payments:[],appliedCoupons:[],observations:{earnings:[]},receiptTotals:{taxAmount:'0',finalTotal:'0'}}}}))
  await page.route(`**/api/receipts/${id}/history`,r=>r.fulfill({json:[]}))
  await page.route(`**/api/receipts/${id}/totals`,r=>r.fulfill({json:row.totals}))
  await page.route(`**/api/receipts/${id}/rr-matches`,async r=>{
   if(r.request().method()==='POST'){expect(r.request().postDataJSON()).toMatchObject({sourceId:rewardId,receiptEarning:false,confirmUnknownHistory:true});matched=true;links++;await r.fulfill({status:201,json:{}})}
   else await r.fulfill({json:[{eventId:'use',amount:'2',description:'Palmolive',matched,options:matched?[]:[{sourceId:rewardId,receiptEarning:false,historyUnknown:true,amount:'2',promotion:'Palmolive RR',sourceReceiptId:null,sourceReceiptNumber:null,earnedDate:null,expires:null}]}]})
  })
  await page.goto('/');await page.getByLabel('API access token').fill('synthetic-inventory-token');await page.getByRole('button', { name: 'Open my workspace', exact: true }).click()
  await expect(page.getByRole('heading',{name:'Make every trip count.'})).toBeVisible()
  async function navigate(name:string){const menu=page.getByRole('button', { name: 'Open navigation', exact: true });if(await menu.isVisible())await menu.click();await page.getByRole('link',{name,exact:true}).click()}
  await navigate('Receipts');await page.getByRole('button', { name: 'Add reward without a receipt', exact: true }).click()
  await page.getByLabel('Reward description').fill('Palmolive RR');await page.getByLabel('Reward amount').fill('2')
  await page.getByLabel('I confirm this is an unused RR coupon available to use.').check();await page.getByRole('button',{name:'Save reward',exact:true}).click()
  await expect(page.getByRole('cell',{name:'Palmolive RR',exact:true})).toBeVisible()
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
  await navigate('Receipts');await page.getByRole('button',{name:'All Time',exact:true}).click();await page.getByRole('button',{name:`Open receipt ${row.metadata!.receiptNumber}`}).click();await page.getByRole('button',{name:'Rewards',exact:true}).click()
  await page.getByLabel('Unused Register Reward').selectOption(rewardId);await page.getByLabel('Matching note').fill('Matched saved coupon')
  await page.getByLabel('I confirm this RR was available for this purchase although its earning date or time is unknown.').check();await page.getByRole('button',{name:'Match RR',exact:true}).click()
  await expect(page.getByText('Matched to its original reward. See View Reward below.')).toBeVisible();expect(created).toBe(1);expect(links).toBe(1)
 })
})

test.describe('dashboard fixture workflow',()=>{
 test.use({serviceWorkers:'block'})
test('dashboard cards collapse to their headers independently',async({page})=>{
 const data=workspace();data.receipts=Array.from({length:5},(_,i)=>receiptRow(String(i)))
 data.balances=Array.from({length:4},(_,i)=>({provider:`Test account ${i}`,balance:{monthUsd:'10',startUsd:'10',endUsd:'10',startPoints:null,endPoints:null}}))
 await page.route('**/api/workspace**',route=>route.fulfill({contentType:'application/json',body:JSON.stringify(data)}))
 await page.goto('/');await page.getByLabel('API access token').fill('synthetic');await page.getByRole('button', { name: 'Open my workspace', exact: true }).click()
 for(const title of ['Your Next Shopping Trip','Rewards','Recent Receipts']){
  const card=page.locator('.dashboard-card').filter({has:page.getByRole('heading',{name:new RegExp('^'+title,'i')})})
  await expect(card).toBeVisible();const expanded=(await card.boundingBox())!.height
  await card.getByRole('button',{name:/^Collapse /,exact:false}).click()
  await expect(card.getByRole('button',{name:/^Expand /,exact:false})).toHaveAttribute('aria-expanded','false')
  const collapsed=(await card.boundingBox())!.height,header=(await card.locator('.panel-head').boundingBox())!.height
  expect(collapsed).toBeLessThan(expanded-40);expect(Math.abs(collapsed-header)).toBeLessThanOrEqual(3)
  await card.getByRole('button',{name:/^Expand /,exact:false}).click()
  expect((await card.boundingBox())!.height).toBeGreaterThan(collapsed+40)
 }
})

})

test('approved shopping notebook compares alternatives and imports editable deal notes', async ({ page }, info) => {
  test.skip(!process.env.COUPON_BROWSER_TEST_TOKEN, 'Requires the verified disposable Java API.')
  const token=process.env.COUPON_BROWSER_TEST_TOKEN!,headers={Authorization:`Bearer ${token}`,'Idempotency-Key':crypto.randomUUID()}
  const snapshot=await (await page.request.get('/api/workspace',{headers})).json()
  const idA=crypto.randomUUID(),idB=crypto.randomUUID(),lineA=crypto.randomUUID(),lineB=crypto.randomUUID(),scott=crypto.randomUUID()
  const run=crypto.randomUUID().slice(0,8),nameA=`Notebook A ${info.project.name} ${run}`,nameB=`Notebook B ${info.project.name} ${run}`
  const scenario=(second:boolean)=>({items:[{id:second?lineB:lineA,name:'Original products',sku:null,regular:null,sale:'22.55',quantity:1},...(second?[{id:scott,name:'Scott packages',sku:null,regular:null,sale:'5.00',quantity:2}]:[])],coupons:[{id:crypto.randomUUID(),description:'Entered coupon',amount:second?'11.50':'7.00',requiredQuantity:1,lineIds:[],wholeReceipt:true,savedCouponId:null}],promotions:second?[]:[{id:crypto.randomUUID(),description:'Expected RR',rewardType:'REGISTER_REWARD',rewardAmount:'10',requiredQuantity:0,requiredSpend:'0',spendBasis:'BEFORE_COUPONS',allowsWalgreensCash:true,wholeReceipt:true,lineIds:[]}],tax:second?'1.63':'1.58',taxRate:null,walgreensCashUsed:'2',registerRewardsUsed:second?'20':'15',giftCardUsed:'0',manualOop:null,paymentMethod:'CASH',fundingNotes:''})
  for(const second of [false,true]){
    const response=await page.request.post('/api/shopping-plans',{headers:{...headers,'Idempotency-Key':crypto.randomUUID()},data:{planId:second?idB:idA,name:second?nameB:nameA,retailer:'Walgreens',shoppingDate:snapshot.asOf,scenario:scenario(second),organization:{stage:'IDEA',alternativeOf:second?idA:null,checkoutOrder:null,rewardSources:[]}}})
    expect(response.status(),await response.text()).toBe(201)
  }
  await page.goto('/#shopping');await page.getByLabel('API access token').fill(token);await page.getByRole('button', { name: 'Open my workspace', exact: true }).click()
  const idea=page.locator('.weekly-deal').filter({has:page.getByRole('heading',{name:nameA,exact:true})})
  await idea.getByRole('button', { name: 'Compare costs', exact: true }).click()
  const compare=page.getByRole('dialog',{name:'Compare Costs — Option A vs. Option B'})
  await compare.getByLabel('Option A',{exact:true}).selectOption(idA);await compare.getByLabel('Option B',{exact:true}).selectOption(idB)
  await expect(compare.getByRole('cell',{name:'$0.13',exact:true})).toBeVisible();await expect(compare.getByRole('cell',{name:'$0.68',exact:true})).toBeVisible()
  await expect(compare.getByText('2 Scott packages',{exact:true})).toBeVisible()
  expect(await compare.locator('.table-wrap').evaluate(node=>node.scrollWidth<=node.clientWidth)).toBe(true)
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
  await page.screenshot({path:info.outputPath('notebook-comparison.png'),fullPage:false})
  await compare.getByRole('button', { name: 'Choose B', exact: true }).click();await expect(compare).toHaveCount(0)
  const trip=page.locator('.checkout-order li').filter({hasText:nameB});await expect(trip).toBeVisible()
  await trip.getByRole('button', { name: 'Edit transaction', exact: true }).click()
  const edited=page.getByRole('dialog',{name:'Edit deal scenario'});await expect(edited.getByRole('button',{name:'Save scenario',exact:true})).toBeEnabled();await edited.getByRole('button',{name:'Save scenario',exact:true}).click();await expect(edited).toHaveCount(0)
  await trip.getByRole('button', { name: 'View shopping list', exact: true }).click()
  await expect(page.getByRole('dialog').getByText('Scott packages',{exact:false})).toBeVisible()
  await page.getByRole('button',{name:'Close dialog',exact:true}).click()
  await page.getByRole('button',{name:'Paste deal',exact:true}).click()
  const editor=page.getByRole('dialog',{name:'Plan a shopping deal'})
  const notes='Lysol 3.29 x2\n5 off 2\nearn 5 RR'
  await editor.getByLabel('Type or paste your deal notes').fill(notes);await editor.getByRole('button', { name: 'Add to this plan', exact: true }).click()
  await editor.getByLabel('Plan name',{exact:true}).fill(`Pasted notebook ${info.project.name}`)
  await expect(editor.getByLabel('Product 1',{exact:true})).toHaveValue('Lysol')
  await expect(editor.getByLabel('Regular price 1',{exact:true})).toHaveValue('')
  await expect(editor.getByRole('button',{name:'Save scenario',exact:true})).toBeEnabled()
  await page.screenshot({path:info.outputPath('notebook-entry.png'),fullPage:false})
  await editor.getByRole('button',{name:'Save scenario',exact:true}).click();await expect(editor).toHaveCount(0)
  const after=await (await page.request.get('/api/workspace',{headers})).json(),saved=after.plans.find((r:{plan:{name:string}})=>r.plan.name===`Pasted notebook ${info.project.name}`)
  expect(saved.plan.scenario.source.text).toBe(notes);expect(saved.plan.scenario.items[0].regular).toBeNull();expect(saved.plan.organization.stage).toBe('IDEA')
  expect(saved.plan.purchasedReceiptId).toBeNull()
})


test.describe('approved form layout',()=>{
 test.use({serviceWorkers:'block'})
 test('outlined form labels, repeated actions and automatic receipt totals',async({page},info)=>{
 const data=workspace()
 await page.route('**/api/**',route=>route.fulfill({status:200,contentType:'application/json',body:'[]'}))
 await page.route('**/api/workspace**',route=>route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(data)}))
 await page.route('**/api/shopping-plans/scenario/preview',async route=>{
  const input=route.request().postDataJSON();await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({items:[{id:input.scenario.items[0].id,totalPrice:'8.99',itemOopBeforeTaxAndFunding:'8.99'}]})})
 })
 await page.goto('/');await page.getByLabel('API access token').fill('synthetic-forms-token');await page.getByRole('button', { name: 'Open my workspace', exact: true }).click()
 await expect(page.getByRole('heading',{name:'Make every trip count.'})).toBeVisible()
 const menu=page.getByRole('button', { name: 'Open navigation', exact: true });if(await menu.isVisible())await menu.click()
 await page.getByRole('link',{name:'Receipts',exact:true}).click();await page.getByRole('button',{name:'Add receipt',exact:true}).click()
 const dialog=page.getByRole('dialog',{name:'Add receipt'})
 await expect(dialog.getByRole('button', { name: 'Top: Delete', exact: true })).toBeVisible()
 await expect(dialog.getByLabel('Store',{exact:true}).locator('..')).toContainText('Store')
 await expect(dialog.getByLabel('Store',{exact:true})).toHaveValue('')
 await dialog.getByLabel('Store',{exact:true}).fill('Target')
 await expect(dialog.getByLabel('Store',{exact:true})).toHaveValue('Target')
 expect(await dialog.getByLabel('Store',{exact:true}).evaluate(input=>getComputedStyle(input.closest('label')!).fontSize)).toBe('13px')
 const outline=await dialog.getByLabel('Store',{exact:true}).evaluate(input=>{const label=input.closest('label')!,a=label.getBoundingClientRect(),b=input.getBoundingClientRect();return {inside:b.top>=a.top&&b.bottom<=a.bottom,color:getComputedStyle(input).borderColor}})
 expect(outline.inside).toBe(true);expect(await dialog.getByLabel('Store',{exact:true}).evaluate(input=>getComputedStyle(input).borderTopWidth)).toBe('1px')
 await dialog.getByLabel('Product description 1').fill('Synthetic Dove');await dialog.getByLabel('Regular price 1',{exact:true}).fill('8.99')
 await expect(dialog.getByText('$8.99',{exact:true}).first()).toBeVisible()
 await dialog.getByRole('button',{name:'Add discount to product 1',exact:true}).click()
 await expect(dialog.getByLabel('Product 1 discount 1 amount')).toBeVisible()
 if(info.project.name!=='mobile'){const q=await dialog.getByLabel('Product 1 discount 1 required purchase quantity (optional)').boundingBox(),a=await dialog.getByLabel('Product 1 discount 1 amount').boundingBox();expect(Math.abs(q!.y-a!.y)).toBeLessThan(2);expect(q!.x).toBeLessThan(a!.x)}
 await expect(dialog.getByRole('button',{name:'BOGO 50%',exact:true})).toBeVisible()
 await dialog.getByRole('button',{name:'Collapse all products',exact:true}).click()
 await expect(dialog.getByLabel('Product description 1')).not.toBeVisible()
 await dialog.locator('.receipt-product > summary').first().click()
 await expect(dialog.getByLabel('Product description 1')).toBeVisible()
 const infoIcon=dialog.getByRole('button',{name:'About product totals',exact:true})
 if(info.project.name==='mobile')await infoIcon.tap();else await infoIcon.hover()
 await expect(page.getByRole('tooltip',{name:'Product totals',exact:true})).toBeVisible()
 if(info.project.name==='mobile')await dialog.getByLabel('Product description 1').tap();else await dialog.getByRole('button',{name:'Add product',exact:true}).hover()
 await expect(page.getByRole('tooltip',{name:'Product totals',exact:true})).toHaveCount(0)
 await expect(dialog).toBeVisible()

 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
 await page.screenshot({path:info.outputPath('receipt-form-layout.png'),fullPage:true})
 await dialog.getByRole('button', { name: 'Top: Cancel', exact: true }).scrollIntoViewIfNeeded()
 page.once('dialog',d=>d.accept());await dialog.getByRole('button', { name: 'Top: Cancel', exact: true }).click();await expect(dialog).toHaveCount(0)
})

 test('account information hovers or taps and unavailable action has consistent insets',async({page},info)=>{
  const data=workspace();data.balances=[{provider:'Walgreens Cash',balance:{monthUsd:'1',startUsd:'1',endUsd:'2',startPoints:null,endPoints:null}}]
  await page.route('**/api/**',route=>{const url=new URL(route.request().url());let body:unknown=[];if(url.pathname.endsWith('/workspace'))body=data;else if(url.pathname.endsWith('/accounts/activity'))body={provider:'Walgreens Cash',from:url.searchParams.get('from'),to:url.searchParams.get('to'),beginning:{...data.balances[0].balance,opening:null},ending:data.balances[0].balance,earned:'1',spent:'0',earnedPoints:'0',spentPoints:'0',usdActivityComplete:true,activity:[]};return route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(body)})})
  await page.goto('/');await page.getByLabel('API access token').fill('synthetic-help-token');await page.getByRole('button',{name:'Open my workspace',exact:true}).click();await expect(page.getByRole('heading',{name:'Make every trip count.'})).toBeVisible()
  const menu=page.getByRole('button',{name:'Open navigation',exact:true});if(await menu.isVisible())await menu.click()
  await page.getByRole('link',{name:'Rewards & Accounts',exact:true}).click();await expect(page.getByRole('button',{name:'Walgreens Cash',exact:true})).toBeVisible()
  const icon=page.getByRole('button',{name:'About Rewards Overview',exact:true})
  if(info.project.name==='mobile')await icon.tap();else await icon.hover()
  const tooltip=page.getByRole('tooltip',{name:'Rewards Overview',exact:true});await expect(tooltip).toBeVisible();await expect(tooltip).toContainText('Available uses positive recorded closing balances')
  const bounds=await tooltip.boundingBox();expect(bounds!.x).toBeGreaterThanOrEqual(0);expect(bounds!.x+bounds!.width).toBeLessThanOrEqual(page.viewportSize()!.width)
  if(info.project.name==='mobile')await page.getByRole('heading',{name:'Rewards & Accounts',exact:true}).tap();else await page.getByRole('heading',{name:'Rewards & Accounts',exact:true}).hover()
  await expect(tooltip).toHaveCount(0)
  await page.getByRole('button',{name:'Walgreens Cash',exact:true}).click()
  const panel=page.locator('.reward-unavailable-panel'),button=panel.getByRole('button',{name:'Mark reward unavailable',exact:true});await button.scrollIntoViewIfNeeded()
  const a=await panel.boundingBox(),b=await button.boundingBox();expect(b!.x-a!.x).toBeGreaterThanOrEqual(12);expect(a!.y+a!.height-b!.y-b!.height).toBeGreaterThanOrEqual(12)
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
 })

})

test.describe('workspace layout verification',()=>{
 test.use({serviceWorkers:'block'})
 test('workspace layouts align controls and group inventory purchase history',async({page},info)=>{
 page.on('dialog',dialog=>dialog.accept())
 const data=workspace()
 const plan=planningRow();plan.plan.shoppingDate=data.asOf;plan.plan.name='Poise';data.plans=[plan]
 data.rewardsOverview={asOf:data.asOf,available:'0.00',expected:'0.00',total:'0.00',knownAvailable:'0.00',knownExpected:'0.00',expectedCount:'0',missingBalances:[],missingValuations:[]}
 data.inventory=[
  {inventoryId:'a',productId:'a',revision:'1',productName:'WALG EVERYDAY PLATES',sku:'019560284600',totalPurchased:'2',quantityOnHand:'2',netCost:'4.00',sources:[{receiptId:'r1',lineItemId:'l1',revision:'1',quantity:'2',active:true,productName:'Plates',store:'Walgreens',receiptNumber:'R1',date:'2026-10-25',netCost:'4.00'}]},
  {inventoryId:'b',productId:'b',revision:'1',productName:'WALG EVERYDAY PLATES 20S',sku:'00019560284600',totalPurchased:'1',quantityOnHand:'1',netCost:'1.50',sources:[{receiptId:'r2',lineItemId:'l2',revision:'1',quantity:'1',active:true,productName:'Plates 20S',store:'Walgreens',receiptNumber:'R2',date:'2026-10-26',netCost:'1.50'}]}
 ]
 await page.route('**/api/workspace**',route=>route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(data)}))
 await page.route('**/api/rewards/register-rewards',route=>route.fulfill({status:200,contentType:'application/json',body:'[]'}))
 await page.goto('/')
 await page.getByLabel('API access token').fill('synthetic-layout-token')
 await page.getByRole('button',{name:'Open my workspace',exact:true}).click()
 await expect(page.getByRole('heading',{name:'Make every trip count.'})).toBeVisible()
 async function navigate(name:string){const menu=page.getByRole('button',{name:'Open navigation',exact:true});if(await menu.isVisible())await menu.click();await page.getByRole('link',{name,exact:false}).click()}
 await navigate('Shopping & Deals')
 await expect(page.getByLabel('Weekly store')).toContainText('H-E-B')
 await expect(page.getByLabel('Weekly store')).toContainText('Walmart')
 await expect(page.getByLabel('Weekly store')).toContainText('Target')
 expect(await page.getByLabel('Shopping week',{exact:true}).evaluate(node=>getComputedStyle(node).textAlign)).toBe('center')
 if(info.project.name!=='mobile') {
  const overview=await page.locator('.shopping-week-overview > .panel').evaluateAll(nodes=>nodes.map(node=>{const r=node.getBoundingClientRect();return {top:r.top,bottom:r.bottom}}))
  expect(Math.abs(overview[0].top-overview[1].top)).toBeLessThanOrEqual(1)
  expect(Math.abs(overview[0].bottom-overview[1].bottom)).toBeLessThanOrEqual(1)
  const headers=await page.locator('.shopping-plan-columns > div:first-child .panel-head, .shopping-plans-active > summary').evaluateAll(nodes=>nodes.map(node=>node.getBoundingClientRect().top))
  expect(Math.max(...headers)-Math.min(...headers)).toBeLessThanOrEqual(1)
 }
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
 await page.screenshot({path:info.outputPath('shopping-layout.png'),fullPage:true})
 await navigate('Receipts')
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
 await page.getByText('More filters — Show / Hide',{exact:true}).click()
 await page.screenshot({path:info.outputPath('receipt-search-layout.png'),fullPage:true})
 await page.getByRole('button',{name:'Add receipt',exact:true}).click()
 await expect(page.getByRole('dialog')).toBeVisible()
 expect(await page.getByRole('dialog').getByLabel('Store',{exact:true}).evaluate(node=>getComputedStyle(node).borderTopWidth)).toBe('1px')
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
 await page.screenshot({path:info.outputPath('add-receipt-layout.png'),fullPage:true})
 await page.getByRole('button',{name:'Cancel',exact:true}).click()
 await navigate('Rewards & Accounts')
 const controls=page.locator('.rewards-account-filters')
 if(info.project.name!=='mobile') {
  const positions=await controls.locator(':scope > *').evaluateAll(nodes=>nodes.map(n=>n.getBoundingClientRect().bottom))
  expect(Math.max(...positions)-Math.min(...positions)).toBeLessThanOrEqual(1)
 }
 await page.screenshot({path:info.outputPath('rewards-layout.png'),fullPage:true})
 await navigate('Promotions & Rebates')
 await expect(page.getByRole('navigation',{name:'Promotion sections'})).toBeVisible()
 await page.getByRole('button',{name:'Add promotion',exact:true}).click()
 await expect(page.getByRole('dialog',{name:'Add promotion'})).toBeVisible()
 await page.getByRole('button',{name:'Cancel',exact:true}).click()
 await navigate('Inventory')
 await expect(page.getByRole('columnheader',{name:'Quantity on hand'})).toHaveCount(0)
 await expect(page.getByRole('cell',{name:'3 package(s)',exact:true})).toBeVisible()
 await expect(page.getByRole('cell',{name:'$5.50',exact:true})).toBeVisible()
 await page.getByRole('button',{name:'View / update',exact:true}).click()
 await expect(page.getByRole('button',{name:'R1',exact:true})).toBeVisible()
 await expect(page.getByRole('button',{name:'R2',exact:true})).toBeVisible()
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
 await page.screenshot({path:info.outputPath('inventory-history.png'),fullPage:true})
})

})

test('individual accounts, observed balances and linked gift-card exchanges retain separate value',async({page},info)=>{
 test.skip(!process.env.COUPON_BROWSER_TEST_TOKEN,'Disposable API required.')
 const headers={Authorization:`Bearer ${process.env.COUPON_BROWSER_TEST_TOKEN}`,'Idempotency-Key':crypto.randomUUID()}
 const id=crypto.randomUUID(),nickname=`My Fetch ${info.project.name} ${Date.now()}`
 const created=await page.request.post('/api/reward-instruments',{headers,data:{id,kind:'ACCOUNT_REWARD',provider:'Fetch',nickname,unit:'POINTS',opening:'20000',date:'2026-10-30',historyComplete:true,reason:'Synthetic observed opening'}})
 expect(created.status()).toBe(201)
 await page.goto('/');await page.getByLabel('API access token').fill(process.env.COUPON_BROWSER_TEST_TOKEN!);await page.getByRole('button',{name:'Open my workspace',exact:true}).click();await expect(page.getByRole('heading',{name:'Make every trip count.'})).toBeVisible()
 const menu=page.getByRole('button',{name:'Open navigation',exact:true});if(await menu.isVisible())await menu.click();await page.getByRole('link',{name:/^Rewards & Accounts(?: \d+)?$/}).click()
 await page.getByLabel('Select individual account or card').selectOption(id)
 await expect(page.getByLabel('Individual balance comparison')).toContainText('20000 points')
 await page.getByText('Record an observed exchange into a gift card',{exact:true}).click()
 await page.getByLabel('Issued card retailer').fill('Target');await page.getByLabel('Issued card nickname').fill(`Issued ${nickname}`);await page.getByLabel('Observed source redemption (POINTS, blank if unknown)').fill('10000');await page.getByLabel('Issued gift-card value (USD)').fill('10.00');await page.getByLabel('Exchange date (optional)').fill('2026-10-30');await page.getByLabel('Exchange evidence or reason').fill('Observed synthetic points redemption and separate card');await page.getByRole('button',{name:'Save linked exchange',exact:true}).click()
 await expect(page.getByLabel('Select individual account or card').getByRole('option',{name:new RegExp(`Issued ${nickname}`)})).toHaveCount(1)
 const data=await (await page.request.get('/api/reward-instruments',{headers})).json();const card=data.instruments.find((r:{nickname:string})=>r.nickname===`Issued ${nickname}`);expect(card.calculated).toBe(10);expect(card.newEarnings).toBe(0)
 await page.getByLabel('Select individual account or card').selectOption(card.id);await page.getByLabel('Activity type',{exact:true}).selectOption('REDEMPTION');await page.getByLabel('Observed activity amount (USD)').fill('4.00');await page.getByLabel('Actual event date (optional)').fill('2026-10-30');await page.getByLabel('Evidence reference or reason',{exact:true}).fill('Observed four dollar card use');await page.getByRole('button',{name:'Save observed activity',exact:true}).click()
 await expect(page.getByLabel('Individual balance comparison')).toContainText('Calculated $6.00')
 await page.getByLabel('Activity type',{exact:true}).selectOption('REPORTED_BALANCE');await page.getByLabel('Observed activity amount (USD)').fill('5.00');await page.getByLabel('Evidence reference or reason',{exact:true}).fill('Retailer reports five dollars');await page.getByRole('button',{name:'Save observed activity',exact:true}).click()
 const comparison=page.getByLabel('Individual balance comparison');await expect(comparison).toContainText('Actual reported $5.00');await expect(comparison).toContainText('Difference $1.00')
 const final=await(await page.request.get('/api/reward-instruments',{headers})).json();expect(final.instruments.find((r:{id:string})=>r.id===id).calculated).toBe(10000)
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
})

test.describe('compact coupon browser checks',()=>{
 test.use({serviceWorkers:'block'})
test('compact coupon review keeps controls readable and imports only approved entries', async ({ page }, info) => {
  const data=workspace(),offerId=crypto.randomUUID()
  data.coupons=Array.from({length:10},(_,i)=>({couponId:crypto.randomUUID(),provider:'P&G',description:'CREST $2 off 1',discountAmount:'2',requiredQuantity:'1',couponType:'MANUFACTURER',externalId:null,status:i===9?'APPLIED':'CLIPPED',clippedOn:'2026-11-08',expirationDate:'2099-12-01',redeemedOn:i===9?'2026-11-08':null,redeemedTransactionId:i===9?'historical-receipt':null,revision:'1',terms:{offerId,source:'PAPER_MANUFACTURER',discount:'DOLLARS',productOrBrand:'Crest',conditions:'Excludes trial sizes',usageLimit:'1'}}))
  const entries=Array.from({length:197},(_,i)=>({couponId:crypto.randomUUID(),provider:'Walgreens',description:`Offer ${i+1} — $2 off 1`,amount:'2',requiredQuantity:1,couponType:'MANUFACTURER',source:'DIGITAL_MANUFACTURER',discount:'DOLLARS',expirationDate:'2026-12-05',clippedOn:'2026-11-08',status:'CLIPPED',copies:1,usageLimit:1,storeRestriction:'Walgreens',conditions:'Recorded qualifying terms',productOrBrand:`Product ${i}`,originalOfferText:i<195?`Original source ${i}`:'Repeated identical evidence',offerDetails:{reviewStatus:i<195?'PARSED':'NEEDS_REVIEW'}}))
  let imported:unknown[]=[]
  await page.route('**/api/**',async route=>{
    const path=new URL(route.request().url()).pathname
    if(path.endsWith('/workspace'))return route.fulfill({json:data})
    if(path.endsWith('/walgreens/preview'))return route.fulfill({json:{entries,extractedCount:197,declaredCount:197,needsReview:2,ocrPages:0}})
    if(path.endsWith('/walgreens/import')){imported=route.request().postDataJSON().entries;return route.fulfill({json:[]})}
    return route.fulfill({json:[]})
  })
  await page.goto('/#coupons');await page.getByLabel('API access token').fill('synthetic-compact-coupons');await page.getByRole('button',{name:'Open my workspace',exact:true}).click()
  await expect(page.getByRole('heading',{name:'Coupons',exact:true})).toBeVisible()
  await expect(page.getByText('· 9 available copies · 10 recorded')).toBeVisible()
  expect(await page.getByRole('article').count()).toBe(1)
  const search=await page.getByLabel('Search Coupons').boundingBox(),group=await page.getByLabel('Group Coupons By').boundingBox(),status=await page.getByLabel('Coupon Status').boundingBox()
  if(page.viewportSize()!.width>760){expect(Math.abs(search!.y-group!.y)).toBeLessThan(3);expect(Math.abs(search!.y-status!.y)).toBeLessThan(3);expect(search!.width).toBeGreaterThan(group!.width)}
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
  await page.getByRole('button',{name:'About coupon grouping',exact:true}).click();await expect(page.getByRole('tooltip')).toContainText('Grouping does not merge records');await page.keyboard.press('Escape');await expect(page.getByRole('tooltip')).toHaveCount(0)
  await page.getByRole('button',{name:'Crest',exact:true}).click();await expect(page.getByRole('button',{name:'Add Copy',exact:true})).toHaveCount(0);await page.getByRole('button',{name:'Crest',exact:true}).click()
  await page.screenshot({path:info.outputPath('compact-coupons.png'),fullPage:true})
  await page.getByRole('button',{name:'Walgreens',exact:true}).click();await page.getByRole('button',{name:'Import Coupons',exact:true}).click()
  await page.getByLabel('Walgreens text PDF').setInputFiles({name:'synthetic-weekly.pdf',mimeType:'application/pdf',buffer:Buffer.from('%PDF synthetic review')})
  await expect(page.getByText('195 Ready to Import',{exact:true})).toBeVisible();await expect(page.getByText('2 Needs Review',{exact:true})).toBeVisible()
  await expect(page.getByLabel('Description', {exact:true})).toHaveCount(0);await expect(page.getByRole('checkbox')).toHaveCount(0)
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
  const overflowing=await page.getByRole('dialog').evaluate(node=>node.scrollWidth>node.clientWidth+1);expect(overflowing).toBe(false)
  await expect(page.getByRole('button',{name:'Import approved offers',exact:true})).toBeInViewport()
  await page.screenshot({path:info.outputPath('compact-import-review.png'),fullPage:true})
  await page.getByRole('button',{name:'Needs Review (2)',exact:true}).click();await expect(page.getByRole('button',{name:/^Details:/})).toHaveCount(2)
  await page.getByRole('button',{name:/^Details:/}).first().click();await expect(page.getByLabel('Description',{exact:true})).toBeVisible();expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
  await page.getByRole('button',{name:'Import approved offers',exact:true}).click();await expect(page.getByRole('dialog')).toHaveCount(0);expect(imported).toHaveLength(195)
})

})

test.describe('global coupon import browser checks',()=>{
 test.use({serviceWorkers:'block'})
 test('Available import previews offers, cancels safely, and imports one confirmed batch',async({page})=>{
  const data=workspace(),id='07c2fcb3-51eb-4dc4-8c69-d7d072c32c50';let imports=0;
  const offer={couponId:id,provider:'Walgreens',description:'Angel Soft — $1.50 off 3',amount:'1.50',requiredQuantity:3,couponType:'OTHER',discount:'DOLLARS',source:'DIGITAL_OTHER',copies:1,usageLimit:1,status:'AVAILABLE',clippedOn:null,expirationDate:'2026-11-14',storeRestriction:'Walgreens',originalOfferText:'Angel Soft selected varieties...',offerDetails:{reviewStatus:'PARSED',sourceLayout:'WEB_CARDS',statusEvidence:'WEBSITE_CLIP_CONTROL',qualificationStatus:'NEEDS_REVIEW'}};
  await page.route('**/api/**',route=>{const path=new URL(route.request().url()).pathname;if(path.endsWith('/workspace'))return route.fulfill({json:data});if(path.endsWith('/preview'))return route.fulfill({json:{documentKind:'AVAILABLE',entries:[offer,{...offer,couponId:'missing',description:'Missing expiration',expirationDate:null,originalOfferText:'Missing expiration source'}]}});if(path.endsWith('/walgreens/import')){imports++;const body=route.request().postDataJSON();expect(body.summary).toBe(true);expect(body.entries).toHaveLength(1);expect(body.entries[0].status).toBe('AVAILABLE');return route.fulfill({json:{coupons:[],outcomes:[{couponId:id,outcome:'IMPORTED'}],summary:{imported:1,updated:0,alreadySaved:0,needsReview:0,failed:0}}})}return route.fulfill({json:[]})});
  await page.goto('/#coupons');await page.getByLabel('API access token').fill('synthetic-auto-available');await page.getByRole('button',{name:'Open my workspace',exact:true}).click();await page.getByRole('button',{name:'Import Coupons',exact:true}).click();await page.getByLabel('Walgreens text PDF').setInputFiles({name:'synthetic.pdf',mimeType:'application/pdf',buffer:Buffer.from('%PDF synthetic')});await expect(page.getByText('1 Needs Review',{exact:true})).toBeVisible();expect(imports).toBe(0);await page.getByRole('button',{name:'Cancel import',exact:true}).click();await expect(page.getByRole('dialog')).toHaveCount(0);expect(imports).toBe(0);await page.getByRole('button',{name:'Import Coupons',exact:true}).click();await page.getByLabel('Walgreens text PDF').setInputFiles({name:'synthetic.pdf',mimeType:'application/pdf',buffer:Buffer.from('%PDF synthetic')});await page.getByRole('button',{name:'Import approved offers',exact:true}).click();await expect(page.getByText('1 Imported',{exact:true})).toBeVisible();await expect(page.getByText('1 Needs Review',{exact:true})).toBeVisible();expect(imports).toBe(1);await expect(page.getByRole('button',{name:'Import approved offers',exact:true})).toBeDisabled();expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.getByRole('button',{name:'Done',exact:true}).click();await expect(page.getByRole('dialog')).toHaveCount(0);
 })

 test('global Import Coupons opens one shared supported-source workflow from every coupon view',async({page},info)=>{
  const data=workspace();await page.route('**/api/**',route=>route.fulfill({json:new URL(route.request().url()).pathname.endsWith('/workspace')?data:[]}));await page.goto('/#coupons');await page.getByLabel('API access token').fill('synthetic-global-import');await page.getByRole('button',{name:'Open my workspace',exact:true}).click();await expect(page.getByRole('heading',{name:'Coupons',exact:true})).toBeVisible()
  const verify=async()=>{const button=page.getByRole('button',{name:'Import Coupons',exact:true});await expect(button).toHaveCount(1);const box=await button.boundingBox(),add=await page.getByRole('button',{name:'Add coupon',exact:true}).boundingBox();if(page.viewportSize()!.width>760)expect(Math.abs(box!.y-add!.y)).toBeLessThan(3);await button.click();await expect(page.getByRole('dialog',{name:'Import Coupons',exact:true})).toBeVisible();await expect(page.getByLabel('Import source')).toHaveValue('WALGREENS');await expect(page.getByLabel('PDF format')).toHaveValue('AUTO');expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.getByRole('button',{name:'Close dialog',exact:true}).click()}
  await verify();await page.getByRole('button',{name:'Paper coupons',exact:true}).click();await verify();await page.getByRole('button',{name:'Walgreens',exact:true}).click();for(const name of ['Available','Clipped','Redeemed','Removed / unknown']){await page.getByRole('button',{name,exact:true}).click();await verify()}for(const name of ['CVS','Target','Costco']){await page.getByRole('button',{name,exact:true}).click();await verify()}
  await page.getByRole('button',{name:'Import Coupons',exact:true}).click();await page.getByLabel('Import source').selectOption('CVS');await expect(page.getByRole('status')).toContainText('PDF import is not supported');await expect(page.getByLabel('Walgreens text PDF')).toBeDisabled();expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.screenshot({path:info.outputPath('global-import-unsupported-source.png'),fullPage:true})
 })
})

test.describe('shared shopping coupon discovery',()=>{
 test.use({serviceWorkers:'block'})
 test('Idea editing discovers Venus offers and preserves a pending Available selection after save and reopen',async({page})=>{
  const data=workspace(),plan=planningRow();plan.plan.name='Venus idea';plan.plan.retailer='Walgreens';plan.plan.shoppingDate=data.asOf;plan.plan.organization={stage:'IDEA',alternativeOf:null,checkoutOrder:null,rewardSources:[]};plan.plan.scenario={items:[{id:'venus',name:'Venus Razor',sku:null,regular:'12',sale:null,quantity:'1'}],coupons:[],promotions:[],tax:'0',walgreensCashUsed:'0',registerRewardsUsed:'0',giftCardUsed:'0',manualOop:null,paymentMethod:'CASH',fundingNotes:''};data.plans=[plan];
  const base={provider:'Walgreens',description:'Gillette or Venus $4 off 1',discountAmount:'4',requiredQuantity:'1',couponType:'MANUFACTURER',externalId:null,expirationDate:'2099-11-30',clippedOn:null,redeemedOn:null,redeemedTransactionId:null,revision:'1',terms:{source:'DIGITAL_MANUFACTURER',discount:'DOLLARS',storeRestriction:'Walgreens',productOrBrand:'Gillette or Venus',offerDetails:{recordedClipLimit:'1'}}};data.coupons=[{...base,couponId:'clipped-venus',status:'CLIPPED'},{...base,couponId:'available-venus',status:'AVAILABLE',description:'Venus $5 Available',discountAmount:'5'}];let saved=false;const mutations:string[]=[];
  await page.route('**/api/**',route=>{const path=new URL(route.request().url()).pathname;if(path.endsWith('/workspace'))return route.fulfill({json:data});mutations.push(path);if(path.endsWith('/match-offers'))return route.fulfill({json:[]});if(path.endsWith('/advertisement-preview'))return route.fulfill({json:{offers:[{dealId:'nice-draft',title:'Nice! Peanuts, Trail Mix or Snacks',interpretedTerms:'$1.99 with myWalgreens',retailer:'Walgreens',validFrom:'2026-11-08',validThrough:'2026-11-14',fields:{page:'1',sourceName:'Availables.pdf',promotionText:'$1.99 with myWalgreens'},warnings:[],draft:{items:[{id:'nice-line',name:'Nice! Peanuts, Trail Mix or Snacks',regular:null,sale:'1.99',quantity:'1'}],coupons:[],promotions:[]}},{dealId:'doritos-draft',title:'Doritos',interpretedTerms:'2/$6 or $5.49 each with myWalgreens',retailer:'Walgreens',validFrom:'2026-11-08',validThrough:'2026-11-14',fields:{page:'1',sourceName:'Availables.pdf',promotionText:'2/$6 or $5.49 each with myWalgreens'},warnings:[],draft:{items:[{id:'doritos-line',name:'Doritos',regular:'5.49',sale:'5.49',quantity:'2',priceRule:{type:'TWO_FOR',bundlePrice:'6'}}],coupons:[],promotions:[]}}]}});if(path.endsWith('/weekly-preview')){const body=route.request().postDataJSON();return route.fulfill({json:{requirementsMet:false,affordable:false,requiredItems:body.requiredItems,issues:['Jiffy Corn Muffin Mix needs review: its source advertisement was removed.'],steps:[],cash:null,earnings:'0',remaining:'20',notice:'No balances changed'}})}if(path.endsWith('/scenario/preview')){const s=route.request().postDataJSON().scenario,selected=s.coupons.some((c:{savedCouponId?:string})=>c.savedCouponId==='available-venus');return route.fulfill({json:{items:s.items.map((l:{id:string;name:string})=>({id:l.id,name:l.name,totalPrice:'12',couponsApplied:selected?'5':'0',itemOopBeforeTaxAndFunding:selected?'7':'12',possibleRewards:'0'})),coupons:s.coupons.map((c:{id:string;description:string})=>({id:c.id,description:c.description,applied:selected,quantityMet:true,amount:selected?'5':'0',reason:'Included in planning estimate'})),promotions:[],regularTotal:'12',saleTotal:'12',couponTotal:selected?'5':'0',merchandiseAfterCoupons:selected?'7':'12',tax:'0',registerRewardsUsed:'0',walgreensCashUsed:'0',giftCardUsed:'0',calculatedOop:selected?'7':'12',selectedOop:selected?'7':'12',potentialRewards:'0'}})}if(path.endsWith('/shopping-plans/'+plan.plan.planId)&&route.request().method()==='PUT'){const body=route.request().postDataJSON();expect(body.scenario.coupons).toHaveLength(1);expect(body.scenario.coupons[0].savedCouponId).toBe('available-venus');expect(body.scenario.coupons[0].pendingVerification).toBe(true);plan.plan={...plan.plan,...body,revision:'2'};saved=true;return route.fulfill({json:plan.plan})}return route.fulfill({json:[]})});
  await page.goto('/#shopping');await page.getByLabel('API access token').fill('synthetic-venus-coupons');await page.getByRole('button',{name:'Open my workspace',exact:true}).click();const idea=page.locator('.weekly-idea').filter({hasText:'Venus idea'});await expect(idea.getByRole('button',{name:'Edit',exact:true})).toHaveCount(0);await idea.getByRole('button',{name:'View',exact:true}).click();await page.getByRole('dialog',{name:'Venus idea',exact:true}).getByRole('button',{name:'Edit',exact:true}).click();let editor=page.getByRole('dialog',{name:'Edit deal scenario'});const whole=editor.locator('.scenario-whole-discounts');await expect(whole).toHaveAttribute('open','');expect(await whole.locator(':scope > summary > .disclosure-arrow').evaluate(el=>getComputedStyle(el,'::before').content)).toContain('▼');await whole.locator(':scope > summary').click();await expect(whole).not.toHaveAttribute('open','');await whole.locator(':scope > summary').click();await expect(whole.getByLabel('Whole-receipt coupons')).toBeVisible();await expect(editor.getByText('Gillette or Venus $4 off 1',{exact:true})).toHaveCount(0);await editor.getByLabel('Coupons for Venus Razor',{exact:true}).getByText(/^Find \/ Change Coupon/).click();await expect(editor.getByText('Gillette or Venus $4 off 1',{exact:true})).toBeVisible();const available=editor.locator('.coupon-picker article').filter({hasText:'Venus $5 Available'});await expect(editor.getByText(/Clipped coupon limit reached/)).toBeVisible();await available.getByRole('button',{name:'Select coupon',exact:true}).click();await expect(editor.getByText(/Planned coupon · Included in estimate/)).toBeVisible();await expect(editor.locator('.live-deal-summary').getByText('$7.00',{exact:true})).toBeVisible();await editor.getByRole('button',{name:'Save scenario',exact:true}).click();await expect(editor).toHaveCount(0);expect(saved).toBe(true);await expect(idea.getByRole('button',{name:'Edit',exact:true})).toHaveCount(0);await idea.getByRole('button',{name:'View',exact:true}).click();await page.getByRole('dialog',{name:'Venus idea',exact:true}).getByRole('button',{name:'Edit',exact:true}).click();editor=page.getByRole('dialog',{name:'Edit deal scenario'});await expect(editor.getByRole('button',{name:'Remove selection',exact:true})).toHaveCount(0);await editor.getByLabel('Coupons for Venus Razor',{exact:true}).getByText(/^Find \/ Change Coupon/).click();await expect(editor.getByRole('button',{name:'Remove selection',exact:true})).toBeVisible();expect(mutations.some(path=>path.endsWith('/state')||path.endsWith('/redeem'))).toBe(false);expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
 })
})

test.describe('weekly advertisement workflow',()=>{
 test.use({serviceWorkers:'block'})
 test('weekly selector collapses, uses responsive cards, and preserves one source-backed combined offer',async({page},info)=>{
  const data=workspace(),existing=planningRow();existing.plan={...existing.plan,planId:'existing-weekly',name:'Existing Walgreens plan',retailer:'Walgreens',shoppingDate:data.asOf,scenario:null,organization:{stage:'TRIP',alternativeOf:null,checkoutOrder:0,rewardSources:[]}};existing.items=[];data.plans=[existing];const calls:{path:string;body:Record<string,unknown>}[]=[];
  data.deals=Array.from({length:3},(_,i)=>({dealId:`weekly-${i}`,title:`Weekly skincare ${i+1}`,interpretedTerms:'Buy 1 get 1 50% off; spend $45 earn $15 Walgreens Cash',retailer:'Walgreens',validFrom:'2026-10-01',validThrough:'2026-11-30',sourceArtifactIds:['advertisement'],revision:'1',pricing:{type:'ADVERTISEMENT',promotionalPrice:null,advertisement:{fields:{sourceName:'weekly.pdf',page:'18',promotionText:'BOGO 50%',rewardText:'Earn $15 Walgreens Cash'},warnings:[],draftJson:JSON.stringify({items:[{id:`item-${i}`,name:'Skincare',sku:null,regular:'30',sale:null,quantity:'2',priceRule:{type:'BOGO_HALF',bundlePrice:null}}],coupons:[],promotions:[]})}}}));
  await page.route('**/api/**',async route=>{const request=route.request(),path=new URL(request.url()).pathname;if(request.method()!=='GET'){const body=request.postDataJSON() as Record<string,unknown>;calls.push({path,body});if(path==='/api/shopping-plans/existing-weekly/purchases'){existing.items.push({plannedPurchaseId:String(body.plannedPurchaseId),planId:'existing-weekly',productName:String(body.productName),productCode:null,quantity:String(body.quantity),targetUnitPrice:'30',advertisedDealId:String(body.advertisedDealId),clippedCouponIds:[],revision:'1'});await route.fulfill({json:{...body,revision:'1'}});return}}await route.fulfill({json:path==='/api/workspace'?data:[]})});
  await page.goto('/#shopping');await page.getByLabel('API access token').fill('weekly-preview');await page.getByRole('button',{name:'Open my workspace',exact:true}).click();
  await expect(page.getByRole('heading',{name:'Weekly Deals & Promotions',exact:true})).toBeVisible();const mainToggle=page.getByRole('button',{name:'Weekly Deals & Promotions',exact:true});await expect(mainToggle).toHaveAttribute('aria-expanded','false');await mainToggle.click();await expect(mainToggle).toHaveAttribute('aria-expanded','true');await expect(page.locator('.weekly-deals-panel > .weekly-deals-body')).toBeVisible();const library=page.locator('.weekly-deals-panel'),firstCard=library.getByRole('button',{name:'Weekly skincare 1',exact:true}),secondCard=library.getByRole('button',{name:'Weekly skincare 2',exact:true});await firstCard.click();await expect(firstCard).toHaveAttribute('aria-expanded','true');await expect(secondCard).toHaveAttribute('aria-expanded','false');await library.getByRole('checkbox',{name:'Select Weekly skincare 1'}).check();await expect(firstCard).toHaveAttribute('aria-expanded','true');await secondCard.click();await expect(firstCard).toHaveAttribute('aria-expanded','true');await library.getByRole('button',{name:'Edit',exact:true}).first().click();await expect(page.getByRole('dialog',{name:'Edit weekly deal'})).toBeVisible();await page.getByRole('button',{name:'Close dialog'}).click();await expect(firstCard).toHaveAttribute('aria-expanded','true');await library.getByLabel('Search Weekly Deals').fill('skincare 1');await expect(secondCard).toHaveCount(0);await library.getByLabel('Search Weekly Deals').fill('');await expect(firstCard).toHaveAttribute('aria-expanded','true');await page.screenshot({path:info.outputPath('weekly-cards-expanded.png'),fullPage:true});await mainToggle.press('Enter');await expect(page.locator('.weekly-deals-panel > .weekly-deals-body')).toBeHidden();await page.getByText('Plan my shopping trip',{exact:true}).click();const selector=page.locator('.weekly-deals-selector');await expect(selector).not.toHaveAttribute('open','');await selector.locator(':scope > summary').click();await expect(selector).toHaveAttribute('open','');await expect(selector.getByRole('checkbox')).toHaveCount(3);
  await page.setViewportSize({width:1440,height:1100});await page.screenshot({path:info.outputPath('shopping-desktop.png'),fullPage:true});
  for(const [width,columns] of [[1440,3],[800,2],[390,1]]){await page.setViewportSize({width,height:1000});expect(await selector.locator('.weekly-offer-grid').evaluate(el=>getComputedStyle(el).gridTemplateColumns.split(' ').length)).toBe(columns);expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)}
  await selector.getByRole('checkbox',{name:'Select Weekly skincare 1'}).check();await selector.getByRole('button',{name:'Add selected to trip'}).click();await page.getByRole('button',{name:'Confirm add to trip'}).click();await expect(selector.getByText('Already in this trip')).toBeVisible();expect(calls).toHaveLength(1);expect(calls[0].path).toBe('/api/shopping-plans/existing-weekly/purchases');expect(calls[0].body.advertisedDealId).toBe('weekly-0');expect(calls[0].body.sourceItemId).toBe('item-0');await selector.locator(':scope > summary').click();await expect(selector).not.toHaveAttribute('open','');
  await page.screenshot({path:info.outputPath('weekly-deals.png'),fullPage:true});
 })
})

test.describe('shopping follow-up verification',()=>{
 test.use({serviceWorkers:'block'});
 test('dashboard compact filters, account navigation and expiration banner',async({page},info)=>{
  await page.clock.install({time:new Date('2026-11-10T18:00:00Z')});const data=workspace();data.balances=[{provider:'Walgreens Cash',balance:{monthUsd:'10',startUsd:'10',endUsd:'10',endPoints:null}}] as typeof data.balances;data.plans=Array.from({length:19},(_,i)=>{const row=planningRow();row.plan={...row.plan,planId:`p-${i}`,name:`Trip ${i}`};return row});data.expiringRewards=[{id:'due',provider:'ECB',description:'Recorded upcoming reward',amount:'5',expirationDate:'2026-11-11',receiptId:null}];
  await page.route('**/api/**',route=>route.fulfill({json:new URL(route.request().url()).pathname==='/api/workspace'?data:[]}));await page.goto('/#dashboard');await page.getByLabel('API access token').fill('isolated-ui');await page.getByRole('button',{name:'Open my workspace',exact:true}).click();
  const filters=page.locator('.shopping-search-filters');await expect(filters).toContainText('19 of 19 plans');await page.getByLabel('Search shopping plans').fill('Trip 18');await expect(filters).toContainText('1 of 19 plans');await page.getByRole('button',{name:'Clear filters',exact:true}).click();await filters.locator(':scope > summary').click();await expect(page.getByLabel('Search shopping plans')).toBeHidden();await filters.locator(':scope > summary').click();
  const banner=page.getByRole('region',{name:'Reward expirations'});await expect(banner).toContainText('Recorded upcoming reward');await banner.getByRole('button',{name:'Dismiss reward expiration notice'}).click();await expect(banner).toHaveCount(0);const account=page.locator('button.account-preview').first();const provider=data.balances[0].provider;await account.focus();await page.keyboard.press('Enter');await expect(page.getByRole('heading',{name:provider,exact:true})).toBeVisible();await page.goto('/#dashboard');await page.getByRole('heading',{name:'Make every trip count.'}).waitFor();data.expiringRewards=[];await page.getByRole('button',{name:'Refresh workspace',exact:true}).click();await expect(page.getByRole('region',{name:'Reward expirations'})).toHaveCount(0);
  for(const width of [1440,900,390]){await page.setViewportSize({width,height:1000});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);expect(await page.getByLabel('Sort shopping plans').evaluate(el=>el.clientWidth)).toBeGreaterThan(140)}await page.screenshot({path:info.outputPath('dashboard-followup.png'),fullPage:true});
 });
 test('weekly picker, counts, scrollable destinations, new-plan action and boosters',async({page},info)=>{
  const data=workspace();data.plans=Array.from({length:30},(_,i)=>{const row=planningRow();row.plan={...row.plan,planId:`p-${i}`,name:`Walgreens shopping plan ${i} with a long name`,retailer:'Walgreens',shoppingDate:'2026-11-10',scenario:null};row.items=[];return row});data.deals=Array.from({length:6},(_,i)=>({dealId:`00000000-0000-4000-8000-00000000000${i}`,title:`Saved laundry ${i}`,retailer:'Walgreens',validFrom:'2026-01-01',validThrough:'2026-12-31',interpretedTerms:'Entered price $5',sourceArtifactIds:[],revision:'1',pricing:{type:'ADVERTISEMENT',promotionalPrice:null,advertisement:{fields:{sourceName:'Recorded ad'},warnings:[],draftJson:JSON.stringify({items:[{id:`10000000-0000-4000-8000-00000000000${i}`,name:`Laundry ${i}`,regular:'5',sale:null,quantity:'1'}],coupons:[],promotions:[]})}}}));
  data.promotions=[{id:'booster',name:'Spend 35 earn 7',threshold:'35',giftCardValue:'7',start:'2026-01-01',end:'2026-12-31',status:'ACTIVE',terms:'Recorded booster terms',token:'1',planningRules:{kind:'SPEND_BOOSTER',store:'Walgreens'}}] as typeof data.promotions;const writes:string[]=[];
  const savedBodies:Record<string,unknown>[]=[];await page.route('**/api/**',route=>{const path=new URL(route.request().url()).pathname;if(path.endsWith('/advertisement-preview'))return route.fulfill({json:{offers:[{dealId:'nice-draft',title:'Nice! Peanuts, Trail Mix or Snacks',interpretedTerms:'$1.99 with myWalgreens',retailer:'Walgreens',validFrom:'2026-11-08',validThrough:'2026-11-14',fields:{page:'1',sourceName:'Availables.pdf',promotionText:'$1.99 with myWalgreens'},warnings:[],draft:{items:[{id:'nice-line',name:'Nice! Peanuts, Trail Mix or Snacks',regular:null,sale:'1.99',quantity:'1'}],coupons:[],promotions:[]}},{dealId:'doritos-draft',title:'Doritos',interpretedTerms:'2/$6 or $5.49 each with myWalgreens',retailer:'Walgreens',validFrom:'2026-11-08',validThrough:'2026-11-14',fields:{page:'1',sourceName:'Availables.pdf',promotionText:'2/$6 or $5.49 each with myWalgreens'},warnings:[],draft:{items:[{id:'doritos-line',name:'Doritos',regular:'5.49',sale:'5.49',quantity:'2',priceRule:{type:'TWO_FOR',bundlePrice:'6'}}],coupons:[],promotions:[]}}]}});if(path.endsWith('/weekly-preview')){const body=route.request().postDataJSON();return route.fulfill({json:{requirementsMet:false,affordable:false,requiredItems:body.requiredItems,issues:['Jiffy Corn Muffin Mix needs review: its source advertisement was removed.'],steps:[],cash:null,earnings:'0',remaining:'20',notice:'No balances changed'}})}if(path.endsWith('/scenario/preview')){const body=route.request().postDataJSON();return route.fulfill({json:{items:body.scenario.items.map((i:{id:string;name:string})=>({id:i.id,name:i.name,totalPrice:'5',couponsApplied:'0',itemOopBeforeTaxAndFunding:'5',possibleRewards:'0'})),coupons:[],promotions:[],regularTotal:'5',saleTotal:'5',couponTotal:'0',merchandiseAfterCoupons:'5',tax:'0',registerRewardsUsed:'0',walgreensCashUsed:'0',giftCardUsed:'0',ecbUsed:'0',walmartCashUsed:'0',calculatedOop:'5',selectedOop:'5',manualDifference:'0',overFunding:'0',potentialRewards:'0',afterPotentialRewards:'5'}})}if(route.request().method()!=='GET')writes.push(path);if(path==='/api/shopping-plans'&&route.request().method()==='POST'){const body=route.request().postDataJSON();savedBodies.push(body);return route.fulfill({json:{...body,revision:'1'}})}return route.fulfill({json:path==='/api/workspace'?data:[]})});await page.goto('/#shopping');await page.getByLabel('API access token').fill('isolated-ui');await page.getByRole('button',{name:'Open my workspace',exact:true}).click();const library=page.locator('.weekly-deals-panel');await library.getByRole('button',{name:'Weekly Deals & Promotions',exact:true}).click();await expect(library).toContainText('6 matching saved deals');await expect(library.locator('.weekly-offer-card')).toHaveCount(6);
  await library.getByRole('button',{name:'Import weekly advertisement',exact:true}).click();const review=page.getByRole('dialog',{name:'Import Weekly Deals — screenshots or PDF'});await review.getByLabel('Advertisement screenshots or PDF').setInputFiles({name:'Availables.pdf',mimeType:'application/pdf',buffer:Buffer.from('%PDF isolated presentation fixture')});await review.getByRole('button',{name:'Review advertisement',exact:true}).click();await expect(review).toContainText('2 identified offers · 0 selected');await expect(review.locator('.weekly-offer-review')).toHaveCount(2);await expect(review.getByRole('checkbox',{name:'Nice! Peanuts, Trail Mix or Snacks',exact:true})).not.toBeChecked();await expect(review.getByRole('checkbox',{name:'Doritos',exact:true})).not.toBeChecked();await expect(review).toContainText('2/$6 or $5.49 each');await review.getByRole('button',{name:'Cancel import',exact:true}).click();await expect(library.locator('.weekly-offer-card')).toHaveCount(6);
  await page.getByRole('button',{name:'+ Quick deal',exact:true}).click();const editor=page.getByRole('dialog',{name:'Plan a shopping deal'});await editor.getByRole('button',{name:'Choose Existing Weekly Deal',exact:true}).click();const picker=page.getByRole('dialog',{name:'Choose Existing Weekly Deal'});await picker.getByRole('checkbox',{name:'Select Saved laundry 0'}).check();await picker.getByRole('button',{name:'Add selected products',exact:true}).click();await expect(picker).toHaveCount(0);await expect(editor.getByLabel('Product 1',{exact:true})).toHaveValue('Laundry 0');await editor.getByLabel('Quantity 1',{exact:true}).fill('2');await expect(editor.getByLabel('Quantity 1',{exact:true})).toHaveValue('2');await editor.getByLabel('Plan name',{exact:true}).fill('Selected weekly laundry');await expect(editor.getByRole('button',{name:'Save scenario',exact:true})).toBeEnabled();await editor.getByRole('button',{name:'Save scenario',exact:true}).click();await expect(editor).toHaveCount(0);expect(savedBodies).toHaveLength(1);expect((savedBodies[0].scenario as {items:{name:string;quantity:number}[]}).items[0]).toMatchObject({name:'Laundry 0',quantity:2});expect((savedBodies[0].scenario as {source:{advertisedDealIds:string[]}}).source.advertisedDealIds).toContain(data.deals[0].dealId);expect(writes).not.toContain('/api/deals');
  const planner=page.locator('.weekly-planner');await planner.locator(':scope > summary').click();await planner.locator('.weekly-deals-selector > summary').click();await planner.getByRole('checkbox',{name:'Select Saved laundry 1'}).check();await planner.getByRole('button',{name:'Add selected to trip',exact:true}).click();const destination=page.getByRole('dialog',{name:'Add selected deals to trip'});expect(await destination.getByRole('radiogroup').evaluate(el=>el.scrollHeight>el.clientHeight)).toBe(true);await destination.getByRole('button',{name:'Create New Shopping Plan',exact:true}).click();const draft=page.getByRole('dialog',{name:'Plan a shopping deal'});await expect(draft.getByLabel('Product 1',{exact:true})).toHaveValue('Laundry 1');expect(savedBodies).toHaveLength(1);await expect(draft.getByRole('button',{name:'Save scenario',exact:true})).toBeEnabled();await draft.getByRole('button',{name:'Save scenario',exact:true}).click();const savedDraft=page.getByRole('dialog',{name:'Edit deal scenario'});await expect(savedDraft.getByText('Saved plan revision 1.',{exact:false})).toBeVisible();expect(savedBodies).toHaveLength(2);expect((savedBodies[1].scenario as {source:{advertisedDealIds:string[]}}).source.advertisedDealIds).toContain(data.deals[1].dealId);await savedDraft.getByRole('button',{name:'Close scenario',exact:true}).click();
  const boosters=page.locator('.weekly-section').filter({has:page.getByText('Spend Boosters',{exact:true})});await expect(boosters.getByRole('button',{name:'Edit Spend Booster',exact:true})).toBeDisabled();await boosters.getByRole('combobox',{name:'Spend Booster',exact:true}).selectOption('booster');await expect(boosters.getByRole('button',{name:'Edit Spend Booster',exact:true})).toBeEnabled();await expect(page.getByText('Plan around this booster:',{exact:false})).toBeVisible();await boosters.locator(':scope > summary').click();await expect(boosters.getByRole('combobox',{name:'Spend Booster',exact:true})).toBeHidden();await boosters.locator(':scope > summary').click();
  data.plans[0].plan={...data.plans[0].plan,shoppingDate:data.asOf,removedWeeklyDealIds:['removed-source'],scenario:{items:[{id:'43680a3c-08ae-4ff1-b1d7-a8c04321c394',name:'Jiffy Corn Muffin Mix',sku:null,regular:'7.29',sale:'7.29',quantity:'2'}],coupons:[],promotions:[],tax:'0',walgreensCashUsed:'0',registerRewardsUsed:'0',giftCardUsed:'0',manualOop:null,paymentMethod:'CASH',fundingNotes:''}};await page.getByRole('button',{name:'Refresh workspace',exact:true}).click();await planner.getByLabel('Shopping mode').selectOption('SELECTED');await planner.getByLabel('Add a required item').selectOption({label:`Jiffy Corn Muffin Mix · ${data.plans[0].plan.name}`});await planner.getByRole('button',{name:'Suggest my shopping plan',exact:true}).click();await expect(planner.getByText('Jiffy Corn Muffin Mix needs review:',{exact:false})).toBeVisible();await expect(planner.getByText(/Required product 43680/)).toHaveCount(0);await planner.getByRole('button',{name:'Edit Jiffy Corn Muffin Mix',exact:true}).click();const correction=page.getByRole('dialog',{name:'Edit deal scenario'});await expect(correction.getByLabel('Product 1',{exact:true})).toHaveValue('Jiffy Corn Muffin Mix');await expect(correction.getByLabel('Regular price 1',{exact:true})).toHaveValue('7.29');await correction.getByRole('button',{name:'Close scenario',exact:true}).click();
  for(const width of [1440,900,390]){await page.setViewportSize({width,height:1000});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)}await page.screenshot({path:info.outputPath('shopping-followup.png'),fullPage:true});
 });
});
