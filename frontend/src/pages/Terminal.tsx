import { useState, useEffect, useCallback, useRef } from 'react';
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

function useIsMobile() {
  const [mobile, setMobile] = useState(window.innerWidth < 768);
  useEffect(() => {
    const handler = () => setMobile(window.innerWidth < 768);
    window.addEventListener('resize', handler);
    return () => window.removeEventListener('resize', handler);
  }, []);
  return mobile;
}

export default function Terminal() {
  const { connected, quotes, orderBooks, indices, fetchHistory } = useMarketData();
  const [selectedTicker, setSelectedTicker] = useState('SGBCI');
  const [history, setHistory] = useState<CandleData[]>([]);
  const [clock, setClock] = useState('');
  const isMobile = useIsMobile();

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

  const widgetClass = "bg-bg-widget rounded-lg border border-border overflow-hidden";

  // Measure container width for react-grid-layout
  const gridContainerRef = useRef<HTMLDivElement>(null);
  const [containerWidth, setContainerWidth] = useState(window.innerWidth);

  useEffect(() => {
    const el = gridContainerRef.current;
    if (!el) return;
    const ro = new ResizeObserver(entries => {
      const width = entries[0].contentRect.width;
      if (width > 0) setContainerWidth(width);
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, [isMobile]);

  return (
    <div className="h-screen bg-bg-primary flex flex-col overflow-hidden">
      {/* Top Bar */}
      <header className="flex items-center justify-between px-3 md:px-4 py-2 bg-bg-header border-b border-border shrink-0">
        <div className="flex items-center gap-2 md:gap-3">
          <img src="/bloomfield-logo.png" alt="Bloomfield" className="h-5 md:h-7" />
          <span className="text-xs md:text-sm font-semibold text-text-primary hidden sm:inline">Bloomfield Terminal</span>
        </div>
        <div className="flex items-center gap-3 md:gap-4">
          <div className="flex items-center gap-1.5 text-[10px] md:text-xs">
            <span className={`w-2 h-2 rounded-full ${connected ? 'bg-gain pulse-dot' : 'bg-loss'}`} />
            <span className={`font-mono ${connected ? 'text-gain' : 'text-loss'}`}>
              {connected ? 'Live' : 'Off'}
            </span>
          </div>
          <div className="text-[10px] md:text-xs font-mono text-text-secondary">
            {clock}
          </div>
        </div>
      </header>

      {/* Ticker Banner */}
      <div className="shrink-0">
        <TickerBanner quotes={quotes} />
      </div>

      {/* Widgets */}
      {isMobile ? (
        <div className="flex-1 p-2 flex flex-col gap-2 overflow-auto">
          {/* Mobile: stacked layout */}
          <div className={`${widgetClass} min-h-[160px]`}>
            <IndicesWidget indices={indices} />
          </div>
          <div className={`${widgetClass} h-[350px]`}>
            <MarketTable quotes={quotes} selectedTicker={selectedTicker} onSelectTicker={handleSelectTicker} />
          </div>
          <div className={`${widgetClass}`}>
            <EmitterDetail ticker={selectedTicker} quote={quotes.get(selectedTicker)} />
          </div>
          <div className={`${widgetClass} h-[300px]`}>
            <CandlestickChart ticker={selectedTicker} history={history} latestQuote={quotes.get(selectedTicker)} />
          </div>
          <div className={`${widgetClass} min-h-[200px]`}>
            <OrderBook orderBook={orderBooks.get(selectedTicker)} ticker={selectedTicker} />
          </div>
        </div>
      ) : (
        <div ref={gridContainerRef} className="flex-1 p-2 overflow-auto">
          <ResponsiveGridLayout
            className="layout"
            layouts={layouts}
            breakpoints={{ lg: 1200, md: 768 }}
            cols={{ lg: 12, md: 12 }}
            rowHeight={60}
            dragConfig={{ handle: '.drag-handle' }}
            width={containerWidth}
          >
            <div key="table" className={widgetClass}>
              <MarketTable quotes={quotes} selectedTicker={selectedTicker} onSelectTicker={handleSelectTicker} />
            </div>
            <div key="chart" className={widgetClass}>
              <CandlestickChart ticker={selectedTicker} history={history} latestQuote={quotes.get(selectedTicker)} />
            </div>
            <div key="orderbook" className={widgetClass}>
              <OrderBook orderBook={orderBooks.get(selectedTicker)} ticker={selectedTicker} />
            </div>
            <div key="indices" className={widgetClass}>
              <IndicesWidget indices={indices} />
            </div>
            <div key="detail" className={widgetClass}>
              <EmitterDetail ticker={selectedTicker} quote={quotes.get(selectedTicker)} />
            </div>
          </ResponsiveGridLayout>
        </div>
      )}
    </div>
  );
}
