import type { OrderStatus, PaymentStatus, StockStatus } from './types';

const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2, minimumFractionDigits: 0 });
const inrWhole = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
const compact = new Intl.NumberFormat('en-IN', { notation: 'compact', maximumFractionDigits: 1 });
const dateFormat = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
const dateTimeFormat = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' });
const relative = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });

export function formatPrice(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '-';
  }
  return Number.isInteger(value) ? inrWhole.format(value) : inr.format(value);
}

export function formatCompactPrice(value: number): string {
  return `₹${compact.format(value)}`;
}

export function formatNumber(value: number): string {
  return new Intl.NumberFormat('en-IN').format(value);
}

const dayFormat = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', timeZone: 'UTC' });
const monthFormat = new Intl.DateTimeFormat('en-IN', { month: 'short', year: 'numeric', timeZone: 'UTC' });

/** A calendar day from the API ("2026-09-24") as "24 Sept"; formatted in UTC so it never shifts a day. */
export function formatDay(isoDate: string): string {
  return dayFormat.format(new Date(`${isoDate.slice(0, 10)}T00:00:00Z`));
}

export function formatMonth(isoDate: string): string {
  return monthFormat.format(new Date(`${isoDate.slice(0, 10)}T00:00:00Z`));
}

export function formatDate(value: string | null | undefined): string {
  return value ? dateFormat.format(new Date(value)) : '-';
}

export function formatDateTime(value: string | null | undefined): string {
  return value ? dateTimeFormat.format(new Date(value)) : '-';
}

export function formatRelative(value: string): string {
  const seconds = Math.round((new Date(value).getTime() - Date.now()) / 1000);
  const units: [Intl.RelativeTimeFormatUnit, number][] = [['year', 31536000], ['month', 2592000], ['week', 604800],
    ['day', 86400], ['hour', 3600], ['minute', 60]];
  for (const [unit, size] of units) {
    if (Math.abs(seconds) >= size) {
      return relative.format(Math.round(seconds / size), unit);
    }
  }
  return 'just now';
}

export function formatPercent(value: number | null | undefined, digits = 0): string {
  if (value === null || value === undefined) {
    return '-';
  }
  return `${value.toFixed(digits)}%`;
}

export type Tone = 'neutral' | 'brand' | 'success' | 'warning' | 'danger' | 'info';

export const orderStatusMeta: Record<OrderStatus, { label: string; tone: Tone }> = {
  PENDING_PAYMENT: { label: 'Awaiting payment', tone: 'warning' },
  PLACED: { label: 'Placed', tone: 'info' },
  CONFIRMED: { label: 'Confirmed', tone: 'info' },
  PROCESSING: { label: 'Processing', tone: 'brand' },
  SHIPPED: { label: 'Shipped', tone: 'brand' },
  OUT_FOR_DELIVERY: { label: 'Out for delivery', tone: 'brand' },
  DELIVERED: { label: 'Delivered', tone: 'success' },
  CANCELLED: { label: 'Cancelled', tone: 'danger' },
};

export const paymentStatusMeta: Record<PaymentStatus, { label: string; tone: Tone }> = {
  PENDING: { label: 'Pending', tone: 'warning' },
  AUTHORIZED: { label: 'Authorised', tone: 'info' },
  PAID: { label: 'Paid', tone: 'success' },
  FAILED: { label: 'Failed', tone: 'danger' },
  REFUNDED: { label: 'Refunded', tone: 'neutral' },
  CANCELLED: { label: 'Cancelled', tone: 'neutral' },
};

export const stockMeta: Record<StockStatus, { label: string; tone: Tone }> = {
  IN_STOCK: { label: 'In stock', tone: 'success' },
  LOW_STOCK: { label: 'Low stock', tone: 'warning' },
  OUT_OF_STOCK: { label: 'Out of stock', tone: 'danger' },
};

/** "Asha Rao" -> "AR", used for avatar placeholders. */
export function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  return ((parts[0]?.[0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase() || '?';
}

export function paymentMethodLabel(method: string): string {
  return method === 'COD' ? 'Cash on Delivery' : method === 'RAZORPAY' ? 'Online (Razorpay)' : method;
}
