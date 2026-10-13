import { useCallback, useEffect, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { BarcodeInput } from '../components/pos/BarcodeInput'
import { CartLineItem } from '../components/pos/CartLineItem'
import { CartTotals } from '../components/pos/CartTotals'
import { CheckoutDialog } from '../components/pos/CheckoutDialog'
import { ProductSearchResults } from '../components/pos/ProductSearchResults'
import { SaleSuccess } from '../components/pos/SaleSuccess'
import { Button } from '../components/ui/Button'
import { Alert, EmptyState, Spinner } from '../components/ui/feedback'
import { useCart } from '../hooks/useCart'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import { usePricePreview } from '../hooks/usePricePreview'
import { posService } from '../services/posService'
import { toApiError } from '../services/errors'
import type { PosProduct, Sale } from '../types/api'

type Feedback = { tone: 'success' | 'error' | 'info'; message: string }

export default function PosPage() {
  const queryClient = useQueryClient()
  const cart = useCart()
  const inputRef = useRef<HTMLInputElement>(null)

  const [text, setText] = useState('')
  const [lookingUp, setLookingUp] = useState(false)
  const [feedback, setFeedback] = useState<Feedback | null>(null)
  const [lineErrors, setLineErrors] = useState<Record<string, string>>({})
  const [discountText, setDiscountText] = useState('0')
  const [checkingOut, setCheckingOut] = useState(false)
  const [confirmingClear, setConfirmingClear] = useState(false)
  const [completedSale, setCompletedSale] = useState<Sale | null>(null)

  const focusInput = useCallback(() => {
    // wait a tick so the element is enabled/visible after a state change
    setTimeout(() => inputRef.current?.focus(), 0)
  }, [])

  useEffect(() => {
    inputRef.current?.focus()
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'F2') {
        event.preventDefault()
        inputRef.current?.focus()
        inputRef.current?.select()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  // ---- search (debounced); exact barcode lookups bypass this entirely --------------------
  const term = useDebouncedValue(text.trim(), 300)
  const search = useQuery({
    queryKey: ['pos', 'search', term],
    queryFn: () => posService.search(term),
    enabled: term.length >= 2,
    staleTime: 10_000,
  })
  const showResults = text.trim().length >= 2 && term === text.trim()

  const report = (result: { ok: true; quantity: number } | { ok: false; reason: string }, product: PosProduct) => {
    if (result.ok) {
      setFeedback({ tone: 'success', message: `Added ${product.name} (qty ${result.quantity}).` })
    } else {
      setFeedback({ tone: 'error', message: result.reason })
    }
  }

  const addProduct = (product: PosProduct) => {
    report(cart.add(product), product)
    setText('')
    focusInput()
  }

  const handleScan = async (code: string) => {
    setLookingUp(true)
    try {
      const product = await posService.byBarcode(code)
      addProduct(product)
    } catch (error) {
      const apiError = toApiError(error)
      if (apiError.status === 404) {
        // not an exact barcode/SKU: if text search narrowed to exactly one product, use that
        const only = search.data && search.data.length === 1 && term === code ? search.data[0] : null
        if (only) {
          addProduct(only)
        } else {
          setFeedback({ tone: 'error', message: `No product matches "${code}". Check the barcode or search by name.` })
          inputRef.current?.select()
        }
      } else {
        setFeedback({ tone: 'error', message: apiError.message })
      }
    } finally {
      setLookingUp(false)
    }
  }

  // ---- pricing ---------------------------------------------------------------------------
  const discountPercent = Number(discountText)
  const discountValid =
    discountText.trim() !== '' && Number.isFinite(discountPercent) && discountPercent >= 0 && discountPercent <= 100
  const preview = usePricePreview(cart.lines, discountValid ? discountPercent : 0)
  const previewError = preview.error ? toApiError(preview.error) : null
  const previewReady = Boolean(preview.data) && !preview.isFetching && !previewError && discountValid

  const handleCompleted = (sale: Sale) => {
    // the cart is only cleared now that the server has confirmed the sale
    cart.clear()
    setCheckingOut(false)
    setCompletedSale(sale)
    setDiscountText('0')
    setText('')
    setFeedback(null)
    queryClient.invalidateQueries({ queryKey: ['products'] })
    queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    queryClient.invalidateQueries({ queryKey: ['sales'] })
    queryClient.invalidateQueries({ queryKey: ['pos'] })
  }

  const startNewSale = () => {
    setCompletedSale(null)
    focusInput()
  }

  const handleQuantity = (product: PosProduct, quantity: number) => {
    const result = cart.setQuantity(product, quantity)
    setLineErrors((current) => {
      const next = { ...current }
      if (result.ok) delete next[product.id]
      else next[product.id] = result.reason
      return next
    })
  }

  const empty = cart.lines.length === 0

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
      {/* ---- left: scan / search ---- */}
      <section aria-label="Find products" className="space-y-4">
        <h1 className="text-2xl font-semibold text-slate-900">Point of sale</h1>
        <BarcodeInput ref={inputRef} value={text} onChange={setText} onSubmit={handleScan} busy={lookingUp} />

        <div aria-live="polite" className="min-h-6">
          {feedback && (
            <p
              className={`text-sm font-medium ${
                feedback.tone === 'error' ? 'text-red-600' : feedback.tone === 'success' ? 'text-emerald-700' : 'text-slate-600'
              }`}
              role={feedback.tone === 'error' ? 'alert' : 'status'}
            >
              {feedback.message}
            </p>
          )}
        </div>

        {showResults && search.isPending && <Spinner label="Searching…" />}
        {showResults && search.isError && <Alert>Search failed: {toApiError(search.error).message}</Alert>}
        {showResults && search.data && search.data.length === 0 && (
          <EmptyState title={`No products match "${text.trim()}"`} />
        )}
        {showResults && search.data && search.data.length > 0 && (
          <ProductSearchResults products={search.data} onSelect={addProduct} />
        )}
        {!showResults && empty && !completedSale && (
          <EmptyState title="Ready to scan">Scan a barcode or search to start a sale.</EmptyState>
        )}
      </section>

      {/* ---- right: cart / success ---- */}
      <section aria-label="Cart" className="space-y-4">
        {completedSale ? (
          <SaleSuccess sale={completedSale} onNewSale={startNewSale} />
        ) : (
          <div className="rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
            <div className="mb-2 flex items-center justify-between">
              <h2 className="text-lg font-semibold text-slate-900">Cart ({cart.itemCount})</h2>
              {!empty && !confirmingClear && (
                <Button variant="ghost" onClick={() => setConfirmingClear(true)} className="!px-2 !py-1 text-red-600">
                  Clear cart
                </Button>
              )}
            </div>

            {confirmingClear && (
              <div role="alertdialog" aria-label="Clear cart?" className="mb-3 rounded-md border border-red-200 bg-red-50 p-3 text-sm">
                <p className="font-medium text-red-800">Remove all {cart.itemCount} items from the cart?</p>
                <div className="mt-2 flex gap-2">
                  <Button
                    variant="danger"
                    onClick={() => {
                      cart.clear()
                      setLineErrors({})
                      setConfirmingClear(false)
                      setFeedback({ tone: 'info', message: 'Cart cleared.' })
                      focusInput()
                    }}
                  >
                    Yes, clear
                  </Button>
                  <Button variant="secondary" onClick={() => setConfirmingClear(false)}>
                    Keep items
                  </Button>
                </div>
              </div>
            )}

            {empty ? (
              <p className="py-8 text-center text-sm text-slate-500">The cart is empty.</p>
            ) : (
              <ul aria-label="Cart items" className="divide-y divide-slate-100">
                {cart.lines.map((line) => (
                  <CartLineItem
                    key={line.product.id}
                    line={line}
                    priced={preview.data?.lines.find((p) => p.productId === line.product.id)}
                    error={lineErrors[line.product.id]}
                    onQuantityChange={(q) => handleQuantity(line.product, q)}
                    onRemove={() => {
                      cart.remove(line.product.id)
                      focusInput()
                    }}
                  />
                ))}
              </ul>
            )}

            {!empty && (
              <div className="mt-4 space-y-4 border-t border-slate-200 pt-4">
                <div>
                  <label htmlFor="order-discount" className="mb-1 block text-sm font-medium text-slate-700">
                    Order discount (%)
                  </label>
                  <input
                    id="order-discount"
                    inputMode="decimal"
                    value={discountText}
                    onChange={(event) => setDiscountText(event.target.value)}
                    className="w-28 rounded-md border border-slate-300 px-3 py-1.5 text-sm tabular-nums focus:border-emerald-600 focus:outline-none"
                  />
                  {preview.data && (
                    <span className="ml-2 text-xs text-slate-500">your limit: {preview.data.maxDiscountPercent}%</span>
                  )}
                  {!discountValid && (
                    <p role="alert" className="mt-1 text-xs font-medium text-red-600">
                      Enter a discount between 0 and 100.
                    </p>
                  )}
                </div>

                {previewError && <Alert>{previewError.message}</Alert>}

                <CartTotals preview={preview.data} loading={preview.isFetching} />

                <Button
                  className="w-full !py-3 text-base"
                  disabled={!previewReady}
                  onClick={() => setCheckingOut(true)}
                >
                  Checkout
                </Button>
              </div>
            )}
          </div>
        )}

        <p className="text-xs text-slate-500">
          Prefer a record? <Link to="/sales" className="text-emerald-700 hover:underline">View sales history</Link>
        </p>
      </section>

      {checkingOut && preview.data && (
        <CheckoutDialog
          lines={cart.lines}
          preview={preview.data}
          discountPercent={discountValid ? discountPercent : 0}
          onClose={() => {
            setCheckingOut(false)
            focusInput()
          }}
          onCompleted={handleCompleted}
        />
      )}
    </div>
  )
}
