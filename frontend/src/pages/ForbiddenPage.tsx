import { Lock } from 'lucide-react';
import { ButtonLink } from '@/components/ui/Button';
import { useDocumentTitle } from '@/lib/hooks';

export function ForbiddenPage() {
  useDocumentTitle('Access denied');
  return (
    <div className="container-page py-24">
      <div className="mx-auto max-w-md text-center">
        <span className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-rose-50 text-rose-600">
          <Lock className="h-7 w-7" aria-hidden />
        </span>
        <h1 className="mt-6 text-3xl font-bold tracking-tight text-slate-900">You don&apos;t have access to this page</h1>
        <p className="mt-3 text-slate-500">This area is only available to store administrators.</p>
        <div className="mt-8 flex justify-center">
          <ButtonLink to="/">Back to the store</ButtonLink>
        </div>
      </div>
    </div>
  );
}
