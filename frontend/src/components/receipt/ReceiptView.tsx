import type { Receipt } from '../../types/api'
import { formatMoney, formatQuantity, UNIT_SHORT } from '../../utils/format'

export type ReceiptWidth = 58 | 80

interface ReceiptViewProps {
  receipt: Receipt
  width: ReceiptWidth
}

function Row({ left, right, bold = false }: { left: string; right: string; bold?: boolean }) {
  return (
    <div className={`flex justify-between gap-2 ${bold ? 'font-bold' : ''}`}>
      <span>{left}</span>
      <span className="tabular-nums">{right}</span>
    </div>
  )
}

const dateTime = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })

/**
 * Thermal-paper receipt. Rendered only from the stored sale (never from live product data), so a
 * reprint is identical to the original. Width classes map to 58 mm and 80 mm paper.
 */
export function ReceiptView({ receipt, width }: ReceiptViewProps) {
  const { store, sale, footer } = receipt
  const payment = sale.payment

  return (
    <article
      data-testid="receipt"
      data-width={width}
      aria-label={`Receipt ${sale.saleNumber}`}
      className={`receipt receipt-${width}`}
    >
      <header className="receipt-center">
        <h1 className="receipt-title">{store.name}</h1>
        {store.address && <p>{store.address}</p>}
        {store.phone && <p>Tel: {store.phone}</p>}
        {store.taxId && <p>Tax ID: {store.taxId}</p>}
      </header>

      <hr className="receipt-rule" />
      <div>
        <Row left="Receipt" right={sale.saleNumber} bold />
        <Row left="Date" right={dateTime.format(new Date(sale.soldAt))} />
        <Row left="Cashier" right={sale.cashierName} />
        {sale.status === 'VOIDED' && <p className="receipt-center font-bold">*** VOIDED ***</p>}
      </div>

      <hr className="receipt-rule" />
      <ul className="receipt-items">
        {sale.items.map((item) => (
          <li key={item.productId}>
            <div className="receipt-item-name">{item.name}</div>
            <Row
              left={`${formatQuantity(item.quantity)} ${UNIT_SHORT[item.unit]} × ${formatMoney(item.unitPrice)}`}
              right={formatMoney(item.lineSubtotal)}
            />
            {item.discountAmount > 0 && <Row left="  Discount" right={`−${formatMoney(item.discountAmount)}`} />}
          </li>
        ))}
      </ul>

      <hr className="receipt-rule" />
      <div>
        <Row left="Subtotal" right={formatMoney(sale.subtotal)} />
        {sale.discountTotal > 0 && (
          <Row left={`Discount (${sale.discountPercent}%)`} right={`−${formatMoney(sale.discountTotal)}`} />
        )}
        {sale.taxTotal > 0 || sale.items.some((i) => i.taxRatePercent > 0) ? (
          <Row left={sale.pricesIncludeTax ? 'Tax (included)' : 'Tax'} right={formatMoney(sale.taxTotal)} />
        ) : null}
        <Row left="TOTAL" right={formatMoney(sale.grandTotal)} bold />
      </div>

      {payment && (
        <>
          <hr className="receipt-rule" />
          <div>
            <Row left={`Paid by ${payment.method}`} right={formatMoney(payment.amount)} />
            {payment.amountTendered !== null && <Row left="Tendered" right={formatMoney(payment.amountTendered)} />}
            {payment.changeDue !== null && <Row left="Change" right={formatMoney(payment.changeDue)} bold />}
          </div>
        </>
      )}

      <hr className="receipt-rule" />
      <footer className="receipt-center">
        <p>{footer}</p>
      </footer>
    </article>
  )
}
