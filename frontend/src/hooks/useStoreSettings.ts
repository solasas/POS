import { useQuery } from '@tanstack/react-query'
import { settingsService } from '../services/settingsService'

/** Store-level switches. Refreshed periodically so a manager's change reaches open tills. */
export function useStoreSettings() {
  return useQuery({
    queryKey: ['settings'],
    queryFn: settingsService.get,
    staleTime: 15_000,
    refetchInterval: 30_000,
  })
}
