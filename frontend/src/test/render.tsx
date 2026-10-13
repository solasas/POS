import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { ToastProvider } from '../components/Toast'
import type { Category, PosProduct, Product, Receipt, Sale } from '../types/api'

export function renderWithProviders(ui: ReactElement, route = '/') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  )
}

export function makeCategory(overrides: Partial<Category> = {}): Category {
  return {
    id: 'cat-1',
    name: 'Dairy',
    description: null,
    active: true,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

export function makeProduct(overrides: Partial<Product> = {}): Product {
  return {
    id: 'prod-1',
    sku: 'MILK-1',
    barcode: '5000000000011',
    name: 'Whole Milk 1L',
    description: null,
    categoryId: 'cat-1',
    categoryName: 'Dairy',
    unit: 'LITRE',
    price: 1.09,
    costPrice: 0.7,
    stockQuantity: 20,
    lowStockThreshold: 5,
    lowStock: false,
    active: true,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

export function makePosProduct(overrides: Partial<PosProduct> = {}): PosProduct {
  return {
    id: 'pos-1',
    name: 'Whole Milk 1L',
    sku: 'MILK-1',
    barcode: '5000000000011',
    categoryName: 'Dairy',
    unit: 'PIECE',
    wholeQuantitiesOnly: true,
    price: 1.5,
    stockQuantity: 10,
    active: true,
    available: true,
    ...overrides,
  }
}

export function makeSale(overrides: Partial<Sale> = {}): Sale {
  return {
    id: 'sale-1',
    saleNumber: 'S20261013-000007',
    status: 'COMPLETED',
    soldAt: '2026-10-13T09:30:00Z',
    cashierId: 'user-1',
    cashierName: 'Cara Cashier',
    items: [
      {
        productId: 'pos-1',
        name: 'Whole Milk 1L',
        sku: 'MILK-1',
        barcode: '5000000000011',
        unit: 'PIECE',
        quantity: 2,
        unitPrice: 1.5,
        lineSubtotal: 3,
        discountAmount: 0.3,
        taxRatePercent: 5,
        taxAmount: 0.14,
        lineTotal: 2.84,
      },
    ],
    subtotal: 3,
    discountPercent: 10,
    discountTotal: 0.3,
    taxTotal: 0.14,
    grandTotal: 2.84,
    pricesIncludeTax: false,
    payment: {
      method: 'CASH',
      status: 'RECORDED',
      amount: 2.84,
      amountTendered: 5,
      changeDue: 2.16,
      recordedAt: '2026-10-13T09:30:00Z',
    },
    costTotal: null,
    profit: null,
    ...overrides,
  }
}

export function makeReceipt(overrides: Partial<Receipt> = {}): Receipt {
  return {
    store: { name: 'Corner Mart', address: '1 Main St', phone: '555-0100', taxId: 'TAX-9' },
    sale: makeSale(),
    footer: 'Thank you for shopping with us!',
    ...overrides,
  }
}
