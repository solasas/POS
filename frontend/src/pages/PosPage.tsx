import { useCallback, useEffect, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { BarcodeInput } from '../components/pos/BarcodeInput'
import { CartLineItem } from '../components/pos/CartLineItem'
import { CartTotals } from '../components/pos/CartTotals'
import { CheckoutDialog } from '../components/pos/CheckoutDialog'
import { ProductSearchResults } from '../components/pos/ProductSearchResults'
import { SaleSuccess } from '../components/pos/SaleSuccess'
import { Button } from '../components/ui/Button'
import { Alert, EmptyState, TableSkeleton } from '../components/ui/feedback'
import { Icon } from '../components/ui/Icon'
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
  // the most recently added/changed line, flashed briefly so the cashier sees where it landed
  const [lastTouched, setLastTouched] = useState<{ id: string | null; n: number }>({ id: null, n: 0 })

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
    const result = cart.add(product)
    if (result.ok) setLastTouched((current) => ({ id: product.id, n: current.n + 1 }))
    report(result, product)
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
    <div className="grid items-start gap-6 md:grid-cols-[minmax(0,1fr)_22rem] xl:grid-cols-[minmax(0,1fr)_26rem]">
      {/* ---- left: scan / search ---- */}
      <section aria-label="Find products" className="space-y-5">
        <div>
          <p className="eyebrow mb-1.5">Point of sale</p>
          <h1 className="font-display text-[1.9rem] leading-tight font-bold text-ink">New sale</h1>
        </div>

        <BarcodeInput ref={inputRef} value={text} onChange={setText} onSubmit={handleScan} busy={lookingUp} />

        <div aria-live="polite" className="min-h-9">
          {feedback && (
            <p
              key={feedback.message}
              className={`animate-rise inline-flex items-center gap-2 rounded-full px-3.5 py-1.5 text-sm font-medium ${
                feedback.tone === 'error'
                  ? 'bg-red-50 text-red-700'
                  : feedback.tone === 'success'
                    ? 'bg-emerald-100 text-emerald-900'
                    : 'bg-slate-200/70 text-slate-700'
              }`}
              role={feedback.tone === 'error' ? 'alert' : 'status'}
            >
              <Icon
                name={feedback.tone === 'error' ? 'alert' : feedback.tone === 'success' ? 'check' : 'info'}
                size={16}
              />
              {feedback.message}
            </p>
          )}
        </div>

        {showResults && search.isPending && <TableSkeleton rows={3} label="Searching…" />}
        {showResults && search.isError && <Alert>Search failed: {toApiError(search.error).message}</Alert>}
        {showResults && search.data && search.data.length === 0 && (
          <EmptyState title={`No products match "${text.trim()}"`} illustration="search">
            Check the spelling, or try the SKU or barcode.
          </EmptyState>
        )}
        {showResults && search.data && search.data.length > 0 && (
          <ProductSearchResults products={search.data} onSelect={addProduct} />
        )}
        {!showResults && !completedSale && (
          <div className="flex flex-col items-center rounded-2xl border border-dashed border-slate-300 bg-card/50 px-6 py-14 text-center">
            <div className="relative flex h-24 w-24 items-center justify-center">
              <svg width="96" height="96" viewBox="0 0 72 72" fill="none" aria-hidden="true">
                <path d="M10 22v-8a4 4 0 0 1 4-4h8M50 10h8a4 4 0 0 1 4 4v8M62 50v8a4 4 0 0 1-4 4h-8M22 62h-8a4 4 0 0 1-4-4v-8" stroke="var(--color-emerald-700)" strokeWidth="3.5" strokeLinecap="round" />
                <path d="M20 26v20M26 26v20M32 26v20M38 26v20M44 26v20M50 26v20" stroke="var(--color-slate-300)" strokeWidth="2.5" strokeLinecap="round" />
              </svg>
              <span className="absolute top-[26px] left-[16px] h-[3px] w-16 animate-scan rounded-full bg-red-500 [--scan-distance:38px]" />
            </div>
            <p className="mt-5 font-display text-xl font-semibold text-ink">Ready to scan</p>
            <p className="mt-1 max-w-xs text-sm text-slate-500">
              Point the scanner at a barcode, or start typing a product name. Items land on the ticket.
            </p>
          </div>
        )}
      </section>

      {/* ---- right: ticket / success ---- */}
      <section aria-label="Cart" className="md:sticky md:top-20">
        {completedSale ? (
          <SaleSuccess sale={completedSale} onNewSale={startNewSale} />
        ) : (
          <div className="ticket px-6 pt-8 pb-9">
            <div className="mb-1 flex items-center justify-between">
              <h2 className="font-display text-lg font-bold text-ink">
                Cart <span className="num ml-1 rounded-full bg-slate-200/80 px-2 py-0.5 align-middle text-xs font-semibold text-slate-600">{cart.itemCount}</span>
              </h2>
              {!empty && !confirmingClear && (
                <button
                  type="button"
                  onClick={() => setConfirmingClear(true)}
                  className="rounded-md px-2 py-1 text-xs font-medium text-slate-500 transition hover:bg-red-50 hover:text-red-600"
                >
                  Clear cart
                </button>
              )}
            </div>

            {confirmingClear && (
              <div role="alertdialog" aria-label="Clear cart?" className="animate-rise my-3 rounded-xl border border-red-200 bg-red-50 p-3.5 text-sm">
                <p className="font-medium text-red-800">Remove all {cart.itemCount} items from the cart?</p>
                <div className="mt-3 flex gap-2">
                  <Button
                    variant="danger"
                    size="sm"
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
                  <Button variant="secondary" size="sm" onClick={() => setConfirmingClear(false)}>
                    Keep items
                  </Button>
                </div>
              </div>
            )}

            {empty ? (
              <div className="flex flex-col items-center py-10 text-center">
                <svg width="56" height="56" viewBox="0 0 72 72" fill="none" aria-hidden="true">
                  <path d="M12 28h48l-5 30a4 4 0 0 1-4 3.4H21a4 4 0 0 1-4-3.4z" fill="var(--color-slate-100)" stroke="var(--color-slate-300)" strokeWidth="2" strokeLinejoin="round" />
                  <path d="M28 28l6-14M44 28l-6-14" stroke="var(--color-slate-400)" strokeWidth="2.5" strokeLinecap="round" />
                </svg>
                <p className="mt-3 text-sm text-slate-500">The cart is empty.</p>
              </div>
            ) : (
              <ul aria-label="Cart items" className="divide-y divide-dashed divide-slate-300">
                {cart.lines.map((line) => (
                  <CartLineItem
                    key={lastTouched.id === line.product.id ? `${line.product.id}-${lastTouched.n}` : line.product.id}
                    line={line}
                    flash={lastTouched.id === line.product.id}
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
              <div className="mt-2 space-y-5 border-t border-dashed border-slate-300 pt-5">
                <div className="flex items-center justify-between gap-4">
                  <label htmlFor="order-discount" className="text-sm font-medium text-slate-700">
                    Order discount (%)
                  </label>
                  <div className="flex items-center gap-2">
                    {preview.data && (
                      <span className="text-[11px] text-slate-400">max {preview.data.maxDiscountPercent}%</span>
                    )}
                    <input
                      id="order-discount"
                      inputMode="decimal"
                      value={discountText}
                      onChange={(event) => setDiscountText(event.target.value)}
                      className="num h-9 w-20 rounded-lg border border-slate-300 bg-card px-2.5 text-right text-sm font-semibold focus:border-emerald-600 focus:ring-4 focus:ring-emerald-600/15 focus:outline-none"
                    />
                  </div>
                </div>
                {!discountValid && (
                  <p role="alert" className="-mt-3 text-xs font-medium text-red-600">
                    Enter a discount between 0 and 100.
                  </p>
                )}

                {previewError && <Alert>{previewError.message}</Alert>}

                <CartTotals preview={preview.data} loading={preview.isFetching} />

                <Button
                  variant="accent"
                  size="lg"
                  className="w-full !h-14 text-[17px] font-semibold"
                  disabled={!previewReady}
                  aria-label="Checkout"
                  onClick={() => setCheckingOut(true)}
                >
                  Checkout
                  <Icon name="arrowRight" size={20} strokeWidth={2.25} />
                </Button>
              </div>
            )}
          </div>
        )}
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
