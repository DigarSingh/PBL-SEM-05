import { z } from 'zod';
import { emptyToNull, numeric } from '@/lib/forms';
import type { AdminProduct, ProductStatus } from '@/lib/types';
import type { ProductInput } from '../api';

export const PRODUCT_STATUSES: ProductStatus[] = ['DRAFT', 'ACTIVE', 'INACTIVE', 'ARCHIVED'];
const SLUG = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const SKU = /^[A-Za-z0-9._-]+$/;

/** "Color: Black, Storage: 256 GB" -> { Color: "Black", Storage: "256 GB" }; null when malformed. */
export function parseAttributes(text: string): Record<string, string> | null {
  const trimmed = text.trim();
  if (!trimmed) {
    return {};
  }
  const result: Record<string, string> = {};
  for (const part of trimmed.split(',')) {
    const index = part.indexOf(':');
    if (index <= 0) {
      return null;
    }
    const name = part.slice(0, index).trim();
    const value = part.slice(index + 1).trim();
    if (!name || !value || name.length > 40 || value.length > 80) {
      return null;
    }
    result[name] = value;
  }
  return Object.keys(result).length <= 10 ? result : null;
}

export function formatAttributes(attributes: Record<string, string>): string {
  return Object.entries(attributes).map(([name, value]) => `${name}: ${value}`).join(', ');
}

export function splitTags(text: string): string[] {
  return [...new Set(text.split(',').map((tag) => tag.trim().toLowerCase()).filter(Boolean))];
}

// The database id lives in `variantId`: useFieldArray reserves `id` for its own row keys.
const variantSchema = z.object({
  variantId: z.number().nullable(),
  sku: z.string().trim().min(1, 'SKU is required').max(64, 'SKU is too long').regex(SKU, "Use letters, digits, '.', '_' or '-'"),
  name: z.string().trim().min(1, 'Variant name is required').max(120, 'Name is too long'),
  attributes: z.string().max(400).refine((value) => parseAttributes(value) !== null, 'Use "Name: value" pairs separated by commas'),
  priceOverride: numeric(z.number({ error: 'Enter a price' }).min(0, 'Cannot be negative').optional()),
  active: z.boolean(),
  initialStock: numeric(z.number({ error: 'Enter a number' }).int('Whole units only').min(0, 'Cannot be negative').max(1_000_000).optional()),
  lowStockThreshold: numeric(z.number({ error: 'Enter a number' }).int('Whole units only').min(0).max(100_000).optional()),
});

export const productSchema = z.object({
  name: z.string().trim().min(3, 'Enter the product name').max(200, 'Name is too long'),
  slug: z.string().trim().max(220).refine((value) => value === '' || SLUG.test(value), 'Lowercase words separated by hyphens'),
  brandId: numeric(z.number({ error: 'Choose a brand' }).int().positive('Choose a brand')),
  categoryId: numeric(z.number({ error: 'Choose a category' }).int().positive('Choose a category')),
  shortDescription: z.string().trim().max(300, 'Keep it under 300 characters'),
  description: z.string().trim().min(20, 'Describe the product in at least 20 characters').max(10_000),
  price: numeric(z.number({ error: 'Enter the list price' }).min(0, 'Cannot be negative').max(99_99_99_999)),
  discountPercent: numeric(z.number({ error: 'Enter a discount (0 for none)' }).min(0).max(95, 'At most 95%')),
  status: z.enum(['DRAFT', 'ACTIVE', 'INACTIVE', 'ARCHIVED']),
  featured: z.boolean(),
  tags: z.string().max(900).refine((value) => splitTags(value).length <= 20, 'Use at most 20 tags'),
  specifications: z.array(z.object({
    name: z.string().trim().max(80, 'Too long'),
    value: z.string().trim().max(255, 'Too long'),
  })).max(50),
  variants: z.array(variantSchema).min(1, 'Add at least one variant').max(50),
}).superRefine((values, context) => {
  const seen = new Set<string>();
  values.variants.forEach((variant, index) => {
    const key = variant.sku.trim().toUpperCase();
    if (seen.has(key)) {
      context.addIssue({ code: 'custom', path: ['variants', index, 'sku'], message: 'Each variant needs a unique SKU' });
    }
    seen.add(key);
  });
  values.specifications.forEach((spec, index) => {
    if (Boolean(spec.name) !== Boolean(spec.value)) {
      context.addIssue({ code: 'custom', path: ['specifications', index, spec.name ? 'value' : 'name'], message: 'Fill in both fields or remove the row' });
    }
  });
});

export type ProductFormValues = z.input<typeof productSchema>;
export type ProductFormOutput = z.output<typeof productSchema>;

export function emptyVariant(): ProductFormValues['variants'][number] {
  return { variantId: null, sku: '', name: 'Standard', attributes: '', priceOverride: '', active: true, initialStock: '0', lowStockThreshold: '' };
}

export function toFormValues(product: AdminProduct | null): ProductFormValues {
  if (!product) {
    return {
      name: '', slug: '', brandId: '', categoryId: '', shortDescription: '', description: '', price: '', discountPercent: '0',
      status: 'DRAFT', featured: false, tags: '', specifications: [{ name: '', value: '' }], variants: [emptyVariant()],
    };
  }
  return {
    name: product.name,
    slug: product.slug,
    brandId: String(product.brand.id),
    categoryId: String(product.category.id),
    shortDescription: product.shortDescription ?? '',
    description: product.description,
    price: String(product.price),
    discountPercent: String(product.discountPercent),
    status: product.status,
    featured: product.featured,
    tags: product.tags.join(', '),
    specifications: product.specifications.length ? product.specifications.map((spec) => ({ ...spec })) : [{ name: '', value: '' }],
    variants: product.variants.map((variant) => ({
      variantId: variant.id,
      sku: variant.sku,
      name: variant.name,
      attributes: formatAttributes(variant.attributes),
      priceOverride: variant.priceOverride === null ? '' : String(variant.priceOverride),
      active: variant.active,
      initialStock: '',
      lowStockThreshold: String(variant.lowStockThreshold),
    })),
  };
}

export function toProductInput(values: ProductFormOutput): ProductInput {
  return {
    name: values.name,
    slug: emptyToNull(values.slug),
    brandId: values.brandId,
    categoryId: values.categoryId,
    shortDescription: emptyToNull(values.shortDescription),
    description: values.description,
    price: values.price,
    discountPercent: values.discountPercent,
    status: values.status,
    featured: values.featured,
    tags: splitTags(values.tags),
    specifications: values.specifications.filter((spec) => spec.name && spec.value),
    variants: values.variants.map((variant) => ({
      id: variant.variantId,
      sku: variant.sku,
      name: variant.name,
      attributes: parseAttributes(variant.attributes) ?? {},
      priceOverride: variant.priceOverride ?? null,
      active: variant.active,
      // Stock of existing variants changes only through inventory adjustments (with an audit trail).
      initialStock: variant.variantId === null ? variant.initialStock ?? 0 : null,
      lowStockThreshold: variant.lowStockThreshold ?? null,
    })),
  };
}
