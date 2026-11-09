import { useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { toast } from 'sonner';
import { errorMessage } from '@/lib/errors';
import { session } from '@/lib/session';
import { authApi } from './api';

/**
 * Revokes the refresh token on the server, then forgets the local session. If the server cannot
 * be reached the user stays signed in (otherwise the still-valid cookie would silently sign them
 * back in on the next visit).
 */
export function useSignOut() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  return async () => {
    try {
      await authApi.logout();
    } catch (error) {
      toast.error(`Could not sign out: ${errorMessage(error)}`);
      return;
    }
    session.clear('logout');
    session.announce('logout');
    queryClient.removeQueries({ queryKey: ['me'] });
    queryClient.removeQueries({ queryKey: ['admin'] });
    navigate('/', { replace: true });
    toast.success('You have been signed out');
  };
}
