import { Star } from 'lucide-react';
import { cn } from '@/lib/cn';

export function RatingStars({ value, size = 'sm', className }: { value: number; size?: 'sm' | 'md' | 'lg'; className?: string }) {
  const dimension = size === 'lg' ? 'h-5 w-5' : size === 'md' ? 'h-4 w-4' : 'h-3.5 w-3.5';
  return (
    <span className={cn('inline-flex items-center gap-0.5', className)} role="img" aria-label={`${value.toFixed(1)} out of 5 stars`}>
      {[1, 2, 3, 4, 5].map((star) => {
        const fill = Math.max(0, Math.min(1, value - star + 1));
        return (
          <span key={star} className={cn('relative', dimension)}>
            <Star className={cn('absolute inset-0 text-stone-300', dimension)} aria-hidden />
            <span className="absolute inset-0 overflow-hidden" style={{ width: `${fill * 100}%` }}>
              <Star className={cn('fill-amber-400 text-amber-400', dimension)} aria-hidden />
            </span>
          </span>
        );
      })}
    </span>
  );
}

export function RatingInput({ value, onChange }: { value: number; onChange: (value: number) => void }) {
  return (
    <div className="flex gap-1" role="radiogroup" aria-label="Rating">
      {[1, 2, 3, 4, 5].map((star) => (
        <button key={star} type="button" role="radio" aria-checked={value === star} aria-label={`${star} star${star > 1 ? 's' : ''}`}
          onClick={() => onChange(star)} className="rounded p-0.5 focus-visible:ring-2 focus-visible:ring-brand-500">
          <Star className={cn('h-7 w-7 transition-colors', star <= value ? 'fill-amber-400 text-amber-400' : 'text-stone-300 hover:text-amber-300')} />
        </button>
      ))}
    </div>
  );
}
