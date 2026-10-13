import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CategoryForm } from '../components/categories/CategoryForm'
import { useToast } from '../hooks/useToast'
import { Button } from '../components/ui/Button'
import { Alert, Badge, EmptyState, TableSkeleton } from '../components/ui/feedback'
import { Icon } from '../components/ui/Icon'
import { PageHeader } from '../components/ui/PageHeader'
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

  const field =
    'h-10 rounded-[10px] border border-slate-300 bg-card px-3 text-sm text-ink transition hover:border-slate-400 focus:border-emerald-600 focus:ring-4 focus:ring-emerald-600/15 focus:outline-none'

  return (
    <div>
      <PageHeader
        eyebrow="Catalog"
        title="Categories"
        description="Group products so they are easy to find and report on."
        actions={
          canManage &&
          editing === undefined && (
            <Button onClick={() => setEditing(null)}>
              <Icon name="plus" size={17} strokeWidth={2.25} />
              Add category
            </Button>
          )
        }
      />

      {editing !== undefined && (
        <div className="card animate-rise mb-5 p-6">
          <h2 className="mb-4 font-display text-lg font-bold text-ink">
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

      <div className="card mb-5 flex flex-wrap gap-3 p-3.5">
        <div className="relative min-w-56 flex-1">
          <Icon name="search" size={18} className="pointer-events-none absolute top-1/2 left-3.5 -translate-y-1/2 text-slate-400" />
          <input
            type="search"
            aria-label="Search categories"
            placeholder="Search categories…"
            value={search}
            onChange={(event) => {
              setSearch(event.target.value)
              setPage(0)
            }}
            className={`${field} w-full pl-10`}
          />
        </div>
        <select
          aria-label="Filter by status"
          value={status}
          onChange={(event) => {
            setStatus(event.target.value as typeof status)
            setPage(0)
          }}
          className={field}
        >
          <option value="all">All statuses</option>
          <option value="active">Active</option>
          <option value="inactive">Inactive</option>
        </select>
      </div>

      {categories.isError && <Alert>Could not load categories: {errorMessage(categories.error)}</Alert>}
      {categories.isPending && <TableSkeleton rows={5} label="Loading categories…" />}

      {categories.data && categories.data.content.length === 0 && (
        <EmptyState
          title={search || status !== 'all' ? 'No categories match your filters' : 'No categories yet'}
          illustration={search || status !== 'all' ? 'search' : 'basket'}
        >
          {canManage && !search && status === 'all' && 'Create a category to start organising products.'}
        </EmptyState>
      )}

      {categories.data && categories.data.content.length > 0 && (
        <>
          <div className="card overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-[11px] font-semibold tracking-[0.08em] text-slate-500 uppercase">
                  <th scope="col" className="px-5 py-3.5">Name</th>
                  <th scope="col" className="hidden px-4 py-3.5 md:table-cell">Description</th>
                  <th scope="col" className="px-4 py-3.5">Status</th>
                  {canManage && <th scope="col" className="px-5 py-3.5 text-right">Actions</th>}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {categories.data.content.map((category) => (
                  <tr
                    key={category.id}
                    className={`transition hover:bg-slate-100/50 ${category.active ? '' : 'bg-slate-100/40 text-slate-500'}`}
                  >
                    <td className="px-5 py-3.5 font-medium text-ink">{category.name}</td>
                    <td className="hidden px-4 py-3.5 text-slate-600 md:table-cell">{category.description ?? '—'}</td>
                    <td className="px-4 py-3.5">
                      <Badge tone={category.active ? 'green' : 'gray'}>
                        {category.active ? 'Active' : 'Inactive'}
                      </Badge>
                    </td>
                    {canManage && (
                      <td className="px-5 py-3.5 text-right whitespace-nowrap">
                        <button
                          type="button"
                          onClick={() => setEditing(category)}
                          className="rounded-md px-2 py-1 text-[13px] font-medium text-emerald-700 hover:bg-emerald-50"
                        >
                          Edit
                        </button>
                        <button
                          type="button"
                          disabled={toggle.isPending}
                          onClick={() => toggle.mutate(category)}
                          className="rounded-md px-2 py-1 text-[13px] font-medium text-slate-500 hover:bg-slate-200/60 disabled:opacity-40"
                        >
                          {category.active ? 'Deactivate' : 'Activate'}
                        </button>
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
