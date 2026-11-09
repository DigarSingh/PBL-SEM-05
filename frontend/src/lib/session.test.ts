import axios from 'axios';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { AuthResponse } from './types';

function auth(minutes: number): AuthResponse {
  return {
    accessToken: `token-${minutes}`,
    tokenType: 'Bearer',
    expiresAt: new Date(Date.now() + minutes * 60_000).toISOString(),
    user: { id: 7, email: 'asha@example.com', fullName: 'Asha Rao', phone: null, avatarUrl: null, roles: ['CUSTOMER'], emailVerified: true, createdAt: '2026-01-01T00:00:00Z' },
  };
}

async function freshSession() {
  vi.resetModules();
  return (await import('./session')).session;
}

describe('session', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it('shares one refresh request between concurrent callers', async () => {
    const session = await freshSession();
    const post = vi.spyOn(axios, 'post').mockResolvedValue({ data: { success: true, data: auth(15) } });
    const results = await Promise.all([session.refresh(), session.refresh(), session.freshToken()]);
    expect(post).toHaveBeenCalledTimes(1);
    expect(results.slice(0, 2)).toEqual([true, true]);
    expect(session.getState().status).toBe('authenticated');
  });

  it('skips the refresh call for visitors who never signed in', async () => {
    const session = await freshSession();
    const post = vi.spyOn(axios, 'post');
    await expect(session.restore()).resolves.toBe(false);
    expect(post).not.toHaveBeenCalled();
    expect(session.getState().status).toBe('anonymous');
  });

  it('keeps the session through a temporary server error', async () => {
    const session = await freshSession();
    session.setAuth(auth(15));
    vi.spyOn(axios, 'post').mockRejectedValue(Object.assign(new Error('unavailable'), { isAxiosError: true, response: { status: 503 } }));
    vi.spyOn(axios, 'isAxiosError').mockReturnValue(true);
    await expect(session.refresh()).resolves.toBe(false);
    expect(session.getState().status).toBe('authenticated');
  });

  it('signs out and announces expiry when the refresh token is rejected', async () => {
    const session = await freshSession();
    session.setAuth(auth(15));
    vi.spyOn(axios, 'isAxiosError').mockReturnValue(true);
    vi.spyOn(axios, 'post').mockRejectedValue(Object.assign(new Error('expired'), { isAxiosError: true, response: { status: 401 } }));
    const expired = vi.fn();
    window.addEventListener('shopsmart:session-expired', expired);
    await expect(session.refresh()).resolves.toBe(false);
    window.removeEventListener('shopsmart:session-expired', expired);
    expect(session.getState().status).toBe('anonymous');
    expect(expired).toHaveBeenCalledTimes(1);
  });
});
