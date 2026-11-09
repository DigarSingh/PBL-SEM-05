import { useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { useNavigate } from 'react-router';
import { toast } from 'sonner';
import { isAdmin, useSession } from '@/features/auth/useSession';
import { realtime } from '@/lib/realtime';
import type { NotificationMessage, OrderUpdateMessage } from '@/lib/realtimeMessages';

/**
 * Connects the STOMP client while signed in and turns pushed events into cache invalidations,
 * so every screen showing orders, notifications or stock refreshes itself. Notifications also
 * appear as toasts.
 */
export function RealtimeBridge() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { status, user } = useSession();
  const userId = user?.id;
  const admin = isAdmin(user);

  useEffect(() => {
    if (status === 'authenticated') {
      realtime.connect();
    } else if (status === 'anonymous') {
      void realtime.disconnect();
    }
  }, [status]);

  useEffect(() => {
    if (!userId) {
      return undefined;
    }
    const unsubscribers = [
      realtime.subscribe('/user/queue/notifications', (payload) => {
        const notification = payload as NotificationMessage;
        void queryClient.invalidateQueries({ queryKey: ['me', 'notifications'] });
        const link = notification.link;
        toast(notification.title, {
          description: notification.message,
          action: link ? { label: 'View', onClick: () => navigate(link) } : undefined,
        });
      }),
      realtime.subscribe('/user/queue/orders', (payload) => {
        const update = payload as OrderUpdateMessage;
        void queryClient.invalidateQueries({ queryKey: ['me', 'orders'] });
        if (update.type === 'ORDER_STATUS_CHANGED' && update.status === 'DELIVERED') {
          void queryClient.invalidateQueries({ queryKey: ['me', 'reviews'] });
        }
      }),
    ];
    if (admin) {
      unsubscribers.push(
        realtime.subscribe('/topic/admin/orders', () => {
          void queryClient.invalidateQueries({ queryKey: ['admin', 'orders'] });
          void queryClient.invalidateQueries({ queryKey: ['admin', 'dashboard'] });
          void queryClient.invalidateQueries({ queryKey: ['admin', 'payments'] });
          void queryClient.invalidateQueries({ queryKey: ['admin', 'inventory'] });
        }),
        realtime.subscribe('/topic/admin/inventory', () => {
          void queryClient.invalidateQueries({ queryKey: ['admin', 'inventory'] });
          void queryClient.invalidateQueries({ queryKey: ['admin', 'dashboard'] });
          void queryClient.invalidateQueries({ queryKey: ['admin', 'products'] });
        }),
      );
    }
    return () => unsubscribers.forEach((unsubscribe) => unsubscribe());
  }, [userId, admin, queryClient, navigate]);

  return null;
}
