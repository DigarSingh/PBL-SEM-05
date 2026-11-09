import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Clock, CreditCard, PartyPopper, Radio, RefreshCw, Star, XCircle } from 'lucide-react';
import { useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router';
import { toast } from 'sonner';
import { ProductImage } from '@/components/ProductImage';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ConfirmDialog } from '@/components/ui/Dialog';
import { ErrorState, InlineAlert, Skeleton } from '@/components/ui/Feedback';
import { Breadcrumbs } from '@/components/ui/PageHeader';
import { AddressLines } from '@/features/account/AddressCard';
import { cn } from '@/lib/cn';
import { errorMessage, toApiError } from '@/lib/errors';
import { formatDate, formatDateTime, formatPrice, orderStatusMeta, paymentMethodLabel } from '@/lib/format';
import { useDocumentTitle, useRealtimeStatus } from '@/lib/hooks';
import type { OrderDetail } from '@/lib/types';
import { useNow } from '@/lib/useNow';
import NotFoundPage from '@/pages/NotFoundPage';
import { orderKey, ordersApi, useOrder } from './api';
import { OrderStatusBadge, PaymentBadge } from './OrderBadges';
import { OrderTimeline } from './OrderTimeline';
import { useRazorpayPayment } from './useRazorpayPayment';

export default function OrderDetailPage() {
  const { orderId } = useParams();
  const id = Number(orderId);
  const order = useOrder(id);
  useDocumentTitle(order.data ? `Order ${order.data.orderNumber}` : 'Order');

  if (!Number.isFinite(id)) {
    return <NotFoundPage />;
  }
  if (order.isPending) {
    return <div className="space-y-4"><Skeleton className="h-10 w-72" /><Skeleton className="h-64 w-full" /><Skeleton className="h-48 w-full" /></div>;
  }
  if (order.isError) {
    return toApiError(order.error).status === 404
      ? <NotFoundPage />
      : <ErrorState error={order.error} onRetry={() => void order.refetch()} />;
  }
  return <OrderView order={order.data} />;
}

