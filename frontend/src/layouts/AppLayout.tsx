import { useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { Button } from '../components/ui/Button'
import { useAuthStore } from '../store/authStore'
import { hasRole } from '../utils/format'
import type { Role } from '../types/api'

interface NavItem {
  to: string
  label: string
  end?: boolean
  minRole: Role
}

const NAV_ITEMS: NavItem[] = [
  { to: '/', label: 'Dashboard', end: true, minRole: 'CASHIER' },
  { to: '/pos', label: 'Point of sale', minRole: 'CASHIER' },
  { to: '/sales', label: 'Sales', minRole: 'CASHIER' },
  { to: '/products', label: 'Products', minRole: 'CASHIER' },
  { to: '/categories', label: 'Categories', minRole: 'CASHIER' },
]

export function AppLayout() {
  const [menuOpen, setMenuOpen] = useState(false)
  const user = useAuthStore((state) => state.user)
  const logout = useAuthStore((state) => state.logout)

  const items = NAV_ITEMS.filter((item) => hasRole(user?.role, item.minRole))

  return (
    <div className="min-h-screen bg-slate-50 md:flex">
      <aside
        className={`${menuOpen ? 'block' : 'hidden'} w-full shrink-0 bg-slate-900 text-slate-100 md:block md:w-48 lg:w-60`}
      >
        <div className="px-5 py-5 text-xl font-semibold tracking-tight">Stockora</div>
        <nav aria-label="Main" className="space-y-1 px-3 pb-4">
          {items.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              onClick={() => setMenuOpen(false)}
              className={({ isActive }) =>
                `block rounded-md px-3 py-2 text-sm font-medium ${
                  isActive ? 'bg-emerald-700 text-white' : 'text-slate-300 hover:bg-slate-800 hover:text-white'
                }`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center justify-between border-b border-slate-200 bg-white px-4 py-3 md:px-6">
          <button
            type="button"
            className="rounded-md p-2 text-slate-600 hover:bg-slate-100 md:hidden"
            aria-label="Toggle navigation"
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((open) => !open)}
          >
            ☰
          </button>
          <span className="hidden text-sm text-slate-500 md:block">Supermarket management</span>
          <div className="flex items-center gap-4">
            <div className="text-right text-sm leading-tight">
              <div className="font-medium text-slate-900">{user?.name}</div>
              <div className="text-xs text-slate-500">{user?.role}</div>
            </div>
            <Button variant="secondary" onClick={logout}>
              Log out
            </Button>
          </div>
        </header>

        <main className="min-w-0 flex-1 p-4 md:p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
