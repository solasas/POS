import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { vi } from 'vitest'
import { productService } from '../services/productService'
import { categoryService } from '../services/categoryService'
import { useAuthStore } from '../store/authStore'
import { makeCategory, makeProduct, renderWithProviders } from '../test/render'
import type { Page, Product } from '../types/api'
import ProductListPage from './ProductListPage'

vi.mock('../services/productService', () => ({
  productService: { list: vi.fn(), setActive: vi.fn() },
}))
vi.mock('../services/categoryService', () => ({
  categoryService: { list: vi.fn() },
}))

const list = vi.mocked(productService.list)
const setActive = vi.mocked(productService.setActive)

function page(content: Product[], overrides: Partial<Page<Product>> = {}): Page<Product> {
  return { content, page: 0, size: 10, totalElements: content.length, totalPages: 1, ...overrides }
}

function signIn(role: 'ADMIN' | 'MANAGER' | 'CASHIER') {
  useAuthStore.setState({ token: 't', user: { id: 'u', name: 'Test', role } })
}

beforeEach(() => {
  vi.mocked(categoryService.list).mockResolvedValue({
    content: [makeCategory(), makeCategory({ id: 'cat-2', name: 'Bakery' })],
    page: 0,
    size: 100,
    totalElements: 2,
    totalPages: 1,
  })
  signIn('MANAGER')
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('ProductListPage', () => {
  it('renders price, stock, unit, category, status and a low-stock badge', async () => {
    list.mockResolvedValue(
      page([
        makeProduct(),
        makeProduct({
          id: 'prod-2',
          sku: 'BREAD',
          name: 'Loaf',
          price: 2.5,
          stockQuantity: 2,
          lowStock: true,
          active: false,
        }),
      ]),
    )

    renderWithProviders(<ProductListPage />)

    const table = within(await screen.findByRole('table'))
    expect(table.getByText('Whole Milk 1L')).toBeInTheDocument()
    expect(table.getByText('₹1.09')).toBeInTheDocument()
    expect(table.getByText('20 L')).toBeInTheDocument()
    expect(table.getAllByText('Active')).toHaveLength(1)
    expect(table.getByText('Inactive')).toBeInTheDocument()
    expect(table.getByText('Low stock')).toBeInTheDocument()
    expect(table.getAllByText('Dairy').length).toBeGreaterThan(0)
  })

  it('shows an empty state with a way to clear filters', async () => {
    list.mockResolvedValue(page([]))

    renderWithProviders(<ProductListPage />, '/products?q=zzz')

    expect(await screen.findByText('No products match your filters')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Clear filters' })).toBeInTheDocument()
  })

  it('shows an error message when loading fails', async () => {
    list.mockRejectedValue(new Error('boom'))

    renderWithProviders(<ProductListPage />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load products')
  })

  it('searches the backend after the user types', async () => {
    list.mockResolvedValue(page([makeProduct()]))
    renderWithProviders(<ProductListPage />)
    await screen.findByText('Whole Milk 1L')

    await userEvent.setup().type(screen.getByLabelText('Search products'), 'milk')

    await waitFor(() =>
      expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ search: 'milk', page: 0 })),
    )
  })

  it('filters by category and status', async () => {
    list.mockResolvedValue(page([makeProduct()]))
    const user = userEvent.setup()
    renderWithProviders(<ProductListPage />)
    await screen.findByText('Whole Milk 1L')
    await screen.findByRole('option', { name: 'Bakery' })

    await user.selectOptions(screen.getByLabelText('Filter by category'), 'cat-2')
    await user.selectOptions(screen.getByLabelText('Filter by status'), 'inactive')

    await waitFor(() =>
      expect(list).toHaveBeenLastCalledWith(
        expect.objectContaining({ categoryId: 'cat-2', active: false }),
      ),
    )
  })

  it('requests the next page', async () => {
    list.mockResolvedValue(page([makeProduct()], { totalElements: 25, totalPages: 3 }))
    const user = userEvent.setup()
    renderWithProviders(<ProductListPage />)
    await screen.findByText('Page 1 of 3', { exact: false })

    await user.click(screen.getByRole('button', { name: 'Next' }))

    await waitFor(() => expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 })))
  })

  it('lets a manager deactivate a product after confirmation', async () => {
    list.mockResolvedValue(page([makeProduct()]))
    setActive.mockResolvedValue(makeProduct({ active: false }))
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderWithProviders(<ProductListPage />)

    await userEvent.setup().click(await screen.findByRole('button', { name: 'Deactivate' }))

    await waitFor(() => expect(setActive).toHaveBeenCalledWith('prod-1', false))
  })

  it('hides management actions from cashiers', async () => {
    signIn('CASHIER')
    list.mockResolvedValue(page([makeProduct()]))

    renderWithProviders(<ProductListPage />)

    await screen.findByText('Whole Milk 1L')
    expect(screen.queryByRole('link', { name: 'Add product' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Edit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Deactivate' })).not.toBeInTheDocument()
  })
})
