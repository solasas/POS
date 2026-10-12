import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../api/client'
import { errorMessage } from '../api/errors'

export default function CategoryManager({ categories }) {
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [error, setError] = useState(null)

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['catalog'] })
  }
  const onError = (err) => setError(errorMessage(err))

  const create = useMutation({
    mutationFn: (newName) => apiClient.post('/catalog/categories', { name: newName }),
    onSuccess: () => {
      setName('')
      setError(null)
      refresh()
    },
    onError,
  })

  const rename = useMutation({
    mutationFn: ({ id, newName }) => apiClient.put(`/catalog/categories/${id}`, { name: newName }),
    onSuccess: () => {
      setError(null)
      refresh()
    },
    onError,
  })

  const remove = useMutation({
    mutationFn: (id) => apiClient.delete(`/catalog/categories/${id}`),
    onSuccess: () => {
      setError(null)
      refresh()
    },
    onError,
  })

  const handleAdd = (event) => {
    event.preventDefault()
    if (name.trim()) create.mutate(name.trim())
  }

  const handleRename = (category) => {
    const newName = window.prompt('Rename category', category.name)
    if (newName && newName.trim() && newName.trim() !== category.name) {
      rename.mutate({ id: category.id, newName: newName.trim() })
    }
  }

  const handleDelete = (category) => {
    if (window.confirm(`Delete category "${category.name}"?`)) remove.mutate(category.id)
  }

  return (
    <section className="rounded-lg bg-white p-6 shadow">
      <h2 className="mb-4 text-lg font-semibold text-slate-900">Categories</h2>

      <form onSubmit={handleAdd} className="mb-4 flex gap-2">
        <input
          value={name}
          onChange={(event) => setName(event.target.value)}
          placeholder="New category"
          maxLength={120}
          className="w-full rounded border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
        />
        <button
          type="submit"
          disabled={create.isPending}
          className="rounded bg-slate-900 px-3 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-50"
        >
          Add
        </button>
      </form>

      {error && <p className="mb-3 text-sm text-red-600">{error}</p>}

      <ul className="divide-y divide-slate-200">
        {categories.map((category) => (
          <li key={category.id} className="flex items-center justify-between py-2 text-sm text-slate-700">
            <span>{category.name}</span>
            <span className="flex gap-3">
              <button onClick={() => handleRename(category)} className="text-slate-600 hover:text-slate-900">
                Rename
              </button>
              <button onClick={() => handleDelete(category)} className="text-red-600 hover:text-red-800">
                Delete
              </button>
            </span>
          </li>
        ))}
        {categories.length === 0 && <li className="py-2 text-sm text-slate-500">No categories yet.</li>}
      </ul>
    </section>
  )
}
