import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Banknote, CreditCard, Lock, MapPin, Plus, ShieldCheck } from 'lucide-react';
import { useRef, useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router';
import { ProductImage } from '@/components/ProductImage';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorState, InlineAlert, Skeleton, Spinner } from '@/components/ui/Feedback';
import { Textarea } from '@/components/ui/Field';
import { PageHeader } from '@/components/ui/PageHeader';
import { AddressLines } from '@/features/account/AddressCard';
import { AddressFormModal } from '@/features/account/AddressFormModal';
import { useAddresses } from '@/features/account/api';
import { useSession } from '@/features/auth/useSession';
import { CART_KEY, useCart } from '@/features/cart/api';
import { PriceBreakdown } from '@/features/cart/PriceBreakdown';
import { ordersApi, orderKey } from '@/features/orders/api';
import { useRazorpayPayment } from '@/features/orders/useRazorpayPayment';
import { usePublicSettings } from '@/features/settings/api';
import { cn } from '@/lib/cn';
import { toApiError } from '@/lib/errors';
import { formatPrice } from '@/lib/format';
import { useDocumentTitle, useOnlineStatus } from '@/lib/hooks';
import { newIdempotencyKey } from '@/lib/idempotency';
import type { Cart, PaymentMethod } from '@/lib/types';
import { defaultPaymentMethod, paymentOptions } from './paymentOptions';

const METHOD_COPY: Record<PaymentMethod, { title: string; description: string; icon: typeof CreditCard }> = {
  RAZORPAY: { title: 'Pay online', description: 'UPI, cards, net banking and wallets via Razorpay', icon: CreditCard },
  COD: { title: 'Cash on Delivery', description: 'Pay in cash or UPI when your order arrives', icon: Banknote },
};

function fingerprint(cart: Cart, addressId: number | null, method: PaymentMethod | null, note: string): string {
  const items = cart.items.map((item) => `${item.variantId}x${item.quantity}`).join(',');
  return [items, cart.coupon?.code ?? '', addressId ?? '', method ?? '', note.trim()].join('|');
}

