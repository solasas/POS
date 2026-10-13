import type { ReactNode } from 'react'

interface PageHeaderProps {
  title: string
  eyebrow?: string
  description?: ReactNode
  actions?: ReactNode
}

export function PageHeader({ title, eyebrow, description, actions }: PageHeaderProps) {
  return (
    <header className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div className="min-w-0">
        {eyebrow && <p className="eyebrow mb-1.5">{eyebrow}</p>}
        <h1 className="font-display text-[1.75rem] leading-tight font-bold text-ink sm:text-[2rem]">{title}</h1>
        {description && <p className="mt-1 max-w-xl text-sm text-slate-500">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-3">{actions}</div>}
    </header>
  )
}
