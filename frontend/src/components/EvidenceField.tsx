import { useState } from 'react'
import type { ReactNode } from 'react'
export interface Upload { mediaType: string; contentBase64: string }
/** Shared drop surface; the existing upload handler owns validation and submission. */
export function FileDropZone({children,onFiles,disabled=false,label='Attachment drop area',multiple=false}:{children:ReactNode;onFiles:(files:FileList)=>void;disabled?:boolean;label?:string;multiple?:boolean}) {
  const [dragging,setDragging]=useState(false)
  return <div className={`screenshot-drop-zone${dragging?' dragging':''}`} role="group" aria-label={label} aria-disabled={disabled}
    onDragOver={e=>{e.preventDefault();const blocked=disabled||!!e.currentTarget.closest('fieldset:disabled');e.dataTransfer.dropEffect=blocked?'none':'copy';setDragging(!blocked)}}
    onDragLeave={e=>{if(!(e.relatedTarget instanceof Node)||!e.currentTarget.contains(e.relatedTarget))setDragging(false)}}
    onDrop={e=>{e.preventDefault();setDragging(false);if(disabled||e.currentTarget.closest('fieldset:disabled'))return;if(e.dataTransfer.files.length)onFiles(e.dataTransfer.files)}}>
    <p>Drag and drop {multiple?'images or PDFs':'an image or PDF'} here, or choose {multiple?'files':'a file'} below.</p>{children}
  </div>
}

/** Optional original file; never sent anywhere until the enclosing form submits it. */
export function EvidenceField({ onChange, onBusy, dropEnabled = true }: { onChange: (value: Upload | null) => void; onBusy?: (value:boolean)=>void; dropEnabled?: boolean }) {
  const [reading,setReading]=useState(false)
  const [error, setError] = useState(''); const [name, setName] = useState('')
  async function read(file?: File) {
    setError(''); onChange(null); setName('')
    if (!file) return
    setReading(true);onBusy?.(true)
    try {
      if (file.size > 5 * 1024 * 1024 || !['image/jpeg', 'image/png', 'image/webp', 'application/pdf'].includes(file.type)) throw new Error('Select JPEG, PNG, WebP or PDF up to 5 MiB.')
      const contentBase64 = await new Promise<string>((resolve, reject) => { const reader = new FileReader(); reader.onerror = () => reject(new Error('Could not read attachment.')); reader.onload = () => resolve(String(reader.result).split(',')[1]); reader.readAsDataURL(file) })
      onChange({ mediaType: file.type, contentBase64 }); setName(file.name)
    } catch (ex) { setError((ex as Error).message) } finally {setReading(false);onBusy?.(false)}
  }
  const picker = <label>Optional supporting image or PDF<input type="file" disabled={reading} accept="image/jpeg,image/png,image/webp,application/pdf" onChange={e => void read(e.target.files?.[0])} /></label>
  return <>{dropEnabled ? <FileDropZone disabled={reading} onFiles={files=>{
    if(files.length!==1){setError('Drop one image or PDF at a time.');return}
    void read(files[0])
  }}>{picker}</FileDropZone> : picker}{name && <p role="status">Attached: {name}</p>}{error && <p role="alert">{error}</p>}</>
}
