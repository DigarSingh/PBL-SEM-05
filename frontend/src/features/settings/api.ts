import { useQuery } from '@tanstack/react-query';
import { apiGet } from '@/lib/api';
import type { PublicSettings } from '@/lib/types';

/** Store-wide public settings (payment options, shipping and tax rules, Razorpay key id). */
export function usePublicSettings() {
  return useQuery({
    queryKey: ['settings', 'public'],
    queryFn: () => apiGet<PublicSettings>('/settings/public'),
    staleTime: 5 * 60_000,
  });
}
