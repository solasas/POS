import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../api/client'

export default function CatalogPage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['catalog', 'products'],
    queryFn: async () => {
      const { data } = await apiClient.get('/catalog/products')
      return data
    },
  })

  return (
    <div className="rounded-lg bg-white p-6 shadow">
      <h1 className="mb-4 text-xl font-semibold text-slate-900">Catalog</h1>
      {isLoading && <p className="text-slate-600">Loading products…</p>}
      {isError && <p className="text-red-600">Failed to load products.</p>}
      {data && (
        <ul className="divide-y divide-slate-200">
          {data.map((product) => (
            <li key={product.id} className="py-2 text-slate-700">
              {product.name}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
