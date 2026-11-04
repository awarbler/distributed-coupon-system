import { StrictMode } from 'react'
import { render } from '@testing-library/react'
import { expect, it } from 'vitest'
import { Modal } from './ui'

it.each([true, false])('restores page scrolling when overlapping dialogs close (first closes first: %s)', firstClosesFirst => {
  const original = document.body.style.overflow
  document.body.style.overflow = 'auto'
  const first = render(<StrictMode><Modal title="First" onClose={() => {}}>First</Modal></StrictMode>)
  const second = render(<StrictMode><Modal title="Second" onClose={() => {}}>Second</Modal></StrictMode>)
  try {
    expect(document.body.style.overflow).toBe('hidden')
    const [earlier, later] = firstClosesFirst ? [first, second] : [second, first]
    earlier.unmount()
    expect(document.body.style.overflow).toBe('hidden')
    later.unmount()
    expect(document.body.style.overflow).toBe('auto')
  } finally {
    first.unmount()
    second.unmount()
    document.body.style.overflow = original
  }
})
