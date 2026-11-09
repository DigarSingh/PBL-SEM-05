import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Archive, ExternalLink, Plus } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router';
import { toast } from 'sonner';
import { ProductImage } from '@/components/ProductImage';
import { Badge } from '@/components/ui/Badge';
import { Button, ButtonLink } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ConfirmDialog } from '@/components/ui/Dialog';
import { ErrorState } from '@/components/ui/Feedback';
import { Checkbox, Select } from '@/components/ui/Field';
import { Pagination } from '@/components/ui/Pagination';
import { Table, TableWrap, Td, Th } from '@/components/ui/Table';
import { errorMessage } from '@/lib/errors';
import { formatDate, formatNumber, formatPrice, stockMeta } from '@/lib/format';
import type { AdminProductSummary, ProductStatus } from '@/lib/types';
import { adminApi, type AdminProductQuery } from '../api';
import { AdminPage } from '../components/AdminPage';
import { SearchInput } from '../components/SearchInput';
import { TableEmpty, TableSkeleton } from '../components/TableStates';
import { useAdminBrands, useAdminCategories } from '../hooks';
import { pageFrom, useParamsState } from '../useParamsState';
import { PRODUCT_STATUSES } from './productForm';

const DEFAULTS = { q: '', status: '', categoryId: '', brandId: '', inStock: '', sort: '', page: '' };
const STATUS_TONE: Record<ProductStatus, 'success' | 'neutral' | 'warning' | 'danger'> = {
  ACTIVE: 'success', DRAFT: 'neutral', INACTIVE: 'warning', ARCHIVED: 'danger',
};

