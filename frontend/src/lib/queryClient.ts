import { QueryClient } from '@tanstack/react-query';
import { ApiError } from './errors';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      gcTime: 5 * 60_000,
      refetchOnWindowFocus: true,
      retry: (failureCount, error) => {
        // Retry transient failures only; client errors (4xx) will not fix themselves.
        const retriable = !(error instanceof ApiError) || error.status === 0 || error.status >= 500;
        return retriable && failureCount < 2;
      },
    },
    mutations: {
      retry: false,
    },
  },
});
