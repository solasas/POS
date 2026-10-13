import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ProductForm } from '../components/products/ProductForm'
import { useToast } from '../hooks/useToast'
import { Alert, Spinner } from '../components/ui/feedback'
import { Icon } from '../components/ui/Icon'
import { PageHeader } from '../components/ui/PageHeader'
import { categoryService } from '../services/categoryService'
import { errorMessage } from '../services/errors'
import { productService } from '../services/productService'
import type { ProductInput } from '../types/api'

/** Serves both /products/new and /products/:id/edit. */
export default function ProductFormPage() {
  const { id } = useParams<{ id: string }>()
  const isEdit = Boolean(id)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const toast = useToast()

  const categories = useQuery({
    queryKey: ['categories', 'all'],
    queryFn: () => categoryService.list({ size: 100 }),
  })
  const product = useQuery({
    queryKey: ['product', id],
    queryFn: () => productService.get(id as string),
    enabled: isEdit,
  })

  const save = useMutation({
    mutationFn: (input: ProductInput) =>
      isEdit ? productService.update(id as string, input) : productService.create(input),
    onSuccess: (saved) => {
      toast.success(isEdit ? `${saved.name} updated.` : `${saved.name} created.`)
      queryClient.invalidateQueries({ queryKey: ['products'] })
      queryClient.invalidateQueries({ queryKey: ['product', saved.id] })
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      navigate('/products')
    },
  })

  const loadError = categories.error ?? product.error
  const loading = categories.isPending || (isEdit && product.isPending)

  return (
    <div className="mx-auto max-w-3xl">
      <Link
        to="/products"
        className="mb-4 inline-flex items-center gap-1.5 text-sm font-medium text-slate-600 hover:text-ink"
      >
        <Icon name="arrowLeft" size={16} />
        Back to products
      </Link>
      <PageHeader
        eyebrow="Catalog"
        title={isEdit ? 'Edit product' : 'Add product'}
        description={
          isEdit
            ? 'Changes apply to future sales; past receipts keep what was sold.'
            : 'New products start with zero stock. Add stock from the product list.'
        }
      />

      {loadError && <Alert>Could not load the form: {errorMessage(loadError)}</Alert>}
      {loading && !loadError && <Spinner />}

      {!loading && !loadError && categories.data && (
        <div className="card p-6 sm:p-8">
          {!isEdit && categories.data.content.filter((c) => c.active).length === 0 ? (
            <Alert tone="info">
              Create an active category first on the <Link to="/categories" className="underline">Categories</Link> page.
            </Alert>
          ) : (
            <ProductForm
              categories={categories.data.content}
              product={product.data}
              onSubmit={(input) => save.mutateAsync(input)}
              onCancel={() => navigate('/products')}
            />
          )}
        </div>
      )}
    </div>
  )
}
