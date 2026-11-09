import { useSyncExternalStore } from 'react';
import { session, type SessionState } from '@/lib/session';
import type { UserProfile } from '@/lib/types';

export function useSession(): SessionState {
  return useSyncExternalStore(session.subscribe, session.getState);
}

export function isAdmin(user: UserProfile | null | undefined): boolean {
  return Boolean(user?.roles.includes('ADMIN'));
}
