import React, { useState, useEffect } from 'react';
import {
  BarChart3,
  Plus,
  Trash2,
  CheckCircle,
} from 'lucide-react';
import { apiClient } from '../lib/api';
import { useAlertsSocket } from '../hooks/useAlertsSocket';

export interface AlertRule {
  id: string;
  ticker: string;
  operator: 'ABOVE' | 'BELOW' | 'CROSSES_UP' | 'CROSSES_DOWN';
  threshold: number;
  enabled: boolean;
  createdAt: string;
}

export interface AlertEvent {
  id: string;
  ticker: string;
  price: number;
  triggeredAt: string;
  read: boolean;
}

/**
 * Widget d'alertes : gère les règles de prix et l'historique des événements déclenchés.
 */
export function AlertsWidget() {
  const [activeTab, setActiveTab] = useState<'rules' | 'events'>('rules');
  const [rules, setRules] = useState<AlertRule[]>([]);
  const [events, setEvents] = useState<AlertEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [unreadCount, setUnreadCount] = useState(0);

  /* Nouveau ticker et seuil en création */
  const [newRule, setNewRule] = useState({
    ticker: '',
    operator: 'ABOVE' as const,
    threshold: '',
  });

  const { isConnected } = useAlertsSocket((event) => {
    /* Ajoute l'événement d'alerte reçu à la liste */
    setEvents((prev) => [event, ...prev]);
    if (!event.read) setUnreadCount((prev) => prev + 1);
  });

  /* Charge les règles et événements au montage */
  useEffect(() => {
    loadRules();
    loadEvents();
  }, []);

  const loadRules = async () => {
    try {
      const response = await apiClient.get('/api/alerts/rules');
      setRules(response.data);
    } catch (err) {
      console.error('Erreur lors du chargement des règles:', err);
    } finally {
      setLoading(false);
    }
  };

  const loadEvents = async () => {
    try {
      const response = await apiClient.get('/api/alerts/events?limit=50');
      const allEvents = response.data;
      setEvents(allEvents);
      setUnreadCount(allEvents.filter((e: AlertEvent) => !e.read).length);
    } catch (err) {
      console.error('Erreur lors du chargement des événements:', err);
    }
  };

  const createRule = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newRule.ticker || !newRule.threshold) return;

    try {
      await apiClient.post('/api/alerts/rules', {
        ticker: newRule.ticker.toUpperCase(),
        operator: newRule.operator,
        threshold: parseFloat(newRule.threshold),
      });
      setNewRule({ ticker: '', operator: 'ABOVE', threshold: '' });
      loadRules();
    } catch (err) {
      console.error('Erreur lors de la création de la règle:', err);
    }
  };

  const deleteRule = async (ruleId: string) => {
    try {
      await apiClient.delete(`/api/alerts/rules/${ruleId}`);
      setRules(rules.filter((r) => r.id !== ruleId));
    } catch (err) {
      console.error('Erreur lors de la suppression de la règle:', err);
    }
  };

  const markEventAsRead = async (eventId: string) => {
    try {
      await apiClient.post(`/api/alerts/events/${eventId}/read`);
      setEvents(
        events.map((e) =>
          e.id === eventId ? { ...e, read: true } : e
        )
      );
      setUnreadCount((prev) => Math.max(0, prev - 1));
    } catch (err) {
      console.error('Erreur lors du marquage comme lu:', err);
    }
  };

  return (
    <div className="bg-white rounded-lg shadow-lg p-6">
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-2">
          <BarChart3 className="w-6 h-6 text-blue-600" />
          <h2 className="text-xl font-bold">Alertes</h2>
          {unreadCount > 0 && (
            <span className="bg-red-500 text-white px-2 py-1 rounded-full text-xs font-bold">
              {unreadCount}
            </span>
          )}
        </div>
        <div className="flex gap-2">
          <button
            onClick={() => setActiveTab('rules')}
            className={`px-4 py-2 rounded ${
              activeTab === 'rules'
                ? 'bg-blue-600 text-white'
                : 'bg-gray-200 text-gray-800'
            }`}
          >
            Règles
          </button>
          <button
            onClick={() => setActiveTab('events')}
            className={`px-4 py-2 rounded ${
              activeTab === 'events'
                ? 'bg-blue-600 text-white'
                : 'bg-gray-200 text-gray-800'
            }`}
          >
            Événements
          </button>
        </div>
      </div>

      {activeTab === 'rules' && (
        <div className="space-y-4">
          {/* Formulaire de création de règle */}
          <form onSubmit={createRule} className="bg-gray-50 p-4 rounded-lg">
            <div className="grid grid-cols-4 gap-2 mb-3">
              <input
                type="text"
                placeholder="SNTS"
                value={newRule.ticker}
                onChange={(e) =>
                  setNewRule({ ...newRule, ticker: e.target.value })
                }
                className="px-3 py-2 border rounded"
              />
              <select
                value={newRule.operator}
                onChange={(e) =>
                  setNewRule({
                    ...newRule,
                    operator: e.target.value as AlertRule['operator'],
                  })
                }
                className="px-3 py-2 border rounded"
              >
                <option value="ABOVE">Au-dessus</option>
                <option value="BELOW">En-dessous</option>
                <option value="CROSSES_UP">Croise à la hausse</option>
                <option value="CROSSES_DOWN">Croise à la baisse</option>
              </select>
              <input
                type="number"
                placeholder="Seuil"
                value={newRule.threshold}
                onChange={(e) =>
                  setNewRule({ ...newRule, threshold: e.target.value })
                }
                className="px-3 py-2 border rounded"
                step="0.01"
              />
              <button
                type="submit"
                className="bg-green-600 text-white px-4 py-2 rounded hover:bg-green-700 flex items-center gap-2"
              >
                <Plus className="w-4 h-4" /> Créer
              </button>
            </div>
          </form>

          {/* Liste des règles */}
          {loading ? (
            <p className="text-gray-600">Chargement...</p>
          ) : rules.length === 0 ? (
            <p className="text-gray-600">Aucune règle créée</p>
          ) : (
            <div className="space-y-2">
              {rules.map((rule) => (
                <div
                  key={rule.id}
                  className="bg-gray-50 p-3 rounded flex justify-between items-center"
                >
                  <div>
                    <span className="font-bold">{rule.ticker}</span>
                    <span className="mx-2 text-gray-600">
                      {rule.operator === 'ABOVE'
                        ? '≥'
                        : rule.operator === 'BELOW'
                          ? '≤'
                          : rule.operator === 'CROSSES_UP'
                            ? '↗'
                            : '↘'}{' '}
                      {rule.threshold}
                    </span>
                    <span
                      className={`text-sm ${
                        rule.enabled
                          ? 'text-green-600'
                          : 'text-gray-400'
                      }`}
                    >
                      {rule.enabled ? 'Actif' : 'Déclenché'}
                    </span>
                  </div>
                  <button
                    onClick={() => deleteRule(rule.id)}
                    className="text-red-600 hover:text-red-800"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {activeTab === 'events' && (
        <div className="space-y-2">
          {events.length === 0 ? (
            <p className="text-gray-600">Aucun événement</p>
          ) : (
            events.map((event) => (
              <div
                key={event.id}
                className={`p-3 rounded flex justify-between items-center ${
                  event.read ? 'bg-gray-50' : 'bg-blue-50'
                }`}
              >
                <div>
                  <span className="font-bold">{event.ticker}</span>
                  <span className="mx-2 text-gray-600">
                    {event.price.toFixed(2)}
                  </span>
                  <span className="text-sm text-gray-500">
                    {new Date(event.triggeredAt).toLocaleString()}
                  </span>
                </div>
                {!event.read && (
                  <button
                    onClick={() => markEventAsRead(event.id)}
                    className="text-blue-600 hover:text-blue-800"
                  >
                    <CheckCircle className="w-4 h-4" />
                  </button>
                )}
              </div>
            ))
          )}
        </div>
      )}

      {/* Indicateur de connexion WebSocket */}
      <div className="mt-4 text-xs text-gray-500">
        {isConnected ? '🟢 Connecté' : '🔴 Déconnecté'}
      </div>
    </div>
  );
}
