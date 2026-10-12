import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { vi } from 'vitest'
import { authService } from '../services/authService'
import { useAuthStore } from '../store/authStore'
import { renderWithProviders } from '../test/render'
import { ProtectedRoute, RequireRole } from './guards'

vi.mock('../services/authService', () => ({
  authService: { login: vi.fn(), me: vi.fn() },
}))

function renderGuarded(route: string) {
  return renderWithProviders(
    <Routes>
      <Route path="/login" element={<div>Login screen</div>} />
      <Route
        path="/secret"
        element={
          <ProtectedRoute>
            <div>Secret content</div>
          </ProtectedRoute>
        }
      />
      <Route
        path="/manage"
        element={
          <ProtectedRoute>
            <RequireRole min="MANAGER">
              <div>Manager tools</div>
            </RequireRole>
          </ProtectedRoute>
        }
      />
    </Routes>,
    route,
  )
}

beforeEach(() => {
  vi.mocked(authService.me).mockResolvedValue({ id: 'u', name: 'Test', role: 'CASHIER', email: 't@x.co' })
})

describe('route guards', () => {
  it('redirects anonymous visitors to the login page', async () => {
    useAuthStore.setState({ token: null, user: null })

    renderGuarded('/secret')

    expect(await screen.findByText('Login screen')).toBeInTheDocument()
    expect(screen.queryByText('Secret content')).not.toBeInTheDocument()
  })

  it('lets a signed-in user through', async () => {
    useAuthStore.setState({ token: 't', user: { id: 'u', name: 'Test', role: 'CASHIER' } })

    renderGuarded('/secret')

    expect(await screen.findByText('Secret content')).toBeInTheDocument()
  })

  it('blocks a cashier from manager-only pages', async () => {
    useAuthStore.setState({ token: 't', user: { id: 'u', name: 'Test', role: 'CASHIER' } })

    renderGuarded('/manage')

    expect(await screen.findByText('Not authorized')).toBeInTheDocument()
    expect(screen.queryByText('Manager tools')).not.toBeInTheDocument()
  })

  it('admits managers and admins to manager-only pages', async () => {
    vi.mocked(authService.me).mockResolvedValue({ id: 'u', name: 'Test', role: 'ADMIN', email: 't@x.co' })
    useAuthStore.setState({ token: 't', user: { id: 'u', name: 'Test', role: 'ADMIN' } })

    renderGuarded('/manage')

    expect(await screen.findByText('Manager tools')).toBeInTheDocument()
  })
})
