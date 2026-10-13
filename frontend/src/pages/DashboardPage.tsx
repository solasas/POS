import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { RecentSales } from '../components/dashboard/RecentSales'
import { RestockList } from '../components/dashboard/RestockList'
import { TodayCard } from '../components/dashboard/TodayCard'
import { Alert } from '../components/ui/feedback'
import { Icon } from '../components/ui/Icon'
import { categoryService } from '../services/categoryService'
import { errorMessage } from '../services/errors'
import { productService } from '../services/productService'
import { salesService } from '../services/salesService'
import { useAuthStore } from '../store/authStore'
import { hasRole } from '../utils/format'

function greeting(hour: number): string {
  if (hour < 12) return 'Good morning'
  if (hour < 17) return 'Good afternoon'
  return 'Good evening'
}

function localDayRange(): { from: string; to: string } {
  const start = new Date()
  start.setHours(0, 0, 0, 0)
  const end = new Date(start)
  end.setDate(end.getDate() + 1)
  return { from: start.toISOString(), to: end.toISOString() }
}

function Stat({ label, value, to, tone = 'default' }: { label: string; value: number | undefined; to: string; tone?: 'default' | 'warn' }) {
  return (
    <Link
      to={to}
      className="group flex items-center justify-between rounded-xl px-3 py-3 transition hover:bg-slate-100/70"
    >
      <span className="text-sm text-slate-600">{label}</span>
      <span className="flex items-center gap-2">
        <span
          className={`num font-display text-2xl font-bold ${tone === 'warn' && (value ?? 0) > 0 ? 'text-amber-600' : 'text-ink'}`}
        >
          {value ?? '—'}
        </span>
        <Icon name="arrowRight" size={16} className="text-slate-300 transition group-hover:translate-x-0.5 group-hover:text-emerald-700" />
      </span>
    </Link>
  )
}

export default function DashboardPage() {
  const user = useAuthStore((state) => state.user)
  const seesEveryone = hasRole(user?.role, 'MANAGER')
  const firstName = user?.name?.split(' ')[0] ?? ''

  const today = useQuery({
    queryKey: ['dashboard', 'today', new Date().toDateString()],
    queryFn: () => salesService.summary(localDayRange()),
    refetchInterval: 60_000,
  })
  const recent = useQuery({
    queryKey: ['dashboard', 'recent-sales', seesEveryone],
    queryFn: () => salesService.list({ size: 5 }, !seesEveryone),
    refetchInterval: 60_000,
  })
  const totalProducts = useQuery({
    queryKey: ['dashboard', 'products-total'],
    queryFn: () => productService.list({ size: 1 }),
  })
  const activeCategories = useQuery({
    queryKey: ['dashboard', 'categories-active'],
    queryFn: () => categoryService.list({ active: true, size: 1 }),
  })
  const lowStock = useQuery({
    queryKey: ['dashboard', 'low-stock'],
    queryFn: () => productService.list({ active: true, lowStock: true, sortBy: 'stock', size: 5 }),
  })

  const error = today.error ?? recent.error ?? totalProducts.error ?? activeCategories.error ?? lowStock.error

  return (
    <div className="space-y-6">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="eyebrow mb-1.5">Dashboard</p>
          <h1 className="font-display text-[1.9rem] leading-tight font-bold text-ink sm:text-[2.25rem]">
            {greeting(new Date().getHours())}
            {firstName ? `, ${firstName}` : ''}
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            {seesEveryone ? "Here's how the store is doing today." : "Here's how your till is doing today."}
          </p>
        </div>
      </header>

      {error && <Alert>Could not load some dashboard data: {errorMessage(error)}</Alert>}

      <div className="grid gap-6 lg:grid-cols-12">
        <div className="space-y-6 lg:col-span-7">
          <TodayCard summary={today.data} loading={today.isPending} />
          <RecentSales sales={recent.data?.content ?? []} loading={recent.isPending} />
        </div>

        <div className="space-y-6 lg:col-span-5">
          <section aria-label="Store overview" className="card p-3">
            <p className="eyebrow px-3 pt-3 pb-1">Store overview</p>
            <Stat label="Total products" value={totalProducts.data?.totalElements} to="/products" />
            <Stat label="Active categories" value={activeCategories.data?.totalElements} to="/categories" />
            <Stat
              label="Low-stock products"
              value={lowStock.data?.totalElements}
              to="/products?low=1&status=active"
              tone="warn"
            />
          </section>

          <RestockList
            products={lowStock.data?.content ?? []}
            totalLow={lowStock.data?.totalElements ?? 0}
            loading={lowStock.isPending}
          />
        </div>
      </div>
    </div>
  )
}
