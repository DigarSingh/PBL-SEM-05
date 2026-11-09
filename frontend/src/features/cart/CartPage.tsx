import { ArrowRight, ShieldCheck, ShoppingBag, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { toast } from 'sonner';
import { ProductImage } from '@/components/ProductImage';
import { Badge } from '@/components/ui/Badge';
import { Button, ButtonLink } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ConfirmDialog } from '@/components/ui/Dialog';
import { EmptyState, ErrorState, InlineAlert, Skeleton } from '@/components/ui/Feedback';
import { PageHeader } from '@/components/ui/PageHeader';
import { QuantityStepper } from '@/components/ui/QuantityStepper';
import { ProductRail } from '@/features/catalog/ProductRail';
import { useForYou } from '@/features/recommendations/api';
import { errorMessage } from '@/lib/errors';
import { formatPrice } from '@/lib/format';
import { useDocumentTitle } from '@/lib/hooks';
import type { CartItem } from '@/lib/types';
import { cartApi, useCart, useCartMutation } from './api';
import { CouponBox } from './CouponBox';
import { PriceBreakdown } from './PriceBreakdown';

export default function CartPage() {
  useDocumentTitle('Your cart');
  const cart = useCart();
  const navigate = useNavigate();
  const [confirmClear, setConfirmClear] = useState(false);
  const clear = useCartMutation(() => cartApi.clear());
  const forYou = useForYou(10);

  if (cart.isPending) {
    return (
      <div className="container-page py-8">
        <Skeleton className="mb-6 h-9 w-48" />
        <div className="grid gap-8 lg:grid-cols-[1fr_22rem]">
          <div className="space-y-3">{[0, 1, 2].map((key) => <Skeleton key={key} className="h-32 w-full" />)}</div>
          <Skeleton className="h-72 w-full" />
        </div>
      </div>
    );
  }
  if (cart.isError) {
    return <div className="container-page py-10"><ErrorState error={cart.error} onRetry={() => void cart.refetch()} /></div>;
  }

  const data = cart.data;
  if (data.items.length === 0) {
    return (
      <div className="container-page py-8">
        <PageHeader title="Your cart" breadcrumbs={[{ label: 'Home', to: '/' }, { label: 'Cart' }]} />
        <EmptyState icon={<ShoppingBag className="h-7 w-7" aria-hidden />} title="Your cart is empty"
          description="Find something you love and it will show up here." action={<ButtonLink to="/shop">Start shopping</ButtonLink>} />
        <ProductRail title="You might like" items={forYou.data?.items} loading={forYou.isPending} />
      </div>
    );
  }

  return (
    <div className="container-page py-8">
      <PageHeader title="Your cart" description={`${data.summary.itemCount} item${data.summary.itemCount === 1 ? '' : 's'}`}
        breadcrumbs={[{ label: 'Home', to: '/' }, { label: 'Cart' }]}
        actions={<Button variant="ghost" size="sm" onClick={() => setConfirmClear(true)} icon={<Trash2 className="h-4 w-4" aria-hidden />}>Clear cart</Button>} />

      <div className="grid gap-8 lg:grid-cols-[1fr_22rem]">
        <div className="space-y-3">
          {!data.canCheckout && data.issues.length > 0 && (
            <InlineAlert tone="warning">
              <p className="font-semibold">Please review your cart before checking out:</p>
              <ul className="mt-1 list-disc pl-5">{data.issues.map((issue) => <li key={issue}>{issue}</li>)}</ul>
            </InlineAlert>
          )}
          <ul className="space-y-3">
            {data.items.map((item) => <CartLine key={item.id} item={item} />)}
          </ul>
        </div>

        <aside className="space-y-4 lg:sticky lg:top-32 lg:self-start">
          <Card className="p-5">
            <h2 className="mb-4 text-base font-semibold text-slate-900">Order summary</h2>
            <PriceBreakdown quote={data.summary} couponCode={data.coupon?.valid ? data.coupon.code : null} />
            <Button size="lg" className="mt-5 w-full" disabled={!data.canCheckout} onClick={() => navigate('/checkout')}
              icon={<ArrowRight className="h-4 w-4" aria-hidden />}>
              Proceed to checkout
            </Button>
            <p className="mt-3 flex items-center justify-center gap-1.5 text-xs text-slate-500">
              <ShieldCheck className="h-4 w-4" aria-hidden />Prices and stock are confirmed again when you place the order.
            </p>
          </Card>
          <Card className="p-5">
            <h2 className="mb-3 text-sm font-semibold text-slate-900">Coupons &amp; offers</h2>
            <CouponBox cart={data} />
          </Card>
        </aside>
      </div>

      <ConfirmDialog open={confirmClear} onClose={() => setConfirmClear(false)} title="Clear your cart?"
        message="All items will be removed from your cart." confirmLabel="Clear cart" loading={clear.isPending}
        onConfirm={() => clear.mutate(undefined, {
          onSuccess: () => {
            setConfirmClear(false);
            toast.success('Your cart is now empty');
          },
          onError: (error) => toast.error(errorMessage(error)),
        })} />
    </div>
  );
}

