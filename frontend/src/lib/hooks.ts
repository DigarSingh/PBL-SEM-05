import { useEffect, useState, useSyncExternalStore } from 'react';
import { realtime, type RealtimeStatus } from './realtime';

export function useDebouncedValue<T>(value: T, delay = 250): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);
  return debounced;
}

export function useDocumentTitle(title: string | undefined): void {
  useEffect(() => {
    if (title) {
      document.title = `${title} | ShopSmart`;
    }
  }, [title]);
}

export function useRealtimeStatus(): RealtimeStatus {
  return useSyncExternalStore(realtime.onStatus, realtime.getStatus);
}

export function useOnlineStatus(): boolean {
  return useSyncExternalStore(
    (listener) => {
      window.addEventListener('online', listener);
      window.addEventListener('offline', listener);
      return () => {
        window.removeEventListener('online', listener);
        window.removeEventListener('offline', listener);
      };
    },
    () => navigator.onLine,
  );
}
