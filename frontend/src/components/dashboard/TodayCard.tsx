import { Icon } from '../ui/Icon'
import type { IconName } from '../ui/Icon'
import type { PaymentMethod, SalesSummary } from '../../types/api'
import { formatMoney } from '../../utils/format'

const METHOD: Record<PaymentMethod, { label: string; bar: string; icon: IconName }> = {
  CASH: { label: 'Cash', bar: 'bg-emerald-600', icon: 'cash' },
  UPI: { label: 'UPI', bar: 'bg-amber-400', icon: 'upi' },
  CARD: { label: 'Card', bar: 'bg-slate-800', icon: 'card' },
}

interface TodayCardProps {
  summary: SalesSummary | undefined
  loading: boolean
}

/** Today's takings: the figure a shopkeeper checks first, then how it was paid. */
export function TodayCard({ summary, loading }: TodayCardProps) {
  const total = summary?.grandTotal ?? 0
  const bills = summary?.saleCount ?? 0
  const average = bills > 0 ? total / bills : 0

  return (
    <section aria-label="Today's sales" className="card overflow-hidden">
      <div className="p-6 sm:p-7">
        <p className="eyebrow">Today's sales</p>
        {loading ? (
          <div className="skeleton mt-3 h-12 w-56" />
        ) : (
          <p className="num mt-2 font-display text-[2.75rem] leading-none font-bold tracking-tight text-ink sm:text-[3.5rem]">
            {formatMoney(total)}
          </p>
        )}

        <dl className="mt-6 grid grid-cols-2 gap-6 sm:max-w-sm">
          <div>
            <dt className="text-xs text-slate-500">Bills</dt>
            <dd className="num mt-0.5 font-display text-2xl font-semibold text-ink">{loading ? '—' : bills}</dd>
          </div>
          <div>
            <dt className="text-xs text-slate-500">Average bill</dt>
            <dd className="num mt-0.5 font-display text-2xl font-semibold text-ink">
              {loading ? '—' : formatMoney(average)}
            </dd>
          </div>
        </dl>
      </div>

      <div className="border-t border-slate-200 bg-slate-100/50 px-6 py-5 sm:px-7">
        <p className="eyebrow mb-3">How customers paid</p>
        {summary && summary.byPaymentMethod.length > 0 && total > 0 ? (
          <>
            <div
              role="img"
              aria-label={summary.byPaymentMethod
                .map((m) => `${METHOD[m.method].label} ${formatMoney(m.grandTotal)}`)
                .join(', ')}
              className="flex h-3 overflow-hidden rounded-full bg-slate-200"
            >
              {summary.byPaymentMethod.map((m) => (
                <span
                  key={m.method}
                  className={`${METHOD[m.method].bar} first:rounded-l-full last:rounded-r-full`}
                  style={{ width: `${(m.grandTotal / total) * 100}%` }}
                />
              ))}
            </div>
            <ul className="mt-4 flex flex-wrap gap-x-8 gap-y-3">
              {summary.byPaymentMethod.map((m) => (
                <li key={m.method} className="flex items-center gap-2.5 text-sm">
                  <span className={`h-2.5 w-2.5 rounded-full ${METHOD[m.method].bar}`} />
                  <span className="text-slate-600">{METHOD[m.method].label}</span>
                  <span className="num font-semibold text-ink">{formatMoney(m.grandTotal)}</span>
                  <span className="num text-xs text-slate-400">{Math.round((m.grandTotal / total) * 100)}%</span>
                </li>
              ))}
            </ul>
          </>
        ) : (
          <p className="flex items-center gap-2 text-sm text-slate-500">
            <Icon name="info" size={16} />
            {loading ? 'Loading…' : 'No sales yet today. The first bill will show up here.'}
          </p>
        )}
      </div>
    </section>
  )
}
