import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ExternalLink, Plus, Trash2 } from 'lucide-react';
import { useFieldArray, useForm, useWatch, type Control } from 'react-hook-form';
import { useNavigate, useParams } from 'react-router';
import { toast } from 'sonner';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { ErrorState, InlineAlert, Skeleton } from '@/components/ui/Feedback';
import { Checkbox, FormField, Input, Select, Textarea } from '@/components/ui/Field';
import { errorMessage } from '@/lib/errors';
import { applyServerErrors } from '@/lib/forms';
import { formatPrice } from '@/lib/format';
import type { AdminProduct, Brand, Category } from '@/lib/types';
import { adminApi } from '../api';
import { AdminPage } from '../components/AdminPage';
import { useAdminBrands, useAdminCategories } from '../hooks';
import { ImagesManager } from './ImagesManager';
import {
  emptyVariant, PRODUCT_STATUSES, productSchema, toFormValues, toProductInput, type ProductFormOutput, type ProductFormValues,
} from './productForm';

export default function AdminProductEditorPage() {
  const { productId } = useParams();
  const isNew = productId === undefined;
  const id = Number(productId);
  const product = useQuery({ queryKey: ['admin', 'products', 'detail', id], queryFn: () => adminApi.product(id), enabled: !isNew && Number.isFinite(id) });
  const categories = useAdminCategories();
  const brands = useAdminBrands();

  if ((!isNew && product.isPending) || categories.isPending || brands.isPending) {
    return <AdminPage title={isNew ? 'New product' : 'Edit product'}><Skeleton className="h-[40rem] w-full" /></AdminPage>;
  }
  const failed = product.error ?? categories.error ?? brands.error;
  if (failed || !categories.data || !brands.data) {
    return <AdminPage title="Product"><ErrorState error={failed} onRetry={() => {
      void product.refetch();
      void categories.refetch();
      void brands.refetch();
    }} /></AdminPage>;
  }
  const loaded = isNew ? null : product.data ?? null;
  // Keyed so the form resets with fresh values after each save.
  return <ProductEditor key={loaded ? `${loaded.id}-${loaded.updatedAt}` : 'new'} product={loaded} categories={categories.data} brands={brands.data} />;
}

