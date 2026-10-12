import { create } from 'zustand'

function loadUser() {
  try {
    return JSON.parse(localStorage.getItem('pos_user'))
  } catch {
    return null
  }
}

export const useAuthStore = create((set) => ({
  user: loadUser(),
  token: localStorage.getItem('pos_token'),

  login: (user, token) => {
    localStorage.setItem('pos_token', token)
    localStorage.setItem('pos_user', JSON.stringify(user))
    set({ user, token })
  },

  logout: () => {
    localStorage.removeItem('pos_token')
    localStorage.removeItem('pos_user')
    set({ user: null, token: null })
  },
}))
