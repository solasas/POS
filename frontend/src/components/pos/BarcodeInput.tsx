import { forwardRef } from 'react'
import type { KeyboardEvent } from 'react'
import { Icon } from '../ui/Icon'

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
      <label htmlFor="barcode-input" className="eyebrow mb-2 block">
        Scan barcode or search products
      </label>
      <div className="relative">
        <Icon
          name="scan"
          size={24}
          className="pointer-events-none absolute top-1/2 left-4 -translate-y-1/2 text-emerald-700"
        />
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
          placeholder="Scan or type to search"
          className="block h-16 w-full rounded-2xl border-2 border-slate-300 bg-card pr-28 pl-14 text-xl font-medium text-ink shadow-[inset_0_1px_2px_rgb(24_34_28/0.06)] transition placeholder:text-base placeholder:font-normal placeholder:text-slate-400 hover:border-slate-400 focus:border-emerald-600 focus:ring-4 focus:ring-emerald-600/15 focus:outline-none"
        />
        <span className="pointer-events-none absolute top-1/2 right-4 flex -translate-y-1/2 items-center gap-1.5 text-xs text-slate-400">
          {busy ? (
            <span className="h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-emerald-700" />
          ) : (
            <>
              <kbd className="kbd">Enter</kbd> add
            </>
          )}
        </span>
      </div>
      <p className="mt-2 text-xs text-slate-500">
        <kbd className="kbd">F2</kbd> returns here from anywhere. Enter adds an exact barcode or SKU match.
      </p>
    </div>
  )
})
