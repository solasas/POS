import type { Page, Product, ProductInput, ProductQuery } from '../types/api'
import { apiClient } from './apiClient'

export const productService = {
  async list(query: ProductQuery = {}): Promise<Page<Product>> {
    const { data } = await apiClient.get<Page<Product>>('/products', { params: query })
    return data
  },

  async get(id: string): Promise<Product> {
    const { data } = await apiClient.get<Product>(`/products/${id}`)
    return data
  },

  async create(input: ProductInput): Promise<Product> {
    const { data } = await apiClient.post<Product>('/products', input)
    return data
  },

  async update(id: string, input: ProductInput): Promise<Product> {
    const { data } = await apiClient.put<Product>(`/products/${id}`, input)
    return data
  },

  async setActive(id: string, active: boolean): Promise<Product> {
    const { data } = await apiClient.patch<Product>(`/products/${id}/status`, { active })
    return data
  },
}
