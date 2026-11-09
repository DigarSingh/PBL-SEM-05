import { useQuery } from '@tanstack/react-query';
import { apiGet, apiPost } from '@/lib/api';
import { useRealtimeStatus } from '@/lib/hooks';
import type { CheckoutResponse, OrderDetail, OrderStatus, OrderSummary, Page, PaymentMethod, RazorpayCheckout } from '@/lib/types';

export interface CheckoutInput {
  addressId: number;
  paymentMethod: PaymentMethod;
  customerNote: string | null;
}

export const ordersApi = {
  /** The idempotency key makes retries of the same attempt return the same order instead of a duplicate. */
  checkout: (input: CheckoutInput, idempotencyKey: string) =>
    apiPost<CheckoutResponse>('/orders', input, { headers: { 'Idempotency-Key': idempotencyKey } }),
  list: (status: OrderStatus | undefined, page: number, size = 10) =>
    apiGet<Page<OrderSummary>>('/orders', { status, page, size }),
  get: (orderId: number) => apiGet<OrderDetail>(`/orders/${orderId}`),
  cancel: (orderId: number, reason: string | null) => apiPost<OrderDetail>(`/orders/${orderId}/cancel`, { reason }),
  pay: (orderId: number) => apiPost<RazorpayCheckout>(`/orders/${orderId}/payment`),
};

export interface VerifyInput {
  orderId: number;
  razorpayOrderId: string;
  razorpayPaymentId: string;
  razorpaySignature: string;
}

export interface FailureInput {
  orderId: number;
  razorpayOrderId: string | null;
  razorpayPaymentId: string | null;
  code: string | null;
  description: string | null;
  reason: string | null;
}

export const paymentsApi = {
  verify: (input: VerifyInput) => apiPost<OrderDetail>('/payments/razorpay/verify', input),
  failure: (input: FailureInput) => apiPost<OrderDetail>('/payments/razorpay/failure', input),
};

export const orderKey = (orderId: number) => ['me', 'orders', 'detail', orderId] as const;

const ACTIVE: OrderStatus[] = ['PENDING_PAYMENT', 'PLACED', 'CONFIRMED', 'PROCESSING', 'SHIPPED', 'OUT_FOR_DELIVERY'];

/**
 * One order, kept current by WebSocket pushes (see RealtimeBridge). While the live connection is
 * down, active orders are polled instead.
 */
export function useOrder(orderId: number) {
  const realtimeStatus = useRealtimeStatus();
  return useQuery({
    queryKey: orderKey(orderId),
    queryFn: () => ordersApi.get(orderId),
    enabled: Number.isFinite(orderId),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return realtimeStatus !== 'connected' && status && ACTIVE.includes(status) ? 15_000 : false;
    },
  });
}
