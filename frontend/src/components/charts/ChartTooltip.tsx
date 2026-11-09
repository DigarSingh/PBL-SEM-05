import type { TooltipContentProps } from 'recharts';

/** Hover card for Recharts: the period on top, then each series with its colour key. */
export function ChartTooltip({ active, payload, label, formatLabel, formatValue }: TooltipContentProps & {
  formatLabel: (label: string) => string;
  formatValue: (value: number) => string;
}) {
  if (!active || !payload?.length) {
    return null;
  }
  return (
    <div className="rounded-lg border border-stone-200 bg-white px-3 py-2 text-xs shadow-lg">
      <p className="font-medium text-slate-500">{formatLabel(String(label))}</p>
      {payload.map((entry) => (
        <p key={String(entry.dataKey)} className="mt-1 flex items-center gap-2 text-slate-900">
          <span className="h-2 w-2 rounded-full" style={{ background: entry.color }} aria-hidden />
          {entry.name}: <strong>{formatValue(Number(entry.value))}</strong>
        </p>
      ))}
    </div>
  );
}
