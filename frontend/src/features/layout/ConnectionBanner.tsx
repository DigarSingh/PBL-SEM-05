import { RefreshCw, WifiOff } from 'lucide-react';
import { useSession } from '@/features/auth/useSession';
import { useOnlineStatus, useRealtimeStatus } from '@/lib/hooks';

/** Tells the user when they are offline or when live updates are temporarily unavailable. */
export function ConnectionBanner() {
  const online = useOnlineStatus();
  const { status } = useSession();
  const realtimeStatus = useRealtimeStatus();
  if (!online) {
    return (
      <div role="status" className="bg-amber-100 text-amber-900">
        <p className="container-page flex items-center gap-2 py-2 text-sm font-medium">
          <WifiOff className="h-4 w-4" aria-hidden />
          You&apos;re offline. Browsing may show saved data; checkout is unavailable until you reconnect.
        </p>
      </div>
    );
  }
  if (status === 'authenticated' && realtimeStatus === 'reconnecting') {
    return (
      <div role="status" className="bg-stone-100 text-slate-600">
        <p className="container-page flex items-center gap-2 py-1.5 text-xs">
          <RefreshCw className="h-3.5 w-3.5 animate-spin" aria-hidden />
          Live updates are reconnecting. Order and stock information refreshes automatically in the meantime.
        </p>
      </div>
    );
  }
  return null;
}
