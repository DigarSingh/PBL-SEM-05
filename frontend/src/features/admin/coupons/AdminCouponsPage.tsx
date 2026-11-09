import { zodResolver } from '@hookform/resolvers/zod';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Pencil, Plus, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ConfirmDialog, Modal } from '@/components/ui/Dialog';
import { ErrorState } from '@/components/ui/Feedback';
import { Checkbox, FormField, Input, Select } from '@/components/ui/Field';
import { Pagination } from '@/components/ui/Pagination';
import { Table, TableWrap, Td, Th } from '@/components/ui/Table';
import { errorMessage } from '@/lib/errors';
import { applyServerErrors, emptyToNull, numeric } from '@/lib/forms';
import { formatDate, formatPrice } from '@/lib/format';
import type { Coupon } from '@/lib/types';
import { useNow } from '@/lib/useNow';
import { adminApi, type CouponInput } from '../api';
import { AdminPage } from '../components/AdminPage';
import { SearchInput } from '../components/SearchInput';
import { TableEmpty, TableSkeleton } from '../components/TableStates';
import { pageFrom, useParamsState } from '../useParamsState';

const DEFAULTS = { q: '', active: '', page: '' };

function toLocalInput(iso: string | null): string {
  if (!iso) {
    return '';
  }
  const date = new Date(iso);
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function fromLocalInput(value: string): string | null {
  return value ? new Date(value).toISOString() : null;
}

const couponSchema = z.object({
  code: z.string().trim().min(3, 'At least 3 characters').max(40).regex(/^[A-Za-z0-9_-]+$/, "Letters, digits, '_' and '-' only"),
  description: z.string().trim().max(255),
  discountType: z.enum(['PERCENTAGE', 'FIXED']),
  discountValue: numeric(z.number({ error: 'Enter the discount' }).positive('Must be more than 0')),
  minOrderAmount: numeric(z.number().min(0).optional()),
  maxDiscountAmount: numeric(z.number().positive().optional()),
  startsAt: z.string(),
  expiresAt: z.string(),
  usageLimit: numeric(z.number().int().min(1).optional()),
  perUserLimit: numeric(z.number().int().min(1).optional()),
  active: z.boolean(),
}).superRefine((values, context) => {
  if (values.discountType === 'PERCENTAGE' && values.discountValue > 100) {
    context.addIssue({ code: 'custom', path: ['discountValue'], message: 'A percentage cannot exceed 100' });
  }
  if (values.startsAt && values.expiresAt && new Date(values.expiresAt) <= new Date(values.startsAt)) {
    context.addIssue({ code: 'custom', path: ['expiresAt'], message: 'Must be after the start' });
  }
});

export default function AdminCouponsPage() {
  const [filters, setFilters] = useParamsState(DEFAULTS);
  const queryClient = useQueryClient();
  const now = useNow(60_000);
  const [editing, setEditing] = useState<Coupon | 'new' | null>(null);
  const [deleting, setDeleting] = useState<Coupon | null>(null);
  const query = { q: filters.q || undefined, active: filters.active === '' ? undefined : filters.active === 'true', page: pageFrom(filters.page), size: 20 };
  const coupons = useQuery({ queryKey: ['admin', 'coupons', query], queryFn: () => adminApi.coupons(query), placeholderData: keepPreviousData });
  const remove = useMutation({
    mutationFn: (coupon: Coupon) => adminApi.deleteCoupon(coupon.id),
    onSuccess: async () => {
      setDeleting(null);
      await Promise.all([queryClient.invalidateQueries({ queryKey: ['admin', 'coupons'] }), queryClient.invalidateQueries({ queryKey: ['offers'] })]);
      toast.success('Coupon deleted');
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const state = (coupon: Coupon) => {
    if (!coupon.active) return <Badge tone="neutral">Inactive</Badge>;
    if (coupon.expiresAt && Date.parse(coupon.expiresAt) < now) return <Badge tone="danger">Expired</Badge>;
    if (coupon.startsAt && Date.parse(coupon.startsAt) > now) return <Badge tone="info">Scheduled</Badge>;
    if (coupon.usageLimit !== null && coupon.usedCount >= coupon.usageLimit) return <Badge tone="warning">Used up</Badge>;
    return <Badge tone="success">Live</Badge>;
  };

  return (
    <AdminPage title="Coupons" description="Discounts are validated and calculated on the server at checkout."
      actions={<Button icon={<Plus className="h-4 w-4" aria-hidden />} onClick={() => setEditing('new')}>New coupon</Button>}>
      <Card className="mb-4 flex flex-wrap items-center gap-3 p-4">
        <SearchInput key={filters.q} value={filters.q} onSearch={(q) => setFilters({ q })} placeholder="Code or description" className="w-full sm:w-72" />
        <Select aria-label="Active" value={filters.active} onChange={(event) => setFilters({ active: event.target.value })} className="h-9 w-auto">
          <option value="">All coupons</option>
          <option value="true">Active</option>
          <option value="false">Inactive</option>
        </Select>
      </Card>
      <Card>
        {coupons.isPending ? <TableSkeleton /> : coupons.isError ? (
          <div className="p-4"><ErrorState error={coupons.error} onRetry={() => void coupons.refetch()} /></div>
        ) : coupons.data.items.length === 0 ? <TableEmpty message="No coupons yet." /> : (
          <TableWrap>
            <Table className="min-w-[900px]">
              <thead><tr><Th>Code</Th><Th>Discount</Th><Th>Conditions</Th><Th>Valid</Th><Th className="text-right">Used</Th><Th>Status</Th><Th /></tr></thead>
              <tbody>
                {coupons.data.items.map((coupon) => (
                  <tr key={coupon.id} className="hover:bg-stone-50">
                    <Td><span className="font-mono font-bold text-slate-900">{coupon.code}</span>{coupon.description && <span className="block max-w-56 truncate text-xs text-slate-500">{coupon.description}</span>}</Td>
                    <Td>{coupon.discountType === 'PERCENTAGE' ? `${coupon.discountValue}%` : formatPrice(coupon.discountValue)}{coupon.maxDiscountAmount ? <span className="block text-xs text-slate-500">up to {formatPrice(coupon.maxDiscountAmount)}</span> : null}</Td>
                    <Td className="text-xs text-slate-600">{coupon.minOrderAmount ? `Orders over ${formatPrice(coupon.minOrderAmount)}` : 'Any order'}{coupon.perUserLimit ? <span className="block">{coupon.perUserLimit} per customer</span> : null}</Td>
                    <Td className="whitespace-nowrap text-xs text-slate-600">{coupon.startsAt ? formatDate(coupon.startsAt) : 'Now'} – {coupon.expiresAt ? formatDate(coupon.expiresAt) : 'No end'}</Td>
                    <Td className="text-right tabular-nums">{coupon.usedCount}{coupon.usageLimit ? ` / ${coupon.usageLimit}` : ''}</Td>
                    <Td>{state(coupon)}</Td>
                    <Td className="whitespace-nowrap text-right">
                      <Button size="icon" variant="ghost" className="h-8 w-8" aria-label={`Edit ${coupon.code}`} onClick={() => setEditing(coupon)}><Pencil className="h-4 w-4" aria-hidden /></Button>
                      <Button size="icon" variant="ghost" className="h-8 w-8 text-rose-700" aria-label={`Delete ${coupon.code}`} onClick={() => setDeleting(coupon)}><Trash2 className="h-4 w-4" aria-hidden /></Button>
                    </Td>
                  </tr>
                ))}
              </tbody>
            </Table>
          </TableWrap>
        )}
      </Card>
      {coupons.data && <Pagination className="mt-4" page={coupons.data.page} totalPages={coupons.data.totalPages} onChange={(page) => setFilters({ page: String(page + 1) })} />}
      {editing !== null && <CouponForm coupon={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
      <ConfirmDialog open={deleting !== null} onClose={() => setDeleting(null)} title={`Delete ${deleting?.code ?? 'coupon'}?`}
        message="Orders that already used it keep their discount. Deactivate it instead if you may reuse it later." confirmLabel="Delete"
        loading={remove.isPending} onConfirm={() => {
          if (deleting) {
            remove.mutate(deleting);
          }
        }} />
    </AdminPage>
  );
}

function CouponForm({ coupon, onClose }: { coupon: Coupon | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const { register, control, handleSubmit, setError, formState: { errors } } = useForm({
    resolver: zodResolver(couponSchema),
    defaultValues: {
      code: coupon?.code ?? '',
      description: coupon?.description ?? '',
      discountType: coupon?.discountType ?? 'PERCENTAGE',
      discountValue: coupon ? String(coupon.discountValue) : '',
      minOrderAmount: coupon?.minOrderAmount != null ? String(coupon.minOrderAmount) : '',
      maxDiscountAmount: coupon?.maxDiscountAmount != null ? String(coupon.maxDiscountAmount) : '',
      startsAt: toLocalInput(coupon?.startsAt ?? null),
      expiresAt: toLocalInput(coupon?.expiresAt ?? null),
      usageLimit: coupon?.usageLimit != null ? String(coupon.usageLimit) : '',
      perUserLimit: coupon?.perUserLimit != null ? String(coupon.perUserLimit) : '',
      active: coupon?.active ?? true,
    },
  });
  const type = useWatch({ control, name: 'discountType' });
  const save = useMutation({
    mutationFn: (values: z.output<typeof couponSchema>) => {
      const input: CouponInput = {
        code: values.code.toUpperCase(),
        description: emptyToNull(values.description),
        discountType: values.discountType,
        discountValue: values.discountValue,
        minOrderAmount: values.minOrderAmount ?? null,
        maxDiscountAmount: values.discountType === 'PERCENTAGE' ? values.maxDiscountAmount ?? null : null,
        startsAt: fromLocalInput(values.startsAt),
        expiresAt: fromLocalInput(values.expiresAt),
        usageLimit: values.usageLimit ?? null,
        perUserLimit: values.perUserLimit ?? null,
        active: values.active,
      };
      return coupon ? adminApi.updateCoupon(coupon.id, input) : adminApi.createCoupon(input);
    },
    onSuccess: async () => {
      await Promise.all([queryClient.invalidateQueries({ queryKey: ['admin', 'coupons'] }), queryClient.invalidateQueries({ queryKey: ['offers'] })]);
      toast.success(coupon ? 'Coupon updated' : 'Coupon created');
      onClose();
    },
    onError: (error) => {
      if (!applyServerErrors(error, setError)) {
        toast.error(errorMessage(error));
      }
    },
  });
  return (
    <Modal open onClose={onClose} size="lg" title={coupon ? `Edit ${coupon.code}` : 'New coupon'}
      footer={<>
        <Button variant="secondary" onClick={onClose} disabled={save.isPending}>Cancel</Button>
        <Button type="submit" form="coupon-form" loading={save.isPending}>Save coupon</Button>
      </>}>
      <form id="coupon-form" noValidate className="grid gap-4 sm:grid-cols-2" onSubmit={handleSubmit((values) => save.mutate(values))}>
        <FormField label="Code" error={errors.code?.message} required>{(field) => <Input {...field} className="font-mono uppercase" {...register('code')} />}</FormField>
        <FormField label="Description" error={errors.description?.message} hint="Shown to shoppers">{(field) => <Input {...field} {...register('description')} />}</FormField>
        <FormField label="Discount type" error={errors.discountType?.message}>
          {(field) => <Select {...field} {...register('discountType')}><option value="PERCENTAGE">Percentage</option><option value="FIXED">Fixed amount (₹)</option></Select>}
        </FormField>
        <FormField label={type === 'PERCENTAGE' ? 'Discount (%)' : 'Discount (₹)'} error={errors.discountValue?.message} required>
          {(field) => <Input {...field} inputMode="decimal" {...register('discountValue')} />}
        </FormField>
        <FormField label="Minimum order (₹)" error={errors.minOrderAmount?.message} hint="Blank for any order">{(field) => <Input {...field} inputMode="decimal" {...register('minOrderAmount')} />}</FormField>
        {type === 'PERCENTAGE' && (
          <FormField label="Maximum discount (₹)" error={errors.maxDiscountAmount?.message} hint="Optional cap">{(field) => <Input {...field} inputMode="decimal" {...register('maxDiscountAmount')} />}</FormField>
        )}
        <FormField label="Starts" error={errors.startsAt?.message} hint="Blank = immediately">{(field) => <Input {...field} type="datetime-local" {...register('startsAt')} />}</FormField>
        <FormField label="Ends" error={errors.expiresAt?.message} hint="Blank = no end date">{(field) => <Input {...field} type="datetime-local" {...register('expiresAt')} />}</FormField>
        <FormField label="Total uses allowed" error={errors.usageLimit?.message} hint="Blank = unlimited">{(field) => <Input {...field} inputMode="numeric" {...register('usageLimit')} />}</FormField>
        <FormField label="Uses per customer" error={errors.perUserLimit?.message} hint="Blank = unlimited">{(field) => <Input {...field} inputMode="numeric" {...register('perUserLimit')} />}</FormField>
        <div className="sm:col-span-2"><Checkbox label="Active" {...register('active')} /></div>
      </form>
    </Modal>
  );
}
