import { catalogApi } from './api';

const WINDOW_MS = 30 * 60_000;
const recorded = new Map<string, number>();

/**
 * Sends a view event at most once per key per half hour in this tab. Analytics must never get in
 * the way of browsing, so a failed request is only forgotten (allowing a later retry), not shown.
 */
function once(key: string, send: () => Promise<unknown>): void {
  const now = Date.now();
  const last = recorded.get(key);
  if (last !== undefined && now - last < WINDOW_MS) {
    return;
  }
  recorded.set(key, now);
  send().catch(() => recorded.delete(key));
}

export function recordProductView(productId: number, userId: number | null): void {
  once(`product:${userId ?? 'guest'}:${productId}`, () => catalogApi.recordView(productId));
}

export function recordCategoryView(categoryId: number, userId: number): void {
  once(`category:${userId}:${categoryId}`, () => catalogApi.recordCategoryView(categoryId));
}
