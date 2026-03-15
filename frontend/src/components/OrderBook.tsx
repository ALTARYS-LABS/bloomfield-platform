import type { OrderBookEntry } from '../types/market';

interface Props {
  orderBook?: OrderBookEntry;
  ticker: string;
}

export default function OrderBook({ orderBook, ticker }: Props) {
  if (!orderBook) {
    return (
      <div className="h-full flex flex-col">
        <div className="drag-handle bg-bg-header px-3 py-2 text-xs font-semibold text-text-secondary uppercase tracking-wider border-b border-border rounded-t-lg cursor-grab">
          Carnet d'ordres — {ticker}
        </div>
        <div className="flex-1 flex items-center justify-center text-text-secondary text-sm">
          En attente de données...
        </div>
      </div>
    );
  }

  const maxQty = Math.max(
    ...orderBook.bids.map(b => b.quantity),
    ...orderBook.asks.map(a => a.quantity),
  );

  return (
    <div className="h-full flex flex-col">
      <div className="drag-handle bg-bg-header px-3 py-2 text-xs font-semibold text-text-secondary uppercase tracking-wider border-b border-border rounded-t-lg cursor-grab">
        Carnet d'ordres — {ticker}
      </div>
      <div className="flex-1 overflow-auto p-2">
        <div className="grid grid-cols-2 gap-2 text-xs font-mono">
          {/* Asks (reversed so highest at top) */}
          <div>
            <div className="text-text-secondary text-[10px] uppercase mb-1 px-1 flex justify-between">
              <span>Prix (Ask)</span><span>Qté</span>
            </div>
            {[...orderBook.asks].reverse().map((a, i) => (
              <div key={i} className="relative flex justify-between px-1 py-0.5">
                <div
                  className="absolute inset-0 bg-loss/10 rounded"
                  style={{ width: `${(a.quantity / maxQty) * 100}%`, right: 0, left: 'auto' }}
                />
                <span className="relative text-loss">{a.price.toLocaleString('fr-FR')}</span>
                <span className="relative text-text-secondary">{a.quantity.toLocaleString('fr-FR')}</span>
              </div>
            ))}
          </div>
          {/* Bids */}
          <div>
            <div className="text-text-secondary text-[10px] uppercase mb-1 px-1 flex justify-between">
              <span>Prix (Bid)</span><span>Qté</span>
            </div>
            {orderBook.bids.map((b, i) => (
              <div key={i} className="relative flex justify-between px-1 py-0.5">
                <div
                  className="absolute inset-0 bg-gain/10 rounded"
                  style={{ width: `${(b.quantity / maxQty) * 100}%` }}
                />
                <span className="relative text-gain">{b.price.toLocaleString('fr-FR')}</span>
                <span className="relative text-text-secondary">{b.quantity.toLocaleString('fr-FR')}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
