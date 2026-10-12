import { useState } from 'react'

const emptyProduct = {
  sku: '',
  barcode: '',
  name: '',
  categoryId: '',
  unitType: 'EACH',
  price: '',
  taxClass: 'STANDARD',
  ageRestricted: false,
  reorderPoint: 0,
}

const inputClass =
  'w-full rounded border border-slate-300 px-3 py-2 focus:border-slate-500 focus:outline-none'

function Field({ label, children }) {
  return (
    <label className="block text-sm font-medium text-slate-700">
      <span className="mb-1 block">{label}</span>
      {children}
    </label>
  )
}

export default function ProductForm({ product, categories, onSubmit, onCancel, isPending, error }) {
  const [form, setForm] = useState(
    product
      ? { ...emptyProduct, ...product, barcode: product.barcode ?? '' }
      : { ...emptyProduct, categoryId: categories[0]?.id ?? '' },
  )

  const set = (key) => (event) => {
    const value = event.target.type === 'checkbox' ? event.target.checked : event.target.value
    setForm((current) => ({ ...current, [key]: value }))
  }

  const handleSubmit = (event) => {
    event.preventDefault()
    onSubmit({
      sku: form.sku.trim(),
      barcode: form.barcode.trim() || null,
      name: form.name.trim(),
      categoryId: form.categoryId,
      unitType: form.unitType,
      price: form.price === '' ? null : Number(form.price),
      taxClass: form.taxClass.trim(),
      ageRestricted: form.ageRestricted,
      reorderPoint: Number(form.reorderPoint) || 0,
    })
  }

  return (
    <form onSubmit={handleSubmit} className="mb-6 rounded-lg border border-slate-200 bg-slate-50 p-4">
      <h2 className="mb-4 text-lg font-semibold text-slate-900">
        {product ? 'Edit product' : 'New product'}
      </h2>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <Field label="SKU">
          <input className={inputClass} value={form.sku} onChange={set('sku')} maxLength={64} required />
        </Field>
        <Field label="Barcode (optional)">
          <input className={inputClass} value={form.barcode} onChange={set('barcode')} maxLength={64} />
        </Field>
        <Field label="Name">
          <input className={inputClass} value={form.name} onChange={set('name')} maxLength={255} required />
        </Field>
        <Field label="Category">
          <select className={inputClass} value={form.categoryId} onChange={set('categoryId')} required>
            <option value="" disabled>
              Select a category
            </option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Unit">
          <select className={inputClass} value={form.unitType} onChange={set('unitType')}>
            <option value="EACH">Each</option>
            <option value="WEIGHT">Weight</option>
          </select>
        </Field>
        <Field label="Price">
          <input
            className={inputClass}
            type="number"
            min="0"
            step="0.01"
            value={form.price}
            onChange={set('price')}
            required
          />
        </Field>
        <Field label="Tax class">
          <input className={inputClass} value={form.taxClass} onChange={set('taxClass')} maxLength={32} required />
        </Field>
        <Field label="Reorder point">
          <input
            className={inputClass}
            type="number"
            min="0"
            step="1"
            value={form.reorderPoint}
            onChange={set('reorderPoint')}
          />
        </Field>
        <label className="flex items-center gap-2 self-end pb-2 text-sm font-medium text-slate-700">
          <input type="checkbox" checked={form.ageRestricted} onChange={set('ageRestricted')} />
          Age restricted
        </label>
      </div>

      {error && <p className="mt-4 text-sm text-red-600">{error}</p>}

      <div className="mt-4 flex gap-2">
        <button
          type="submit"
          disabled={isPending}
          className="rounded bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-50"
        >
          {isPending ? 'Saving…' : 'Save'}
        </button>
        <button
          type="button"
          onClick={onCancel}
          className="rounded border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-white"
        >
          Cancel
        </button>
      </div>
    </form>
  )
}
