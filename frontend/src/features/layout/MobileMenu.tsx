import { ChevronRight, Heart, Home, LayoutDashboard, LogOut, Package, Percent, ShoppingBag, User } from 'lucide-react';
import { NavLink } from 'react-router';
import { Drawer } from '@/components/ui/Dialog';
import { isAdmin, useSession } from '@/features/auth/useSession';
import { useSignOut } from '@/features/auth/useSignOut';
import { cn } from '@/lib/cn';
import type { Category } from '@/lib/types';

const linkClass = ({ isActive }: { isActive: boolean }) =>
  cn('flex items-center gap-3 rounded-xl px-3.5 py-3 text-sm font-medium transition-colors',
    isActive ? 'bg-brand-50 text-brand-900 ring-1 ring-inset ring-brand-100' : 'text-slate-700 hover:bg-stone-50');

export function MobileMenu({ open, onClose, categories }: { open: boolean; onClose: () => void; categories: Category[] }) {
  const { status, user } = useSession();
  const signOut = useSignOut();
  return (
    <Drawer open={open} onClose={onClose} title="Explore ShopSmart" side="left">
      <nav aria-label="Mobile" className="space-y-6">
        <div className="rounded-2xl border border-stone-200 bg-stone-50/70 p-1.5">
          <NavLink to="/" end onClick={onClose} className={linkClass}><Home className="h-4 w-4" aria-hidden />Home</NavLink>
          <NavLink to="/shop" end onClick={onClose} className={linkClass}><ShoppingBag className="h-4 w-4" aria-hidden />Products</NavLink>
        </div>
        <div>
          <p className="px-3 pb-2 text-[10px] font-bold uppercase tracking-[0.16em] text-slate-400">Categories</p>
          <div className="space-y-1">
            {categories.map((category) => (
              <NavLink key={category.id} to={`/categories/${category.slug}`} onClick={onClose} className={linkClass}>
                <span className="flex-1">{category.name}</span><ChevronRight className="h-4 w-4 text-slate-400" aria-hidden />
              </NavLink>
            ))}
          </div>
        </div>
        <div className="rounded-2xl border border-stone-200 bg-stone-50/70 p-1.5">
          <NavLink to="/shop?sort=discount" onClick={onClose} className={({ isActive }) => cn(linkClass({ isActive }), !isActive && 'text-brand-800')}>
            <Percent className="h-4 w-4" aria-hidden />Deals
          </NavLink>
          <NavLink to="/orders" onClick={onClose} className={linkClass}><Package className="h-4 w-4" aria-hidden />Track Order</NavLink>
        </div>
        <div>
          <p className="px-3 pb-2 text-[10px] font-bold uppercase tracking-[0.16em] text-slate-400">Your account</p>
          <div className="space-y-1">
            {status === 'authenticated' && user ? (
              <>
                {isAdmin(user) && (
                  <NavLink to="/admin" onClick={onClose} className={linkClass}><LayoutDashboard className="h-4 w-4" aria-hidden />Admin dashboard</NavLink>
                )}
                <NavLink to="/wishlist" onClick={onClose} className={linkClass}><Heart className="h-4 w-4" aria-hidden />Wishlist</NavLink>
                <NavLink to="/profile" onClick={onClose} className={linkClass}><User className="h-4 w-4" aria-hidden />Profile</NavLink>
                <button type="button" onClick={() => {
                  onClose();
                  void signOut();
                }} className={linkClass({ isActive: false })}>
                  <LogOut className="h-4 w-4" aria-hidden />Sign out
                </button>
              </>
            ) : (
              <>
                <NavLink to="/login" onClick={onClose} className={linkClass}><User className="h-4 w-4" aria-hidden />Sign in</NavLink>
                <NavLink to="/register" onClick={onClose} className={linkClass}><User className="h-4 w-4" aria-hidden />Create account</NavLink>
              </>
            )}
          </div>
        </div>
      </nav>
    </Drawer>
  );
}
