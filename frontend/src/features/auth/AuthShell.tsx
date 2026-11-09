import { ShieldCheck, Truck, Zap } from 'lucide-react';
import type { ReactNode } from 'react';
import { Card } from '@/components/ui/Card';

const perks = [
  { icon: Zap, text: 'Smart search that understands what you need' },
  { icon: Truck, text: 'Live order tracking from checkout to doorstep' },
  { icon: ShieldCheck, text: 'Secure payments with UPI, cards and cash on delivery' },
];

/** Centered card layout shared by the sign-in, sign-up and password pages. */
export function AuthShell({ title, subtitle, children, footer }: {
  title: string;
  subtitle?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
}) {
  return (
    <div className="container-page grid items-start gap-10 py-10 sm:py-16 lg:grid-cols-[1fr_28rem] lg:gap-16">
      <div className="hidden pt-8 lg:block">
        <p className="text-sm font-semibold uppercase tracking-widest text-brand-600">ShopSmart</p>
        <h2 className="mt-3 max-w-md text-4xl font-bold tracking-tight text-slate-900">
          Electronics you&apos;ll love, delivered with care.
        </h2>
        <ul className="mt-8 space-y-4">
          {perks.map(({ icon: Icon, text }) => (
            <li key={text} className="flex items-center gap-3 text-slate-600">
              <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-brand-50 text-brand-600">
                <Icon className="h-5 w-5" aria-hidden />
              </span>
              {text}
            </li>
          ))}
        </ul>
      </div>
      <div className="mx-auto w-full max-w-md animate-slide-up">
        <Card className="p-6 sm:p-8">
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">{title}</h1>
          {subtitle && <p className="mt-1.5 text-sm text-slate-500">{subtitle}</p>}
          <div className="mt-6">{children}</div>
        </Card>
        {footer && <div className="mt-5 text-center text-sm text-slate-600">{footer}</div>}
      </div>
    </div>
  );
}
