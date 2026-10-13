import { Link } from 'react-router-dom'
import { Button } from '../ui/Button'
import { Icon } from '../ui/Icon'
import type { Sale } from '../../types/api'
import { formatMoney } from '../../utils/format'

interface SaleSuccessProps {
  sale: Sale
  onNewSale: () => void
}

export function SaleSuccess({ sale, onNewSale }: SaleSuccessProps) {
  const changeDue = sale.payment?.changeDue

  return (
    <section aria-label="Sale complete" className="ticket px-6 pt-10 pb-10 text-center animate-rise">
      <div className="animate-pop mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-emerald-700 text-white shadow-[0_0_0_8px_var(--color-emerald-100)]">
        <Icon name="check" size={30} strokeWidth={2.5} />
      </div>
      <h2 className="mt-5 font-display text-2xl font-bold text-ink">Sale complete</h2>
      <p className="mt-1 text-sm text-slate-500">
        Sale number <span className="font-mono font-semibold text-ink">{sale.saleNumber}</span>
      </p>

      <dl className="mx-auto mt-6 max-w-xs space-y-2 text-left">
        <div className="flex justify-between text-sm">
          <dt className="text-slate-600">Total</dt>
          <dd className="num font-semibold text-ink">{formatMoney(sale.grandTotal)}</dd>
        </div>
        <div className="flex justify-between text-sm">
          <dt className="text-slate-600">Paid by</dt>
          <dd className="font-medium text-ink">{sale.payment?.method ?? '—'}</dd>
        </div>
        {changeDue !== null && changeDue !== undefined && (
          <div className="mt-3 rounded-xl bg-amber-100 px-4 py-3">
            <div className="flex items-end justify-between">
              <dt className="pb-1 text-sm font-semibold text-amber-900">Change due</dt>
              <dd className="num font-display text-[2rem] leading-none font-bold text-ink">
                {formatMoney(changeDue)}
              </dd>
            </div>
          </div>
        )}
      </dl>

      <div className="mt-8 grid gap-3">
        <Button size="lg" onClick={onNewSale} autoFocus>
          New sale
        </Button>
        <div className="grid grid-cols-2 gap-3">
          <a
            href={`/receipt/${encodeURIComponent(sale.saleNumber)}?print=1`}
            target="_blank"
            rel="noreferrer"
            className="inline-flex h-10 items-center justify-center gap-2 rounded-[10px] border border-slate-300 bg-card text-sm font-medium text-slate-800 hover:border-slate-400 hover:bg-white"
          >
            <Icon name="printer" size={17} />
            Print receipt
          </a>
          <Link
            to={`/sales/${sale.id}`}
            className="inline-flex h-10 items-center justify-center rounded-[10px] border border-slate-300 bg-card text-sm font-medium text-slate-800 hover:border-slate-400 hover:bg-white"
          >
            View sale
          </Link>
        </div>
      </div>
    </section>
  )
}
