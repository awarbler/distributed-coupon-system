import { integer } from '../../api/format'
import type { Earning, Receipt } from '../../types/platform'
export function rewardStatusLabel(e: Pick<Earning, 'kind' | 'status'>): string {
  switch (e.status) {
    case 'EXPECTED': return 'Expected'
    case 'REJECTED': return 'Rejected'
    case 'CREDITED': return e.kind === 'REGISTER_REWARD' ? 'Added to program account (legacy RR status)' : 'Added to program account'
    case 'PAID': return e.kind === 'REGISTER_REWARD' ? 'Printed at register' : 'Received / delivered (legacy)'
    case 'APPROVED': return 'Approved (legacy; not credited)'
    case 'SUBMITTED': return 'Submitted (legacy)'
    case 'PENDING': return 'Pending (legacy)'
    default: return e.status
  }
}
export function rewardChoices(e: Pick<Earning, 'kind'>) {
  return [{ value: 'EXPECTED', text: 'Expected' }, e.kind === 'REGISTER_REWARD' ? { value: 'PAID', text: 'Printed at register' } : { value: 'CREDITED', text: 'Added to program account' }, { value: 'REJECTED', text: 'Rejected' }]
}

export type RewardDraft = {originalReceiptId?:string;originalEarningId?:string; attachment: {evidenceId:string;mediaType:string;contentBase64:string}|null; id: string; kind: string; provider: string; offer: string; form: string; amount: string; status: string; quantities: Record<string,string>; confirmed: string; unit: string; confirm: boolean; valuation: string; basis: string; expiry: string; reason: string; requirementType: string; threshold: string; spendBasis: string; payout: string; submitted: string; reference: string; confirmationReference: string }
export const newReward = (): RewardDraft => ({ attachment:null,id:crypto.randomUUID(),kind:'THIRD_PARTY_REBATE',provider:'',offer:'',form:'POINTS',amount:'',status:'CREDITED',quantities:{},confirmed:'',unit:'',confirm:false,valuation:'',basis:'',expiry:'',reason:'',requirementType:'',threshold:'',spendBasis:'SALE_BEFORE_COUPONS',payout:'',submitted:'',reference:'',confirmationReference:'' })
export function requirementBody(r: RewardDraft) {
  if (!r.requirementType) return null
  const categories: Record<string, string> = { PURCHASE: 'Purchase', RECEIPT_SCAN: 'ReceiptScan', RECEIPT: 'Receipt', OTHER: 'Other' }
  const category = categories[r.requirementType]
  return category
    ? { type: null, amount: null, category, spendBasis: 'UNKNOWN' }
    : { type: r.requirementType, amount: r.threshold, category: null, spendBasis: r.requirementType === 'SPEND' ? r.spendBasis : 'UNKNOWN' }
}
export function rewardBody(r:RewardDraft,evidenceIds:string[]=r.attachment?[r.attachment.evidenceId]:[]){return {originalReceiptId:r.originalReceiptId||undefined,originalEarningId:r.originalEarningId||undefined,earningId:r.id,kind:r.kind,provider:r.provider||null,offer:r.offer||null,form:r.form,amount:r.amount,status:r.status,lineItemIds:Object.keys(r.quantities),qualifyingQuantities:Object.fromEntries(Object.entries(r.quantities).map(([id,q])=>[id,integer(q)])),...(r.status==='CREDITED'||r.status==='PAID'?{confirmedAmount:r.amount,unit:r.form==='POINTS'?'points':'USD',confirmAmountAndUnit:true}:{}),valuation:r.form==='POINTS'&&['fetch','pg','pgbrand','pggoodeveryday','pgbrandsaver'].includes(r.provider.toLowerCase().replace(/[^a-z0-9]/g,''))?null:r.valuation||null,valuationBasis:r.form==='POINTS'&&['fetch','pg','pgbrand','pggoodeveryday','pgbrandsaver'].includes(r.provider.toLowerCase().replace(/[^a-z0-9]/g,''))?null:r.basis||null,expirationDate:r.expiry||null,rejectionReason:r.status==='REJECTED'?r.reason||null:null,requirement:requirementBody(r),payoutDestination:r.payout||null,submittedOn:r.submitted||null,submissionReference:r.reference||null,confirmationReference:r.confirmationReference||null,evidenceIds}}


