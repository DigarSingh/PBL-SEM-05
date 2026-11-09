import { useQuery } from '@tanstack/react-query';
import { apiGet, apiPost } from '@/lib/api';
import type { Brand, Category, Facets, Page, ProductDetail, ProductSummary } from '@/lib/types';

export type ProductQuery = {
  q?: string;
  category?: string;
  brand?: string[];
  minPrice?: number;
  maxPrice?: number;
  minRating?: number;
  minDiscount?: number;
  inStock?: boolean;
  featured?: boolean;
  sort?: string;
  page?: number;
  size?: number;
};

export type FacetQuery = Pick<ProductQuery, 'q' | 'category' | 'brand' | 'minPrice' | 'maxPrice' | 'minRating' | 'inStock'>;

export const catalogApi = {
  products: (query: ProductQuery) => apiGet<Page<ProductSummary>>('/products', query),
  facets: (query: FacetQuery) => apiGet<Facets>('/products/facets', query),
  product: (slug: string) => apiGet<ProductDetail>(`/products/slug/${encodeURIComponent(slug)}`),
  productById: (productId: number) => apiGet<ProductDetail>(`/products/${productId}`),
  related: (productId: number, limit = 8) => apiGet<ProductSummary[]>(`/products/${productId}/related`, { limit }),
  recordView: (productId: number) => apiPost<void>(`/products/${productId}/views`),
  categories: () => apiGet<Category[]>('/categories'),
  category: (slug: string) => apiGet<Category>(`/categories/${encodeURIComponent(slug)}`),
  brands: () => apiGet<Brand[]>('/brands'),
  recordCategoryView: (categoryId: number) => apiPost<void>('/activity/category-views', { categoryId }),
};

export function useCategories() {
  return useQuery({ queryKey: ['categories'], queryFn: catalogApi.categories, staleTime: 5 * 60_000 });
}

export function useBrands() {
  return useQuery({ queryKey: ['brands'], queryFn: catalogApi.brands, staleTime: 5 * 60_000 });
}
