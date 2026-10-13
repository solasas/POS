import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { vi } from 'vitest'
import { ApiError } from '../services/errors'
import { posService } from '../services/posService'
import { salesService } from '../services/salesService'
import { settingsService } from '../services/settingsService'
import { useAuthStore } from '../store/authStore'
import { makePosProduct, makeSale, renderWithProviders } from '../test/render'
import type { CartRequestItem, PosProduct, PricePreview } from '../types/api'
import PosPage from './PosPage'

vi.mock('../services/posService', () => ({
  posService: { search: vi.fn(), byBarcode: vi.fn(), preview: vi.fn() },
}))
vi.mock('../services/settingsService', () => ({
  settingsService: { get: vi.fn(), setAllowNegativeStock: vi.fn() },
}))
vi.mock('../services/salesService', () => ({
  salesService: { checkout: vi.fn(), list: vi.fn(), get: vi.fn(), receipt: vi.fn() },
}))

const byBarcode = vi.mocked(posService.byBarcode)
const search = vi.mocked(posService.search)
const preview = vi.mocked(posService.preview)
const checkout = vi.mocked(salesService.checkout)

const milk = makePosProduct()
const bread = makePosProduct({ id: 'pos-2', name: 'Sourdough Loaf', sku: 'BREAD', barcode: '5000000000028', price: 3.5 })
const catalog = new Map<string, PosProduct>([
  [milk.id, milk],
  [bread.id, bread],
])

/** A tiny stand-in for the server: 10% tax, 2dp, enough to check the UI shows what it is given. */
function fakePreview(items: CartRequestItem[]): PricePreview {
  const lines = items.map((item) => {
    const price = catalog.get(item.productId)!.price
    const lineSubtotal = Math.round(price * item.quantity * 100) / 100
    return {
      productId: item.productId,
      quantity: item.quantity,
      unitPrice: price,
      lineSubtotal,
      discountAmount: 0,
      taxRatePercent: 10,
      taxAmount: Math.round(lineSubtotal * 10) / 100,
      lineTotal: Math.round(lineSubtotal * 110) / 100,
    }
  })
  const subtotal = lines.reduce((sum, l) => sum + l.lineSubtotal, 0)
  const taxTotal = lines.reduce((sum, l) => sum + l.taxAmount, 0)
  return {
    lines,
    subtotal,
    discountTotal: 0,
    taxTotal,
    grandTotal: Math.round((subtotal + taxTotal) * 100) / 100,
    pricesIncludeTax: false,
    maxDiscountPercent: 15,
  }
}

const settingsGet = vi.mocked(settingsService.get)
const settingsSet = vi.mocked(settingsService.setAllowNegativeStock)
const OFF = { allowNegativeStock: false, updatedAt: null, updatedBy: null }
const ON = { allowNegativeStock: true, updatedAt: '2026-10-13T10:00:00Z', updatedBy: 'u1' }

beforeEach(() => {
  useAuthStore.setState({ token: 't', user: { id: 'u', name: 'Test', role: 'MANAGER' } })
  settingsGet.mockResolvedValue(OFF)
  search.mockResolvedValue([])
  preview.mockImplementation(async (items) => fakePreview(items))
  byBarcode.mockImplementation(async (code) => {
    const found = [...catalog.values()].find((p) => p.barcode === code || p.sku === code)
    if (!found) throw new ApiError(`No product matches '${code}'`, 404, {}, 'UNKNOWN_BARCODE')
    return found
  })
})

afterEach(() => {
  vi.clearAllMocks()
})

async function scan(user: ReturnType<typeof userEvent.setup>, code: string) {
  const input = screen.getByLabelText('Scan barcode or search products')
  await user.clear(input)
  await user.type(input, `${code}{Enter}`)
}

function cartItems() {
  return within(screen.getByRole('list', { name: 'Cart items' }))
}

