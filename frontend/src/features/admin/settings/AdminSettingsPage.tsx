import { zodResolver } from '@hookform/resolvers/zod';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { ErrorState, InlineAlert, Skeleton } from '@/components/ui/Feedback';
import { Checkbox, FormField, Input } from '@/components/ui/Field';
import { Pagination } from '@/components/ui/Pagination';
import { Table, TableWrap, Td, Th } from '@/components/ui/Table';
import { errorMessage } from '@/lib/errors';
import { applyServerErrors, numeric } from '@/lib/forms';
import { formatDateTime } from '@/lib/format';
import type { StoreSettings } from '@/lib/types';
import { adminApi } from '../api';
import { AdminPage } from '../components/AdminPage';
import { SearchInput } from '../components/SearchInput';
import { TableEmpty, TableSkeleton } from '../components/TableStates';

const settingsSchema = z.object({
  storeName: z.string().trim().min(2).max(80),
  supportEmail: z.string().trim().pipe(z.email('Enter a valid email')),
  taxPercent: numeric(z.number({ error: 'Enter a rate' }).min(0).max(50, 'At most 50%')),
  pricesIncludeTax: z.boolean(),
  shippingFee: numeric(z.number({ error: 'Enter an amount' }).min(0)),
  freeShippingThreshold: numeric(z.number({ error: 'Enter an amount' }).min(0)),
  defaultLowStockThreshold: numeric(z.number({ error: 'Enter a number' }).int().min(0).max(10_000)),
  codEnabled: z.boolean(),
  codMaxOrderAmount: numeric(z.number().min(0).optional()),
  razorpayEnabled: z.boolean(),
  pendingPaymentTimeoutMinutes: numeric(z.number({ error: 'Enter minutes' }).int().min(5, 'At least 5').max(1440, 'At most 1440')),
});

export default function AdminSettingsPage() {
  const settings = useQuery({ queryKey: ['admin', 'settings'], queryFn: adminApi.settings });
  return (
    <AdminPage title="Settings" description="Store-wide rules applied by the server to every cart and order.">
      {settings.isPending ? <Skeleton className="h-96" /> : settings.isError ? <ErrorState error={settings.error} onRetry={() => void settings.refetch()} /> : (
        <SettingsForm key={settings.data.updatedAt} settings={settings.data} />
      )}
      <AuditLog />
    </AdminPage>
  );
}

