import { Bell, Heart, MapPin, Package, User } from 'lucide-react';
import { NavLink, Outlet } from 'react-router';
import { useSession } from '@/features/auth/useSession';
import { cn } from '@/lib/cn';
import { initials } from '@/lib/format';

const LINKS = [
  { to: '/orders', label: 'Orders', icon: Package },
  { to: '/wishlist', label: 'Wishlist', icon: Heart },
  { to: '/addresses', label: 'Addresses', icon: MapPin },
  { to: '/notifications', label: 'Notifications', icon: Bell },
  { to: '/profile', label: 'Profile & security', icon: User },
];

export function AccountLayout() {
  const { user } = useSession();
  return (
    <div className="container-page py-8">
      <div className="grid gap-8 lg:grid-cols-[15rem_1fr]">
        <aside>
          {user && (
            <div className="mb-4 flex items-center gap-3 rounded-2xl border border-stone-200 bg-white p-4">
              <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-ink text-sm font-bold text-white">{initials(user.fullName)}</span>
              <span className="min-w-0">
                <span className="block truncate text-sm font-semibold text-slate-900">{user.fullName}</span>
                <span className="block truncate text-xs text-slate-500">{user.email}</span>
              </span>
            </div>
          )}
          <nav aria-label="Account" className="flex gap-1 overflow-x-auto scrollbar-none lg:flex-col">
            {LINKS.map(({ to, label, icon: Icon }) => (
              <NavLink key={to} to={to} className={({ isActive }) => cn('flex items-center gap-2.5 whitespace-nowrap rounded-lg px-3 py-2 text-sm font-medium',
                isActive ? 'bg-white text-slate-900 shadow-xs ring-1 ring-stone-200' : 'text-slate-600 hover:bg-white hover:text-slate-900')}>
                <Icon className="h-4 w-4" aria-hidden />{label}
              </NavLink>
            ))}
          </nav>
        </aside>
        <div className="min-w-0">
          <Outlet />
        </div>
      </div>
    </div>
  );
}
