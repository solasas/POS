import type { Category, CategoryInput, CategoryQuery, Page } from '../types/api'
import { apiClient } from './apiClient'

export const categoryService = {
  async list(query: CategoryQuery = {}): Promise<Page<Category>> {
    const { data } = await apiClient.get<Page<Category>>('/categories', { params: query })
    return data
  },

  async create(input: CategoryInput): Promise<Category> {
    const { data } = await apiClient.post<Category>('/categories', input)
    return data
  },

  async update(id: string, input: CategoryInput): Promise<Category> {
    const { data } = await apiClient.put<Category>(`/categories/${id}`, input)
    return data
  },

  async setActive(id: string, active: boolean): Promise<Category> {
    const { data } = await apiClient.patch<Category>(`/categories/${id}/status`, { active })
    return data
  },
}
