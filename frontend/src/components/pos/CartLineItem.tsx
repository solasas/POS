import { useState } from 'react'
import type { CartLine } from '../../hooks/useCart'
import type { PricePreviewLine } from '../../types/api'
import { formatMoney, UNIT_SHORT } from '../../utils/format'
import { Icon } from '../ui/Icon'

interface CartLineItemProps {
  line: CartLine
  priced?: PricePreviewLine
  onQuantityChange: (quantity: number) => void
  onRemove: () => void
  /** Reason the last quantity edit was refused; shown under the line. */
  error?: string
  /** Briefly highlights a line that was just added or incremented. */
  flash?: boolean
  /** When billing beyond stock is allowed, a line above the stock on hand is flagged, not blocked. */
  allowBeyondStock?: boolean
}

export function CartLineItem({
  line,
  priced,
  onQuantityChange,
  onRemove,
  error,
  flash,
  allowBeyondStock = false,
}: CartLineItemProps) {
  const { product, quantity } = line
  // text being typed; null means "show the committed quantity"
  const [draft, setDraft] = useState<string | null>(null)
  const text = draft ?? String(quantity)

  const commit = () => {
    const parsed = Number(text)
    setDraft(null)
    if (text.trim() === '' || Number.isNaN(parsed)) return
    if (parsed !== quantity) onQuantityChange(parsed)
  }

  const step = 1
  const beyondStock = allowBeyondStock && quantity > product.stockQuantity
  const shortBy = Math.round((quantity - Math.max(product.stockQuantity, 0)) * 1000) / 1000
  const stepper =
    'flex h-8 w-8 items-center justify-center text-slate-600 transition hover:bg-slate-200/70 hover:text-ink disabled:opacity-30 disabled:hover:bg-transparent'

  return (
    <li className={`-mx-2 rounded-lg px-2 py-3.5 ${flash ? 'row-flash' : ''}`}>
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="truncate font-display text-[15px] leading-snug font-semibold text-ink">{product.name}</div>
          <div className="num mt-0.5 text-xs text-slate-500">
            {formatMoney(product.price)} / {UNIT_SHORT[product.unit]}
          </div>
        </div>
        <div className="shrink-0 text-right">
          <div className="num font-display text-base font-semibold text-ink" aria-label={`Line total for ${product.name}`}>
            {priced ? formatMoney(priced.lineTotal) : '…'}
          </div>
          {priced && priced.discountAmount > 0 && (
            <div className="num text-[11px] font-medium text-emerald-700">−{formatMoney(priced.discountAmount)}</div>
          )}
        </div>
      </div>

      <div className="mt-2.5 flex items-center gap-3">
        <div className="inline-flex items-center overflow-hidden rounded-[9px] border border-slate-300 bg-card">
          <button
            type="button"
            aria-label={`Decrease quantity of ${product.name}`}
            onClick={() => onQuantityChange(quantity - step)}
            disabled={quantity - step <= 0}
            className={stepper}
          >
            <Icon name="minus" size={15} strokeWidth={2.25} />
          </button>
          <input
            aria-label={`Quantity of ${product.name}`}
            inputMode="decimal"
            value={text}
            onChange={(event) => setDraft(event.target.value)}
            onBlur={commit}
            onKeyDown={(event) => {
              if (event.key === 'Enter') {
                event.preventDefault()
                commit()
              }
            }}
            className="num h-8 w-12 border-x border-slate-200 bg-transparent text-center text-sm font-semibold text-ink focus:bg-white focus:outline-none"
          />
          <button
            type="button"
            aria-label={`Increase quantity of ${product.name}`}
            onClick={() => onQuantityChange(quantity + step)}
            className={stepper}
          >
            <Icon name="plus" size={15} strokeWidth={2.25} />
          </button>
        </div>
        <span className="text-xs text-slate-500">
          {UNIT_SHORT[product.unit]} · <span className="num">{product.stockQuantity}</span> in stock
        </span>
        <button
          type="button"
          aria-label={`Remove ${product.name}`}
          onClick={onRemove}
          className="ml-auto flex items-center gap-1 rounded-md px-1.5 py-1 text-xs font-medium text-slate-400 transition hover:bg-red-50 hover:text-red-600"
        >
          <Icon name="trash" size={15} />
          Remove
        </button>
      </div>
      {beyondStock && (
        <p className="mt-2 flex items-center gap-1.5 rounded-md bg-amber-100 px-2.5 py-1.5 text-xs font-medium text-amber-900">
          <Icon name="alert" size={14} className="shrink-0" />
          Beyond stock by {shortBy} {UNIT_SHORT[product.unit]}. The balance will go negative.
        </p>
      )}
      {error && (
        <p role="alert" className="mt-2 text-xs font-medium text-red-600">
          {error}
        </p>
      )}
    </li>
  )
}
