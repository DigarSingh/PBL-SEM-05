import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router';
import { toast } from 'sonner';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ConfirmDialog, Drawer } from '@/components/ui/Dialog';
import { ErrorState, Spinner } from '@/components/ui/Feedback';
import { Select } from '@/components/ui/Field';
import { Pagination } from '@/components/ui/Pagination';
import { Table, TableWrap, Td, Th } from '@/components/ui/Table';
import { AddressLines } from '@/features/account/AddressCard';
import { OrderStatusBadge } from '@/features/orders/OrderBadges';
import { errorMessage } from '@/lib/errors';
import { formatDate, formatDateTime, formatPrice, initials } from '@/lib/format';
import type { AdminCustomerSummary } from '@/lib/types';
import { adminApi } from '../api';
import { AdminPage } from '../components/AdminPage';
import { SearchInput } from '../components/SearchInput';
import { TableEmpty, TableSkeleton } from '../components/TableStates';
import { pageFrom, useParamsState } from '../useParamsState';

const DEFAULTS = { q: '', status: '', page: '', open: '' };

export default function AdminCustomersPage() {
  const [filters, setFilters] = useParamsState(DEFAULTS);
  const query = {
    q: filters.q || undefined,
    status: (filters.status || undefined) as 'ACTIVE' | 'DISABLED' | undefined,
    page: pageFrom(filters.page),
    size: 20,
  };
  const customers = useQuery({ queryKey: ['admin', 'customers', query], queryFn: () => adminApi.customers(query), placeholderData: keepPreviousData });
  const openId = filters.open ? Number(filters.open) : null;

  return (
    <AdminPage title="Customers">
      <Card className="mb-4 flex flex-wrap items-center gap-3 p-4">
        <SearchInput key={filters.q} value={filters.q} onSearch={(q) => setFilters({ q })} placeholder="Name, email or phone" className="w-full sm:w-72" />
        <Select aria-label="Account status" value={filters.status} onChange={(event) => setFilters({ status: event.target.value })} className="h-9 w-auto">
          <option value="">All accounts</option>
          <option value="ACTIVE">Active</option>
          <option value="DISABLED">Disabled</option>
        </Select>
      </Card>
      <Card>
        {customers.isPending ? <TableSkeleton /> : customers.isError ? (
          <div className="p-4"><ErrorState error={customers.error} onRetry={() => void customers.refetch()} /></div>
        ) : customers.data.items.length === 0 ? <TableEmpty message="No customers match." /> : (
          <TableWrap className={customers.isPlaceholderData ? 'opacity-60' : undefined}>
            <Table className="min-w-[860px]">
              <thead><tr><Th>Customer</Th><Th>Phone</Th><Th className="text-right">Orders</Th><Th className="text-right">Spent</Th><Th>Last order</Th><Th>Joined</Th><Th>Status</Th></tr></thead>
              <tbody>
                {customers.data.items.map((customer) => (
                  <tr key={customer.id} className="cursor-pointer hover:bg-stone-50" onClick={() => setFilters({ open: String(customer.id), page: filters.page })}>
                    <Td>
                      <button type="button" className="text-left" onClick={(event) => {
                        event.stopPropagation();
                        setFilters({ open: String(customer.id), page: filters.page });
                      }}>
                        <span className="block font-semibold text-slate-900 hover:text-brand-700">{customer.fullName}</span>
                        <span className="block text-xs text-slate-500">{customer.email}{!customer.emailVerified && ' · unverified'}</span>
                      </button>
                    </Td>
                    <Td>{customer.phone ?? '-'}</Td>
                    <Td className="text-right tabular-nums">{customer.orders}</Td>
                    <Td className="text-right font-medium tabular-nums">{formatPrice(customer.totalSpent)}</Td>
                    <Td className="text-slate-500">{formatDate(customer.lastOrderAt)}</Td>
                    <Td className="text-slate-500">{formatDate(customer.createdAt)}</Td>
                    <Td>{customer.status === 'ACTIVE' ? <Badge tone="success">Active</Badge> : <Badge tone="danger">Disabled</Badge>}</Td>
                  </tr>
                ))}
              </tbody>
            </Table>
          </TableWrap>
        )}
      </Card>
      {customers.data && (
        <div className="mt-4 flex items-center justify-between text-sm text-slate-500">
          <span>{customers.data.totalElements} customers</span>
          <Pagination page={customers.data.page} totalPages={customers.data.totalPages} onChange={(page) => setFilters({ page: String(page + 1) })} />
        </div>
      )}
      <Drawer open={openId !== null} onClose={() => setFilters({ open: '', page: filters.page })} title="Customer">
        {openId !== null && <CustomerDetail customerId={openId} />}
      </Drawer>
    </AdminPage>
  );
}

