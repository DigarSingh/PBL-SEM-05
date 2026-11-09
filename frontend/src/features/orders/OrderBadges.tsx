import { Badge } from '@/components/ui/Badge';
import { orderStatusMeta, paymentMethodLabel, paymentStatusMeta } from '@/lib/format';
import type { OrderStatus, PaymentMethod, PaymentStatus } from '@/lib/types';

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  const meta = orderStatusMeta[status];
  return <Badge tone={meta.tone} dot>{meta.label}</Badge>;
}

export function PaymentBadge({ method, status }: { method: PaymentMethod; status: PaymentStatus | null }) {
  if (!status) {
    return <Badge tone="neutral">{paymentMethodLabel(method)}</Badge>;
  }
  const meta = paymentStatusMeta[status];
  const label = method === 'COD' && status === 'PENDING' ? 'Pay on delivery' : meta.label;
  return <Badge tone={meta.tone}>{label}</Badge>;
}
