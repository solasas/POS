import { Badge } from '../ui/feedback'
import type { PosProduct } from '../../types/api'
import { formatMoney, formatQuantity, UNIT_SHORT } from '../../utils/format'

interface ProductSearchResultsProps {
  products: PosProduct[]
  onSelect: (product: PosProduct) => void
}

export function ProductSearchResults({ products, onSelect }: ProductSearchResultsProps) {
  return (
    <ul aria-label="Matching products" className="divide-y divide-slate-100 rounded-lg bg-white shadow-sm ring-1 ring-slate-200">
      {products.map((product) => (
        <li key={product.id}>
          <button
            type="button"
            disabled={!product.available}
            onClick={() => onSelect(product)}
            className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left hover:bg-emerald-50 focus-visible:bg-emerald-50 focus-visible:outline-none disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:bg-white"
          >
            <span className="min-w-0">
              <span className="block truncate font-medium text-slate-900">{product.name}</span>
              <span className="block truncate text-xs text-slate-500">
                {product.sku}
                {product.barcode ? ` · ${product.barcode}` : ''}
              </span>
            </span>
            <span className="flex shrink-0 items-center gap-3">
              {product.available ? (
                <Badge tone="green">
                  {formatQuantity(product.stockQuantity)} {UNIT_SHORT[product.unit]} in stock
                </Badge>
              ) : (
                <Badge tone="red">Out of stock</Badge>
              )}
              <span className="w-20 text-right font-semibold tabular-nums text-slate-900">
                {formatMoney(product.price)}
              </span>
            </span>
          </button>
        </li>
      ))}
    </ul>
  )
}
