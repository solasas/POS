import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { vi } from 'vitest'
import { ApiError } from '../services/errors'
import { authService } from '../services/authService'
import { useAuthStore } from '../store/authStore'
import { renderWithProviders } from '../test/render'
import LoginPage from './LoginPage'

vi.mock('../services/authService', () => ({
  authService: { login: vi.fn(), me: vi.fn() },
}))

const login = vi.mocked(authService.login)

function renderLogin() {
  return renderWithProviders(
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={<div>Dashboard home</div>} />
    </Routes>,
    '/login',
  )
}

beforeEach(() => {
  useAuthStore.setState({ token: null, user: null, sessionExpired: false })
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('LoginPage', () => {
  it('validates required fields before calling the API', async () => {
    renderLogin()

    await userEvent.setup().click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Email is required')).toBeInTheDocument()
    expect(screen.getByText('Password is required')).toBeInTheDocument()
    expect(login).not.toHaveBeenCalled()
  })

  it('rejects a malformed email address', async () => {
    const user = userEvent.setup()
    renderLogin()

    await user.type(screen.getByLabelText('Email'), 'not-an-email')
    await user.type(screen.getByLabelText('Password'), 'secret')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Enter a valid email address')).toBeInTheDocument()
    expect(login).not.toHaveBeenCalled()
  })

  it('shows a generic message for invalid credentials', async () => {
    login.mockRejectedValue(new ApiError('Invalid email or password', 401))
    const user = userEvent.setup()
    renderLogin()

    await user.type(screen.getByLabelText('Email'), 'a@b.co')
    await user.type(screen.getByLabelText('Password'), 'wrong')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password.')
    expect(useAuthStore.getState().token).toBeNull()
  })

  it('stores the session and navigates home on success', async () => {
    login.mockResolvedValue({
      token: 'jwt-token',
      user: { id: 'u1', name: 'Ada', role: 'ADMIN' },
      registerId: null,
    })
    const user = userEvent.setup()
    renderLogin()

    await user.type(screen.getByLabelText('Email'), 'ada@example.com')
    await user.type(screen.getByLabelText('Password'), 'correct-password')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Dashboard home')).toBeInTheDocument()
    await waitFor(() => expect(useAuthStore.getState().token).toBe('jwt-token'))
    expect(useAuthStore.getState().user?.role).toBe('ADMIN')
  })

  it('explains why the user was signed out when the session expired', () => {
    useAuthStore.setState({ sessionExpired: true })

    renderLogin()

    expect(screen.getByRole('status')).toHaveTextContent('Your session has expired')
  })
})
