import type { CheckoutPayload, Page, Receipt, Sale, SaleQuery, SaleSummary, SalesSummary } from '../types/api'
import { apiClient } from './apiClient'

export const salesService = {
  /**
   * @param idempotencyKey one key per checkout attempt; resending the same key and payload returns
   *                       the original sale instead of creating a second one.
   */
  async checkout(payload: CheckoutPayload, idempotencyKey: string): Promise<Sale> {
    const { data } = await apiClient.post<Sale>('/sales/checkout', payload, {
      headers: { 'Idempotency-Key': idempotencyKey },
    })
    return data
  },

  async list(query: SaleQuery, onlyMine: boolean): Promise<Page<SaleSummary>> {
    const url = onlyMine ? '/sales/my-sales' : '/sales'
    const { data } = await apiClient.get<Page<SaleSummary>>(url, { params: query })
    return data
  },

  /** Count and total of completed sales in [from, to), split by payment method. */
  async summary(range: { from?: string; to?: string }): Promise<SalesSummary> {
    const { data } = await apiClient.get<SalesSummary>('/sales/summary', { params: range })
    return data
  },

  async get(id: string): Promise<Sale> {
    const { data } = await apiClient.get<Sale>(`/sales/${id}`)
    return data
  },

  async receipt(saleNumber: string): Promise<Receipt> {
    const { data } = await apiClient.get<Receipt>(`/sales/receipt/${encodeURIComponent(saleNumber)}`)
    return data
  },
}
