import { expect,it } from 'vitest'
import { rewardOriginDefaults,updateRewardOriginDefaults } from './rewardOriginDefaults'
it('prefills all blank origin fields from the first description word',()=>{
 expect(rewardOriginDefaults('  Crest Buy 3 Get $6 RR')).toEqual({originProduct:'Crest',originBrand:'Crest',originProgram:'Crest',originPromotion:'Crest'})
 expect(rewardOriginDefaults('P&G Spend 30 Get 10').originProgram).toBe('P&G')
})
it('keeps existing verified origins and updates only text defaults when the description changes',()=>{
 const original=rewardOriginDefaults('Crest Buy 3',{originProgram:'P&G'})
 expect(updateRewardOriginDefaults('Colgate Buy 2','Crest Buy 3',original)).toEqual({originProduct:'Colgate',originBrand:'Colgate',originProgram:'P&G',originPromotion:'Colgate'})
 expect(rewardOriginDefaults('')).toEqual({originProduct:'',originBrand:'',originProgram:'',originPromotion:''})
})
