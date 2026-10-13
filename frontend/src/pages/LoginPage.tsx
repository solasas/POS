import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { Button } from '../components/ui/Button'
import { Alert } from '../components/ui/feedback'
import { TextField } from '../components/ui/fields'
import { Wordmark, LogoMark } from '../components/ui/Logo'
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
      setFormError(apiError.status === 401 ? 'Invalid email or password.' : apiError.message)
    }
  })

  return (
    <div className="grid min-h-screen lg:grid-cols-[1.05fr_1fr]">
      {/* brand panel */}
      <aside className="relative hidden overflow-hidden bg-emerald-900 p-12 text-emerald-50 lg:flex lg:flex-col lg:justify-between">
        <div className="barcode-motif absolute inset-0 opacity-70" aria-hidden="true" />
        <div
          className="absolute -right-24 -bottom-24 h-96 w-96 rounded-full bg-amber-400/90 blur-3xl"
          style={{ opacity: 0.16 }}
          aria-hidden="true"
        />
        <div className="relative">
          <Wordmark />
        </div>
        <div className="relative max-w-md">
          <p className="font-display text-[2.75rem] leading-[1.05] font-bold tracking-tight text-white">
            Fast at the counter.
            <br />
            <span className="text-amber-300">Honest</span> about the shelf.
          </p>
          <p className="mt-5 max-w-sm text-[15px] leading-relaxed text-emerald-100/80">
            Every bill priced on the server, every unit of stock accounted for, every receipt reprintable
            exactly as it was sold.
          </p>
        </div>
        <p className="relative text-xs text-emerald-200/60">Point of sale &amp; inventory for the neighbourhood supermarket</p>
      </aside>

      {/* form */}
      <main className="flex items-center justify-center px-6 py-12">
        <form onSubmit={onSubmit} noValidate className="w-full max-w-sm animate-rise">
          <div className="mb-8 lg:hidden">
            <span className="flex items-center gap-2.5">
              <LogoMark size={36} />
              <span className="font-display text-2xl font-bold text-ink">Stockora</span>
            </span>
          </div>
          <h1 className="font-display text-[2rem] leading-tight font-bold text-ink">Welcome back</h1>
          <p className="mt-1.5 mb-7 text-[15px] text-slate-500">Sign in to open the till and manage your store.</p>

          <div className="space-y-4">
            {sessionExpired && !formError && (
              <Alert tone="info">Your session has expired. Please sign in again.</Alert>
            )}
            {formError && <Alert>{formError}</Alert>}

            <TextField
              label="Email"
              type="email"
              autoComplete="username"
              placeholder="you@yourstore.in"
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

            <Button type="submit" size="lg" className="w-full" disabled={isSubmitting}>
              {isSubmitting ? 'Signing in…' : 'Sign in'}
            </Button>
          </div>
        </form>
      </main>
    </div>
  )
}
