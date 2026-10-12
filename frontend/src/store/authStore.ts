import { create } from 'zustand'
import type { AuthUser } from '../types/api'

const TOKEN_KEY = 'stockora_token'
const USER_KEY = 'stockora_user'

function loadUser(): AuthUser | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? (JSON.parse(raw) as AuthUser) : null
  } catch {
    return null
  }
}

function loadToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

interface AuthState {
  token: string | null
  user: AuthUser | null
  /** Set when a request came back 401 so the login page can explain why the user was signed out. */
  sessionExpired: boolean
  login: (token: string, user: AuthUser) => void
  setUser: (user: AuthUser) => void
  logout: () => void
  expireSession: () => void
}

function clearStorage() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export const useAuthStore = create<AuthState>((set) => ({
  token: loadToken(),
  user: loadUser(),
  sessionExpired: false,

  login: (token, user) => {
    localStorage.setItem(TOKEN_KEY, token)
    localStorage.setItem(USER_KEY, JSON.stringify(user))
    set({ token, user, sessionExpired: false })
  },

  setUser: (user) => {
    localStorage.setItem(USER_KEY, JSON.stringify(user))
    set({ user })
  },

  logout: () => {
    clearStorage()
    set({ token: null, user: null, sessionExpired: false })
  },

  expireSession: () => {
    clearStorage()
    set({ token: null, user: null, sessionExpired: true })
  },
}))
