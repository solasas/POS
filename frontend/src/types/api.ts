export type Role = 'ADMIN' | 'MANAGER' | 'CASHIER'

export const UNITS = ['PIECE', 'KG', 'GRAM', 'LITRE', 'MILLILITRE', 'PACKET', 'BOX', 'DOZEN'] as const
export type UnitOfMeasure = (typeof UNITS)[number]

export interface AuthUser {
  id: string
  name: string
  role: Role
  email?: string | null
}

export interface AuthResponse {
  token: string
  user: { id: string; name: string; role: Role }
  registerId: string | null
}

export interface Category {
  id: string
  name: string
  description: string | null
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface CategoryInput {
  name: string
  description: string | null
}

export interface Product {
  id: string
  sku: string
  barcode: string | null
  name: string
  description: string | null
  categoryId: string
  categoryName: string
  unit: UnitOfMeasure
  price: number
  costPrice: number
  stockQuantity: number
  lowStockThreshold: number
  lowStock: boolean
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface ProductInput {
  sku: string
  barcode: string | null
  name: string
  description: string | null
  categoryId: string
  unit: UnitOfMeasure
  price: number
  costPrice: number
  lowStockThreshold: number
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface ApiErrorBody {
  timestamp: string
  status: number
  error: string
  message: string
  fieldErrors?: Record<string, string>
}

export type ProductSortKey = 'name' | 'sku' | 'price' | 'stock' | 'createdAt'

export interface ProductQuery {
  search?: string
  categoryId?: string
  active?: boolean
  lowStock?: boolean
  sortBy?: ProductSortKey
  direction?: 'asc' | 'desc'
  page?: number
  size?: number
}

export interface CategoryQuery {
  search?: string
  active?: boolean
  page?: number
  size?: number
}
