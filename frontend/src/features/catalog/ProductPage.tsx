import { useQuery } from '@tanstack/react-query';
import { Banknote, Heart, PackageCheck, RotateCcw, Truck, Zap } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link, Navigate, useParams } from 'react-router';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { ErrorState, Skeleton } from '@/components/ui/Feedback';
import { Breadcrumbs } from '@/components/ui/PageHeader';
import { Price } from '@/components/ui/Price';
import { QuantityStepper } from '@/components/ui/QuantityStepper';
import { RatingStars } from '@/components/ui/Rating';
import { useSession } from '@/features/auth/useSession';
import { useAddToCart } from '@/features/cart/api';
import { useSimilar } from '@/features/recommendations/api';
import { ReviewsSection } from '@/features/reviews/ReviewsSection';
import { usePublicSettings } from '@/features/settings/api';
import { useToggleWishlist, useWishlistIds } from '@/features/wishlist/api';
import { cn } from '@/lib/cn';
import { toApiError } from '@/lib/errors';
import { formatPrice } from '@/lib/format';
import { useDocumentTitle } from '@/lib/hooks';
import type { ProductDetail } from '@/lib/types';
import NotFoundPage from '@/pages/NotFoundPage';
import { recordProductView } from './activity';
import { catalogApi } from './api';
import { ProductGallery } from './ProductGallery';
import { ProductRail } from './ProductRail';
import { VariantPicker } from './VariantPicker';
import { findVariant, initialSelection, selectValue } from './variants';

export default function ProductPage() {
  const { slug = '' } = useParams();
  // Order items link by product id (/products/42); those URLs redirect to the canonical slug URL.
  const byId = /^\d+$/.test(slug);
  const product = useQuery({
    queryKey: byId ? ['product-by-id', slug] : ['product', slug],
    queryFn: () => (byId ? catalogApi.productById(Number(slug)) : catalogApi.product(slug)),
  });
  useDocumentTitle(product.data?.name);

  if (byId && product.data) {
    return <Navigate to={`/products/${product.data.slug}`} replace />;
  }

  if (product.isPending) {
    return <ProductSkeleton />;
  }
  if (product.isError) {
    return toApiError(product.error).status === 404
      ? <NotFoundPage />
      : <div className="container-page py-10"><ErrorState error={product.error} onRetry={() => void product.refetch()} /></div>;
  }
  // Keyed by id so variant selection and quantity reset when moving to another product.
  return <ProductDetails key={product.data.id} product={product.data} />;
}