function SettingsForm({ settings }: { settings: StoreSettings }) {
  const queryClient = useQueryClient();
  const { register, handleSubmit, setError, formState: { errors, isDirty } } = useForm({
    resolver: zodResolver(settingsSchema),
    defaultValues: {
      storeName: settings.storeName,
      supportEmail: settings.supportEmail,
      taxPercent: String(Math.round(settings.taxRate * 10000) / 100),
      pricesIncludeTax: settings.pricesIncludeTax,
      shippingFee: String(settings.shippingFee),
      freeShippingThreshold: String(settings.freeShippingThreshold),
      defaultLowStockThreshold: String(settings.defaultLowStockThreshold),
      codEnabled: settings.codEnabled,
      codMaxOrderAmount: settings.codMaxOrderAmount === null ? '' : String(settings.codMaxOrderAmount),
      razorpayEnabled: settings.razorpayEnabled,
      pendingPaymentTimeoutMinutes: String(settings.pendingPaymentTimeoutMinutes),
    },
  });
  const save = useMutation({
    mutationFn: (values: z.output<typeof settingsSchema>) => adminApi.updateSettings({
      storeName: values.storeName,
      supportEmail: values.supportEmail,
      taxRate: Math.round(values.taxPercent * 100) / 10000,
      pricesIncludeTax: values.pricesIncludeTax,
      shippingFee: values.shippingFee,
      freeShippingThreshold: values.freeShippingThreshold,
      defaultLowStockThreshold: values.defaultLowStockThreshold,
      codEnabled: values.codEnabled,
      codMaxOrderAmount: values.codMaxOrderAmount ?? null,
      razorpayEnabled: values.razorpayEnabled,
      pendingPaymentTimeoutMinutes: values.pendingPaymentTimeoutMinutes,
    }),
    onSuccess: async (saved) => {
      queryClient.setQueryData(['admin', 'settings'], saved);
      await Promise.all([queryClient.invalidateQueries({ queryKey: ['settings'] }), queryClient.invalidateQueries({ queryKey: ['me', 'cart'] })]);
      toast.success('Settings saved');
    },
    onError: (error) => {
      if (!applyServerErrors(error, setError)) {
        toast.error(errorMessage(error));
      }
    },
  });
  return (
    <form noValidate onSubmit={handleSubmit((values) => save.mutate(values))} className="grid gap-6 xl:grid-cols-2">
      <Card>
        <CardHeader title="Store" />
        <div className="grid gap-4 p-5 sm:grid-cols-2">
          <FormField label="Store name" error={errors.storeName?.message}>{(field) => <Input {...field} {...register('storeName')} />}</FormField>
          <FormField label="Support email" error={errors.supportEmail?.message}>{(field) => <Input {...field} type="email" {...register('supportEmail')} />}</FormField>
          <FormField label="Default low-stock alert (units)" error={errors.defaultLowStockThreshold?.message}>{(field) => <Input {...field} inputMode="numeric" {...register('defaultLowStockThreshold')} />}</FormField>
        </div>
      </Card>
      <Card>
        <CardHeader title="Tax & delivery" />
        <div className="grid gap-4 p-5 sm:grid-cols-2">
          <FormField label="GST rate (%)" error={errors.taxPercent?.message}>{(field) => <Input {...field} inputMode="decimal" {...register('taxPercent')} />}</FormField>
          <div className="flex items-end pb-2"><Checkbox label="Prices include GST" {...register('pricesIncludeTax')} /></div>
          <FormField label="Delivery fee (₹)" error={errors.shippingFee?.message}>{(field) => <Input {...field} inputMode="decimal" {...register('shippingFee')} />}</FormField>
          <FormField label="Free delivery above (₹)" error={errors.freeShippingThreshold?.message}>{(field) => <Input {...field} inputMode="decimal" {...register('freeShippingThreshold')} />}</FormField>
        </div>
      </Card>
      <Card className="xl:col-span-2">
        <CardHeader title="Payments" />
        <div className="grid gap-4 p-5 sm:grid-cols-2 lg:grid-cols-4">
          <div className="space-y-2">
            <Checkbox label="Accept online payments (Razorpay)" disabled={!settings.razorpayConfigured} {...register('razorpayEnabled')} />
            {settings.razorpayConfigured
              ? <Badge tone={settings.razorpayEnabled ? 'success' : 'neutral'}>Keys configured</Badge>
              : <p className="text-xs text-amber-800">Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET on the server to enable.</p>}
          </div>
          <FormField label="Unpaid order hold (minutes)" error={errors.pendingPaymentTimeoutMinutes?.message} hint="Stock is reserved this long for online payment.">
            {(field) => <Input {...field} inputMode="numeric" {...register('pendingPaymentTimeoutMinutes')} />}
          </FormField>
          <div className="flex items-end pb-2"><Checkbox label="Offer Cash on Delivery" {...register('codEnabled')} /></div>
          <FormField label="COD limit (₹)" error={errors.codMaxOrderAmount?.message} hint="Blank for no limit">
            {(field) => <Input {...field} inputMode="decimal" {...register('codMaxOrderAmount')} />}
          </FormField>
        </div>
      </Card>
      <div className="flex items-center gap-3 xl:col-span-2">
        <Button type="submit" loading={save.isPending} disabled={!isDirty}>Save settings</Button>
        <span className="text-xs text-slate-500">Last changed {formatDateTime(settings.updatedAt)}</span>
      </div>
      {save.isError && Object.keys(errors).length === 0 && <InlineAlert tone="danger" className="xl:col-span-2">{errorMessage(save.error)}</InlineAlert>}
    </form>
  );
}

function AuditLog() {
  const [action, setAction] = useState('');
  const [page, setPage] = useState(0);
  const logs = useQuery({ queryKey: ['admin', 'audit', action, page], queryFn: () => adminApi.auditLogs({ action: action || undefined, page, size: 25 }), placeholderData: keepPreviousData });
  return (
    <Card className="mt-8">
      <CardHeader title="Audit log" description="Security-relevant and administrative actions, newest first."
        action={<SearchInput value={action} onSearch={(value) => {
          setAction(value.toUpperCase());
          setPage(0);
        }} placeholder="Filter by action, e.g. ORDER_STATUS_CHANGED" className="w-72" />} />
      {logs.isPending ? <TableSkeleton rows={5} /> : logs.isError ? <div className="p-4"><ErrorState error={logs.error} /></div> : logs.data.items.length === 0 ? <TableEmpty message="No entries." /> : (
        <TableWrap>
          <Table className="min-w-[900px]">
            <thead><tr><Th>When</Th><Th>Action</Th><Th>Who</Th><Th>Target</Th><Th>Details</Th><Th>IP</Th></tr></thead>
            <tbody>
              {logs.data.items.map((log) => (
                <tr key={log.id}>
                  <Td className="whitespace-nowrap text-slate-500">{formatDateTime(log.createdAt)}</Td>
                  <Td className="font-mono text-xs">{log.action}</Td>
                  <Td>{log.actorEmail ?? 'System'}</Td>
                  <Td className="text-xs text-slate-600">{log.entityType ? `${log.entityType} ${log.entityId ?? ''}` : '-'}</Td>
                  <Td className="max-w-xs truncate font-mono text-xs text-slate-500" title={log.details ?? ''}>{log.details ?? '-'}</Td>
                  <Td className="text-xs text-slate-500">{log.ipAddress ?? '-'}</Td>
                </tr>
              ))}
            </tbody>
          </Table>
        </TableWrap>
      )}
      {logs.data && <Pagination className="p-4" page={logs.data.page} totalPages={logs.data.totalPages} onChange={setPage} />}
    </Card>
  );
}
