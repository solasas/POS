import type { Role, UnitOfMeasure } from '../types/api'

const currency = import.meta.env.VITE_CURRENCY || 'USD'
const moneyFormat = new Intl.NumberFormat(undefined, { style: 'currency', currency })
const quantityFormat = new Intl.NumberFormat(undefined, { maximumFractionDigits: 3 })

export const formatMoney = (value: number) => moneyFormat.format(value)
export const formatQuantity = (value: number) => quantityFormat.format(value)

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
