import type { PosProduct } from '../../types/api'
import { formatMoney, formatQuantity, UNIT_SHORT } from '../../utils/format'
import { Icon } from '../ui/Icon'

interface ProductSearchResultsProps {
  products: PosProduct[]
  onSelect: (product: PosProduct) => void
  /** Billing continues past zero stock, so "out of stock" is a warning rather than a block. */
  allowBeyondStock?: boolean
}

export function ProductSearchResults({ products, onSelect, allowBeyondStock = false }: ProductSearchResultsProps) {
  return (
    <ul aria-label="Matching products" className="card divide-y divide-slate-100 overflow-hidden">
      {products.map((product) => {
        const low = product.available && product.stockQuantity > 0 && product.stockQuantity <= 5
        const beyond = allowBeyondStock && product.available && product.stockQuantity <= 0
        return (
          <li key={product.id}>
            <button
              type="button"
              disabled={!product.available}
              onClick={() => onSelect(product)}
              className="group flex w-full items-center gap-4 px-4 py-3.5 text-left transition hover:bg-emerald-50/70 focus-visible:bg-emerald-50 focus-visible:outline-none disabled:cursor-not-allowed disabled:bg-slate-100/60 disabled:opacity-60"
            >
              <span className="min-w-0 flex-1">
                <span className="block truncate font-medium text-ink">{product.name}</span>
                <span className="mt-0.5 block truncate font-mono text-xs text-slate-500">
                  {product.sku}
                  {product.barcode ? ` · ${product.barcode}` : ''}
                </span>
              </span>
              <span className="shrink-0 text-right">
                {beyond ? (
                  <span className="block text-xs font-semibold text-amber-700">
                    No stock · billing allowed
                  </span>
                ) : product.available ? (
                  <span className={`block text-xs ${low ? 'font-semibold text-amber-700' : 'text-slate-500'}`}>
                    {formatQuantity(product.stockQuantity)} {UNIT_SHORT[product.unit]} in stock
                  </span>
                ) : (
                  <span className="block text-xs font-semibold text-red-600">Out of stock</span>
                )}
                <span className="num block font-display text-lg font-semibold text-ink">
                  {formatMoney(product.price)}
                </span>
              </span>
              <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-emerald-700 text-white opacity-0 transition group-hover:opacity-100 group-focus-visible:opacity-100 group-disabled:hidden">
                <Icon name="plus" size={16} strokeWidth={2.5} />
              </span>
            </button>
          </li>
        )
      })}
    </ul>
  )
}
