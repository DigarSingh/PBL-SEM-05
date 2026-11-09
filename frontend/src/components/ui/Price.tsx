import { cn } from '@/lib/cn';
import { formatPrice } from '@/lib/format';

export function Price({ salePrice, price, discountPercent, size = 'md', className }: {
  salePrice: number;
  price: number;
  discountPercent?: number;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
}) {
  const discounted = price > salePrice;
  return (
    <div className={cn('flex flex-wrap items-baseline gap-x-2 gap-y-0.5', className)}>
      <span className={cn('font-bold text-slate-900', size === 'lg' ? 'text-3xl' : size === 'md' ? 'text-lg' : 'text-base')}>
        {formatPrice(salePrice)}
      </span>
      {discounted && (
        <>
          <span className={cn('text-slate-400 line-through', size === 'lg' ? 'text-base' : 'text-sm')}>
            <span className="sr-only">Original price </span>{formatPrice(price)}
          </span>
          {discountPercent !== undefined && discountPercent > 0 && (
            <span className={cn('font-semibold text-emerald-700', size === 'lg' ? 'text-base' : 'text-sm')}>
              {Math.round(discountPercent)}% off
            </span>
          )}
        </>
      )}
    </div>
  );
}
