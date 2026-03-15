import { useEffect, useState } from 'react';
import type { EmitterInfo, Quote } from '../types/market';

interface Props {
  ticker: string;
  quote?: Quote;
}

export default function EmitterDetail({ ticker, quote }: Props) {
  const [info, setInfo] = useState<EmitterInfo | null>(null);

  useEffect(() => {
    fetch(`/api/brvm/emitters/${ticker}`)
      .then(r => r.json())
      .then(setInfo)
      .catch(() => setInfo(null));
  }, [ticker]);

  const formatCap = (v: number) => {
    if (v >= 1e12) return `${(v / 1e12).toFixed(1)}T FCFA`;
    if (v >= 1e9) return `${(v / 1e9).toFixed(1)}Md FCFA`;
    return `${(v / 1e6).toFixed(0)}M FCFA`;
  };

  return (
    <div className="h-full flex flex-col">
      <div className="drag-handle bg-bg-header px-3 py-2 text-xs font-semibold text-text-secondary uppercase tracking-wider border-b border-border rounded-t-lg cursor-grab">
        Détail émetteur
      </div>
      <div className="flex-1 overflow-auto p-4">
        {!info ? (
          <div className="text-text-secondary text-sm text-center py-4">Sélectionnez un ticker</div>
        ) : (
          <div className="space-y-4">
            <div>
              <div className="text-xl font-bold text-accent">{ticker}</div>
              <div className="text-text-secondary text-sm">{info.name}</div>
              <div className="inline-block mt-1 px-2 py-0.5 text-xs bg-bg-header rounded text-text-secondary">{info.sector}</div>
            </div>

            {quote && (
              <div className="grid grid-cols-2 gap-3">
                <Stat label="Dernier prix" value={quote.price.toLocaleString('fr-FR')} />
                <Stat
                  label="Variation"
                  value={`${quote.changePercent >= 0 ? '+' : ''}${quote.changePercent.toFixed(2)}%`}
                  color={quote.changePercent >= 0 ? 'text-gain' : 'text-loss'}
                />
                <Stat label="Volume" value={quote.volume.toLocaleString('fr-FR')} />
                <Stat label="Ouverture" value={quote.open.toLocaleString('fr-FR')} />
              </div>
            )}

            <div className="border-t border-border pt-3 grid grid-cols-2 gap-3">
              <Stat label="Capitalisation" value={formatCap(info.marketCap)} />
              <Stat label="PER" value={info.per.toFixed(1)} />
              <Stat label="Rendement div." value={`${info.dividendYield.toFixed(1)}%`} color="text-gold" />
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function Stat({ label, value, color }: { label: string; value: string; color?: string }) {
  return (
    <div>
      <div className="text-[10px] text-text-secondary uppercase">{label}</div>
      <div className={`text-sm font-mono font-semibold ${color || 'text-text-primary'}`}>{value}</div>
    </div>
  );
}
