import { NavLink, Outlet } from 'react-router-dom'
import { useAuthStore } from '../store/authStore'

const navItems = [
  { to: '/', label: 'Register' },
  { to: '/catalog', label: 'Catalog' },
  { to: '/inventory', label: 'Inventory' },
  { to: '/reports', label: 'Reports' },
]

export default function Layout() {
  const logout = useAuthStore((state) => state.logout)

  return (
    <div className="min-h-screen bg-slate-100">
      <header className="flex items-center justify-between bg-slate-900 px-6 py-3 text-white">
        <span className="text-lg font-semibold">Supermarket POS</span>
        <nav className="flex gap-4">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `text-sm font-medium ${isActive ? 'text-white' : 'text-slate-300 hover:text-white'}`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <button
          onClick={logout}
          className="rounded bg-slate-700 px-3 py-1 text-sm hover:bg-slate-600"
        >
          Log out
        </button>
      </header>
      <main className="p-6">
        <Outlet />
      </main>
    </div>
  )
}
