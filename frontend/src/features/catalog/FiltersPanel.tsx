import { useId, useState, type FormEvent, type ReactNode } from 'react';
import { Button } from '@/components/ui/Button';
import { Checkbox, Input } from '@/components/ui/Field';
import { cn } from '@/lib/cn';
import { formatPrice } from '@/lib/format';
import type { Facets } from '@/lib/types';
import { hasActiveFilters, type FilterPatch, type ProductFilters } from './filters';

const PRICE_PRESETS = [
  { label: 'Under ₹2,000', min: undefined, max: 2000 },
  { label: '₹2,000 – ₹10,000', min: 2000, max: 10000 },
  { label: '₹10,000 – ₹40,000', min: 10000, max: 40000 },
  { label: 'Above ₹40,000', min: 40000, max: undefined },
];

const RATINGS = [
  { value: undefined, label: 'Any rating' },
  { value: 4, label: '4 stars & up' },
  { value: 3, label: '3 stars & up' },
];

const DISCOUNTS = [
  { value: undefined, label: 'Any discount' },
  { value: 10, label: '10% off or more' },
  { value: 25, label: '25% off or more' },
  { value: 40, label: '40% off or more' },
];

export function FiltersPanel({ filters, facets, onChange, onClear, showBrands = true, showDiscount = true }: {
  filters: ProductFilters;
  facets?: Facets;
  onChange: (patch: FilterPatch) => void;
  onClear: () => void;
  showBrands?: boolean;
  showDiscount?: boolean;
}) {
  const id = useId();
  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-slate-900">Filters</h2>
        {hasActiveFilters(filters) && (
          <button type="button" onClick={onClear} className="text-xs font-semibold text-brand-700 hover:underline">Clear all</button>
        )}
      </div>
      <FilterGroup title="Availability">
        <Checkbox label="In stock only" checked={filters.inStock} onChange={(event) => onChange({ inStock: event.target.checked })} />
      </FilterGroup>
      {showBrands && <BrandFilter filters={filters} facets={facets} onChange={onChange} />}
      <PriceFilter key={`${filters.minPrice ?? ''}:${filters.maxPrice ?? ''}`} filters={filters} facets={facets} onChange={onChange} />
      <FilterGroup title="Customer rating">
        <RadioList name={`${id}-rating`} value={filters.minRating} options={RATINGS} onChange={(value) => onChange({ minRating: value })} />
      </FilterGroup>
      {showDiscount && (
        <FilterGroup title="Discount">
          <RadioList name={`${id}-discount`} value={filters.minDiscount} options={DISCOUNTS} onChange={(value) => onChange({ minDiscount: value })} />
        </FilterGroup>
      )}
    </div>
  );
}

function FilterGroup({ title, children }: { title: string; children: ReactNode }) {
  return (
    <fieldset className="border-t border-stone-200 pt-5">
      <legend className="mb-3 text-xs font-semibold uppercase tracking-wide text-slate-500">{title}</legend>
      {children}
    </fieldset>
  );
}

function RadioList({ name, value, options, onChange }: {
  name: string;
  value: number | undefined;
  options: { value: number | undefined; label: string }[];
  onChange: (value: number | undefined) => void;
}) {
  return (
    <div className="space-y-2">
      {options.map((option) => (
        <label key={option.label} className="flex cursor-pointer items-center gap-2 text-sm text-slate-700">
          <input type="radio" name={name} checked={value === option.value} onChange={() => onChange(option.value)}
            className="h-4 w-4 accent-brand-600" />
          {option.label}
        </label>
      ))}
    </div>
  );
}

function BrandFilter({ filters, facets, onChange }: { filters: ProductFilters; facets?: Facets; onChange: (patch: FilterPatch) => void }) {
  const [expanded, setExpanded] = useState(false);
  const brands = facets?.brands ?? [];
  // Keep selected brands visible even when the other filters leave them with no matches.
  const missing = filters.brands.filter((slug) => !brands.some((brand) => brand.slug === slug))
    .map((slug) => ({ id: -1, name: slug, slug, count: 0 }));
  const all = [...brands, ...missing];
  if (all.length === 0) {
    return null;
  }
  const visible = expanded ? all : all.slice(0, 8);
  const toggle = (slug: string, checked: boolean) =>
    onChange({ brands: checked ? [...filters.brands, slug] : filters.brands.filter((brand) => brand !== slug) });
  return (
    <FilterGroup title="Brand">
      <div className="space-y-2">
        {visible.map((brand) => (
          <Checkbox key={brand.slug} checked={filters.brands.includes(brand.slug)}
            onChange={(event) => toggle(brand.slug, event.target.checked)}
            label={<span className="flex items-center gap-1.5">{brand.name}<span className="text-xs text-slate-400">({brand.count})</span></span>} />
        ))}
      </div>
      {all.length > 8 && (
        <button type="button" onClick={() => setExpanded((value) => !value)} className="mt-2 text-xs font-semibold text-brand-700 hover:underline">
          {expanded ? 'Show fewer' : `Show all ${all.length} brands`}
        </button>
      )}
    </FilterGroup>
  );
}

function parseAmount(value: string): number | undefined {
  const parsed = Number(value.replace(/[^0-9.]/g, ''));
  return value.trim() === '' || !Number.isFinite(parsed) ? undefined : parsed;
}

function PriceFilter({ filters, facets, onChange }: { filters: ProductFilters; facets?: Facets; onChange: (patch: FilterPatch) => void }) {
  const [min, setMin] = useState(filters.minPrice?.toString() ?? '');
  const [max, setMax] = useState(filters.maxPrice?.toString() ?? '');
  const apply = (event: FormEvent) => {
    event.preventDefault();
    let low = parseAmount(min);
    let high = parseAmount(max);
    if (low !== undefined && high !== undefined && low > high) {
      [low, high] = [high, low];
    }
    onChange({ minPrice: low, maxPrice: high });
  };
  return (
    <FilterGroup title="Price">
      <form onSubmit={apply} className="flex items-center gap-2">
        <Input aria-label="Minimum price" inputMode="numeric" value={min} onChange={(event) => setMin(event.target.value)}
          placeholder={facets?.minPrice != null ? formatPrice(Math.floor(facets.minPrice)) : 'Min'} className="h-9" />
        <span className="text-slate-400" aria-hidden>–</span>
        <Input aria-label="Maximum price" inputMode="numeric" value={max} onChange={(event) => setMax(event.target.value)}
          placeholder={facets?.maxPrice != null ? formatPrice(Math.ceil(facets.maxPrice)) : 'Max'} className="h-9" />
        <Button type="submit" size="sm" variant="secondary" className="h-9">Go</Button>
      </form>
      <div className="mt-3 flex flex-wrap gap-1.5">
        {PRICE_PRESETS.map((preset) => {
          const active = filters.minPrice === preset.min && filters.maxPrice === preset.max;
          return (
            <button key={preset.label} type="button" aria-pressed={active}
              onClick={() => onChange(active ? { minPrice: undefined, maxPrice: undefined } : { minPrice: preset.min, maxPrice: preset.max })}
              className={cn('rounded-full border px-2.5 py-1 text-xs font-medium transition-colors',
                active ? 'border-brand-500 bg-brand-50 text-brand-800' : 'border-stone-300 text-slate-600 hover:border-stone-400')}>
              {preset.label}
            </button>
          );
        })}
      </div>
    </FilterGroup>
  );
}
