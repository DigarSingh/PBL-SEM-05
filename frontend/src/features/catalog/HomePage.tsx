import { useQuery } from '@tanstack/react-query';
import { ArrowRight, Copy, Sparkles, Ticket } from 'lucide-react';
import { Link } from 'react-router';
import { toast } from 'sonner';
import { ProductImage } from '@/components/ProductImage';
import { ButtonLink } from '@/components/ui/Button';
import { Skeleton } from '@/components/ui/Feedback';
import { useSession } from '@/features/auth/useSession';
import { useOffers } from '@/features/cart/api';
import { useForYou, useTrending } from '@/features/recommendations/api';
import { usePublicSettings } from '@/features/settings/api';
import { formatDate, formatPrice } from '@/lib/format';
import { useDocumentTitle } from '@/lib/hooks';
import type { Offer } from '@/lib/types';
import { catalogApi, useCategories } from './api';
import { ProductRail } from './ProductRail';

const EXAMPLE_SEARCHES = ['wireless earbuds for the gym under 3000', 'laptop for video editing', 'phone with a great camera', 'smartwatch for running'];

export default function HomePage() {
  useDocumentTitle('Smart electronics, delivered');
  const { status } = useSession();
  const categories = useCategories();
  const featured = useQuery({ queryKey: ['products', 'home-featured'], queryFn: () => catalogApi.products({ featured: true, size: 8 }) });
  const deals = useQuery({ queryKey: ['products', 'home-deals'], queryFn: () => catalogApi.products({ sort: 'discount', inStock: true, size: 10 }) });
  const newest = useQuery({ queryKey: ['products', 'home-new'], queryFn: () => catalogApi.products({ sort: 'newest', size: 10 }) });
  const trending = useTrending(10);
  const forYou = useForYou(12);
  const offers = useOffers();
  const { data: settings } = usePublicSettings();

  const heroProducts = featured.data?.items.slice(0, 4) ?? [];
  const personalised = status === 'authenticated' && forYou.data && forYou.data.strategy !== 'trending';

  return (
    <div>
      <section className="border-b border-stone-200 bg-gradient-to-br from-white via-stone-50 to-brand-50/60">
        <div className="container-page grid items-center gap-10 py-12 lg:grid-cols-2 lg:py-20">
          <div className="animate-slide-up">
            <p className="inline-flex items-center gap-1.5 rounded-full bg-brand-100 px-3 py-1 text-xs font-semibold text-brand-800">
              <Sparkles className="h-3.5 w-3.5" aria-hidden /> AI-powered shopping
            </p>
            <h1 className="mt-4 max-w-xl text-4xl font-bold tracking-tight text-slate-900 sm:text-5xl">
              Find the right tech, just by describing it.
            </h1>
            <p className="mt-4 max-w-lg text-lg text-slate-600">
              Search the way you talk, get recommendations that learn from you, and follow every order live
              from checkout to your door.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <ButtonLink to="/shop" size="lg" icon={<ArrowRight className="h-4 w-4" aria-hidden />}>Shop all products</ButtonLink>
              <ButtonLink to="/shop?sort=discount" size="lg" variant="secondary">Today&apos;s deals</ButtonLink>
            </div>
            <div className="mt-8">
              <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Try asking for</p>
              <div className="mt-2 flex flex-wrap gap-2">
                {EXAMPLE_SEARCHES.map((example) => (
                  <Link key={example} to={`/search?q=${encodeURIComponent(example)}`}
                    className="rounded-full border border-stone-300 bg-white px-3 py-1.5 text-sm text-slate-700 hover:border-brand-400 hover:text-brand-800">
                    {example}
                  </Link>
                ))}
              </div>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3 sm:gap-4">
            {featured.isPending
              ? Array.from({ length: 4 }, (_, index) => <Skeleton key={index} className="aspect-square rounded-2xl" />)
              : heroProducts.map((product, index) => (
                <Link key={product.id} to={`/products/${product.slug}`}
                  className={`group relative aspect-square overflow-hidden rounded-2xl border border-stone-200 bg-white shadow-sm ${index % 2 === 1 ? 'translate-y-6' : ''}`}>
                  <ProductImage src={product.imageUrl} alt={product.name} eager className="h-full w-full transition-transform duration-500 group-hover:scale-105" />
                  <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-slate-900/75 to-transparent p-3 text-white">
                    <p className="line-clamp-1 text-sm font-semibold">{product.name}</p>
                    <p className="text-xs opacity-90">{formatPrice(product.salePrice)}</p>
                  </div>
                </Link>
              ))}
          </div>
        </div>
      </section>

      <div className="container-page">
        {offers.data && offers.data.length > 0 && <OffersStrip offers={offers.data} />}

        <section className="py-8" aria-labelledby="home-categories">
          <div className="mb-4 flex items-end justify-between">
            <h2 id="home-categories" className="text-xl font-bold tracking-tight text-slate-900">Shop by category</h2>
            <Link to="/shop" className="text-sm font-semibold text-brand-700 hover:underline">View all</Link>
          </div>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 sm:gap-4">
            {categories.isPending
              ? Array.from({ length: 8 }, (_, index) => <Skeleton key={index} className="h-28 rounded-2xl" />)
              : categories.data?.map((category) => (
                <Link key={category.id} to={`/categories/${category.slug}`}
                  className="group relative flex h-28 items-end overflow-hidden rounded-2xl border border-stone-200 bg-white p-4 sm:h-32">
                  <ProductImage src={category.imageUrl} alt="" className="absolute inset-0 h-full w-full opacity-90 transition-transform duration-500 group-hover:scale-105" />
                  <span className="absolute inset-0 bg-gradient-to-t from-slate-900/70 via-slate-900/10 to-transparent" aria-hidden />
                  <span className="relative text-white">
                    <span className="block font-semibold">{category.name}</span>
                    <span className="text-xs opacity-90">{category.productCount} products</span>
                  </span>
                </Link>
              ))}
          </div>
        </section>

        {personalised && (
          <ProductRail title="Recommended for you" description="Picked from what you've viewed, searched, saved and bought."
            items={forYou.data?.items} error={forYou.error} onRetry={() => void forYou.refetch()} />
        )}
        <ProductRail title="Trending this week" description="What shoppers are buying, viewing and saving right now."
          items={trending.data?.items} loading={trending.isPending} error={trending.error} onRetry={() => void trending.refetch()} />
        <ProductRail title="Top deals" description="The biggest discounts on products in stock."
          items={deals.data?.items.map((product) => ({ product }))} loading={deals.isPending} error={deals.error}
          onRetry={() => void deals.refetch()}
          action={<Link to="/shop?sort=discount" className="text-sm font-semibold text-brand-700 hover:underline">See all</Link>} />
        <ProductRail title="New arrivals" items={newest.data?.items.map((product) => ({ product }))} loading={newest.isPending}
          error={newest.error} onRetry={() => void newest.refetch()}
          action={<Link to="/shop?sort=newest" className="text-sm font-semibold text-brand-700 hover:underline">See all</Link>} />

        {settings && (
          <p className="mt-6 rounded-2xl bg-ink px-6 py-5 text-center text-sm text-white">
            Free delivery on orders above <strong>{formatPrice(settings.freeShippingThreshold)}</strong>
            {settings.codEnabled && <> &middot; Cash on Delivery available</>}
            {settings.onlinePaymentsEnabled && <> &middot; UPI, cards &amp; net banking via Razorpay</>}
          </p>
        )}
      </div>
    </div>
  );
}

