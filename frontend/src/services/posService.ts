import type { CartRequestItem, PosProduct, PricePreview } from '../types/api'
import { apiClient } from './apiClient'

export const posService = {
  async search(query: string): Promise<PosProduct[]> {
    const { data } = await apiClient.get<PosProduct[]>('/pos/products', { params: { query } })
    return data
  },

  /** Exact barcode or SKU match. Rejects with a 404 ApiError (code UNKNOWN_BARCODE) when nothing matches. */
  async byBarcode(code: string): Promise<PosProduct> {
    const { data } = await apiClient.get<PosProduct>(`/pos/products/barcode/${encodeURIComponent(code)}`)
    return data
  },

  /** Server-calculated totals for a basket; nothing is persisted. */
  async preview(items: CartRequestItem[], discountPercent: number): Promise<PricePreview> {
    const { data } = await apiClient.post<PricePreview>('/sales/preview', { items, discountPercent })
    return data
  },
}