const ISSUE_TEXT: Record<string, string> = {
  OUT_OF_STOCK: 'Out of stock. Remove it to continue.',
  UNAVAILABLE: 'No longer available. Remove it to continue.',
};

function CartLine({ item }: { item: CartItem }) {
  const update = useCartMutation((quantity: number) => cartApi.update(item.id, quantity));
  const remove = useCartMutation(() => cartApi.remove(item.id));
  const busy = update.isPending || remove.isPending;
  const attributes = Object.entries(item.attributes).map(([name, value]) => `${name}: ${value}`).join(' · ');
  const issue = item.issue === 'INSUFFICIENT_STOCK'
    ? `Only ${item.available} left. Reduce the quantity to continue.`
    : item.issue ? ISSUE_TEXT[item.issue] : null;

  return (
    <li className="flex gap-4 rounded-2xl border border-stone-200 bg-white p-4">
      <Link to={`/products/${item.productSlug}`} className="h-24 w-24 shrink-0 overflow-hidden rounded-xl sm:h-28 sm:w-28">
        <ProductImage src={item.imageUrl} alt={item.productName} className="h-full w-full" />
      </Link>
      <div className="flex min-w-0 flex-1 flex-col">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{item.brandName}</p>
            <Link to={`/products/${item.productSlug}`} className="line-clamp-2 font-semibold text-slate-900 hover:text-brand-700">{item.productName}</Link>
            {attributes && <p className="mt-0.5 text-xs text-slate-500">{attributes}</p>}
          </div>
          <div className="shrink-0 text-right">
            <p className="font-bold text-slate-900">{formatPrice(item.lineTotal)}</p>
            {item.quantity > 1 && <p className="text-xs text-slate-500">{formatPrice(item.unitPrice)} each</p>}
            {item.unitMrp > item.unitPrice && <p className="text-xs text-slate-400 line-through">{formatPrice(item.unitMrp * item.quantity)}</p>}
          </div>
        </div>
        {issue && <p className="mt-2"><Badge tone="danger">{issue}</Badge></p>}
        {!issue && item.stockStatus === 'LOW_STOCK' && <p className="mt-2"><Badge tone="warning">Only {item.available} left</Badge></p>}
        <div className="mt-auto flex items-center justify-between gap-3 pt-3">
          <QuantityStepper size="sm" value={item.quantity} min={1} max={Math.max(item.maxQuantity, item.quantity)}
            disabled={busy || item.issue === 'OUT_OF_STOCK' || item.issue === 'UNAVAILABLE'}
            onChange={(quantity) => update.mutate(quantity, { onError: (error) => toast.error(errorMessage(error)) })} />
          <Button variant="ghost" size="sm" loading={remove.isPending} disabled={busy}
            onClick={() => remove.mutate(undefined, { onError: (error) => toast.error(errorMessage(error)) })}
            icon={<Trash2 className="h-4 w-4" aria-hidden />}>
            Remove
          </Button>
        </div>
      </div>
    </li>
  );
}
