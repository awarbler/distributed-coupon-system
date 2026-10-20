import { integer } from '../../api/format'
import type { Earning } from '../../types/platform'
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

export type RewardDraft = { attachment: {evidenceId:string;mediaType:string;contentBase64:string}|null; id: string; kind: string; provider: string; offer: string; form: string; amount: string; status: string; quantities: Record<string,string>; confirmed: string; unit: string; confirm: boolean; valuation: string; basis: string; expiry: string; reason: string; requirementType: string; threshold: string; spendBasis: string; payout: string; submitted: string; reference: string; confirmationReference: string }
export const newReward = (): RewardDraft => ({ attachment:null,id:crypto.randomUUID(),kind:'THIRD_PARTY_REBATE',provider:'',offer:'',form:'POINTS',amount:'',status:'EXPECTED',quantities:{},confirmed:'',unit:'',confirm:false,valuation:'',basis:'',expiry:'',reason:'',requirementType:'',threshold:'',spendBasis:'SALE_BEFORE_COUPONS',payout:'',submitted:'',reference:'',confirmationReference:'' })
export function rewardBody(r:RewardDraft,evidenceIds:string[]=r.attachment?[r.attachment.evidenceId]:[]){return {earningId:r.id,kind:r.kind,provider:r.provider||null,offer:r.offer||null,form:r.form,amount:r.amount,status:r.status,lineItemIds:Object.keys(r.quantities),qualifyingQuantities:Object.fromEntries(Object.entries(r.quantities).map(([id,q])=>[id,integer(q)])),...(r.status==='CREDITED'||r.status==='PAID'?{confirmedAmount:r.confirmed,unit:r.unit,confirmAmountAndUnit:r.confirm}:{}),valuation:r.valuation||null,valuationBasis:r.basis||null,expirationDate:r.expiry||null,rejectionReason:r.status==='REJECTED'?r.reason||null:null,requirement:r.requirementType?{type:r.requirementType,amount:r.threshold,spendBasis:r.requirementType==='SPEND'?r.spendBasis:'UNKNOWN'}:null,payoutDestination:r.payout||null,submittedOn:r.submitted||null,submissionReference:r.reference||null,confirmationReference:r.confirmationReference||null,evidenceIds}}

