import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { toast } from 'sonner';
import { useSession } from '@/features/auth/useSession';

/** Keeps cached data consistent with the signed-in user and tells the user when a session ends. */
export function SessionEvents() {
  const queryClient = useQueryClient();
  const { status, user } = useSession();
  const userId = status === 'loading' ? undefined : (user?.id ?? null);
  const previousUserId = useRef<number | null | undefined>(undefined);

  useEffect(() => {
    if (userId === undefined) {
      return;
    }
    if (previousUserId.current !== undefined && previousUserId.current !== userId) {
      // Someone else (or nobody) is signed in now: drop everything that belonged to the previous user.
      queryClient.removeQueries({ queryKey: ['me'] });
      queryClient.removeQueries({ queryKey: ['admin'] });
      void queryClient.invalidateQueries({ queryKey: ['recommendations'] });
    }
    previousUserId.current = userId;
  }, [userId, queryClient]);

  useEffect(() => {
    const onExpired = () => toast.info('Your session has ended. Please sign in again.');
    window.addEventListener('shopsmart:session-expired', onExpired);
    return () => window.removeEventListener('shopsmart:session-expired', onExpired);
  }, []);

  return null;
}
