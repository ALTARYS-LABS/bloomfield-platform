import { useState, useRef, useEffect } from 'react';
import type { Quote } from '../types/market';

interface Props {
  quotes: Map<string, Quote>;
  selectedTicker: string;
  onSelectTicker: (ticker: string) => void;
}

type SortKey = 'ticker' | 'price' | 'changePercent' | 'volume';

export default function MarketTable({ quotes, selectedTicker, onSelectTicker }: Props) {
  const [sortKey, setSortKey] = useState<SortKey>('ticker');
  const [sortAsc, setSortAsc] = useState(true);
  const prevPrices = useRef<Map<string, number>>(new Map());
  const [flashes, setFlashes] = useState<Map<string, 'up' | 'down'>>(new Map());

  const items = Array.from(quotes.values());

  useEffect(() => {
    const newFlashes = new Map<string, 'up' | 'down'>();
    items.forEach(q => {
      const prev = prevPrices.current.get(q.ticker);
      if (prev !== undefined && prev !== q.price) {
        newFlashes.set(q.ticker, q.price > prev ? 'up' : 'down');
      }
      prevPrices.current.set(q.ticker, q.price);
    });
    if (newFlashes.size > 0) {
      setFlashes(newFlashes);
      const t = setTimeout(() => setFlashes(new Map()), 300);
      return () => clearTimeout(t);
    }
  }, [items]);

  const sorted = [...items].sort((a, b) => {
    let cmp = 0;
    switch (sortKey) {
      case 'ticker': cmp = a.ticker.localeCompare(b.ticker); break;
      case 'price': cmp = a.price - b.price; break;
      case 'changePercent': cmp = a.changePercent - b.changePercent; break;
      case 'volume': cmp = a.volume - b.volume; break;
    }
    return sortAsc ? cmp : -cmp;
  });

  const handleSort = (key: SortKey) => {
    if (sortKey === key) setSortAsc(!sortAsc);
    else { setSortKey(key); setSortAsc(true); }
  };

  const SortIcon = ({ k }: { k: SortKey }) => (
    <span className="ml-1 text-text-secondary">
      {sortKey === k ? (sortAsc ? '▲' : '▼') : ''}
    </span>
  );

  return (
    <div className="h-full flex flex-col">
      <div className="drag-handle bg-bg-header px-3 py-2 text-xs font-semibold text-text-secondary uppercase tracking-wider border-b border-border rounded-t-lg cursor-grab">
        Cotations BRVM
      </div>
      <div className="overflow-auto flex-1">
        <table className="w-full text-sm">
          <thead className="sticky top-0 bg-bg-widget">
            <tr className="text-text-secondary text-xs">
              <th className="text-left px-3 py-2 cursor-pointer" onClick={() => handleSort('ticker')}>
                Ticker<SortIcon k="ticker" />
              </th>
              <th className="text-left px-3 py-2">Nom</th>
              <th className="text-right px-3 py-2 cursor-pointer" onClick={() => handleSort('price')}>
                Dernier<SortIcon k="price" />
              </th>
              <th className="text-right px-3 py-2 cursor-pointer" onClick={() => handleSort('changePercent')}>
                Var%<SortIcon k="changePercent" />
              </th>
              <th className="text-right px-3 py-2 cursor-pointer" onClick={() => handleSort('volume')}>
                Volume<SortIcon k="volume" />
              </th>
              <th className="text-right px-3 py-2">High</th>
              <th className="text-right px-3 py-2">Low</th>
            </tr>
          </thead>
          <tbody>
            {sorted.map(q => {
              const flash = flashes.get(q.ticker);
              return (
                <tr
                  key={q.ticker}
                  onClick={() => onSelectTicker(q.ticker)}
                  className={`cursor-pointer border-b border-border/50 hover:bg-bg-header/50 transition-colors
                    ${selectedTicker === q.ticker ? 'bg-accent/10' : ''}
                    ${flash === 'up' ? 'flash-green' : flash === 'down' ? 'flash-red' : ''}`}
                >
                  <td className="px-3 py-2 font-mono font-semibold text-accent">{q.ticker}</td>
                  <td className="px-3 py-2 text-text-secondary truncate max-w-[120px]">{q.name}</td>
                  <td className="px-3 py-2 text-right font-mono">{q.price.toLocaleString('fr-FR')}</td>
                  <td className={`px-3 py-2 text-right font-mono font-semibold ${q.changePercent >= 0 ? 'text-gain' : 'text-loss'}`}>
                    {q.changePercent >= 0 ? '+' : ''}{q.changePercent.toFixed(2)}%
                  </td>
                  <td className="px-3 py-2 text-right font-mono text-text-secondary">{q.volume.toLocaleString('fr-FR')}</td>
                  <td className="px-3 py-2 text-right font-mono text-text-secondary">{q.high.toLocaleString('fr-FR')}</td>
                  <td className="px-3 py-2 text-right font-mono text-text-secondary">{q.low.toLocaleString('fr-FR')}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
