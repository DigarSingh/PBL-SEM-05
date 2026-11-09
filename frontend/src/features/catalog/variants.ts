import type { Variant, VariantOption } from '@/lib/types';

export type Selection = Record<string, string>;

export function initialSelection(variants: Variant[], defaultVariantId: number | null): Selection {
  const variant = variants.find((candidate) => candidate.id === defaultVariantId)
    ?? variants.find((candidate) => candidate.inStock)
    ?? variants[0];
  return variant ? { ...variant.attributes } : {};
}

export function findVariant(variants: Variant[], options: VariantOption[], selection: Selection): Variant | undefined {
  if (options.length === 0) {
    return variants[0];
  }
  return variants.find((variant) => options.every((option) => variant.attributes[option.name] === selection[option.name]));
}

export type ValueState = 'available' | 'out-of-stock' | 'unavailable';

/** Whether a value can be combined with the other current selections, and if so whether it is in stock. */
export function valueState(variants: Variant[], options: VariantOption[], selection: Selection, optionName: string,
                           value: string): ValueState {
  const candidates = variants.filter((variant) => variant.attributes[optionName] === value
    && options.every((option) => option.name === optionName || variant.attributes[option.name] === selection[option.name]));
  if (candidates.length === 0) {
    return 'unavailable';
  }
  return candidates.some((variant) => variant.inStock) ? 'available' : 'out-of-stock';
}

/** Selects a value; if no variant combines it with the other selections, jumps to the best variant that has it. */
export function selectValue(variants: Variant[], options: VariantOption[], selection: Selection, optionName: string,
                            value: string): Selection {
  const next = { ...selection, [optionName]: value };
  if (findVariant(variants, options, next)) {
    return next;
  }
  const fallback = variants
    .filter((variant) => variant.attributes[optionName] === value)
    .sort((a, b) => Number(b.inStock) - Number(a.inStock))[0];
  return fallback ? { ...fallback.attributes } : next;
}
