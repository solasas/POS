import { useId } from 'react'
import type { ComponentProps, ReactNode } from 'react'

export const controlClass =
  'block w-full h-11 rounded-[10px] border bg-card px-3.5 text-[15px] text-ink shadow-[inset_0_1px_2px_rgb(24_34_28/0.05)] ' +
  'placeholder:text-slate-400 transition hover:border-slate-400 focus:border-emerald-600 focus:outline-none ' +
  'focus:ring-4 focus:ring-emerald-600/15 disabled:bg-slate-100 disabled:text-slate-500'

interface FieldShellProps {
  label: string
  error?: string
  hint?: string
  htmlFor: string
  children: ReactNode
}

function FieldShell({ label, error, hint, htmlFor, children }: FieldShellProps) {
  return (
    <div>
      <label htmlFor={htmlFor} className="mb-1.5 block text-[13px] font-medium text-slate-700">
        {label}
      </label>
      {children}
      {hint && !error && <p className="mt-1.5 text-xs leading-snug text-slate-500">{hint}</p>}
      {error && (
        <p role="alert" className="mt-1.5 text-xs font-medium text-red-600">
          {error}
        </p>
      )}
    </div>
  )
}

interface CommonProps {
  label: string
  error?: string
  hint?: string
}

const border = (error?: string) => (error ? 'border-red-400' : 'border-slate-300')

export function TextField({ label, error, hint, className = '', ...props }: CommonProps & ComponentProps<'input'>) {
  const id = useId()
  return (
    <FieldShell label={label} error={error} hint={hint} htmlFor={id}>
      <input
        id={id}
        aria-invalid={error ? true : undefined}
        className={`${controlClass} ${border(error)} ${className}`}
        {...props}
      />
    </FieldShell>
  )
}

export function TextAreaField({ label, error, hint, ...props }: CommonProps & ComponentProps<'textarea'>) {
  const id = useId()
  return (
    <FieldShell label={label} error={error} hint={hint} htmlFor={id}>
      <textarea
        id={id}
        rows={3}
        aria-invalid={error ? true : undefined}
        className={`${controlClass} h-auto py-2.5 ${border(error)}`}
        {...props}
      />
    </FieldShell>
  )
}

export function SelectField({ label, error, hint, children, ...props }: CommonProps & ComponentProps<'select'>) {
  const id = useId()
  return (
    <FieldShell label={label} error={error} hint={hint} htmlFor={id}>
      <select
        id={id}
        aria-invalid={error ? true : undefined}
        className={`${controlClass} ${border(error)}`}
        {...props}
      >
        {children}
      </select>
    </FieldShell>
  )
}
