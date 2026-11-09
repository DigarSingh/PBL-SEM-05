import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { SlidersHorizontal } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useParams } from 'react-router';
import { Button } from '@/components/ui/Button';
import { Drawer } from '@/components/ui/Dialog';
import { EmptyState, ErrorState } from '@/components/ui/Feedback';
import { Select } from '@/components/ui/Field';
import { PageHeader } from '@/components/ui/PageHeader';
import { Pagination } from '@/components/ui/Pagination';
import { useSession } from '@/features/auth/useSession';
import { toApiError } from '@/lib/errors';
import { formatNumber } from '@/lib/format';
import { useDocumentTitle } from '@/lib/hooks';
import NotFoundPage from '@/pages/NotFoundPage';
import { ActiveFilterChips } from './ActiveFilterChips';
import { recordCategoryView } from './activity';
import { catalogApi, type FacetQuery, type ProductQuery } from './api';
import { hasActiveFilters, useProductFilters } from './filters';
import { FiltersPanel } from './FiltersPanel';
import { ProductGrid } from './ProductGrid';

const PAGE_SIZE = 24;

const SORT_OPTIONS = [
  { value: 'newest', label: 'Newest first' },
  { value: 'price_asc', label: 'Price: low to high' },
  { value: 'price_desc', label: 'Price: high to low' },
  { value: 'rating', label: 'Top rated' },
  { value: 'discount', label: 'Biggest discount' },
];

export default function ShopPage() {
  const { slug } = useParams();
  const { filters, update, clear } = useProductFilters();
  const { status, user } = useSession();
  const [filtersOpen, setFiltersOpen] = useState(false);

  const category = useQuery({
    queryKey: ['category', slug],
    queryFn: () => catalogApi.category(slug ?? ''),
    enabled: Boolean(slug),
    staleTime: 5 * 60_000,
  });

  const facetQuery: FacetQuery = {
    q: filters.q || undefined,
    category: slug,
    minPrice: filters.minPrice,
    maxPrice: filters.maxPrice,
    minRating: filters.minRating,
    inStock: filters.inStock || undefined,
  };
  const query: ProductQuery = {
    ...facetQuery,
    brand: filters.brands,
    minDiscount: filters.minDiscount,
    sort: filters.sort || undefined,
    page: filters.page,
    size: PAGE_SIZE,
  };
  const products = useQuery({ queryKey: ['products', query], queryFn: () => catalogApi.products(query), placeholderData: keepPreviousData });
  const facets = useQuery({ queryKey: ['facets', facetQuery], queryFn: () => catalogApi.facets(facetQuery), placeholderData: keepPreviousData });

  const categoryId = category.data?.id;
  const userId = user?.id;
  useEffect(() => {
    if (categoryId && userId && status === 'authenticated') {
      recordCategoryView(categoryId, userId);
    }
  }, [categoryId, userId, status]);

  const title = slug ? category.data?.name ?? '' : filters.q ? `Results for "${filters.q}"` : 'All products';
  useDocumentTitle(title || 'Shop');

  if (slug && category.isError && toApiError(category.error).status === 404) {
    return <NotFoundPage />;
  }

  const data = products.data;
  const first = data ? data.page * data.size + 1 : 0;
  const last = data ? Math.min(data.totalElements, (data.page + 1) * data.size) : 0;
  const panel = (
    <FiltersPanel filters={filters} facets={facets.data} onClear={clear}
      onChange={(patch) => update(patch)} />
  );

  return (
    <div className="container-page py-8">
      <PageHeader title={title || ' '}
        description={slug ? category.data?.description ?? undefined : 'Phones, laptops, audio, wearables and more.'}
        breadcrumbs={[{ label: 'Home', to: '/' }, { label: 'Shop', to: '/shop' }, ...(slug ? [{ label: category.data?.name ?? '...' }] : [])]} />

      <div className="grid gap-8 lg:grid-cols-[15rem_1fr]">
        <aside className="hidden lg:block" aria-label="Product filters">{panel}</aside>

        <div className="min-w-0">
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
            <p className="text-sm text-slate-500" aria-live="polite">
              {data ? (data.totalElements === 0 ? 'No products' : `Showing ${first}–${last} of ${formatNumber(data.totalElements)} products`) : ' '}
            </p>
            <div className="flex items-center gap-2">
              <Button variant="secondary" size="sm" className="lg:hidden" onClick={() => setFiltersOpen(true)}
                icon={<SlidersHorizontal className="h-4 w-4" aria-hidden />}>
                Filters
              </Button>
              <label className="flex items-center gap-2 text-sm text-slate-600">
                <span className="hidden sm:inline">Sort by</span>
                <Select value={filters.sort} onChange={(event) => update({ sort: event.target.value })} className="h-9 w-auto">
                  <option value="">{filters.q ? 'Most relevant' : 'Most popular'}</option>
                  {SORT_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
                </Select>
              </label>
            </div>
          </div>

          <ActiveFilterChips filters={filters} facets={facets.data} onChange={(patch) => update(patch)} />

          <div className="mt-4">
            {products.isError && !data ? (
              <ErrorState error={products.error} onRetry={() => void products.refetch()} />
            ) : data && data.items.length === 0 ? (
              <EmptyState title="No products match" description={hasActiveFilters(filters) || filters.q
                ? 'Try removing a filter or searching for something broader.'
                : 'This category has no products yet. Check back soon.'}
                action={hasActiveFilters(filters) ? <Button variant="secondary" onClick={clear}>Clear filters</Button> : undefined} />
            ) : (
              <ProductGrid items={data?.items.map((product) => ({ product })) ?? []} loading={!data} dimmed={products.isPlaceholderData} />
            )}
          </div>

          {data && (
            <Pagination className="mt-10" page={data.page} totalPages={data.totalPages}
              onChange={(page) => update({ page }, { scrollToTop: true })} />
          )}
        </div>
      </div>

      <Drawer open={filtersOpen} onClose={() => setFiltersOpen(false)} title="Filters" side="left"
        footer={<Button className="w-full" onClick={() => setFiltersOpen(false)}>
          {data ? `Show ${formatNumber(data.totalElements)} products` : 'Show products'}
        </Button>}>
        {panel}
      </Drawer>
    </div>
  );
}
