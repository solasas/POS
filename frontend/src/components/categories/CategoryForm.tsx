import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Button } from '../ui/Button'
import { Alert } from '../ui/feedback'
import { TextAreaField, TextField } from '../ui/fields'
import { toApiError } from '../../services/errors'
import { categoryFormSchema, toCategoryInput } from '../../validation/category'
import type { CategoryFormValues } from '../../validation/category'
import type { Category, CategoryInput } from '../../types/api'

interface CategoryFormProps {
  category?: Category
  onSubmit: (input: CategoryInput) => Promise<unknown>
  onCancel: () => void
}

export function CategoryForm({ category, onSubmit, onCancel }: CategoryFormProps) {
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CategoryFormValues>({
    resolver: zodResolver(categoryFormSchema),
    defaultValues: { name: category?.name ?? '', description: category?.description ?? '' },
  })

  const submit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      await onSubmit(toCategoryInput(values))
    } catch (error) {
      const apiError = toApiError(error)
      if (apiError.fieldErrors.name) {
        setError('name', { type: 'server', message: apiError.fieldErrors.name })
      } else if (apiError.fieldErrors.description) {
        setError('description', { type: 'server', message: apiError.fieldErrors.description })
      } else {
        setFormError(apiError.message)
      }
    }
  })

  return (
    <form onSubmit={submit} noValidate className="space-y-4">
      {formError && <Alert>{formError}</Alert>}
      <TextField label="Name" error={errors.name?.message} {...register('name')} />
      <TextAreaField label="Description" error={errors.description?.message} {...register('description')} />
      <div className="flex gap-3">
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Saving…' : category ? 'Save changes' : 'Create category'}
        </Button>
        <Button variant="secondary" onClick={onCancel} disabled={isSubmitting}>
          Cancel
        </Button>
      </div>
    </form>
  )
}
