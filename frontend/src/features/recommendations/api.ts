import { useQuery } from '@tanstack/react-query';
import { useSession } from '@/features/auth/useSession';
import { apiGet } from '@/lib/api';
import type { Recommendations } from '@/lib/types';

export const recommendationsApi = {
  forYou: (limit: number) => apiGet<Recommendations>('/recommendations/for-you', { limit }),
  similar: (productId: number, limit: number) => apiGet<Recommendations>(`/recommendations/similar/${productId}`, { limit }),
  trending: (limit: number) => apiGet<Recommendations>('/recommendations/trending', { limit }),
};

/** Personalised picks for the signed-in shopper (trending for guests). Waits until the session is known. */
export function useForYou(limit = 12) {
  const { status, user } = useSession();
  return useQuery({
    queryKey: ['recommendations', 'for-you', user?.id ?? 'guest', limit],
    queryFn: () => recommendationsApi.forYou(limit),
    enabled: status !== 'loading',
    staleTime: 60_000,
  });
}

export function useTrending(limit = 12) {
  return useQuery({
    queryKey: ['recommendations', 'trending', limit],
    queryFn: () => recommendationsApi.trending(limit),
    staleTime: 5 * 60_000,
  });
}

export function useSimilar(productId: number, limit = 8) {
  return useQuery({
    queryKey: ['recommendations', 'similar', productId, limit],
    queryFn: () => recommendationsApi.similar(productId, limit),
    staleTime: 5 * 60_000,
  });
}
