import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Banknote, Mail, Phone, RotateCcw, XCircle } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { Link, useParams } from 'react-router';
import { toast } from 'sonner';
import { ProductImage } from '@/components/ProductImage';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { ConfirmDialog, Modal } from '@/components/ui/Dialog';
import { ErrorState, InlineAlert, Skeleton } from '@/components/ui/Feedback';
import { Textarea } from '@/components/ui/Field';
import { AddressLines } from '@/features/account/AddressCard';
import { OrderStatusBadge, PaymentBadge } from '@/features/orders/OrderBadges';
import { OrderTimeline } from '@/features/orders/OrderTimeline';
import { errorMessage } from '@/lib/errors';
import { formatDate, formatDateTime, formatPrice, orderStatusMeta, paymentMethodLabel } from '@/lib/format';
import { useRealtimeStatus } from '@/lib/hooks';
import type { OrderDetail, OrderStatus } from '@/lib/types';
import { adminApi } from '../api';
import { AdminPage } from '../components/AdminPage';

const ACTION_LABELS: Partial<Record<OrderStatus, string>> = {
  CONFIRMED: 'Confirm order',
  PROCESSING: 'Start processing',
  SHIPPED: 'Mark as shipped',
  OUT_FOR_DELIVERY: 'Out for delivery',
  DELIVERED: 'Mark as delivered',
};

export default function AdminOrderDetailPage() {
  const { orderId } = useParams();
  const id = Number(orderId);
  const realtimeStatus = useRealtimeStatus();
  const order = useQuery({
    queryKey: ['admin', 'orders', 'detail', id],
    queryFn: () => adminApi.order(id),
    enabled: Number.isFinite(id),
    refetchInterval: realtimeStatus === 'connected' ? false : 20_000,
  });
  if (order.isPending) {
    return <AdminPage title="Order"><Skeleton className="h-96 w-full" /></AdminPage>;
  }
  if (order.isError) {
    return <AdminPage title="Order"><ErrorState error={order.error} onRetry={() => void order.refetch()} /></AdminPage>;
  }
  return <AdminOrderView order={order.data} />;
}

