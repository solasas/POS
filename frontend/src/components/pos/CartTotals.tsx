import type { PricePreview } from '../../types/api'
import { formatMoney } from '../../utils/format'

interface CartTotalsProps {
  preview: PricePreview | undefined
  loading: boolean
}

function Row({ label, value, strong = false }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className={`flex justify-between ${strong ? 'text-lg font-semibold text-slate-900' : 'text-sm text-slate-600'}`}>
      <dt>{label}</dt>
      <dd className="tabular-nums">{value}</dd>
    </div>
  )
}

/** Totals shown here are the server's figures from /api/sales/preview, never computed in the browser. */
export function CartTotals({ preview, loading }: CartTotalsProps) {
  const money = (value: number | undefined) => (value === undefined ? '—' : formatMoney(value))
  return (
    <dl aria-label="Order totals" aria-busy={loading} className="space-y-1">
      <Row label="Subtotal" value={money(preview?.subtotal)} />
      <Row label="Discount" value={preview ? `−${formatMoney(preview.discountTotal)}` : '—'} />
      <Row label={preview?.pricesIncludeTax ? 'Tax (included)' : 'Tax'} value={money(preview?.taxTotal)} />
      <div className="border-t border-slate-200 pt-2">
        <Row label="Total payable" value={money(preview?.grandTotal)} strong />
      </div>
    </dl>
  )
}
