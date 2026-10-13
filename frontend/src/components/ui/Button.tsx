import type { ComponentProps } from 'react'

type Variant = 'primary' | 'secondary' | 'danger' | 'ghost' | 'accent'
type Size = 'md' | 'lg' | 'sm'

const base =
  'inline-flex select-none items-center justify-center gap-2 rounded-[10px] font-medium whitespace-nowrap transition ' +
  'duration-100 active:translate-y-px disabled:cursor-not-allowed disabled:opacity-45 disabled:active:translate-y-0'

const variants: Record<Variant, string> = {
  primary:
    'bg-emerald-700 text-white shadow-[inset_0_-2px_0_rgb(0_0_0/0.18)] hover:bg-emerald-800',
  accent:
    'bg-amber-400 text-ink shadow-[inset_0_-2px_0_rgb(0_0_0/0.16)] hover:bg-amber-300',
  secondary:
    'border border-slate-300 bg-card text-slate-800 shadow-[0_1px_0_rgb(24_34_28/0.05)] hover:border-slate-400 hover:bg-white',
  danger: 'bg-red-600 text-white shadow-[inset_0_-2px_0_rgb(0_0_0/0.18)] hover:bg-red-700',
  ghost: 'text-slate-600 hover:bg-slate-200/60 hover:text-ink',
}

const sizes: Record<Size, string> = {
  sm: 'h-8 px-2.5 text-[13px]',
  md: 'h-10 px-4 text-sm',
  lg: 'h-12 px-6 text-base',
}

interface ButtonProps extends ComponentProps<'button'> {
  variant?: Variant
  size?: Size
}

export function Button({
  variant = 'primary',
  size = 'md',
  className = '',
  type = 'button',
  ...props
}: ButtonProps) {
  return <button type={type} className={`${base} ${variants[variant]} ${sizes[size]} ${className}`} {...props} />
}