function AdminOrderView({ order }: { order: OrderDetail }) {
  const queryClient = useQueryClient();
  const [transition, setTransition] = useState<OrderStatus | null>(null);
  const [note, setNote] = useState('');
  const [cancelOpen, setCancelOpen] = useState(false);
  const [collectOpen, setCollectOpen] = useState(false);

  const refresh = (updated?: OrderDetail) => {
    if (updated) {
      queryClient.setQueryData(['admin', 'orders', 'detail', order.id], updated);
    }
    void queryClient.invalidateQueries({ queryKey: ['admin'] });
  };
  const updateStatus = useMutation({
    mutationFn: ({ status, text }: { status: OrderStatus; text: string }) => adminApi.updateOrderStatus(order.id, status, text || null),
    onSuccess: (updated) => {
      refresh(updated);
      setTransition(null);
      setNote('');
      toast.success(`Order ${updated.orderNumber} is now ${orderStatusMeta[updated.status].label.toLowerCase()}`);
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  const cancel = useMutation({
    mutationFn: (reason: string) => adminApi.cancelOrder(order.id, reason || null),
    onSuccess: (updated) => {
      refresh(updated);
      setCancelOpen(false);
      toast.success(`Order ${updated.orderNumber} cancelled`);
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  const collect = useMutation({
    mutationFn: (paymentId: number) => adminApi.collectPayment(paymentId),
    onSuccess: () => {
      refresh();
      setCollectOpen(false);
      toast.success('Cash payment recorded');
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  const refund = useMutation({
    mutationFn: (paymentId: number) => adminApi.retryRefund(paymentId),
    onSuccess: () => {
      refresh();
      toast.success('Refund requested with Razorpay');
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const payment = order.payment;
  // Unpaid online orders only move forward when the payment succeeds; staff can only cancel them.
  const forward = order.status === 'PENDING_PAYMENT' ? [] : order.allowedTransitions.filter((status) => status !== 'CANCELLED');
  const canCancel = order.allowedTransitions.includes('CANCELLED');
  const canCollect = payment?.method === 'COD' && payment.status === 'PENDING' && (order.status === 'OUT_FOR_DELIVERY' || order.status === 'DELIVERED');
  const willRefund = payment?.method === 'RAZORPAY' && (payment.status === 'PAID' || payment.status === 'AUTHORIZED');

  return (
    <AdminPage title={`Order ${order.orderNumber}`} breadcrumbs={[{ label: 'Orders', to: '/admin/orders' }, { label: order.orderNumber }]}
      description={`Created ${formatDateTime(order.createdAt)}${order.placedAt ? ` · placed ${formatDateTime(order.placedAt)}` : ''}`}
      actions={<div className="flex flex-wrap items-center gap-2"><OrderStatusBadge status={order.status} /><PaymentBadge method={order.paymentMethod} status={payment?.status ?? null} /></div>}>

      <Card className="mb-6 p-4">
        <div className="flex flex-wrap items-center gap-2">
          {forward.map((status) => (
            <Button key={status} onClick={() => setTransition(status)}>{ACTION_LABELS[status] ?? orderStatusMeta[status].label}</Button>
          ))}
          {canCollect && payment && (
            <Button variant="brand" icon={<Banknote className="h-4 w-4" aria-hidden />} onClick={() => setCollectOpen(true)}>Mark cash collected</Button>
          )}
          {payment?.refundPending && (
            <Button variant="secondary" icon={<RotateCcw className="h-4 w-4" aria-hidden />} loading={refund.isPending} onClick={() => refund.mutate(payment.id)}>
              Retry refund
            </Button>
          )}
          {canCancel && (
            <Button variant="secondary" className="text-rose-700" icon={<XCircle className="h-4 w-4" aria-hidden />} onClick={() => setCancelOpen(true)}>Cancel order</Button>
          )}
          {forward.length === 0 && !canCancel && !canCollect && !payment?.refundPending && (
            <p className="text-sm text-slate-500">
              {order.status === 'PENDING_PAYMENT' ? 'Waiting for the customer to pay online.' : 'No further actions for this order.'}
            </p>
          )}
        </div>
      </Card>

      {order.status === 'CANCELLED' && (
        <InlineAlert tone="danger" className="mb-6">
          Cancelled {order.cancelledAt ? formatDateTime(order.cancelledAt) : ''}{order.cancellationReason ? ` · ${order.cancellationReason}` : ''}
          {payment?.status === 'REFUNDED' && ` · Refunded ${formatPrice(payment.refundedAmount ?? payment.amount)}`}
          {payment?.refundPending && ' · Refund pending'}
        </InlineAlert>
      )}

      <div className="grid gap-6 xl:grid-cols-[1fr_22rem]">
        <div className="space-y-6">
          <Card>
            <CardHeader title={`Items (${order.items.length})`} />
            <ul className="divide-y divide-stone-100">
              {order.items.map((item) => (
                <li key={item.id} className="flex items-center gap-4 px-5 py-3">
                  <span className="h-12 w-12 shrink-0 overflow-hidden rounded-lg border border-stone-200"><ProductImage src={item.imageUrl} alt="" className="h-full w-full" /></span>
                  <span className="min-w-0 flex-1 text-sm">
                    <Link to={`/admin/products/${item.productId}`} className="font-medium text-slate-900 hover:text-brand-700">{item.productName}</Link>
                    <span className="block text-xs text-slate-500">{item.variantName} · {item.sku}</span>
                  </span>
                  <span className="text-sm tabular-nums text-slate-600">{item.quantity} × {formatPrice(item.unitPrice)}</span>
                  <span className="w-24 text-right text-sm font-semibold tabular-nums text-slate-900">{formatPrice(item.lineTotal)}</span>
                </li>
              ))}
            </ul>
            <dl className="space-y-1.5 border-t border-stone-100 px-5 py-4 text-sm">
              <Row label="Subtotal" value={formatPrice(order.subtotal)} />
              {order.couponDiscount > 0 && <Row label={`Coupon ${order.couponCode}`} value={`− ${formatPrice(order.couponDiscount)}`} />}
              <Row label="Delivery" value={order.shippingFee === 0 ? 'Free' : formatPrice(order.shippingFee)} />
              <Row label={`GST ${Math.round(order.taxRate * 100)}% ${order.taxInclusive ? '(included)' : ''}`} value={formatPrice(order.taxAmount)} />
              <div className="flex justify-between border-t border-stone-100 pt-2 text-base font-bold text-slate-900"><dt>Total</dt><dd>{formatPrice(order.totalAmount)}</dd></div>
            </dl>
          </Card>

          {order.status !== 'CANCELLED' && order.status !== 'PENDING_PAYMENT' && (
            <Card className="p-5">
              <h2 className="mb-4 text-base font-semibold text-slate-900">Fulfilment</h2>
              <OrderTimeline steps={order.timeline} cancelled={false} />
            </Card>
          )}

          <Card className="p-5">
            <h2 className="mb-3 text-base font-semibold text-slate-900">History</h2>
            <ul className="space-y-2 text-sm">
              {[...order.history].reverse().map((entry, index) => (
                <li key={`${entry.at}-${index}`} className="flex flex-wrap justify-between gap-x-4">
                  <span className="text-slate-700">
                    <strong>{orderStatusMeta[entry.toStatus].label}</strong>
                    {entry.note && <span className="text-slate-500"> · {entry.note}</span>}
                    <span className="text-slate-400"> · {entry.changedBy ?? 'System'}</span>
                  </span>
                  <span className="text-slate-400">{formatDateTime(entry.at)}</span>
                </li>
              ))}
            </ul>
          </Card>
        </div>

        <aside className="space-y-6">
          {order.customer && (
            <Card className="p-5">
              <h2 className="mb-2 text-sm font-semibold text-slate-900">Customer</h2>
              <Link to={`/admin/customers?open=${order.customer.id}`} className="font-medium text-slate-900 hover:text-brand-700">{order.customer.fullName}</Link>
              <p className="mt-1 flex items-center gap-1.5 text-sm text-slate-600"><Mail className="h-3.5 w-3.5" aria-hidden />{order.customer.email}</p>
              {order.customer.phone && <p className="mt-0.5 flex items-center gap-1.5 text-sm text-slate-600"><Phone className="h-3.5 w-3.5" aria-hidden />{order.customer.phone}</p>}
            </Card>
          )}
          <Card className="p-5">
            <h2 className="mb-2 text-sm font-semibold text-slate-900">Ship to</h2>
            <AddressLines address={order.shippingAddress} />
            {order.estimatedDeliveryDate && <p className="mt-3 text-xs text-slate-500">Promised by {formatDate(order.estimatedDeliveryDate)}</p>}
            {order.customerNote && <p className="mt-3 rounded-lg bg-amber-50 p-2 text-xs text-amber-900">Customer note: {order.customerNote}</p>}
          </Card>
          <Card className="p-5">
            <h2 className="mb-2 text-sm font-semibold text-slate-900">Payment</h2>
            {payment ? (
              <dl className="space-y-1.5 text-sm">
                <Row label="Method" value={`${paymentMethodLabel(payment.method)}${payment.providerMethod ? ` (${payment.providerMethod})` : ''}`} />
                <Row label="Status" value={<PaymentBadge method={payment.method} status={payment.status} />} />
                <Row label="Amount" value={formatPrice(payment.amount)} />
                {payment.paidAt && <Row label="Paid" value={formatDateTime(payment.paidAt)} />}
                {payment.razorpayOrderId && <Row label="Razorpay order" value={<span className="break-all font-mono text-xs">{payment.razorpayOrderId}</span>} />}
                {payment.razorpayPaymentId && <Row label="Razorpay payment" value={<span className="break-all font-mono text-xs">{payment.razorpayPaymentId}</span>} />}
                {payment.failureReason && <Row label="Failure" value={<span className="text-rose-700">{payment.failureReason}</span>} />}
                {payment.refundedAt && <Row label="Refunded" value={`${formatPrice(payment.refundedAmount ?? payment.amount)} on ${formatDate(payment.refundedAt)}`} />}
              </dl>
            ) : <p className="text-sm text-slate-500">No payment recorded.</p>}
          </Card>
        </aside>
      </div>

      <Modal open={transition !== null} onClose={() => setTransition(null)} size="sm"
        title={transition ? `${ACTION_LABELS[transition] ?? orderStatusMeta[transition].label}?` : ''}
        description="The customer is notified instantly and by email for shipping and delivery updates."
        footer={<>
          <Button variant="secondary" onClick={() => setTransition(null)} disabled={updateStatus.isPending}>Back</Button>
          <Button loading={updateStatus.isPending} onClick={() => transition && updateStatus.mutate({ status: transition, text: note.trim() })}>Update order</Button>
        </>}>
        <label className="block text-sm">
          <span className="mb-1 block font-medium text-slate-700">Note (optional, shown in the order history)</span>
          <Textarea value={note} maxLength={500} rows={3} onChange={(event) => setNote(event.target.value)} placeholder="e.g. Shipped via BlueDart, AWB 1234567890" />
        </label>
      </Modal>

      <ConfirmDialog open={cancelOpen} onClose={() => setCancelOpen(false)} title="Cancel this order?"
        message={willRefund ? 'Reserved stock is released and the online payment is refunded automatically through Razorpay.' : 'Reserved stock is released. The customer is notified immediately.'}
        reasonLabel="Reason (sent to the customer)" confirmLabel="Cancel order" loading={cancel.isPending}
        onConfirm={(reason) => cancel.mutate(reason)} />
      <ConfirmDialog open={collectOpen} onClose={() => setCollectOpen(false)} tone="primary" title="Record cash collection?"
        message={`Confirm that ${formatPrice(order.totalAmount)} was collected on delivery.`} confirmLabel="Cash collected"
        loading={collect.isPending} onConfirm={() => {
          if (payment) {
            collect.mutate(payment.id);
          }
        }} />
    </AdminPage>
  );
}

function Row({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-3">
      <dt className="text-slate-500">{label}</dt>
      <dd className="text-right text-slate-800">{value}</dd>
    </div>
  );
}
