import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { apiClient } from '../api/client'
import { useAuthStore } from '../store/authStore'

export default function LoginPage() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const login = useAuthStore((state) => state.login)
  const navigate = useNavigate()

  const mutation = useMutation({
    mutationFn: async () => {
      const { data } = await apiClient.post('/auth/login', { username, password })
      return data
    },
    onSuccess: (data) => {
      login(data.user, data.token)
      navigate('/')
    },
  })

  const handleSubmit = (event) => {
    event.preventDefault()
    mutation.mutate()
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-100">
      <form
        onSubmit={handleSubmit}
        className="w-full max-w-sm rounded-lg bg-white p-8 shadow"
      >
        <h1 className="mb-6 text-xl font-semibold text-slate-900">Sign in</h1>

        <label className="mb-1 block text-sm font-medium text-slate-700">Username</label>
        <input
          type="text"
          value={username}
          onChange={(event) => setUsername(event.target.value)}
          className="mb-4 w-full rounded border border-slate-300 px-3 py-2 focus:border-slate-500 focus:outline-none"
          required
        />

        <label className="mb-1 block text-sm font-medium text-slate-700">Password</label>
        <input
          type="password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          className="mb-6 w-full rounded border border-slate-300 px-3 py-2 focus:border-slate-500 focus:outline-none"
          required
        />

        {mutation.isError && (
          <p className="mb-4 text-sm text-red-600">Invalid username or password.</p>
        )}

        <button
          type="submit"
          disabled={mutation.isPending}
          className="w-full rounded bg-slate-900 py-2 font-medium text-white hover:bg-slate-800 disabled:opacity-50"
        >
          {mutation.isPending ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </div>
  )
}
