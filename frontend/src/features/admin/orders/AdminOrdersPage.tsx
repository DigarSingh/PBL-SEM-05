import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router';
import { useState } from 'react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Modal } from '@/components/ui/Dialog';
import { ErrorState } from '@/components/ui/Feedback';
import { Checkbox, Input, Select, Textarea } from '@/components/ui/Field';
import { Pagination } from '@/components/ui/Pagination';
import { Table, TableWrap, Td, Th } from '@/components/ui/Table';
import { OrderStatusBadge, PaymentBadge } from '@/features/orders/OrderBadges';
import { formatDateTime, formatPrice, orderStatusMeta, paymentMethodLabel, paymentStatusMeta } from '@/lib/format';
import { useRealtimeStatus } from '@/lib/hooks';
import type { AdminOrderSummary, OrderStatus, PaymentMethod, PaymentStatus } from '@/lib/types';
import { adminApi, type AdminOrderQuery } from '../api';
import { AdminPage } from '../components/AdminPage';
import { SearchInput } from '../components/SearchInput';
import { TableEmpty, TableSkeleton } from '../components/TableStates';
import { pageFrom, useParamsState } from '../useParamsState';

const DEFAULTS = { status: '', paymentStatus: '', paymentMethod: '', includePending: '', q: '', from: '', to: '', page: '' };
const NEXT_STATUS: Partial<Record<OrderStatus, OrderStatus>> = {
  PLACED: 'CONFIRMED',
  CONFIRMED: 'PROCESSING',
  PROCESSING: 'SHIPPED',
  SHIPPED: 'OUT_FOR_DELIVERY',
  OUT_FOR_DELIVERY: 'DELIVERED',
};
const NEXT_ACTION_LABEL: Partial<Record<OrderStatus, string>> = {
  PLACED: 'Confirm',
  CONFIRMED: 'Process',
  PROCESSING: 'Ship',
  SHIPPED: 'Out for delivery',
  OUT_FOR_DELIVERY: 'Deliver',
};

