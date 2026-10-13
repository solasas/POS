import { Link } from 'react-router-dom'
import { Badge } from '../ui/feedback'
import { Icon } from '../ui/Icon'
import type { IconName } from '../ui/Icon'
import type { PaymentMethod, SaleSummary } from '../../types/api'
import { formatDateTime, formatMoney } from '../../utils/format'

const METHOD_ICON: Record<PaymentMethod, IconName> = { CASH: 'cash', UPI: 'upi', CARD: 'card' }

export function SalesTable({ sales }: { sales: SaleSummary[] }) {
  return (
    <div className="card overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead>
          <tr className="border-b border-slate-200 text-[11px] font-semibold tracking-[0.08em] text-slate-500 uppercase">
            <th scope="col" className="px-5 py-3.5">Sale number</th>
            <th scope="col" className="px-4 py-3.5">Date</th>
            <th scope="col" className="hidden px-4 py-3.5 md:table-cell">Cashier</th>
            <th scope="col" className="px-4 py-3.5 text-right">Items</th>
            <th scope="col" className="px-4 py-3.5 text-right">Total</th>
            <th scope="col" className="px-4 py-3.5">Payment</th>
            <th scope="col" className="px-4 py-3.5">Status</th>
            <th scope="col" className="px-5 py-3.5 text-right">
              <span className="sr-only">Actions</span>
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {sales.map((sale) => (
            <tr key={sale.id} className="transition hover:bg-slate-100/50">
              <td className="px-5 py-3.5 font-mono text-[13px] font-medium text-ink">{sale.saleNumber}</td>
              <td className="px-4 py-3.5 whitespace-nowrap text-slate-600">{formatDateTime(sale.soldAt)}</td>
              <td className="hidden px-4 py-3.5 text-slate-600 md:table-cell">{sale.cashierName}</td>
              <td className="num px-4 py-3.5 text-right text-slate-600">{sale.itemCount}</td>
              <td className="num px-4 py-3.5 text-right font-display text-[15px] font-semibold text-ink">
                {formatMoney(sale.grandTotal)}
              </td>
              <td className="px-4 py-3.5">
                <span className="inline-flex items-center gap-2 text-slate-700">
                  {sale.paymentMethod && <Icon name={METHOD_ICON[sale.paymentMethod]} size={16} className="text-slate-400" />}
                  {sale.paymentMethod ?? '—'}
                </span>
              </td>
              <td className="px-4 py-3.5">
                <Badge tone={sale.status === 'COMPLETED' ? 'green' : 'red'}>
                  {sale.status === 'COMPLETED' ? 'Completed' : 'Voided'}
                </Badge>
              </td>
              <td className="px-5 py-3.5 text-right whitespace-nowrap">
                <Link
                  to={`/sales/${sale.id}`}
                  className="rounded-md px-2 py-1 text-[13px] font-medium text-emerald-700 hover:bg-emerald-50"
                >
                  View
                </Link>
                <a
                  href={`/receipt/${encodeURIComponent(sale.saleNumber)}`}
                  target="_blank"
                  rel="noreferrer"
                  className="rounded-md px-2 py-1 text-[13px] font-medium text-slate-600 hover:bg-slate-200/60"
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
