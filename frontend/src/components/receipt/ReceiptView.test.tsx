import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { vi } from 'vitest'
import ReceiptPage from '../../pages/ReceiptPage'
import { salesService } from '../../services/salesService'
import { makeReceipt, makeSale, renderWithProviders } from '../../test/render'
import { ReceiptView } from './ReceiptView'

vi.mock('../../services/salesService', () => ({
  salesService: { receipt: vi.fn(), checkout: vi.fn(), list: vi.fn(), get: vi.fn() },
}))

describe('ReceiptView', () => {
  it('prints the store, sale details, items, totals and payment from the stored sale', () => {
    render(<ReceiptView receipt={makeReceipt()} width={80} />)
    const receipt = within(screen.getByTestId('receipt'))

    expect(receipt.getByText('Corner Mart')).toBeInTheDocument()
    expect(receipt.getByText('1 Main St')).toBeInTheDocument()
    expect(receipt.getByText('S20261013-000007')).toBeInTheDocument()
    expect(receipt.getByText('Cara Cashier')).toBeInTheDocument()
    expect(receipt.getByText('Whole Milk 1L')).toBeInTheDocument()
    expect(receipt.getByText('2 pc × ₹1.50')).toBeInTheDocument()
    expect(receipt.getByText('Discount (10%)')).toBeInTheDocument()
    expect(receipt.getByText('Tax')).toBeInTheDocument()
    expect(receipt.getByText('TOTAL')).toBeInTheDocument()
    // the total appears on the TOTAL line and again as the amount paid
    expect(receipt.getAllByText('₹2.84')).toHaveLength(2)
    expect(receipt.getByText('Paid by CASH')).toBeInTheDocument()
    expect(receipt.getByText('Tendered')).toBeInTheDocument()
    expect(receipt.getByText('Change')).toBeInTheDocument()
    expect(receipt.getByText('Thank you for shopping with us!')).toBeInTheDocument()
  })

  it('omits cash fields for card payments', () => {
    const sale = makeSale({
      payment: { method: 'CARD', status: 'RECORDED', amount: 2.84, amountTendered: null, changeDue: null, recordedAt: '' },
    })
    render(<ReceiptView receipt={makeReceipt({ sale })} width={80} />)

    expect(screen.getByText('Paid by CARD')).toBeInTheDocument()
    expect(screen.queryByText('Tendered')).not.toBeInTheDocument()
    expect(screen.queryByText('Change')).not.toBeInTheDocument()
  })

  it('uses the narrow-paper layout for 58 mm and the wide one for 80 mm', () => {
    const { rerender } = render(<ReceiptView receipt={makeReceipt()} width={58} />)
    expect(screen.getByTestId('receipt')).toHaveClass('receipt', 'receipt-58')

    rerender(<ReceiptView receipt={makeReceipt()} width={80} />)
    expect(screen.getByTestId('receipt')).toHaveClass('receipt', 'receipt-80')
  })

  it('ships print styles that hide non-receipt content and size the paper', () => {
    const css = readFileSync(resolve(__dirname, '../../index.css'), 'utf8')

    expect(css).toMatch(/@media print/)
    expect(css).toMatch(/\.no-print\s*\{[^}]*display:\s*none/)
    expect(css).toMatch(/\.receipt-58\s*\{[^}]*width:\s*58mm/)
    expect(css).toMatch(/\.receipt-80\s*\{[^}]*width:\s*80mm/)
  })
})

describe('ReceiptPage', () => {
  function renderPage(route = '/receipt/S20261013-000007') {
    return renderWithProviders(
      <Routes>
        <Route path="/receipt/:saleNumber" element={<ReceiptPage />} />
      </Routes>,
      route,
    )
  }

  afterEach(() => {
    vi.restoreAllMocks()
    vi.clearAllMocks()
  })

  it('loads the receipt by sale number and prints it on request', async () => {
    vi.mocked(salesService.receipt).mockResolvedValue(makeReceipt())
    const print = vi.spyOn(window, 'print').mockImplementation(() => {})
    renderPage()

    expect(await screen.findByTestId('receipt')).toBeInTheDocument()
    expect(salesService.receipt).toHaveBeenCalledWith('S20261013-000007')

    await userEvent.setup().click(screen.getByRole('button', { name: 'Print receipt' }))

    expect(print).toHaveBeenCalledTimes(1)
  })

  it('lets the cashier switch paper width and keeps controls out of the printed area', async () => {
    vi.mocked(salesService.receipt).mockResolvedValue(makeReceipt())
    renderPage()
    const receipt = await screen.findByTestId('receipt')
    expect(receipt).toHaveClass('receipt-80')

    await userEvent.setup().selectOptions(screen.getByLabelText('Paper width'), '58')

    expect(screen.getByTestId('receipt')).toHaveClass('receipt-58')
    // toolbar carries the no-print class, so it disappears in the print stylesheet
    expect(screen.getByRole('button', { name: 'Print receipt' }).closest('.no-print')).not.toBeNull()
  })

  it('opens the print dialog automatically when asked to', async () => {
    vi.mocked(salesService.receipt).mockResolvedValue(makeReceipt())
    const print = vi.spyOn(window, 'print').mockImplementation(() => {})
    renderPage('/receipt/S20261013-000007?print=1')

    await screen.findByTestId('receipt')

    await vi.waitFor(() => expect(print).toHaveBeenCalledTimes(1))
  })

  it('shows an error instead of an empty receipt when the sale cannot be loaded', async () => {
    vi.mocked(salesService.receipt).mockRejectedValue(new Error('boom'))
    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load this receipt')
    expect(screen.queryByTestId('receipt')).not.toBeInTheDocument()
  })
})
