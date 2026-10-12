import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Alert, Badge, Spinner } from '../components/ui/feedback'
import { categoryService } from '../services/categoryService'
import { errorMessage } from '../services/errors'
import { productService } from '../services/productService'
import { formatQuantity, UNIT_SHORT } from '../utils/format'

function Metric({ label, value, to }: { label: string; value: number | undefined; to: string }) {
  return (
    <Link
      to={to}
      className="rounded-lg bg-white p-5 shadow-sm ring-1 ring-slate-200 transition hover:ring-emerald-600"
    >
      <div className="text-sm text-slate-500">{label}</div>
      <div className="mt-1 text-3xl font-semibold text-slate-900">{value ?? '—'}</div>
    </Link>
  )
}

export default function DashboardPage() {
  // every figure below comes from the API; "size: 1" requests only need totalElements
  const totalProducts = useQuery({
    queryKey: ['dashboard', 'products-total'],
    queryFn: () => productService.list({ size: 1 }),
  })
  const activeCategories = useQuery({
    queryKey: ['dashboard', 'categories-active'],
    queryFn: () => categoryService.list({ active: true, size: 1 }),
  })
  const lowStock = useQuery({
    queryKey: ['dashboard', 'low-stock'],
    queryFn: () => productService.list({ active: true, lowStock: true, sortBy: 'stock', size: 5 }),
  })

  const error = totalProducts.error ?? activeCategories.error ?? lowStock.error
  const loading = totalProducts.isPending || activeCategories.isPending || lowStock.isPending

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold text-slate-900">Dashboard</h1>

      {error && <Alert>Could not load dashboard data: {errorMessage(error)}</Alert>}

      <div className="grid gap-4 sm:grid-cols-3">
        <Metric label="Total products" value={totalProducts.data?.totalElements} to="/products" />
        <Metric label="Active categories" value={activeCategories.data?.totalElements} to="/categories" />
        <Metric
          label="Low-stock products"
          value={lowStock.data?.totalElements}
          to="/products?low=1&status=active"
        />
      </div>

      <section className="rounded-lg bg-white p-5 shadow-sm ring-1 ring-slate-200">
        <h2 className="mb-3 text-lg font-semibold text-slate-900">Needs restocking</h2>
        {loading && <Spinner />}
        {lowStock.data && lowStock.data.content.length === 0 && (
          <p className="text-sm text-slate-500">No active products are at or below their low-stock threshold.</p>
        )}
        {lowStock.data && lowStock.data.content.length > 0 && (
          <ul className="divide-y divide-slate-100">
            {lowStock.data.content.map((product) => (
              <li key={product.id} className="flex items-center justify-between py-2 text-sm">
                <span className="text-slate-800">
                  {product.name} <span className="text-slate-400">· {product.sku}</span>
                </span>
                <Badge tone="amber">
                  {formatQuantity(product.stockQuantity)} {UNIT_SHORT[product.unit]} left
                </Badge>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}
