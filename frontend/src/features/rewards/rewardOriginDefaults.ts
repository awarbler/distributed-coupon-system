const originFields=['originProduct','originBrand','originProgram','originPromotion'] as const
export type RewardOrigins=Record<typeof originFields[number],string>
/** User-requested text defaults. Existing explicit origins are preserved. */
export function rewardOriginDefaults(description:string,existing:Partial<RewardOrigins>={}):RewardOrigins {
 const word=description.trim().split(/\s+/)[0]??''
 return Object.fromEntries(originFields.map(field=>[field,existing[field]?.trim()?existing[field]:word])) as RewardOrigins
}
export function updateRewardOriginDefaults(description:string,previousDescription:string,origins:RewardOrigins):RewardOrigins {
 const previousWord=previousDescription.trim().split(/\s+/)[0]??''
 return rewardOriginDefaults(description,Object.fromEntries(originFields.map(field=>[field,origins[field]===previousWord?'':origins[field]])))
}
