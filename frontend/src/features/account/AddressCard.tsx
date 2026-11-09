import type { ReactNode } from 'react';
import { Badge } from '@/components/ui/Badge';
import type { Address, ShippingAddress } from '@/lib/types';

export function AddressLines({ address }: { address: Address | ShippingAddress }) {
  return (
    <div className="text-sm text-slate-600">
      <p className="font-semibold text-slate-900">{address.fullName}</p>
      <p>{address.line1}</p>
      {address.line2 && <p>{address.line2}</p>}
      {address.landmark && <p>Near {address.landmark}</p>}
      <p>{address.city}, {address.state} {address.postalCode}</p>
      <p className="mt-1">Phone: {address.phone}</p>
    </div>
  );
}

export function AddressCard({ address, actions }: { address: Address; actions?: ReactNode }) {
  return (
    <div className="flex h-full flex-col rounded-2xl border border-stone-200 bg-white p-4">
      <div className="mb-2 flex items-center gap-2">
        {address.label && <Badge tone="neutral">{address.label}</Badge>}
        {address.isDefault && <Badge tone="brand">Default</Badge>}
      </div>
      <AddressLines address={address} />
      {actions && <div className="mt-auto flex flex-wrap gap-2 pt-4">{actions}</div>}
    </div>
  );
}