function OrderView({ order }: { order: OrderDetail }) {
  const queryClient = useQueryClient();
  const [params] = useSearchParams();
  const realtimeStatus = useRealtimeStatus();
  const { pay, busy: paying } = useRazorpayPayment();
  const [confirmCancel, setConfirmCancel] = useState(false);

  const cancel = useMutation({
    mutationFn: (reason: string) => ordersApi.cancel(order.id, reason || null),
    onSuccess: async (updated) => {
      queryClient.setQueryData(orderKey(order.id), updated);
      await queryClient.invalidateQueries({ queryKey: ['me', 'orders'] });
      setConfirmCancel(false);
      toast.success(`Order ${updated.orderNumber} cancelled`);
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  const startPayment = useMutation({
    mutationFn: () => ordersApi.pay(order.id),
    onSuccess: (checkout) => pay(checkout),
    onError: async (error) => {
      toast.error(errorMessage(error));
      await queryClient.invalidateQueries({ queryKey: orderKey(order.id) });
    },
  });

  const justPlaced = params.get('placed') === '1';
  const payment = order.payment;
  const cancelled = order.status === 'CANCELLED';

  return (
    <div>
      <Breadcrumbs className="mb-4" items={[{ label: 'Orders', to: '/orders' }, { label: order.orderNumber }]} />
      <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">Order {order.orderNumber}</h1>
          <p className="mt-1 text-sm text-slate-500">Ordered {formatDateTime(order.placedAt ?? order.createdAt)}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <OrderStatusBadge status={order.status} />
          <PaymentBadge method={order.paymentMethod} status={payment?.status ?? null} />
          <span className={cn('inline-flex items-center gap-1 text-xs', realtimeStatus === 'connected' ? 'text-emerald-700' : 'text-slate-500')}>
            {realtimeStatus === 'connected'
              ? <><Radio className="h-3.5 w-3.5" aria-hidden />Live updates on</>
              : <><RefreshCw className="h-3.5 w-3.5" aria-hidden />Refreshing automatically</>}
          </span>
        </div>
      </div>

      {justPlaced && order.status !== 'PENDING_PAYMENT' && !cancelled && (
        <InlineAlert tone="success" className="mb-6 flex items-start gap-3">
          <PartyPopper className="mt-0.5 h-5 w-5 shrink-0" aria-hidden />
          <span>
            <strong className="block">Thank you! Your order has been placed.</strong>
            We&apos;ve emailed your confirmation and will notify you here as it moves along.
            {order.estimatedDeliveryDate && <> Estimated delivery: <strong>{formatDate(order.estimatedDeliveryDate)}</strong>.</>}
          </span>
        </InlineAlert>
      )}

      {order.status === 'PENDING_PAYMENT' && (
        <PendingPayment order={order} paying={paying || startPayment.isPending} onPay={() => startPayment.mutate()} />
      )}

      {cancelled && (
        <InlineAlert tone="danger" className="mb-6">
          <strong>This order was cancelled</strong>{order.cancelledAt ? ` on ${formatDateTime(order.cancelledAt)}` : ''}.
          {order.cancellationReason && <> Reason: {order.cancellationReason}.</>}
          {payment?.status === 'REFUNDED' && <> A refund of {formatPrice(payment.refundedAmount ?? payment.amount)} was issued to your original payment method.</>}
          {payment?.refundPending && <> Your refund is being processed.</>}
        </InlineAlert>
      )}

      <div className="grid gap-6 lg:grid-cols-[1fr_20rem]">
        <div className="space-y-6">
          {!cancelled && order.status !== 'PENDING_PAYMENT' && (
            <Card className="p-5">
              <div className="mb-4 flex items-center justify-between">
                <h2 className="text-base font-semibold text-slate-900">Tracking</h2>
                {order.estimatedDeliveryDate && order.status !== 'DELIVERED' && (
                  <span className="text-sm text-slate-500">Expected by <strong className="text-slate-900">{formatDate(order.estimatedDeliveryDate)}</strong></span>
                )}
              </div>
              <OrderTimeline steps={order.timeline} cancelled={cancelled} />
            </Card>
          )}

          <Card>
            <h2 className="border-b border-stone-100 px-5 py-4 text-base font-semibold text-slate-900">Items ({order.items.length})</h2>
            <ul className="divide-y divide-stone-100">
              {order.items.map((item) => (
                <li key={item.id} className="flex gap-4 px-5 py-4">
                  <span className="h-16 w-16 shrink-0 overflow-hidden rounded-xl border border-stone-200">
                    <ProductImage src={item.imageUrl} alt="" className="h-full w-full" />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block font-medium text-slate-900">{item.productName}</span>
                    <span className="block text-xs text-slate-500">{item.variantName} · SKU {item.sku}</span>
                    <span className="mt-1 block text-sm text-slate-600">{item.quantity} × {formatPrice(item.unitPrice)}</span>
                    {order.status === 'DELIVERED' && (
                      <Link to={`/products/${item.productId}`} className="mt-1 inline-flex items-center gap-1 text-xs font-semibold text-brand-700 hover:underline">
                        <Star className="h-3.5 w-3.5" aria-hidden />Write a review
                      </Link>
                    )}
                  </span>
                  <span className="text-right text-sm font-semibold text-slate-900">{formatPrice(item.lineTotal)}</span>
                </li>
              ))}
            </ul>
          </Card>

          {order.history.length > 0 && (
            <Card className="p-5">
              <h2 className="mb-3 text-base font-semibold text-slate-900">Order history</h2>
              <ul className="space-y-2 text-sm">
                {[...order.history].reverse().map((entry, index) => (
                  <li key={`${entry.at}-${index}`} className="flex flex-wrap justify-between gap-x-4 gap-y-0.5">
                    <span className="text-slate-700">
                      <strong>{orderStatusMeta[entry.toStatus].label}</strong>
                      {entry.note && <span className="text-slate-500"> · {entry.note}</span>}
                    </span>
                    <span className="text-slate-400">{formatDateTime(entry.at)}</span>
                  </li>
                ))}
              </ul>
            </Card>
          )}
        </div>

        <aside className="space-y-4">
          <Card className="p-5">
            <h2 className="mb-3 text-sm font-semibold text-slate-900">Payment</h2>
            <dl className="space-y-2 text-sm">
              <div className="flex justify-between gap-3"><dt className="text-slate-500">Items (MRP)</dt><dd>{formatPrice(order.mrpTotal)}</dd></div>
              {order.mrpTotal > order.subtotal && (
                <div className="flex justify-between gap-3"><dt className="text-slate-500">Discount</dt><dd className="text-emerald-700">− {formatPrice(order.mrpTotal - order.subtotal)}</dd></div>
              )}
              {order.couponDiscount > 0 && (
                <div className="flex justify-between gap-3"><dt className="text-slate-500">Coupon {order.couponCode}</dt><dd className="text-emerald-700">− {formatPrice(order.couponDiscount)}</dd></div>
              )}
              <div className="flex justify-between gap-3"><dt className="text-slate-500">Delivery</dt><dd>{order.shippingFee === 0 ? 'Free' : formatPrice(order.shippingFee)}</dd></div>
              {!order.taxInclusive && order.taxAmount > 0 && (
                <div className="flex justify-between gap-3"><dt className="text-slate-500">GST</dt><dd>{formatPrice(order.taxAmount)}</dd></div>
              )}
              <div className="flex justify-between gap-3 border-t border-stone-200 pt-2 text-base font-bold text-slate-900"><dt>Total</dt><dd>{formatPrice(order.totalAmount)}</dd></div>
              {order.taxInclusive && order.taxAmount > 0 && <p className="text-xs text-slate-500">Includes {formatPrice(order.taxAmount)} GST.</p>}
            </dl>
            <div className="mt-4 space-y-1 border-t border-stone-100 pt-3 text-xs text-slate-500">
              <p className="flex items-center gap-1.5 text-sm text-slate-700"><CreditCard className="h-4 w-4" aria-hidden />{paymentMethodLabel(order.paymentMethod)}{payment?.providerMethod ? ` · ${payment.providerMethod.toUpperCase()}` : ''}</p>
              {payment?.paidAt && <p>Paid {formatDateTime(payment.paidAt)}</p>}
              {payment?.razorpayPaymentId && <p className="break-all">Payment ID {payment.razorpayPaymentId}</p>}
            </div>
          </Card>

          <Card className="p-5">
            <h2 className="mb-3 text-sm font-semibold text-slate-900">Delivery address</h2>
            <AddressLines address={order.shippingAddress} />
            {order.customerNote && <p className="mt-3 rounded-lg bg-stone-50 p-2 text-xs text-slate-600">Note: {order.customerNote}</p>}
          </Card>

          {order.canCancel && (
            <Button variant="secondary" className="w-full text-rose-700" icon={<XCircle className="h-4 w-4" aria-hidden />} onClick={() => setConfirmCancel(true)}>
              Cancel order
            </Button>
          )}
        </aside>
      </div>

      <ConfirmDialog open={confirmCancel} onClose={() => setConfirmCancel(false)} title="Cancel this order?"
        message={order.paymentMethod === 'RAZORPAY' && payment && ['PAID', 'AUTHORIZED'].includes(payment.status)
          ? 'Your payment will be refunded to the original payment method, usually within 5–7 business days.'
          : 'The items will be released and you will not be charged.'}
        reasonLabel="Reason (optional)" confirmLabel="Cancel order" loading={cancel.isPending}
        onConfirm={(reason) => cancel.mutate(reason)} />
    </div>
  );
}

function PendingPayment({ order, paying, onPay }: { order: OrderDetail; paying: boolean; onPay: () => void }) {
  const now = useNow(1000);
  const deadline = order.paymentDeadline ? Date.parse(order.paymentDeadline) : null;
  const remaining = deadline ? Math.max(0, deadline - now) : null;
  const minutes = remaining !== null ? Math.floor(remaining / 60_000) : null;
  const seconds = remaining !== null ? Math.floor((remaining % 60_000) / 1000) : null;
  const failed = order.payment?.status === 'FAILED';
  return (
    <InlineAlert tone={failed ? 'danger' : 'warning'} className="mb-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <p className="font-semibold">{failed ? 'Your payment did not go through' : 'Complete your payment to place this order'}</p>
          <p className="mt-0.5">
            {failed && order.payment?.failureReason ? `${order.payment.failureReason}. ` : ''}
            {remaining !== null && remaining > 0
              ? <>Your items are reserved for <strong className="tabular-nums">{minutes}:{String(seconds).padStart(2, '0')}</strong> more minutes.</>
              : 'The reservation is about to expire.'}
          </p>
        </div>
        {order.canPay && (
          <Button onClick={onPay} loading={paying} icon={<Clock className="h-4 w-4" aria-hidden />}>
            {failed ? 'Retry payment' : 'Pay now'} · {formatPrice(order.totalAmount)}
          </Button>
        )}
      </div>
    </InlineAlert>
  );
}
