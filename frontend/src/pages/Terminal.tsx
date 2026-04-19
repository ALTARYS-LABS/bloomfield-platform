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
import PortfolioWidget from '../components/PortfolioWidget';
import AlertsWidget from '../components/AlertsWidget';
import AlertToasts from '../components/AlertToasts';
import { useAlerts } from '../hooks/useAlerts';
import { useFocusTrap } from '../hooks/useFocusTrap';
import type { CandleData } from '../types/market';

type MobileTab = 'marche' | 'analyse' | 'portefeuille' | 'alertes';

// Layouts react-grid-layout : le widget "portfolio" occupe la ligne du bas sur toute la largeur
// afin de laisser la place au formulaire d'ordre et à la liste des positions.
const layouts = {
  lg: [
    { i: 'table', x: 0, y: 0, w: 7, h: 4 },
    { i: 'indices', x: 7, y: 0, w: 2, h: 4 },
    { i: 'detail', x: 9, y: 0, w: 3, h: 4 },
    { i: 'chart', x: 0, y: 4, w: 7, h: 5 },
    { i: 'orderbook', x: 7, y: 4, w: 5, h: 5 },
    { i: 'portfolio', x: 0, y: 9, w: 12, h: 5 },
  ],
  md: [
    { i: 'table', x: 0, y: 0, w: 6, h: 4 },
    { i: 'indices', x: 6, y: 0, w: 3, h: 4 },
    { i: 'detail', x: 9, y: 0, w: 3, h: 4 },
    { i: 'chart', x: 0, y: 4, w: 6, h: 5 },
    { i: 'orderbook', x: 6, y: 4, w: 6, h: 5 },
    { i: 'portfolio', x: 0, y: 9, w: 12, h: 5 },
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
  // Default aligne sur le seed `data/brvm-tickers.yml` (code BRVM officiel SGBC,
  // pas SGBCI), sinon les widgets graphique / carnet / detail emetteur restent
  // vides au montage car le ticker n'existe pas cote backend.
  const [selectedTicker, setSelectedTicker] = useState('SGBC');
  const [history, setHistory] = useState<CandleData[]>([]);
  const [clock, setClock] = useState('');
  const isMobile = useIsMobile();
  const [activeTab, setActiveTab] = useState<MobileTab>('marche');
  // Panneau d'alertes (slide-over) en desktop. Sur mobile, l'onglet "alertes"
  // occupe la vue plein ecran et ce booleen est ignore.
  const [alertsOpen, setAlertsOpen] = useState(false);

  const {
    rules: alertRules,
    events: alertEvents,
    loading: alertsLoading,
    error: alertsError,
    unreadCount: alertsUnread,
    toasts: alertToasts,
    createRule,
    deleteRule,
    markRead,
    dismissToast,
  } = useAlerts();

  useEffect(() => {
    const interval = setInterval(() => {
      setClock(new Date().toLocaleTimeString('fr-FR', { timeZone: 'Africa/Abidjan', hour: '2-digit', minute: '2-digit', second: '2-digit' }));
    }, 1000);
    return () => clearInterval(interval);
  }, []);

  const handleSelectTicker = useCallback((ticker: string) => {
    setSelectedTicker(ticker);
  }, []);

  useEffect(() => {
    fetchHistory(selectedTicker).then(setHistory);
  }, [fetchHistory, selectedTicker]);

  const widgetClass = "bg-bg-widget rounded-lg border border-border overflow-hidden";

  // Liste des tickers disponibles pour le formulaire d'ordre : dérivée des cotations
  // déjà reçues, ainsi on n'expose que des valeurs pour lesquelles le backend saura
  // fournir un prix d'exécution.
  const availableTickers = Array.from(quotes.keys()).sort();

  // Refs d'accessibilite pour le slide-over : on piege le focus dans l'aside
  // et on restaure le focus sur la cloche a la fermeture.
  const bellRef = useRef<HTMLButtonElement>(null);
  const slideOverRef = useRef<HTMLElement>(null);
  const closeAlerts = useCallback(() => setAlertsOpen(false), []);
  useFocusTrap(alertsOpen && !isMobile, slideOverRef, closeAlerts, bellRef);

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
          {/* Bouton cloche d'alertes : visible uniquement en desktop, le mobile passe par l'onglet dedie. */}
          {!isMobile && (
            <button
              ref={bellRef}
              type="button"
              onClick={() => setAlertsOpen((v) => !v)}
              aria-label="Ouvrir les alertes"
              aria-expanded={alertsOpen}
              aria-haspopup="dialog"
              className="relative text-text-secondary hover:text-text-primary text-base leading-none"
            >
              <span aria-hidden>⚑</span>
              {alertsUnread > 0 && (
                <span className="absolute -top-1 -right-2 bg-loss text-bg-primary text-[9px] font-mono px-1 rounded-full">
                  {alertsUnread}
                </span>
              )}
            </button>
          )}
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
        <div className="flex-1 flex flex-col overflow-hidden">
          {/* Tab content */}
          <div className="flex-1 overflow-auto p-2 flex flex-col gap-2">
            {activeTab === 'marche' && (
              <>
                <div className={`${widgetClass} h-[280px]`}>
                  <IndicesWidget indices={indices} />
                </div>
                <div className={`${widgetClass} h-[380px]`}>
                  <MarketTable quotes={quotes} selectedTicker={selectedTicker} onSelectTicker={handleSelectTicker} />
                </div>
                <div className={`${widgetClass} h-[280px]`}>
                  <EmitterDetail ticker={selectedTicker} quote={quotes.get(selectedTicker)} />
                </div>
              </>
            )}
            {activeTab === 'analyse' && (
              <>
                <div className={`${widgetClass} h-[420px]`}>
                  <CandlestickChart ticker={selectedTicker} history={history} latestQuote={quotes.get(selectedTicker)} />
                </div>
                <div className={`${widgetClass} h-[320px]`}>
                  <OrderBook orderBook={orderBooks.get(selectedTicker)} ticker={selectedTicker} />
                </div>
              </>
            )}
            {activeTab === 'portefeuille' && (
              <div className={`${widgetClass} h-[620px]`}>
                <PortfolioWidget availableTickers={availableTickers} />
              </div>
            )}
            {activeTab === 'alertes' && (
              <div className={`${widgetClass} h-[620px]`}>
                <AlertsWidget
                  rules={alertRules}
                  events={alertEvents}
                  loading={alertsLoading}
                  error={alertsError}
                  availableTickers={availableTickers}
                  unreadCount={alertsUnread}
                  onCreate={(ticker, operator, threshold) =>
                    createRule({ ticker, operator, threshold })
                  }
                  onDelete={deleteRule}
                  onMarkRead={markRead}
                />
              </div>
            )}
          </div>

          {/* Bottom tab bar */}
          <nav className="shrink-0 flex border-t border-border bg-bg-header">
            <button
              onClick={() => setActiveTab('marche')}
              className={`flex-1 flex flex-col items-center justify-center py-2 gap-0.5 text-[10px] font-semibold uppercase tracking-wider transition-colors
                ${activeTab === 'marche' ? 'text-accent border-t-2 border-accent -mt-px' : 'text-text-secondary'}`}
            >
              <span className="text-base leading-none">📊</span>
              <span>Marché</span>
            </button>
            <button
              onClick={() => setActiveTab('analyse')}
              className={`flex-1 flex flex-col items-center justify-center py-2 gap-0.5 text-[10px] font-semibold uppercase tracking-wider transition-colors
                ${activeTab === 'analyse' ? 'text-accent border-t-2 border-accent -mt-px' : 'text-text-secondary'}`}
            >
              <span className="text-base leading-none">📈</span>
              <span>Analyse</span>
            </button>
            <button
              onClick={() => setActiveTab('portefeuille')}
              className={`flex-1 flex flex-col items-center justify-center py-2 gap-0.5 text-[10px] font-semibold uppercase tracking-wider transition-colors
                ${activeTab === 'portefeuille' ? 'text-accent border-t-2 border-accent -mt-px' : 'text-text-secondary'}`}
            >
              <span className="text-base leading-none">💼</span>
              <span>Portef.</span>
            </button>
            <button
              onClick={() => setActiveTab('alertes')}
              className={`flex-1 flex flex-col items-center justify-center py-2 gap-0.5 text-[10px] font-semibold uppercase tracking-wider transition-colors relative
                ${activeTab === 'alertes' ? 'text-accent border-t-2 border-accent -mt-px' : 'text-text-secondary'}`}
            >
              <span className="text-base leading-none">⚑</span>
              <span>Alertes</span>
              {alertsUnread > 0 && (
                <span className="absolute top-1 right-2 bg-loss text-bg-primary text-[9px] font-mono px-1 rounded-full">
                  {alertsUnread}
                </span>
              )}
            </button>
          </nav>
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
            <div key="portfolio" className={widgetClass}>
              <PortfolioWidget availableTickers={availableTickers} />
            </div>
          </ResponsiveGridLayout>
        </div>
      )}

      {/* Panneau alertes desktop : slide-over a droite. Evite de re-dimensionner
          la grille react-grid-layout qui est deja bien remplie. */}
      {!isMobile && alertsOpen && (
        <div className="fixed inset-0 z-40 flex">
          <button
            type="button"
            aria-label="Fermer le panneau alertes"
            className="flex-1 bg-black/40"
            onClick={() => setAlertsOpen(false)}
          />
          <aside
            ref={slideOverRef}
            role="dialog"
            aria-modal="true"
            aria-label="Panneau alertes"
            tabIndex={-1}
            className="w-full max-w-md bg-bg-widget border-l border-border shadow-2xl focus:outline-none"
          >
            <AlertsWidget
              rules={alertRules}
              events={alertEvents}
              loading={alertsLoading}
              error={alertsError}
              availableTickers={availableTickers}
              unreadCount={alertsUnread}
              onCreate={(ticker, operator, threshold) =>
                createRule({ ticker, operator, threshold })
              }
              onDelete={deleteRule}
              onMarkRead={markRead}
              onClose={() => setAlertsOpen(false)}
            />
          </aside>
        </div>
      )}

      {/* Pile de toasts (desktop et mobile) : les nouveaux evenements declenches
          tant que l'utilisateur ne regarde pas la liste sautent aux yeux. */}
      <AlertToasts toasts={alertToasts} onDismiss={dismissToast} />
    </div>
  );
}
