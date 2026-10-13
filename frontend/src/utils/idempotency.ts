/** A fresh opaque key for one checkout attempt (letters, digits, '-' and '_' only; max 64). */
export function newIdempotencyKey(): string {
  const random =
    typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? crypto.randomUUID()
      : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`
  return `co-${random}`.slice(0, 64)
}
