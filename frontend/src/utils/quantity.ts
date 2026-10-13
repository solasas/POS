import type { PosProduct } from '../types/api'

/** Quantities are limited to three decimal places, matching the backend's NUMERIC(12,3). */
const DECIMALS = 3

export function roundQuantity(value: number): number {
  const factor = 10 ** DECIMALS
  return Math.round(value * factor) / factor
}

export type QuantityCheck = { ok: true; quantity: number } | { ok: false; reason: string }

/**
 * Validates a requested quantity for a product: positive, whole for countable units, and not more
 * than the stock currently on hand. This is a convenience for the cashier; the server re-checks
 * everything (and the stock) at checkout.
 */
export function checkQuantity(product: PosProduct, requested: number): QuantityCheck {
  if (!Number.isFinite(requested) || requested <= 0) {
    return { ok: false, reason: 'Quantity must be greater than zero.' }
  }
  const quantity = roundQuantity(requested)
  if (quantity <= 0) {
    return { ok: false, reason: 'Quantity must be greater than zero.' }
  }
  if (product.wholeQuantitiesOnly && !Number.isInteger(quantity)) {
    return { ok: false, reason: `${product.name} is sold in whole units.` }
  }
  if (quantity > product.stockQuantity) {
    return {
      ok: false,
      reason: `Only ${product.stockQuantity} of ${product.name} available.`,
    }
  }
  return { ok: true, quantity }
}