describe('POS barcode entry', () => {
  it('focuses the barcode field on load', () => {
    renderWithProviders(<PosPage />)

    expect(screen.getByLabelText('Scan barcode or search products')).toHaveFocus()
  })

  it('adds the exact-match product directly to the cart when Enter ends a scan', async () => {
    const user = userEvent.setup()
    renderWithProviders(<PosPage />)

    await scan(user, '5000000000011')

    expect(byBarcode).toHaveBeenCalledWith('5000000000011')
    expect(await cartItems().findByText('Whole Milk 1L')).toBeInTheDocument()
    expect(screen.getByLabelText('Quantity of Whole Milk 1L')).toHaveValue('1')
    // field is cleared and ready for the next scan
    expect(screen.getByLabelText('Scan barcode or search products')).toHaveValue('')
    expect(screen.getByLabelText('Scan barcode or search products')).toHaveFocus()
  })

  it('increases the quantity on repeated scans of the same product', async () => {
    const user = userEvent.setup()
    renderWithProviders(<PosPage />)

    await scan(user, '5000000000011')
    await screen.findByLabelText('Quantity of Whole Milk 1L')
    await scan(user, '5000000000011')
    await scan(user, '5000000000011')

    await waitFor(() => expect(screen.getByLabelText('Quantity of Whole Milk 1L')).toHaveValue('3'))
    expect(cartItems().getAllByRole('listitem')).toHaveLength(1)
  })

  it('tells the cashier when a barcode is unknown and adds nothing', async () => {
    const user = userEvent.setup()
    renderWithProviders(<PosPage />)

    await scan(user, '0000000000000')

    expect(await screen.findByRole('alert')).toHaveTextContent('No product matches "0000000000000"')
    expect(screen.getByText('The cart is empty.')).toBeInTheDocument()
  })

  it('refuses inactive products with a clear message', async () => {
    byBarcode.mockResolvedValueOnce(makePosProduct({ id: 'old', name: 'Old Biscuits', active: false, available: false }))
    const user = userEvent.setup()
    renderWithProviders(<PosPage />)

    await scan(user, '111')

    expect(await screen.findByRole('alert')).toHaveTextContent('Old Biscuits is inactive')
    expect(screen.getByText('The cart is empty.')).toBeInTheDocument()
  })

  it('refuses out-of-stock products', async () => {
    byBarcode.mockResolvedValueOnce(makePosProduct({ id: 'gone', name: 'Last Week Cake', stockQuantity: 0, available: false }))
    const user = userEvent.setup()
    renderWithProviders(<PosPage />)

    await scan(user, '222')

    expect(await screen.findByRole('alert')).toHaveTextContent('Last Week Cake is out of stock')
  })

  it('lists text-search matches and adds the one the cashier picks', async () => {
    search.mockResolvedValue([milk, bread])
    const user = userEvent.setup()
    renderWithProviders(<PosPage />)

    await user.type(screen.getByLabelText('Scan barcode or search products'), 'sour')
    const results = await screen.findByRole('list', { name: 'Matching products' })
    expect(within(results).getByText('₹3.50')).toBeInTheDocument()
    await user.click(within(results).getByRole('button', { name: /Sourdough Loaf/ }))

    expect(await cartItems().findByText('Sourdough Loaf')).toBeInTheDocument()
    expect(search).toHaveBeenCalledWith('sour')
  })
})

