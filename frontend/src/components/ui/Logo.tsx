/** Stockora mark: a shopping basket whose slats are barcode bars. */
export function LogoMark({ size = 32 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden="true" focusable="false">
      <rect width="32" height="32" rx="9" fill="var(--color-amber-400)" />
      <path
        d="M6.5 12.5h19l-2.2 11a2 2 0 0 1-2 1.6H10.7a2 2 0 0 1-2-1.6z"
        fill="var(--color-emerald-900)"
      />
      <path d="M12 15.5v6.5M15 15.5v6.5M17.5 15.5v6.5M20 15.5v6.5" stroke="var(--color-amber-300)" strokeWidth="1.6" strokeLinecap="round" />
      <path d="M11.5 12.5 14.5 7M20.5 12.5 17.5 7" stroke="var(--color-emerald-900)" strokeWidth="2" strokeLinecap="round" />
    </svg>
  )
}

export function Wordmark({ tone = 'light' }: { tone?: 'light' | 'dark' }) {
  return (
    <span className="flex items-center gap-2.5">
      <LogoMark />
      <span
        className={`font-display text-[1.35rem] leading-none font-bold tracking-tight ${
          tone === 'light' ? 'text-emerald-50' : 'text-ink'
        }`}
      >
        Stockora
      </span>
    </span>
  )
}
