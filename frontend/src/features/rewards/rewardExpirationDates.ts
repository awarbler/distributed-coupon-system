export function rewardExpirationDay(now=new Date()) {
 return now.toLocaleDateString('sv-SE',{timeZone:'America/Chicago'})
}
export function tomorrowFor(day:string) {
 const next=new Date(`${day}T12:00:00Z`);next.setUTCDate(next.getUTCDate()+1);return next.toISOString().slice(0,10)
}
