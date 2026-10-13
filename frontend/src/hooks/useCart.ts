import { useCallback, useMemo, useReducer } from 'react'
import type { PosProduct } from '../types/api'
import { checkQuantity, roundQuantity } from '../utils/quantity'

export interface CartLine {
  product: PosProduct
  quantity: number
}

type Action =
  | { type: 'set'; product: PosProduct; quantity: number }
  | { type: 'remove'; productId: string }
  | { type: 'clear' }

function reducer(state: CartLine[], action: Action): CartLine[] {
  switch (action.type) {
    case 'set': {
      const exists = state.some((line) => line.product.id === action.product.id)
      return exists
        ? state.map((line) =>
            line.product.id === action.product.id
              ? { product: action.product, quantity: action.quantity }
              : line,
          )
        : [...state, { product: action.product, quantity: action.quantity }]
    }
    case 'remove':
      return state.filter((line) => line.product.id !== action.productId)
    case 'clear':
      return []
  }
}

export type CartResult = { ok: true; quantity: number } | { ok: false; reason: string }

/**
 * Temporary, client-only basket. Adding or removing items never touches stock; stock is only
 * deducted when the server completes a checkout.
 */
export function useCart({ allowBeyondStock = false }: { allowBeyondStock?: boolean } = {}) {
  const [lines, dispatch] = useReducer(reducer, [])

  /** Adds {@code amount} (default 1) of a product, increasing the quantity of an existing line. */
  const add = useCallback(
    (product: PosProduct, amount = 1): CartResult => {
      if (!product.active) {
        return { ok: false, reason: `${product.name} is inactive and cannot be sold.` }
      }
      if (!product.available) {
        return { ok: false, reason: `${product.name} is out of stock.` }
      }
      const current = lines.find((line) => line.product.id === product.id)?.quantity ?? 0
      const wanted = roundQuantity(current + amount)
      const check = checkQuantity(product, wanted, allowBeyondStock)
      if (!check.ok) {
        return check
      }
      dispatch({ type: 'set', product, quantity: check.quantity })
      return check
    },
    [lines, allowBeyondStock],
  )

  const setQuantity = useCallback(
    (product: PosProduct, quantity: number): CartResult => {
      const check = checkQuantity(product, quantity, allowBeyondStock)
      if (check.ok) {
        dispatch({ type: 'set', product, quantity: check.quantity })
      }
      return check
    },
    [allowBeyondStock],
  )

  const remove = useCallback((productId: string) => dispatch({ type: 'remove', productId }), [])
  const clear = useCallback(() => dispatch({ type: 'clear' }), [])

  const itemCount = useMemo(() => lines.length, [lines])

  return { lines, itemCount, add, setQuantity, remove, clear }
}
