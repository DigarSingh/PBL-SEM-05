import { cn } from '@/lib/cn';

export function Segmented<T extends string>({ value, options, onChange, className }: {
  value: T;
  options: { value: T; label: string; count?: number }[];
  onChange: (value: T) => void;
  className?: string;
}) {
  return (
    <div className={cn('flex gap-1 overflow-x-auto rounded-xl bg-stone-100 p-1 scrollbar-none', className)} role="tablist">
      {options.map((option) => (
        <button key={option.value} type="button" role="tab" aria-selected={value === option.value}
          onClick={() => onChange(option.value)}
          className={cn('whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-medium transition-colors',
            value === option.value ? 'bg-white text-slate-900 shadow-xs' : 'text-slate-600 hover:text-slate-900')}>
          {option.label}
          {option.count !== undefined && <span className="ml-1.5 text-xs text-slate-400">{option.count}</span>}
        </button>
      ))}
    </div>
  );
}
