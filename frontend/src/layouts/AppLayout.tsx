import { useState } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'
import { Icon } from '../components/ui/Icon'
import type { IconName } from '../components/ui/Icon'
import { LogoMark, Wordmark } from '../components/ui/Logo'
import { useAuthStore } from '../store/authStore'
import { LOCALE, hasRole } from '../utils/format'
import type { Role } from '../types/api'

interface NavItem {
  to: string
  label: string
  icon: IconName
  end?: boolean
  minRole: Role
}

const NAV_GROUPS: { label?: string; items: NavItem[] }[] = [
  { items: [{ to: '/', label: 'Dashboard', icon: 'home', end: true, minRole: 'CASHIER' }] },
  {
    label: 'Sell',
    items: [
      { to: '/pos', label: 'Point of sale', icon: 'register', minRole: 'CASHIER' },
      { to: '/sales', label: 'Sales', icon: 'receipt', minRole: 'CASHIER' },
    ],
  },
  {
    label: 'Catalog',
    items: [
      { to: '/products', label: 'Products', icon: 'box', minRole: 'CASHIER' },
      { to: '/categories', label: 'Categories', icon: 'tag', minRole: 'CASHIER' },
    ],
  },
]

const ROLE_LABEL: Record<Role, string> = { ADMIN: 'Administrator', MANAGER: 'Manager', CASHIER: 'Cashier' }

const todayFormat = new Intl.DateTimeFormat(LOCALE, { weekday: 'long', day: 'numeric', month: 'long' })

function initials(name: string | undefined): string {
  return (name ?? '?')
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('')
}

export function AppLayout() {
  const [menuOpen, setMenuOpen] = useState(false)
  const user = useAuthStore((state) => state.user)
  const logout = useAuthStore((state) => state.logout)
  const location = useLocation()
  const onPos = location.pathname.startsWith('/pos')

  const groups = NAV_GROUPS.map((group) => ({
    ...group,
    items: group.items.filter((item) => hasRole(user?.role, item.minRole)),
  })).filter((group) => group.items.length > 0)

  return (
    <div className="min-h-screen lg:flex">
      {/* ---- sidebar ---- */}
      <aside
        className={`${menuOpen ? 'flex' : 'hidden'} no-print z-30 w-full shrink-0 flex-col bg-emerald-900 text-emerald-50 lg:sticky lg:top-0 lg:flex lg:h-screen lg:w-60`}
      >
        <div className="hidden px-5 pt-6 pb-7 lg:block">
          <Wordmark />
        </div>

        <nav aria-label="Main" className="flex-1 space-y-6 overflow-y-auto px-3 pt-4 lg:pt-0">
          {groups.map((group, index) => (
            <div key={group.label ?? index}>
              {group.label && (
                <p className="mb-1.5 px-3 text-[0.6875rem] font-semibold tracking-[0.1em] text-emerald-300/70 uppercase">
                  {group.label}
                </p>
              )}
              <ul className="space-y-0.5">
                {group.items.map((item) => (
                  <li key={item.to}>
                    <NavLink
                      to={item.to}
                      end={item.end}
                      onClick={() => setMenuOpen(false)}
                      className={({ isActive }) =>
                        `group relative flex items-center gap-3 rounded-[10px] px-3 py-2.5 text-[14.5px] font-medium transition ${
                          isActive
                            ? 'bg-white/10 text-white'
                            : 'text-emerald-100/80 hover:bg-white/5 hover:text-white'
                        }`
                      }
                    >
                      {({ isActive }) => (
                        <>
                          {isActive && (
                            <span className="absolute top-2 bottom-2 -left-3 w-1 rounded-r-full bg-amber-400" />
                          )}
                          <Icon name={item.icon} size={19} className={isActive ? 'text-amber-300' : 'text-emerald-300/80'} />
                          {item.label}
                        </>
                      )}
                    </NavLink>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </nav>

        <div className="m-3 flex items-center gap-3 rounded-xl bg-black/20 p-3">
          <span
            aria-hidden="true"
            className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-amber-400 font-display text-sm font-bold text-ink"
          >
            {initials(user?.name)}
          </span>
          <div className="min-w-0 flex-1 leading-tight">
            <div className="truncate text-sm font-semibold text-white">{user?.name}</div>
            <div className="text-xs text-emerald-200/80">{user ? ROLE_LABEL[user.role] : ''}</div>
          </div>
          <button
            type="button"
            onClick={logout}
            aria-label="Log out"
            title="Log out"
            className="rounded-lg p-2 text-emerald-200/80 transition hover:bg-white/10 hover:text-white"
          >
            <Icon name="logout" size={18} />
          </button>
        </div>
      </aside>

      {/* ---- content ---- */}
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="no-print sticky top-0 z-20 flex h-14 items-center gap-3 border-b border-slate-200 bg-paper/85 px-4 backdrop-blur md:px-8">
          <button
            type="button"
            className="-ml-2 rounded-lg p-2 text-slate-600 hover:bg-slate-200/60 lg:hidden"
            aria-label="Toggle navigation"
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((open) => !open)}
          >
            <Icon name={menuOpen ? 'close' : 'menu'} />
          </button>
          <span className="lg:hidden">
            <LogoMark size={26} />
          </span>
          <span className="hidden items-center gap-2 text-sm text-slate-500 lg:flex">
            <Icon name="calendar" size={16} />
            {todayFormat.format(new Date())}
          </span>
          <div className="ml-auto">
            {!onPos && (
              <Link
                to="/pos"
                className="inline-flex h-9 items-center gap-2 rounded-[10px] bg-emerald-700 px-3.5 text-sm font-medium text-white shadow-[inset_0_-2px_0_rgb(0_0_0/0.18)] hover:bg-emerald-800"
              >
                <Icon name="register" size={17} />
                New sale
              </Link>
            )}
          </div>
        </header>

        <main className="mx-auto w-full max-w-[1280px] min-w-0 flex-1 px-4 py-6 md:px-8 md:py-8">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
