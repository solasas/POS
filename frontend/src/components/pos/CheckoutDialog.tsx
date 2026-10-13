import { useRef, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Modal } from '../ui/Modal'
import { Button } from '../ui/Button'
import { Alert } from '../ui/feedback'
import { Icon } from '../ui/Icon'
import type { IconName } from '../ui/Icon'
import { toApiError } from '../../services/errors'
import { salesService } from '../../services/salesService'
import type { CartLine } from '../../hooks/useCart'
import type { PaymentMethod, PricePreview, Sale } from '../../types/api'
import { newIdempotencyKey } from '../../utils/idempotency'
import { formatMoney } from '../../utils/format'
import { CartTotals } from './CartTotals'

interface CheckoutDialogProps {
  lines: CartLine[]
  preview: PricePreview
  discountPercent: number
  onClose: () => void
  /** Called with the completed sale once the server has confirmed it. */
  onCompleted: (sale: Sale) => void
}

const METHODS: { value: PaymentMethod; label: string; hint: string; icon: IconName }[] = [
  { value: 'CASH', label: 'Cash', hint: 'Enter the amount handed over', icon: 'cash' },
  { value: 'UPI', label: 'UPI', hint: 'Confirm the customer has paid', icon: 'upi' },
  { value: 'CARD', label: 'Card', hint: 'Confirm the card payment went through', icon: 'card' },
]

/** Indian currency notes, used to offer "the customer gave a ₹500 note" shortcuts. */
const NOTES = [10, 20, 50, 100, 200, 500, 2000]

/** The smallest few notes that cover the total, e.g. ₹37 -> 50, 100, 200 (plus exact). */
function noteShortcuts(total: number): number[] {
  return NOTES.filter((note) => note >= total).slice(0, 3)
}

function toCents(value: number): number {
  return Math.round(value * 100)
}

/**
 * Review, choose how the customer paid, and submit. UPI and card are *recorded* as confirmed by the
 * cashier; nothing is verified with a payment provider and no card or UPI credentials are handled.
 */
