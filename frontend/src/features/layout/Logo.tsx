import { Link } from 'react-router';
import { cn } from '@/lib/cn';

export function LogoMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 64 64" className={className} aria-hidden>
      <rect width="64" height="64" rx="16" fill="#0f172a" />
      <path d="M20 24h28l-3 20a4 4 0 0 1-4 3.5H27a4 4 0 0 1-4-3.5L20 24z" fill="none" stroke="#fb923c" strokeWidth="4" strokeLinejoin="round" />
      <path d="M26 24a6 6 0 0 1 12 0" fill="none" stroke="#fff" strokeWidth="4" strokeLinecap="round" />
    </svg>
  );
}

export function Logo({ className, to = '/' }: { className?: string; to?: string }) {
  return (
    <Link to={to} className={cn('flex items-center gap-2 font-bold tracking-tight text-slate-900', className)} aria-label="ShopSmart home">
      <LogoMark className="h-8 w-8" />
      <span className="text-lg">Shop<span className="text-brand-600">Smart</span></span>
    </Link>
  );
}
