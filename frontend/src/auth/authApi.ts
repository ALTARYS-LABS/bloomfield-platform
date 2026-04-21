import type { AuthUser, TokenResponse } from './types';

// Enveloppes fetch pour les endpoints /auth/*.
//
// `credentials: 'include'` est requis sur login/refresh/logout pour que le navigateur
// accepte de recevoir et renvoyer le cookie `refresh_token` (HttpOnly, SameSite=Strict).
// Sans cette option, le cookie ne survivrait pas au rechargement de la page.
// Voir _kb_/web-auth-security-tutorial.md.

export class AuthApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'AuthApiError';
    this.status = status;
  }
}

async function parseError(response: Response): Promise<AuthApiError> {
  // On tente d'extraire un message JSON type ProblemDetails ; sinon on retombe sur le statut HTTP.
  try {
    const body = await response.clone().json();
    const message = body?.detail ?? body?.message ?? response.statusText;
    return new AuthApiError(response.status, message);
  } catch {
    return new AuthApiError(response.status, response.statusText || 'Erreur réseau');
  }
}

export async function login(email: string, password: string): Promise<TokenResponse> {
  const response = await fetch('/auth/login', {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  if (!response.ok) {
    throw await parseError(response);
  }
  return response.json();
}

export async function register(
  email: string,
  password: string,
  fullName: string,
): Promise<void> {
  const response = await fetch('/auth/register', {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password, fullName }),
  });
  if (!response.ok) {
    throw await parseError(response);
  }
}

export async function refresh(): Promise<TokenResponse> {
  // Aucun corps : le backend lit le refresh token depuis le cookie HttpOnly.
  const response = await fetch('/auth/refresh', {
    method: 'POST',
    credentials: 'include',
  });
  if (!response.ok) {
    throw await parseError(response);
  }
  return response.json();
}

export async function logout(): Promise<void> {
  const response = await fetch('/auth/logout', {
    method: 'POST',
    credentials: 'include',
  });
  if (!response.ok && response.status !== 401) {
    // 401 signifie que le cookie a déjà été effacé, ce qui n'est pas une erreur utilisateur.
    throw await parseError(response);
  }
}

export async function me(accessToken: string): Promise<AuthUser> {
  const response = await fetch('/auth/me', {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!response.ok) {
    throw await parseError(response);
  }
  return response.json();
}
