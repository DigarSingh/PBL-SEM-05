import { X } from 'lucide-react';
import { formatPrice } from '@/lib/format';
import type { Facets } from '@/lib/types';
import type { FilterPatch, ProductFilters } from './filters';

/** Removable chips summarising the active filters. */
export function ActiveFilterChips({ filters, facets, onChange, showQuery = true }: {
  filters: ProductFilters;
  facets?: Facets;
  onChange: (patch: FilterPatch) => void;
  showQuery?: boolean;
}) {
  const chips: { key: string; label: string; patch: FilterPatch }[] = [];
  if (showQuery && filters.q) {
    chips.push({ key: 'q', label: `"${filters.q}"`, patch: { q: '' } });
  }
  for (const slug of filters.brands) {
    const name = facets?.brands.find((brand) => brand.slug === slug)?.name ?? slug;
    chips.push({ key: `brand-${slug}`, label: name, patch: { brands: filters.brands.filter((brand) => brand !== slug) } });
  }
  if (filters.minPrice !== undefined || filters.maxPrice !== undefined) {
    const label = filters.minPrice !== undefined && filters.maxPrice !== undefined
      ? `${formatPrice(filters.minPrice)} – ${formatPrice(filters.maxPrice)}`
      : filters.minPrice !== undefined ? `Above ${formatPrice(filters.minPrice)}` : `Under ${formatPrice(filters.maxPrice)}`;
    chips.push({ key: 'price', label, patch: { minPrice: undefined, maxPrice: undefined } });
  }
  if (filters.minRating !== undefined) {
    chips.push({ key: 'rating', label: `${filters.minRating}★ & up`, patch: { minRating: undefined } });
  }
  if (filters.minDiscount !== undefined) {
    chips.push({ key: 'discount', label: `${filters.minDiscount}%+ off`, patch: { minDiscount: undefined } });
  }
  if (filters.inStock) {
    chips.push({ key: 'stock', label: 'In stock', patch: { inStock: false } });
  }
  if (chips.length === 0) {
    return null;
  }
  return (
    <ul className="flex flex-wrap gap-2" aria-label="Active filters">
      {chips.map((chip) => (
        <li key={chip.key}>
          <button type="button" onClick={() => onChange(chip.patch)} aria-label={`Remove filter ${chip.label}`}
            className="inline-flex items-center gap-1 rounded-full border border-stone-300 bg-white py-1 pl-3 pr-2 text-xs font-medium text-slate-700 hover:border-stone-400">
            {chip.label}
            <X className="h-3.5 w-3.5 text-slate-400" aria-hidden />
          </button>
        </li>
      ))}
    </ul>
  );
}
