import type { Quote } from '../types/market';

interface Props {
  quotes: Map<string, Quote>;
}

export default function TickerBanner({ quotes }: Props) {
  const items = Array.from(quotes.values());
  if (items.length === 0) return null;

  const doubled = [...items, ...items];

  return (
    <div className="overflow-hidden bg-bg-header border-b border-border py-2">
      <div className="ticker-scroll flex whitespace-nowrap gap-8">
        {doubled.map((q, i) => (
          <span key={`${q.ticker}-${i}`} className="inline-flex items-center gap-2 text-sm font-mono">
            <span className="text-text-primary font-semibold">{q.ticker}</span>
            <span className="text-text-primary">{q.price.toLocaleString('fr-FR')}</span>
            <span className={q.changePercent >= 0 ? 'text-gain' : 'text-loss'}>
              {q.changePercent >= 0 ? '+' : ''}{q.changePercent.toFixed(2)}%
            </span>
          </span>
        ))}
      </div>
    </div>
  );
}
