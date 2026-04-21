import { useCallback, useEffect, useRef, useState } from 'react';
import { apiFetch, apiJson } from '../api/client';
import { useAuth } from '../auth/useAuth';
import { useWebSocket } from './useWebSocket';
import type { AlertEvent, AlertRule, AlertRuleRequest } from '../types/alerts';

// Hook central du module alertes :
//  - charge les regles et evenements via REST au montage (et apres chaque mutation),
//  - ecoute /user/queue/alerts pour les nouveaux declenchements live (le backend
//    republie aussi les evenements non livres a la reconnexion),
//  - expose createRule / deleteRule / markRead et un buffer de toasts consommable
//    par le composant d'affichage.
//
// Comme usePortfolio, on cree un client STOMP dedie via useWebSocket ; le surcout
// est acceptable et le decouplage facilite les tests.
export function useAlerts() {
  const { accessToken } = useAuth();
  const { connected, subscribe } = useWebSocket(accessToken);

  const [rules, setRules] = useState<AlertRule[]>([]);
  const [events, setEvents] = useState<AlertEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // File de toasts non encore affiches. Le widget les consomme via `dismissToast`
  // apres leur rendu. On utilise un ref pour eviter les re-subscribe inutiles.
  const [toasts, setToasts] = useState<AlertEvent[]>([]);
  // Deduplication : le backend renvoie les evenements non livres a la reconnexion
  // *et* aussi le REST GET /events les expose - sans garde, le meme evenement
  // apparait deux fois dans la liste.
  const seenIdsRef = useRef<Set<string>>(new Set());

  const refreshAll = useCallback(async () => {
    try {
      const [rulesResp, eventsResp] = await Promise.all([
        apiJson<AlertRule[]>('/api/alerts/rules'),
        apiJson<AlertEvent[]>('/api/alerts/events?limit=50'),
      ]);
      setRules(rulesResp);
      setEvents(eventsResp);
      eventsResp.forEach((e) => seenIdsRef.current.add(e.id));
      setError(null);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Erreur inconnue');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!accessToken) return;
    let cancelled = false;
    refreshAll().then(() => {
      if (cancelled) return;
    });
    return () => {
      cancelled = true;
    };
  }, [accessToken, refreshAll]);

  // Abonnement STOMP : messages pousses sur /user/queue/alerts (live + flush
  // des non-livres). Le broker route deja vers le bon utilisateur cote serveur.
  useEffect(() => {
    if (!accessToken) return;
    subscribe('/user/queue/alerts', (data) => {
      const incoming = data as AlertEvent;
      if (seenIdsRef.current.has(incoming.id)) return;
      seenIdsRef.current.add(incoming.id);
      setEvents((prev) => [incoming, ...prev].slice(0, 50));
      setToasts((prev) => [...prev, incoming]);
    });
  }, [subscribe, accessToken]);

  const createRule = useCallback(async (request: AlertRuleRequest): Promise<void> => {
    const response = await apiFetch('/api/alerts/rules', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    });
    if (!response.ok) {
      throw new Error(`Creation refusee (${response.status})`);
    }
    const saved = (await response.json()) as AlertRule;
    setRules((prev) => [saved, ...prev]);
  }, []);

  const deleteRule = useCallback(async (id: string): Promise<void> => {
    const response = await apiFetch(`/api/alerts/rules/${id}`, { method: 'DELETE' });
    // 404 (regle d'un autre utilisateur ou deja supprimee) est traite comme un succes
    // cote UX : le rendu desire est dans tous les cas "la regle n'est plus la".
    if (!response.ok && response.status !== 404) {
      throw new Error(`Suppression refusee (${response.status})`);
    }
    setRules((prev) => prev.filter((r) => r.id !== id));
  }, []);

  const markRead = useCallback(async (id: string): Promise<void> => {
    const response = await apiFetch(`/api/alerts/events/${id}/read`, { method: 'POST' });
    if (!response.ok && response.status !== 404) {
      throw new Error(`Marquage refuse (${response.status})`);
    }
    setEvents((prev) => prev.map((e) => (e.id === id ? { ...e, read: true } : e)));
  }, []);

  const dismissToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const unreadCount = events.filter((e) => !e.read).length;

  return {
    connected,
    rules,
    events,
    loading,
    error,
    unreadCount,
    toasts,
    createRule,
    deleteRule,
    markRead,
    dismissToast,
  };
}
