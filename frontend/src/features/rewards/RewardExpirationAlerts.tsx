import { rewardExpirationDay, tomorrowFor } from './rewardExpirationDates'
import { useEffect, useState } from 'react'
import { X } from 'lucide-react'
import type { ExpiringReward } from '../../types/platform'
import { date, money } from '../../api/format'


export function RewardExpirationAlerts({rewards=[],visible,onReceipt,onRewards}:{rewards?:ExpiringReward[];visible:boolean;onReceipt:(id:string)=>void;onRewards:(provider?:string)=>void}) {
 const [today,setToday]=useState(rewardExpirationDay)
 const [dismissed,setDismissed]=useState('')
 const [permission,setPermission]=useState(()=>typeof Notification==='undefined'?'unsupported':Notification.permission)
 const [message,setMessage]=useState('')
 useEffect(()=>{const update=()=>setToday(rewardExpirationDay());const timer=setInterval(update,60000);document.addEventListener('visibilitychange',update);return()=>{clearInterval(timer);document.removeEventListener('visibilitychange',update)}},[])
 const tomorrow=tomorrowFor(today)
 useEffect(()=>{
  if(permission!=='granted'||typeof Notification==='undefined')return
  for(const reward of rewards.filter(r=>r.expirationDate===tomorrow)) {
   const key=`reward-expiration:${reward.id}:${reward.expirationDate}`
   try {
    if(localStorage.getItem(key))continue
    new Notification(`${reward.provider} reward expires tomorrow`,{body:`${reward.description||'Reward'}${reward.amount==null?'':` · ${money(reward.amount)}`} expires ${date(reward.expirationDate)}.`,tag:key}).onclick=()=>{window.focus();if(reward.receiptId)onReceipt(reward.receiptId);else onRewards(reward.provider)}
    localStorage.setItem(key,'sent')
   }catch { /* The dashboard reminder remains available if browser alerts are blocked. */ }
  }
 },[rewards,tomorrow,permission,onReceipt,onRewards])
 async function enable(){try{setPermission(await Notification.requestPermission())}catch{setMessage('Browser notifications could not be enabled. Dashboard reminders are still available.')}}
 if(!visible)return null
 const cutoff=rewardExpirationDay(new Date(Date.now()+48*60*60*1000))
 const upcoming=rewards.filter(r=>!!r.expirationDate&&r.expirationDate>=today&&r.expirationDate<=cutoff).slice().sort((a,b)=>a.expirationDate.localeCompare(b.expirationDate))
 const key=today+':'+upcoming.map(r=>r.id+':'+r.expirationDate).join('|')
 if(!upcoming.length||dismissed===key)return null
 return <section className="reward-expiration-banner" aria-label="Reward expirations"><div className="reward-expiration-banner-heading"><strong>Rewards expiring soon</strong><button type="button" className="icon-button" aria-label="Dismiss reward expiration notice" onClick={()=>setDismissed(key)}><X size={16}/></button></div>
 {upcoming.map(r=><div className="reward-expiration-banner-row" key={r.id}><span><strong>{r.provider}</strong> · {r.description||'Reward'}{r.amount==null?'':` · ${money(r.amount)}`} · {r.expirationDate===today?'Expires today':r.expirationDate===tomorrow?'Expires tomorrow':'Expires'} {date(r.expirationDate)}</span><button type="button" className="text-button" onClick={()=>r.receiptId?onReceipt(r.receiptId):onRewards(r.provider)}>{r.receiptId?'View receipt':'View rewards'}</button></div>)}
 {permission==='default'&&<button type="button" className="text-button" onClick={()=>void enable()}>Enable expiration notifications</button>}{message&&<small role="status">{message}</small>}
 </section>
}
