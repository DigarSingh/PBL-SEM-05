import { AlertTriangle } from 'lucide-react';
import { isRouteErrorResponse, useRouteError } from 'react-router';
import { Button, ButtonLink } from '@/components/ui/Button';
import NotFoundPage from './NotFoundPage';

const CHUNK_ERROR = /dynamically imported module|Importing a module script failed|error loading dynamically imported/i;

/** Route error boundary: 404s, failed code-split downloads (after a deploy) and unexpected errors. */
export function RouteErrorPage() {
  const error = useRouteError();
  if (isRouteErrorResponse(error) && error.status === 404) {
    return <NotFoundPage />;
  }
  const staleBundle = error instanceof Error && CHUNK_ERROR.test(error.message);
  return (
    <div className="container-page py-24">
      <div className="mx-auto max-w-lg text-center">
        <span className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-rose-50 text-rose-600">
          <AlertTriangle className="h-7 w-7" aria-hidden />
        </span>
        <h1 className="mt-6 text-2xl font-bold tracking-tight text-slate-900">
          {staleBundle ? 'A newer version of the store is available' : 'Something went wrong'}
        </h1>
        <p className="mt-3 text-slate-500">
          {staleBundle
            ? 'Reload the page to continue with the latest version.'
            : 'An unexpected error stopped this page from loading. Reload to try again; if it keeps happening, contact support.'}
        </p>
        <div className="mt-8 flex justify-center gap-2">
          <Button onClick={() => window.location.reload()}>Reload page</Button>
          <ButtonLink to="/" variant="secondary">Go to home</ButtonLink>
        </div>
        {import.meta.env.DEV && error instanceof Error && (
          <pre className="mt-8 max-h-64 overflow-auto rounded-xl bg-stone-100 p-4 text-left text-xs text-slate-700">{error.stack}</pre>
        )}
      </div>
    </div>
  );
}
