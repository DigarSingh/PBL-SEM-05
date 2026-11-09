import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Bell, CheckCheck } from 'lucide-react';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { Button } from '@/components/ui/Button';
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/Feedback';
import { Pagination } from '@/components/ui/Pagination';
import { cn } from '@/lib/cn';
import { formatDateTime } from '@/lib/format';
import { useDocumentTitle } from '@/lib/hooks';
import type { Notification } from '@/lib/types';
import { notificationsApi } from './api';

export default function NotificationsPage() {
  useDocumentTitle('Notifications');
  const [page, setPage] = useState(0);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const list = useQuery({
    queryKey: ['me', 'notifications', 'page', page],
    queryFn: () => notificationsApi.list(page, 20),
    placeholderData: keepPreviousData,
  });
  const markAll = useMutation({
    mutationFn: notificationsApi.markAllRead,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['me', 'notifications'] }),
  });
  const open = (notification: Notification) => {
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
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">Notifications</h1>
          <p className="mt-1 text-sm text-slate-500">Order, payment and account updates.</p>
        </div>
        <Button variant="secondary" size="sm" icon={<CheckCheck className="h-4 w-4" aria-hidden />} loading={markAll.isPending} onClick={() => markAll.mutate()}>
          Mark all as read
        </Button>
      </div>
      {list.isPending ? (
        <div className="space-y-2">{[0, 1, 2, 3].map((key) => <Skeleton key={key} className="h-20 w-full" />)}</div>
      ) : list.isError ? (
        <ErrorState error={list.error} onRetry={() => void list.refetch()} />
      ) : list.data.items.length === 0 ? (
        <EmptyState icon={<Bell className="h-7 w-7" aria-hidden />} title="No notifications yet" description="Updates about your orders will appear here." />
      ) : (
        <ul className="divide-y divide-stone-100 overflow-hidden rounded-2xl border border-stone-200 bg-white">
          {list.data.items.map((notification) => (
            <li key={notification.id}>
              <button type="button" onClick={() => open(notification)}
                className={cn('flex w-full gap-3 px-5 py-4 text-left hover:bg-stone-50', !notification.read && 'bg-brand-50/40')}>
                <span className={cn('mt-1.5 h-2 w-2 shrink-0 rounded-full', notification.read ? 'bg-stone-200' : 'bg-brand-500')} aria-hidden />
                <span className="min-w-0 flex-1">
                  <span className="block text-sm font-semibold text-slate-900">{notification.title}</span>
                  <span className="mt-0.5 block text-sm text-slate-600">{notification.message}</span>
                </span>
                <span className="shrink-0 text-xs text-slate-400">{formatDateTime(notification.createdAt)}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
      {list.data && <Pagination className="mt-6" page={list.data.page} totalPages={list.data.totalPages} onChange={setPage} />}
    </div>
  );
}
