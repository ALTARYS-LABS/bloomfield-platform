import { useState, useEffect, useCallback } from 'react';
import { useWebSocket } from './useWebSocket';
import { useAuth } from '../auth/useAuth';
import { apiJson } from '../api/client';
import type { Quote, OrderBookEntry, MarketIndex, CandleData } from '../types/market';

export function useMarketData() {
  // On propage l'access token au STOMP CONNECT pour préparer les canaux /user/*
  // (portefeuille, alertes) introduits par STORY-006 et STORY-007.
  const { accessToken } = useAuth();
  const { connected, subscribe } = useWebSocket(accessToken);
  const [quotes, setQuotes] = useState<Map<string, Quote>>(new Map());
  const [orderBooks, setOrderBooks] = useState<Map<string, OrderBookEntry>>(new Map());
  const [indices, setIndices] = useState<MarketIndex[]>([]);

  useEffect(() => {
    subscribe('/topic/brvm/quotes', (data) => {
      const incoming = data as Quote[];
      setQuotes(prev => {
        const next = new Map(prev);
        incoming.forEach(q => next.set(q.ticker, q));
        return next;
      });
    });

    subscribe('/topic/brvm/orderbook', (data) => {
      const incoming = data as OrderBookEntry[];
      setOrderBooks(prev => {
        const next = new Map(prev);
        incoming.forEach(ob => next.set(ob.ticker, ob));
        return next;
      });
    });

    subscribe('/topic/brvm/indices', (data) => {
      setIndices(data as MarketIndex[]);
    });
  }, [subscribe]);

  const fetchHistory = useCallback(async (ticker: string): Promise<CandleData[]> => {
    // `apiJson` attache le header Authorization: Bearer <jwt> et gere le refresh.
    // Le bare `fetch` precedent partait sans token: sur les environnements ou
    // `/api/**` exige l'authentification, le backend repondait 401 et le
    // graphique retombait sur son fallback "une seule bougie" (le dernier quote
    // STOMP), donnant l'illusion que les 30 jours d'historique n'existaient pas.
    try {
      return await apiJson<CandleData[]>(`/api/brvm/history/${ticker}`);
    } catch {
      return [];
    }
  }, []);

  return { connected, quotes, orderBooks, indices, fetchHistory };
}
