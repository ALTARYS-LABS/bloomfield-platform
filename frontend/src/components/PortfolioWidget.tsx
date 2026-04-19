import { useMemo, useState } from 'react';
import { usePortfolio } from '../hooks/usePortfolio';
import type { TradeSide } from '../types/portfolio';

interface Props {
  availableTickers: string[];
}

// Formatage monétaire XOF à deux décimales. On reçoit des strings du backend (préservation
// de la précision BigDecimal) et on ne les convertit en Number que pour l'affichage.
function formatAmount(value: string): string {
  const n = Number(value);
  if (!Number.isFinite(n)) return value;
  return n.toLocaleString('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatPercent(value: string): string {
  const n = Number(value);
  if (!Number.isFinite(n)) return value;
  const sign = n >= 0 ? '+' : '';
  return `${sign}${n.toFixed(2)}%`;
}

function pnlClass(value: string): string {
  const n = Number(value);
  if (!Number.isFinite(n) || n === 0) return 'text-text-secondary';
  return n > 0 ? 'text-gain' : 'text-loss';
}

export default function PortfolioWidget({ availableTickers }: Props) {
  const { connected, summary, loading, error, submitTrade } = usePortfolio();
  const [ticker, setTicker] = useState('');
  const [side, setSide] = useState<TradeSide>('BUY');
  const [quantity, setQuantity] = useState('1');
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  // On pré-remplit le ticker dès que la liste de cotations est dispo, pour qu'il n'y ait
  // pas de flash "vide" au premier render.
  const resolvedTicker = ticker || availableTickers[0] || '';

  const positions = useMemo(() => summary?.positions ?? [], [summary]);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!resolvedTicker) return;
    setFormError(null);
    setSubmitting(true);
    try {
      await submitTrade({ ticker: resolvedTicker, side, quantity });
    } catch (e: unknown) {
      setFormError(e instanceof Error ? e.message : 'Erreur inconnue');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="h-full flex flex-col">
      <div className="drag-handle bg-bg-header px-3 py-2 text-xs font-semibold text-text-secondary uppercase tracking-wider border-b border-border rounded-t-lg cursor-grab flex items-center justify-between">
        <span>Portefeuille</span>
        <span className={`text-[10px] font-mono ${connected ? 'text-gain' : 'text-loss'}`}>
          {connected ? 'Live' : 'Off'}
        </span>
      </div>

      <div className="flex-1 overflow-auto">
        {loading && !summary && (
          <div className="p-3 text-xs text-text-secondary">Chargement…</div>
        )}
        {error && (
          <div className="p-3 text-xs text-loss">Erreur : {error}</div>
        )}

        {summary && (
          <>
            {/* En-tête totaux : valeur, P&L non réalisé, P&L réalisé */}
            <div className="grid grid-cols-3 gap-2 p-3 border-b border-border text-xs">
              <div>
                <div className="text-text-secondary uppercase text-[10px]">Valeur</div>
                <div className="font-mono font-semibold">{formatAmount(summary.totalValue)}</div>
              </div>
              <div>
                <div className="text-text-secondary uppercase text-[10px]">P&L latent</div>
                <div className={`font-mono font-semibold ${pnlClass(summary.unrealizedPnl)}`}>
                  {formatAmount(summary.unrealizedPnl)}{' '}
                  <span className="text-[10px]">({formatPercent(summary.unrealizedPnlPercent)})</span>
                </div>
              </div>
              <div>
                <div className="text-text-secondary uppercase text-[10px]">P&L réalisé</div>
                <div className={`font-mono font-semibold ${pnlClass(summary.realizedPnl)}`}>
                  {formatAmount(summary.realizedPnl)}
                </div>
              </div>
            </div>

            {/* Tableau positions */}
            <table className="w-full text-xs md:text-sm">
              <thead className="sticky top-0 bg-bg-widget">
                <tr className="text-text-secondary text-[10px] md:text-xs">
                  <th className="text-left px-2 md:px-3 py-2">Ticker</th>
                  <th className="text-right px-2 md:px-3 py-2">Qté</th>
                  <th className="text-right px-2 md:px-3 py-2 hidden sm:table-cell">PRU</th>
                  <th className="text-right px-2 md:px-3 py-2">Cours</th>
                  <th className="text-right px-2 md:px-3 py-2 hidden md:table-cell">Valeur</th>
                  <th className="text-right px-2 md:px-3 py-2">P&L</th>
                </tr>
              </thead>
              <tbody>
                {positions.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="px-3 py-4 text-center text-text-secondary text-xs">
                      Aucune position. Passez un ordre pour démarrer.
                    </td>
                  </tr>
                ) : (
                  positions.map((p) => (
                    <tr key={p.ticker} className="border-b border-border/50">
                      <td className="px-2 md:px-3 py-1.5 md:py-2 font-mono font-semibold text-accent">{p.ticker}</td>
                      <td className="px-2 md:px-3 py-1.5 md:py-2 text-right font-mono">{formatAmount(p.quantity)}</td>
                      <td className="px-2 md:px-3 py-1.5 md:py-2 text-right font-mono text-text-secondary hidden sm:table-cell">
                        {formatAmount(p.avgCost)}
                      </td>
                      <td className="px-2 md:px-3 py-1.5 md:py-2 text-right font-mono">{formatAmount(p.currentPrice)}</td>
                      <td className="px-2 md:px-3 py-1.5 md:py-2 text-right font-mono text-text-secondary hidden md:table-cell">
                        {formatAmount(p.marketValue)}
                      </td>
                      <td className={`px-2 md:px-3 py-1.5 md:py-2 text-right font-mono font-semibold ${pnlClass(p.unrealizedPnl)}`}>
                        {formatAmount(p.unrealizedPnl)}
                        <div className="text-[10px] font-normal">{formatPercent(p.unrealizedPnlPercent)}</div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </>
        )}
      </div>

      {/* Formulaire de passage d'ordre : volontairement minimal pour la démo RFP. */}
      <form
        onSubmit={handleSubmit}
        className="shrink-0 border-t border-border bg-bg-header px-3 py-2 flex flex-wrap gap-2 items-end text-xs"
      >
        <div className="flex flex-col">
          <label htmlFor="pf-ticker" className="text-[10px] text-text-secondary uppercase">Ticker</label>
          <select
            id="pf-ticker"
            value={resolvedTicker}
            onChange={(e) => setTicker(e.target.value)}
            className="bg-bg-widget border border-border rounded px-1.5 py-1 font-mono"
          >
            {availableTickers.length === 0 ? (
              <option value="">—</option>
            ) : (
              availableTickers.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))
            )}
          </select>
        </div>
        <div className="flex flex-col">
          <label htmlFor="pf-side" className="text-[10px] text-text-secondary uppercase">Sens</label>
          <select
            id="pf-side"
            value={side}
            onChange={(e) => setSide(e.target.value as TradeSide)}
            className="bg-bg-widget border border-border rounded px-1.5 py-1 font-mono"
          >
            <option value="BUY">BUY</option>
            <option value="SELL">SELL</option>
          </select>
        </div>
        <div className="flex flex-col">
          <label htmlFor="pf-quantity" className="text-[10px] text-text-secondary uppercase">Qté</label>
          <input
            id="pf-quantity"
            type="number"
            min="0.000001"
            step="any"
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
            className="bg-bg-widget border border-border rounded px-1.5 py-1 font-mono w-24"
          />
        </div>
        <button
          type="submit"
          disabled={submitting || !resolvedTicker}
          className="px-3 py-1 bg-accent text-bg-primary rounded font-semibold uppercase text-[10px] disabled:opacity-50"
        >
          {submitting ? '…' : 'Exécuter'}
        </button>
        {formError && <span className="text-loss text-[10px] w-full">{formError}</span>}
      </form>
    </div>
  );
}
