import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Pencil, Plus, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ConfirmDialog, Modal } from '@/components/ui/Dialog';
import { ErrorState } from '@/components/ui/Feedback';
import { Checkbox, FormField, Input, Textarea } from '@/components/ui/Field';
import { Segmented } from '@/components/ui/Segmented';
import { Table, TableWrap, Td, Th } from '@/components/ui/Table';
import { errorMessage } from '@/lib/errors';
import { applyServerErrors, emptyToNull, numeric } from '@/lib/forms';
import type { Brand, Category } from '@/lib/types';
import { adminApi } from '../api';
import { AdminPage } from '../components/AdminPage';
import { TableEmpty, TableSkeleton } from '../components/TableStates';
import { useAdminBrands, useAdminCategories } from '../hooks';

const SLUG = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const URL_RULE = /^(https?:\/\/|\/).+/;

const entrySchema = z.object({
  name: z.string().trim().min(2, 'Enter a name').max(80),
  slug: z.string().trim().max(100).refine((value) => value === '' || SLUG.test(value), 'Lowercase words separated by hyphens'),
  description: z.string().trim().max(500),
  image: z.string().trim().max(1024).refine((value) => value === '' || URL_RULE.test(value), 'Use an http(s) link'),
  sortOrder: numeric(z.number().int().min(0).max(10_000).optional()),
  active: z.boolean(),
});

type Kind = 'categories' | 'brands';
type Entry = Category | Brand;

export default function AdminCatalogPage() {
  const [kind, setKind] = useState<Kind>('categories');
  const categories = useAdminCategories();
  const brands = useAdminBrands();
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<Entry | 'new' | null>(null);
  const [deleting, setDeleting] = useState<Entry | null>(null);
  const list = kind === 'categories' ? categories : brands;

  const remove = useMutation({
    mutationFn: (entry: Entry) => (kind === 'categories' ? adminApi.deleteCategory(entry.id) : adminApi.deleteBrand(entry.id)),
    onSuccess: async () => {
      setDeleting(null);
      await refreshCatalog(queryClient);
      toast.success(`${kind === 'categories' ? 'Category' : 'Brand'} deleted`);
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <AdminPage title="Categories & brands" description="Inactive categories and brands are hidden from shoppers."
      actions={<Button icon={<Plus className="h-4 w-4" aria-hidden />} onClick={() => setEditing('new')}>New {kind === 'categories' ? 'category' : 'brand'}</Button>}>
      <Segmented className="mb-4 w-fit" value={kind} onChange={(value) => setKind(value)}
        options={[{ value: 'categories', label: 'Categories', count: categories.data?.length }, { value: 'brands', label: 'Brands', count: brands.data?.length }]} />
      <Card>
        {list.isPending ? <TableSkeleton /> : list.isError ? (
          <div className="p-4"><ErrorState error={list.error} onRetry={() => void list.refetch()} /></div>
        ) : list.data.length === 0 ? <TableEmpty message="Nothing here yet." /> : (
          <TableWrap>
            <Table>
              <thead><tr><Th>Name</Th><Th>Slug</Th><Th className="text-right">Products</Th>{kind === 'categories' && <Th className="text-right">Order</Th>}<Th>Status</Th><Th /></tr></thead>
              <tbody>
                {list.data.map((entry) => (
                  <tr key={entry.id} className="hover:bg-stone-50">
                    <Td>
                      <span className="font-semibold text-slate-900">{entry.name}</span>
                      {entry.description && <span className="block max-w-md truncate text-xs text-slate-500">{entry.description}</span>}
                    </Td>
                    <Td className="font-mono text-xs text-slate-500">{entry.slug}</Td>
                    <Td className="text-right tabular-nums">{entry.productCount}</Td>
                    {kind === 'categories' && <Td className="text-right tabular-nums">{(entry as Category).sortOrder}</Td>}
                    <Td>{entry.active ? <Badge tone="success">Active</Badge> : <Badge tone="neutral">Inactive</Badge>}</Td>
                    <Td className="whitespace-nowrap text-right">
                      <Button size="icon" variant="ghost" className="h-8 w-8" aria-label={`Edit ${entry.name}`} onClick={() => setEditing(entry)}><Pencil className="h-4 w-4" aria-hidden /></Button>
                      <Button size="icon" variant="ghost" className="h-8 w-8 text-rose-700" aria-label={`Delete ${entry.name}`} onClick={() => setDeleting(entry)}><Trash2 className="h-4 w-4" aria-hidden /></Button>
                    </Td>
                  </tr>
                ))}
              </tbody>
            </Table>
          </TableWrap>
        )}
      </Card>
      {editing !== null && <EntryForm kind={kind} entry={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
      <ConfirmDialog open={deleting !== null} onClose={() => setDeleting(null)} title={`Delete ${deleting?.name ?? ''}?`}
        message="Only possible when no products use it. Otherwise deactivate it instead." confirmLabel="Delete" loading={remove.isPending}
        onConfirm={() => {
          if (deleting) {
            remove.mutate(deleting);
          }
        }} />
    </AdminPage>
  );
}

function refreshCatalog(queryClient: ReturnType<typeof useQueryClient>) {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: ['admin', 'categories'] }),
    queryClient.invalidateQueries({ queryKey: ['admin', 'brands'] }),
    queryClient.invalidateQueries({ queryKey: ['categories'] }),
    queryClient.invalidateQueries({ queryKey: ['brands'] }),
  ]);
}

