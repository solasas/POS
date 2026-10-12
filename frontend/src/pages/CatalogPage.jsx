import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../api/client'
import { errorMessage } from '../api/errors'
import { useAuthStore } from '../store/authStore'
import CategoryManager from '../components/CategoryManager'
import ProductForm from '../components/ProductForm'

const MANAGER_ROLES = ['MANAGER', 'ADMIN']

const money = new Intl.NumberFormat(undefined, { style: 'currency', currency: 'USD' })

export default function CatalogPage() {
  const queryClient = useQueryClient()
  const role = useAuthStore((state) => state.user?.role)
  const canManage = MANAGER_ROLES.includes(role)

  // null = form closed, {} = creating, product object = editing
  const [editing, setEditing] = useState(null)
  const [actionError, setActionError] = useState(null)

  const products = useQuery({
    queryKey: ['catalog', 'products'],
    queryFn: async () => (await apiClient.get('/catalog/products')).data,
  })
  const categories = useQuery({
    queryKey: ['catalog', 'categories'],
    queryFn: async () => (await apiClient.get('/catalog/categories')).data,
  })

  const save = useMutation({
    mutationFn: (payload) =>
      editing?.id
        ? apiClient.put(`/catalog/products/${editing.id}`, payload)
        : apiClient.post('/catalog/products', payload),
    onSuccess: () => {
      setEditing(null)
      queryClient.invalidateQueries({ queryKey: ['catalog'] })
    },
  })

  const remove = useMutation({
    mutationFn: (id) => apiClient.delete(`/catalog/products/${id}`),
    onSuccess: () => {
      setActionError(null)
      queryClient.invalidateQueries({ queryKey: ['catalog'] })
    },
    onError: (error) => setActionError(errorMessage(error)),
  })

  const handleDelete = (product) => {
    if (window.confirm(`Delete "${product.name}"?`)) remove.mutate(product.id)
  }

  const categoryList = categories.data ?? []

  return (
    <div className="space-y-6">
      <section className="rounded-lg bg-white p-6 shadow">
        <div className="mb-4 flex items-center justify-between">
          <h1 className="text-xl font-semibold text-slate-900">Catalog</h1>
          {canManage && !editing && (
            <button
              onClick={() => {
                save.reset()
                setEditing({})
              }}
              disabled={categoryList.length === 0}
              title={categoryList.length === 0 ? 'Create a category first' : undefined}
              className="rounded bg-slate-900 px-3 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-50"
            >
              Add product
            </button>
          )}
        </div>

        {editing && (
          <ProductForm
            key={editing.id ?? 'new'}
            product={editing.id ? editing : null}
            categories={categoryList}
            onSubmit={(payload) => save.mutate(payload)}
            onCancel={() => setEditing(null)}
            isPending={save.isPending}
            error={save.isError ? errorMessage(save.error, 'Failed to save product.') : null}
          />
        )}

        {actionError && <p className="mb-3 text-sm text-red-600">{actionError}</p>}
        {products.isLoading && <p className="text-slate-600">Loading products…</p>}
        {products.isError && <p className="text-red-600">Failed to load products.</p>}

        {products.data && (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-slate-700">
              <thead className="border-b border-slate-200 text-xs uppercase text-slate-500">
                <tr>
                  <th className="py-2 pr-4">SKU</th>
                  <th className="py-2 pr-4">Name</th>
                  <th className="py-2 pr-4">Category</th>
                  <th className="py-2 pr-4">Unit</th>
                  <th className="py-2 pr-4 text-right">Price</th>
                  <th className="py-2 pr-4">Tax</th>
                  <th className="py-2 pr-4">18+</th>
                  {canManage && <th className="py-2" />}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {products.data.map((product) => (
                  <tr key={product.id}>
                    <td className="py-2 pr-4 font-mono text-xs">{product.sku}</td>
                    <td className="py-2 pr-4">{product.name}</td>
                    <td className="py-2 pr-4">{product.categoryName}</td>
                    <td className="py-2 pr-4">{product.unitType === 'WEIGHT' ? 'Weight' : 'Each'}</td>
                    <td className="py-2 pr-4 text-right">{money.format(product.price)}</td>
                    <td className="py-2 pr-4">{product.taxClass}</td>
                    <td className="py-2 pr-4">{product.ageRestricted ? 'Yes' : ''}</td>
                    {canManage && (
                      <td className="space-x-3 py-2 text-right">
                        <button
                          onClick={() => {
                            save.reset()
                            setEditing(product)
                          }}
                          className="text-slate-600 hover:text-slate-900"
                        >
                          Edit
                        </button>
                        <button onClick={() => handleDelete(product)} className="text-red-600 hover:text-red-800">
                          Delete
                        </button>
                      </td>
                    )}
                  </tr>
                ))}
                {products.data.length === 0 && (
                  <tr>
                    <td colSpan={canManage ? 8 : 7} className="py-4 text-slate-500">
                      No products yet.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {canManage && <CategoryManager categories={categoryList} />}
    </div>
  )
}
