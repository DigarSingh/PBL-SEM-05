import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router';

/**
 * Table filters kept in the URL (so Back from a detail page restores them). `defaults` must be a
 * module-level constant. Any change other than `page` returns to the first page.
 */
export function useParamsState<T extends Record<string, string>>(defaults: T) {
  const [params, setParams] = useSearchParams();
  const values = useMemo(() => {
    const result: Record<string, string> = { ...defaults };
    for (const key of Object.keys(defaults)) {
      const value = params.get(key);
      if (value !== null) {
        result[key] = value;
      }
    }
    return result as T;
  }, [params, defaults]);

  const update = useCallback((patch: Partial<T>) => {
    setParams((current) => {
      const next = new URLSearchParams(current);
      for (const [key, value] of Object.entries(patch)) {
        if (value === undefined || value === '' || value === defaults[key]) {
          next.delete(key);
        } else {
          next.set(key, String(value));
        }
      }
      if (!('page' in patch)) {
        next.delete('page');
      }
      return next;
    }, { preventScrollReset: true });
  }, [setParams, defaults]);

  return [values, update] as const;
}

export function pageFrom(value: string | undefined): number {
  const page = Number(value);
  return Number.isInteger(page) && page > 0 ? page - 1 : 0;
}