function EntryForm({ kind, entry, onClose }: { kind: Kind; entry: Entry | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const isCategory = kind === 'categories';
  const image = entry ? ('imageUrl' in entry ? entry.imageUrl : entry.logoUrl) : null;
  const { register, handleSubmit, setError, formState: { errors } } = useForm({
    resolver: zodResolver(entrySchema),
    defaultValues: {
      name: entry?.name ?? '',
      slug: entry?.slug ?? '',
      description: entry?.description ?? '',
      image: image ?? '',
      sortOrder: entry && 'sortOrder' in entry ? String(entry.sortOrder) : '',
      active: entry?.active ?? true,
    },
  });
  const save = useMutation({
    mutationFn: (values: z.output<typeof entrySchema>): Promise<Entry> => {
      if (isCategory) {
        const input = { name: values.name, slug: emptyToNull(values.slug), description: emptyToNull(values.description), imageUrl: emptyToNull(values.image), sortOrder: values.sortOrder ?? null, active: values.active };
        return entry ? adminApi.updateCategory(entry.id, input) : adminApi.createCategory(input);
      }
      const input = { name: values.name, slug: emptyToNull(values.slug), description: emptyToNull(values.description), logoUrl: emptyToNull(values.image), active: values.active };
      return entry ? adminApi.updateBrand(entry.id, input) : adminApi.createBrand(input);
    },
    onSuccess: async () => {
      await refreshCatalog(queryClient);
      toast.success(entry ? 'Saved' : 'Created');
      onClose();
    },
    onError: (error) => {
      if (!applyServerErrors(error, setError, ['name', 'slug', 'description'])) {
        toast.error(errorMessage(error));
      }
    },
  });
  const noun = isCategory ? 'category' : 'brand';
  return (
    <Modal open onClose={onClose} title={entry ? `Edit ${noun}` : `New ${noun}`}
      footer={<>
        <Button variant="secondary" onClick={onClose} disabled={save.isPending}>Cancel</Button>
        <Button type="submit" form="catalog-entry" loading={save.isPending}>Save</Button>
      </>}>
      <form id="catalog-entry" noValidate className="grid gap-4 sm:grid-cols-2" onSubmit={handleSubmit((values) => save.mutate(values))}>
        <FormField label="Name" error={errors.name?.message} required>{(field) => <Input {...field} {...register('name')} />}</FormField>
        <FormField label="Slug" error={errors.slug?.message} hint="Blank to generate">{(field) => <Input {...field} className="font-mono text-xs" {...register('slug')} />}</FormField>
        <FormField label="Description" error={errors.description?.message} className="sm:col-span-2">{(field) => <Textarea {...field} rows={3} {...register('description')} />}</FormField>
        <FormField label={isCategory ? 'Image URL' : 'Logo URL'} error={errors.image?.message} className="sm:col-span-2">{(field) => <Input {...field} type="url" {...register('image')} />}</FormField>
        {isCategory && <FormField label="Display order" error={errors.sortOrder?.message}>{(field) => <Input {...field} inputMode="numeric" {...register('sortOrder')} />}</FormField>}
        <div className="flex items-end pb-2"><Checkbox label="Active" {...register('active')} /></div>
      </form>
    </Modal>
  );
}
