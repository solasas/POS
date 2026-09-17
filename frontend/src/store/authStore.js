import { create } from 'zustand'

export const useAuthStore = create((set) => ({
  user: null,
  token: localStorage.getItem('pos_token'),

  login: (user, token) => {
    localStorage.setItem('pos_token', token)
    set({ user, token })
  },

  logout: () => {
    localStorage.removeItem('pos_token')
    set({ user: null, token: null })
  },
}))
