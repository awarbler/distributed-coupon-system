import type { StandaloneRR, Workspace } from '../../types/platform'
/** Shared projection of original reward records. Planned selections never create inventory. */
export function availableRegisterRewards(data:Workspace,standalone:StandaloneRR[],day:string){
 const used=new Set((data.rrMatches as {sourceId:string}[]).map(r=>r.sourceId))
 const unavailable=new Set((data.unavailableRewardSources??[]).filter(r=>!day||r.date<=day).map(r=>r.sourceId))
 const receipt=(data.receiptEarnings??[]).filter(r=>r.receiptStatus==='ACTIVE'&&r.earning.kind==='REGISTER_REWARD'&&r.earning.status==='PAID').map(r=>({id:r.earning.earningId,name:r.earning.offerReference??'Register Reward — name not recorded',amount:r.earning.confirmed?.amount??null,expires:r.earning.expirationDate,earned:data.receipts.find(v=>v.transactionId===r.receiptId)?.date,receiptId:r.receiptId,registerRewardType:r.earning.details?.registerRewardType??'UNKNOWN',originProduct:r.earning.details?.originProduct,originBrand:r.earning.details?.originBrand,originProgram:r.earning.details?.originProgram,originPromotion:r.earning.details?.originPromotion}))
 const external=standalone.filter(r=>r.status==='UNUSED').map(r=>({id:r.rewardId,name:r.description,amount:r.amount,expires:r.expirationDate,earned:r.earnedDate,receiptId:null,registerRewardType:r.registerRewardType??'UNKNOWN',originProduct:r.originProduct,originBrand:r.originBrand,originProgram:r.originProgram,originPromotion:r.originPromotion}))
 const seen=new Set<string>()
 return [...receipt,...external].filter(r=>!seen.has(r.id)&&!!seen.add(r.id)&&!used.has(r.id)&&!unavailable.has(r.id)&&r.amount!=null&&(!r.expires||!day||r.expires>=day)&&(!r.earned||!day||r.earned<=day))
}
export function registerRewardTotal(rewards:ReturnType<typeof availableRegisterRewards>){
 const total=rewards.reduce((sum,r)=>{const [a,b='']=(r.amount??'0').split('.');return sum+BigInt(a)*100n+BigInt(b.padEnd(2,'0').slice(0,2))},0n)
 return `${total/100n}.${(total%100n).toString().padStart(2,'0')}`
}
