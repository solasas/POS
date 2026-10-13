import type { ReactNode } from 'react'
import { Icon } from './Icon'

export function Spinner({ label = 'Loading…' }: { label?: string }) {
  return (
    <div role="status" className="flex items-center gap-3 py-8 text-sm text-slate-500">
      <span className="h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-emerald-700" />
      {label}
    </div>
  )
}

/** Placeholder rows shown while a table loads; announces itself once for screen readers. */
export function TableSkeleton({ rows = 6, label = 'Loading…' }: { rows?: number; label?: string }) {
  return (
    <div role="status" aria-label={label} className="card divide-y divide-slate-100 overflow-hidden">
      <span className="sr-only">{label}</span>
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="flex items-center gap-4 px-5 py-4">
          <div className="skeleton h-4 w-1/3" />
          <div className="skeleton ml-auto h-4 w-16" />
          <div className="skeleton h-4 w-20" />
        </div>
      ))}
    </div>
  )
}

export function Alert({ children, tone = 'error' }: { children: ReactNode; tone?: 'error' | 'info' }) {
  const styles =
    tone === 'error'
      ? 'border-red-200 bg-red-50 text-red-800'
      : 'border-emerald-200 bg-emerald-50 text-emerald-900'
  return (
    <div
      role={tone === 'error' ? 'alert' : 'status'}
      className={`flex gap-3 rounded-xl border px-4 py-3 text-sm leading-snug ${styles}`}
    >
      <Icon name={tone === 'error' ? 'alert' : 'info'} size={18} className="mt-0.5 shrink-0" />
      <div className="min-w-0">{children}</div>
    </div>
  )
}

type Illustration = 'basket' | 'receipt' | 'search' | 'scan'

function EmptyIllustration({ kind }: { kind: Illustration }) {
  const common = { width: 72, height: 72, viewBox: '0 0 72 72', fill: 'none', 'aria-hidden': true } as const
  if (kind === 'receipt') {
    return (
      <svg {...common}>
        <path d="M18 8h36v56l-9-5-9 5-9-5-9 5z" fill="var(--color-slate-100)" stroke="var(--color-slate-300)" strokeWidth="2" strokeLinejoin="round" />
        <path d="M27 22h18M27 31h18M27 40h9" stroke="var(--color-slate-400)" strokeWidth="2.5" strokeLinecap="round" />
        <circle cx="52" cy="52" r="10" fill="var(--color-amber-300)" />
        <path d="M47.5 52h9M52 47.5v9" stroke="var(--color-ink)" strokeWidth="2.5" strokeLinecap="round" />
      </svg>
    )
  }
  if (kind === 'search') {
    return (
      <svg {...common}>
        <circle cx="31" cy="31" r="17" fill="var(--color-slate-100)" stroke="var(--color-slate-300)" strokeWidth="2" />
        <path d="m44 44 16 16" stroke="var(--color-emerald-700)" strokeWidth="5" strokeLinecap="round" />
        <path d="M24 31h14" stroke="var(--color-slate-400)" strokeWidth="2.5" strokeLinecap="round" />
      </svg>
    )
  }
  if (kind === 'scan') {
    return (
      <svg {...common}>
        <path d="M10 22v-8a4 4 0 0 1 4-4h8M50 10h8a4 4 0 0 1 4 4v8M62 50v8a4 4 0 0 1-4 4h-8M22 62h-8a4 4 0 0 1-4-4v-8" stroke="var(--color-emerald-700)" strokeWidth="3.5" strokeLinecap="round" />
        <path d="M20 26v20M26 26v20M32 26v20M38 26v20M44 26v20M50 26v20" stroke="var(--color-slate-400)" strokeWidth="2.5" strokeLinecap="round" />
        <rect x="14" y="35" width="44" height="3" rx="1.5" fill="var(--color-red-500)" className="animate-scan [--scan-distance:0px]" />
      </svg>
    )
  }
  return (
    <svg {...common}>
      <path d="M12 28h48l-5 30a4 4 0 0 1-4 3.4H21a4 4 0 0 1-4-3.4z" fill="var(--color-slate-100)" stroke="var(--color-slate-300)" strokeWidth="2" strokeLinejoin="round" />
      <path d="M28 28l6-14M44 28l-6-14" stroke="var(--color-slate-400)" strokeWidth="2.5" strokeLinecap="round" />
      <path d="M26 38v14M36 38v14M46 38v14" stroke="var(--color-amber-400)" strokeWidth="3" strokeLinecap="round" />
    </svg>
  )
}

export function EmptyState({
  title,
  children,
  illustration = 'basket',
}: {
  title: string
  children?: ReactNode
  illustration?: Illustration
}) {
  return (
    <div className="flex flex-col items-center rounded-2xl border border-dashed border-slate-300 bg-card/60 px-6 py-12 text-center">
      <EmptyIllustration kind={illustration} />
      <p className="mt-4 font-display text-lg font-semibold text-ink">{title}</p>
      {children && <div className="mt-1.5 max-w-sm text-sm text-slate-500">{children}</div>}
    </div>
  )
}

const BADGE: Record<'green' | 'gray' | 'amber' | 'red', { chip: string; dot: string }> = {
  green: { chip: 'bg-emerald-50 text-emerald-800', dot: 'bg-emerald-500' },
  gray: { chip: 'bg-slate-200/70 text-slate-600', dot: 'bg-slate-400' },
  amber: { chip: 'bg-amber-100 text-amber-800', dot: 'bg-amber-500' },
  red: { chip: 'bg-red-50 text-red-700', dot: 'bg-red-500' },
}

export function Badge({
  children,
  tone,
}: {
  children: ReactNode
  tone: 'green' | 'gray' | 'amber' | 'red'
}) {
  const styles = BADGE[tone]
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium ${styles.chip}`}>
      <span className={`h-1.5 w-1.5 rounded-full ${styles.dot}`} />
      {children}
    </span>
  )
}
