import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { vi } from 'vitest'
import { salesService } from '../services/salesService'
import { useAuthStore } from '../store/authStore'
import { renderWithProviders } from '../test/render'
import type { Page, SaleSummary } from '../types/api'
import SalesHistoryPage from './SalesHistoryPage'

vi.mock('../services/salesService', () => ({
  salesService: { list: vi.fn(), get: vi.fn(), receipt: vi.fn(), checkout: vi.fn() },
}))

const list = vi.mocked(salesService.list)

function summary(overrides: Partial<SaleSummary> = {}): SaleSummary {
  return {
    id: 's1',
    saleNumber: 'S20261013-000001',
    soldAt: '2026-10-13T09:30:00Z',
    cashierId: 'u1',
    cashierName: 'Cara Cashier',
    itemCount: 3,
    grandTotal: 12.34,
    paymentMethod: 'UPI',
    status: 'COMPLETED',
    ...overrides,
  }
}

function page(content: SaleSummary[], overrides: Partial<Page<SaleSummary>> = {}): Page<SaleSummary> {
  return { content, page: 0, size: 10, totalElements: content.length, totalPages: 1, ...overrides }
}

function signIn(role: 'ADMIN' | 'MANAGER' | 'CASHIER') {
  useAuthStore.setState({ token: 't', user: { id: 'u', name: 'Test', role } })
}

beforeEach(() => {
  signIn('MANAGER')
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('SalesHistoryPage', () => {
  it('lists real sales with number, cashier, items, total, payment and status', async () => {
    list.mockResolvedValue(page([summary(), summary({ id: 's2', saleNumber: 'S20261013-000002', status: 'VOIDED', paymentMethod: 'CASH', grandTotal: 99.5 })]))

    renderWithProviders(<SalesHistoryPage />)

    const table = within(await screen.findByRole('table'))
    expect(table.getByText('S20261013-000001')).toBeInTheDocument()
    expect(table.getByText('$12.34')).toBeInTheDocument()
    expect(table.getByText('$99.50')).toBeInTheDocument()
    expect(table.getAllByText('Cara Cashier').length).toBeGreaterThan(0)
    expect(table.getByText('UPI')).toBeInTheDocument()
    expect(table.getByText('CASH')).toBeInTheDocument()
    expect(table.getByText('Completed')).toBeInTheDocument()
    expect(table.getByText('Voided')).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: 'View' })[0]).toHaveAttribute('href', '/sales/s1')
    expect(screen.getAllByRole('link', { name: 'Receipt' })[0]).toHaveAttribute('href', '/receipt/S20261013-000001')
  })

  it('managers query all sales; cashiers only their own', async () => {
    list.mockResolvedValue(page([summary()]))
    const view = renderWithProviders(<SalesHistoryPage />)
    await screen.findByText('S20261013-000001')
    expect(list).toHaveBeenLastCalledWith(expect.anything(), false)
    view.unmount()

    signIn('CASHIER')
    renderWithProviders(<SalesHistoryPage />)

    expect(await screen.findByRole('heading', { name: 'My sales' })).toBeInTheDocument()
    await waitFor(() => expect(list).toHaveBeenLastCalledWith(expect.anything(), true))
  })

  it('filters by receipt number, payment method and date range', async () => {
    list.mockResolvedValue(page([summary()]))
    const user = userEvent.setup()
    renderWithProviders(<SalesHistoryPage />)
    await screen.findByText('S20261013-000001')

    await user.type(screen.getByLabelText('Search by receipt number'), 'S2026')
    await user.selectOptions(screen.getByLabelText('Payment method'), 'CARD')
    await user.type(screen.getByLabelText('From date'), '2026-10-01')
    await user.type(screen.getByLabelText('To date'), '2026-10-13')

    await waitFor(() => {
      const [query] = list.mock.lastCall!
      expect(query).toMatchObject({ saleNumber: 'S2026', paymentMethod: 'CARD', page: 0 })
      expect(query.from).toBe(new Date('2026-10-01T00:00:00').toISOString())
      // "to" is inclusive of the whole day, so it is the start of the next local day
      expect(query.to).toBe(new Date('2026-10-14T00:00:00').toISOString())
    })
  })

  it('paginates', async () => {
    list.mockResolvedValue(page([summary()], { totalElements: 25, totalPages: 3 }))
    const user = userEvent.setup()
    renderWithProviders(<SalesHistoryPage />)
    await screen.findByText('Page 1 of 3', { exact: false })

    await user.click(screen.getByRole('button', { name: 'Next' }))

    await waitFor(() => expect(list.mock.lastCall![0]).toMatchObject({ page: 1 }))
  })

  it('shows empty and error states', async () => {
    list.mockResolvedValueOnce(page([]))
    const view = renderWithProviders(<SalesHistoryPage />)
    expect(await screen.findByText('No sales yet')).toBeInTheDocument()
    view.unmount()

    list.mockRejectedValueOnce(new Error('boom'))
    renderWithProviders(<SalesHistoryPage />)
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load sales')
  })
})
