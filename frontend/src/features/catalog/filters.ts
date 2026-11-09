import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router';

/** Listing filters kept in the URL so results can be shared, bookmarked and navigated with Back. */
export interface ProductFilters {
  q: string;
  brands: string[];
  minPrice?: number;
  maxPrice?: number;
  minRating?: number;
  minDiscount?: number;
  inStock: boolean;
  sort: string;
  /** Zero-based page (the URL shows it one-based). */
  page: number;
}

export type FilterPatch = Partial<ProductFilters>;

function nonNegative(value: string | null): number | undefined {
  if (value === null || value.trim() === '') {
    return undefined;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : undefined;
}

export function parseFilters(params: URLSearchParams): ProductFilters {
  const brands = params.getAll('brand').flatMap((value) => value.split(',')).map((value) => value.trim()).filter(Boolean);
  const page = nonNegative(params.get('page'));
  return {
    q: params.get('q')?.trim() ?? '',
    brands: [...new Set(brands)],
    minPrice: nonNegative(params.get('minPrice')),
    maxPrice: nonNegative(params.get('maxPrice')),
    minRating: nonNegative(params.get('minRating')),
    minDiscount: nonNegative(params.get('minDiscount')),
    inStock: params.get('inStock') === 'true',
    sort: params.get('sort') ?? '',
    page: page && page >= 1 ? Math.floor(page) - 1 : 0,
  };
}

/** Applies a change to the URL parameters; any change other than the page returns to page one. */
export function applyFilters(params: URLSearchParams, patch: FilterPatch): URLSearchParams {
  const next = new URLSearchParams(params);
  const set = (key: string, value: string | number | undefined) => {
    if (value === undefined || value === '') {
      next.delete(key);
    } else {
      next.set(key, String(value));
    }
  };
  if ('q' in patch) set('q', patch.q?.trim());
  if ('brands' in patch) {
    next.delete('brand');
    patch.brands?.forEach((brand) => next.append('brand', brand));
  }
  if ('minPrice' in patch) set('minPrice', patch.minPrice);
  if ('maxPrice' in patch) set('maxPrice', patch.maxPrice);
  if ('minRating' in patch) set('minRating', patch.minRating);
  if ('minDiscount' in patch) set('minDiscount', patch.minDiscount);
  if ('inStock' in patch) set('inStock', patch.inStock ? 'true' : undefined);
  if ('sort' in patch) set('sort', patch.sort);
  if (patch.page) {
    next.set('page', String(patch.page + 1));
  } else {
    next.delete('page');
  }
  return next;
}

export function hasActiveFilters(filters: ProductFilters): boolean {
  return filters.brands.length > 0 || filters.minPrice !== undefined || filters.maxPrice !== undefined
    || filters.minRating !== undefined || filters.minDiscount !== undefined || filters.inStock;
}

export function useProductFilters() {
  const [params, setParams] = useSearchParams();
  const filters = useMemo(() => parseFilters(params), [params]);
  const update = useCallback((patch: FilterPatch, options: { scrollToTop?: boolean } = {}) => {
    setParams((current) => applyFilters(current, patch), { preventScrollReset: !options.scrollToTop });
  }, [setParams]);
  const clear = useCallback(() => {
    setParams((current) => {
      const next = new URLSearchParams();
      for (const key of ['q', 'sort']) {
        const value = current.get(key);
        if (value) {
          next.set(key, value);
        }
      }
      return next;
    }, { preventScrollReset: true });
  }, [setParams]);
  return { filters, update, clear };
}
