import { Link } from 'react-router-dom'
import { Icon } from '../ui/Icon'
import type { IconName } from '../ui/Icon'
import type { PaymentMethod, SaleSummary } from '../../types/api'
import { formatMoney } from '../../utils/format'

const ICON: Record<PaymentMethod, IconName> = { CASH: 'cash', UPI: 'upi', CARD: 'card' }

const time = new Intl.DateTimeFormat('en-IN', { hour: 'numeric', minute: '2-digit' })
const dayTime = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })

function when(iso: string): string {
  const date = new Date(iso)
  const now = new Date()
  const sameDay = date.toDateString() === now.toDateString()
  return sameDay ? time.format(date) : dayTime.format(date)
}

export function RecentSales({ sales, loading }: { sales: SaleSummary[]; loading: boolean }) {
  return (
    <section aria-label="Recent sales" className="card p-6">
      <div className="mb-3 flex items-baseline justify-between">
        <h2 className="font-display text-lg font-bold text-ink">Recent bills</h2>
        <Link to="/sales" className="text-sm font-medium text-emerald-700 hover:underline">
          All sales
        </Link>
      </div>

      {loading && (
        <div className="space-y-3" aria-hidden="true">
          {[0, 1, 2, 3].map((i) => (
            <div key={i} className="skeleton h-11" />
          ))}
        </div>
      )}

      {!loading && sales.length === 0 && (
        <p className="py-6 text-sm text-slate-500">Bills will appear here as soon as the first sale is rung up.</p>
      )}

      <ul className="-mx-2 divide-y divide-slate-100">
        {sales.map((sale) => (
          <li key={sale.id}>
            <Link
              to={`/sales/${sale.id}`}
              className="flex items-center gap-4 rounded-lg px-2 py-3 transition hover:bg-slate-100/70"
            >
              <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-slate-100 text-slate-600">
                <Icon name={sale.paymentMethod ? ICON[sale.paymentMethod] : 'receipt'} size={17} />
              </span>
              <span className="min-w-0 flex-1">
                <span className="block font-mono text-[13px] font-medium text-ink">{sale.saleNumber}</span>
                <span className="block truncate text-xs text-slate-500">
                  {when(sale.soldAt)} · {sale.itemCount} {sale.itemCount === 1 ? 'item' : 'items'} · {sale.cashierName}
                </span>
              </span>
              <span className="num font-display text-base font-semibold text-ink">{formatMoney(sale.grandTotal)}</span>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}
