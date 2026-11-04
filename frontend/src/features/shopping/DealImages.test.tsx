import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
import { ApiClient } from '../../api/client'
import { DealImages } from './DealImages'
it('accepts companion images together including a file with an empty media type',async()=>{
 const change=vi.fn(),user=userEvent.setup();render(<DealImages api={new ApiClient('test')} onChange={change} onBusy={vi.fn()}/>)
 await user.upload(screen.getByLabelText('Deal photos — select companion images together'),[new File(['products'],'products.PNG',{type:'image/png'}),new File(['receipt'],'receipt.png',{type:''})])
 await waitFor(()=>expect(change).toHaveBeenLastCalledWith([{mediaType:'image/png',contentBase64:btoa('products')},{mediaType:'image/png',contentBase64:btoa('receipt')}]))
 await user.click(screen.getAllByRole('button',{name:'Remove photo'})[0]);expect(change.mock.lastCall?.[0]).toHaveLength(1)
})
it('converts a HEIC drop before including it in the deal',async()=>{
 const api=new ApiClient('test'),change=vi.fn();const mutate=vi.spyOn(api,'mutate').mockResolvedValue({mediaType:'image/jpeg',contentBase64:'AQID'})
 render(<DealImages api={api} onChange={change} onBusy={vi.fn()}/>);fireEvent.drop(screen.getByRole('group'),{dataTransfer:{files:[new File(['heic'],'photo.HEIC',{type:''})]}})
 await waitFor(()=>expect(change).toHaveBeenCalledWith([{mediaType:'image/jpeg',contentBase64:'AQID'}]));expect(mutate).toHaveBeenCalledWith('/shopping-plans/convert-image','POST',{mediaType:'image/heic',contentBase64:btoa('heic')},expect.any(String))
})
it('keeps prior photos when a new upload is invalid',async()=>{
 const change=vi.fn();render(<DealImages api={new ApiClient('test')} onChange={change} onBusy={vi.fn()}/>);const zone=screen.getByRole('group')
 fireEvent.drop(zone,{dataTransfer:{files:[new File(['photo'],'photo.png',{type:'image/png'})]}});await waitFor(()=>expect(change).toHaveBeenCalledTimes(1))
 fireEvent.drop(zone,{dataTransfer:{files:[new File(['bad'],'bad.txt',{type:'text/plain'})]}});await screen.findByRole('alert');expect(change).toHaveBeenCalledTimes(1);expect(screen.getByText('Attached: photo.png')).toBeInTheDocument()
})
it('accepts a file exposed only through drag items without constructing DataTransfer',async()=>{
 const change=vi.fn();render(<DealImages api={new ApiClient('test')} onChange={change} onBusy={vi.fn()}/>);const file=new File(['photo'],'photo.png',{type:'image/png'})
 fireEvent.dragEnter(screen.getByRole('group'),{dataTransfer:{types:['Files']}});expect(screen.getByRole('group')).toHaveClass('dragging')
 fireEvent.drop(screen.getByRole('group'),{dataTransfer:{files:[],items:[{kind:'file',getAsFile:()=>file}]}})
 await waitFor(()=>expect(change).toHaveBeenCalledWith([{mediaType:'image/png',contentBase64:btoa('photo')}]))
})
it('explains an Apple Photos drop that contains no browser-readable file',()=>{
 render(<DealImages api={new ApiClient('test')} onChange={vi.fn()} onBusy={vi.fn()}/>);fireEvent.drop(screen.getByRole('group'),{dataTransfer:{files:[],items:[{kind:'string',type:'text/uri-list'}]}})
 expect(screen.getByRole('alert')).toHaveTextContent('browser received no image file')
})
it('converts TIFF files supplied by Photos',async()=>{
 const api=new ApiClient('test'),change=vi.fn();vi.spyOn(api,'mutate').mockResolvedValue({mediaType:'image/jpeg',contentBase64:'AQID'});render(<DealImages api={api} onChange={change} onBusy={vi.fn()}/>)
 fireEvent.drop(screen.getByRole('group'),{dataTransfer:{files:[new File(['tiff'],'photo.tiff',{type:'image/tiff'})]}})
 await waitFor(()=>expect(change).toHaveBeenCalledWith([{mediaType:'image/jpeg',contentBase64:'AQID'}]))
})
