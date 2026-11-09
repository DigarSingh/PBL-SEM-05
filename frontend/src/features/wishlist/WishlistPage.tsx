import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Heart, ShoppingBag, Trash2 } from 'lucide-react';
import { Link } from 'react-router';
import { toast } from 'sonner';
import { ProductImage } from '@/components/ProductImage';
import { Badge } from '@/components/ui/Badge';
import { Button, ButtonLink } from '@/components/ui/Button';
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/Feedback';
import { PageHeader } from '@/components/ui/PageHeader';
import { Price } from '@/components/ui/Price';
import { CART_KEY } from '@/features/cart/api';
import { errorMessage } from '@/lib/errors';
import { formatDate } from '@/lib/format';
import { useDocumentTitle } from '@/lib/hooks';
import { useToggleWishlist, useWishlist, WISHLIST_IDS_KEY, WISHLIST_KEY, wishlistApi } from './api';

export default function WishlistPage() {
  useDocumentTitle('Wishlist');
  const wishlist = useWishlist();
  const queryClient = useQueryClient();
  const toggle = useToggleWishlist();
  const moveToCart = useMutation({
    mutationFn: ({ productId }: { productId: number; name: string }) => wishlistApi.moveToCart(productId),
    onSuccess: (updated, { name }) => {
      queryClient.setQueryData(WISHLIST_KEY, updated);
      queryClient.setQueryData(WISHLIST_IDS_KEY, updated.items.map((item) => item.product.id));
      void queryClient.invalidateQueries({ queryKey: CART_KEY });
      toast.success(`${name} moved to your cart`);
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <div className="container-page py-8">
      <PageHeader title="Your wishlist" description="Products you've saved for later."
        breadcrumbs={[{ label: 'Home', to: '/' }, { label: 'Wishlist' }]} />
      {wishlist.isPending ? (
        <div className="space-y-3">{[0, 1, 2].map((key) => <Skeleton key={key} className="h-28 w-full" />)}</div>
      ) : wishlist.isError ? (
        <ErrorState error={wishlist.error} onRetry={() => void wishlist.refetch()} />
      ) : wishlist.data.items.length === 0 ? (
        <EmptyState icon={<Heart className="h-7 w-7" aria-hidden />} title="Your wishlist is empty"
          description="Tap the heart on any product to save it here." action={<ButtonLink to="/shop">Browse products</ButtonLink>} />
      ) : (
        <ul className="space-y-3">
          {wishlist.data.items.map(({ product, addedAt }) => (
            <li key={product.id} className="flex gap-4 rounded-2xl border border-stone-200 bg-white p-4">
              <Link to={`/products/${product.slug}`} className="h-24 w-24 shrink-0 overflow-hidden rounded-xl">
                <ProductImage src={product.thumbnailUrl ?? product.imageUrl} alt={product.name} className="h-full w-full" />
              </Link>
              <div className="flex min-w-0 flex-1 flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                <div className="min-w-0">
                  <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{product.brand.name}</p>
                  <Link to={`/products/${product.slug}`} className="line-clamp-2 font-semibold text-slate-900 hover:text-brand-700">{product.name}</Link>
                  <div className="mt-1 flex flex-wrap items-center gap-2">
                    <Price salePrice={product.salePrice} price={product.price} size="sm" />
                    {!product.inStock && <Badge tone="danger">Out of stock</Badge>}
                  </div>
                  <p className="mt-1 text-xs text-slate-400">Saved {formatDate(addedAt)}</p>
                </div>
                <div className="flex shrink-0 gap-2">
                  <Button size="sm" disabled={!product.inStock} icon={<ShoppingBag className="h-4 w-4" aria-hidden />}
                    loading={moveToCart.isPending && moveToCart.variables?.productId === product.id}
                    onClick={() => moveToCart.mutate({ productId: product.id, name: product.name })}>
                    Move to cart
                  </Button>
                  <Button size="sm" variant="ghost" aria-label={`Remove ${product.name} from wishlist`} onClick={() => toggle(product.id, true)}>
                    <Trash2 className="h-4 w-4" aria-hidden />
                  </Button>
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
