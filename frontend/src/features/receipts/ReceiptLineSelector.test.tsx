import {useState} from 'react'
import {render,screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {describe,it,expect} from 'vitest'
import {ReceiptLineSelector} from './ReceiptLineSelector'

describe('ReceiptLineSelector selected count',()=>{
 it('counts selected product lines, independently of purchased and qualifying quantities',async()=>{
  const user=userEvent.setup()
  function Example(){const [value,setValue]=useState<Record<string,string>>({crest:'2',charcoal:'1'});return <ReceiptLineSelector name="Reward products 3" lines={[
   {id:'crest',description:'Crest Gum Detox',quantity:'2',price:'6.29'},
   {id:'charcoal',description:'Crest Charcoal',quantity:'1',price:'7.79'},
   {id:'tide',description:'Tide',quantity:'1',price:'21.99'},
  ]} value={value} onChange={setValue}/>}
  render(<Example/>);
  expect(screen.getByText('2 products selected')).toBeInTheDocument()
  expect(screen.getAllByRole('checkbox')).toHaveLength(3)
  expect(screen.getByLabelText('Reward products 3: line 3')).not.toBeChecked()
  await user.click(screen.getByLabelText('Reward products 3: line 3'))
  expect(screen.getByText('3 products selected')).toBeInTheDocument()
  expect(screen.getAllByRole('checkbox').every(input=>(input as HTMLInputElement).checked)).toBe(true)
  expect(screen.getByLabelText('Qualifying quantity for line 1')).toHaveValue(2)
  await user.clear(screen.getByLabelText('Qualifying quantity for line 1'))
  await user.type(screen.getByLabelText('Qualifying quantity for line 1'),'1')
  expect(screen.getByText('3 products selected')).toBeInTheDocument()
 })
})
