import { cn } from '@/lib/cn';

export type ButtonVariant = 'primary' | 'brand' | 'secondary' | 'ghost' | 'danger' | 'link';
export type ButtonSize = 'sm' | 'md' | 'lg' | 'icon';

const variants: Record<ButtonVariant, string> = {
  primary: 'bg-ink text-white hover:bg-slate-800 shadow-sm',
  brand: 'bg-brand-500 text-white hover:bg-brand-600 shadow-sm',
  secondary: 'bg-white text-slate-900 border border-stone-300 hover:bg-stone-50 hover:border-stone-400 shadow-xs',
  ghost: 'text-slate-700 hover:bg-stone-100',
  danger: 'bg-rose-600 text-white hover:bg-rose-700 shadow-sm',
  link: 'text-brand-700 hover:text-brand-800 underline-offset-4 hover:underline px-0',
};

const sizes: Record<ButtonSize, string> = {
  sm: 'h-8 px-3 text-sm gap-1.5 rounded-lg',
  md: 'h-10 px-4 text-sm gap-2 rounded-lg',
  lg: 'h-12 px-6 text-base gap-2 rounded-xl',
  icon: 'h-10 w-10 rounded-lg',
};

export function buttonClasses(variant: ButtonVariant = 'primary', size: ButtonSize = 'md', className?: string): string {
  return cn(
    'inline-flex items-center justify-center font-semibold transition-colors select-none',
    'disabled:cursor-not-allowed disabled:opacity-50',
    variants[variant],
    variant === 'link' ? 'h-auto' : sizes[size],
    className,
  );
}
