import type { StoreSettings } from '../types/api'
import { apiClient } from './apiClient'

export const settingsService = {
  async get(): Promise<StoreSettings> {
    const { data } = await apiClient.get<StoreSettings>('/settings')
    return data
  },

  /** MANAGER+ only. */
  async setAllowNegativeStock(allowNegativeStock: boolean): Promise<StoreSettings> {
    const { data } = await apiClient.put<StoreSettings>('/settings', { allowNegativeStock })
    return data
  },
}
