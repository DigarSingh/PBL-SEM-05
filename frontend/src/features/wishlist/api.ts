import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { toast } from 'sonner';
import { useSession } from '@/features/auth/useSession';
import { apiDelete, apiGet, apiPost } from '@/lib/api';
import { errorMessage } from '@/lib/errors';
import { loginPath } from '@/lib/navigation';
import type { Wishlist } from '@/lib/types';

export const WISHLIST_KEY = ['me', 'wishlist'] as const;
export const WISHLIST_IDS_KEY = ['me', 'wishlist', 'ids'] as const;

export const wishlistApi = {
  get: () => apiGet<Wishlist>('/wishlist'),
  ids: () => apiGet<number[]>('/wishlist/ids'),
  add: (productId: number) => apiPost<Wishlist>('/wishlist/items', { productId }),
  remove: (productId: number) => apiDelete<Wishlist>(`/wishlist/items/${productId}`),
  moveToCart: (productId: number) => apiPost<Wishlist>(`/wishlist/items/${productId}/move-to-cart`),
};

export function useWishlist() {
  const { status } = useSession();
  return useQuery({ queryKey: WISHLIST_KEY, queryFn: wishlistApi.get, enabled: status === 'authenticated' });
}

export function useWishlistIds(): Set<number> {
  const { status } = useSession();
  const { data } = useQuery({
    queryKey: WISHLIST_IDS_KEY,
    queryFn: wishlistApi.ids,
    enabled: status === 'authenticated',
    staleTime: 60_000,
  });
  return useMemo(() => new Set(data ?? []), [data]);
}

/** Saves or removes a product, updating the heart icons immediately and rolling back on failure. */
export function useToggleWishlist() {
  const { status } = useSession();
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: ({ productId, saved }: { productId: number; saved: boolean }) =>
      saved ? wishlistApi.remove(productId) : wishlistApi.add(productId),
    onMutate: async ({ productId, saved }) => {
      await queryClient.cancelQueries({ queryKey: WISHLIST_IDS_KEY });
      const previous = queryClient.getQueryData<number[]>(WISHLIST_IDS_KEY);
      queryClient.setQueryData<number[]>(WISHLIST_IDS_KEY, (ids = []) =>
        saved ? ids.filter((id) => id !== productId) : [...ids, productId]);
      return { previous };
    },
    onError: (error, _variables, context) => {
      queryClient.setQueryData(WISHLIST_IDS_KEY, context?.previous);
      toast.error(errorMessage(error));
    },
    onSuccess: (wishlist, { saved }) => {
      queryClient.setQueryData(WISHLIST_KEY, wishlist);
      queryClient.setQueryData(WISHLIST_IDS_KEY, wishlist.items.map((item) => item.product.id));
      toast.success(saved ? 'Removed from your wishlist' : 'Saved to your wishlist');
    },
  });

  return (productId: number, saved: boolean) => {
    if (status !== 'authenticated') {
      toast.info('Sign in to save products to your wishlist');
      navigate(loginPath(location.pathname + location.search));
      return;
    }
    mutation.mutate({ productId, saved });
  };
}
