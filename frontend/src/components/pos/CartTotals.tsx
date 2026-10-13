import type { PricePreview } from '../../types/api'
import { formatMoney } from '../../utils/format'

interface CartTotalsProps {
  preview: PricePreview | undefined
  loading: boolean
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between text-sm text-slate-600">
      <dt>{label}</dt>
      <dd className="num">{value}</dd>
    </div>
  )
}

/** Totals shown here are the server's figures from /api/sales/preview, never computed in the browser. */
export function CartTotals({ preview, loading }: CartTotalsProps) {
  const money = (value: number | undefined) => (value === undefined ? '—' : formatMoney(value))
  return (
    <dl aria-label="Order totals" aria-busy={loading} className="space-y-1.5">
      <Row label="Subtotal" value={money(preview?.subtotal)} />
      <Row label="Discount" value={preview ? `−${formatMoney(preview.discountTotal)}` : '—'} />
      <Row label={preview?.pricesIncludeTax ? 'Tax (included)' : 'Tax'} value={money(preview?.taxTotal)} />
      <div className="flex items-end justify-between border-t border-dashed border-slate-300 pt-3">
        <dt className="pb-1 text-sm font-semibold tracking-wide text-slate-700 uppercase">Total</dt>
        <dd
          aria-label="Total payable"
          className={`num font-display text-[2.4rem] leading-none font-bold tracking-tight text-ink transition ${
            loading ? 'opacity-50' : ''
          }`}
        >
          {money(preview?.grandTotal)}
        </dd>
      </div>
    </dl>
  )
}
