import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { ReceiptView } from '../components/receipt/ReceiptView'
import type { ReceiptWidth } from '../components/receipt/ReceiptView'
import { Button } from '../components/ui/Button'
import { Icon } from '../components/ui/Icon'
import { Alert, Spinner } from '../components/ui/feedback'
import { errorMessage } from '../services/errors'
import { salesService } from '../services/salesService'

/**
 * Dedicated print view (rendered outside the app shell, so there is no navigation to hide).
 * Reprinting only reads the stored sale; it never modifies it.
 */
export default function ReceiptPage() {
  const { saleNumber = '' } = useParams<{ saleNumber: string }>()
  const [params] = useSearchParams()
  const [width, setWidth] = useState<ReceiptWidth>(80)

  const receipt = useQuery({
    queryKey: ['receipt', saleNumber],
    queryFn: () => salesService.receipt(saleNumber),
  })

  const autoPrint = params.get('print') === '1'
  useEffect(() => {
    if (autoPrint && receipt.data) {
      // let the receipt paint before the print dialog opens
      const timer = setTimeout(() => window.print(), 300)
      return () => clearTimeout(timer)
    }
  }, [autoPrint, receipt.data])

  return (
    <div className="receipt-page min-h-screen bg-slate-200 px-4 pt-6 pb-16">
      {/* paper size for the browser's print dialog */}
      <style>{`@media print { @page { size: ${width}mm auto; margin: 3mm; } }`}</style>

      <div className="no-print mx-auto mb-8 flex max-w-xl flex-wrap items-center gap-3">
        <Link
          to="/pos"
          className="inline-flex items-center gap-1.5 text-sm font-medium text-slate-700 hover:text-ink"
        >
          <Icon name="arrowLeft" size={16} />
          Back to POS
        </Link>
        <div className="ml-auto flex items-center gap-3">
          <label className="flex items-center gap-2 text-sm text-slate-700">
            Paper
            <select
              aria-label="Paper width"
              value={width}
              onChange={(event) => setWidth(Number(event.target.value) as ReceiptWidth)}
              className="h-10 rounded-[10px] border border-slate-300 bg-card px-3 text-sm focus:border-emerald-600 focus:outline-none"
            >
              <option value={58}>58 mm</option>
              <option value={80}>80 mm</option>
            </select>
          </label>
          <Button onClick={() => window.print()} disabled={!receipt.data}>
            <Icon name="printer" size={17} />
            Print receipt
          </Button>
        </div>
      </div>

      {receipt.isPending && <Spinner label="Loading receipt…" />}
      {receipt.isError && (
        <div className="no-print mx-auto max-w-xl">
          <Alert>Could not load this receipt: {errorMessage(receipt.error)}</Alert>
        </div>
      )}
      {receipt.data && (
        <div className="receipt-sheet ticket mx-auto w-fit bg-white px-4 py-9 animate-rise">
          <ReceiptView receipt={receipt.data} width={width} />
        </div>
      )}
    </div>
  )
}
