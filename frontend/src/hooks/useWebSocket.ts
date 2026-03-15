import { useEffect, useRef, useState, useCallback } from 'react';
import { Client, type IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

type MessageHandler = (body: unknown) => void;

export function useWebSocket() {
  const [connected, setConnected] = useState(false);
  const clientRef = useRef<Client | null>(null);
  const handlersRef = useRef<Map<string, MessageHandler>>(new Map());

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 3000,
      onConnect: () => setConnected(true),
      onDisconnect: () => setConnected(false),
      onStompError: () => setConnected(false),
    });

    client.onConnect = () => {
      setConnected(true);
      handlersRef.current.forEach((handler, dest) => {
        client.subscribe(dest, (msg: IMessage) => {
          handler(JSON.parse(msg.body));
        });
      });
    };

    client.onDisconnect = () => setConnected(false);
    client.activate();
    clientRef.current = client;

    return () => {
      client.deactivate();
    };
  }, []);

  const subscribe = useCallback((destination: string, handler: MessageHandler) => {
    handlersRef.current.set(destination, handler);
    const client = clientRef.current;
    if (client?.connected) {
      client.subscribe(destination, (msg: IMessage) => {
        handler(JSON.parse(msg.body));
      });
    }
  }, []);

  return { connected, subscribe };
}
