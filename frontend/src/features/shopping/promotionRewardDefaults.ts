import type { ScenarioPromotion } from '../../types/platform'
import { rewardOriginDefaults,updateRewardOriginDefaults } from '../rewards/rewardOriginDefaults'
export function promotionRewardDefaults(promotion:ScenarioPromotion,previousDescription?:string):ScenarioPromotion {
 if(promotion.rewardType!=='REGISTER_REWARD')return promotion
 const origins={originProduct:promotion.originProduct??'',originBrand:promotion.originBrand??'',originProgram:promotion.originProgram??'',originPromotion:promotion.originPromotion??''}
 const word=promotion.description.trim().split(/\s+/)[0]??''
 const previousWord=previousDescription?.trim().split(/\s+/)[0]
 const prohibited=promotion.prohibitedRegisterRewardProgram?.trim()
 return {...promotion,...(previousDescription===undefined?rewardOriginDefaults(promotion.description,origins):updateRewardOriginDefaults(promotion.description,previousDescription,origins)),prohibitedRegisterRewardProgram:!prohibited||prohibited===previousWord?word:promotion.prohibitedRegisterRewardProgram}
}
