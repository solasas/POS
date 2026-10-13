import { useCallback, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { ToastContext } from '../hooks/useToast'
import { Icon } from './ui/Icon'
import type { ToastApi } from '../hooks/useToast'

type ToastTone = 'success' | 'error'
interface ToastItem {
  id: number
  message: string
  tone: ToastTone
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([])

  const push = useCallback((message: string, tone: ToastTone) => {
    const id = Date.now() + Math.random()
    setToasts((current) => [...current, { id, message, tone }])
    setTimeout(() => setToasts((current) => current.filter((t) => t.id !== id)), 4000)
  }, [])

  const api = useMemo<ToastApi>(
    () => ({ success: (m) => push(m, 'success'), error: (m) => push(m, 'error') }),
    [push],
  )

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div aria-live="polite" className="fixed right-4 bottom-4 z-50 flex w-80 flex-col gap-2">
        {toasts.map((toast) => (
          <div
            key={toast.id}
            role={toast.tone === 'error' ? 'alert' : 'status'}
            className={`animate-rise flex items-start gap-3 rounded-xl px-4 py-3 text-sm font-medium text-white shadow-pop ${
              toast.tone === 'success' ? 'bg-emerald-900' : 'bg-red-700'
            }`}
          >
            <span
              className={`mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full ${
                toast.tone === 'success' ? 'bg-amber-400 text-ink' : 'bg-white/20 text-white'
              }`}
            >
              <Icon name={toast.tone === 'success' ? 'check' : 'alert'} size={13} strokeWidth={2.5} />
            </span>
            <span>{toast.message}</span>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}