export function CheckoutDialog({ lines, preview, discountPercent, onClose, onCompleted }: CheckoutDialogProps) {
  const [method, setMethod] = useState<PaymentMethod>('CASH')
  const [tenderedText, setTenderedText] = useState('')
  const [error, setError] = useState<{ message: string; code?: string } | null>(null)
  const submitting = useRef(false)

  // One key per logical attempt. The same basket + payment keeps its key, so a retry after a
  // dropped connection returns the original sale instead of charging twice.
  const attempt = useRef<{ signature: string; key: string } | null>(null)

  const tendered = tenderedText.trim() === '' ? null : Number(tenderedText)
  const tenderedValid = tendered !== null && Number.isFinite(tendered) && tendered >= 0
  const changeDue =
    method === 'CASH' && tenderedValid && toCents(tendered) >= toCents(preview.grandTotal)
      ? (toCents(tendered) - toCents(preview.grandTotal)) / 100
      : null
  const shortBy =
    method === 'CASH' && tenderedValid && toCents(tendered) < toCents(preview.grandTotal)
      ? (toCents(preview.grandTotal) - toCents(tendered)) / 100
      : null

  const mutation = useMutation({
    mutationFn: async () => {
      const payload = {
        items: lines.map((l) => ({ productId: l.product.id, quantity: l.quantity })),
        discountPercent,
        payment: {
          method,
          ...(method === 'CASH' ? { amountTendered: tendered as number } : {}),
        },
      }
      const signature = JSON.stringify(payload)
      if (attempt.current?.signature !== signature) {
        attempt.current = { signature, key: newIdempotencyKey() }
      }
      return salesService.checkout(payload, attempt.current.key)
    },
    onSuccess: (sale) => {
      attempt.current = null
      onCompleted(sale)
    },
    onError: (err) => {
      const apiError = toApiError(err)
      setError({ message: apiError.message, code: apiError.code })
    },
    onSettled: () => {
      submitting.current = false
    },
  })

  const canSubmit = method !== 'CASH' || (tenderedValid && shortBy === null)

  const submit = () => {
    if (submitting.current || mutation.isPending || !canSubmit) return
    submitting.current = true
    setError(null)
    mutation.mutate()
  }

  return (
    <Modal title="Confirm checkout" onClose={onClose} locked={mutation.isPending}>
      <form
        onSubmit={(event) => {
          event.preventDefault()
          submit()
        }}
        className="space-y-5"
      >
        <section aria-label="Items">
          <ul className="max-h-40 divide-y divide-slate-100 overflow-y-auto rounded-xl bg-slate-100/60 px-4 text-sm">
            {lines.map((line) => {
              const priced = preview.lines.find((p) => p.productId === line.product.id)
              return (
                <li key={line.product.id} className="flex justify-between gap-3 py-1.5">
                  <span className="min-w-0 truncate">
                    {line.quantity} × {line.product.name}
                  </span>
                  <span className="tabular-nums">{priced ? formatMoney(priced.lineTotal) : '—'}</span>
                </li>
              )
            })}
          </ul>
        </section>

        <CartTotals preview={preview} loading={false} />

        <fieldset>
          <legend className="eyebrow mb-2">Payment method</legend>
          <div className="grid grid-cols-3 gap-2.5">
            {METHODS.map((option) => (
              <label
                key={option.value}
                className={`flex cursor-pointer flex-col items-center gap-1.5 rounded-xl border-2 px-3 py-3 text-sm font-semibold transition focus-within:ring-4 focus-within:ring-emerald-600/15 ${
                  method === option.value
                    ? 'border-emerald-700 bg-emerald-50 text-emerald-900'
                    : 'border-slate-200 bg-card text-slate-700 hover:border-slate-300'
                }`}
              >
                <Icon name={option.icon} size={22} />
                <input
                  type="radio"
                  name="payment-method"
                  value={option.value}
                  checked={method === option.value}
                  onChange={() => {
                    setMethod(option.value)
                    setError(null)
                  }}
                  className="sr-only"
                />
                {option.label}
              </label>
            ))}
          </div>
          <p className="mt-2 text-xs text-slate-500">{METHODS.find((m) => m.value === method)?.hint}</p>
          {method !== 'CASH' && (
            <p className="mt-1 text-xs text-slate-500">
              This records that you confirmed the payment. It is not verified with a payment provider.
            </p>
          )}
        </fieldset>

        {method === 'CASH' && (
          <div>
            <label htmlFor="amount-tendered" className="eyebrow mb-2 block">
              Amount tendered
            </label>
            <div className="relative">
              <span className="pointer-events-none absolute top-1/2 left-4 -translate-y-1/2 font-display text-xl font-semibold text-slate-400">
                ₹
              </span>
              <input
                id="amount-tendered"
                inputMode="decimal"
                value={tenderedText}
                onChange={(event) => setTenderedText(event.target.value)}
                className="num block h-14 w-full rounded-xl border-2 border-slate-300 bg-card pr-4 pl-10 font-display text-2xl font-semibold text-ink transition hover:border-slate-400 focus:border-emerald-600 focus:ring-4 focus:ring-emerald-600/15 focus:outline-none"
                autoFocus
              />
            </div>
            <div className="mt-2.5 flex flex-wrap gap-2" aria-label="Quick amounts">
              <button
                type="button"
                onClick={() => setTenderedText(String(preview.grandTotal))}
                className="num h-9 rounded-lg border border-slate-300 bg-card px-3.5 text-sm font-semibold text-slate-700 transition hover:border-emerald-600 hover:bg-emerald-50"
              >
                Exact
              </button>
              {noteShortcuts(preview.grandTotal).map((note) => (
                <button
                  key={note}
                  type="button"
                  onClick={() => setTenderedText(String(note))}
                  className="num h-9 rounded-lg border border-slate-300 bg-card px-3.5 text-sm font-semibold text-slate-700 transition hover:border-emerald-600 hover:bg-emerald-50"
                >
                  ₹{note}
                </button>
              ))}
            </div>
            {shortBy !== null && (
              <p role="alert" className="mt-3 text-sm font-medium text-red-600">
                {formatMoney(shortBy)} short of the amount payable.
              </p>
            )}
            {changeDue !== null && (
              <p className="mt-3 rounded-lg bg-amber-100 px-3.5 py-2.5 text-sm font-semibold text-amber-900">
                Change due: {formatMoney(changeDue)}
              </p>
            )}
          </div>
        )}

        {error && (
          <Alert>
            <p className="font-medium">Checkout was not completed. Your cart is unchanged.</p>
            <p>{error.message}</p>
            {error.code === 'INSUFFICIENT_STOCK' || error.code === 'PRODUCT_INACTIVE' ? (
              <p className="mt-1">Close this window, adjust the cart, and try again.</p>
            ) : null}
          </Alert>
        )}

        <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          <Button variant="secondary" size="lg" onClick={onClose} disabled={mutation.isPending}>
            Back to cart
          </Button>
          <Button type="submit" size="lg" disabled={!canSubmit || mutation.isPending}>
            {mutation.isPending ? 'Processing…' : `Complete sale · ${formatMoney(preview.grandTotal)}`}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
