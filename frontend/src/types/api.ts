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
  code?: string
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

// ---- POS, sales, receipts ----------------------------------------------------------------

export type PaymentMethod = 'CASH' | 'UPI' | 'CARD'
export type SaleStatus = 'COMPLETED' | 'VOIDED'

export interface PosProduct {
  id: string
  name: string
  sku: string
  barcode: string | null
  categoryName: string
  unit: UnitOfMeasure
  wholeQuantitiesOnly: boolean
  price: number
  stockQuantity: number
  active: boolean
  available: boolean
}

export interface CartRequestItem {
  productId: string
  quantity: number
}

export interface PricePreviewLine {
  productId: string
  quantity: number
  unitPrice: number
  lineSubtotal: number
  discountAmount: number
  taxRatePercent: number
  taxAmount: number
  lineTotal: number
}

export interface PricePreview {
  lines: PricePreviewLine[]
  subtotal: number
  discountTotal: number
  taxTotal: number
  grandTotal: number
  pricesIncludeTax: boolean
  maxDiscountPercent: number
}

export interface CheckoutPayload {
  items: CartRequestItem[]
  discountPercent?: number
  payment: { method: PaymentMethod; amountTendered?: number }
}

export interface SaleItem {
  productId: string
  name: string
  sku: string
  barcode: string | null
  unit: UnitOfMeasure
  quantity: number
  unitPrice: number
  lineSubtotal: number
  discountAmount: number
  taxRatePercent: number
  taxAmount: number
  lineTotal: number
}

export interface SalePayment {
  method: PaymentMethod
  status: 'RECORDED'
  amount: number
  amountTendered: number | null
  changeDue: number | null
  recordedAt: string
}

export interface Sale {
  id: string
  saleNumber: string
  status: SaleStatus
  soldAt: string
  cashierId: string
  cashierName: string
  items: SaleItem[]
  subtotal: number
  discountPercent: number
  discountTotal: number
  taxTotal: number
  grandTotal: number
  pricesIncludeTax: boolean
  payment: SalePayment | null
}

export interface SaleSummary {
  id: string
  saleNumber: string
  soldAt: string
  cashierId: string
  cashierName: string
  itemCount: number
  grandTotal: number
  paymentMethod: PaymentMethod | null
  status: SaleStatus
}

export interface Receipt {
  store: { name: string; address: string; phone: string; taxId: string }
  sale: Sale
  footer: string
}

export interface SaleQuery {
  saleNumber?: string
  from?: string
  to?: string
  paymentMethod?: PaymentMethod
  status?: SaleStatus
  page?: number
  size?: number
}

export interface StockAdjustmentInput {
  productId: string
  quantityChange: number
  reason: string
}

export interface InventoryTransaction {
  id: string
  productId: string
  productName: string
  productSku: string
  movementType: 'SALE' | 'ADJUSTMENT'
  quantityChange: number
  stockBefore: number
  stockAfter: number
  saleId: string | null
  reason: string | null
  actorId: string
  createdAt: string
}

export interface SalesSummary {
  saleCount: number
  grandTotal: number
  byPaymentMethod: { method: PaymentMethod; saleCount: number; grandTotal: number }[]
}

export interface StoreSettings {
  allowNegativeStock: boolean
  updatedAt: string | null
  updatedBy: string | null
}
