import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { History, PackagePlus } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router';
import { toast } from 'sonner';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Drawer, Modal } from '@/components/ui/Dialog';
import { ErrorState, InlineAlert, Spinner } from '@/components/ui/Feedback';
import { Checkbox, Input, Select, Textarea } from '@/components/ui/Field';
import { Pagination } from '@/components/ui/Pagination';
import { Segmented } from '@/components/ui/Segmented';
import { Table, TableWrap, Td, Th } from '@/components/ui/Table';
import { errorMessage } from '@/lib/errors';
import { formatDateTime, formatNumber, stockMeta } from '@/lib/format';
import { useRealtimeStatus } from '@/lib/hooks';
import type { InventoryItem } from '@/lib/types';
import { adminApi } from '../api';
import { AdminPage } from '../components/AdminPage';
import { SearchInput } from '../components/SearchInput';
import { TableEmpty, TableSkeleton } from '../components/TableStates';
import { useAdminCategories } from '../hooks';
import { pageFrom, useParamsState } from '../useParamsState';

const DEFAULTS = { q: '', categoryId: '', lowStock: '', outOfStock: '', page: '' };

export default function AdminInventoryPage() {
  const [filters, setFilters] = useParamsState(DEFAULTS);
  const categories = useAdminCategories();
  const realtimeStatus = useRealtimeStatus();
  const [adjusting, setAdjusting] = useState<InventoryItem | null>(null);
  const [history, setHistory] = useState<InventoryItem | null>(null);
  const query = {
    q: filters.q || undefined,
    categoryId: filters.categoryId ? Number(filters.categoryId) : undefined,
    lowStock: filters.lowStock === 'true' || undefined,
    outOfStock: filters.outOfStock === 'true' || undefined,
    page: pageFrom(filters.page),
    size: 25,
  };
  const inventory = useQuery({
    queryKey: ['admin', 'inventory', query],
    queryFn: () => adminApi.inventory(query),
    placeholderData: keepPreviousData,
    refetchInterval: realtimeStatus === 'connected' ? false : 30_000,
  });

  return (
    <AdminPage title="Inventory" description="On hand = physical stock. Reserved = held by unpaid online orders. Available = what shoppers can buy.">
      <Card className="mb-4 flex flex-wrap items-center gap-3 p-4">
        <SearchInput key={filters.q} value={filters.q} onSearch={(q) => setFilters({ q })} placeholder="Product, variant or SKU" className="w-full sm:w-72" />
        <Select aria-label="Category" value={filters.categoryId} onChange={(event) => setFilters({ categoryId: event.target.value })} className="h-9 w-auto">
          <option value="">All categories</option>
          {categories.data?.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
        </Select>
        <Checkbox label="Low stock" checked={filters.lowStock === 'true'} onChange={(event) => setFilters({ lowStock: event.target.checked ? 'true' : '' })} />
        <Checkbox label="Out of stock" checked={filters.outOfStock === 'true'} onChange={(event) => setFilters({ outOfStock: event.target.checked ? 'true' : '' })} />
      </Card>
      <Card>
        {inventory.isPending ? <TableSkeleton /> : inventory.isError ? (
          <div className="p-4"><ErrorState error={inventory.error} onRetry={() => void inventory.refetch()} /></div>
        ) : inventory.data.items.length === 0 ? <TableEmpty message="No variants match these filters." /> : (
          <TableWrap className={inventory.isPlaceholderData ? 'opacity-60' : undefined}>
            <Table className="min-w-[900px]">
              <thead><tr><Th>Product</Th><Th>Category</Th><Th className="text-right">On hand</Th><Th className="text-right">Reserved</Th><Th className="text-right">Available</Th><Th className="text-right">Alert at</Th><Th>Status</Th><Th /></tr></thead>
              <tbody>
                {inventory.data.items.map((item) => (
                  <tr key={item.variantId} className="hover:bg-stone-50">
                    <Td>
                      <Link to={`/admin/products/${item.productId}`} className="font-semibold text-slate-900 hover:text-brand-700">{item.productName}</Link>
                      <span className="block text-xs text-slate-500">{item.variantName} · <span className="font-mono">{item.sku}</span>{!item.variantActive && ' · inactive'}</span>
                    </Td>
                    <Td>{item.categoryName}</Td>
                    <Td className="text-right tabular-nums">{formatNumber(item.quantity)}</Td>
                    <Td className="text-right tabular-nums">{formatNumber(item.reserved)}</Td>
                    <Td className="text-right font-semibold tabular-nums">{formatNumber(item.available)}</Td>
                    <Td className="text-right tabular-nums">{item.lowStockThreshold}</Td>
                    <Td><Badge tone={stockMeta[item.stockStatus].tone} dot>{stockMeta[item.stockStatus].label}</Badge></Td>
                    <Td className="whitespace-nowrap text-right">
                      <Button size="sm" variant="secondary" icon={<PackagePlus className="h-4 w-4" aria-hidden />} onClick={() => setAdjusting(item)}>Adjust</Button>
                      <Button size="icon" variant="ghost" className="ml-1 h-8 w-8" aria-label={`Stock history for ${item.sku}`} onClick={() => setHistory(item)}><History className="h-4 w-4" aria-hidden /></Button>
                    </Td>
                  </tr>
                ))}
              </tbody>
            </Table>
          </TableWrap>
        )}
      </Card>
      {inventory.data && (
        <div className="mt-4 flex items-center justify-between text-sm text-slate-500">
          <span>{inventory.data.totalElements} variants</span>
          <Pagination page={inventory.data.page} totalPages={inventory.data.totalPages} onChange={(page) => setFilters({ page: String(page + 1) })} />
        </div>
      )}
      {adjusting && <AdjustStockModal item={adjusting} onClose={() => setAdjusting(null)} />}
      <Drawer open={history !== null} onClose={() => setHistory(null)} title={history ? `Stock history · ${history.sku}` : 'Stock history'}>
        {history && <MovementList variantId={history.variantId} />}
      </Drawer>
    </AdminPage>
  );
}

function AdjustStockModal({ item, onClose }: { item: InventoryItem; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [mode, setMode] = useState<'RESTOCK' | 'ADJUSTMENT'>('RESTOCK');
  const [quantity, setQuantity] = useState('');
  const [note, setNote] = useState('');
  const [threshold, setThreshold] = useState(String(item.lowStockThreshold));
  const amount = Number(quantity);
  const valid = Number.isInteger(amount) && amount !== 0 && (mode === 'ADJUSTMENT' || amount > 0);
  const resulting = item.quantity + (valid ? amount : 0);
  const refresh = () => Promise.all([
    queryClient.invalidateQueries({ queryKey: ['admin'] }),
    queryClient.invalidateQueries({ queryKey: ['products'] }),
    queryClient.invalidateQueries({ queryKey: ['product'] }),
  ]);
  const adjust = useMutation({
    mutationFn: () => adminApi.adjustStock(item.variantId, { type: mode, quantity: amount, note: note.trim() || null }),
    onSuccess: async (updated) => {
      await refresh();
      toast.success(`${updated.sku}: ${updated.available} available`);
      onClose();
    },
  });
  const saveThreshold = useMutation({
    mutationFn: () => adminApi.setThreshold(item.variantId, Number(threshold)),
    onSuccess: async () => {
      await refresh();
      toast.success('Low-stock alert level saved');
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  return (
    <Modal open onClose={onClose} title={`Adjust stock · ${item.productName}`} description={`${item.variantName} (${item.sku})`}
      footer={<>
        <Button variant="secondary" onClick={onClose} disabled={adjust.isPending}>Cancel</Button>
        <Button loading={adjust.isPending} disabled={!valid || resulting < item.reserved} onClick={() => adjust.mutate()}>Save adjustment</Button>
      </>}>
      <div className="space-y-4">
        <Segmented value={mode} onChange={setMode} options={[{ value: 'RESTOCK', label: 'Receive stock' }, { value: 'ADJUSTMENT', label: 'Correct count (±)' }]} />
        <div className="grid grid-cols-3 gap-3 text-center text-sm">
          <div className="rounded-lg bg-stone-50 p-2"><p className="text-xs text-slate-500">On hand</p><p className="font-semibold">{item.quantity}</p></div>
          <div className="rounded-lg bg-stone-50 p-2"><p className="text-xs text-slate-500">Reserved</p><p className="font-semibold">{item.reserved}</p></div>
          <div className="rounded-lg bg-stone-50 p-2"><p className="text-xs text-slate-500">After</p><p className="font-semibold">{resulting}</p></div>
        </div>
        <label className="block text-sm">
          <span className="mb-1 block font-medium text-slate-700">{mode === 'RESTOCK' ? 'Units received' : 'Change (use a minus sign to remove units)'}</span>
          <Input inputMode="numeric" value={quantity} onChange={(event) => setQuantity(event.target.value)} placeholder={mode === 'RESTOCK' ? 'e.g. 50' : 'e.g. -3'} />
        </label>
        {resulting < item.reserved && valid && <InlineAlert tone="warning">On hand can&apos;t go below the {item.reserved} units reserved for unpaid orders.</InlineAlert>}
        <label className="block text-sm">
          <span className="mb-1 block font-medium text-slate-700">Note (kept in the stock history)</span>
          <Textarea rows={2} maxLength={255} value={note} onChange={(event) => setNote(event.target.value)} placeholder={mode === 'RESTOCK' ? 'Supplier invoice or PO number' : 'Reason, e.g. damaged in warehouse'} />
        </label>
        {adjust.isError && <InlineAlert tone="danger">{errorMessage(adjust.error)}</InlineAlert>}
        <div className="flex items-end gap-2 border-t border-stone-100 pt-4">
          <label className="block flex-1 text-sm">
            <span className="mb-1 block font-medium text-slate-700">Low-stock alert at</span>
            <Input inputMode="numeric" value={threshold} onChange={(event) => setThreshold(event.target.value)} />
          </label>
          <Button variant="secondary" loading={saveThreshold.isPending}
            disabled={!/^\d+$/.test(threshold) || Number(threshold) === item.lowStockThreshold} onClick={() => saveThreshold.mutate()}>
            Save level
          </Button>
        </div>
      </div>
    </Modal>
  );
}

function MovementList({ variantId }: { variantId: number }) {
  const [page, setPage] = useState(0);
  const movements = useQuery({ queryKey: ['admin', 'inventory', 'movements', variantId, page], queryFn: () => adminApi.movements(variantId, page), placeholderData: keepPreviousData });
  if (movements.isPending) {
    return <Spinner />;
  }
  if (movements.isError) {
    return <ErrorState error={movements.error} onRetry={() => void movements.refetch()} />;
  }
  if (movements.data.items.length === 0) {
    return <p className="text-sm text-slate-500">No stock movements yet.</p>;
  }
  return (
    <div>
      <ul className="divide-y divide-stone-100 text-sm">
        {movements.data.items.map((movement) => (
          <li key={movement.id} className="py-3">
            <div className="flex items-center justify-between gap-2">
              <span className="font-medium text-slate-900">{movement.type.replaceAll('_', ' ').toLowerCase()}</span>
              <span className={movement.quantityChange > 0 ? 'font-semibold text-emerald-700' : movement.quantityChange < 0 ? 'font-semibold text-rose-700' : 'text-slate-500'}>
                {movement.quantityChange > 0 ? '+' : ''}{movement.quantityChange !== 0 ? movement.quantityChange : `${movement.reservedChange > 0 ? '+' : ''}${movement.reservedChange} reserved`}
              </span>
            </div>
            <p className="text-xs text-slate-500">
              On hand {movement.quantityAfter} · reserved {movement.reservedAfter}
              {movement.orderId && <> · <Link to={`/admin/orders/${movement.orderId}`} className="text-brand-700 hover:underline">order #{movement.orderId}</Link></>}
            </p>
            {movement.note && <p className="text-xs text-slate-600">{movement.note}</p>}
            <p className="text-xs text-slate-400">{formatDateTime(movement.createdAt)}</p>
          </li>
        ))}
      </ul>
      <Pagination className="mt-4" page={movements.data.page} totalPages={movements.data.totalPages} onChange={setPage} />
    </div>
  );
}
