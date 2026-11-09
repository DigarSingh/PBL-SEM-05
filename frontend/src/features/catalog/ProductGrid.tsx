import { cn } from '@/lib/cn';
import type { ProductSummary } from '@/lib/types';
import { ProductCard, ProductCardSkeleton } from './ProductCard';

const GRID = 'grid grid-cols-2 gap-3 sm:gap-4 md:grid-cols-3 xl:grid-cols-4';

export function ProductGrid({ items, loading = false, skeletonCount = 8, dimmed = false, className }: {
  items: { product: ProductSummary; reason?: string }[];
  loading?: boolean;
  skeletonCount?: number;
  dimmed?: boolean;
  className?: string;
}) {
  if (loading) {
    return (
      <div className={cn(GRID, className)} aria-busy="true">
        {Array.from({ length: skeletonCount }, (_, index) => <ProductCardSkeleton key={index} />)}
      </div>
    );
  }
  return (
    <div className={cn(GRID, 'transition-opacity', dimmed && 'pointer-events-none opacity-60', className)}>
      {items.map(({ product, reason }) => <ProductCard key={product.id} product={product} reason={reason} />)}
    </div>
  );
}
