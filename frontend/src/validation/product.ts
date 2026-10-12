import { z } from 'zod'
import { UNITS } from '../types/api'
import type { ProductInput } from '../types/api'

/** Numeric fields are edited as strings; they are validated here and converted in toProductInput. */
const decimal = (maxDecimals: number) =>
  z
    .string()
    .trim()
    .min(1, 'Required')
    .regex(
      new RegExp(`^\\d{1,9}(\\.\\d{1,${maxDecimals}})?$`),
      `Enter a number with up to ${maxDecimals} decimal places`,
    )

export const productFormSchema = z.object({
  sku: z
    .string()
    .trim()
    .min(1, 'SKU is required')
    .max(64, 'At most 64 characters')
    .regex(/^[A-Za-z0-9._-]+$/, "Only letters, digits, '.', '_' and '-'"),
  barcode: z
    .string()
    .trim()
    .max(64, 'At most 64 characters')
    .regex(/^[A-Za-z0-9-]*$/, "Only letters, digits and '-'"),
  name: z.string().trim().min(1, 'Name is required').max(255, 'At most 255 characters'),
  description: z.string().trim().max(1000, 'At most 1000 characters'),
  categoryId: z.string().min(1, 'Select a category'),
  unit: z.enum(UNITS),
  price: decimal(2).refine((v) => Number(v) > 0, 'Must be greater than 0'),
  costPrice: decimal(2),
  lowStockThreshold: decimal(3),
})

export type ProductFormValues = z.infer<typeof productFormSchema>

export function toProductInput(values: ProductFormValues): ProductInput {
  return {
    sku: values.sku,
    barcode: values.barcode || null,
    name: values.name,
    description: values.description || null,
    categoryId: values.categoryId,
    unit: values.unit,
    price: Number(values.price),
    costPrice: Number(values.costPrice),
    lowStockThreshold: Number(values.lowStockThreshold),
  }
}
