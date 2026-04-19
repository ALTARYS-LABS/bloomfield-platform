import { useCallback, useEffect, useState } from 'react';
import { apiFetch, apiJson } from '../api/client';
import { useAuth } from '../auth/useAuth';
import { useWebSocket } from './useWebSocket';
import type { PortfolioSummary, SubmitTradeRequest } from '../types/portfolio';

// Hook central du module portefeuille :
//   - récupère le résumé initial via REST (au montage et après chaque trade),
//   - s'abonne à /user/queue/portfolio pour recevoir les valorisations live toutes les 2 s,
//   - expose submitTrade() qui POST /api/portfolio/trades et remplace immédiatement le summary
//     avec la réponse (le broadcast STOMP prendra ensuite le relais).
// Le WebSocket est créé ici avec son propre client ; c'est intentionnellement redondant avec
// useMarketData (un socket supplémentaire), en échange d'un découplage complet des deux flux.
export function usePortfolio() {
  const { accessToken } = useAuth();
  const { connected, subscribe } = useWebSocket(accessToken);
  const [summary, setSummary] = useState<PortfolioSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Chargement initial via REST : on a besoin du summary avant le premier push pour
  // éviter un écran vide pendant les 2 premières secondes.
  useEffect(() => {
    if (!accessToken) {
      return;
    }
    // L'effet ne fait que brancher le fetch sur l'état ; on évite setLoading synchrone
    // pour respecter la règle react-hooks/set-state-in-effect (pas de cascade de renders).
    let cancelled = false;
    apiJson<PortfolioSummary>('/api/portfolio')
      .then((initial) => {
        if (!cancelled) {
          setSummary(initial);
          setError(null);
          setLoading(false);
        }
      })
      .catch((e: unknown) => {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : 'Erreur inconnue');
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [accessToken]);

  // Abonnement STOMP : le broker route /user/queue/portfolio vers le principal courant
  // (UUID utilisateur), donc pas d'isolation à faire côté client.
  useEffect(() => {
    if (!accessToken) {
      return;
    }
    subscribe('/user/queue/portfolio', (data) => {
      setSummary(data as PortfolioSummary);
    });
  }, [subscribe, accessToken]);

  const submitTrade = useCallback(async (request: SubmitTradeRequest): Promise<void> => {
    const response = await apiFetch('/api/portfolio/trades', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    });
    if (!response.ok) {
      // Le backend renvoie un body { error: "..." } avec un message français prêt à afficher.
      const payload = await response.json().catch(() => ({ error: 'Erreur inconnue' }));
      throw new Error(payload.error ?? `Trade refusé (${response.status})`);
    }
    const updated = (await response.json()) as PortfolioSummary;
    setSummary(updated);
  }, []);

  return { connected, summary, loading, error, submitTrade };
}