function ProductEditor({ product, categories, brands }: { product: AdminProduct | null; categories: Category[]; brands: Brand[] }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { register, control, handleSubmit, setError, formState: { errors, isDirty } } = useForm<ProductFormValues, unknown, ProductFormOutput>({
    resolver: zodResolver(productSchema),
    defaultValues: toFormValues(product),
  });
  const specs = useFieldArray({ control, name: 'specifications' });
  const variants = useFieldArray({ control, name: 'variants' });

  const save = useMutation({
    mutationFn: (values: ProductFormOutput) => {
      const input = toProductInput(values);
      return product ? adminApi.updateProduct(product.id, input) : adminApi.createProduct(input);
    },
    onSuccess: async (saved) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['admin'] }),
        queryClient.invalidateQueries({ queryKey: ['products'] }),
        queryClient.invalidateQueries({ queryKey: ['product'] }),
      ]);
      if (product) {
        queryClient.setQueryData(['admin', 'products', 'detail', saved.id], saved);
        toast.success('Product saved');
      } else {
        toast.success('Product created. Add photos below.');
        navigate(`/admin/products/${saved.id}`, { replace: true });
      }
    },
    onError: (error) => {
      if (!applyServerErrors(error, setError)) {
        toast.error(errorMessage(error));
      }
    },
  });

  const title = product ? product.name : 'New product';
  return (
    <AdminPage title={title} breadcrumbs={[{ label: 'Products', to: '/admin/products' }, { label: product ? 'Edit' : 'New' }]}
      actions={product?.status === 'ACTIVE' ? (
        <a href={`/products/${product.slug}`} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1.5 text-sm font-semibold text-brand-700 hover:underline">
          View in store <ExternalLink className="h-4 w-4" aria-hidden />
        </a>
      ) : undefined}>
      <form noValidate onSubmit={handleSubmit((values) => save.mutate(values))} className="space-y-6">
        {Object.keys(errors).length > 0 && <InlineAlert tone="danger">Please fix the highlighted fields.</InlineAlert>}
        <div className="grid gap-6 xl:grid-cols-[1fr_22rem]">
          <div className="space-y-6">
            <Card>
              <CardHeader title="Details" />
              <div className="grid gap-4 p-5 sm:grid-cols-2">
                <FormField label="Name" error={errors.name?.message} required className="sm:col-span-2">
                  {(field) => <Input {...field} {...register('name')} />}
                </FormField>
                <FormField label="Brand" error={errors.brandId?.message} required>
                  {(field) => (
                    <Select {...field} {...register('brandId')}>
                      <option value="">Choose a brand</option>
                      {brands.map((brand) => <option key={brand.id} value={brand.id}>{brand.name}{brand.active ? '' : ' (inactive)'}</option>)}
                    </Select>
                  )}
                </FormField>
                <FormField label="Category" error={errors.categoryId?.message} required>
                  {(field) => (
                    <Select {...field} {...register('categoryId')}>
                      <option value="">Choose a category</option>
                      {categories.map((category) => <option key={category.id} value={category.id}>{category.name}{category.active ? '' : ' (inactive)'}</option>)}
                    </Select>
                  )}
                </FormField>
                <FormField label="Short description" error={errors.shortDescription?.message} hint="One line shown near the price." className="sm:col-span-2">
                  {(field) => <Input {...field} {...register('shortDescription')} />}
                </FormField>
                <FormField label="Description" error={errors.description?.message} required className="sm:col-span-2">
                  {(field) => <Textarea {...field} rows={7} {...register('description')} />}
                </FormField>
                <FormField label="Tags" error={errors.tags?.message} hint="Comma-separated, e.g. wireless, noise cancelling, gaming. Tags help search." className="sm:col-span-2">
                  {(field) => <Input {...field} {...register('tags')} />}
                </FormField>
              </div>
            </Card>

            <Card>
              <CardHeader title="Variants & stock" description="Each variant is a sellable SKU. Stock of existing variants is changed from Inventory, which keeps an audit trail." />
              <div className="space-y-4 p-5">
                {errors.variants?.root?.message && <InlineAlert tone="danger">{errors.variants.root.message}</InlineAlert>}
                {variants.fields.map((field, index) => {
                  const existing = field.variantId === null ? undefined : product?.variants.find((variant) => variant.id === field.variantId);
                  const rowErrors = errors.variants?.[index];
                  return (
                    <div key={field.id} className="rounded-xl border border-stone-200 p-4">
                      <div className="mb-3 flex items-center justify-between">
                        <p className="text-sm font-semibold text-slate-900">
                          Variant {index + 1}
                          {existing && <span className="ml-2 font-normal text-slate-500">{existing.available} available · {existing.reserved} reserved</span>}
                        </p>
                        {variants.fields.length > 1 && !existing?.hasOrders && (
                          <Button size="sm" variant="ghost" className="text-rose-700" icon={<Trash2 className="h-4 w-4" aria-hidden />} onClick={() => variants.remove(index)}>Remove</Button>
                        )}
                        {existing?.hasOrders && <Badge tone="neutral">Has orders: deactivate instead of removing</Badge>}
                      </div>
                      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                        <FormField label="SKU" error={rowErrors?.sku?.message} required>
                          {(props) => <Input {...props} {...register(`variants.${index}.sku`)} className="font-mono" />}
                        </FormField>
                        <FormField label="Name" error={rowErrors?.name?.message} required>
                          {(props) => <Input {...props} placeholder="e.g. 256 GB, Black" {...register(`variants.${index}.name`)} />}
                        </FormField>
                        <FormField label="Price override (₹)" error={rowErrors?.priceOverride?.message} hint="Blank uses the product price.">
                          {(props) => <Input {...props} inputMode="decimal" {...register(`variants.${index}.priceOverride`)} />}
                        </FormField>
                        <FormField label="Options" error={rowErrors?.attributes?.message} hint='e.g. "Color: Black, Storage: 256 GB"' className="sm:col-span-2">
                          {(props) => <Input {...props} {...register(`variants.${index}.attributes`)} />}
                        </FormField>
                        {existing ? (
                          <FormField label="Low-stock alert at" error={rowErrors?.lowStockThreshold?.message}>
                            {(props) => <Input {...props} inputMode="numeric" {...register(`variants.${index}.lowStockThreshold`)} />}
                          </FormField>
                        ) : (
                          <FormField label="Opening stock" error={rowErrors?.initialStock?.message}>
                            {(props) => <Input {...props} inputMode="numeric" {...register(`variants.${index}.initialStock`)} />}
                          </FormField>
                        )}
                        {!existing && (
                          <FormField label="Low-stock alert at" error={rowErrors?.lowStockThreshold?.message} hint="Blank uses the store default.">
                            {(props) => <Input {...props} inputMode="numeric" {...register(`variants.${index}.lowStockThreshold`)} />}
                          </FormField>
                        )}
                        <div className="flex items-end pb-2">
                          <Checkbox label="Available for sale" {...register(`variants.${index}.active`)} />
                        </div>
                      </div>
                    </div>
                  );
                })}
                <Button variant="secondary" icon={<Plus className="h-4 w-4" aria-hidden />} onClick={() => variants.append(emptyVariant())}>Add variant</Button>
              </div>
            </Card>

            <Card>
              <CardHeader title="Specifications" description="Shown as a table on the product page." />
              <div className="space-y-3 p-5">
                {specs.fields.map((field, index) => (
                  <div key={field.id} className="flex items-start gap-2">
                    <FormField label="Name" error={errors.specifications?.[index]?.name?.message} className="w-2/5">
                      {(props) => <Input {...props} placeholder="e.g. Battery" {...register(`specifications.${index}.name`)} />}
                    </FormField>
                    <FormField label="Value" error={errors.specifications?.[index]?.value?.message} className="flex-1">
                      {(props) => <Input {...props} placeholder="e.g. 5000 mAh" {...register(`specifications.${index}.value`)} />}
                    </FormField>
                    <Button size="icon" variant="ghost" className="mt-6 text-slate-500" aria-label="Remove specification" onClick={() => specs.remove(index)}>
                      <Trash2 className="h-4 w-4" aria-hidden />
                    </Button>
                  </div>
                ))}
                <Button variant="secondary" size="sm" icon={<Plus className="h-4 w-4" aria-hidden />} onClick={() => specs.append({ name: '', value: '' })}>Add specification</Button>
              </div>
            </Card>
          </div>

          <aside className="space-y-6">
            <Card>
              <CardHeader title="Pricing" />
              <div className="space-y-4 p-5">
                <FormField label="List price (MRP, ₹)" error={errors.price?.message} required>
                  {(field) => <Input {...field} inputMode="decimal" {...register('price')} />}
                </FormField>
                <FormField label="Discount (%)" error={errors.discountPercent?.message} required>
                  {(field) => <Input {...field} inputMode="decimal" {...register('discountPercent')} />}
                </FormField>
                <SalePreview control={control} />
              </div>
            </Card>
            <Card>
              <CardHeader title="Visibility" />
              <div className="space-y-4 p-5">
                <FormField label="Status" error={errors.status?.message}>
                  {(field) => (
                    <Select {...field} {...register('status')}>
                      {PRODUCT_STATUSES.map((status) => <option key={status} value={status}>{status.charAt(0) + status.slice(1).toLowerCase()}</option>)}
                    </Select>
                  )}
                </FormField>
                <Checkbox label="Feature on the home page" {...register('featured')} />
                <FormField label="URL slug" error={errors.slug?.message} hint="Leave blank to generate from the name.">
                  {(field) => <Input {...field} className="font-mono text-xs" {...register('slug')} />}
                </FormField>
              </div>
            </Card>
            <div className="sticky top-20 space-y-2">
              <Button type="submit" size="lg" className="w-full" loading={save.isPending} disabled={product !== null && !isDirty}>
                {product ? 'Save changes' : 'Create product'}
              </Button>
              {product && <p className="text-center text-xs text-slate-500">Last updated {new Date(product.updatedAt).toLocaleString('en-IN')}</p>}
            </div>
          </aside>
        </div>
      </form>

      {product && <div className="mt-6"><ImagesManager product={product} /></div>}
    </AdminPage>
  );
}

function SalePreview({ control }: { control: Control<ProductFormValues, unknown, ProductFormOutput> }) {
  const [price, discount] = useWatch({ control, name: ['price', 'discountPercent'] });
  const list = Number(price);
  const off = Number(discount);
  if (!Number.isFinite(list) || list <= 0 || !Number.isFinite(off) || off < 0 || off > 95) {
    return null;
  }
  const sale = Math.round(list * (1 - off / 100) * 100) / 100;
  return <p className="rounded-lg bg-stone-50 px-3 py-2 text-sm text-slate-600">Customers pay about <strong className="text-slate-900">{formatPrice(sale)}</strong>. The server calculates the exact price when saving.</p>;
}
