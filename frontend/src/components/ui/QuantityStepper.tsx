import { Minus, Plus } from 'lucide-react';
import { cn } from '@/lib/cn';

export function QuantityStepper({ value, min = 1, max, onChange, disabled, size = 'md' }: {
  value: number;
  min?: number;
  max: number;
  onChange: (value: number) => void;
  disabled?: boolean;
  size?: 'sm' | 'md';
}) {
  const button = cn('flex items-center justify-center text-slate-700 transition-colors hover:bg-stone-100 disabled:opacity-40 disabled:hover:bg-transparent',
    size === 'sm' ? 'h-8 w-8' : 'h-10 w-10');
  return (
    <div className="inline-flex items-center rounded-lg border border-stone-300 bg-white" role="group" aria-label="Quantity">
      <button type="button" className={button} onClick={() => onChange(value - 1)} disabled={disabled || value <= min} aria-label="Decrease quantity">
        <Minus className="h-4 w-4" />
      </button>
      <span className={cn('min-w-8 text-center text-sm font-semibold tabular-nums', size === 'sm' ? 'px-1' : 'px-2')} aria-live="polite">{value}</span>
      <button type="button" className={button} onClick={() => onChange(value + 1)} disabled={disabled || value >= max} aria-label="Increase quantity">
        <Plus className="h-4 w-4" />
      </button>
    </div>
  );
}
