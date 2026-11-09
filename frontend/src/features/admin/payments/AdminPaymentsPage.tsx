import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router';
import { toast } from 'sonner';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorState } from '@/components/ui/Feedback';
import { Select } from '@/components/ui/Field';
import { Pagination } from '@/components/ui/Pagination';
import { Table, TableWrap, Td, Th } from '@/components/ui/Table';
import { OrderStatusBadge, PaymentBadge } from '@/features/orders/OrderBadges';
import { errorMessage } from '@/lib/errors';
import { formatDateTime, formatPrice, paymentMethodLabel, paymentStatusMeta } from '@/lib/format';
import type { AdminPayment, PaymentMethod, PaymentStatus } from '@/lib/types';
import { adminApi } from '../api';
import { AdminPage } from '../components/AdminPage';
import { SearchInput } from '../components/SearchInput';
import { TableEmpty, TableSkeleton } from '../components/TableStates';
import { pageFrom, useParamsState } from '../useParamsState';

const DEFAULTS = { status: '', method: '', q: '', page: '' };

export default function AdminPaymentsPage() {
  const [filters, setFilters] = useParamsState(DEFAULTS);
  const queryClient = useQueryClient();
  const query = {
    status: (filters.status || undefined) as PaymentStatus | undefined,
    method: (filters.method || undefined) as PaymentMethod | undefined,
    q: filters.q || undefined,
    page: pageFrom(filters.page),
    size: 20,
  };
  const payments = useQuery({ queryKey: ['admin', 'payments', query], queryFn: () => adminApi.payments(query), placeholderData: keepPreviousData });
  const collect = useMutation({
    mutationFn: (payment: AdminPayment) => adminApi.collectPayment(payment.id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin'] });
      toast.success('Cash payment recorded');
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  const refund = useMutation({
    mutationFn: (payment: AdminPayment) => adminApi.retryRefund(payment.id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin'] });
      toast.success('Refund requested with Razorpay');
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <AdminPage title="Payments" description="Online payments are confirmed with Razorpay on the server; cash is recorded when collected.">
      <Card className="mb-4 flex flex-wrap items-center gap-3 p-4">
        <SearchInput key={filters.q} value={filters.q} onSearch={(q) => setFilters({ q })} placeholder="Order number, customer or payment ID" className="w-full sm:w-80" />
        <Select aria-label="Payment status" value={filters.status} onChange={(event) => setFilters({ status: event.target.value })} className="h-9 w-auto">
          <option value="">All statuses</option>
          {(Object.keys(paymentStatusMeta) as PaymentStatus[]).map((status) => <option key={status} value={status}>{paymentStatusMeta[status].label}</option>)}
        </Select>
        <Select aria-label="Payment method" value={filters.method} onChange={(event) => setFilters({ method: event.target.value })} className="h-9 w-auto">
          <option value="">All methods</option>
          <option value="RAZORPAY">{paymentMethodLabel('RAZORPAY')}</option>
          <option value="COD">{paymentMethodLabel('COD')}</option>
        </Select>
      </Card>
      <Card>
        {payments.isPending ? <TableSkeleton /> : payments.isError ? (
          <div className="p-4"><ErrorState error={payments.error} onRetry={() => void payments.refetch()} /></div>
        ) : payments.data.items.length === 0 ? <TableEmpty message="No payments match these filters." /> : (
          <TableWrap>
            <Table className="min-w-[900px]">
              <thead><tr><Th>Order</Th><Th>Customer</Th><Th>Method</Th><Th>Status</Th><Th className="text-right">Amount</Th><Th>Reference</Th><Th>Date</Th><Th /></tr></thead>
              <tbody>
                {payments.data.items.map((payment) => (
                  <tr key={payment.id} className="hover:bg-stone-50">
                    <Td>
                      <Link to={`/admin/orders/${payment.orderId}`} className="font-semibold text-slate-900 hover:text-brand-700">{payment.orderNumber}</Link>
                      <span className="mt-0.5 block"><OrderStatusBadge status={payment.orderStatus} /></span>
                    </Td>
                    <Td><span className="block">{payment.customerName}</span><span className="text-xs text-slate-500">{payment.customerEmail}</span></Td>
                    <Td>{paymentMethodLabel(payment.method)}{payment.providerMethod && <span className="block text-xs text-slate-500">{payment.providerMethod}</span>}</Td>
                    <Td>
                      <PaymentBadge method={payment.method} status={payment.status} />
                      {payment.refundPending && <span className="mt-0.5 block text-xs text-amber-700">Refund pending</span>}
                      {payment.failureReason && <span className="mt-0.5 block max-w-48 truncate text-xs text-rose-700" title={payment.failureReason}>{payment.failureReason}</span>}
                    </Td>
                    <Td className="text-right font-medium tabular-nums">{formatPrice(payment.amount)}</Td>
                    <Td className="max-w-40 truncate font-mono text-xs text-slate-500" title={payment.razorpayPaymentId ?? payment.razorpayOrderId ?? ''}>{payment.razorpayPaymentId ?? payment.razorpayOrderId ?? '-'}</Td>
                    <Td className="whitespace-nowrap text-slate-500">{formatDateTime(payment.paidAt ?? payment.createdAt)}</Td>
                    <Td className="text-right">
                      {payment.method === 'COD' && payment.status === 'PENDING' && (payment.orderStatus === 'OUT_FOR_DELIVERY' || payment.orderStatus === 'DELIVERED') && (
                        <Button size="sm" variant="secondary" loading={collect.isPending && collect.variables?.id === payment.id} onClick={() => collect.mutate(payment)}>Collected</Button>
                      )}
                      {payment.refundPending && (
                        <Button size="sm" variant="secondary" loading={refund.isPending && refund.variables?.id === payment.id} onClick={() => refund.mutate(payment)}>Retry refund</Button>
                      )}
                    </Td>
                  </tr>
                ))}
              </tbody>
            </Table>
          </TableWrap>
        )}
      </Card>
      {payments.data && (
        <div className="mt-4 flex items-center justify-between text-sm text-slate-500">
          <span>{payments.data.totalElements} payments</span>
          <Pagination page={payments.data.page} totalPages={payments.data.totalPages} onChange={(page) => setFilters({ page: String(page + 1) })} />
        </div>
      )}
    </AdminPage>
  );
}
