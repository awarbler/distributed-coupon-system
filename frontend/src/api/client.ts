import { parse } from 'lossless-json'
import { integer } from './format'
import type {WeeklyOffer,PlanScenario,Workspace,Receipt,History,Totals,PlanCheckout,} from '../types/platform'

export class ApiError extends Error {
  readonly status: number
  constructor(status: number, message: string) { super(message); this.status = status }
}
export function decode(text: string): unknown { return parse(text, null, { parseNumber: (lexeme) => lexeme }) }
export class ApiClient {
  private readonly token: string
  constructor(token: string) { this.token = token }
  private async request(path: string, init: RequestInit = {}): Promise<Response> {
    let response: Response
    try {
      response = await fetch(`${import.meta.env.VITE_API_BASE || '/api'}${path}`, {
        ...init, cache: 'no-store', credentials: 'omit',
        headers: { ...init.headers, Authorization: `Bearer ${this.token}` },
      })
    } catch (error) {
      if (error instanceof Error && error.name === 'AbortError') throw error
      throw new ApiError(0, 'Cannot reach the API. If you submitted a change, its outcome may be unknown. Retry the same request or refresh to reconcile.')
    }
    if (!response.ok) {
      let message = `Request failed (${response.status}).`
      try { const body = decode(await response.text()) as { error?: string }; if (body.error) message = body.error } catch { /* Non-JSON proxy error. */ }
      if (response.status === 404 && (path === '/reward-instruments' || path.startsWith('/reward-instruments/'))) message = 'The connected API does not support individual accounts and gift cards yet. Start the updated Java API, check the frontend API target, then reload instrument balances.'
      if (response.status === 404 && path === '/workspace') message = 'This API does not provide workspace views yet. Start the updated Java backend, then reconnect.'
      throw new ApiError(response.status, message)
    }
    return response
  }
  private async get<T>(path: string, signal?: AbortSignal): Promise<T> { return decode(await (await this.request(path, { signal })).text()) as T }
  async rewardInstruments(signal?:AbortSignal){const result=await this.get<{instruments:import("../types/platform").RewardInstrument[];cardSummary?:{recordedOpenings:string;knownRemaining:string;recordedRedemptions:string;unknownBalanceCount:number;activeCards:number}}>("/reward-instruments",signal);if(!result||!Array.isArray(result.instruments))throw new ApiError(502,"The API returned an incompatible instrument response.");return result}
  payout(id:string){return this.get<{revision:string;payout:{status:string;provider?:string;requestedDate?:string;completedDate?:string|null;sourceUsdValue?:string;destinationUsdValue?:string;originalUnitsUsed?:string|null;reference?:string;destination?:string};history?:{status:string;requestedDate:string;sourceUsdValue:string;destinationUsdValue:string}[]}>(`/accounts/payouts/${encodeURIComponent(id)}`)}
  memberReward(id:string){return this.get<{amount:string;points:string|null;provider:string;description?:string;date?:string;time?:string|null;expirationDate?:string|null;details?:Record<string,string>;recordedAt?:string;token?:string;history?:{eventId:string;usdChange:string|null;unitChange:string|null;reason:string;recordedBy:string;recordedAt:string}[]}>(`/accounts/member-rewards/${encodeURIComponent(id)}`)}
  async workspace(signal?: AbortSignal) {
    const value = await this.get<Workspace>('/workspace', signal)
    if (!value || typeof value.canWrite !== 'boolean' || !value.summaries ||
      !['receipts', 'plans', 'coupons', 'deals', 'rebates', 'inventory', 'balances', 'reportedBalances', 'movements', 'promotions'].every(key => Array.isArray(value[key as keyof Workspace])))
      throw new ApiError(502, 'The server returned an incompatible workspace contract. Start the updated Java API.')
    return value
  }
  checkout(
  planId: string,
  signal?: AbortSignal
): Promise<PlanCheckout> {
  return this.get<PlanCheckout>(
    `/shopping-plans/${encodeURIComponent(planId)}/checkout`,
    signal
  )
}

saveCheckout(
  planId: string,
  values: Record<string, string | null>,
  revision: string,
  key: string
): Promise<PlanCheckout> {
  return this.mutate(
    `/shopping-plans/${encodeURIComponent(planId)}/checkout`,
    'PUT',
    values,
    key,
    revision
  ) as Promise<PlanCheckout>
}
  workspaceAt(asOf: string, signal?: AbortSignal) { return this.get<Workspace>(`/workspace?asOf=${encodeURIComponent(asOf)}`, signal) }
  account(provider: string, from: string, to: string, signal?: AbortSignal) { return this.get<import('../types/platform').AccountReport>(`/accounts/activity?provider=${encodeURIComponent(provider)}&from=${from}&to=${to}`, signal) }
  async standaloneRewards(signal?: AbortSignal) { const value = await this.get<import('../types/platform').StandaloneRR[]>('/rewards/register-rewards',signal); if(!Array.isArray(value))throw new ApiError(502,'Restart the updated API to load rewards without receipts.'); return value }
  receiptReport(metric: string, from: string, to: string, search: string, sort: string, signal?: AbortSignal) { return this.get<import('../types/platform').ReceiptReport>(`/receipts/report?${new URLSearchParams({metric,from,to,search,sort})}`,signal) }
  receiptSummary(from: string, to: string, signal?: AbortSignal) { return this.get<import('../types/platform').Savings>(`/receipts/summary?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`, signal) }
  rrMatches(id: string, signal?: AbortSignal) { return this.get<import('../types/platform').RRMatchUse[]>(`/receipts/${encodeURIComponent(id)}/rr-matches`,signal) }
  receipt(id: string, signal?: AbortSignal) { return this.get<Receipt>(`/receipts/${encodeURIComponent(id)}`, signal) }
  history(id: string, signal?: AbortSignal) { return this.get<History[]>(`/receipts/${encodeURIComponent(id)}/history`, signal) }
  totals(id: string, signal?: AbortSignal) { return this.get<Totals>(`/receipts/${encodeURIComponent(id)}/totals`, signal) }
  async receiptDraft(id:string):Promise<{draftId:string;revision:string;payload:string}>{return decode(await (await this.request(`/receipt-drafts/${encodeURIComponent(id)}`)).text()) as {draftId:string;revision:string;payload:string}}
  async evidence(receipt: string, id: string) { return (await this.request(`/receipts/${encodeURIComponent(receipt)}/evidence/${encodeURIComponent(id)}`)).blob() }
  async previewScenario(scenario:unknown,signal?:AbortSignal,context?:{retailer?:string|null;day?:string|null}) {
    const response=await this.request('/shopping-plans/scenario/preview',{method:'POST',signal,headers:{'Content-Type':'application/json'},body:JSON.stringify({scenario,...context})})
    return decode(await response.text()) as import('../types/platform').ScenarioEstimate
  }
  async weeklySource(deal:string,source:string){return (await this.request(`/deals/${encodeURIComponent(deal)}/sources/${encodeURIComponent(source)}`)).blob()}
  async previewAdvertisementFiles(files:File[],options:Record<string,string>,signal?:AbortSignal){
    const uploads=await Promise.all(files.map(async file=>({name:file.name,mediaType:file.type||(/\.pdf$/i.test(file.name)?'application/pdf':/\.png$/i.test(file.name)?'image/png':'image/jpeg'),contentBase64:await new Promise<string>((resolve,reject)=>{const reader=new FileReader();reader.onerror=()=>reject(new Error('Could not read screenshot.'));reader.onload=()=>resolve(String(reader.result).split(',')[1]);reader.readAsDataURL(file)})})))
    return this.mutate('/deals/import-preview','POST',{files:uploads,...Object.fromEntries(Object.entries(options).map(([key,value])=>[key,value||null]))},crypto.randomUUID(),undefined,signal) as Promise<{offers:import('../types/platform').WeeklyOffer[];sourcePreviewToken:string}>
  }
  async previewAdvertisement(file:File,options:Record<string,string>,signal?:AbortSignal) {
    const query=new URLSearchParams({...options,name:file.name})
    const response=await this.request(`/deals/advertisement-preview?${query}`,{method:'POST',signal,headers:{'Content-Type':file.type||(/\.pdf$/i.test(file.name)?'application/pdf':/\.png$/i.test(file.name)?'image/png':'image/jpeg')},body:file})
    return decode(await response.text()) as {offers:import('../types/platform').WeeklyOffer[];sourcePreviewToken:string}
  }
  async mutate(path: string, method: 'POST' | 'PUT' | 'DELETE', body: unknown, key: string, revision?: string, signal?: AbortSignal): Promise<unknown> {
    const response = await this.request(path, { method, signal, body: JSON.stringify(body), headers: {
      'Content-Type': 'application/json', 'Idempotency-Key': key, ...(revision ? { 'If-Match-Revision': revision } : {}),
    } })
    return decode(await response.text())
  }
}

