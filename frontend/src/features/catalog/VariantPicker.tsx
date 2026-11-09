import { cn } from '@/lib/cn';
import type { Variant, VariantOption } from '@/lib/types';
import { valueState, type Selection } from './variants';

export function VariantPicker({ options, variants, selection, onSelect }: {
  options: VariantOption[];
  variants: Variant[];
  selection: Selection;
  onSelect: (optionName: string, value: string) => void;
}) {
  return (
    <div className="space-y-4">
      {options.map((option) => (
        <fieldset key={option.name}>
          <legend className="mb-2 text-sm text-slate-600">
            {option.name}: <span className="font-semibold text-slate-900">{selection[option.name] ?? 'Select'}</span>
          </legend>
          <div className="flex flex-wrap gap-2">
            {option.values.map((value) => {
              const state = valueState(variants, options, selection, option.name, value);
              const selected = selection[option.name] === value;
              return (
                <button key={value} type="button" aria-pressed={selected} onClick={() => onSelect(option.name, value)}
                  title={state === 'out-of-stock' ? 'Out of stock' : state === 'unavailable' ? 'Not available with the current selection' : undefined}
                  className={cn('min-w-12 rounded-lg border px-3 py-2 text-sm font-medium transition-colors',
                    selected ? 'border-ink bg-ink text-white' : 'border-stone-300 bg-white text-slate-800 hover:border-stone-500',
                    state !== 'available' && !selected && 'border-dashed text-slate-400 line-through decoration-slate-300')}>
                  {value}
                </button>
              );
            })}
          </div>
        </fieldset>
      ))}
    </div>
  );
}
