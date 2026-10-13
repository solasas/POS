import { Link } from 'react-router-dom'
import { Badge } from '../ui/feedback'
import type { SaleSummary } from '../../types/api'
import { formatMoney } from '../../utils/format'

const dateTime = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })

export function SalesTable({ sales }: { sales: SaleSummary[] }) {
  return (
    <div className="overflow-x-auto rounded-lg bg-white shadow-sm ring-1 ring-slate-200">
      <table className="w-full text-left text-sm">
        <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
          <tr>
            <th scope="col" className="px-4 py-3">Sale number</th>
            <th scope="col" className="px-4 py-3">Date</th>
            <th scope="col" className="hidden px-4 py-3 md:table-cell">Cashier</th>
            <th scope="col" className="px-4 py-3 text-right">Items</th>
            <th scope="col" className="px-4 py-3 text-right">Total</th>
            <th scope="col" className="px-4 py-3">Payment</th>
            <th scope="col" className="px-4 py-3">Status</th>
            <th scope="col" className="px-4 py-3 text-right">
              <span className="sr-only">Actions</span>
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {sales.map((sale) => (
            <tr key={sale.id}>
              <td className="px-4 py-3 font-mono text-xs font-semibold text-slate-900">{sale.saleNumber}</td>
              <td className="px-4 py-3 whitespace-nowrap">{dateTime.format(new Date(sale.soldAt))}</td>
              <td className="hidden px-4 py-3 md:table-cell">{sale.cashierName}</td>
              <td className="px-4 py-3 text-right tabular-nums">{sale.itemCount}</td>
              <td className="px-4 py-3 text-right font-medium tabular-nums">{formatMoney(sale.grandTotal)}</td>
              <td className="px-4 py-3">{sale.paymentMethod ?? '—'}</td>
              <td className="px-4 py-3">
                <Badge tone={sale.status === 'COMPLETED' ? 'green' : 'red'}>
                  {sale.status === 'COMPLETED' ? 'Completed' : 'Voided'}
                </Badge>
              </td>
              <td className="space-x-3 px-4 py-3 text-right whitespace-nowrap">
                <Link to={`/sales/${sale.id}`} className="font-medium text-emerald-700 hover:underline">
                  View
                </Link>
                <a
                  href={`/receipt/${encodeURIComponent(sale.saleNumber)}`}
                  target="_blank"
                  rel="noreferrer"
                  className="font-medium text-slate-600 hover:underline"
                >
                  Receipt
                </a>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
