import { money } from '../../api/format'
import type { PlanScenario, ScenarioEstimate } from '../../types/platform'

const rewardName=(type:string)=>({REGISTER_REWARD:'printed RR',WALGREENS_CASH:'Walgreens Cash',ECB:'ECB',WALMART_CASH:'Walmart Cash',GIFT_CARD:'Gift card'}[type]??type.replaceAll('_',' '))
export function WrittenDeal({scenario,report}:{scenario?:PlanScenario;report:ScenarioEstimate}) {
 return <section className="written-deal" aria-label="Shopping checkout guide">
 {report.items.map(item=>{const line=scenario?.items.find(l=>l.id===item.id);return <article key={item.id}>
 <strong>{item.name} — buy {line?.quantity??'—'}</strong>
 <p>{line?.regular&&<>Regular {money(line.regular)} · </>}{line?.priceRule?'Sale pricing': 'Price'} {money(line?.sale||line?.regular)} × {line?.quantity??'—'} = <strong>{money(item.totalPrice)}</strong></p>
 {scenario?.coupons.filter(c=>!c.wholeReceipt&&c.lineIds.includes(item.id)&&c.lineIds[0]===item.id).map(c=><p key={c.id}>Coupon: <strong>{money(c.amount)} off {c.requiredQuantity}</strong> · {c.description}{report.coupons.find(v=>v.id===c.id)?.applied===false?' — requirements not met':''}</p>)}
 {scenario?.promotions.filter(p=>!p.wholeReceipt&&p.lineIds[0]===item.id).map(p=><p key={p.id}>Earn: <strong>{money(report.promotions.find(v=>v.id===p.id)?.potentialReward)} {rewardName(p.rewardType)}</strong> · {p.description}{report.promotions.find(v=>v.id===p.id)?.eligible===false?' — requirements not met':''}</p>)}
 </article>})}
 <div className="written-checkout"><strong>At checkout</strong><p>{report.items.map(i=>money(i.totalPrice)).join(' + ')} − {money(report.couponTotal)} coupons = <strong>{money(report.merchandiseAfterCoupons)}</strong></p>
 {scenario?.coupons.filter(c=>c.wholeReceipt).map(c=><p key={c.id}>Whole-purchase coupon: {money(c.amount)} · {c.description}</p>)}
 {scenario?.payments?.filter(p=>!['CASH','CARD'].includes(p.type)&&p.amount&&p.amount!=='0').map(p=><p key={p.id}>Use: <strong>{money(p.amount)} {rewardName(p.type)}</strong>{p.notes&&` · ${p.notes}`}</p>)}
 <p>{money(report.merchandiseAfterCoupons)} − {money(report.registerRewardsUsed)} RR + {money(report.tax)} tax − {money(report.walgreensCashUsed)} Walgreens Cash{report.ecbUsed&&/[1-9]/.test(report.ecbUsed)&&<> − {money(report.ecbUsed)} ECB</>}{report.giftCardUsed&&/[1-9]/.test(report.giftCardUsed)&&<> − {money(report.giftCardUsed)} Gift card</>}{report.walmartCashUsed&&/[1-9]/.test(report.walmartCashUsed)&&<> − {money(report.walmartCashUsed)} Walmart Cash</>} = {money(report.calculatedOop)} cash/card</p>
 {[['ECB',report.ecbUsed],['Gift card',report.giftCardUsed],['Walmart Cash',report.walmartCashUsed]].filter(([,v])=>v&&/[1-9]/.test(v)).map(([label,v])=><p key={label}>Use {label}: {money(v)}</p>)}
 <p className="written-pay">Pay <strong>{money(report.calculatedOop)}</strong> cash/card</p>
 {scenario?.promotions.filter(p=>p.wholeReceipt).map(p=><p key={p.id}>Earn: {money(report.promotions.find(v=>v.id===p.id)?.potentialReward)} {rewardName(p.rewardType)} · {p.description}</p>)}
 {report.promotions.map(p=><p key={p.id}>{p.description} · Payment Rule: {p.paymentRuleStatus??(p.walgreensCashAllowed?'Met':'Not Met')} · Spend: {p.spendMet?'Met':'Not Met'}{scenario?.promotions.find(v=>v.id===p.id)&&<> · Qualifying spend: {money(p.qualifyingSpend)} / {money(scenario.promotions.find(v=>v.id===p.id)!.requiredSpend)} · Basis: {scenario.promotions.find(v=>v.id===p.id)!.spendBasis.replaceAll('_',' ').toLowerCase()}</>}</p>)}
 {report.fundingIssues?.map(issue=><p key={issue}>{report.fundingRuleStatus}: {issue}</p>)}
 <p>Expected Register Rewards: {money(report.expectedRegisterRewards)} · Expected Walgreens Cash: {money(report.expectedWalgreensCash)}</p>{report.expectedExtraBucks&&/[1-9]/.test(report.expectedExtraBucks)&&<p>Expected CVS ExtraBucks: {money(report.expectedExtraBucks)}</p>}{report.expectedWalmartCash&&/[1-9]/.test(report.expectedWalmartCash)&&<p>Expected Walmart Cash: {money(report.expectedWalmartCash)}</p>}<p><strong>Expected rewards: {money(report.potentialRewards)}</strong></p><small>Confirm coupons applied and collect the listed printed rewards. Tax and earnings use your entered terms.</small></div>
 </section>
}
