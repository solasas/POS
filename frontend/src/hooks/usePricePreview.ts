import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { posService } from '../services/posService'
import type { CartLine } from './useCart'
import { useDebouncedValue } from './useDebouncedValue'

/**
 * Server-calculated totals for the current basket. The key changes whenever an id, quantity or the
 * discount changes; the request is debounced so rapid quantity edits do not flood the API.
 */
export function usePricePreview(lines: CartLine[], discountPercent: number) {
  const signature = lines.map((l) => `${l.product.id}:${l.quantity}`).join('|') + `#${discountPercent}`
  const debounced = useDebouncedValue(signature, 150)

  const enabled = lines.length > 0 && debounced === signature
  return useQuery({
    queryKey: ['pos', 'preview', debounced],
    queryFn: () =>
      posService.preview(
        lines.map((l) => ({ productId: l.product.id, quantity: l.quantity })),
        discountPercent,
      ),
    enabled,
    placeholderData: keepPreviousData,
    retry: false,
  })
}
