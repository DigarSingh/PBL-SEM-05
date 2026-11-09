import { ChevronDown, ChevronRight, Heart, Home, Layers, Menu, Package, ShoppingBag, Tag } from 'lucide-react';
import { Menu as HeadlessMenu, MenuButton, MenuItem, MenuItems } from '@headlessui/react';
import { useState, type ReactNode } from 'react';
import { Link, NavLink, useLocation } from 'react-router';
import { useSession } from '@/features/auth/useSession';
import { useCartCount } from '@/features/cart/api';
import { useCategories } from '@/features/catalog/api';
import { NotificationBell } from '@/features/notifications/NotificationBell';
import { SearchBox } from '@/features/search/SearchBox';
import { useWishlistIds } from '@/features/wishlist/api';
import { cn } from '@/lib/cn';
import type { Category } from '@/lib/types';
import { AccountMenu } from './AccountMenu';
import { Logo } from './Logo';
import { MobileMenu } from './MobileMenu';

export function Header() {
  const { status } = useSession();
  const [menuOpen, setMenuOpen] = useState(false);
  const cartCount = useCartCount();
  const wishlistCount = useWishlistIds().size;
  const { data: categories = [] } = useCategories();

  return (
    <header className="sticky top-0 z-40 border-b border-stone-200/80 bg-white/95 shadow-[0_6px_24px_-20px_rgba(15,23,42,0.45)] backdrop-blur-xl">
      <div className="container-page flex h-[4.5rem] items-center gap-2 sm:gap-4 lg:h-[4.75rem] lg:gap-6">
        <button type="button" onClick={() => setMenuOpen(true)} aria-label="Open menu"
          className="-ml-2 flex h-10 w-10 items-center justify-center rounded-xl border border-stone-200 text-slate-700 transition-colors hover:border-stone-300 hover:bg-stone-50 lg:hidden">
          <Menu className="h-5 w-5" aria-hidden />
        </button>
        <Logo className="shrink-0" />
        <SearchBox className="mx-1 hidden max-w-2xl flex-1 md:block lg:mx-3" />
        <nav aria-label="Shopping" className="ml-auto flex shrink-0 items-center gap-1 rounded-2xl border border-stone-200/80 bg-stone-50/80 p-1 sm:gap-1.5">
          <HeaderIconLink to="/wishlist" label="Wishlist" count={wishlistCount}><Heart className="h-[18px] w-[18px]" aria-hidden /></HeaderIconLink>
          <HeaderIconLink to="/cart" label="Cart" count={cartCount}><ShoppingBag className="h-[18px] w-[18px]" aria-hidden /></HeaderIconLink>
          {status === 'authenticated' && <NotificationBell />}
          <span className="flex min-w-10 items-center justify-center border-l border-stone-200 pl-1 sm:pl-1.5"><AccountMenu /></span>
        </nav>
      </div>
      <div className="container-page pb-3 md:hidden">
        <SearchBox />
      </div>
      <PrimaryNav categories={categories} />
      <MobileMenu open={menuOpen} onClose={() => setMenuOpen(false)} categories={categories} />
    </header>
  );
}

function HeaderIconLink({ to, label, count, children }: { to: string; label: string; count: number; children: ReactNode }) {
  return (
    <Link to={to} aria-label={count > 0 ? `${label}, ${count} item${count === 1 ? '' : 's'}` : label}
      className="group relative flex h-10 items-center justify-center gap-2 rounded-xl px-2.5 text-slate-600 transition-colors hover:bg-white hover:text-slate-950 sm:px-3">
      {children}
      <span className="hidden text-xs font-semibold xl:inline">{label}</span>
      {count > 0 && (
        <span className="absolute right-1 top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-brand-500 px-1 text-[10px] font-bold leading-none text-white ring-2 ring-stone-50">
          {count > 99 ? '99+' : count}
        </span>
      )}
    </Link>
  );
}

function PrimaryNav({ categories }: { categories: Category[] }) {
  const { pathname, search } = useLocation();
  const dealsActive = pathname === '/shop' && new URLSearchParams(search).get('sort') === 'discount';
  const productsActive = pathname === '/shop' && !dealsActive;
  const categoriesActive = pathname.startsWith('/categories/');
  const linkClass = (active: boolean) =>
    cn('inline-flex h-10 items-center gap-2 whitespace-nowrap rounded-full px-4 text-[13px] font-semibold transition-colors',
      active ? 'bg-slate-900 text-white shadow-sm' : 'text-slate-600 hover:bg-stone-100 hover:text-slate-950');
  return (
    <nav aria-label="Main" className="hidden border-t border-stone-100/80 bg-white/90 lg:block">
      <ul className="container-page flex h-[3.5rem] items-center gap-1.5 overflow-x-auto scrollbar-none">
        <li>
          <NavLink to="/" end className={({ isActive }) => linkClass(isActive)}>
            <Home className="h-4 w-4" aria-hidden />Home
          </NavLink>
        </li>
        <li>
          <NavLink to="/shop" end className={() => linkClass(productsActive)}>
            <ShoppingBag className="h-4 w-4" aria-hidden />Products
          </NavLink>
        </li>
        <li>
          <HeadlessMenu as="div" className="relative">
            <MenuButton className={linkClass(categoriesActive)} aria-label="Browse categories">
              <Layers className="h-4 w-4" aria-hidden />Categories
              <ChevronDown className="h-3.5 w-3.5 opacity-60" aria-hidden />
            </MenuButton>
            <MenuItems anchor="bottom start" transition
              className="z-50 mt-2 w-64 origin-top-left rounded-2xl border border-stone-200 bg-white p-2 shadow-xl transition duration-150 focus:outline-none data-closed:-translate-y-1 data-closed:opacity-0">
              <p className="px-3 pb-1 pt-2 text-[10px] font-bold uppercase tracking-[0.16em] text-slate-500">Shop by category</p>
              <MenuItem>
                <Link to="/shop" className="flex items-center justify-between rounded-xl px-3 py-2.5 text-sm font-semibold text-slate-800 data-focus:bg-stone-50">
                  All products<ChevronRight className="h-4 w-4 text-slate-400" aria-hidden />
                </Link>
              </MenuItem>
              {categories.map((category) => (
                <MenuItem key={category.id}>
                  <Link to={`/categories/${category.slug}`} className="flex items-center justify-between rounded-xl px-3 py-2.5 text-sm text-slate-700 data-focus:bg-stone-50 data-focus:text-slate-950">
                    {category.name}<ChevronRight className="h-4 w-4 text-slate-400" aria-hidden />
                  </Link>
                </MenuItem>
              ))}
            </MenuItems>
          </HeadlessMenu>
        </li>
        <li>
          <NavLink to="/shop?sort=discount" className={() => linkClass(dealsActive)}>
            <Tag className="h-4 w-4" aria-hidden />Deals
          </NavLink>
        </li>
        <li className="ml-auto">
          <NavLink to="/orders" className={({ isActive }) => cn(linkClass(isActive),
            isActive ? 'border border-slate-900' : 'border border-stone-200 bg-white')}>
            <Package className="h-4 w-4" aria-hidden />Track Order
          </NavLink>
        </li>
      </ul>
    </nav>
  );
}
