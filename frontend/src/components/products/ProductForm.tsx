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
import type { ReactNode } from 'react'

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

function Section({ title, description, children }: { title: string; description: string; children: ReactNode }) {
  return (
    <section>
      <h2 className="font-display text-lg font-bold text-ink">{title}</h2>
      <p className="mt-0.5 mb-4 text-sm text-slate-500">{description}</p>
      {children}
    </section>
  )
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
    <form onSubmit={submit} noValidate className="space-y-9">
      {formError && <Alert>{formError}</Alert>}

      <Section title="Identity" description="How this product is named and found at the till.">
        <div className="grid gap-4 md:grid-cols-2">
          <TextField label="SKU" error={errors.sku?.message} className="font-mono" {...register('sku')} />
          <TextField
            label="Barcode"
            hint="Optional. Must be unique when set."
            error={errors.barcode?.message}
            className="font-mono"
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
        </div>
        <div className="mt-4">
          <TextAreaField label="Description" error={errors.description?.message} {...register('description')} />
        </div>
      </Section>

      <Section title="Pricing" description="Selling price is what the customer pays; cost price powers margin reports.">
        <div className="grid gap-4 md:grid-cols-2">
          <TextField
            label="Selling price"
            hint="In rupees (₹), charged per unit."
            inputMode="decimal"
            error={errors.price?.message}
            {...register('price')}
          />
          <TextField
            label="Cost price"
            hint="In rupees (₹), what you pay per unit."
            inputMode="decimal"
            error={errors.costPrice?.message}
            {...register('costPrice')}
          />
        </div>
      </Section>

      <Section title="Stock" description="When quantity falls to the threshold, the product is flagged for restocking.">
        <div className="grid gap-4 md:grid-cols-2">
          <TextField
            label="Low-stock threshold"
            inputMode="decimal"
            error={errors.lowStockThreshold?.message}
            {...register('lowStockThreshold')}
          />
          {product && (
            <TextField
              label="Current stock"
              hint="Change stock from the product list (Stock)."
              value={String(product.stockQuantity)}
              readOnly
              disabled
            />
          )}
        </div>
      </Section>

      <div className="flex gap-3 border-t border-slate-200 pt-6">
        <Button type="submit" size="lg" disabled={isSubmitting}>
          {isSubmitting ? 'Saving…' : product ? 'Save changes' : 'Create product'}
        </Button>
        <Button variant="secondary" size="lg" onClick={onCancel} disabled={isSubmitting}>
          Cancel
        </Button>
      </div>
    </form>
  )
}
