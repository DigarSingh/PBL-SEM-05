import { SearchX } from 'lucide-react';
import { ButtonLink } from '@/components/ui/Button';
import { useDocumentTitle } from '@/lib/hooks';

export default function NotFoundPage() {
  useDocumentTitle('Page not found');
  return (
    <div className="container-page py-24">
      <div className="mx-auto max-w-md text-center">
        <span className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-stone-100 text-slate-500">
          <SearchX className="h-7 w-7" aria-hidden />
        </span>
        <p className="mt-6 text-sm font-semibold text-brand-700">404</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-900">We couldn&apos;t find that page</h1>
        <p className="mt-3 text-slate-500">The link may be broken or the page may have moved. Try searching, or head back to the store.</p>
        <div className="mt-8 flex justify-center gap-2">
          <ButtonLink to="/">Go to home</ButtonLink>
          <ButtonLink to="/shop" variant="secondary">Browse products</ButtonLink>
        </div>
      </div>
    </div>
  );
}
