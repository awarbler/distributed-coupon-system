import { expect,it } from 'vitest'
import type { ScenarioPromotion } from '../../types/platform'
import { promotionRewardDefaults } from './promotionRewardDefaults'
const promotion=():ScenarioPromotion=>({id:'rr',description:'Colgate Buy 2 Get 4',rewardType:'REGISTER_REWARD',rewardAmount:'4',requiredQuantity:'2',requiredSpend:'0',spendBasis:'BEFORE_COUPONS',allowsWalgreensCash:false,lineIds:['line']})
it('fills the five requested promotion fields from the earned description without approving WC funding',()=>{
 const p=promotionRewardDefaults(promotion());expect([p.prohibitedRegisterRewardProgram,p.originProduct,p.originBrand,p.originProgram,p.originPromotion]).toEqual(['Colgate','Colgate','Colgate','Colgate','Colgate']);expect(p.allowsWalgreensCash).toBe(false);expect(p.paymentRulesReviewed).toBeUndefined()
})
it('updates text defaults when the earned description changes and retains explicit corrections',()=>{
 const p=promotionRewardDefaults({...promotionRewardDefaults(promotion()),originProgram:'Recorded program',description:'Crest Buy 3'},'Colgate Buy 2 Get 4');expect(p.originBrand).toBe('Crest');expect(p.prohibitedRegisterRewardProgram).toBe('Crest');expect(p.originProgram).toBe('Recorded program')
})
it('does not add an RR program prohibition to a Walgreens Cash booster',()=>{
 const p={...promotion(),rewardType:'WALGREENS_CASH'};expect(promotionRewardDefaults(p)).toEqual(p)
})
