import type { Role, UnitOfMeasure } from '../types/api'

/** Indian rupees with lakh/crore digit grouping (e.g. ₹1,23,456.00) unless overridden at build time. */
const CURRENCY = import.meta.env.VITE_CURRENCY || 'INR'
export const LOCALE = import.meta.env.VITE_LOCALE || 'en-IN'

const moneyFormat = new Intl.NumberFormat(LOCALE, { style: 'currency', currency: CURRENCY })
const quantityFormat = new Intl.NumberFormat(LOCALE, { maximumFractionDigits: 3 })
const dateTimeFormat = new Intl.DateTimeFormat(LOCALE, { dateStyle: 'medium', timeStyle: 'short' })
const longDateTimeFormat = new Intl.DateTimeFormat(LOCALE, { dateStyle: 'full', timeStyle: 'medium' })

export const formatMoney = (value: number) => moneyFormat.format(value)
export const formatQuantity = (value: number) => quantityFormat.format(value)
export const formatDateTime = (value: string | Date) => dateTimeFormat.format(new Date(value))
export const formatDateTimeLong = (value: string | Date) => longDateTimeFormat.format(new Date(value))

export const UNIT_LABELS: Record<UnitOfMeasure, string> = {
  PIECE: 'Piece',
  KG: 'Kilogram',
  GRAM: 'Gram',
  LITRE: 'Litre',
  MILLILITRE: 'Millilitre',
  PACKET: 'Packet',
  BOX: 'Box',
  DOZEN: 'Dozen',
}

export const UNIT_SHORT: Record<UnitOfMeasure, string> = {
  PIECE: 'pc',
  KG: 'kg',
  GRAM: 'g',
  LITRE: 'L',
  MILLILITRE: 'mL',
  PACKET: 'pkt',
  BOX: 'box',
  DOZEN: 'doz',
}

const ROLE_RANK: Record<Role, number> = { CASHIER: 1, MANAGER: 2, ADMIN: 3 }

/** Mirrors the backend role hierarchy: ADMIN implies MANAGER implies CASHIER. */
export function hasRole(role: Role | undefined, minimum: Role): boolean {
  return role !== undefined && ROLE_RANK[role] >= ROLE_RANK[minimum]
}
