import { useEffect, useRef } from 'react'
import type { ReactNode } from 'react'
import { Icon } from './Icon'

interface ModalProps {
  title: string
  onClose: () => void
  children: ReactNode
  /** Disable closing (Escape, backdrop) while a request is in flight. */
  locked?: boolean
  size?: 'md' | 'lg'
}

/** Accessible modal: labelled dialog, Escape to close, focus moves in and returns on close. */
export function Modal({ title, onClose, children, locked = false, size = 'md' }: ModalProps) {
  const panelRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    const panel = panelRef.current
    const focusable = panel?.querySelector<HTMLElement>(
      'input:not([disabled]), button:not([disabled]):not([data-modal-close]), select:not([disabled]), textarea:not([disabled])',
    )
    ;(focusable ?? panel)?.focus()
    return () => previous?.focus?.()
  }, [])

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !locked) onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [locked, onClose])

  return (
    <div
      className="no-print fixed inset-0 z-40 flex items-end justify-center bg-slate-950/55 p-0 backdrop-blur-[2px] sm:items-center sm:p-4 animate-fade"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !locked) onClose()
      }}
    >
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        tabIndex={-1}
        className={`animate-rise max-h-[92vh] w-full overflow-y-auto rounded-t-3xl bg-card p-6 shadow-pop outline-none sm:rounded-2xl sm:p-7 ${
          size === 'lg' ? 'sm:max-w-2xl' : 'sm:max-w-lg'
        }`}
      >
        <div className="mb-5 flex items-start justify-between gap-4">
          <h2 className="font-display text-xl font-bold text-ink">{title}</h2>
          <button
            type="button"
            data-modal-close
            aria-label="Close"
            disabled={locked}
            onClick={onClose}
            className="-mt-1 -mr-2 rounded-lg p-2 text-slate-500 hover:bg-slate-200/60 hover:text-ink disabled:opacity-40"
          >
            <Icon name="close" size={18} />
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}
