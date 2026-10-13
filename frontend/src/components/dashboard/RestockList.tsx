import { Link } from 'react-router-dom'
import { Icon } from '../ui/Icon'
import type { Product } from '../../types/api'
import { formatQuantity, UNIT_SHORT } from '../../utils/format'

interface RestockListProps {
  products: Product[]
  totalLow: number
  loading: boolean
}

/** Lowest-stock first, each with a level bar relative to its own reorder threshold. */
export function RestockList({ products, totalLow, loading }: RestockListProps) {
  return (
    <section aria-label="Needs restocking" className="card p-6">
      <div className="mb-4 flex items-baseline justify-between">
        <h2 className="font-display text-lg font-bold text-ink">Needs restocking</h2>
        {totalLow > 5 && (
          <Link to="/products?low=1&status=active" className="text-sm font-medium text-emerald-700 hover:underline">
            See all {totalLow}
          </Link>
        )}
      </div>

      {loading && (
        <div className="space-y-4" aria-hidden="true">
          {[0, 1, 2].map((i) => (
            <div key={i} className="skeleton h-10" />
          ))}
        </div>
      )}

      {!loading && products.length === 0 && (
        <p className="flex items-center gap-2 text-sm text-emerald-800">
          <Icon name="check" size={18} className="text-emerald-600" />
          Every active product is above its low-stock level.
        </p>
      )}

      <ul className="space-y-4">
        {products.map((product) => {
          const threshold = product.lowStockThreshold
          const level = threshold > 0 ? Math.min(product.stockQuantity / threshold, 1) : 0
          const out = product.stockQuantity <= 0
          return (
            <li key={product.id}>
              <div className="flex items-baseline justify-between gap-3">
                <Link to={`/products/${product.id}/edit`} className="min-w-0 truncate text-sm font-medium text-ink hover:underline">
                  {product.name}
                </Link>
                <span className={`num shrink-0 text-xs font-semibold ${out ? 'text-red-600' : 'text-amber-700'}`}>
                  {product.stockQuantity < 0
                    ? `Oversold by ${formatQuantity(-product.stockQuantity)} ${UNIT_SHORT[product.unit]}`
                    : out
                      ? 'Out of stock'
                      : `${formatQuantity(product.stockQuantity)} ${UNIT_SHORT[product.unit]} left`}
                </span>
              </div>
              <div
                className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-slate-200"
                role="img"
                aria-label={`Stock level ${Math.round(level * 100)}% of the reorder point`}
              >
                <div
                  className={`h-full rounded-full ${out ? 'bg-red-500' : 'bg-amber-400'}`}
                  style={{ width: `${out ? 3 : Math.max(level * 100, 6)}%` }}
                />
              </div>
              <p className="mt-1 text-[11px] text-slate-400">
                {product.sku} · reorder at {formatQuantity(threshold)} {UNIT_SHORT[product.unit]}
              </p>
            </li>
          )
        })}
      </ul>
    </section>
  )
}
