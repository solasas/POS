import { forwardRef } from 'react'
import type { KeyboardEvent } from 'react'

interface BarcodeInputProps {
  value: string
  onChange: (value: string) => void
  /** Called with the trimmed text when Enter is pressed (a scanner ends its burst with Enter). */
  onSubmit: (code: string) => void
  busy?: boolean
}

/**
 * One field for both keyboard-emulating barcode scanners and manual search. A scanner types the
 * digits quickly and finishes with Enter, which triggers an immediate exact lookup with no
 * debounce; ordinary typing instead drives the (debounced) text search shown below.
 */
export const BarcodeInput = forwardRef<HTMLInputElement, BarcodeInputProps>(function BarcodeInput(
  { value, onChange, onSubmit, busy = false },
  ref,
) {
  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') {
      event.preventDefault()
      const code = value.trim()
      if (code && !busy) onSubmit(code)
    }
  }

  return (
    <div>
      <label htmlFor="barcode-input" className="mb-1 block text-sm font-medium text-slate-700">
        Scan barcode or search products
      </label>
      <input
        id="barcode-input"
        ref={ref}
        type="text"
        inputMode="search"
        autoComplete="off"
        autoCapitalize="off"
        spellCheck={false}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        onKeyDown={handleKeyDown}
        placeholder="Scan, or type a name / SKU / barcode and press Enter"
        className="block w-full rounded-lg border-2 border-slate-300 bg-white px-4 py-3 text-lg shadow-sm placeholder:text-base placeholder:text-slate-400 focus:border-emerald-600 focus:outline-none"
      />
      <p className="mt-1 text-xs text-slate-500">Press F2 to jump back here. Enter adds an exact barcode or SKU match.</p>
    </div>
  )
})
