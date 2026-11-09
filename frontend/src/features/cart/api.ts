import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocation, useNavigate } from 'react-router';
import { toast } from 'sonner';
import { useSession } from '@/features/auth/useSession';
import { apiDelete, apiGet, apiPatch, apiPost } from '@/lib/api';
import { errorMessage } from '@/lib/errors';
import { loginPath } from '@/lib/navigation';
import type { Cart, Offer } from '@/lib/types';

export const CART_KEY = ['me', 'cart'] as const;

export const cartApi = {
  get: () => apiGet<Cart>('/cart'),
  add: (variantId: number, quantity: number) => apiPost<Cart>('/cart/items', { variantId, quantity }),
  update: (itemId: number, quantity: number) => apiPatch<Cart>(`/cart/items/${itemId}`, { quantity }),
  remove: (itemId: number) => apiDelete<Cart>(`/cart/items/${itemId}`),
  clear: () => apiDelete<Cart>('/cart'),
  applyCoupon: (code: string) => apiPost<Cart>('/cart/coupon', { code }),
  removeCoupon: () => apiDelete<Cart>('/cart/coupon'),
  offers: () => apiGet<Offer[]>('/coupons'),
};

export function useCart() {
  const { status } = useSession();
  return useQuery({ queryKey: CART_KEY, queryFn: cartApi.get, enabled: status === 'authenticated' });
}

export function useCartCount(): number {
  const { data } = useCart();
  return data ? data.items.reduce((sum, item) => sum + item.quantity, 0) : 0;
}

export function useOffers() {
  return useQuery({ queryKey: ['offers'], queryFn: cartApi.offers, staleTime: 5 * 60_000 });
}

/** Cart mutations answer with the updated cart, which replaces the cached copy. */
export function useCartMutation<TVariables>(mutationFn: (variables: TVariables) => Promise<Cart>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: (cart) => queryClient.setQueryData(CART_KEY, cart),
  });
}

interface AddVariables {
  variantId: number;
  quantity: number;
  productName: string;
  goToCheckout: boolean;
}

export function useAddToCart() {
  const { status } = useSession();
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: ({ variantId, quantity }: AddVariables) => cartApi.add(variantId, quantity),
    onSuccess: (cart, { productName, goToCheckout }) => {
      queryClient.setQueryData(CART_KEY, cart);
      if (goToCheckout) {
        navigate('/checkout');
        return;
      }
      toast.success(`${productName} added to your cart`, {
        action: { label: 'View cart', onClick: () => navigate('/cart') },
      });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const addToCart = (variantId: number, productName: string, quantity = 1, options: { goToCheckout?: boolean } = {}) => {
    if (status !== 'authenticated') {
      toast.info('Sign in to add items to your cart');
      navigate(loginPath(location.pathname + location.search));
      return;
    }
    mutation.mutate({ variantId, quantity, productName, goToCheckout: Boolean(options.goToCheckout) });
  };

  return {
    addToCart,
    isPending: mutation.isPending,
    pendingVariantId: mutation.isPending ? mutation.variables?.variantId : undefined,
  };
}
