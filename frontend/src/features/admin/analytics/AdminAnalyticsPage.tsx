import { useQuery } from '@tanstack/react-query';
import { Brain, History, TrendingDown, TrendingUp } from 'lucide-react';
import { useState } from 'react';
import { BarList } from '@/components/charts/BarList';
import { ColumnChart } from '@/components/charts/ColumnChart';
import { DataTableDetails } from '@/components/charts/DataTableDetails';
import { StatTile } from '@/components/charts/StatTile';
import { TimeSeriesChart } from '@/components/charts/TimeSeriesChart';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { ErrorState, InlineAlert, Skeleton } from '@/components/ui/Feedback';
import { Input, Select } from '@/components/ui/Field';
import { Table, TableWrap, Td, Th } from '@/components/ui/Table';
import { cn } from '@/lib/cn';
import { formatCompactPrice, formatDateTime, formatDay, formatMonth, formatNumber, formatPercent, formatPrice, orderStatusMeta, paymentMethodLabel } from '@/lib/format';
import type { Insights, OrderStatus } from '@/lib/types';
import { adminApi } from '../api';
import { AdminPage } from '../components/AdminPage';

function isoDay(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function rangeFor(days: number): { from: string; to: string } {
  const to = new Date();
  const from = new Date();
  from.setDate(to.getDate() - (days - 1));
  return { from: isoDay(from), to: isoDay(to) };
}

const PRESETS = [7, 30, 90, 180];

export default function AdminAnalyticsPage() {
  const [range, setRange] = useState(() => ({ ...rangeFor(30), days: 30 as number | null }));
  const [granularity, setGranularity] = useState('day');
  const { from, to } = range;
  const sales = useQuery({ queryKey: ['admin', 'analytics', 'sales', from, to, granularity], queryFn: () => adminApi.sales(from, to, granularity) });
  const products = useQuery({ queryKey: ['admin', 'analytics', 'products', from, to], queryFn: () => adminApi.productAnalytics(from, to) });
  const categories = useQuery({ queryKey: ['admin', 'analytics', 'categories', from, to], queryFn: () => adminApi.categoryAnalytics(from, to) });
  const customers = useQuery({ queryKey: ['admin', 'analytics', 'customers', from, to], queryFn: () => adminApi.customerAnalytics(from, to) });
  const inventory = useQuery({ queryKey: ['admin', 'analytics', 'inventory'], queryFn: adminApi.inventoryAnalytics });
  const insights = useQuery({ queryKey: ['admin', 'analytics', 'insights', from, to], queryFn: () => adminApi.insights(from, to) });
  const formatPeriod = granularity === 'month' ? formatMonth : formatDay;
  const periodLabel = granularity === 'month' ? 'Month' : granularity === 'week' ? 'Week of' : 'Day';

  return (
    <AdminPage title="Analytics & insights" description="Historical figures come straight from orders. Forecasts are model predictions and are labelled as such.">
      <Card className="sticky top-14 z-20 mb-6 flex flex-wrap items-end gap-3 p-4">
        <div className="flex gap-1" role="group" aria-label="Date range">
          {PRESETS.map((days) => (
            <Button key={days} size="sm" variant={range.days === days ? 'primary' : 'secondary'} onClick={() => setRange({ ...rangeFor(days), days })}>
              {days} days
            </Button>
          ))}
        </div>
        <label className="text-xs text-slate-500">From
          <Input type="date" value={from} max={to} onChange={(event) => event.target.value && setRange({ from: event.target.value, to, days: null })} className="mt-1 h-9 w-auto" />
        </label>
        <label className="text-xs text-slate-500">To
          <Input type="date" value={to} min={from} onChange={(event) => event.target.value && setRange({ from, to: event.target.value, days: null })} className="mt-1 h-9 w-auto" />
        </label>
        <label className="text-xs text-slate-500">Group by
          <Select value={granularity} onChange={(event) => setGranularity(event.target.value)} className="mt-1 h-9 w-auto">
            <option value="day">Day</option>
            <option value="week">Week</option>
            <option value="month">Month</option>
          </Select>
        </label>
      </Card>

      <section aria-labelledby="sales-heading" className="space-y-4">
        <h2 id="sales-heading" className="text-lg font-bold text-slate-900">Sales</h2>
        {sales.isPending ? <Skeleton className="h-28" /> : sales.isError ? <ErrorState error={sales.error} onRetry={() => void sales.refetch()} /> : (
          <>
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              <StatTile label="Revenue" value={formatPrice(sales.data.revenue)} change={sales.data.revenueChange} changeLabel="vs the previous period" />
              <StatTile label="Orders" value={formatNumber(sales.data.orders)} />
              <StatTile label="Units sold" value={formatNumber(sales.data.units)} />
              <StatTile label="Average order value" value={formatPrice(Math.round(sales.data.averageOrderValue))} />
            </div>
            <div className="grid gap-6 xl:grid-cols-2">
              <Card>
                <CardHeader title="Revenue" description={`Per ${granularity}`} />
                <div className="p-5">
                  <TimeSeriesChart data={sales.data.series} xKey="period" yKey="revenue" name="Revenue" formatX={formatPeriod} formatY={formatCompactPrice} />
                  <DataTableDetails caption="Revenue by period" columns={[periodLabel, 'Revenue', 'Orders', 'Units']}
                    rows={sales.data.series.map((point) => [formatPeriod(point.period), formatPrice(point.revenue), point.orders, point.units])} />
                </div>
              </Card>
              <Card>
                <CardHeader title="Orders" description={`Per ${granularity}`} />
                <div className="p-5">
                  <ColumnChart data={sales.data.series} xKey="period" yKey="orders" name="Orders" formatX={formatPeriod} formatY={(value) => formatNumber(value)} height={260} />
                  <DataTableDetails caption="Orders by period" columns={[periodLabel, 'Orders']} rows={sales.data.series.map((point) => [formatPeriod(point.period), point.orders])} />
                </div>
              </Card>
            </div>
            <div className="grid gap-6 lg:grid-cols-2">
              <Card>
                <CardHeader title="Payment methods" />
                <div className="grid gap-3 p-5 sm:grid-cols-2">
                  {sales.data.paymentMethods.length === 0 ? <p className="text-sm text-slate-500">No orders in this period.</p> : sales.data.paymentMethods.map((method) => (
                    <div key={method.method} className="rounded-xl bg-stone-50 p-4">
                      <p className="text-sm text-slate-500">{paymentMethodLabel(method.method)}</p>
                      <p className="mt-1 text-xl font-bold text-slate-900">{formatPrice(method.revenue)}</p>
                      <p className="text-sm text-slate-500">{formatNumber(method.orders)} orders</p>
                    </div>
                  ))}
                </div>
              </Card>
              <Card>
                <CardHeader title="Orders by status" description="Orders created in this period" />
                <div className="p-5">
                  <BarList formatValue={(value) => formatNumber(value)}
                    items={(Object.keys(orderStatusMeta) as OrderStatus[]).map((status) => ({ key: status, label: orderStatusMeta[status].label, value: sales.data.orderStatusDistribution[status] ?? 0 })).filter((row) => row.value > 0)} />
                </div>
              </Card>
            </div>
          </>
        )}
      </section>

      <section aria-labelledby="catalog-heading" className="mt-10 space-y-4">
        <h2 id="catalog-heading" className="text-lg font-bold text-slate-900">Products & categories</h2>
        <div className="grid gap-6 xl:grid-cols-[1fr_24rem]">
          <Card>
            <CardHeader title="Best sellers" description="By revenue; conversion = units sold per product-page view" />
            {products.isPending ? <Skeleton className="m-5 h-48" /> : products.isError ? <div className="p-5"><ErrorState error={products.error} /></div> : products.data.topProducts.length === 0 ? (
              <p className="p-5 text-sm text-slate-500">No sales in this period.</p>
            ) : (
              <TableWrap>
                <Table>
                  <thead><tr><Th>Product</Th><Th className="text-right">Units</Th><Th className="text-right">Revenue</Th><Th className="text-right">Views</Th><Th className="text-right">Conversion</Th><Th className="text-right">In stock</Th></tr></thead>
                  <tbody className="tabular-nums">
                    {products.data.topProducts.map((row) => (
                      <tr key={row.productId}><Td className="font-medium text-slate-900">{row.name}</Td><Td className="text-right">{row.units}</Td><Td className="text-right">{formatPrice(row.revenue)}</Td><Td className="text-right">{formatNumber(row.views)}</Td><Td className="text-right">{row.conversionRate === null ? '-' : formatPercent(row.conversionRate, 1)}</Td><Td className="text-right">{row.available ?? '-'}</Td></tr>
                    ))}
                  </tbody>
                </Table>
              </TableWrap>
            )}
          </Card>
          <Card>
            <CardHeader title="Revenue by category" />
            <div className="p-5">
              {categories.isPending ? <Skeleton className="h-48" /> : categories.isError ? <ErrorState error={categories.error} /> : (
                <BarList formatValue={formatCompactPrice}
                  items={categories.data.categories.map((row) => ({ key: row.categoryId, label: row.name, value: row.revenue, detail: formatPercent(row.revenueShare * 100, 0) }))} />
              )}
            </div>
          </Card>
        </div>
        {products.data && products.data.slowMoving.length > 0 && (
          <Card>
            <CardHeader title="Slow movers" description="Products in stock that sold little or nothing in this period" />
            <TableWrap>
              <Table>
                <thead><tr><Th>Product</Th><Th className="text-right">Units sold</Th><Th className="text-right">Views</Th><Th className="text-right">In stock</Th></tr></thead>
                <tbody className="tabular-nums">
                  {products.data.slowMoving.map((row) => (
                    <tr key={row.productId}><Td className="font-medium text-slate-900">{row.name}</Td><Td className="text-right">{row.units}</Td><Td className="text-right">{formatNumber(row.views)}</Td><Td className="text-right">{row.available ?? '-'}</Td></tr>
                  ))}
                </tbody>
              </Table>
            </TableWrap>
          </Card>
        )}
      </section>

      <section aria-labelledby="customers-heading" className="mt-10 space-y-4">
        <h2 id="customers-heading" className="text-lg font-bold text-slate-900">Customers</h2>
        {customers.isPending ? <Skeleton className="h-28" /> : customers.isError ? <ErrorState error={customers.error} /> : (
          <>
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              <StatTile label="Customers (all time)" value={formatNumber(customers.data.totalCustomers)} />
              <StatTile label="New in period" value={formatNumber(customers.data.newCustomers)} />
              <StatTile label="Bought in period" value={formatNumber(customers.data.purchasingCustomers)} />
              <StatTile label="Repeat purchase rate" value={customers.data.repeatPurchaseRate === null ? '-' : formatPercent(customers.data.repeatPurchaseRate, 1)} />
            </div>
            <div className="grid gap-6 xl:grid-cols-2">
              <Card>
                <CardHeader title="New customers per day" />
                <div className="p-5">
                  <ColumnChart data={customers.data.newCustomersByDay} xKey="day" yKey="count" name="New customers" formatX={formatDay} formatY={(value) => formatNumber(value)} />
                  <DataTableDetails caption="New customers per day" columns={['Day', 'New customers']} rows={customers.data.newCustomersByDay.map((row) => [formatDay(row.day), row.count])} />
                </div>
              </Card>
              <Card>
                <CardHeader title="Top customers" description="By spend in this period" />
                <TableWrap>
                  <Table className="min-w-0">
                    <thead><tr><Th>Customer</Th><Th className="text-right">Orders</Th><Th className="text-right">Spent</Th></tr></thead>
                    <tbody className="tabular-nums">
                      {customers.data.topCustomers.map((row) => (
                        <tr key={row.userId}><Td><span className="block font-medium text-slate-900">{row.name}</span><span className="text-xs text-slate-500">{row.email}</span></Td><Td className="text-right">{row.orders}</Td><Td className="text-right">{formatPrice(row.spent)}</Td></tr>
                      ))}
                    </tbody>
                  </Table>
                </TableWrap>
              </Card>
            </div>
          </>
        )}
      </section>

      <section aria-labelledby="stock-heading" className="mt-10 space-y-4">
        <h2 id="stock-heading" className="text-lg font-bold text-slate-900">Stock cover</h2>
        <Card>
          <CardHeader title="Days of stock left at the last 30 days' sales rate" description="Lowest cover first" />
          {inventory.isPending ? <Skeleton className="m-5 h-40" /> : inventory.isError ? <div className="p-5"><ErrorState error={inventory.error} /></div> : (
            <TableWrap className="max-h-96 overflow-y-auto">
              <Table>
                <thead><tr><Th>Product</Th><Th className="text-right">Available</Th><Th className="text-right">Sold (30 days)</Th><Th className="text-right">Per day</Th><Th className="text-right">Days of cover</Th><Th>Status</Th></tr></thead>
                <tbody className="tabular-nums">
                  {[...inventory.data.products].sort((a, b) => (a.daysOfCover ?? Infinity) - (b.daysOfCover ?? Infinity)).map((row) => (
                    <tr key={row.productId}>
                      <Td className="font-medium text-slate-900">{row.name}</Td><Td className="text-right">{row.available}</Td><Td className="text-right">{row.unitsLast30Days}</Td>
                      <Td className="text-right">{row.dailyVelocity.toFixed(2)}</Td><Td className="text-right">{row.daysOfCover === null ? 'No recent sales' : Math.round(row.daysOfCover)}</Td>
                      <Td><Badge tone={row.status === 'OUT_OF_STOCK' || row.status === 'CRITICAL' ? 'danger' : row.status === 'LOW' || row.status === 'LOW_STOCK' ? 'warning' : 'neutral'}>{row.status.replaceAll('_', ' ').toLowerCase()}</Badge></Td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            </TableWrap>
          )}
        </Card>
      </section>

      <section aria-labelledby="insights-heading" className="mt-10 space-y-4">
        <h2 id="insights-heading" className="text-lg font-bold text-slate-900">Insights</h2>
        {insights.isPending ? <Skeleton className="h-64" /> : insights.isError ? <ErrorState error={insights.error} onRetry={() => void insights.refetch()} /> : (
          <div className="grid gap-6">
            <HistoricalInsights items={insights.data.historical} />
            <Forecast predictions={insights.data.predictions} />
          </div>
        )}
      </section>
    </AdminPage>
  );
}

function HistoricalInsights({ items }: { items: Insights['historical'] }) {
  return (
    <Card>
      <CardHeader title={<span className="flex items-center gap-2"><History className="h-4 w-4" aria-hidden />What happened</span>}
        description="Facts computed from your orders and page views in this period, not predictions." />
      {items.length === 0 ? <p className="p-5 text-sm text-slate-500">Nothing notable in this period.</p> : (
        <ul className="divide-y divide-stone-100">
          {items.map((item, index) => (
            <li key={`${item.type}-${index}`} className="flex gap-3 px-5 py-3">
              <Badge tone={item.severity === 'critical' || item.severity === 'HIGH' ? 'danger' : item.severity === 'warning' || item.severity === 'MEDIUM' ? 'warning' : 'info'} className="h-fit shrink-0">
                {item.severity.toLowerCase()}
              </Badge>
              <span className="text-sm">
                <span className="block font-semibold text-slate-900">{item.title}</span>
                <span className="text-slate-600">{item.message}</span>
              </span>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

function Forecast({ predictions }: { predictions: Insights['predictions'] }) {
  return (
    <Card>
      <CardHeader title={<span className="flex items-center gap-2"><Brain className="h-4 w-4" aria-hidden />Demand forecast <Badge tone="brand">Machine learning</Badge></span>}
        description={predictions.available
          ? `${predictions.model ?? 'Model'} · next ${predictions.horizonDays} days · ${predictions.leadTimeDays}-day restock lead time · generated ${formatDateTime(predictions.generatedAt)}`
          : 'Predictions from the AI service'} />
      <div className="space-y-3 p-5">
        <InlineAlert tone={predictions.available ? 'info' : 'warning'}>
          {predictions.message} Ranges are 80% prediction intervals: estimates, not guarantees.
        </InlineAlert>
        {predictions.available && predictions.items.length > 0 && (
          <TableWrap className="max-h-96 overflow-y-auto">
            <Table className="min-w-[560px]">
              <thead><tr><Th>Product</Th><Th className="text-right">Forecast units</Th><Th>Trend</Th><Th className="text-right">Cover (days)</Th><Th className="text-right">Suggested restock</Th></tr></thead>
              <tbody className="tabular-nums">
                {predictions.items.map((item) => (
                  <tr key={item.productId} className={cn(item.status !== 'OK' && 'text-slate-400')}>
                    <Td>
                      <span className="block font-medium text-slate-900">{item.name ?? `Product ${item.productId}`}</span>
                      {item.note && <span className="block text-xs text-slate-500">{item.note}</span>}
                    </Td>
                    <Td className="text-right">
                      {item.predictedUnits === null ? 'Not enough data' : <>{Math.round(item.predictedUnits)}<span className="block text-xs text-slate-500">{Math.round(item.lower ?? 0)}–{Math.round(item.upper ?? 0)}</span></>}
                    </Td>
                    <Td>
                      {item.trend === 'up' || item.trend === 'UP' ? <span className="inline-flex items-center gap-1 text-emerald-700"><TrendingUp className="h-4 w-4" aria-hidden />Rising</span>
                        : item.trend === 'down' || item.trend === 'DOWN' ? <span className="inline-flex items-center gap-1 text-rose-700"><TrendingDown className="h-4 w-4" aria-hidden />Falling</span>
                          : <span className="text-slate-500">{item.trend ? item.trend.toLowerCase() : '-'}</span>}
                    </Td>
                    <Td className="text-right">{item.daysOfCover === null ? '-' : Math.round(item.daysOfCover)}</Td>
                    <Td className="text-right font-semibold">{item.restockQuantity ? item.restockQuantity : '-'}</Td>
                  </tr>
                ))}
              </tbody>
            </Table>
          </TableWrap>
        )}
      </div>
    </Card>
  );
}
