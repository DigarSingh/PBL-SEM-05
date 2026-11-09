import { formatPrice } from '@/lib/format';
import type { PaymentMethod, PublicSettings } from '@/lib/types';

export interface PaymentOption {
  method: PaymentMethod;
  available: boolean;
  /** Why the option is unavailable, shown next to it. */
  reason: string | null;
}

/**
 * Which payment methods to offer for an order total. This only guides the UI: the backend applies
 * the same rules again when the order is placed.
 */
export function paymentOptions(settings: PublicSettings, total: number): PaymentOption[] {
  const onlineReady = settings.onlinePaymentsEnabled && Boolean(settings.razorpayKeyId);
  const online: PaymentOption = {
    method: 'RAZORPAY',
    available: onlineReady,
    reason: onlineReady ? null : 'Online payments are unavailable right now.',
  };
  let cod: PaymentOption = { method: 'COD', available: true, reason: null };
  if (!settings.codEnabled) {
    cod = { method: 'COD', available: false, reason: 'Cash on Delivery is unavailable right now.' };
  } else if (settings.codMaxOrderAmount != null && total > settings.codMaxOrderAmount) {
    cod = { method: 'COD', available: false, reason: `Available for orders up to ${formatPrice(settings.codMaxOrderAmount)}.` };
  }
  return [online, cod];
}

export function defaultPaymentMethod(options: PaymentOption[]): PaymentMethod | null {
  return options.find((option) => option.available)?.method ?? null;
}
