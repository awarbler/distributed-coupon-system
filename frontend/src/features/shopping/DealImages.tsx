import { useState } from 'react'
import type { ApiClient } from '../../api/client'
import { FileDropZone } from '../../components/EvidenceField'
import type { Upload } from '../../components/EvidenceField'

export function DealImages({api,onChange,onBusy}:{api:ApiClient;onChange:(files:Upload[])=>void;onBusy:(busy:boolean)=>void}) {
 const [files,setFiles]=useState<{name:string;upload:Upload}[]>([]),[busy,setBusy]=useState(false),[error,setError]=useState('')
 async function add(incoming:FileList|File[]){setBusy(true);onBusy(true);setError('');try{
  const next=[...files]
  for(const file of Array.from(incoming)){
   const ext=file.name.split('.').at(-1)?.toLowerCase()
   const media=({'jpg':'image/jpeg','jpeg':'image/jpeg','png':'image/png','webp':'image/webp','heic':'image/heic','heif':'image/heif','tif':'image/tiff','tiff':'image/tiff'} as Record<string,string>)[ext??'']??file.type
   if(!['image/jpeg','image/png','image/webp','image/heic','image/heif','image/tiff'].includes(media)||file.size>5*1024*1024)throw new Error('Choose JPEG, PNG, WebP, HEIC or TIFF images up to 5 MiB each.')
   const contentBase64=await new Promise<string>((resolve,reject)=>{const reader=new FileReader();reader.onerror=()=>reject(new Error('Could not read photo.'));reader.onload=()=>resolve(String(reader.result).split(',')[1]);reader.readAsDataURL(file)})
   let upload:Upload={mediaType:media,contentBase64}
   if(media==='image/heic'||media==='image/heif'||media==='image/tiff')upload=await api.mutate('/shopping-plans/convert-image','POST',upload,crypto.randomUUID()) as Upload
   if(!next.some(f=>f.upload.contentBase64===upload.contentBase64))next.push({name:file.name,upload})
  }
  if(next.length>10||next.reduce((n,f)=>n+Math.floor(f.upload.contentBase64.length*3/4),0)>5*1024*1024)throw new Error('Attach up to 10 images, totaling at most 5 MiB per deal.')
  setFiles(next);onChange(next.map(f=>f.upload))
 }catch(e){setError((e as Error).message)}finally{setBusy(false);onBusy(false)}}
 return <><FileDropZone multiple disabled={busy} onFiles={incoming=>void add(incoming)}><label>Deal photos — select companion images together<input type="file" multiple disabled={busy} accept="image/jpeg,image/png,image/webp,image/heic,image/heif,image/tiff,.tif,.tiff,.jpg,.jpeg,.png,.webp,.heic,.heif" onChange={e=>{if(e.target.files)void add(e.target.files)}}/></label></FileDropZone><p className="muted">Add product, receipt and reward photos to the same deal. You can select more photos before reading.</p>{files.map((file,index)=><p key={index}>Attached: {file.name} <button type="button" className="text-button" disabled={busy} onClick={()=>{const next=files.filter((_,i)=>i!==index);setFiles(next);onChange(next.map(f=>f.upload))}}>Remove photo</button></p>)}{error&&<p role="alert">{error}</p>}</>
}
