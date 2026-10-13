import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { Alert, Badge, Spinner } from '../components/ui/feedback'
import { Icon } from '../components/ui/Icon'
import { errorMessage } from '../services/errors'
import { salesService } from '../services/salesService'
import { formatDateTimeLong, formatMoney, formatQuantity, UNIT_SHORT } from '../utils/format'

function Line({ label, value, strong = false }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className={`flex justify-between ${strong ? 'items-end border-t border-dashed border-slate-300 pt-3' : 'text-sm'}`}>
      <dt className={strong ? 'pb-0.5 text-sm font-semibold tracking-wide text-slate-700 uppercase' : 'text-slate-600'}>
        {label}
      </dt>
      <dd className={`num ${strong ? 'font-display text-3xl leading-none font-bold text-ink' : 'text-ink'}`}>{value}</dd>
    </div>
  )
}

export default function SaleDetailPage() {
  const { id = '' } = useParams<{ id: string }>()
  const sale = useQuery({ queryKey: ['sale', id], queryFn: () => salesService.get(id) })

  return (
    <div className="mx-auto max-w-4xl">
      <Link to="/sales" className="mb-4 inline-flex items-center gap-1.5 text-sm font-medium text-slate-600 hover:text-ink">
        <Icon name="arrowLeft" size={16} />
        Back to sales
      </Link>

      {sale.isPending && <Spinner label="Loading sale…" />}
      {sale.isError && <Alert>Could not load this sale: {errorMessage(sale.error)}</Alert>}

      {sale.data && (
        <div className="space-y-6">
          <header className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <p className="eyebrow mb-1.5">Sale</p>
              <h1 className="font-mono text-[1.75rem] leading-tight font-semibold tracking-tight text-ink">
                {sale.data.saleNumber}
              </h1>
              <p className="mt-1 text-sm text-slate-500">
                {formatDateTimeLong(sale.data.soldAt)} · Cashier {sale.data.cashierName}
              </p>
            </div>
            <div className="flex items-center gap-3">
              <Badge tone={sale.data.status === 'COMPLETED' ? 'green' : 'red'}>
                {sale.data.status === 'COMPLETED' ? 'Completed' : 'Voided'}
              </Badge>
              <a
                href={`/receipt/${encodeURIComponent(sale.data.saleNumber)}`}
                target="_blank"
                rel="noreferrer"
                className="inline-flex h-10 items-center gap-2 rounded-[10px] bg-emerald-700 px-4 text-sm font-medium text-white shadow-[inset_0_-2px_0_rgb(0_0_0/0.18)] hover:bg-emerald-800"
              >
                <Icon name="printer" size={17} />
                Open receipt
              </a>
            </div>
          </header>

          <div className="card overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-[11px] font-semibold tracking-[0.08em] text-slate-500 uppercase">
                  <th scope="col" className="px-5 py-3.5">Item</th>
                  <th scope="col" className="px-4 py-3.5 text-right">Qty</th>
                  <th scope="col" className="px-4 py-3.5 text-right">Unit price</th>
                  <th scope="col" className="px-4 py-3.5 text-right">Discount</th>
                  <th scope="col" className="px-4 py-3.5 text-right">Tax</th>
                  <th scope="col" className="px-5 py-3.5 text-right">Total</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {sale.data.items.map((item) => (
                  <tr key={item.productId}>
                    <td className="px-5 py-3.5">
                      <div className="font-medium text-ink">{item.name}</div>
                      <div className="mt-0.5 font-mono text-xs text-slate-500">
                        {item.sku}
                        {item.barcode ? ` · ${item.barcode}` : ''}
                      </div>
                    </td>
                    <td className="num px-4 py-3.5 text-right">
                      {formatQuantity(item.quantity)} {UNIT_SHORT[item.unit]}
                    </td>
                    <td className="num px-4 py-3.5 text-right">{formatMoney(item.unitPrice)}</td>
                    <td className="num px-4 py-3.5 text-right text-slate-600">
                      {item.discountAmount > 0 ? `−${formatMoney(item.discountAmount)}` : '—'}
                    </td>
                    <td className="num px-4 py-3.5 text-right text-slate-600">{formatMoney(item.taxAmount)}</td>
                    <td className="num px-5 py-3.5 text-right font-display text-[15px] font-semibold text-ink">
                      {formatMoney(item.lineTotal)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="grid gap-6 md:grid-cols-2">
            <section className="card p-6" aria-label="Totals">
              <h2 className="eyebrow mb-4">Totals</h2>
              <dl className="space-y-2.5">
                <Line label="Subtotal" value={formatMoney(sale.data.subtotal)} />
                <Line label={`Discount (${sale.data.discountPercent}%)`} value={`−${formatMoney(sale.data.discountTotal)}`} />
                <Line label={sale.data.pricesIncludeTax ? 'Tax (included)' : 'Tax'} value={formatMoney(sale.data.taxTotal)} />
                <Line label="Total" value={formatMoney(sale.data.grandTotal)} strong />
              </dl>
            </section>

            {sale.data.payment && (
              <section className="card p-6" aria-label="Payment">
                <h2 className="eyebrow mb-4">Payment</h2>
                <dl className="space-y-2.5">
                  <Line label="Method" value={sale.data.payment.method} />
                  <Line label="Amount" value={formatMoney(sale.data.payment.amount)} />
                  {sale.data.payment.amountTendered !== null && (
                    <Line label="Tendered" value={formatMoney(sale.data.payment.amountTendered)} />
                  )}
                  {sale.data.payment.changeDue !== null && (
                    <Line label="Change given" value={formatMoney(sale.data.payment.changeDue)} />
                  )}
                </dl>
                <p className="mt-4 flex items-start gap-2 rounded-lg bg-slate-100 px-3 py-2.5 text-xs leading-snug text-slate-600">
                  <Icon name="info" size={15} className="mt-px shrink-0" />
                  Recorded by the cashier. Not verified with a payment provider.
                </p>
              </section>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
