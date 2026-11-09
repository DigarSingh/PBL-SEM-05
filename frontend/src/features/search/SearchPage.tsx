import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { Brain, SearchX, SlidersHorizontal } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Drawer } from '@/components/ui/Dialog';
import { EmptyState, ErrorState, InlineAlert } from '@/components/ui/Feedback';
import { Select } from '@/components/ui/Field';
import { Pagination } from '@/components/ui/Pagination';
import { useSession } from '@/features/auth/useSession';
import { ActiveFilterChips } from '@/features/catalog/ActiveFilterChips';
import { hasActiveFilters, useProductFilters } from '@/features/catalog/filters';
import { FiltersPanel } from '@/features/catalog/FiltersPanel';
import { ProductGrid } from '@/features/catalog/ProductGrid';
import { formatNumber, formatPrice } from '@/lib/format';
import { useDocumentTitle } from '@/lib/hooks';
import type { SearchInterpretation } from '@/lib/types';
import { searchApi, type SmartSearchQuery } from './api';

const EXAMPLES = ['gaming headphones under 5000 with wireless', 'laptop for video editing', 'earphones for the gym', 'phone with long battery life'];

const SORTS = [
  { value: '', label: 'Best match' },
  { value: 'price_asc', label: 'Price: low to high' },
  { value: 'price_desc', label: 'Price: high to low' },
  { value: 'rating', label: 'Top rated' },
  { value: 'newest', label: 'Newest first' },
];

