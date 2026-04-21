import { useEffect } from 'react';
import type { AlertEvent } from '../types/alerts';

interface Props {
  toasts: AlertEvent[];
  onDismiss: (id: string) => void;
}

const AUTO_DISMISS_MS = 5000;

// Pile de toasts en haut a droite de l'ecran. Chaque toast correspond a un
// evenement d'alerte fraichement recu via STOMP. Auto-dismiss apres 5s, ou
// manuellement via la croix. On s'appuie sur le parent (useAlerts) pour la
// file : les toasts affiches ici sont juste un miroir.
export default function AlertToasts({ toasts, onDismiss }: Props) {
  return (
    <div className="fixed top-14 right-3 z-50 flex flex-col gap-2 w-72 max-w-[calc(100vw-1.5rem)]">
      {toasts.map((t) => (
        <AlertToast key={t.id} event={t} onDismiss={onDismiss} />
      ))}
    </div>
  );
}

function AlertToast({ event, onDismiss }: { event: AlertEvent; onDismiss: (id: string) => void }) {
  useEffect(() => {
    // Timer d'auto-dismiss. Memoriser l'id dans la closure suffit, l'effet ne
    // depend que de lui.
    const timer = window.setTimeout(() => onDismiss(event.id), AUTO_DISMISS_MS);
    return () => window.clearTimeout(timer);
  }, [event.id, onDismiss]);

  const priceLabel = Number(event.price).toLocaleString('fr-FR', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });

  return (
    <div
      role="status"
      className="bg-bg-widget border border-accent rounded-lg shadow-lg px-3 py-2 text-xs flex items-start gap-2"
    >
      <span className="text-accent text-sm leading-none">⚑</span>
      <div className="flex-1">
        <div className="font-semibold">
          Alerte <span className="font-mono text-accent">{event.ticker}</span>
        </div>
        <div className="text-text-secondary">Declenchee a {priceLabel}</div>
      </div>
      <button
        type="button"
        onClick={() => onDismiss(event.id)}
        aria-label="Fermer"
        className="text-text-secondary hover:text-text-primary"
      >
        ×
      </button>
    </div>
  );
}
