import { useEffect } from 'react'
import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Spinner } from '../components/ui/feedback'
import { authService } from '../services/authService'
import { useAuthStore } from '../store/authStore'
import { hasRole } from '../utils/format'
import type { Role } from '../types/api'

/** Redirects to /login without a session, and re-validates the stored token against /auth/me. */
export function ProtectedRoute({ children }: { children: ReactNode }) {
  const token = useAuthStore((state) => state.token)
  const user = useAuthStore((state) => state.user)
  const setUser = useAuthStore((state) => state.setUser)
  const location = useLocation()

  const me = useQuery({
    queryKey: ['auth', 'me', token],
    queryFn: authService.me,
    enabled: Boolean(token),
    staleTime: 5 * 60_000,
    retry: false,
  })

  useEffect(() => {
    if (me.data) setUser(me.data)
  }, [me.data, setUser])

  if (!token) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  if (!user && me.isPending) {
    return <Spinner label="Checking your session…" />
  }
  return <>{children}</>
}

/** Renders a "not authorized" panel when the signed-in user's role is below {@code min}. */
export function RequireRole({ min, children }: { min: Role; children: ReactNode }) {
  const role = useAuthStore((state) => state.user?.role)
  if (!hasRole(role, min)) {
    return (
      <div role="alert" className="mx-auto max-w-md rounded-lg border border-slate-200 bg-white p-8 text-center">
        <h1 className="text-lg font-semibold text-slate-900">Not authorized</h1>
        <p className="mt-2 text-sm text-slate-600">
          Your role does not have access to this page. Ask a manager or administrator if you need it.
        </p>
      </div>
    )
  }
  return <>{children}</>
}
