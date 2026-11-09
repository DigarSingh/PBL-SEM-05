import { describe, expect, it } from 'vitest';
import type { PublicSettings } from '@/lib/types';
import { defaultPaymentMethod, paymentOptions } from './paymentOptions';

const settings: PublicSettings = {
  storeName: 'ShopSmart', supportEmail: 'support@example.com', currency: 'INR', taxRate: 0.18, pricesIncludeTax: true,
  shippingFee: 49, freeShippingThreshold: 499, codEnabled: true, codMaxOrderAmount: 50000, onlinePaymentsEnabled: true,
  razorpayKeyId: 'rzp_test_public',
};

describe('paymentOptions', () => {
  it('offers online payment first and COD within its limit', () => {
    const options = paymentOptions(settings, 1200);
    expect(options.map((option) => [option.method, option.available])).toEqual([['RAZORPAY', true], ['COD', true]]);
    expect(defaultPaymentMethod(options)).toBe('RAZORPAY');
  });

  it('explains why COD is unavailable above the limit', () => {
    const cod = paymentOptions(settings, 60000).find((option) => option.method === 'COD');
    expect(cod?.available).toBe(false);
    expect(cod?.reason).toContain('50,000');
  });

  it('falls back to COD when online payments are not configured', () => {
    const options = paymentOptions({ ...settings, razorpayKeyId: null }, 1200);
    expect(options[0].available).toBe(false);
    expect(defaultPaymentMethod(options)).toBe('COD');
  });

  it('has no default when nothing is available', () => {
    expect(defaultPaymentMethod(paymentOptions({ ...settings, onlinePaymentsEnabled: false, codEnabled: false }, 100))).toBeNull();
  });
});
