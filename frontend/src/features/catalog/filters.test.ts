import { describe, expect, it } from 'vitest';
import { applyFilters, hasActiveFilters, parseFilters } from './filters';

describe('product filters in the URL', () => {
  it('parses repeated and comma-separated brands, prices and a one-based page', () => {
    const filters = parseFilters(new URLSearchParams('brand=nova&brand=orbit,nova&minPrice=1000&maxPrice=abc&page=3&inStock=true'));
    expect(filters.brands).toEqual(['nova', 'orbit']);
    expect(filters.minPrice).toBe(1000);
    expect(filters.maxPrice).toBeUndefined();
    expect(filters.page).toBe(2);
    expect(filters.inStock).toBe(true);
    expect(hasActiveFilters(filters)).toBe(true);
  });

  it('returns to the first page whenever a filter changes', () => {
    const next = applyFilters(new URLSearchParams('q=phone&page=4'), { minRating: 4 });
    expect(next.get('page')).toBeNull();
    expect(next.get('minRating')).toBe('4');
    expect(next.get('q')).toBe('phone');
  });

  it('writes pages one-based and removes cleared values', () => {
    const next = applyFilters(new URLSearchParams('brand=nova&inStock=true'), { page: 2, brands: [], inStock: false });
    expect(next.get('page')).toBe('3');
    expect(next.getAll('brand')).toEqual([]);
    expect(next.has('inStock')).toBe(false);
  });
});
