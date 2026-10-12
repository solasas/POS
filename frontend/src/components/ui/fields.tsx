import { useId } from 'react'
import type { ComponentProps, ReactNode } from 'react'

const controlClass =
  'block w-full rounded-md border bg-white px-3 py-2 text-sm text-slate-900 shadow-sm placeholder:text-slate-400 focus:outline-2 focus:outline-offset-0 focus:outline-emerald-600 disabled:bg-slate-50'

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
      <label htmlFor={htmlFor} className="mb-1 block text-sm font-medium text-slate-700">
        {label}
      </label>
      {children}
      {hint && !error && <p className="mt-1 text-xs text-slate-500">{hint}</p>}
      {error && (
        <p role="alert" className="mt-1 text-xs font-medium text-red-600">
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

export function TextField({ label, error, hint, className = '', ...props }: CommonProps & ComponentProps<'input'>) {
  const id = useId()
  return (
    <FieldShell label={label} error={error} hint={hint} htmlFor={id}>
      <input
        id={id}
        aria-invalid={error ? true : undefined}
        className={`${controlClass} ${error ? 'border-red-400' : 'border-slate-300'} ${className}`}
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
        className={`${controlClass} ${error ? 'border-red-400' : 'border-slate-300'}`}
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
        className={`${controlClass} ${error ? 'border-red-400' : 'border-slate-300'}`}
        {...props}
      >
        {children}
      </select>
    </FieldShell>
  )
}