// Normalize optional deal inputs without performing financial arithmetic.
export const zeroScenarioAmount=(amount:string)=>/^(?:0+(?:[.]0*)?|[.]0+)$/.test(amount.trim())
export const activeScenarioCoupons=(coupons:PlanScenario['coupons'])=>coupons.filter(c=>(c.description.trim()||c.amount.trim())&&(c.pendingVerification||!zeroScenarioAmount(c.amount)))
export function scenarioRequest(value:PlanScenario) {
 const decimal=/^(?:[0-9]+(?:[.][0-9]*)?|[.][0-9]+)$/
 const moneyField=(amount:string,label:string)=>{if(!decimal.test(amount.trim()))throw new Error(`${label}: enter a valid amount.`);return amount.trim()}
 return {...value,paymentMethod:value.payments?.find(p=>['CASH','CARD'].includes(p.type))?.type??value.paymentMethod,items:value.items.map((x,i)=>{if(!x.name.trim())throw new Error(`Product ${i+1}: enter its description.`);if(!x.regular&&!x.sale)throw new Error(`${x.name || `Product ${i+1}`}: regular or sale unit price is missing.`);return {...x,priceRule:x.priceRule?.type==='BUY_GET'?{...x.priceRule,buyQuantity:integer(String(x.priceRule.buyQuantity??'')),freeQuantity:integer(String(x.priceRule.freeQuantity??''))}:x.priceRule,sku:x.sku||null,sale:x.sale?moneyField(x.sale,`Product ${i+1} sale price`):null,regular:x.regular?moneyField(x.regular,`Product ${i+1} regular price`):null,memberRewardRate:x.memberRewardRate==null?null:integer(String(x.memberRewardRate),0),quantity:integer(String(x.quantity))}}),coupons:activeScenarioCoupons(value.coupons).map((c,i)=>{if(!c.description.trim())throw new Error(`Coupon ${i+1}: enter its description or clear the amount to leave it unused.`);return {...c,amount:moneyField(c.amount,`Coupon ${i+1} amount`),requiredQuantity:integer(String(c.requiredQuantity))}}),promotions:value.promotions.filter(p=>(p.description.trim()||p.rewardAmount.trim())&&!zeroScenarioAmount(p.rewardAmount)).map((p,i)=>{if(!p.description.trim())throw new Error(`Rebate ${i+1}: enter its description or clear the amount to leave it unused.`);return {...p,rewardAmount:moneyField(p.rewardAmount,`Rebate ${i+1} amount`),requiredQuantity:integer(String(p.requiredQuantity).trim()||'0',0),requiredSpend:moneyField(p.requiredSpend.trim()||'0',`Rebate ${i+1} required spend`)}}),payments:value.payments?.filter(p=>p.amount.trim()).map((p,i)=>({...p,amount:moneyField(p.amount,`Payment ${i+1} amount`)})),tax:value.taxRate==null?moneyField(value.tax,'Tax amount'):value.tax,taxRate:value.taxRate==null?null:moneyField(value.taxRate,'Tax rate'),manualOop:value.manualOop||null}
}

