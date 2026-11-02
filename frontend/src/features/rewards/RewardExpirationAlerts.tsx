import { rewardExpirationDay, tomorrowFor } from './rewardExpirationDates'
import { useEffect, useState } from 'react'
import type { ExpiringReward } from '../../types/platform'
import { date, money } from '../../api/format'
import { Panel } from '../../components/ui'

export function RewardExpirationAlerts({rewards=[],visible,onReceipt,onRewards}:{rewards?:ExpiringReward[];visible:boolean;onReceipt:(id:string)=>void;onRewards:()=>void}) {
 const [today,setToday]=useState(rewardExpirationDay)
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
    new Notification(`${reward.provider} reward expires tomorrow`,{body:`${reward.description||'Reward'}${reward.amount==null?'':` · ${money(reward.amount)}`} expires ${date(reward.expirationDate)}.`,tag:key}).onclick=()=>{window.focus();if(reward.receiptId)onReceipt(reward.receiptId);else onRewards()}
    localStorage.setItem(key,'sent')
   }catch { /* The dashboard reminder remains available if browser alerts are blocked. */ }
  }
 },[rewards,tomorrow,permission,onReceipt,onRewards])
 async function enable(){try{setPermission(await Notification.requestPermission())}catch{setMessage('Browser notifications could not be enabled. Dashboard reminders are still available.')}}
 if(!visible)return null
 const upcoming=rewards.filter(r=>r.expirationDate>=today).slice().sort((a,b)=>a.expirationDate.localeCompare(b.expirationDate))
 return <Panel className="reward-expiration-panel" title="Reward expirations" subtitle="Recorded expiration dates for received rewards.">
  <div className="reward-expiration-content">
   {permission==='default'&&<button type="button" className="secondary" onClick={()=>void enable()}>Enable expiration notifications</button>}
   <p>{permission==='granted'?'Browser notifications are enabled for one day before expiration while this app is open.':permission==='denied'?'Browser notifications are blocked. Allow notifications in your browser settings to receive reminders.':permission==='unsupported'?'This browser does not support notifications. Reminders appear here.':'Enable browser notifications for a reminder one day before expiration while this app is open.'}</p>
   {message&&<p role="status">{message}</p>}
   {upcoming.length?upcoming.map(r=><div key={r.id} className={`reward-expiration-row${r.expirationDate<=tomorrow?' urgent':''}`} role={r.expirationDate<=tomorrow?'status':undefined}>
    <div><strong>{r.provider}{r.amount!=null?` · ${money(r.amount)}`:''}</strong><p>{r.description||'Reward'}</p><strong>{r.expirationDate===today?'Expires today':r.expirationDate===tomorrow?'Expires tomorrow':'Expires'} · {date(r.expirationDate)}</strong></div>
    <button type="button" className="secondary" onClick={()=>r.receiptId?onReceipt(r.receiptId):onRewards()}>{r.receiptId?'View receipt':'View rewards'}</button>
   </div>):<p>No upcoming reward expirations recorded.</p>}
   <p className="muted">Only rewards with a recorded expiration date appear. Review your provider’s remaining balance before using a reward.</p>
  </div>
 </Panel>
}
