import { Link } from 'react-router-dom'
import { Button } from '../ui/Button'
import type { Sale } from '../../types/api'
import { formatMoney } from '../../utils/format'

interface SaleSuccessProps {
  sale: Sale
  onNewSale: () => void
}

export function SaleSuccess({ sale, onNewSale }: SaleSuccessProps) {
  const changeDue = sale.payment?.changeDue

  return (
    <section
      aria-label="Sale complete"
      className="rounded-xl bg-white p-6 text-center shadow-sm ring-1 ring-emerald-200"
    >
      <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-emerald-100 text-2xl text-emerald-700">
        ✓
      </div>
      <h2 className="text-xl font-semibold text-slate-900">Sale complete</h2>
      <p className="mt-1 text-sm text-slate-500">
        Sale number <span className="font-mono font-semibold text-slate-800">{sale.saleNumber}</span>
      </p>

      <dl className="mx-auto mt-4 max-w-xs space-y-1 text-left">
        <div className="flex justify-between">
          <dt className="text-slate-600">Total</dt>
          <dd className="font-semibold tabular-nums">{formatMoney(sale.grandTotal)}</dd>
        </div>
        <div className="flex justify-between">
          <dt className="text-slate-600">Paid by</dt>
          <dd>{sale.payment?.method ?? '—'}</dd>
        </div>
        {changeDue !== null && changeDue !== undefined && (
          <div className="flex justify-between text-lg">
            <dt className="font-medium text-slate-800">Change due</dt>
            <dd className="font-bold tabular-nums text-emerald-700">{formatMoney(changeDue)}</dd>
          </div>
        )}
      </dl>

      <div className="mt-6 flex flex-wrap justify-center gap-3">
        <a
          href={`/receipt/${encodeURIComponent(sale.saleNumber)}?print=1`}
          target="_blank"
          rel="noreferrer"
          className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800"
        >
          Print receipt
        </a>
        <Link
          to={`/sales/${sale.id}`}
          className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
        >
          View sale
        </Link>
        <Button onClick={onNewSale} autoFocus>
          New sale
        </Button>
      </div>
    </section>
  )
}
