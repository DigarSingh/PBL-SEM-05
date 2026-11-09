import { useQuery } from '@tanstack/react-query';
import { adminApi } from './api';

export function useAdminCategories() {
  return useQuery({ queryKey: ['admin', 'categories'], queryFn: adminApi.categories, staleTime: 60_000 });
}

export function useAdminBrands() {
  return useQuery({ queryKey: ['admin', 'brands'], queryFn: adminApi.brands, staleTime: 60_000 });
}