// Adapt a saved advertisement into the existing scenario contract; no financial calculation.
export function weeklyDealScenario(offer:WeeklyOffer):PlanScenario {
 if(!offer.draft.items.length)throw new Error(`${offer.title}: enter products and prices before adding to your trip.`)
 const missing:string[]=[]
 for(const [index,item] of offer.draft.items.entries()){
  const label=item.name||`Product ${index+1}`
  if(!item.name?.trim())missing.push(`Product ${index+1}: product name`)
  if(!item.regular&&!item.sale)missing.push(`${label}: regular or sale unit price`)
  if(!Number.isInteger(Number(item.quantity))||Number(item.quantity)<1)missing.push(`${label}: positive product quantity`)
  if(item.priceRule?.type==='BUY_GET'&&(!item.priceRule.buyQuantity||!item.priceRule.freeQuantity))missing.push(`${label}: buy and free promotion quantities`)
 }
 for(const [index,coupon] of offer.draft.coupons.entries())if(!coupon.amount?.trim())missing.push(`Coupon ${index+1}: discount amount`)
 for(const [index,promotion] of offer.draft.promotions.entries()){
  if(!promotion.rewardType)missing.push(`Reward ${index+1}: reward type`)
  if(!promotion.rewardAmount?.trim())missing.push(`Reward ${index+1}: reward amount`)
  if(!promotion.spendBasis)missing.push(`Reward ${index+1}: spend basis`)
 }
 if(missing.length)throw new Error(`${offer.title}: missing ${missing.join('; ')}. Corrections can be saved before calculation.`)
 if(!offer.validFrom||!offer.validThrough)throw new Error(`${offer.title}: record the advertisement dates first.`)
 return {...offer.draft,tax:'0',walgreensCashUsed:'0',registerRewardsUsed:'0',giftCardUsed:'0',manualOop:null,paymentMethod:'CASH',fundingNotes:'',payments:[],source:{text:offer.interpretedTerms,mediaType:null,contentBase64:null,advertisedDealIds:[offer.dealId]}}
}

/** Copy selected advertisements into an unsaved existing scenario, preserving source links. */
export function addWeeklyOffers(scenario:PlanScenario,offers:WeeklyOffer[]):PlanScenario {
 const result=structuredClone(scenario),sources=new Set(result.source?.advertisedDealIds??[])
 for(const offer of offers){if(sources.has(offer.dealId))continue
  const draft=weeklyDealScenario(offer),ids=new Map(draft.items.map(item=>[item.id,crypto.randomUUID()]))
  result.items.push(...draft.items.map(item=>({...item,id:ids.get(item.id)!,priceRule:item.priceRule?{...item.priceRule,lineIds:item.priceRule.lineIds?.map(id=>ids.get(id)!)}:item.priceRule})))
  result.coupons.push(...draft.coupons.map(c=>({...c,id:crypto.randomUUID(),lineIds:c.lineIds.map(id=>ids.get(id)!)})))
  result.promotions.push(...draft.promotions.map(p=>({...p,id:crypto.randomUUID(),lineIds:p.lineIds.map(id=>ids.get(id)!)})))
  sources.add(offer.dealId)
 }
 result.source={text:[result.source?.text,...offers.map(o=>o.interpretedTerms)].filter(Boolean).join('\n\n'),mediaType:null,contentBase64:null,advertisedDealIds:[...sources]}
 return result
}