export default function AdminProductsPage() {
  const [filters, setFilters] = useParamsState(DEFAULTS);
  const queryClient = useQueryClient();
  const categories = useAdminCategories();
  const brands = useAdminBrands();
  const [archiving, setArchiving] = useState<AdminProductSummary | null>(null);
  const query: AdminProductQuery = {
    q: filters.q || undefined,
    status: (filters.status || undefined) as ProductStatus | undefined,
    categoryId: filters.categoryId ? Number(filters.categoryId) : undefined,
    brandId: filters.brandId ? Number(filters.brandId) : undefined,
    inStock: filters.inStock === 'true' || undefined,
    sort: filters.sort || undefined,
    page: pageFrom(filters.page),
    size: 20,
  };
  const products = useQuery({ queryKey: ['admin', 'products', 'list', query], queryFn: () => adminApi.products(query), placeholderData: keepPreviousData });
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin'] }).then(() => queryClient.invalidateQueries({ queryKey: ['products'] }));
  const setStatus = useMutation({
    mutationFn: ({ product, status }: { product: AdminProductSummary; status: ProductStatus }) => adminApi.setProductStatus(product.id, status),
    onSuccess: (updated) => {
      void invalidate();
      toast.success(`${updated.name} is now ${updated.status.toLowerCase()}`);
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  const archive = useMutation({
    mutationFn: (product: AdminProductSummary) => adminApi.archiveProduct(product.id),
    onSuccess: () => {
      void invalidate();
      setArchiving(null);
      toast.success('Product archived');
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <AdminPage title="Products" description="Only active products appear in the store."
      actions={<ButtonLink to="/admin/products/new" icon={<Plus className="h-4 w-4" aria-hidden />}>New product</ButtonLink>}>
      <Card className="mb-4 flex flex-wrap items-center gap-3 p-4">
        <SearchInput key={filters.q} value={filters.q} onSearch={(q) => setFilters({ q })} placeholder="Name, brand or SKU" className="w-full sm:w-72" />
        <Select aria-label="Status" value={filters.status} onChange={(event) => setFilters({ status: event.target.value })} className="h-9 w-auto">
          <option value="">All statuses</option>
          {PRODUCT_STATUSES.map((status) => <option key={status} value={status}>{status.charAt(0) + status.slice(1).toLowerCase()}</option>)}
        </Select>
        <Select aria-label="Category" value={filters.categoryId} onChange={(event) => setFilters({ categoryId: event.target.value })} className="h-9 w-auto">
          <option value="">All categories</option>
          {categories.data?.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
        </Select>
        <Select aria-label="Brand" value={filters.brandId} onChange={(event) => setFilters({ brandId: event.target.value })} className="h-9 w-auto">
          <option value="">All brands</option>
          {brands.data?.map((brand) => <option key={brand.id} value={brand.id}>{brand.name}</option>)}
        </Select>
        <Select aria-label="Sort" value={filters.sort} onChange={(event) => setFilters({ sort: event.target.value })} className="h-9 w-auto">
          <option value="">Recently added</option>
          <option value="popularity">Best selling</option>
          <option value="price_asc">Price: low to high</option>
          <option value="price_desc">Price: high to low</option>
          <option value="rating">Top rated</option>
        </Select>
        <Checkbox label="In stock only" checked={filters.inStock === 'true'} onChange={(event) => setFilters({ inStock: event.target.checked ? 'true' : '' })} />
      </Card>

      <Card>
        {products.isPending ? <TableSkeleton /> : products.isError ? (
          <div className="p-4"><ErrorState error={products.error} onRetry={() => void products.refetch()} /></div>
        ) : products.data.items.length === 0 ? <TableEmpty message="No products match these filters." /> : (
          <TableWrap className={products.isPlaceholderData ? 'opacity-60' : undefined}>
            <Table className="min-w-[960px]">
              <thead>
                <tr><Th>Product</Th><Th>Category</Th><Th className="text-right">Price</Th><Th>Stock</Th><Th className="text-right">Sold</Th><Th>Status</Th><Th>Updated</Th><Th /></tr>
              </thead>
              <tbody>
                {products.data.items.map((product) => (
                  <tr key={product.id} className="hover:bg-stone-50">
                    <Td>
                      <div className="flex items-center gap-3">
                        <span className="h-11 w-11 shrink-0 overflow-hidden rounded-lg border border-stone-200"><ProductImage src={product.imageUrl} alt="" className="h-full w-full" /></span>
                        <span className="min-w-0">
                          <Link to={`/admin/products/${product.id}`} className="line-clamp-1 font-semibold text-slate-900 hover:text-brand-700">{product.name}</Link>
                          <span className="block text-xs text-slate-500">{product.brand.name} · {product.variantCount} variant{product.variantCount === 1 ? '' : 's'}{product.featured ? ' · Featured' : ''}</span>
                        </span>
                      </div>
                    </Td>
                    <Td>{product.category.name}</Td>
                    <Td className="text-right tabular-nums">
                      <span className="font-medium text-slate-900">{formatPrice(product.salePrice)}</span>
                      {product.discountPercent > 0 && <span className="block text-xs text-slate-400 line-through">{formatPrice(product.price)}</span>}
                    </Td>
                    <Td><Badge tone={stockMeta[product.stockStatus].tone}>{formatNumber(product.totalAvailable)} available</Badge></Td>
                    <Td className="text-right tabular-nums">{formatNumber(product.soldCount)}</Td>
                    <Td>
                      <Select aria-label={`Status of ${product.name}`} value={product.status} disabled={setStatus.isPending}
                        onChange={(event) => setStatus.mutate({ product, status: event.target.value as ProductStatus })} className="h-8 w-32 text-xs">
                        {PRODUCT_STATUSES.map((status) => <option key={status} value={status}>{status.charAt(0) + status.slice(1).toLowerCase()}</option>)}
                      </Select>
                      <span className="sr-only"><Badge tone={STATUS_TONE[product.status]}>{product.status}</Badge></span>
                    </Td>
                    <Td className="whitespace-nowrap text-slate-500">{formatDate(product.updatedAt)}</Td>
                    <Td className="whitespace-nowrap text-right">
                      <ButtonLink to={`/admin/products/${product.id}`} size="sm" variant="ghost">Edit</ButtonLink>
                      {product.status === 'ACTIVE' && (
                        <a href={`/products/${product.slug}`} target="_blank" rel="noreferrer" className="inline-flex h-8 w-8 items-center justify-center rounded-lg text-slate-500 hover:bg-stone-100" aria-label={`View ${product.name} in the store`}>
                          <ExternalLink className="h-4 w-4" aria-hidden />
                        </a>
                      )}
                      {product.status !== 'ARCHIVED' && (
                        <Button size="icon" variant="ghost" className="h-8 w-8 text-slate-500" aria-label={`Archive ${product.name}`} onClick={() => setArchiving(product)}>
                          <Archive className="h-4 w-4" aria-hidden />
                        </Button>
                      )}
                    </Td>
                  </tr>
                ))}
              </tbody>
            </Table>
          </TableWrap>
        )}
      </Card>
      {products.data && (
        <div className="mt-4 flex items-center justify-between text-sm text-slate-500">
          <span>{products.data.totalElements} products</span>
          <Pagination page={products.data.page} totalPages={products.data.totalPages} onChange={(page) => setFilters({ page: String(page + 1) })} />
        </div>
      )}
      <ConfirmDialog open={archiving !== null} onClose={() => setArchiving(null)} title="Archive this product?"
        message={`${archiving?.name ?? 'The product'} will be hidden from the store. Order history keeps its details, and you can reactivate it later.`}
        confirmLabel="Archive" loading={archive.isPending} onConfirm={() => {
          if (archiving) {
            archive.mutate(archiving);
          }
        }} />
    </AdminPage>
  );
}
