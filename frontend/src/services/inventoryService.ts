import type { InventoryTransaction, StockAdjustmentInput } from '../types/api'
import { apiClient } from './apiClient'

export const inventoryService = {
  async adjust(input: StockAdjustmentInput): Promise<InventoryTransaction> {
    const { data } = await apiClient.post<InventoryTransaction>('/inventory/adjustments', input)
    return data
  },
}
