import { test, expect } from '@playwright/test'
import { workspace, receiptRow } from '../src/api/fixtures.test-support'

test('responsive navigation, installation assets, and private-response caching', async ({ page, context }, info) => {
  // This test uses synthetic network fixtures; the application itself has no demo mode.
  const data = workspace(); let calls = 0
  data.balances.push({ provider: 'Test rewards', balance: { monthUsd: '25.00', startUsd: '25.00', endUsd: '25.00', startPoints: null, endPoints: null } })
  await page.route('**/api/workspace', route => { calls++; return route.fulfill({ status: 200, contentType: 'application/json', headers: { 'Cache-Control': 'no-store' }, body: JSON.stringify(data) }) })
  await page.goto('/')
  await page.getByLabel('API access token').fill('synthetic-browser-token')
  await page.getByRole('button', { name: 'Open my workspace' }).click()
  await expect(page.getByRole('heading', { name: 'Make every trip count.' })).toBeVisible()
  expect(await page.evaluate(() => innerWidth)).toBe(page.viewportSize()!.width)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: info.outputPath('dashboard.png'), fullPage: true })
  const menu = page.getByRole('button', { name: 'Open navigation' }); if (await menu.isVisible()) await menu.click()
  await page.getByRole('link', { name: 'Inventory', exact: true }).click()
  await expect(page.getByText(/authenticated approval workflow/)).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.getByRole('button', { name: 'Refresh workspace' }).click()
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
  await page.getByRole('button', { name: 'Open my workspace' }).click()
  await expect(page.getByRole('heading', { name: 'Make every trip count.' })).toBeVisible()
  expect(await page.evaluate(() => innerWidth)).toBe(page.viewportSize()!.width)
  await page.getByRole('button', { name: 'New shopping plan' }).click()
  await page.getByLabel('Plan name').fill(planName)
  await page.getByRole('dialog').getByRole('button', { name: 'Save', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  const menu = page.getByRole('button', { name: 'Open navigation' }); if (await menu.isVisible()) await menu.click()
  await page.getByRole('link', { name: /^Shopping plans(?: \d+)?$/ }).click()
  const panel = page.locator('section.panel').filter({ has: page.getByRole('heading', { name: planName, exact: true }) })
  await expect(panel).toBeVisible()
  await panel.getByRole('button', { name: 'Add item' }).click()
  await page.getByLabel('Product description').fill('Disposable test soap')
  await page.getByLabel('Quantity', { exact: true }).fill('3')
  await page.getByLabel('Regular unit price').fill('1.25')
  await page.getByRole('dialog').getByRole('button', { name: 'Save', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(panel.locator('.panel-bottom').getByText('$3.75', { exact: true })).toBeVisible()
  await panel.getByRole('button', { name: 'Edit', exact: true }).click()
  await page.getByLabel('Quantity', { exact: true }).fill('2')
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
  await page.getByRole('button', { name: 'Open my workspace' }).click()
  await expect(page.getByRole('heading', { name: 'Make every trip count.' })).toBeVisible()
  expect(await page.evaluate(() => innerWidth)).toBe(page.viewportSize()!.width)
  const menu = page.getByRole('button', { name: 'Open navigation' }); if (await menu.isVisible()) await menu.click()
  await page.getByRole('link', { name: 'Receipts', exact: true }).click()
  await page.getByRole('button', { name: `Open receipt ${process.env.COUPON_BROWSER_REWARD_RECEIPT}` }).click()
  await page.getByRole('button', { name: 'Rewards', exact: true }).click()
  for (const [offer, target] of [['RR test offer', 'PAID', '5', 'USD'], ['Fetch test offer', 'CREDITED', '25', 'points']]) {
    const section = page.getByRole('dialog').locator('section').filter({ has: page.getByText(offer, { exact: true }) })
    await expect(section.getByRole('button', { name: 'Update status' })).toBeEnabled()
    await section.getByRole('button', { name: 'Update status' }).click()
    if (await section.getByLabel('Reward status', { exact: true }).inputValue() !== 'EXPECTED') {
      await section.getByLabel('Reward status', { exact: true }).selectOption('EXPECTED')
      await section.getByRole('button', { name: 'Save reward status' }).click()
      await expect(section.getByRole('form')).toHaveCount(0)
      await expect(section.getByRole('button', { name: 'Update status' })).toBeEnabled()
      await section.getByRole('button', { name: 'Update status' }).click()
    }
    await section.getByLabel('Reward status', { exact: true }).selectOption(target)
    await section.getByRole('button', { name: 'Save reward status' }).click()
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
  await expect(section.getByRole('button', { name: 'Update status' })).toBeEnabled()
  await section.getByRole('button', { name: 'Update status' }).click()
  await section.getByLabel('Reward status', { exact: true }).selectOption('REJECTED')
  await section.getByLabel('Rejection reason (optional)').fill(`Not issued — ${info.project.name}`)
  await section.getByRole('button', { name: 'Save reward status' }).click()
  await expect(section.getByText(/Excluded from earned value/)).toBeVisible()
  await expect(section.getByText(/Expected: 5 USD/)).toBeVisible()
  await expect(section.getByText(new RegExp(`Rejection reason: Not issued — ${info.project.name}`))).toBeVisible()
})

test('create and review a complete receipt through the disposable API', async ({ page }, info) => {
  test.skip(!process.env.COUPON_BROWSER_TEST_TOKEN, 'Requires an explicitly configured disposable API; never run write tests against personal records.')
  const receiptNumber = `web-create-${info.project.name}-${Date.now()}`
  await page.goto('/')
  await page.getByLabel('API access token').fill(process.env.COUPON_BROWSER_TEST_TOKEN!)
  await page.getByRole('button', { name: 'Open my workspace' }).click()
  await expect(page.getByRole('heading', { name: 'Make every trip count.' })).toBeVisible()
  const menu = page.getByRole('button', { name: 'Open navigation' }); if (await menu.isVisible()) await menu.click()
  await page.getByRole('link', { name: 'Receipts', exact: true }).click()
  await page.getByRole('button', { name: 'Add receipt', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: 'Add receipt' })
  await dialog.getByLabel('Store', { exact: true }).fill('Synthetic grocery')
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
  await dialog.getByLabel('Register Rewards used (optional)', { exact: true }).fill('1.00')
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
  await dialog.getByLabel('Reward amount 2 (USD)', { exact: true }).fill('10.00') // expected, never a credit
  const image = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl6hWQAAAAASUVORK5CYII=', 'base64')
  await dialog.getByLabel('Receipt files').setInputFiles({ name: 'synthetic-receipt.png', mimeType: 'image/png', buffer: image })
  await dialog.getByRole('button', { name: 'Review calculated totals', exact: true }).click()
  const review = page.getByRole('dialog', { name: 'Review new receipt' })
  await expect(review.getByText('$20.00', { exact: true })).toBeVisible()
  await expect(review.getByText('$8.50', { exact: true })).toHaveCount(2)
  await expect(review.getByText('$5.00', { exact: true })).toHaveCount(2) // WC used, RR earned
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
  await saved.getByRole('button', { name: 'Close dialog' }).click()
  await expect(page.getByRole('button', { name: `Open receipt ${receiptNumber}`, exact: true })).toBeVisible()
  const response = await page.request.get('/api/workspace', { headers: { Authorization: `Bearer ${process.env.COUPON_BROWSER_TEST_TOKEN}` } })
  expect(response.ok()).toBe(true)
  const data = await response.json(); const row = data.receipts.find((r: { metadata: { receiptNumber: string } }) => r.metadata.receiptNumber === receiptNumber)
  expect(row.totals.calculatedDue).toBe(8.5); expect(row.totals.savings.newlyEarned).toBe(5)
  const moves = data.movements.filter((m: { source: string }) => m.source.includes(row.transactionId))
  expect(moves.filter((m: { provider: string; usd: number }) => m.provider === 'Walgreens Cash').map((m: { usd: number }) => m.usd)).toEqual([-5])
  expect(moves.filter((m: { provider: string }) => m.provider === 'Register Rewards')).toHaveLength(2) // one earning and one separate use
})

test('inline deals and physical coupons preserve a planned item', async ({ page }, info) => {
  test.skip(!process.env.COUPON_BROWSER_TEST_TOKEN, 'Disposable API required.')
  const headers={Authorization:`Bearer ${process.env.COUPON_BROWSER_TEST_TOKEN}`,'Idempotency-Key':crypto.randomUUID()}
  const planId=crypto.randomUUID(),name=`Inline trip ${info.project.name} ${Date.now()}`
  expect((await page.request.post('/api/shopping-plans',{headers,data:{planId,name,retailer:'Synthetic store',shoppingDate:'2026-10-19'}})).status()).toBe(201)
  await page.goto('/');await page.getByLabel('API access token').fill(process.env.COUPON_BROWSER_TEST_TOKEN!);await page.getByRole('button',{name:'Open my workspace'}).click();await expect(page.getByRole('heading',{name:'Make every trip count.'})).toBeVisible()
  const menu=page.getByRole('button',{name:'Open navigation'});if(await menu.isVisible())await menu.click();await page.getByRole('link',{name:/^Shopping plans(?: \d+)?$/}).click()
  const panel=page.locator('.panel').filter({has:page.getByRole('heading',{name,exact:true})});await panel.getByRole('button',{name:'Add item',exact:true}).click()
  const dialog=page.getByRole('dialog');await dialog.getByLabel('Product description',{exact:true}).fill('Shampoo');await dialog.getByLabel('Regular unit price',{exact:false}).fill('3.00');await dialog.getByLabel('Quantity',{exact:true}).fill('2')
  await dialog.getByRole('button',{name:'Add a deal',exact:true}).click();await dialog.getByLabel('Store',{exact:true}).fill('Synthetic store');await dialog.getByLabel('Promotion name').fill(`Shampoo bundle ${info.project.name} ${Date.now()}`);await dialog.getByLabel('Qualifying product description').fill('Shampoo');await dialog.getByLabel('Recorded offer terms').fill('Two for five dollars');await dialog.getByLabel('Promotion type').selectOption('MULTIBUY');await dialog.getByLabel('Price for the full bundle').fill('5.00');await dialog.getByLabel('Units per bundle').fill('2');await dialog.getByLabel('Start date',{exact:true}).fill('2026-10-01');await dialog.getByLabel('Expiration date',{exact:true}).fill('2026-10-31');await dialog.getByRole('button',{name:'Save and link deal'}).click()
  await expect(dialog.getByLabel('Product description',{exact:true})).toHaveValue('Shampoo');await dialog.getByRole('button',{name:'Add coupon',exact:true}).click();await dialog.getByLabel('Coupon description').fill('One dollar on shampoo');await dialog.getByLabel('Issuing manufacturer, store or provider').fill('Synthetic maker');await dialog.getByLabel('Dollar amount',{exact:true}).fill('1.00');await dialog.getByLabel('Number of available copies').fill('2');await dialog.getByLabel('Saved / clipped date').fill('2026-10-01');await dialog.getByLabel('Expiration date',{exact:true}).fill('2026-10-31');await dialog.getByRole('button',{name:'Save coupon',exact:true}).click()
  await expect(dialog.getByLabel('Product description',{exact:true})).toHaveValue('Shampoo');await expect(dialog.getByLabel('Quantity',{exact:true})).toHaveValue('2');await dialog.getByRole('button',{name:'Save',exact:true}).click();await expect(dialog).toHaveCount(0)
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
  await page.goto('/');await page.getByLabel('API access token').fill(process.env.COUPON_BROWSER_TEST_TOKEN!);await page.getByRole('button',{name:'Open my workspace'}).click();await expect(page.getByRole('heading',{name:'Make every trip count.'})).toBeVisible()
  const menu=page.getByRole('button',{name:'Open navigation'});if(await menu.isVisible())await menu.click();await page.getByRole('link',{name:'Receipts',exact:true}).click();await page.getByRole('button',{name:`Open receipt ${number}`,exact:true}).click();const dialog=page.getByRole('dialog');await expect(dialog.getByText('Shampoo: 3 unit(s), 2 receipt line(s)',{exact:true})).toBeVisible();await dialog.getByRole('button',{name:'Rewards',exact:true}).click()
  await dialog.getByRole('button',{name:'Add rebate or reward',exact:true}).click();await dialog.getByLabel('Reward program 1').selectOption('WALGREENS_CASH');await dialog.getByLabel('Reward amount 1 (USD)').fill('5');await dialog.getByText('Optional reward details — products, promotion, valuation and evidence',{exact:true}).click();await dialog.getByLabel('Reward products 1: line 1',{exact:true}).check();await dialog.getByLabel('Reward products 1: line 2',{exact:true}).check();await dialog.getByRole('combobox',{name:'Promotion requirement',exact:true}).selectOption('SPEND');await dialog.getByLabel('Required qualifying spend',{exact:true}).fill('20');await dialog.getByLabel('Offer description 1 (optional)').fill('Spend 20 earn 5');await dialog.getByRole('button',{name:'Review rebate or reward',exact:true}).click();await dialog.getByText('Optional qualification review',{exact:true}).click();await expect(dialog.getByText(/Qualification: MET/)).toBeVisible();await dialog.getByRole('button',{name:'Save rebate or reward',exact:true}).click();await expect(dialog.getByText('Spend 20 earn 5',{exact:true})).toBeVisible();await expect(dialog.getByRole('button',{name:'Add rebate or reward',exact:true})).toBeEnabled()
  await dialog.getByRole('button',{name:'Add rebate or reward',exact:true}).click();await dialog.getByLabel('Provider 1',{exact:true}).fill('P&G Good Everyday');await dialog.getByLabel('Reward amount 1 (points)').fill('100');await dialog.getByText('Optional reward details — products, promotion, valuation and evidence',{exact:true}).click();await dialog.getByLabel('Reward products 1: line 1',{exact:true}).check();await dialog.getByLabel('Reward products 1: line 2',{exact:true}).check();await dialog.getByText('Submission and payout destination (optional)',{exact:true}).click();await dialog.getByRole('combobox',{name:'Payout destination',exact:true}).selectOption('PAYPAL');await dialog.getByRole('button',{name:'Review rebate or reward',exact:true}).click();await dialog.getByRole('button',{name:'Save rebate or reward',exact:true}).click();await expect(dialog.getByText('P&G Good Everyday',{exact:true})).toBeVisible();await dialog.getByRole('button',{name:'History',exact:true}).click();await expect(dialog.locator('ol.history li')).toHaveCount(3)
  const saved=await (await page.request.get(`/api/receipts/${id}`,{headers})).json();expect(saved.contents.receiptTotals.finalTotal).toBe(30);expect(saved.contents.observations.earnings).toHaveLength(2);expect(saved.contents.observations.earnings[1].details.payoutDestination).toBe('PAYPAL');expect(saved.contents.observations.earnings.every((e:{status:string})=>e.status==='EXPECTED')).toBe(true)
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
})


test.describe('receipt fixture workflows', () => {
  test.use({ serviceWorkers: 'block' })

test('receipt search, five-row pages and selected-period savings', async ({ page }, info) => {
  const data = workspace()
  data.receipts = Array.from({ length: 7 }, (_, i) => receiptRow(String(i), '2026-10-19', `9:0${i} AM`))
  data.receiptPeriods = { empty: data.summaries.all, 'month:2026-10': { ...data.summaries.all, totalSaved: '349.72', recordedCashCardPaid: '35.96', newlyEarned: '18.74' } }
  await page.route('**/api/workspace', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify(data) }))
  await page.goto('/')
  await page.getByLabel('API access token').fill('synthetic-browser-token')
  await page.getByRole('button', { name: 'Open my workspace' }).click()
  await expect(page.getByRole('heading', { name: 'Make every trip count.' })).toBeVisible()
  const menu = page.getByRole('button', { name: 'Open navigation' })
  if (await menu.isVisible()) await menu.click()
  await page.getByRole('link', { name: 'Receipts', exact: true }).click()
  await expect(page.getByRole('row')).toHaveCount(6)
  const controls = await Promise.all(['Purchase day', 'Month', 'Sort receipts'].map(label => page.getByLabel(label, { exact: label === 'Purchase day' || label === 'Month' }).boundingBox()))
  for (const box of controls) expect(box!.height).toBe(60)
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
  await expect(page.getByRole('heading', { name: 'No matching receipts' })).toBeVisible()
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
  await page.getByRole('button', { name: 'Open my workspace' }).click()
  await expect(page.getByRole('heading', { name: 'Make every trip count.' })).toBeVisible()
  await page.getByRole('button', { name: `Open receipt ${row.metadata!.receiptNumber}` }).click()
  await page.getByRole('button', { name: 'Edit receipt', exact: true }).click()
  await expect(page.getByLabel('Unit price before listed discounts 1')).toHaveCount(0)
  await page.getByLabel('Product 1', { exact: true }).fill('Neutrogena Fragrance Free Makeup Remover Cleansing Towelettes with a long recorded product description')
  await page.getByRole('button', { name: 'Add discount to product 1' }).click()
  await page.getByLabel('Product 1 discount 1 description').fill('Attached sale promotion')
  await page.getByLabel('Product 1 discount 1 amount').fill('1.58')
  await page.getByRole('button', { name: 'Add receipt-wide discount' }).click()
  await page.getByRole('button', { name: 'Delete receipt-wide discount 1' }).click()
  await page.getByRole('button', { name: 'Add receipt-wide discount' }).click()
  await page.getByLabel('Walgreens Cash Used This Visit').fill('0')
  const dimensions = await Promise.all(['Store', 'Purchase date', 'Printed time', 'Regular price 1', 'Quantity 1', 'Purchase channel', 'Printed-total basis', 'Have you entered all payments shown on this receipt?', 'Product 1 discount 1 description', 'Product 1 discount 1 type', 'Product 1 discount 1 amount'].map(name => page.getByLabel(name, { exact: !['Purchase channel','Printed-total basis','Have you entered all payments shown on this receipt?','Product 1 discount 1 type'].includes(name) }).boundingBox()))
  for (const [index, box] of dimensions.entries()) { if (index === 8) expect(box!.height).toBeGreaterThanOrEqual(60); else expect(box!.height).toBe(60) }
  const fonts = await page.locator('.receipt-editor input, .receipt-editor select, .receipt-editor textarea').evaluateAll(nodes => nodes.map(node => getComputedStyle(node).fontSize))
  expect(fonts.every(font => font === '18px')).toBe(true)
  const textFits = await page.getByLabel('Product 1', { exact: true }).evaluate(node => node.scrollHeight <= node.clientHeight + 1)
  expect(textFits).toBe(true)
  const description = dimensions[8]!, selection = dimensions[9]!
  expect(Math.abs(description.width - selection.width)).toBeLessThanOrEqual(1)
  expect(selection.y).toBeGreaterThan(description.y)
  await page.screenshot({ path: `/private/tmp/coupon-control-sizing-${info.project.name}.png`, fullPage: true })

  await page.getByLabel('Store', { exact: true }).fill('Corrected store')
  await page.getByLabel('Reason for correction').fill('Correct store spelling')
  await page.getByRole('button', { name: 'Review updated totals' }).click()
  await page.getByLabel(/I reviewed the amounts/).check()
  await page.getByRole('button', { name: 'Save receipt changes' }).click()
  await expect(page.getByText('Active · revision 2', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Delete receipt', exact: true }).click()
  await page.getByLabel('Reason', { exact: true }).fill('Duplicate entry')
  await page.getByLabel('I confirm this deletion.').check()
  await page.getByRole('button', { name: 'Confirm delete' }).click()
  await expect(page.getByText('Deleted · revision 3', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Restore receipt', exact: true }).click()
  await page.getByLabel('Reason', { exact: true }).fill('Keep receipt')
  await page.getByLabel('I confirm this restoration.').check()
  await page.getByRole('button', { name: 'Confirm restore' }).click()
  await expect(page.getByText('Active · revision 4', { exact: true })).toBeVisible()
  expect({ edits, deletes, restores }).toEqual({ edits: 1, deletes: 1, restores: 1 })
})

})

test.describe('account detail fixture workflow', () => {
  test.use({ serviceWorkers: 'block' })
  test('account activity and printed receipt controls remain readable', async ({ page }, info) => {
    const data=workspace()
    data.balances=[{provider:'Register Rewards',balance:{monthUsd:'2',startUsd:'2',endUsd:'2',startPoints:null,endPoints:null}}]
    await page.route('**/api/workspace',route=>route.fulfill({json:data}))
    await page.route('**/api/accounts/activity?**',route=>route.fulfill({json:{provider:'Register Rewards',from:data.asOf.slice(0,7)+'-01',to:data.asOf,beginning:{startUsd:'2',startPoints:null,opening:{date:'2026-09-30',sourceNote:'Confirmed opening'}},ending:{endUsd:'2',endPoints:null},earned:'7',spent:'7',earnedPoints:'0',spentPoints:'0',usdActivityComplete:true,activity:[]}}))
    await page.goto('/')
    await page.getByLabel('API access token').fill('synthetic-account-token')
    await page.getByRole('button',{name:'Open my workspace'}).click()
    await expect(page.getByRole('heading',{name:'Make every trip count.'})).toBeVisible()
    const menu=page.getByRole('button',{name:'Open navigation'});if(await menu.isVisible())await menu.click()
    await page.getByRole('link',{name:'Rewards & Accounts',exact:true}).click()
    await page.getByRole('button',{name:'Register Rewards',exact:true}).click()
    await expect(page.getByRole('heading',{name:'Account Balances',exact:true})).toBeVisible()
    await page.getByRole('button',{name:'Set Beginning Balance'}).click()
    await expect(page.getByLabel('Dollar Balance')).toBeVisible()
    expect(await page.getByLabel('Dollar Balance').evaluate(el=>el.getBoundingClientRect().height)).toBeGreaterThanOrEqual(60)
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
    await page.route('**/api/workspace',route=>route.fulfill({json:data}))
    await page.route(`**/api/receipts/${id}`,route=>route.fulfill({json:{transactionId:id,revision:'1',status:'ACTIVE',metadata:row.metadata,evidenceIds:[],rrUses:matched?[reward]:[],contents:{purchaseItems:[],payments:[],appliedCoupons:[],observations:{earnings:[],footerStatus:'PRINTED'},receiptTotals:{taxAmount:'0',finalTotal:'0'}}}}))
    await page.route(`**/api/receipts/${id}/history`,route=>route.fulfill({json:[]}))
    await page.route(`**/api/receipts/${id}/totals`,route=>route.fulfill({json:row.totals}))
    await page.route(`**/api/receipts/${id}/rr-matches`,async route=>{
      if(route.request().method()==='POST'){posts++;expect(route.request().postDataJSON()).toMatchObject({usageEventId:'use',sourceId:'rr-earned',receiptEarning:true});expect(route.request().headers()['if-match-revision']).toBe('1');matched=true;await route.fulfill({status:201,json:{}})}
      else await route.fulfill({json:[{eventId:'use',amount:'5',description:'Lysol RR',matched,options:matched?[]:[{sourceId:'rr-earned',receiptEarning:true,amount:'5',promotion:reward.promotion,sourceReceiptId:'source-receipt',sourceReceiptNumber:'1151-source',earnedDate:'2026-10-04',expires:'2026-10-18'}]}]})
    })
    await page.goto('/')
    await page.getByLabel('API access token').fill('synthetic-rr-token')
    await page.getByRole('button',{name:'Open my workspace'}).click()
    await page.getByRole('button',{name:`Open receipt ${row.metadata!.receiptNumber}`}).click()
    await page.getByRole('button',{name:'Totals',exact:true}).click()
    await expect(page.getByText('Total Paid after rebates').locator('..')).toContainText('$−3.01')
    await page.getByLabel('Unused Register Reward').selectOption('rr-earned')
    await page.getByLabel('Matching note').fill('Matched Lysol printed coupon')
    await page.getByRole('button',{name:'Match RR',exact:true}).click()
    await expect(page.getByText('Matched to its original reward. See View Reward below.')).toBeVisible()
    expect(posts).toBe(1)
    const printed=page.getByText('Printed Savings & Walgreens Cash — Show / Hide'),used=page.getByText('Used Lysol Travel Wipes — Buy 2, Get $5 RR: $5.00')
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
  await page.route('**/api/workspace',r=>r.fulfill({json:data}))
  await page.route(`**/api/receipts/${id}`,r=>r.fulfill({json:{transactionId:id,revision:'1',status:'ACTIVE',metadata:row.metadata,evidenceIds:[],contents:{purchaseItems:[],payments:[],appliedCoupons:[],observations:{earnings:[]},receiptTotals:{taxAmount:'0',finalTotal:'0'}}}}))
  await page.route(`**/api/receipts/${id}/history`,r=>r.fulfill({json:[]}))
  await page.route(`**/api/receipts/${id}/totals`,r=>r.fulfill({json:row.totals}))
  await page.route(`**/api/receipts/${id}/rr-matches`,r=>r.fulfill({json:[{eventId:'use',amount:'2',description:'Palmolive',matched,options:[]}]}))
  await page.route(`**/api/receipts/${id}/rr-matches/unknown-source`,async r=>{posts++;expect(r.request().postDataJSON()).toMatchObject({usageEventId:'use',description:'Palmolive',earnedDate:null,expirationDate:null,confirmRecordedUse:true});matched=true;await r.fulfill({status:201,json:{}})})
  await page.goto('/');await page.getByLabel('API access token').fill('synthetic-unknown-rr');await page.getByRole('button',{name:'Open my workspace'}).click()
  await page.getByRole('button',{name:`Open receipt ${row.metadata!.receiptNumber}`}).click()
  await page.getByRole('button',{name:'Rewards',exact:true}).click()
  await page.getByRole('button',{name:'Add RR without an earning receipt'}).click()
  await expect(page.getByLabel('Earned date (optional)')).toHaveValue('')
  await page.getByLabel('I confirm this RR was used on this receipt; its earning receipt or source may be unknown.').check()
  await page.getByRole('button',{name:'Save RR and match'}).click()
  await expect(page.getByText('Matched to its original reward. See View Reward below.')).toBeVisible();expect(posts).toBe(1)
 })
})

test.describe('Rewards standalone inventory fixture',()=>{
 test.use({serviceWorkers:'block'})
 test('adds a reward at the bottom of Receipts and matches it later without an earning receipt',async({page})=>{
  const data=workspace(),row=receiptRow('standalone-spent'),id=row.transactionId;data.receipts=[row]
  let rewardId='',matched=false,created=0,links=0
  await page.route('**/api/workspace',r=>r.fulfill({json:data}))
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
  await page.goto('/');await page.getByLabel('API access token').fill('synthetic-inventory-token');await page.getByRole('button',{name:'Open my workspace'}).click()
  await expect(page.getByRole('heading',{name:'Make every trip count.'})).toBeVisible()
  async function navigate(name:string){const menu=page.getByRole('button',{name:'Open navigation'});if(await menu.isVisible())await menu.click();await page.getByRole('link',{name,exact:true}).click()}
  await navigate('Receipts');await page.getByRole('button',{name:'Add reward without a receipt'}).click()
  await page.getByLabel('Reward description').fill('Palmolive RR');await page.getByLabel('Reward amount').fill('2')
  await page.getByLabel('I confirm this is an unused RR coupon available to use.').check();await page.getByRole('button',{name:'Save reward',exact:true}).click()
  await expect(page.getByRole('cell',{name:'Palmolive RR',exact:true})).toBeVisible()
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
  await navigate('Receipts');await page.getByRole('button',{name:`Open receipt ${row.metadata!.receiptNumber}`}).click();await page.getByRole('button',{name:'Rewards',exact:true}).click()
  await page.getByLabel('Unused Register Reward').selectOption(rewardId);await page.getByLabel('Matching note').fill('Matched saved coupon')
  await page.getByLabel('I confirm this RR was available for this purchase although its earning date or time is unknown.').check();await page.getByRole('button',{name:'Match RR',exact:true}).click()
  await expect(page.getByText('Matched to its original reward. See View Reward below.')).toBeVisible();expect(created).toBe(1);expect(links).toBe(1)
 })
})

test('dashboard cards collapse to their headers independently',async({page})=>{
 const data=workspace();data.receipts=Array.from({length:5},(_,i)=>receiptRow(String(i)))
 data.balances=Array.from({length:4},(_,i)=>({provider:`Test account ${i}`,balance:{monthUsd:'10',startUsd:'10',endUsd:'10',startPoints:null,endPoints:null}}))
 await page.route('**/api/workspace',route=>route.fulfill({contentType:'application/json',body:JSON.stringify(data)}))
 await page.goto('/');await page.getByLabel('API access token').fill('synthetic');await page.getByRole('button',{name:'Open my workspace'}).click()
 for(const title of ['Your next shopping trip','Rewards to keep track of','Recent receipts']){
  const card=page.locator('.dashboard-card').filter({has:page.getByRole('heading',{name:title,exact:true})})
  await expect(card).toBeVisible();const expanded=(await card.boundingBox())!.height
  await card.getByRole('button',{name:'Collapse',exact:true}).click()
  await expect(card.getByRole('button',{name:'Expand',exact:true})).toHaveAttribute('aria-expanded','false')
  const collapsed=(await card.boundingBox())!.height,header=(await card.locator('.panel-head').boundingBox())!.height
  expect(collapsed).toBeLessThan(expanded-40);expect(Math.abs(collapsed-header)).toBeLessThanOrEqual(3)
  await card.getByRole('button',{name:'Expand',exact:true}).click()
  expect((await card.boundingBox())!.height).toBeGreaterThan(collapsed+40)
 }
})
