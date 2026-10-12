import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { Button } from '../components/ui/Button'
import { Alert } from '../components/ui/feedback'
import { TextField } from '../components/ui/fields'
import { authService } from '../services/authService'
import { toApiError } from '../services/errors'
import { useAuthStore } from '../store/authStore'
import { loginSchema } from '../validation/auth'
import type { LoginFormValues } from '../validation/auth'

export default function LoginPage() {
  const token = useAuthStore((state) => state.token)
  const sessionExpired = useAuthStore((state) => state.sessionExpired)
  const login = useAuthStore((state) => state.login)
  const navigate = useNavigate()
  const location = useLocation()
  const [formError, setFormError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
  })

  if (token) {
    return <Navigate to="/" replace />
  }

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      const response = await authService.login(values.email, values.password)
      login(response.token, { ...response.user, email: values.email })
      const from = (location.state as { from?: string } | null)?.from
      navigate(from && from !== '/login' ? from : '/', { replace: true })
    } catch (error) {
      const apiError = toApiError(error)
      setFormError(
        apiError.status === 401 ? 'Invalid email or password.' : apiError.message,
      )
    }
  })

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-100 px-4">
      <form
        onSubmit={onSubmit}
        noValidate
        className="w-full max-w-sm space-y-4 rounded-xl bg-white p-8 shadow-sm ring-1 ring-slate-200"
      >
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Stockora</h1>
          <p className="mt-1 text-sm text-slate-500">Sign in to manage your store.</p>
        </div>

        {sessionExpired && !formError && (
          <Alert tone="info">Your session has expired. Please sign in again.</Alert>
        )}
        {formError && <Alert>{formError}</Alert>}

        <TextField
          label="Email"
          type="email"
          autoComplete="username"
          error={errors.email?.message}
          {...register('email')}
        />
        <TextField
          label="Password"
          type="password"
          autoComplete="current-password"
          error={errors.password?.message}
          {...register('password')}
        />

        <Button type="submit" className="w-full" disabled={isSubmitting}>
          {isSubmitting ? 'Signing in…' : 'Sign in'}
        </Button>
      </form>
    </div>
  )
}