function CustomerDetail({ customerId }: { customerId: number }) {
  const queryClient = useQueryClient();
  const [confirm, setConfirm] = useState(false);
  const detail = useQuery({ queryKey: ['admin', 'customers', 'detail', customerId], queryFn: () => adminApi.customer(customerId) });
  const setStatus = useMutation({
    mutationFn: (customer: AdminCustomerSummary) => adminApi.setCustomerStatus(customer.id, customer.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'),
    onSuccess: async (updated) => {
      setConfirm(false);
      await queryClient.invalidateQueries({ queryKey: ['admin', 'customers'] });
      toast.success(updated.status === 'ACTIVE' ? 'Account enabled' : 'Account disabled and signed out');
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  if (detail.isPending) {
    return <Spinner />;
  }
  if (detail.isError) {
    return <ErrorState error={detail.error} onRetry={() => void detail.refetch()} />;
  }
  const { customer, addresses, recentOrders, lastLoginAt } = detail.data;
  const active = customer.status === 'ACTIVE';
  return (
    <div className="space-y-6 text-sm">
      <div className="flex items-center gap-3">
        <span className="flex h-12 w-12 items-center justify-center rounded-full bg-ink font-bold text-white">{initials(customer.fullName)}</span>
        <div className="min-w-0">
          <p className="font-semibold text-slate-900">{customer.fullName}</p>
          <p className="truncate text-slate-500">{customer.email}</p>
          <p className="text-slate-500">{customer.phone ?? 'No phone'}</p>
        </div>
      </div>
      <dl className="grid grid-cols-2 gap-3">
        <div className="rounded-lg bg-stone-50 p-3"><dt className="text-xs text-slate-500">Orders</dt><dd className="text-lg font-bold">{customer.orders}</dd></div>
        <div className="rounded-lg bg-stone-50 p-3"><dt className="text-xs text-slate-500">Spent</dt><dd className="text-lg font-bold">{formatPrice(customer.totalSpent)}</dd></div>
        <div className="rounded-lg bg-stone-50 p-3"><dt className="text-xs text-slate-500">Joined</dt><dd>{formatDate(customer.createdAt)}</dd></div>
        <div className="rounded-lg bg-stone-50 p-3"><dt className="text-xs text-slate-500">Last sign-in</dt><dd>{lastLoginAt ? formatDateTime(lastLoginAt) : 'Never'}</dd></div>
      </dl>
      <section>
        <h3 className="mb-2 font-semibold text-slate-900">Recent orders</h3>
        {recentOrders.length === 0 ? <p className="text-slate-500">No orders yet.</p> : (
          <ul className="divide-y divide-stone-100 rounded-lg border border-stone-200">
            {recentOrders.map((order) => (
              <li key={order.id} className="flex items-center justify-between gap-2 px-3 py-2">
                <Link to={`/admin/orders/${order.id}`} className="font-medium text-slate-900 hover:text-brand-700">{order.orderNumber}</Link>
                <OrderStatusBadge status={order.status} />
                <span className="tabular-nums">{formatPrice(order.totalAmount)}</span>
              </li>
            ))}
          </ul>
        )}
      </section>
      <section>
        <h3 className="mb-2 font-semibold text-slate-900">Addresses</h3>
        {addresses.length === 0 ? <p className="text-slate-500">No saved addresses.</p> : (
          <ul className="space-y-3">{addresses.map((address) => <li key={address.id} className="rounded-lg border border-stone-200 p-3"><AddressLines address={address} /></li>)}</ul>
        )}
      </section>
      <div className="border-t border-stone-100 pt-4">
        <Button variant={active ? 'danger' : 'primary'} onClick={() => setConfirm(true)}>{active ? 'Disable account' : 'Enable account'}</Button>
        <p className="mt-2 text-xs text-slate-500">{active ? 'A disabled customer is signed out everywhere and cannot sign in.' : 'The customer will be able to sign in again.'}</p>
      </div>
      <ConfirmDialog open={confirm} onClose={() => setConfirm(false)} tone={active ? 'danger' : 'primary'}
        title={active ? `Disable ${customer.fullName}'s account?` : `Enable ${customer.fullName}'s account?`}
        message={active ? 'They will be signed out on all devices immediately. Existing orders are not affected.' : 'They can sign in and place orders again.'}
        confirmLabel={active ? 'Disable' : 'Enable'} loading={setStatus.isPending} onConfirm={() => setStatus.mutate(customer)} />
    </div>
  );
}
