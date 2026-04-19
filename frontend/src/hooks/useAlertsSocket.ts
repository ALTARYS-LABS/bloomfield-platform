import { useEffect } from 'react';
import { useWebSocket } from './useWebSocket';
import { useAuth } from './useAuth';
import { AlertEvent } from '../components/AlertsWidget';

/**
 * Hook pour écouter les alertes en temps réel via WebSocket STOMP.
 * S'abonne à la destination /user/queue/alerts et appelle le callback à la réception.
 */
export function useAlertsSocket(
  onAlertReceived: (event: AlertEvent) => void
): { isConnected: boolean } {
  const { accessToken } = useAuth();
  const { connected, subscribe } = useWebSocket(accessToken);

  useEffect(() => {
    if (connected) {
      subscribe('/user/queue/alerts', (data: unknown) => {
        try {
          const event = data as AlertEvent;
          onAlertReceived(event);
          /* Affiche une notification toast */
          showToast(`Alerte ${event.ticker} : ${event.price}`);
        } catch (err) {
          console.error('Erreur lors du traitement de l\'alerte:', err);
        }
      });
    }
  }, [connected, subscribe, onAlertReceived]);

  return { isConnected: connected };
}

/**
 * Affiche une notification toast (simple implémentation).
 */
function showToast(message: string) {
  /* En production, utiliser une librairie toast comme react-hot-toast */
  console.log('Toast:', message);

  /* Affichage basique en page */
  const toastDiv = document.createElement('div');
  toastDiv.textContent = message;
  toastDiv.style.cssText = `
    position: fixed;
    bottom: 20px;
    right: 20px;
    background: #10b981;
    color: white;
    padding: 12px 20px;
    border-radius: 6px;
    z-index: 9999;
  `;
  document.body.appendChild(toastDiv);
  setTimeout(() => toastDiv.remove(), 3000);
}
