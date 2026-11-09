import { useId, type ReactNode } from 'react';
import { Button } from '@/components/ui/Button';
import { InlineAlert } from '@/components/ui/Feedback';
import type { ProductSummary } from '@/lib/types';
import { ProductCard, ProductCardSkeleton } from './ProductCard';

/** Horizontally scrolling row of products; renders nothing when there is nothing to show. */
export function ProductRail({ title, description, items, loading = false, error, onRetry, action }: {
  title: string;
  description?: ReactNode;
  items: { product: ProductSummary; reason?: string }[] | undefined;
  loading?: boolean;
  error?: unknown;
  onRetry?: () => void;
  action?: ReactNode;
}) {
  const headingId = useId();
  if (!loading && !error && (!items || items.length === 0)) {
    return null;
  }
  return (
    <section className="py-6" aria-labelledby={headingId} aria-busy={loading || undefined}>
      <div className="mb-4 flex items-end justify-between gap-4">
        <div>
          <h2 id={headingId} className="text-xl font-bold tracking-tight text-slate-900">{title}</h2>
          {description && <p className="mt-1 text-sm text-slate-500">{description}</p>}
        </div>
        {action}
      </div>
      {error ? (
        <InlineAlert tone="danger" className="flex items-center justify-between gap-4">
          <span>We couldn&apos;t load these products.</span>
          {onRetry && <Button size="sm" variant="secondary" onClick={onRetry}>Try again</Button>}
        </InlineAlert>
      ) : (
        <div className="-mx-4 flex snap-x gap-3 overflow-x-auto px-4 pb-2 scrollbar-none sm:mx-0 sm:gap-4 sm:px-0">
          {loading
            ? Array.from({ length: 5 }, (_, index) => (
              <div key={index} className="w-44 shrink-0 sm:w-56"><ProductCardSkeleton /></div>
            ))
            : items?.map(({ product, reason }) => (
              <div key={product.id} className="w-44 shrink-0 snap-start sm:w-56">
                <ProductCard product={product} reason={reason} />
              </div>
            ))}
        </div>
      )}
    </section>
  );
}
