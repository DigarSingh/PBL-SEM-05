import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { ChevronRight, Package } from 'lucide-react';
import { Link, useSearchParams } from 'react-router';
import { ProductImage } from '@/components/ProductImage';
import { ButtonLink } from '@/components/ui/Button';
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/Feedback';
import { Select } from '@/components/ui/Field';
import { Pagination } from '@/components/ui/Pagination';
import { formatDate, formatPrice, orderStatusMeta } from '@/lib/format';
import { useDocumentTitle, useRealtimeStatus } from '@/lib/hooks';
import type { OrderStatus } from '@/lib/types';
import { ordersApi } from './api';
import { OrderStatusBadge, PaymentBadge } from './OrderBadges';

const STATUSES = Object.keys(orderStatusMeta) as OrderStatus[];

export default function OrdersPage() {
  useDocumentTitle('Your orders');
  const [params, setParams] = useSearchParams();
  const statusParam = params.get('status');
  const status = STATUSES.includes(statusParam as OrderStatus) ? (statusParam as OrderStatus) : undefined;
  const page = Math.max(0, Number(params.get('page') ?? '1') - 1) || 0;
  const realtimeStatus = useRealtimeStatus();
  const orders = useQuery({
    queryKey: ['me', 'orders', 'list', status ?? 'all', page],
    queryFn: () => ordersApi.list(status, page),
    placeholderData: keepPreviousData,
    refetchInterval: realtimeStatus === 'connected' ? false : 30_000,
  });

  const setFilter = (next: { status?: string; page?: number }) => {
    const search = new URLSearchParams(params);
    if ('status' in next) {
      if (next.status) search.set('status', next.status);
      else search.delete('status');
      search.delete('page');
    }
    if (next.page !== undefined) {
      if (next.page > 0) search.set('page', String(next.page + 1));
      else search.delete('page');
    }
    setParams(search);
  };

  return (
    <div>
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">Your orders</h1>
          <p className="mt-1 text-sm text-slate-500">Track deliveries, pay pending orders and view past purchases.</p>
        </div>
        <Select aria-label="Filter by status" value={status ?? ''} onChange={(event) => setFilter({ status: event.target.value })} className="h-9 w-auto">
          <option value="">All orders</option>
          {STATUSES.map((value) => <option key={value} value={value}>{orderStatusMeta[value].label}</option>)}
        </Select>
      </div>

      {orders.isPending ? (
        <div className="space-y-3">{[0, 1, 2].map((key) => <Skeleton key={key} className="h-28 w-full" />)}</div>
      ) : orders.isError ? (
        <ErrorState error={orders.error} onRetry={() => void orders.refetch()} />
      ) : orders.data.items.length === 0 ? (
        <EmptyState icon={<Package className="h-7 w-7" aria-hidden />} title={status ? 'No orders with this status' : 'No orders yet'}
          description={status ? 'Try another filter.' : 'When you place an order it will appear here, with live tracking.'}
          action={!status ? <ButtonLink to="/shop">Start shopping</ButtonLink> : undefined} />
      ) : (
        <ul className="space-y-3">
          {orders.data.items.map((order) => (
            <li key={order.id}>
              <Link to={`/orders/${order.id}`} className="flex items-center gap-4 rounded-2xl border border-stone-200 bg-white p-4 transition-shadow hover:shadow-md">
                <span className="h-16 w-16 shrink-0 overflow-hidden rounded-xl border border-stone-200">
                  <ProductImage src={order.firstItemImage} alt="" className="h-full w-full" />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="flex flex-wrap items-center gap-2">
                    <span className="font-semibold text-slate-900">{order.orderNumber}</span>
                    <OrderStatusBadge status={order.status} />
                    <PaymentBadge method={order.paymentMethod} status={order.paymentStatus} />
                  </span>
                  <span className="mt-1 block truncate text-sm text-slate-600">
                    {order.firstItemName}{order.itemCount > 1 ? ` and ${order.itemCount - 1} more item${order.itemCount > 2 ? 's' : ''}` : ''}
                  </span>
                  <span className="mt-0.5 block text-xs text-slate-500">Ordered {formatDate(order.placedAt ?? order.createdAt)}</span>
                </span>
                <span className="text-right">
                  <span className="block font-bold text-slate-900">{formatPrice(order.totalAmount)}</span>
                  <ChevronRight className="ml-auto mt-1 h-4 w-4 text-slate-400" aria-hidden />
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}

      {orders.data && <Pagination className="mt-8" page={orders.data.page} totalPages={orders.data.totalPages} onChange={(next) => setFilter({ page: next })} />}
    </div>
  );
}
