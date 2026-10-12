import { Link } from 'react-router-dom'

export default function NotFoundPage() {
  return (
    <div className="mx-auto max-w-md py-16 text-center">
      <h1 className="text-xl font-semibold text-slate-900">Page not found</h1>
      <p className="mt-2 text-sm text-slate-600">The page you are looking for does not exist.</p>
      <Link to="/" className="mt-4 inline-block text-sm font-medium text-emerald-700 hover:underline">
        Back to the dashboard
      </Link>
    </div>
  )
}
