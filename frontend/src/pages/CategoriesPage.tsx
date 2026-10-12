import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CategoryForm } from '../components/categories/CategoryForm'
import { useToast } from '../hooks/useToast'
import { Button } from '../components/ui/Button'
import { Alert, Badge, EmptyState, Spinner } from '../components/ui/feedback'
import { Pagination } from '../components/ui/Pagination'
import { categoryService } from '../services/categoryService'
import { errorMessage } from '../services/errors'
import { useAuthStore } from '../store/authStore'
import type { Category, CategoryInput } from '../types/api'
import { hasRole } from '../utils/format'

const PAGE_SIZE = 10

export default function CategoriesPage() {
  const queryClient = useQueryClient()
  const toast = useToast()
  const canManage = hasRole(useAuthStore((state) => state.user?.role), 'MANAGER')

  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<'all' | 'active' | 'inactive'>('all')
  // undefined = form closed, null = creating, Category = editing
  const [editing, setEditing] = useState<Category | null | undefined>(undefined)

  const query = {
    search: search || undefined,
    active: status === 'all' ? undefined : status === 'active',
    page,
    size: PAGE_SIZE,
  }
  const categories = useQuery({
    queryKey: ['categories', query],
    queryFn: () => categoryService.list(query),
    placeholderData: keepPreviousData,
  })

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['categories'] })
    queryClient.invalidateQueries({ queryKey: ['dashboard'] })
  }

  const save = useMutation({
    mutationFn: (input: CategoryInput) =>
      editing ? categoryService.update(editing.id, input) : categoryService.create(input),
    onSuccess: (saved) => {
      toast.success(`Category "${saved.name}" saved.`)
      setEditing(undefined)
      refresh()
    },
  })

  const toggle = useMutation({
    mutationFn: (category: Category) => categoryService.setActive(category.id, !category.active),
    onSuccess: (updated) => {
      toast.success(`${updated.name} ${updated.active ? 'activated' : 'deactivated'}.`)
      refresh()
    },
    onError: (error) => toast.error(errorMessage(error)),
  })

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold text-slate-900">Categories</h1>
        {canManage && editing === undefined && (
          <Button onClick={() => setEditing(null)}>Add category</Button>
        )}
      </div>

      {editing !== undefined && (
        <div className="rounded-lg bg-white p-5 shadow-sm ring-1 ring-slate-200">
          <h2 className="mb-4 text-lg font-semibold text-slate-900">
            {editing ? 'Edit category' : 'New category'}
          </h2>
          <CategoryForm
            key={editing?.id ?? 'new'}
            category={editing ?? undefined}
            onSubmit={(input) => save.mutateAsync(input)}
            onCancel={() => setEditing(undefined)}
          />
        </div>
      )}

      <div className="flex flex-wrap gap-3 rounded-lg bg-white p-4 shadow-sm ring-1 ring-slate-200">
        <input
          type="search"
          aria-label="Search categories"
          placeholder="Search categories…"
          value={search}
          onChange={(event) => {
            setSearch(event.target.value)
            setPage(0)
          }}
          className="min-w-56 flex-1 rounded-md border border-slate-300 px-3 py-2 text-sm focus:outline-2 focus:outline-emerald-600"
        />
        <select
          aria-label="Filter by status"
          value={status}
          onChange={(event) => {
            setStatus(event.target.value as typeof status)
            setPage(0)
          }}
          className="rounded-md border border-slate-300 bg-white px-3 py-2 text-sm"
        >
          <option value="all">All statuses</option>
          <option value="active">Active</option>
          <option value="inactive">Inactive</option>
        </select>
      </div>

      {categories.isError && <Alert>Could not load categories: {errorMessage(categories.error)}</Alert>}
      {categories.isPending && <Spinner label="Loading categories…" />}

      {categories.data && categories.data.content.length === 0 && (
        <EmptyState title={search || status !== 'all' ? 'No categories match your filters' : 'No categories yet'}>
          {canManage && !search && status === 'all' && 'Create a category to start organising products.'}
        </EmptyState>
      )}

      {categories.data && categories.data.content.length > 0 && (
        <>
          <div className="overflow-x-auto rounded-lg bg-white shadow-sm ring-1 ring-slate-200">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th scope="col" className="px-4 py-3">Name</th>
                  <th scope="col" className="px-4 py-3">Description</th>
                  <th scope="col" className="px-4 py-3">Status</th>
                  {canManage && <th scope="col" className="px-4 py-3 text-right">Actions</th>}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {categories.data.content.map((category) => (
                  <tr key={category.id} className={category.active ? '' : 'bg-slate-50 text-slate-500'}>
                    <td className="px-4 py-3 font-medium text-slate-900">{category.name}</td>
                    <td className="px-4 py-3">{category.description ?? '—'}</td>
                    <td className="px-4 py-3">
                      <Badge tone={category.active ? 'green' : 'gray'}>
                        {category.active ? 'Active' : 'Inactive'}
                      </Badge>
                    </td>
                    {canManage && (
                      <td className="space-x-2 px-4 py-3 text-right whitespace-nowrap">
                        <Button variant="ghost" onClick={() => setEditing(category)}>
                          Edit
                        </Button>
                        <Button
                          variant="ghost"
                          disabled={toggle.isPending}
                          onClick={() => toggle.mutate(category)}
                        >
                          {category.active ? 'Deactivate' : 'Activate'}
                        </Button>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination
            page={categories.data.page}
            totalPages={categories.data.totalPages}
            totalElements={categories.data.totalElements}
            onPageChange={setPage}
          />
        </>
      )}
    </div>
  )
}
