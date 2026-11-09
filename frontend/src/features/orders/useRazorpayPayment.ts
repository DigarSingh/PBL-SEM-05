import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { toast } from 'sonner';
import { CART_KEY } from '@/features/cart/api';
import { errorMessage } from '@/lib/errors';
import { openRazorpay } from '@/lib/razorpay';
import type { RazorpayCheckout } from '@/lib/types';
import { orderKey, paymentsApi } from './api';

export type PaymentStep = 'idle' | 'open' | 'verifying' | 'done';

/**
 * Runs Razorpay Checkout for an order and hands the result to the backend for verification. The
 * order page is the single place the outcome is shown: paid, failed (with retry) or still pending.
 */
export function useRazorpayPayment() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [step, setStep] = useState<PaymentStep>('idle');

  const refreshOrderData = (orderId: number) => Promise.all([
    queryClient.invalidateQueries({ queryKey: orderKey(orderId) }),
    queryClient.invalidateQueries({ queryKey: ['me', 'orders'] }),
    queryClient.invalidateQueries({ queryKey: CART_KEY }),
  ]);

  const pay = async (checkout: RazorpayCheckout) => {
    setStep('open');
    try {
      await openRazorpay(checkout, {
        onSuccess: (response) => {
          setStep('verifying');
          paymentsApi.verify({
            orderId: checkout.orderId,
            razorpayOrderId: response.razorpay_order_id,
            razorpayPaymentId: response.razorpay_payment_id,
            razorpaySignature: response.razorpay_signature,
          }).then(async (order) => {
            queryClient.setQueryData(orderKey(order.id), order);
            // 'done' (not 'idle') so the now-empty checkout page doesn't bounce to the cart meanwhile.
            setStep('done');
            toast.success('Payment successful. Your order is confirmed!');
            navigate(`/orders/${order.id}?placed=1`, { replace: true });
            await refreshOrderData(order.id);
          }).catch(async (error) => {
            setStep('idle');
            toast.error(errorMessage(error));
            navigate(`/orders/${checkout.orderId}`, { replace: true });
            await refreshOrderData(checkout.orderId);
          });
        },
        onFailure: (failure) => {
          // Recorded for the order history; Razorpay lets the shopper retry in the same window.
          paymentsApi.failure({
            orderId: checkout.orderId,
            razorpayOrderId: failure.metadata?.order_id ?? checkout.razorpayOrderId,
            razorpayPaymentId: failure.metadata?.payment_id ?? null,
            code: failure.code ?? null,
            description: failure.description ?? null,
            reason: failure.reason ?? null,
          }).catch((error) => toast.error(errorMessage(error)));
          toast.error(failure.description ?? 'The payment did not go through. You can try again.');
        },
        onDismiss: () => {
          setStep('idle');
          void refreshOrderData(checkout.orderId);
          toast.info('Payment not completed. You can pay from the order page before it expires.');
          navigate(`/orders/${checkout.orderId}`, { replace: true });
        },
      });
    } catch (error) {
      setStep('idle');
      toast.error(errorMessage(error));
      navigate(`/orders/${checkout.orderId}`, { replace: true });
    }
  };

  return { pay, step, busy: step !== 'idle' };
}
