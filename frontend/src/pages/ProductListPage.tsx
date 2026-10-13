import { useEffect, useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router-dom'
import { Button } from '../components/ui/Button'
import { Alert, Badge, EmptyState, TableSkeleton } from '../components/ui/feedback'
import { Icon } from '../components/ui/Icon'
import { PageHeader } from '../components/ui/PageHeader'
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
  const selectClass =
    'h-10 rounded-[10px] border border-slate-300 bg-card px-3 text-sm text-ink transition hover:border-slate-400 focus:border-emerald-600 focus:ring-4 focus:ring-emerald-600/15 focus:outline-none'

  return (
    <div>
      <PageHeader
        eyebrow="Catalog"
        title="Products"
        description="Everything you sell, with live stock and price."
        actions={
          canManage && (
            <Link
              to="/products/new"
              className="inline-flex h-10 items-center gap-2 rounded-[10px] bg-emerald-700 px-4 text-sm font-medium text-white shadow-[inset_0_-2px_0_rgb(0_0_0/0.18)] hover:bg-emerald-800"
            >
              <Icon name="plus" size={17} strokeWidth={2.25} />
              Add product
            </Link>
          )
        }
      />

      <div className="card mb-5 flex flex-wrap items-center gap-3 p-3.5">
        <div className="relative min-w-60 flex-1">
          <Icon name="search" size={18} className="pointer-events-none absolute top-1/2 left-3.5 -translate-y-1/2 text-slate-400" />
          <input
            type="search"
            aria-label="Search products"
            placeholder="Search name, SKU or barcode…"
            value={searchText}
            onChange={(event) => setSearchText(event.target.value)}
            className={`${selectClass} w-full pl-10`}
          />
        </div>
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
        <label className="flex h-10 cursor-pointer items-center gap-2 rounded-[10px] border border-slate-300 bg-card px-3 text-sm text-slate-700 transition hover:border-slate-400 has-[:checked]:border-amber-400 has-[:checked]:bg-amber-50 has-[:checked]:text-amber-900">
          <input
            type="checkbox"
            checked={lowStock}
            onChange={(event) => update({ low: event.target.checked ? '1' : null })}
            className="h-4 w-4 accent-amber-500"
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
      {products.isPending && <TableSkeleton rows={7} label="Loading products…" />}

      {products.data && products.data.content.length === 0 && (
        <EmptyState
          title={filtersActive ? 'No products match your filters' : 'No products yet'}
          illustration={filtersActive ? 'search' : 'basket'}
        >
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
          <div className="card overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-[11px] font-semibold tracking-[0.08em] text-slate-500 uppercase">
                  <th scope="col" className="px-5 py-3.5">Product</th>
                  <th scope="col" className="hidden px-4 py-3.5 lg:table-cell">Category</th>
                  <th scope="col" className="px-4 py-3.5 text-right">Price</th>
                  <th scope="col" className="px-4 py-3.5 text-right">Stock</th>
                  <th scope="col" className="px-4 py-3.5">Status</th>
                  {canManage && <th scope="col" className="px-5 py-3.5 text-right">Actions</th>}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {products.data.content.map((product) => (
                  <tr
                    key={product.id}
                    className={`transition hover:bg-slate-100/50 ${product.active ? '' : 'bg-slate-100/40 text-slate-500'}`}
                  >
                    <td className="px-5 py-3.5">
                      <div className="font-medium text-ink">{product.name}</div>
                      <div className="mt-0.5 font-mono text-xs text-slate-500">
                        {product.sku}
                        {product.barcode ? ` · ${product.barcode}` : ''}
                        <span className="lg:hidden"> · {product.categoryName}</span>
                      </div>
                    </td>
                    <td className="hidden px-4 py-3.5 text-slate-600 lg:table-cell">{product.categoryName}</td>
                    <td className="num px-4 py-3.5 text-right font-display text-[15px] font-semibold text-ink">
                      {formatMoney(product.price)}
                    </td>
                    <td className="px-4 py-3.5 text-right whitespace-nowrap">
                      <span className="num font-medium text-ink">
                        {formatQuantity(product.stockQuantity)} {UNIT_SHORT[product.unit]}
                      </span>
                      {(product.stockQuantity <= 0 || product.lowStock) && (
                        <div className="mt-1">
                          {product.stockQuantity <= 0 ? (
                            <Badge tone="red">Out of stock</Badge>
                          ) : (
                            <Badge tone="amber">Low stock</Badge>
                          )}
                        </div>
                      )}
                    </td>
                    <td className="px-4 py-3.5">
                      <Badge tone={product.active ? 'green' : 'gray'}>{product.active ? 'Active' : 'Inactive'}</Badge>
                    </td>
                    {canManage && (
                      <td className="px-5 py-3.5 text-right whitespace-nowrap">
                        <div className="flex flex-col items-end gap-0.5 lg:flex-row lg:items-center lg:justify-end lg:gap-1">
                          <Link
                            to={`/products/${product.id}/edit`}
                            className="rounded-md px-2 py-1 text-[13px] font-medium text-emerald-700 hover:bg-emerald-50"
                          >
                            Edit
                          </Link>
                          <button
                            type="button"
                            onClick={() => setAdjusting(product)}
                            className="rounded-md px-2 py-1 text-[13px] font-medium text-slate-600 hover:bg-slate-200/60"
                          >
                            Stock
                          </button>
                          <button
                            type="button"
                            disabled={toggleStatus.isPending}
                            onClick={() => handleToggle(product)}
                            className="rounded-md px-2 py-1 text-[13px] font-medium text-slate-500 hover:bg-slate-200/60 disabled:opacity-40"
                          >
                            {product.active ? 'Deactivate' : 'Activate'}
                          </button>
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
