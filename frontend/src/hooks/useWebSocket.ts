import { useEffect, useRef, useState, useCallback } from 'react';
import { Client, type IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

type MessageHandler = (body: unknown) => void;

// Si un access token est fourni, on le passe au backend dans le header
// Authorization du CONNECT frame. Le StompAuthChannelInterceptor côté Spring
// le valide et attache un Principal à la session, ce qui débloque /user/queue/*.
// Sans token, la connexion reste anonyme et seuls les /topic/* publics fonctionnent.
export function useWebSocket(accessToken?: string | null) {
  const [connected, setConnected] = useState(false);
  const clientRef = useRef<Client | null>(null);
  const handlersRef = useRef<Map<string, MessageHandler>>(new Map());

  useEffect(() => {
    const connectHeaders: Record<string, string> = accessToken
      ? { Authorization: `Bearer ${accessToken}` }
      : {};

    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      connectHeaders,
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
  }, [accessToken]);

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