function ProductDetails({ product }: { product: ProductDetail }) {
  const { status, user } = useSession();
  const [selection, setSelection] = useState(() => initialSelection(product.variants, product.defaultVariantId));
  const [quantity, setQuantity] = useState(1);
  const { addToCart, isPending } = useAddToCart();
  const saved = useWishlistIds().has(product.id);
  const toggleWishlist = useToggleWishlist();
  const { data: settings } = usePublicSettings();
  const similar = useSimilar(product.id, 10);
  const related = useQuery({ queryKey: ['product', product.id, 'related'], queryFn: () => catalogApi.related(product.id, 10) });

  const userId = user?.id ?? null;
  useEffect(() => {
    if (status !== 'loading') {
      recordProductView(product.id, userId);
    }
  }, [product.id, userId, status]);

  const variant = findVariant(product.variants, product.options, selection);
  const maxQuantity = variant?.stockStatus === 'LOW_STOCK' && variant.lowStockQuantity ? Math.min(10, variant.lowStockQuantity) : 10;
  const orderQuantity = Math.min(quantity, maxQuantity);
  const canBuy = Boolean(variant?.inStock);

  return (
    <div className="container-page py-8">
      <Breadcrumbs className="mb-6" items={[
        { label: 'Home', to: '/' },
        { label: product.category.name, to: `/categories/${product.category.slug}` },
        { label: product.name },
      ]} />

      <div className="grid gap-8 lg:grid-cols-2 lg:gap-12">
        <ProductGallery images={product.images} name={product.name} />

        <div>
          <Link to={`/shop?brand=${encodeURIComponent(product.brand.slug)}`} className="text-sm font-semibold uppercase tracking-wide text-brand-700 hover:underline">
            {product.brand.name}
          </Link>
          <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">{product.name}</h1>
          <div className="mt-3 flex flex-wrap items-center gap-3 text-sm">
            {product.reviewCount > 0 ? (
              <a href="#reviews" className="flex items-center gap-2 text-slate-600 hover:text-slate-900">
                <RatingStars value={product.averageRating} size="md" />
                <span>{product.averageRating.toFixed(1)} · {product.reviewCount} review{product.reviewCount === 1 ? '' : 's'}</span>
              </a>
            ) : (
              <span className="text-slate-500">No reviews yet</span>
            )}
            {product.soldCount > 0 && <span className="text-slate-500">{product.soldCount}+ sold</span>}
          </div>

          <div className="mt-6">
            <Price salePrice={variant?.salePrice ?? product.salePrice} price={variant?.price ?? product.price}
              discountPercent={product.discountPercent} size="lg" />
            <p className="mt-1 text-xs text-slate-500">
              {settings?.pricesIncludeTax === false ? 'GST extra, calculated at checkout.' : 'Inclusive of all taxes.'}
            </p>
          </div>

          {product.shortDescription && <p className="mt-5 text-slate-600">{product.shortDescription}</p>}

          {product.options.length > 0 && (
            <div className="mt-6">
              <VariantPicker options={product.options} variants={product.variants} selection={selection}
                onSelect={(name, value) => setSelection((current) => selectValue(product.variants, product.options, current, name, value))} />
            </div>
          )}

          <div className="mt-6">
            {!variant ? (
              <Badge tone="neutral">Select an option</Badge>
            ) : variant.stockStatus === 'OUT_OF_STOCK' ? (
              <Badge tone="danger" dot>Out of stock</Badge>
            ) : variant.stockStatus === 'LOW_STOCK' ? (
              <Badge tone="warning" dot>Hurry, only {variant.lowStockQuantity} left</Badge>
            ) : (
              <Badge tone="success" dot>In stock</Badge>
            )}
            {variant && product.variants.length > 1 && <span className="ml-3 text-xs text-slate-400">SKU {variant.sku}</span>}
          </div>

          <div className="mt-6 flex flex-wrap items-center gap-3">
            <QuantityStepper value={orderQuantity} max={maxQuantity} onChange={setQuantity} disabled={!canBuy} />
            <Button size="lg" className="flex-1 sm:flex-none" disabled={!canBuy} loading={isPending}
              onClick={() => variant && addToCart(variant.id, product.name, orderQuantity)}>
              Add to cart
            </Button>
            <Button size="lg" variant="brand" className="flex-1 sm:flex-none" disabled={!canBuy || isPending}
              icon={<Zap className="h-4 w-4" aria-hidden />}
              onClick={() => variant && addToCart(variant.id, product.name, orderQuantity, { goToCheckout: true })}>
              Buy now
            </Button>
            <Button size="lg" variant="secondary" aria-pressed={saved} onClick={() => toggleWishlist(product.id, saved)}
              aria-label={saved ? 'Remove from wishlist' : 'Save to wishlist'} className="px-3">
              <Heart className={cn('h-5 w-5', saved ? 'fill-rose-500 text-rose-500' : 'text-slate-700')} aria-hidden />
            </Button>
          </div>

          <ul className="mt-8 space-y-3 rounded-2xl border border-stone-200 bg-white p-4 text-sm text-slate-600">
            <li className="flex gap-3"><Truck className="h-5 w-5 shrink-0 text-slate-400" aria-hidden />
              {settings ? `Free delivery on orders above ${formatPrice(settings.freeShippingThreshold)}; otherwise ${formatPrice(settings.shippingFee)}.` : 'Fast delivery across India.'}
            </li>
            {settings?.codEnabled && (
              <li className="flex gap-3"><Banknote className="h-5 w-5 shrink-0 text-slate-400" aria-hidden />
                Cash on Delivery available{settings.codMaxOrderAmount ? ` for orders up to ${formatPrice(settings.codMaxOrderAmount)}` : ''}.
              </li>
            )}
            <li className="flex gap-3"><RotateCcw className="h-5 w-5 shrink-0 text-slate-400" aria-hidden />Cancel free of charge until the order ships.</li>
            <li className="flex gap-3"><PackageCheck className="h-5 w-5 shrink-0 text-slate-400" aria-hidden />Live tracking from confirmation to delivery.</li>
          </ul>

          {product.tags.length > 0 && (
            <div className="mt-6 flex flex-wrap gap-1.5">
              {product.tags.map((tag) => (
                <Link key={tag} to={`/search?q=${encodeURIComponent(tag)}`}
                  className="rounded-full bg-stone-100 px-2.5 py-1 text-xs font-medium text-slate-600 hover:bg-stone-200">#{tag}</Link>
              ))}
            </div>
          )}
        </div>
      </div>

      <div className="mt-14 grid gap-10 lg:grid-cols-[1fr_24rem]">
        <section aria-labelledby="description-heading">
          <h2 id="description-heading" className="text-xl font-bold tracking-tight text-slate-900">About this product</h2>
          <div className="mt-4 whitespace-pre-line text-slate-700">{product.description}</div>
        </section>
        {product.specifications.length > 0 && (
          <section aria-labelledby="specs-heading">
            <h2 id="specs-heading" className="text-xl font-bold tracking-tight text-slate-900">Specifications</h2>
            <dl className="mt-4 divide-y divide-stone-100 overflow-hidden rounded-2xl border border-stone-200 bg-white text-sm">
              {product.specifications.map((spec) => (
                <div key={spec.name} className="grid grid-cols-[9rem_1fr] gap-3 px-4 py-2.5">
                  <dt className="text-slate-500">{spec.name}</dt>
                  <dd className="font-medium text-slate-800">{spec.value}</dd>
                </div>
              ))}
            </dl>
          </section>
        )}
      </div>

      <ProductRail title="Similar products"
        description={similar.data?.strategy === 'semantic' ? 'Matched by our AI on features and use, not just category.' : 'More products like this one.'}
        items={similar.data?.items} loading={similar.isPending} error={similar.error} onRetry={() => void similar.refetch()} />

      <ReviewsSection productId={product.id} productSlug={product.slug} />

      <ProductRail title={`More in ${product.category.name}`} items={related.data?.map((item) => ({ product: item }))}
        loading={related.isPending} error={related.error} onRetry={() => void related.refetch()} />
    </div>
  );
}

function ProductSkeleton() {
  return (
    <div className="container-page py-8" aria-busy="true">
      <Skeleton className="mb-6 h-4 w-64" />
      <div className="grid gap-8 lg:grid-cols-2 lg:gap-12">
        <Skeleton className="aspect-square rounded-2xl" />
        <div className="space-y-4">
          <Skeleton className="h-4 w-24" />
          <Skeleton className="h-9 w-3/4" />
          <Skeleton className="h-5 w-40" />
          <Skeleton className="h-10 w-48" />
          <Skeleton className="h-20 w-full" />
          <Skeleton className="h-12 w-full" />
        </div>
      </div>
    </div>
  );
}
