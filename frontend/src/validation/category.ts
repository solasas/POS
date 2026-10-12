import { z } from 'zod'
import type { CategoryInput } from '../types/api'

export const categoryFormSchema = z.object({
  name: z.string().trim().min(1, 'Name is required').max(120, 'At most 120 characters'),
  description: z.string().trim().max(1000, 'At most 1000 characters'),
})

export type CategoryFormValues = z.infer<typeof categoryFormSchema>

export function toCategoryInput(values: CategoryFormValues): CategoryInput {
  return { name: values.name, description: values.description || null }
}
