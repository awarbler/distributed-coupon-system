import { useState } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it } from 'vitest'
import { StoreInput, formatReceiptNumber } from './StoreInput'

it.each([['walm','Walmart'],['walg','Walgreens'],['He','H-E-B'],['Cost','Costco']])('suggests %s without requiring the full name', async (prefix, store) => {
  function Form() {const [value,setValue]=useState('');return <StoreInput value={value} onChange={setValue}/>}
  const user=userEvent.setup();render(<Form/>);
  await user.type(screen.getByRole('combobox',{name:'Store'}),prefix)
  await user.click(screen.getByRole('option',{name:store}))
  expect(screen.getByRole('combobox')).toHaveValue(store)
  expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
})
it('accepts a store suggestion with the keyboard and allows custom stores',async()=>{
  function Form() {const [value,setValue]=useState('');return <StoreInput value={value} onChange={setValue}/>}
  const user=userEvent.setup();render(<Form/>);
  const input=screen.getByRole('combobox');await user.type(input,'walg{ArrowDown}{Enter}')
  expect(input).toHaveValue('Walgreens');await user.clear(input);await user.type(input,'Local store')
  expect(input).toHaveValue('Local store')
})
it('formats complete Walgreens RFNs while retaining leading zeros and incomplete references',()=>{
  for(const value of ['00001234567890123456','0000 1234 5678 9012 3456','RFN# 0000-1234-5678-9012-3456']) expect(formatReceiptNumber('Walgreens',value)).toBe('0000-1234-5678-9012-3456')
  expect(formatReceiptNumber('Walmart','00001234567890123456')).toBe('00001234567890123456')
  expect(formatReceiptNumber('Walgreens','1234')).toBe('1234')
})
