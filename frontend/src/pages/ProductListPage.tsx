import { useEffect, useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router-dom'
import { Button } from '../components/ui/Button'
import { Alert, Badge, EmptyState, Spinner } from '../components/ui/feedback'
import { Pagination } from '../components/ui/Pagination'
import { useToast } from '../hooks/useToast'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import { categoryService } from '../services/categoryService'
import { errorMessage } from '../services/errors'
import { productService } from '../services/productService'
import { useAuthStore } from '../store/authStore'
import { StockAdjustDialog } from '../components/products/StockAdjustDialog'
import type { Product, ProductQuery, ProductSortKey } from '../types/api'
import { formatMoney, formatQuantity, hasRole, UNIT_SHORT } from '../utils/format'

const PAGE_SIZE = 10

const SORT_OPTIONS: { value: ProductSortKey; label: string }[] = [
  { value: 'name', label: 'Name' },
  { value: 'sku', label: 'SKU' },
  { value: 'price', label: 'Price' },
  { value: 'stock', label: 'Stock' },
  { value: 'createdAt', label: 'Newest' },
]

const selectClass =
  'rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:outline-2 focus:outline-emerald-600'

export default function ProductListPage() {
  const [params, setParams] = useSearchParams()
  const queryClient = useQueryClient()
  const toast = useToast()
  const canManage = hasRole(useAuthStore((state) => state.user?.role), 'MANAGER')

  const search = params.get('q') ?? ''
  const categoryId = params.get('category') ?? ''
  const status = params.get('status') ?? 'all'
  const lowStock = params.get('low') === '1'
  const sortBy = (params.get('sort') as ProductSortKey | null) ?? 'name'
  const direction = params.get('dir') === 'desc' ? 'desc' : 'asc'
  const page = Math.max(Number(params.get('page') ?? '1') - 1, 0) || 0

  const [adjusting, setAdjusting] = useState<Product | null>(null)
  const [searchText, setSearchText] = useState(search)
  const debouncedSearch = useDebouncedValue(searchText, 300)

  const update = (changes: Record<string, string | null>, resetPage = true) => {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(changes)) {
      if (value === null || value === '') next.delete(key)
      else next.set(key, value)
    }
    if (resetPage) next.delete('page')
    setParams(next, { replace: true })
  }

  useEffect(() => {
    if (debouncedSearch !== search) update({ q: debouncedSearch })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedSearch])

  const query: ProductQuery = {
    search: search || undefined,
    categoryId: categoryId || undefined,
    active: status === 'active' ? true : status === 'inactive' ? false : undefined,
    lowStock: lowStock || undefined,
    sortBy,
    direction,
    page,
    size: PAGE_SIZE,
  }

  const products = useQuery({
    queryKey: ['products', query],
    queryFn: () => productService.list(query),
    placeholderData: keepPreviousData,
  })
  const categories = useQuery({
    queryKey: ['categories', 'all'],
    queryFn: () => categoryService.list({ size: 100 }),
  })

  const toggleStatus = useMutation({
    mutationFn: (product: Product) => productService.setActive(product.id, !product.active),
    onSuccess: (updated) => {
      toast.success(`${updated.name} ${updated.active ? 'activated' : 'deactivated'}.`)
      queryClient.invalidateQueries({ queryKey: ['products'] })
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
    onError: (error) => toast.error(errorMessage(error)),
  })

  const handleToggle = (product: Product) => {
    if (product.active && !window.confirm(`Deactivate "${product.name}"?`)) return
    toggleStatus.mutate(product)
  }

  const filtersActive = Boolean(search || categoryId || status !== 'all' || lowStock)

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold text-slate-900">Products</h1>
        {canManage && (
          <Link
            to="/products/new"
            className="rounded-md bg-emerald-700 px-3.5 py-2 text-sm font-medium text-white hover:bg-emerald-800"
          >
            Add product
          </Link>
        )}
      </div>

      <div className="flex flex-wrap items-center gap-3 rounded-lg bg-white p-4 shadow-sm ring-1 ring-slate-200">
        <input
          type="search"
          aria-label="Search products"
          placeholder="Search name, SKU or barcode…"
          value={searchText}
          onChange={(event) => setSearchText(event.target.value)}
          className={`${selectClass} min-w-56 flex-1`}
        />
        <select
          aria-label="Filter by category"
          value={categoryId}
          onChange={(event) => update({ category: event.target.value })}
          className={selectClass}
        >
          <option value="">All categories</option>
          {categories.data?.content.map((category) => (
            <option key={category.id} value={category.id}>
              {category.name}
            </option>
          ))}
        </select>
        <select
          aria-label="Filter by status"
          value={status}
          onChange={(event) => update({ status: event.target.value === 'all' ? null : event.target.value })}
          className={selectClass}
        >
          <option value="all">All statuses</option>
          <option value="active">Active</option>
          <option value="inactive">Inactive</option>
        </select>
        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input
            type="checkbox"
            checked={lowStock}
            onChange={(event) => update({ low: event.target.checked ? '1' : null })}
          />
          Low stock only
        </label>
        <select
          aria-label="Sort by"
          value={sortBy}
          onChange={(event) => update({ sort: event.target.value })}
          className={selectClass}
        >
          {SORT_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              Sort: {option.label}
            </option>
          ))}
        </select>
        <select
          aria-label="Sort direction"
          value={direction}
          onChange={(event) => update({ dir: event.target.value })}
          className={selectClass}
        >
          <option value="asc">Ascending</option>
          <option value="desc">Descending</option>
        </select>
      </div>

      {products.isError && <Alert>Could not load products: {errorMessage(products.error)}</Alert>}
      {products.isPending && <Spinner label="Loading products…" />}

      {products.data && products.data.content.length === 0 && (
        <EmptyState title={filtersActive ? 'No products match your filters' : 'No products yet'}>
          {filtersActive ? (
            <Button
              variant="secondary"
              onClick={() => {
                setSearchText('')
                setParams({}, { replace: true })
              }}
            >
              Clear filters
            </Button>
          ) : canManage ? (
            <Link to="/products/new" className="font-medium text-emerald-700 hover:underline">
              Add your first product
            </Link>
          ) : (
            'Ask a manager to add products.'
          )}
        </EmptyState>
      )}

      {products.data && products.data.content.length > 0 && (
        <>
          <div className="overflow-x-auto rounded-lg bg-white shadow-sm ring-1 ring-slate-200">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th scope="col" className="px-4 py-3">Product</th>
                  <th scope="col" className="hidden px-4 py-3 lg:table-cell">Category</th>
                  <th scope="col" className="px-4 py-3 text-right">Price</th>
                  <th scope="col" className="px-4 py-3 text-right">Stock</th>
                  <th scope="col" className="px-4 py-3">Status</th>
                  {canManage && <th scope="col" className="px-4 py-3 text-right">Actions</th>}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {products.data.content.map((product) => (
                  <tr key={product.id} className={product.active ? '' : 'bg-slate-50 text-slate-500'}>
                    <td className="px-4 py-3">
                      <div className="font-medium text-slate-900">{product.name}</div>
                      <div className="text-xs text-slate-500">
                        {product.sku}
                        {product.barcode ? ` · ${product.barcode}` : ''}
                        <span className="lg:hidden"> · {product.categoryName}</span>
                      </div>
                    </td>
                    <td className="hidden px-4 py-3 lg:table-cell">{product.categoryName}</td>
                    <td className="px-4 py-3 text-right tabular-nums">{formatMoney(product.price)}</td>
                    <td className="px-4 py-3 text-right whitespace-nowrap tabular-nums">
                      {formatQuantity(product.stockQuantity)} {UNIT_SHORT[product.unit]}
                      {product.lowStock && (
                        <div className="mt-1">
                          <Badge tone="amber">Low stock</Badge>
                        </div>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <Badge tone={product.active ? 'green' : 'gray'}>
                        {product.active ? 'Active' : 'Inactive'}
                      </Badge>
                    </td>
                    {canManage && (
                      <td className="px-4 py-3 text-right whitespace-nowrap">
                        <div className="flex flex-col items-end gap-1 lg:flex-row lg:items-center lg:justify-end lg:gap-2">
                          <Link
                            to={`/products/${product.id}/edit`}
                            className="font-medium text-emerald-700 hover:underline"
                          >
                            Edit
                          </Link>
                          <Button variant="ghost" className="!px-2 !py-1" onClick={() => setAdjusting(product)}>
                            Stock
                          </Button>
                          <Button
                            variant="ghost"
                            className="!px-2 !py-1"
                            disabled={toggleStatus.isPending}
                            onClick={() => handleToggle(product)}
                          >
                            {product.active ? 'Deactivate' : 'Activate'}
                          </Button>
                        </div>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <Pagination
            page={products.data.page}
            totalPages={products.data.totalPages}
            totalElements={products.data.totalElements}
            onPageChange={(next) => update({ page: String(next + 1) }, false)}
          />
        </>
      )}
      {adjusting && (
        <StockAdjustDialog
          product={adjusting}
          onClose={() => setAdjusting(null)}
          onAdjusted={(stock) => {
            toast.success(`${adjusting.name}: stock is now ${stock}.`)
            setAdjusting(null)
            queryClient.invalidateQueries({ queryKey: ['products'] })
            queryClient.invalidateQueries({ queryKey: ['dashboard'] })
            queryClient.invalidateQueries({ queryKey: ['pos'] })
          }}
        />
      )}
    </div>
  )
}
