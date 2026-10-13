import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { Alert, Badge, Spinner } from '../components/ui/feedback'
import { errorMessage } from '../services/errors'
import { salesService } from '../services/salesService'
import { formatMoney, formatQuantity, UNIT_SHORT, formatDateTimeLong } from '../utils/format'


export default function SaleDetailPage() {
  const { id = '' } = useParams<{ id: string }>()
  const sale = useQuery({ queryKey: ['sale', id], queryFn: () => salesService.get(id) })

  return (
    <div className="mx-auto max-w-3xl space-y-5">
      <Link to="/sales" className="text-sm text-emerald-700 hover:underline">
        ← Back to sales
      </Link>

      {sale.isPending && <Spinner label="Loading sale…" />}
      {sale.isError && <Alert>Could not load this sale: {errorMessage(sale.error)}</Alert>}

      {sale.data && (
        <>
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h1 className="font-mono text-2xl font-semibold text-slate-900">{sale.data.saleNumber}</h1>
              <p className="text-sm text-slate-500">{formatDateTimeLong(sale.data.soldAt)}</p>
              <p className="text-sm text-slate-500">Cashier: {sale.data.cashierName}</p>
            </div>
            <div className="flex items-center gap-3">
              <Badge tone={sale.data.status === 'COMPLETED' ? 'green' : 'red'}>{sale.data.status}</Badge>
              <a
                href={`/receipt/${encodeURIComponent(sale.data.saleNumber)}`}
                target="_blank"
                rel="noreferrer"
                className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800"
              >
                Open receipt
              </a>
            </div>
          </div>

          <div className="overflow-x-auto rounded-lg bg-white shadow-sm ring-1 ring-slate-200">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th scope="col" className="px-4 py-3">Item</th>
                  <th scope="col" className="px-4 py-3 text-right">Qty</th>
                  <th scope="col" className="px-4 py-3 text-right">Unit price</th>
                  <th scope="col" className="px-4 py-3 text-right">Discount</th>
                  <th scope="col" className="px-4 py-3 text-right">Tax</th>
                  <th scope="col" className="px-4 py-3 text-right">Total</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {sale.data.items.map((item) => (
                  <tr key={item.productId}>
                    <td className="px-4 py-3">
                      <div className="font-medium text-slate-900">{item.name}</div>
                      <div className="text-xs text-slate-500">
                        {item.sku}
                        {item.barcode ? ` · ${item.barcode}` : ''}
                      </div>
                    </td>
                    <td className="px-4 py-3 text-right tabular-nums">
                      {formatQuantity(item.quantity)} {UNIT_SHORT[item.unit]}
                    </td>
                    <td className="px-4 py-3 text-right tabular-nums">{formatMoney(item.unitPrice)}</td>
                    <td className="px-4 py-3 text-right tabular-nums">
                      {item.discountAmount > 0 ? `−${formatMoney(item.discountAmount)}` : '—'}
                    </td>
                    <td className="px-4 py-3 text-right tabular-nums">{formatMoney(item.taxAmount)}</td>
                    <td className="px-4 py-3 text-right font-medium tabular-nums">{formatMoney(item.lineTotal)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <dl className="space-y-1 rounded-lg bg-white p-4 text-sm shadow-sm ring-1 ring-slate-200">
              <div className="flex justify-between"><dt className="text-slate-600">Subtotal</dt><dd className="tabular-nums">{formatMoney(sale.data.subtotal)}</dd></div>
              <div className="flex justify-between"><dt className="text-slate-600">Discount ({sale.data.discountPercent}%)</dt><dd className="tabular-nums">−{formatMoney(sale.data.discountTotal)}</dd></div>
              <div className="flex justify-between"><dt className="text-slate-600">{sale.data.pricesIncludeTax ? 'Tax (included)' : 'Tax'}</dt><dd className="tabular-nums">{formatMoney(sale.data.taxTotal)}</dd></div>
              <div className="flex justify-between border-t border-slate-200 pt-2 text-base font-semibold"><dt>Total</dt><dd className="tabular-nums">{formatMoney(sale.data.grandTotal)}</dd></div>
            </dl>

            {sale.data.payment && (
              <dl className="space-y-1 rounded-lg bg-white p-4 text-sm shadow-sm ring-1 ring-slate-200">
                <div className="flex justify-between"><dt className="text-slate-600">Payment method</dt><dd>{sale.data.payment.method}</dd></div>
                <div className="flex justify-between"><dt className="text-slate-600">Amount</dt><dd className="tabular-nums">{formatMoney(sale.data.payment.amount)}</dd></div>
                {sale.data.payment.amountTendered !== null && (
                  <div className="flex justify-between"><dt className="text-slate-600">Tendered</dt><dd className="tabular-nums">{formatMoney(sale.data.payment.amountTendered)}</dd></div>
                )}
                {sale.data.payment.changeDue !== null && (
                  <div className="flex justify-between"><dt className="text-slate-600">Change</dt><dd className="tabular-nums">{formatMoney(sale.data.payment.changeDue)}</dd></div>
                )}
                <p className="pt-2 text-xs text-slate-500">
                  Recorded by the cashier. Not verified with a payment provider.
                </p>
              </dl>
            )}
          </div>
        </>
      )}
    </div>
  )
}