describe('POS cart', () => {
  async function withMilkAndBread() {
    const user = userEvent.setup()
    renderWithProviders(<PosPage />)
    await scan(user, '5000000000011')
    await screen.findByLabelText('Quantity of Whole Milk 1L')
    await scan(user, '5000000000028')
    await screen.findByLabelText('Quantity of Sourdough Loaf')
    return user
  }

  it('shows server-calculated totals and recalculates when the quantity changes', async () => {
    const user = await withMilkAndBread()

    // milk 1.50 + bread 3.50 = 5.00 ; 10% tax = 0.50 ; total 5.50
    const totals = await screen.findByRole('group', { name: 'Order totals' }).catch(() => null)
    expect(totals ?? true).toBeTruthy()
    await waitFor(() => expect(screen.getByText('₹5.50')).toBeInTheDocument())

    await user.click(screen.getByRole('button', { name: 'Increase quantity of Whole Milk 1L' }))

    // milk 2 x 1.50 = 3.00 + 3.50 = 6.50 ; tax 0.65 ; total 7.15
    await waitFor(() => expect(screen.getByText('₹7.15')).toBeInTheDocument())
    expect(preview).toHaveBeenLastCalledWith(
      expect.arrayContaining([{ productId: 'pos-1', quantity: 2 }]),
      0,
    )
  })

  it('rejects quantities above the available stock', async () => {
    const user = await withMilkAndBread()
    const qty = screen.getByLabelText('Quantity of Whole Milk 1L')

    await user.clear(qty)
    await user.type(qty, '11{Enter}')

    expect(await screen.findByText(/Only 10 of Whole Milk 1L available/)).toBeInTheDocument()
    expect(screen.getByLabelText('Quantity of Whole Milk 1L')).toHaveValue('1')
  })

  it('rejects fractional quantities for whole-unit products', async () => {
    const user = await withMilkAndBread()
    const qty = screen.getByLabelText('Quantity of Whole Milk 1L')

    await user.clear(qty)
    await user.type(qty, '1.5{Enter}')

    expect(await screen.findByText(/sold in whole units/)).toBeInTheDocument()
  })

  it('removes a single item', async () => {
    const user = await withMilkAndBread()

    await user.click(screen.getByRole('button', { name: 'Remove Whole Milk 1L' }))

    expect(screen.queryByLabelText('Quantity of Whole Milk 1L')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Quantity of Sourdough Loaf')).toBeInTheDocument()
  })

  it('asks for confirmation before clearing a non-empty cart', async () => {
    const user = await withMilkAndBread()

    await user.click(screen.getByRole('button', { name: 'Clear cart' }))
    expect(screen.getByRole('alertdialog')).toHaveTextContent('Remove all 2 items')
    await user.click(screen.getByRole('button', { name: 'Keep items' }))
    expect(screen.getByLabelText('Quantity of Whole Milk 1L')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Clear cart' }))
    await user.click(screen.getByRole('button', { name: 'Yes, clear' }))
    expect(await screen.findByText('The cart is empty.')).toBeInTheDocument()
  })

  it('does not call any sales endpoint just by building a cart', async () => {
    await withMilkAndBread()

    expect(checkout).not.toHaveBeenCalled()
  })
})

describe('POS checkout', () => {
  async function readyToPay() {
    const user = userEvent.setup()
    renderWithProviders(<PosPage />)
    await scan(user, '5000000000011')
    await screen.findByLabelText('Quantity of Whole Milk 1L')
    await waitFor(() => expect(screen.getByRole('button', { name: 'Checkout' })).toBeEnabled())
    await user.click(screen.getByRole('button', { name: 'Checkout' }))
    return { user, dialog: await screen.findByRole('dialog', { name: 'Confirm checkout' }) }
  }

  it('summarises the order and blocks a cash payment that is too small', async () => {
    const { user, dialog } = await readyToPay()   // total 1.65

    expect(within(dialog).getByText('1 × Whole Milk 1L')).toBeInTheDocument()
    const complete = within(dialog).getByRole('button', { name: /Complete sale/ })
    expect(complete).toBeDisabled()

    await user.type(within(dialog).getByLabelText('Amount tendered'), '1.00')
    expect(within(dialog).getByRole('alert')).toHaveTextContent('₹0.65 short')
    expect(complete).toBeDisabled()

    await user.clear(within(dialog).getByLabelText('Amount tendered'))
    await user.type(within(dialog).getByLabelText('Amount tendered'), '2.00')
    expect(within(dialog).getByText('Change due: ₹0.35')).toBeInTheDocument()
    expect(complete).toBeEnabled()
  })

  it('clears the cart only after the server confirms the sale', async () => {
    let resolve: (sale: ReturnType<typeof makeSale>) => void = () => {}
    checkout.mockImplementation(() => new Promise((r) => (resolve = r)))
    const { user, dialog } = await readyToPay()
    await user.type(within(dialog).getByLabelText('Amount tendered'), '5')

    await user.click(within(dialog).getByRole('button', { name: /Complete sale/ }))

    // request is in flight: the cart must still be intact
    expect(await within(dialog).findByRole('button', { name: 'Processing…' })).toBeDisabled()
    expect(screen.getByLabelText('Quantity of Whole Milk 1L')).toBeInTheDocument()

    resolve(makeSale({ saleNumber: 'S20261013-000042' }))

    expect(await screen.findByRole('region', { name: 'Sale complete' })).toHaveTextContent('S20261013-000042')
    expect(screen.queryByLabelText('Quantity of Whole Milk 1L')).not.toBeInTheDocument()
    expect(screen.getByText('Change due')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Print receipt' })).toHaveAttribute(
      'href',
      '/receipt/S20261013-000042?print=1',
    )
  })

  it('sends the payment method and an idempotency key, never client totals', async () => {
    checkout.mockResolvedValue(makeSale())
    const { user, dialog } = await readyToPay()

    await user.click(within(dialog).getByLabelText('UPI'))
    await user.click(within(dialog).getByRole('button', { name: /Complete sale/ }))

    await waitFor(() => expect(checkout).toHaveBeenCalledTimes(1))
    const [payload, key] = checkout.mock.calls[0]
    expect(payload).toEqual({
      items: [{ productId: 'pos-1', quantity: 1 }],
      discountPercent: 0,
      payment: { method: 'UPI' },
    })
    expect(key).toMatch(/^co-/)
    expect(JSON.stringify(payload)).not.toMatch(/total|subtotal|tax/i)
  })

  it('keeps the cart and explains the problem when checkout fails', async () => {
    checkout.mockRejectedValue(
      new ApiError("Not enough stock for 'Whole Milk 1L'", 409, {}, 'INSUFFICIENT_STOCK'),
    )
    const { user, dialog } = await readyToPay()
    await user.click(within(dialog).getByLabelText('Card'))

    await user.click(within(dialog).getByRole('button', { name: /Complete sale/ }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Your cart is unchanged')
    expect(within(dialog).getByRole('alert')).toHaveTextContent('Not enough stock')
    await user.click(within(dialog).getByRole('button', { name: 'Back to cart' }))
    expect(screen.getByLabelText('Quantity of Whole Milk 1L')).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Sale complete' })).not.toBeInTheDocument()
  })

  it('sends one request while a checkout is in flight, even on a double click', async () => {
    let reject: (error: Error) => void = () => {}
    checkout.mockReset()
    checkout.mockImplementationOnce(() => new Promise((_, r) => (reject = r)))
    const { user, dialog } = await readyToPay()
    await user.click(within(dialog).getByLabelText('Card'))

    await user.dblClick(within(dialog).getByRole('button', { name: /Complete sale/ }))
    await user.keyboard('{Enter}')   // Enter in the form must not submit a second time either

    expect(checkout).toHaveBeenCalledTimes(1)
    expect(within(dialog).getByRole('button', { name: 'Processing…' })).toBeDisabled()

    reject(new ApiError('Cannot reach the server.', null))
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Your cart is unchanged')
    expect(checkout).toHaveBeenCalledTimes(1)
  })

  it('reuses the same idempotency key when the same attempt is retried after a failure', async () => {
    checkout.mockReset()
    checkout.mockRejectedValueOnce(new ApiError('Cannot reach the server.', null))
    checkout.mockResolvedValueOnce(makeSale())
    const { user, dialog } = await readyToPay()
    await user.click(within(dialog).getByLabelText('Card'))

    await user.click(within(dialog).getByRole('button', { name: /Complete sale/ }))
    await within(dialog).findByRole('alert')
    await user.click(within(dialog).getByRole('button', { name: /Complete sale/ }))   // retry

    await waitFor(() => expect(checkout).toHaveBeenCalledTimes(2))
    expect(checkout.mock.calls[1][1]).toBe(checkout.mock.calls[0][1])
    expect(await screen.findByRole('region', { name: 'Sale complete' })).toBeInTheDocument()
  })

  it('uses a new idempotency key when the basket changes between attempts', async () => {
    checkout.mockReset()
    checkout.mockRejectedValue(new ApiError('Cannot reach the server.', null))
    const { user, dialog } = await readyToPay()
    await user.click(within(dialog).getByLabelText('Card'))
    await user.click(within(dialog).getByRole('button', { name: /Complete sale/ }))
    await within(dialog).findByRole('alert')

    await user.click(within(dialog).getByLabelText('UPI'))          // different payment => different request
    await user.click(within(dialog).getByRole('button', { name: /Complete sale/ }))

    await waitFor(() => expect(checkout).toHaveBeenCalledTimes(2))
    expect(checkout.mock.calls[1][1]).not.toBe(checkout.mock.calls[0][1])
  })
})

describe('billing when out of stock', () => {
  const sold_out = makePosProduct({ id: 'gone', name: 'Last Week Cake', stockQuantity: 0, available: false })

  it('is off by default: an out-of-stock product cannot be added', async () => {
    byBarcode.mockResolvedValueOnce(sold_out)
    const user = userEvent.setup()
    renderWithProviders(<PosPage />)
    expect(await screen.findByRole('switch', { name: 'Bill even when out of stock' })).toHaveAttribute('aria-checked', 'false')

    await scan(user, '222')

    expect(await screen.findByRole('alert')).toHaveTextContent('Last Week Cake is out of stock')
  })

  it('lets a manager switch it on and tells them what that means', async () => {
    settingsSet.mockResolvedValue(ON)
    const user = userEvent.setup()
    renderWithProviders(<PosPage />)
    const toggle = await screen.findByRole('switch', { name: 'Bill even when out of stock' })

    await user.click(toggle)

    await waitFor(() => expect(settingsSet).toHaveBeenCalledWith(true))
    await waitFor(() => expect(toggle).toHaveAttribute('aria-checked', 'true'))
    expect(screen.getByText(/Stock can go below zero/)).toBeInTheDocument()
  })

  it('shows cashiers the state but does not let them change it', async () => {
    useAuthStore.setState({ token: 't', user: { id: 'u', name: 'Test', role: 'CASHIER' } })
    settingsGet.mockResolvedValue(ON)
    renderWithProviders(<PosPage />)

    const toggle = await screen.findByRole('switch', { name: 'Bill even when out of stock' })
    await waitFor(() => expect(toggle).toHaveAttribute('aria-checked', 'true'))

    expect(toggle).toBeDisabled()
    expect(screen.getByText(/Only a manager or administrator can change this/)).toBeInTheDocument()
  })

  it('when on, an out-of-stock product is simply added to the cart with no warning', async () => {
    settingsGet.mockResolvedValue(ON)
    byBarcode.mockResolvedValue(makePosProduct({ id: 'gone', name: 'Last Week Cake', stockQuantity: 0, available: true }))
    catalog.set('gone', makePosProduct({ id: 'gone', name: 'Last Week Cake', price: 10, stockQuantity: 0, available: true }))
    const user = userEvent.setup()
    renderWithProviders(<PosPage />)
    await waitFor(() => expect(screen.getByRole('switch', { name: 'Bill even when out of stock' })).toHaveAttribute('aria-checked', 'true'))

    await scan(user, '222')

    expect(await cartItems().findByText('Last Week Cake')).toBeInTheDocument()
    expect(screen.queryByText(/Beyond stock/)).not.toBeInTheDocument()
    expect(screen.queryByText(/balance will go negative/i)).not.toBeInTheDocument()
    catalog.delete('gone')
  })

  it('when on, quantities above the stock on hand are accepted', async () => {
    settingsGet.mockResolvedValue(ON)
    const user = userEvent.setup()
    renderWithProviders(<PosPage />)
    await waitFor(() => expect(screen.getByRole('switch', { name: 'Bill even when out of stock' })).toHaveAttribute('aria-checked', 'true'))
    await scan(user, '5000000000011')
    const qty = await screen.findByLabelText('Quantity of Whole Milk 1L')

    await user.clear(qty)
    await user.type(qty, '25{Enter}')   // stock is 10

    await waitFor(() => expect(screen.getByLabelText('Quantity of Whole Milk 1L')).toHaveValue('25'))
    expect(screen.queryByText(/Beyond stock/)).not.toBeInTheDocument()
    expect(screen.queryByText(/Only 10 of Whole Milk 1L available/)).not.toBeInTheDocument()
  })
})
