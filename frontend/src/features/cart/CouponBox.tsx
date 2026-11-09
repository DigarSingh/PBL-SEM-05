import { CircleCheck, CircleX, Ticket } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Field';
import { cn } from '@/lib/cn';
import { errorMessage } from '@/lib/errors';
import { formatPrice } from '@/lib/format';
import type { Cart } from '@/lib/types';
import { cartApi, useCartMutation, useOffers } from './api';

export function CouponBox({ cart }: { cart: Cart }) {
  const [code, setCode] = useState('');
  const offers = useOffers();
  const apply = useCartMutation((value: string) => cartApi.applyCoupon(value));
  const remove = useCartMutation(() => cartApi.removeCoupon());

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const value = code.trim().toUpperCase();
    if (!value) {
      return;
    }
    apply.mutate(value, {
      onSuccess: (updated) => {
        setCode('');
        if (updated.coupon?.valid) {
          toast.success(`Coupon ${value} applied`);
        }
      },
    });
  };

  if (cart.coupon) {
    return (
      <div className={cn('rounded-xl border px-3 py-2.5 text-sm', cart.coupon.valid ? 'border-emerald-200 bg-emerald-50' : 'border-amber-200 bg-amber-50')}>
        <div className="flex items-center justify-between gap-2">
          <p className="flex items-center gap-2 font-semibold text-slate-900">
            {cart.coupon.valid ? <CircleCheck className="h-4 w-4 text-emerald-600" aria-hidden /> : <CircleX className="h-4 w-4 text-amber-600" aria-hidden />}
            <span className="font-mono">{cart.coupon.code}</span>
          </p>
          <Button variant="link" size="sm" loading={remove.isPending} onClick={() => remove.mutate(undefined, {
            onError: (error) => toast.error(errorMessage(error)),
          })}>Remove</Button>
        </div>
        <p className={cn('mt-1 text-xs', cart.coupon.valid ? 'text-emerald-800' : 'text-amber-800')}>{cart.coupon.message}</p>
      </div>
    );
  }

  return (
    <div>
      <form onSubmit={submit} className="flex gap-2">
        <label htmlFor="coupon-code" className="sr-only">Coupon code</label>
        <Input id="coupon-code" value={code} onChange={(event) => setCode(event.target.value)} placeholder="Coupon code"
          className="font-mono uppercase" autoComplete="off" aria-invalid={apply.isError}
          aria-describedby={apply.isError ? 'coupon-error' : undefined} />
        <Button type="submit" variant="secondary" loading={apply.isPending} disabled={!code.trim()}>Apply</Button>
      </form>
      {apply.isError && <p id="coupon-error" className="mt-1.5 text-xs font-medium text-rose-600" role="alert">{errorMessage(apply.error)}</p>}
      {offers.data && offers.data.length > 0 && (
        <ul className="mt-3 space-y-2">
          {offers.data.map((offer) => (
            <li key={offer.code}>
              <button type="button" onClick={() => apply.mutate(offer.code, { onSuccess: (updated) => {
                if (updated.coupon?.valid) {
                  toast.success(`Coupon ${offer.code} applied`);
                }
              } })}
                className="flex w-full items-start gap-2 rounded-lg border border-dashed border-stone-300 px-3 py-2 text-left text-xs hover:border-brand-400 hover:bg-brand-50/50">
                <Ticket className="mt-0.5 h-3.5 w-3.5 shrink-0 text-brand-600" aria-hidden />
                <span>
                  <span className="font-mono font-bold text-slate-900">{offer.code}</span>{' '}
                  <span className="text-slate-600">
                    {offer.discountType === 'PERCENTAGE' ? `${offer.discountValue}% off` : `${formatPrice(offer.discountValue)} off`}
                    {offer.minOrderAmount ? ` on orders above ${formatPrice(offer.minOrderAmount)}` : ''}
                    {offer.maxDiscountAmount ? ` (up to ${formatPrice(offer.maxDiscountAmount)})` : ''}
                  </span>
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
