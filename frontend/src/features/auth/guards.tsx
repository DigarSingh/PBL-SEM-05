import type { ReactNode } from 'react';
import { Navigate, Outlet, useLocation, useSearchParams } from 'react-router';
import { Spinner } from '@/components/ui/Feedback';
import { loginPath, safeRedirect } from '@/lib/navigation';
import { ForbiddenPage } from '@/pages/ForbiddenPage';
import { isAdmin, useSession } from './useSession';

/** Customer pages: anonymous visitors are sent to sign in and brought back afterwards. */
export function RequireAuth({ children }: { children?: ReactNode }) {
  const { status } = useSession();
  const location = useLocation();
  if (status === 'loading') {
    return <Spinner className="py-32" label="Checking your session" />;
  }
  if (status === 'anonymous') {
    return <Navigate to={loginPath(location.pathname + location.search)} replace />;
  }
  return children ?? <Outlet />;
}

/**
 * Admin screens. This only hides the UI: every admin endpoint is independently protected by the
 * backend (role check), so a customer cannot reach admin data even by calling the API directly.
 */
export function RequireAdmin({ children }: { children?: ReactNode }) {
  const { status, user } = useSession();
  const location = useLocation();
  if (status === 'loading') {
    return <Spinner className="py-32" label="Checking your session" />;
  }
  if (status === 'anonymous') {
    return <Navigate to={loginPath(location.pathname + location.search)} replace />;
  }
  if (!isAdmin(user)) {
    return <ForbiddenPage />;
  }
  return children ?? <Outlet />;
}

/** Sign-in and sign-up pages: once signed in, continue to where the user was heading. */
export function GuestOnly() {
  const { status, user } = useSession();
  const [params] = useSearchParams();
  if (status === 'authenticated') {
    return <Navigate to={safeRedirect(params.get('redirect'), isAdmin(user) ? '/admin' : '/')} replace />;
  }
  return <Outlet />;
}