export const footerLabels = { myWalgreensSavings: 'myWalgreens Savings', storeCouponSavings: 'Store Coupon Savings', manufacturerCouponSavings: 'Mfg Coupon Savings', totalSavings: 'Printed Total Savings', savingsValuePercent: 'Savings Value (%)', wcOpening: 'Opening Balance', wcEarned: 'Earned This Visit', wcUsed: 'Used This Visit', wcClosing: 'Closing Balance' }
export type FooterDraft = Record<keyof typeof footerLabels, string> & { status: string;wcAvailable?:string }
export function footerDraft(o?: Receipt['contents']['observations']): FooterDraft {
  const categories = o?.savings ?? []
  const find = (name: string) => categories.find(c => c.printedLabel.toLowerCase() === name.toLowerCase())?.reportedAmount ?? ''
  return { wcAvailable:o?.walgreensCash?.availableBalance??'', status: o?.footerStatus ?? 'UNKNOWN', myWalgreensSavings: find('myWalgreens Savings'), storeCouponSavings: find('Store coupon savings'), manufacturerCouponSavings: find('Mfg coupon savings'), totalSavings: find('Your total Savings'), savingsValuePercent: o?.receiptSavingValuePercent ?? '', wcOpening: o?.walgreensCash?.startingBalance ?? '', wcEarned: o?.walgreensCash?.earned ?? '', wcUsed: o?.walgreensCash?.used ?? '', wcClosing: o?.walgreensCash?.endingBalance ?? '' }
}
export const footerBody = (draft: FooterDraft) => Object.fromEntries(Object.entries(draft).map(([key,value]) => [key,value.trim() || null]))

export interface MemberEstimateInput {
 items:{lineItemId:string;description:string;sku:string|null;regularUnitPrice:string|null;unitPrice:string;quantity:number}[]
 discounts:{applicationId:string;label:string;amount:string;lineItemIds:string[];wholeReceipt?:boolean;kind?:string;requiredQuantity?:number|null}[]
 payments:{paymentId:string;type:string;amount:string;notes?:string|null;accountName?:string|null}[]
 rrUses:{eventId:string;amount:string;recordedDescription:string}[]
}
/** Uses the existing Java what-if calculation, never creates an account credit. */
export async function estimateReceiptMemberReward(api:import('../../api/client').ApiClient,retailer:string,input:MemberEstimateInput):Promise<string>{
 const scenario={items:input.items.map(l=>({id:l.lineItemId,name:l.description,sku:l.sku,regular:l.regularUnitPrice||l.unitPrice,sale:l.unitPrice||null,quantity:l.quantity})),coupons:input.discounts.map(d=>({id:d.applicationId,description:d.label,amount:d.amount,requiredQuantity:d.requiredQuantity??1,lineIds:d.lineItemIds,wholeReceipt:d.wholeReceipt??!d.lineItemIds.length,savedCouponId:null,kind:['MANUFACTURER','SALE_PROMOTION','STORE_COUPON'].includes(d.kind??'')?d.kind:'STORE_COUPON'})),promotions:[],tax:'0',walgreensCashUsed:'0',registerRewardsUsed:'0',giftCardUsed:'0',manualOop:null,paymentMethod:'CASH',fundingNotes:'',payments:[...input.payments.map(p=>({id:p.paymentId,type:p.accountName==='ECB'?'ECB':p.accountName==='Walmart Cash'?'WALMART_CASH':['WALGREENS_CASH','GIFT_CARD','CASH','CARD'].includes(p.type)?p.type:'CARD',amount:p.amount,notes:p.notes??''})),...input.rrUses.map(p=>({id:p.eventId,type:'REGISTER_REWARD',amount:p.amount,notes:p.recordedDescription}))]}
 const result=await api.mutate('/shopping-plans/scenario/preview','POST',{retailer,scenario},crypto.randomUUID()) as {memberRewardEstimate?:string};if(result.memberRewardEstimate==null)throw Error('The API did not return a member estimate. Enter the printed amount manually.');return result.memberRewardEstimate
}
