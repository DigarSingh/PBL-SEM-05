import { ArrowDownRight, ArrowUpRight, Minus } from 'lucide-react';
import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { cn } from '@/lib/cn';

/** KPI tile: label, value, and an optional signed change against a named period. */
export function StatTile({ label, value, change, changeLabel = 'vs previous 30 days', upIsGood = true, icon, to, className }: {
  label: string;
  value: ReactNode;
  change?: number | null;
  changeLabel?: string;
  upIsGood?: boolean;
  icon?: ReactNode;
  to?: string;
  className?: string;
}) {
  const body = (
    <>
      <div className="flex items-center justify-between gap-2">
        <p className="text-sm font-medium text-slate-500">{label}</p>
        {icon && <span className="text-slate-400">{icon}</span>}
      </div>
      <p className="mt-2 text-2xl font-bold tracking-tight text-slate-900">{value}</p>
      {change !== undefined && <Change value={change} label={changeLabel} upIsGood={upIsGood} />}
    </>
  );
  const classes = cn('block rounded-2xl border border-stone-200 bg-white p-4 shadow-xs', to && 'transition-shadow hover:shadow-md', className);
  return to ? <Link to={to} className={classes}>{body}</Link> : <div className={classes}>{body}</div>;
}

function Change({ value, label, upIsGood }: { value: number | null; label: string; upIsGood: boolean }) {
  if (value === null) {
    return <p className="mt-1 text-xs text-slate-400">No earlier data to compare</p>;
  }
  const flat = Math.abs(value) < 0.05;
  const good = flat ? null : (value > 0) === upIsGood;
  const Icon = flat ? Minus : value > 0 ? ArrowUpRight : ArrowDownRight;
  return (
    <p className={cn('mt-1 flex items-center gap-1 text-xs font-medium',
      good === null ? 'text-slate-500' : good ? 'text-emerald-700' : 'text-rose-700')}>
      <Icon className="h-3.5 w-3.5" aria-hidden />
      {value > 0 ? '+' : ''}{value.toFixed(1)}% <span className="font-normal text-slate-500">{label}</span>
    </p>
  );
}
