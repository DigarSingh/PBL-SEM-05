import axios from 'axios';
import type { ApiEnvelope, AuthResponse, UserProfile } from './types';

export type SessionStatus = 'loading' | 'authenticated' | 'anonymous';

export interface SessionState {
  status: SessionStatus;
  token: string | null;
  expiresAt: number | null;
  user: UserProfile | null;
}

type Listener = (state: SessionState) => void;

const REFRESH_EARLY_MS = 60_000;
const RETRY_DELAY_MS = 15_000;
const CHANNEL_NAME = 'shopsmart-session';
// Non-secret marker that this browser had a session, so anonymous visitors skip the refresh call.
const HINT_KEY = 'shopsmart.session';

function hasSessionHint(): boolean {
  try {
    return window.localStorage.getItem(HINT_KEY) === '1';
  } catch {
    return false; // storage blocked (e.g. privacy mode): behave like a first visit
  }
}

function setSessionHint(present: boolean): void {
  try {
    if (present) {
      window.localStorage.setItem(HINT_KEY, '1');
    } else {
      window.localStorage.removeItem(HINT_KEY);
    }
  } catch {
    // Storage blocked: the session still works in this tab; it just won't be restored on reload.
  }
}

/**
 * Holds the short-lived access token in memory only (never in localStorage, so injected scripts
 * cannot read it from storage). The long-lived refresh token is an HttpOnly cookie managed by
 * the browser; refresh() exchanges it for a new access token. Sign-in and sign-out are broadcast
 * to other tabs so they follow along.
 */
class Session {
  private state: SessionState = { status: 'loading', token: null, expiresAt: null, user: null };
  private readonly listeners = new Set<Listener>();
  private refreshing: Promise<boolean> | null = null;
  private timer: ReturnType<typeof setTimeout> | undefined;
  private readonly channel: BroadcastChannel | null;

  constructor() {
    this.channel = typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel(CHANNEL_NAME);
    this.channel?.addEventListener('message', (event: MessageEvent) => {
      if (event.data === 'logout' && this.state.status === 'authenticated') {
        this.clear();
      } else if (event.data === 'login' && this.state.status !== 'authenticated') {
        void this.refresh();
      }
    });
  }

  getState = (): SessionState => this.state;

  getToken(): string | null {
    return this.state.token;
  }

  subscribe = (listener: Listener): (() => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };

  setAuth(auth: AuthResponse): void {
    setSessionHint(true);
    this.state = { status: 'authenticated', token: auth.accessToken, expiresAt: Date.parse(auth.expiresAt), user: auth.user };
    this.schedule(Math.max(5_000, Date.parse(auth.expiresAt) - Date.now() - REFRESH_EARLY_MS));
    this.emit();
  }

  updateUser(user: UserProfile): void {
    if (this.state.status === 'authenticated') {
      this.state = { ...this.state, user };
      this.emit();
    }
  }

  clear(reason?: 'expired' | 'logout'): void {
    clearTimeout(this.timer);
    const wasAuthenticated = this.state.status === 'authenticated';
    setSessionHint(false);
    this.state = { status: 'anonymous', token: null, expiresAt: null, user: null };
    this.emit();
    if (reason === 'expired' && wasAuthenticated) {
      window.dispatchEvent(new CustomEvent('shopsmart:session-expired'));
    }
  }

  /** Tells other open tabs about a sign-in or sign-out in this tab. */
  announce(event: 'login' | 'logout'): void {
    this.channel?.postMessage(event);
  }

  /** Restores the session on page load; skipped for visitors who have never signed in here. */
  restore(): Promise<boolean> {
    if (!hasSessionHint()) {
      this.clear();
      return Promise.resolve(false);
    }
    return this.refresh();
  }

  /** Exchanges the refresh cookie for a new access token. Concurrent callers share one request. */
  refresh(): Promise<boolean> {
    if (!this.refreshing) {
      this.refreshing = this.doRefresh().finally(() => {
        this.refreshing = null;
      });
    }
    return this.refreshing;
  }

  /** Ensures a token that stays valid for at least the next minute (used before WebSocket connects). */
  async freshToken(): Promise<string | null> {
    if (this.state.token && this.state.expiresAt && this.state.expiresAt - Date.now() > REFRESH_EARLY_MS) {
      return this.state.token;
    }
    return (await this.refresh()) ? this.state.token : null;
  }

  private async doRefresh(attempt = 0): Promise<boolean> {
    try {
      const response = await axios.post<ApiEnvelope<AuthResponse>>('/api/auth/refresh', {}, {
        withCredentials: true,
        headers: { 'X-Requested-With': 'XMLHttpRequest' },
        timeout: 15_000,
      });
      this.setAuth(response.data.data);
      return true;
    } catch (error) {
      const status = axios.isAxiosError(error) ? error.response?.status : undefined;
      const authenticated = this.state.status === 'authenticated';
      // Another tab may have just rotated the token; the browser now holds the new cookie.
      if (status === 401 && attempt === 0 && authenticated) {
        await new Promise((resolve) => setTimeout(resolve, 400));
        return this.doRefresh(1);
      }
      // Network problems, rate limiting and server errors are temporary: keep the session and retry soon.
      if (authenticated && (status === undefined || status === 429 || status >= 500)) {
        this.schedule(RETRY_DELAY_MS);
        return false;
      }
      this.clear(authenticated ? 'expired' : undefined);
      return false;
    }
  }

  private schedule(delay: number): void {
    clearTimeout(this.timer);
    this.timer = setTimeout(() => {
      void this.refresh();
    }, delay);
  }

  private emit(): void {
    for (const listener of this.listeners) {
      listener(this.state);
    }
  }
}

export const session = new Session();
