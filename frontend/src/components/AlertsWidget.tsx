import { useState } from 'react';
import { OPERATOR_LABEL, type AlertEvent, type AlertRule, type ThresholdOperator } from '../types/alerts';

interface Props {
  rules: AlertRule[];
  events: AlertEvent[];
  loading: boolean;
  error: string | null;
  availableTickers: string[];
  unreadCount: number;
  onCreate: (ticker: string, operator: ThresholdOperator, threshold: string) => Promise<void>;
  onDelete: (id: string) => Promise<void>;
  onMarkRead: (id: string) => Promise<void>;
  onClose?: () => void;
}

type Tab = 'rules' | 'events';

function formatAmount(value: string): string {
  const n = Number(value);
  if (!Number.isFinite(n)) return value;
  return n.toLocaleString('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatRelative(isoDate: string): string {
  const then = new Date(isoDate).getTime();
  const diffSec = Math.max(0, Math.floor((Date.now() - then) / 1000));
  if (diffSec < 60) return `il y a ${diffSec}s`;
  if (diffSec < 3600) return `il y a ${Math.floor(diffSec / 60)}min`;
  if (diffSec < 86400) return `il y a ${Math.floor(diffSec / 3600)}h`;
  return new Date(isoDate).toLocaleDateString('fr-FR');
}

export default function AlertsWidget({
  rules,
  events,
  loading,
  error,
  availableTickers,
  unreadCount,
  onCreate,
  onDelete,
  onMarkRead,
  onClose,
}: Props) {
  const [tab, setTab] = useState<Tab>('rules');
  const [ticker, setTicker] = useState('');
  const [operator, setOperator] = useState<ThresholdOperator>('ABOVE');
  const [threshold, setThreshold] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const resolvedTicker = ticker || availableTickers[0] || '';

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!resolvedTicker || !threshold) return;
    setFormError(null);
    setSubmitting(true);
    try {
      await onCreate(resolvedTicker, operator, threshold);
      // Reset partiel : on garde le ticker et l'operateur (l'utilisateur va souvent
      // enchainer plusieurs seuils sur la meme valeur) mais on vide le seuil.
      setThreshold('');
    } catch (e: unknown) {
      setFormError(e instanceof Error ? e.message : 'Erreur inconnue');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="h-full flex flex-col">
      <div className="bg-bg-header px-3 py-2 text-xs font-semibold text-text-secondary uppercase tracking-wider border-b border-border flex items-center justify-between">
        <span>Alertes</span>
        {onClose && (
          <button
            type="button"
            onClick={onClose}
            aria-label="Fermer"
            className="text-text-secondary hover:text-text-primary text-sm"
          >
            ×
          </button>
        )}
      </div>

      <div className="flex border-b border-border bg-bg-header text-xs">
        <button
          type="button"
          onClick={() => setTab('rules')}
          className={`flex-1 py-2 uppercase tracking-wider font-semibold ${
            tab === 'rules' ? 'text-accent border-b-2 border-accent' : 'text-text-secondary'
          }`}
        >
          Regles ({rules.length})
        </button>
        <button
          type="button"
          onClick={() => setTab('events')}
          className={`flex-1 py-2 uppercase tracking-wider font-semibold flex items-center justify-center gap-1 ${
            tab === 'events' ? 'text-accent border-b-2 border-accent' : 'text-text-secondary'
          }`}
        >
          Evenements
          {unreadCount > 0 && (
            <span className="bg-loss text-bg-primary text-[10px] font-mono px-1.5 rounded-full">
              {unreadCount}
            </span>
          )}
        </button>
      </div>

      <div className="flex-1 overflow-auto">
        {loading && <div className="p-3 text-xs text-text-secondary">Chargement...</div>}
        {error && <div className="p-3 text-xs text-loss">Erreur : {error}</div>}

        {tab === 'rules' && !loading && (
          <ul className="divide-y divide-border/50">
            {rules.length === 0 ? (
              <li className="p-4 text-xs text-text-secondary text-center">
                Aucune regle. Creez-en une ci-dessous.
              </li>
            ) : (
              rules.map((r) => (
                <li key={r.id} className="px-3 py-2 flex items-center gap-2 text-xs">
                  <span className="font-mono font-semibold text-accent w-16">{r.ticker}</span>
                  <span className="text-text-secondary flex-1">
                    {OPERATOR_LABEL[r.operator]} <span className="font-mono">{formatAmount(r.threshold)}</span>
                  </span>
                  <span
                    className={`text-[10px] uppercase font-mono ${r.enabled ? 'text-gain' : 'text-text-secondary'}`}
                  >
                    {r.enabled ? 'Armee' : 'Dec.'}
                  </span>
                  <button
                    type="button"
                    onClick={() => void onDelete(r.id)}
                    className="text-text-secondary hover:text-loss px-1"
                    aria-label={`Supprimer la regle ${r.ticker}`}
                  >
                    ×
                  </button>
                </li>
              ))
            )}
          </ul>
        )}

        {tab === 'events' && !loading && (
          <ul className="divide-y divide-border/50">
            {events.length === 0 ? (
              <li className="p-4 text-xs text-text-secondary text-center">Aucun evenement.</li>
            ) : (
              events.map((e) => (
                <li
                  key={e.id}
                  className={`px-3 py-2 flex items-center gap-2 text-xs ${
                    e.read ? 'opacity-60' : ''
                  }`}
                >
                  <span className="font-mono font-semibold text-accent w-16">{e.ticker}</span>
                  <span className="font-mono flex-1">{formatAmount(e.price)}</span>
                  <span className="text-[10px] text-text-secondary">{formatRelative(e.triggeredAt)}</span>
                  {!e.read && (
                    <button
                      type="button"
                      onClick={() => void onMarkRead(e.id)}
                      className="text-[10px] text-accent hover:underline"
                    >
                      Lu
                    </button>
                  )}
                </li>
              ))
            )}
          </ul>
        )}
      </div>

      {tab === 'rules' && (
        <form
          onSubmit={handleSubmit}
          className="shrink-0 border-t border-border bg-bg-header p-2 grid grid-cols-[1fr_1fr_1fr_auto] gap-2 items-end text-xs"
        >
          <div className="flex flex-col">
            <label htmlFor="alert-ticker" className="text-[10px] text-text-secondary uppercase">
              Ticker
            </label>
            <select
              id="alert-ticker"
              value={resolvedTicker}
              onChange={(e) => setTicker(e.target.value)}
              className="bg-bg-widget border border-border rounded px-1.5 py-1 font-mono"
            >
              {availableTickers.length === 0 ? (
                <option value="">-</option>
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
            <label htmlFor="alert-op" className="text-[10px] text-text-secondary uppercase">
              Condition
            </label>
            <select
              id="alert-op"
              value={operator}
              onChange={(e) => setOperator(e.target.value as ThresholdOperator)}
              className="bg-bg-widget border border-border rounded px-1.5 py-1 font-mono"
            >
              {(Object.keys(OPERATOR_LABEL) as ThresholdOperator[]).map((op) => (
                <option key={op} value={op}>
                  {OPERATOR_LABEL[op]}
                </option>
              ))}
            </select>
          </div>
          <div className="flex flex-col">
            <label htmlFor="alert-threshold" className="text-[10px] text-text-secondary uppercase">
              Seuil
            </label>
            <input
              id="alert-threshold"
              type="number"
              min="0"
              step="any"
              value={threshold}
              onChange={(e) => setThreshold(e.target.value)}
              className="bg-bg-widget border border-border rounded px-1.5 py-1 font-mono"
              required
            />
          </div>
          <button
            type="submit"
            disabled={submitting || !resolvedTicker || !threshold}
            className="px-3 py-1 bg-accent text-bg-primary rounded font-semibold uppercase text-[10px] disabled:opacity-50"
          >
            {submitting ? '...' : 'Armer'}
          </button>
          {formError && <span className="text-loss text-[10px] col-span-4">{formError}</span>}
        </form>
      )}
    </div>
  );
}
