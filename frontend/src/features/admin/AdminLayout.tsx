import {
  ArrowLeft, ChartLine, CreditCard, LayoutDashboard, Layers, Menu, MessageSquare, Package, ReceiptText, Settings, Ticket, Users,
  Warehouse,
} from 'lucide-react';
import { useState } from 'react';
import { Link, NavLink, Outlet } from 'react-router';
import { Drawer } from '@/components/ui/Dialog';
import { AccountMenu } from '@/features/layout/AccountMenu';
import { Logo, LogoMark } from '@/features/layout/Logo';
import { NotificationBell } from '@/features/notifications/NotificationBell';
import { cn } from '@/lib/cn';
import { useRealtimeStatus } from '@/lib/hooks';

const NAV_GROUPS = [
  { label: 'WORKSPACE', items: [
    { to: '/admin', label: 'Overview', icon: LayoutDashboard, end: true },
    { to: '/admin/orders', label: 'Orders', icon: ReceiptText },
  ] },
  { label: 'STORE', items: [
    { to: '/admin/products', label: 'Products', icon: Package },
    { to: '/admin/catalog', label: 'Categories & brands', icon: Layers },
    { to: '/admin/inventory', label: 'Inventory', icon: Warehouse },
    { to: '/admin/coupons', label: 'Coupons', icon: Ticket },
  ] },
  { label: 'CUSTOMERS', items: [
    { to: '/admin/customers', label: 'Customers', icon: Users },
    { to: '/admin/reviews', label: 'Reviews', icon: MessageSquare },
  ] },
  { label: 'INSIGHTS', items: [
    { to: '/admin/payments', label: 'Payments', icon: CreditCard },
    { to: '/admin/analytics', label: 'Analytics & insights', icon: ChartLine },
  ] },
  { label: 'PREFERENCES', items: [
    { to: '/admin/settings', label: 'Settings', icon: Settings },
  ] },
];

function NavLinks({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <nav aria-label="Admin" className="space-y-5">
      {NAV_GROUPS.map(({ label: groupLabel, items }) => (
        <section key={groupLabel}>
          <h2 className="mb-1.5 px-3 text-[10px] font-bold tracking-[0.16em] text-slate-500">{groupLabel}</h2>
          <div className="space-y-1">
            {items.map(({ to, label, icon: Icon, end }) => (
              <NavLink key={to} to={to} end={end} onClick={onNavigate}
                className={({ isActive }) => cn('group relative flex min-h-10 items-center gap-3 rounded-xl px-3 text-[13px] font-medium transition-colors',
                  isActive
                    ? 'bg-brand-400/15 text-white before:absolute before:inset-y-2 before:left-0 before:w-0.5 before:rounded-full before:bg-brand-300'
                    : 'text-slate-400 hover:bg-white/[0.06] hover:text-slate-100')}>
                <Icon className="h-[17px] w-[17px] shrink-0 text-slate-500 transition-colors group-hover:text-slate-300 group-aria-[current=page]:text-brand-200" aria-hidden />
                <span className="truncate">{label}</span>
              </NavLink>
            ))}
          </div>
        </section>
      ))}
    </nav>
  );
}

export function AdminLayout() {
  const [open, setOpen] = useState(false);
  const realtimeStatus = useRealtimeStatus();
  return (
    <div className="min-h-screen bg-stone-50 lg:pl-64">
      <aside className="fixed inset-y-0 left-0 z-40 hidden w-64 flex-col overflow-y-auto border-r border-white/10 bg-slate-950 px-3.5 py-5 lg:flex">
        <Link to="/admin" className="mb-7 flex items-center gap-3 rounded-xl px-2 py-1.5 text-white">
          <LogoMark className="h-10 w-10 shrink-0 rounded-xl shadow-lg shadow-black/20" />
          <span className="min-w-0">
            <span className="block text-[15px] font-bold tracking-tight">ShopSmart</span>
            <span className="mt-0.5 block text-[10px] font-semibold tracking-[0.18em] text-slate-500">ADMIN STUDIO</span>
          </span>
        </Link>
        <NavLinks />
        <Link to="/" className="mt-auto mt-6 flex items-center gap-3 rounded-xl border border-white/10 bg-white/[0.03] px-3 py-3 text-xs font-medium text-slate-400 transition-colors hover:border-white/15 hover:bg-white/[0.06] hover:text-white">
          <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-white/[0.06]"><ArrowLeft className="h-4 w-4" aria-hidden /></span>
          <span><span className="block text-slate-200">Back to the store</span><span className="mt-0.5 block text-[10px] text-slate-500">Return to storefront</span></span>
        </Link>
      </aside>

      <header className="sticky top-0 z-30 flex h-16 items-center gap-3 border-b border-stone-200/80 bg-white/90 px-4 backdrop-blur-xl sm:px-6 lg:px-8">
        <button type="button" onClick={() => setOpen(true)} aria-label="Open admin menu"
          className="-ml-1 flex h-10 w-10 items-center justify-center rounded-xl text-slate-700 transition-colors hover:bg-stone-100 lg:hidden">
          <Menu className="h-5 w-5" aria-hidden />
        </button>
        <Logo className="lg:hidden" to="/admin" />
        <div className="hidden lg:block">
          <p className="text-xs font-semibold text-slate-800">Store workspace</p>
          <p className="text-[11px] text-slate-500">Manage your shop in one place</p>
        </div>
        <span className={cn('ml-auto inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-[11px] font-semibold',
          realtimeStatus === 'connected' ? 'border-emerald-200 bg-emerald-50 text-emerald-800' : 'border-stone-200 bg-stone-50 text-slate-500')} role="status">
          <span className={cn('h-1.5 w-1.5 rounded-full', realtimeStatus === 'connected' ? 'bg-emerald-500 animate-pulse-ring' : 'bg-stone-400')} aria-hidden />
          {realtimeStatus === 'connected' ? 'Live updates' : 'Reconnecting'}
        </span>
        <NotificationBell />
        <AccountMenu />
      </header>

      <main id="main">
        <Outlet />
      </main>

      <Drawer open={open} onClose={() => setOpen(false)} title="ShopSmart admin" side="left">
        <div className="-mx-5 -my-4 min-h-full bg-ink px-4 py-4">
          <NavLinks onNavigate={() => setOpen(false)} />
          <Link to="/" onClick={() => setOpen(false)} className="mt-6 flex items-center gap-2 rounded-lg px-3 py-2 text-sm text-slate-400 hover:text-white">
            <ArrowLeft className="h-4 w-4" aria-hidden />Back to the store
          </Link>
        </div>
      </Drawer>
    </div>
  );
}
