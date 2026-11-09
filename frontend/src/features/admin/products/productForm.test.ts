import { describe, expect, it } from 'vitest';
import { formatAttributes, parseAttributes, productSchema, splitTags, toFormValues, toProductInput } from './productForm';

describe('product form helpers', () => {
  it('round-trips variant options', () => {
    const attributes = parseAttributes('Color: Black, Storage: 256 GB');
    expect(attributes).toEqual({ Color: 'Black', Storage: '256 GB' });
    expect(formatAttributes(attributes ?? {})).toBe('Color: Black, Storage: 256 GB');
    expect(parseAttributes('Color Black')).toBeNull();
    expect(parseAttributes('')).toEqual({});
  });

  it('normalises tags', () => {
    expect(splitTags('Wireless, gaming,, wireless ')).toEqual(['wireless', 'gaming']);
  });

  it('builds the API payload and only sends opening stock for new variants', () => {
    const values = toFormValues(null);
    values.name = 'Test Charger';
    values.brandId = '3';
    values.categoryId = '9';
    values.description = 'A compact charger for phones and tablets.';
    values.price = '1999';
    values.discountPercent = '10';
    values.variants = [
      { ...values.variants[0], sku: 'TC-1', initialStock: '5' },
      { variantId: 44, sku: 'TC-2', name: 'Blue', attributes: 'Color: Blue', priceOverride: '2099', active: true, initialStock: '9', lowStockThreshold: '3' },
    ];
    const parsed = productSchema.parse(values);
    const input = toProductInput(parsed);
    expect(input.brandId).toBe(3);
    expect(input.price).toBe(1999);
    expect(input.variants[0]).toMatchObject({ id: null, sku: 'TC-1', initialStock: 5 });
    expect(input.variants[1]).toMatchObject({ id: 44, initialStock: null, priceOverride: 2099, attributes: { Color: 'Blue' } });
    expect(input.specifications).toEqual([]);
  });

  it('rejects duplicate SKUs', () => {
    const values = toFormValues(null);
    Object.assign(values, { name: 'Dup', brandId: '1', categoryId: '1', description: 'Long enough description text.', price: '100' });
    values.variants = [{ ...values.variants[0], sku: 'SAME' }, { ...values.variants[0], sku: 'same' }];
    const result = productSchema.safeParse(values);
    expect(result.success).toBe(false);
    expect(result.error?.issues.some((issue) => issue.path.join('.') === 'variants.1.sku')).toBe(true);
  });
});
