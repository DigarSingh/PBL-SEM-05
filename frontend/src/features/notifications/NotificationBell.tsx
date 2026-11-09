import { Popover, PopoverButton, PopoverPanel } from '@headlessui/react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Bell, CheckCheck } from 'lucide-react';
import { Link, useNavigate } from 'react-router';
import { Spinner } from '@/components/ui/Feedback';
import { cn } from '@/lib/cn';
import { errorMessage } from '@/lib/errors';
import { formatRelative } from '@/lib/format';
import { useRealtimeStatus } from '@/lib/hooks';
import type { Notification } from '@/lib/types';
import { notificationsApi } from './api';

export function NotificationBell() {
  const realtimeStatus = useRealtimeStatus();
  // Pushed notifications invalidate this query; poll only while the live connection is down.
  const { data: unread = 0 } = useQuery({
    queryKey: ['me', 'notifications', 'unread'],
    queryFn: notificationsApi.unreadCount,
    refetchInterval: realtimeStatus === 'connected' ? false : 30_000,
  });

  return (
    <Popover className="relative">
      <PopoverButton className="relative flex h-10 w-10 items-center justify-center rounded-lg text-slate-700 hover:bg-stone-100 focus:outline-none data-focus:ring-2 data-focus:ring-brand-500"
        aria-label={unread > 0 ? `Notifications, ${unread} unread` : 'Notifications'}>
        <Bell className="h-5 w-5" aria-hidden />
        {unread > 0 && (
          <span className="absolute right-1 top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-brand-500 px-1 text-[10px] font-bold leading-none text-white">
            {unread > 9 ? '9+' : unread}
          </span>
        )}
      </PopoverButton>
      <PopoverPanel anchor="bottom end" transition
        className="z-50 mt-2 w-[22rem] max-w-[calc(100vw-1.5rem)] rounded-2xl border border-stone-200 bg-white shadow-xl transition duration-150 data-closed:translate-y-1 data-closed:opacity-0">
        {({ close }) => <NotificationList unread={unread} close={close} />}
      </PopoverPanel>
    </Popover>
  );
}

function NotificationList({ unread, close }: { unread: number; close: () => void }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const latest = useQuery({ queryKey: ['me', 'notifications', 'latest'], queryFn: () => notificationsApi.list(0, 8) });
  const markAll = useMutation({
    mutationFn: notificationsApi.markAllRead,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['me', 'notifications'] }),
  });

  const open = (notification: Notification) => {
    close();
    if (!notification.read) {
      notificationsApi.markRead(notification.id)
        .then(() => queryClient.invalidateQueries({ queryKey: ['me', 'notifications'] }))
        // Read state is cosmetic: if this fails the item simply stays unread.
        .catch(() => undefined);
    }
    if (notification.link) {
      navigate(notification.link);
    }
  };

  return (
    <div>
      <div className="flex items-center justify-between border-b border-stone-100 px-4 py-3">
        <p className="text-sm font-semibold text-slate-900">Notifications</p>
        {unread > 0 && (
          <button type="button" onClick={() => markAll.mutate()} disabled={markAll.isPending}
            className="flex items-center gap-1 text-xs font-semibold text-brand-700 hover:underline disabled:opacity-50">
            <CheckCheck className="h-3.5 w-3.5" aria-hidden /> Mark all read
          </button>
        )}
      </div>
      <div className="max-h-96 overflow-y-auto">
        {latest.isPending ? (
          <Spinner className="py-8" label="Loading notifications" />
        ) : latest.isError ? (
          <p className="px-4 py-6 text-center text-sm text-rose-600">{errorMessage(latest.error)}</p>
        ) : latest.data.items.length === 0 ? (
          <p className="px-4 py-8 text-center text-sm text-slate-500">You&apos;re all caught up.</p>
        ) : (
          <ul className="divide-y divide-stone-100">
            {latest.data.items.map((notification) => (
              <li key={notification.id}>
                <button type="button" onClick={() => open(notification)}
                  className={cn('flex w-full gap-3 px-4 py-3 text-left hover:bg-stone-50', !notification.read && 'bg-brand-50/40')}>
                  <span className={cn('mt-1.5 h-2 w-2 shrink-0 rounded-full', notification.read ? 'bg-transparent' : 'bg-brand-500')} aria-hidden />
                  <span className="min-w-0">
                    <span className="block text-sm font-semibold text-slate-900">{notification.title}</span>
                    <span className="mt-0.5 block text-sm text-slate-600">{notification.message}</span>
                    <span className="mt-1 block text-xs text-slate-400">{formatRelative(notification.createdAt)}</span>
                  </span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
      <div className="border-t border-stone-100 p-2">
        <Link to="/notifications" onClick={close} className="block rounded-lg px-3 py-2 text-center text-sm font-semibold text-slate-700 hover:bg-stone-50">
          See all notifications
        </Link>
      </div>
    </div>
  );
}
