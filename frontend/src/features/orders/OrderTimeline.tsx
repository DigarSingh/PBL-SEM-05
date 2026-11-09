import { Check } from 'lucide-react';
import { cn } from '@/lib/cn';
import { formatDateTime } from '@/lib/format';
import type { TimelineStep } from '@/lib/types';

/** Progress from placement to delivery; the current step pulses while the order is on its way. */
export function OrderTimeline({ steps, cancelled }: { steps: TimelineStep[]; cancelled: boolean }) {
  const current = steps.reduce((last, step, index) => (step.reached ? index : last), -1);
  return (
    <ol className="relative space-y-0">
      {steps.map((step, index) => {
        const active = index === current && !cancelled && index < steps.length - 1;
        return (
          <li key={step.status} className="relative flex gap-4 pb-6 last:pb-0">
            {index < steps.length - 1 && (
              <span aria-hidden className={cn('absolute left-[13px] top-7 h-[calc(100%-1.25rem)] w-0.5',
                steps[index + 1].reached ? 'bg-emerald-500' : 'bg-stone-200')} />
            )}
            <span className={cn('relative z-10 flex h-7 w-7 shrink-0 items-center justify-center rounded-full border-2',
              step.reached ? 'border-emerald-500 bg-emerald-500 text-white' : 'border-stone-300 bg-white text-transparent',
              active && 'animate-pulse-ring')}>
              <Check className="h-4 w-4" aria-hidden />
            </span>
            <div className="pt-0.5">
              <p className={cn('text-sm font-semibold', step.reached ? 'text-slate-900' : 'text-slate-400')}>
                {step.label}
                {active && <span className="sr-only"> (current step)</span>}
              </p>
              {step.at && <p className="text-xs text-slate-500">{formatDateTime(step.at)}</p>}
            </div>
          </li>
        );
      })}
    </ol>
  );
}
