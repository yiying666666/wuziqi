import type { WsPacket } from "../types";

type EventHandler = (payload: unknown) => void;

const WS_BASE = import.meta.env.VITE_WS_BASE ?? `${window.location.protocol === "https:" ? "wss" : "ws"}://${window.location.host}/ws`;

export class SocketClient {
  private ws: WebSocket | null = null;
  private reconnectTimer: number | null = null;
  private reconnectAttempt = 0;
  private shouldReconnect = false;
  private token = "";

  private handlers = new Map<string, Set<EventHandler>>();
  private systemHandlers = {
    open: new Set<() => void>(),
    close: new Set<() => void>(),
    reconnecting: new Set<(attempt: number) => void>(),
  };

  connect(token: string): void {
    this.token = token;
    this.shouldReconnect = true;

    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) {
      return;
    }

    this.openSocket();
  }

  disconnect(): void {
    this.shouldReconnect = false;
    this.reconnectAttempt = 0;
    if (this.reconnectTimer) {
      window.clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.ws?.close();
    this.ws = null;
  }

  send(event: string, payload?: unknown): void {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      return;
    }
    this.ws.send(JSON.stringify({ event, payload }));
  }

  on(event: string, handler: EventHandler): () => void {
    const set = this.handlers.get(event) ?? new Set<EventHandler>();
    set.add(handler);
    this.handlers.set(event, set);

    return () => {
      set.delete(handler);
    };
  }

  onOpen(handler: () => void): () => void {
    this.systemHandlers.open.add(handler);
    return () => this.systemHandlers.open.delete(handler);
  }

  onClose(handler: () => void): () => void {
    this.systemHandlers.close.add(handler);
    return () => this.systemHandlers.close.delete(handler);
  }

  onReconnecting(handler: (attempt: number) => void): () => void {
    this.systemHandlers.reconnecting.add(handler);
    return () => this.systemHandlers.reconnecting.delete(handler);
  }

  isConnected(): boolean {
    return this.ws?.readyState === WebSocket.OPEN;
  }

  private openSocket(): void {
    if (!this.token) {
      return;
    }

    const url = `${WS_BASE}?token=${encodeURIComponent(this.token)}`;
    this.ws = new WebSocket(url);

    this.ws.onopen = () => {
      this.reconnectAttempt = 0;
      this.systemHandlers.open.forEach((handler) => handler());
    };

    this.ws.onclose = () => {
      this.systemHandlers.close.forEach((handler) => handler());
      if (this.shouldReconnect) {
        this.scheduleReconnect();
      }
    };

    this.ws.onerror = () => {
      this.ws?.close();
    };

    this.ws.onmessage = (message) => {
      const packet = JSON.parse(message.data) as WsPacket;
      const set = this.handlers.get(packet.event);
      set?.forEach((handler) => handler(packet.payload));
    };
  }

  private scheduleReconnect(): void {
    if (this.reconnectTimer) {
      return;
    }

    this.reconnectAttempt += 1;
    this.systemHandlers.reconnecting.forEach((handler) => handler(this.reconnectAttempt));

    const delay = Math.min(1000 * 2 ** Math.min(this.reconnectAttempt, 4), 8000);
    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectTimer = null;
      this.openSocket();
    }, delay);
  }
}
