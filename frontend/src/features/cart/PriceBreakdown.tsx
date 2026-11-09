import { Truck } from 'lucide-react';
import { formatPrice } from '@/lib/format';
import type { PriceQuote } from '@/lib/types';

/** Server-calculated totals. The browser never computes prices itself; it only displays them. */
export function PriceBreakdown({ quote, couponCode }: { quote: PriceQuote; couponCode?: string | null }) {
  const taxLabel = `GST (${Math.round(quote.taxRate * 100)}%)`;
  return (
    <div className="space-y-4">
      <dl className="space-y-2.5 text-sm">
        <Row label={`Price (${quote.itemCount} item${quote.itemCount === 1 ? '' : 's'})`} value={formatPrice(quote.mrpTotal)} />
        {quote.productSavings > 0 && <Row label="Discount" value={`− ${formatPrice(quote.productSavings)}`} positive />}
        {quote.couponDiscount > 0 && <Row label={couponCode ? `Coupon (${couponCode})` : 'Coupon'} value={`− ${formatPrice(quote.couponDiscount)}`} positive />}
        <Row label="Delivery" value={quote.shippingFee === 0 ? 'Free' : formatPrice(quote.shippingFee)} positive={quote.shippingFee === 0} />
        {!quote.taxInclusive && quote.taxAmount > 0 && <Row label={taxLabel} value={formatPrice(quote.taxAmount)} />}
        <div className="flex items-center justify-between border-t border-stone-200 pt-3 text-base font-bold text-slate-900">
          <dt>Total</dt>
          <dd>{formatPrice(quote.total)}</dd>
        </div>
      </dl>
      {quote.taxInclusive && quote.taxAmount > 0 && (
        <p className="text-xs text-slate-500">Includes {formatPrice(quote.taxAmount)} {taxLabel}.</p>
      )}
      {quote.productSavings + quote.couponDiscount > 0 && (
        <p className="rounded-lg bg-emerald-50 px-3 py-2 text-sm font-medium text-emerald-800">
          You save {formatPrice(quote.productSavings + quote.couponDiscount)} on this order
        </p>
      )}
      {quote.amountToFreeShipping > 0 && (
        <div className="text-xs text-slate-600">
          <p className="flex items-center gap-1.5"><Truck className="h-4 w-4 text-brand-600" aria-hidden />
            Add {formatPrice(quote.amountToFreeShipping)} more for free delivery.
          </p>
          <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-stone-200">
            <div className="h-full rounded-full bg-brand-500"
              style={{ width: `${Math.min(100, (quote.subtotal / Math.max(quote.freeShippingThreshold, 1)) * 100)}%` }} />
          </div>
        </div>
      )}
    </div>
  );
}

function Row({ label, value, positive = false }: { label: string; value: string; positive?: boolean }) {
  return (
    <div className="flex items-center justify-between gap-4">
      <dt className="text-slate-600">{label}</dt>
      <dd className={positive ? 'font-medium text-emerald-700' : 'font-medium text-slate-900'}>{value}</dd>
    </div>
  );
}
