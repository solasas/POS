import axios from 'axios'
import { useAuthStore } from '../store/authStore'

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api',
})

apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    // A 401 anywhere except the login call itself means the session is no longer valid.
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      const isLoginCall = error.config?.url?.includes('/auth/login')
      if (!isLoginCall && useAuthStore.getState().token) {
        useAuthStore.getState().expireSession()
      }
    }
    return Promise.reject(error)
  },
)
