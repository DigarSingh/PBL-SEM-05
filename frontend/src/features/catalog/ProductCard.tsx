import { Heart, ShoppingBag, Sparkles } from 'lucide-react';
import { Link } from 'react-router';
import { ProductImage } from '@/components/ProductImage';
import { Badge } from '@/components/ui/Badge';
import { Button, ButtonLink } from '@/components/ui/Button';
import { Skeleton } from '@/components/ui/Feedback';
import { Price } from '@/components/ui/Price';
import { RatingStars } from '@/components/ui/Rating';
import { useAddToCart } from '@/features/cart/api';
import { useToggleWishlist, useWishlistIds } from '@/features/wishlist/api';
import { cn } from '@/lib/cn';
import type { ProductSummary } from '@/lib/types';

export function ProductCard({ product, reason, className }: { product: ProductSummary; reason?: string; className?: string }) {
  const saved = useWishlistIds().has(product.id);
  const toggleWishlist = useToggleWishlist();
  const { addToCart, pendingVariantId } = useAddToCart();
  const href = `/products/${product.slug}`;
  const quickAddVariant = product.inStock && product.variantCount <= 1 ? product.defaultVariantId : null;

  return (
    <article className={cn('group relative flex h-full flex-col overflow-hidden rounded-2xl border border-stone-200 bg-white transition-shadow hover:shadow-md', className)}>
      <Link to={href} className="relative block aspect-square overflow-hidden bg-stone-100" tabIndex={-1} aria-hidden>
        <ProductImage src={product.thumbnailUrl ?? product.imageUrl} alt=""
          className="h-full w-full transition-transform duration-300 group-hover:scale-105" />
        <div className="absolute left-2.5 top-2.5 flex flex-col items-start gap-1.5">
          {product.discountPercent >= 5 && <Badge tone="success">{Math.round(product.discountPercent)}% off</Badge>}
          {product.stockStatus === 'LOW_STOCK' && product.lowStockQuantity !== null && (
            <Badge tone="warning">Only {product.lowStockQuantity} left</Badge>
          )}
          {!product.inStock && <Badge tone="danger">Out of stock</Badge>}
        </div>
      </Link>
      <button type="button" onClick={() => toggleWishlist(product.id, saved)} aria-pressed={saved}
        aria-label={saved ? `Remove ${product.name} from wishlist` : `Save ${product.name} to wishlist`}
        className="absolute right-2.5 top-2.5 flex h-9 w-9 items-center justify-center rounded-full bg-white/90 shadow-sm transition-colors hover:bg-white">
        <Heart className={cn('h-4 w-4', saved ? 'fill-rose-500 text-rose-500' : 'text-slate-600')} aria-hidden />
      </button>
      <div className="flex flex-1 flex-col p-3.5 sm:p-4">
        <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{product.brand.name}</p>
        <h3 className="mt-1 line-clamp-2 text-sm font-semibold leading-snug text-slate-900">
          <Link to={href} className="hover:text-brand-700">{product.name}</Link>
        </h3>
        {product.reviewCount > 0 ? (
          <div className="mt-1.5 flex items-center gap-1.5">
            <RatingStars value={product.averageRating} />
            <span className="text-xs text-slate-500">{product.averageRating.toFixed(1)} ({product.reviewCount})</span>
          </div>
        ) : (
          <p className="mt-1.5 text-xs text-slate-500">No reviews yet</p>
        )}
        {reason && (
          <p className="mt-2 flex items-start gap-1 text-xs leading-snug text-brand-700">
            <Sparkles className="mt-0.5 h-3 w-3 shrink-0" aria-hidden />
            <span className="line-clamp-2">{reason}</span>
          </p>
        )}
        <div className="mt-auto flex items-end justify-between gap-2 pt-3">
          <Price salePrice={product.salePrice} price={product.price} size="sm" className="min-w-0" />
          {quickAddVariant !== null ? (
            <Button size="icon" variant="secondary" className="h-9 w-9 shrink-0" aria-label={`Add ${product.name} to cart`}
              loading={pendingVariantId === quickAddVariant}
              icon={pendingVariantId === quickAddVariant ? undefined : <ShoppingBag className="h-4 w-4" aria-hidden />}
              onClick={() => addToCart(quickAddVariant, product.name)} />
          ) : product.inStock && product.variantCount > 1 ? (
            <ButtonLink to={href} size="sm" variant="secondary" className="shrink-0">Options</ButtonLink>
          ) : null}
        </div>
      </div>
    </article>
  );
}

export function ProductCardSkeleton() {
  return (
    <div className="flex h-full flex-col overflow-hidden rounded-2xl border border-stone-200 bg-white" aria-hidden>
      <Skeleton className="aspect-square rounded-none" />
      <div className="space-y-2 p-4">
        <Skeleton className="h-3 w-16" />
        <Skeleton className="h-4 w-full" />
        <Skeleton className="h-4 w-2/3" />
        <Skeleton className="mt-3 h-5 w-20" />
      </div>
    </div>
  );
}
