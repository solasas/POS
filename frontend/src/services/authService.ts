import type { AuthResponse, AuthUser } from '../types/api'
import { apiClient } from './apiClient'

export const authService = {
  async login(email: string, password: string): Promise<AuthResponse> {
    const { data } = await apiClient.post<AuthResponse>('/auth/login', { email, password })
    return data
  },

  async me(): Promise<AuthUser> {
    const { data } = await apiClient.get<AuthUser>('/auth/me')
    return data
  },
}
