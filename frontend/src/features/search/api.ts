import { apiDelete, apiGet } from '@/lib/api';
import type { SearchSuggestions, SmartSearchResponse } from '@/lib/types';

export type SmartSearchQuery = {
  q: string;
  category?: string;
  brand?: string[];
  minPrice?: number;
  maxPrice?: number;
  minRating?: number;
  inStock?: boolean;
  sort?: string;
  page?: number;
  size?: number;
};

export const searchApi = {
  search: (query: SmartSearchQuery, signal?: AbortSignal) => apiGet<SmartSearchResponse>('/search', query, { signal }),
  suggestions: (q: string, signal?: AbortSignal) => apiGet<SearchSuggestions>('/search/suggestions', { q }, { signal }),
  clearRecent: () => apiDelete<void>('/search/recent'),
};