function OffersStrip({ offers }: { offers: Offer[] }) {
  const copy = async (code: string) => {
    try {
      await navigator.clipboard.writeText(code);
      toast.success(`Code ${code} copied. Apply it in your cart.`);
    } catch {
      toast.info(`Use code ${code} in your cart.`);
    }
  };
  return (
    <section className="pt-8" aria-label="Offers">
      <div className="-mx-4 flex gap-3 overflow-x-auto px-4 pb-1 scrollbar-none sm:mx-0 sm:px-0">
        {offers.map((offer) => (
          <div key={offer.code} className="flex min-w-72 items-center gap-3 rounded-2xl border border-dashed border-brand-300 bg-brand-50/60 p-4">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-white text-brand-600">
              <Ticket className="h-5 w-5" aria-hidden />
            </span>
            <div className="min-w-0 flex-1">
              <p className="text-sm font-semibold text-slate-900">
                {offer.discountType === 'PERCENTAGE' ? `${offer.discountValue}% off` : `${formatPrice(offer.discountValue)} off`}
                {offer.minOrderAmount ? ` on ${formatPrice(offer.minOrderAmount)}+` : ''}
              </p>
              <p className="truncate text-xs text-slate-500">
                {offer.description ?? 'Limited-time offer'}{offer.expiresAt ? ` · Ends ${formatDate(offer.expiresAt)}` : ''}
              </p>
            </div>
            <button type="button" onClick={() => void copy(offer.code)}
              className="inline-flex items-center gap-1 rounded-lg border border-brand-300 bg-white px-2.5 py-1.5 font-mono text-xs font-bold text-brand-800 hover:bg-brand-50"
              aria-label={`Copy coupon code ${offer.code}`}>
              {offer.code}<Copy className="h-3 w-3" aria-hidden />
            </button>
          </div>
        ))}
      </div>
    </section>
  );
}
