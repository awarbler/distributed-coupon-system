import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { RewardExpirationAlerts } from './RewardExpirationAlerts'
import { tomorrowFor, rewardExpirationDay } from './rewardExpirationDates'

afterEach(()=>{vi.useRealTimers();vi.unstubAllGlobals();localStorage.clear()})
const reward={id:'r1',provider:'ECB',description:'Weekly reward',amount:'5',expirationDate:'2026-11-02',receiptId:'receipt-1'}
it('uses Chicago dates and handles month and year rollover',()=>{
 expect(rewardExpirationDay(new Date('2026-11-02T01:00:00Z'))).toBe('2026-11-01')
 expect(tomorrowFor('2026-12-31')).toBe('2027-01-01')
})
it('shows tomorrow and today reminders and hides past expirations',()=>{
 vi.useFakeTimers();vi.setSystemTime(new Date('2026-11-01T18:00:00Z'))
 render(<RewardExpirationAlerts visible rewards={[reward,{...reward,id:'today',expirationDate:'2026-11-01'},{...reward,id:'old',description:'Expired reward',expirationDate:'2026-10-31'}]} onReceipt={vi.fn()} onRewards={vi.fn()}/> )
 expect(screen.getByText(/Expires tomorrow/)).toBeInTheDocument()
 expect(screen.getByText(/Expires today/)).toBeInTheDocument()
 expect(screen.queryByText('Expired reward')).not.toBeInTheDocument()
})
it('sends one browser notification the day before expiration across remounts',()=>{
 vi.useFakeTimers();vi.setSystemTime(new Date('2026-11-01T18:00:00Z'))
 const notification=vi.fn(function(){return {onclick:null}})
 Object.assign(notification,{permission:'granted'})
 vi.stubGlobal('Notification',notification)
 const props={visible:false,rewards:[reward],onReceipt:vi.fn(),onRewards:vi.fn()}
 const view=render(<RewardExpirationAlerts {...props}/>);view.unmount()
 render(<RewardExpirationAlerts {...props}/>)
 expect(notification).toHaveBeenCalledTimes(1)
 expect(notification).toHaveBeenCalledWith('ECB reward expires tomorrow',expect.objectContaining({body:expect.stringContaining('$5.00')}))
})

it('shows every reward in the 48-hour date window and can dismiss the compact banner',()=>{
 vi.useFakeTimers();vi.setSystemTime(new Date('2026-11-06T18:00:00Z'));
 const rewards=Array.from({length:8},(_,i)=>({...reward,id:`soon-${i}`,description:`Soon ${i}`,expirationDate:i%2?'2026-11-07':'2026-11-08'}));
 render(<RewardExpirationAlerts visible rewards={[...rewards,{...reward,id:'later',description:'Later reward',expirationDate:'2026-11-27'}]} onReceipt={vi.fn()} onRewards={vi.fn()}/>);
 expect(screen.getByRole('region',{name:'Reward expirations'})).toBeVisible();expect(screen.queryByText('Later reward')).not.toBeInTheDocument();for(let i=0;i<8;i++)expect(screen.getByText(new RegExp(`Soon ${i}`))).toBeVisible();
 fireEvent.click(screen.getByRole('button',{name:'Dismiss reward expiration notice'}));expect(screen.queryByRole('region',{name:'Reward expirations'})).toBeNull();
});
it('renders no empty-state panel when there are no rewards due',()=>{vi.useFakeTimers();vi.setSystemTime(new Date('2026-11-06T18:00:00Z'));const view=render(<RewardExpirationAlerts visible rewards={[{...reward,expirationDate:'2026-12-10'}]} onReceipt={vi.fn()} onRewards={vi.fn()}/>);expect(view.container).toBeEmptyDOMElement()})

it('opens the matching account for a reward without a receipt',()=>{vi.useFakeTimers();vi.setSystemTime(new Date('2026-11-01T18:00:00Z'));const onRewards=vi.fn();render(<RewardExpirationAlerts visible rewards={[{...reward,receiptId:null}]} onReceipt={vi.fn()} onRewards={onRewards}/>);fireEvent.click(screen.getByRole('button',{name:'View rewards'}));expect(onRewards).toHaveBeenCalledWith('ECB')})
