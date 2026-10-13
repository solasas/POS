import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useToast } from '../../hooks/useToast'
import { errorMessage } from '../../services/errors'
import { settingsService } from '../../services/settingsService'
import { Icon } from '../ui/Icon'

interface StockPolicyToggleProps {
  enabled: boolean
  /** Only managers and admins may change the setting; cashiers see its state. */
  canChange: boolean
}

/**
 * "Bill even when out of stock": a store-wide switch. When on, a bill is never blocked by missing
 * stock; the balance simply goes negative and is made good when stock is received.
 */
export function StockPolicyToggle({ enabled, canChange }: StockPolicyToggleProps) {
  const queryClient = useQueryClient()
  const toast = useToast()

  const mutation = useMutation({
    mutationFn: (next: boolean) => settingsService.setAllowNegativeStock(next),
    onSuccess: (settings) => {
      queryClient.setQueryData(['settings'], settings)
      // availability of products at the till depends on the switch
      queryClient.invalidateQueries({ queryKey: ['pos'] })
      toast.success(
        settings.allowNegativeStock
          ? 'Billing is now allowed even when a product is out of stock.'
          : 'Billing now stops when a product runs out of stock.',
      )
    },
    onError: (error) => toast.error(errorMessage(error)),
  })

  const label = 'Bill even when out of stock'

  return (
    <div
      className={`flex items-start gap-3 rounded-xl border px-4 py-3 transition ${
        enabled ? 'border-amber-300 bg-amber-50' : 'border-slate-200 bg-card'
      }`}
    >
      <button
        type="button"
        role="switch"
        aria-checked={enabled}
        aria-label={label}
        disabled={!canChange || mutation.isPending}
        onClick={() => mutation.mutate(!enabled)}
        className={`relative mt-0.5 h-6 w-11 shrink-0 rounded-full transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 disabled:cursor-not-allowed ${
          enabled ? 'bg-amber-500' : 'bg-slate-300'
        } ${!canChange ? 'opacity-60' : ''}`}
      >
        <span
          className={`absolute top-0.5 left-0.5 h-5 w-5 rounded-full bg-white shadow transition-transform ${
            enabled ? 'translate-x-5' : ''
          }`}
        />
      </button>
      <div className="min-w-0 text-sm leading-snug">
        <p className="font-medium text-ink">
          {label}
          <span
            className={`ml-2 rounded-full px-2 py-0.5 align-middle text-[11px] font-semibold ${
              enabled ? 'bg-amber-200 text-amber-900' : 'bg-slate-200 text-slate-600'
            }`}
          >
            {enabled ? 'ON' : 'OFF'}
          </span>
        </p>
        <p className="mt-0.5 text-xs text-slate-500">
          {enabled ? (
            <>
              <Icon name="info" size={13} className="mr-1 inline -translate-y-px" />
              Stock can go below zero. Receive stock later from Products → Stock to correct the balance.
            </>
          ) : (
            'Items with no stock cannot be billed.'
          )}
          {!canChange && ' Only a manager or administrator can change this.'}
        </p>
      </div>
    </div>
  )
}