export default function SearchPage() {
  const { filters, update, clear } = useProductFilters();
  const { status, user } = useSession();
  const queryClient = useQueryClient();
  const [filtersOpen, setFiltersOpen] = useState(false);
  const q = filters.q;
  useDocumentTitle(q ? `Search: ${q}` : 'Search');

  const query: SmartSearchQuery = {
    q,
    brand: filters.brands,
    minPrice: filters.minPrice,
    maxPrice: filters.maxPrice,
    minRating: filters.minRating,
    inStock: filters.inStock || undefined,
    sort: filters.sort || undefined,
    page: filters.page,
    size: 24,
  };
  // Waits for the session so signed-in searches count towards personal recommendations.
  const results = useQuery({
    queryKey: ['search', user?.id ?? 'guest', query],
    queryFn: ({ signal }) => searchApi.search(query, signal),
    enabled: q.length > 0 && status !== 'loading',
    placeholderData: keepPreviousData,
  });

  const answeredQuery = results.data?.query;
  useEffect(() => {
    if (answeredQuery) {
      void queryClient.invalidateQueries({ queryKey: ['search-suggestions'] });
    }
  }, [answeredQuery, queryClient]);

  if (!q) {
    return (
      <div className="container-page py-16">
        <EmptyState title="What are you looking for?" description="Describe what you need in your own words. We understand budgets, features and how you plan to use it."
          action={<div className="flex flex-wrap justify-center gap-2">{EXAMPLES.map((example) => (
            <Link key={example} to={`/search?q=${encodeURIComponent(example)}`}
              className="rounded-full border border-stone-300 bg-white px-3 py-1.5 text-sm text-slate-700 hover:border-brand-400">{example}</Link>
          ))}</div>} />
      </div>
    );
  }

  const data = results.data;
  const panel = <FiltersPanel filters={filters} onChange={(patch) => update(patch)} onClear={clear} showBrands={false} showDiscount={false} />;

  return (
    <div className="container-page py-8">
      <div className="mb-6">
        <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">Results for &ldquo;{q}&rdquo;</h1>
        {data && (
          <p className="mt-1 flex flex-wrap items-center gap-2 text-sm text-slate-500">
            {formatNumber(data.totalElements)} product{data.totalElements === 1 ? '' : 's'}
            {data.engine === 'semantic' ? (
              <Badge tone="brand"><Brain className="h-3 w-3" aria-hidden />Ranked by AI semantic search</Badge>
            ) : (
              <Badge tone="neutral">Ranked by keyword match</Badge>
            )}
          </p>
        )}
      </div>

      {data && <Interpretation interpretation={data.interpretation} />}

      <div className="mt-6 grid gap-8 lg:grid-cols-[15rem_1fr]">
        <aside className="hidden lg:block" aria-label="Refine results">{panel}</aside>
        <div className="min-w-0">
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
            <ActiveFilterChips filters={filters} onChange={(patch) => update(patch)} showQuery={false} />
            <div className="ml-auto flex items-center gap-2">
              <Button variant="secondary" size="sm" className="lg:hidden" onClick={() => setFiltersOpen(true)}
                icon={<SlidersHorizontal className="h-4 w-4" aria-hidden />}>Refine</Button>
              <Select aria-label="Sort results" value={filters.sort} onChange={(event) => update({ sort: event.target.value })} className="h-9 w-auto">
                {SORTS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
              </Select>
            </div>
          </div>

          {results.isError && !data ? (
            <ErrorState error={results.error} onRetry={() => void results.refetch()} />
          ) : data && data.items.length === 0 ? (
            <EmptyState icon={<SearchX className="h-7 w-7" aria-hidden />} title="No products found"
              description={hasActiveFilters(filters) ? 'Try removing a filter.' : 'Try different words, a broader budget, or browse a category.'}
              action={hasActiveFilters(filters) ? <Button variant="secondary" onClick={clear}>Clear filters</Button>
                : <Link to="/shop" className="font-semibold text-brand-700 hover:underline">Browse all products</Link>} />
          ) : (
            <ProductGrid loading={!data} dimmed={results.isPlaceholderData}
              items={data?.items.map((item) => ({ product: item.product, reason: item.matchReasons.slice(0, 2).join(' · ') || undefined })) ?? []} />
          )}

          {data && <Pagination className="mt-10" page={data.page} totalPages={data.totalPages} onChange={(page) => update({ page }, { scrollToTop: true })} />}
        </div>
      </div>

      <Drawer open={filtersOpen} onClose={() => setFiltersOpen(false)} title="Refine results" side="left"
        footer={<Button className="w-full" onClick={() => setFiltersOpen(false)}>Show results</Button>}>
        {panel}
      </Drawer>
    </div>
  );
}

/** Shows how the query was understood, so shoppers can see why these results appear. */
function Interpretation({ interpretation }: { interpretation: SearchInterpretation }) {
  const chips: string[] = [];
  if (interpretation.category) chips.push(`Category: ${interpretation.category}`);
  if (interpretation.brand) chips.push(`Brand: ${interpretation.brand}`);
  if (interpretation.minPrice != null && interpretation.maxPrice != null) {
    chips.push(`${formatPrice(interpretation.minPrice)} – ${formatPrice(interpretation.maxPrice)}`);
  } else if (interpretation.maxPrice != null) {
    chips.push(`Under ${formatPrice(interpretation.maxPrice)}`);
  } else if (interpretation.minPrice != null) {
    chips.push(`Above ${formatPrice(interpretation.minPrice)}`);
  }
  if (interpretation.minRating != null) chips.push(`${interpretation.minRating}★ & up`);
  if (interpretation.inStockOnly) chips.push('In stock');
  interpretation.features.forEach((feature) => chips.push(feature));
  interpretation.useCases.forEach((useCase) => chips.push(`For ${useCase}`));
  if (chips.length === 0 && !interpretation.filtersRelaxed) {
    return null;
  }
  return (
    <div className="space-y-3">
      {chips.length > 0 && (
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <span className="text-slate-500">We looked for:</span>
          {chips.map((chip) => <Badge key={chip} tone="info">{chip}</Badge>)}
        </div>
      )}
      {interpretation.filtersRelaxed && (
        <InlineAlert tone="info">No product matched every requirement, so we&apos;re showing the closest matches.</InlineAlert>
      )}
    </div>
  );
}
