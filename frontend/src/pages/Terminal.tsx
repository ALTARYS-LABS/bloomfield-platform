import { useState, useEffect, useCallback } from 'react';
import { ResponsiveGridLayout } from 'react-grid-layout';
import 'react-grid-layout/css/styles.css';
import 'react-resizable/css/styles.css';
import { useMarketData } from '../hooks/useMarketData';
import TickerBanner from '../components/TickerBanner';
import MarketTable from '../components/MarketTable';
import CandlestickChart from '../components/CandlestickChart';
import OrderBook from '../components/OrderBook';
import IndicesWidget from '../components/IndicesWidget';
import EmitterDetail from '../components/EmitterDetail';
import type { CandleData } from '../types/market';

const layouts = {
  lg: [
    { i: 'table', x: 0, y: 0, w: 7, h: 4 },
    { i: 'indices', x: 7, y: 0, w: 2, h: 4 },
    { i: 'detail', x: 9, y: 0, w: 3, h: 4 },
    { i: 'chart', x: 0, y: 4, w: 7, h: 5 },
    { i: 'orderbook', x: 7, y: 4, w: 5, h: 5 },
  ],
  md: [
    { i: 'table', x: 0, y: 0, w: 6, h: 4 },
    { i: 'indices', x: 6, y: 0, w: 3, h: 4 },
    { i: 'detail', x: 9, y: 0, w: 3, h: 4 },
    { i: 'chart', x: 0, y: 4, w: 6, h: 5 },
    { i: 'orderbook', x: 6, y: 4, w: 6, h: 5 },
  ],
};

export default function Terminal() {
  const { connected, quotes, orderBooks, indices, fetchHistory } = useMarketData();
  const [selectedTicker, setSelectedTicker] = useState('SGBCI');
  const [history, setHistory] = useState<CandleData[]>([]);
  const [clock, setClock] = useState('');

  useEffect(() => {
    const interval = setInterval(() => {
      setClock(new Date().toLocaleTimeString('fr-FR', { timeZone: 'Africa/Abidjan', hour: '2-digit', minute: '2-digit', second: '2-digit' }));
    }, 1000);
    return () => clearInterval(interval);
  }, []);

  const handleSelectTicker = useCallback((ticker: string) => {
    setSelectedTicker(ticker);
    fetchHistory(ticker).then(setHistory);
  }, [fetchHistory]);

  useEffect(() => {
    fetchHistory(selectedTicker).then(setHistory);
  }, []);

  return (
    <div className="min-h-screen bg-bg-primary flex flex-col">
      {/* Top Bar */}
      <header className="flex items-center justify-between px-4 py-2 bg-bg-header border-b border-border">
        <div className="flex items-center gap-3">
          <img
            src="https://bloomfield-investment.com/wp-content/uploads/2020/02/Bl1.png"
            alt="Bloomfield"
            className="h-7"
            crossOrigin="anonymous"
          />
          <span className="text-sm font-semibold text-text-primary">Bloomfield Terminal</span>
        </div>
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2 text-xs">
            <span className={`w-2 h-2 rounded-full ${connected ? 'bg-gain pulse-dot' : 'bg-loss'}`} />
            <span className={`font-mono ${connected ? 'text-gain' : 'text-loss'}`}>
              {connected ? 'Live' : 'Déconnecté'}
            </span>
          </div>
          <div className="text-xs font-mono text-text-secondary">
            Abidjan {clock}
          </div>
        </div>
      </header>

      {/* Ticker Banner */}
      <TickerBanner quotes={quotes} />

      {/* Grid Layout */}
      <div className="flex-1 p-2">
        <ResponsiveGridLayout
          className="layout"
          layouts={layouts}
          breakpoints={{ lg: 1200, md: 768 }}
          cols={{ lg: 12, md: 12 }}
          rowHeight={60}
          dragConfig={{ handle: '.drag-handle' }}
          width={1200}
        >
          <div key="table" className="bg-bg-widget rounded-lg border border-border overflow-hidden">
            <MarketTable quotes={quotes} selectedTicker={selectedTicker} onSelectTicker={handleSelectTicker} />
          </div>
          <div key="chart" className="bg-bg-widget rounded-lg border border-border overflow-hidden">
            <CandlestickChart ticker={selectedTicker} history={history} latestQuote={quotes.get(selectedTicker)} />
          </div>
          <div key="orderbook" className="bg-bg-widget rounded-lg border border-border overflow-hidden">
            <OrderBook orderBook={orderBooks.get(selectedTicker)} ticker={selectedTicker} />
          </div>
          <div key="indices" className="bg-bg-widget rounded-lg border border-border overflow-hidden">
            <IndicesWidget indices={indices} />
          </div>
          <div key="detail" className="bg-bg-widget rounded-lg border border-border overflow-hidden">
            <EmitterDetail ticker={selectedTicker} quote={quotes.get(selectedTicker)} />
          </div>
        </ResponsiveGridLayout>
      </div>
    </div>
  );
}
