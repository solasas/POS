import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { SalesTable } from '../components/sales/SalesTable'
import { Alert, EmptyState, TableSkeleton } from '../components/ui/feedback'
import { PageHeader } from '../components/ui/PageHeader'
import { Pagination } from '../components/ui/Pagination'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import { errorMessage } from '../services/errors'
import { salesService } from '../services/salesService'
import { useAuthStore } from '../store/authStore'
import type { PaymentMethod, SaleStatus } from '../types/api'
import { hasRole } from '../utils/format'

const PAGE_SIZE = 10
const controlClass =
  'h-10 rounded-[10px] border border-slate-300 bg-card px-3 text-sm text-ink transition hover:border-slate-400 focus:border-emerald-600 focus:ring-4 focus:ring-emerald-600/15 focus:outline-none'

/** Local calendar day boundaries as ISO instants, so "from/to" mean the cashier's own days. */
function startOfDay(date: string): string {
  return new Date(`${date}T00:00:00`).toISOString()
}
function startOfNextDay(date: string): string {
  const d = new Date(`${date}T00:00:00`)
  d.setDate(d.getDate() + 1)
  return d.toISOString()
}

export default function SalesHistoryPage() {
  const role = useAuthStore((state) => state.user?.role)
  const seesEveryone = hasRole(role, 'MANAGER')

  const [saleNumber, setSaleNumber] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [method, setMethod] = useState<'' | PaymentMethod>('')
  const [status, setStatus] = useState<'' | SaleStatus>('')
  const [page, setPage] = useState(0)

  const debouncedNumber = useDebouncedValue(saleNumber.trim(), 300)

  // any filter change returns to the first page
  const onFilter =
    <T,>(set: (value: T) => void) =>
    (value: T) => {
      set(value)
      setPage(0)
    }

  const query = {
    saleNumber: debouncedNumber || undefined,
    from: from ? startOfDay(from) : undefined,
    to: to ? startOfNextDay(to) : undefined,
    paymentMethod: method || undefined,
    status: status || undefined,
    page,
    size: PAGE_SIZE,
  }

  const sales = useQuery({
    queryKey: ['sales', seesEveryone, query],
    queryFn: () => salesService.list(query, !seesEveryone),
    placeholderData: keepPreviousData,
  })

  const filtered = Boolean(debouncedNumber || from || to || method || status)

  return (
    <div>
      <PageHeader
        eyebrow="Sell"
        title={seesEveryone ? 'Sales history' : 'My sales'}
        description={seesEveryone ? 'Every completed bill. Open one to see or reprint it.' : 'Bills you have rung up.'}
      />

      <div className="card mb-5 flex flex-wrap items-end gap-3 p-3.5">
        <label className="text-sm text-slate-700">
          <span className="eyebrow mb-1.5 block">Receipt number</span>
          <input
            type="search"
            aria-label="Search by receipt number"
            placeholder="S20261013-000001"
            value={saleNumber}
            onChange={(event) => onFilter(setSaleNumber)(event.target.value)}
            className={`${controlClass} w-52`}
          />
        </label>
        <label className="text-sm text-slate-700">
          <span className="eyebrow mb-1.5 block">From</span>
          <input type="date" aria-label="From date" value={from} max={to || undefined}
            onChange={(event) => onFilter(setFrom)(event.target.value)} className={controlClass} />
        </label>
        <label className="text-sm text-slate-700">
          <span className="eyebrow mb-1.5 block">To</span>
          <input type="date" aria-label="To date" value={to} min={from || undefined}
            onChange={(event) => onFilter(setTo)(event.target.value)} className={controlClass} />
        </label>
        <label className="text-sm text-slate-700">
          <span className="eyebrow mb-1.5 block">Payment</span>
          <select aria-label="Payment method" value={method}
            onChange={(event) => onFilter(setMethod)(event.target.value as '' | PaymentMethod)} className={controlClass}>
            <option value="">All methods</option>
            <option value="CASH">Cash</option>
            <option value="UPI">UPI</option>
            <option value="CARD">Card</option>
          </select>
        </label>
        {seesEveryone && (
          <label className="text-sm text-slate-700">
            <span className="eyebrow mb-1.5 block">Status</span>
            <select aria-label="Sale status" value={status}
              onChange={(event) => onFilter(setStatus)(event.target.value as '' | SaleStatus)} className={controlClass}>
              <option value="">All</option>
              <option value="COMPLETED">Completed</option>
              <option value="VOIDED">Voided</option>
            </select>
          </label>
        )}
      </div>

      {sales.isError && <Alert>Could not load sales: {errorMessage(sales.error)}</Alert>}
      {sales.isPending && <TableSkeleton rows={6} label="Loading sales…" />}

      {sales.data && sales.data.content.length === 0 && (
        <EmptyState title={filtered ? 'No sales match your filters' : 'No sales yet'} illustration="receipt">
          {!filtered && 'Completed checkouts will appear here.'}
        </EmptyState>
      )}

      {sales.data && sales.data.content.length > 0 && (
        <>
          <SalesTable sales={sales.data.content} />
          <Pagination
            page={sales.data.page}
            totalPages={sales.data.totalPages}
            totalElements={sales.data.totalElements}
            onPageChange={setPage}
          />
        </>
      )}
    </div>
  )
}
