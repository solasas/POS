import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Button } from '../ui/Button'
import { Alert } from '../ui/feedback'
import { SelectField, TextAreaField, TextField } from '../ui/fields'
import { toApiError } from '../../services/errors'
import { productFormSchema, toProductInput } from '../../validation/product'
import type { ProductFormValues } from '../../validation/product'
import { UNITS } from '../../types/api'
import type { Category, Product, ProductInput } from '../../types/api'
import { UNIT_LABELS } from '../../utils/format'
import { useState } from 'react'

interface ProductFormProps {
  categories: Category[]
  /** Present when editing; absent when creating. */
  product?: Product
  /** Should reject with the original error so server field errors can be shown inline. */
  onSubmit: (input: ProductInput) => Promise<unknown>
  onCancel: () => void
}

const FIELD_NAMES = [
  'sku', 'barcode', 'name', 'description', 'categoryId', 'unit', 'price', 'costPrice', 'lowStockThreshold',
] as const

function defaults(product?: Product): ProductFormValues {
  return {
    sku: product?.sku ?? '',
    barcode: product?.barcode ?? '',
    name: product?.name ?? '',
    description: product?.description ?? '',
    categoryId: product?.categoryId ?? '',
    unit: product?.unit ?? 'PIECE',
    price: product ? String(product.price) : '',
    costPrice: product ? String(product.costPrice) : '0',
    lowStockThreshold: product ? String(product.lowStockThreshold) : '0',
  }
}

export function ProductForm({ categories, product, onSubmit, onCancel }: ProductFormProps) {
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ProductFormValues>({
    resolver: zodResolver(productFormSchema),
    defaultValues: defaults(product),
  })

  const submit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      await onSubmit(toProductInput(values))
    } catch (error) {
      const apiError = toApiError(error)
      let mapped = false
      for (const [field, message] of Object.entries(apiError.fieldErrors)) {
        if ((FIELD_NAMES as readonly string[]).includes(field)) {
          setError(field as (typeof FIELD_NAMES)[number], { type: 'server', message })
          mapped = true
        }
      }
      if (!mapped) setFormError(apiError.message)
    }
  })

  // an inactive category may still be the product's current one, so keep it selectable
  const selectable = categories.filter((c) => c.active || c.id === product?.categoryId)

  return (
    <form onSubmit={submit} noValidate className="space-y-6">
      {formError && <Alert>{formError}</Alert>}

      <div className="grid gap-4 md:grid-cols-2">
        <TextField label="SKU" error={errors.sku?.message} {...register('sku')} />
        <TextField
          label="Barcode"
          hint="Optional. Must be unique when set."
          error={errors.barcode?.message}
          {...register('barcode')}
        />
        <TextField label="Name" error={errors.name?.message} className="md:col-span-2" {...register('name')} />
        <SelectField label="Category" error={errors.categoryId?.message} {...register('categoryId')}>
          <option value="">Select a category…</option>
          {selectable.map((category) => (
            <option key={category.id} value={category.id}>
              {category.name}
              {category.active ? '' : ' (inactive)'}
            </option>
          ))}
        </SelectField>
        <SelectField label="Unit of measurement" error={errors.unit?.message} {...register('unit')}>
          {UNITS.map((unit) => (
            <option key={unit} value={unit}>
              {UNIT_LABELS[unit]}
            </option>
          ))}
        </SelectField>
        <TextField
          label="Selling price"
          inputMode="decimal"
          error={errors.price?.message}
          {...register('price')}
        />
        <TextField
          label="Cost price"
          inputMode="decimal"
          error={errors.costPrice?.message}
          {...register('costPrice')}
        />
        <TextField
          label="Low-stock threshold"
          inputMode="decimal"
          hint="Flagged as low stock when quantity is at or below this."
          error={errors.lowStockThreshold?.message}
          {...register('lowStockThreshold')}
        />
        {product && (
          <TextField
            label="Current stock"
            hint="Stock adjustments arrive in a later release."
            value={String(product.stockQuantity)}
            readOnly
            disabled
          />
        )}
      </div>

      <TextAreaField label="Description" error={errors.description?.message} {...register('description')} />

      <div className="flex gap-3">
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Saving…' : product ? 'Save changes' : 'Create product'}
        </Button>
        <Button variant="secondary" onClick={onCancel} disabled={isSubmitting}>
          Cancel
        </Button>
      </div>
    </form>
  )
}