export default function AdminOrdersPage() {
  const [filters, setFilters] = useParamsState(DEFAULTS);
  const realtimeStatus = useRealtimeStatus();
  const query: AdminOrderQuery = {
    status: (filters.status || undefined) as OrderStatus | undefined,
    paymentStatus: (filters.paymentStatus || undefined) as PaymentStatus | undefined,
    paymentMethod: (filters.paymentMethod || undefined) as PaymentMethod | undefined,
    includePending: filters.includePending === 'true' || filters.status === 'PENDING_PAYMENT' || undefined,
    q: filters.q || undefined,
    from: filters.from || undefined,
    to: filters.to || undefined,
    page: pageFrom(filters.page),
    size: 20,
  };
  const orders = useQuery({
    queryKey: ['admin', 'orders', 'list', query],
    queryFn: () => adminApi.orders(query),
    placeholderData: keepPreviousData,
    refetchInterval: realtimeStatus === 'connected' ? false : 30_000,
  });

  return (
    <AdminPage title="Orders" description="New orders appear here instantly while you're connected.">
      <Card className="mb-4 p-4">
        <div className="flex flex-wrap items-end gap-3">
          <SearchInput key={filters.q} value={filters.q} onSearch={(q) => setFilters({ q })} placeholder="Order number, customer name or email" className="w-full sm:w-80" />
          <Select aria-label="Order status" value={filters.status} onChange={(event) => setFilters({ status: event.target.value })} className="h-9 w-auto">
            <option value="">All statuses</option>
            {(Object.keys(orderStatusMeta) as OrderStatus[]).map((status) => <option key={status} value={status}>{orderStatusMeta[status].label}</option>)}
          </Select>
          <Select aria-label="Payment status" value={filters.paymentStatus} onChange={(event) => setFilters({ paymentStatus: event.target.value })} className="h-9 w-auto">
            <option value="">Any payment status</option>
            {(Object.keys(paymentStatusMeta) as PaymentStatus[]).map((status) => <option key={status} value={status}>{paymentStatusMeta[status].label}</option>)}
          </Select>
          <Select aria-label="Payment method" value={filters.paymentMethod} onChange={(event) => setFilters({ paymentMethod: event.target.value })} className="h-9 w-auto">
            <option value="">Any method</option>
            {(['COD', 'RAZORPAY'] as PaymentMethod[]).map((method) => <option key={method} value={method}>{paymentMethodLabel(method)}</option>)}
          </Select>
          <label className="text-xs text-slate-500">From
            <Input type="date" value={filters.from} onChange={(event) => setFilters({ from: event.target.value })} className="mt-1 h-9 w-auto" />
          </label>
          <label className="text-xs text-slate-500">To
            <Input type="date" value={filters.to} onChange={(event) => setFilters({ to: event.target.value })} className="mt-1 h-9 w-auto" />
          </label>
          <Checkbox label="Include unpaid online orders" checked={filters.includePending === 'true'}
            onChange={(event) => setFilters({ includePending: event.target.checked ? 'true' : '' })} className="h-9" />
        </div>
      </Card>

      <Card>
        {orders.isPending ? <TableSkeleton /> : orders.isError ? (
          <div className="p-4"><ErrorState error={orders.error} onRetry={() => void orders.refetch()} /></div>
        ) : orders.data.items.length === 0 ? <TableEmpty message="No orders match these filters." /> : (
          <TableWrap className={orders.isPlaceholderData ? 'opacity-60' : undefined}>
            <Table>
              <thead>
                <tr><Th>Order</Th><Th>Customer</Th><Th>Placed</Th><Th className="text-right">Items</Th><Th className="text-right">Total</Th><Th>Payment</Th><Th>Status</Th><Th>Next step</Th></tr>
              </thead>
              <tbody>
                {orders.data.items.map((order) => (
                  <tr key={order.id} className="hover:bg-stone-50">
                    <Td><Link to={`/admin/orders/${order.id}`} className="font-semibold text-slate-900 hover:text-brand-700">{order.orderNumber}</Link></Td>
                    <Td><span className="block font-medium text-slate-800">{order.customerName}</span><span className="block text-xs text-slate-500">{order.customerEmail}</span></Td>
                    <Td className="whitespace-nowrap text-slate-500">{formatDateTime(order.placedAt ?? order.createdAt)}</Td>
                    <Td className="text-right tabular-nums">{order.itemCount}</Td>
                    <Td className="text-right font-medium tabular-nums">{formatPrice(order.totalAmount)}</Td>
                    <Td><PaymentBadge method={order.paymentMethod} status={order.paymentStatus} /><span className="mt-0.5 block text-xs text-slate-500">{paymentMethodLabel(order.paymentMethod)}</span></Td>
                    <Td><OrderStatusBadge status={order.status} /></Td>
                    <Td><OrderStatusAction order={order} /></Td>
                  </tr>
                ))}
              </tbody>
            </Table>
          </TableWrap>
        )}
      </Card>
      {orders.data && (
        <div className="mt-4 flex flex-wrap items-center justify-between gap-3 text-sm text-slate-500">
          <span>{orders.data.totalElements} orders</span>
          <Pagination page={orders.data.page} totalPages={orders.data.totalPages} onChange={(page) => setFilters({ page: String(page + 1) })} />
        </div>
      )}
    </AdminPage>
  );
}

function OrderStatusAction({ order }: { order: AdminOrderSummary }) {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [note, setNote] = useState('');
  const nextStatus = NEXT_STATUS[order.status];
  const updateStatus = useMutation({
    mutationFn: () => adminApi.updateOrderStatus(order.id, nextStatus!, note.trim() || null),
    onSuccess: (updated) => {
      void queryClient.invalidateQueries({ queryKey: ['admin'] });
      setOpen(false);
      setNote('');
      toast.success(`Order ${updated.orderNumber} is now ${orderStatusMeta[updated.status].label.toLowerCase()}`);
    },
    onError: (error) => toast.error(error instanceof Error ? error.message : 'Could not update the order status.'),
  });

  if (!nextStatus) {
    return <span className="text-xs text-slate-400">{order.status === 'PENDING_PAYMENT' ? 'Awaiting payment' : '—'}</span>;
  }

  return (
    <>
      <Button size="sm" variant="secondary" onClick={() => setOpen(true)}>{NEXT_ACTION_LABEL[order.status]}</Button>
      <Modal open={open} onClose={() => !updateStatus.isPending && setOpen(false)} size="sm"
        title={`Move ${order.orderNumber} to ${orderStatusMeta[nextStatus].label.toLowerCase()}?`}
        description="This advances the order by one fulfilment step."
        footer={<>
          <Button variant="secondary" onClick={() => setOpen(false)} disabled={updateStatus.isPending}>Back</Button>
          <Button loading={updateStatus.isPending} onClick={() => updateStatus.mutate()}>Update order</Button>
        </>}>
        <label className="block text-sm">
          <span className="mb-1 block font-medium text-slate-700">Note (optional, shown in order history)</span>
          <Textarea value={note} maxLength={500} rows={3} onChange={(event) => setNote(event.target.value)}
            placeholder="e.g. Shipped via BlueDart, AWB 1234567890" />
        </label>
      </Modal>
    </>
  );
}
