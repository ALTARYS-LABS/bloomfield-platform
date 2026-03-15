import { useState, useEffect, useCallback } from 'react';
import { useWebSocket } from './useWebSocket';
import type { Quote, OrderBookEntry, MarketIndex, CandleData } from '../types/market';

export function useMarketData() {
  const { connected, subscribe } = useWebSocket();
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
    const res = await fetch(`/api/brvm/history/${ticker}`);
    return res.json();
  }, []);

  return { connected, quotes, orderBooks, indices, fetchHistory };
}
