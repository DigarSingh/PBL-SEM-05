import { apiGet, apiPost } from '@/lib/api';
import type { Notification, Page } from '@/lib/types';

export const notificationsApi = {
  list: (page = 0, size = 20) => apiGet<Page<Notification>>('/notifications', { page, size }),
  unreadCount: async () => (await apiGet<{ count: number }>('/notifications/unread-count')).count,
  markRead: (id: number) => apiPost<Notification>(`/notifications/${id}/read`),
  markAllRead: () => apiPost<{ updated: number }>('/notifications/read-all'),
};
