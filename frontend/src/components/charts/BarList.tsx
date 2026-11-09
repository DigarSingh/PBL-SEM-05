import type { ReactNode } from 'react';
import { chartColors } from './theme';

/** Horizontal bars for comparing categories, each labelled with its value at the tip. */
export function BarList({ items, formatValue, emptyText = 'No data for this period.' }: {
  items: { key: string | number; label: ReactNode; value: number; detail?: ReactNode }[];
  formatValue: (value: number) => string;
  emptyText?: string;
}) {
  if (items.length === 0) {
    return <p className="py-6 text-center text-sm text-slate-500">{emptyText}</p>;
  }
  const max = Math.max(...items.map((item) => item.value), 0) || 1;
  return (
    <ul className="space-y-3">
      {items.map((item) => (
        <li key={item.key}>
          <div className="mb-1 flex items-baseline justify-between gap-3 text-sm">
            <span className="min-w-0 truncate text-slate-700">{item.label}</span>
            <span className="shrink-0 font-semibold tabular-nums text-slate-900">
              {formatValue(item.value)}{item.detail && <span className="ml-1.5 font-normal text-slate-500">{item.detail}</span>}
            </span>
          </div>
          <div className="h-2.5 overflow-hidden rounded-r-[4px] bg-stone-100" aria-hidden>
            <div className="h-full rounded-r-[4px]" style={{ width: `${Math.max(1, (item.value / max) * 100)}%`, background: chartColors.series }} />
          </div>
        </li>
      ))}
    </ul>
  );
}
