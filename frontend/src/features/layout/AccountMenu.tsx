import { Menu, MenuButton, MenuItem, MenuItems } from '@headlessui/react';
import { Bell, ChevronDown, Heart, LayoutDashboard, LogOut, MapPin, Package, User } from 'lucide-react';
import { Link } from 'react-router';
import { ButtonLink } from '@/components/ui/Button';
import { isAdmin, useSession } from '@/features/auth/useSession';
import { useSignOut } from '@/features/auth/useSignOut';
import { initials } from '@/lib/format';

const itemClass = 'flex w-full items-center gap-2.5 rounded-lg px-3 py-2 text-sm text-slate-700 data-focus:bg-stone-100 data-focus:text-slate-900';

const accountLinks = [
  { to: '/orders', label: 'My orders', icon: Package },
  { to: '/wishlist', label: 'Wishlist', icon: Heart },
  { to: '/notifications', label: 'Notifications', icon: Bell },
  { to: '/addresses', label: 'Addresses', icon: MapPin },
  { to: '/profile', label: 'Profile & security', icon: User },
];

export function AccountMenu() {
  const { status, user } = useSession();
  const signOut = useSignOut();

  if (status !== 'authenticated' || !user) {
    return (
      <div className="flex items-center gap-1.5 pl-1">
        <ButtonLink to="/login" variant="ghost" size="sm" className="hidden sm:inline-flex">Sign in</ButtonLink>
        <ButtonLink to="/register" size="sm" className="hidden sm:inline-flex">Create account</ButtonLink>
        <Link to="/login" className="flex h-10 w-10 items-center justify-center rounded-lg text-slate-700 hover:bg-stone-100 sm:hidden" aria-label="Sign in">
          <User className="h-5 w-5" aria-hidden />
        </Link>
      </div>
    );
  }

  return (
    <Menu as="div" className="relative">
      <MenuButton className="flex items-center gap-2 rounded-lg px-1.5 py-1 hover:bg-stone-100 focus:outline-none data-focus:ring-2 data-focus:ring-brand-500" aria-label="Account menu">
        <span className="flex h-8 w-8 items-center justify-center rounded-full bg-ink text-xs font-bold text-white">{initials(user.fullName)}</span>
        <span className="hidden max-w-28 truncate text-sm font-medium text-slate-700 lg:block">{user.fullName.split(' ')[0]}</span>
        <ChevronDown className="hidden h-4 w-4 text-slate-500 lg:block" aria-hidden />
      </MenuButton>
      <MenuItems anchor="bottom end" transition
        className="z-50 mt-2 w-64 rounded-2xl border border-stone-200 bg-white p-1.5 shadow-xl transition duration-150 focus:outline-none data-closed:translate-y-1 data-closed:opacity-0">
        <div className="px-3 py-2">
          <p className="truncate text-sm font-semibold text-slate-900">{user.fullName}</p>
          <p className="truncate text-xs text-slate-500">{user.email}</p>
        </div>
        <div className="my-1 h-px bg-stone-100" />
        {isAdmin(user) && (
          <MenuItem>
            <Link to="/admin" className={itemClass}><LayoutDashboard className="h-4 w-4" aria-hidden />Admin dashboard</Link>
          </MenuItem>
        )}
        {accountLinks.map(({ to, label, icon: Icon }) => (
          <MenuItem key={to}>
            <Link to={to} className={itemClass}><Icon className="h-4 w-4" aria-hidden />{label}</Link>
          </MenuItem>
        ))}
        <div className="my-1 h-px bg-stone-100" />
        <MenuItem>
          <button type="button" onClick={() => void signOut()} className={itemClass}>
            <LogOut className="h-4 w-4" aria-hidden />Sign out
          </button>
        </MenuItem>
      </MenuItems>
    </Menu>
  );
}
