import { useQuery } from '@tanstack/react-query';
import { AlertTriangle, Boxes, IndianRupee, PackageCheck, ReceiptText, Timer, Users } from 'lucide-react';
import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { BarList } from '@/components/charts/BarList';
import { ColumnChart } from '@/components/charts/ColumnChart';
import { DataTableDetails } from '@/components/charts/DataTableDetails';
import { StatTile } from '@/components/charts/StatTile';
import { TimeSeriesChart } from '@/components/charts/TimeSeriesChart';
import { Badge } from '@/components/ui/Badge';
import { Card, CardHeader } from '@/components/ui/Card';
import { ErrorState, Skeleton } from '@/components/ui/Feedback';
import { Table, TableWrap, Td, Th } from '@/components/ui/Table';
import { OrderStatusBadge } from '@/features/orders/OrderBadges';
import { formatCompactPrice, formatDay, formatNumber, formatPrice, formatRelative, orderStatusMeta } from '@/lib/format';
import { useRealtimeStatus } from '@/lib/hooks';
import type { OrderStatus } from '@/lib/types';
import { adminApi } from './api';
import { AdminPage } from './components/AdminPage';

export default function AdminDashboardPage() {
  const realtimeStatus = useRealtimeStatus();
  // Live pushes refresh this immediately; the interval only covers periods without a connection.
  const dashboard = useQuery({
    queryKey: ['admin', 'dashboard'],
    queryFn: adminApi.dashboard,
    refetchInterval: realtimeStatus === 'connected' ? 120_000 : 30_000,
  });

  if (dashboard.isPending) {
    return (
      <AdminPage title="Dashboard">
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">{[0, 1, 2, 3].map((key) => <Skeleton key={key} className="h-28" />)}</div>
        <Skeleton className="mt-6 h-80" />
      </AdminPage>
    );
  }
  if (dashboard.isError) {
    return <AdminPage title="Dashboard"><ErrorState error={dashboard.error} onRetry={() => void dashboard.refetch()} /></AdminPage>;
  }

  const { totals, last30Days, salesLast30Days, orderStatusDistribution, recentOrders, lowStock } = dashboard.data;
  const statuses = (Object.keys(orderStatusMeta) as OrderStatus[])
    .map((status) => ({ key: status, label: orderStatusMeta[status].label, value: orderStatusDistribution[status] ?? 0 }))
    .filter((row) => row.value > 0);

  return (
    <AdminPage title="Dashboard" description="Live store performance. Figures are calculated from real orders; the last 30 days exclude unpaid and cancelled orders.">
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatTile label="Revenue, last 30 days" value={formatPrice(last30Days.revenue)} change={last30Days.revenueChange} icon={<IndianRupee className="h-4 w-4" aria-hidden />} to="/admin/analytics" />
        <StatTile label="Orders, last 30 days" value={formatNumber(last30Days.orders)} change={last30Days.ordersChange} icon={<ReceiptText className="h-4 w-4" aria-hidden />} to="/admin/orders" />
        <StatTile label="Average order value" value={formatPrice(Math.round(last30Days.averageOrderValue))} change={last30Days.averageOrderValueChange} icon={<IndianRupee className="h-4 w-4" aria-hidden />} />
        <StatTile label="New customers, last 30 days" value={formatNumber(last30Days.newCustomers)} change={last30Days.newCustomersChange} icon={<Users className="h-4 w-4" aria-hidden />} to="/admin/customers" />
      </div>

      <div className="mt-4 grid gap-4 sm:grid-cols-3 xl:grid-cols-6">
        <MiniStat label="Total sales" value={formatCompactPrice(totals.totalSales)} />
        <MiniStat label="All orders" value={formatNumber(totals.orders)} />
        <MiniStat label="Customers" value={formatNumber(totals.customers)} />
        <MiniStat label="Products" value={formatNumber(totals.products)} icon={<Boxes className="h-4 w-4" aria-hidden />} />
        <MiniStat label="To fulfil" value={formatNumber(totals.pendingOrders)} icon={<Timer className="h-4 w-4" aria-hidden />} to="/admin/orders?status=PLACED" />
        <MiniStat label="Low stock" value={formatNumber(totals.lowStockProducts)} icon={<AlertTriangle className="h-4 w-4" aria-hidden />} to="/admin/inventory?lowStock=true" warn={totals.lowStockProducts > 0} />
      </div>

      <div className="mt-6 grid gap-6 xl:grid-cols-2">
        <Card>
          <CardHeader title="Revenue per day" description="Last 30 days, paid and cash-on-delivery orders (IST)" />
          <div className="p-5">
            <TimeSeriesChart data={salesLast30Days} xKey="period" yKey="revenue" name="Revenue" formatX={formatDay} formatY={formatCompactPrice} />
            <DataTableDetails caption="Revenue per day" columns={['Day', 'Revenue', 'Orders', 'Units']}
              rows={salesLast30Days.map((point) => [formatDay(point.period), formatPrice(point.revenue), point.orders, point.units])} />
          </div>
        </Card>
        <Card>
          <CardHeader title="Orders per day" description="Last 30 days" />
          <div className="p-5">
            <ColumnChart data={salesLast30Days} xKey="period" yKey="orders" name="Orders" formatX={formatDay} formatY={(value) => formatNumber(value)} height={260} />
            <DataTableDetails caption="Orders per day" columns={['Day', 'Orders']} rows={salesLast30Days.map((point) => [formatDay(point.period), point.orders])} />
          </div>
        </Card>
      </div>

      <div className="mt-6 grid gap-6 xl:grid-cols-[1fr_22rem]">
        <Card>
          <CardHeader title="Recent orders" action={<Link to="/admin/orders" className="text-sm font-semibold text-brand-700 hover:underline">View all</Link>} />
          <TableWrap>
            <Table>
              <thead><tr><Th>Order</Th><Th>Customer</Th><Th>Status</Th><Th className="text-right">Total</Th><Th>When</Th></tr></thead>
              <tbody>
                {recentOrders.map((order) => (
                  <tr key={order.id} className="hover:bg-stone-50">
                    <Td><Link to={`/admin/orders/${order.id}`} className="font-semibold text-slate-900 hover:text-brand-700">{order.orderNumber}</Link></Td>
                    <Td>{order.customerName}</Td>
                    <Td><OrderStatusBadge status={order.status} /></Td>
                    <Td className="text-right font-medium tabular-nums">{formatPrice(order.totalAmount)}</Td>
                    <Td className="text-slate-500">{formatRelative(order.placedAt ?? order.createdAt)}</Td>
                  </tr>
                ))}
              </tbody>
            </Table>
          </TableWrap>
        </Card>
        <div className="space-y-6">
          <Card>
            <CardHeader title="Orders by status" description="All orders to date" />
            <div className="p-5">
              <BarList items={statuses} formatValue={(value) => formatNumber(value)} />
            </div>
          </Card>
          <Card>
            <CardHeader title="Low stock" action={<Link to="/admin/inventory?lowStock=true" className="text-sm font-semibold text-brand-700 hover:underline">Manage</Link>} />
            {lowStock.length === 0 ? (
              <p className="flex items-center gap-2 px-5 py-6 text-sm text-slate-500"><PackageCheck className="h-4 w-4 text-emerald-600" aria-hidden />Everything is well stocked.</p>
            ) : (
              <ul className="divide-y divide-stone-100">
                {lowStock.map((item) => (
                  <li key={item.variantId} className="flex items-center justify-between gap-3 px-5 py-3 text-sm">
                    <span className="min-w-0">
                      <span className="block truncate font-medium text-slate-900">{item.productName}</span>
                      <span className="block text-xs text-slate-500">{item.sku}</span>
                    </span>
                    <Badge tone={item.available === 0 ? 'danger' : 'warning'}>{item.available} left</Badge>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>
      </div>
    </AdminPage>
  );
}

function MiniStat({ label, value, icon, to, warn = false }: { label: string; value: string; icon?: ReactNode; to?: string; warn?: boolean }) {
  const content = (
    <>
      <p className="flex items-center justify-between text-xs font-medium text-slate-500">{label}{icon && <span className={warn ? 'text-amber-600' : 'text-slate-400'}>{icon}</span>}</p>
      <p className="mt-1 text-lg font-bold text-slate-900">{value}</p>
    </>
  );
  const classes = 'block rounded-xl border border-stone-200 bg-white px-4 py-3';
  return to ? <Link to={to} className={`${classes} hover:shadow-sm`}>{content}</Link> : <div className={classes}>{content}</div>;
}
