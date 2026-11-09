import { Mail, ShieldCheck, Truck, Undo2 } from 'lucide-react';
import { Link } from 'react-router';
import { useCategories } from '@/features/catalog/api';
import { usePublicSettings } from '@/features/settings/api';
import { formatPrice } from '@/lib/format';
import { LogoMark } from './Logo';

const YEAR = new Date().getFullYear();

export function Footer() {
  const { data: categories = [] } = useCategories();
  const { data: settings } = usePublicSettings();
  return (
    <footer className="mt-16 border-t border-stone-200 bg-white">
      <div className="container-page grid gap-4 border-b border-stone-100 py-6 text-sm text-slate-600 sm:grid-cols-3">
        <p className="flex items-center gap-2">
          <Truck className="h-5 w-5 text-brand-600" aria-hidden />
          {settings ? `Free delivery on orders above ${formatPrice(settings.freeShippingThreshold)}` : 'Fast delivery across India'}
        </p>
        <p className="flex items-center gap-2"><ShieldCheck className="h-5 w-5 text-brand-600" aria-hidden />Secure payments via Razorpay</p>
        <p className="flex items-center gap-2"><Undo2 className="h-5 w-5 text-brand-600" aria-hidden />Cancel anytime before shipping</p>
      </div>
      <div className="container-page grid gap-8 py-10 sm:grid-cols-2 lg:grid-cols-4">
        <div>
          <div className="flex items-center gap-2 font-bold text-slate-900">
            <LogoMark className="h-7 w-7" /> ShopSmart
          </div>
          <p className="mt-3 text-sm text-slate-500">Phones, laptops, audio, wearables and accessories, with live order tracking.</p>
          {settings?.supportEmail && (
            <a href={`mailto:${settings.supportEmail}`} className="mt-4 inline-flex items-center gap-2 text-sm font-medium text-slate-700 hover:text-brand-700">
              <Mail className="h-4 w-4" aria-hidden />{settings.supportEmail}
            </a>
          )}
        </div>
        <FooterColumn title="Shop" links={[
          { to: '/shop', label: 'All products' },
          { to: '/shop?sort=discount', label: 'Deals' },
          { to: '/shop?sort=newest', label: 'New arrivals' },
          { to: '/shop?sort=rating', label: 'Top rated' },
        ]} />
        <FooterColumn title="Categories" links={categories.slice(0, 6).map((category) => ({ to: `/categories/${category.slug}`, label: category.name }))} />
        <FooterColumn title="Your account" links={[
          { to: '/orders', label: 'Orders & tracking' },
          { to: '/wishlist', label: 'Wishlist' },
          { to: '/cart', label: 'Cart' },
          { to: '/profile', label: 'Profile' },
        ]} />
      </div>
      <div className="border-t border-stone-100">
        <p className="container-page py-5 text-xs text-slate-400">&copy; {YEAR} ShopSmart. All prices in INR and include GST where applicable.</p>
      </div>
    </footer>
  );
}

function FooterColumn({ title, links }: { title: string; links: { to: string; label: string }[] }) {
  return (
    <div>
      <p className="text-sm font-semibold text-slate-900">{title}</p>
      <ul className="mt-3 space-y-2 text-sm">
        {links.map((link) => (
          <li key={link.to}><Link to={link.to} className="text-slate-500 hover:text-slate-900">{link.label}</Link></li>
        ))}
      </ul>
    </div>
  );
}
