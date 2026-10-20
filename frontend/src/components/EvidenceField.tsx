import { useState } from 'react'
export interface Upload { mediaType: string; contentBase64: string }
/** Optional original file; never sent anywhere until the enclosing form is saved. */
export function EvidenceField({ onChange, onBusy }: { onChange: (value: Upload | null) => void; onBusy?: (value:boolean)=>void }) {
  const [error, setError] = useState(''); const [name, setName] = useState('')
  async function read(file?: File) {
    setError(''); onChange(null); setName('')
    if (!file) return
    onBusy?.(true)
    try {
      if (file.size > 5 * 1024 * 1024 || !['image/jpeg', 'image/png', 'image/webp', 'application/pdf'].includes(file.type)) throw new Error('Select JPEG, PNG, WebP or PDF up to 5 MiB.')
      const contentBase64 = await new Promise<string>((resolve, reject) => { const reader = new FileReader(); reader.onerror = () => reject(new Error('Could not read attachment.')); reader.onload = () => resolve(String(reader.result).split(',')[1]); reader.readAsDataURL(file) })
      onChange({ mediaType: file.type, contentBase64 }); setName(file.name)
    } catch (ex) { setError((ex as Error).message) } finally {onBusy?.(false)}
  }
  return <><label>Optional supporting image or PDF<input type="file" accept="image/jpeg,image/png,image/webp,application/pdf" onChange={e => void read(e.target.files?.[0])} /></label>{name && <p>Attached: {name}</p>}{error && <p role="alert">{error}</p>}</>
}
