import { useMutation, useQueryClient } from '@tanstack/react-query';
import { MapPin, Plus } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/Button';
import { ConfirmDialog } from '@/components/ui/Dialog';
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/Feedback';
import { useSession } from '@/features/auth/useSession';
import { errorMessage } from '@/lib/errors';
import { useDocumentTitle } from '@/lib/hooks';
import type { Address } from '@/lib/types';
import { AddressCard } from './AddressCard';
import { AddressFormModal } from './AddressFormModal';
import { accountApi, ADDRESSES_KEY, useAddresses } from './api';

export default function AddressesPage() {
  useDocumentTitle('Addresses');
  const { user } = useSession();
  const addresses = useAddresses();
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<Address | null | 'new'>(null);
  const [deleting, setDeleting] = useState<Address | null>(null);

  const makeDefault = useMutation({
    mutationFn: (address: Address) => accountApi.makeDefault(address.id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ADDRESSES_KEY });
      toast.success('Default address updated');
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  const remove = useMutation({
    mutationFn: (address: Address) => accountApi.deleteAddress(address.id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ADDRESSES_KEY });
      setDeleting(null);
      toast.success('Address deleted');
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <div>
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">Addresses</h1>
          <p className="mt-1 text-sm text-slate-500">Where we deliver your orders.</p>
        </div>
        <Button icon={<Plus className="h-4 w-4" aria-hidden />} onClick={() => setEditing('new')}>Add address</Button>
      </div>
      {addresses.isPending ? (
        <div className="grid gap-4 sm:grid-cols-2">{[0, 1].map((key) => <Skeleton key={key} className="h-44 w-full" />)}</div>
      ) : addresses.isError ? (
        <ErrorState error={addresses.error} onRetry={() => void addresses.refetch()} />
      ) : addresses.data.length === 0 ? (
        <EmptyState icon={<MapPin className="h-7 w-7" aria-hidden />} title="No saved addresses"
          description="Add an address to check out faster." action={<Button onClick={() => setEditing('new')}>Add address</Button>} />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2">
          {addresses.data.map((address) => (
            <AddressCard key={address.id} address={address} actions={<>
              <Button size="sm" variant="secondary" onClick={() => setEditing(address)}>Edit</Button>
              {!address.isDefault && (
                <Button size="sm" variant="ghost" loading={makeDefault.isPending && makeDefault.variables?.id === address.id}
                  onClick={() => makeDefault.mutate(address)}>Set as default</Button>
              )}
              <Button size="sm" variant="ghost" className="text-rose-700" onClick={() => setDeleting(address)}>Delete</Button>
            </>} />
          ))}
        </div>
      )}

      {editing !== null && (
        <AddressFormModal open onClose={() => setEditing(null)} address={editing === 'new' ? null : editing}
          defaultName={user?.fullName} defaultPhone={user?.phone} />
      )}
      <ConfirmDialog open={deleting !== null} onClose={() => setDeleting(null)} title="Delete this address?"
        message="Past orders keep their delivery details; this only removes the saved address." confirmLabel="Delete"
        loading={remove.isPending} onConfirm={() => {
          if (deleting) {
            remove.mutate(deleting);
          }
        }} />
    </div>
  );
}
