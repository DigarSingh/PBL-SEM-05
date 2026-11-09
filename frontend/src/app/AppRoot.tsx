import { Outlet, ScrollRestoration, useNavigation } from 'react-router';
import { RealtimeBridge } from './RealtimeBridge';
import { SessionEvents } from './SessionEvents';

/** Root route: app-wide side effects plus a thin progress bar while the next page loads. */
export function AppRoot() {
  const navigation = useNavigation();
  return (
    <>
      {navigation.state !== 'idle' && (
        <div className="fixed inset-x-0 top-0 z-[60] h-0.5 animate-pulse bg-brand-500" role="progressbar" aria-label="Loading page" />
      )}
      <ScrollRestoration />
      <SessionEvents />
      <RealtimeBridge />
      <Outlet />
    </>
  );
}
