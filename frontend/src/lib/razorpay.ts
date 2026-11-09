import type { RazorpayCheckout } from './types';

export interface RazorpaySuccess {
  razorpay_payment_id: string;
  razorpay_order_id: string;
  razorpay_signature: string;
}

export interface RazorpayFailure {
  code?: string;
  description?: string;
  reason?: string;
  metadata?: { payment_id?: string; order_id?: string };
}

interface RazorpayInstance {
  open(): void;
  on(event: 'payment.failed', handler: (response: { error: RazorpayFailure }) => void): void;
}

declare global {
  interface Window {
    Razorpay?: new (options: Record<string, unknown>) => RazorpayInstance;
  }
}

const SCRIPT_URL = 'https://checkout.razorpay.com/v1/checkout.js';
let loading: Promise<void> | null = null;

export function loadRazorpay(): Promise<void> {
  if (window.Razorpay) {
    return Promise.resolve();
  }
  if (!loading) {
    loading = new Promise<void>((resolve, reject) => {
      const script = document.createElement('script');
      script.src = SCRIPT_URL;
      script.async = true;
      script.onload = () => resolve();
      script.onerror = () => {
        loading = null;
        script.remove();
        reject(new Error('The payment window could not be loaded. Check your connection and try again.'));
      };
      document.body.appendChild(script);
    });
  }
  return loading;
}

export interface PaymentCallbacks {
  onSuccess: (response: RazorpaySuccess) => void;
  /** A failed attempt; Razorpay keeps its window open so the shopper can retry another way. */
  onFailure: (failure: RazorpayFailure) => void;
  /** The shopper closed the window without a successful payment. */
  onDismiss: () => void;
}

/**
 * Opens Razorpay Checkout for an order created by the backend. Only the public key id is used in
 * the browser; the payment is confirmed by the backend (signature + Razorpay API) before the
 * order counts as paid.
 */
export async function openRazorpay(checkout: RazorpayCheckout, callbacks: PaymentCallbacks): Promise<void> {
  await loadRazorpay();
  if (!window.Razorpay) {
    throw new Error('The payment window is unavailable. Please try again.');
  }
  let succeeded = false;
  const instance = new window.Razorpay({
    key: checkout.keyId,
    order_id: checkout.razorpayOrderId,
    amount: checkout.amount,
    currency: checkout.currency,
    name: checkout.name,
    description: checkout.description,
    prefill: { name: checkout.prefill.name, email: checkout.prefill.email, contact: checkout.prefill.contact ?? undefined },
    notes: { order_number: checkout.orderNumber },
    theme: { color: '#0f172a' },
    retry: { enabled: true },
    handler: (response: RazorpaySuccess) => {
      succeeded = true;
      callbacks.onSuccess(response);
    },
    modal: {
      ondismiss: () => {
        if (!succeeded) {
          callbacks.onDismiss();
        }
      },
    },
  });
  instance.on('payment.failed', (response) => callbacks.onFailure(response.error));
  instance.open();
}
