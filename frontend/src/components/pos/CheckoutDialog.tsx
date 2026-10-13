import { useRef, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Modal } from '../ui/Modal'
import { Button } from '../ui/Button'
import { Alert } from '../ui/feedback'
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

const METHODS: { value: PaymentMethod; label: string; hint: string }[] = [
  { value: 'CASH', label: 'Cash', hint: 'Enter the amount handed over' },
  { value: 'UPI', label: 'UPI', hint: 'Confirm the customer has paid' },
  { value: 'CARD', label: 'Card', hint: 'Confirm the card payment went through' },
]

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
          <ul className="max-h-40 divide-y divide-slate-100 overflow-y-auto text-sm">
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
          <legend className="mb-2 text-sm font-medium text-slate-700">Payment method</legend>
          <div className="grid grid-cols-3 gap-2">
            {METHODS.map((option) => (
              <label
                key={option.value}
                className={`cursor-pointer rounded-lg border-2 px-3 py-2 text-center text-sm font-medium ${
                  method === option.value
                    ? 'border-emerald-600 bg-emerald-50 text-emerald-800'
                    : 'border-slate-200 text-slate-700 hover:border-slate-300'
                }`}
              >
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
            <label htmlFor="amount-tendered" className="mb-1 block text-sm font-medium text-slate-700">
              Amount tendered
            </label>
            <input
              id="amount-tendered"
              inputMode="decimal"
              value={tenderedText}
              onChange={(event) => setTenderedText(event.target.value)}
              className="block w-full rounded-md border border-slate-300 px-3 py-2 text-lg tabular-nums focus:border-emerald-600 focus:outline-none"
              autoFocus
            />
            {shortBy !== null && (
              <p role="alert" className="mt-1 text-sm font-medium text-red-600">
                {formatMoney(shortBy)} short of the amount payable.
              </p>
            )}
            {changeDue !== null && (
              <p className="mt-1 text-sm font-semibold text-emerald-700">Change due: {formatMoney(changeDue)}</p>
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

        <div className="flex justify-end gap-3">
          <Button variant="secondary" onClick={onClose} disabled={mutation.isPending}>
            Back to cart
          </Button>
          <Button type="submit" disabled={!canSubmit || mutation.isPending}>
            {mutation.isPending ? 'Processing…' : `Complete sale · ${formatMoney(preview.grandTotal)}`}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