export default function CheckoutPage() {
  useDocumentTitle('Checkout');
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const online = useOnlineStatus();
  const { user } = useSession();
  const cart = useCart();
  const addresses = useAddresses();
  const settings = usePublicSettings();
  const { pay, step } = useRazorpayPayment();
  const [chosenAddressId, setChosenAddressId] = useState<number | null>(null);
  const [chosenMethod, setChosenMethod] = useState<PaymentMethod | null>(null);
  const [note, setNote] = useState('');
  const [addressFormOpen, setAddressFormOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [placedOrderId, setPlacedOrderId] = useState<number | null>(null);
  // One idempotency key per checkout attempt: resubmitting the same attempt cannot create a second order.
  const attempt = useRef<{ fingerprint: string; key: string } | null>(null);

  const placeOrder = useMutation({
    mutationFn: ({ key, addressId, method }: { key: string; addressId: number; method: PaymentMethod }) =>
      ordersApi.checkout({ addressId, paymentMethod: method, customerNote: note.trim() || null }, key),
    onSuccess: async (response) => {
      attempt.current = null;
      queryClient.setQueryData(orderKey(response.order.id), response.order);
      if (response.razorpay) {
        await pay(response.razorpay);
        return;
      }
      setPlacedOrderId(response.order.id);
      navigate(`/orders/${response.order.id}?placed=1`, { replace: true });
      void queryClient.invalidateQueries({ queryKey: CART_KEY });
      void queryClient.invalidateQueries({ queryKey: ['me', 'orders'] });
    },
    onError: (failure) => {
      const apiError = toApiError(failure);
      if (['INSUFFICIENT_STOCK', 'INVALID_COUPON', 'CONFLICT', 'BUSINESS_RULE_VIOLATION'].includes(apiError.code)) {
        void queryClient.invalidateQueries({ queryKey: CART_KEY });
      }
      if (apiError.status !== 0) {
        attempt.current = null; // the server answered: the next click is a new attempt
      }
      setError(apiError.message);
    },
  });

  if (cart.isPending || addresses.isPending || settings.isPending) {
    return (
      <div className="container-page py-8">
        <Skeleton className="mb-6 h-9 w-40" />
        <div className="grid gap-8 lg:grid-cols-[1fr_24rem]">
          <div className="space-y-4"><Skeleton className="h-48 w-full" /><Skeleton className="h-40 w-full" /></div>
          <Skeleton className="h-80 w-full" />
        </div>
      </div>
    );
  }
  if (cart.isError || addresses.isError || settings.isError) {
    const failed = cart.error ?? addresses.error ?? settings.error;
    return (
      <div className="container-page py-10">
        <ErrorState error={failed} onRetry={() => {
          void cart.refetch();
          void addresses.refetch();
          void settings.refetch();
        }} />
      </div>
    );
  }
  if (placedOrderId !== null || step === 'done') {
    // The order exists and the order page is loading; the cart is legitimately empty now.
    return <Spinner className="py-32" label="Opening your order" />;
  }
  if (cart.data.items.length === 0 && !placeOrder.isPending && step === 'idle') {
    return <Navigate to="/cart" replace />;
  }

  const data = cart.data;
  const addressList = addresses.data;
  const addressId = chosenAddressId ?? addressList.find((address) => address.isDefault)?.id ?? addressList[0]?.id ?? null;
  const options = paymentOptions(settings.data, data.summary.total);
  const method = chosenMethod && options.some((option) => option.method === chosenMethod && option.available)
    ? chosenMethod : defaultPaymentMethod(options);
  const testMode = settings.data.razorpayKeyId?.startsWith('rzp_test_') ?? false;
  const busy = placeOrder.isPending || step !== 'idle';

  const submit = () => {
    if (!addressId || !method) {
      return;
    }
    setError(null);
    const current = fingerprint(data, addressId, method, note);
    if (!attempt.current || attempt.current.fingerprint !== current) {
      attempt.current = { fingerprint: current, key: newIdempotencyKey() };
    }
    placeOrder.mutate({ key: attempt.current.key, addressId, method });
  };

  return (
    <div className="container-page py-8">
      <PageHeader title="Checkout" breadcrumbs={[{ label: 'Cart', to: '/cart' }, { label: 'Checkout' }]} />

      <div className="grid gap-8 lg:grid-cols-[1fr_24rem]">
        <div className="space-y-6">
          <Card className="p-5">
            <div className="mb-4 flex items-center justify-between gap-3">
              <h2 className="flex items-center gap-2 text-base font-semibold text-slate-900">
                <MapPin className="h-5 w-5 text-brand-600" aria-hidden />Delivery address
              </h2>
              <Button variant="secondary" size="sm" icon={<Plus className="h-4 w-4" aria-hidden />} onClick={() => setAddressFormOpen(true)}>
                Add address
              </Button>
            </div>
            {addressList.length === 0 ? (
              <InlineAlert tone="info">Add a delivery address to continue.</InlineAlert>
            ) : (
              <fieldset>
                <legend className="sr-only">Choose a delivery address</legend>
                <div className="grid gap-3 sm:grid-cols-2">
                  {addressList.map((address) => (
                    <label key={address.id} className={cn('relative flex cursor-pointer gap-3 rounded-2xl border p-4 transition-colors',
                      addressId === address.id ? 'border-ink ring-1 ring-ink' : 'border-stone-200 hover:border-stone-400')}>
                      <input type="radio" name="address" className="mt-1 h-4 w-4 accent-slate-900" checked={addressId === address.id}
                        onChange={() => setChosenAddressId(address.id)} />
                      <span className="min-w-0">
                        {(address.label || address.isDefault) && (
                          <span className="mb-1 flex gap-1.5">
                            {address.label && <Badge tone="neutral">{address.label}</Badge>}
                            {address.isDefault && <Badge tone="brand">Default</Badge>}
                          </span>
                        )}
                        <AddressLines address={address} />
                      </span>
                    </label>
                  ))}
                </div>
              </fieldset>
            )}
          </Card>

          <Card className="p-5">
            <h2 className="mb-4 flex items-center gap-2 text-base font-semibold text-slate-900">
              <CreditCard className="h-5 w-5 text-brand-600" aria-hidden />Payment method
              {testMode && <Badge tone="warning">Razorpay test mode</Badge>}
            </h2>
            <fieldset className="space-y-3">
              <legend className="sr-only">Choose a payment method</legend>
              {options.map((option) => {
                const copy = METHOD_COPY[option.method];
                const Icon = copy.icon;
                return (
                  <label key={option.method} className={cn('flex gap-3 rounded-2xl border p-4 transition-colors',
                    !option.available ? 'cursor-not-allowed border-stone-200 bg-stone-50 opacity-70'
                      : method === option.method ? 'cursor-pointer border-ink ring-1 ring-ink' : 'cursor-pointer border-stone-200 hover:border-stone-400')}>
                    <input type="radio" name="payment" className="mt-1 h-4 w-4 accent-slate-900" disabled={!option.available}
                      checked={method === option.method} onChange={() => setChosenMethod(option.method)} />
                    <Icon className="mt-0.5 h-5 w-5 shrink-0 text-slate-500" aria-hidden />
                    <span>
                      <span className="block text-sm font-semibold text-slate-900">{copy.title}</span>
                      <span className="block text-sm text-slate-500">{option.reason ?? copy.description}</span>
                    </span>
                  </label>
                );
              })}
            </fieldset>
            {options.every((option) => !option.available) && (
              <InlineAlert tone="danger" className="mt-3">No payment method is available for this order right now.</InlineAlert>
            )}
          </Card>

          <Card className="p-5">
            <label htmlFor="order-note" className="mb-2 block text-sm font-semibold text-slate-900">Delivery instructions <span className="font-normal text-slate-500">(optional)</span></label>
            <Textarea id="order-note" rows={2} maxLength={500} value={note} onChange={(event) => setNote(event.target.value)}
              placeholder="e.g. Please call before delivery" />
          </Card>
        </div>

        <aside className="space-y-4 lg:sticky lg:top-32 lg:self-start">
          <Card className="p-5">
            <h2 className="mb-4 text-base font-semibold text-slate-900">Order summary</h2>
            <ul className="mb-4 max-h-64 space-y-3 overflow-y-auto pr-1">
              {data.items.map((item) => (
                <li key={item.id} className="flex gap-3">
                  <span className="relative h-14 w-14 shrink-0 overflow-hidden rounded-lg border border-stone-200">
                    <ProductImage src={item.imageUrl} alt="" className="h-full w-full" />
                    <span className="absolute -right-0 -top-0 rounded-bl-lg bg-ink px-1.5 text-[10px] font-bold text-white">{item.quantity}</span>
                  </span>
                  <span className="min-w-0 flex-1 text-sm">
                    <span className="line-clamp-1 font-medium text-slate-900">{item.productName}</span>
                    <span className="block text-xs text-slate-500">{item.variantName}</span>
                  </span>
                  <span className="text-sm font-semibold text-slate-900">{formatPrice(item.lineTotal)}</span>
                </li>
              ))}
            </ul>
            <PriceBreakdown quote={data.summary} couponCode={data.coupon?.valid ? data.coupon.code : null} />

            {!data.canCheckout && (
              <InlineAlert tone="warning" className="mt-4">
                Some items need attention. <Link to="/cart" className="font-semibold underline">Review your cart</Link>.
              </InlineAlert>
            )}
            {error && <InlineAlert tone="danger" className="mt-4">{error}</InlineAlert>}
            {!online && <InlineAlert tone="warning" className="mt-4">You&apos;re offline. Reconnect to place your order.</InlineAlert>}

            <Button size="lg" className="mt-5 w-full" loading={busy} icon={<Lock className="h-4 w-4" aria-hidden />}
              disabled={!data.canCheckout || !addressId || !method || !online} onClick={submit}>
              {method === 'COD' ? `Place order · ${formatPrice(data.summary.total)}` : `Pay ${formatPrice(data.summary.total)}`}
            </Button>
            {step === 'verifying' && <Spinner className="py-3" label="Confirming your payment" />}
            <p className="mt-3 flex items-start gap-1.5 text-xs text-slate-500">
              <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
              Prices, stock and discounts are confirmed by our servers when you place the order. Card and UPI details are handled by Razorpay and never reach ShopSmart.
            </p>
          </Card>
        </aside>
      </div>

      {addressFormOpen && (
        <AddressFormModal open={addressFormOpen} onClose={() => setAddressFormOpen(false)}
          defaultName={user?.fullName} defaultPhone={user?.phone}
          onSaved={(address) => setChosenAddressId(address.id)} />
      )}
    </div>
  );
}
