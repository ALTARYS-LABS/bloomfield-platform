// Client HTTP authentifié. Enveloppe fetch() pour attacher automatiquement le
// header Authorization et, en cas de 401, déclencher un refresh silencieux puis
// rejouer la requête une seule fois. Au-delà, on considère la session perdue.

interface ClientConfig {
  getAccessToken: () => string | null;
  refreshAccessToken: () => Promise<string>;
  onUnauthorized: () => void;
}

let config: ClientConfig | null = null;

// Un seul refresh simultané : si plusieurs requêtes échouent en 401 en même temps,
// elles partagent la même promesse plutôt que de bombarder le backend.
let refreshInFlight: Promise<string> | null = null;

export function configureApiClient(next: ClientConfig): void {
  config = next;
}

function ensureConfigured(): ClientConfig {
  if (!config) {
    throw new Error("Le client API n'est pas configuré : AuthProvider doit être monté avant utilisation.");
  }
  return config;
}

function withAuth(init: RequestInit | undefined, token: string | null): RequestInit {
  const headers = new Headers(init?.headers);
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }
  return { ...init, headers };
}

async function doRefresh(): Promise<string> {
  const { refreshAccessToken } = ensureConfigured();
  if (!refreshInFlight) {
    refreshInFlight = refreshAccessToken().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

export async function apiFetch(input: string, init?: RequestInit): Promise<Response> {
  const cfg = ensureConfigured();
  const firstAttempt = await fetch(input, withAuth(init, cfg.getAccessToken()));
  if (firstAttempt.status !== 401) {
    return firstAttempt;
  }

  // 401 : on tente un unique refresh puis on rejoue la requête originale.
  try {
    const newToken = await doRefresh();
    const secondAttempt = await fetch(input, withAuth(init, newToken));
    if (secondAttempt.status === 401) {
      cfg.onUnauthorized();
    }
    return secondAttempt;
  } catch {
    // Refresh échoué : session perdue, on notifie le contexte pour déclencher la déconnexion UI.
    cfg.onUnauthorized();
    return firstAttempt;
  }
}

export async function apiJson<T>(input: string, init?: RequestInit): Promise<T> {
  const response = await apiFetch(input, init);
  if (!response.ok) {
    throw new Error(`Requête ${input} a échoué : ${response.status}`);
  }
  return response.json();
}

// Exposé pour les tests uniquement : permet de réinitialiser l'état entre cas.
export function __resetApiClientForTests(): void {
  config = null;
  refreshInFlight = null;
}
