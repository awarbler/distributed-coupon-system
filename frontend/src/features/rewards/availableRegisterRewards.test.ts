import { expect,it } from 'vitest'
import { workspace } from '../../api/fixtures.test-support'
import { availableRegisterRewards,registerRewardTotal } from './availableRegisterRewards'
import type { StandaloneRR } from '../../types/platform'
it('keeps six distinct physical rewards totaling $34, including two separate $6 rewards',()=>{
 const data=workspace();data.receiptEarnings=[];data.rrMatches=[];data.unavailableRewardSources=[]
 const rows:StandaloneRR[]=['5','10','6','6','4','3'].map((amount,i)=>({rewardId:String(i),description:i===3?'Name not recorded':'Recorded reward',amount,status:'UNUSED',earnedDate:'2026-11-01',expirationDate:null}))
 const rewards=availableRegisterRewards(data,[...rows,rows[0]],'2026-11-06');expect(rewards).toHaveLength(6);expect(registerRewardTotal(rewards)).toBe('34.00');expect(rewards.filter(r=>r.amount==='6').map(r=>r.id)).toEqual(['2','3']);expect(rewards[3].name).toBe('Name not recorded')
})
it('excludes expired, spent, unavailable and reserved identities without removing other available funding',()=>{
 const data=workspace();data.receiptEarnings=[];data.rrMatches=[{sourceId:'reserved'}];data.unavailableRewardSources=[{sourceId:'lost',date:'2026-11-05',reason:'Lost'}]
 const rows:StandaloneRR[]=['good','expired','spent','reserved','lost'].map(id=>({rewardId:id,description:id,amount:'6',status:id==='spent'?'REDEEMED':'UNUSED',earnedDate:null,expirationDate:id==='expired'?'2026-11-05':null}))
 expect(availableRegisterRewards(data,rows,'2026-11-06').map(r=>r.id)).toEqual(['good'])
})
