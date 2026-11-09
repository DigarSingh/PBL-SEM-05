import { Client, ReconnectionTimeMode, type IMessage, type StompSubscription } from '@stomp/stompjs';
import { session } from './session';

export type RealtimeStatus = 'idle' | 'connecting' | 'connected' | 'reconnecting';

type Handler = (payload: unknown) => void;

interface Registration {
  destination: string;
  handler: Handler;
  subscription?: StompSubscription;
}

/**
 * Single STOMP connection for the tab. Subscriptions are registered once and re-established
 * automatically after reconnects (exponential back-off). While disconnected, screens fall back to
 * polling via TanStack Query (see useRealtimeStatus).
 */
class Realtime {
  private client: Client | null = null;
  private status: RealtimeStatus = 'idle';
  private readonly registrations = new Set<Registration>();
  private readonly listeners = new Set<(status: RealtimeStatus) => void>();

  getStatus = (): RealtimeStatus => this.status;

  onStatus = (listener: (status: RealtimeStatus) => void): (() => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };

  connect(): void {
    if (this.client?.active) {
      return;
    }
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
    const client = new Client({
      brokerURL: `${protocol}://${window.location.host}/ws`,
      reconnectDelay: 2_000,
      maxReconnectDelay: 30_000,
      reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      beforeConnect: async () => {
        const token = await session.freshToken();
        if (!token) {
          await client.deactivate();
          return;
        }
        client.connectHeaders = { Authorization: `Bearer ${token}` };
        this.setStatus(this.status === 'idle' ? 'connecting' : 'reconnecting');
      },
      onConnect: () => {
        this.setStatus('connected');
        for (const registration of this.registrations) {
          this.attach(registration);
        }
      },
      onWebSocketClose: () => {
        if (this.client) {
          this.setStatus('reconnecting');
        }
      },
      onStompError: () => this.setStatus('reconnecting'),
    });
    this.client = client;
    this.setStatus('connecting');
    client.activate();
  }

  async disconnect(): Promise<void> {
    const client = this.client;
    this.client = null;
    this.setStatus('idle');
    for (const registration of this.registrations) {
      registration.subscription = undefined;
    }
    if (client) {
      await client.deactivate();
    }
  }

  subscribe(destination: string, handler: Handler): () => void {
    const registration: Registration = { destination, handler };
    this.registrations.add(registration);
    if (this.client?.connected) {
      this.attach(registration);
    }
    return () => {
      this.registrations.delete(registration);
      try {
        registration.subscription?.unsubscribe();
      } catch {
        // Connection already closed; nothing to unsubscribe.
      }
    };
  }

  private attach(registration: Registration): void {
    if (!this.client?.connected) {
      return;
    }
    registration.subscription = this.client.subscribe(registration.destination, (message: IMessage) => {
      try {
        registration.handler(JSON.parse(message.body));
      } catch {
        registration.handler(message.body);
      }
    });
  }

  private setStatus(status: RealtimeStatus): void {
    if (this.status !== status) {
      this.status = status;
      for (const listener of this.listeners) {
        listener(status);
      }
    }
  }
}

export const realtime = new Realtime();
