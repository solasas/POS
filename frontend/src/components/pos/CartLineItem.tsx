import { useState } from 'react'
import type { CartLine } from '../../hooks/useCart'
import type { PricePreviewLine } from '../../types/api'
import { formatMoney, UNIT_SHORT } from '../../utils/format'
import { Button } from '../ui/Button'

interface CartLineItemProps {
  line: CartLine
  priced?: PricePreviewLine
  onQuantityChange: (quantity: number) => void
  onRemove: () => void
  /** Reason the last quantity edit was refused; shown under the line. */
  error?: string
}

export function CartLineItem({ line, priced, onQuantityChange, onRemove, error }: CartLineItemProps) {
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

  return (
    <li className="py-3">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="truncate font-medium text-slate-900">{product.name}</div>
          <div className="truncate text-xs text-slate-500">
            {product.sku}
            {product.barcode ? ` · ${product.barcode}` : ''} · {formatMoney(product.price)} / {UNIT_SHORT[product.unit]}
          </div>
        </div>
        <div className="shrink-0 text-right">
          <div className="font-semibold tabular-nums text-slate-900" aria-label={`Line total for ${product.name}`}>
            {priced ? formatMoney(priced.lineTotal) : '…'}
          </div>
          {priced && priced.discountAmount > 0 && (
            <div className="text-xs text-emerald-700">−{formatMoney(priced.discountAmount)} discount</div>
          )}
        </div>
      </div>

      <div className="mt-2 flex items-center gap-2">
        <Button
          variant="secondary"
          aria-label={`Decrease quantity of ${product.name}`}
          onClick={() => onQuantityChange(quantity - step)}
          disabled={quantity - step <= 0}
          className="!px-2.5 !py-1"
        >
          −
        </Button>
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
          className="w-20 rounded-md border border-slate-300 px-2 py-1 text-center text-sm tabular-nums focus:border-emerald-600 focus:outline-none"
        />
        <Button
          variant="secondary"
          aria-label={`Increase quantity of ${product.name}`}
          onClick={() => onQuantityChange(quantity + step)}
          className="!px-2.5 !py-1"
        >
          +
        </Button>
        <span className="text-xs text-slate-500">
          {UNIT_SHORT[product.unit]} · {product.stockQuantity} in stock
        </span>
        <Button
          variant="ghost"
          aria-label={`Remove ${product.name}`}
          onClick={onRemove}
          className="ml-auto !px-2 !py-1 text-red-600"
        >
          Remove
        </Button>
      </div>
      {error && (
        <p role="alert" className="mt-1 text-xs font-medium text-red-600">
          {error}
        </p>
      )}
    </li>
  )
}
