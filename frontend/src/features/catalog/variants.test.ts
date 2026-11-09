import { describe, expect, it } from 'vitest';
import type { Variant, VariantOption } from '@/lib/types';
import { findVariant, initialSelection, selectValue, valueState } from './variants';

function variant(id: number, color: string, storage: string, inStock = true): Variant {
  return {
    id, sku: `SKU-${id}`, name: `${color} ${storage}`, attributes: { Color: color, Storage: storage },
    price: 100, salePrice: 90, inStock, stockStatus: inStock ? 'IN_STOCK' : 'OUT_OF_STOCK', lowStockQuantity: null,
  };
}

const variants = [variant(1, 'Black', '128 GB'), variant(2, 'Black', '256 GB', false), variant(3, 'Blue', '256 GB')];
const options: VariantOption[] = [{ name: 'Color', values: ['Black', 'Blue'] }, { name: 'Storage', values: ['128 GB', '256 GB'] }];

describe('variant selection', () => {
  it('starts from the default variant', () => {
    expect(initialSelection(variants, 3)).toEqual({ Color: 'Blue', Storage: '256 GB' });
  });

  it('finds the variant for a full selection', () => {
    expect(findVariant(variants, options, { Color: 'Black', Storage: '256 GB' })?.id).toBe(2);
  });

  it('marks combinations as available, out of stock or impossible', () => {
    const selection = { Color: 'Black', Storage: '128 GB' };
    expect(valueState(variants, options, selection, 'Storage', '256 GB')).toBe('out-of-stock');
    expect(valueState(variants, options, { Color: 'Blue', Storage: '256 GB' }, 'Storage', '128 GB')).toBe('unavailable');
    expect(valueState(variants, options, selection, 'Color', 'Blue')).toBe('unavailable');
  });

  it('jumps to an existing in-stock combination when needed', () => {
    expect(selectValue(variants, options, { Color: 'Black', Storage: '128 GB' }, 'Color', 'Blue')).toEqual({ Color: 'Blue', Storage: '256 GB' });
  });
});
